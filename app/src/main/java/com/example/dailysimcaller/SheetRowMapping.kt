package com.example.dailysimcaller

/**
 * A completed call always occupies the same Google Sheet row on repeated uploads.
 * Row one is reserved for column labels, and local call IDs start at one.
 */
internal object SheetRowMapping {
    const val MAX_CALL_ID = 499_999L

    fun rowNumber(callId: Long): Long {
        require(callId in 1L..MAX_CALL_ID) { "Call ID is outside the supported Sheet range" }
        return callId + 1L
    }

    fun deviceTab(deviceId: String): String {
        require(Regex("^C9-[A-F0-9]{8}$").matches(deviceId)) {
            "Invalid call-history device identifier"
        }
        return deviceId
    }

    fun callRange(deviceId: String, callId: Long): String {
        val title = deviceTab(deviceId)
        val row = rowNumber(callId)
        return "'$title'!A$row:H$row"
    }
}
