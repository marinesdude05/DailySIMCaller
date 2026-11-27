package com.example.dailysimcaller

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Explicit, independent consent for completed call metadata on authorized test phones. */
internal class CallSummaryConsent(private val activity: ComponentActivity) {
    val available get() = CallLogSync.available
    var connected by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("Call summary sync off")
        private set
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            busy = false
            status = "Google Sheets access declined"
        } else {
            try {
                complete(Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(result.data!!).accessToken)
            } catch (_: Exception) {
                busy = false
                status = "Google authorization failed; retry."
            }
        }
    }

    fun initialize() {
        connected = CallLogSync.configured(activity)
        status = when {
            !available -> "Call summary destination is not configured in this APK."
            connected -> "Call summary sync enabled; pending uploads retry automatically."
            else -> "Call summary sync off"
        }
        if (connected) {
            CallLogSync.schedulePeriodic(activity)
            CallLogSync.queueImmediate(activity)
        }
    }

    fun connect() {
        if (!available || busy) return
        if (!CallLogSync.hasGooglePlayServices(activity)) {
            status = "Google Play services are required."
            return
        }
        busy = true
        status = "Checking Google permission..."
        Identity.getAuthorizationClient(activity).authorize(CallLogSync.authorizationRequest())
            .addOnSuccessListener { auth ->
                try {
                    if (auth.hasResolution()) {
                        val pending = auth.pendingIntent ?: error("No consent window")
                        launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else complete(auth.accessToken)
                } catch (_: Exception) {
                    busy = false
                    status = "Could not open Google permission; retry."
                }
            }.addOnFailureListener {
                busy = false
                status = "Google authorization unavailable; retry."
            }
    }

    private fun complete(token: String?) {
        if (token.isNullOrBlank()) {
            busy = false
            status = "Google permission was not granted."
            return
        }
        activity.lifecycleScope.launch {
            try {
                status = CallLogSync.verifyAndConnect(activity, token)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                status = "Could not confirm sync. Check internet, Sheet access and device ID conflicts; retry."
            } finally {
                connected = CallLogSync.configured(activity)
                busy = false
            }
        }
    }

    fun syncNow() {
        if (!connected || busy) return
        busy = true
        activity.lifecycleScope.launch {
            try {
                status = CallLogSync.uploadPending(activity)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                status = "Upload not confirmed; check access and reconnect."
            } finally {
                busy = false
            }
        }
    }

    fun disconnect() {
        CallLogSync.disconnect(activity)
        connected = false
        status = "Call summary sync stopped; local records retained."
    }
}
