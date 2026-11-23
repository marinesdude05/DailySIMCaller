package com.example.dailysimcaller

import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import android.telecom.Call
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Basic incoming/ongoing call controls for the default dialer role. */
class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        SpeakerphoneControl.initialize(this)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContent {
            val call = PhoneCallUi.active
            var state by androidx.compose.runtime.remember(call) { mutableIntStateOf(call?.state ?: -1) }
            androidx.compose.runtime.DisposableEffect(call) {
                val watcher = object : Call.Callback() {
                    override fun onStateChanged(call: Call, newState: Int) {
                        state = newState
                    }
                }
                call?.registerCallback(watcher)
                onDispose { call?.unregisterCallback(watcher) }
            }
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Daily SIM Caller", style = MaterialTheme.typography.headlineMedium)
                    Text(call?.details?.handle?.schemeSpecificPart ?: "No active call")
                    Text("Call state: ${stateLabel(state)}")
                    ConnectedCallTimer()
                    OutlinedButton(
                        onClick = { SpeakerphoneControl.toggle(this@InCallActivity) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (SpeakerphoneControl.speakerPreferred)
                        "Speaker ON - Turn OFF" else "Speaker OFF - Turn ON") }
                    Text(SpeakerphoneControl.message)
                    if (call != null) {
                        if (state == Call.STATE_RINGING) {
                            Button(onClick = { call.answer(VideoProfile.STATE_AUDIO_ONLY) },
                                modifier = Modifier.fillMaxWidth()) { Text("Answer") }
                            OutlinedButton(onClick = { call.reject(false, null) },
                                modifier = Modifier.fillMaxWidth()) { Text("Decline") }
                        }
                        OutlinedButton(onClick = { call.disconnect() },
                            modifier = Modifier.fillMaxWidth()) { Text("End call") }
                    }
                    OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Return to app")
                    }
                }
            }
        }
    }

    private fun stateLabel(state: Int) = when (state) {
        Call.STATE_RINGING -> "Ringing"
        Call.STATE_DIALING -> "Dialing"
        Call.STATE_CONNECTING -> "Connecting"
        Call.STATE_ACTIVE -> "Connected"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_DISCONNECTED -> "Disconnected"
        else -> "Unknown"
    }
}
