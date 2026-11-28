package com.example.dailysimcaller

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal object CallLogSync {
    private const val IMMEDIATE = 52521
    private const val PERIODIC = 52522
    private const val BATCH_SIZE = 20
    private const val TIMEOUT_MS = 15_000
    private val lock = Mutex()
    private val tokenPattern = Regex("[0-9a-fA-F-]{72}")
    val available get() = BuildConfig.CENTRAL_SYNC_URL.isNotEmpty()

    data class Identity(val deviceId: String, val installationId: String)

    @Synchronized
    fun identity(context: Context): Identity {
        // Reuse V51's installation identity to avoid a second dashboard entry on upgrade.
        val file = AtomicFile(File(context.noBackupFilesDir, "device-registration-id"))
        val installationId = try {
            UUID.fromString(file.readFully().decodeToString()).toString()
        } catch (_: FileNotFoundException) {
            UUID.randomUUID().toString().also { write(file, it) }
        }
        return Identity(PerDeviceCallHistory.deviceId(context), installationId)
    }

    private fun credentialFile(context: Context) =
        AtomicFile(File(context.noBackupFilesDir, "central-sync-credential"))

    private fun credential(context: Context): String? =
        try {
            credentialFile(context).readFully().decodeToString().takeIf(tokenPattern::matches)
        } catch (_: IOException) {
            null
        }

    suspend fun provision(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (!available) return@withContext false
        try {
            val encoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(512)
                var length = 0
                while (length < buffer.size) {
                    val count = stream.read(buffer, length, buffer.size - length)
                    if (count < 0) break
                    length += count
                }
                if (length == 0 || stream.read() != -1) throw IOException("Invalid credential file size")
                buffer.decodeToString(endIndex = length)
            } ?: return@withContext false
            val token = JSONObject(encoded).getString("token")
            if (!tokenPattern.matches(token)) return@withContext false
            lock.withLock {
                val response = send(body(identity(context), token, "register"))
                if (!response.optBoolean("registered")) return@withLock false
                write(credentialFile(context), token)
                schedule(context)
                true
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private fun write(file: AtomicFile, text: String) {
        val stream = file.startWrite()
        try {
            stream.write(text.encodeToByteArray())
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun body(identity: Identity, token: String, action: String) = JSONObject()
        .put("deviceId", identity.deviceId)
        .put("installationId", identity.installationId)
        .put("token", token)
        .put("consent", true)
        .put("version", BuildConfig.VERSION_NAME)
        .put("action", action)

    suspend fun uploadPending(context: Context): Boolean = lock.withLock {
        if (!available) return@withLock true
        val token = credential(context) ?: return@withLock true
        val device = identity(context)
        repeat(2) {
            val batch = PerDeviceCallHistory.pending(context, BATCH_SIZE)
            if (batch.isEmpty()) return@withLock true
            val calls = JSONArray()
            for (entry in batch) {
                check(entry.deviceId == device.deviceId)
                calls.put(JSONObject()
                    .put("id", entry.id)
                    .put("date", entry.localDate())
                    .put("time", entry.localTime())
                    .put("timeZone", entry.timeZoneId)
                    .put("connectedSeconds", entry.connectedSeconds)
                    .put("outcome", entry.outcome))
            }
            val response = send(body(device, token, "sync").put("calls", calls))
            val result = response.getJSONArray("confirmed")
            val confirmed = (0 until result.length()).map(result::getLong).toSet()
            if (confirmed != batch.map { it.id }.toSet()) throw IOException("Incomplete sync confirmation")
            confirmed.forEach { PerDeviceCallHistory.markSynced(context, it) }
        }
        PerDeviceCallHistory.pendingCount(context) == 0
    }

    private fun connection(url: URL): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

    private fun send(payload: JSONObject): JSONObject {
        val request = connection(URL(BuildConfig.CENTRAL_SYNC_URL))
        val response = try {
            request.requestMethod = "POST"
            request.doOutput = true
            request.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            request.outputStream.use { it.write(payload.toString().encodeToByteArray()) }
            when (request.responseCode) {
                200 -> readResponse(request)
                302, 303 -> {
                    // Apps Script sends ContentService results through a one-time GET URL.
                    // Never resend the POST or token to the redirect destination.
                    val redirect = URL(request.getHeaderField("Location") ?: throw IOException("Missing redirect"))
                    if (redirect.protocol != "https" || redirect.host != "script.googleusercontent.com" ||
                        redirect.userInfo != null || redirect.port !in listOf(-1, 443)
                    ) throw IOException("Unexpected sync response redirect")
                    val result = connection(redirect)
                    try {
                        if (result.responseCode != 200) throw IOException("Sync response unavailable")
                        readResponse(result)
                    } finally {
                        result.disconnect()
                    }
                }
                else -> throw IOException("Sync request rejected")
            }
        } finally {
            request.disconnect()
        }
        if (!response.optBoolean("ok")) throw IOException("Sync request was not confirmed")
        return response
    }

    private fun readResponse(connection: HttpURLConnection): JSONObject =
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancel(46001) // Retired direct-Sheets jobs from V51.
        scheduler.cancel(46015)
        if (!available || credential(context) == null) return
        if (scheduler.getPendingJob(PERIODIC) == null) {
            scheduler.schedule(job(context, PERIODIC).setPeriodic(15 * 60_000L).setPersisted(true).build())
        }
        queueImmediate(context)
    }

    fun queueImmediate(context: Context) {
        if (!available || credential(context) == null) return
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(IMMEDIATE) == null) {
            scheduler.schedule(job(context, IMMEDIATE)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build())
        }
    }

    private fun job(context: Context, id: Int): JobInfo.Builder =
        JobInfo.Builder(id, ComponentName(context, CallLogSyncJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
}

class CallLogSyncJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val active = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        active[params.jobId] = scope.launch {
            val retry = try {
                !withContext(Dispatchers.IO) { CallLogSync.uploadPending(this@CallLogSyncJob) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                true
            }
            active.remove(params.jobId)
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        active.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
