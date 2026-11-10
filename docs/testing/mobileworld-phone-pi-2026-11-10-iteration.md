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

## Batch 3: Mail to native calendar, cancellation, selector repair

| Source task | 35a3 | DeepSeek | Terra |
| --- | --- | --- | --- |
| CheckSetMeetTimeTask | Correct event; host acceptance query corrected before cleanup | Pass | Pass |
| CheckInterviewTimesTask | Three exact interviews; pass | Three exact interviews; pass | Three exact interviews; pass |
| CancelMeetingTask | Local reply; pass, four node-grounded actions | Same | Same; added an unrequested future rescheduling commitment |

Mail uses the original fixture bytes. Calendar adaptations explicitly specify the fixture year 2025 and UTC, use unique title prefixes and native calendar APIs, and independently compare timestamps. The interview acceptance checks the three expected companies and exact times rather than merely accepting the source judge's weak count of three. All owned calendar records were deleted and scoped readback returned zero; Mail baseline bytes were restored. These are new task adaptations, not official MobileWorld scores.

The first board-meeting host query omitted dates. The calendar API defaults to now minus 30 days through now plus 180 days even when a keyword is supplied, so the host initially missed the correctly created 2025 event. Explicit date bounds found it; the original false report was retained, then the unique event was deleted and absence verified. This is a harness acceptance bug, not a model failure. The model itself had recovered by specifying dates. Chinese and English calendar tool descriptions now disclose this default window and require explicit dates for historical/distant searches; implementation semantics are unchanged. Android MCP tests and build passed; deployment of that latest description is tracked separately.

A deeper selector defect explained why prompt advice alone was insufficient: the Python action-tool whitelist removed `node_id`, and grounding did not consume explicit IDs. The managed schemas override now exposes live IDs; grounding resolves them against the current XML and reuses the actionable-node/bounds path. A first retest still copied the summary's numbered row index instead of its actual ID. Removing that competing index fixed this ambiguity. In the new cancellation matrix, all 12 actions resolved through `accessibility_node_id`, and local send readback and terminal RunLogs passed. Unknown IDs still fall back, so this is not a strict selector enforcement boundary. No persistent Android node-ID executor or general reliability improvement is claimed.

There were 32 passing focused override tests. Sources were applied through the official whole-file managed interface, with prior sources preserved and unrelated overrides retained; the runtime ZIP was not changed. Managed candidate hashes: schemas `823858d2d6126f11a27721ca3c9ca1eaf06aa2fac2170b7263044656fb995288`, grounding `a19cb6566f5c1841bef128317e135e193c8f4a4c203cd73348304fb3f8ec2c1a`, context `50bbe98f23cba634855b5a5485f1d7ede215b926e9082b90ee9992ab406996df`.

The mail harness denied some harmless discovery calls (`list_functions`/app lookup); those denials should not be counted as product defects. Future cases should allow bounded capability discovery. All GUI cases here still share one OmniFlow visual executor beneath the three outer Pi models.

Latest calendar-description APK installed and independently hashed on device: `986a5fb7f30965fccf43ce9e9f2acede9858dd78989073a49e1cf6bfdcb85bad`. The live MCP tools list includes both default date bounds. An existing alarm banner obscured the app menu; the app's existing settings Intent route allowed service startup without dismissing or modifying the alarm.

## Batch 4 in progress: isolated Mattermost APIs

The pinned MobileWorld image's Mattermost/PostgreSQL fixture was recovered into a newly owned, outbound-isolated Docker network. Normal fixture-user API authentication is held on the host; phone Pi receives typed MCP tools and no backend token. The task's original post belongs to Harry, so the fixture actor was verified against the source before testing. Only the newly cloned fixture account was prepared; no external user account or real messaging service was used.

