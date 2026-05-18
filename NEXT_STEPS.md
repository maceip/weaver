# NEXT_STEPS — Weaver mobile client

A handoff for a **local** Claude Code agent (or any developer) with the test
device plugged in. The remote agent that built this branch can push commits
and reason about HARs but **cannot reach `adb`** — so this doc is the
substitute for "be in the room while iterating."

Branch: `claude/promote-sample-gradle-target-El32F`. Last commit before
handoff: `3868e87` (CI fix). APK builds green; on first launch on a real
device it **crashes immediately**.

---

## Iteration loop on device

Use the same logcat tags throughout so every change is comparable:

```bash
# rebuild, install, watch — one command
./gradlew :app:installDebug \
  && adb logcat -c \
  && adb logcat -s WeaverJS:* WeaverNet:* WeaverWebView:* \
                   WeaverMain:* WeaverAuth:* WeaverBridge:* \
                   AndroidRuntime:E Dari:* \
  | tee /tmp/weaver.log
```

When you see the crash, the relevant lines are between
`AndroidRuntime: FATAL EXCEPTION` and the next `Process:` boundary.
Save that block as `crash-<sha>.txt` next to this file and commit it
in the same change that diagnoses it — future-you needs the artifact.

For the WebView side specifically:

```bash
# attach Chrome DevTools at the live WebView (USB debugging on, screen unlocked)
# desktop browser: chrome://inspect/#devices
adb forward tcp:9229 localabstract:chrome_devtools_remote
```

Console logs are also mirrored to logcat as `WeaverJS:*` so `console.log()`
sprinkled into `StitchContentScript.kt` / `StitchFetchInterceptor.kt`
shows up in the same stream without DevTools.

---

## Ranked guesses for the immediate-launch crash

I don't have the stack trace, so this is by likelihood, not certainty.
Each item has a `# verify` line — run that, save the output to the
crash artifact, prune the list.

**1. Compose / Material3 runtime version skew.** I forced
`material3 = "1.4.0"` (for the eventual MaterialExpressiveTheme switch)
while `material3-adaptive = "1.1.0"` and `adaptive-navigation3 = "1.0.0-alpha03"`
were built against an earlier material3. A `NoSuchMethodError` or
`AbstractMethodError` at class load is the symptom.

```bash
# verify
adb logcat -d | grep -E 'NoSuchMethodError|AbstractMethodError|NoClassDefFoundError' | head -20
```

Fix path: drop the `material3Expressive = "1.4.0"` override from
`gradle/libs.versions.toml` and let the Compose BOM (`2026.02.01`)
pick its own coherent material3 version. The `MaterialExpressiveTheme`
swap is gated on the API becoming public anyway — there's no benefit
to forcing 1.4.0 today.

**2. `WeaverApp` Application class fails to load.** The manifest declares
`android:name=".WeaverApp"`. If kotlinx-serialization's compiler plugin
mis-generated the `Project.serializer()` for `ProjectRepository`, the
class lazy-init throws and the process dies before MainActivity runs.

```bash
# verify
adb logcat -d | grep -E "Unable to instantiate application|ClassNotFoundException.*WeaverApp" | head -5
```

Fix path: replace `kotlinx-serialization` in `ProjectRepository` with
`org.json.JSONArray/JSONObject` for the persistence round-trip. Tiny
change, removes the plugin from the project's critical path.

**3. `WebView.setWebContentsDebuggingEnabled(true)` thrown by an OEM
patch.** Some manufacturer WebView builds throw on this when the
process is not debuggable. Unlikely on a Pixel but cheap to rule out.

```bash
# verify
adb logcat -d | grep -E 'setWebContentsDebuggingEnabled|WebView' | head -10
```

Fix path: wrap in `if (BuildConfig.DEBUG && (applicationInfo.flags and FLAG_DEBUGGABLE != 0))`.

**4. The `WebViewClient.shouldInterceptRequest` MITM that rewrites
the Stitch outer HTML.** It opens a `HttpURLConnection`, reads bytes,
patches the sandbox attribute. If the connection throws (network not
ready, HTTPS handshake fails, etc.) the WebView's request thread may
propagate the exception. Should be caught by my `runCatching`, but
worth confirming.

```bash
# verify
adb logcat -d | grep WeaverWebView | head -20
```

Fix path: short-circuit the rewrite when `req.isForMainFrame` returns
false or the response body is empty. Already conservative; this is
unlikely to be #1 cause but cheap to harden.

