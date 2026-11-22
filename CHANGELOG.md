# Change log

## Unreleased - Direct Home app settings shortcut
- Tapping **Set Home App** (and admin-only **Choose Home App**) opens Android's Home app settings directly, like Shut Down Helper opens Accessibility settings.
- The existing Home/Phone role checks, guided setup, fallback settings screens, calling and power-off behavior are unchanged. Android must confirm the Home choice.
- Source change only; not yet built or tested on Cloud Mobile C9 phones.

## v1.3.20 - Permission checklist (November 22, 2026)
- Added dark, clickable permission rows with actual Android green checks/orange warnings, based on the provided settings demo.
- Listed Phone, Notifications, Phone app, Home app, Battery Settings, Fix Permissions and Shut Down Helper.
- Preserved the helper directly above Power Menu, guided setup and v1.3.19 retry behavior. Source only, not published.

## v1.3.19 - Source-only test candidate (November 21, 2026)
- Retry temporary network / Google Sheets errors for an already-authorized call run, with 10/20/30/60-second waits, until a fresh list can be downloaded or the user cancels.
- Show **Stop Trying** during a retry. Prevent duplicate restart dialing, retry cancellation problems, stale lists and Compose refresh failures.
- No signed APK or distribution change. v1.3.18 remains the approved team version.

## v1.3.18 — Approved final source (November 20, 2026)
- Clarified Step 1: “If Android says access is restricted, tap Close, return here, and tap Next.”
- Put **Power Menu** under **Set Home App**. It opens the Android power menu for a manual choice; it does not shut down automatically.
- **Fix Permissions** dims and reads **Opened** when tapped; Android-verified settings show **✓ Done**.
- Preserved the v1.3.17 four-step setup/restart flow, connected-call timer, manual calls, CSV sync, optional restart calling, and admin power-off setting.
- Added an easy employee walkthrough and updated repo release guidance. No app-behavior changes were made for this documentation handoff.

## Earlier history

Earlier per-version release notes are kept in the root (`RELEASE_NOTES_v1.3.7.md` through `RELEASE_NOTES_v1.3.17.md`). See those files for historical release details.
