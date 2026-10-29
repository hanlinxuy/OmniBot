#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import assert from 'node:assert/strict';

// Planning and evidence aggregation only. No device or Agent calls occur here.
export function plan(inventory) {
  assert(Array.isArray(inventory.items), 'inventory.items must be an array');
  const ids = new Set();
  const scenarios = inventory.items.map(scenario => {
    assert(typeof scenario.source_id === 'string' && scenario.source_id.trim(), 'source_id required');
    assert(!ids.has(scenario.source_id), `duplicate scenario id: ${scenario.source_id}`);
    ids.add(scenario.source_id);
    assert(typeof scenario.status === 'string' && scenario.status.trim(), `status required: ${scenario.source_id}`);
    assert(typeof scenario.goal === 'string' && scenario.goal.trim(), `goal required: ${scenario.source_id}`);
    return { ...scenario };
  });
  const ready = scenarios.filter(s => s.status === 'ready');
  return { status: ready.length ? 'planned' : 'blocked', reason: ready.length ? null : 'No ready scenarios', execution: 'serial', scenarios };
}

export function summarize(scanPlan, results) {
  assert(Array.isArray(results), 'results must be an array');
  const known = new Map(scanPlan.scenarios.map(s => [s.source_id, s]));
  const attempts = new Map();
  for (const result of results) {
    assert(known.has(result.scenario_id), `unknown scenario: ${result.scenario_id}`);
    assert(known.get(result.scenario_id).status === 'ready', `result for blocked scenario: ${result.scenario_id}`);
    assert(typeof result.attempt_id === 'string' && result.attempt_id.trim(), 'attempt_id required');
    assert(['independent_success', 'assisted', 'failed', 'blocked', 'incomplete'].includes(result.status), 'invalid result status');
    assert(Array.isArray(result.evidence) && result.evidence.every(e => typeof e === 'string' && e.trim()), 'evidence must be an array of paths');
    const key = JSON.stringify([result.scenario_id, result.attempt_id]);
    if (attempts.has(key)) assert.deepEqual(attempts.get(key), result, `conflicting duplicate attempt: ${key}`);
    else attempts.set(key, result);
  }
  const rows = scanPlan.scenarios.map(s => ({ scenario_id: s.source_id, plan_status: s.status,
    attempts: [...attempts.values()].filter(r => r.scenario_id === s.source_id) }));
  return { status: scanPlan.status === 'blocked' ? 'blocked' : 'incomplete', scenarios: rows };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = process.argv.slice(2);
  assert(args.length === 3 || args.length === 5, 'Usage: node scan.mjs --inventory FILE --plan [--results FILE]');
  assert(args[0] === '--inventory' && args[2] === '--plan', 'Expected --inventory FILE --plan');
  const scanPlan = plan(JSON.parse(readFileSync(args[1], 'utf8')));
  if (args.length === 5) {
    assert(args[3] === '--results', 'Expected --results FILE');
    console.log(JSON.stringify(summarize(scanPlan, JSON.parse(readFileSync(args[4], 'utf8'))), null, 2));
  } else console.log(JSON.stringify(scanPlan, null, 2));
}
