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
