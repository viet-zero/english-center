import { randomInt } from 'node:crypto';
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

const secret = process.env.SEPAY_WEBHOOK_API_KEY;
assert.ok(secret, 'Set SEPAY_WEBHOOK_API_KEY to the local webhook secret');
const transactionIds = new Map();
async function webhook(order, amount, reference, valid = true, useContent = false) {
  if (!transactionIds.has(reference)) transactionIds.set(reference, randomInt(1, 2 ** 47));
  const data = {
    id: transactionIds.get(reference), transferAmount: amount, referenceCode: reference,
    accountNumber: order.receivingAccount, code: useContent ? null : order.paymentCode, content: order.paymentCode,
    transferType: 'in', gateway: 'DemoBank', transactionDate: '2026-11-18 10:00:00',
  };
  return fetch(base + '/api/payments/sepay/webhook', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Apikey ' + (valid ? secret : 'invalid') },
    body: JSON.stringify(data),
  });
}
async function raw(event, authorization = 'Apikey ' + secret) {
  return fetch(base + '/api/payments/sepay/webhook', {
    method:'POST', headers:{'Content-Type':'application/json',Authorization:authorization}, body:JSON.stringify(event),
  });
}
const outgoing=ok(await student.post('/enrollments/checkout',{classId:lastSeat.id}));
const outgoingEvent={
  id:randomInt(1,2**47),accountNumber:outgoing.receivingAccount,transferType:'out',transferAmount:outgoing.amount,
  code:outgoing.paymentCode,content:outgoing.paymentCode,referenceCode:'out-test',
};
assert.equal((await raw(outgoingEvent)).status,201);
assert.equal(ok(await student.request('/enrollments/checkout/'+outgoing.orderCode)).status,'PENDING');
assert.equal((await raw({...outgoingEvent,id:randomInt(1,2**47),accountNumber:'WRONG-ACCOUNT',transferType:'in'})).status,403);
assert.equal((await raw({...outgoingEvent,id:randomInt(1,2**47),transferType:'in'},'Apikey wrong')).status,401);
const unmatchedId=randomInt(1,2**47);
assert.equal((await raw({...outgoingEvent,id:unmatchedId,transferType:'in',code:null,content:'Khong co ma don'})).status,201);
const clients = [student, otherStudent];
const orders = await Promise.all(clients.map(async client =>
  ok(await client.post('/enrollments/checkout', { classId: lastSeat.id }))));
const [first, second] = orders;
const ambiguousId=randomInt(1,2**47);
assert.equal((await raw({...outgoingEvent,id:ambiguousId,transferType:'in',code:null,
  content:first.paymentCode+' '+second.paymentCode})).status,201);
assert.equal(ok(await student.request('/enrollments/checkout/'+first.orderCode)).status,'PENDING');
assert.ok(orders.every(order => order.status === 'PENDING' && !order.invoiceId));
assert.equal(ok(await student.request(classPath))
  .find(item => item.id === lastSeat.id).remainingSeats, 1);
assert.equal((await otherStudent.request('/enrollments/checkout/' + first.orderCode)).status, 404);
const short = ok(await student.post('/enrollments/checkout', { classId: lastSeat.id }));
assert.equal((await webhook(short, short.amount - 1, String(randomInt(1, 2 ** 47)))).status, 201);
assert.equal(ok(await student.request('/enrollments/checkout/' + short.orderCode)).status, 'REVIEW');
assert.equal((await webhook(first, first.amount, String(randomInt(1, 2 ** 47)), false)).status, 401);
assert.equal(ok(await student.request('/enrollments/checkout/' + first.orderCode)).status, 'PENDING');

const references = orders.map(() => String(randomInt(1, 2 ** 47)));
const responses = await Promise.all(orders.map((order, index) => webhook(order, order.amount, references[index], true, index === 1)));
for (const response of responses) assert.equal(response.status, 201, await response.text());
const results = await Promise.all(clients.map(async (client, index) =>
  ok(await client.request('/enrollments/checkout/' + orders[index].orderCode))));
assert.deepEqual(results.map(result => result.status).sort(), ['ENROLLED', 'REVIEW']);
const winnerIndex = results.findIndex(result => result.status === 'ENROLLED');
const loserIndex = 1 - winnerIndex;
const winner=clients[winnerIndex];
const invoicePath = '/invoices/' + results[winnerIndex].invoiceId;
assert.equal((await clients[loserIndex].request(invoicePath)).status, 404);
const paid = ok(await clients[winnerIndex].request(invoicePath));
assert.equal(paid.status, 'PAID');
assert.equal(paid.remainingAmount, 0);
assert.equal(results[loserIndex].invoiceId, null);
assert.equal((await webhook(orders[winnerIndex], orders[winnerIndex].amount, references[winnerIndex])).status, 201);
assert.equal((await webhook(orders[loserIndex], orders[loserIndex].amount, references[loserIndex])).status, 201);
assert.equal((await student.request('/payments/reviews')).status, 403);
const reviews = ok(await accountant.request('/payments/reviews'));
assert.equal(reviews.filter(item => item.reference === 'SEPAY-DEMO-' + transactionIds.get(references[loserIndex])).length, 1);
assert.equal(reviews.find(item => item.reference === 'SEPAY-DEMO-' + transactionIds.get(references[loserIndex])).reason, 'Lớp đã hết chỗ.');
assert.ok(reviews.some(item => item.orderCode === short.orderCode));
assert.equal((await webhook(orders[winnerIndex],orders[winnerIndex].amount+1,references[winnerIndex])).status,409);
const unmatched=ok(await accountant.request('/payments/sepay/unmatched'));
assert.ok(unmatched.some(item=>item.transactionId===unmatchedId));
assert.ok(unmatched.some(item=>item.transactionId===ambiguousId));
assert.equal((await student.request('/payments/sepay/unmatched')).status,403);
const buttonRetry=ok(await winner.post('/enrollments/checkout/'+orders[winnerIndex].orderCode+'/demo-payment',{}));
assert.equal(buttonRetry.status,'ENROLLED');
assert.equal((await fetch(base+'/api/payments/payos/webhook',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'})).status,403);
console.log('Passed: login, course catalog, no hold, checkout ownership, invalid API Key, short payment, concurrent last seat, duplicate webhook and accountant review');
