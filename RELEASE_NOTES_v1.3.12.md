# Daily SIM Caller v1.3.12 - Simple Setup

This update is based on the **v1.3.11 Shutdown and Restart Diagnostics source**, not an older v1.3.8 or v1.3.9 project.

## Screen changes

- Short setup labels: **Help Me Set Up**, **Set Phone App**, **Set Home App**, **Power Menu**, **Fix Permissions**, **Set Power Access**.
- **Power Menu** sits immediately under **Set Home App**. It only opens Android's power menu for a test; it does **not** turn off the phone.
- Android-verified default Phone, default Home, and shutdown Accessibility helper permissions show **checkmark Done**; previously opened settings show **Opened** in muted colors, without a checkmark. Opened buttons remain tappable to retry.
- Removes the repetitive speakerphone explanation; shows the live speaker message only during a call.
- Trims verbose explanatory text, keeps important warnings, and moves restart diagnostics to **More Details**.
- Keeps **Call Now**, manual dialing, call duration, wait interval, Google Sheet/CSV support, and settings.

## Preserved behavior

- No change to CallQueue, BootReceiver, StartupMode, BootPreparation, ShutdownPowerHelper, or phone-call services.
- Retains existing opted-in calling after reboot/unlock and optional power off after the last completed queue call.
- Does not reinstate daily scheduling/reminder controls.
- Android permissions are never self-granted. **Opened** is not the same as **Done**.

## Version and build

- Package: `com.example.dailysimcaller` (unchanged).
- Version name: `1.3.12-simple-setup`; version code: **16**, advancing from v1.3.11 code 15.
- Android Studio source only. Build and sign a release APK using the same original key as the installed version.
- Do not uninstall the currently working APK. Install over it only after a signed, compatible upgrade is built.
- Source-level checks passed; no Android SDK compilation, device testing, reboot test, or signed APK generated here.

## Safe verification on the device

1. Open the app and confirm the six top shortcuts appear in the order above.
2. Confirm Phone/Home settings show checkmarks only after Android grants them; opening App info without approval must say **Opened**.
3. Confirm Power Menu opens the menu **without** selecting Power off.
4. Check manual Call Now on an authorized test number, then reboot/unlock and verify the existing auto-call flow.
5. Test optional shutdown only if the owner has explicitly enabled it, and with awareness the phone will not make future calls until powered on.
