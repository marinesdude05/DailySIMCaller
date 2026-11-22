# Daily SIM Caller v1.3.21 — Ordered Permissions (source candidate)

Based on **v1.3.20** (versionCode **25**, versionName `1.3.21-ordered-permissions`).

## Setup screen fix

- A single, ordered permission checklist replaces the competing four-step guide. The old **Step 1 of 4** through **Step 4 of 4** cards are removed entirely.
- Top controls: **Help Me Set Up** followed immediately by **Fix Permissions**.
- Permission rows in order: **Phone Permission**, **Notifications**, **Set Phone App**, **Set Home App**, **Shut Down Helper**. The red **Power Menu** stays directly beneath, followed by **Call Now**.
- **Battery Settings** is removed from the visible setup checklist.
- **Help Me Set Up** asks for each missing regular permission one at a time, then default Phone, Home (if reboot calling is enabled), and Shut Down Helper (if power-off is enabled). Stops after denial; employees can tap Help again or use the individual rows. No unexpected automatic restart/power-menu opening after setup.
- **Fix Permissions** reveals a clear, flashing **⋮** indicator in the app explaining the *top-right* Android App info overflow menu, followed by **Open App Info**. An Android toast also reminds users when App Info opens. The flashing indicator does **not** animate or appear over Android's actual Settings screen. Android controls whether its overflow menu or **Allow restricted settings** is available.
- Corrects default-Phone role denial recovery: use Android **Default apps**, not the unrelated Accessibility restricted-settings menu.
- Checklist shows green completion only when Android reports approval; visiting Settings is not permission approval.

## Unchanged

- Queued calling, authorized Google Sheets CSV refresh/retry, boot calling, speakerphone, connected-call timer, and optional post-queue power-off code paths. The app cannot grant Android permissions or automatically click protected Settings menus.
- No signed APK, physical-phone test or production-link replacement was performed by this source change. Keep `HBKInstall26` on the existing approved release until an updated APK is compiled, signed with the current release key, tested and approved.

[Employee walkthrough](docs/EMPLOYEE_EASY_SETUP_v1.3.21.md)
