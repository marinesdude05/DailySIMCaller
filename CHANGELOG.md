# Changelog

Track releases and operator-visible changes here. For the detailed evidence/verification log, see [BUILD_STATUS.md](BUILD_STATUS.md).

## Unreleased - 2026-11-18

- Fixed CI environment setup: replaced `android-actions/setup-android@v3` with v4 after two failed runs attempting to install the retired Android SDK `tools` package. App behavior unchanged; CI build verification remains pending.

- Imported the preserved Android Studio source baseline to the private GitHub repository in commit `ee9288f53b7b5d9b6c037c2e24fe1cc69ef83039` (56 source/support files, no application logic changes).
- Verified the GitHub file tree and representative Kotlin, Gradle wrapper, and CI workflow paths.
- GitHub Actions Android CI run #1 was started by the import push; result was pending at the documentation update.

- Established GitHub-ready folder organization and living project documents.
- Added initial CI workflow and Android engineering/test/release documentation.
- Preserved the last project ZIP that was reported to build and open on a C9; no telephony runtime changes in this documentation revision.
- Stored blank sample CSV separately from any real centrally managed phone list.
