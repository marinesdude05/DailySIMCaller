# Boot auto-start and call sequence (v1.3)

## Behavior

- **ON by default for new and upgraded installs**, via `auto_start_enabled` default `true`. Toggle OFF on the main screen at any time. Existing OFF preference is honored.
- To **appear on screen** after power-on, Android must be configured to use **Daily SIM Caller as the default Home app**. This replaces the usual launcher Home screen while selected. Choose it from the app's **Make Daily SIM Caller the Home App** button, or Android Settings > Apps > Default apps > Home app.
- Separately select it as **default Phone app**, and grant CALL_PHONE and notification access using the in-app buttons. Reboot after setup.
- On **BOOT_COMPLETED**, the receiver saves one pending attempt and restores any 10 AM reminder. It **never** directly starts a foreground service or an activity from the background (restricted on modern Android).
- On the **first visible, unlocked Home activity** after reboot, it consumes that pending attempt; then it downloads the current Google Sheets CSV **before** placing any calls. If the download fails, **no calls** occur and no saved stale numbers are used.
- Calls follow the existing 45-second connected-call duration and 10-second pause defaults (or saved values), and **Stop Current Queue** stops the sequence.
- **Only once per boot**. Tapping Home, reopening the app, or returning from a phone call does not auto-redial.
- The original 10:00 AM Central reminder is a **separate** feature. Enabling boot calls does not turn the 10 AM notification into automated calls.

## Important Android limitations

- Selecting the app as **default Home is a required user action**; an APK cannot silently take over the system's Home app.
- Android can prevent boot broadcasts after Force Stop, before first unlock, or under OEM startup/battery restrictions. Test on the actual Cloud Mobile C9. If Home doesn't open at boot, check its default Home setting and OEM battery/autostart restrictions.
- No call begins if the device is locked, necessary roles or runtime permissions are unavailable, or Google Sheet refresh fails. Users can fix those conditions on the main screen.
- Automatic calling may incur normal carrier charges. Ensure every number and recipient is authorized before enabling fleet-wide.

## Release process

1. Import the project into Android Studio, wait for Gradle sync, run **Build**. Gradle optimization is deliberately **disabled** for release due to the previously diagnosed `IllegalAccessError` crash.
2. Generate a **signed release APK using the existing release keystore**. Do not create or share a new keystore. `versionCode = 4` supports updating the release-signed v1.2 app in place.
3. Install on the spare C9, select **default Home** AND **default Phone**, grant permissions, and reboot. After unlock, watch the screen, number refresh, first call, call timeout, and Stop button. Test with authorized test numbers before any team rollout.
4. Do **not** update the Drive APK behind HBKInstall26 until you have tested the new release build on the C9. Source ZIP is not an installable APK.
