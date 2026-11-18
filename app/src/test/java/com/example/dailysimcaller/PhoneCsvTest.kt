package com.example.dailysimcaller

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCsvTest {
    @Test fun importsQuotedPhoneNumbersAndSkipsDuplicates() {
        val csv = "\uFEFFname,phone_number,notes\r\n" +
            "\"Alice, A\",+12125550101,first\r\n" +
            "Bob,(212) 555-0102,second\r\n" +
            "Again,+12125550101,duplicate\r\n" +
            "Invalid,not-a-number,bad\r\n"
        assertEquals(listOf("+12125550101", "2125550102"), PhoneCsv.parse(csv))
    }
}
