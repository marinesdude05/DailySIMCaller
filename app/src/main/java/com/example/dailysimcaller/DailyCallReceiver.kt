package com.example.dailysimcaller

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** The alarm never places calls by itself: it prompts the user to start a run. */
class DailyCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("daily_enabled", false)) return
        if (!DailyAlarm.schedule(context)) DailyAlarm.scheduleInexact(context)

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "daily_reminder"
        manager.createNotificationChannel(
            NotificationChannel(channel, "Daily call reminder", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = Intent(context, MainActivity::class.java)
            .putExtra("daily_reminder", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 101, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Daily SIM Caller: 10 AM")
            .setContentText("Open the app to refresh your CSV and start the call sequence.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        manager.notify(100, notification)
    }
}
