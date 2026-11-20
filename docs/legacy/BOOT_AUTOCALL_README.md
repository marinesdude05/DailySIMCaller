# Daily SIM Caller: automatic startup and calling (v1.3.0)

## What this update does

- Adds an **auto-start** switch: "Auto-open and call after restart". It is ON initially and can be switched off.
- The Android **Home app** role makes Daily SIM Caller the screen that appears when you unlock after restarting the phone. Merely having BOOT_COMPLETED permission cannot force an app window onto the screen on newer Android versions.
- When enabled, BOOT_COMPLETED marks exactly one pending call sequence. The main screen, when visible and unlocked, checks this flag, consumes it, downloads a fresh phone list from the existing Google Sheets HTTPS endpoint, and starts the existing call queue.
- **The boot receiver never places calls**. If Google Sheets cannot be refreshed, permissions are missing, or the phone is not the default Home/Phone app, the app does not start calls.
- Calls are not restarted by pressing Home or reopening the app. They start once after each reboot only while the opt-in switch is on.
- The 10:00 AM reminder is unchanged (it is still a reminder, not an automatic second sequence).
- Keeps the proven release fix `optimization.enable = false`.
- Version code is 4 (upgrade from working version code 3), so Android can install the new release over the old one with the **same existing signing key**.

## One-time setup on the test phone

1. Build a signed **release APK** in Android Studio using the ORIGINAL `.jks` signing key that signed your working 1.2.0 release. **Never upload the keystore or its password.**
2. Install the new signed APK on the Cloud Mobile C9 (do NOT publish to HBKInstall26 yet).
3. Open Daily SIM Caller. Tap **Set as Default Phone App** if necessary and grant CALL_PHONE permission.
4. Tap **Set as Default Home App** and approve Android's Home role prompt. This replaces the system launcher/Home screen until changed back in Settings. You can restore your previous Home app from **Manage / restore Home app**.
5. Confirm **Auto-open and call after restart** is ON (the default). Verify the list, call length (45 sec default) and pause (10 sec default), then tap **Save Settings**.
6. Reboot the phone. Unlock it. Daily SIM Caller should be visible as Home and should refresh the Google Sheet before starting the phone sequence. Android may require additional manufacturer-specific setup, such as disabling battery restrictions.
7. Confirm calls initiate, pause and disconnect properly. Test with permitted test numbers, preferably outside off-hours. Test the **Stop Current Queue** button.
8. If anything fails, turn OFF the boot switch and restore the original Home app under Android Settings > Default apps > Home app.
9. Only after the signed release is proven on the test device, update the existing Google Drive APK using **Manage versions > Upload new version**, preserving the HBKInstall26 link. Employees who have the working 1.2.0 release should be able to update in place if the same signing key is used.

### Android limitations

Automatic visible startup requires the user to select Daily SIM Caller as the default Home app. Android controls startup and lockscreen behavior; no ordinary APK can guarantee an app opens above the lockscreen and places calls immediately upon electrical power-on. Calls start only when unlocked, visible, authorized as the default Phone app, and network-sync succeeds. Calling functionality depends on device, carrier, SIM connectivity, and Android Telecom behavior.
