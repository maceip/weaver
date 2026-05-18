# CRASH_GUESS — immediate-launch crash, top guess

> The remote agent that built this branch couldn't reach `adb`. The APK
> crashes immediately on real-device launch. This is the remote agent's
> single best guess at the cause, separated from the longer ranked list
> in `NEXT_STEPS.md` so a local iterator can verify it in one step
> before considering the alternatives.

## Top guess: Compose / Material3 runtime version skew

`gradle/libs.versions.toml` carries:

```toml
material3Expressive          = "1.4.0"
androidxMaterial3Adaptive    = "1.1.0"
androidxMaterial3AdaptiveNav3 = "1.0.0-alpha03"
```

`material3 1.4.0` is forced (in anticipation of an eventual
`MaterialExpressiveTheme` swap) while `adaptive 1.1.0` and
`adaptive-navigation3 1.0.0-alpha03` were built against an earlier
`material3`. The classpath has the 1.4 jar at runtime; the alpha03
nav3-adaptive code calls API entry points that have moved or changed
signatures since the version it was compiled against. The symptom is
a `NoSuchMethodError`, `AbstractMethodError`, or `NoClassDefFoundError`
at class load — Android's classloader resolves the call lazily, so
the crash typically fires the moment the first composable on the
affected path enters composition (often inside `NavDisplay` /
`rememberSupportingPaneSceneStrategy`).

## Verify in one command

```bash
adb logcat -d \
  | grep -E 'NoSuchMethodError|AbstractMethodError|NoClassDefFoundError|adaptive|material3' \
  | head -20
```

A hit on any of those three error types pointing at
`androidx.compose.material3.adaptive*` or `androidx.compose.material3.*`
confirms this guess.

## Fix

One-line change in `gradle/libs.versions.toml` — drop the override
and let the Compose BOM (`composeBom = "2026.02.01"`) pick a
coherent `material3` version:

```diff
-material3Expressive = "1.4.0"
+# material3Expressive intentionally unset — the Compose BOM picks
+# a material3 version compatible with adaptive 1.1.0 and
+# adaptive-navigation3 1.0.0-alpha03.
```

And remove the corresponding library entry:

```diff
-androidx-compose-material3-expressive = { group = "androidx.compose.material3", name = "material3", version.ref = "material3Expressive" }
```

The `androidx-compose-material3` entry from the BOM stays. Any
`MaterialExpressiveTheme` references would need to be reverted to
`MaterialTheme` — `app/src/main/java/com/weaver/app/ui/theme/Theme.kt`
was already swapped to `MaterialTheme` in commit `69de616`, so this
part should be a no-op for source files.

Rebuild, reinstall, re-launch. If the crash signature changes (e.g.
to a different exception), drop down to guess #2 in `NEXT_STEPS.md`
("`WeaverApp` Application class fails to load" — likely
`kotlinx-serialization` plugin transformation of `ProjectRepository`).

## If this guess is wrong

The full ranked list of five candidate causes with one-line verify
commands for each lives in `NEXT_STEPS.md` under "Ranked guesses for
the immediate-launch crash". Save the relevant `AndroidRuntime: FATAL
EXCEPTION` block from `adb logcat` as `crash-<sha>.txt` and prune
the list against it.
