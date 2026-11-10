# Phone Pi benchmark adapters

These are explicit evaluation adaptations. Phone Pi remains the agent runtime; the host supplies owned fixtures and independently verifies effects and cleanup. A tool receipt or model answer is not a task pass. The results and source pins are recorded in `docs/testing/mobileworld-phone-pi-2026-11-10-iteration.md`.

## Scoped calendar MCP

`scoped-calendar-mcp.py` uses the official Python MCP SDK to expose native OmniBot calendar tools through a fixture-bound interface. It binds a selected calendar ID and a unique test-title marker on the host, so the model need not repeat those isolation parameters in every call. Date bounds remain explicit model inputs. An optional `requiredTitlePrefix` enforces a title requirement already stated in the task, rejecting a missing prefix before mutation. It does not choose event names, dates or which events are missing.

Create a private scope file for the current owned fixture:

```json
{
  "marker": "mw-audit-UNIQUE-RUN-ID",
  "calendarId": "YOUR_TEST_CALENDAR_ID",
  "requiredTitlePrefix": "[AUTO] "
}
```

Use a dedicated Python environment with `requirements-scoped-calendar.txt`, then run:

```sh
python scripts/benchmark-adapters/scoped-calendar-mcp.py \
  --scope "$PRIVATE_RUN_DIR/scope.json" \
  --token-file "$OMNIBOT_MCP_TOKEN_FILE" \
  --endpoint http://127.0.0.1:18899/mcp \
  --port 18900
```

The example endpoint assumes an existing authenticated native MCP service and caller-owned port forwarding. Supply only the credential file reference; do not put its contents in task prompts or evidence. The adapter listens on loopback. Caller-owned phone reverse forwarding and teardown remain the harness's responsibility.

The additional `run_gui` tool is specifically for the synthetic MobileWorld Mail clone (`com.gmailclone`), allows one dispatch, and forwards actual native progress events through the same MCP request. It writes a dispatch marker and terminal receipt beside the scope file. This GUI package restriction is instruction guidance, not an enforced application sandbox. Use the original device-access policy and exclude personal messaging, banking and payment applications. After an uncertain send, inspect terminal RunLog and independent fixture state before cleanup; do not replay it. Stop the per-run adapter before starting another run.

GUI execution constraints are placed before the task and explicitly labelled as non-content. Never append fixture restrictions or trace markers directly after a goal that ends with message-body text: the executor can copy them into the outgoing body. Still independently inspect the saved message; prompt separation is not enforcement.

The title constraint and scope checks are harness improvements, not a new Android Calendar permission or a general authorization boundary. The model still needs to discover relevant records, reconcile dates, create the correct events, verify them and accurately report completion. Use a unique marker whose ownership was recorded before setup. Restore only those records and owned Mail/Mattermost fixtures after execution settles.

A local integration control starts a fake native endpoint and proves that a rejected title produces no native write, valid writes bind the selected calendar, and list calls bind both calendar and marker:

```sh
python scripts/benchmark-adapters/test_scoped_calendar.py
```

For the separate original iOSWorld tools and picker adaptation, see [iosworld/README.md](iosworld/README.md). iOS simulator state is separate from Android device state.


## Recorded shift-request acceptance

`audit-shift-run.py` is read-only and accepts one private recorded run directory
plus the known acting fixture user ID. It checks reply root identity, conflict
reason, forwarded request content, unchanged calendar state and cleanup evidence.
It reports exact reply equality separately from business acceptance, and does
not mistake a source-channel citation for an executor instruction.

```sh
python scripts/benchmark-adapters/audit-shift-run.py "$PRIVATE_RUN_DIR" \
  --actor-id "$FIXTURE_ACTOR_ID"
```

The caller must supply the independently collected fixture/readback/cleanup JSON
files; this script does not execute tasks or establish the trust of those files.
When cleaning threaded fixture posts, delete replies before roots: deleting a
root can cascade to its replies. After an unexpected deletion response, read back
the known owned IDs before deciding whether more cleanup is needed.


### Phone Pi file-command admission

`file_command_policy.py` contains the rules exercised by the invoice-copy and
confirmed-delete phone Pi fixture runs. Quoted arguments stay intact when
parsing command sequences. Delete admission tracks `cd` within the owned
workspace and allows only explicitly confirmed fixture paths; a trailing
readback command does not turn a legitimate deletion into an approval denial.
These are narrow evaluation rules, not a general shell parser or security
sandbox. The caller still owns fixture creation, ACP permission replies,
terminal state, independent file-content verification and cleanup. No device
identifier, credential or personal file belongs in this module.

Run the observed regression cases with:
`python3 -m unittest discover -s scripts/benchmark-adapters -p test_file_command_policy.py`.

## Unified Android source catalog

`build_android_scenario_catalog.py` maps all 133 pinned iOSWorld variants into Android capabilities and combines them with the existing 201-task MobileWorld inventory. It conservatively shares reviewed templates without inheriting results. Use `--ios-source`, `--coverage`, `--results` and `--output` to regenerate the JSON and 133-row mapping. See [current mapping and measured results](../../docs/testing/android-unified-2026-11-10.md) and [phone Pi workspace runner](android-workspace/README.md).