MattermostReplyToMessageTask passed for 35a3, DeepSeek and Terra, approximately 19.2 / 24.0 / 25.7 seconds with 5 / 5 / 4 model requests. Each independently found the current user and relevant original post, then posted exactly one reply containing 35.5 to the correct root/channel as the correct actor. Original goal, posts and judge criterion were preserved; API execution replaces the benchmark GUI, so this is an adaptation, not an official score. All created posts were deleted and their absence in the original thread verified. The first cleanup incorrectly expected a deleted post to remain GET-readable; the API returned 404. The host recovered and verified cleanup before continuing, then fixed the cleanup query. That harness error does not invalidate the independently recorded task mutation.

This small matrix demonstrates that the weaker local model can complete a suitable task through explicit system tools. It does not establish superiority or general benchmark performance. DeepSeek expanded the numeric result to a percentage/pass@1 formulation inferred from context; retain the actual response for content-quality review.

## Batch 4 continuation: batch membership and three-app reconciliation

MattermostCreateChannelTask passed for all three models (about 19.0 / 18.6 / 25.9 seconds). Each created its uniquely named paper-reading channel, added all 11 team members through one batch tool, independently listed the exact membership set, and posted one welcome message. Owned posts were removed and channels archived. The unique name replaces the source's fixed `reading`; the rest of the business criterion is preserved. This shows a concrete bounded batch interface, not a comparison to a measured GUI baseline.

MattermostDeadlineReconciliationTask uses the original goal, message text and dynamic-date calculation, with unique channel/title prefixes, native UTC calendar records, and local mock Mail. Seed messages are authored by the fixture actor rather than Sam; author identity is not used by this task's decision logic. The baseline matrix found:

- 35a3 completed the business effects and terminal GUI verification. It prematurely posted that the report had been emailed before the actual send; retain this intermediate false claim as a quality defect.
- DeepSeek omitted the required calendar scope arguments, repeatedly received generic harness denials, then reported blocked. No calendar events or mail were created; its two fixture status posts were cleaned. This is a scope-adherence/harness-feedback failure, not missing Android permissions. Harmless calendar metadata discovery was also unnecessarily denied by the harness.
- Terra produced all required business effects, but its GUI call hit Pi's 60-second MCP timeout during verification; RunLog was failed, and the outer model correctly reported the send as uncertain. Independent mock-send readback passed. The user also reported accepting an “always open” system dialog around this run; exact app/timestamp attribution is unconfirmed. Do not report a fully autonomous pass.

Two product defects were repaired. First, the tool documents date-only input for all-day calendar events, but `parseCalendarDateTime` rejected it. ISO local dates now resolve at the selected zone's start of day; existing all-day normalization retains exclusive UTC end dates. Five date/time tests and four MCP tests passed. A device control verified leap-day input and same-day all-day expansion in UTC and Asia/Shanghai, then deleted both owned records.

Second, native `run_gui` already emits actual execution events, but the MCP endpoint discarded them. Those events now become request-scoped MCP progress notifications using the existing progress token and cancellation owner. Pi 1.4.0's client re-arms its idle timeout on progress; no global timeout increase, synthetic heartbeat, separate retry loop or cancellation owner was added. A Terra retest completed the exact date-only inputs and the full business task, received 23 progress notifications, and ended with a succeeded RunLog. Its GUI took 19.042 seconds, so this proves wiring and the repaired task path, **not yet a measured GUI run exceeding 60 seconds**. Installed APK hash: `fc7c3964b96ee09bf439c9abe51f46e5d9ae4ad7507dbbdb4aa35947a7d03006`.

The English alarm description also contradicted the existing Chinese guidance by preferring app reminders. It now distinguishes native system Clock dispatch from app reminders and verification, matching the Chinese description. No new clock-task pass is claimed by that text change.

