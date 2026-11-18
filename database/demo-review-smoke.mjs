
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

const accountant = session();
const student = session();
await accountant.login('demo.accountant', 'ACCOUNTANT');
await student.login('demo.student2', 'STUDENT');
const transferRef = process.env.DEMO_TRANSFER_REFERENCE;
const refundRef = process.env.DEMO_REFUND_REFERENCE;
const targetId = process.env.DEMO_TARGET_CLASS_ID;
assert.ok(transferRef && refundRef && targetId, 'Set demo resolution references and target class');
const transferPath = '/payments/reviews/' + transferRef + '/resolve';
const refundPath = '/payments/reviews/' + refundRef + '/resolve';
const transfer = { action: 'TRANSFER', targetClassId: targetId, note: 'Kiểm tra gửi lặp' };
const refund = { action: 'REFUND_DEMO', note: 'Kiểm tra gửi lặp' };
assert.equal((await student.post(transferPath, transfer)).status, 403);
assert.equal((await student.request('/payments/reviews/' + transferRef + '/classes')).status, 403);
const token = await accountant.request('/auth/csrf');
const tokens = [token, token];
const retries = await Promise.all(tokens.map(token => accountant.post(transferPath, transfer, false, token)));
assert.ok(retries.every(result => result.status === 204), JSON.stringify(retries));
assert.equal((await accountant.post(refundPath, refund)).status, 204);
assert.equal((await accountant.post(transferPath, refund)).status, 409);
assert.equal((await accountant.post(refundPath, transfer)).status, 409);
const reviews = ok(await accountant.request('/payments/reviews'));
const transferred = reviews.find(item => item.reference === transferRef);
const refunded = reviews.find(item => item.reference === refundRef);
assert.equal(transferred.resolution, 'TRANSFER');
assert.equal(transferred.targetClassId, targetId);
assert.equal(refunded.resolution, 'REFUND_DEMO');
assert.equal(refunded.resolvedInvoiceId, null);
const invoice = ok(await student.request('/invoices/' + transferred.resolvedInvoiceId));
assert.equal(invoice.remainingAmount, 0);
assert.equal(invoice.status, 'PAID');
console.log('PASS: resolution retries, concurrent retries, conflicting decisions, accountant permissions and paid invoice');
