// Programmatic browser interaction through a dedicated local Chrome DevTools port.
// Start a disposable headless Chrome on port 9223; see docs/walkthrough.md.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
const fixture = JSON.parse(readFileSync(process.argv[2] || 'docs/evidence/demo-1.json', 'utf8'));
const tabs = await (await fetch('http://127.0.0.1:9223/json/list')).json();
const tab = tabs.find(t => t.type === 'page' && t.url === 'http://localhost:8080/operations.html');
assert(tab, 'Open the local operations page in the dedicated debugging browser');
const socket = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((resolve, reject) => {socket.onopen = resolve; socket.onerror = reject;});
let next = 0;
const waiting = new Map();
socket.onmessage = event => {
  const message = JSON.parse(event.data);
  if (waiting.has(message.id)) {
    const {resolve, reject, timer} = waiting.get(message.id);
    clearTimeout(timer); waiting.delete(message.id);
    if (message.error || message.result?.exceptionDetails) reject(new Error('Browser evaluation failed'));
    else resolve(message.result?.result?.value);
  }
};
function evaluate(expression) {
  return new Promise((resolve, reject) => {
    const id = ++next;
    const timer = setTimeout(() => {waiting.delete(id); reject(new Error('Browser command timed out'));}, 10000);
    waiting.set(id, {resolve, reject, timer});
    socket.send(JSON.stringify({id, method: 'Runtime.evaluate', params: {expression, awaitPromise: true, returnByValue: true}}));
  });
}
async function text(id, predicate) {
  for (let i = 0; i < 100; i++) {
    const value = await evaluate(`document.getElementById(${JSON.stringify(id)}).textContent`);
    if (predicate(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error(`Output did not reach expected state: ${id}`);
}
const click = index => evaluate(`document.querySelectorAll('button')[${index}].click()`);
const set = (id, value) => evaluate(`document.getElementById(${JSON.stringify(id)}).value=${JSON.stringify(value)}`);
try {
  await set('token', 'invalid-demo-token'); await click(0);
  await text('health', t => t.includes('401'));
  const response = await fetch('http://localhost:8080/auth/login', {method: 'POST',
    headers: {'Content-Type': 'application/json'}, body: JSON.stringify({email: process.env.OPERATOR_EMAIL,
      password: process.env.OPERATOR_PASSWORD})});
  assert.equal(response.status, 200);
  await set('token', (await response.json()).accessToken); await click(0);
  const health = JSON.parse(await text('health', t => t.includes('pendingOutboxEvents')));
  assert.equal(typeof health.pendingOutboxEvents, 'number');
  await set('correlation', fixture.correlationId);
  await click(1);
  const result = await text('payments', t => t.includes(fixture.paymentId));
  assert.equal(JSON.parse(result)[0].payment.id, fixture.paymentId);
  await click(1); await text('payments', t => t.includes(fixture.paymentId));
  await click(2); await text('exceptions', t => t.trim() === '[]');
  await click(3); await text('exceptions', t => t.includes('paymentsChecked'));
  await click(2); await text('exceptions', t => t.trim() === '[]');
  await set('deadLetterId', randomUUID()); await click(4);
  await text('replayResult', t => t.includes('404'));
  assert.equal(await evaluate("document.getElementById('correlation').value"), fixture.correlationId);
  console.log(JSON.stringify({kind: 'programmatic-chrome-interaction', date: new Date().toISOString(),
    browser: await evaluate('navigator.userAgent'), checks: ['invalid token displays 401', 'operator connects',
      'correlation search returns saved payment', 'repeat search', 'empty exceptions',
      'run reconciliation then refresh', 'missing dead letter displays 404', 'correlation input preserved'],
    paymentId: fixture.paymentId, result: 'passed'}, null, 2));
} finally {
  await set('token', ''); // Do not leave credentials in the dedicated browser.
  socket.close();
}
