<p align="center">
  <img src="docs/assets/logo.webp" alt="Stitch" width="160" height="160" />
</p>

<h1 align="center">Stitch</h1>

<p align="center">
  <a href="https://github.com/maceip/stitch/actions/workflows/build-apk.yml"><img alt="Build & Release" src="https://img.shields.io/github/actions/workflow/status/maceip/stitch/build-apk.yml?branch=main&label=build" /></a>
  <a href="https://github.com/maceip/stitch/actions/workflows/docs.yml"><img alt="Website" src="https://img.shields.io/github/actions/workflow/status/maceip/stitch/docs.yml?branch=main&label=site" /></a>
  <a href="https://github.com/maceip/stitch/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/maceip/stitch?include_prereleases" /></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/maceip/stitch" /></a>
  <a href="https://developer.android.com/"><img alt="Android API" src="https://img.shields.io/badge/Android-API_34%2B-3DDC84?logo=android&logoColor=white" /></a>
</p>

> Multi-screen design studio for Android foldables. Prompt the layout, ship the pixel.

Stitch is a native Android app for designing mobile UI by prompt instead of by hand.
Type what you want, get a real screen back. The foldable's hinge is the editor:
tabletop posture turns the bottom panel into a physical control deck, the hinge angle
scrubs A/B blends between candidates, and "fire-and-fold" hands off generation to the
cover screen so you can keep moving.

## Why

Pushing rectangles around a vector canvas is the slow path to a screen.
A prompt with the right execution layer beats it on speed and on consistency —
spacing, type ramp, component variants, and dark/light pairs come out aligned
because the generator owns the system, not the designer's muscle memory.

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│  Pixel 10 Pro Fold — Stitch (Kotlin / Jetpack Compose)   │
│  · posture-aware Compose UI (tabletop, fold, cover)      │
│  · prompt + version filmstrip + agent orb                │
│  · WebView host w/ bridge instrumentation (dari-core)    │
└──────────────┬───────────────────────────────────────────┘
               │ attested session (JWT, x509)
               ▼
┌──────────────────────────────────────────────────────────┐
│  Session bridge — Fastify (Node 20) + Playwright         │
│  · owns an authenticated upstream design session         │
│  · projects pixel-correct frames to the Android client   │
│  · server-side attestation verified by JVM sidecar       │
└──────────────────────────────────────────────────────────┘
```

- **`app/`** — Android client (Jetpack Compose, posture-aware layouts).
- **`dari-core/`, `dari/`, `dari-noop/`** — WebView bridge inspection layer used inside the client.
- **`server/`** — TypeScript Fastify session bridge with a Playwright browser
  worker. Attestation is verified by a small JVM sidecar
  (`server/attestation-jvm/`).
- **`www/`** — Vite + React landing site ([stitch.secure.build](https://stitch.secure.build)).
- **`documentation/`** — Next.js docs (fumadocs).

## Quickstart

```bash
# Android client
./gradlew :app:assembleDebug

# Session bridge
cd server && npm install && npm run build && npm start

# Landing site
cd www && npm install && npm run dev
```

## License

Apache-2.0 — see [LICENSE](LICENSE).
