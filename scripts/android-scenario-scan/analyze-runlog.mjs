#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import assert from 'node:assert/strict';

// Findings are signals for review, not determinations of defect or task success.
export function analyze(run, options = {}) {
  assert(Array.isArray(run.steps), 'steps must be an array');
  const findings = [];
  const steps = run.steps;
  for (let i = 1; i < steps.length; i++) {
    const previous = steps[i - 1];
    const current = steps[i];
    if (previous.action && current.action && previous.action.tool === current.action.tool &&
        JSON.stringify(previous.action.args) === JSON.stringify(current.action.args)) {
      findings.push({ type: 'repeated_action', steps: [previous.step_index, current.step_index] });
    }
  }
  for (const step of steps) {
    if (step.before_state_id && step.after_state_id && step.before_state_id === step.after_state_id) {
      findings.push({ type: 'unchanged_state_id', steps: [step.step_index] });
    }
  }
  const actions = steps.filter(step => step.action?.tool);
  if ((run.success === false || ['failed', 'failure', 'error'].includes(run.status)) &&
      actions.length && actions.every(step => step.result?.success === true)) {
    findings.push({ type: 'actions_succeeded_run_failed', steps: actions.map(step => step.step_index),
      interpretation: 'Action success does not establish task success.' });
  }
  if (options.targetPackage && options.states) {
    let outside = [];
    for (const step of steps) {
      const packageName = options.states[step.before_state_id]?.package_name;
      if (packageName && packageName !== options.targetPackage) outside.push(step.step_index);
      else {
        if (outside.length >= 2) findings.push({ type: 'repeated_outside_target', steps: outside });
        outside = [];
      }
    }
    if (outside.length >= 2) findings.push({ type: 'repeated_outside_target', steps: outside });
  }
  return { run_id: run.run_id ?? null, findings };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = process.argv.slice(2);
  assert(args.length === 1 || args.length === 5, 'Usage: node analyze-runlog.mjs RUN.json [--states STATES.json --target-package PACKAGE]');
  const run = JSON.parse(readFileSync(args[0], 'utf8'));
  let options = {};
  if (args.length === 5) {
    assert(args[1] === '--states' && args[3] === '--target-package', 'Expected --states FILE --target-package PACKAGE');
    options = { states: JSON.parse(readFileSync(args[2], 'utf8')), targetPackage: args[4] };
  }
  console.log(JSON.stringify(analyze(run, options), null, 2));
}
