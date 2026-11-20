# Daily SIM Caller v1.3.9 - Permission Buttons Above Power-Off

Based on v1.3.8. The four existing controls were moved directly BEFORE the Power off after last queued call section near the top:

1. Automatically Open Missing Permission Screens
2. Open Default Phone app Settings
3. Open Default Home app Settings
4. Open App Info if Accessibility is restricted

Only button placement changed; click handlers are untouched. Call Now, daily reminder removal, shutdown, and reboot-calling behavior retain their previous code. The existing reboot auto-call problem has not been fixed or tested by this layout update.

Version code: 13; version name: 1.3.9-permissions-top. Build a signed release in Android Studio using the same original signing key. Install as an update, keeping the current version installed until verified. Source checks are not device testing.
