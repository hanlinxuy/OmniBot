# Android scenario scan planning

`scan.mjs` is an offline planning and result aggregation entry point; it does not connect to a device or verify evidence. `live-smoke.py` is a separate, experimental BA19 UI runner. It defaults to a plan and connects only when explicitly invoked with `--live`, a serial, the guarded runtime root, and a private output path. Its device execution and cancellation behavior must be verified independently.

```sh
node scripts/android-scenario-scan/scan.mjs --inventory inventory.json --plan
node scripts/android-scenario-scan/scan.mjs --inventory inventory.json --plan --results results.json
node --test scripts/android-scenario-scan/scan.test.mjs
```

Inventory uses `docs/testing/android-scenario-inventory.json`: an object with an `items` array, each with a unique `source_id`, `goal`, and adaptation `status`. All fields and status values are preserved. Only the exact status `ready` is eligible for a future execution; every other adaptation status remains visible and is not eligible. The plan preserves inventory order for serial execution and reports blocked if no scenario is ready. Inventory readiness does not establish device readiness.

Results are a JSON array of attempts with `scenario_id`, unique per-scenario `attempt_id`, `status` (`independent_success`, `assisted`, `failed`, `blocked`, or `incomplete`), and `evidence` (an array of evidence path strings). Exact duplicate attempts collapse; conflicting duplicates fail. Categories remain claims supplied by the operator: this tool does not inspect or validate evidence. Aggregation stays incomplete even when attempts claim success, because independent verification and execution coverage are not implemented. Treat all reports as local evidence, and protect potentially private screenshots and logs.

```sh
python3 scripts/android-scenario-scan/live-smoke.py --scenario BA19
python3 scripts/android-scenario-scan/live-smoke.py --live --serial SERIAL --scenario BA19 --device-runtime-root /path/to/phone-real-use --output /private/path/attempt.json
```

The caller must ensure exclusive device coordination for the full attempt; Runtime locks individual operations. A failure or timeout attempts to stop only the verified test conversation. If cancellation cannot be verified, intervene and confirm the device is idle. Protect attempt outputs and run logs as private evidence. The runner emits one attempt object with the aggregation status and evidence fields; wrap it in a JSON array before passing it to `scan.mjs`. A zero-step `android_gui_accessibility_not_ready` run is classified as environment-blocked, retaining the original run status and error.
