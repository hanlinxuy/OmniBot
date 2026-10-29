# Android scan: retained artifacts pending disposition

These paths are retained because their pre-run state or ownership was not established. This list does not authorize deletion. Private contents remain outside this document and the public issue.

- Agent workspace `/workspace/smile/{mk.py,smile.png,now.jpg,edit.jpg,final.jpg,after.jpg}`: the BA21 run recorded writes, but there is no pre-run baseline establishing exclusive ownership. Retain; do not reuse. Disposition question is pending.
- Termux `/tmp/sh.json`: the LI15 run's recorded `terminal_execute` command fetched weather data to this path. It had no unique marker and no pre-run baseline; retain. It may be distinct from Android's other temporary directories.

- Amap route UI state: the transit read-only subcase replaced an existing start-field value and selected “metro preferred”; persistence and the complete prior route state are unverified. No restoration or clearing is claimed.

- NetEase Cloud Music notification permission: the agent selected deny and do not ask again. Post-run inspection shows denied with user-set/fixed flags for a reported user entry; pre-run state is unknown. User disposition requested; no reset or grant has been performed.

- Shared `/workspace/.venv`: the paper task installed `pypdf` after its import was observed missing, and ran `pip install pdfplumber`; the latter and dependency baselines are unknown. A `pymupdf` attempt failed. Retain installed packages and caches pending review; no uninstall or reset has been performed.

- NetEase Cloud Music, Dianping and Xiaohongshu search histories: these apps were searched during the scan. Persistence and pre-run histories are unknown; do not clear or reconstruct them without a baseline.

For subsequent file-producing scenarios, specify a unique test workspace as the output boundary without prescribing the solution; record whether the agent follows it. Record any newly discovered unmarked side effects here before deciding disposition.
