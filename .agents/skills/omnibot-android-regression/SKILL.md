---
name: omnibot-android-regression
description: Run or review OmniBot Android scenario regressions using repository cases, independent acceptance, and scoped cleanup.
---

Use this skill for this repository's authorized Android scenario regression. Read `docs/testing/android-regression-cases.json` for each case's scope, prerequisites, acceptance, cleanup and historical evidence. A case marked ready or a previous success does not prove the current device is ready or the current attempt passed. Component, normal-chat, independent acceptance and assisted cleanup are separate results.

Plan first with `python3 scripts/android-scenario-scan/regression.py --case CASE` or `--suite stable`. Live execution requires the caller's authorized device, explicit `--live --serial SERIAL --adb PATH --device-runtime-root ROOT --output DIR`, and exclusive device coordination. Confirm the runner's current supported cases and output before relying on it. This repository skill is guidance for an agent that loads it; it does not mean the Android app automatically loads or follows it.

For a system alarm use `alarm_reminder_create` with `mode=clock_app` and ISO `repeatDays`; for a system timer use `timer_create`; for a calendar event use `calendar_event_create`, then independently query `calendar_event_list` and its `reminderMinutes`. Intent dispatch alone does not verify a clock alarm or timer exists or runs. Keep a unique test marker, record baseline, and independently verify the target state and cleanup. Confirm ownership before deleting a test object. Timer cancellation is not provided by the native tool; report human or GUI cleanup as assisted rather than inventing a native capability.

Normal-chat cases use the existing ACP/MCP boundary and require persisted ACP turn terminal status and tool results. GUI-component cases use the existing DebugVlmTaskReceiver; neither path is a substitute for the other. Report execution_layer and cleanup_actor explicitly: host-side verification or automated cleanup does not prove full OmniBot autonomy.

GUI navigation between similarly named apps is not yet stable. Identify the actual foreground app and use fresh observations; do not assume an app is installed or a target selected from its name. Calculator values and some note bodies may require visual verification. Do not run a concurrent debug observation while normal chat or GUI execution is active: a previous concurrent observation resulted in an ANR. Observe only at an appropriate execution boundary.

Respect the project's device-access policy and current authorization, including exclusions for bank, payment, WeChat, `huawei.lock` and `com.huawei.works`. Preserve existing data; stop on uncertain state and never replay a potentially submitted action. Keep private captures and raw logs out of tracked files. Record actual attempt, independent verification and restoration separately; do not infer a complete original scenario from a subscenario.

For development discovery, a quick next scenario batch, or performance work, read [the development scan reference](references/dev-scan.md). It records the demonstrated vivo integration pitfalls and distinguishes host test overhead from product execution time.

For changes to previously observed contacts, RunLog, link-preview, browser-surface or GUI-date boundaries, use [focused repair regressions](references/repair-regressions.md).
