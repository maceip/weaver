# NEXT_STEPS — handoff to the local agent

The remote agent that built this branch **cannot reach `adb` or the test
device**. Real-phone testing and any device-dependent fix must be done by a
local agent with the Pixel plugged in. This doc is the handoff.

Branch: `claude/promote-sample-gradle-target-El32F` (also merged to `main`).
Everything below builds in CI; **nothing has run on real hardware.**

---

## Build / install / observe

```bash
./gradlew :app:installDebug
adb logcat -c && adb logcat -s \
  WeaverJS:* WeaverNet:* WeaverWebView:* WeaverMain:* WeaverAuth:* \
  WeaverRouter:* WeaverRemoteXport:* WeaverBridge:* WeaverAttest:* \
  AndroidRuntime:E Dari:* | tee /tmp/weaver.log
```

DevTools against the live WebView: `chrome://inspect/#devices` on a desktop
(USB debugging on, screen unlocked).

Run the unit + E2E suites (no device needed):
```bash
./gradlew :app:testDebugUnitTest        # 61 tests
cd server && npm install && npm test    # 23 tests
```

---

## Device test plan — the transport handoffs

`BridgeRouterE2eTest` covers all six scenarios as unit tests with fakes.
On real hardware, verify the same with `WeaverRouter` logcat (it logs every
`route X -> Y`) and the Dari notification (every bridge message). Watch
`activeId` flips:

| # | Scenario | Expect |
|---|---|---|
| 1 | local webview authes, then remote bridge connects | stays `local` |
| 2 | local never authes (404/landing), remote up | routes `remote` |
| 3 | remote first, cookies shared into webview | flips `remote -> local` |
| 4 | remote first, user signs into local webview | flips `remote -> local` |
| 5 | two phones, same Google account | each phone's router independent; server puts both on one `StitchSession` |
| 6 | two users on a shared remote, each authes locally | each diverges to its own local session |

The router prefers local-when-`Ready`, else remote; a flapping backend trips
its circuit breaker (3 consecutive failures, 20s cooldown).

---

## Secrets to set before the remote path / signing work

Repo → Settings → Secrets, and the server's env:

| Secret / env | Where | What |
|---|---|---|
| `WEAVER_KEYSTORE_BASE64` + `WEAVER_KEYSTORE_PASSWORD` + `WEAVER_KEY_ALIAS` + `WEAVER_KEY_PASSWORD` | GitHub repo secrets | The release signing keystore. Generate once: `keytool -genkeypair -v -keystore weaver-release.jks -alias weaver -keyalg RSA -keysize 4096 -validity 10000`, then `base64 weaver-release.jks`. |
| `ServerClientIds.WEB_OAUTH` (`MainActivity.kt`) | source | Google OAuth Web client id for Credential Manager. |
| `ServerEndpoints.SESSION_BRIDGE` (`MainActivity.kt`) | source | `wss://<alb-host>/bridge`. |
| `WEAVER_ALLOWED_AUDIENCES` | server env | Must equal `WEB_OAUTH`. |
| `WEAVER_SIGNING_DIGESTS` | server env | Base64 SHA-256 of the signing cert: `keytool -list -v -keystore weaver-release.jks` → SHA-256 → base64. This is the attestation allowlist. |

Until the OAuth id is set the app runs in **devMode** (anonymous account,
loads Stitch unauthenticated). Until `WEAVER_SIGNING_DIGESTS` is set the
server's attestation gate fails closed — use `WEAVER_DEV_SKIP_ATTESTATION=1`
for local server dev.

---

## Open task 1 — JVM attestation sidecar (replace the TS port)

`server/src/attestation/verifier.ts` is a focused Node port of
`github.com/android/keyattestation`. The full library is Kotlin/JVM and
should be **run, not reimplemented** — it ships a `VerifierCli` entry point.

Plan:
1. Add a `server/attestation-jvm/` Gradle module that depends on
   `com.android.keyattestation` (or vendors the cloned source) and builds a
   fat jar exposing `VerifierCli`.
2. In `server/Dockerfile`, add a JRE (the Playwright base image is Ubuntu —
   `apt-get install -y openjdk-21-jre-headless`) and copy the jar in.
3. Replace `verifyAttestation()`'s body with a subprocess call to the jar
   (`java -jar verifier.jar <pemfile>`), parse its result. Keep the same
   `AttestationResult` interface so `index.ts` / tests don't change.
4. `attestation.test.ts` already exercises a real chain — it should pass
   unchanged against the sidecar, proving parity.

Cost: ~50 MB JRE in the image, one subprocess per WS upgrade (ms-scale).
Win: Google's real verification — revocation, full constraints, future
format changes — with zero drift.

## Open task 2 — server session-multiplexing test

`BridgeRouterE2eTest` covers the *client* side of scenarios 5 & 6. The
*server* side (two sockets with the same Google `sub` share one
`StitchSession`; different `sub` → isolated) is untested because
`BridgeGateway` needs a real Chromium via `ContextManager`.

Plan: extract a `SessionProvider` interface (`sessionFor(identity)`) that
`ContextManager` implements, and an interface from `StitchSession` for the
methods the gateway uses (`attachDevice`/`detachDevice`/`deviceCount`/
`sessionId`/`onOutbound`/`sendInbound`). Then a `gateway.test.ts` with fakes
asserts: two `hello`s with the same identity → one session, outbound
broadcasts to both sockets; different identity → isolated session.

---

## State of the tree

- `:app` builds; 61 unit/E2E tests green. **Crashes on real launch** —
  see `CRASH_GUESS.md` for the ranked diagnosis (top guess: material3 1.4
  vs adaptive-navigation3 alpha ABI skew).
- `server/` typechecks; 23 tests green (incl. a real attestation chain and
  the wrb.fr parser against the recorded HAR).
- CI (`Build & Release`): tests + builds debug/release APK, bumps the
  version tag, cuts a GitHub release with build-provenance attestation.
- Architecture: hidden WebView + native Compose, bridge over a router that
  circuit-breaks between the local WebView and the AWS session bridge;
  Android Key Attestation gates the bridge.

First thing to do on the device: install, capture the logcat above, and
work `CRASH_GUESS.md` top-down.
