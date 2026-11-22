# Daily SIM Caller - v1.3.20 permission checklist candidate (code 24)

**Deployment status:** Source only. The previously approved signed v1.3.18 APK remains the installed/team version. HBKInstall26 is unchanged.

## This change

- Reference: owner-provided screen recording of an AnyDesk setup checklist (November 22, 2026).
- Changed only main-screen UI, permission result refresh and version metadata; original network retry/call/restart logic preserved.
- Source ZIP integrity: check in authoring environment; no signed APK produced.
- Android SDK/Gradle build and live-device tests: **not verified**. The GitHub CI workflow has failed during unavailable Android SDK 37 installation rather than app compilation.

## Test before rollout

- Open all seven setup rows and verify green checks only when Android reports permissions/roles enabled.
- Test denied permissions, Home/Phone app roles, restricted Accessibility settings, manual power menu, battery settings, retry/cancel and authorized calls.
- On upgrade, verify the signature matches the installed APK; never uninstall to bypass mismatch.
- Update this file with observed device logs, APK checksum and installer test result only after on-device verification.

[Release notes](RELEASE_NOTES_v1.3.20.md)
