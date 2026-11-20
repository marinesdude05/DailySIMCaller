# Daily SIM Caller v1.3.11 — shutdown shortcut and restart diagnostics

## Changes from v1.3.10

- Moved **Enable shutdown helper in Accessibility settings** into the top settings-shortcut group, just after the App Info shortcut and before Default Phone and Default Home. The five shortcuts stay above **Power off after last queued call**. The Settings intent is unchanged.
- Investigated the reboot queue and identified a plausible software blocker: the boot sequence required a legacy `guided_setup_complete_v136` preference. That preference was only marked complete after the guided setup passed the optional shutdown Accessibility helper. Consequently, the shutdown helper could block calling even when the user had enabled reboot calling, unlocked, and granted the real Phone/Home roles.
- The reboot sequence now checks that initial setup has been started, the app is visible and unlocked, the user opted in, Phone/Home roles and runtime permissions are held, and an authorized CSV is available. The optional shutdown helper is no longer a requirement for *calling*. Auto-shutdown still requires the Accessibility helper.
- A boot-event status is now persisted and displayed near the restart switch as **Last boot diagnostic**; **Restart readiness** shows which prerequisite is missing. The in-app boot broadcast is counted even during a lifecycle transition so the next visible resume can check the pending flag.
- The pending boot marker is still consumed before any call attempt, preventing duplicate sequences. A failed CSV refresh or service start does not trigger unexpected later retries; use Call Now or reboot for a new attempt.

## Build and test

Android Studio source only; app id `com.example.dailysimcaller`, versionCode 15. Build a **signed release APK** using the same original signing key used to install the current version. Keep the existing APK installed until verification.

On the owner's approved test phone: update; inspect Restart readiness; confirm automatic reboot switch ON and permissions/roles as desired; use a one-number authorized list; reboot; unlock to Home; read **Last boot diagnostic** before making more changes. If it says `No restart signal recorded yet`, inspect whether BOOT_COMPLETED was delivered or the app was force-stopped. If it reports a number-list refresh failure, check network and the configured CSV. If the queue says it started but no calls appear, inspect foreground-service and Telecom logs on device. Do not publish the download to others until verified.

## Limits

Static source checks only; no Android SDK, signed APK, or on-device boot test in this environment. The precise cause on your phone is **not confirmed** without a new boot test and system logs. Android/device policy may still prevent automatic calling.
