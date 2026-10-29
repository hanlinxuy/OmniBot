# vivo Android regression harness — 2026-10-29

This run unifies six executable cases with their source scenarios and prior scan evidence. The broader inventory still has 280 source entries; these six scoped cases do not establish coverage of that inventory. In particular, the notes case is a LI04 subcase, timer/stopwatch are BA02 variants, and a subcase passing never promotes its full source scenario.

## What runs

`regression.py` serially performs prerequisites, one uniquely identified task submission, independent acceptance, and scoped cleanup. GUI component cases use the existing debug receiver and RunLog; normal-chat cases use the existing conversation and persisted ACP terminal projection. Active tasks are polled through saved logs/database snapshots, without concurrent UI observation. A timeout or unknown completion stops the suite instead of submitting another mutation.

Acceptance is independent of the model's completion claim: calculator display OCR, notes body OCR in the observed editor bounds, clock state from fresh XML, and calendar event/reminder readback through the Android provider. Timer acceptance also checks native duration arguments and remaining time against the measured submission-to-observation interval. Calendar acceptance checks the selected local calendar, timestamps, timezone and reminder. Only uniquely marked test objects are eligible for removal. Visible alarm baseline comparison does not establish that every off-screen alarm was audited.

## Failures retained and fixes

Initial driver failures included invalid runtime arguments, assuming a cleared calculator displays zero, failing to restart the host UI service after agent completion, duplicate clock tab labels, and post-tap transient window-focus errors. The calculator's cleared display is now checked as an empty light display region, while its calculated value is independently OCR-read. Clock tabs use the observed control class. Known navigation postconditions can establish that a tap took effect without replaying it.

A changing SQLite/WAL copy produced a malformed read during the first alarm attempt; only read snapshots are retried. Serialized tool arguments are decoded at their owning persistence boundary. GUI polling tolerates bounded focus transitions without repeating a submission. The alarm cleanup also needed to scroll its target clear of the bottom toolbar and verify exactly one selected item.

The first submitted alarm and notes attempts required explicit recovery after the runner stopped. Their actual task completion and owned-object cleanup were independently checked, but those original attempts remain `cleanup_failed`. They are not rewritten into automatic successes.

Skills are repository guidance, not evidence of automatic installation or use inside the Android app. This batch adds host regression files and repository skills; the device tests depend on the previously built native tools, fixes and debug receiver. The initial tested APK SHA-256 was `f9b67807e205dd3726f959cfc9a36fa9c69a419bbab8b1fdf1057455c72b33f4`. Raw screenshots, note lists, conversations, and temporary database snapshots stay private and are not committed.

## Product defect found by the regression

The calendar check found that explicitly requested `Asia/Shanghai` was stored as `+08:00`. The instant and 60-minute reminder were correct, but the named timezone was lost. Create/update now preserve an explicit zone using the same instant; unspecified-offset input and all-day UTC handling retain their existing behavior. Parser tests and actual provider-write assertions cover the change: 18 Android unit tests passed, and the debug APK built and installed successfully. The patched APK SHA-256 is `315e31b064bc572df9cd62ab0dd7508c9530e96ae8d8514a659037fd47f16f43`.

The runner additionally exposed two OEM integration details: UIAutomator must actually stop before OmniBot GUI execution (one shared backend is retained across cases), and Notes search can show the same note both as an AI reference and a regular result. Exact title matching now excludes the reference subtree and validates the unique regular result and opened detail. Three synthetic tests cover this ambiguity; they are not device evidence.

## Validation boundaries

20 Python tests and 4 inventory/aggregation Node tests passed. All four repository skills passed the skill-creator validator. The native timezone change passed 18 Android unit tests, an APK build/install, and a fresh normal-chat calendar regression with independent provider readback and cleanup.

The table records separate attempts, not one uninterrupted six-case passing suite or a multi-run reliability benchmark. Failed cleanup attempts retain their failures even if a later recovery established that an object was removed. The last notes deletion was sent before the phone's ADB connection disappeared; fresh post-interruption confirmation remains pending. The timer was independently canceled and its picker restored in recovery, but its full unattended regression still needs a passing repeat.

## Attempt history

The following table is generated by `summarize.py` from private reports using an explicit public-field allowlist. Missing gates mean no verified result, not success. Step counts/durations are available for GUI component runs only; they exclude host preparation, acceptance and cleanup and are not a normal-chat latency benchmark.

<!-- generated-attempts -->

| Case | Latest status | Execution layer | Cleanup actor |
| --- | --- | --- | --- |
| ba19-calculator | passed | gui_component | host_automation |
| li04-notes | cleanup_failed | gui_component | host_automation |
| ba20-alarm | passed | normal_chat | host_automation |
| ba02-timer | cleanup_failed | normal_chat | host_automation |
| li22-calendar | passed | normal_chat | normal_chat |
| clock-stopwatch | passed | gui_component | host_automation |

| Case | Attempt | Status | Execution | Acceptance | Cleanup | GUI steps | GUI duration ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ba19-calculator | REGa5e511f84895 | blocked | — | — | True | — | — |
| ba19-calculator | REG6d33d61d16b9 | blocked | — | — | True | — | — |
| ba19-calculator | REG28dffeab65de | blocked | — | — | True | — | — |
| ba19-calculator | REG61d5f4523f78 | cleanup_failed | True | — | False | 6 | 28415 |
| ba19-calculator | REG9554f4ef954a | passed | True | True | True | 6 | 23966 |
| li04-notes | REG7c1f5f6db35f | blocked | — | — | True | — | — |
| li04-notes | REGd22465175639 | blocked | — | — | True | — | — |
| li04-notes | REGd28a68bbb572 | blocked | — | — | True | — | — |
| li04-notes | REG6f5560af97d1 | cleanup_failed | — | — | False | — | — |
| li04-notes | REG8ede71139d1a | cleanup_failed | False | — | False | 0 | 30073 |
| li04-notes | REG77d20bd3c667 | cleanup_failed | True | — | False | 10 | 44064 |
| li04-notes | REG570a930de535 | blocked | — | — | True | — | — |
| li04-notes | REG58ab5eac9cda | cleanup_failed | True | True | False | 13 | 73767 |
| ba20-alarm | REGf49d0c646b38 | blocked | — | — | False | — | — |
| ba20-alarm | REGd06149283e9d | blocked | — | — | True | — | — |
| ba20-alarm | REG8ae93e3672f8 | cleanup_failed | — | — | False | — | — |
| ba20-alarm | REG30625ff1afb4 | passed | True | True | True | — | — |
| ba02-timer | REG4cde8edac2a3 | blocked | — | — | True | — | — |
| ba02-timer | REG2ceda2548ef4 | cleanup_failed | True | True | False | — | — |
| ba02-timer | REGa97f3522f007 | cleanup_failed | True | True | False | — | — |
| li22-calendar | REG7455ff54ccef | blocked | — | — | True | — | — |
| li22-calendar | REG43f73e3bc152 | failed | True | False | True | — | — |
| li22-calendar | REGb9871cb4459f | passed | True | True | True | — | — |
| clock-stopwatch | REG8e6f3cbd3ab6 | blocked | — | — | True | — | — |
| clock-stopwatch | REG85ae3618e360 | blocked | — | — | True | — | — |
| clock-stopwatch | REGee8952b018b3 | passed | True | True | True | 0 | 3155 |
