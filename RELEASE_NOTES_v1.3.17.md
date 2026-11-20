# Daily SIM Caller v1.3.17 — Restart at End of Setup

Built from the complete v1.3.16 Connected Call Timer source. Version code: 21.

## Changes
- After the guided setup confirms all required permissions, roles, and helper access, it displays **Step 4 of 4: Restart Phone** and requests Android's power menu once.
- **Open Power Menu** is also available on the final step to reopen the dialog.
- The app NEVER selects Restart or Power off in this setup flow. The user chooses **Restart** manually in Android's system menu.
- If Accessibility is not connected or Android refuses the dialog, the user sees an instruction to hold the phone's physical power button.
- The restricted-settings steps are now labeled 1–4 rather than 1–3. If all permissions are already approved, the guided role-check flow still proceeds to the final restart step.

## Preserved
- v1.3.16 connected-call duration timer, red Power Menu main button, help and Fix Permissions controls, call queue and default role checks, boot startup, and optional after-queue power-off behavior.
- The automatic after-queue power-off routine is NOT used by setup's Restart step. The setup uses only Android's power dialog, with no automated selection.

## Build/verification
- Static source checks and ZIP integrity were performed; no on-device test is available.
- Not a signed/installable APK unless an Android SDK/Gradle build is performed separately.

## Device test
1. Complete the Accessibility, Home, Phone, and notification setup.
2. Confirm final **Restart Phone** step appears and the Android power dialog opens.
3. Verify no menu item is selected automatically; tap Restart yourself when ready.
4. Check **Open Power Menu** can reopen the dialog and **Finish Without Restarting** exits the guide.
5. Check the no-service fallback and the older calling, timer, and auto shutdown features.
