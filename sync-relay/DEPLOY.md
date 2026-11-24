# Activate Daily SIM Caller v45 syncing (operator checklist)

**This branch is not deployed just by committing it.** No calls synchronize until a real call is verified in the live Google Sheet.

## A. Authorize Cloudflare safely

The ChatGPT Cloudflare connection currently lacks Workers Scripts and D1 scopes. Use a separate, account-restricted Cloudflare API token stored as a *GitHub Actions repository secret*, rather than pasting a token into ChatGPT or repeatedly trying a broken connection prompt.

1. In the [Cloudflare API token dashboard](https://dash.cloudflare.com/profile/api-tokens), create an expiring custom token with account-level **Workers Scripts: Edit** and **D1: Edit**, restricted to the relevant account. Cloudflare may require an additional Workers binding/deployment permission; grant only the exact scope it requests.
2. In [GitHub repository Actions secrets](https://github.com/marinesdude05/DailySIMCaller/settings/secrets/actions), add **CLOUDFLARE_API_TOKEN** containing that token.
3. Add **CLOUDFLARE_ACCOUNT_ID** as another repository secret (your account identifier, found in the Cloudflare dashboard). Never put either value in a commit, issue, or message.
4. GitHub Actions has a manual-only `Deploy v45 call-log relay` workflow. Because GitHub workflow_dispatch may require a workflow to exist on the default branch, a maintainer must expose the reviewed workflow on the default branch before running it. Do not make routine pushes deploy automatically.
5. The workflow runs tests, builds the Worker, does a dry run, and publishes only after private credentials are available.

## B. Configure D1 database and Google Sheet permissions

1. Create/verify a Cloudflare D1 database named `daily-sim-caller-logs`. Ensure the Worker has binding `DB`. Run [schema.sql](schema.sql) in D1's SQL console, without dropping unrelated data.
2. Set a strong, random, operator-only `ENROLLMENT_SECRET` (minimum 32 characters) as a **Cloudflare Worker secret**. Do not embed the code in the APK or public GitHub repository.
3. In a Google Cloud project, enable Google Sheets API; create a service account for this sync service. Put the entire private credential JSON in a **Cloudflare Worker secret** named `GOOGLE_SERVICE_ACCOUNT_JSON`. Do not paste this credential into chat, public source, or GitHub Actions logs.
4. Grant that service account **Editor** access to the existing *call-log* workbook (not the separate approved-number source).
5. Confirm the Worker is deployed with `SHEET_ID` set to the known call-log workbook, and a D1 `DB` binding.

## C. Verify before enabling the fleet

1. Check `GET /health` on the deployed Worker. This only confirms that the HTTP handler is running.
2. Install the [v45 preview APK](https://github.com/marinesdude05/DailySIMCaller/actions/runs/45337718598) on **one** company-owned Cloud Mobile C9. Open Extra Tools > Google Sheets call-log sync.
3. Enter the deployed HTTPS Worker URL and operator enrollment code, then tap **Enroll & Connect Phone**.
4. Complete an authorized test call; tap **Sync Call Logs Now**. Verify the Google Sheet creates a device-specific `C9-XXXXXXXX` tab with correct phone number, date, time, time zone, connected seconds, and result.
5. Sync the same call again and verify no duplication; test a second device and an offline/retry scenario.
6. Only then distribute a production-signed APK to the wider fleet. Android does not guarantee exact five-minute background execution.

Links: [v45 draft PR](https://github.com/marinesdude05/DailySIMCaller/pull/3) | [Call-log Sheet](https://docs.google.com/spreadsheets/d/1m93ppcPSgreho3RNRDI1QKUm5fecUazWcdDt8PRglNg/edit)

**Never mark syncing ready until a real record appears in its proper tab.**
