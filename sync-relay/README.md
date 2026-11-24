# Daily SIM Caller v45 - cloud-sync relay (development only)

This folder contains an **undeployed prototype** Cloudflare Worker plus D1 schema. It is not a live sync service until deployed and tested against the existing call-log Google Sheet.

## Design

- Each Cloud Mobile C9 handset enrolls with its stable app-generated ID and a confidential operator enrollment code.
- Enrollment creates an independent 256-bit device bearer token. Never embed the enrollment code or Google credentials in an APK, GitHub repository, or distributed sheet.
- Devices upload only completed calls through authenticated HTTPS POST requests.
- D1 stores each call durably. Primary key is (device_id, call_id), so retrying an upload does not make duplicate records.
- The relay mirrors each call to a tab named after the device ID in the existing shared call-log Sheet, with one stable sheet row per call, and acknowledges only rows confirmed mirrored.
- Google Sheets is a **reporting mirror**, not an unbounded primary database. Google's tab/cell/API limits prevent a literally unlimited number of phone tabs. For a large fleet, roll older logs into archives or partition reporting workbooks; keep D1 as the master.
- The original approved-number spreadsheet is unrelated and should not be edited.

## Required configuration (private)

Provision a D1 database and apply schema.sql. Bind it to the Worker as DB. Set SHEET_ID to the call-log workbook's existing ID. Put the following in Cloudflare **secret bindings**, not in source control:

- ENROLLMENT_SECRET: a random confidential enrollment code at least 32 characters long.
- GOOGLE_SERVICE_ACCOUNT_JSON: a service-account credential JSON. Create it in a Google Cloud project with Sheets API enabled.

Share only the call-log workbook with the service-account email as Editor. Do not share or change the approved-number source Sheet. Protect the enrollment route with rate-limits or WAF rules. Enable logs and traces before production use.

There is currently no configured D1 binding, deployment, credential, or verified device-to-Sheets transaction in this branch. Cloudflare connection previously returned HTTP 403 on Workers Scripts due to missing scope, so deployment through that connection is blocked.

## Operator test protocol

1. Deploy Worker with D1, a restricted secret enrollment code, service account credentials and correct SHEET_ID.
2. GET /health. This only reports that the Worker process is running.
3. Enroll one company-owned C9 by POST /v1/enroll with JSON fields device_id and enrollment_code; store the returned per-device token on that phone.
4. POST /v1/calls with header Authorization: Bearer DEVICE_TOKEN and JSON containing device_id and calls. Each call needs call_id, phone_number, date, time, time_zone, connected_seconds, result ("Ended" or "Not connected").
5. Confirm the device-specific tab in the call-log Google Sheet has that exact row.
6. Resend the same request. It must not create a duplicate row. Confirm a second device gets a separate tab.
7. Disable Sheet editing temporarily. Client must keep calls queued and retry until the write is confirmed.
8. Install a v45 sync-enabled APK on one C9, exercise actual calls, foreground sync and background retries before fleet deployment.

Android cannot guarantee exact five-minute execution with the screen off. Implement foreground attempts every five minutes, prompt sync after completed calls, and Android-managed background retries at a nominal fifteen-minute interval.

## API shapes

POST /v1/enroll accepts:

    { "device_id": "C9-AB12CD34", "enrollment_code": "PRIVATE_OPERATOR_CODE" }

Response:

    { "device_id": "C9-AB12CD34", "token": "per-device-bearer-token" }

POST /v1/calls accepts Authorization: Bearer DEVICE_TOKEN and:

    { "device_id": "C9-AB12CD34", "calls": [{ "call_id": 1, "phone_number": "4055550133", "date": "2026-11-23", "time": "19:03:55", "time_zone": "America/Chicago", "connected_seconds": 57, "result": "Ended" }] }

Response:

    { "synced_call_ids": [1], "mirrored_count": 1 }

Only call IDs included in synced_call_ids may be marked synced locally.
