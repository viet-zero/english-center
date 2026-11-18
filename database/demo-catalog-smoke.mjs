
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

const training=session(), admin=session(), student=session(), other=session();
await training.login(process.env.DEMO_TRAINING_USERNAME??'demo.training.mweokbew','TRAINING_STAFF');
await admin.login('demo.admin','ADMIN');
await student.login('demo.student','STUDENT');
await other.login('demo.student2','STUDENT');
async function put(client,path,data) {
  const token=await client.request('/auth/csrf');
  return client.request(path,{method:'PUT',headers:{[token.body.headerName]:token.body.token,'Content-Type':'application/json'},body:JSON.stringify(data)});
}
function created(result) {
  assert.equal(result.status,201,JSON.stringify(result));
  return result.body;
}
const tag='catalog-'+Date.now().toString(36);
const course=created(await training.post('/training/courses',{name:tag,description:'Demo training catalog',status:'ACTIVE'}));
assert.equal((await training.post('/training/courses',{name:tag,description:'Duplicate',status:'ACTIVE'})).status,409);
const courseData={name:tag+' edited',description:'Updated by training',status:'ACTIVE'};
ok(await put(training,'/training/courses/'+course.id,courseData));
const moduleData={name:'Demo module',tuition:12000,durationHours:36,status:'ACTIVE'};
const module=created(await training.post('/training/courses/'+course.id+'/modules',{sequenceNumber:1,details:{...moduleData,tuition:10000}}));
ok(await put(training,'/training/modules/'+module.id,moduleData));
assert.equal((await training.post('/training/courses/'+course.id+'/modules',{sequenceNumber:1,details:moduleData})).status,409);
assert.equal((await put(training,'/training/modules/'+module.id,{...moduleData,tuition:-1})).status,400);
const date=new Date();
const nextYear=date.getFullYear()+1;
const classroom=created(await training.post('/training/modules/'+module.id+'/classes',{
  teacherId:'demo-teacher',classCode:tag,startDate:nextYear+'-08-01',endDate:nextYear+'-08-31',
  details:{maxStudents:3,status:'OPEN'},
}));
assert.equal((await student.post('/training/courses',{name:'Forbidden',status:'ACTIVE'})).status,403);
assert.equal((await put(student,'/training/classes/'+classroom.id,{maxStudents:9,status:'OPEN'})).status,403);
assert.ok(ok(await student.request('/catalog/courses')).some(item=>item.id===course.id&&item.name===courseData.name));
const modules=ok(await student.request('/catalog/courses/'+course.id+'/modules'));
assert.equal(Number(modules.find(item=>item.id===module.id).tuition),12000);
const invoiceIds=[];
for (const client of [student,other]) {
  const order=ok(await client.post('/enrollments/checkout',{classId:classroom.id}));
  const paid=ok(await client.post('/enrollments/checkout/'+order.orderCode+'/demo-payment',{}));
  assert.equal(paid.status,'ENROLLED');
  invoiceIds.push(paid.invoiceId);
}
assert.equal((await put(training,'/training/classes/'+classroom.id,{maxStudents:1,status:'OPEN'})).status,409);
ok(await put(training,'/training/classes/'+classroom.id,{maxStudents:4,status:'OPEN'}));
let openClasses=ok(await student.request('/catalog/modules/'+module.id+'/classes'));
assert.equal(openClasses.find(item=>item.id===classroom.id).remainingSeats,2);
ok(await put(training,'/training/modules/'+module.id,{...moduleData,tuition:15000}));
assert.equal(Number(ok(await student.request('/invoices/'+invoiceIds[0])).totalDue),12000);
ok(await put(training,'/training/classes/'+classroom.id,{maxStudents:4,status:'CLOSED'}));
assert.equal(ok(await student.request('/catalog/modules/'+module.id+'/classes')).some(item=>item.id===classroom.id),false);
assert.ok(ok(await student.request('/academics/student/classes')).some(item=>item.id===classroom.id));
ok(await put(training,'/training/classes/'+classroom.id,{maxStudents:4,status:'OPEN'}));
ok(await put(training,'/training/modules/'+module.id,{...moduleData,status:'INACTIVE'}));
assert.equal(ok(await student.request('/catalog/courses/'+course.id+'/modules')).some(item=>item.id===module.id),false);
ok(await put(training,'/training/modules/'+module.id,moduleData));
ok(await put(admin,'/training/courses/'+course.id,{...courseData,status:'INACTIVE'}));
assert.equal(ok(await student.request('/catalog/courses')).some(item=>item.id===course.id),false);
assert.ok(ok(await training.request('/training/courses')).some(item=>item.id===course.id));
const finalized=ok(await training.request('/training/completion/classes')).find(item=>item.finalizedAt);
if (finalized) assert.equal((await put(training,'/training/classes/'+finalized.id,{maxStudents:999,status:'OPEN'})).status,409);
console.log('PASS: training/admin catalog management, duplicate and input checks, permissions, capacity, archived visibility, original tuition and finalized-class protection');
console.log(JSON.stringify({courseId:course.id,moduleId:module.id,classId:classroom.id},null,2));
