package com.example.dailysimcaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Arms opt-in post-unlock calling and schedules quiet preparation; never dials from this receiver. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // Fold setup after the first actual reboot following the setup restart attempt.
        val prefs = context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SetupRestartPolicy.ATTEMPTED_PREF, false) &&
            !prefs.getBoolean("setup_first_reboot_seen_v48", false)) {
            prefs.edit()
                .putBoolean("setup_first_reboot_seen_v48", true)
                .remove("setup_panel_expanded_v48")
                .commit()
        }
        // Armed once per reboot, but only after the user opted into v1.3.4 auto-calling.
        // A visible, unlocked Home activity handles the queue, never this receiver.
        StartupMode.armAfterBoot(context)
        if (BootPreparation.isEnabled(context)) BootPreparation.schedule(context)
        // Older releases may have left a 10 AM reminder pending.
        DailyAlarm.clearLegacyReminder(context)
        context.sendBroadcast(Intent(StartupMode.ACTION_BOOT_READY).setPackage(context.packageName))
    }
}