A fixture-bound calendar MCP adapter is under evaluation: it enforces calendar ID and unique title scope on the host instead of repeatedly requiring model-supplied isolation parameters. Its first DeepSeek run completed the calendar subtask but the GUI forwarding client failed before dispatch because it inherited a SOCKS proxy without its optional dependency. The full attempt is infrastructure-invalid, not a model-task score. Local HTTP now ignores proxy environment. All owned partial effects were independently cleaned; system channel-join messages are not mistaken for leftover task posts.


## Batch 5: original iOSWorld Notes through phone Pi

A dedicated iOS 26.5 simulator now runs seven original iOSWorld apps. All three models still execute inside Pi 1.4.0 on vivo; their MCP calls reach the original iOSWorld tools on the Mac. These tools mix Appium/WDA UI automation with source-provided direct SwiftData helpers. This is a remote-tool adaptation, not iOS execution on Android, a pure GUI result, or an official benchmark score. Clock and Mail needed only a deployment-target build override for the installed Xcode; benchmark source was unchanged. The simulator and all test stores are isolated from personal applications.

For notes-001, the original request asks for a bullet about a bug fix completed today in Team Standup Notes. All three baseline runs added one bullet and preserved existing content. Their quality differed:

| Model | Baseline content and placement | Explicit native Pi skill condition |
| --- | --- | --- |
| 35a3 | Invented login timeout / issue number; appended under Blockers | Generic factual wording; still appended under Blockers and incorrectly claimed placement was checked |
| DeepSeek | Invented token-refresh crash / merged status; appended under Blockers | Read-modify-write under Today; generic wording; independently verified pass |
| Terra | Generic wording under Today; independently verified pass | Generic wording under Today; independently verified pass |

The skill is a general `edit-structured-note` workflow: read the full body, select the appropriate section, inspect append versus replacement semantics, preserve unrelated content, use only supplied facts, and read back the actual placement. It contains no fixture IDs, note titles or answer key. The comparison explicitly selects it through Pi's native `/skill:` expansion; autonomous skill discovery is not claimed. These are single-run observations, not a reliable success-rate estimate. The 35a3 placement failure remains open.

The first host verifier incorrectly required the updated body to begin with the entire old body, so it rejected Terra's correct insertion before the final section. The raw result is preserved and a separate acceptance audit corrects it. A reusable line-diff verifier now distinguishes original-content preservation, exactly one added bullet and section placement; factual accuracy remains a separate source-based audit. Three focused tests cover middle insertion, wrong-section append, and destructive/duplicate edits. Each completed run restored and independently compared all Notes rows and folders before the next model.


## Batch 6: iOSWorld Clock, a concrete picker defect

The original clock-001 goal asks for a 6:45 AM alarm named Gym. All three baseline tools paths saved the wrong time: the original `add_alarm` assumes three wheels, but the observed 24-hour picker has two. After reporting that mismatch internally, it still saved the current picker time and returned the requested time as a success message. 35a3 claimed success, DeepSeek eventually qualified its verification but still relied on the echoed time, and Terra explicitly said the time could not be verified. Independent persisted alarm data failed all three. No wrong-time alarm was left after fixture restoration.

A first host audit also overflowed while converting the original seed alarms' ancient date values. That attempt remains acceptance-incomplete; later runs preserve the raw store before interpretation and convert only the newly created target date. A correct row count alone is not an alarm-time pass.

The separate iOSWorld adaptation patch now detects two/three wheels, verifies their values and cancels without saving on mismatch. A first candidate still failed because this 24-hour hour wheel silently ignores `6`; a no-save control showed that `06` works. The corrected candidate uses two-digit 24-hour values. The original XML response was also truncated before the alarm rows, so `list_alarms` now includes concise rendered row texts and switch values. These are source-tool changes in a versioned patch, **not OmniBot Android native-clock changes**.

| Corrected picker condition | Model requests | Persisted time / label / enabled | Cleanup |
| --- | --- | --- | --- |
| 35a3 | 3 | 06:45 / Gym / true | Restored |
| DeepSeek | 3 | 06:45 / Gym / true | Restored |
| Terra | 3 | 06:45 / Gym / true | Restored |

