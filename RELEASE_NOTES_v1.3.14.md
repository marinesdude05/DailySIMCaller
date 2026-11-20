# Daily SIM Caller v1.3.14 - Setup refinements

Built from v1.3.13 source, not an earlier version. Android version code 18.

## Main screen
- Keep the short **Help Me Set Up** button directly above **Fix Permissions**. Neither ever displays Opened or Done and neither is dimmed after use.
- **Fix Permissions** opens this app's Android App info via the supported public settings intent. Android still requires the owner to open the three-dot menu and choose Allow restricted settings if available. The app cannot pre-open, approve, or bypass that security control.
- **Set Phone App** and **Set Home App** preserve checked Done only when Android confirms the roles. A shortcut that was opened but not completed may show Opened.
- Rename **Set Power Access** to **Shut Down Helper** with its existing Accessibility shortcut and checked Done indicator when Android reports the service is enabled. On a restart, read Android's persistent Accessibility service setting rather than relying only on whether the helper has reconnected.
- Move **Stop Current Queue** under **For Admin Only**, immediately after expansion. Call Now remains a prominent main button.
- Preserve **Power Menu** as the final MAIN action, red with white text. It opens Android's system power dialog without directly requesting shutdown.
- Apply Android safe-drawing inset padding so the bottom navigation bar does not overlap the Power Menu or other content; the screen remains scrollable.

## Preserved from v1.3.13
- Calling and phone-number configuration, manual/automatic queued calling, alarm cleanup, default Phone/Home role handling.
- Auto-call after restart, background number preparation and diagnostics, optional power-off after queue, no-PIN swipe lock dismissal request, screen kept awake in main and in-call activities.
- **For Admin Only** stays a collapsible visibility control, not a password or security barrier.

## Verification
- Static assertions confirm ordering and single instances of all key buttons, no Opened/Done labels for setup shortcuts, Android role and helper refresh, inset handling, version code increment, and unchanged calling/boot/shutdown modules.
- ZIP archive integrity, source readback and file hashes verified.
- No Android SDK/Gradle build toolchain was available in this runtime. Source not compiled into an APK or signed; device testing remains necessary.

## Device checks before distributing
1. Use the original signing identity when building the release APK in Android Studio and install as an update over v1.3.13.
2. Confirm Fix Permissions opens Daily SIM Caller App info; manually choose Allow restricted settings only if offered and you trust the app.
3. Confirm Set Phone App, Set Home App and Shut Down Helper display Done after reboot when Android settings stay approved.
4. Confirm the purple Call Now control and red Power Menu are fully visible and the lower navigation bar no longer overlaps them.
5. Verify Stop Current Queue appears only after expanding For Admin Only; verify accessibility, manual calls, authorized call queue, reboot and shutdown on a test phone.
