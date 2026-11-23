package com.example.dailysimcaller

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Experimental Android 16 power-off helper for the owner's dedicated phone.
 * Owner-requested one-time v1.3.5 ON default; any later OFF change is retained.
 * Only an explicitly enabled Accessibility Service is allowed
 * to interact with the system power menu; never escalates privileges.
 */
object ShutdownPowerHelper {
    private const val PREFS = "power_off_preferences"
    private const val ENABLED = "after_queue_power_off_enabled"
    private const val V135_DEFAULTS_APPLIED = "after_queue_power_off_v135_defaults_applied"
    private val handler = Handler(Looper.getMainLooper())
    private var queueGeneration = 0
    private var delayedRequest: Runnable? = null

    var message by mutableStateOf("Power-off setting follows saved preference; helper requires Accessibility")
        private set

    /** Recompose setup UI and retry pending setup restart when Android binds the service. */
    var connectionVersion by mutableIntStateOf(0)
        private set

    internal fun onConnectionChanged() { connectionVersion++ }

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(V135_DEFAULTS_APPLIED, false)) {
            prefs.edit().putBoolean(ENABLED, true)
                .putBoolean(V135_DEFAULTS_APPLIED, true).commit()
        }
        return prefs.getBoolean(ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(ENABLED, enabled).apply()
        message = if (enabled) "Power off after completed queue: armed" else "Automatic power off is OFF"
        if (!enabled) cancelPending()
    }

    fun isServiceConnected(): Boolean = ShutdownAccessibilityService.instance != null

    fun connectionStatus(context: Context): String {
        if (isServiceConnected()) return "Connected"
        val enabled = try {
            Settings.Secure.getString(context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').any { it.equals(ComponentName(context,
                    ShutdownAccessibilityService::class.java).flattenToString(), ignoreCase = true) }
        } catch (_: SecurityException) {
            false
        }
        return if (enabled) "Enabled in Android; waiting for service connection"
            else "Not enabled in Android Accessibility settings"
    }

    fun testPowerMenu(): Boolean {
        val service = ShutdownAccessibilityService.instance
        if (service == null) {
            message = "Power Menu unavailable: Shut Down Helper is not connected. Open Android Accessibility settings, enable the helper, and come back."
            return false
        }
        // Test only opens the menu; never clicks Power off.
        val result = service.openPowerMenu(testOnly = true)
        message = if (result)
            "Power menu requested. If no menu appeared, Android may restrict this action on your phone."
        else
            "Android refused to open the power menu. Check the helper in Accessibility settings; the device may not support this action."
        return result
    }

    /** Final guided-setup step: show Android's power dialog for a USER-SELECTED restart. */
    fun showRestartMenu(): Boolean {
        val service = ShutdownAccessibilityService.instance
        if (service == null) {
            message = "Shut Down Helper is not connected. Hold the power button to restart."
            return false
        }
        val opened = service.openPowerMenu(testOnly = true)
        message = if (opened) "Power menu opened. Choose Restart yourself if ready."
            else "Android did not open the power menu; use the phone's power button."
        return opened
    }

    /**
     * Only called after the user explicitly completes the setup guide (or taps Retry).
     * Device-owner reboot is reliable where provisioned; on ordinary fleet phones the
     * enabled Accessibility helper may select the exact system "Restart" button.
     * No call or running test queue may be interrupted by this request.
     */
    fun restartAfterSetup(context: Context): Boolean {
        if (CallQueue.running || PhoneCallUi.active != null) {
            message = "Restart postponed: an active phone call or test queue must finish first."
            return false
        }
        if (ManagedDefaultApps.isDeviceOwner(context)) {
            try {
                context.getSystemService(DevicePolicyManager::class.java).reboot(
                    ComponentName(context, DailySimDeviceAdminReceiver::class.java)
                )
                message = "Automatic restart requested by device-owner policy."
                return true
            } catch (_: IllegalStateException) {
                message = "Android refused restart because a call is still active."
                return false
            } catch (_: SecurityException) {
                // Device-owner role may have changed. Try only the user-enabled helper.
            } catch (e: Exception) {
                message = "Android could not restart: ${e.message ?: "Unknown error"}."
                return false
            }
        }
        val service = ShutdownAccessibilityService.instance
        if (service == null) {
            message = "Automatic restart requires device-owner access or enabled Shut Down Helper. Restart manually with the phone power button."
            return false
        }
        val opened = service.requestAutomaticRestart()
        message = if (opened)
            "Power menu opened; Shut Down Helper will select Restart if Android exposes that button."
        else "Android refused to open the power menu. Hold the phone's power button to restart."
        return opened
    }

    fun queueStarted() {
        cancelPending()
        message = "Call queue running; no shutdown until completion"
    }

    fun cancelPending() {
        queueGeneration++
        delayedRequest?.let(handler::removeCallbacks)
        delayedRequest = null
        ShutdownAccessibilityService.instance?.cancelPowerOffRequest()
    }

    /** Called only for a naturally finished queue, never an error or Stop. */
    fun queueFinished(context: Context) {
        if (!isEnabled(context)) return
        val generation = queueGeneration
        val app = context.applicationContext
        val request = Runnable {
            delayedRequest = null
            if (queueGeneration != generation || CallQueue.running || PhoneCallUi.active != null) {
                message = "Shutdown cancelled: another call or session is active"
                return@Runnable
            }
            if (!isEnabled(app)) return@Runnable
            val service = ShutdownAccessibilityService.instance
            if (service == null) {
                message = "Shutdown not available: Accessibility service isn't enabled"
                return@Runnable
            }
            message = "Queue complete. Requesting Android power-off menu"
            val opened = service.openPowerMenu(testOnly = false)
            if (!opened) message = "Shutdown unavailable: Android refused the power menu"
        }
        // onCallRemoved must finish and clear the in-call state before a shutdown attempt.
        delayedRequest = request
        handler.postDelayed(request, 3_000L)
    }

    internal fun report(messageText: String) { message = messageText }
}

