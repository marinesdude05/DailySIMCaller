package com.example.dailysimcaller

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Outbound call-history sync only. Never fetches the approved dialing list.
 * Encryption key remains in Android Keystore; tokens and the operator code never ship in the APK.
 * The user supplies the HTTPS relay endpoint and private enrollment code after deployment.
 */
internal object CallLogSync {
    private const val PREFS = "call_log_sync"
    private const val URL_KEY = "relay_url"
    private const val TOKEN_KEY = "device_token_ciphertext"
    private const val IV_KEY = "device_token_iv"
    private const val KEY_ALIAS = "daily_sim_caller_device_sync_key"
    private const val BACKGROUND_JOB = 45015
    private const val IMMEDIATE_JOB = 45001
    private const val INTERVAL_MS = 15L * 60L * 1000L
    private val lock = Mutex()

    fun endpoint(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(URL_KEY, "").orEmpty()

    fun configured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(URL_KEY, "").orEmpty().isNotBlank() &&
            prefs.getString(TOKEN_KEY, "").orEmpty().isNotBlank()
    }

    private fun validEndpoint(value: String): String {
        val url = URL(value.trim().trimEnd('/'))
        val protocol = url.protocol.lowercase()
        require(protocol == "https" && url.host.isNotBlank() &&
            url.toURI().userInfo == null && url.query == null && url.ref == null) {
            "Enter the HTTPS address of your deployed sync service"
        }
        return url.toExternalForm().trimEnd('/')
    }

    private fun getOrMakeKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val found = store.getKey(KEY_ALIAS, null)
        if (found is SecretKey) return found
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun saveCredentials(context: Context, endpoint: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrMakeKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(prefs.edit()
            .putString(URL_KEY, validEndpoint(endpoint))
            .putString(TOKEN_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()) { "Could not store encrypted enrollment credentials" }
    }

    private fun deviceToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(TOKEN_KEY, null)
            ?: error("This phone is not enrolled")
        val iv = prefs.getString(IV_KEY, null)
            ?: error("This phone is not enrolled")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrMakeKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    /** Must be called on Dispatchers.IO, with a newly issued operator-only code. */
    fun enroll(context: Context, endpoint: String, enrollmentCode: String): String {
        require(enrollmentCode.isNotBlank()) { "Enter the private enrollment code" }
        val address = validEndpoint(endpoint)
        val id = PerDeviceCallHistory.deviceId(context)
        val body = JSONObject()
            .put("device_id", id)
            .put("enrollment_code", enrollmentCode)
        val response = post(address + "/v1/enroll", body.toString(), null)
        val token = response.optString("token")
        require(token.length >= 32) { "Service did not return a valid device token" }
        saveCredentials(context, address, token)
        schedulePeriodic(context)
        queueImmediate(context)
        return id
    }

    private fun post(address: String, json: String, token: String?): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token)
            connection.doOutput = true
            connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val reason = runCatching { JSONObject(raw).optString("error") }.getOrNull()
                error("Sync service returned HTTP " + code +
                    (if (!reason.isNullOrBlank()) ": " + reason.take(120) else ""))
            }
            JSONObject(raw)
        } finally { connection.disconnect() }
    }

    /** Acknowledge only IDs returned by the server after a confirmed Sheets write. */
    suspend fun uploadPending(context: Context): String = lock.withLock {
        if (!configured(context)) return@withLock "Cloud sync is not enrolled"
        val id = PerDeviceCallHistory.deviceId(context)
        var synced = 0
        val address = validEndpoint(endpoint(context))
        val token = deviceToken(context)
        repeat(10) {
            val pending = PerDeviceCallHistory.pending(context, 60)
            if (pending.isEmpty()) {
                return@withLock if (synced > 0) "Synced " + synced + " calls" else "No calls waiting to sync"
            }
            val rows = JSONArray()
            for (entry in pending) {
                rows.put(JSONObject()
                    .put("call_id", entry.id)
                    .put("phone_number", entry.phoneNumber)
                    .put("date", entry.localDate())
                    .put("time", entry.localTime())
                    .put("time_zone", entry.timeZoneId)
                    .put("connected_seconds", entry.connectedSeconds)
                    .put("result", entry.outcome))
            }
            val body = JSONObject().put("device_id", id).put("calls", rows)
            val response = post(address + "/v1/calls", body.toString(), token)
            val ids = response.optJSONArray("synced_call_ids")
                ?: error("Sync service did not confirm call IDs")
            val pendingIds = pending.map { it.id }.toSet()
            var batchSynced = 0
            for (i in 0 until ids.length()) {
                val accepted = ids.optLong(i, -1L)
                if (accepted in pendingIds) {
                    PerDeviceCallHistory.markSynced(context, accepted)
                    batchSynced++
                }
            }
            synced += batchSynced
            if (batchSynced == 0) error("Sync service has not confirmed any pending records")
        }
        "Synced " + synced + " calls; remaining calls will retry"
    }

    fun queueImmediate(context: Context) {
        if (!configured(context)) return
        val job = JobInfo.Builder(IMMEDIATE_JOB, ComponentName(context, CallLogSyncJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }

    fun schedulePeriodic(context: Context) {
        if (!configured(context)) return
        val job = JobInfo.Builder(BACKGROUND_JOB, ComponentName(context, CallLogSyncJob::class.java))
            .setPeriodic(INTERVAL_MS)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }
}

/** Android may defer background jobs during Doze/battery restrictions. */
class CallLogSyncJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var active: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!CallLogSync.configured(this)) return false
        active = scope.launch {
            val success = try {
                CallLogSync.uploadPending(this@CallLogSyncJob)
                true
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                false
            }
            jobFinished(params, !success)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        active?.cancel()
        return true
    }

    override fun onDestroy() {
        active?.cancel()
        super.onDestroy()
    }
}
