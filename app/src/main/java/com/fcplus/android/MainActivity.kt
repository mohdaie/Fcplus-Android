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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
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
            title = { Text("FC+ Android") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Version ${BuildConfig.VERSION_NAME}")
                    Text("Build ${BuildConfig.VERSION_CODE}")
                    Text("Native Kotlin + Jetpack Compose")
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionDialog = false }) {
                    Text("OK")
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
            .background(MaterialTheme.colorScheme.background)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("FC+ Market Brain", style = MaterialTheme.typography.headlineMedium)
                Text("Running v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}")
            }
            TextButton(onClick = onVersion) {
                Text("v${BuildConfig.VERSION_NAME}")
            }
        }

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
            "EA Login follows the phone's normal orientation. FC+ does not force portrait or landscape. The embedded browser uses a desktop-compatible EA viewport to avoid EA's false Rotate Device gate."
        )
    }
}

object AppContextHolder {
    lateinit var context: android.content.Context
}
