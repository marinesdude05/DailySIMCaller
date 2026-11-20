**Historical reference only; superseded by RESTART_SETUP_v1.3.4.md.**

# Daily SIM Caller v1.3.3 — restart setup from step 1

**Status: source changes prepared; not built, signed, installed, or tested on the C9 Prime.**

## What's changed

- The new boot receiver **never starts calls or a call foreground service** and **does not launch the app on screen**.
- Android JobScheduler schedules a network-dependent job after restart to refresh the Google Sheets CSV into the app's existing local cache. Jobs may be delayed or blocked by battery/network restrictions or Force Stop.
- Default Home app intent and Home-app setup controls have been removed. Your standard Home screen remains unchanged.
- The previous automatic calling switch and stored pending boot-call marker are retired. Calling remains **manual only** via Call Now or Dial Manually.
- A guided setup control checks regular Phone/Notification permissions, then requests default Phone role, then opens Accessibility Settings for the optional shutdown helper. The first launch attempts ordinary runtime permission requests once.
- Android requires the owner to approve permissions, default Phone role, and Accessibility manually. For restricted settings, the user must use the three-dot App info menu; it cannot be bypassed from the app.
- The optional daily 10 AM reminder stays a **reminder**, not an automatic calling sequence.

## Build and signing — do this before installing

1. Open this source folder in Android Studio; allow Gradle sync, confirm JDK and Android SDK 37 are installed.
2. Build the project; fix any reported build errors before distributing. **No Android SDK/Gradle build was available in the authoring environment, so the changed source was not compiled here.**
3. Create a *signed* APK with the **same original keystore and alias** used for the installed v1.3.2 release. Keep the keystore and password private.
4. Confirm the APK package ID is `com.example.dailysimcaller`, `versionCode=7`, and it is signed with the certificate matching the installed APK. Do not uninstall the existing app to work around a signature mismatch: that may erase its settings.
5. Test on one spare Cloud Mobile C9 Prime and a permitted test number before uploading a new version to the existing deployment link.

## Fresh phone procedure

1. Stop any active calls in v1.3.2. Keep the phone charged and unlocked; back up any settings you care about.
2. Update the app using the **signed** v1.3.3 APK. This source ZIP is **not** installable on a phone.
3. Open Daily SIM Caller. Android should show any missing Phone or Notification permission prompts; tap Allow only if you trust this app.
4. Tap **Start / Continue Guided Permission Setup**. If required, approve the **default Phone app** role. This is still needed for reliable hangups; it is not the Home role.
5. Tap **Continue** again after returning to the app; if the shutdown helper is not connected, the app opens Android **Accessibility settings**. Manually enable the Daily SIM Caller shutdown helper. If blocked, open **App info** from the in-app button, choose **Allow restricted settings** via its three-dot menu, then return to Accessibility. Understand that Accessibility can observe and interact with the screen.
6. Tap **Continue** again until the app reports checks passed; if daily reminders are already enabled and Android asks, approve Alarms & reminders.
7. Verify **Prepare number list quietly after restart** is ON and **Last background preparation** has a status. Do not choose Daily SIM Caller as the Home app. **No calls will start automatically after reboot.**
8. Tap **Refresh Phone Numbers Now**; confirm the Google Sheets fingerprint and *authorized* test phone-number count.
9. Verify call duration, pause, shutdown helper connection and automatic shutdown preference, then **Save Settings**. Do not tap Call Now until you're ready for a supervised, authorized test.
10. Reboot, unlock, and wait for Android to schedule a network-enabled background job. Open the app and check **Last background preparation**. The job may not have run yet depending on network/battery restrictions. Verify normal Home screen is unchanged.
11. Only for an approved test, put a single permitted number in the list, tap **Call Now**, check hangup and shutdown behavior, and retain an easy way to stop the queue.
12. After successful signed installation and device testing, update the deployed Drive APK in place; do not replace the existing release preemptively.

## Known limitations

- Background preparation is **best-effort** and is not a constantly running process. Android may defer it until device/network conditions permit.
- The requested unattended startup does **not** mean unattended call placement. Automatic dialing at boot is intentionally disabled.
- The source is uncompiled and untested; the installed v1.3.2 release has not been touched.
