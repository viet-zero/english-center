
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

assert.equal(ok(await student.request('/enrollments/payment-mode')).demo, true);
const clients = [student, otherStudent];
const orders = await Promise.all(clients.map(async client =>
  ok(await client.post('/enrollments/checkout', { classId }))));
assert.ok(orders.every(order => order.status === 'PENDING' && !order.invoiceId));
assert.equal(ok(await student.request(classPath)).find(item => item.id === classId).remainingSeats, 1);
const tokens = await Promise.all(clients.map(client => client.request('/auth/csrf')));
const results = await Promise.all(clients.map(async (client, index) =>
  ok(await client.post('/enrollments/checkout/' + orders[index].orderCode + '/demo-payment', {}, false, tokens[index]))));
assert.deepEqual(results.map(order => order.status).sort(), ['ENROLLED', 'REVIEW']);
const winnerIndex = results.findIndex(order => order.status === 'ENROLLED');
const loserIndex = 1 - winnerIndex;
assert.equal(results[loserIndex].invoiceId, null);
const invoice = ok(await clients[winnerIndex].request('/invoices/' + results[winnerIndex].invoiceId));
assert.equal(invoice.status, 'PAID');
assert.equal(invoice.remainingAmount, 0);
assert.equal(ok(await student.request(classPath)).find(item => item.id === classId).remainingSeats, 0);
for (let index = 0; index < clients.length; index++) {
  assert.deepEqual(ok(await clients[index].post(
    '/enrollments/checkout/' + orders[index].orderCode + '/demo-payment', {})), results[index]);
}
const reviews = ok(await accountant.request('/payments/payos/reviews'));
const review = reviews.filter(item => item.reference === 'DEMO-' + orders[loserIndex].orderCode);
assert.equal(review.length, 1);
assert.equal(review[0].reason, 'Lớp đã hết chỗ.');
assert.equal(Number(review[0].receivedAmount), orders[loserIndex].amount);
assert.equal((await student.request('/payments/payos/reviews')).status, 403);
console.log(JSON.stringify({ classId, results, invoiceCode: invoice.invoiceCode, remainingAmount: invoice.remainingAmount, review: review[0] }, null, 2));
console.log('PASS: simultaneous demo payments, one seat, paid invoice, accountant review, retries and permissions');