/**
 * No broad automation: only reacts for a brief period to the system's power
 * dialog, looking for an exact 'Power off' label. Device UI varies by vendor.
 */
class ShutdownAccessibilityService : AccessibilityService() {
    companion object {
        var instance: ShutdownAccessibilityService? = null
            private set
    }

    private var pendingShutdown = false
    private var pendingRestart = false
    private var validUntil = 0L
    private var clickCount = 0
    private var lastClickAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ShutdownPowerHelper.onConnectionChanged()
        ShutdownPowerHelper.report("Shutdown Helper connected and ready to request Android's power menu.")
    }

    override fun onDestroy() {
        cancelPowerOffRequest()
        if (instance === this) {
            instance = null
            ShutdownPowerHelper.onConnectionChanged()
        }
        super.onDestroy()
    }

    fun cancelPowerOffRequest() {
        pendingShutdown = false
        pendingRestart = false
        validUntil = 0L
        clickCount = 0
    }

    /** Open the system power dialog and click Restart only, never Power off. */
    fun requestAutomaticRestart(): Boolean {
        cancelPowerOffRequest()
        val opened = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        if (opened) {
            pendingRestart = true
            validUntil = SystemClock.elapsedRealtime() + 12_000L
            handler.postDelayed({
                if (pendingRestart && SystemClock.elapsedRealtime() >= validUntil) {
                    pendingRestart = false
                    ShutdownPowerHelper.report("Restart was not confirmed automatically. Tap Restart in the power menu or hold the phone's power button.")
                }
            }, 12_100L)
        }
        return opened
    }

    /** Test only opens the dialog; production mode may click exact Power off. */
    fun openPowerMenu(testOnly: Boolean): Boolean {
        cancelPowerOffRequest()
        val opened = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        if (!opened) {
            ShutdownPowerHelper.report("Android rejected the Power Menu action. Check the helper's Accessibility switch or use the physical power key.")
        }
        if (opened && !testOnly) {
            pendingShutdown = true
            validUntil = SystemClock.elapsedRealtime() + 12_000L
            handler.postDelayed({
                if (pendingShutdown && SystemClock.elapsedRealtime() >= validUntil) {
                    pendingShutdown = false
                    ShutdownPowerHelper.report("Power off not confirmed; phone may require manual action")
                }
            }, 12_100L)
        }
        return opened
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if ((!pendingShutdown && !pendingRestart) || event == null) return
        val now = SystemClock.elapsedRealtime()
        if (now > validUntil) {
            pendingShutdown = false
            pendingRestart = false
            return
        }
        if (now - lastClickAt < 900L) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        // Never interact with another app, incoming call, or unrelated dialog.
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName != "com.android.systemui" && packageName != "android") return
        val root = rootInActiveWindow ?: return
        val rootPackage = root.packageName?.toString().orEmpty()
        if (rootPackage != "com.android.systemui" && rootPackage != "android") return
        val actionLabel = if (pendingRestart) "Restart" else "Power off"
        val matches = root.findAccessibilityNodeInfosByText(actionLabel)
        val target = matches.firstOrNull { node ->
            node.text?.toString()?.trim()?.equals(actionLabel, true) == true ||
                node.contentDescription?.toString()?.trim()?.equals(actionLabel, true) == true
        } ?: return
        var clickable: AccessibilityNodeInfo? = target
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            clickCount++
            lastClickAt = now
            ShutdownPowerHelper.report(
                if (pendingRestart) "Restart selected in Android system menu"
                else "Power off selected in Android system menu"
            )
            // Some vendors show a second confirmation with the same exact label.
            // Never click another action or perform more than two clicks.
            if (clickCount >= 2) {
                pendingShutdown = false
                pendingRestart = false
            }
        }
    }

    override fun onInterrupt() { cancelPowerOffRequest() }
}
