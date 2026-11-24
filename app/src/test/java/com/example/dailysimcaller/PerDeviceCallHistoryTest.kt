package com.example.dailysimcaller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerDeviceCallHistoryTest {
    @Test
    fun exportFieldsIncludePhoneDateTimeAndActualSeconds() {
        val entry = PerDeviceCallHistory.Entry(
            id = 12,
            deviceId = "C9-ABC12345",
            phoneNumber = "4055991234",
            dialedAtMs = 0L,
            timeZoneId = "UTC",
            connectedSeconds = 57L,
            outcome = "Ended"
        )
        assertEquals("1970-01-01", entry.localDate())
        assertEquals("00:00:00", entry.localTime())
        val header = PerDeviceCallHistory.csvHeader()
        assertTrue(header.contains("phone_number,date,time"))
        assertTrue(header.contains("connected_seconds"))
        val row = PerDeviceCallHistory.rowAsCsv(entry)
        assertTrue(row.contains("\"4055991234\""))
        assertTrue(row.contains("\"1970-01-01\""))
        assertTrue(row.contains("\"57\""))
        assertTrue(row.contains("\"C9-ABC12345\""))
    }
}
