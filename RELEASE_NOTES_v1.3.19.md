# Daily SIM Caller v1.3.19 - Connection Retry (test candidate)

Built directly from the finalized v1.3.18 source. App versionCode **23**, versionName **1.3.19-auto-retry**. The approved v1.3.18 install remains unchanged.

## What changed

- Once an **authorized Call Now** run or opted-in **post-reboot** run is ready, a temporary Google Sheets / network error no longer ends the run. The app fetches a **fresh list again** after 10, 20, 30, and then 60 seconds between attempts, for as long as the request stays active and the app is visible/unlocked.
- The main screen shows a short retry message and a **Stop Trying** button while waiting. **Call Now** can start waiting even if a fresh install has never downloaded its first list. The button cancels retries; on post-reboot runs, it also cancels the remaining attempt for that reboot.
- **No calls are started from an old/cached number list.** Each retry must obtain a new valid CSV before it can start a queue.
- The boot one-shot marker is kept during temporary connectivity failures and cleared after calls start or a permanent error; repeated lifecycle events cannot start duplicate queues.
- A deliberate Call Now action supersedes a pending boot run. The retry job uses Activity lifecycle scope, not a Compose-only scope, so recomposition does not falsely report a network failure.
- **Permanent errors** (invalid/empty/malformed number list, denied authorization, HTTP 403/404) stop rather than retry forever.
- Existing default Phone/Home, setup wizard, connected-call timer, audio route, optional post-queue shutdown, and admin controls are preserved.

## Limits and tests

- Retrying only continues while this trusted app is visible, the phone is unlocked, and required roles/permissions are present. It stops if the app is hidden or the owner presses Stop Trying. An opted-in post-reboot marker can resume once the Home screen becomes visible again. It is **not** a general background auto-dial service.
- Temporary HTTP 408/429/5xx and network IO errors retry. Other errors stop without dialing.
- The new retry helper passed standalone Kotlin test cases covering repeated DNS failure then successful refresh, cancellation, malformed list refusal, and HTTP classification. Static integration checks and ZIP integrity are also run.
- **Not verified:** Android Studio/Gradle compilation, signed APK, SIM dialing, OEM reboot behavior, real network reconnection, or physical shutdown. Test on a dedicated Cloud Mobile C9 with only authorized destinations before replacing the HBKInstall26 APK.
- Build a new signed release APK in Android Studio using the same protected signing key as v1.3.18. Do **not** publish the source ZIP or debug APK as if it were the installed release.
