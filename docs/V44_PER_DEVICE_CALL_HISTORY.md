# Daily SIM Caller v44 - Per-phone call history

The shared reporting format is **one Google Sheets spreadsheet with a separate tab per company-owned phone**. A CSV file cannot contain tabs. The original shared approved-number sheet is not a call log, and must not be overwritten.

## One row for every outgoing telephone call
- `call_id`: local increasing ID for deduplicating future sync uploads
- `device_id`: app-generated stable ID per installed device, such as `C9-ABC12345`
- `phone_number`: normalized outgoing dialed number
- `date`: local date of dial attempt, `YYYY-MM-DD`
- `time`: local time of dial attempt, `HH:mm:ss`
- `time_zone`: IANA phone time zone at the attempt, for example `America/Chicago`
- `connected_seconds`: actual whole seconds connected, measured using Android's monotonic clock, not the randomized 46-90-second target
- `result`: dialing, connected, ended, or not connected

Date and time are captured for the original dial attempt, not the moment a report is viewed. If a call ends before its target time, record the shorter **actual** connected duration. If never answered, record **0 connected seconds**. Timing starts when Android reports the call as active.

Call history is persisted locally using an app-private SQLite database: each handset has its own database, device ID, and rows. The Extra Tools section of the app displays the most recent 20 calls for **that** handset. The app requires the Default Phone role to observe calls.

## Shared Google Sheets design

| Tab | Content |
| --- | --- |
| `Overview` (optional) | Aggregate statistics, when central syncing is available |
| `C9-ABC12345` | Only this handset's call entries |
| `C9-XYZ98765` | Only another handset's call entries |

A future HTTPS authenticated relay/service must accept per-device call-log uploads, validate the device identity, deduplicate using `(device_id, call_id)`, then append to the matching Google Sheets tab. **This authenticated upload component is not implemented yet**. GitHub distribution of the APK does not grant permission to edit a user's Google spreadsheet, and the public phone-number CSV endpoint is read-only.

Do not put Google API refresh tokens or service-account private keys in the APK or a public GitHub repository. Device-specific logging can function offline, but cloud sync requires network and authorization.

## Important validation
- Confirm dates and local time zones on a real Cloud Mobile C9.
- Check answered, unanswered, manually ended, and network-interrupted calls.
- Confirm logs survive a normal app update and remain isolated between phones.
- Authenticate and test Google Sheets uploads before describing centralized automatic syncing as complete.
