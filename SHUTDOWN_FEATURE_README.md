# Daily SIM Caller v1.3.2 — Android 16 experimental power-off

This is a **source project**, not a compiled or signed APK. Build and sign it with the same keystore as v1.3.1, only after review. Automatic shutdown is **OFF by default**.

## What changed

- Existing call queue and speakerphone controls are retained.
- Added a user-controlled **Automatically power off when queue finishes** switch.
- Added a separately enabled Android Accessibility Service named **Daily SIM Caller shutdown helper**.
- Added **Test power menu (will NOT shut down)** to open the system power menu safely before testing the actual shutdown sequence.
- On normal queue completion *only*, after the last call is removed, the helper waits three seconds, checks that no other call/session is active, opens the system power dialog, and attempts an exact-match **Power off** click. It may need a second exact-match confirmation on some devices. It does not click Emergency, Restart, or unrelated controls.
- An error, manual stop, or missing Accessibility Service does **not** initiate shutdown.

## Important limitations

- **Not guaranteed to power off** a stock Android 16 Cloud Mobile C9 Prime. The OEM power menu may not expose an actionable accessibility node; Android may require a confirmation that differs from the expected label.
- Requires **explicit** enablement by the phone owner in Android Settings > Accessibility, plus the opt-in switch in the app. On a sideloaded app, Android may impose additional security restrictions.
- A powered-off phone cannot autonomously start the next day's calls without separate hardware/firmware support.
- This project **reads Google Sheets as a CSV phone-number list**. It does **not upload call results to Sheets**; no such confirmation is implemented. Do not treat power-off as evidence of a successful Sheets write.
- The accessibility service receives window events and can inspect the active window for the narrowly scoped power-off action. Enable it only on a dedicated phone you trust.

## Testing plan

1. Before enabling automatic power-off, perform a **Build > Assemble 'app' Run Configuration** in Android Studio. Fix any errors first.
2. Sign a release APK with the same signing key and install as an update. No key is included in this archive.
3. In the app, open Accessibility settings and enable **Daily SIM Caller shutdown helper**. Check status **Connected** after returning.
4. Leave auto power-off switch OFF and tap **Test power menu**. Verify Android shows Emergency / Power off / Restart. Press Back to dismiss. The helper **never taps Power off in this test**.
5. When ready, enable **Automatically power off when queue finishes**. Test with **one number you own** and ensure no important calls are in progress. Fully charging the phone and backing up are advised.
6. Observe whether the phone powers down after the call. If it fails, capture a screenshot of the system menu so the text/action can be adapted. Do not enable unattended production calling until independently verified.

## Packaging

This source archive intentionally excludes APKs and generated caches to avoid confusion between v1.3.1 and v1.3.2. It has **not** been compiled in this environment (Android SDK unavailable). The version is bumped to versionCode 6 so the v1.3.1 APK (versionCode 5) can be updated in place.
