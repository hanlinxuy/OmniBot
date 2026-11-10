# Phone Pi iteration — 2026-11-10, eight-hour window

Window: 10:32–18:32 Asia/Shanghai. This report is incremental, not a declaration that the full window or benchmark is complete.

## Environment and boundaries

Same vivo device, Android Alpine Pi 1.4.0 / ACP adapter 0.1.0, 37 advertised tools, isolated sessions/workspaces. Models: local `qwen3.6-35b-a3b-q4_k_m`, official `deepseek-flash`, and `gpt-6.1-terra` through the local subscription model relay. Reasoning off for this matrix. A shared `run_gui` delegates to OmniFlow; these file/calendar cases did not rely on that visual route. These are source-fixture Android adaptations, not official benchmark scores.

Source inventory: MobileWorld 201 tasks at `aa2ea47052dd15729bfca1f27fa6aca4630607a9`; iOSWorld 133 at `c59287b6ec9a9d0d7269613ad873f6aa9db0f42c`. See the companion research note for Mika source and implementation findings. Inventory entries do not count as runs.

## Batch 1: actual results

| Task / condition | 35a3 | DeepSeek | Terra |
| --- | --- | --- | --- |
| CheckInvoiceTask1, isolated baseline | 360-second timeout installing PyMuPDF | Same dependency-install timeout | Pass, 104417.70; 5 model requests |
| Same task, discoverable PDF skill | Skill listed but not read; pdfplumber install timeout | Read skill/PDF and computed main amount correctly, but violated required single-number output; 10 requests | Pass; read skill and used helper; 5 requests |
| Same task, skill plus generic workspace guidance | Still ignored skill, install timeout | Returned 104386.22 using annualized days/365 convention; source judge fails; 6 requests | Pass; 6 requests |
| BidFileRenameTask | Pass; 9 requests | Pass; 4 requests | Pass; 3 requests |
| CheckDeduplicatedEventsTask adaptation | Failed: omitted required test prefix, scoped read denied; then unrelated shell exploration and 18-request budget exhausted | 9 unique events; scoped read and unchanged fixture verified; 3 requests | 9 unique events; scoped read and unchanged fixture verified; 2 requests |

Invoice evidence uses the original PDF bytes and question. The source question explicitly requires one number. The document's printed subtotal differs from the sum of its line items; DeepSeek's observation of that discrepancy is real, but does not excuse reporting a source-judge pass. The monthly-interest convention also leaves room for a different day-count interpretation; retain the exact answer and source criterion rather than treating every judge mismatch as an arithmetic error.

One preliminary 35a3 invoice run answered correctly but installed parsers globally despite the requested workspace scope. It is **not a pass**. Only the two newly introduced parser packages were removed and their absence verified. A separate orchestration run started after a failed preparation command; it is explicitly **invalid**, excluded from the comparison, and its workspace was removed. Subsequent cases set workspace-local pip target/cache paths.

For rename, all three independently read-back file inventories matched all 15 expected names and file bytes: four renamed bid files plus 11 untouched distractors. The source initializer/evaluator uses mtime despite the goal saying creation date; this convention was made explicit. No claim covers names with spaces, destination collisions or real shared Downloads.

For calendar, the source's 15 stored records were seeded with a unique title prefix and original dates in a native test calendar. Nine templates overlap October 20–26, 2025. This does not validate recurrence expansion. Acceptance checks the actual scoped query, semantic answer, complete fixture readback and deletion of the owned records; the source's weak substring-9 judge is retained separately. The 35a3 shell detour is a scope failure. No MCP credential was found in that captured trajectory. Future calendar-only runs should disallow shell exploration while permitting nonpersonal capability/environment discovery.

## Product changes verified on device

- `context_environment_get` now returns currentTime, localDate, timezone and utcOffsetSeconds from one clock sample, alongside sampledAt. Tool guidance calls out relative dates and the terminal/device timezone distinction.
- Built-in `read-local-pdf` skill selects a pure Python parser installed inside the workspace. Its helper performs bounded page reads/search, reports truncation and omitted pages, and preserves the source file. It contains no benchmark answers or fixed answer-page hints.
- Two device-time tests (including daylight saving), 17 existing skill-runtime tests, four PDF-helper checks and the Android build passed.
- Installed APK SHA-256: `21fa76f4bd44b4de5af75b27edc50f5d8c7f511128b73d5594eaf8e52aec53a1`. Independent device readback matched Asia/Shanghai, the phone's local date and offset. Both installed skill files exactly matched bundled sources after normal app initialization.

The skill improves parser selection for models that use it; it is not a universal success or speed claim. Explicit Pi skill selection and broader paper tasks remain to be tested.

## Batch 2: five new PDF tasks with explicit Pi skill selection

All 15 runs used the same original PDF bytes and pre-change helper. The harness invoked Pi's native `/skill:read-local-pdf` expansion; this is explicit selection, not autonomous skill discovery. Each run used a new workspace and parser install. No source answer or answer page was injected.

| Original task | 35a3 | DeepSeek | Terra |
| --- | --- | --- | --- |
| Paper 1, AIME score difference | Correct 1.9 with forbidden extra prose; fail | 1.9; pass | 1.9; pass |
| Paper 2, core contributor count | 60; pass | 60; pass | 60; pass |
| Paper 3, Thinking text benchmark count | Correct 12 with forbidden extra prose; fail | Correct 12 with extra prose; also wrote temporary files outside workspace; fail | 12; pass |
| Paper 4, vision encoder size | Explains both 540/543 instead of one number; fail | 540; pass | Source-accepted number; pass |
| Paper 5, Austroasiatic languages | Correct language codes but extra prose; fail | Vietnamese, Khmer: user goal satisfied, strict source judge fails | Same semantic pass / source-judge failure |

