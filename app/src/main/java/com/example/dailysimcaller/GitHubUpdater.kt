package com.example.dailysimcaller

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.provider.Settings
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GitHub-only distribution. A published, signed GitHub Release must include:
 * a DailySIMCaller .apk, its .apk.sha256 sidecar, and a tag matching v<versionCode>.
 * System PackageInstaller still decides whether the phone needs user confirmation.
 */
internal object GitHubUpdater {
    const val STATUS_PREF = "github_update_status"
    private const val PREFS = "github_updater"
    private const val LAST_CHECK = "last_check_millis"
    private const val LAST_ATTEMPT = "last_attempted_version"
    private const val CHECK_INTERVAL = 12L * 60L * 60L * 1000L
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L
    private const val RELEASE_API = "https://api.github.com/repos/marinesdude05/DailySIMCaller/releases/latest"
    private val busy = AtomicBoolean(false)

    internal data class Release(
        val version: Long,
        val apkUrl: String,
        val apkSize: Long,
        val checksumUrl: String
    )

    internal fun parseRelease(response: String): Release? {
        val json = JSONObject(response)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val match = Regex("^v([0-9]+)$").matchEntire(json.optString("tag_name")) ?: return null
        val version = match.groupValues[1].toLongOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        var name = ""
        var url = ""
        var size = 0L
        for (i in 0 until assets.length()) {
            val item = assets.optJSONObject(i) ?: continue
            val candidate = item.optString("name")
            if (candidate.endsWith(".apk") &&
                (candidate.contains("DailySIMCaller", ignoreCase = true) ||
                 candidate == "app-release.apk")) {
                name = candidate
                url = item.optString("browser_download_url")
                size = item.optLong("size")
                break
            }
        }
        if (name.isBlank() || size !in 1..MAX_APK_BYTES) return null
        val checksumName = name + ".sha256"
        var checksumUrl = ""
        for (i in 0 until assets.length()) {
            val item = assets.optJSONObject(i) ?: continue
            if (item.optString("name") == checksumName) {
                checksumUrl = item.optString("browser_download_url")
            }
        }
        if (!isTrustedReleaseAsset(url) || !isTrustedReleaseAsset(checksumUrl)) return null
        return Release(version, url, size, checksumUrl)
    }

