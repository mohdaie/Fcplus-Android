package com.fcplus.android

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface

class FcJsBridge(
    private val context: Context,
    private val source: String
) {
    @JavascriptInterface
    fun onPageState(title: String?, url: String?) {
        AppState.update {
            it.copy(
                pageTitle = title.orEmpty(),
                pageUrl = url.orEmpty(),
                engineStatus = if (it.running) "EA session active" else it.engineStatus,
                lastEvent = "EA page detected from " + source
            )
        }
    }

    @JavascriptInterface
    fun onEvent(type: String?, message: String?) {
        val event = listOfNotNull(type, message).joinToString(": ")
        AppState.update { it.copy(lastEvent = event.ifBlank { "FC+ event" }) }
    }

    @JavascriptInterface
    fun onSecurityStop(reason: String?) {
        val detail = reason?.takeIf { it.isNotBlank() } ?: "EA security verification required"
        AppState.update {
            it.copy(
                running = false,
                securityStop = true,
                engineStatus = "Stopped for verification",
                lastEvent = detail
            )
        }
        context.stopService(Intent(context, TraderService::class.java))
    }
}
