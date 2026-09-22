package com.fcplus.android

import kotlin.math.floor
import kotlin.math.roundToInt

object MarketBrain {
    private fun stableBin(values: List<Int>): Int {
        val clean = values.filter { it > 0 }.sorted()
        if (clean.isEmpty()) return 0
        if (clean.size == 1) return clean.first()
        if (clean[1] > clean[0] * 1.15) return clean[1]
        val sample = clean.take(minOf(5, clean.size))
        return sample[(sample.size - 1) / 2]
    }

    private fun priceStep(price: Int): Int = when {
        price <= 1_000 -> 50
        price <= 10_000 -> 100
        price <= 50_000 -> 250
        price <= 100_000 -> 500
        else -> 1_000
    }

    private fun legalDown(price: Int): Int {
        val safe = maxOf(150, price)
        val step = priceStep(safe)
        return maxOf(150, (safe / step) * step)
    }

    fun analyze(snapshot: MarketSnapshot, settings: FcStatus): TradeAdvice {
        val listings = snapshot.listings
        if (listings.isEmpty()) {
            return TradeAdvice(
                summary = if (snapshot.pageType == "results") {
                    "Search Results detected, but no priced listings were parsed yet."
                } else {
                    "Open Transfer Market Search Results. FC+ will evaluate the visible market automatically."
                }
            )
        }

        val bins = listings.mapNotNull { it.buyNow.takeIf { price -> price > 0 } }.sorted()
        val bids = listings.mapNotNull { it.effectiveBid.takeIf { price -> price > 0 } }.sorted()

        val minBin = bins.firstOrNull() ?: 0
        val stable = stableBin(bins)
        val minBid = bids.firstOrNull() ?: 0
        val netSale = floor(stable * 0.95).toInt()
        val maxBid = if (stable > 0) legalDown(netSale - settings.minProfit) else 0

        val validEntries = listings.mapNotNull { listing ->
            val bid = listing.effectiveBid.takeIf { it > 0 && it <= maxBid }
            val bin = listing.buyNow.takeIf { it > 0 && it <= maxBid }
            listOfNotNull(bid, bin).minOrNull()
        }.sorted()

        val bestEntry = validEntries.firstOrNull() ?: 0
        val expected = if (bestEntry > 0) maxOf(0, netSale - bestEntry) else 0
        val roi = if (bestEntry > 0) expected.toDouble() / bestEntry.toDouble() * 100.0 else 0.0
        val confidence = when {
            listings.size >= 8 -> "HIGH"
            listings.size >= 4 -> "MEDIUM"
            else -> "LOW"
        }

        val recommended = bestEntry > 0 &&
            expected >= settings.minProfit &&
            roi >= settings.minRoiPercent

        val strategyText = if (settings.strategy == "Chem Flip") {
            val chem = snapshot.chemStyle.ifBlank { "selected chemistry style" }
            "Chem Flip · $chem"
        } else {
            "Quick Flip"
        }

        val summary = when {
            recommended ->
                "$strategyText opportunity: entry ${format(bestEntry)} → target ${format(stable)} · est +${format(expected)}"
            maxBid <= 0 ->
                "$strategyText: market value is too low for the current minimum profit."
            validEntries.isEmpty() ->
                "$strategyText: no visible auction/BIN is below the ${format(maxBid)} max-buy ceiling."
            roi < settings.minRoiPercent ->
                "$strategyText: profit exists but ROI ${"%.1f".format(roi)}% is below ${settings.minRoiPercent}%."
            else ->
                "$strategyText: wait for a better entry."
        }

        return TradeAdvice(
            minBin = minBin,
            stableBin = stable,
            minBid = minBid,
            maxBid = maxBid,
            bestEntry = bestEntry,
            expectedProfit = expected,
            roiPercent = roi,
            opportunities = validEntries.size,
            confidence = confidence,
            recommended = recommended,
            summary = summary
        )
    }

    fun format(value: Int): String = when {
        value <= 0 -> "—"
        value >= 1_000_000 -> {
            val n = value / 1_000_000.0
            if (n >= 10) "${n.roundToInt()}M" else "${"%.1f".format(n)}M"
        }
        value >= 1_000 -> {
            val n = value / 1_000.0
            if (n >= 100) "${n.roundToInt()}K" else "${"%.1f".format(n)}K"
        }
        else -> value.toString()
    }
}
