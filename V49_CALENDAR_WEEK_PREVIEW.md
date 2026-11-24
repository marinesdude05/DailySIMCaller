# v49 — Calendar-Month Week Groups

Corrects the previous week-counting method: **November 24, 2026 is Week 4 of November**, not a week since the first call.

- Week 1: 1–7; Week 2: 8–14; Week 3: 15–21; Week 4: 22–28; Week 5 (when present): 29–end of month.
- Each calendar week is independently collapsible with a switch. The current week initially opens; earlier weeks initially close.
- Each call retains phone number, date, local time, connected duration, and result, in a separate readable card.
- The Call Logs toggle stays directly below Set Up Your Phone and **does not** depend on For Admin Only.
- Displays up to 1,000 most recent local call records, grouped by calendar month and week, including months with earlier call records.
- Google Sheets controls and upload jobs remain removed.
- This debug build keeps a distinct `.weekpreview` application ID and increases versionCode to 49. This version installs beside v48 and keeps its records untouched. The new v49 preview starts with an empty local call history, and will record new outgoing calls on its own. Do not uninstall previous versions just to install this preview.
- The original v46 and v47 app installs remain untouched.

Source: `feature/v49-calendar-week-groups`.
