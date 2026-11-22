# Daily SIM Caller v1.3.20 - Permission Checklist (source candidate)

Based on v1.3.19 auto-retry and the owner's November 22, 2026 setup-screen recording, which demonstrates a dark settings list with short descriptions and green success/orange warning symbols.

## New layout
- A dark **Setup** section displays a clickable permission checklist with a status icon for each of the app's relevant settings.
- **Make Calls:** Phone Permission, Notifications, and Set Phone App.
- **After Restart / Shut Down:** Set Home App, Battery Settings (optional), Fix Permissions, and Shut Down Helper.
- **Shut Down Helper** remains directly above the red **Power Menu** button. The four-step **Help Me Set Up** guide and **Check Helper Next** wording are kept.
- A **green check** means the app can verify the Android permission/default role/helper is enabled; an **orange exclamation mark** means it is not confirmed. **Fix Permissions** is a neutral shortcut; Android exposes no reliable permission API confirming that the user selected "Allow restricted settings."
- Opening settings never automatically grants access. If Android does not support a direct settings intent, the app provides fallback guidance.
- The screen shows a short, understandable message for routine connection failures rather than a wall of stacktrace/error text.

The reference video's unrelated AnyDesk features (files, microphone, overlays, plugins, chat) were **not** added. Daily SIM Caller does not need those permissions for its existing functions.

## Preserved features
- v1.3.19 automatic Google Sheets reconnection and cancel, fresh-list-only calling, restart calling, four-step guide, connected-call timer, speaker controls, after-call power-off and admin settings.
- No change to CallQueue, BootReceiver, ShutdownPowerHelper, StartupMode or remote CSV implementation.

## Build and verification
- Package unchanged: `com.example.dailysimcaller`.
- `versionCode = 24`, `versionName = 1.3.20-permission-checklist`.
- Static source checks and source ZIP integrity performed. **No signed APK or C9 device test** performed for this version.
- GitHub Actions currently fails on Android SDK setup, before compilation. Use Android Studio with the installed SDK and the **original protected signing key** to build an update. Do not uninstall the working app to bypass signer mismatch.
- **HBKInstall26 remains on the previously approved APK** until installation/restart/call tests pass.
