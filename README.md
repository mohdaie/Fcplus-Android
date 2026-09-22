# FC+ Android 0.4.0

Native Compose / GeckoView app with one shared authenticated mobile EA session.

## Implemented

- Pure JavaScript trading state machine: exact-variant market comparison, tax/ROI-aware ceilings, next legal bid increments, bid/rebid, Buy Now, confirmed wins, listing, expired-item relisting, target rotation.
- Native synchronous write-ahead journal before each financial action. Unconfirmed outcomes block retry and survive process death. No automatic resume after a process restart.
- Persistent per-item ledger; realized profit is recorded once, only from a matching confirmed sale, less 5% tax. UTC daily 100,000 profit stop.
- Budget including outstanding bids, per-card cap, maximum open positions, action interval/count, session deadline, heartbeat timeout and notification Stop.
- Dry Run by default on process restart. Live mode requires an explicit in-app selection. Changing settings is disabled during a session.
- Optional Gemini + Google Search target research with FUTBIN/FUT.GG source links, source display and encrypted on-device API key storage. No EA cookies or credentials sent to Gemini. No hardcoded API keys. Model, edition and platform configurable.
- Diagnostics copy contains parsed market fields and ledger, not raw page HTML/cookies/API keys.

## Verification boundaries — read before live use

This release has deterministic state-machine tests and a CI Android build. It has **not been exercised against an authenticated EA account on a device**. The DOM adapter is deliberately strict: it requires explicit auction IDs, exact card definition IDs, chemistry identity and positive order/sale evidence. If the EA DOM does not expose these fields or uses different selectors/labels, it pauses or refuses live orders. The selectors are compatibility assumptions, not captured production fixtures. Passing CI does not certify live trading compatibility.

Chem Flip currently trades cards **already carrying** the specified chemistry style. Buying/applying consumables is not implemented. AI Scout is user-triggered, requires a user-supplied Gemini API key/quota, and returns candidates rather than guaranteed opportunities. It is search-grounded research, not an official FUTBIN/FUT.GG pricing API integration. Unattended first login and security challenges are not automated.

## Device check

1. Install the debug APK from the successful workflow artifact; verify v0.4.0 / build 9.
2. Open EA Login and sign in. The same page stays mounted when switching to Brain.
3. Open Targets & settings. Add an exact player name, card rating and chemistry style, or configure AI Scout and research candidates.
4. Start in Dry Run. Check the diagnostics for identified card/auction IDs. A missing-ID warning means live trading cannot run on that screen; copy diagnostics for adapter repair.
5. Stop, review limits, and explicitly enable live mode only after Dry Run behaves correctly.
6. If an order outcome is uncertain, inspect the matching Transfer Targets/List screen and press Reconcile. Reconciliation reads observations; it never clears an uncertain order just to resume trading.

## Build and tests

Java 17, Gradle 8.11.1, Android SDK 36. `gradle assembleDebug`.

`node --test tests/*.test.cjs` tests synthetic observations, not live EA DOM captures. Coverage includes bid increments, write-ahead intents, duplicate suppression, restart recovery, rebid ceilings, buy/win/list/sale accounting, stale data, variant ambiguity, budgets, security stops, UTC rollover and multi-position monitoring.

The debug APK is an arm64 build. Local keystore signing follows the existing workflow; if Android rejects an update because the old debug signing key differs, do not uninstall without preserving your existing session/ledger.