Strict source-judge counts on these five tasks: 35a3 1/5, DeepSeek 3/5, Terra 4/5. Independently audited user-goal counts, accepting the two correctly named languages in Paper 5 while retaining the original judge result: 1/5, 4/5, 5/5. These small selected source-fixture adaptations are not MobileWorld benchmark scores. Elapsed times were roughly 28–51 seconds; network/package installation variability prevents a clean model-speed conclusion.

Paper 3's oracle was independently checked against rendered Table 5 on page 10: 12 benchmarks. The prose and Instruct table list 11, so they are not interchangeable. Paper 5 asks for languages, while its implementation only accepts `vie Latn,khm Khmr`; correct language names should not be reported as factual errors.

DeepSeek Paper 3 wrote `/tmp/full.txt` and `/tmp/pages.txt`, outside its isolated workspace. Their exact bytes matched independent reconstruction from the supplied PDF; those generated files were removed and absence verified. Their prior existence was not recorded, so no claim is made about restoring any earlier contents. The scope failure remains despite cleanup. A cwd and permission prompt are not filesystem isolation.

A repeatable helper issue appeared in Paper 1: literal `AIME25` misses the PDF typography `AIME’25`. A candidate fallback tolerates whitespace, apostrophes and hyphenation, labels its match mode and preserves the actual excerpt. Five helper tests pass. The installed phone skill matched both bundled source files byte-for-byte, found the formerly missed typography in the original PDF, preserved the excerpt, and removed its unique test workspace. This was a deterministic helper control, not another model-task score. This candidate was prepared only after the entire matrix finished, preserving within-batch comparability.

## Mail batch in progress

The original AcceptMeetingTask fixture now runs in the real ARM64 Mail clone on vivo. The outer Pi model is varied; GUI execution still uses the same configured OmniFlow planner, so this is not a comparison of three independent vision executors.

The first 35a3 run failed: four gestures were dispatched, but the last tap missed Send, the saved screen remained Compose, and no `sentEmail.json` existed. The planner's next turn ended with `finish_reason=length`; the outer model nevertheless claimed success and attempted further GUI calls, which the harness denied to prevent replaying an uncertain submission. This is a task failure and false completion report, not successful mail delivery. Source readback, terminal RunLog and fixture restoration were checked separately.

The first DeepSeek run produced the correct local recipient, subject and body and a terminal succeeded RunLog; its nine actions included missed taps and recovery. Fixture restoration was independently verified. Terra also passed with four actions and independently verified local send/cleanup. A focused candidate then increased the GUI completion budget to 4096, emphasized current-node IDs and already-normalized coordinates, and clarified the MCP tool description that gesture success is not business completion. The failed 35a3 path passed once under this candidate with four actions and correct local send/cleanup. This bounded retest does not establish reliability or isolate which change caused the improvement; its actions still used coordinates, so it does not prove adoption of node IDs. Mail sends are local fixture output, not external email delivery.

## Further work in progress

- Bound omitted bash timeouts using Pi's existing shell execution owner; retain explicit longer timeouts and official ACP completion/cancellation. All 26 adapter tests passed. The updated adapter was installed through the app's normal Pi installer. A real-device local-provider test verified the omitted 120-second bash timeout, termination of the spawned child, continued use of the same ACP session, cancellation, restored session identity, file/MCP access and clean close. These deterministic checks validate the harness, not a model benchmark score. Adapter revision: `28ec749c37aaaef2d272`; updated APK SHA-256: `0be3a430aee9ea4110c01001037516d638aee2d5966e23e16ceb5b6411da1c6f`.
- The original MobileWorld image booted with software emulation after prior KVM failures. The official `com.gmailclone` APK was extracted (SHA-256 `b2ff1cd9f2e8ffce8ac46a15298f38082890d9267fc3ca4095a9aab74e32a37c`) and contains ARM64. In a network-disabled software emulator, composing a fixture message produced the expected local `sentEmail.json` readback. The temporary emulator was removed after extraction/verification, and the original APK is now installed on vivo. This is dependency recovery and a bounded offline control, not a completed phone mail task or proof about every network path.
- Device document-tree permission is denied at preflight. Calendar/contact permissions are available; no unrelated personal records were needed for these fixtures.

Evidence is retained locally under `phone-real-use/artifacts/pi-eight-hour-20261110/`: per-run task metadata, source hashes, ACP updates, provider requests, answer/readback, explicit acceptance audits and cleanup. Secret values and private device dumps must not be committed. USB keep-awake was temporarily enabled during installation after automatic sleep; restore its recorded original value (0) at the end of the window.

Repair validation: 28 existing override tests, four Android MCP-server tests, five PDF-helper tests and Android build passed. Installed APK SHA-256 `ace408542a18c666c124cef0869c3da96897cdf62f996e11eaedfb0d9618b3bf`. Managed planner readback SHA-256 `e11d5267cdddd247c69ed509ec2ed192b4f4c7ceafed2869bdab2264b978dbe0`; prior source was preserved, other overrides retained. One APK installation was aborted by the installer; the subsequent ordinary confirmation flow completed successfully.
