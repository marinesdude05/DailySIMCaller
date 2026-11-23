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
                SetupPermissionKey.PHONE_APP,
                SetupPermissionKey.SHUTDOWN_HELPER
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

    @Test fun afterHomeApprovalGoesToPhoneThenHelper() {
        val phoneNext = PermissionSetupSteps.build(
            phonePermissionGranted = true,
            needsNotificationPermission = true,
            notificationGranted = true,
            helperRequired = true,
            helperEnabled = false,
            phoneRoleHeld = false,
            homeRequired = true,
            homeRoleHeld = true
        )
        assertEquals(SetupPermissionKey.PHONE_APP, PermissionSetupSteps.next(phoneNext)?.key)

        val helperNext = phoneNext.map {
            if (it.key == SetupPermissionKey.PHONE_APP) it.copy(done = true) else it
        }
        assertEquals(SetupPermissionKey.SHUTDOWN_HELPER, PermissionSetupSteps.next(helperNext)?.key)
        assertEquals(null, PermissionSetupSteps.next(helperNext.map { it.copy(done = true) }))
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

    @Test fun nextButtonOnPhoneSelectsHelperWithoutGrantingPhone() {
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
        assertEquals(
            SetupPermissionKey.SHUTDOWN_HELPER,
            PermissionSetupSteps.nextAfter(steps, SetupPermissionKey.PHONE_APP)?.key
        )
        assertFalse(steps.first { it.key == SetupPermissionKey.PHONE_APP }.done)
    }

    @Test fun nextButtonFromHomeGoesToPhoneBeforeHelper() {
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
            SetupPermissionKey.PHONE_APP,
            PermissionSetupSteps.nextAfter(steps, SetupPermissionKey.HOME_APP)?.key
        )
    }

    @Test fun lastUnapprovedHelperHasNoFollowingNextButton() {
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

    @Test fun restartOnlyEligibleWhenAllAndroidStepsApproved() {
        val steps = PermissionSetupSteps.build(
            phonePermissionGranted = true,
            needsNotificationPermission = true,
            notificationGranted = true,
            helperRequired = true,
            helperEnabled = true,
            phoneRoleHeld = true,
            homeRequired = true,
            homeRoleHeld = true
        )
        assertTrue(PermissionSetupSteps.allApproved(steps))
        assertEquals(5, steps.count { it.done })
    }

    @Test fun incompleteHelperCannotTriggerSetupCompletion() {
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
        assertFalse(PermissionSetupSteps.allApproved(steps))
        assertEquals(SetupPermissionKey.SHUTDOWN_HELPER, PermissionSetupSteps.next(steps)?.key)
    }

}
