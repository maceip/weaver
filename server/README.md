# Weaver Remote Session Bridge

A high-performance Node service that owns a real Chromium and projects an
authenticated Stitch session to Weaver's Android client. It exists so the
device never has to perform Google sign-in inside a WebView (no autofill,
triggers 2FA fan-out across the user's other devices).

## Why this exists

The Android app does a native Google login (Credential Manager). That
yields a Google **id_token** but *not* a Stitch web session — Stitch
sessions are minted by a `gapi` OAuth handshake that, run inside a
WebView, behaves badly. Rather than fight it on-device, the device
attaches to a remote browser that already holds the session.

The bridge protocol is transport-agnostic: the exact same
`Outbound` / `Inbound` JSON messages the on-device `Bridge` speaks
flow over a WebSocket to this server instead of over a local
`@JavascriptInterface`. The server injects the *same* content script
and fetch interceptor (`src/browser/injected.ts`, ported from the
Kotlin `StitchContentScript` / `StitchFetchInterceptor`) into the
remote page.

## Identity, multiplexing, isolation

```
Google id_token ──verify(JWKS)──> sub ──shard key──> BrowserContext
```

- **Identity** is the Google `sub` claim. The server never trusts a
  client-supplied user id — it verifies the id_token against Google's
  JWKS and extracts `sub` itself.
- **Multiplexing**: a user with two phones presents the *same* `sub`
  twice. Both sockets attach to the *same* `StitchSession` (one
  `BrowserContext`, one Stitch `Page`). Outbound events broadcast to
  every socket on the session, so both phones see the same canvas.
- **Isolation**: a different developer presents a different `sub` →
  a different `BrowserContext` with its own cookie jar → Stitch only
  ever renders that context's projects. Isolation is structural; there
  is no cross-user filtering to get wrong.

## How the per-context Stitch session is established

Deferred — per the project brief, "how that authentication happens on
the remote server is left for an exercise for us later." The server
ships the *plumbing*: `ContextManager` can be handed a cookie set per
identity (`hydrateContext(identity, cookies)`) and will inject it
before the first Stitch navigation. Acquiring those cookies (a
server-side OAuth dance, a shared service account, or an operator
paste) is a separate workstream.

## Components

| File | Responsibility |
|---|---|
| `src/index.ts` | Fastify app: `/healthz`, WebSocket upgrade, lifecycle |
| `src/config.ts` | Env-driven config |
| `src/auth/verifier.ts` | Google id_token verification via JWKS, `sub` extraction |
| `src/browser/contextManager.ts` | One Chromium; per-identity `BrowserContext` pool with idle eviction |
| `src/browser/stitchSession.ts` | One Stitch `Page` per context; injects scripts; fans outbound events to sockets |
| `src/browser/injected.ts` | Content script + fetch interceptor (ported from Kotlin) |
| `src/bridge/protocol.ts` | WS envelope + the `Outbound`/`Inbound` mirror of the Kotlin schema |
| `src/bridge/gateway.ts` | WS gateway: auth handshake, routes frames to/from the right session |

## Authorization: the attestation gate

Before any bridge traffic — before the id_token is even read — the WebSocket
upgrade must carry a valid **Android Key Attestation** in the
`X-Weaver-Attestation` header. `src/attestation/verifier.ts` (ported from the
focused checks of [github.com/android/keyattestation](https://github.com/android/keyattestation))
verifies it:

1. the X.509 chain validates and roots in a Google hardware attestation CA
   (`src/attestation/google-attestation-roots.json`),
2. the leaf's Key Description extension names our package (`WEAVER_APP_PACKAGE`),
3. a signing-cert digest in the extension is allowlisted
   (`WEAVER_SIGNING_DIGESTS` — the key the GitHub CI build signs with).

A desktop or scripted client cannot produce such a chain — only a genuine
install of our CI-signed app on a real device can. This is the baseline that
stops random users from hitting the API from the web. It is layered *under*
the per-user id_token check (which establishes *which* user).

`WEAVER_SIGNING_DIGESTS` is empty by default — the gate fails closed. Set
`WEAVER_DEV_SKIP_ATTESTATION=1` to bypass it in local development only.

Deferred vs. the full keyattestation library: certificate revocation, the
full authorization-list constraint set, and a server-issued challenge for
replay resistance (today the leaf challenge is client-generated; the
chain + app-identity check does not depend on it).

## Transport protocol

WebSocket. Each frame is JSON with a `kind` envelope:

```
client → server   { "kind": "hello", "idToken": "...", "deviceId": "..." }
server → client   { "kind": "ready", "identity": "<sub-hash>", "sessionId": "..." }
client → server   { "kind": "inbound",  "payload": <Inbound JSON> }
server → client   { "kind": "outbound", "payload": <Outbound JSON> }
both              { "kind": "ping" } / { "kind": "pong" }
server → client   { "kind": "error", "code": "...", "message": "..." }
```

`payload` is exactly the bridge JSON — `submit_prompt`, `nodes_updated`,
`session_progress`, etc. — so neither side needs a second schema.

## Running locally

```bash
cd server
npm install
npx playwright install --with-deps chromium
npm run dev          # ts-node, :8080
```

```bash
# smoke test the WS (needs a real Google id_token)
WEAVER_DEV_TRUST_TOKENS=1 npm run dev   # accepts unverified tokens, dev only
```

## AWS

`Dockerfile` builds a self-contained image (Chromium bundled via the
Playwright base image). `deploy/ecs-task-definition.json` runs it on
ECS Fargate — 2 vCPU / 4 GB, which comfortably holds one Chromium with
a few dozen contexts. Horizontal scale: run N tasks behind an ALB with
**session affinity on the `sub`** (the shard key) so a user's two
phones always land on the task holding their `BrowserContext`. See
`deploy/README.md`.
