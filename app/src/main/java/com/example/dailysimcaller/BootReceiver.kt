package com.example.dailysimcaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("daily_enabled", false)) {
            if (!DailyAlarm.schedule(context)) DailyAlarm.scheduleInexact(context)
        }
    }
}
