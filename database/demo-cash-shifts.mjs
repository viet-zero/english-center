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

const tag=process.env.DEMO_CASH_PREFIX;
assert.ok(tag,'Set DEMO_CASH_PREFIX for prepared demo invoices/classes');
const accountant=session(),student=session(),other=session(),training=session(),admin=session();
await accountant.login('demo.accountant','ACCOUNTANT');
await student.login('demo.student','STUDENT');
await other.login('demo.student2','STUDENT');
await training.login(process.env.DEMO_TRAINING_USERNAME??'demo.training.mweokbew','TRAINING_STAFF');
await admin.login('demo.admin','ADMIN');
async function put(client,path,data) {
  const token=await client.request('/auth/csrf');
  return client.request(path,{method:'PUT',headers:{[token.body.headerName]:token.body.token,'Content-Type':'application/json'},body:JSON.stringify(data)});
}
const now=new Date();
const day=[now.getFullYear(),String(now.getMonth()+1).padStart(2,'0'),String(now.getDate()).padStart(2,'0')].join('-');
const reportPath='/accounting/revenue?from='+day+'&to='+day;
const report=async()=>ok(await accountant.request(reportPath));
let shifts=ok(await accountant.request('/accounting/shifts'));
if(shifts.current.id) ok(await accountant.post('/accounting/shifts/'+shifts.current.id+'/close',{actualCash:shifts.current.expected_cash,note:'Chốt ca demo trước kiểm thử'}));
const pay=(invoice,amount,method,referenceCode)=>accountant.post('/invoices/'+invoice+'/payments',{amount,method,referenceCode});
assert.equal((await pay(tag+'ci',10000,'CASH',tag+'cash')).status,409);
const baseline=(await report()).summary;
const id=randomUUID();
const token=await accountant.request('/auth/csrf');
const opened=await Promise.all([1,2].map(()=>accountant.post('/accounting/shifts',{id,openingCash:1000},false,token)));
assert.ok(opened.every(item=>item.status===200 && item.body.id===id),JSON.stringify(opened));
assert.equal((await accountant.post('/accounting/shifts',{id:randomUUID(),openingCash:0})).status,409);
ok(await pay(tag+'ci',10000,'CASH',tag+'cash'));
ok(await pay(tag+'bi',20000,'BANK_TRANSFER',tag+'bank'));
shifts=ok(await accountant.request('/accounting/shifts'));
assert.equal(Number(shifts.current.expected_cash),11000);
async function cancel(enrollmentId,method) {
  const request=ok(await student.post('/academics/student/requests',{enrollmentId,kind:'CANCEL',reason:'Hủy demo ca thu tiền'}));
  assert.equal((await put(training,'/training/student-requests/'+request.id,{approved:true,note:'Duyệt hủy demo'})).status,204);
  assert.equal((await accountant.post('/payments/student-refunds/'+request.id,{note:'Hoàn trong ca demo',method})).status,204);
  assert.equal((await accountant.post('/payments/student-refunds/'+request.id,{note:'Hoàn trong ca demo',method})).status,204);
}
await cancel(tag+'ce','CASH');
await cancel(tag+'be','BANK_TRANSFER');
shifts=ok(await accountant.request('/accounting/shifts'));
assert.equal(Number(shifts.current.cash_received),10000);
assert.equal(Number(shifts.current.cash_refunded),5000);
assert.equal(Number(shifts.current.expected_cash),6000);
async function reviewPair(suffix,transfer) {
  const clients=[student,other];
  const orders=await Promise.all(clients.map(async client=>ok(await client.post('/enrollments/checkout',{classId:tag+suffix}))));
  const paid=await Promise.all(clients.map(async(client,index)=>ok(await client.post('/enrollments/checkout/'+orders[index].orderCode+'/demo-payment',{}))));
  assert.deepEqual(paid.map(item=>item.status).sort(),['ENROLLED','REVIEW']);
  const loser=paid.findIndex(item=>item.status==='REVIEW');
  const reference='SEPAY-DEMO-'+orders[loser].orderCode;
  const before=(await report()).summary;
  assert.equal((await accountant.post('/payments/reviews/'+reference+'/resolve',{
    action:transfer?'TRANSFER':'REFUND_DEMO',targetClassId:transfer?tag+'target':null,note:'Đối soát ca demo',
  })).status,204);
  if(transfer) assert.equal(Number((await report()).summary.received),Number(before.received),'Transfer must not count payOS money twice');
}
await reviewPair('p',true);
await reviewPair('q',false);
const summary=(await report()).summary;
assert.equal(Number(summary.received)-Number(baseline.received),70000);
assert.equal(Number(summary.refunded)-Number(baseline.refunded),25000);
assert.equal(Number(summary.netReceived)-Number(baseline.netReceived),45000);
assert.equal(Number(summary.cashReceived)-Number(baseline.cashReceived),10000);
assert.equal(Number(summary.cashRefunded)-Number(baseline.cashRefunded),5000);
assert.equal((await accountant.post('/accounting/shifts/'+id+'/close',{actualCash:5900,note:''})).status,409);
const closeToken=await accountant.request('/auth/csrf');
const closed=await Promise.all([1,2].map(()=>accountant.post('/accounting/shifts/'+id+'/close',{actualCash:5900,note:'Chênh lệch demo 100 đồng'},false,closeToken)));
assert.ok(closed.every(item=>item.status===200 && Number(item.body.difference)===-100),JSON.stringify(closed));
assert.equal((await accountant.post('/accounting/shifts/'+id+'/close',{actualCash:6000,note:'Sửa ca'})).status,409);
assert.equal((await pay(tag+'ri',1000,'CASH',tag+'after')).status,409);
assert.equal((await pay(tag+'ci',10000,'CASH',tag+'cash')).status,200,'Payment retry must remain safe after close');
assert.equal((await student.request('/accounting/shifts')).status,403);
assert.equal((await student.request(reportPath)).status,403);
assert.equal((await accountant.request('/accounting/revenue?from=2026-12-02&to=2026-12-01')).status,400);
const accountName='demo.cash.'+tag;
assert.equal((await admin.post('/users/employees',{employee:{account:{fullName:'Demo cash owner',username:accountName,password},employeeCode:tag},roles:['ACCOUNTANT']})).status,201);
const secondAccountant=session();
await secondAccountant.login(accountName,'ACCOUNTANT');
assert.equal(ok(await secondAccountant.request('/accounting/shifts')).history.length,0);
assert.equal((await secondAccountant.post('/accounting/shifts/'+id+'/close',{actualCash:5900,note:'Sai chủ ca'})).status,404);
const raceId=randomUUID();
ok(await accountant.post('/accounting/shifts',{id:raceId,openingCash:1000}));
const raceToken=await accountant.request('/auth/csrf');
const [payment,closure]=await Promise.all([
  accountant.post('/invoices/'+tag+'ri/payments',{amount:3000,method:'CASH',referenceCode:tag+'race'},false,raceToken),
  accountant.post('/accounting/shifts/'+raceId+'/close',{actualCash:1000,note:'Kiểm tra thu đồng thời chốt ca'},false,raceToken),
]);
assert.ok([200,409].includes(payment.status),JSON.stringify(payment));
assert.equal(closure.status,200,JSON.stringify(closure));
assert.equal(Number(closure.body.expected_cash),payment.status===200?4000:1000);
assert.equal(ok(await accountant.request('/accounting/shifts')).current.id,undefined);
console.log('PASS: open/close retries, shift requirement, cash vs bank, two refund sources, no double-count on transfer, discrepancy notes, ownership and payment/close concurrency');
console.log(JSON.stringify({tag,receivedDelta:70000,refundedDelta:25000,netDelta:45000,closedDifference:-100,racePaymentStatus:payment.status},null,2));
