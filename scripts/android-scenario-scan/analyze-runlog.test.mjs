import test from 'node:test';
import assert from 'node:assert/strict';
import { analyze } from './analyze-runlog.mjs';

const step = (index, before, after, tool = 'tap', args = { x: 1 }, success = true) =>
  ({ step_index: index, before_state_id: before, after_state_id: after, action: { tool, args }, result: { success } });

test('detects review signals without exposing action arguments or state contents', () => {
  const run = { run_id: 'r', status: 'failed', success: false, steps: [
    step(0, 'a', 'a', 'type', { text: 'private text' }),
    step(1, 'a', 'b', 'type', { text: 'private text' }),
    step(2, 'b', 'c', 'tap'),
  ] };
  const result = analyze(run, { targetPackage: 'target', states: { a: { package_name: 'other', xml: 'private xml' }, b: { package_name: 'target' } } });
  assert.deepEqual(result.findings.map(f => f.type), ['repeated_action', 'unchanged_state_id', 'actions_succeeded_run_failed', 'repeated_outside_target']);
  assert.deepEqual(result.findings.at(-1).steps, [0, 1]);
  assert.doesNotMatch(JSON.stringify(result), /private text|private xml|"other"/);
});
test('distinct actions, state ids, unknown packages and failed action do not trigger signals', () => {
  const run = { status: 'failed', steps: [step(1, 'a', 'b'), step(2, 'b', 'c', 'swipe', {}, false)] };
  assert.deepEqual(analyze(run, { targetPackage: 'target', states: { a: { package_name: 'other' } } }).findings, []);
});
test('empty run does not imply action success and a successful run does not signal failure', () => {
  assert.deepEqual(analyze({ status: 'failed', steps: [] }).findings, []);
  assert.deepEqual(analyze({ status: 'completed', success: true, steps: [step(0, 'a', 'b')] }).findings, []);
});
