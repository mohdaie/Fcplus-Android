package com.fcplus.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.mozilla.geckoview.GeckoSession

class TraderService : Service() {
    companion object {
        const val CHANNEL_ID = "fcplus_trader"
        const val NOTIFICATION_ID = 2701
    }

    private var engineSession: GeckoSession? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting Market Brain"))

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
                engineStatus = "Starting background Market Brain",
                lastEvent = "Foreground service started"
            )
        }

        startBackgroundSession()
    }

    private fun startBackgroundSession() {
        val session = GeckoEngine.newEaSession(this)
        engineSession = session

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                AppState.update {
                    it.copy(pageTitle = title.orEmpty(), engineStatus = "Background EA session active")
                }
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                AppState.update { it.copy(pageUrl = url, engineStatus = "Background EA loading") }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                AppState.update {
                    it.copy(
                        running = true,
                        engineStatus = if (success) "Background Market Brain active" else "Background EA load failed",
                        lastEvent = if (success) "Background EA page loaded" else "Background EA load failed"
                    )
                }
                updateNotification(
                    if (success) {
                        "Market Brain active · " + if (AppState.status.value.dryRun) "Dry Run" else "Live"
                    } else {
                        "EA background load failed"
                    }
                )
            }
        }

        FcExtensionBridge.wireSession(this, session) {
            session.setActive(true)
            session.loadUri(GeckoEngine.EA_URL)
        }
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
            .setContentTitle("FC+ Market Brain")
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
            channel.description = "Keeps FC+ Market Brain active in the background."
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        runCatching { engineSession?.setActive(false) }
        runCatching { engineSession?.close() }
        engineSession = null

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
