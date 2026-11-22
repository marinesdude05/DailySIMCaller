# Daily SIM Caller — v1.3.21 ordered setup (code 25)

**Status:** Android Studio source candidate only. The previously approved signed v1.3.18 APK and `HBKInstall26` distribution link are unchanged.

## Source checks

- Started from the exact v1.3.20 Drive ZIP plus fixes to MainActivity, Gradle version and docs.
- Consolidated setup UI into one ordered checklist; removed Battery Settings and numbered guide.
- Static checks verify button order, instructions, flashing in-app menu-dot cue, no Android Settings overlay, one-at-a-time permission requests, and preserved core calling/service files.
- Archive ZIP integrity checked.

## Still required

- A successful Android Gradle compile, signed release APK using the existing signing certificate, physical C9 setup test, Google Sheets retry/call test and reboot/shutdown checks.
- GitHub CI has failed before compiling at Android SDK 37 installation. Static tests are **not** a compiled or phone-tested APK.

See [release notes](RELEASE_NOTES_v1.3.21.md).
