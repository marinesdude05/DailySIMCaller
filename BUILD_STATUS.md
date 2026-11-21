# Daily SIM Caller - v1.3.19 retry candidate (code 23)

**Current deployed/owner-approved APK: v1.3.18.** v1.3.19 is source-only pending an Android Studio signed build and device test. No production install link has been changed.

## v1.3.19 checks

- PASS - Standalone Kotlin retry tests: repeated DNS failure then success, cancellation, malformed list refusal, HTTP error classification.
- PASS - Static integration checks and ZIP integrity.
- NOT VERIFIED - Android Gradle build, APK signing, real network reconnection, phone dialing after reconnection, reboot queue, auto-power off.

## Existing approved release

- PASS — Android Studio source based on v1.3.17 FinalSetupRestart, with v1.3.18 setup wording/button changes.
- PASS — Static source verification and source ZIP integrity from the v1.3.18 source creation step.
- PASS — Repository documentation synchronized to v1.3.18; employee guide added.
- NOT INDEPENDENTLY VERIFIED — Gradle compile, APK signature, installation upgrade, on-device calls, reboot-calling and automatic power-off.

## Release handoff

- Keep the v1.3.18 signed APK and signing key secure. Do not distribute v1.3.19 until tested.
- [Retry details](RELEASE_NOTES_v1.3.19.md)
- [Employee guide for currently approved version](docs/EMPLOYEE_EASY_SETUP_v1.3.18.md)
