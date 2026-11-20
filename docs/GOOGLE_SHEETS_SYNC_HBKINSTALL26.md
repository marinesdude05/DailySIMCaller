# HBKInstall26 - Google Sheets live-sync source update

## New canonical number list
- Google Sheet: `Daily_SIM_Caller_Phone_Numbers`, in `Call Phone Numbers`
- Spreadsheet ID: `11vTMIc9wVV3oi3BZi191BxulLwSaxBflq9ASr02PYuE`
- Tab ID: `174037873`
- Header must be `phone_number` in column A; as observed on 2026-11-18, it had three nonblank test entries.
- App endpoint: `https://docs.google.com/spreadsheets/d/11vTMIc9wVV3oi3BZi191BxulLwSaxBflq9ASr02PYuE/export?format=csv&gid=174037873`
- Google Drive metadata reported sharing `anyone with the link: Viewer`. This exposes the Sheet to anyone with the link; use strictly authorized non-sensitive test numbers, never customer/personal numbers.

## Source behavior
- The existing HTTPS CSV parser stays in place: Google Sheets exports the current tab as CSV. There is no separate CSV file to maintain when this new build is installed.
- The configured source is read when the app opens/resumes and before a test begins.
- On failed HTTP response, login/HTML output, missing header, empty/invalid data, timeout, or parse error, the app displays a refresh failure and blocks a new test. It retains the last successful list on screen as stale reference only.
- Manual file-picker support remains in source as a fallback when the build-configured endpoint is blank.
- App release versionCode raised to 3; versionName `1.2.0-google-sheets-preview`.

## Test status and important limitations
- **Confirmed with authenticated Drive connector:** correct Google Sheet and tab exist, header `phone_number`, three nonblank entries, anyone-with-link Viewer permission.
- **NOT confirmed:** anonymous HTTPS CSV export from an unauthenticated device; external HTTP requests were unavailable in the build environment. Test using a browser Incognito window or directly on a C9, and verify received CSV rather than a Google login HTML page.
- **NOT built / signed / installed / published here:** this is an Android Studio source ZIP. Test through Android Studio, with the same signing identity for an upgrade. Do not replace the working APK behind `HBKInstall26` until verified.

## Operator acceptance procedure
1. Keep existing Drive `.csv` file intact as rollback. In Android Studio, open this project and run **Build > Assemble Project**.
2. Install/run on one dedicated C9, with internet, using the Android Studio Run button. Verify it shows `Automatic Google Sheet: enabled`, three current numbers, and a fresh refresh time/fingerprint without selecting a file.
3. Temporarily remove one authorized test entry **in the Google Sheet**, leaving the `phone_number` header, then reopen/refresh the app. It should show two numbers. Restore the entry; refresh to confirm three again.
4. Disable internet and tap Refresh. Confirm a clear failure, and do not start calls. Restore connectivity and confirm it refreshes successfully.
5. Verify updating an existing installation works without uninstalling or losing relevant state. Only then build/sign release APK and update the same Cloudflare/Drive destination used by `is.gd/HBKInstall26`.
6. After the new APK is confirmed on at least one C9 and the Google Sheet becomes the sole source, archive or remove the old `.csv` only if rollback is no longer needed.
