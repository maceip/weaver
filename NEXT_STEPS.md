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

## Open task 1 — JVM attestation sidecar — DONE

The Node port of `github.com/android/keyattestation` is replaced by Google's
real Kotlin/JVM library, vendored and run as a sidecar.

- `server/attestation-jvm/` — Gradle module vendoring the keyattestation
  source (`src/main/kotlin/com/android/...`, Apache-2.0) plus a thin
  `com.weaver.attestation.Sidecar` CLI that emits one line of JSON. The
  `com.gradleup.shadow` plugin builds `weaver-attestation-verifier.jar`.
- `server/Dockerfile` is now multi-stage: an `eclipse-temurin:21-jdk` stage
  builds the jar; the runtime stage adds `openjdk-21-jre-headless`, copies
  the jar to `/app/attestation-verifier.jar`, and sets
  `WEAVER_ATTESTATION_JAR`.
- `verifyAttestation()` keeps the cheap input checks (parse, length,
  validity window → `cert_expired`) and the package/digest/challenge policy,
  but delegates the cryptographic chain validation + identity extraction to
  the sidecar. Same `AttestationResult` interface — `index.ts` unchanged.
- `attestation.test.ts` passes unchanged against the sidecar (8/8). `npm
  test`'s `pretest` builds the jar via the gradle wrapper if absent;
  `npm run build:attestation` builds it explicitly.

Cost: JRE in the image, one ~350 ms JVM subprocess per WS upgrade.
Win: Google's real verification — revocation, full constraints, future
format changes — with zero drift.

## Open task 2 — server session-multiplexing test — DONE

`BridgeGateway` now depends on the extracted `SessionProvider` /
`BridgeSession` interfaces (`server/src/bridge/sessionTypes.ts`) instead of
the concrete `ContextManager` / `StitchSession`. `server/test/gateway.test.ts`
(7 tests, fake socket + provider) covers the server side of scenarios 5 & 6:
two `hello`s with the same Google `sub` → one shared session, outbound
broadcasts to every device, inbound merges; different `sub` → isolated
sessions; close detaches one device; bad token / inbound-before-hello refused.

---

## State of the tree

- `:app` builds; 61 unit/E2E tests green. **Crashes on real launch** —
  see `CRASH_GUESS.md` for the ranked diagnosis (top guess: material3 1.4
  vs adaptive-navigation3 alpha ABI skew).
- `server/` typechecks; 30 tests green (incl. a real attestation chain
  verified by the JVM sidecar, gateway session-multiplexing, and the wrb.fr
  parser against the recorded HAR).
- CI (`Build & Release`): tests + builds debug/release APK, bumps the
  version tag, cuts a GitHub release with build-provenance attestation.
- Architecture: hidden WebView + native Compose, bridge over a router that
  circuit-breaks between the local WebView and the AWS session bridge;
  Android Key Attestation gates the bridge.

First thing to do on the device: install, capture the logcat above, and
work `CRASH_GUESS.md` top-down.
