package com.example.dailysimcaller

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.IOException

/**
 * Retry only a previously authorized foreground call request (manual or post-boot).
 * Never dial from cached numbers and never auto-start a new call run just because
 * internet became available. This object only fetches; the visible Activity owns dialing.
 */
internal object CallStartRetry {
    internal fun waitSeconds(failures: Int): Long = when {
        failures <= 1 -> 10L
        failures == 2 -> 20L
        failures == 3 -> 30L
        else -> 60L
    }

    internal fun isTemporary(error: Throwable): Boolean {
        if (error is IOException) return true
        val message = error.message.orEmpty()
        val http = Regex("CSV server returned HTTP (\\d+)").find(message)?.groupValues?.get(1)?.toIntOrNull()
        return http == 408 || http == 429 || (http != null && http >= 500)
    }

    suspend fun waitForFreshNumbers(
        shouldContinue: () -> Boolean,
        fetch: suspend () -> List<String>,
        onStatus: (String) -> Unit,
        wait: suspend (Long) -> Unit = { delay(it) }
    ): List<String>? {
        var failures = 0
        while (currentCoroutineContext().isActive && shouldContinue()) {
            onStatus(if (failures == 0) "Loading phone numbers..." else "Trying Google Sheets again...")
            try {
                val fresh = fetch()
                require(fresh.isNotEmpty()) { "No approved numbers in the current list" }
                if (!shouldContinue()) return null
                return fresh.distinct()
            } catch (e: CancellationException) {
                throw e // Lifecycle changes must never be misreported as a network error.
            } catch (e: Exception) {
                if (!isTemporary(e)) {
                    onStatus("Could not load the number list. Check the list or ask your supervisor. No calls started.")
                    return null
                }
                failures++
                val delaySeconds = waitSeconds(failures)
                onStatus("Connection failed. Trying again in $delaySeconds seconds. No calls started.")
                wait(delaySeconds * 1000L)
            }
        }
        return null
    }
}
