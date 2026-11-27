package com.example.dailysimcaller

import android.app.Activity
import android.content.Context
import android.util.AtomicFile
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

/**
 * Optional registration for an authorized test installation. Separate from call history.
 * No credential, tracker ID, phone number, hardware ID, or location is embedded in source.
 * The administrator supplies the private destination as a build property.
 */
internal class DeviceRegistration(private val activity: ComponentActivity) {
    private val prefs by lazy {
        activity.getSharedPreferences("device_registration_v50", Context.MODE_PRIVATE)
    }
    private val trackingId = BuildConfig.DEVICE_TRACKING_SHEET_ID
    val configured: Boolean get() = trackingId.isNotBlank()
    var status by mutableStateOf("Not registered")
        private set
    var registered by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    private val consentLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            busy = false
            status = "Registration declined. Nothing was sent."
            return@registerForActivityResult
        }
        try {
            val auth = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(result.data!!)
            completeAuthorization(auth.accessToken)
        } catch (_: Exception) {
            busy = false
            status = "Google authorization failed. Please try again."
        }
    }

    fun initialize() {
        if (!configured) {
            status = "Registration is not configured in this APK."
            return
        }
        try {
            val id = installationId()
            registered = prefs.getString("confirmed_id", null) == id
            status = if (registered) "Device registered" else "Not registered"
            // Only retry a previous, explicitly granted authorization. Never launch consent
            // automatically or send anything merely because a phone opens the app.
            if (!registered && prefs.getString("approved_id", null) == id) {
                authorize(interactive = false)
            }
        } catch (_: Exception) {
            status = "Could not read installation identity."
        }
    }

    fun requestRegistration() {
        if (!configured || registered || busy) return
        authorize(interactive = true)
    }

    private fun authorize(interactive: Boolean) {
        if (busy || registered) return
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
            != ConnectionResult.SUCCESS) {
            status = "Google Play services is required for registration."
            return
        }
        busy = true
        status = "Checking Google permission..."
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/spreadsheets")))
            .build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { auth ->
                try {
                    if (auth.hasResolution()) {
                        if (!interactive) {
                            busy = false
                            status = "Tap Consent and register device to reconnect."
                        } else {
                            val intent = auth.pendingIntent ?: error("No consent window")
                            consentLauncher.launch(
                                IntentSenderRequest.Builder(intent.intentSender).build()
                            )
                        }
                    } else completeAuthorization(auth.accessToken)
                } catch (_: Exception) {
                    busy = false
                    status = "Could not open Google permission. Please retry."
                }
            }
            .addOnFailureListener {
                busy = false
                status = "Google authorization unavailable. Please retry."
            }
    }

    private fun completeAuthorization(token: String?) {
        if (token.isNullOrBlank()) {
            busy = false
            status = "Google permission was not granted."
            return
        }
        activity.lifecycleScope.launch {
            try {
                val id = installationId()
                // Persist only the approval marker, never the OAuth token.
                check(prefs.edit().putString("approved_id", id).commit())
                status = "Registering device..."
                withContext(Dispatchers.IO) { appendRegistration(id, token) }
                check(prefs.edit().putString("confirmed_id", id).commit())
                registered = true
                status = "Device registered"
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                status = "Registration not confirmed. Check internet and authorized dashboard access; retry."
            } finally {
                busy = false
            }
        }
    }

    private fun installationId(): String = synchronized(idLock) {
        // noBackupFilesDir prevents Android backup/restore from cloning IDs across phones.
        val file = AtomicFile(File(activity.noBackupFilesDir, "device-registration-id"))
        if (file.baseFile.exists()) {
            return@synchronized UUID.fromString(file.readFully().toString(Charsets.UTF_8)).toString()
        }
        val id = UUID.randomUUID().toString()
        val stream = file.startWrite()
        try {
            stream.write(id.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        id
    }

    private fun appendRegistration(id: String, token: String) {
        val range = URLEncoder.encode("'Device Events'!A4:F", "UTF-8").replace("+", "%20")
        val url = URL("https://sheets.googleapis.com/v4/spreadsheets/" +
            trackingId + "/values/" + range + ":append?valueInputOption=RAW&insertDataOption=INSERT_ROWS")
        val body = JSONObject().put("majorDimension", "ROWS").put("values",
            JSONArray().put(JSONArray().put(Instant.now().toString()).put(id)
                .put("first_open").put(BuildConfig.VERSION_NAME).put("Yes")
                .put("Opted-in test installation; first observed registration")))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + token)
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw IOException("Registration rejected")
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (response.optJSONObject("updates")?.optInt("updatedRows") != 1) {
                throw IOException("Registration not confirmed")
            }
            // If a response was lost, a retry can append the same anonymous ID.
            // The dashboard counts unique IDs, so such retries cannot inflate its install count.
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val idLock = Any()
    }
}
