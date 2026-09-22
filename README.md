# FC+ Android

Native Android FC transfer-market companion built with Kotlin, Jetpack Compose, and Mozilla GeckoView.

## v0.3.0 — Market Bridge

This milestone begins migrating FC+ from the Violentmonkey userscript into the native Android app.

- Bundled GeckoView WebExtension running inside EA pages.
- Read-only Transfer Market telemetry from visible and background Gecko sessions.
- Search Results parser for Start Price, Current Bid, Buy Now, and remaining time.
- Native Market Brain: Min BIN, Stable BIN, Min Bid, Max Bid, estimated profit, ROI, opportunity count, and confidence.
- Quick Flip / Chem Flip selector.
- Adjustable minimum profit and minimum ROI.
- Gemini-style live market recommendation card.
- CAPTCHA/security/restriction text hard-stops the background service.
- Dry Run remains the default and v0.3.0 does not submit bids, Buy Now, or listings yet.

### Test

1. Install v0.3.0 and confirm Build 8.
2. Open EA Login.
3. Go to Transfers and perform a normal player search.
4. Leave Search Results open long enough for FC+ to read the listing cards.
5. Return to Brain.
6. LIVE MARKET should show Min BIN, Stable, Min Bid, Max Bid, and opportunities.
7. Start Background and verify the service survives when FC+ is minimized.

Once this telemetry is verified on the real EA UI, the controlled bid/rebid/relist executor can be migrated behind the Dry Run/Live gate.
