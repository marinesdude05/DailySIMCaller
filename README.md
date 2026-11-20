# Daily SIM Caller

**Current approved source: v1.3.18 (version code 22).** The owner accepted this release on November 20, 2026. This repository contains the Android Studio project; it does not include a release signing key or a signed APK.

- **Employees:** [Easy setup walkthrough](docs/EMPLOYEE_EASY_SETUP_v1.3.18.md)
- **Installers and developers:** [START_HERE.md](START_HERE.md), [BUILD_STATUS.md](BUILD_STATUS.md)
- **Release details:** [RELEASE_NOTES_v1.3.18.md](RELEASE_NOTES_v1.3.18.md), [CHANGELOG.md](CHANGELOG.md)
- **Source archive in Drive:** [DailySIMCaller_v1.3.18_ClearerSetup_Source.zip](https://drive.google.com/file/d/1P9WIukorqJYajyOt2onau419sDpHRAH7/view?usp=drivesdk)

## What it does

Daily SIM Caller loads an approved CSV list of phone numbers, can start a manual call run with **Call Now**, and supports optional calls after a restart when an administrator enables them. The app also has a four-step Android permission/helper setup and an optional after-queue power-off feature.

## Safety and release status

The owner reports that the final build and device testing were completed. This repository update did **not** independently validate APK signing, installation, actual call behavior, restart calling, or shutdown on a physical phone. No production signing material or live phone-number data belongs in the repository.

**Note:** Android can only grant Phone, Home and Accessibility permissions after the user makes the selection on the device. **Opened** means a settings button was visited; **✓ Done** means Android confirmed the setting.
