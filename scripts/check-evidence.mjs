// Local consistency checks, not a substitute for executing the application/tests.
import assert from 'node:assert/strict';
import {existsSync, readFileSync, readdirSync} from 'node:fs';
import {dirname, resolve} from 'node:path';
import {execFileSync} from 'node:child_process';
for (const path of ['mvnw', 'entrypoint.sh']) {
  assert(execFileSync('git', ['ls-files', '--stage', '--', path], {encoding: 'utf8'}).startsWith('100755 '),
    `${path} must retain its executable bit for Linux checkouts`);
}
const json = path => JSON.parse(readFileSync(path, 'utf8'));
const metadata = json('docs/evidence/measurement.json');
execFileSync('git', ['cat-file', '-e', `${metadata.sourceCommit}^{commit}`]);
const runs = [1, 2, 3, 4, 5].map(i => json(`docs/evidence/demo-${i}.json`));
assert.equal(metadata.repetitions, runs.length);
assert.equal(new Set(runs.map(r => r.paymentId)).size, 5);
assert.deepEqual(metadata.workflowMs, runs.map(r => r.workflowMs));
const sorted = runs.map(r => r.workflowMs).sort((a, b) => a - b);
assert.equal(metadata.medianWorkflowMs, sorted[2]);
assert.equal(metadata.minWorkflowMs, sorted[0]);
assert.equal(metadata.maxWorkflowMs, sorted[4]);
const impact = readFileSync('docs/impact-and-resume-evidence.md', 'utf8');
runs.forEach((run, index) => {
  assert.equal(run.finalStatus, 'REFUNDED');
  assert.deepEqual(run.finalBalances, ['100.00', '0.00']);
  assert.equal(run.openExceptionsForPayment, 0);
  assert.equal(run.requests, run.samples.length);
  const row = impact.split('\n').find(line => line.startsWith(`| ${index + 1} |`));
  assert(row && +row.split('|')[2].trim() === run.workflowMs, 'Documented run must match raw sample');
});
for (const file of ['demo-database-checks.txt', 'demo-after-restart.txt']) {
  const lines = readFileSync(`docs/evidence/${file}`, 'utf8').trim().split(/\r?\n/);
  assert.equal(lines.length, 5);
  for (const run of runs) assert(lines.some(line => line.startsWith(`PASS ${run.paymentId} `)));
}
assert.equal(json('docs/evidence/console-interaction.json').result, 'passed');
function files(directory) {
  return readdirSync(directory, {withFileTypes: true}).flatMap(entry =>
    entry.isDirectory() ? files(`${directory}/${entry.name}`) : [`${directory}/${entry.name}`]);
}
let links = 0;
for (const file of [...files('docs').filter(f => f.endsWith('.md')), 'README.md', 'DECISIONS.md']) {
  for (const match of readFileSync(file, 'utf8').matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const target = match[1].split('#')[0];
    if (!target || /^(https?:|mailto:)/.test(target)) continue;
    assert(existsSync(resolve(dirname(file), target)), `Broken local link in ${file}: ${target}`);
    links++;
  }
}
for (const file of files('docs/evidence')) {
  assert(!/"(?:accessToken|refreshToken|password|Authorization)"\s*:/i.test(readFileSync(file, 'utf8')),
    `Credential-bearing evidence: ${file}`);
}
console.log(JSON.stringify({measurementSamples: runs.length, medianWorkflowMs: sorted[2],
  databaseChecksBeforeAndAfterRestart: 'matched all payment IDs', localLinks: links,
  credentialFieldsInEvidence: 0, result: 'passed'}, null, 2));
