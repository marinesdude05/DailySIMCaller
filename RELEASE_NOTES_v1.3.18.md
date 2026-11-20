# Daily SIM Caller v1.3.18 — Clearer Setup

Built from the exact `DailySIMCaller_v1.3.17_FinalSetupRestart_Source.zip` archived in Google Drive / APK / Daily Sim Caller Builds. Version code: **22**.

## Updated setup
- Step 1 now ends: **If Android says access is restricted, tap Close, return here, and tap Next.**
- Moved the red **Power Menu** button immediately after **Set Home App**, ahead of **Shut Down Helper**. It still only opens the system power menu as a test; it does not select a power action.
- **Fix Permissions** now dims and displays **Opened** once the user taps it. As with the existing shortcut controls, a tap does **not** imply a permission has been approved; only Android-verified roles/services display **✓ Done**.
- Retained the existing simple labels: Help Me Set Up, Fix Permissions, Set Phone App, Set Home App, Power Menu, and Call Now. The repetitive speaker explanation was already absent from the v1.3.17 main screen.

## Preserved
- Four-step guided setup including its **Restart Phone** step and its manual-only Android power menu.
- Connected call timer, CSV sync, Call Now, startup/restart queue logic, after-queue power-off switch, and shutdown accessibility helper logic.
- No change to boot, calling, timing, or power-off service source files.

## Verification
- Static source checks and ZIP integrity checked; the source is not an installable or signed APK.
- Android compilation and physical-device behavior **not verified** in this environment.
- Keep the current installed version until the new APK is compiled, signed with the same key, and tested.

## Final handoff (November 20, 2026)
- Owner accepted v1.3.18 and reports that the APK has been built and tested. Those tests were not independently rerun in this documentation/GitHub sync.
- Employee walkthrough: [`docs/EMPLOYEE_EASY_SETUP_v1.3.18.md`](docs/EMPLOYEE_EASY_SETUP_v1.3.18.md).
- No further code or version-number change was made during final documentation.
