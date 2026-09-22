package com.fcplus.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

@Composable
fun EaWebView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> GeckoView(context).apply { setSession(GeckoEngine.eaSession(context)) } },
        onRelease = { view ->
            view.releaseSession()
            GeckoEngine.eaSession(AppContextHolder.context).setActive(TradingStore.running)
        }
    )
}
