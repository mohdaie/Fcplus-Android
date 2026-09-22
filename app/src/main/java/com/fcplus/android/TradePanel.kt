package com.fcplus.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun TradePanel() {
    val revision by TradingStore.revision.collectAsState()
    val cfg = remember(revision) { TradingStore.settings }
    val state = remember(revision) { TradingStore.state }
    val running = TradingStore.running
    val ledger = state.optJSONArray("ledger") ?: JSONArray()
    val context = AppContextHolder.context

    var confirmLive by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }

    fun safe(block: () -> Unit) {
        runCatching(block).onFailure { info = it.message ?: "Could not save" }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = GeminiSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TRADING SESSION", color = GeminiCyan, style = MaterialTheme.typography.titleMedium)
            Text("Realized today: ${state.optInt("dayProfit")} coins", color = GeminiText)
            Text("${ledger.length()} tracked trades · ${state.optInt("sessionActions")} actions", color = GeminiMuted)
            Text(state.optString("message", "Choose a trading method, then start."), color = GeminiText)
            Text(TradingStore.snapshot.optString("diagnostics", "EA market bridge ready"), color = GeminiMuted)

            HorizontalDivider()
            Text("TRADING METHOD", color = GeminiPurple, style = MaterialTheme.typography.labelLarge)

            Card(
                colors = CardDefaults.cardColors(containerColor = GeminiSurface2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Silver Quick Flip", color = GeminiText, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "FC+ scans silver players automatically, checks live market prices, chooses the best quick-flip candidate, sets the buy ceiling and sell price, then tracks the sale and profit.",
                        color = GeminiMuted
                    )
                    Text("No player name required.", color = GeminiCyan)
                    Button(
                        enabled = !running,
                        onClick = {
                            safe {
                                TradingStore.applyStrategy("silver_quick_flip")
                                info = "Silver Quick Flip selected. FC+ set the parameters automatically."
                            }
                        }
                    ) {
                        Text(
                            if (cfg.optString("strategy") == "silver_quick_flip") {
                                "SILVER QUICK FLIP SELECTED"
                            } else {
                                "USE SILVER QUICK FLIP"
                            }
                        )
                    }
                }
            }

            val strategyTarget = TradingStore.snapshot.optJSONObject("strategyTarget")
            Card(
                colors = CardDefaults.cardColors(containerColor = GeminiSurface2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("MARKET SCANNER", color = GeminiCyan, style = MaterialTheme.typography.labelLarge)
                    if (strategyTarget != null) {
                        Text(
                            strategyTarget.optString("name") + " · " + strategyTarget.optInt("rating"),
                            color = GeminiText,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("FC+ selected this card from the current Silver Quick Flip scan.", color = GeminiMuted)
                    } else {
                        Text("No candidate selected yet", color = GeminiText)
                        Text(
                            if (running) "Scanning EA silver market in the background…" else "Start the trader and FC+ will scan automatically.",
                            color = GeminiMuted
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Dry Run", color = GeminiText)
                    Text("Test the same scan and decisions without real orders.", color = GeminiMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = cfg.optBoolean("dryRun", true),
                    enabled = !running,
                    onCheckedChange = {
                        if (!it) confirmLive = true
                        else safe {
                            TradingStore.setSetting("dryRun", true)
                            AppState.update { current -> current.copy(dryRun = true) }
                        }
                    }
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = GeminiSurface2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("AUTO PARAMETERS", color = GeminiCyan, style = MaterialTheme.typography.labelLarge)
                    Text("Max/card ${cfg.optInt("maxBuy")} · Budget ${cfg.optInt("budget")}", color = GeminiText)
                    Text("Min profit ${cfg.optInt("minProfit")} · Min ROI ${cfg.optInt("minRoi")}% ", color = GeminiText)
                    Text("Open trades ${cfg.optInt("maxOpen")} · Every ${cfg.optInt("intervalSeconds")}s", color = GeminiMuted)
                    Text(
                        "Sell price comes from the live market. Profit is counted only after EA reports the item sold, after 5% tax.",
                        color = GeminiMuted
                    )
                }
            }

            HorizontalDivider()
            Text("TRADING PARAMETERS", color = GeminiPurple, style = MaterialTheme.typography.labelLarge)
            SettingNumber("Maximum per card", "maxBuy", 150, 100000, cfg, !running)
            SettingNumber("Session spend budget", "budget", 150, 500000, cfg, !running)
            SettingNumber("Minimum profit", "minProfit", 50, 100000, cfg, !running)
            SettingNumber("Minimum ROI %", "minRoi", 1, 100, cfg, !running)
            SettingNumber("Session minutes", "durationMinutes", 1, 60, cfg, !running)
            SettingNumber("Seconds between actions", "intervalSeconds", 10, 120, cfg, !running)
            SettingNumber("Maximum open trades", "maxOpen", 1, 10, cfg, !running)
            SettingNumber("Maximum session actions", "maxActions", 1, 120, cfg, !running)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !running, onClick = {
                    TradingStore.reconcileRequested = true
                    info = "Reconcile requested."
                }) { Text("Reconcile") }

                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("FC+ diagnostics", TradingStore.diagnostic()))
                    info = "Diagnostics copied."
                }) { Text("Copy diagnostics") }
            }

            if (info.isNotBlank()) Text(info, color = GeminiCyan)

            val logs = state.optJSONArray("logs") ?: JSONArray()
            for (i in 0 until minOf(6, logs.length())) {
                Text(logs.getJSONObject(i).optString("text"), color = GeminiMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (confirmLive) {
        AlertDialog(
            onDismissRequest = { confirmLive = false },
            title = { Text("Enable live trading?") },
            text = { Text("Silver Quick Flip will automatically scan, choose a player, buy or bid within the current limits, list the card, and track the final sale.") },
            confirmButton = {
                TextButton(onClick = {
                    safe {
                        TradingStore.setSetting("dryRun", false)
                        AppState.update { it.copy(dryRun = false) }
                    }
                    confirmLive = false
                }) { Text("Enable live") }
            },
            dismissButton = { TextButton(onClick = { confirmLive = false }) { Text("Keep Dry Run") } }
        )
    }
}

@Composable
private fun SettingNumber(
    label: String,
    key: String,
    min: Int,
    max: Int,
    cfg: JSONObject,
    enabled: Boolean
) {
    var value by remember(cfg.optInt(key)) { mutableStateOf(cfg.optInt(key).toString()) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.filter(Char::isDigit).take(6) },
            label = { Text(label) },
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            enabled = enabled && (value.toIntOrNull() ?: -1) in min..max,
            onClick = { TradingStore.setSetting(key, value.toInt()) }
        ) { Text("Save") }
    }
}
