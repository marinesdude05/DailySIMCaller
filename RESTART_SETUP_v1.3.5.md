# Daily SIM Caller v1.3.5 - permission and default-switch update

## What changed

1. The guided setup still opens Phone and Notifications permission prompts one at a time.
2. If Android rejects the request to make Daily SIM Caller the default Phone app, the app opens Android's **Default apps** settings screen instead of repeating the rejected prompt. In Default apps, select **Phone app** and then choose Daily SIM Caller. The app checks the actual Phone role when you return.
3. If Android rejects the default Home role request, the app opens **Home selection** settings. Choose Daily SIM Caller as Home and return. If the device has no dedicated Home settings page, the app falls back to Default apps or App info.
4. After a denied role or unanswered settings screen, the guided flow stops; tapping **Automatically Open Missing Permission Screens** retries Settings rather than repeating the denied chooser. Separate buttons also open Phone and Home settings directly.
5. The two switches **Automatically power off when queue finishes** and **Auto-start authorized calls after reboot and unlock** are set to **ON once** when upgrading to this owner-requested v1.3.5. Any OFF selection made *after* that migration remains OFF. This changes saved preferences; use on the owner's test phone only until validated.
6. A first launch of v1.3.5 clears any old pending boot sequence and starts guided setup. No calls start during installation or permission setup. A new reboot is required before automatic calling can run.

## Important Android limitations

Android must approve the Phone and Home roles, and the user must approve runtime and Accessibility permissions. Apps cannot select themselves as default or grant Accessibility access. Some Android builds do not expose every Settings screen. The app opens the closest supported alternative and shows where to tap. If Daily SIM Caller does not appear as an option under Phone app, its role eligibility or device firmware must be investigated, not bypassed. This source update does not guarantee the original role rejection is resolved.

The Home app role changes the device's launcher. Automatic calling is attempted only after a *future reboot* when the user has unlocked the phone, Daily SIM Caller is visible as the selected Home app, the required permissions and Phone role are held, and the authorized phone list is successfully refreshed. One boot triggers at most one sequence. The optional power-off helper still needs the user to approve Accessibility, and physical shutdown may be device-specific.

## Rebuild and install

1. On a trusted Windows computer, extract the source ZIP and open the `DailySIMCaller` project in Android Studio.
2. Run a Gradle sync, then build a signed release APK with the SAME ORIGINAL signing key used by v1.3.4. Keep the key and its password private. The package remains `com.example.dailysimcaller`, versionCode is 9.
3. Do not uninstall v1.3.4; install the signed v1.3.5 APK as an update. If Android reports a signature conflict, stop and check signing rather than uninstalling.
4. Open the new app. Confirm both requested switches display ON. The guided setup should open Phone and Notifications permissions, then the default Phone and Home role choosers/settings, and Accessibility settings for shutdown. On Android's Default apps screen you may need to tap **Phone app** yourself.
5. If a role prompt is declined, verify the app opens the corresponding settings page once; it must not repeatedly show the denied prompt. On returning without choosing Daily SIM Caller, it should stop with an explanatory status and allow retry.
6. Confirm **Shutdown helper: Connected** before testing full power-off. The switch being ON alone does not enable Android Accessibility.
7. Use ONE owned/authorized test number, not the full live list. Test manual placing/ending and then one *separate* post-reboot attempt on the dedicated test phone. Verify the app was selected as both default Phone and Home, was unlocked, and downloaded a current authorized list. Confirm whether the phone fully powered off after a naturally completed queue. Do not assume success from a successful APK installation.
8. After testing, restore the normal Home app from Android Settings > Apps > Default apps > Home app if desired, or turn OFF automatic calling in Daily SIM Caller. Avoid distributing v1.3.5 broadly until behavior is verified.

## Current verification

The included `tools/verify_v135_static.py` performs source-only checks, not a Gradle build. This environment has not compiled or signed this update and has not tested the Android 16 C9 Prime device. Install only a locally built, signed APK after signing and checking the intended version.
