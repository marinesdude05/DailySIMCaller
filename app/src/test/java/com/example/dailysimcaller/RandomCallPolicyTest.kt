package com.example.dailysimcaller

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RandomCallPolicyTest {
    @Test fun choosesExactlyTwoDistinctApprovedNumbers() {
        val approved = listOf("4055991234", "9182244200", "4055991234", "4795550101")
        repeat(200) { seed ->
            val selected = RandomCallPolicy.selectNumbers(approved, Random(seed))
            assertEquals(2, selected.size)
            assertEquals(2, selected.distinct().size)
            assertTrue(approved.containsAll(selected))
        }
    }

    @Test fun picksIndependentDurationsStrictlyAbove45() {
        val random = Random(2026)
        val values = (1..200).map { RandomCallPolicy.durationSeconds(90, random) }
        assertTrue(values.all { it in 46..90 })
        assertTrue(values.distinct().size > 1)
        assertEquals(46, RandomCallPolicy.durationSeconds(46, Random(1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSingleDestination() {
        RandomCallPolicy.selectNumbers(listOf("4055991234"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMaximumOf45Seconds() {
        RandomCallPolicy.durationSeconds(45)
    }
}
