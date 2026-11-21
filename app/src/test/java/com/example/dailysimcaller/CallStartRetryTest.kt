package com.example.dailysimcaller

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class CallStartRetryTest {
    @Test fun retryAfterDnsFailureThenUseFreshList() = runBlocking {
        var attempts = 0
        val waits = mutableListOf<Long>()
        val result = CallStartRetry.waitForFreshNumbers(
            shouldContinue = { true },
            fetch = {
                attempts++
                if (attempts < 3) throw UnknownHostException("docs.google.com")
                listOf("+12125550101")
            },
            onStatus = {},
            wait = { waits.add(it) }
        )
        assertEquals(listOf("+12125550101"), result)
        assertEquals(3, attempts)
        assertEquals(listOf(10_000L, 20_000L), waits)
    }

    @Test fun stopsWhenOperatorCancels() = runBlocking {
        var active = true
        var attempts = 0
        val result = CallStartRetry.waitForFreshNumbers(
            shouldContinue = { active },
            fetch = { attempts++; throw UnknownHostException("offline") },
            onStatus = {},
            wait = { active = false }
        )
        assertNull(result)
        assertEquals(1, attempts)
    }

    @Test fun badListDoesNotRetry() = runBlocking {
        var attempts = 0
        val result = CallStartRetry.waitForFreshNumbers(
            shouldContinue = { true },
            fetch = { attempts++; emptyList() },
            onStatus = {},
            wait = { error("must not wait for malformed data") }
        )
        assertNull(result)
        assertEquals(1, attempts)
    }

    @Test fun temporaryHttpStatusOnly() {
        assertTrue(CallStartRetry.isTemporary(IllegalArgumentException("CSV server returned HTTP 503")))
        assertTrue(CallStartRetry.isTemporary(IllegalArgumentException("CSV server returned HTTP 429")))
        assertFalse(CallStartRetry.isTemporary(IllegalArgumentException("CSV server returned HTTP 403")))
        assertFalse(CallStartRetry.isTemporary(IllegalArgumentException("Missing phone_number column header")))
        assertEquals(60L, CallStartRetry.waitSeconds(400))
    }
}
