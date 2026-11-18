# Daily SIM Caller

Android stress-test application for owned Cloud Mobile C9 devices.

## Read these first

1. [START HERE](START_HERE.md) — requirements, milestones, acceptance criteria, and development rules.
2. [BUILD STATUS](BUILD_STATUS.md) — verified progress, known limitations, code history, and next actions.

## Current repository state (2026-11-18)

The repository is **connected and the two living documents have been uploaded**. The full Android Studio source project has **not yet been imported into this GitHub repository**.

The preserved source snapshot is `DailySIMCaller_GitHub_Ready_2026-11-18.zip` in the ChatGPT Library folder `Daily SIM Caller - Master Project`. Its ZIP contains an Android Studio project, Gradle wrapper, Kotlin source, resource files, tests, and a proposed GitHub Actions workflow. Until those files are imported, this GitHub repository is documentation-only and **cannot yet build an APK**.

## Planned repository structure

```text
DailySIMCaller/
  START_HERE.md
  BUILD_STATUS.md
  README.md
  CHANGELOG.md
  docs/
  app/
  gradle/
  gradlew
  gradlew.bat
  settings.gradle.kts
  build.gradle.kts
  .github/workflows/android-ci.yml
```

## Development rules

- Review START HERE and BUILD STATUS before modifying source.
- Commit code changes in small logical increments and document each verified change.
- Distinguish compile success from tests on a physical phone.
- Do not mark automatic call progression, hangup, scheduling, or device shutdown complete without device evidence.
- Keep the Android Studio working directory local; use Git/GitHub for version history.
