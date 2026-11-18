
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

const admin = session();
await admin.login('demo.admin', 'ADMIN');
const suffix = Date.now().toString(36);
const username = 'demo.account.' + suffix;
const created = await admin.post('/users/employees', {
  employee: { account: { fullName: 'Demo Account QA', username, password }, employeeCode: 'QA-' + suffix },
  roles: ['TEACHER'],
});
assert.equal(created.status, 201);
const accountId = created.body.accountId;
const user = session();
const second = session();
await user.login(username, 'TEACHER');
await second.login(username, 'TEACHER');
const records = () => admin.request('/admin/accounts').then(ok);
let record = (await records()).find(item => item.id === accountId);
const edit = item => ({
  fullName: item.fullName, phone: item.phone, email: item.email, identityNumber: item.identityNumber,
  roles: item.roles.split(','), status: item.status, version: item.version,
});
async function put(client, path, data) {
  const token = await client.request('/auth/csrf');
  return client.request(path, { method: 'PUT', headers: {
    [token.body.headerName]: token.body.token, 'Content-Type': 'application/json',
  }, body: JSON.stringify(data) });
}
const path = '/admin/accounts/' + accountId;
assert.equal((await user.request('/admin/accounts')).status, 403);
const initial = edit(record);
assert.equal((await put(admin, path, {...initial, fullName:'Demo Account Edited', roles:['ACCOUNTANT']})).status, 204);
assert.equal((await user.request('/auth/me')).status, 401);
assert.equal((await second.request('/auth/me')).status, 401);
assert.equal((await put(admin, path, initial)).status, 409);
await user.login(username, 'ACCOUNTANT');
record = (await records()).find(item => item.id === accountId);
assert.equal((await put(admin, path, {...edit(record), status:'LOCKED'})).status, 204);
assert.equal((await user.request('/auth/me')).status, 401);
const denied = session();
assert.equal((await denied.post('/auth/login', {username,password},true)).status, 401);
record = (await records()).find(item => item.id === accountId);
assert.equal((await put(admin,path,{...edit(record),status:'ACTIVE'})).status,204);
await user.login(username,'ACCOUNTANT');
await second.login(username,'ACCOUNTANT');
assert.equal((await put(user,'/auth/password',{currentPassword:'incorrect',newPassword:'DemoUpdated2026!',confirmPassword:'DemoUpdated2026!'})).status,400);
assert.equal((await put(user,'/auth/password',{currentPassword:password,newPassword:'DemoUpdated2026!',confirmPassword:'mismatch'})).status,400);
assert.equal((await put(user,'/auth/password',{currentPassword:password,newPassword:'DemoUpdated2026!',confirmPassword:'DemoUpdated2026!'})).status,204);
assert.equal((await user.request('/auth/me')).status,401);
assert.equal((await second.request('/auth/me')).status,401);
const relogin = session();
assert.equal((await relogin.post('/auth/login',{username,password},true)).status,401);
assert.equal((await relogin.post('/auth/login',{username,password:'DemoUpdated2026!'},true)).status,204);
assert.equal(ok(await relogin.request('/auth/me')).roles[0],'ACCOUNTANT');
console.log('PASS: list, edit, roles, stale edit, lock/unlock, invalid password, password change and all old sessions revoked');
console.log('UI test account: ' + username);

const all = await records();
const actor = all.find(item => item.username === 'demo.admin');
if (all.filter(item => item.roles.split(',').includes('ADMIN') && item.status === 'ACTIVE').length === 1) {
  assert.equal((await put(admin, '/admin/accounts/' + actor.id, {...edit(actor),status:'LOCKED'})).status,409);
}
record = (await records()).find(item => item.id === accountId);
assert.equal((await put(admin, path, {...edit(record),roles:['STUDENT']})).status,400);
assert.equal((await put(admin, path, {...edit(record),roles:[]})).status,400);
assert.equal((await put(admin, path, {...edit(record),phone:'123'})).status,400);
console.log('PASS: last active admin protection, profile-compatible roles and input validation');