package com.example.dailysimcaller

import android.Manifest
import android.app.KeyguardManager
import android.view.WindowManager
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.IntentFilter
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    // The fleet's phones have no PIN/pattern/password. Never try to bypass a secure lock.
    private fun prepareUnsecuredHomeScreen() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(!keyguard.isKeyguardSecure)
            setTurnScreenOn(!keyguard.isKeyguardSecure)
        } else if (!keyguard.isKeyguardSecure) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    // Dismiss only the normal swipe/keyguard screen; credentials are never circumvented.
    // A successful callback retriggers the pending startup check once the lock is gone.
    private fun dismissNonSecureLockScreen() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardSecure || !keyguard.isKeyguardLocked) return
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                if (!isFinishing && !isDestroyed) bootSignalCount++
            }
        })
    }

    // Read Android's persistent Accessibility approval on startup/resume.
    // The service connection can lag behind an Android reboot.
    private fun isShutdownHelperApproved(): Boolean {
        if (ShutdownPowerHelper.isServiceConnected()) return true
        val target = ComponentName(this, ShutdownAccessibilityService::class.java)
        return try {
            Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty().split(':').mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == target }
        } catch (_: SecurityException) {
            false
        }
    }

    private val prefs by lazy { getSharedPreferences(DailyAlarm.PREFS, MODE_PRIVATE) }
    private var resumeCount by mutableIntStateOf(0)
    private var bootSignalCount by mutableIntStateOf(0)
    private var isVisible = false
    // Kept on Activity lifecycle, not the Compose coroutine scope: recomposition
    // must not accidentally cancel a network retry and report a false failure.
    private var callRetryJob: Job? = null
    private val bootReadyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == StartupMode.ACTION_BOOT_READY) bootSignalCount++
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, bootReadyReceiver,
            IntentFilter(StartupMode.ACTION_BOOT_READY), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(bootReadyReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        resumeCount++
        dismissNonSecureLockScreen()
    }

    override fun onPause() {
        isVisible = false
        // No delayed call may start while the app is hidden. If this was an
        // authorized boot attempt, its one-shot marker is retained for next Home resume.
        callRetryJob?.cancel()
        super.onPause()
    }

    private fun readDriveCsv(uriText: String): List<String> =
        contentResolver.openInputStream(Uri.parse(uriText))?.bufferedReader()?.use {
            PhoneCsv.parse(it.readText())
        } ?: error("Could not open selected Google Drive CSV")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the app display on while this Activity is foreground, not system-wide.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prepareUnsecuredHomeScreen()
        // Disable any 10 AM alarm left by an older installed version.
        // This cleanup never changes the independently opted-in reboot call setting.
        DailyAlarm.clearLegacyReminder(this)
        // One shared HTTPS CSV endpoint is embedded during the build, not entered per phone.
        val remoteCsvUrl = getString(R.string.csv_endpoint).trim()
        val initialDial = intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()

        setContent {
            var numbers by rememberSaveable { mutableStateOf(prefs.getString("numbers", "").orEmpty()) }
            var csvUri by rememberSaveable { mutableStateOf(prefs.getString("csv_uri", "").orEmpty()) }
            var seconds by rememberSaveable { mutableStateOf(prefs.getInt("call_seconds", 45).toString()) }
            var pause by rememberSaveable { mutableStateOf(prefs.getInt("pause_seconds", 10).toString()) }
            var manualDial by rememberSaveable { mutableStateOf(initialDial) }
            var status by remember { mutableStateOf("Ready") }
            var lastRefresh by remember { mutableStateOf(prefs.getString("last_csv_refresh", "Not refreshed yet").orEmpty()) }
            var csvRevision by remember { mutableStateOf(prefs.getString("last_csv_revision", "None").orEmpty()) }
            var isSyncing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            var powerOffAfterQueue by remember {
                mutableStateOf(ShutdownPowerHelper.isEnabled(this@MainActivity))
            }
            var backgroundBootPrep by remember { mutableStateOf(BootPreparation.isEnabled(this@MainActivity)) }
            var lastBootPrep by remember { mutableStateOf(prefs.getString(BootPreparation.RESULT, "Not yet run").orEmpty()) }
            var autoOnBoot by remember { mutableStateOf(StartupMode.enabled(this@MainActivity)) }
            var retryingCalls by remember { mutableStateOf(false) }
            var retryingBoot by remember { mutableStateOf(false) }
            // A one-time guided flow automatically OPENS each Android permission/role screen.
            // It never grants a permission, role, or accessibility access itself.
            var guidedSetupActive by remember { mutableStateOf(false) }
            var guidedAwaiting by remember { mutableStateOf("") }
            var guidedSettingsOpenedAtResume by remember { mutableIntStateOf(-1) }
            var guidedPulse by remember { mutableIntStateOf(0) }
            // A dimmed shortcut says it was opened; only Android-verified roles get a checkmark.
            var fixPermissionsOpened by rememberSaveable { mutableStateOf(false) }
            var phoneSettingsOpened by rememberSaveable { mutableStateOf(false) }
            var homeSettingsOpened by rememberSaveable { mutableStateOf(false) }
            var accessibilitySettingsOpened by rememberSaveable { mutableStateOf(false) }
            var showDetails by rememberSaveable { mutableStateOf(false) }
            // Extra operational controls are collapsed on a fresh screen.
            var showAdminOnly by rememberSaveable { mutableStateOf(false) }
            // A user-driven guide for Android's restricted Accessibility settings.
            // An app may open Settings but cannot reveal the protected overflow menu
            // or approve restricted settings on the user's behalf.
            var restrictedHelperStep by rememberSaveable { mutableIntStateOf(0) }
            // The setup-complete restart dialog is offered only once per guide completion.
            var restartMenuAutoOpened by rememberSaveable { mutableStateOf(false) }

            fun setupLabel(label: String, granted: Boolean = false, opened: Boolean = false): String =
                when {
                    granted -> "$label  \u2713 Done"
                    opened -> "$label - Opened"
                    else -> label
                }

            suspend fun refreshCsv(): List<String>? {
                // A central endpoint takes precedence over the legacy on-device Drive picker.
                if (remoteCsvUrl.isBlank() && csvUri.isBlank()) {
                    return numbers.lines().mapNotNull(PhoneCsv::cleanPhoneNumber).distinct()
                }
                if (isSyncing) {
                    status = "Number-list refresh already running; please try again"
                    return null
                }
                isSyncing = true
                return try {
                    val fetched = if (remoteCsvUrl.isNotBlank()) RemoteCsv.fetch(remoteCsvUrl) else null
                    val refreshed = fetched?.numbers ?: withContext(Dispatchers.IO) { readDriveCsv(csvUri) }
                    require(refreshed.isNotEmpty()) { "No valid numbers in CSV" }
                    numbers = refreshed.joinToString("\n")
                    lastRefresh = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
                        .format(java.util.Date())
                    csvRevision = fetched?.sha256Prefix ?: "Drive document picker"
                    prefs.edit()
                        .putString("numbers", numbers)
                        .putString("last_csv_refresh", lastRefresh)
                        .putString("last_csv_revision", csvRevision)
                        .apply()
                    status = "Loaded ${refreshed.size} current phone numbers"
                    refreshed
                } catch (e: CancellationException) {
                    throw e // Screen changes are not CSV/network errors.
                } catch (e: Exception) {
                    status = "Number-list refresh failed. No calls started: ${e.message ?: "Unable to read file"}"
                    null // Keep last good list visible, but never call it when refresh failed.
                } finally {
                    isSyncing = false
                }
            }

            // Foreground phoneCall service starts only while an activity is visible,
            // never from BOOT_COMPLETED or a background job.
            suspend fun runQueue(fresh: List<String>, callDuration: Int, callPause: Int, boot: Boolean = false): Boolean {
                return try {
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, CallSessionService::class.java)
                    )
                    val started = CallQueue.start(this@MainActivity, fresh, callDuration, callPause)
                    if (!started) stopService(Intent(this@MainActivity, CallSessionService::class.java))
                    status = if (started) {
                        if (boot) "Startup: dialing ${fresh.size} permitted numbers after unlock"
                        else "Starting ${fresh.size} user-requested queued calls"
                    } else "Queue could not start. Check the default phone role."
                    started
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    stopService(Intent(this@MainActivity, CallSessionService::class.java))
                    status = "Unable to start call session: ${e.message ?: "Unknown error"}"
                    false
                }
            }

            fun canRetryCall(boot: Boolean): Boolean {
                if (!isVisible || CallQueue.running) return false
                if (boot && (!StartupMode.isPending(this@MainActivity) ||
                        !StartupMode.enabled(this@MainActivity) ||
                        !StartupMode.isHomeApp(this@MainActivity))) return false
                if (!StartupMode.isUnlocked(this@MainActivity) ||
                    !StartupMode.isDefaultPhoneApp(this@MainActivity) ||
                    checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                    (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return false
                return true
            }

            fun bootReport(message: String) {
                status = message
                StartupMode.recordBootResult(this@MainActivity, message)
            }

            fun startCallsWithRetry(boot: Boolean, callDuration: Int, callPause: Int) {
                // A second tap, a Home resume, and a boot broadcast cannot create
                // duplicate downloads or calls while one authorized run is pending.
                if (callRetryJob?.isActive == true || CallQueue.running) return
                // A deliberate manual run supersedes any pending post-boot run.
                if (!boot) StartupMode.clearPending(this@MainActivity)
                retryingCalls = true
                retryingBoot = boot
                val newJob = lifecycleScope.launch {
                    val thisJob = coroutineContext[Job]
                    try {
                        val fresh = CallStartRetry.waitForFreshNumbers(
                            shouldContinue = { canRetryCall(boot) },
                            fetch = {
                                // Wait for an ordinary screen refresh to finish; never
                                // place a call from its cached result.
                                while (isSyncing) delay(250L)
                                isSyncing = true
                                try {
                                    if (remoteCsvUrl.isNotBlank()) {
                                        val snapshot = RemoteCsv.fetch(remoteCsvUrl)
                                        numbers = snapshot.numbers.joinToString("\n")
                                        csvRevision = snapshot.sha256Prefix
                                        snapshot.numbers
                                    } else if (csvUri.isNotBlank()) {
                                        val fromDrive = withContext(Dispatchers.IO) { readDriveCsv(csvUri) }
                                        numbers = fromDrive.joinToString("\n")
                                        csvRevision = "Drive document picker"
                                        fromDrive
                                    } else numbers.lines().mapNotNull(PhoneCsv::cleanPhoneNumber).distinct()
                                } finally { isSyncing = false }
                            },
                            onStatus = { message ->
                                if (boot) bootReport("Startup: $message") else status = message
                            }
                        )
                        if (fresh != null && canRetryCall(boot)) {
                            val now = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
                                .format(java.util.Date())
                            lastRefresh = now
                            prefs.edit().putString("numbers", fresh.joinToString("\n"))
                                .putString("last_csv_refresh", now)
                                .putString("last_csv_revision", csvRevision).apply()
                            // The boot flag stays pending through connection failures.
                            // Consume it only after a real call run starts (or abort on
                            // a non-network failure), never on a refresh attempt.
                            val started = runQueue(fresh, callDuration, callPause, boot)
                            if (boot) {
                                StartupMode.clearPending(this@MainActivity)
                                StartupMode.recordBootResult(this@MainActivity, status)
                            }
                            if (!started) status = "Could not start calls. Check the Phone app setting."
                        } else if (boot && canRetryCall(boot)) {
                            // Permanent CSV errors should not cause surprise retries.
                            StartupMode.clearPending(this@MainActivity)
                        }
                    } catch (_: CancellationException) {
                        // Leaving the screen cancels waiting. The boot marker stays
                        // available for the next visible, unlocked Home resume.
                    } finally {
                        // A canceled old run cannot reset a newer run started on resume.
                        if (callRetryJob === thisJob) {
                            retryingCalls = false
                            retryingBoot = false
                            callRetryJob = null
                        }
                    }
                }
                callRetryJob = newJob
            }

            // BOOT_COMPLETED only arms a one-shot flag. The user-selected Home activity
            // must be visible/unlocked and retain the actual Phone/Home roles to place calls.
            // The optional shutdown Accessibility service is not a calling permission.
            LaunchedEffect(csvUri, resumeCount, bootSignalCount, autoOnBoot) {
                if (resumeCount <= 0 || !isVisible) return@LaunchedEffect
                lastBootPrep = prefs.getString(BootPreparation.RESULT, "Not yet run").orEmpty()
                if (autoOnBoot && StartupMode.isPending(this@MainActivity)) {
                    when {
                        guidedSetupActive ->
                            bootReport("Startup paused: finish the on-screen guided setup")
                        !prefs.getBoolean("guided_setup_started_v136", false) ->
                            bootReport("Startup paused: open the app and complete initial setup first")
                        !StartupMode.isHomeApp(this@MainActivity) ->
                            bootReport("Startup waiting: set Daily SIM Caller as default Home app")
                        !StartupMode.isUnlocked(this@MainActivity) ->
                            bootReport("Startup waiting for phone unlock")
                        !StartupMode.isDefaultPhoneApp(this@MainActivity) ->
                            bootReport("Startup waiting: set Daily SIM Caller as default Phone app")
                        checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ->
                            bootReport("Startup waiting: grant Phone permission")
                        Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                            bootReport("Startup waiting: grant Notifications permission")
                        CallQueue.running -> {
                            StartupMode.clearPending(this@MainActivity)
                            bootReport("Calls already running; duplicate startup skipped")
                        }
                        remoteCsvUrl.isBlank() && csvUri.isBlank() -> {
                            StartupMode.clearPending(this@MainActivity)
                            bootReport("Startup skipped: no authorized CSV source configured")
                        }
                        else -> {
                            val duration = prefs.getInt("call_seconds", 45)
                            val pauseTime = prefs.getInt("pause_seconds", 10)
                            if (duration !in 1..3600 || pauseTime !in 0..3600) {
                                StartupMode.clearPending(this@MainActivity)
                                bootReport("Startup skipped: invalid call duration or pause")
                            } else {
                                startCallsWithRetry(true, duration, pauseTime)
                            }
                        }
                    }
                } else if (!retryingCalls && (remoteCsvUrl.isNotBlank() || csvUri.isNotBlank())) {
                    refreshCsv()
                }
            }

            val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null && remoteCsvUrl.isBlank()) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        csvUri = uri.toString()
                        prefs.edit().putString("csv_uri", csvUri).apply()
                        scope.launch { refreshCsv() }
                    } catch (e: Exception) {
                        status = "Could not keep access to file: ${e.message ?: "Unknown error"}"
                    }
                }
            }

            // Android can decline a role dialog before showing its chooser. On that result,
            // open the supported Settings screen once instead of repeating the denied dialog.
            // Settings cannot grant a role automatically: the owner must select our app.
            fun openDefaultAppSettings(home: Boolean, guided: Boolean): Boolean {
                val destinations = if (home) listOf(
                    Intent(Settings.ACTION_HOME_SETTINGS),
                    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                ) else listOf(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                for (destination in destinations + fallback) {
                    try {
                        if (guided) {
                            guidedAwaiting = if (home) "homeSettings" else "phoneSettings"
                            guidedSettingsOpenedAtResume = resumeCount
                        }
                        startActivity(destination)
                        status = if (home)
                            "In Android Settings, choose Home app, then Daily SIM Caller. Return to continue setup."
                        else "In Android Settings > Default apps, choose Phone app, then Daily SIM Caller. Return to continue setup."
                        return true
                    } catch (_: ActivityNotFoundException) {
                        // Some Android vendor builds omit individual settings screens.
                    } catch (_: SecurityException) {
                        // Fall back to another public Settings screen, not a hidden intent.
                    }
                }
                guidedAwaiting = ""
                guidedSetupActive = false
                status = "Android could not open Default apps settings. Open Settings > Apps > Default apps manually."
                return false
            }

            // A blocked/default-Phone role isn't a runtime permission. When Android
            // denies the role, take the owner to THIS app's details instead of looping
            // through a rejected role request. We cannot change restricted settings.
            fun openPhoneAppInfo(guided: Boolean): Boolean {
                return try {
                    if (guided) {
                        guidedAwaiting = "phoneAppInfo"
                        guidedSettingsOpenedAtResume = resumeCount
                    }
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                    status = if (guided) "In App info tap the three dots, choose Allow restricted settings if offered, then return to setup."
                        else "In App info tap the three dots, choose Allow restricted settings if shown, then return and tap Shut Down Helper. Android must approve it."
                    true
                } catch (_: ActivityNotFoundException) {
                    openDefaultAppSettings(home = false, guided = guided)
                } catch (_: SecurityException) {
                    openDefaultAppSettings(home = false, guided = guided)
                }
            }

            val homeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val granted = StartupMode.isHomeApp(this@MainActivity)
                if (granted) {
                    prefs.edit().remove("home_role_denied_v135").apply()
                    status = "Default Home role granted. Auto-calling can run after the NEXT reboot and unlock."
                    if (guidedAwaiting == "home") {
                        guidedAwaiting = ""
                        guidedPulse++
                    }
                } else {
                    prefs.edit().putBoolean("home_role_denied_v135", true).apply()
                    // Do not repeat a rejected system prompt; use Home selection settings.
                    openDefaultAppSettings(home = true, guided = guidedAwaiting == "home")
                }
            }
            val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val granted = StartupMode.isDefaultPhoneApp(this@MainActivity)
                if (granted) {
                    prefs.edit().remove("phone_role_denied_v135").apply()
                    status = "Default Phone app role granted."
                    if (guidedAwaiting == "phone") {
                        guidedAwaiting = ""
                        guidedPulse++
                    }
                } else {
                    prefs.edit().putBoolean("phone_role_denied_v135", true).apply()
                    // Do not repeat the blocked prompt. Show this app's details so
                    // the user can investigate device restrictions, if any.
                    openPhoneAppInfo(guided = guidedAwaiting == "phone")
                }
            }
            val permissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                val allGranted = checkSelfPermission(Manifest.permission.CALL_PHONE) ==
                    PackageManager.PERMISSION_GRANTED &&
                    (Build.VERSION.SDK_INT < 33 || checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                status = if (allGranted) "Phone and notification permissions granted." else
                    "Some permissions were declined. You can resume setup from the guide button."
                // Refresh checklist even when a system permission sheet does not pause Activity.
                guidedPulse++
                if (guidedAwaiting == "permissions") {
                    guidedAwaiting = ""
                    if (!allGranted) guidedSetupActive = false
                }
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

            fun requestHomeRole() {
                if (StartupMode.isHomeApp(this@MainActivity)) {
                    prefs.edit().remove("home_role_denied_v135").apply()
                    status = "Already the default Home app"
                    return
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    val manager = getSystemService(RoleManager::class.java)
                    if (prefs.getBoolean("home_role_denied_v135", false) ||
                        !manager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                        openDefaultAppSettings(home = true, guided = false)
                        return
                    }
                    homeLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    status = "Choose Daily SIM Caller as the default Home app"
                    return
                }
                openDefaultAppSettings(home = true, guided = false)
            }

            fun requestDialerRole(): Boolean {
                if (StartupMode.isDefaultPhoneApp(this@MainActivity)) {
                    prefs.edit().remove("phone_role_denied_v135").apply()
                    return true
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    val manager = getSystemService(RoleManager::class.java)
                    if (!prefs.getBoolean("phone_role_denied_v135", false) &&
                        manager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                        roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                        status = "Choose Daily SIM Caller as the default Phone app"
                        return false
                    }
                }
                if (prefs.getBoolean("phone_role_denied_v135", false)) {
                    openPhoneAppInfo(guided = false)
                } else {
                    openDefaultAppSettings(home = false, guided = false)
                }
                return false
            }

            fun retryPhoneRole() {
                // Explicit user action only; never retry a rejected role in a loop.
                prefs.edit().remove("phone_role_denied_v135").apply()
                if (StartupMode.isDefaultPhoneApp(this@MainActivity)) {
                    status = "Daily SIM Caller is already the default Phone app"
                } else if (Build.VERSION.SDK_INT >= 29) {
                    val manager = getSystemService(RoleManager::class.java)
                    if (manager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                        guidedSetupActive = true
                        guidedAwaiting = "phone"
                        roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                        status = "Confirm the Phone app role in Android's system prompt"
                    } else {
                        status = "Android reports that the Phone role is unavailable on this device"
                        openDefaultAppSettings(home = false, guided = false)
                    }
                } else {
                    openDefaultAppSettings(home = false, guided = false)
                }
            }

            fun beginGuidedSetup() {
                guidedAwaiting = ""
                guidedSetupActive = true
                guidedPulse++
                status = "Guided setup started. Android will ask you to approve required access."
            }

            // First opening of v1.3.6 begins setup without searching through Settings.
            // Rejected permissions are never requested in a loop. The owner explicitly retries.
            LaunchedEffect(Unit) {
                if (BootPreparation.isEnabled(this@MainActivity)) {
                    BootPreparation.schedule(this@MainActivity)
                    lastBootPrep = prefs.getString(BootPreparation.RESULT, "Not yet run").orEmpty()
                }
                if (!prefs.getBoolean("guided_setup_started_v136", false)) {
                    // Installation/update is never itself a trigger to call a number.
                    StartupMode.clearPending(this@MainActivity)
                    prefs.edit().putBoolean("guided_setup_started_v136", true)
                        .putBoolean("guided_setup_complete_v136", false).apply()
                    beginGuidedSetup()
                }
            }

            // Only run one prompt at a time, and automatically advance after acceptance.
            LaunchedEffect(guidedSetupActive, guidedPulse, resumeCount) {
                if (!guidedSetupActive || guidedAwaiting.isNotEmpty() || resumeCount == 0) return@LaunchedEffect
                val missing = mutableListOf<String>()
                if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                    missing.add(Manifest.permission.CALL_PHONE)
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    missing.add(Manifest.permission.POST_NOTIFICATIONS)
                if (missing.isNotEmpty()) {
                    guidedAwaiting = "permissions"
                    status = "Android is requesting Phone and Notifications permissions. Tap Allow to continue."
                    permissionsLauncher.launch(missing.toTypedArray())
                    return@LaunchedEffect
                }
                if (Build.VERSION.SDK_INT < 29) {
                    guidedSetupActive = false
                    status = "This guided Phone role setup requires Android 10 or later."
                    return@LaunchedEffect
                }
                if (autoOnBoot && !StartupMode.isHomeApp(this@MainActivity)) {
                    val role = getSystemService(RoleManager::class.java)
                    if (prefs.getBoolean("home_role_denied_v135", false) ||
                        !role.isRoleAvailable(RoleManager.ROLE_HOME)) {
                        openDefaultAppSettings(home = true, guided = true)
                    } else {
                        guidedAwaiting = "home"
                        status = "Choose Daily SIM Caller as the Home app for calls after reboot and unlock."
                        homeLauncher.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    }
                    return@LaunchedEffect
                }
                if (autoOnBoot) prefs.edit().remove("home_role_denied_v135").apply()
                if (ShutdownPowerHelper.isEnabled(this@MainActivity) &&
                    !ShutdownPowerHelper.isServiceConnected()) {
                    guidedAwaiting = "accessibility"
                    guidedSettingsOpenedAtResume = resumeCount
                    status = "Enable Daily SIM Caller shutdown helper in Accessibility. Android requires your approval."
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    return@LaunchedEffect
                }
                if (!StartupMode.isDefaultPhoneApp(this@MainActivity)) {
                    val role = getSystemService(RoleManager::class.java)
                    if (prefs.getBoolean("phone_role_denied_v135", false)) {
                        openPhoneAppInfo(guided = true)
                    } else if (!role.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                        guidedSetupActive = false
                        status = "Android does not offer the default Phone role here. Calls will stay blocked."
                    } else {
                        guidedAwaiting = "phone"
                        status = "Choose Daily SIM Caller as the default Phone app in Android's prompt."
                        roleLauncher.launch(role.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                    }
                    return@LaunchedEffect
                }
                prefs.edit().remove("phone_role_denied_v135").apply()
                prefs.edit().putBoolean("guided_setup_complete_v136", true).apply()
                guidedSetupActive = false
                restrictedHelperStep = 4
                restartMenuAutoOpened = false
                status = "Setup complete. The Android power menu will open so you can choose Restart. No calls started."
            }

            // Android Settings (Accessibility and Phone/Home fallback) has no simple
            // permission-result callback. Check only ON RETURN; never reopen a denied screen.
            LaunchedEffect(resumeCount, guidedSetupActive) {
                if (!guidedSetupActive || guidedSettingsOpenedAtResume < 0 ||
                    resumeCount <= guidedSettingsOpenedAtResume) return@LaunchedEffect
                val awaited = guidedAwaiting
                if (awaited !in listOf("accessibility", "homeSettings", "phoneSettings", "phoneAppInfo")) return@LaunchedEffect
                delay(750) // Let Android's Accessibility service bind after approval.
                val granted = when (awaited) {
                    "accessibility" -> ShutdownPowerHelper.isServiceConnected()
                    "homeSettings" -> StartupMode.isHomeApp(this@MainActivity)
                    "phoneSettings", "phoneAppInfo" -> StartupMode.isDefaultPhoneApp(this@MainActivity)
                    else -> false
                }
                guidedAwaiting = ""
                guidedSettingsOpenedAtResume = -1
                if (granted) guidedPulse++ else {
                    guidedSetupActive = false
                    status = when (awaited) {
                        "accessibility" -> "Accessibility not connected. If Android blocks the trusted helper, use Open App Info > Allow restricted settings, then tap Guided Setup again."
                        "homeSettings" -> "Home app not selected yet. Tap Guided Setup to reopen Home selection settings."
                        "phoneSettings" -> "Phone app not selected yet. Tap Retry Phone role or open Default Phone apps."
                        "phoneAppInfo" -> "Android still blocks the Phone role. If App info offered 'Allow restricted settings', approve it, then tap Retry Phone role. Otherwise the phone or its policy may prohibit this role."
                        else -> "Return to guided setup to review permission status."
                    }
                }
            }

            // Once all required setup checks pass, offer the Android restart menu.
            // Only the user can choose Restart; this never performs automatic shutdown.
            LaunchedEffect(restrictedHelperStep) {
                if (restrictedHelperStep == 4 && !restartMenuAutoOpened) {
                    restartMenuAutoOpened = true
                    delay(450) // Let the completed step render before showing the system dialog.
                    val opened = ShutdownPowerHelper.showRestartMenu()
                    status = if (opened) {
                        "Setup complete. Tap Restart in Android's power menu when ready."
                    } else {
                        "Setup complete. Tap Open Power Menu below, or hold the phone's power button to restart."
                    }
                }
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
            // Allow Call Now to begin a network retry even on a fresh install
            // where the first CSV download failed and no cached numbers exist.
            val hasCallSource = remoteCsvUrl.isNotBlank() || csvUri.isNotBlank() || list.isNotEmpty()
            val valid = hasCallSource && duration != null && duration in 1..3600 &&
                    gap != null && gap in 0..3600

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFE74B56),
                onPrimary = Color.White,
                background = Color(0xFF111214),
                surface = Color(0xFF1B1C20),
                surfaceVariant = Color(0xFF303239),
                onSurface = Color(0xFFF5F5F5),
                onSurfaceVariant = Color(0xFFBEBFC5)
            )) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Daily SIM Caller", style = MaterialTheme.typography.headlineMedium)
                    Text("Call status", style = MaterialTheme.typography.titleLarge)
                    Text("Queue: ${CallQueue.status}")
                    ConnectedCallTimer()
                    Text("Completed attempts: ${CallQueue.completed}")
                    Text(
                        when {
                            status.contains("Unable to resolve host") || status.contains("number-list refresh failed", true) ->
                                "Waiting for Google Sheets. Calls will start after a fresh list loads."
                            status.contains("coroutine scope left", ignoreCase = true) ->
                                "Checking connection. Open More Details if this keeps happening."
                            else -> status
                        },
                        maxLines = 3,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        enabled = SpeakerphoneControl.callPresent,
                        onClick = { SpeakerphoneControl.toggle() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (SpeakerphoneControl.speakerOn)
                            "Speaker ON - Turn OFF" else "Speaker OFF - Turn ON")
                    }
                    if (SpeakerphoneControl.callPresent && SpeakerphoneControl.message.isNotBlank()) {
                        Text(SpeakerphoneControl.message)
                    }
                    HorizontalDivider()
                    Text("Setup", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Tap a row to open its settings. Green means Done. Orange means check it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Re-read Android roles each time the app returns from Settings.
                    val phoneReady = remember(resumeCount, guidedPulse) {
                        StartupMode.isDefaultPhoneApp(this@MainActivity)
                    }
                    val homeReady = remember(resumeCount, guidedPulse) {
                        StartupMode.isHomeApp(this@MainActivity)
                    }
                    val powerAccessReady = remember(resumeCount, guidedPulse) {
                        isShutdownHelperApproved()
                    }
                    val phonePermissionReady = remember(resumeCount, guidedPulse) {
                        checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                    }
                    val notificationsReady = remember(resumeCount, guidedPulse) {
                        Build.VERSION.SDK_INT < 33 || checkSelfPermission(
                            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    }
                    val batteryReady = remember(resumeCount, guidedPulse) {
                        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
                    }
                    OutlinedButton(
                        onClick = {
                            if (isShutdownHelperApproved()) {
                                restrictedHelperStep = 0
                                beginGuidedSetup()
                            } else {
                                // Keep the role/permission flow idle while the user
                                // performs the restricted-settings handoff manually.
                                guidedSetupActive = false
                                guidedAwaiting = ""
                                guidedSettingsOpenedAtResume = -1
                                restrictedHelperStep = 1
                                status = "Follow the setup steps below. Android will ask you to approve access."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Help Me Set Up")
                    }
                    if (restrictedHelperStep != 0) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (restrictedHelperStep) {
                                    1 -> {
                                        Text("Step 1 of 4: Try Shut Down Helper", style = MaterialTheme.typography.titleMedium)
                                        Text("Tap Open Accessibility. Find Daily SIM Caller > Shut Down Helper and try to turn it on. If Android says access is restricted, tap Close, return here, and tap Next.")
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                try {
                                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                                } catch (_: ActivityNotFoundException) {
                                                    status = "Open Settings > Accessibility manually."
                                                } catch (_: SecurityException) {
                                                    status = "Android blocked this shortcut. Open Settings > Accessibility manually."
                                                }
                                            }
                                        ) { Text("Open Accessibility") }
                                        Button(
                                            onClick = {
                                                if (isShutdownHelperApproved()) {
                                                    restrictedHelperStep = 0
                                                    beginGuidedSetup()
                                                } else restrictedHelperStep = 2
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Next") }
                                    }
                                    2 -> {
                                        Text("Step 2 of 4: Fix Permissions", style = MaterialTheme.typography.titleMedium)
                                        Text("Open App Info. Tap the three dots, then Allow restricted settings, if shown. You must approve it yourself. If there are no three dots, return to Step 1 and try the helper first.")
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { openPhoneAppInfo(guided = false) }
                                        ) { Text("Open App Info") }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { restrictedHelperStep = 1 }, modifier = Modifier.weight(1f)) {
                                                Text("Back")
                                            }
                                            Button(onClick = { restrictedHelperStep = 3 }, modifier = Modifier.weight(1f)) {
                                                Text("Next")
                                            }
                                        }
                                    }
                                    3 -> {
                                        Text("Step 3 of 4: Turn On Helper", style = MaterialTheme.typography.titleMedium)
                                        Text("Open Accessibility again and turn on Shut Down Helper. Return here to check if Android enabled it.")
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                try {
                                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                                } catch (_: ActivityNotFoundException) {
                                                    status = "Open Settings > Accessibility manually."
                                                } catch (_: SecurityException) {
                                                    status = "Android blocked this shortcut. Open Settings > Accessibility manually."
                                                }
                                            }
                                        ) { Text("Open Accessibility") }
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                if (isShutdownHelperApproved()) {
                                                    restrictedHelperStep = 0
                                                    beginGuidedSetup()
                                                } else {
                                                    status = "Shut Down Helper is not enabled yet. Use Fix Permissions if Android still blocks it."
                                                }
                                            }
                                        ) { Text("Check Helper Next") }
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { restrictedHelperStep = 2 }
                                        ) { Text("Back") }
                                    }
                                    4 -> {
                                        Text("Step 4 of 4: Restart Phone", style = MaterialTheme.typography.titleMedium)
                                        Text("Setup is complete. The Android power menu lets you choose Restart. The app will not restart or shut down the phone for you.")
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFC62828),
                                                contentColor = Color.White
                                            ),
                                            onClick = {
                                                val opened = ShutdownPowerHelper.showRestartMenu()
                                                status = if (opened)
                                                    "Tap Restart in Android's power menu when ready."
                                                else "Power menu unavailable. Hold the phone's side power button to restart."
                                            }
                                        ) { Text("Open Power Menu") }
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                restrictedHelperStep = 0
                                                status = "Setup complete. You can restart later using Power Menu."
                                            }
                                        ) { Text("Finish Without Restarting") }
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        restrictedHelperStep = 0
                                        status = "You can return to setup at any time."
                                    }
                                ) { Text("Close Guide") }
                            }
                        }
                    }
                    // A tap-friendly checklist like a settings app. Checked states are read from
                    // Android, not inferred from merely visiting the settings screen.
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column {
                            Text("Make Calls", style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
                                color = MaterialTheme.colorScheme.primary)
                            SetupPermissionRow("Phone Permission", "Allow phone calls", phonePermissionReady) {
                                if (!phonePermissionReady) permissionsLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                                else status = "Phone permission is ready"
                            }
                            SetupPermissionRow("Notifications", "Allow call status notifications", notificationsReady) {
                                if (Build.VERSION.SDK_INT >= 33 && !notificationsReady)
                                    permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                else status = "Notifications are ready"
                            }
                            SetupPermissionRow("Set Phone App", "Choose Daily SIM Caller as Phone app",
                                phoneReady, phoneSettingsOpened) {
                                phoneSettingsOpened = true
                                requestDialerRole()
                            }
                            Text("After Restart / Shut Down", style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 6.dp),
                                color = MaterialTheme.colorScheme.primary)
                            SetupPermissionRow("Set Home App", "Needed for calls after a restart",
                                homeReady, homeSettingsOpened) {
                                homeSettingsOpened = true
                                requestHomeRole()
                            }
                            SetupPermissionRow("Battery Settings", "Optional: allow background activity",
                                batteryReady) {
                                try {
                                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                } catch (_: Exception) {
                                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:$packageName")
                                    })
                                }
                            }
                            SetupPermissionRow("Fix Permissions", "Open App Info if access is restricted",
                                null, fixPermissionsOpened) {
                                fixPermissionsOpened = true
                                openPhoneAppInfo(guided = false)
                            }
                            SetupPermissionRow("Shut Down Helper", "Accessibility access for power off",
                                powerAccessReady, accessibilitySettingsOpened) {
                                accessibilitySettingsOpened = true
                                try {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                } catch (_: Exception) {
                                    status = "Open Android Settings > Accessibility manually."
                                }
                            }
                        }
                    }
                    // This settings shortcut remains a test: opening the power menu does NOT shut down.
                    Button(
                        onClick = { ShutdownPowerHelper.testPowerMenu() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC62828),
                            contentColor = Color.White
                        )
                    ) { Text("Power Menu") }
                    Button(enabled = valid && !CallQueue.running && !retryingCalls,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (!requestDialerRole() || !requestNecessaryPermissions()) return@Button
                            persistSettings()
                            startCallsWithRetry(false, duration!!, gap!!)
                        }
                    ) { Text("Call Now") }
                    if (retryingCalls) {
                        OutlinedButton(onClick = {
                            callRetryJob?.cancel()
                            if (retryingBoot) StartupMode.clearPending(this@MainActivity)
                            status = "Stopped trying. No new calls started."
                        }, modifier = Modifier.fillMaxWidth()) { Text("Stop Trying") }
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("For Admin Only", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium)
                        Switch(checked = showAdminOnly, onCheckedChange = { showAdminOnly = it })
                    }
                    if (showAdminOnly) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Admin-only operation: this still stops a running queue immediately.
                    Button(
                        enabled = CallQueue.running,
                        modifier = Modifier.fillMaxWidth(), onClick = {
                            CallQueue.stop()
                            status = "Calling stopped"
                        }
                    ) { Text("Stop Current Queue") }

                        HorizontalDivider()
                        Text("Power off after last queued call (experimental)",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Auto power off needs Shut Down Helper. The phone may ask you to confirm.")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Automatically power off when queue finishes", modifier = Modifier.weight(1f))
                            Switch(checked = powerOffAfterQueue, onCheckedChange = { enabled ->
                                powerOffAfterQueue = enabled
                                ShutdownPowerHelper.setEnabled(this@MainActivity, enabled)
                            })
                        }
                        Text("A powered-off phone cannot call again until it is turned on.")
                        HorizontalDivider()
                        Text("Calls After Restart", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Call after restart",
                                modifier = Modifier.weight(1f))
                            Switch(checked = autoOnBoot, onCheckedChange = { checked ->
                                autoOnBoot = checked
                                StartupMode.setEnabled(this@MainActivity, checked)
                                if (checked) {
                                    status = "Auto-calling opted in for future reboots. Android will guide Home and Phone roles. No calls start yet."
                                    beginGuidedSetup()
                                } else {
                                    callRetryJob?.cancel()
                                    StartupMode.clearPending(this@MainActivity)
                                    status = "Automatic calls disabled for future reboots"
                                }
                            })
                        }
                        if (autoOnBoot) {
                            Text("Set Home App and unlock your phone after restart. This replaces your usual Home screen.")
                            OutlinedButton(onClick = { requestHomeRole() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Choose Home App")
                            }
                            OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
                                modifier = Modifier.fillMaxWidth()) {
                                Text("Restore Old Home Screen")
                            }
                        }
                        Button(onClick = {
                            callRetryJob?.cancel()
                            StartupMode.clearPending(this@MainActivity)
                            if (CallQueue.running) CallQueue.stop()
                            status = "Pending startup sequence canceled and active queue stopped"
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Stop Restart Calls")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Get numbers ready after restart", modifier = Modifier.weight(1f))
                            Switch(checked = backgroundBootPrep, onCheckedChange = { checked ->
                                backgroundBootPrep = checked
                                BootPreparation.setEnabled(this@MainActivity, checked)
                                status = if (checked) "Background preparation enabled; Android may refresh the list after reboot."
                                    else "Background preparation disabled; auto-calling can still refresh once Home opens."
                            })
                        }
                        if (showDetails) Text("Last number-list preparation: $lastBootPrep")
                        OutlinedButton(onClick = {
                            lastBootPrep = prefs.getString(BootPreparation.RESULT, "Not yet run").orEmpty()
                            status = "Background preparation status checked. The Android job never places calls."
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Check Restart Preparation")
                        }
                        Text("Calls start with Call Now, or after restart when you turn that on.")
                        TextButton(onClick = { showDetails = !showDetails }) {
                            Text(if (showDetails) "Hide Details" else "More Details")
                        }
                        if (showDetails) {
                            Text("Restart readiness: " + StartupMode.readiness(this@MainActivity,
                                remoteCsvUrl.isNotBlank() || csvUri.isNotBlank()))
                            Text("Last restart: " + StartupMode.lastBootReport(this@MainActivity))
                            Text("Power access: " + ShutdownPowerHelper.connectionStatus(this@MainActivity))
                            Text(ShutdownPowerHelper.message)
                            Text("Android asks you to approve permissions. The app cannot approve them for you.")
                        }

                        if (remoteCsvUrl.isNotBlank()) {
                            Text("Phone list: Connected")
                            if (showDetails) {
                                Text("Last refresh: $lastRefresh")
                                Text("List fingerprint: $csvRevision")
                            }
                        } else {
                            Text("Choose a phone-number list")
                            Button(onClick = { csvPicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth()) {
                                Text("Connect Google Drive CSV")
                            }
                            if (csvUri.isNotBlank()) {
                                Text("Phone list selected")
                                if (showDetails) Text("Last refresh: $lastRefresh")
                                TextButton(onClick = {
                                    csvUri = ""
                                    prefs.edit().remove("csv_uri").apply()
                                    status = "Disconnected CSV; saved numbers are editable"
                                }) { Text("Disconnect CSV") }
                            }
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val refreshed = refreshCsv()
                                if (refreshed != null) status = "Refreshed ${refreshed.size} numbers at $lastRefresh"
                            }
                        }, enabled = (remoteCsvUrl.isNotBlank() || csvUri.isNotBlank()) && !isSyncing,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (isSyncing) "Refreshing..." else "Refresh Numbers")
                        }
                        OutlinedTextField(
                            value = numbers,
                            onValueChange = { numbers = it },
                            readOnly = remoteCsvUrl.isNotBlank() || csvUri.isNotBlank(),
                            label = { Text("Phone numbers (one per line)") },
                            supportingText = { Text("${list.size} valid unique numbers. CSV updates replace this list.") },
                            minLines = 3, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = seconds,
                            onValueChange = { seconds = it },
                            label = { Text("Call length (seconds)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pause,
                            onValueChange = { pause = it },
                            label = { Text("Wait between calls (seconds)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { persistSettings(); status = "Settings saved" },
                            modifier = Modifier.fillMaxWidth()) { Text("Save Settings") }

                        Text(if (phoneReady) "Phone app: Ready" else "Phone app: Not set")
                        OutlinedButton(onClick = { retryPhoneRole() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Try Phone App Again")
                        }
                        if (showDetails) {
                            Text("If Android denies access, use Fix Permissions. Only you can approve it in Android Settings.")
                        }
                        HorizontalDivider()
                        Text("Dial a Number")
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
                            }) { Text("Call This Number") }
                        }
                    }
                }
                }
            }
        }
    }
}

/** Visual setup row; a checkmark is shown only for permission/roles confirmed by Android. */
@Composable
private fun SetupPermissionRow(
    title: String,
    hint: String,
    ready: Boolean?,
    visited: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    ready == true -> "Done"
                    visited -> "$hint · Opened, not yet confirmed"
                    else -> hint
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            when (ready) {
                true -> "✓"
                false -> "!"
                null -> "›"
            },
            color = when (ready) {
                true -> Color(0xFF64D38A)
                false -> Color(0xFFFFA34B)
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.headlineMedium
        )
    }
    HorizontalDivider(color = Color(0xFF35363B))
}
