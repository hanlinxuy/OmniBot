import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { plan, summarize } from './scan.mjs';

const inventory = { items: [{ source_id: 'a', status: 'ready', goal: 'Open calculator' }, { source_id: 'b', status: 'unverified', goal: 'Other' }] };
test('serial plan and no-ready blocking', () => {
  assert.equal(plan(inventory).execution, 'serial');
  assert.equal(plan({ items: [] }).status, 'blocked');
  assert.throws(() => plan({ items: [inventory.items[0], inventory.items[0]] }), /duplicate/);
});
test('aggregate distinct attempts without inventing completion', () => {
  const p = plan(inventory);
  const r = { scenario_id: 'a', attempt_id: '1', status: 'independent_success', evidence: ['screen.png'] };
  const summary = summarize(p, [r, r]);
  assert.equal(summary.status, 'incomplete');
  assert.equal(summary.scenarios[0].attempts.length, 1);
  assert.equal(summarize(p, [{ ...r, status: 'assisted', evidence: [] }]).scenarios[0].attempts[0].status, 'assisted');
  assert.throws(() => summarize(p, [r, { ...r, status: 'failed' }]), /conflicting/);
  assert.throws(() => summarize(p, [{ ...r, scenario_id: 'b' }]), /blocked/);
});
test('invalid scenario is rejected before prompt interpolation or adb', () => {
  const dir = mkdtempSync(join(tmpdir(), 'oob-scan-'));
  try {
    const file = join(dir, 'scenario.json');
    writeFileSync(file, JSON.stringify({ prompt: 123 }));
    const run = spawnSync(process.execPath, [resolve('scripts/send-agent-test-message.mjs'), 'emulator-1', 'TEST_MARKER'], { env: { ...process.env, OOB_USER_SCENARIO_FILE: file }, encoding: 'utf8' });
    assert.notEqual(run.status, 0);
    assert.match(run.stderr, /Invalid bounded user scenario/);
    assert.doesNotMatch(run.stderr, /replaceAll is not a function/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('actual inventory plans all 280 items through CLI', () => {
  const run = spawnSync(process.execPath, [resolve('scripts/android-scenario-scan/scan.mjs'), '--inventory', resolve('docs/testing/android-scenario-inventory.json'), '--plan'], { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
  assert.equal(run.status, 0, run.stderr);
  const output = JSON.parse(run.stdout);
  assert.equal(output.scenarios.length, 280);
  assert.equal(output.status, output.scenarios.some(s => s.status === 'ready') ? 'planned' : 'blocked');
  assert.equal(new Set(output.scenarios.map(s => s.source_id)).size, 280);
});
