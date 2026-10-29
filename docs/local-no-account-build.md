# Personal build without an OmniBot account

This variant uses the existing local Agent and model-provider configuration without an OmniBot account.

- Removes the startup account prompt and account settings entries.
- Replaces tutorial registration with a model-provider overview. Model setup can still be skipped.
- Redirects the legacy `/my/account` route to model-provider settings.
- Initializes the account boundary without a backend URL, which selects the existing BYOK path and prevents account refresh and official-provider exposure.
- Hides the account-dependent Na cloud Agent. A previously selected Na profile falls back through the existing enabled-Agent selection logic. Stored profiles and conversation data are retained.
- Keeps third-party provider authentication, MCP credentials, and permission checks intact.

Version: `0.6.3.6-noaccount` (`versionCode=19`). Build with the upstream Flutter/Android toolchain:

```sh
cd ui
flutter pub get --enforce-lockfile
cd ..
./gradlew :app:assembleDevelopStandardDebug -Ptarget=lib/main_standard.dart
```

The resulting debug APK uses the normal package ID, `cn.com.omnimind.bot`. Update an existing debug installation signed with the same local key using `adb install -r`; finish active Agent tasks before updating. Do not uninstall just to update.

Focused validation covers tutorial navigation without account fields, provider configuration, absence of the account settings entry, Na selection fallback, and the existing account-disabled/BYOK routing tests. Building and installing this variant does not establish that a particular external model or MCP account is configured or usable.