**5. The `Nav3` `SupportingPaneSceneStrategy` initialization order.**
`rememberSupportingPaneSceneStrategy<NavKey>(...)` may need the
`Login`-as-NavKey case to opt out of pane metadata, but I only attach
`SupportingPaneSceneStrategy.mainPane()` / `.supportingPane()` to
the post-auth entries. The Login entry has no pane metadata — that
may be valid or may throw at runtime.

```bash
# verify
adb logcat -d | grep -E 'navigation3|SupportingPane' | head -10
```

Fix path: give Login `metadata = SupportingPaneSceneStrategy.mainPane()`
too, OR remove the scene strategy entirely for the pre-auth segment
of the back stack.

**My #1 guess overall is the material3 version skew (#1).** Try that
first — it's a one-line change in `libs.versions.toml`.

---

## What was already fixed (don't redo)

In order of commit, most recent first:

| Commit | Fix |
|---|---|
| `3868e87` | CI: skip `installGitHooks`, pin SDK packages, upload build-reports on failure |
| `1667465` | MITM Stitch's outer HTML to neutralise the iframe `sandbox` attribute so the content script can reach the editor DOM |
| `75c569f` | Four preempts: strip `wv;` from UA, WebView at MATCH_PARENT not 1×1, `devMode` auth bypass when OAuth client id is the placeholder, explicit cookie setup |
| `8e42507` | CI workflow added: builds `weaver-debug-apk` artifact on every push |
| `69de616` | On-device observability: `WebView.setWebContentsDebuggingEnabled(true)`, `onConsoleMessage` → `WeaverJS` logcat, `shouldInterceptRequest` → `WeaverNet` logcat |
| `4206374` | Document-start `fetch` interceptor + wrb.fr parser teeing `StreamCreateSession` and `batchexecute f6CJY` into typed bridge events |
| `a0bd348` | Real DOM selectors from the projects-dashboard fixture; replaced speculative `[data-design-node]` with `[data-testid^="rf__node-"]` |
| `940d01b` | Real composer / send / agent-log selectors from agent-view fixture |
| `b962929` | `DownloadListener` to catch Stitch exports without replaying the protobuf token |
| `3270881` | URL-change → `bindStitchId` so freshly-minted Stitch project ids attach to local drafts |

---

## Architecture in one paragraph

`MainActivity` constructs a headless `WebView` (MATCH_PARENT, alpha=0,
behind the Compose tree) and pre-warms it against
`https://stitch.withgoogle.com/` in `onCreate` before `setContent`.
`WebViewHost` registers `JsBridgeInterface` (`@JavascriptInterface
Android.post`), a document-start fetch interceptor
(`StitchFetchInterceptor`) and a page-finished content script
(`StitchContentScript`). Both inject into the Stitch frame after the
`WebViewClient` rewrites `sandbox="allow-scripts"` to add
`allow-same-origin` so the parent and the srcdoc iframe share an
origin. Typed events flow back through `Bridge` as `Outbound`
sealed-class messages and into `StateFlow`s consumed by Compose:
`bridge.nodes`, `bridge.selection`, `bridge.sessions`,
`bridge.projectThemes`, `bridge.agentLog`. The Compose surface is a
Nav3 back stack (`Login` → `Home` → `Overview` ⇄ `Focused | MultiSelect`)
with a `SupportingPaneSceneStrategy` so the unfolded Pixel Fold
shows Overview + Focused side-by-side.

---

## Priority work after the crash is unstuck

1. **Real OAuth client id** in `MainActivity.ServerClientIds.WEB_OAUTH`.
   Until then `devMode` short-circuits Credential Manager and the
   WebView loads Stitch anonymously — usable for UI work, not for
   actually generating designs.
2. **First end-to-end capture** with the device running: install,
   sign in, type one prompt, screenshot Dari's message list, save
   logcat. Compare what the bridge actually emits against the
   `Outbound` schema. Almost everything else below depends on this
   ground-truth check.
3. **Resolve the iframe sandbox question.** I MITM'd the outer HTML
   to add `allow-same-origin`. If that works, `WeaverJS` will show
   `nodes_updated` events within a few seconds of page load. If it
   doesn't, the failure modes are documented in
   `app/src/main/java/com/weaver/app/webview/WebViewHost.kt#rewriteStitchOuter`.
