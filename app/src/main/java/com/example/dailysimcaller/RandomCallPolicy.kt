package com.example.dailysimcaller

import kotlin.random.Random

/** One test cycle calls exactly two distinct authorized numbers. */
internal object RandomCallPolicy {
    const val NUMBERS_PER_RUN = 2
    const val MIN_CALL_SECONDS = 46
    const val DEFAULT_MAX_CALL_SECONDS = 90

    fun selectNumbers(authorizedNumbers: List<String>, random: Random = Random.Default): List<String> {
        val candidates = authorizedNumbers.distinct()
        require(candidates.size >= NUMBERS_PER_RUN) { "At least two distinct approved phone numbers are required" }
        return candidates.shuffled(random).take(NUMBERS_PER_RUN)
    }

    /** The target is sampled per answered call, not per queue or dial attempt. */
    fun durationSeconds(maximumSeconds: Int, random: Random = Random.Default): Int {
        require(maximumSeconds in MIN_CALL_SECONDS..3600)
        return random.nextInt(MIN_CALL_SECONDS, maximumSeconds + 1)
    }
}
