package com.fcplus.android

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EaWebView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                WebViewTools.configure(this)
                addJavascriptInterface(FcJsBridge(context, "visible-webview"), "FCPlusAndroid")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        val script = WebViewTools.loadBridgeScript(context)
                        view.evaluateJavascript(script, null)
                    }
                }
                loadUrl(WebViewTools.EA_URL)
            }
        }
    )
}
