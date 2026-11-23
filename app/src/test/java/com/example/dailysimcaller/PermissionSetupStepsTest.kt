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
        assertEquals(
            listOf(
                SetupPermissionKey.PHONE_PERMISSION,
                SetupPermissionKey.NOTIFICATIONS,
                SetupPermissionKey.HOME_APP,
                SetupPermissionKey.SHUTDOWN_HELPER,
                SetupPermissionKey.PHONE_APP
            ),
            steps.map { it.key }
        )
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
        assertEquals(SetupPermissionKey.HOME_APP, steps.first { !it.done }.key)
    }
    @Test fun afterNotificationsGuidedSetupGoesToHomeNotHelper() {
        val steps = PermissionSetupSteps.build(
            phonePermissionGranted = true,
            needsNotificationPermission = true,
            notificationGranted = true,
            helperRequired = true,
            helperEnabled = false,
            phoneRoleHeld = false,
            homeRequired = true,
            homeRoleHeld = false
        )
        assertEquals(SetupPermissionKey.HOME_APP, PermissionSetupSteps.next(steps)?.key)
    }

    @Test fun afterHomeApprovalGoesToHelperThenPhone() {
        val helperNext = PermissionSetupSteps.build(
            phonePermissionGranted = true,
            needsNotificationPermission = true,
            notificationGranted = true,
            helperRequired = true,
            helperEnabled = false,
            phoneRoleHeld = false,
            homeRequired = true,
            homeRoleHeld = true
        )
        assertEquals(SetupPermissionKey.SHUTDOWN_HELPER, PermissionSetupSteps.next(helperNext)?.key)

        val phoneNext = helperNext.map {
            if (it.key == SetupPermissionKey.SHUTDOWN_HELPER) it.copy(done = true) else it
        }
        assertEquals(SetupPermissionKey.PHONE_APP, PermissionSetupSteps.next(phoneNext)?.key)
        assertEquals(null, PermissionSetupSteps.next(phoneNext.map { it.copy(done = true) }))
    }

    @Test fun denialLeavesSameStepPendingWithoutSkippingAhead() {
        val steps = PermissionSetupSteps.build(
            phonePermissionGranted = true,
            needsNotificationPermission = true,
            notificationGranted = false,
            helperRequired = true,
            helperEnabled = false,
            phoneRoleHeld = false,
            homeRequired = true,
            homeRoleHeld = false
        )
        assertEquals(SetupPermissionKey.NOTIFICATIONS, PermissionSetupSteps.next(steps)?.key)
    }

    @Test fun nextButtonOnHelperSelectsPhoneWithoutGrantingHelper() {
        val steps = PermissionSetupSteps.build(
            phonePermissionGranted = true,
            needsNotificationPermission = true,
            notificationGranted = true,
            helperRequired = true,
            helperEnabled = false,
            phoneRoleHeld = false,
            homeRequired = true,
            homeRoleHeld = true
        )
        val following = PermissionSetupSteps.nextAfter(
            steps, SetupPermissionKey.SHUTDOWN_HELPER
        )
        assertEquals(SetupPermissionKey.PHONE_APP, following?.key)
        assertFalse(steps.first { it.key == SetupPermissionKey.SHUTDOWN_HELPER }.done)
    }

    @Test fun nextButtonFromHomeGoesToHelperBeforePhone() {
        val steps = PermissionSetupSteps.build(
            phonePermissionGranted = true,
            needsNotificationPermission = true,
            notificationGranted = true,
            helperRequired = true,
            helperEnabled = false,
            phoneRoleHeld = false,
            homeRequired = true,
            homeRoleHeld = false
        )
        assertEquals(
            SetupPermissionKey.SHUTDOWN_HELPER,
            PermissionSetupSteps.nextAfter(steps, SetupPermissionKey.HOME_APP)?.key
        )
    }

    @Test fun noNextButtonWhenFollowingItemsAreAlreadyApproved() {
        val steps = PermissionSetupSteps.build(
            phonePermissionGranted = true,
            needsNotificationPermission = true,
            notificationGranted = true,
            helperRequired = true,
            helperEnabled = false,
            phoneRoleHeld = true,
            homeRequired = true,
            homeRoleHeld = true
        )
        assertEquals(null, PermissionSetupSteps.nextAfter(steps, SetupPermissionKey.SHUTDOWN_HELPER))
    }

}
