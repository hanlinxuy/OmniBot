# Android scenario regressions

The unified entry point is `regression.py`, using [`docs/testing/android-regression-cases.json`](../../docs/testing/android-regression-cases.json). It defaults to a plan and performs no device actions unless `--live` is explicit:

```sh
python3 scripts/android-scenario-scan/regression.py --case ba19-calculator
python3 scripts/android-scenario-scan/regression.py --suite stable
python3 scripts/android-scenario-scan/regression.py --case li22-calendar --live --serial SERIAL --adb /path/to/adb --device-runtime-root /path/to/phone-real-use --output /private/new-output-directory
```

Repeat `--case` to run an explicit ordered subset. Calculator/notes acceptance requires macOS Vision (`swift`) and the guarded runtime Python environment (including Pillow).

`stable` selects historical candidates, not a guarantee of readiness or a passing result. `--suite all` includes unverified exploratory cases. Recheck each case's prerequisites and authorization before a live run. A passing attempt requires all three gates: execution, independent acceptance, and cleanup. Reports distinguish `normal_chat` from `gui_component` and identify the cleanup actor; host verification or assisted cleanup does not establish full OmniBot autonomy. The manifest preserves historical evidence separately from the current attempt. Mock tests verify code paths only and never count as live evidence.

The runner and selected tools operate on the foreground device. The caller must ensure only one agent or process uses it throughout an attempt, including independent verification and cleanup; per-operation locks do not replace this coordination. Do not run debug observation concurrently with active chat or GUI execution. Do not replay an uncertain submission. If stopping or cleanup cannot be verified, report the unresolved state and stop the suite.

Use a fresh private output directory for each live run. Evidence can include screenshots, UI text, run logs and calendar data. Outputs under the repository must be Git-ignored; never commit raw device evidence. Verify the target app, permissions, test-object ownership and baseline before changes, and preserve unrelated objects. The repository's access exclusions remain in force.

The create/search/reopen note path is recorded in [`omnibot-vivo-notes`](../../.agents/skills/omnibot-vivo-notes/SKILL.md). The successful calculator path is recorded in [`omnibot-calculator`](../../.agents/skills/omnibot-calculator/SKILL.md). The repository skills [`omnibot-android-regression`](../../.agents/skills/omnibot-android-regression/SKILL.md) and [`omnibot-native-clock-calendar`](../../.agents/skills/omnibot-native-clock-calendar/SKILL.md) provide guidance to agents that read them. Their presence in this repository does not mean the Android app has installed or automatically loaded either skill.

`scan.mjs` remains an offline inventory planner and attempt aggregator for `docs/testing/android-scenario-inventory.json`; its `ready` status is execution eligibility, not a result. `live-smoke.py` is a legacy experimental BA19 path whose current device behavior and cancellation are not independently validated as this unified runner; do not treat it as the regression entry point.

Offline checks:

```sh
python3 -m unittest discover -s scripts/android-scenario-scan -p 'test_*.py'
node --test scripts/android-scenario-scan/scan.test.mjs
```

Summarize one or more private runner reports without publishing raw evidence or error text:

```sh
python3 scripts/android-scenario-scan/summarize.py --report /private/run-a/report.json --report /private/run-b/report.json --format markdown
python3 scripts/android-scenario-scan/test_summarize.py
```

The default output is JSON on stdout; `--format markdown` prints a table. Every attempt is retained, including earlier failures. Cases absent from all reports are `not_run`; a passed subcase never implies its source scenario passed. Invalid or conflicting inputs are rejected rather than silently omitted.

Current device evidence and retained failures: [2026-10-29 results](../../docs/testing/android-regression-results-2026-10-29.md). The inventory links to each scoped regression; it does not promote a source scenario based on a subcase.
