# Focused repair regressions

Use these existing tests when changing the associated production boundary; select the affected classes and run the repository's configured offline Gradle environment. Do not treat passing JVM tests as proof of device behavior.

```sh
gradle :app:testDevelopStandardDebugUnitTest \
  --tests 'cn.com.omnimind.bot.agent.personal.PersonalContextAndroidTest' \
  --tests 'cn.com.omnimind.bot.plugin.official.OmniFlowManagementToolHandlerTest' \
  --tests 'cn.com.omnimind.bot.agent.ChatLinkPreviewRepositoryTest' \
  --tests 'cn.com.omnimind.bot.agent.BrowserToolResultPayloadsTest' --offline
```

- Contacts: test unreadable provider rows, filtered and unfiltered queries, an actually empty provider, and a readable provider with a filtered no-match. A provider reporting an unreadable ID is not evidence that no contact exists; an uncertain write must not be replayed through another channel without reliable independent confirmation. These tests do not prove every OEM redaction mode is detectable.
- RunLog: a structurally valid failed run must be readable while preserving its failed outcome; absent/malformed results and failures from other tools remain failures. A successful read is evidence access, not proof that the model interprets it correctly.
- Link preview: test Chinese sentence punctuation, normal Unicode paths, percent-encoded punctuation, and parenthesized URLs. The extractor boundary does not disable automatic fetching or prove a zero-network policy.
- Browser surface: both attached and unattached embedded WebViews retain actual attachment state and unverified user visibility. Attachment alone does not prove visibility or that a foreground app was used. For a device check, use a uniquely owned local static page and test tab, record actual requests, and clean only verified test resources; do not inspect or close unrelated tabs.

For GUI local-date changes, run `:omniflow-android:testDebugUnitTest` with `OmniFlowStateHostTest` and the existing Python override tests. The APK and the separately installed Python override are both part of the runtime; verify each actual device version before interpreting a trial. The device clock is context, not proof that a model used it or chose the right calendar value.

For each changed boundary, consult the linked `docs/testing/android-scan-*` record for prior evidence, scope and unresolved side effects. Run at most the affected, authorized real-device check with unique fixture and independent readback; distinguish installed version, tool data, model interpretation, and cleanup. Do not touch an existing app's permission merely to reproduce a historical prompt. If a test requires an unavailable identity check or an unowned baseline, record the block without changing the setting.
