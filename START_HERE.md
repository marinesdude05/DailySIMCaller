# Start here - Daily SIM Caller v1.3.19 test candidate

## If you are an employee

Read [Easy Phone Setup](docs/EMPLOYEE_EASY_SETUP_v1.3.18.md). Follow the steps in order. Ask your supervisor before using **Call Now** or any **For Admin Only** switches.

## If you install or maintain the app

1. For employees, keep using the approved signed v1.3.18 APK. The new v1.3.19 project is **source only**, not an installable APK.
2. This Android Studio project has application ID `com.example.dailysimcaller`, `versionCode = 23`, and `versionName = 1.3.19-auto-retry`.
3. Open this folder as an Android Studio project. Run checks and compile before using a new APK; sign any update with the same protected signing key as the installed app.
4. Test phone role, Home role, Accessibility helper, the four-step setup, manual calls, connected-call timer, restart behavior, and power menu on an authorized test phone.
5. Keep an older known-good APK available until the replacement is validated on the fleet. Do not commit signing keys or private phone lists.
6. Read [new retry release notes](RELEASE_NOTES_v1.3.19.md) and [release process](docs/RELEASE_PROCESS.md).

## Source-of-truth links

- [Approved v1.3.18 source ZIP in Drive](https://drive.google.com/file/d/1P9WIukorqJYajyOt2onau419sDpHRAH7/view?usp=drivesdk)
- [Easy employee walkthrough](docs/EMPLOYEE_EASY_SETUP_v1.3.18.md)
