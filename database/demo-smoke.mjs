import assert from 'node:assert/strict';

const base = process.env.DEMO_URL ?? 'http://127.0.0.1:5175';
const password = process.env.DEMO_PASSWORD;
assert.ok(password, 'Set DEMO_PASSWORD to the local demo password');

function session() {
  let cookie;
  async function request(path, options = {}) {
    const headers = new Headers(options.headers);
    if (cookie) headers.set('Cookie', cookie);
    const response = await fetch(base + '/api' + path, { ...options, headers });
    for (const value of response.headers.getSetCookie()) {
      if (value.startsWith('JSESSIONID=')) cookie = value.split(';', 1)[0];
    }
    const body = await response.text();
    return { status: response.status, body: body ? JSON.parse(body) : null };
  }

  async function post(path, payload, form = false, existingToken) {
    const token = existingToken ?? await request('/auth/csrf');
    assert.equal(token.status, 200);
    return request(path, {
      method: 'POST',
      headers: {
        [token.body.headerName]: token.body.token,
        'Content-Type': form ? 'application/x-www-form-urlencoded' : 'application/json',
      },
      body: form ? new URLSearchParams(payload) : JSON.stringify(payload),
    });
  }

  async function login(username, role) {
    const result = await post('/auth/login', { username, password }, true);
    assert.equal(result.status, 204);
    const user = await request('/auth/me');
    assert.equal(user.status, 200);
    assert.ok(user.body.roles.includes(role));
  }
  return { request, post, login };
}

function ok(result) {
  assert.equal(result.status, 200, JSON.stringify(result));
  return result.body;
}

const student = session();
const otherStudent = session();
const accountant = session();
await Promise.all([
  student.login('demo.student', 'STUDENT'),
  otherStudent.login('demo.student2', 'STUDENT'),
  accountant.login('demo.accountant', 'ACCOUNTANT'),
]);
const courses = ok(await student.request('/catalog/courses'));
assert.ok(courses.some(course => course.id === 'demo-course'));
const modules = ok(await student.request('/catalog/courses/demo-course/modules'));
assert.ok(modules.some(module => module.id === 'demo-module'));
const classes = ok(await student.request('/catalog/modules/demo-module/classes'));
const lastSeat = classes.find(classroom => classroom.id === 'demo-last-seat');
assert.equal(lastSeat.remainingSeats, 1, 'Run this smoke test on a fresh demo seed');
assert.equal(lastSeat.lessons[0].roomName, 'Phong 101');

const results = await Promise.all([student, otherStudent].map(client =>
  client.post('/enrollments', { classId: lastSeat.id })));
assert.deepEqual(results.map(result => result.status).sort(), [200, 409], JSON.stringify(results));
const winnerIndex = results.findIndex(result => result.status === 200);
const winner = [student, otherStudent][winnerIndex];
const loser = [otherStudent, student][winnerIndex];
const registration = results[winnerIndex].body;
assert.deepEqual(ok(await winner.post('/enrollments', { classId: lastSeat.id })), registration);
const invoicePath = '/invoices/' + registration.invoiceId;
assert.equal((await loser.request(invoicePath)).status, 404);
assert.equal(ok(await winner.request(invoicePath)).remainingAmount, 1000000);

const payment = { amount: '400000.00', method: 'CASH', referenceCode: 'DEMO-SMOKE-01' };
assert.equal((await winner.post(invoicePath + '/payments', payment)).status, 403);
const paymentToken = await accountant.request('/auth/csrf');
const receipts = await Promise.all([1, 2].map(() =>
  accountant.post(invoicePath + '/payments', payment, false, paymentToken)));
assert.equal(ok(receipts[0]).id, ok(receipts[1]).id);
assert.equal(ok(await winner.request(invoicePath)).remainingAmount, 600000);
ok(await accountant.post(invoicePath + '/payments', {
  ...payment, amount: '600000.00', referenceCode: 'DEMO-SMOKE-02',
}));
const paid = ok(await winner.request(invoicePath));
assert.equal(paid.status, 'PAID');
assert.equal(paid.remainingAmount, 0);
console.log('Passed: login, catalog, last-seat concurrency, invoice ownership, payment authorization, retry and paid balance');
