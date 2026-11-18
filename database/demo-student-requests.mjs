import { randomUUID } from 'node:crypto';

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

const prefix = process.env.DEMO_REQUEST_PREFIX;
assert.ok(prefix, 'Set DEMO_REQUEST_PREFIX for the prepared demo classes');
const student = session();
const other = session();
const training = session();
const accountant = session();
await student.login('demo.student', 'STUDENT');
await other.login('demo.student2', 'STUDENT');
await training.login(process.env.DEMO_TRAINING_USERNAME ?? 'demo.training.mweokbew', 'TRAINING_STAFF');
await accountant.login('demo.accountant', 'ACCOUNTANT');
const shifts = ok(await accountant.request('/accounting/shifts'));
if (!shifts.current.id) ok(await accountant.post('/accounting/shifts', {id:randomUUID(),openingCash:0}));
async function put(client, path, data, token) {
  token ??= await client.request('/auth/csrf');
  return client.request(path, { method: 'PUT', headers: {
    [token.body.headerName]: token.body.token, 'Content-Type': 'application/json',
  }, body: JSON.stringify(data) });
}
const registrations = {};
for (const suffix of ['c','h','r','p']) {
  const order = ok(await student.post('/enrollments/checkout', {classId: prefix + suffix}));
  registrations[suffix] = ok(await student.post('/enrollments/checkout/' + order.orderCode + '/demo-payment', {}));
  assert.equal(registrations[suffix].status, 'ENROLLED');
}
const enrollments = ok(await student.request('/academics/student/request-enrollments'));
function enrollment(suffix) {
  return enrollments.find(item => item.classCode === prefix + suffix).id;
}
async function submit(suffix, kind) {
  return student.post('/academics/student/requests', {enrollmentId:enrollment(suffix),kind,reason:'Lý do demo ' + suffix});
}
assert.equal((await other.post('/academics/student/requests', {
  enrollmentId:enrollment('c'),kind:'CANCEL',reason:'Sai chủ sở hữu',
})).status,404);
const cancelled = ok(await submit('c','CANCEL')).id;
assert.equal((await submit('c','HOLD')).status,409);
const held = ok(await submit('h','HOLD')).id;
const rejected = ok(await submit('r','CANCEL')).id;
assert.equal((await submit('p','CANCEL')).status,409);
assert.equal((await accountant.post('/payments/student-refunds/' + cancelled,{note:'Chưa duyệt'})).status,409);
assert.equal((await put(student,'/training/student-requests/' + cancelled,{approved:true,note:'Sai quyền'})).status,403);
const decisionPath = '/training/student-requests/' + cancelled;
const token = await training.request('/auth/csrf');
const decisions = await Promise.all([1,2].map(() => put(training,decisionPath,{approved:true,note:'Duyệt demo'},token)));
assert.ok(decisions.every(result => result.status === 204), JSON.stringify(decisions));
assert.equal((await put(training,'/training/student-requests/' + held,{approved:true,note:'Duyệt bảo lưu demo'})).status,204);
assert.equal((await put(training,'/training/student-requests/' + rejected,{approved:false,note:'Từ chối demo'})).status,204);
assert.equal((await put(training,decisionPath,{approved:false,note:'Đổi quyết định'})).status,409);
const list = ok(await student.request('/academics/student/requests'));
assert.equal(Number(list.find(item => item.id === cancelled).remainingValue),5000);
assert.equal(Number(list.find(item => item.id === held).remainingValue),5000);
assert.equal(list.find(item => item.id === rejected).status,'REJECTED');
const classes = ok(await student.request('/academics/student/classes'));
assert.ok(!classes.some(item => item.id === prefix+'c' || item.id === prefix+'h'));
assert.ok(classes.some(item => item.id === prefix+'r'));
for (const suffix of ['c','h']) {
  const catalog = ok(await student.request('/catalog/modules/' + prefix+suffix + '/classes'));
  assert.equal(catalog.find(item => item.id === prefix+suffix).remainingSeats,2);
}
assert.equal((await student.post('/payments/student-refunds/'+cancelled,{note:'Sai quyền'})).status,403);
assert.equal((await accountant.post('/payments/student-refunds/'+held,{note:'Bảo lưu'})).status,409);
assert.equal((await accountant.post('/payments/student-refunds/'+rejected,{note:'Từ chối'})).status,409);
const refundToken = await accountant.request('/auth/csrf');
const refunds = await Promise.all([1,2].map(() => accountant.post(
  '/payments/student-refunds/'+cancelled,{note:'Phiếu hoàn tiền demo'},false,refundToken)));
assert.ok(refunds.every(item => item.status===204),JSON.stringify(refunds));
const invoice = ok(await student.request('/invoices/'+registrations.c.invoiceId));
assert.equal(Number(invoice.refundedAmount),5000);
assert.equal(Number(invoice.remainingAmount),0);
const holdInvoice = ok(await student.request('/invoices/'+registrations.h.invoiceId));
assert.equal(Number(holdInvoice.reservedAmount),5000);
const final = ok(await student.request('/academics/student/requests'));
assert.equal(final.find(item => item.id===cancelled).status,'REFUNDED');
const accounting = ok(await accountant.request('/payments/student-refunds'));
assert.equal(accounting.items.filter(item => item.id===cancelled).length,1);
console.log('PASS: ownership, 50% policy, pending duplicates, approve/reject, released seats, reserved value, refunds, permissions and concurrent retries');
console.log(JSON.stringify({prefix,cancelled,held,rejected},null,2));
