package com.example.dailysimcaller

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Direct OAuth uploads from the phone to one known Google Sheets workbook.
 * No Cloudflare relay, enrollment password, JSON key or saved access token.
 */
internal object CallLogSync {
    private const val PREFS = "google_sheets_direct_sync"
    private const val ENABLED = "enabled"
    private const val SHEET_ID = "1m93ppcPSgreho3RNRDI1QKUm5fecUazWcdDt8PRglNg"
    private const val ROOT = "https://sheets.googleapis.com/v4/spreadsheets/"
    private const val SCOPE = "https://www.googleapis.com/auth/spreadsheets"
    private const val AFTER_CALL = 46001
    private const val BACKGROUND = 46015
    private const val FIFTEEN_MINUTES = 15L * 60L * 1000L
    private val lock = Mutex()
    private val headings = listOf(
        "call_id", "device_id", "phone_number", "date", "time",
        "time_zone", "connected_seconds", "result"
    )

    fun configured(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false)

    fun hasGooglePlayServices(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
            ConnectionResult.SUCCESS

    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    fun disconnect(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, false).apply()
        context.getSystemService(JobScheduler::class.java).apply {
            cancel(BACKGROUND)
            cancel(AFTER_CALL)
        }
        // Call history and Google's account grants are not deleted.
    }

