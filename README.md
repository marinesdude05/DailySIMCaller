# Daily SIM Caller

**Newest source: v1.3.19 (version code 23), TEST CANDIDATE.** The currently approved and installed release remains **v1.3.18 (code 22)**. This repository contains Android Studio source, not a signed APK.

- **Employees:** [Easy setup walkthrough](docs/EMPLOYEE_EASY_SETUP_v1.3.18.md)
- **Installers and developers:** [START_HERE.md](START_HERE.md), [BUILD_STATUS.md](BUILD_STATUS.md)
- **New retry feature:** [RELEASE_NOTES_v1.3.19.md](RELEASE_NOTES_v1.3.19.md), [CHANGELOG.md](CHANGELOG.md)
- **Source archive in Drive:** [DailySIMCaller_v1.3.18_ClearerSetup_Source.zip](https://drive.google.com/file/d/1P9WIukorqJYajyOt2onau419sDpHRAH7/view?usp=drivesdk)

## What it does

Daily SIM Caller loads an approved CSV list of phone numbers, can start a manual call run with **Call Now**, and supports optional calls after a restart when an administrator enables them. The app also has a four-step Android permission/helper setup and an optional after-queue power-off feature.

For the v1.3.19 source, **Call Now** or an authorized reboot run will retry temporary Google Sheets failures while the app is visible. Use **Stop Trying** to cancel. Calls only start after a fresh valid list is downloaded.

## Safety and release status

The owner reports that v1.3.18 was built and tested. **v1.3.19 has not been compiled into an APK or tested on a phone here.** Do not replace the working HBKInstall26 download until a new signed APK passes on-device tests. No production signing material or live phone-number data belongs in the repository.

**Note:** Android can only grant Phone, Home and Accessibility permissions after the user makes the selection on the device. **Opened** means a settings button was visited; **✓ Done** means Android confirmed the setting.
