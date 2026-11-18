package com.example.dailysimcaller

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.time.ZoneId
import java.time.ZonedDateTime

/** Reminder at 10:00 AM America/Chicago (handles daylight-saving transitions). */
object DailyAlarm {
    const val PREFS = "daily_sim_caller"

    private fun nextAlarmTime(): Long {
        val now = ZonedDateTime.now(ZoneId.of("America/Chicago"))
        var next = now.withHour(10).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 100, Intent(context, DailyCallReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) return false
        return try {
            alarms.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, nextAlarmTime(), pending(context)
            )
            true
        } catch (_: SecurityException) { false }
    }

    fun scheduleInexact(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime(), pending(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
        pending(context).cancel()
    }

    fun requestExactAlarmAccess(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                )
            } catch (_: Exception) {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                })
            }
        }
    }
}
