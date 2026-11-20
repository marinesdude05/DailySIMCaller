package com.example.dailysimcaller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Fetches one centrally administered HTTPS CSV; does not upload phone numbers anywhere. */
object RemoteCsv {
    data class Snapshot(val numbers: List<String>, val sha256Prefix: String)

    private const val MAX_CSV_BYTES = 1024 * 1024 // Max 1 MiB

    suspend fun fetch(endpoint: String): Snapshot = withContext(Dispatchers.IO) {
        val url = URL(endpoint)
        require(url.protocol.equals("https", ignoreCase = true)) { "CSV source must use HTTPS" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/csv, text/plain")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "CSV server returned HTTP ${connection.responseCode}"
            }
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "CSV redirect left HTTPS"
            }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    require(bytes.size() + count <= MAX_CSV_BYTES) { "CSV exceeds size limit" }
                    bytes.write(buffer, 0, count)
                }
            }
            val raw = bytes.toByteArray()
            val csv = raw.toString(Charsets.UTF_8)
            require(!csv.trimStart().startsWith("<")) {
                "CSV endpoint returned an HTML page instead of a CSV file"
            }
            val numbers = PhoneCsv.parse(csv)
            require(numbers.isNotEmpty()) { "No valid phone numbers in downloaded CSV" }
            val digest = MessageDigest.getInstance("SHA-256").digest(raw)
            val fingerprint = digest.take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            Snapshot(numbers, fingerprint)
        } finally {
            connection.disconnect()
        }
    }
}
