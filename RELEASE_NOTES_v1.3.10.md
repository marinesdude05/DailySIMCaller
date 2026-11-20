# Daily SIM Caller v1.3.10 - Accessibility shortcut moved up

Based on v1.3.9 source. The four permission/settings buttons remain directly BEFORE the "Power off after last queued call" section near the top of the app, now in this order:

1. Automatically Open Missing Permission Screens
2. Open App Info if Accessibility is restricted
3. Open Default Phone app Settings
4. Open Default Home app Settings

Only the order of these UI controls was changed. The Accessibility button still opens Android App Info, and the other three keep their previous click handlers. The manual call, removed daily scheduling, shutdown, and reboot calling code were not changed.

Version code: 14; version name: 1.3.10-accessibility-button-first. This is source only, not an APK. Build/sign using the original signing key and install as an update; do not uninstall the old build. The separate failure to call automatically after reboot is still not fixed or verified. Static source checks do not verify Android compilation or device behavior.