The failed intermediate candidate is retained, including 35a3's eventual request-budget exhaustion and attempts to change the requested time. No pass is inferred from those attempts. The corrected matrix used the same tool source. Two focused controls cover 12/24-hour conversion boundaries and ensure a silently failed wheel write cannot reach Save. The source patch, upstream license, application instructions and controls are in `scripts/benchmark-adapters/iosworld/`. Repository clock guidance also records the general lesson: inspect the actual picker and treat contradictory success/failure receipts as unverified.


## Scoped workflow and Pi tool-surface controls

The corrected fixture-bound calendar adapter completed its same-condition matrix: DeepSeek and Terra passed the three-app reconciliation task, with independent native calendar/local Mail/Mattermost evidence and cleanup. 35a3 created the correct dates and sent the report but omitted the explicitly requested `[AUTO]` prefix while claiming it had used it; retain that source-task failure. A targeted 35a3 retest with a pre-write title constraint returned an actionable error without creating an event, after which the model corrected its titles and passed. This is a task-policy harness condition, not improved raw model capability or a native Android product fix. The adapter does not supply the missing-event decision or dates. Its portable source, dependencies, usage and local integration control are now under `scripts/benchmark-adapters/`.

Repeated denied shell/file calls during the app-only Clock failure exposed another harness problem: those tools were still advertised to the model. The OmniBot Pi adapter now accepts `OMNIBOT_PI_TOOLS_JSON` and forwards it directly to the official SDK's existing `tools` selection option. No replacement execution or permission owner was added. The default tool surface is retained when unset; callers can select app-specific MCP tools for a bounded task. The existing integration control now verifies both default and selected surfaces and continued ACP permission ownership. All 26 adapter tests, packaging and Android build passed.

The updated APK and managed Pi revision `5ebbbade2433c1e1da78` were installed through normal app flows. The first new CloudDrive run's actual model request advertised only 23 selected CloudDrive MCP tools, with no shell/file tools, and completed through phone Pi. New Cloud-suite runs record this explicit selection as a separate harness condition; no before/after model comparison is inferred from different tasks.

## Batch 7: CloudDrive, CloudSheets and CloudDocs

All nine app-scoped phone Pi runs completed their independent business checks and restored the owned shared workspace. This uses original iOSWorld task goals/seeds, original app tools and the new SDK tool-selection condition; it is not an official GUI benchmark score.

| Original task | 35a3 | DeepSeek | Terra | Important boundary |
| --- | --- | --- | --- | --- |
| clouddrive-001 | 21.4 s / 7 requests | 30.2 s / 8 | 30.1 s / 5 | Latest Product Strategy document was already starred; correct no-op, not proof of a new star mutation |
| cloudsheets-003 | 20.6 s / 7 | 21.5 s / 6 | 24.5 s / 6 | Changed one previously Confirmed volunteer to Pending; preserved other spreadsheet content/styles |
| clouddocs-004 | 38.7 s / 9 | 40.2 s / 10 | 41.5 s / 7 | Added a generic Q2 planning note and preserved old text; model verification only checked metadata, while host read actual body |

The first two spreadsheet raw verdicts were false because the host compared omitted default styles and the MCP's duplicate `value` alias literally. Source inspection established the app's default-style decoding and the setter's identical alias. Separate audits correct those verdicts; raw evidence is retained. The reusable verifier normalizes only those exact equivalents and has controls that retain real styling and conflicting values. This is a harness acceptance correction, not a product repair.

