package com.example.dailysimcaller

import android.app.KeyguardManager
import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Opt-in one-shot sequence, only after a reboot and once the Home Activity is visible/unlocked. */
object StartupMode {
    const val AUTO_ENABLED = "auto_start_enabled"
    const val BOOT_PENDING = "boot_call_pending"
    const val ACTION_BOOT_READY = "com.example.dailysimcaller.BOOT_READY"
    private const val LAST_BOOT_AT = "last_boot_receiver_at"
    private const val LAST_BOOT_RESULT = "last_boot_receiver_result"

    // Owner-requested v1.3.5 migration: enable automatic authorized calling once on
    // upgrade, then respect any future OFF selection. Calling still requires roles,
    // initial setup launch, a subsequent boot, an unlocked Home activity and sync.
    private const val V134_CONSENT = "autocall_v134_opt_in"
    private const val V135_DEFAULTS_APPLIED = "autocall_v135_defaults_applied"

    fun enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(V135_DEFAULTS_APPLIED, false)) {
            val edit = prefs.edit().putBoolean(V135_DEFAULTS_APPLIED, true)
            edit.putBoolean(V134_CONSENT, true).putBoolean(AUTO_ENABLED, true)
            edit.commit()
        }
        return prefs.getBoolean(V134_CONSENT, false) && prefs.getBoolean(AUTO_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(V134_CONSENT, true).putBoolean(AUTO_ENABLED, enabled)
            .putBoolean(BOOT_PENDING, false).commit()
    }

    fun recordBootResult(context: Context, result: String) {
        context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
            .edit().putString(LAST_BOOT_RESULT, result).apply()
    }

    fun lastBootReport(context: Context): String {
        val prefs = context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(LAST_BOOT_AT, 0L)
        if (timestamp <= 0) return "No restart signal recorded yet"
        val whenSeen = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(timestamp))
        return "$whenSeen: ${prefs.getString(LAST_BOOT_RESULT, "Unknown")}"
    }

    /** Read-only checks; never place a call or grant a permission. */
    fun readiness(context: Context, hasCsvSource: Boolean): String = when {
        !enabled(context) -> "OFF: automatic calling after reboot is disabled"
        !context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
            .getBoolean("guided_setup_started_v136", false) -> "Complete initial setup"
        !isHomeApp(context) -> "Not ready: default Home app is missing"
        !isDefaultPhoneApp(context) -> "Not ready: default Phone app is missing"
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ->
            "Not ready: Phone permission is missing"
        Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
            "Not ready: Notifications permission is missing"
        !hasCsvSource -> "Not ready: no authorized number-list source"
        !isUnlocked(context) -> "Waiting for unlock"
        else -> "Ready for next reboot; a successful refresh is still required"
    }

    fun isPending(context: Context): Boolean =
        context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
            .getBoolean(BOOT_PENDING, false)

    fun armAfterBoot(context: Context) {
        val prefs = context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
        // commit() makes the one-shot marker available to the Home activity immediately.
        val armed = enabled(context)
        prefs.edit().putBoolean(BOOT_PENDING, armed)
            .putLong(LAST_BOOT_AT, System.currentTimeMillis())
            .putString(LAST_BOOT_RESULT, if (armed)
                "Boot receiver ran; waiting for visible and unlocked Home app"
                else "Boot receiver ran; auto-calling is off")
            .commit()
    }

    fun clearPending(context: Context) {
        context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(BOOT_PENDING, false).commit()
    }

    fun consumePending(context: Context): Boolean {
        if (!enabled(context) || !isPending(context)) return false
        clearPending(context)
        return true
    }

    fun isHomeApp(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            return roles.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        @Suppress("DEPRECATION")
        val resolved = context.packageManager.resolveActivity(
            home, PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun isDefaultPhoneApp(context: Context): Boolean =
        context.getSystemService(TelecomManager::class.java).defaultDialerPackage == context.packageName

    fun isUnlocked(context: Context): Boolean =
        !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
}
