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
