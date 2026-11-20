# Daily SIM Caller — v1.3.18 (code 22)

**Release disposition:** Owner approved v1.3.18 as final on November 20, 2026. The owner reports APK build and testing completed. Specific device-test evidence, signer identity, artifact checksum, and APK-to-source correspondence were not independently supplied or verified here.

## Source verification

- PASS — Android Studio source based on v1.3.17 FinalSetupRestart, with v1.3.18 setup wording/button changes.
- PASS — Static source verification and source ZIP integrity from the v1.3.18 source creation step.
- PASS — Repository documentation synchronized to v1.3.18; employee guide added.
- NOT INDEPENDENTLY VERIFIED — Gradle compile, APK signature, installation upgrade, on-device calls, reboot-calling and automatic power-off.

## Release handoff

- Keep signed APK and its signing key under appropriate access control. Signing keys and private lists must not enter public GitHub.
- Record final APK checksum, firmware/model, installation link and test outcome when available.
- [Employee guide](docs/EMPLOYEE_EASY_SETUP_v1.3.18.md)
- [Release notes](RELEASE_NOTES_v1.3.18.md)
