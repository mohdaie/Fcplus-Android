package com.fcplus.android

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

object FcExtensionBridge {
    private const val EXT_LOCATION = "resource://android/assets/fcplus_ext/"
    private const val EXT_ID = "fcplus@marketbrain"
    private const val NATIVE_APP = "fcplus_native"

    fun wireSession(
        context: Context,
        session: GeckoSession,
        onReady: () -> Unit
    ) {
        val runtime = GeckoEngine.runtime(context)

        runtime.webExtensionController
            .ensureBuiltIn(EXT_LOCATION, EXT_ID)
            .accept(
                { extension ->
                    if (extension == null) {
                        AppState.update { it.copy(lastEvent = "FC+ extension install returned empty") }
                        onReady()
                        return@accept
                    }

                    session.webExtensionController.setMessageDelegate(
                        extension,
                        messageDelegate(context.applicationContext),
                        NATIVE_APP
                    )

                    AppState.update { it.copy(lastEvent = "FC+ market bridge connected") }
                    onReady()
                },
                { error ->
                    AppState.update {
                        it.copy(lastEvent = "FC+ bridge error: ${error?.message ?: "unknown"}")
                    }
                    onReady()
                }
            )
    }

    private fun messageDelegate(context: Context) =
        object : WebExtension.MessageDelegate {
            override fun onMessage(
                nativeApp: String,
                message: Any,
                sender: WebExtension.MessageSender
            ): GeckoResult<Any?>? {
                val obj = message as? JSONObject ?: return null

                when (obj.optString("type")) {
                    "snapshot" -> AppState.applyMarketSnapshot(parseSnapshot(obj))
                    "security_stop" -> {
                        val reason = obj.optString("reason", "EA security verification required")
                        AppState.update {
                            it.copy(
                                running = false,
                                securityStop = true,
                                engineStatus = "Stopped for EA verification",
                                lastEvent = reason
                            )
                        }
                        context.stopService(Intent(context, TraderService::class.java))
                    }
                }

                return null
            }
        }

    private fun parseSnapshot(obj: JSONObject): MarketSnapshot {
        val array = obj.optJSONArray("listings")
        val listings = buildList {
            if (array != null) {
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    add(
                        MarketListing(
                            name = row.optString("name", "Item"),
                            startPrice = row.optInt("startPrice", 0),
                            currentBid = row.optInt("currentBid", 0),
                            buyNow = row.optInt("buyNow", 0),
                            timeSeconds = row.optInt("timeSeconds", 999999)
                        )
                    )
                }
            }
        }

        return MarketSnapshot(
            pageType = obj.optString("pageType", "other"),
            playerName = obj.optString("playerName", ""),
            chemStyle = obj.optString("chemStyle", ""),
            listings = listings,
            capturedAt = obj.optLong("capturedAt", System.currentTimeMillis())
        )
    }
}
