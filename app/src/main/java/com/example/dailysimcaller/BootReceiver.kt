package com.example.dailysimcaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Arms opt-in post-unlock calling and schedules quiet preparation; never dials from this receiver. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // Armed once per reboot, but only after the user opted into v1.3.4 auto-calling.
        // A visible, unlocked Home activity handles the queue, never this receiver.
        StartupMode.armAfterBoot(context)
        if (BootPreparation.isEnabled(context)) BootPreparation.schedule(context)
        // Older releases may have left a 10 AM reminder pending.
        DailyAlarm.clearLegacyReminder(context)
        context.sendBroadcast(Intent(StartupMode.ACTION_BOOT_READY).setPackage(context.packageName))
    }
}
