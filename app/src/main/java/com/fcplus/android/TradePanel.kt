package com.fcplus.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun TradePanel() {
    val revision by TradingStore.revision.collectAsState()
    val cfg = remember(revision) { TradingStore.settings }
    val state = remember(revision) { TradingStore.state }
    val running = TradingStore.running
    var expanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf("") }
    var chem by remember { mutableStateOf("Basic") }
    var key by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var scouting by remember { mutableStateOf(false) }
    var confirmLive by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val targets = cfg.optJSONArray("targets") ?: JSONArray()
    val ledger = state.optJSONArray("ledger") ?: JSONArray()
    val context = AppContextHolder.context
    fun safe(block: () -> Unit) { runCatching(block).onFailure { info = it.message ?: "Could not save" } }
    Card(colors = CardDefaults.cardColors(containerColor = GeminiSurface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TRADING SESSION", color = GeminiCyan, style = MaterialTheme.typography.titleMedium)
            Text("Realized today (UTC): ${state.optInt("dayProfit")} / 100,000 coins", color = GeminiText)
            Text("${ledger.length()} tracked trades · ${state.optInt("sessionActions")} session actions", color = GeminiMuted)
            Text(state.optString("message","Configure targets, log in to EA, then start a Dry Run."), color = GeminiText)
            Text(TradingStore.snapshot.optString("diagnostics","Waiting for EA"),color = GeminiMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide settings" else "Targets & settings") }
                TextButton(enabled = !running, onClick = {
                    TradingStore.reconcileRequested = true
                    info = "Open the matching Transfer Targets/List screen in EA. Reconcile will only read the result."
                }) { Text("Reconcile") }
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dry Run", color = GeminiText, modifier = Modifier.weight(1f))
                    Switch(checked = cfg.optBoolean("dryRun",true), enabled = !running && !scouting, onCheckedChange = {
                        if (!it) confirmLive = true else safe { TradingStore.setSetting("dryRun",true); AppState.update { s -> s.copy(dryRun = true) } }
                    })
                }
                Text("Dry Run evaluates opportunities without placing orders. Live mode spends EA coins within your limits.",color = GeminiMuted)
                SettingNumber("Maximum per card", "maxBuy",150,100000, cfg, !running && !scouting)
                SettingNumber("Session spend budget", "budget",150,500000, cfg, !running && !scouting)
                SettingNumber("Minimum profit", "minProfit",50,100000, cfg, !running && !scouting)
                SettingNumber("Minimum ROI %", "minRoi",1,100, cfg, !running && !scouting)
                SettingNumber("Session minutes", "durationMinutes",1,60, cfg, !running && !scouting)
                SettingNumber("Seconds between actions", "intervalSeconds",10,120, cfg, !running && !scouting)
                SettingNumber("Maximum open trades", "maxOpen",1,10, cfg, !running && !scouting)
                SettingNumber("Maximum session actions", "maxActions",1,120, cfg, !running && !scouting)
                Text("Targets rotate automatically. Chem Flip buys cards with the chosen style already applied; consumables are not purchased or applied.",color = GeminiMuted)
                for (i in 0 until targets.length()) {
                    val target = targets.getJSONObject(i)
                    Text("${i+1}. ${target.optString("name")} · ${target.optInt("rating")} · ${target.optString("chem","Basic")}", color = GeminiText)
                    if (target.optString("reason").isNotBlank()) Text(target.optString("reason"),color = GeminiMuted)
                }
                OutlinedTextField(name,{ name = it },label = { Text("Exact EA player name") },enabled = !running && !scouting,modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rating,{ rating = it.filter(Char::isDigit).take(2) },label = { Text("Card rating (required)") },enabled = !running && !scouting,modifier = Modifier.fillMaxWidth())
                OutlinedTextField(chem,{ chem = it },label = { Text("Chem style: Basic, Hunter, Shadow…") },enabled = !running && !scouting,modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(enabled = !running && !scouting,onClick = { safe {
                        require(name.trim().length in 2..60 && (rating.toIntOrNull() ?: 0) in 40..99) { "Enter the exact EA name and card rating" }
                        require(chem.trim() in listOf("Basic","Hunter","Shadow","Engine","Hawk","Anchor","Catalyst","Finisher","Deadeye","Marksman","Sniper","Artist","Architect","Powerhouse","Maestro","Sentinel","Guardian","Gladiator","Backbone")) { "Choose a valid chemistry style" }
                        require(targets.length() < 10) { "Maximum 10 targets" }
                        val next = JSONArray(targets.toString()).put(JSONObject().put("name",name.trim()).put("rating",rating.toInt()).put("chem",chem.trim()))
                        TradingStore.setSetting("targets",next); name = ""; rating = ""; info = "Target added"
                    } }) { Text("Add target") }
                    TextButton(enabled = !running && !scouting,onClick = { safe { TradingStore.setSetting("targets",JSONArray()) } }) { Text("Clear targets") }
                }
                HorizontalDivider()
                Text("AI SCOUT · GEMINI",color = GeminiPurple)
                Text("Researches FUTBIN/FUT.GG through Google Search. Suggestions are hypotheses; EA must supply fresh matching prices. Requires your Gemini API key and may use paid API quota.",color = GeminiMuted)
                SettingText("Edition (for example FC 26)","edition",cfg,!running && !scouting)
                SettingText("Market platform","platform",cfg,!running && !scouting)
                SettingText("Gemini model","model",cfg,!running && !scouting)
                OutlinedTextField(key,{ key = it },label = { Text(if (ScoutKey.read().isNotBlank()) "API key saved · enter to replace" else "Gemini API key") },visualTransformation = PasswordVisualTransformation(),enabled = !running && !scouting,modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(enabled = !running && !scouting && key.isNotBlank(),onClick = { safe { ScoutKey.save(key); key = ""; info = "API key saved on this device" } }) { Text("Save key") }
                    TextButton(enabled = !running && !scouting,onClick = { safe { ScoutKey.save(""); info = "API key removed" } }) { Text("Remove key") }
                }
                Button(enabled = !running && !scouting,onClick = {
                    scouting = true; info = "Researching current targets…"
                    val input = JSONObject(TradingStore.settings.toString())
                    Thread {
                        val result = runCatching { Scout.research(input) }
                        Handler(Looper.getMainLooper()).post {
                            scouting = false
                            result.onSuccess { found -> safe {
                                val next = JSONObject(TradingStore.settings.toString()).put("targets",found.targets).put("sources",found.sources).put("searchSuggestions",found.suggestions)
                                TradingStore.saveSettings(next); info = "${found.targets.length()} sourced candidates added. EA validation is still required."
                            } }.onFailure { info = it.message ?: "AI Scout failed" }
                        }
                    }.start()
                }) { Text(if (scouting) "Researching…" else "Find targets with AI") }
                if ((cfg.optJSONArray("sources")?.length() ?: 0) > 0) TextButton(onClick = { showSources = !showSources }) { Text("Research sources & Google suggestions") }
                if (showSources) {
                    val sources = cfg.optJSONArray("sources") ?: JSONArray()
                    for (i in 0 until sources.length()) {
                        val source = sources.getJSONObject(i)
                        TextButton(onClick = {
                            val uri = Uri.parse(source.optString("uri"))
                            if (uri.scheme == "https") context.startActivity(Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text(source.optString("title","Source")) }
                    }
                    val html = cfg.optString("searchSuggestions")
                    if (html.isNotBlank()) AndroidView(modifier = Modifier.fillMaxWidth().height(150.dp),factory = { ctx ->
                        WebView(ctx).apply { settings.javaScriptEnabled = false; settings.allowFileAccess = false; settings.allowContentAccess = false; loadDataWithBaseURL("https://www.google.com/",html,"text/html","UTF-8",null) }
                    })
                }
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("FC+ diagnostics",TradingStore.diagnostic()))
                    info = "Copied market diagnostics and ledger; no API key or cookies included."
                }) { Text("Copy diagnostics") }
            }
            if (info.isNotBlank()) Text(info,color = GeminiCyan)
            val logs = state.optJSONArray("logs") ?: JSONArray()
            for (i in 0 until minOf(6,logs.length())) Text(logs.getJSONObject(i).optString("text"),color = GeminiMuted,style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmLive) AlertDialog(onDismissRequest = { confirmLive = false }, title = { Text("Enable live orders?") },
        text = { Text("FC+ may bid, buy and list cards using your EA coins. Limits: ${cfg.optInt("maxBuy")} per card, ${cfg.optInt("budget")} session spend, ${cfg.optInt("durationMinutes")} minutes. It stops on uncertain outcomes. Device testing of EA screen compatibility is still required.") },
        confirmButton = { TextButton(onClick = { safe { TradingStore.setSetting("dryRun",false); AppState.update { it.copy(dryRun = false) } }; confirmLive = false }) { Text("Enable live") } },
        dismissButton = { TextButton(onClick = { confirmLive = false }) { Text("Keep Dry Run") } })
}

@Composable
private fun SettingNumber(label: String, key: String, min: Int, max: Int, cfg: JSONObject, enabled: Boolean) {
    var value by remember(cfg.optInt(key)) { mutableStateOf(cfg.optInt(key).toString()) }
    Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value,{ value = it.filter(Char::isDigit).take(6) }, label = { Text(label) },enabled = enabled,modifier = Modifier.weight(1f))
        TextButton(enabled = enabled && (value.toIntOrNull() ?: -1) in min..max,onClick = { TradingStore.setSetting(key,value.toInt()) }) { Text("Save") }
    }
}
@Composable
private fun SettingText(label: String,key: String,cfg: JSONObject,enabled: Boolean) {
    var value by remember(cfg.optString(key)) { mutableStateOf(cfg.optString(key)) }
    Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value,{ value = it.take(80) },label = { Text(label) },enabled = enabled,modifier = Modifier.weight(1f))
        TextButton(enabled = enabled && value.isNotBlank(),onClick = { TradingStore.setSetting(key,value.trim()) }) { Text("Save") }
    }
}