The CloudDocs baseline exposes an actionable tool-design gap: write tools are available, but a compact persisted body reader is missing. Full XML truncates before the content, so none of the three models independently read back the actual addition. A separate source patch adds bounded read-only body pagination; two focused controls and a live read-only preflight pass. The same-task repaired matrix now passes for all three models, each actually calling the new body reader: 35a3 33.5 seconds / 8 requests, DeepSeek 32.6 seconds / 8, Terra 43.7 seconds / 7. All fixtures were restored. 35a3 still overstates its own preservation check for unrelated documents: it only inspected their metadata before claiming all bodies unchanged. The host separately confirms preservation, but the model claim exceeds its observed evidence. Generic proposed Q2 planning actions are allowed by this task; they should not be conflated with the invented completed-bug facts in the Notes task.

## Batch 8 in progress: shared-UI concurrency

The first multi-037 attempt with 35a3 reached both TeamChat channels and Mail, then issued four `open_message` calls in one assistant turn. Pi 1.4.0 defaults to parallel tool execution. All four Mail calls timed out, while later `clouddocs_launch` reported an All Inboxes page: outstanding UI work was still competing for the same Appium screen. The host stopped the owned MCP workers and runner, then restored the Cloud workspace, Notes database, TeamChat JSON and Mail preferences with the owned simulator shut down. This attempt is infrastructure-failed, not a clean model-capability score or a full-task pass.

The Pi adapter now accepts an explicit session launch setting, `OMNIBOT_PI_TOOL_EXECUTION=sequential`, forwarding it to the SDK agent's existing execution mode. This does not add a second executor, lock or permission owner, and does not change the default parallel mode for other workloads. A real SDK/MCP control reproduces two concurrent reads both returning the second shared page; sequential mode returns the intended first and second pages, with both ACP permission decisions retained. All 27 adapter tests and packaging pass; the Android build succeeds. Live installation and same-task retesting are underway. This setting controls one session, not arbitrary concurrent clients of the same device.

The sequential retry exposed a second, separate source-tool defect. Mail's
`_in_message_detail` checks old SF-Symbol names, while the pinned Swift app
explicitly sets `detail_archive_button` and `detail_delete_button`. It therefore
misclassifies an opened message and sweeps more folders. A pinned patch uses
those real IDs; the previously problematic subject then opens in a measured
3.76-second isolated control. This also limits the causal claim about the
first attempt: concurrency and the stale selector both existed; the timeout
cannot be attributed solely to model behavior or solely to parallelism.

The detail observation has another evidence gap: only 2,000 of 10,050 XML
characters reached the model, omitting the email body. A compact read-only
accessibility text reader now exposes the actual open message, with pagination.
Two focused controls pass, and a live open-plus-read control obtains the actual
subject/sender/date/body in 3.82 seconds. The source fixture is synthetic; no
personal mailbox is accessed. The second incomplete attempt was also stopped
and fully restored. The corrected six-app matrix is now running with both
source patches and explicit sequential execution.

## Android refocus: weekend alarm and selector dispatch

The iOS simulator branch has been stopped and its one owned simulator deleted;
zero simulators remained booted at readback. Existing iOS adaptations remain
separate from vivo Android coverage.

The first explicit reminder-skill SetAlarmTask run on phone Pi with 35a3 passed
independent configuration readback: 08:25, Saturday and Sunday, enabled, exact
owned BeeBeep audio filename, vibration off. The host then deleted only the
uniquely marked test alarm and verified the visible alarm baseline remained.
Actual ringing was not tested. Performance was poor: 213.7567 seconds, three
GUI calls and 36 actions. The first GUI call alone lasted 152.218 seconds with
real progress notifications, demonstrating that the prior MCP progress change
supports an actively progressing call beyond 60 seconds.

One missing explicit node ID fell through to model coordinates and opened a
different alarm; the agent backed out without editing it. A managed execution
override now rejects that live action before dispatch and returns to the
existing planner recovery path. A separate context fix removes sibling index
as a purported node ID. Thirty-seven focused tests pass; source readback and
worker reload pass on the phone. A reminder skill revision also carries exact
observed asset filenames/URIs into delegated GUI goals, avoiding repeated
searches for a friendly media title that the device picker does not display.
The repaired 35a3 run is underway; these changes are not yet a measured speedup.

