package com.example.dailysimcaller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRestartPolicyTest {
    @Test fun autoRequestsWhenSetupFirstReachesOneHundredPercent() {
        assertTrue(SetupRestartPolicy.shouldRequest(true, true, false, false))
    }

    @Test fun cannotRestartBeforeAllPermissionsAreVerified() {
        assertFalse(SetupRestartPolicy.shouldRequest(false, true, false, false))
    }

    @Test fun cannotRestartTwiceOnReopeningTheHomeActivity() {
        assertFalse(SetupRestartPolicy.shouldRequest(true, true, true, false))
    }

    @Test fun doesNotRebootWhileAppIsBehindAndroidSettings() {
        assertFalse(SetupRestartPolicy.shouldRequest(true, false, false, false))
    }

    @Test fun doesNotInterruptAnActiveCallOrQueue() {
        assertFalse(SetupRestartPolicy.shouldRequest(true, true, false, true))
    }
}
