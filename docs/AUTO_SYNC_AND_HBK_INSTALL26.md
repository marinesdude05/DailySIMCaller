# Automatic CSV loading — HBKInstall26 release target

## User-facing URL and release boundary
- Team download link (user reported working): `https://is.gd/HBKInstall26`
- Underlying Cloudflare Worker: `https://hbk-group-install.nmaxwellhbk.workers.dev/`
- Current Worker redirect in earlier setup targeted Google Drive APK file ID `1VS0qRyJD1CvR3ixdKKerjHMaKro8jZZk`.
- This patch is **source only**. It has NOT updated the APK on Google Drive or the Cloudflare Worker, and it has NOT verified the current is.gd target by an independent HTTP check. Existing installs do not gain auto-sync until a signed updated APK is distributed and installed.
- Retain the is.gd URL. After a verified signed APK is built, update the existing Drive APK content in place if it preserves the file ID and versioning correctly, or point the Cloudflare Worker at a new versioned APK. Do not change the is.gd destination unless necessary. Re-test the complete chain in an incognito browser and on a C9.

## CSV source and privacy
- Selected file, confirmed as the **only item in the `Call Phone Numbers` folder** through Drive folder listing: `Daily_SIM_Caller_Phone_Numbers.csv` (Drive file ID `1I0OxzSSFHwVcil-vBOQesb29ZQf7GLRs`).
- File has `phone_number` header and four data rows. Do not copy the actual numbers into source control or this release package.
- Google Drive metadata currently reports an **anyone-with-the-link read-only** permission. The raw CSV URL is embedded in `app/src/main/res/values/csv_endpoint.xml` as:
  `https://drive.google.com/uc?export=download&id=1I0OxzSSFHwVcil-vBOQesb29ZQf7GLRs`
- The file can be read by anyone who knows the URL. Use **only controlled, authorized non-sensitive test destinations** with this approach. If the data is private, replace it with an authenticated server endpoint and revoke broad sharing before production distribution. Do **not** hard-code a credential into the APK.
- The URL is a Google Drive raw-download endpoint, not the file's `/view` link. Raw unauthenticated HTTP delivery still needs a test on C9/Android; Google may serve an HTML warning/sign-in page or change download behavior. The app will refuse to start calls if the endpoint does not serve valid CSV.
- **Important:** edits to a Google Sheet do not automatically modify this separately stored CSV attachment. Update the same CSV file ID (or swap the app to an authoritative live endpoint) for changes to reach the phones. Re-uploading under a different ID will require an app update.

## Runtime behavior
- On every app launch/resume, fetch the current HTTPS CSV before use.
- On manual run, fetch again; if fetch fails, do not start new calls using stale data.
- Display last successful fetch time and a SHA-256 fingerprint prefix (not a complete cryptographic integrity verification of the sender).
- Keep the last valid list visible and cached locally so failures are diagnosable; do not silently treat cached data as freshly fetched.
- The legacy picker remains available only in builds where `csv_endpoint` is empty.
- The source ZIP does not include actual destination numbers, credentials, or signing keys.

## Release acceptance test
1. Build with Android Studio, run unit checks, and sign under the same signing identity as the installed version. Confirm `versionCode=2` is greater than the previous release.
2. Install on one C9 using a side-loaded development build: app should load four numbers automatically, without file selection. Verify count and fingerprint. Disconnect networking and confirm run refuses to dial an unrefreshed list.
3. Update **the same CSV file** in Drive (preserve ID `1I0OxzSSFHwVcil-vBOQesb29ZQf7GLRs`), then verify two C9s independently pull its new contents on their next refresh. Do not use private customer numbers for public testing.
4. Test install and upgrade through `HBKInstall26` after replacing the backend APK; verify user-visible version and checksum and record results in BUILD_STATUS.
5. Before production use, decide whether read-only anyone-with-link access is acceptable for this strictly authorized test list. If not, move the data behind an access-controlled HTTPS service and republish an app with its endpoint.
