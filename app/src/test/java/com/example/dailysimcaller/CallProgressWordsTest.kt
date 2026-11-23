package com.example.dailysimcaller

import org.junit.Assert.assertEquals
import org.junit.Test

class CallProgressWordsTest {
    @Test fun readyIsSimple() {
        val result = CallProgressWords.describe(CallQueue.Stage.READY, 0, 0, false)
        assertEquals("Ready to start", result.headline)
    }

    @Test fun waitingIsEasyToRead() {
        val result = CallProgressWords.describe(CallQueue.Stage.WAITING, 2, 5, false)
        assertEquals("Waiting for an answer", result.headline)
    }

    @Test fun finishingDoesNotClaimEveryCallConnected() {
        val result = CallProgressWords.describe(CallQueue.Stage.FINISHED, 4, 4, false)
        assertEquals("All done!", result.headline)
        assertEquals("We tried each number in this test.", result.explanation)
    }

    @Test fun loadingNumbersNeverClaimsCallsStarted() {
        val result = CallProgressWords.describe(CallQueue.Stage.READY, 0, 0, true)
        assertEquals("Getting the phone numbers", result.headline)
    }

    @Test fun troubleIsClear() {
        val result = CallProgressWords.describe(CallQueue.Stage.NEEDS_HELP, 1, 5, false)
        assertEquals("The test could not keep going", result.headline)
    }
}
