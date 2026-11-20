# Daily SIM Caller v1.3.13 — Red Power Menu & Collapsible Admin Controls

Source based directly on the v1.3.12 Simple Setup project. Version code increased from 16 to 17.

## Changes
- **Power Menu** is the last main action button, after **Call Now** and **Stop Current Queue**. It has a red background and white text; it still only opens the Android power menu and does not automatically shut down the phone.
- Added a **For Admin Only** switch immediately below the Power Menu button. It starts OFF on a fresh screen, hiding all operational settings below the main buttons. Turning it ON reveals those controls; turning it OFF hides them again.
- Moved **Call Now** and **Stop Current Queue** into the visible main controls so the person using the app can still place or stop a queued call without opening the admin section.
- Inside **For Admin Only**: auto-power-off, auto-call after restart, Home recovery, restart preparation, restart diagnostics, CSV list and manual number configuration, timing settings, role retry, manual dial, and related details. Existing behaviors are not modified.
- Existing short labels and Android-verified **✓ Done** permission feedback retained.
- **Keep screen awake:** `FLAG_KEEP_SCREEN_ON` in MainActivity and InCallActivity prevents normal display sleep while those screens are foreground. The phone's global timeout preference is not altered; power saving/owner screen-off can still take precedence.
- **Unsecured startup:** On devices without a secure PIN/pattern/password/SIM keyguard, the Home activity can show over the simple swipe lock and request dismissal with `KeyguardManager.requestDismissKeyguard`. The restart-call readiness check is re-evaluated after successful dismissal. Secure keyguards are not bypassed. Automatic startup is still subject to the device launching the configured default Home app and Android/OEM boot policies.

**Important:** “For Admin Only” is a visibility toggle, **not password protection**. Anyone using the phone can expand it. Do not rely on it as an authorization boundary.

## Validation
- Static source checks verify button ordering, one instance each of the key actions, collapsed-by-default state, and that calling, boot receiver, and shutdown modules remain unchanged.
- Source packaging checked for ZIP integrity.
- Android APK compilation/signing and device behavior have not been verified. Build and sign using the appropriate Android Studio SDK and original signing key, then test calls, reboot, and power-menu behavior on the device.
