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
