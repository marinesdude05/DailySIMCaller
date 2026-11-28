# Deploy V52 central sync

Create a standalone **Daily SIM Caller V52 Central Sync** project in the Google account that can edit both existing spreadsheets. Add `Code.gs` and `appsscript.json`. Set the script properties `CALL_LOG_SHEET_ID` and `DEVICE_TRACKING_SHEET_ID` to the existing call summary and device tracking spreadsheet IDs. Run `verifyDestinations` from the editor to authorize the Sheets scope and verify both destinations without writing rows.

Deploy a web app as the deploying account with access for **Anyone** so counter phones can reach the endpoint without Google sign-in. Device requests still require a credential bound to the handset's installation and call history IDs. Pass the resulting HTTPS `/exec` URL as Gradle property `CENTRAL_SYNC_URL` when building the release. Sign with the same key as V51 for in-place upgrades.

On an authorized test phone, open **For Admin Only**. Set script properties `ENROLL_DEVICE_ID` and `ENROLL_INSTALLATION_ID` from the two IDs shown there, then run `issueDeviceCredential` in the editor. Save the resulting JSON privately and use **Import sync credential** on that phone. Remove the transferred credential file once imported. On subsequent launches, reboots, and completed calls, records sync in the background. Keep credentials out of shared downloads, logs accessible to non-admins, and source control. To revoke a phone's credential, remove its `device:<device-id>` script property.

For a rollout check, install V52 over V51 on one C9, confirm its local call history still appears, import a credential, then complete an authorized test call. Verify its existing history and one new call appear under its device ID, column C is unchanged, and retrying the same upload leaves exactly one row per call.