The repaired 35a3 run completed in 92.394 seconds with one GUI call and 16 GUI
steps (76.573 seconds inside GUI). Independent UI readback confirmed 08:25,
Saturday/Sunday, enabled, the unique audio filename (vivo omits its extension),
and no vibration. One unresolved node action was blocked without dispatch;
the planner then issued a separate visual action and completed. This is one
combined-change observation, not causal attribution or a general speed claim.
The shared GUI executor is DeepSeek Flash: the outer model comparison does not
mean that each model independently controls every GUI action.

The host verifier initially hit the floating Add button over a lower alarm row
and opened an unsaved draft. It cancelled that draft, repositioned the row, and
completed independent readback. This is a host acceptance-tool defect, separate
from model execution. No draft alarm was saved. Cleanup confirmation is tracked
separately in private evidence. Ninety-two seconds remains poor alarm-setting
latency despite the improvement; DeepSeek and Terra still need the same revised
condition before a model comparison is valid.

### Same-condition weekend alarm matrix completed

| Outer phone Pi model | Elapsed seconds | GUI calls | GUI steps | Independent configuration | Owned alarm cleanup |
| --- | ---: | ---: | ---: | --- | --- |
| 35a3 | 92.39 | 1 | 16 | pass | pass |
| DeepSeek Flash | 139.95 | 2 | 35 | pass | pass |
| Terra | 143.20 | 2 | 30 | pass | pass |

All three use reminder skill v2, the unresolved-live-ID guard and the same
DeepSeek GUI executor. Settings checked: enabled, 08:25, Saturday and Sunday,
unique supplied audio filename and vibration off. Actual ringing and source URI
readback are not established by the Clock UI. Failed node actions blocked before
dispatch: 1/2/3 respectively. Only the unique test alarms were deleted; visible
baseline preservation was checked for each run. These are single-run adapted
task results, not official benchmark scores or universal model rankings.

### GUI receipt truncation: concrete diagnosis and candidate

The first DeepSeek GUI call's complete result explicitly said the alarm had been
saved and reopened. Its steps occupied about 45 KB and function-recall diagnostics
about 13 KB. Pi's truncated tool output omitted `finished_content` and
`done_reason`, while retaining `final_state.activity_name=com.android.BBKClock.Timer`.
The next model call incorrectly inferred a Timer tab and requested another GUI
verification. Terra also made a second GUI call after a truncated success result;
its causal attribution has not been isolated.

The MCP `run_gui` result now puts terminal status, completion report, errors and
RunLog identity first, excludes full steps/recall diagnostics from the immediate
receipt, and retains recent failed action receipts and existing permission-action
notices. Full traces remain in RunLog. Other result fields, including payment
confirmation state, are preserved. The receipt explicitly distinguishes the GUI
executor's report from independent verification and component name from tab state.
Six focused Android unit tests and APK assembly pass. The APK was installed
with matching device/local SHA-256, and existing runtime overrides survived.
A new CheckEventTimeTask phone Pi run produced two complete GUI receipts of
1,141 and 1,755 bytes with their finished content intact, without Pi truncation.
The underlying full RunLogs remained available. Business acceptance is recorded
separately below; this does not establish general latency improvement.

### CheckEventTimeTask: email content to native Clock

The original synthetic Mail fixture says the Christmas party starts at 19:00.
Each model read it through GUI, computed one hour earlier itself, created an
18:00 system Clock alarm through the native API, then used GUI to verify it.
The harness supplied the unique title and actual-device-day interpretation, not
the party time or expected answer. This is an adapted task on vivo, not the
original emulator score. The same compact-receipt APK, reminder skill and
shared GUI executor were used for all three outer Pi models.

