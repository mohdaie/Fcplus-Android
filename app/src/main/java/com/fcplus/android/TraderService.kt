package com.fcplus.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

class TraderService : Service() {
    companion object {
        const val CHANNEL_ID = "fcplus_trader"
        const val NOTIFICATION_ID = 2701
    }

    private var engineWebView: WebView? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting FC+ engine"))

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FCPlus::Trader"
        ).apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L)
        }

        AppState.update {
            it.copy(
                running = true,
                securityStop = false,
                engineStatus = "Starting background EA session",
                lastEvent = "Foreground service started"
            )
        }

        Handler(Looper.getMainLooper()).post { startBackgroundWebView() }
    }

    private fun startBackgroundWebView() {
        val webView = WebView(this)
        engineWebView = webView
        WebViewTools.configure(webView)
        webView.addJavascriptInterface(FcJsBridge(this, "background-engine"), "FCPlusAndroid")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val script = WebViewTools.loadBridgeScript(this@TraderService)
                view.evaluateJavascript(script, null)
                AppState.update {
                    it.copy(
                        running = true,
                        engineStatus = "Background EA session loaded",
                        lastEvent = "Engine page loaded"
                    )
                }
                updateNotification("EA session active · Dry Run")
            }
        }
        webView.loadUrl(WebViewTools.EA_URL)
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("FC+ Trader")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FC+ background trader",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Keeps the FC+ trading coordinator active."
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Handler(Looper.getMainLooper()).post {
            engineWebView?.stopLoading()
            engineWebView?.removeJavascriptInterface("FCPlusAndroid")
            engineWebView?.destroy()
            engineWebView = null
        }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        AppState.update {
            it.copy(
                running = false,
                engineStatus = if (it.securityStop) it.engineStatus else "Stopped",
                lastEvent = if (it.securityStop) it.lastEvent else "Background service stopped"
            )
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
