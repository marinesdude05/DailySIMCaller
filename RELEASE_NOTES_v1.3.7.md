# Daily SIM Caller v1.3.7 — Source update and installation notes

## Changes

- Removed **Enable Daily Reminder Only** and **Cancel Daily Reminder** from the screen.
- Replaced **Call Now + Enable Daily 10 AM** with **Call Now**. Pressing Call Now only starts a user-requested queue; it does not schedule a daily reminder.
- Removed the legacy 10 AM alarm receiver, exact alarm permission and alarm scheduling flow.
- Added cleanup of the old alarm and `daily_enabled` preference when the updated app opens, and cleanup on reboot.
- Preserved the opt-in **Auto-start authorized calls after reboot and unlock** feature and quiet boot preparation without rescheduling daily reminders.
- Updated version to **1.3.7**, version code **11**, package `com.example.dailysimcaller`.

## Validation and limitations

Static checks passed (`python tools/verify_v137_static.py`) and the project archive passed ZIP integrity checks. **No Android APK was built or signed in this environment**, and no on-device dialing or restart test was performed.

**The existing reboot auto-call problem reported in v1.3.6 remains unverified.** The switch was ON, the phone was unlocked and Daily SIM Caller was the default Home app, but calls did not automatically start. The scheduling changes above do not fix or explain that independently failing reboot path.

## Build and test

1. Open `DailySIMCaller` in Android Studio, sync Gradle, and compile a signed release APK using the same signing key as your installed v1.3.6.
2. Keep the existing app installed. Install the new APK as an update; do not uninstall first.
3. Open the app once to cancel any leftover 10 AM alarm. Confirm only **Call Now** appears among the manual call/start controls, and test with a small authorized list.
4. Separately investigate and test reboot startup after verifying the automatic-start switch and default Phone/Home roles. Avoid unexpected live calls.