| Outer model | Elapsed seconds | GUI calls | GUI steps | Independent readback |
| --- | ---: | ---: | ---: | --- |
| 35a3 | 50.13 | 2 | 7 | today, 18:00, once, enabled |
| DeepSeek Flash | 48.80 | 2 | 9 | today, 18:00, once, enabled |
| Terra | 71.45 | 2 | 11 | today, 18:00, once, enabled |

The two GUI calls are different operations: mail reading and Clock verification.
All six returned complete completion reports without truncation, and their full
RunLogs were retained. Synthetic mail state/sent-record files were restored
byte-for-byte (or removed when originally absent). All three unique alarms were
independently deleted with visible baseline preservation. Actual ringing was not tested.

### DownloadReadmeTask: public source to synthetic Mail

The GitHub connector supplied the complete AndroidWorld README, blob
`a19a6777526c00e4b8132c7034a117899c336f67`. A read-only MCP fixture exposed
those exact 10,817 characters in pages of at most 4,000. All valid baseline runs
read all three pages; the original recipient, subject and six-keyword predicate
were retained. Sending used the offline synthetic Mail app on vivo, with direct
sent-record readback and baseline restoration; external delivery was not tested.

| Outer model | Seconds | GUI steps | Source predicate | Actual mismatch |
| --- | ---: | ---: | --- | --- |
| 35a3 | 36.87 | 5 | fail, 4/6 keywords | omitted issue URL; `slow` instead of `slowly` |
| DeepSeek Flash | 68.13 | 10 | fail, 5/6 keywords | omitted issue URL |
| Terra | 53.48 | 5 | fail, 5/6 keywords | omitted issue URL |

Recipients and subjects matched; all fixtures were cleaned. The performance
warning was semantically present in 35a3's message, so the literal-word failure
is distinct from the missing reference. Do not relabel these as original-predicate
passes. An earlier 35a3 run was invalid for comparison: the host matched the
whole permission display title, missing Pi's `: README.md` suffix, and denied all
source reads. The model then sent guessed source content. That artifact and its
failed predicate were retained separately; the harness title matching was fixed
against the captured request before valid runs.

`share-source-summary` is a generic skill, with no benchmark answer or keyword
list. In a targeted 35a3 rerun it retained the exact linked issue reference and
performance caveat, completing in 34.42 seconds with five GUI actions. Original
predicate still failed because the body said `slower`, not `slowly`; semantic
reference preservation improved without changing the judge.

A controlled unavailable-source case exposed another issue: the first skill
version prevented invented source content but still let the model send an
unrequested failure notice. The revised skill explicitly reports failure in the
current conversation and forbids a replacement message unless requested. Its
negative control made zero GUI calls and left no sent record; fixture restoration
passed. The model still made three unsuccessful source calls, so avoidance of
redundant retries remains unresolved. These are bounded skill observations, not
proof that prompts enforce all constraints. Both public-source fixture servers
and their phone reverse ports were stopped/removed after these tests.

The validated reminder workflow and source-summary skill are registered in the
built-in skill manifest. The asset-update APK was installed with a matching device hash, and both seeded
skill files matched source hashes. A read-only phone Pi probe, with no skill
name or slash expansion in its user prompt, advertised both from the normal
installed catalog and successfully read both in two model requests. This proves
installation/discovery/read wiring, not full autonomous business-task execution.
An earlier probe omitted the existing `OMNIBOT_HOST_WORKSPACE` bind and therefore
had no skill catalog; it is invalid for discovery assessment and was corrected
before this result. The valid explanation still overgeneralized same-time alarm
identity, so guidance interpretation is not assumed perfect.


### MattermostCustomerFeedbackAnalysisTask: feedback to digest and meeting

All three phone Pi outer models completed this new adapted scenario. The source's
16 feedback messages were preserved (three negative, thirteen positive), with a
unique fixture channel and calendar title. Fixture messages share Harry as author;
times are explicitly UTC, so Friday 14:00 means 22:00 on the vivo's Shanghai clock.
These adaptations are not an official benchmark score. All three share the same
DeepSeek GUI executor; this is not a comparison of three GUI models.

