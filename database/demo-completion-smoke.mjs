import { randomUUID } from 'node:crypto';
import { writeFile } from 'node:fs/promises';

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
    if (response.headers.get('content-type')?.includes('application/pdf')) {
      return { status: response.status, body: new Uint8Array(await response.arrayBuffer()) };
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

const tag = process.env.DEMO_CERTIFICATE_PREFIX;
assert.ok(tag, 'Set DEMO_CERTIFICATE_PREFIX for prepared completion demo classes');
const student = session(), other = session(), teacher = session(), training = session(), accountant = session();
await student.login('demo.student','STUDENT');
await other.login('demo.student2','STUDENT');
await teacher.login('demo.teacher','TEACHER');
await training.login(process.env.DEMO_TRAINING_USERNAME ?? 'demo.training.mweokbew','TRAINING_STAFF');
await accountant.login('demo.accountant','ACCOUNTANT');
const shifts = ok(await accountant.request('/accounting/shifts'));
if (!shifts.current.id) ok(await accountant.post('/accounting/shifts', {id:randomUUID(),openingCash:0}));
async function put(client,path,data) {
  const csrf = await client.request('/auth/csrf');
  return client.request(path,{method:'PUT',headers:{[csrf.body.headerName]:csrf.body.token,'Content-Type':'application/json'},body:JSON.stringify(data)});
}
const basePath = suffix => '/training/completion/classes/' + tag + suffix;
const preview = async suffix => ok(await training.request(basePath(suffix)));
async function enroll(client,suffix) {
  const checkout=ok(await client.post('/enrollments/checkout',{classId:tag+suffix}));
  return ok(await client.post('/enrollments/checkout/'+checkout.orderCode+'/demo-payment',{}));
}
await enroll(student,'a');
await enroll(other,'a');
await enroll(student,'c');
let a=await preview('a');
const winner=a.students.find(item=>item.studentId==='demo-student').enrollmentId;
const loser=a.students.find(item=>item.studentId==='demo-racer').enrollmentId;
assert.equal((await training.post(basePath('a')+'/finalize',{})).status,409);
assert.equal((await student.post(basePath('a')+'/finalize',{})).status,403);
const policy={passScore:5,minimumAttendance:80,weights:{[tag+'a-x1']:1,[tag+'a-x2']:3}};
assert.equal((await put(training,basePath('a')+'/policy',policy)).status,204);
async function grade(suffix,enrollment,index,score) {
  assert.equal((await put(teacher,'/academics/teacher/classes/'+tag+suffix+'/exams/'+tag+suffix+'-x'+index+'/grades/'+enrollment,{score})).status,204);
}
async function attendance(suffix,enrollment,index,status) {
  assert.equal((await put(teacher,'/academics/teacher/classes/'+tag+suffix+'/lessons/'+tag+suffix+'-l'+index+'/attendance/'+enrollment,{status})).status,204);
}
await grade('a',winner,1,4);
await grade('a',loser,1,9);
assert.equal((await training.post(basePath('a')+'/finalize',{})).status,409);
await grade('a',winner,2,8);
await grade('a',loser,2,9);
assert.equal((await training.post(basePath('a')+'/finalize',{})).status,409);
for (const index of [1,2]) {
  await attendance('a',winner,index,'PRESENT');
  await attendance('a',loser,index,'ABSENT');
}
a=await preview('a');
assert.equal(Number(a.students.find(item=>item.enrollmentId===winner).finalScore),7);
assert.equal(a.students.find(item=>item.enrollmentId===loser).decision,'FAIL');
const trainingToken=await training.request('/auth/csrf');
const closed=await Promise.all([1,2].map(()=>training.post(basePath('a')+'/finalize',{},false,trainingToken)));
assert.ok(closed.every(item=>item.status===204),JSON.stringify(closed));
assert.equal((await put(teacher,'/academics/teacher/classes/'+tag+'a/exams/'+tag+'a-x1/grades/'+winner,{score:10})).status,409);
assert.equal((await put(training,basePath('a')+'/policy',policy)).status,409);
assert.equal((await training.post(basePath('a')+'/certificates/'+loser,{})).status,409);
let certificates=ok(await student.request('/academics/student/certificates'));
const certificate=certificates.find(item=>item.courseName.includes(tag) && item.courseName.includes(' a '));
assert.ok(certificate);
assert.equal(Number(certificate.finalScore),7);
assert.equal(ok(await other.request('/academics/student/certificates')).some(item=>item.courseName.includes(tag)),false);
assert.equal((await other.request('/academics/student/certificates/'+certificate.id+'/pdf')).status,404);
const file=ok(await student.request('/academics/student/certificates/'+certificate.id+'/pdf'));
assert.equal(new TextDecoder().decode(file.slice(0,5)),'%PDF-');
await writeFile('target/integration-certificate.pdf',file);
const verification=await fetch(base+'/api/certificates/verify/'+certificate.verificationToken);
assert.equal(verification.status,200);
assert.ok((await verification.text()).includes(certificate.code));
assert.equal((await fetch(base+'/api/certificates/verify/not-a-certificate')).status,404);
async function fullAcademic(suffix,score) {
  const view=await preview(suffix);
  const enrollment=view.students.find(item=>item.studentId==='demo-student').enrollmentId;
  for (const index of [1,2]) {
    await grade(suffix,enrollment,index,score);
    await attendance(suffix,enrollment,index,'PRESENT');
  }
  return enrollment;
}
const unpaid=await fullAcademic('b',9);
assert.equal((await training.post(basePath('b')+'/finalize',{})).status,204);
assert.equal((await preview('b')).students[0].decision,'FINANCIAL_HOLD');
assert.equal((await training.post(basePath('b')+'/certificates/'+unpaid,{})).status,409);
assert.equal(ok(await student.request('/academics/student/certificates')).some(item=>item.courseName.includes(tag)&&item.courseName.includes(' b ')),false);
ok(await accountant.post('/invoices/'+tag+'b-i/payments',{amount:10000,method:'CASH',referenceCode:'CERT-DEMO-'+tag}));
assert.equal((await training.post(basePath('b')+'/certificates/'+unpaid,{})).status,204);
assert.equal((await preview('b')).students[0].decision,'PASS');
assert.ok(ok(await student.request('/academics/student/certificates')).some(item=>item.courseName.includes(tag)&&item.courseName.includes(' b ')));
await fullAcademic('c',8);
assert.equal((await training.post(basePath('c')+'/finalize',{})).status,204);
assert.equal(ok(await student.request('/academics/student/certificates')).some(item=>item.courseName.includes(tag)&&item.courseName.includes(' c ')),false);
await enroll(student,'d');
const lastModule=await fullAcademic('d',6);
assert.equal((await training.post(basePath('d')+'/finalize',{})).status,204);
assert.equal((await training.post(basePath('d')+'/certificates/'+lastModule,{})).status,204);
certificates=ok(await student.request('/academics/student/certificates'));
const courseCertificates=certificates.filter(item=>item.courseName.includes(tag)&&item.courseName.includes(' c '));
assert.equal(courseCertificates.length,1);
assert.equal(Number(courseCertificates[0].finalScore),7);
console.log('PASS: weighted score, missing grades/attendance, fail attendance, financial hold/payment, all modules, immutable results, duplicate finalization, PDF ownership and public QR verification');
console.log(JSON.stringify({tag,certificate:certificate.code,courseCertificates:courseCertificates.map(item=>item.code)},null,2));
