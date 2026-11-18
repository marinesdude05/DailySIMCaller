# Daily SIM Caller - Repaired Android Studio project

This ZIP contains the user's uploaded Android Studio project, with broken Kotlin source
files repaired. Package/application ID: `com.example.dailysimcaller`.

## What was broken in the uploaded ZIP

- `CallSessionService.kt` consisted of partial methods pasted outside any class and was not compilable.
- `DailyCallReceiver.kt` accidentally contained an extra `MainActivity`, not a receiver.
- `MainActivity.kt` and `PhoneCsv.kt` lacked package declarations.
- AndroidManifest.xml began with a blank line before its XML declaration.
- Manifest entries referenced `InCallActivity`, `CallMonitorService`, and `BootReceiver`, but these files did not exist.
- `MainActivity.kt` referenced missing `DailyAlarm`.

## Repairs included

- Restored package declarations and a single `MainActivity`.
- Implemented daily reminder receiver, boot re-scheduling, and 10:00 AM
  `America/Chicago` alarms.
- Implemented notification-only daily reminder (the alarm never starts a call unattended).
- Added a minimal in-call UI and `InCallService` integration for a user-approved default
  phone app role, plus a user-initiated foreground service for the call queue.
- Retained Google Drive CSV selection via Android's document picker and refresh-on-open/
  before calling (Drive can return cached content; this is NOT push/live sync).
- Retained `Call Now + Enable Daily 10 AM`, stop queue, schedule/cancel reminder, and
  sequential queue with configured duration and pause.
- Added CSV parser test `PhoneCsvTest.kt`.

## Open it without compiling old broken classes

1. Back up the existing `DailySIMCaller` Android Studio folder.
2. Extract this ZIP into a NEW empty folder.
3. In Android Studio select **File > Open** and open the extracted `DailySIMCaller` folder
   (the folder containing `settings.gradle.kts`). Do not overlay or merge only some files
   into the old project because stale Kotlin files can recreate duplicate classes.
4. Let Gradle sync and install the required Android SDK platforms/tools when prompted.
5. Select **Build > Rebuild Project**. If Gradle fails before Kotlin compilation, first
   resolve the Gradle/SDK setup error.
6. If the build succeeds, select the connected Cloud Mobile C9 and click **Run**.

## Safe phone test

- Use one phone number you own or are authorized to call.
- Connect a .csv file containing `phone_number` header and one number per row.
- If trying automatic hangup, **test only on a spare/test device**: changing the default
  phone app affects ALL incoming and outgoing cellular calls, including the ability to
  answer and end them; this is a minimal dialer UI, not a full production dialer.
- Grant requested permissions, then tap `Call Now + Enable Daily 10 AM`.
- Verify start/end/next-number transitions on the physical phone before increasing the
  list; return to the original default Phone app after testing.
- The daily 10 AM alarm displays a reminder and does **not** automatically place calls.
- Android and carrier behaviors can vary; automatic hangup/next-call behavior has
  NOT been confirmed on the Cloud Mobile C9.

## Verification performed in this environment

- Parsed `AndroidManifest.xml` and checked that declared activity/service/receiver
  class files exist.
- Compiled and executed a standalone Kotlin CSV parsing smoke test (passed).
- Kotlin syntax scan found no obvious parser errors, but Android-dependent symbol
  resolution cannot be verified without the Android SDK/Compose dependencies.
- **Not a verified BUILD SUCCESSFUL**: a full Android Gradle build and a device test
  were unavailable in the current environment.
