# Engineering notes and technical decisions

These notes were reviewed 2026-11-18 from Android Developers documentation and the known source snapshot; they do not replace tests on the exact C9 firmware.

## Telephony and call control

- Android requires an app seeking the `ROLE_DIALER` role to implement both `ACTION_DIAL` and a complete `InCallService` providing incoming-call and ongoing-call interfaces. A minimal prototype isn't necessarily role-complete. Role changes should be explicit on a spare phone. https://developer.android.com/develop/connectivity/telecom/dialer-app
- `TelecomManager.placeCall()` requires supported permissions and carrier/service conditions; successful API invocation does not prove a connected call. https://developer.android.com/reference/android/telecom/TelecomManager
- A `phoneCall` foreground service has prerequisites (e.g. default dialer role). On Android 15+ such a foreground service cannot be launched from `BOOT_COMPLETED`. https://developer.android.com/develop/background-work/services/fgs/service-types
- Android limits other foreground service starts while the application is in the background; exceptions have conditions. https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android telecom APIs do not create an exception to carrier rules, destination authorization, or unattended silent-call requirements. Keep testing to controlled authorized endpoints.

## Scheduling

- Prefer alarms only when an exact wall-clock trigger is truly necessary. `SCHEDULE_EXACT_ALARM` requires special access on recent Android versions; verify `canScheduleExactAlarms()` and handle permission denial/revocation. https://developer.android.com/develop/background-work/services/alarms
- A scheduled notification is **not** the same as a verified unattended call session. Keep states separate: `SCHEDULED`, `TRIGGERED`, `RUNNING`, `COMPLETE`, `FAILED`.

## Phone shutdown

- Ordinary unprivileged Android apps cannot generally issue a full device power-off on stock firmware. Investigate manufacturer documentation and enterprise device management for C9-specific support; do not assume root, shell hacks, or privileged API access. The operator must be shown an explicit completion result if shutdown cannot be verified.

## Distribution and remote data

- Publish signed release APKs via HTTPS (Cloudflare Pages/R2 or equivalent) only after a device-tested release is produced. Android may require the installer app to be allowed to install unknown apps. Do not commit signing keystores or credentials.
- Prefer a versioned canonical CSV endpoint served over HTTPS. Include ETag/checksum, last-sync status, validation, failure handling, and offline cache; a Google Drive document picker `Uri` can return cached content and isn't a guaranteed real-time feed.
- Do not publish private destination numbers in a publicly accessible bucket, even if the URL is hard to guess. Limit public CSVs to intentionally public, owned test numbers or use authenticated access. https://developers.cloudflare.com/r2/buckets/public-buckets/

## Upgrade policy

- Existing snapshot uses a version catalog and a Gradle wrapper. Record actual JDK, Gradle, Android Gradle Plugin, SDK and Kotlin versions for every CI/device test. Upgrade in isolated commits with evidence; never change all dependencies while debugging call behavior.
