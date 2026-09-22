package com.fcplus.android

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewTools {
    const val EA_URL = "https://www.ea.com/ea-sports-fc/ultimate-team/web-app/"

    private const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    fun configure(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true

            // EA's Web App rejects some narrow/mobile WebView viewports with the
            // misleading "Rotate Device" screen. Keep Android orientation natural,
            // but present a desktop-class browser viewport like mobile browsers'
            // "Desktop site" mode.
            userAgentString = DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            allowFileAccess = false
            allowContentAccess = false
        }

        webView.setInitialScale(0)

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
