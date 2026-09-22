package com.fcplus.android

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewTools {
    const val EA_URL = "https://www.ea.com/ea-sports-fc/ultimate-team/web-app/"

    fun configure(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false

            // Present the embedded page as a normal Android browser rather than
            // an in-app "wv" client. EA's mobile layout behaves more consistently
            // with the regular mobile browser user agent.
            userAgentString = WebSettings.getDefaultUserAgent(webView.context)
                .replace("; wv", "")
                .replace("Version/4.0 ", "")
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    fun loadBridgeScript(context: Context): String =
        context.assets.open("fcplus_bridge.js")
            .bufferedReader()
            .use { it.readText() }
}
