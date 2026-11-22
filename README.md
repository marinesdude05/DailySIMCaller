# Daily SIM Caller

**Latest Android Studio source: v1.3.20 (code 24), test candidate.** The latest owner-approved, installed APK is **v1.3.18** until this new version is built, signed and tested on a phone.

- [New permission checklist release notes](RELEASE_NOTES_v1.3.20.md)
- [Automatic-retry notes](RELEASE_NOTES_v1.3.19.md)
- [Build and installation instructions](START_HERE.md)
- [Employee guide for currently approved APK](docs/EMPLOYEE_EASY_SETUP_v1.3.18.md)

## Current app

The app loads an approved Google Sheets CSV number list and can place authorized calls through **Call Now**. With configured roles and opt-in, it can begin a call sequence after reboot/unlock. It retries temporary network failures before calling and never uses a stale list as fresh data.

The main screen has a dark **Setup** checklist. **Fix Permissions** now sits directly beneath **Help Me Set Up**, followed by Phone Permission, Notifications, Set Phone App, Set Home App and Shut Down Helper. The Battery Settings row was removed from the screen. Green **✓** means Android confirmed access; orange **!** means check it. Fix Permissions is a shortcut without a verified yes/no status. The four-step guided setup remains available.

The **For Admin Only** section controls operational settings and is not password protected. Android permission changes require the phone owner's approval.

## Release boundary

This repository contains **source, not a signed production APK**. GitHub Actions cannot currently download the Android SDK specified by the project. Use Android Studio and the original signing identity, then test an authorized single-number call and restart before changing the live **HBKInstall26** installation link. Do not commit signing keys or real phone lists.
