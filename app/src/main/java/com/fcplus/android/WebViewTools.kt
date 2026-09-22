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
            allowFileAccess = false
            allowContentAccess = false
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
