# iOSWorld tool adaptations

These patches are separate from the OmniBot Android product. The comparison uses the actual Pi process on Android to call original iOSWorld tools on an owned Mac simulator. Original tools mix Appium UI actions and direct fixture-store operations. Results are adaptations, not official benchmark scores or iOS apps running on Android.

## Clock picker and readback

`clock-picker.patch` targets [iOSWorld](https://github.com/ljang0/iOSWorld) commit `c59287b6ec9a9d0d7269613ad873f6aa9db0f42c` (`mcps/clock.py`). The unmodified tool requires three picker wheels, but the observed 24-hour picker has two. It nevertheless saves after setting fails and returns the requested time as success. A persisted readback found the current time instead of the requested alarm time.

The patch:

- Handles two-wheel 24-hour and three-wheel 12-hour layouts. The observed 24-hour hour wheel needs a two-digit value: `06` works where `6` silently leaves the previous hour unchanged.
- Reads each wheel back and cancels the sheet without saving if the values do not match.
- Adds compact rendered row texts and switch values to `list_alarms`; the existing slug list remains available. This avoids relying on a full XML response whose truncation removes the alarm rows.

Apply only to a matching checkout while its MCP server and model tasks are stopped:

```sh
git -C "$IOSWORLD_SOURCE" apply --check "$OMNIBOT_ROOT/scripts/benchmark-adapters/iosworld/clock-picker.patch"
git -C "$IOSWORLD_SOURCE" apply "$OMNIBOT_ROOT/scripts/benchmark-adapters/iosworld/clock-picker.patch"
IOSWORLD_SOURCE="$IOSWORLD_SOURCE" python3 "$OMNIBOT_ROOT/scripts/benchmark-adapters/iosworld/test_clock_picker.py"
```

Restart the existing MCP server after applying. Test on a dedicated simulator, preserve the original alarm store before the task, independently read the saved time/label/enabled state, close the model session, and restore the owned fixture. A rendered `h:mm` row alone does not distinguish AM from PM; use the picker readback and independent store evidence. No unsupported layout should be saved optimistically.

The controls exercise hour conversion and rejection of a silently failed wheel write. They do not replace simulator verification. The first repair candidate used an unpadded hour and failed; that attempt is retained separately from the corrected candidate. See `docs/testing/mobileworld-phone-pi-2026-11-10-iteration.md` for actual model results.

Upstream iOSWorld is distributed under Apache-2.0; its license is included in `LICENSE.iosworld`. This directory contains a patch, not an unmodified upstream snapshot. Local modifications are the picker handling, save guard, compact row evidence and focused controls described above.

## Document content readback

`clouddocs-read.patch` targets the same source pin. The original tools can append/set the body but expose only body length and block count as compact persisted evidence; the full UI XML can be truncated before the text. The patch adds a read-only `read_document(title_or_id, offset, limit)` with bounded character pagination. It neither creates a missing document nor provides task-specific answers. Follow `next_offset` to read the whole saved body.

Apply and restart the MCP server as above, substituting `clouddocs-read.patch`. Run `test_clouddocs_read.py` with `IOSWORLD_SOURCE` set. Controls cover Unicode pagination/reconstruction, invalid bounds, missing records and no mutation. Model use of the reader remains a separate empirical question; the presence of the tool does not establish successful verification.

## Shared spreadsheet evidence

`workspace_evidence.py` normalizes two source-defined serialization equivalents before comparing spreadsheet snapshots: an omitted/null style versus the app's exact default style, and a duplicate `value` field equal to `rawValue` written by the MCP setter. It preserves conflicting aliases, actual formatting and all cell values. `test_workspace_evidence.py` covers those boundaries. Keep raw snapshots and original verdicts; record corrected acceptance in a separate audit. This normalization must not mask real content or style changes.

## Mail detail identity and compact reading

`mail-detail.patch` uses the pinned app's explicit `detail_archive_button` and
`detail_delete_button` accessibility identifiers to recognize an open message.
The original check expected old icon names (`archive`/`trash`), so a successful
open was misclassified and followed by unnecessary folder sweeps. A simulator
control opens the previously problematic subject successfully in about 3.8 s.

The patch also adds `read_open_message(offset, limit)`. The original XML tool
returns a 2,000-character prefix; in the observed 10,050-character detail tree
that prefix contains the subject but omits the body. The compact reader returns
only static text from the current Mail detail accessibility tree, with bounded
pagination. It does not read hidden mailbox storage or guarantee content that
the UI has not exposed. Its controls cover body text beyond verbose XML,
escaped characters, pagination, and rejection of a list screen/invalid bounds.
Run `test_mail_detail.py` against the patched source, as with the other controls.

Use one execution owner and serial tool calls for the shared simulator UI. The
Mail selector defect and concurrent calls are distinct observed problems; a
serial setting alone cannot repair a stale selector.
