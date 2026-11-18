package com.example.dailysimcaller

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences(DailyAlarm.PREFS, MODE_PRIVATE) }
    private var resumeCount by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    private fun readDriveCsv(uriText: String): List<String> =
        contentResolver.openInputStream(Uri.parse(uriText))?.bufferedReader()?.use {
            PhoneCsv.parse(it.readText())
        } ?: error("Could not open selected Google Drive CSV")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reminder = intent?.getBooleanExtra("daily_reminder", false) == true
        val initialDial = intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()

        setContent {
            var numbers by rememberSaveable { mutableStateOf(prefs.getString("numbers", "").orEmpty()) }
            var csvUri by rememberSaveable { mutableStateOf(prefs.getString("csv_uri", "").orEmpty()) }
            var seconds by rememberSaveable { mutableStateOf(prefs.getInt("call_seconds", 45).toString()) }
            var pause by rememberSaveable { mutableStateOf(prefs.getInt("pause_seconds", 10).toString()) }
            var manualDial by rememberSaveable { mutableStateOf(initialDial) }
            var status by remember { mutableStateOf(if (reminder) "10 AM reminder: review numbers and tap Start Now" else "Ready") }
            var lastRefresh by remember { mutableStateOf("Not refreshed yet") }
            var isDailyOn by remember { mutableStateOf(prefs.getBoolean("daily_enabled", false)) }

            fun refreshCsv(): List<String>? {
                if (csvUri.isBlank()) return numbers.lines().mapNotNull(PhoneCsv::cleanPhoneNumber).distinct()
                return try {
                    val refreshed = readDriveCsv(csvUri)
                    require(refreshed.isNotEmpty()) { "No valid numbers in CSV" }
                    numbers = refreshed.joinToString("\n")
                    prefs.edit().putString("numbers", numbers).apply()
                    lastRefresh = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
                        .format(java.util.Date())
                    refreshed
                } catch (e: Exception) {
                    status = "CSV refresh failed. No calls started: ${e.message ?: "Unable to read file"}"
                    null // Fail closed; do not dial a stale cached list.
                }
            }

            LaunchedEffect(csvUri, resumeCount) {
                if (csvUri.isNotBlank()) refreshCsv()
            }

            val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        csvUri = uri.toString()
                        prefs.edit().putString("csv_uri", csvUri).apply()
                        val list = refreshCsv()
                        if (list != null) status = "Connected CSV: ${list.size} numbers loaded"
                    } catch (e: Exception) {
                        status = "Could not keep access to file: ${e.message ?: "Unknown error"}"
                    }
                }
            }

            val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                status = if (getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER))
                    "Default phone role granted. Tap Start Now again." else
                    "Default phone role not granted."
            }
            val permissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                status = if (grants.values.all { it }) "Permissions granted; tap Start Now again" else
                    "Phone and notification permissions are needed for the selected features"
            }

            fun requestNecessaryPermissions(): Boolean {
                val needs = mutableListOf<String>()
                if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                    needs.add(Manifest.permission.CALL_PHONE)
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    needs.add(Manifest.permission.POST_NOTIFICATIONS)
                if (needs.isNotEmpty()) {
                    permissionsLauncher.launch(needs.toTypedArray())
                    status = "Grant the requested permissions, then tap again"
                    return false
                }
                return true
            }

            fun requestDialerRole(): Boolean {
                if (Build.VERSION.SDK_INT < 29) {
                    status = "Default phone role requires Android 10 or later"
                    return false
                }
                val manager = getSystemService(RoleManager::class.java)
                if (manager.isRoleHeld(RoleManager.ROLE_DIALER)) return true
                if (!manager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    status = "This device does not offer the default dialer role"
                    return false
                }
                roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                status = "Approve the Default Phone app role, then tap again"
                return false
            }

            fun persistSettings() {
                prefs.edit()
                    .putString("numbers", numbers)
                    .putInt("call_seconds", seconds.toIntOrNull() ?: 45)
                    .putInt("pause_seconds", pause.toIntOrNull() ?: 10)
                    .apply()
            }

            val list = numbers.lines().mapNotNull(PhoneCsv::cleanPhoneNumber).distinct()
            val duration = seconds.toIntOrNull()
            val gap = pause.toIntOrNull()
            val valid = list.isNotEmpty() && duration != null && duration in 1..3600 &&
                    gap != null && gap in 0..3600

            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Daily SIM Caller", style = MaterialTheme.typography.headlineMedium)
                    Text("Daily reminder: 10:00 AM Central (America/Chicago)")
                    Text("Calls start when you tap Call Now. Automatic ending requires the default-phone role and may vary by device.")
                    if (reminder) Text("Daily reminder fired: tap Start Now when ready")

                    Button(onClick = { csvPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Connect Google Drive CSV")
                    }
                    OutlinedButton(onClick = {
                        val refreshed = refreshCsv()
                        if (refreshed != null) status = "Refreshed ${refreshed.size} numbers at $lastRefresh"
                    }, enabled = csvUri.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("Refresh CSV Now")
                    }
                    if (csvUri.isNotBlank()) {
                        Text("CSV linked. Last refresh: $lastRefresh")
                        Text("Drive may return a cached copy; refresh is not guaranteed real-time.")
                        TextButton(onClick = {
                            csvUri = ""
                            prefs.edit().remove("csv_uri").apply()
                            status = "Disconnected CSV; saved numbers are editable"
                        }) { Text("Disconnect CSV") }
                    }
                    OutlinedTextField(
                        value = numbers,
                        onValueChange = { numbers = it },
                        readOnly = csvUri.isNotBlank(),
                        label = { Text("Phone numbers (one per line)") },
                        supportingText = { Text("${list.size} valid unique numbers. CSV updates replace this list.") },
                        minLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it },
                        label = { Text("Auto end each connected call after (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pause,
                        onValueChange = { pause = it },
                        label = { Text("Pause between calls (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { persistSettings(); status = "Settings saved" },
                        modifier = Modifier.fillMaxWidth()) { Text("Save Settings") }

                    OutlinedButton(onClick = { requestDialerRole() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Set as Default Phone App (required for hangups)")
                    }
                    Button(enabled = valid && !CallQueue.running, modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (!requestDialerRole() || !requestNecessaryPermissions()) return@Button
                            if (!DailyAlarm.schedule(this@MainActivity)) {
                                status = "Allow Alarms & reminders, then tap again"
                                DailyAlarm.requestExactAlarmAccess(this@MainActivity)
                                return@Button
                            }
                            isDailyOn = true
                            persistSettings()
                            val fresh = refreshCsv() ?: return@Button
                            if (fresh.isEmpty()) {
                                status = "No phone numbers available"
                                return@Button
                            }
                            try {
                                androidx.core.content.ContextCompat.startForegroundService(
                                    this@MainActivity,
                                    Intent(this@MainActivity, CallSessionService::class.java)
                                )
                                val started = CallQueue.start(this@MainActivity, fresh, duration!!, gap!!)
                                if (!started) {
                                    stopService(Intent(this@MainActivity, CallSessionService::class.java))
                                }
                                status = if (started)
                                    "Starting ${fresh.size} queued calls; daily reminder enabled"
                                else "Queue could not start. Check the default phone role."
                            } catch (e: Exception) {
                                stopService(Intent(this@MainActivity, CallSessionService::class.java))
                                status = "Unable to start call session: ${e.message ?: "Unknown error"}"
                            }
                        }
                    ) { Text("Call Now + Enable Daily 10 AM") }

                    OutlinedButton(enabled = valid, modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                status = "Allow notifications, then tap again"
                            } else if (DailyAlarm.schedule(this@MainActivity)) {
                                isDailyOn = true
                                persistSettings()
                                status = "10 AM Central daily reminder enabled"
                            } else {
                                DailyAlarm.requestExactAlarmAccess(this@MainActivity)
                                status = "Allow Alarms & reminders, then tap Schedule again"
                            }
                        }
                    ) { Text("Enable Daily Reminder Only") }
                    Button(enabled = CallQueue.running, modifier = Modifier.fillMaxWidth(), onClick = {
                        CallQueue.stop()
                        status = "Current queue stopped"
                    }) { Text("Stop Current Queue") }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        DailyAlarm.cancel(this@MainActivity)
                        isDailyOn = false
                        status = "Future daily reminders canceled"
                    }) { Text("Cancel Daily Reminder") }
                    Text("Daily schedule: ${if(isDailyOn) "Enabled" else "Disabled"}")
                    Text("Queue: ${CallQueue.status}")
                    Text(status)
                    HorizontalDivider()
                    Text("Dial pad (needed when this app is the default phone app)")
                    OutlinedTextField(value = manualDial, onValueChange = { manualDial = it },
                        label = { Text("Enter phone number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth())
                    OutlinedButton(modifier = Modifier.fillMaxWidth(),
                        enabled = PhoneCsv.cleanPhoneNumber(manualDial) != null,
                        onClick = {
                            if (!requestDialerRole() || !requestNecessaryPermissions()) return@OutlinedButton
                            val cleaned = PhoneCsv.cleanPhoneNumber(manualDial) ?: return@OutlinedButton
                            try {
                                getSystemService(TelecomManager::class.java).placeCall(
                                    Uri.fromParts("tel", cleaned, null), Bundle()
                                )
                                status = "Manual call requested"
                            } catch (e: Exception) { status = "Dial failed: ${e.message}" }
                        }) { Text("Dial Manually (not in queue)") }
                }
            }
        }
    }
}
