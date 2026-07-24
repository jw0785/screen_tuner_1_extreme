package com.androidscreentuner.extreme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("screen_tuner_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("master_enabled", false)) return

        val nightLight = Settings.Secure.getInt(
            context.contentResolver, "night_display_activated", 0
        )
        val extraDim = Settings.Secure.getInt(
            context.contentResolver, "reduce_bright_colors_activated", 0
        )
        if (nightLight == 1 || extraDim == 1) {
            Log.w("ScreenTuner", "Night Light or Extra Dim is enabled, may conflict")
        }

        context.startForegroundService(Intent(context, ColorService::class.java))
    }
}
