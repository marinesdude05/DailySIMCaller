# Daily SIM Caller

Kotlin / Jetpack Compose Android stress-test application for company-owned Cloud Mobile C9/C9 Prime phones.

## Start here

1. [START HERE](START_HERE.md) — goals, daily operator workflow, acceptance milestones, and engineering rules.
2. [BUILD STATUS](BUILD_STATUS.md) — verified source revision, tests, open issues, and incremental change history.
3. [TEST MATRIX](docs/TEST_MATRIX.md) — repeatable C9 hardware test plan.

## Repository baseline

The complete original Android Studio source project was imported into this private GitHub repository on **2026-11-18**, baseline commit [`ee9288f53b7b5d9b6c037c2e24fe1cc69ef83039`](https://github.com/marinesdude05/DailySIMCaller/commit/ee9288f53b7b5d9b6c037c2e24fe1cc69ef83039). This includes `app/`, Kotlin source, resources, tests, Gradle build/wrapper files, `.github/workflows/android-ci.yml`, and engineering documentation.

The original source ZIP is retained separately in ChatGPT Library at `Daily SIM Caller - Master Project/DailySIMCaller_GitHub_Ready_2026-11-18.zip`.

**Import verified; build not yet verified.** The first [GitHub Actions Android CI run](https://github.com/marinesdude05/DailySIMCaller/actions/runs/44377773261) began after the import. Check its final result before treating the GitHub source as a tested build. Runtime call control, unattended triggers, and shutdown remain unverified.

## Build and development

Clone this repository to a **local** folder on your Windows development PC, open its root (containing `settings.gradle.kts`) in Android Studio, then allow Gradle synchronization.

- Windows build: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
- macOS/Linux build: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- CI: [Android CI workflow](.github/workflows/android-ci.yml)

After each verified change: update tests, record the exact code revision and results in BUILD STATUS, and update START HERE when a milestone changes. Commit and push the revision; local saves are not automatically pushed.

**Safety and release limitations:** Use controlled, authorized test destinations only. Ordinary Android apps cannot silently bypass install permissions, become the default dialer without role authorization, or reliably power off stock Android devices. Do not commit real destination number lists or signing secrets.
