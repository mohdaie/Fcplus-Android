package com.fcplus.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = GeminiInk.toArgb()
        window.navigationBarColor = GeminiInk.toArgb()

        setContent {
            FcPlusTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = GeminiInk) {
                    FcPlusApp(
                        onStart = {
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, TraderService::class.java)
                            )
                        },
                        onStop = {
                            stopService(Intent(this, TraderService::class.java))
                        }
                    )
                }
            }
        }
    }
}

private val GeminiGradient = Brush.linearGradient(
    listOf(GeminiBlue, GeminiPurple, GeminiPink)
)

@Composable
private fun FcPlusApp(
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val status by AppState.status.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var showVersionDialog by remember { mutableStateOf(true) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                AppContextHolder.context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = GeminiInk,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D1220), tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("✦") },
                    label = { Text("Brain") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("EA") },
                    label = { Text("EA Login") },
                    colors = navColors()
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> Dashboard(
                modifier = Modifier.padding(padding),
                status = status,
                onStart = onStart,
                onStop = onStop,
                onVersion = { showVersionDialog = true }
            )
            else -> EaWebView(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }
    }

    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            containerColor = GeminiSurface,
            titleContentColor = GeminiText,
            textContentColor = GeminiMuted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GeminiSpark()
                    Spacer(Modifier.size(10.dp))
                    Text("FC+ Android")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version " + BuildConfig.VERSION_NAME, color = GeminiText)
                    Text("Build " + BuildConfig.VERSION_CODE)
                    Text("Market Bridge · Quick Flip · Chem Flip")
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionDialog = false }) {
                    Text("Continue", color = GeminiCyan)
                }
            }
        )
    }
}

@Composable
private fun Dashboard(
    modifier: Modifier,
    status: FcStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onVersion: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GeminiInk)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GeminiSpark()
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("Market Brain", style = MaterialTheme.typography.headlineLarge, color = GeminiText)
                    Text("EA live market telemetry", style = MaterialTheme.typography.bodyMedium, color = GeminiMuted)
                }
            }
            VersionChip(onClick = onVersion)
        }

        StatusCard(status)
        StrategyCard(status)
        MarketCard(status)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GradientAction(
                text = if (status.running) "RUNNING" else "START BACKGROUND",
                enabled = !status.running,
                modifier = Modifier.weight(1f),
                onClick = onStart
            )
            SecondaryAction(
                text = "STOP",
                enabled = status.running,
                modifier = Modifier.weight(0.58f),
                onClick = onStop
            )
        }

        InfoCard(
            title = "EA session",
            eyebrow = "FIREFOX ENGINE",
            body = if (status.pageTitle.isNotBlank()) status.pageTitle else "No EA page reported yet",
            detail = status.pageUrl.ifBlank { "Open EA Login to authenticate." }
        )

        InfoCard(
            title = "Last event",
            eyebrow = "LIVE ACTIVITY",
            body = status.lastEvent,
            detail = status.engineStatus
        )

        Text(
            "v0.3 reads EA market data and calculates trade ceilings in Dry Run. It does not place live bids yet. Security/CAPTCHA text always hard-stops the background service.",
            color = GeminiMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun StatusCard(status: FcStatus) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GeminiSurface),
        border = BorderStroke(1.dp, GeminiStroke),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (status.running) "Market Brain active" else "Market Brain stopped",
                        style = MaterialTheme.typography.headlineSmall,
                        color = GeminiText
                    )
                    Text(status.engineStatus, style = MaterialTheme.typography.bodyMedium, color = GeminiMuted)
                }
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (status.running) GeminiGreen else GeminiMuted.copy(alpha = .45f))
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dry run", color = GeminiText)
                Switch(
                    checked = status.dryRun,
                    onCheckedChange = { checked -> AppState.update { it.copy(dryRun = checked) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = GeminiPurple,
                        uncheckedThumbColor = GeminiMuted,
                        uncheckedTrackColor = GeminiSurface2
                    )
                )
            }
        }
    }
}

