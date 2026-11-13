import { createHmac, randomUUID } from 'node:crypto';
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
const courseId = process.env.DEMO_COURSE_ID ?? 'demo-course';
const moduleId = process.env.DEMO_MODULE_ID ?? 'demo-module';
const classId = process.env.DEMO_CLASS_ID ?? 'demo-last-seat';
const classPath = '/catalog/modules/' + moduleId + '/classes';
const courses = ok(await student.request('/catalog/courses'));
assert.ok(courses.some(course => course.id === courseId));
const modules = ok(await student.request('/catalog/courses/' + courseId + '/modules'));
assert.ok(modules.some(module => module.id === moduleId));
const classes = ok(await student.request(classPath));
const lastSeat = classes.find(classroom => classroom.id === classId);
assert.equal(lastSeat.remainingSeats, 1, 'Run this smoke test on a fresh demo seed');
assert.equal(lastSeat.lessons[0].roomName, 'Phong 101');

const secret = process.env.PAYOS_CHECKSUM_KEY;
assert.ok(secret, 'Set PAYOS_CHECKSUM_KEY to the local webhook secret');
async function webhook(order, amount, reference, valid = true) {
  const data = {
    orderCode: order.orderCode, amount, reference, code: '00',
    currency: 'VND', paymentLinkId: 'demo-' + order.orderCode,
  };
  const fields = Object.keys(data).sort().map(key => key + '=' + data[key]).join('&');
  const signature = createHmac('sha256', secret).update(fields).digest('hex');
  return fetch(base + '/api/payments/payos/webhook', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code: '00', success: true, data, signature: valid ? signature : '0'.repeat(64) }),
  });
}

const clients = [student, otherStudent];
const orders = await Promise.all(clients.map(async client =>
  ok(await client.post('/enrollments/checkout', { classId: lastSeat.id }))));
const [first, second] = orders;
assert.ok(orders.every(order => order.status === 'PENDING' && !order.invoiceId));
assert.equal(ok(await student.request(classPath))
  .find(item => item.id === lastSeat.id).remainingSeats, 1);
assert.equal((await otherStudent.request('/enrollments/checkout/' + first.orderCode)).status, 404);
const short = ok(await student.post('/enrollments/checkout', { classId: lastSeat.id }));
assert.equal((await webhook(short, short.amount - 1, randomUUID())).status, 200);
assert.equal(ok(await student.request('/enrollments/checkout/' + short.orderCode)).status, 'REVIEW');
assert.equal((await webhook(first, first.amount, randomUUID(), false)).status, 401);
assert.equal(ok(await student.request('/enrollments/checkout/' + first.orderCode)).status, 'PENDING');

const references = orders.map(() => randomUUID());
const responses = await Promise.all(orders.map((order, index) => webhook(order, order.amount, references[index])));
for (const response of responses) assert.equal(response.status, 200, await response.text());
const results = await Promise.all(clients.map(async (client, index) =>
  ok(await client.request('/enrollments/checkout/' + orders[index].orderCode))));
assert.deepEqual(results.map(result => result.status).sort(), ['ENROLLED', 'REVIEW']);
const winnerIndex = results.findIndex(result => result.status === 'ENROLLED');
const loserIndex = 1 - winnerIndex;
const invoicePath = '/invoices/' + results[winnerIndex].invoiceId;
assert.equal((await clients[loserIndex].request(invoicePath)).status, 404);
const paid = ok(await clients[winnerIndex].request(invoicePath));
assert.equal(paid.status, 'PAID');
assert.equal(paid.remainingAmount, 0);
assert.equal(results[loserIndex].invoiceId, null);
assert.equal((await webhook(orders[winnerIndex], orders[winnerIndex].amount, references[winnerIndex])).status, 200);
assert.equal((await webhook(orders[loserIndex], orders[loserIndex].amount, references[loserIndex])).status, 200);
assert.equal((await student.request('/payments/payos/reviews')).status, 403);
const reviews = ok(await accountant.request('/payments/payos/reviews'));
assert.equal(reviews.filter(item => item.reference === references[loserIndex]).length, 1);
assert.equal(reviews.find(item => item.reference === references[loserIndex]).reason, 'Lớp đã hết chỗ.');
assert.ok(reviews.some(item => item.orderCode === short.orderCode));
console.log('Passed: login, course catalog, no hold, checkout ownership, invalid signature, short payment, concurrent last seat, duplicate webhook and accountant review');
