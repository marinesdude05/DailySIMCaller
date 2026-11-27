# V50 opt-in device registration review candidate

V49 stores call logs on the phone and cannot tell the download dashboard which phones opened the APK. This candidate adds a separate optional registration event without changing call behavior, local call history, signing identity, or the existing updater.

## Build configuration
Supply the private management spreadsheet ID locally with Gradle property DEVICE_TRACKING_SHEET_ID (not a URL). Do not commit a private sheet ID, credential, service-account key, or token. A build without this property disables registration. The Android OAuth client must match the actual package (debug: com.example.dailysimcaller.weekpreview; release: com.example.dailysimcaller) and signer SHA-1. The Google Sheets API and consent screen must be configured, and the account approving access must already be authorized to edit the private tracking workbook. Do not make that workbook public. Google Sheets OAuth uses the spreadsheets scope; a future production service should use an authenticated, narrowly scoped enrollment endpoint instead of granting broad Sheets access to operators.

## Verification gates
1. Build with the original installed APK's signing identity and matching application ID. Verify version 50 upgrades V49 without uninstalling it. Do not publish before testing.
2. Open a test device. Without choosing Consent and register device, confirm no Device Events row is added.
3. Opt in and authorize the Google account. Verify a first_open event appears with a random app ID, timestamp, version and consent only; no call history or phone number.
4. Reopen and retry after simulated network failure. Confirm the installation metric counts the ID once, even if a response-loss retry appended a duplicate event. Reboot and test another authorized phone; expect one additional distinct app ID.
5. Confirm local calendar-week call history is preserved. Confirm download totals and call-reporting device totals remain separate.
6. A registration event means the first observed, consented app open, not all downloads, historic installations, or continuous activity. V49 phones do not retroactively report until they install an appropriate update and opt in.

No APK was built or signed by this source change.

## Optional completed-call summary sync

Supply CALL_LOG_SHEET_ID as a second local Gradle property for the private call-log workbook. First-open registration and completed-call summary sync are separate, explicit approvals. The new sync preference defaults to off, even if a legacy V46 grant exists. The app creates the C9 tab after authorization, checks existing rows before writing, uses stable local call IDs, and retries with network-constrained Android jobs. The phone number column is left empty for new uploads. Existing raw call rows are never deliberately replaced; a conflicting occupied row stops sync with local history retained. Enable only on authorized test devices: this includes previously stored completed outgoing call metadata. Unfinished calls are excluded. Stopping sync preserves local and uploaded history; an already in-flight upload may finish. Registration is a first observed consenting app open, not all downloads. The call-reporting registry counts devices with completed uploaded rows, not installations.

Additional release gates: test registration and summary opt-ins independently; verify no writes before consent; test an already-synced call, a new completed test call, interrupted network, conflict, reconnect, stop, and upgrade from V49 using matching package and signer. Verify no dialed number is sent. Check a second authorized phone contributes another unique registry ID. Do not replace the live APK until these checks pass.
