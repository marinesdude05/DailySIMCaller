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
    @Test
    fun monthWeekLabelsFollowTheCalendarRatherThanFirstCall() {
        val zone = java.util.TimeZone.getTimeZone("America/Chicago")
        fun timestamp(year: Int, month: Int, day: Int): Long =
            java.util.Calendar.getInstance(zone).apply {
                clear()
                set(year, month - 1, day, 12, 0, 0)
            }.timeInMillis

        assertEquals(1, PerDeviceCallHistory.calendarWeek(timestamp(2026, 11, 1), zone.id))
        assertEquals(1, PerDeviceCallHistory.calendarWeek(timestamp(2026, 11, 7), zone.id))
        assertEquals(2, PerDeviceCallHistory.calendarWeek(timestamp(2026, 11, 8), zone.id))
        assertEquals(3, PerDeviceCallHistory.calendarWeek(timestamp(2026, 11, 21), zone.id))
        assertEquals(4, PerDeviceCallHistory.calendarWeek(timestamp(2026, 11, 22), zone.id))
        assertEquals(4, PerDeviceCallHistory.calendarWeek(timestamp(2026, 11, 24), zone.id))
        assertEquals(4, PerDeviceCallHistory.calendarWeek(timestamp(2026, 11, 28), zone.id))
        assertEquals(5, PerDeviceCallHistory.calendarWeek(timestamp(2026, 11, 29), zone.id))
        assertEquals(1, PerDeviceCallHistory.calendarWeek(timestamp(2026, 12, 1), zone.id))
        assertEquals("2026-11", PerDeviceCallHistory.monthKey(timestamp(2026, 11, 24), zone.id))
        assertEquals("November 2026", PerDeviceCallHistory.monthLabel(timestamp(2026, 11, 24), zone.id))
        assertEquals(30, PerDeviceCallHistory.daysInMonth("2026-11"))
        assertEquals(5, PerDeviceCallHistory.weeksInMonth("2026-11"))
        assertEquals(4, PerDeviceCallHistory.weeksInMonth("2027-02"))
    }

}
