# MobileWorld original-backend API adaptation

The runner sends the pinned original task goal to **Pi installed on the Android phone**. Pi uses a scoped Mastodon REST MCP tool; the host forwards requests into a dedicated, isolated source backend. This is not an Android app GUI run and carries no official benchmark score.

`run.py` takes explicit `--source`, `--ios-source`, `--runtime-root`, `--output`, `--compose`, `--ssh-target`, `--ssh-control`, `--adb`, `--serial`, and `--task` arguments. The dedicated compose directory must start with `omnibot-mw-`. Its `pi-baseline.sql` must contain the original source data plus only owned authentication state. External federation and delivery must remain disabled. The separately configured loopback relay owns credentials; never commit tokens or raw run artifacts.

Each task restores the dedicated database and Redis, checks health, scores the initial state, runs phone Pi, executes the original SQL helper definitions and original `is_successful` body, then restores and hashes all database data again. `--backend-check` verifies initial judgment and repeatable restoration without executing a model. A SQL transport or parse error raises instead of becoming a model failure. Duplicate SQL column names retain their original positional values.

A completed runner is not task acceptance. Require the original source judge, a terminal phone execution, independent review of unintended mutations, phone workspace cleanup, and matching backend restoration hash. A source judge may only inspect its requested final predicate; audit the action trace for extra changes. Keep failed attempts and infrastructure errors separate. No source constants or expected answer IDs are exposed to the model.

Install the pinned source timezone dependency `pytz==2025.2` into the isolated runner environment (from MobileWorld uv.lock). Compiled source SQL helpers receive this dependency unchanged; missing judge dependencies are infrastructure errors, not zero model scores.

Regression: `python3 -m unittest discover -s scripts/benchmark-adapters/mobileworld-api -p 'test_*.py'`. Task and helper sources are pinned to the MobileWorld version recorded in each report; see the adjacent Android workspace adapter for source licensing.