    private fun isTrustedReleaseAsset(url: String): Boolean =
        url.startsWith("https://github.com/marinesdude05/DailySIMCaller/releases/download/")

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "DailySIMCaller-Android-Updater")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.instanceFollowRedirects = true
        return connection
    }

    private fun fetchText(url: String): Pair<Int, String> {
        val connection = open(url)
        return try {
            val code = connection.responseCode
            if (code != 200) code to ""
            else code to connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(release: Release, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = open(release.apkUrl)
        try {
            require(connection.responseCode == 200) { "GitHub could not download the APK" }
            var copied = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val chunk = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_APK_BYTES) { "Update APK exceeds safety limit" }
                        output.write(chunk, 0, count)
                        digest.update(chunk, 0, count)
                    }
                }
            }
            require(copied == release.apkSize) { "Incomplete update download" }
        } finally {
            connection.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @Suppress("DEPRECATION")
    private fun matchesInstalledApp(context: Context, file: File, expectedVersion: Long): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        val current = pm.getPackageInfo(context.packageName, flags)
        if (candidate.packageName != context.packageName) return false
        val candidateVersion = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode
                               else candidate.versionCode.toLong()
        if (candidateVersion != expectedVersion) return false
        val currentSignatures = if (Build.VERSION.SDK_INT >= 28)
            current.signingInfo?.apkContentsSigners else current.signatures
        val newSignatures = if (Build.VERSION.SDK_INT >= 28)
            candidate.signingInfo?.apkContentsSigners else candidate.signatures
        if (currentSignatures.isNullOrEmpty() || newSignatures.isNullOrEmpty()) return false
        return currentSignatures.map { it.toCharsString() }.toSet() ==
               newSignatures.map { it.toCharsString() }.toSet()
    }

    private fun submitInstall(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(apk.length())
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callbackIntent = Intent(context, GitHubUpdateResultReceiver::class.java)
                    .setAction("com.example.dailysimcaller.GITHUB_UPDATE_RESULT")
                val callback = PendingIntent.getBroadcast(
                    context, sessionId, callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(callback.intentSender)
            }
        } catch (error: Exception) {
            installer.abandonSession(sessionId)
            throw error
        }
    }

    suspend fun checkAndInstall(context: Context, force: Boolean = false): String =
        withContext(Dispatchers.IO) {
            if (!busy.compareAndSet(false, true)) return@withContext "Update check already running"
            try {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (CallQueue.running) return@withContext "Update postponed until the call queue is finished"
                val now = System.currentTimeMillis()
                if (!force && now - prefs.getLong(LAST_CHECK, 0L) < CHECK_INTERVAL)
                    return@withContext prefs.getString(STATUS_PREF, "Checked recently").orEmpty()

                val (httpStatus, json) = fetchText(RELEASE_API)
                prefs.edit().putLong(LAST_CHECK, now).apply()
                if (httpStatus == 404) {
                    val message = "No GitHub Release published yet"
                    prefs.edit().putString(STATUS_PREF, message).apply()
                    return@withContext message
                }
                require(httpStatus == 200) { "GitHub release check returned HTTP " + httpStatus }
                val release = parseRelease(json)
                    ?: return@withContext "No eligible signed release found (requires vNUMBER tag and APK plus .sha256)"
                val installed = if (Build.VERSION.SDK_INT >= 28)
                    context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
                else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
                }
                if (release.version <= installed) {
                    val message = "Daily SIM Caller is up to date (code " + installed + ")"
                    prefs.edit().putString(STATUS_PREF, message).apply()
                    return@withContext message
                }
                if (prefs.getLong(LAST_ATTEMPT, 0L) == release.version)
                    return@withContext "Version " + release.version + " already submitted to Android installer"

                if (!context.packageManager.canRequestPackageInstalls()) {
                    val message = "Allow Daily SIM Caller to install unknown apps in Android Settings before GitHub updates can install"
                    prefs.edit().putString(STATUS_PREF, message).apply()
                    return@withContext message
                }

                val (checksumStatus, checksumContents) = fetchText(release.checksumUrl)
                require(checksumStatus == 200) { "Missing required SHA-256 checksum" }
                val expected = Regex("^[a-fA-F0-9]{64}").find(checksumContents.trim())?.value
                    ?: error("Invalid SHA-256 checksum")
                val apk = File(context.cacheDir, "github-update-v" + release.version + ".apk")
                try {
                    val actual = download(release, apk)
                    require(expected.equals(actual, ignoreCase = true)) { "Downloaded APK checksum mismatch" }
                    require(matchesInstalledApp(context, apk, release.version)) {
                        "Update package, version, or signing certificate does not match this installation"
                    }
                    if (CallQueue.running) return@withContext "Update postponed; an active call started"
                    submitInstall(context, apk)
                    prefs.edit().putLong(LAST_ATTEMPT, release.version).apply()
                    val message = "Update v" + release.version + " submitted to Android; approval may be required"
                    prefs.edit().putString(STATUS_PREF, message).apply()
                    message
                } finally {
                    apk.delete()
                }
            } catch (error: Exception) {
                val message = "GitHub update not installed: " + (error.message ?: "unknown error")
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(STATUS_PREF, message).apply()
                message
            } finally {
                busy.set(false)
            }
        }
}

/** System installer callback: never treat a pending approval as a successful update. */
internal class GitHubUpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val code = intent?.getIntExtra(
            PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE
        ) ?: PackageInstaller.STATUS_FAILURE
        val prefs = context.getSharedPreferences("github_updater", Context.MODE_PRIVATE)
        when (code) {
            PackageInstaller.STATUS_SUCCESS ->
                prefs.edit().putLong("last_attempted_version", 0L)
                    .putString(GitHubUpdater.STATUS_PREF, "Update installed successfully").apply()
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                prefs.edit().putString(GitHubUpdater.STATUS_PREF, "Android requires confirmation for this update").apply()
                val approval = if (Build.VERSION.SDK_INT >= 33)
                    intent?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (approval != null) {
                    val manager = context.getSystemService(NotificationManager::class.java)
                    val channel = "github_update_approval"
                    manager.createNotificationChannel(
                        NotificationChannel(channel, "App update approval", NotificationManager.IMPORTANCE_HIGH)
                    )
                    val pending = PendingIntent.getActivity(
                        context, 4160, approval.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    manager.notify(4160, Notification.Builder(context, channel)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Daily SIM Caller update needs approval")
                        .setContentText("Tap to approve the Android update")
                        .setAutoCancel(true)
                        .setContentIntent(pending).build())
                }
            }
            else -> prefs.edit().putLong("last_attempted_version", 0L)
                .putString(
                    GitHubUpdater.STATUS_PREF,
                    "Android rejected the GitHub update: " +
                        (intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: code.toString())
                ).apply()
        }
    }
}
