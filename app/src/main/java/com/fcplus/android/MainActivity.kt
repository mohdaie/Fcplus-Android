package com.fcplus.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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

@Composable
private fun FcPlusApp(
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val status by AppState.status.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(tab) {
        val activity = context as? Activity ?: return@LaunchedEffect
        activity.requestedOrientation = if (tab == 1) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

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
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("F+") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("EA") },
                    label = { Text("EA Login") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> Dashboard(
                modifier = Modifier.padding(padding),
                status = status,
                onStart = onStart,
                onStop = onStop
            )
            else -> EaWebView(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
private fun Dashboard(
    modifier: Modifier,
    status: FcStatus,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("FC+ Market Brain", style = MaterialTheme.typography.headlineMedium)
        Text("Native Android prototype v0.1.1")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (status.running) "● RUNNING" else "○ STOPPED")
                Text(status.engineStatus)
                Text("Strategy: " + status.strategy)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dry Run")
                    Switch(
                        checked = status.dryRun,
                        onCheckedChange = { checked ->
                            AppState.update { it.copy(dryRun = checked) }
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onStart, enabled = !status.running) {
                Text("START BACKGROUND")
            }
            Button(onClick = onStop, enabled = status.running) {
                Text("STOP")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("EA Session", style = MaterialTheme.typography.titleMedium)
                Text(status.pageTitle.ifBlank { "No page reported yet" })
                if (status.pageUrl.isNotBlank()) Text(status.pageUrl)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Last event", style = MaterialTheme.typography.titleMedium)
                Text(status.lastEvent)
                if (status.securityStop) {
                    Spacer(Modifier.height(4.dp))
                    Text("Manual EA verification required before restart.")
                }
            }
        }

        Text(
            "First login to EA from the EA Login tab. FC+ automatically opens that tab in landscape because EA's embedded Web App can reject portrait WebView layouts. FC+ then reuses the normal WebView session in the foreground service. Dry Run remains the default."
        )
    }
}

object AppContextHolder {
    lateinit var context: android.content.Context
}