| Outer model | Seconds | Provider requests | GUI calls / steps | Independent outcome |
| --- | ---: | ---: | ---: | --- |
| 35a3 | 50.02 | 7 | 1 / 5 | pass |
| DeepSeek Flash | 41.62 | 8 | 1 / 5 | pass |
| Terra | 60.21 | 7 | 1 / 5 | pass |

Only the offline Mail send used GUI. Channel reads/reply and calendar creation
used MCP/system interfaces. Independent readback required the exact recipient
and subject, all three negative items with no positive-item leakage, exactly one
Friday 14:00 event, and the requested channel confirmation. Each run passed those
checks. GUI terminal RunLogs were confirmed before restoring mail state byte for
byte; all unique calendar events and posts were removed and channels archived.
External email delivery is not exercised.

Two narrative caveats remain: 35a3 and DeepSeek counted the system join notice
as another positive message; Terra did not make that claim. DeepSeek posted the
channel confirmation before sending the digest, though the final independent
state passed. These observations are retained separately from business-effect
acceptance. No product fix or general speedup is claimed from this batch.


### MattermostIncidentEscalationTask: critical ticket to coordinated response

The complete 27-message source fixture was preserved, with synthetic Harry as
author and unique source/incident channel prefixes. All models had to discover
the critical ticket, create its channel, locate Sam by email and add him, send
the description through offline Mail, and schedule tomorrow's discussion. The
fixture adapter now exposes the synthetic team users' existing email field;
previously it returned names without the identity field required by this task.
Calendar times are explicitly UTC in this adaptation; this is not an official
benchmark score. No ongoing monitoring service is created by this one-shot run.

| Outer model | Seconds | GUI steps | Independent adapted outcome |
| --- | ---: | ---: | --- |
| 35a3 | 52.02 | 7 | fail: scheduled 01:00 UTC instead of explicit 09:00 UTC |
| DeepSeek Flash | 54.98 | 8 | pass |
| Terra | 58.68 | 5 | pass |

All three correctly created the incident channel, added the requested identity,
and sent the correct local email. Each made one GUI call for Mail; other actions
used interfaces. Membership, email and calendar state were read independently;
all unique source/incident channels, posts, events and Mail fixtures were cleaned.
35a3 verified that its submitted timestamp persisted, but that timestamp already
contained its timezone interpretation mistake. Its 01:00 UTC equals 09:00 Shanghai;
this is failure to honor this request's explicit UTC constraint, not evidence
that ordinary unzoned requests should default to UTC.

An earlier 35a3 attempt was invalid as a model comparison: it supplied the
channel name as an ID, and the host permission handler performed an unguarded
lookup that raised 404 and aborted the run. The corrected handler compares the
argument with known owned channel IDs and rejects unknown IDs without lookup.
The valid rerun recovered from the same initial wrong argument by listing
channels. Invalid-run evidence and successful cleanup are retained separately.

The reminder skill now explicitly gives a requested time zone precedence over
the device zone and requires comparing readback to the requested wall-clock
meaning, not merely to submitted arguments. The asset APK built successfully;
installed APK and seeded skill hashes matched. A separately labelled 35a3 control
explicitly requested reading this skill, and the read completed. It passed all
independent checks in 60.13 seconds with 5 GUI steps,
including 09:00 UTC. All owned effects were cleaned. This is one successful
explicit-skill control, not a repeatability claim or proof of automatic selection.
The original baseline failure remains in the comparison.

After the asset install, the native service selected a new ephemeral port. The
host's stale forwarding was updated to the observed service port and authenticated
health checked before the control. This infrastructure interruption is excluded
from model results. APK SHA-256:
`ad615ef890c2e3e902bdfc92535184af727360bc0fdfdc91397038aba0efef32`.
