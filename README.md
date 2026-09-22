# FC+ Android

Native Android shell for FC+ built with Kotlin + Jetpack Compose.

## v0.1.0

The first milestone proves the Android architecture before live trading is enabled:

- Native Jetpack Compose dashboard
- Embedded EA FC Web App login/session
- Persistent WebView cookies
- Android to JavaScript bridge
- Foreground service
- Background/headless WebView prototype
- Persistent notification
- Partial wake lock while the service is active
- Dry Run enabled by default
- Automatic hard-stop when common CAPTCHA/security/restriction text is detected
- GitHub Actions APK build

## First test

1. Open the EA Login tab in FC+.
2. Sign in to EA normally.
3. Return to Dashboard.
4. Leave Dry Run enabled.
5. Tap START BACKGROUND.
6. Lock the screen or switch apps for a few minutes.
7. Reopen FC+ and confirm the EA session still reports as active.

Android can still throttle a background WebView on some devices. This milestone measures that behavior on the target Samsung device before trading execution is moved into it.

## Build

Every push to main runs .github/workflows/android-build.yml.

Open Actions -> Build Android APK, choose the newest successful run, and download the FCPlus-Android-debug artifact.

## Next milestones

- Inject the FC+ Quick Flip / Chem Flip engine
- Native trade/job model
- Market Brain candidate feed
- FUT.GG / market-data integration
- Trade history and realized profit
- Supabase job queue
- Optional remote trading worker for true unattended operation
