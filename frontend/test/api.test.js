import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api } from '../src/services/api.js';

function mockApi(t, steps) {
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    const step = steps[calls++];
    assert.ok(step, 'Unexpected request: ' + path);
    assert.equal(path, '/api' + step.path);
    assert.equal(options.credentials, 'same-origin');
    assert.equal(options.method ?? 'GET', step.method ?? 'GET');
    step.check?.(options);
    return step.status === 204 ? new Response(null, { status: 204 })
      : Response.json(step.body ?? {}, { status: step.status ?? 200 });
  });
  t.after(() => assert.equal(calls, steps.length));
}

const csrf = token => ({ path: '/auth/csrf', body: { headerName: 'X-CSRF-TOKEN', token } });

test('student logs in and opens checkout and reads the paid invoice', async t => {
  mockApi(t, [
    csrf('before-login'),
    { path: '/auth/login', method: 'POST', status: 204, check: options => {
      assert.equal(options.headers['X-CSRF-TOKEN'], 'before-login');
      assert.equal(options.headers['Content-Type'], 'application/x-www-form-urlencoded');
      assert.equal(options.body.get('username'), 'student');
      assert.equal(options.body.get('password'), 'demo');
    } },
    { path: '/auth/me', body: { roles: ['STUDENT'] } },
    { path: '/catalog/courses', body: [{ id: 'course' }] },
    { path: '/catalog/courses/course/modules', body: [{ id: 'module' }] },
    { path: '/catalog/modules/module/classes', body: [{ id: 'class', remainingSeats: 1 }] },
    csrf('after-login'),
    { path: '/enrollments/checkout', method: 'POST', body: { orderCode: 123, status: 'PENDING' }, check: options => {
      assert.equal(options.headers['X-CSRF-TOKEN'], 'after-login');
      assert.deepEqual(JSON.parse(options.body), { classId: 'class' });
    } },
    { path: '/enrollments/checkout/123', body: { status: 'ENROLLED', invoiceId: 'invoice' } },
    { path: '/invoices/invoice', body: { remainingAmount: 0 } },
  ]);
  assert.deepEqual((await api.login('student', 'demo')).roles, ['STUDENT']);
  const [course] = await api.courses();
  const [module] = await api.modules(course.id);
  const [classroom] = await api.classes(module.id);
  const order = await api.openCheckout(classroom.id);
  assert.equal(order.status, "PENDING");
  const registration = await api.checkoutStatus(order.orderCode);
  assert.equal((await api.invoice(registration.invoiceId)).remainingAmount, 0);
});

test('accountant submits payment with the same reference on retry', async t => {
  const payment = { amount: '120000.50', method: 'BANK_TRANSFER', referenceCode: 'REF-01' };
  const receipt = (token, status) => ({
    path: '/invoices/invoice/payments', method: 'POST', status,
    body: status === 409 ? { detail: 'Retry the request' } : { id: 'payment' },
    check: options => {
      assert.equal(options.headers['X-CSRF-TOKEN'], token);
      assert.deepEqual(JSON.parse(options.body), payment);
    },
  });
  mockApi(t, [csrf('a'), receipt('a', 409), csrf('b'), receipt('b', 200),
    csrf('c'), { path: '/auth/logout', method: 'POST', status: 204 }]);
  await assert.rejects(api.pay('invoice', payment), { status: 409, message: 'Retry the request' });
  assert.equal((await api.pay('invoice', payment)).id, 'payment');
  await api.logout();
});

test('preserves forbidden and expired-session status', async t => {
  mockApi(t, [
    { path: '/invoices/other', status: 403 },
    { path: '/auth/me', status: 401 },
  ]);
  await assert.rejects(api.invoice('other'), { status: 403 });
  await assert.rejects(api.me(), { status: 401 });
});
