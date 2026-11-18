# START HERE - Daily SIM Caller

**Project:** Daily SIM Caller - Cloud Mobile C9 device stress testing  
**Plan version:** 1.3 | **Last reviewed:** 2026-11-18  
**Read next:** [BUILD_STATUS.md](BUILD_STATUS.md)  
**Source of truth:** Private GitHub repository [`marinesdude05/DailySIMCaller`](https://github.com/marinesdude05/DailySIMCaller), branch `main`. Source baseline commit `ee9288f53b7b5d9b6c037c2e24fe1cc69ef83039`. The ZIP in ChatGPT Library remains the original backup.

## 1. Goal in plain English

Workers turn on batches of company-owned Cloud Mobile C9 phones, install a signed app through one direct HTTPS link (no app store), and let each phone carry out controlled cellular connectivity tests against company-owned or otherwise explicitly authorized destination numbers. A central, maintained CSV list defines the test destinations. Each test makes ordered calls using configurable call lengths and pauses, records what happened, and clearly marks the phone finished so it can be stored until its next round.

We need **up to 20 independent test cycles per device during approximately 3 months**. Efficiency, reliable results, and recoverability matter more than flashy screens. **Powering the handset off automatically is the desired finish behavior but is NOT supported by an ordinary Android app on stock Android without an approved manufacturer/system integration.** A clear TEST COMPLETE screen and an operator shutdown step are the fallback.

## 2. Daily operator scenario

1. Turn on the C9 and ensure a working SIM and data/Wi-Fi are available.
2. On a newly prepared phone, open the short internal installation URL, download the properly signed APK, approve Android's install-from-source prompt when required, and install. A previously provisioned phone skips this step.
3. The installed app reads its saved settings. On launch and before each test, it fetches the current authorized CSV over HTTPS, validates it, reports the version and age, and caches the last valid copy. Network failures must be visible to the operator; an old list must not silently count as current.
4. Start by a configured trigger: manual Run Test Now, a chosen local time, or a delay after startup (example: 5 minutes). Do not start duplicate overlapping runs.
5. During a test, make one authorized call at a time, in CSV order. Track call setup, connected duration, hangup outcome, inter-call pause, unexpected disconnects, and errors. Any automatic call-control path must work under Android's approved telecom APIs on this specific firmware.
6. Save the test results, increment the completed-cycle counter only after a valid completion, and show TEST COMPLETE or TEST FAILED/INTERRUPTED. Upload summaries when a central results endpoint is available.
7. The operator puts the phone away after confirming completion; optional automatic power-off depends on a verified OEM/enterprise mechanism. At the next power-on, the next cycle should be available without accidentally replaying finished calls.

## 3. Scope and non-goals

- **In scope:** Private source repository; signed APK distribution; live shared destination list; user-configured test triggers; ordered test calls; adjustable durations and pauses; logs and a 20-cycle ledger; clear result/status screen; repeatable deployment and updates.
- **Not yet proven:** Unattended outgoing calls after boot/when locked; call-time enforcement; reliable hangup/advance; 20-cycle persistence across failures; auto-shutdown; large-fleet rollout.
- **Not in scope:** Calling random external people; unconsented silent marketing calls; bypassing Android security, install permissions, or carrier restrictions; storing actual destination/customer phone lists in GitHub source.
- **Environment:** Company-owned Cloud Mobile C9 / C9 Prime devices. Android 16 was reported during setup, but exact firmware/OS should be confirmed on production units. Current Gradle project should not be upgraded blindly.

## 4. Milestone plan and definition of done

| ID | Milestone | Acceptance test | Current state |
| --- | --- | --- | --- |
| M0 | Stable baseline and documentation | Compiling snapshot archived, source identifiable, START HERE and BUILD STATUS available | **Complete for source/document archive**; prior local build was user-reported, not rerun from GitHub |
| M1 | GitHub source control and CI | Private repository; clean commit; CI assembles debug APK and runs unit tests | **Complete** — private source committed; [CI run #3](https://github.com/marinesdude05/DailySIMCaller/actions/runs/44378192800) passed unit tests, assembled debug APK and uploaded artifact. Hardware testing is a separate milestone. |
| M2 | Fast APK installation | Signed versioned APK loads from one short HTTPS link onto a reset C9; upgrade works | **Not started** |
| M3 | Shared updated CSV | Fresh install and previously connected phones fetch same current CSV and display version/refresh errors | **Partially complete** (manual Drive picker refresh only) |
| M4 | Supported call session management | One-phone controlled test of connect, duration, hangup, next, pause, cancellation, and errors | **Partially complete — operator reported successful prior call test and directly confirmed first-number dialing on C9.** Exact connected timing, auto-hangup, intercall pause, second-number transition, and logs still need individual confirmation. |
| M5 | Startup and schedule triggers | No duplicate runs; schedule/boot/delay work with permissions and reboot behavior on C9 | **Partially complete** (reminder/scheduling interface) |
| M6 | Completion + 20-cycle ledger | Durable counts and per-cycle logs survive force-stop, restart, and CSV changes | **Not started** |
| M7 | Completion action | TEST COMPLETE reliably shown; OEM power-off only if technically verified | **Not started** |
| M8 | Fleet rollout | Pilot on 2 devices, then 5, then 20+ with repeatable setup instructions | **Not started** |

Each milestone stays **Unverified** or **Partially complete** until there is a reproducible hardware test, not just a successful compilation or UI screenshot.

## 5. Approved architecture

- **Code:** Kotlin, Jetpack Compose, Android Gradle project (existing package `com.example.dailysimcaller`). Keep an Android Studio working copy local; GitHub now stores the source baseline and commit history. Local edits still require an explicit Git commit and push.
- **Distribution:** Versioned release APK signed with a private keystore; a stable HTTPS page/URL (Cloudflare is a candidate). Never commit the keystore or passwords. Android sideload install confirmation cannot be bypassed by a regular application.
- **CSV:** One canonical endpoint; preferably authenticated when lists include nonpublic numbers. Version with an ETag/hash and `last-fetched` timestamp. Android's Drive document picker may serve cached copies and is not always-live synchronization.
- **Scheduler:** Supported alarm/task APIs, explicit alarm permissions where justified, foreground processing only in legally supported situations, lifecycle-safe state.
- **Telephony:** Android default dialer role and a complete `InCallService` may be required. Becoming the default phone app means supporting normal incoming/outgoing phone UI and emergency-call behavior; do not treat a minimal demo dialer as a finished product.
- **Persistence:** Durable per-device state (e.g., Room), atomic cycle-state transitions, failure logs; no duplicate in-progress sessions.
- **Device shutoff:** Verify C9 manufacturer/enterprise support before proposing automated shutdown. Avoid root/bypass assumptions.

## 6. Quality and change-control rules

**Before starting any change:** Read this file and `BUILD_STATUS.md`; inspect the last known source revision and exact failure/feature request.

**For every change:** Make one focused, reviewable change; update tests; run supported static/unit/build checks; report what ran, what was not run, and hardware results separately. Update `BUILD_STATUS.md` in the same commit with the files touched, outcome, blockers, and next action. Update this plan whenever scope or milestone status changes. Use Git commits and branches after connection, rather than overwriting an unknown phone or laptop version.

**No false auto-save claim:** ChatGPT cannot observe Android Studio Ctrl+S events or push to GitHub while disconnected. With GitHub connected, changes made in this workflow can be committed and logged per revision; Android Studio local edits are saved in GitHub only after an explicit push or configured local Git workflow.

**No premature deployment:** Source compiles != test calls work. Signed release, private repository, and real device verification are separate gates.

## 7. Where to continue

1. Read [BUILD_STATUS.md](BUILD_STATUS.md) for exact current evidence and next change.
2. Inspect [docs/ENGINEERING_NOTES.md](docs/ENGINEERING_NOTES.md) for Android constraints and official links.
3. Implement in small milestones, verify with [docs/TEST_MATRIX.md](docs/TEST_MATRIX.md), and update both living documents.
4. Use [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md) for signed APK distribution when ready.