# Daily SIM Caller

**Latest development source: v1.3.21 (version code 25)** — ordered permission setup.

**Currently approved deployed release remains v1.3.18.** This source is **not** an installable, signed APK. Do **not** change the employee installation link `https://is.gd/HBKInstall26` until v1.3.21 has been built, tested and approved.

- [v1.3.21 release notes](RELEASE_NOTES_v1.3.21.md)
- [Easy setup guide for v1.3.21](docs/EMPLOYEE_EASY_SETUP_v1.3.21.md)
- [Current build status](BUILD_STATUS.md)
- [Existing employee guide for approved v1.3.18](docs/EMPLOYEE_EASY_SETUP_v1.3.18.md)

Daily SIM Caller loads an approved Google Sheets CSV list, supports authorized **Call Now** and opted-in calls after reboot, and can retry temporary list-download failures while conditions allow. Android still requires the user to approve Phone, Home and Accessibility access manually. An empty or failed list never authorizes calls using stale numbers.

The source excludes signing material and phone-number data. Use the **same original signing key** when preparing an in-place update.
