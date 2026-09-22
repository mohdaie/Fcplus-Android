# FC+ Android 0.4.1

Native Compose / GeckoView app with one shared authenticated mobile EA session.

## Implemented

- Pure JavaScript trading state machine: exact-variant market comparison, tax/ROI-aware ceilings, next legal bid increments, bid/rebid, Buy Now, confirmed wins, listing, expired-item relisting, target rotation.
- Native synchronous write-ahead journal before each financial action. Unconfirmed outcomes block retry and survive process death. No automatic resume after a process restart.
- Persistent per-item ledger; realized profit is recorded once, only from a matching confirmed sale, less 5% tax. UTC daily 100,000 profit stop.
- Budget including outstanding bids, per-card cap, maximum open positions, action interval/count, session deadline, heartbeat timeout and notification Stop.
- Dry Run by default on process restart. Live mode requires an explicit in-app selection. Changing settings is disabled during a session.
- Optional Gemini + Google Search target research with FUTBIN/FUT.GG source links, source display and encrypted on-device API key storage. No EA cookies or credentials sent to Gemini. No hardcoded API keys. Model, edition and platform configurable.
- Read-only EA service observer taps the web app's own Transfer Market, Transfer Targets and Transfer List responses and binds exact auction/card metadata back to visible rows. It does not capture authentication headers or submit transactions; financial actions still pass through the strict visible-UI adapter and native permit gate.
- DOM parsing remains as a fallback when EA services are unavailable. Ambiguous row-to-auction matches are intentionally left unbound so the trading engine cannot act on them.
- A Brain-screen Reload EA action reloads the shared mobile Gecko session while trading is stopped, useful after login/session/UI failures.
- Diagnostics copy contains parsed market fields and ledger, not raw page HTML/cookies/API keys.

## Verification boundaries — read before live use

This release has deterministic state-machine tests and a CI Android build. It has **not been fully exercised against an authenticated EA account on a device**. v0.4.1 reduces reliance on brittle HTML by observing EA's own read-only item-service responses, then only binds an observed auction to a visible row when the visible name/rating/prices form one unique match. If that binding cannot be proved, the auction ID is withheld and live orders are blocked. The DOM selectors and service shapes remain compatibility assumptions until confirmed on-device; passing CI does not certify live trading compatibility.

Chem Flip currently trades cards **already carrying** the specified chemistry style. Buying/applying consumables is not implemented. AI Scout is user-triggered, requires a user-supplied Gemini API key/quota, and returns candidates rather than guaranteed opportunities. It is search-grounded research, not an official FUTBIN/FUT.GG pricing API integration. Unattended first login and security challenges are not automated.

## Device check

1. Install the debug APK from the successful workflow artifact; verify v0.4.1 / build 10.
2. Open EA Login and sign in. The same page stays mounted when switching to Brain.
3. Open Targets & settings. Add an exact player name, card rating and chemistry style, or configure AI Scout and research candidates.
4. Start in Dry Run. Run a normal EA market search and check diagnostics. "EA service adapter: X/Y exact rows bound" confirms the read-only service observer is matching EA objects to the visible auctions. Any unbound or missing-ID row remains ineligible for live execution.
5. Stop, review limits, and explicitly enable live mode only after Dry Run behaves correctly.
6. If EA gets stuck after login/navigation, stop the trader and use Reload EA. If an order outcome is uncertain, inspect the matching Transfer Targets/List screen and press Reconcile. Reconciliation reads observations; it never clears an uncertain order just to resume trading.

## Build and tests

Java 17, Gradle 8.11.1, Android SDK 36. `gradle assembleDebug`.

`node --test tests/*.test.cjs` tests synthetic observations, not live EA DOM captures. Coverage includes bid increments, write-ahead intents, duplicate suppression, restart recovery, rebid ceilings, buy/win/list/sale accounting, stale data, variant ambiguity, budgets, security stops, UTC rollover and multi-position monitoring.

The debug APK is an arm64 build. Local keystore signing follows the existing workflow; if Android rejects an update because the old debug signing key differs, do not uninstall without preserving your existing session/ledger.
