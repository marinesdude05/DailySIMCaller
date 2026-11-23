package com.example.dailysimcaller

/**
 * Setup completion is based on Android-confirmed permissions and roles.
 * The attempted flag is persisted before asking Android to reboot so reopening
 * the Home activity after a reboot cannot cause an endless restart loop.
 */
internal object SetupRestartPolicy {
    const val ATTEMPTED_PREF = "setup_auto_restart_v40_attempted"

    fun shouldRequest(
        allApproved: Boolean,
        foreground: Boolean,
        alreadyAttempted: Boolean,
        busyWithCall: Boolean
    ): Boolean = allApproved && foreground && !alreadyAttempted && !busyWithCall
}
