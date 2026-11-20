# v1.3.1 speaker and status update — Cloud Mobile C9 test

## Release steps

1. Open the project root in Android Studio and verify `app/build.gradle.kts` has versionCode `5`, versionName `1.3.1-speaker-status`, and release optimization `enable = false`.
2. Synchronize and build a signed **release** APK using the same keystore and alias as the installed v1.3 release. Do not share passwords or signing keys.
3. Install over the v1.3 app on the dedicated test C9. Preserve app data and existing authorized test numbers.
4. Do not replace HBKInstall26 until a controlled phone test passes.

## Acceptance checks

- Open app: the **Call status** section and queue state are visible immediately under the title. With no call active, the speaker button is disabled.
- Confirm Daily SIM Caller remains the default Phone app and the default Home app only where intended.
- Use a small set of consented / authorized test numbers and start a single test call. Observe the button become active when Telecom reports the call.
- During the call, confirm speakerphone becomes ON automatically (and actually plays over loudspeaker).
- Tap **Speaker ON - Turn OFF**: confirm audio moves to the earpiece / supported alternative, and the UI changes to OFF.
- Tap **Speaker OFF - Turn ON**: confirm loudspeaker returns.
- End the call and initiate another; speaker should default back ON for the next call.
- Confirm the queue progress and Stop button still work, and Google Sheets refresh, call duration, pause, and reboot-one-shot startup behavior did not regress.
- Test with wired / Bluetooth audio only after basic earpiece/loudspeaker behavior works. Telecom may reject unavailable routes.

## Limitations

Routing relies on the device's Android Telecom and InCallService behavior; a successful Gradle build does not prove that speaker switching works on the Cloud Mobile C9 firmware. If routing is unavailable or denied, the UI shows a message rather than claiming the switch succeeded. The Android 14+ path uses Telecom's advertised CallEndpoints, while older systems use CallAudioState routes.
