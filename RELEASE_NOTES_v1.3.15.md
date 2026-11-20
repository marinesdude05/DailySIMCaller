# Daily SIM Caller v1.3.15 - Restricted Settings Guide

Based on v1.3.14 (versionCode 18); this source uses versionCode **19**.

## Changes
- **Help Me Set Up** now presents a three-step manual helper guide when Android has not approved the Shut Down Helper Accessibility service:
  1. Open Accessibility and attempt to enable Shut Down Helper; dismiss Android's denial warning if shown.
  2. Open the app's Android App Info screen and, **if Android offers the option**, use More (three dots) > Allow restricted settings, subject to Android's user approval. If the menu is absent, return to Step 1.
  3. Open Accessibility again, enable Shut Down Helper, return to the app and tap Check Helper.
- The helper guide cannot approve, disable, or bypass Android restricted settings. Users must choose to approve the sensitive Accessibility service themselves and should do so only for software they trust.
- Guide can be closed at any time. Existing `Fix Permissions` remains a direct App Info shortcut. Neither shortcut displays Opened/Done.
- Once helper approval is detected, Help Me Set Up continues the existing Phone/Home/notification setup flow. It does not place calls just because setup is opened.

## Preserved
- Call Now, queue, boot startup, permissions confirmation, auto-shutdown, stay-awake, red Power Menu, and For Admin Only layout remain unchanged from v1.3.14.

## Build and verification
- Source syntax and static assertions passed; the ZIP archive was tested.
- Android Gradle compilation and signed-APK generation are not available in this environment. This ZIP is an **Android Studio source project**, not an APK. Build a signed release using the same original signing certificate to update existing installations.

## Device test checklist
1. Tap Help Me Set Up without helper enabled. Step 1 should open Android Accessibility settings.
2. Trigger Android's restricted-access warning if needed, dismiss it, and return to the app.
3. Step 2 should open Android App Info. The presence of More > Allow restricted settings is **controlled by Android**; check the actual phone.
4. After the user approves restricted settings, Step 3 should reopen Accessibility. Enable Shut Down Helper, return, tap Check Helper.
5. Confirm Shut Down Helper displays Done after reboot, and the original calling, lock, power-menu, and admin controls still work. Do not distribute before tests succeed.
