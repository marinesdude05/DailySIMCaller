package com.example.dailysimcaller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Friendly progress text is separate from the technical Telecom status.
 * "Tried" means an attempt ended, NOT that someone answered the phone.
 */
internal data class CallProgressText(
    val badge: String,
    val headline: String,
    val explanation: String
)

internal object CallProgressWords {
    fun describe(
        stage: CallQueue.Stage,
        finished: Int,
        total: Int,
        gettingReady: Boolean
    ): CallProgressText {
        if (gettingReady && stage != CallQueue.Stage.CONNECTED &&
            stage != CallQueue.Stage.CALLING && stage != CallQueue.Stage.WAITING &&
            stage != CallQueue.Stage.BREAK) {
            return CallProgressText(
                "Getting ready", "Getting the phone numbers",
                "We are getting the call list. No new calls have started."
            )
        }
        return when (stage) {
            CallQueue.Stage.READY -> CallProgressText(
                "Ready", "Ready to start",
                "When setup is done, tap Call Now to begin."
            )
            CallQueue.Stage.CALLING -> CallProgressText(
                "Calling", "Calling number ${(finished + 1).coerceAtMost(total.coerceAtLeast(1))}",
                "The phone is starting this call."
            )
            CallQueue.Stage.WAITING -> CallProgressText(
                "Waiting", "Waiting for an answer",
                "The phone is ringing. We will move on if nobody answers."
            )
            CallQueue.Stage.CONNECTED -> CallProgressText(
                "On a call", "The call is connected",
                "The call timer starts when someone answers."
            )
            CallQueue.Stage.BREAK -> CallProgressText(
                "Short break", "Getting ready for the next call",
                "The next call will start soon."
            )
            CallQueue.Stage.FINISHED -> CallProgressText(
                "Finished", "All done!",
                "We tried each number in this test."
            )
            CallQueue.Stage.STOPPED -> CallProgressText(
                "Stopped", "The test was stopped",
                "You can start a new test when you are ready."
            )
            CallQueue.Stage.NEEDS_HELP -> CallProgressText(
                "Needs help", "The test could not keep going",
                "Check the message below to see what needs fixing."
            )
        }
    }
}

@Composable
internal fun SimpleCallProgressCard(
    finished: Int,
    total: Int,
    stage: CallQueue.Stage,
    gettingReady: Boolean,
    appMessage: String,
    technicalStatus: String
) {
    var showTechnical by rememberSaveable { mutableStateOf(false) }
    val display = CallProgressWords.describe(stage, finished, total, gettingReady)
    val tried = finished.coerceIn(0, total.coerceAtLeast(0))
    val fraction = if (total > 0) tried.toFloat() / total else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Call progress",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        display.badge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (total > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$tried",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "of $total calls tried",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                        .clip(RoundedCornerShape(50.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "${(total - tried).coerceAtLeast(0)} left to try",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "No calls tried yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                display.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                display.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (stage == CallQueue.Stage.CONNECTED) ConnectedCallTimer()

            // Show urgent setup/errors immediately; keep technical chatter folded away.
            val needsAttention = listOf(
                "error", "failed", "missing", "denied", "blocked",
                "could not", "unable", "grant", "permission"
            ).any { appMessage.contains(it, ignoreCase = true) }
            if (appMessage.isNotBlank() && appMessage != "Ready" &&
                (needsAttention || stage == CallQueue.Stage.NEEDS_HELP)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        appMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(onClick = { showTechnical = !showTechnical }) {
                Text(if (showTechnical) "Hide details" else "See more details")
            }
            if (showTechnical) {
                Text(
                    "Call details: $technicalStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (appMessage.isNotBlank() && appMessage != "Ready") {
                    Text(
                        "App message: $appMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "A call counts as tried when it ends, even if nobody answers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
