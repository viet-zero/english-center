import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';

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

const dbPassword=process.env.DEMO_DB_PASSWORD;
assert.ok(dbPassword,'Set DEMO_DB_PASSWORD for expiry checks in english_center_demo');
const admin=session(), anonymous=session();
await admin.login('demo.admin','ADMIN');
const tag=Date.now().toString(36);
const username='demo.recovery.'+tag;
const email=username+'@example.test';
const created=await admin.post('/users/students',{account:{fullName:'Demo recovery QA',username,password,email},studentCode:'PW-'+tag});
assert.equal(created.status,201,JSON.stringify(created));
const accountId=created.body.accountId;
assert.match(accountId,/^[0-9a-f-]{36}$/);
async function account() {
  return ok(await admin.request('/admin/accounts')).find(item=>item.id===accountId);
}
const data=item=>({fullName:item.fullName,phone:item.phone,email:item.email,identityNumber:item.identityNumber,status:item.status,roles:item.roles.split(','),version:item.version});
async function put(client,path,body) {
  const token=await client.request('/auth/csrf');
  return client.request(path,{method:'PUT',headers:{[token.body.headerName]:token.body.token,'Content-Type':'application/json'},body:JSON.stringify(body)});
}
async function login(client,pass) { return client.post('/auth/login',{username,password:pass},true); }
async function bad() { return login(session(),'incorrect-demo-password'); }
const first=session(), second=session();
assert.equal((await login(first,password)).status,204);
assert.equal((await login(second,password)).status,204);
for(let i=0;i<2;i++) assert.equal((await bad()).status,401);
assert.equal((await account()).failedLoginCount,2);
assert.equal((await login(session(),password)).status,204);
assert.equal((await account()).failedLoginCount,0);
for(let i=0;i<4;i++) assert.equal((await bad()).status,401);
assert.equal((await account()).status,'ACTIVE');
assert.equal((await account()).failedLoginCount,4);
assert.equal((await bad()).status,401);
assert.equal((await account()).status,'LOCKED');
assert.equal((await account()).failedLoginCount,5);
assert.equal((await account()).lockReason,'FAILED_ATTEMPTS');
assert.equal((await login(session(),password)).status,401);
assert.equal((await first.request('/auth/me')).status,401);
const known=ok(await second.post('/auth/password/forgot',{email}));
const unknown=ok(await anonymous.post('/auth/password/forgot',{email:'absent-'+tag+'@example.test'}));
assert.deepEqual(known,unknown);
assert.deepEqual(Object.keys(known),['message']);
const outbox=async()=>ok(await admin.request('/admin/password-reset-emails')).filter(item=>item.recipient===email);
let inbox=await outbox();
assert.equal(inbox.length,1);
const tokenOf=item=>new URL(item.resetUrl).searchParams.get('reset_token');
const oldToken=tokenOf(inbox[0]);
ok(await anonymous.post('/auth/password/forgot',{email}));
inbox=await outbox();
const active=inbox.find(item=>Boolean(item.valid));
const token=tokenOf(active);
assert.notEqual(token,oldToken);
async function reset(client,token,next,confirm=next) {
  return client.post('/auth/password/reset',{token,newPassword:next,confirmPassword:confirm});
}
assert.equal((await reset(anonymous,oldToken,'RecoveredDemo2026!')).status,409);
assert.equal((await reset(anonymous,token,'weakpassword')).status,400);
assert.equal((await reset(anonymous,token,'RecoveredDemo2026!','mismatch')).status,400);
assert.equal((await reset(anonymous,token,password)).status,400);
assert.equal((await reset(anonymous,token,'RecoveredDemo2026!')).status,204);
assert.equal((await account()).status,'ACTIVE');
assert.equal((await account()).failedLoginCount,0);
assert.equal((await reset(anonymous,token,'AnotherDemo2026!')).status,409);
assert.equal((await login(session(),password)).status,401);
const activeSession=session(), otherSession=session();
assert.equal((await login(activeSession,'RecoveredDemo2026!')).status,204);
assert.equal((await login(otherSession,'RecoveredDemo2026!')).status,204);
ok(await anonymous.post('/auth/password/forgot',{email}));
const concurrentToken=tokenOf((await outbox()).find(item=>Boolean(item.valid)));
const choices=['ConcurrentDemo2026!','OtherConcurrent2026!'];
const resets=await Promise.all(choices.map(next=>reset(session(),concurrentToken,next)));
assert.deepEqual(resets.map(item=>item.status).sort(),[204,409]);
const currentPassword=choices[resets.findIndex(item=>item.status===204)];
assert.equal((await activeSession.request('/auth/me')).status,401);
assert.equal((await otherSession.request('/auth/me')).status,401);
assert.equal((await login(session(),currentPassword)).status,204);
ok(await anonymous.post('/auth/password/forgot',{email}));
const expiryToken=tokenOf((await outbox()).find(item=>Boolean(item.valid)));
const mysql=process.env.MYSQL_EXE??'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe';
function sql(query) {
  return execFileSync(mysql,['-N','-h','127.0.0.1','-u','root','english_center_demo','-e',query],
    {env:{...process.env,MYSQL_PWD:dbPassword},encoding:'utf8'}).trim();
}
assert.equal(sql("SELECT token_hash FROM password_reset WHERE account_id='"+accountId+"'"),createHash('sha256').update(expiryToken).digest('hex'));
assert.equal(Number(sql("SELECT TIMESTAMPDIFF(SECOND,created_at,expires_at) FROM password_reset WHERE account_id='"+accountId+"'")),300);
sql("UPDATE password_reset r JOIN tai_khoan a ON a.id=r.account_id SET r.expires_at=CURRENT_TIMESTAMP-INTERVAL 1 SECOND WHERE a.id='"+accountId+"' AND a.username LIKE 'demo.recovery.%'");
assert.equal((await reset(anonymous,expiryToken,'ExpiredDemo2026!')).status,409);
assert.equal((await login(session(),currentPassword)).status,204);
ok(await anonymous.post('/auth/password/forgot',{email}));
const adminLockToken=tokenOf((await outbox()).find(item=>Boolean(item.valid)));
let item=await account();
assert.equal((await put(admin,'/admin/accounts/'+accountId,{...data(item),status:'LOCKED'})).status,204);
assert.equal((await account()).lockReason,'ADMIN');
const count=(await outbox()).length;
ok(await anonymous.post('/auth/password/forgot',{email}));
assert.equal((await outbox()).length,count);
assert.equal((await reset(anonymous,adminLockToken,'BlockedDemo2026!')).status,409);
item=await account();
assert.equal((await put(admin,'/admin/accounts/'+accountId,{...data(item),status:'ACTIVE'})).status,204);
assert.equal((await account()).failedLoginCount,0);
for(let i=0;i<2;i++) assert.equal((await bad()).status,401);
item=await account();
assert.equal((await put(admin,'/admin/accounts/'+accountId,{...data(item),fullName:'Demo recovery edited'})).status,204);
assert.equal((await account()).failedLoginCount,2);
assert.equal((await login(session(),currentPassword)).status,204);
assert.equal((await account()).failedLoginCount,0);
const failures=await Promise.all([1,2,3,4,5].map(()=>bad()));
assert.ok(failures.every(item=>item.status===401));
assert.equal((await account()).status,'LOCKED');
assert.equal((await account()).failedLoginCount,5);
item=await account();
assert.equal((await put(admin,'/admin/accounts/'+accountId,{...data(item),status:'ACTIVE'})).status,204);
const student=session();
assert.equal((await login(student,currentPassword)).status,204);
assert.equal((await student.request('/admin/password-reset-emails')).status,403);
assert.equal((await session().request('/admin/password-reset-emails')).status,401);
const noCsrf=await fetch(base+'/api/auth/password/forgot',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email})});
assert.equal(noCsrf.status,403);
console.log('PASS: 5-failure lock, success reset, concurrent failures, hashed expiring tokens, token replacement, one-time concurrent reset, sessions, admin locks, email privacy and CSRF');
console.log('Demo account '+username+'; fake email '+email);
