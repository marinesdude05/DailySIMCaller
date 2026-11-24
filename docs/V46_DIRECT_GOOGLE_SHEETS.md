# v46 direct Google Sheets preview — no relay

Daily SIM Caller v46 uses Google Play Services authorization inside the Android app to write outgoing call history directly to the separate **Daily SIM Caller — Call Logs** Google workbook. The existing approved phone-number source is unchanged.

## What changed

- The app's Google Sheets connection button opens Google's account/permission flow inside the Android app.
- The upload is sent directly to `sheets.googleapis.com`; there is no upload relay, secret enrollment password, or service-account JSON on the handset.
- Each device keeps its local `C9-XXXXXXXX` identity and uploads completed call history into a same-named tab in the call-log workbook.
- Row `call_id + 1` keeps retries idempotent. The app marks calls synced only after a matching batch-update response.
- Older v45 relay jobs and saved enrollment credentials are removed on app start/boot. Existing local call records are **never** deleted.
- Network retries run after completed calls and through Android-managed background jobs (which Android may delay). The foreground attempts run approximately every five minutes.

## Not ready until verified

Google Play Services must exist on the target C9 handset. Google OAuth configuration must recognize the APK's signing certificate, package name `com.example.dailysimcaller`, and the Sheets authorization scope. Because GitHub Actions' default debug keystore may change between runners, a repeatably signed test APK is required before reliable OAuth sign-in can be validated. Do not assume a one-time build success proves real sign-in.

The owner of the call-log workbook must sign in or explicitly grant Sheet editing rights to the Google account chosen on the phone. The existing service-account permission is not a substitute for this interactive sign-in.

Before wider installation, verify (1) a build, (2) Google account consent, (3) one completed call appears in the correct device tab, (4) retry does not duplicate it, and (5) reboot/background retries work. The direct Sheets OAuth scope requests spreadsheet access; grant it only to trusted fleet devices and review organization Google security policy.

## Scope

This branch does not delete the old deployed Cloudflare Worker, D1 database, or any Google Sheet data. It only removes the relay from the **new app source and Android build pipeline**. The old v45 branch remains available for rollback. 
