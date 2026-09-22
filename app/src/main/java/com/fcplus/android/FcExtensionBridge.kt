package com.fcplus.android

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

object FcExtensionBridge {
    fun wireSession(context: Context, session: GeckoSession, onReady: () -> Unit) {
        GeckoEngine.runtime(context).webExtensionController.ensureBuiltIn("resource://android/assets/fcplus_ext/", "fcplus@marketbrain").accept(
            { extension ->
                if (extension == null) { TradingStore.stop("EA bridge unavailable"); return@accept }
                session.webExtensionController.setMessageDelegate(extension, delegate(context.applicationContext), "fcplus_native")
                onReady()
            },
            { error -> TradingStore.stop("EA bridge install failed: ${error?.message ?: "unknown"}") }
        )
    }
    private fun delegate(context: Context) = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any?>? {
            val obj = message as? JSONObject ?: return null
            val response = JSONObject()
            when (obj.optString("type")) {
                "tick" -> {
                    val snap = obj.optJSONObject("snapshot") ?: return null
                    TradingStore.acceptSnapshot(snap)
                    val security = snap.optString("security")
                    if (security.isNotBlank() || snap.optBoolean("loggedOut")) {
                        TradingStore.stop(if (security.isNotBlank()) "EA verification: $security" else "EA login required")
                        context.stopService(Intent(context, TraderService::class.java))
                    }
                    response.put("reconcile", TradingStore.reconcileRequested && !TradingStore.running).put("running", TradingStore.running).put("state",TradingStore.state.takeIf { it.has("schema") } ?: JSONObject.NULL).put("config",TradingStore.config())
                }
                "checkpoint" -> {
                    response.put("allowed",TradingStore.checkpoint(obj))
                    if (!TradingStore.running) context.stopService(Intent(context, TraderService::class.java))
                }
                "permit" -> response.put("allowed",TradingStore.permit(obj.optString("id")))
                "action_result" -> {
                    TradingStore.actionResult(obj)
                    if (!TradingStore.running) context.stopService(Intent(context, TraderService::class.java))
                }
                "adapter_error" -> {
                    TradingStore.stop("EA adapter: ${obj.optString("error")}")
                    context.stopService(Intent(context, TraderService::class.java))
                }
                "reconcile" -> {
                    response.put("saved", obj.optJSONObject("state")?.let { TradingStore.reconcile(it) } ?: false)
                    TradingStore.reconcileRequested = false
                }
            }
            return GeckoResult.fromValue<Any?>(response)
        }
    }
}