@Composable
private fun StrategyCard(status: FcStatus) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GeminiSurface),
        border = BorderStroke(1.dp, GeminiStroke),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("STRATEGY", color = GeminiPurple, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StrategyPill("Quick Flip", status.strategy == "Quick Flip") {
                    AppState.update { it.copy(strategy = "Quick Flip") }
                    AppState.recalculate()
                }
                StrategyPill("Chem Flip", status.strategy == "Chem Flip") {
                    AppState.update { it.copy(strategy = "Chem Flip") }
                    AppState.recalculate()
                }
            }

            SettingStepper(
                label = "Minimum profit",
                value = status.minProfit.toString() + " coins",
                onMinus = {
                    AppState.update { it.copy(minProfit = maxOf(0, it.minProfit - 50)) }
                    AppState.recalculate()
                },
                onPlus = {
                    AppState.update { it.copy(minProfit = it.minProfit + 50) }
                    AppState.recalculate()
                }
            )

            SettingStepper(
                label = "Minimum ROI",
                value = status.minRoiPercent.toString() + "%",
                onMinus = {
                    AppState.update { it.copy(minRoiPercent = maxOf(0, it.minRoiPercent - 1)) }
                    AppState.recalculate()
                },
                onPlus = {
                    AppState.update { it.copy(minRoiPercent = minOf(100, it.minRoiPercent + 1)) }
                    AppState.recalculate()
                }
            )
        }
    }
}

@Composable
private fun MarketCard(status: FcStatus) {
    val advice = status.advice
    val market = status.market

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (advice.recommended) Color(0xFF10251F) else GeminiSurface
        ),
        border = BorderStroke(1.dp, if (advice.recommended) GeminiGreen.copy(alpha = .65f) else GeminiStroke),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("LIVE MARKET", color = if (advice.recommended) GeminiGreen else GeminiCyan, style = MaterialTheme.typography.labelLarge)
                    Text(
                        market.playerName.ifBlank { market.pageType.uppercase() },
                        color = GeminiText,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(advice.confidence, color = GeminiMuted, style = MaterialTheme.typography.labelLarge)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("MIN BIN", MarketBrain.format(advice.minBin))
                Metric("STABLE", MarketBrain.format(advice.stableBin))
                Metric("MAX BID", MarketBrain.format(advice.maxBid))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("MIN BID", MarketBrain.format(advice.minBid))
                Metric("OPPS", advice.opportunities.toString())
                Metric("EST PROFIT", if (advice.expectedProfit > 0) "+" + MarketBrain.format(advice.expectedProfit) else "—")
            }

            if (market.chemStyle.isNotBlank()) {
                Text("Chemistry style: " + market.chemStyle, color = GeminiPurple, style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                advice.summary,
                color = if (advice.recommended) GeminiText else GeminiMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = GeminiMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = GeminiText, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StrategyPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) GeminiPurple else GeminiSurface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, color = if (selected) Color.White else GeminiMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SettingStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(label, color = GeminiMuted, style = MaterialTheme.typography.bodyMedium)
            Text(value, color = GeminiText, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniButton("−", onMinus)
            MiniButton("+", onPlus)
        }
    }
}

@Composable
private fun MiniButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GeminiSurface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = GeminiCyan, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun InfoCard(title: String, eyebrow: String, body: String, detail: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GeminiSurface),
        border = BorderStroke(1.dp, GeminiStroke),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(eyebrow, color = GeminiPurple, style = MaterialTheme.typography.labelLarge)
            Text(title, color = GeminiText, style = MaterialTheme.typography.titleMedium)
            Text(body, color = GeminiText, style = MaterialTheme.typography.bodyLarge)
            Text(detail, color = GeminiMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GradientAction(text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) GeminiGradient else Brush.linearGradient(listOf(GeminiSurface2, GeminiSurface2)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.White else GeminiMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SecondaryAction(text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GeminiSurface2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) GeminiText else GeminiMuted.copy(alpha = .5f), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun VersionChip(onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(GeminiSurface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("v" + BuildConfig.VERSION_NAME, color = GeminiCyan, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GeminiSpark() {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GeminiGradient),
        contentAlignment = Alignment.Center
    ) {
        Text("✦", color = Color.White, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = GeminiCyan,
    selectedTextColor = GeminiText,
    indicatorColor = GeminiSurface2,
    unselectedIconColor = GeminiMuted,
    unselectedTextColor = GeminiMuted
)
