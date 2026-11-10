# Android phone Pi workspace adaptations

Runs `cloudsheets-003` and `clouddocs-004` from pinned iOSWorld source through the **phone's Pi ACP runtime**. The host provides fixtures, a model HTTP relay, bounded tool admission, independent file readback and cleanup. It does not execute model-chosen edits on the host. No iOS simulator is used.

The sheet is a real XLSX containing the original `SeedDataFactory.swift` volunteer cells. The document is a plain-text export of the original seed body. Original GUI navigation criteria are substituted by file access and are **not awarded official iOSWorld UI credit**. Do not compare these results directly with original GUI benchmark scores.

Install `openpyxl==3.1.5` in the host Python environment used to launch the runner. Its pure Python dependencies are copied into the unique phone test directory and removed with it. Prerequisites: connected debug build with phone Pi installed, ADB `run-as` access, available local model endpoint, and the existing Android device-lock directory under the runtime root.

```sh
python3 scripts/benchmark-adapters/android-workspace/run.py \
  --case cloudsheets-003 \
  --runtime-root /path/to/phone-real-use \
  --ios-source /path/to/iosworld-c59287b6 \
  --output /path/to/private-artifacts \
  --adb /path/to/adb --serial DEVICE_SERIAL \
  --endpoint http://127.0.0.1:18081/v1/chat/completions
```

Use `--case clouddocs-004` for the document task. Keep output private: it includes full model requests and ACP updates. This adapter uses a local unauthenticated model relay, not an API-key discovery mechanism. The runtime provider model defaults to the tested 35a3 model and can be selected with `--model`.

The model receives the original goal and a generic capability contract (`list`, `read`, `set`, `append`). It chooses the volunteer/cell or appended content. Acceptance is independent: pull the actual saved file using ADB, compare all cells/original body, and check the answer. Results, failed attempts and cleanup are separate records. Only the unique run directory is removed. The scoped CLI is a benchmark capability, not a general Python/shell sandbox and not a product plugin installation.

Regression: `python3 -m unittest discover -s scripts/benchmark-adapters/android-workspace -p 'test_*.py'`.

## Source attribution

Task goals, rubric and fixture content derive from [iOSWorld](https://github.com/ljang0/iOSWorld/tree/c59287b6ec9a9d0d7269613ad873f6aa9db0f42c), Apache-2.0. The unified catalog also contains goals and metadata from [MobileWorld](https://github.com/Tongyi-MAI/MobileWorld/tree/aa2ea47052dd15729bfca1f27fa6aca4630607a9), Apache-2.0. The adjacent `SOURCE-LICENSE` preserves the license. These are modified Android adaptations, not upstream releases. Source task IDs, pins and hashes remain in the catalog.

## Expanded source-backed API coverage

The runner now defaults to phone-local structured MCP (`--transport mcp`); `--transport shell` retains the older scoped CLI control. Capabilities cover workbook cells, slide bullets, source-backed notes, message threads, catalogs/carts, nutrition logs, travel queries, and isolated request/reservation records. They are evaluation adapters, **not installed Android third-party integrations**. Mock send/booking results never contact a real person or merchant. The APK itself is unchanged by these adapter improvements.

Host dependencies additionally include `python-pptx` and `tzdata`. Only pure Python dependencies are shipped to the owned phone directory; host `lxml` is not copied. Phone slides use ZIP/XML edits; host acceptance reads the actual saved PPTX independently.

Export source fixtures to `<output>/source-exports` with the scripts in `exporters/`. Each takes `--ios-source`, `--output`, and explicit `--timezone` (default America/Los_Angeles). `foundation.py` exports the Foundation model/seed apps; `reflected.py notes stayfinder quickchat` exports original seed graphs without UI/persistence/network; `mail_movies.py` exports Mail and Cinephile; `food_orders.py` exports QuickBite after QuickChat. Swift is required on the Mac. These extractors target the pinned source layout, including line ranges, and must be reviewed before changing the source pin. They do not launch simulators. Export dates are fixed to March 5, 2026; original declarations with their own explicit dates remain unchanged. Prior artifacts retain host-timezone provenance and must not be silently regraded against newly exported fixtures.

Weather uses a separately captured NWS snapshot from the upstream provider. The standalone weather case explicitly uses capture/forecast dates instead of the March fixture date. Do not use November forecasts to claim March travel-weather criteria passed.

Cross-app cases materialize the requested original app datasets together. Missing capabilities, wrong recipients, invented details, or inconsistent cross-document meaning remain failures. `semanticReviewRequired` deliberately prevents automatic pass: inspect source rubric, saved-state changes, final answer, and cleanup before writing an independent review. Bank/payment tasks are excluded; Clock requires the native device adapter and is rejected here.

`aggregate_results.py --artifacts PRIVATE_OUTPUT --output docs/testing/android-adaptation-results.json` combines raw acceptance and separate independent reviews without deleting failed attempts. The public catalog contains only sanitized result summaries; full requests, message bodies and raw fixtures stay private. A task having any passing attempt does not imply subsequent regressions passed.
