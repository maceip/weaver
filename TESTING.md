# Testing

## What is honest vs not

| Suite | Runs where | What it proves |
|-------|------------|----------------|
| `:app:testDebugUnitTest` | JVM (CI) | Bridge JSON, nav reconcile, circuit breaker, offline outbox — **logic contracts** |
| `server/npm test` | Node (CI) | Session bridge protocol against HAR fixtures |
| `:app:connectedDebugAndroidTest` | **Physical device only** | **One** integration test — real `MainActivity`, real WebView, real `Bridge` |

There are **no** isolated Compose widget tests on device anymore. They only checked that labels render inside `createComposeRule()` and were removed.

## Integration test (device required)

`gradle.properties` sets `android.injected.androidTest.leaveApksInstalledAfterRun=true` so
`connectedDebugAndroidTest` does **not** uninstall the app afterward. Re-running tests
upgrades the same install (`installDebug -r`) and **keeps** WebView cookies and login.

```bash
adb devices   # must show one device
./gradlew :app:connectedDebugAndroidTest
```

`WeaverIntegrationTest.liveStitchSession_nodesFromWebView_exportHitsBridge`:

1. Launches **MainActivity**
2. Signs in (dev-mode when OAuth client id is still the placeholder)
3. Creates a project from the Home composer
4. **Waits up to 90s** for `nodes_updated` from the **WebView** — **fails** if Stitch never publishes nodes (no injection shortcut)
5. Focuses a real node, exports to Figma, **asserts** `request_export` JSON on the live `Bridge`

If this test passes on your machine, the UI → bridge → export path ran against **live** canvas data from Stitch. If it fails, that is expected when the WebView session is empty, offline, or unauthenticated for Stitch itself.

## CI

GitHub Actions runs **unit tests + server tests only**, not `connectedDebugAndroidTest`. Device integration is a **manual / lab** gate until a device farm job exists.

## Naming

JVM tests named `*E2e*` that use `MockWebServer`, `FakeTransport`, or Robolectric are **contract tests**, not product E2E. Prefer renaming to `*Test` when touching those files.
