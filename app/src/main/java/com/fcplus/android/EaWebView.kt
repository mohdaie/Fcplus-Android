package com.fcplus.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@Composable
fun EaWebView(modifier: Modifier = Modifier) {
    val session = remember {
        GeckoEngine.newEaSession(AppContextHolder.context).apply {
            contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String?) {
                    AppState.update {
                        it.copy(
                            pageTitle = title.orEmpty(),
                            engineStatus = "EA session active · GeckoView"
                        )
                    }
                }
            }

            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStart(session: GeckoSession, url: String) {
                    AppState.update { it.copy(pageUrl = url, engineStatus = "Loading EA") }
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    AppState.update {
                        it.copy(engineStatus = if (success) "EA session active · Market bridge" else "EA load failed")
                    }
                }
            }

            FcExtensionBridge.wireSession(AppContextHolder.context, this) {
                loadUri(GeckoEngine.EA_URL)
            }
        }
    }

    DisposableEffect(session) {
        onDispose {
            runCatching { session.setActive(false) }
            runCatching { session.close() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context -> GeckoView(context).apply { setSession(session) } }
    )
}
