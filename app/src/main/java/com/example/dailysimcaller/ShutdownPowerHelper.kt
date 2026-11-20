package com.example.dailysimcaller

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.ComponentName
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
            message = "Enable Daily SIM Caller shutdown helper in Accessibility settings first"
            return false
        }
        // Test only opens the menu; never clicks Power off.
        val result = service.openPowerMenu(testOnly = true)
        message = if (result) "Test menu opened. Close with Back; no shutdown requested" else "Android did not open the power menu"
        return result
    }

    /** Final guided-setup step: show Android's power dialog for a USER-SELECTED restart. */
    fun showRestartMenu(): Boolean {
        val service = ShutdownAccessibilityService.instance
        if (service == null) {
            message = "Shut Down Helper is not connected. Hold the power button to restart."
            return false
        }
        // testOnly is essential: it must never select Power off, Restart, or confirmations.
        val opened = service.openPowerMenu(testOnly = true)
        message = if (opened) "Power menu opened. Choose Restart yourself if ready."
            else "Android did not open the power menu; use the phone's power button."
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
    private var validUntil = 0L
    private var clickCount = 0
    private var lastClickAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ShutdownPowerHelper.report("Shutdown helper connected. Auto power off requires opt-in")
    }

    override fun onDestroy() {
        cancelPowerOffRequest()
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun cancelPowerOffRequest() {
        pendingShutdown = false
        validUntil = 0L
        clickCount = 0
    }

    /** Test only opens the dialog; production mode may click exact Power off. */
    fun openPowerMenu(testOnly: Boolean): Boolean {
        cancelPowerOffRequest()
        val opened = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
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
        if (!pendingShutdown || event == null) return
        val now = SystemClock.elapsedRealtime()
        if (now > validUntil) { pendingShutdown = false; return }
        if (now - lastClickAt < 900L) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        // Never interact with another app, incoming call, or unrelated dialog.
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName != "com.android.systemui" && packageName != "android") return
        val root = rootInActiveWindow ?: return
        val rootPackage = root.packageName?.toString().orEmpty()
        if (rootPackage != "com.android.systemui" && rootPackage != "android") return
        val matches = root.findAccessibilityNodeInfosByText("Power off")
        val target = matches.firstOrNull { node ->
            node.text?.toString()?.trim()?.equals("Power off", true) == true ||
                node.contentDescription?.toString()?.trim()?.equals("Power off", true) == true
        } ?: return
        var clickable: AccessibilityNodeInfo? = target
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            clickCount++
            lastClickAt = now
            ShutdownPowerHelper.report("Power off selected in Android system menu")
            // Some vendors show a second confirmation button with the same label.
            // Never click other labels or click more than two times.
            if (clickCount >= 2) pendingShutdown = false
        }
    }

    override fun onInterrupt() { cancelPowerOffRequest() }
}
