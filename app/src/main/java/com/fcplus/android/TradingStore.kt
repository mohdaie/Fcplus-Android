package com.fcplus.android

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Native write-ahead journal. A financial click is permitted only after commit() succeeds. */
object TradingStore {
    private val prefs get() = AppContextHolder.context.getSharedPreferences("trading_v1", Context.MODE_PRIVATE)
    val revision = MutableStateFlow(0)
    var running = false
        private set
    var runId = ""
        private set
    var deadline = 0L
        private set
    private var allowedAction = ""
    private var permitUntil = 0L
    var reconcileRequested = false
    var snapshot = JSONObject()
        private set
    var state: JSONObject = JSONObject()
        private set
    var settings: JSONObject = JSONObject()
        private set

    fun initialize() {
        state = runCatching { JSONObject(prefs.getString("state", "{}")!!) }.getOrDefault(JSONObject())
        settings = runCatching { JSONObject(prefs.getString("settings", "{}")!!) }.getOrDefault(JSONObject())
        val defaults = JSONObject("""{"dryRun":true,"strategy":"silver_quick_flip","minProfit":200,"minRoi":8,"maxBuy":5000,"budget":20000,"maxOpen":3,"durationMinutes":20,"intervalSeconds":12,"maxActions":60,"dailyCap":100000,"targets":[],"model":"gemini-2.5-flash","edition":"Current EA SPORTS FC","platform":"Console"}""")
        defaults.keys().forEach { key -> if (!settings.has(key)) settings.put(key, defaults.get(key)) }
        // Process restarts never silently resume live orders.
        settings.put("dryRun", true)
        AppState.update { it.copy(dryRun = true, minProfit = settings.optInt("minProfit",300), minRoiPercent = settings.optInt("minRoi",8)) }
        changed()
    }
    private fun changed() { revision.value += 1 }
    fun saveSettings(next: JSONObject) {
        check(!running) { "Stop the session before changing trading settings" }
        check(prefs.edit().putString("settings", next.toString()).commit()) { "Could not save settings" }
        settings = next; changed()
    }
    fun setSetting(key: String, value: Any) {
        val next = JSONObject(settings.toString()).put(key, value)
        saveSettings(next)
    }
    fun applyStrategy(strategy: String) {
        check(!running) { "Stop the session before changing trading method" }
        require(strategy == "silver_quick_flip") { "Trading method is not available yet" }
        val coins = snapshot.optInt("coins", 0)
        val maxBuy = if (coins > 0) (coins / 20).coerceIn(1500, 7500) else 5000
        val budget = if (coins > 0) (coins / 6).coerceIn(5000, 30000) else 20000
        val next = JSONObject(settings.toString())
            .put("strategy", strategy)
            .put("minProfit", 200)
            .put("minRoi", 8)
            .put("maxBuy", maxBuy)
            .put("budget", budget)
            .put("maxOpen", 3)
            .put("durationMinutes", 20)
            .put("intervalSeconds", 12)
            .put("maxActions", 60)
            .put("targets", JSONArray())
        saveSettings(next)
        AppState.update { it.copy(minProfit = 200, minRoiPercent = 8) }
    }
    fun start(): String? {
        if (running) return "A trading session is already running"
        if (state.optJSONObject("pending") != null) return "An order is unconfirmed. Use Reconcile on the matching EA screen first."
        if (settings.optString("strategy").isBlank() && (settings.optJSONArray("targets")?.length() ?: 0) == 0 && (state.optJSONArray("ledger")?.length() ?: 0) == 0) return "Choose a trading method first"
        runId = UUID.randomUUID().toString()
        deadline = System.currentTimeMillis() + settings.optInt("durationMinutes", 20).coerceIn(1,60) * 60000L
        running = true
        allowedAction = ""
        val mode = if (settings.optBoolean("dryRun", true)) "Dry Run" else "Live"
        state = JSONObject(state.toString()).put("message", "Starting $mode · waiting for fresh EA market data")
        AppState.update {
            it.copy(
                running = true,
                engineStatus = "Starting trader · $mode",
                lastEvent = "Waiting for EA market data"
            )
        }
        changed()
        return null
    }
    fun stop(reason: String) {
        running = false; allowedAction = ""; permitUntil = 0
        AppState.update { it.copy(running = false, engineStatus = reason, lastEvent = reason) }
        changed()
    }
    fun config(): JSONObject = JSONObject(settings.toString()).put("runId", runId).put("dailyCap",100000)
    fun acceptSnapshot(value: JSONObject) {
        snapshot = value
        AppState.update { it.copy(pageUrl = value.optString("url"), lastEvent = value.optString("diagnostics")) }
        changed()
    }
    fun checkpoint(message: JSONObject): Boolean {
        if (!running || message.optString("runId") != runId || System.currentTimeMillis() >= deadline) return false
        val next = message.optJSONObject("state") ?: return false
        if (next.optInt("schema") != 1 || next.toString().length > 2_000_000) return false
        if (!prefs.edit().putString("state", next.toString()).commit()) { stop("Ledger could not be saved; execution stopped"); return false }
        state = next
        val command = message.optJSONObject("command")
        allowedAction = command?.optString("id").orEmpty()
        permitUntil = System.currentTimeMillis() + 20000
        AppState.update { it.copy(lastEvent = next.optString("message"), engineStatus = next.optString("message")) }
        if (message.optBoolean("halt")) stop(next.optString("message", "Trading stopped"))
        changed()
        return running
    }
    fun permit(id: String): Boolean = running && id.isNotBlank() && id == allowedAction && System.currentTimeMillis() < minOf(deadline, permitUntil)
    fun actionResult(message: JSONObject) {
        if (message.optString("id") != allowedAction) return
        val error = message.optString("error")
        if (error.isNotBlank()) {
            // Only a failure before the first transaction click may clear the write-ahead intent.
            if (!message.optBoolean("submitted")) {
                val next = JSONObject(state.toString())
                if (next.optJSONObject("pending")?.optString("id") == message.optString("id")) next.put("pending", JSONObject.NULL)
                if (prefs.edit().putString("state",next.toString()).commit()) state = next
            }
            stop("EA adapter paused: $error")
        }
        allowedAction = ""; permitUntil = 0; changed()
    }
    // Used only with no live service. It replays observations, never clicks or clears uncertain orders.
    fun reconcile(next: JSONObject): Boolean {
        if (running || next.optInt("schema") != 1) return false
        if (!prefs.edit().putString("state",next.toString()).commit()) return false
        state = next; changed(); return true
    }
    fun diagnostic(): String = JSONObject().put("version", BuildConfig.VERSION_NAME).put("snapshot", snapshot).put("state",state).toString(2)
}
