package com.example.dailysimcaller

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Compatibility cleanup only. No daily reminder is scheduled by this app. */
object DailyAlarm {
    const val PREFS = "daily_sim_caller"

    fun clearLegacyReminder(context: Context) {
        // Match the exact broadcast component and request code used in v1.3.6.
        // We intentionally do not create a new PendingIntent or new reminder.
        val oldReceiver = Intent().setClassName(context, "com.example.dailysimcaller.DailyCallReceiver")
        val pending = PendingIntent.getBroadcast(
            context, 100, oldReceiver,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            context.getSystemService(AlarmManager::class.java).cancel(pending)
            pending.cancel()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove("daily_enabled").apply()
    }
}
