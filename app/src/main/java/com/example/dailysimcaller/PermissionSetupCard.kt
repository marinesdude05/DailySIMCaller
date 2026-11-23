package com.example.dailysimcaller

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * No step is approved just because a screen opened or the operator pressed Next.
 * These are snapshots of Android runtime permissions, system default roles and
 * the enabled Accessibility service.
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
            "Allow Daily SIM Caller to make test calls.", "Allow phone calls",
            phonePermissionGranted
        ))
        if (needsNotificationPermission) add(SetupPermissionStep(
            SetupPermissionKey.NOTIFICATIONS, "Notifications",
            "Allow updates while a call test is running.", "Allow notifications",
            notificationGranted
        ))
        if (homeRequired) add(SetupPermissionStep(
            SetupPermissionKey.HOME_APP, "Home app",
            "Choose Daily SIM Caller as Home for tests after restart.", "Choose Home app",
            homeRoleHeld
        ))
        if (helperRequired) add(SetupPermissionStep(
            SetupPermissionKey.SHUTDOWN_HELPER, "Shut Down Helper",
            "In Accessibility, turn on Daily SIM Caller shutdown helper.", "Open helper settings",
            helperEnabled
        ))
        add(SetupPermissionStep(
            SetupPermissionKey.PHONE_APP, "Phone app",
            "Choose Daily SIM Caller as Android's default Phone app.", "Choose Phone app",
            phoneRoleHeld
        ))
    }

    fun next(steps: List<SetupPermissionStep>): SetupPermissionStep? =
        steps.firstOrNull { !it.done }

    fun allApproved(steps: List<SetupPermissionStep>): Boolean =
        steps.isNotEmpty() && steps.all { it.done }

    /** Allows staff to visit any next screen; earlier roles stay unapproved. */
    fun nextAfter(steps: List<SetupPermissionStep>, current: SetupPermissionKey): SetupPermissionStep? {
        val position = steps.indexOfFirst { it.key == current }
        if (position < 0) return null
        return steps.drop(position + 1).firstOrNull { !it.done }
    }
}

@Composable
internal fun PermissionSetupCard(
    steps: List<SetupPermissionStep>,
    onNext: (SetupPermissionKey) -> Unit,
    onCheckAgain: () -> Unit,
    onHelp: () -> Unit,
    onBlockedHelper: () -> Unit,
    phoneRoleBlocked: Boolean,
    onFixPhoneRole: () -> Unit,
    helperAttemptedBlocked: Boolean,
    onRestartPhone: () -> Unit,
    restartStatus: String
) {
    // The reference recordings show a compact, tappable checklist with green
    // confirmations and warning indicators, followed by Android's own settings.
    val panel = Color(0xFF17212B)
    val line = Color(0xFF27333E)
    val row = Color(0xFF1F2B37)
    val selected = Color(0xFF263E56)
    val good = Color(0xFF6CC99E)
    val warning = Color(0xFFFFC078)
    val heading = Color(0xFFF5F8FC)
    val muted = Color(0xFFCAD6E1)
    val action = Color(0xFF3C86CC)

    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val firstMissing = PermissionSetupSteps.next(steps)
    val current = steps.firstOrNull { !it.done && it.key.name == selectedKey } ?: firstMissing
    val following = current?.let { PermissionSetupSteps.nextAfter(steps, it.key) }
    val readyCount = steps.count { it.done }
    val progress = if (steps.isEmpty()) 1f else readyCount.toFloat() / steps.size
    val earlierMissing = if (current == null) false else
        steps.takeWhile { it.key != current.key }.any { !it.done }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = panel),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text("SET UP YOUR PHONE", color = heading,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Tap a step to open its Android settings. Return here to see what is done.",
                color = muted,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$readyCount of ${steps.size} ready", color = heading,
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("${(progress * 100).toInt()}%", color = muted,
                    style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = good,
                trackColor = line
            )
            steps.forEachIndexed { index, step ->
                val highlighted = step.key == current?.key
                // Put helper recovery exactly above the helper's checklist row.
                if (step.key == SetupPermissionKey.SHUTDOWN_HELPER &&
                    helperAttemptedBlocked && !step.done) {
                if (helperAttemptedBlocked && steps.any {
                        it.key == SetupPermissionKey.SHUTDOWN_HELPER && !it.done
                    }) {
                    Surface(color = Color(0xFF3C3028), shape = RoundedCornerShape(10.dp)) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Shut Down Helper needs one more step",
                                color = warning,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Android says this helper is controlled by restricted settings. " +
                                    "Open Daily SIM Caller App info, tap the three-dot menu, " +
                                    "and choose Allow restricted settings if available. " +
                                    "Then come back and turn on Shut Down Helper in Accessibility.",
                                color = heading,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = onBlockedHelper,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = action)
                            ) { Text("Fix helper permissions in App info") }
                        }
                    }
                }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            if (!step.done) selectedKey = step.key.name
                            onNext(step.key)
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (highlighted) selected else row
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (step.done) Color(0xFF245542) else Color(0xFF58432D)
                        ) {
                            Text(
                                if (step.done) "✓" else "!",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = if (step.done) good else warning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(step.title, color = heading, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge)
                            Text(step.instruction, color = muted,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (step.done) {
                            Text("Done", color = good,
                                style = MaterialTheme.typography.labelMedium)
                        } else if (highlighted && following != null) {
                            TextButton(
                                onClick = {
                                    selectedKey = following.key.name
                                    onNext(following.key)
                                }
                            ) {
                                Text("Next", color = heading)
                            }
                        } else {
                            Text("Open ›", color = warning,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (phoneRoleBlocked && steps.any { it.key == SetupPermissionKey.PHONE_APP && !it.done }) {
                Surface(color = Color(0xFF3C3028), shape = RoundedCornerShape(10.dp)) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text("Phone app blocked by Android?", color = warning,
                            fontWeight = FontWeight.Bold)
                        Text(
                            "Android may restrict default Phone app selection for sideloaded apps. " +
                                "Open App info, tap the three-dot menu, and select " +
                                "Allow restricted settings if Android offers it. " +
                                "Then return and tap Phone app again.",
                            color = heading, style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = onFixPhoneRole,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = action)
                        ) { Text("Open Daily SIM Caller App info") }
                    }
                }
            }
            if (current != null) {
                Button(
                    onClick = { onNext(current.key) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = action)
                ) { Text(current.action) }
                if (earlierMissing) {
                    Text("Earlier steps still need approval. They have not been skipped.",
                        color = warning, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { selectedKey = null }) {
                        Text("Back to first unfinished step", color = heading)
                    }
                }
            } else {
                Text("✓ All required setup checks are approved", color = good,
                    fontWeight = FontWeight.Bold)
                Button(
                    onClick = onRestartPhone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = action)
                ) { Text("Finish setup — Restart phone") }
                Text(
                    "Your phone needs one restart to finish setup. " +
                        "The app will request Restart when Android allows it.",
                    color = muted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (restartStatus.isNotBlank()) {
                    Text(
                        restartStatus,
                        color = warning,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    selectedKey = null
                    onCheckAgain()
                }) { Text("Check again", color = heading) }
                TextButton(onClick = {
                    selectedKey = null
                    onHelp()
                }) { Text("Help Me Set Up", color = heading) }
            }
            if (current?.key == SetupPermissionKey.SHUTDOWN_HELPER &&
                !helperAttemptedBlocked) {
                TextButton(onClick = onBlockedHelper) {
                    Text("Helper blocked? Open App info", color = warning)
                }
            }
            Text("Only Android-approved settings turn green. The Next button never grants a permission.",
                color = muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
