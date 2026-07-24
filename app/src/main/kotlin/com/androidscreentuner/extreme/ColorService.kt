package com.androidscreentuner.extreme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.app.Service
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ColorService : Service() {

    companion object {
        const val CHANNEL_ID = "screen_tuner"
        const val NOTIFICATION_ID = 1
        private const val INTERVAL_MS = 60_000L
        private const val SUNSET_REFRESH_MS = 12 * 60 * 60 * 1000L
        @Volatile var previewUntilMs: Long = 0
    }

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var initialized = false
    private var cachedSunsetMinutes = -1
    private var lastSunsetRefreshMs = 0L

    private val periodicUpdate = object : Runnable {
        override fun run() {
            Thread { updateColorTransform() }.start()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("screen_tuner_prefs", Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
        lastSunsetRefreshMs = 0

        Thread {
            if (!initialized) {
                if (SurfaceFlingerManager.transactionCode == -1) {
                    val cached = prefs.getInt("sf_transaction_code", -1)
                    if (!SurfaceFlingerManager.probeWithCached(cached)) {
                        handler.post { stopSelf() }
                        return@Thread
                    }
                    prefs.edit()
                        .putInt("sf_transaction_code", SurfaceFlingerManager.transactionCode)
                        .apply()
                }
                initialized = true
                handler.post {
                    handler.removeCallbacks(periodicUpdate)
                    handler.postDelayed(periodicUpdate, INTERVAL_MS)
                }
            }
            updateColorTransform()
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(periodicUpdate)
        initialized = false
        ScheduleCalculator.clear()
        Thread {
            SurfaceFlingerManager.applyIdentity()
            SurfaceFlingerManager.closeRootShell()
        }.start()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateColorTransform() {
        if (System.currentTimeMillis() < previewUntilMs) return

        val dimFactor = prefs.getFloat("dim_factor", 0.5f)
        val daytimeK = prefs.getInt("daytime_k", 5000)
        val sunsetK = prefs.getInt("sunset_k", 4200)
        val bedtimeK = prefs.getInt("bedtime_k", 3800)
        val wakeHour = prefs.getInt("wake_hour", 7)
        val wakeMinute = prefs.getInt("wake_minute", 0)
        val useLocation = prefs.getBoolean("use_location", false)

        val wakeMinutes = wakeHour * 60 + wakeMinute

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastSunsetRefreshMs > SUNSET_REFRESH_MS || cachedSunsetMinutes < 0) {
            cachedSunsetMinutes = calculateSunsetMinutes(wakeMinutes, useLocation)
            lastSunsetRefreshMs = nowMs
        }

        ScheduleCalculator.updateSchedule(wakeMinutes, cachedSunsetMinutes)

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val currentK = ScheduleCalculator.tick(currentMinutes, daytimeK, sunsetK, bedtimeK)

        val matrix = ColorMath.buildMatrix(currentK, dimFactor)
        SurfaceFlingerManager.applyMatrix(matrix)

        val dimPercent = (dimFactor * 100).toInt()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("${currentK}K / ${dimPercent}%"))
    }

    private fun calculateSunsetMinutes(wakeMinutes: Int, useLocation: Boolean): Int {
        if (useLocation) {
            val lat = prefs.getFloat("cached_latitude", 0f).toDouble()
            val lng = prefs.getFloat("cached_longitude", 0f).toDouble()
            if (lat != 0.0 || lng != 0.0) {
                return SunCalculator.calculate(lat, lng, Calendar.getInstance()).sunsetMinutes
            }
        }
        return (wakeMinutes + 720) % 1440
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
