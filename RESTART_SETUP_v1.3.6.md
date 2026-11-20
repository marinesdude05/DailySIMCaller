# Daily SIM Caller v1.3.6 — role-recovery update

## What changed

- **Denied default Phone role:** A refused Android role request now opens this app's **App info**, not another copy of the blocked chooser. If the device offers an **Allow restricted settings** menu item, only the phone's owner can select it. You can then tap **Retry Phone app role**. This app does not and cannot grant its own roles or bypass Android policy.
- **Permission order:** The guided flow now requests Phone/Notifications, Home (when auto-start is enabled), and the optional shutdown Accessibility service before the default Phone role. One refused Phone-role request no longer prevents the other independent steps from being presented.
- **Shutdown Helper:** Status distinguishes **Connected**, **Enabled in Android; waiting for service connection**, and **Not enabled in Android Accessibility settings**. Automatic shutdown still requires a genuinely connected service and may be blocked by device confirmation dialogs.
- **Background preparation:** A quiet network-refresh job is also scheduled when the updated app opens, not only on reboot. Its status distinguishes **Scheduled** from **Finished**; a **Refresh background preparation status** button rereads the last result. This job never initiates calls.
- **Reboot calls:** The existing opt-in Home app/reboot/unlock workflow remains. **No calls run during installation or guided setup.** Calls remain blocked unless the Phone and Home roles are actually granted and other readiness checks pass.

## Build the update

1. In Android Studio, open the `DailySIMCaller` project folder within this ZIP.
2. Let Gradle sync, then run `python tools/verify_v136_static.py` for source-only checks (optional).
3. Use **Build > Generate Signed App Bundle / APK > APK**, and select the **same signing key** used for the app currently installed on your phone. This is required for an in-place update.
4. Choose the **release** variant and build the APK. **Version code is 10** and version name is **1.3.6-guided-role-recovery**.
5. Do not uninstall the current app. Save a backup of its configuration before updating if possible. Verify the resulting APK signature and version before installing.

## On-phone verification — no live calls until requested

1. Update the existing app with the signed APK. Do not remove the old version first.
2. The guided setup opens missing permission screens in sequence. Approve only the ones you want.
3. If Android says **App was denied access to be default Phone app**, the app opens **App info** for Daily SIM Caller. On that page, examine the top-right menu for **Allow restricted settings** **if available**. That option may not exist on all Android builds; do not assume it does. After using it, return to the app and tap **Retry Phone app role**. You can also open **Android Default Phone app settings** from within the app.
4. If the role remains blocked, do not repeatedly tap the denied system prompt. The phone's security policy, OS version, installation source, or dialer eligibility must be diagnosed on-device; a code change cannot promise to lift an Android denial. Leave the normal Phone app as default until solved.
5. Verify the **Home app** role only if you want reboot auto-calling. Verify **Shutdown helper: Connected** if you want automatic power-off.
6. Tap **Refresh background preparation status** after a network-connected interval. **Scheduled** means queued, not completed.
7. Only after role and shutdown checks succeed, deliberately test a permitted call and then a reboot/unlock with an approved small test list. Avoid unplanned calls during setup.

## Verification status

Source/static checks: passed. Android APK compilation, signing, installation, role grant, live calls, reboot, and physical shutdown: **not tested here**. This environment lacks the Android SDK and cannot download its Gradle distribution.
