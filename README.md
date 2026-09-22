# FC+ Android

Native Android shell for FC+ built with Kotlin + Jetpack Compose.

## v0.2.0

This milestone changes the browser engine and redesigns the app:

- Mozilla GeckoView replaces Android WebView for EA Login.
- GeckoView uses the Firefox rendering engine family that already works with EA FC on the target phone.
- No forced portrait or landscape orientation.
- Visible EA session and background session use the same Gecko runtime.
- New Gemini-inspired dark interface:
  - deep navy surfaces
  - blue / violet / pink gradient accents
  - cleaner rounded cards
  - system sans typography tuned to a Gemini-like visual hierarchy
- Version popup and version chip remain visible.
- Dry Run remains enabled by default.
- GitHub Actions continues to build the APK automatically.

Note: Google Sans is not bundled. FC+ uses the Android system sans font with Gemini-style typography and spacing.

## Current test

1. Install v0.2.0.
2. Confirm the startup popup says Version 0.2.0 / Build 6.
3. Open EA Login.
4. Sign in normally.
5. Return to Market Brain.
6. Keep Dry Run enabled.
7. Start Background.
8. Switch apps / lock the phone and verify the Gecko session remains alive.

## Next milestones

- WebExtension bridge for the existing FC+ JavaScript engine
- Quick Flip + Chem Flip candidate model
- FUT.GG / market-data integration
- Native trade history and realized profit
- Supabase Market Brain jobs
