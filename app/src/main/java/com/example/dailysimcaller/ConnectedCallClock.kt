package com.example.dailysimcaller

import android.os.SystemClock
import android.telecom.Call
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.util.Locale

/** Tracks the answered portion of the current Telecom call, not the dialing/ringing time. */
internal object ConnectedCallClock {
    private var trackedCall: Call? = null
    var startedAtElapsedMs: Long? by mutableStateOf(null)
        private set

    fun onCallAdded(call: Call) {
        trackedCall = call
        startedAtElapsedMs = null
        onStateChanged(call, call.state)
    }

    fun onStateChanged(call: Call, state: Int) {
        if (trackedCall !== call) return
        if (state == Call.STATE_ACTIVE && startedAtElapsedMs == null) {
            // Start at the observed ACTIVE transition. A monotonic clock avoids
            // wall-clock changes and unreliable Telecom connection timestamps.
            startedAtElapsedMs = SystemClock.elapsedRealtime()
        } else if (state == Call.STATE_DISCONNECTED) {
            startedAtElapsedMs = null
        }
        // Holding is part of the same connected call; do not reset the timer.
    }

    fun onCallRemoved(call: Call) {
        if (trackedCall === call) {
            trackedCall = null
            startedAtElapsedMs = null
        }
    }

    fun clear() {
        trackedCall = null
        startedAtElapsedMs = null
    }
}

/** Reusable read-only counter for the main app and the ongoing-call screen. */
@Composable
internal fun ConnectedCallTimer() {
    val activeCall = PhoneCallUi.active
    val connectedAt = ConnectedCallClock.startedAtElapsedMs
    var nowElapsed by remember(connectedAt) { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(connectedAt, activeCall) {
        if (connectedAt != null && activeCall != null) {
            while (true) {
                nowElapsed = SystemClock.elapsedRealtime()
                delay(1000L)
            }
        }
    }

    if (activeCall != null) {
        if (connectedAt == null) {
            Text("Call time: 00:00 (starts when answered)",
                style = MaterialTheme.typography.titleMedium)
        } else {
            val seconds = ((nowElapsed - connectedAt).coerceAtLeast(0L)) / 1000L
            val hours = seconds / 3600L
            val minutes = (seconds % 3600L) / 60L
            val remainingSeconds = seconds % 60L
            val duration = if (hours > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds)
            }
            Text("Call time: $duration", style = MaterialTheme.typography.titleMedium)
        }
    }
}
