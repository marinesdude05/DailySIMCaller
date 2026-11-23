# Managed Home and Phone toggles (v1.3.19 test feature)

The **For Admin Only** panel shows the real Android default Home and Phone app status. The switches are not independent preferences.

- **Fully managed devices where this package is the device owner:** ON requests Home/Phone default changes using Android DevicePolicyManager. OFF removes the app Home override (and attempts to restore the last launcher) or selects the saved previous/system dialer, respectively. The UI re-reads actual Android roles.
- **Ordinary sideloaded installations:** Android disallows silently changing the default Home or Phone apps. Tapping the switch opens the appropriate Android selection/confirmation screen instead; the switch does not falsely show the requested state.
- **Requirements:** Phone management needs Android 14/API 34+. Home management uses persistent preferred activity policy. Device owner is a provisioning role, not something a normal toggle can grant. Merely enabling an Accessibility service or legacy device administrator is not equivalent to device owner.
- **Caution:** Device-owner provisioning has security and deployment implications; do not factory-reset or enroll a working phone just to test this feature. Device-owner enrollment and restoration of defaults need IT-supervised spare-device testing.
- **Verification:** Compile/unit tests do not establish that either policy is effective on a Cloud Mobile C9, or that restoration works with the device-specific launcher/dialer. Validate one isolated test phone before fleet deployment.
- **Source:** Based on v1.3.19 AutoRetry/UpdatedButtons. The existing approved APK and default app settings are not changed by updating source code.
