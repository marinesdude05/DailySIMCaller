package com.example.dailysimcaller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SheetRowMappingTest {
    @Test fun sameCallIdAlwaysMapsToSameRow() {
        assertEquals(2L, SheetRowMapping.rowNumber(1))
        assertEquals(19L, SheetRowMapping.rowNumber(18))
        assertEquals(19L, SheetRowMapping.rowNumber(18))
    }

    @Test fun eachDeviceHasItsOwnStableTab() {
        assertEquals("'C9-ABCDEF12'!A19:H19",
            SheetRowMapping.callRange("C9-ABCDEF12", 18))
        assertEquals("'C9-12345678'!A19:H19",
            SheetRowMapping.callRange("C9-12345678", 18))
    }

    @Test fun rejectsInvalidIdentifiersAndRows() {
        assertThrows(IllegalArgumentException::class.java) {
            SheetRowMapping.callRange("Wrong title'!A1", 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SheetRowMapping.rowNumber(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SheetRowMapping.rowNumber(500_000L)
        }
    }
}
