# Daily SIM Caller v1.3.4 — restart installation & auto-calling setup

**Status:** Revised Android Studio source only. The code has not been compiled, signed, installed, or tested on the Cloud Mobile C9 Prime. Do not treat the source ZIP as a phone-installable APK.

## What v1.3.4 is intended to do

- **Automatic call sequence after restart and unlock (opt-in):** When you explicitly enable **Auto-start authorized calls after reboot and unlock**, Android's `BOOT_COMPLETED` broadcast marks exactly one pending sequence. The app must have both the default **Home** role (so Android can make the app visible after reboot/unlock) and the default **Phone** role. When the app is visible and the phone is unlocked, it downloads the latest authorized CSV list. Only if the refresh succeeds does it attempt the queue. It does not dial from the boot receiver or a background job.
- **Automatic navigation through permission setup:** The first opening of this release starts a guided setup check. It opens the actual Android permission dialogs or relevant Settings screens for missing Phone, Notifications, and default Phone-role access; it also checks the Home role **when auto-calling is opted into**, the Accessibility helper **only when power-off is enabled**, and exact-alarm access only when the separate reminder is already enabled. After you approve a screen, the app attempts to continue the guide automatically. If you decline, it stops and the guide button lets you retry.
- **No silent permission grants:** Android requires you to personally tap Allow/choose the default app/turn on Accessibility. The app cannot bypass restricted-settings safeguards or click those choices for you.
- **Independent background preparation:** An optional JobScheduler job may prefetch the number list when connectivity allows; it does not dial.
- **Safe migration:** The v1.3.4 auto-call switch requires a *fresh explicit opt-in*, even if an earlier version saved auto-start as enabled. No calls should begin merely by installing the update.

## Step 1 — build and sign on a computer

1. Open the project folder (inside the source ZIP) in Android Studio. Sync Gradle. Install the Android SDK/API level requested by the project if needed; resolve any build errors first.
2. Build a release APK with **versionCode 8**, package `com.example.dailysimcaller`. Sign it using the **same original private signing keystore/certificate** that signed the installed v1.3.2 APK. Keep keystores and passwords private. If you cannot access that signing key, stop: do not uninstall the existing app to bypass signature mismatch, because settings may be lost.
3. Verify the APK signature and package, then test the new build on one dedicated C9 Prime with one authorized test destination. Do not replace the existing Google Drive deployment APK until the pilot passes. The source package itself is not an APK.

## Step 2 — install and approve setup prompts

1. Stop any existing queue in Daily SIM Caller. Confirm the central calling list contains **only a number you own or have authorization to call** for the pilot. Keep the device charged and unlocked.
2. Install the **verified, correctly signed APK update**; choose Update instead of uninstalling. Open the app. Android may show Phone and Notifications prompts. Tap Allow if you approve them.
3. The permission guide opens missing permission screens automatically. Select Daily SIM Caller as the default **Phone app** when Android asks. The guide should advance after you return; if a prompt was declined or the activity was interrupted, use **Automatically Open Missing Permission Screens** to retry.
4. Switch **Auto-start authorized calls after reboot and unlock** **ON** after reviewing the call list and durations. The app will then guide you to choose **Daily SIM Caller as the default Home app** if it is not already selected. **This replaces the phone's normal Home screen until you restore your original launcher.** Android requires your selection.
5. If **Automatically power off when queue finishes** is ON and the shutdown helper is not connected, the guide opens Android Accessibility Settings. Select Daily SIM Caller shutdown helper and enable it yourself. If Android blocks it as a restricted setting for this trusted APK, open the app's **App Info** from the in-app button, use the three-dot menu > **Allow restricted settings**, and return to Accessibility. Android controls this approval.
6. If Alarms & reminders is required for a previously enabled 10 AM reminder, approve it in Android Settings. A reminder is not an additional automatic call queue.

## Step 3 — verify without accidental calls

1. Review the central CSV refresh status, fingerprint, number count, call duration, and pause. Confirm **only the authorized test number** is in the source list. Save settings.
2. Confirm **Daily SIM Caller is the default Home app** and **default Phone app**. Confirm **Auto-start authorized calls after reboot and unlock** is ON. Verify Phone and Notifications permissions are granted. Confirm the shutdown helper only if you want automatic shutdown.
3. Do not tap **Call Now** during configuration. Once ready for the supervised one-number test, **reboot the phone**. Unlock it if required by Android. Android should show Daily SIM Caller as Home; it should refresh the central list and attempt **one** call sequence if all checks succeed. Startup timing cannot be guaranteed across builds or lock screens.
4. Watch the queue. Use **Stop / Cancel Startup Calls** or **Stop Current Queue** if necessary. Check that calls end at the intended interval. If automatic power-off is enabled, verify it only on a controlled test: a fully powered-off phone cannot run another scheduled call until someone or an external power-on mechanism turns it back on.
5. If any permission, Home/Phone role, network refresh or call start fails, the app should show a status instead of using an old list. Do not assume a call ran without observing the device. Restore your original Home launcher via the app's **Restore previous Home app in Android Settings** control if needed.

## Limitations and changes from previous guide

- Android 15/16 restricts launching a phone-call foreground service directly from `BOOT_COMPLETED`, and normal apps cannot start unattended calls from a completely locked/powered-off phone on demand. This build instead relies on the user-selected Home role and a visible, unlocked Activity. Some phone/OS configurations can still delay or block startup.
- Permissions cannot be granted automatically; only the navigation and verification can be automated.
- Calls after reboot require explicit opt-in and must use a valid, authorized list refreshed successfully. This is not a guarantee of fully unattended operation on every device.
- Version 1.3.3's **manual-only calls** plan is superseded by this v1.3.4 plan. No signed APK is provided in this source archive.