    private fun enable(context: Context) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, true).commit()) {
            "Could not save the Google Sheets connection"
        }
        schedulePeriodic(context)
        queueImmediate(context)
    }

    /** Called after the in-app Google consent flow; checks access before enabling. */
    suspend fun verifyAndConnect(context: Context, token: String): String =
        withContext(Dispatchers.IO) {
            require(token.isNotBlank()) { "Google did not return an access token" }
            val file = request("GET", ROOT + SHEET_ID +
                "?fields=spreadsheetId,properties(title)", token)
            check(file.optString("spreadsheetId") == SHEET_ID) {
                "Could not verify the Daily SIM Caller call-log workbook"
            }
            enable(context)
            "Google Sheets connected. " + uploadWithToken(context, token)
        }

    /** Retrieves short-lived Google credentials on demand (including background jobs). */
    suspend fun uploadPending(context: Context): String = withContext(Dispatchers.IO) {
        if (!configured(context)) return@withContext "Connect Google Sheets first"
        if (!hasGooglePlayServices(context)) throw IOException(
            "This phone needs Google Play services for Sheets sign-in")
        val auth = try {
            Tasks.await(Identity.getAuthorizationClient(context)
                .authorize(authorizationRequest()))
        } catch (error: Exception) {
            throw IOException("Google authorization needs attention in the app", error)
        }
        if (auth.hasResolution()) throw IOException(
            "Open Daily SIM Caller and reconnect Google Sheets")
        val token = auth.accessToken
        if (token.isNullOrBlank()) throw IOException(
            "Google authorization expired; reconnect Sheets in the app")
        uploadWithToken(context, token)
    }

    private suspend fun uploadWithToken(context: Context, token: String): String =
        lock.withLock {
            var sent = 0
            var tab: Tab? = null
            repeat(10) {
                val waiting = PerDeviceCallHistory.pending(context, 50)
                if (waiting.isEmpty()) {
                    return@withLock if (sent == 0) "No completed calls waiting to sync"
                        else "Synced $sent completed calls"
                }
                val deviceId = PerDeviceCallHistory.deviceId(context)
                if (tab == null) tab = ensureTab(token, deviceId)
                val neededRows = waiting.maxOf { it.id + 1 }
                if (neededRows > 500_000L) throw IOException(
                    "The phone has reached its sheet row limit; archive old records")
                val current = tab!!
                if (neededRows > current.rows) {
                    expandTab(token, current.id, neededRows.toInt())
                    tab = current.copy(rows = neededRows.toInt())
                }
                val updates = JSONArray()
                for (item in waiting) {
                    val rowNum = item.id + 1
                    val columns = JSONArray().put(item.id).put(item.deviceId)
                        .put(item.phoneNumber).put(item.localDate())
                        .put(item.localTime()).put(item.timeZoneId)
                        .put(item.connectedSeconds).put(item.outcome)
                    updates.put(JSONObject()
                        .put("range", "'$deviceId'!A" + rowNum + ":H" + rowNum)
                        .put("majorDimension", "ROWS")
                        .put("values", JSONArray().put(columns)))
                }
                val response = request("POST", ROOT + SHEET_ID + "/values:batchUpdate",
                    token, JSONObject().put("valueInputOption", "RAW").put("data", updates))
                if (response.optInt("totalUpdatedRows", 0) != waiting.size ||
                    response.optJSONArray("responses")?.length() != waiting.size) {
                    throw IOException("Google did not confirm every call row; will retry")
                }
                // Stable row=call_id+1 makes retries idempotent.
                waiting.forEach { PerDeviceCallHistory.markSynced(context, it.id) }
                sent += waiting.size
            }
            "Synced $sent calls; the rest will retry"
        }

    private data class Tab(val id: Int, val rows: Int)

    private fun tabOf(metadata: JSONObject, deviceId: String): Tab? {
        val sheets = metadata.optJSONArray("sheets") ?: return null
        for (i in 0 until sheets.length()) {
            val props = sheets.optJSONObject(i)?.optJSONObject("properties") ?: continue
            if (props.optString("title") == deviceId) {
                return Tab(props.getInt("sheetId"),
                    props.optJSONObject("gridProperties")?.optInt("rowCount", 1000) ?: 1000)
            }
        }
        return null
    }

    private fun sheetMetadata(token: String): JSONObject =
        request("GET", ROOT + SHEET_ID +
            "?fields=sheets(properties(sheetId,title,gridProperties(rowCount)))", token)

    private fun ensureTab(token: String, deviceId: String): Tab {
        require(Regex("C9-[A-F0-9]{8}").matches(deviceId))
        var tab = tabOf(sheetMetadata(token), deviceId)
        if (tab == null) {
            val properties = JSONObject().put("title", deviceId)
                .put("gridProperties", JSONObject()
                    .put("rowCount", 1000).put("columnCount", 8))
            val command = JSONObject().put("requests", JSONArray().put(
                JSONObject().put("addSheet", JSONObject().put("properties", properties))))
            try {
                request("POST", ROOT + SHEET_ID + ":batchUpdate", token, command)
            } catch (error: IOException) {
                if (tabOf(sheetMetadata(token), deviceId) == null) throw error
            }
            tab = tabOf(sheetMetadata(token), deviceId)
                ?: throw IOException("Google did not create this device tab")
        }
        // A fixed header keeps the ten existing placeholder tabs untouched.
        request("PUT", ROOT + SHEET_ID + "/values/" +
            URLEncoder.encode("'$deviceId'!A1:H1", "UTF-8") +
            "?valueInputOption=RAW", token,
            JSONObject().put("values", JSONArray().put(JSONArray(headings))))
        return tab
    }

    private fun expandTab(token: String, sheetId: Int, rowCount: Int) {
        val properties = JSONObject().put("sheetId", sheetId)
            .put("gridProperties", JSONObject().put("rowCount", rowCount))
        val command = JSONObject().put("requests", JSONArray().put(
            JSONObject().put("updateSheetProperties", JSONObject()
                .put("properties", properties).put("fields", "gridProperties.rowCount"))))
        request("POST", ROOT + SHEET_ID + ":batchUpdate", token, command)
    }

    private fun request(
        method: String, url: String, token: String, body: JSONObject? = null
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            when (code) {
                401 -> throw IOException("Google sign-in expired; reconnect in the app")
                403 -> throw IOException("Google denied Sheets access; use an Editor account")
                404 -> throw IOException("The chosen Google account cannot find this Sheet")
            }
            if (code !in 200..299) throw IOException("Google Sheets returned HTTP $code")
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }

    fun queueImmediate(context: Context) {
        if (!configured(context)) return
        context.getSystemService(JobScheduler::class.java).schedule(
            JobInfo.Builder(AFTER_CALL, ComponentName(context, CallLogSyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build())
    }

    fun schedulePeriodic(context: Context) {
        if (!configured(context)) return
        context.getSystemService(JobScheduler::class.java).schedule(
            JobInfo.Builder(BACKGROUND, ComponentName(context, CallLogSyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(FIFTEEN_MINUTES)
                .setPersisted(true)
                .build())
    }
}

/** Does not open the Google account picker when the phone is locked/asleep. */
class CallLogSyncJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var active: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        if (!CallLogSync.configured(this)) return false
        active = scope.launch {
            val worked = try {
                CallLogSync.uploadPending(this@CallLogSyncJob)
                true
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                false
            }
            jobFinished(params, !worked)
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
