# Screenshot-only decision-policy experiment

Status: experimental harness, not a completed phone benchmark. Four real StartLux runs
failed independent acceptance; see the dated visual-policy research report.

`run.py` executes against the dedicated `omnibot-lab` AVD / `emulator-5580`.
It sends screenshots, the user goal and recent actions to a StartLux-compatible
`/v1/systemone` endpoint. It never queries a UI tree, OCR text or application APIs
for the model. A package-only foreground guard refuses to capture or operate outside the selected test app. ADB is the transport for screenshot and coordinate input, not a
semantic observation source. Run on the emulator host to avoid SSH per action.

The decision model selects an action and two coordinate bins (24 per axis).
This is a constrained decision-policy prototype, not a model that freely generates
coordinates or text. Three questions may require three backend forwards; an HTTP
request is not an inference-step count. Generic coordinate bins can miss small
controls, and X/Y decisions can disagree about the target. Those are harness limits
to measure, not model failures to hide. ASCII text literals are supplied by the user
as task parameters; the policy chooses when to enter them. No computed answer or
page-specific action sequence belongs in the options.

```bash
python3 run.py --adb "$HOME/Library/Android/sdk/platform-tools/adb" \
  --endpoint http://127.0.0.1:8090 --goal 'Create a note with the supplied text' \
  --package org.fossify.notes --text 'visual-loop-owned-example' --out /path/to/unique-run
```

Only use an isolated test snapshot containing the authorized calculator, clock and
notes apps. Do not use a personal device or install bank/payment/WeChat/blocked apps
in this AVD. Run one controller at a time. The operator prepares the same starting
app state for both arms; no setup action counts as model execution. Disable OmniBot
accessibility during the pure-visual arm and restore the fixture snapshot afterward.

Saved evidence: exact model requests/responses, every observed screenshot, actions,
observation/decision/execution timings, final screenshot and terminal reason.
`accepted` remains null even when the model claims completion. A separate evaluator
must inspect the final app state, retain failure evidence, clean only owned objects
and verify cleanup. Timeouts or malformed responses are errors; no replay of an
uncertain input is implemented. Runtime failures after run admission are recorded
as runtime_error and re-raised, without automatically replaying an action.

`prepare.py --adb ... --case calculator|notes|alarm` restores only the disposable
fixture and waits for ADB and the expected foreground. `--policy regions` selects
the conditional region experiment; it has not demonstrated a successful fix.

Current scope: calculator, alarm and notes only. Report per-case success and total
wall time; separate model warm-up and capture/transport latency. Do not pool text
judgment microbenchmarks with complete GUI tasks or call these official benchmark
scores. Do not compare publisher H200/5090 latency against our 3090 end-to-end time.

Offline checks: `python3 -m unittest -v` in this directory verifies that unadvertised
actions cannot execute and model-done does not become an independent acceptance.
