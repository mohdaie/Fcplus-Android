package com.fcplus.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class TraderService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val watchdog = object : Runnable {
        override fun run() {
            if (!TradingStore.running || System.currentTimeMillis() >= TradingStore.deadline) {
                if (TradingStore.running) TradingStore.stop("Session time limit reached")
                stopSelf(); return
            }
            val last = TradingStore.snapshot.optLong("capturedAt")
            if (last > 0 && System.currentTimeMillis() - last > 30000) {
                TradingStore.stop("EA heartbeat lost; trading stopped"); stopSelf(); return
            }
            getSystemService(NotificationManager::class.java).notify(2701, notification(TradingStore.state.optString("message", "Waiting for EA")))
            handler.postDelayed(this, 5000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("fcplus_trader", "FC+ trader", NotificationManager.IMPORTANCE_LOW))
        startForeground(2701, notification("Starting bounded trading session"))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { TradingStore.stop("Stopped by you"); stopSelf(); return START_NOT_STICKY }
        if (TradingStore.running) return START_NOT_STICKY
        val error = TradingStore.start()
        if (error != null) { TradingStore.stop(error); stopSelf(); return START_NOT_STICKY }
        GeckoEngine.eaSession(this).setActive(true)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"FCPlus::Trader").apply {
            setReferenceCounted(false); acquire((TradingStore.deadline - System.currentTimeMillis()).coerceAtLeast(1000))
        }
        AppState.update { it.copy(running = true, securityStop = false, engineStatus = "Trader active · " + if (TradingStore.settings.optBoolean("dryRun",true)) "Dry Run" else "Live") }
        handler.post(watchdog)
        return START_NOT_STICKY
    }
    private fun notification(message: String): Notification {
        val launch = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this,1,Intent(this,TraderService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,"fcplus_trader").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("FC+ · " + if (TradingStore.settings.optBoolean("dryRun",true)) "Dry Run" else "Live trader")
            .setContentText(message).setContentIntent(launch).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause,"Stop",stop).build()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (TradingStore.running) TradingStore.stop("Trading service stopped")
        super.onDestroy()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { TradingStore.stop("Android background time limit reached"); stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null
}
