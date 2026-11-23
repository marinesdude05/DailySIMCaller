package com.example.dailysimcaller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A step is DONE only when Android's actual permission, default-role, or
 * enabled Accessibility-service state says it is done. Opening Settings never counts.
 */
internal enum class SetupPermissionKey {
    PHONE_PERMISSION, NOTIFICATIONS, SHUTDOWN_HELPER, PHONE_APP, HOME_APP
}

internal data class SetupPermissionStep(
    val key: SetupPermissionKey,
    val title: String,
    val instruction: String,
    val action: String,
    val done: Boolean
)

internal object PermissionSetupSteps {
    fun build(
        phonePermissionGranted: Boolean,
        needsNotificationPermission: Boolean,
        notificationGranted: Boolean,
        helperRequired: Boolean,
        helperEnabled: Boolean,
        phoneRoleHeld: Boolean,
        homeRequired: Boolean,
        homeRoleHeld: Boolean
    ): List<SetupPermissionStep> = buildList {
        add(SetupPermissionStep(
            SetupPermissionKey.PHONE_PERMISSION, "Phone permission",
            "Let Daily SIM Caller make test calls.", "Allow phone calls",
            phonePermissionGranted
        ))
        if (needsNotificationPermission) add(SetupPermissionStep(
            SetupPermissionKey.NOTIFICATIONS, "Notifications",
            "Let the phone show you when testing is running.", "Allow notifications",
            notificationGranted
        ))
        if (homeRequired) add(SetupPermissionStep(
            SetupPermissionKey.HOME_APP, "Home app",
            "For calls after restart, choose Daily SIM Caller as the Home app.",
            "Choose Home app", homeRoleHeld
        ))
        if (helperRequired) add(SetupPermissionStep(
            SetupPermissionKey.SHUTDOWN_HELPER, "Shut Down Helper",
            "In Accessibility, choose Daily SIM Caller, then turn on Shut Down Helper.",
            "Open helper settings", helperEnabled
        ))
        add(SetupPermissionStep(
            SetupPermissionKey.PHONE_APP, "Phone app",
            "Choose Daily SIM Caller as the phone app when Android asks.",
            "Choose Phone app", phoneRoleHeld
        ))
    }
}

@Composable
internal fun PermissionSetupCard(
    steps: List<SetupPermissionStep>,
    onNext: (SetupPermissionKey) -> Unit,
    onCheckAgain: () -> Unit,
    onHelp: () -> Unit
) {
    val done = steps.count { it.done }
    val next = steps.firstOrNull { !it.done }
    val fraction = if (steps.isEmpty()) 1f else done.toFloat() / steps.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Set up your phone",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (next == null) "Everything is ready for this setup."
                else "Finish these steps. We'll show you what to do next.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$done of ${steps.size} ready",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            steps.forEachIndexed { index, step ->
                val active = step == next
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        step.done -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        active -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (step.done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                if (step.done) "✓" else "${index + 1}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = if (step.done) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(step.title, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold)
                            if (active) Text(
                                step.instruction,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (step.done) "Done" else if (active) "Next" else "To do",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (step.done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (next != null) {
                Button(
                    onClick = { onNext(next.key) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(next.action)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCheckAgain) { Text("Check again") }
                TextButton(onClick = onHelp) { Text("Need help?") }
            }
            Text(
                "Android asks you to approve each step. Only approved steps show Done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
