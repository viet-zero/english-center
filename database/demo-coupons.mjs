import { randomInt, randomUUID } from 'node:crypto';

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

const key=process.env.SEPAY_WEBHOOK_API_KEY;
assert.ok(key,'Set SEPAY_WEBHOOK_API_KEY for the local mock webhook');
const admin=session(),student=session(),other=session(),accountant=session();
await admin.login('demo.admin','ADMIN');
await student.login('demo.student','STUDENT');
await other.login('demo.student2','STUDENT');
await accountant.login('demo.accountant','ACCOUNTANT');
const tag=Date.now().toString(36).toUpperCase();
function created(result) { assert.equal(result.status,201,JSON.stringify(result)); return result.body; }
async function put(client,path,data) {
  const csrf=await client.request('/auth/csrf');
  return client.request(path,{method:'PUT',headers:{[csrf.body.headerName]:csrf.body.token,'Content-Type':'application/json'},body:JSON.stringify(data)});
}
const time=offset=>new Date(Date.now()+offset- new Date().getTimezoneOffset()*60000).toISOString().slice(0,19);
const window={startsAt:time(-86400000),expiresAt:time(7*86400000),maxUses:3,status:'ACTIVE'};
async function fixture(suffix,capacity=10) {
  const course=created(await admin.post('/training/courses',{name:'Coupon '+tag+suffix,status:'ACTIVE'}));
  const module=created(await admin.post('/training/courses/'+course.id+'/modules',{
    sequenceNumber:1,details:{name:'Coupon module',tuition:10000,durationHours:24,status:'ACTIVE'},
  }));
  async function classroom(code) {
    return created(await admin.post('/training/modules/'+module.id+'/classes',{
      teacherId:'demo-teacher',classCode:'CP-'+tag+suffix+code,startDate:'2027-01-01',endDate:'2027-12-31',
      details:{maxStudents:capacity,status:'OPEN'},
    }));
  }
  return {course,module,first:await classroom('A'),second:await classroom('B')};
}
async function coupon(fixture,suffix,amount=2000,options={}) {
  const code='CP'+tag+suffix;
  const details={code,courseId:fixture.course.id,amount,...window,...options};
  const result=created(await admin.post('/admin/discounts',details));
  return {...details,id:result.id};
}
const uses=async code=>ok(await admin.request('/admin/discounts')).find(item=>item.id===code.id).usedCount;
const checkout=(client,fixture,code)=>client.post('/enrollments/checkout',{classId:fixture.first.id,couponCode:code?.code.toLowerCase()});
function event(order,amount=order.amount) {
  return {id:randomInt(1,2**47),accountNumber:order.receivingAccount,transferType:'in',transferAmount:amount,
    code:order.paymentCode,content:order.paymentCode,referenceCode:randomUUID()};
}
async function webhook(data) {
  return fetch(base+'/api/payments/sepay/webhook',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Apikey '+key},body:JSON.stringify(data)});
}
async function pay(order,amount=order.amount) {
  const data=event(order,amount),response=await webhook(data);
  assert.equal(response.status,201,await response.text());
  return data;
}
const normal=await fixture('N');
const normalCode=await coupon(normal,'NORMAL');
assert.equal((await student.request('/admin/discounts')).status,403);
assert.equal((await student.post('/admin/discounts',normalCode)).status,403);
assert.equal((await admin.post('/admin/discounts',{...normalCode,code:normalCode.code.toLowerCase()})).status,409);
const incomplete=ok(await checkout(student,normal,normalCode));
const quoted=ok(await student.post('/enrollments/checkout',{
  classId:normal.first.id,couponCode:normalCode.code,amount:1,discountAmount:9999,
}));
assert.equal(quoted.tuitionAmount,10000);
assert.equal(quoted.discountAmount,2000);
assert.equal(quoted.amount,8000);
assert.equal(await uses(normalCode),0);
await pay(incomplete,10000);
assert.equal(await uses(normalCode),0);
assert.equal(ok(await student.request('/enrollments/checkout/'+incomplete.orderCode)).status,'REVIEW');
const successfulEvent=await pay(quoted);
const paid=ok(await student.request('/enrollments/checkout/'+quoted.orderCode));
assert.equal(paid.status,'ENROLLED');
let invoice=ok(await student.request('/invoices/'+paid.invoiceId));
assert.equal(Number(invoice.tuitionAmount),10000);
assert.equal(Number(invoice.discount),2000);
assert.equal(Number(invoice.totalDue),8000);
assert.equal(Number(invoice.totalPaid),8000);
assert.equal(invoice.couponCode,normalCode.code);
assert.equal(Number(invoice.remainingAmount),0);
assert.equal(await uses(normalCode),1);
assert.equal((await webhook(successfulEvent)).status,201);
assert.equal(await uses(normalCode),1);
const shared=ok(await checkout(other,normal,normalCode));
await pay(shared);
assert.equal(await uses(normalCode),2);
assert.equal((await put(admin,'/admin/discounts/'+normalCode.id,{...window,maxUses:1})).status,409);
const wrong=await fixture('W');
assert.equal((await checkout(student,wrong,normalCode)).status,409);
for(const [suffix,options,amount] of [
  ['EXPIRED',{startsAt:time(-3*86400000),expiresAt:time(-86400000)},2000],
  ['FUTURE',{startsAt:time(86400000)},2000],
  ['OFF',{status:'INACTIVE'},2000],
  ['LARGE',{},10001],
]) {
  const invalid=await coupon(wrong,suffix,amount,options);
  assert.equal((await checkout(student,wrong,invalid)).status,409);
  assert.equal(await uses(invalid),0);
}
const racing=await fixture('R');
const raceCode=await coupon(racing,'RACE',2000,{maxUses:1});
const raceOrders=await Promise.all([student,other].map(async(client,index)=>ok(await client.post('/enrollments/checkout',{
  classId:index===0?racing.first.id:racing.second.id,couponCode:raceCode.code,
}))));
assert.equal(await uses(raceCode),0);
const raceEvents=raceOrders.map(order=>event(order));
const responses=await Promise.all(raceEvents.map(webhook));
for(const response of responses) assert.equal(response.status,201,await response.text());
const raceResults=await Promise.all([student,other].map(async(client,index)=>ok(await client.request('/enrollments/checkout/'+raceOrders[index].orderCode))));
assert.deepEqual(raceResults.map(item=>item.status).sort(),['ENROLLED','REVIEW']);
assert.equal(await uses(raceCode),1);
const expired=await fixture('E');
const expiredCode=await coupon(expired,'LATE',2000,{maxUses:1});
const late=ok(await checkout(student,expired,expiredCode));
assert.equal((await put(admin,'/admin/discounts/'+expiredCode.id,{...window,startsAt:time(-3*86400000),expiresAt:time(-86400000),maxUses:1})).status,204);
const lateEvent=await pay(late);
assert.equal(ok(await student.request('/enrollments/checkout/'+late.orderCode)).status,'REVIEW');
assert.equal(await uses(expiredCode),0);
const full=await fixture('F',1);
const fullCode=await coupon(full,'FULL',2000,{maxUses:1});
const waiting=ok(await checkout(student,full,fullCode));
const lastSeat=ok(await checkout(other,full,null));
await pay(lastSeat);
const waitingEvent=await pay(waiting);
assert.equal(ok(await student.request('/enrollments/checkout/'+waiting.orderCode)).status,'REVIEW');
assert.equal(await uses(fullCode),0);
assert.equal((await accountant.post('/payments/reviews/SEPAY-DEMO-'+waitingEvent.id+'/resolve',{
  action:'TRANSFER',targetClassId:full.second.id,note:'Chuyển lớp với mã giảm giá',
})).status,204);
assert.equal(await uses(fullCode),1);
const moved=ok(await student.request('/enrollments/checkout/'+waiting.orderCode));
invoice=ok(await student.request('/invoices/'+moved.invoiceId));
assert.equal(Number(invoice.discount),2000);
assert.equal(Number(invoice.totalDue),8000);
const shifts=ok(await accountant.request('/accounting/shifts'));
if(!shifts.current.id) ok(await accountant.post('/accounting/shifts',{id:randomUUID(),openingCash:0}));
assert.equal((await accountant.post('/payments/reviews/SEPAY-DEMO-'+lateEvent.id+'/resolve',{action:'REFUND_DEMO',note:'Hoàn do mã hết hạn'})).status,204);
assert.equal(await uses(expiredCode),0);
const free=await fixture('Z');
const freeCode=await coupon(free,'FREE',10000,{maxUses:1});
const zero=ok(await checkout(student,free,freeCode));
assert.equal(zero.amount,0);
assert.equal(zero.qrUrl,null);
assert.equal(await uses(freeCode),0);
assert.equal((await other.post('/enrollments/checkout/'+zero.orderCode+'/confirm-free',{})).status,404);
const confirmed=ok(await student.post('/enrollments/checkout/'+zero.orderCode+'/confirm-free',{}));
assert.equal(confirmed.status,'ENROLLED');
assert.equal(await uses(freeCode),1);
assert.deepEqual(ok(await student.post('/enrollments/checkout/'+zero.orderCode+'/confirm-free',{})),confirmed);
invoice=ok(await student.request('/invoices/'+confirmed.invoiceId));
assert.equal(Number(invoice.discount),10000);
assert.equal(Number(invoice.totalPaid),0);
assert.equal(invoice.status,'PAID');
console.log('PASS: coupon windows/course/limits, pending and wrong payments, server pricing, SePay success/replay, concurrent last use, expiry during payment, class-full transfer/refund, full discount and permissions');
console.log(JSON.stringify({normal:normalCode.code,race:raceCode.code,free:freeCode.code,invoice:invoice.invoiceCode},null,2));
