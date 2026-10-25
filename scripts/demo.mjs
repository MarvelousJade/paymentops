// Node 22+, no npm dependencies. Synthetic data only; use a disposable local database.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {performance} from 'node:perf_hooks';

const base = process.env.BASE_URL || 'http://localhost:8080';
assert(['localhost', '127.0.0.1', '[::1]'].includes(new URL(base).hostname),
  'This fixture creates users and money: use a loopback URL only');
const started = performance.now();
const requests = [];
async function api(method, path, token, body, key, expected = 200) {
  const begin = performance.now();
  const response = await fetch(base + path, {
    method, signal: AbortSignal.timeout(10000),
    headers: {'Content-Type': 'application/json', ...(token ? {Authorization: `Bearer ${token}`} : {}),
      ...(key ? {'Idempotency-Key': key} : {})},
    ...(body === undefined ? {} : {body: JSON.stringify(body)})
  });
  requests.push({method, path, status: response.status, ms: +(performance.now() - begin).toFixed(3)});
  const text = await response.text();
  assert.equal(response.status, expected, `${method} ${path}: ${text}`);
  return text ? JSON.parse(text) : null;
}
const post = (path, token, body, key, status = 202) => api('POST', path, token, body, key, status);
const get = (path, token) => api('GET', path, token);
async function user(label) {
  return (await post('/auth/register', null, {email: `${label}-${randomUUID()}@example.test`,
    password: randomUUID(), fullName: `Synthetic ${label}`}, null, 201)).accessToken;
}
async function account(token, amount) {
  const result = await post('/accounts', token, {type: 'CHEQUING'}, null, 201);
  if (amount) await post(`/accounts/${result.id}/deposits`, token, {amount}, null, 201);
  return result.id;
}
async function wait(id, token, status) {
  const deadline = performance.now() + 15000;
  while (performance.now() < deadline) {
    const payment = await get(`/api/v1/payments/${id}`, token);
    if (payment.status === status) return payment;
    assert(!['FAILED', 'REJECTED', 'REVIEW_REQUIRED'].includes(payment.status), JSON.stringify(payment));
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error(`Timed out waiting for ${id}: ${status}`);
}

assert(process.env.OPERATOR_EMAIL && process.env.OPERATOR_PASSWORD,
  'Set OPERATOR_EMAIL and OPERATOR_PASSWORD for a pre-provisioned local operator; see docs/walkthrough.md');
const operator = (await post('/auth/login', null, {email: process.env.OPERATOR_EMAIL,
  password: process.env.OPERATOR_PASSWORD}, null, 200)).accessToken;
const merchant = await user('payer');
const payee = await user('payee');
const source = await account(merchant, '100.00');
const destination = await account(payee);
const workflowStarted = performance.now();
const key = randomUUID();
const payload = {sourceAccountId: source, destinationAccountId: destination,
  amount: '30.00', currency: 'CAD', paymentToken: 'tok_synthetic_demo'};
const payment = await post('/api/v1/payments', merchant, payload, key);
assert.equal((await post('/api/v1/payments', merchant, payload, key)).id, payment.id);
await post('/api/v1/payments', merchant, {...payload, amount: '31.00'}, key, 409);
await api('GET', `/api/v1/payments/${payment.id}`, payee, undefined, null, 404);
await wait(payment.id, merchant, 'AUTHORIZED');
await post(`/api/v1/payments/${payment.id}/capture`, merchant, undefined, 'capture-demo');
await wait(payment.id, merchant, 'SETTLED');
assert.equal((await get(`/accounts/${source}`, merchant)).balance, '70.00');
assert.equal((await get(`/accounts/${destination}`, payee)).balance, '30.00');
await post('/api/v1/operations/reconciliation/run', operator, undefined, null, 200);
const partialKey = randomUUID();
const partial = await post(`/api/v1/payments/${payment.id}/refunds`, merchant, {amount: '10.00'}, partialKey);
assert.equal((await post(`/api/v1/payments/${payment.id}/refunds`, merchant,
  {amount: '10.00'}, partialKey)).id, partial.id);
await post(`/api/v1/payments/${payment.id}/refunds`, merchant, {amount: '11.00'}, partialKey, 409);
await wait(payment.id, merchant, 'PARTIALLY_REFUNDED');
await post(`/api/v1/payments/${payment.id}/refunds`, merchant, {amount: '21.00'}, randomUUID(), 409);
await post(`/api/v1/payments/${payment.id}/refunds`, merchant, {amount: '20.00'}, randomUUID());
await wait(payment.id, merchant, 'REFUNDED');
await post(`/api/v1/payments/${payment.id}/capture`, merchant, undefined, 'capture-demo');
assert.equal((await get(`/accounts/${source}`, merchant)).balance, '100.00');
assert.equal((await get(`/accounts/${destination}`, payee)).balance, '0.00');
const workflowMs = +(performance.now() - workflowStarted).toFixed(3);
const reconciled = await post('/api/v1/operations/reconciliation/run', operator, undefined, null, 200);
const exceptions = await get('/api/v1/operations/reconciliation/exceptions', operator);
assert.equal(exceptions.filter(e => e.paymentId === payment.id && e.status !== 'RESOLVED').length, 0);
const timeline = await get(`/api/v1/payments/${payment.id}/timeline`, merchant);
for (const type of ['payment.approved.v1', 'payment.capture-posted.v1', 'payment.settled.v1', 'payment.refunded.v1']) {
  assert(timeline.some(e => e.eventType === type), `Missing timeline event: ${type}`);
}
assert.equal(timeline.filter(e => e.eventType === 'payment.capture-posted.v1').length, 1);
assert.equal(timeline.filter(e => e.eventType === 'payment.refunded.v1').length, 2);
const deliveries = await get(`/api/v1/payments/${payment.id}/deliveries`, merchant);
console.log(JSON.stringify({kind: 'synthetic-local-http-demo', date: new Date().toISOString(),
  paymentId: payment.id, correlationId: payment.correlationId, source, destination,
  finalStatus: 'REFUNDED', finalBalances: ['100.00', '0.00'], workflowMs,
  totalMs: +(performance.now() - started).toFixed(3), requests: requests.length,
  timelineEntries: timeline.length, webhookRecords: deliveries.length,
  reconciledPayments: reconciled.paymentsChecked, openExceptionsForPayment: 0,
  node: process.version, samples: requests}, null, 2));
