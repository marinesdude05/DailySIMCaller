# Daily SIM Caller v43 - Random two-call tests and GitHub-only updates

Development branch: `feature/v43-random-two-github-updater`.
This is a test candidate, not an approved APK. Do not replace the live installation link.

## Phone list
- Keep editing the existing shared Google Sheet's `phone_number` column. Add real authorized company test destinations under the existing entries. Leave the column title unchanged.
- Each initiated cycle refreshes the current list, filters valid unique numbers, then randomly selects exactly **two distinct numbers**. Fewer than two valid entries prevents the cycle from starting.
- The selection is fresh for each cycle and independent of row order. No numbers are hard-coded.

## Call durations
- Each **connected** call chooses its own random integer duration in **46..90 seconds** (inclusive by default).
- The Admin "Random call maximum (seconds)" field can change the maximum in the range 46..3600.
- A call that never connects is not guaranteed to last 46 seconds; it uses the existing unanswered-call timeout.
- If an answered call drops early due to the network or remote party, the app cannot force the call to continue.
- The user can stop a call or cycle at any time. Calls remain limited to the current approved CSV list.

## GitHub updater architecture
- GitHub Releases are the distribution channel; there is no Play Store requirement.
- The app looks for an eligible **published, non-prerelease** release from `/releases/latest` while it is visible, at most once per 12 hours. "Check for GitHub App Update" in Extra Tools forces a check.
- Each release must have:
  - Tag exactly `v44`, `v45`, etc., representing the Android **versionCode** (not the dotted versionName).
  - Exactly one desired signed APK asset named e.g. `DailySIMCaller_v44.apk` and a checksum asset named `DailySIMCaller_v44.apk.sha256` whose contents begin with the lowercase or uppercase 64-digit SHA-256 hex digest.
  - APK built with `applicationId=com.example.dailysimcaller`, a **higher** versionCode than installed, and the **same signing certificate** as the installed updater-enabled APK.
- The updater verifies asset origin, download size, SHA-256, package identity, version code, and APK signer before submitting an Android PackageInstaller session.
- It does not submit a new install while a call queue is active.
- On Android versions that allow it, a qualifying self-update may finish unattended. If Android requires consent, the installer callback requests confirmation via an Android notification; users must approve it.
- First installation requires the normal Android approval flow. An app installed by another signing key cannot be updated in place; preserve the previous working APK and settings before choosing a different signing identity.

## Release process
1. Build and test on a spare Cloud Mobile C9.
2. Build a release APK signed with the **same private signing key** used for the updater-enabled installed version. **Never commit that key** to this repository.
3. Set its `versionCode` (for example 44), produce `DailySIMCaller_v44.apk`, and calculate `sha256sum DailySIMCaller_v44.apk > DailySIMCaller_v44.apk.sha256`.
4. Publish both files under a GitHub Release tagged exactly `v44`.
5. Confirm that the installed v43 app checks and downloads the signed v44 package.
6. Verify the installer result on the actual C9. It may require initial "Install unknown apps" authorization or one-time/ongoing confirmation, depending on Android.
7. Only then consider switching any larger device group to the updater-enabled build.

## Current limitations
- GitHub had no published latest release when this branch was created; automatic update delivery has not been tested against a live release.
- Android approval-free updating is conditional and **not guaranteed**. GitHub hosting by itself does not confer silent-install privileges.
- This branch requires unit tests + actual device verification before distribution.
