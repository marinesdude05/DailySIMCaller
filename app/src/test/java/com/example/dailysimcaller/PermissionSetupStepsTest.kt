package com.example.dailysimcaller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionSetupStepsTest {
    @Test fun missingPermissionsAreNotCalledDone() {
        val steps = PermissionSetupSteps.build(
            phonePermissionGranted = false,
            needsNotificationPermission = true,
            notificationGranted = false,
            helperRequired = true,
            helperEnabled = false,
            phoneRoleHeld = false,
            homeRequired = true,
            homeRoleHeld = false
        )
        assertEquals(5, steps.size)
        assertEquals(0, steps.count { it.done })
        assertEquals(SetupPermissionKey.PHONE_PERMISSION, steps.first().key)
    }

    @Test fun optionalHomeAndNotificationsAreNotShownWhenNotRequired() {
        val steps = PermissionSetupSteps.build(
            phonePermissionGranted = true,
            needsNotificationPermission = false,
            notificationGranted = false,
            helperRequired = false,
            helperEnabled = false,
            phoneRoleHeld = true,
            homeRequired = false,
            homeRoleHeld = false
        )
        assertEquals(2, steps.size)
        assertTrue(steps.all { it.done })
        assertFalse(steps.any { it.key == SetupPermissionKey.NOTIFICATIONS || it.key == SetupPermissionKey.HOME_APP })
    }

    @Test fun progressCountsOnlySystemVerifiedApprovals() {
        val steps = PermissionSetupSteps.build(
            phonePermissionGranted = true,
            needsNotificationPermission = true,
            notificationGranted = true,
            helperRequired = true,
            helperEnabled = false,
            phoneRoleHeld = true,
            homeRequired = true,
            homeRoleHeld = false
        )
        assertEquals(3, steps.count { it.done })
        assertEquals(SetupPermissionKey.SHUTDOWN_HELPER, steps.first { !it.done }.key)
    }
}
