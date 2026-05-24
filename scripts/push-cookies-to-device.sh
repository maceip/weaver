#!/usr/bin/env bash
# Push exported cookies to a connected device and run CookieRestoreTest once.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JSON="${1:-$ROOT/.local-secrets/chrome-canary-cookies.json}"
REMOTE="/sdcard/Download/weaver-cookies-import.json"

if [[ ! -f "$JSON" ]]; then
  echo "Missing $JSON — run: python3 scripts/extract-chrome-cookies.py" >&2
  exit 1
fi

adb devices | grep -w device >/dev/null || {
  echo "No adb device. Connect Pixel and enable USB debugging." >&2
  exit 1
}

adb push "$JSON" "$REMOTE"
cd "$ROOT"
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.weaver.app.CookieRestoreTest

echo "Done. Cookies should be in WebView; check logcat WeaverCookieRestore."
