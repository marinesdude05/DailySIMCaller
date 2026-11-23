package com.example.dailysimcaller

/**
 * A remembered "setup started" flag does not imply Android permissions remain
 * approved after reinstalling or upgrading an Android Studio build.
 * Offer guided setup once on the first v42 launch if checks remain incomplete.
 * Never auto-loop Settings after a refusal.
 */
internal object SetupFirstLaunchPolicy {
    const val V42_PROMPTED = "guided_setup_first_launch_v42_prompted"

    fun shouldBegin(allApproved: Boolean, promptedThisVersion: Boolean): Boolean =
        !allApproved && !promptedThisVersion
}