4. **Surface CookieManager state in Dari** — after Credential Manager
   succeeds we expect a `__Secure-*` session cookie on
   `.stitch.withgoogle.com`. If it's missing, the visible-WebView
   one-time auth path needs wiring (see `AuthController` doc comment).
5. **Wire the project theme.** `Bridge.projectThemes` flows but
   `WeaverTheme` ignores it. Add an optional `tokens: Map<String,String>?`
   parameter and let the user toggle per-project recolouring.
6. **DownloadManager handoff for exports.** `setDownloadListener`
   currently emits `Outbound.ExportComplete` carrying the URL; nothing
   on the Compose side does anything with it yet. Hand it to
   `DownloadManager` + show a snackbar on completion.

---

## Fixture inventory

Under `app/src/test/resources/stitch-fixtures/`:

| File | What it tells us |
|---|---|
| `landing-logged-out.html` | Two CTAs (`Try now` / `Start designing`) trigger gapi-mediated Google sign-in. No labelled "Sign in" button. |
| `landing-prompt.html` | iframe URL is `app-companion-430619.appspot.com/?usegapi=1...` — no project id yet |
| `projects-dashboard.html` | Real React Flow DOM: `[data-testid^="rf__node-"]`, `data-id`, `react-flow__node-node-screen/-design-system`, `.selected`, `transform: translate(...)`, `<img src="lh3.googleusercontent.com/aida/...">` |
| `project-logged-out.html` | Unauthenticated `/projects/<id>` returns a 404 page, not a sign-in redirect |
| `project-agent-view.html` | Real composer `textarea[placeholder*="change or create"]` + Tiptap variant at `.chat-tiptap-v3 .tiptap.ProseMirror` + agent log rows as `span.text-sm.block.whitespace-nowrap.truncate` |
| `project-agent-streaming.html` | Generation-state markers: `data-stitch-pending-src`, `data-stitch-anim-opacity`, `#stitch-agent-cursor`, `.stitch-anim-fade-in` |
| `project-agent-finished.html` | Same DOM after generation completes — no `pending-src` left |
| `new-project-generating.html` | iframe URL `app-companion-430619.appspot.com/projects/<numericId>` — the project id is in the URL |
| `new-project-done.html` | Final state of a freshly generated project |
| `network-trace.har` | One `StreamCreateSession` call (962KB, 287s, 36 wrb.fr frames) + `batchexecute f6CJY` project state polls |
| `network-trace-new-project.har` | New-project flow: `cabgj` mint → `XaOLp` attach files → `f6CJY` state → `StreamCreateSession` (4.6MB with images inline) |
| `network-trace-export-zip.har` | Export → ZIP/HTML goes to `contribution.usercontent.google.com/download?c=<base64-protobuf>` — token is client-built, can't be synthesized |

---

## Bridge schema map

`app/src/main/java/com/weaver/app/bridge/BridgeMessages.kt`:

**Outbound** (`web → native`, decoded from `Android.post(json)`):
`nodes_updated · selection_changed · generation_progress · asset_ready ·
export_complete · agent_log_updated · session_started · session_progress ·
session_finished · project_theme · error`

**Inbound** (`native → web`, posted via
`window.__weaverBridge.receive(json)`):
`submit_prompt · attach · select_preset · select_model · voice_input ·
select_node · clear_selection · synthesize_input · request_export ·
viewport_changed · canvas_action · select_tool`

Selectors live in **one file** — `StitchContentScript.kt` — so when
Stitch breaks them there is exactly one patch point. The fetch
interceptor lives in `StitchFetchInterceptor.kt` with the wrb.fr
parser; URLs and rpcids are documented in its file-level KDoc.

---

## How to grab the latest APK from CI (also: how to know if a build failed)

```bash
# install GitHub CLI once: brew install gh / sudo apt install gh
gh auth login
gh run list --repo maceip/weaver --workflow build-apk.yml --limit 5
gh run download --repo maceip/weaver --name weaver-debug-apk --dir ./apk
adb install -r ./apk/app-debug.apk
```

If the most recent run is red, also pull the `build-reports` artifact —
it carries `**/build/reports/**` (detekt / ktlint / lint / test) and
`**/build/outputs/logs/` from the failed run.

---

*This branch was developed by a remote Claude Code agent that couldn't
reach `adb` or your device. Every commit builds in CI; nothing has
been validated on real hardware. The above is the agent's best guess
at how to close that loop.*
