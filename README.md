# Claude Usage (Android)

Shows how close your Claude limits are and when they reset. The phone counterpart of
the Windows tray app (`Y:\Projects\ClaudeUsageTray`), in its "Mono" look.

- **Status bar:** the session percent drawn as the notification icon (`!!` at 100%).
- **Shade card:** expand the notification for a bar per limit, reset times, refresh.
- **Widget:** session and highest weekly limit, resizable from 2×1 to 4×2.
- **Alerts:** once per window at 75 / 90 / 100%, and a reset notice after a window that
  had alerted.

## How it gets the numbers

Sign in to claude.ai inside the app (email code; Google sign-in is blocked in WebViews).
The session stays in the WebView cookie jar. Every 15 minutes (configurable) WorkManager
calls `claude.ai/api/organizations/{org}/usage` with it. That endpoint is unofficial and
can change; the parser skips fields it doesn't recognise.

## Build and install

    ./gradlew :app:testDebugUnitTest :app:assembleDebug
    adb install -r -d app/build/outputs/apk/debug/app-debug.apk

`local.properties` needs `sdk.dir`. Toolchain matches Nudge: AGP 9.0.1, Kotlin 2.3.20,
compileSdk 36, minSdk 29.

Debug builds have Settings › debug › simulate, which replaces the network reading with a
chosen session percent so the icon, card, widget and alerts can be checked without
using any real quota.

## Android notes

- The live notification channel is default importance with no sound. Low importance puts
  it in the shade's "Silent" group, and the status bar hides those icons.
- Alerts post with a group summary. Android 16 bundles stray notifications and
  summary-less groups together under the app icon, which would hide the percent glyph.
