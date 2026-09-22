package com.fcplus.android

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

object GeckoEngine {
    const val EA_URL = "https://www.ea.com/ea-sports-fc/ultimate-team/web-app/"

    @Volatile
    private var runtimeInstance: GeckoRuntime? = null

    fun runtime(context: Context): GeckoRuntime {
        return runtimeInstance ?: synchronized(this) {
            runtimeInstance ?: GeckoRuntime.create(
                context.applicationContext,
                GeckoRuntimeSettings.Builder()
                    .consoleOutput(BuildConfig.DEBUG)
                    .forceUserScalableEnabled(true)
                    .build()
            ).also { runtimeInstance = it }
        }
    }

    private var sharedSession: GeckoSession? = null

    fun eaSession(context: Context): GeckoSession {
        sharedSession?.let { return it }
        val session = newEaSession(context)
        sharedSession = session
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                AppState.update { it.copy(pageTitle = title.orEmpty()) }
            }
        }
        FcExtensionBridge.wireSession(context, session) {
            session.setActive(true)
            session.loadUri(EA_URL)
        }
        return session
    }

    fun newEaSession(context: Context): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .allowJavascript(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .suspendMediaWhenInactive(false)
            .build()

        return GeckoSession(settings).also { it.open(runtime(context)) }
    }
}
