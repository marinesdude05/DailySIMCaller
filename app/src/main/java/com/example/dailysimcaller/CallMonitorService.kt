package com.example.dailysimcaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Android Telecom owns the call audio route; UI controls request changes via this InCallService. */
class CallMonitorService : InCallService() {
    private var monitoredCall: Call? = null
    private var callWatcher: Call.Callback? = null
    private var availableEndpoints: List<CallEndpoint> = emptyList()
    private var currentEndpoint: CallEndpoint? = null
    private var autoSpeakerPending = false
    private var manualOverride = false
    private var routeRequestInFlight = false
    private var pendingManualRouteChange = false

    override fun onCreate() {
        super.onCreate()
        SpeakerphoneControl.initialize(this)
        SpeakerphoneControl.attach(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        PhoneCallUi.active = call
        ConnectedCallClock.onCallAdded(call)
        CallQueue.onCallAdded(call)
        // Reapply the owner's saved speaker preference on EVERY new call.
        // Do not reset an OFF selection when the previous call ends.
        clearCallWatcher()
        monitoredCall = call
        currentEndpoint = null
        availableEndpoints = emptyList()
        manualOverride = false
        autoSpeakerPending = true
        routeRequestInFlight = false
        pendingManualRouteChange = false
        SpeakerphoneControl.updateCallPresent(true)
        SpeakerphoneControl.updateSpeakerOn(false) // New call audio route is not known yet.
        SpeakerphoneControl.updateMessage(
            if (SpeakerphoneControl.speakerPreferred)
                "Applying saved Speaker ON setting to this call"
            else "Applying saved Speaker OFF setting to this call"
        )
        val watcher = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                ConnectedCallClock.onStateChanged(call, state)
                tryAutoSpeaker()
            }
        }
        callWatcher = watcher
        call.registerCallback(watcher)
        tryAutoSpeaker()
        showCallNotification(call)
    }

    override fun onCallRemoved(call: Call) {
        ConnectedCallClock.onCallRemoved(call)
        CallQueue.onCallRemoved(call)
        if (PhoneCallUi.active === call) PhoneCallUi.active = null
        if (monitoredCall === call) {
            clearCallWatcher()
            monitoredCall = null
            autoSpeakerPending = false
            manualOverride = false
            routeRequestInFlight = false
            pendingManualRouteChange = false
            SpeakerphoneControl.updateCallPresent(false)
            SpeakerphoneControl.updateSpeakerOn(false)
            SpeakerphoneControl.updateMessage(
                if (SpeakerphoneControl.speakerPreferred)
                    "Speaker ON saved for future calls"
                else "Speaker OFF saved for future calls"
            )
        }
        getSystemService(NotificationManager::class.java).cancel(300)
        super.onCallRemoved(call)
    }

    private fun clearCallWatcher() {
        val call = monitoredCall
        val watcher = callWatcher
        if (call != null && watcher != null) call.unregisterCallback(watcher)
        callWatcher = null
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (Build.VERSION.SDK_INT < 34) {
            SpeakerphoneControl.updateSpeakerOn(audioState?.route == CallAudioState.ROUTE_SPEAKER)
            tryAutoSpeaker()
        }
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        if (Build.VERSION.SDK_INT >= 34) {
            this.availableEndpoints = availableEndpoints.toList()
            if (pendingManualRouteChange && !routeRequestInFlight) applyPendingManualRouteChange()
            tryAutoSpeaker()
        }
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        if (Build.VERSION.SDK_INT >= 34) {
            currentEndpoint = callEndpoint
            SpeakerphoneControl.updateSpeakerOn(callEndpoint.endpointType == CallEndpoint.TYPE_SPEAKER)
            if (pendingManualRouteChange && !routeRequestInFlight) applyPendingManualRouteChange()
            tryAutoSpeaker()
        }
    }

    private fun tryAutoSpeaker() {
        if (!autoSpeakerPending || manualOverride || routeRequestInFlight) return
        val call = monitoredCall ?: return
        if (call.state !in listOf(Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE)) return
        // Wait until Telecom has reported an actual route for THIS call.
        val currentIsSpeaker = if (Build.VERSION.SDK_INT >= 34) {
            val endpoint = currentEndpoint ?: return
            endpoint.endpointType == CallEndpoint.TYPE_SPEAKER
        } else {
            @Suppress("DEPRECATION")
            val audioState = callAudioState ?: return
            audioState.route == CallAudioState.ROUTE_SPEAKER
        }
        val desired = SpeakerphoneControl.speakerPreferred
        if (currentIsSpeaker == desired) {
            autoSpeakerPending = false
            SpeakerphoneControl.updateMessage(
                if (desired) "Speaker ON (saved for all calls)" else "Speaker OFF (saved for all calls)"
            )
            return
        }
        changeSpeakerRoute(enable = desired, automatic = true)
    }

    /** An explicit speaker selection is retained for the entire queue and future calls. */
    fun applySpeakerPreference() {
        manualOverride = true
        autoSpeakerPending = false
        if (monitoredCall == null) return
        if (routeRequestInFlight) {
            // Do not lose an OFF tap made while the prior ON request is in flight.
            pendingManualRouteChange = true
            return
        }
        changeSpeakerRoute(enable = SpeakerphoneControl.speakerPreferred, automatic = false)
    }

    private fun applyPendingManualRouteChange() {
        if (!pendingManualRouteChange || routeRequestInFlight || monitoredCall == null) return
        pendingManualRouteChange = false
        // Endpoint callbacks can lag a successful request: disregard stale route.
        currentEndpoint = null
        changeSpeakerRoute(enable = SpeakerphoneControl.speakerPreferred, automatic = false)
    }

    @Suppress("DEPRECATION")
    private fun changeSpeakerRoute(enable: Boolean, automatic: Boolean) {
        if (monitoredCall == null || routeRequestInFlight) return
        if (Build.VERSION.SDK_INT >= 34) {
            // Use only endpoints offered by Telecom; never invent a CallEndpoint.
            val endpoint = if (enable) {
                availableEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_SPEAKER }
            } else {
                availableEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_EARPIECE }
                    ?: availableEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_WIRED_HEADSET }
                    ?: availableEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_BLUETOOTH }
            }
            if (endpoint == null) {
                if (!automatic) SpeakerphoneControl.updateMessage("Requested audio route not available on this phone")
                return // Auto-on retries when Telecom reports available endpoints.
            }
            if (currentEndpoint?.identifier == endpoint.identifier) {
                SpeakerphoneControl.updateSpeakerOn(enable)
                if (automatic) autoSpeakerPending = false
                SpeakerphoneControl.updateMessage(if (enable) "Speakerphone ON" else "Speakerphone OFF")
                return
            }
            routeRequestInFlight = true
            SpeakerphoneControl.updateMessage(if (enable) "Switching to speaker..." else "Switching from speaker...")
            try {
                requestCallEndpointChange(endpoint, mainExecutor,
                    object : OutcomeReceiver<Void, CallEndpointException> {
                        override fun onResult(result: Void?) {
                            routeRequestInFlight = false
                            if (automatic) autoSpeakerPending = false
                            // Actual state is updated by onCallEndpointChanged, not guessed here.
                            SpeakerphoneControl.updateMessage(if (enable) "Speaker requested" else "Earpiece requested")
                            applyPendingManualRouteChange()
                        }
                        override fun onError(error: CallEndpointException) {
                            routeRequestInFlight = false
                            if (automatic) autoSpeakerPending = false
                            SpeakerphoneControl.updateMessage("Audio route change denied: ${error.message ?: "unknown error"}")
                            applyPendingManualRouteChange()
                        }
                    })
            } catch (error: Exception) {
                routeRequestInFlight = false
                if (automatic) autoSpeakerPending = false
                SpeakerphoneControl.updateMessage("Audio route unavailable: ${error.message ?: "unknown error"}")
                applyPendingManualRouteChange()
            }
        } else {
            val state = callAudioState ?: return // Telecom has not initialized call audio.
            val desiredRoute = if (enable) {
                CallAudioState.ROUTE_SPEAKER
            } else when {
                state.supportedRouteMask and CallAudioState.ROUTE_EARPIECE != 0 -> CallAudioState.ROUTE_EARPIECE
                state.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET != 0 -> CallAudioState.ROUTE_WIRED_HEADSET
                state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0 -> CallAudioState.ROUTE_BLUETOOTH
                else -> 0
            }
            if (desiredRoute == 0 || state.supportedRouteMask and desiredRoute == 0) {
                if (!automatic) SpeakerphoneControl.updateMessage("Requested audio route not available")
                return
            }
            if (state.route == desiredRoute) {
                SpeakerphoneControl.updateSpeakerOn(enable)
                if (automatic) autoSpeakerPending = false
                return
            }
            try {
                setAudioRoute(desiredRoute)
                if (automatic) autoSpeakerPending = false
                SpeakerphoneControl.updateMessage(if (enable) "Speaker requested" else "Earpiece requested")
            } catch (error: Exception) {
                if (automatic) autoSpeakerPending = false
                SpeakerphoneControl.updateMessage("Audio route unavailable: ${error.message ?: "unknown error"}")
            }
        }
    }

    override fun onDestroy() {
        clearCallWatcher()
        ConnectedCallClock.clear()
        monitoredCall = null
        SpeakerphoneControl.detach(this)
        super.onDestroy()
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

/** The active call and route are Compose-observable on both the main and in-call screens. */
object PhoneCallUi {
    var active: Call? by mutableStateOf(null)
}

object SpeakerphoneControl {
    private const val PREFS = "speaker_settings"
    private const val SPEAKER_PREFERRED = "speaker_preferred"
    private var service: CallMonitorService? = null
    var callPresent by mutableStateOf(false)
        private set
    var speakerOn by mutableStateOf(false)
        private set
    /** Persisted choice; not confused with Telecom's live audio route. */
    var speakerPreferred by mutableStateOf(true)
        private set
    var message by mutableStateOf("Speaker ON saved for all calls")
        private set

    fun initialize(context: Context) {
        speakerPreferred = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(SPEAKER_PREFERRED, true)
        if (!callPresent) message =
            if (speakerPreferred) "Speaker ON saved for all calls"
            else "Speaker OFF saved for all calls"
    }

    fun attach(newService: CallMonitorService) { service = newService }
    fun detach(oldService: CallMonitorService) {
        if (service === oldService) {
            service = null
            updateCallPresent(false)
            updateSpeakerOn(false)
        }
    }
    fun updateCallPresent(present: Boolean) { callPresent = present }
    fun updateSpeakerOn(enabled: Boolean) { speakerOn = enabled }
    fun updateMessage(text: String) { message = text }
    fun toggle(context: Context) {
        speakerPreferred = !speakerPreferred
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(SPEAKER_PREFERRED, speakerPreferred).apply()
        updateMessage(
            if (speakerPreferred) "Speaker ON saved for all calls"
            else "Speaker OFF saved for all calls"
        )
        service?.applySpeakerPreference()
    }
}
