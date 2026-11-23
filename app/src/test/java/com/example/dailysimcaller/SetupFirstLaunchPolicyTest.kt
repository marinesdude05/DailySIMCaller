package com.example.dailysimcaller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupFirstLaunchPolicyTest {
    @Test fun newStudioBuildOpensSetupWhenPermissionsMissing() {
        assertTrue(SetupFirstLaunchPolicy.shouldBegin(allApproved = false, promptedThisVersion = false))
    }

    @Test fun initialSetupDoesNotPopupWhenAllFiveDone() {
        assertFalse(SetupFirstLaunchPolicy.shouldBegin(allApproved = true, promptedThisVersion = false))
    }

    @Test fun deniedPermissionsDoNotCreateRepeatingPromptLoop() {
        assertFalse(SetupFirstLaunchPolicy.shouldBegin(allApproved = false, promptedThisVersion = true))
    }

    @Test fun previouslyCompletedAndPromptedSetupNeverReopens() {
        assertFalse(SetupFirstLaunchPolicy.shouldBegin(allApproved = true, promptedThisVersion = true))
    }
}
