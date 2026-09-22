package com.fcplus.android

import kotlinx.coroutines.flow.MutableStateFlow

data class MarketListing(
    val name: String = "Item",
    val startPrice: Int = 0,
    val currentBid: Int = 0,
    val buyNow: Int = 0,
    val timeSeconds: Int = 999999
) {
    val effectiveBid: Int
        get() = if (currentBid > 0) currentBid else startPrice
}

data class MarketSnapshot(
    val pageType: String = "other",
    val playerName: String = "",
    val chemStyle: String = "",
    val listings: List<MarketListing> = emptyList(),
    val capturedAt: Long = 0L
)

data class TradeAdvice(
    val minBin: Int = 0,
    val stableBin: Int = 0,
    val minBid: Int = 0,
    val maxBid: Int = 0,
    val bestEntry: Int = 0,
    val expectedProfit: Int = 0,
    val roiPercent: Double = 0.0,
    val opportunities: Int = 0,
    val confidence: String = "—",
    val recommended: Boolean = false,
    val summary: String = "Open a Transfer Market search to feed Market Brain."
)

data class FcStatus(
    val running: Boolean = false,
    val dryRun: Boolean = true,
    val strategy: String = "Quick Flip",
    val minProfit: Int = 300,
    val minRoiPercent: Int = 8,
    val engineStatus: String = "Stopped",
    val pageTitle: String = "",
    val pageUrl: String = "",
    val lastEvent: String = "Ready",
    val securityStop: Boolean = false,
    val market: MarketSnapshot = MarketSnapshot(),
    val advice: TradeAdvice = TradeAdvice()
)

object AppState {
    val status = MutableStateFlow(FcStatus())

    fun update(block: (FcStatus) -> FcStatus) {
        status.value = block(status.value)
    }

    fun applyMarketSnapshot(snapshot: MarketSnapshot) {
        val current = status.value
        status.value = current.copy(
            market = snapshot,
            advice = MarketBrain.analyze(snapshot, current),
            lastEvent = if (snapshot.listings.isNotEmpty()) {
                "Market snapshot · ${snapshot.listings.size} listings"
            } else {
                "EA page detected · ${snapshot.pageType}"
            }
        )
    }

    fun recalculate() {
        val current = status.value
        status.value = current.copy(advice = MarketBrain.analyze(current.market, current))
    }
}
