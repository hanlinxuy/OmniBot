# Android read-only entry scan — 2026-10-29

Three new source-scenario fragments were explored serially on the authorized vivo device. A unique GUI RunLog established each task's terminal state, and a fresh host observation after termination confirmed the foreground and relevant controls. The tasks requested read-only navigation; no test object was created. These are exploratory component results, not full source-scenario passes or executable three-gate regressions.

| Source and scope | Observed outcome | GUI actions / duration | Remaining work |
| --- | --- | --- | --- |
| LI20: current-city hourly weather entry | System weather opened; independent observation found hourly forecast | 1 / 11.915 s | Verify the requested Shanghai source and complete weather-to-note creation, independent content check and cleanup. |
| LI21: todo entry | Notes todo tab opened; independent observation found todo controls | 7 / 34.626 s | Verify uniquely marked items, reminder dates, baseline preservation and cleanup. |
| BA04: file manager entry | File Manager home opened; independent observation found categories | 1 / 6.499 s | Obtain authorized health export and verify the exact file and archive. |

The todo run explored Calendar before reaching Notes. A follow-up request naming Notes returned to its previously selected todo tab in one action / 7.525 s, with todo reminder controls independently observed. This sample benefited from retained tab state and a more specific prompt; it is not a controlled product-speed benchmark. The durations are product GUI RunLog intervals for distinct tasks, not comparable before/after measurements and not total host acceptance time. The private run logs and UI observations remain ignored; no unrelated data is published.

Before the runs, the device was locked and the first exploratory submission was blocked before dispatch. Following renewed user instruction, the observed vivo PIN keyboard was used locally, and device-unlocked state was verified before proceeding. No credential is recorded here. A prior locked attempt and the subsequent exploration must not be represented as a successful full regression.

A later direct-entry attempt was also blocked before dispatch after the phone locked again. A separately authorized local unlock succeeded; the direct-entry run then completed. USB keep-awake was restored to `0` and read back.
