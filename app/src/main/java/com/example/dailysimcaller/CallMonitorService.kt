package com.example.dailysimcaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** InCallService required by Android to act as the chosen default phone app. */
class CallMonitorService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        PhoneCallUi.active = call
        CallQueue.onCallAdded(call)
        showCallNotification(call)
    }

    override fun onCallRemoved(call: Call) {
        CallQueue.onCallRemoved(call)
        if (PhoneCallUi.active === call) PhoneCallUi.active = null
        getSystemService(NotificationManager::class.java).cancel(300)
        super.onCallRemoved(call)
    }

    private fun showCallNotification(call: Call) {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = "phone_controls"
        manager.createNotificationChannel(
            NotificationChannel(channel, "Phone call controls", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this, 300, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Phone call")
            .setContentText(call.details.handle?.schemeSpecificPart ?: "Call controls")
            .setContentIntent(pending)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .build()
        manager.notify(300, notification)
    }
}

/** Only the active Call is exposed to the simple Compose call-control UI. */
object PhoneCallUi {
    var active: Call? by mutableStateOf(null)
}
