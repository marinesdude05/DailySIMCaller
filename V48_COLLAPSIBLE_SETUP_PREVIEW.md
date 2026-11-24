# v48: Collapsible setup with call logs below it

This build preserves the v47 local-only calling behavior. Google Sheets connection controls and uploading remain removed.

- Set Up Your Phone has a collapsible switch; the panel defaults expanded until the first reboot following the setup restart attempt, then defaults collapsed.
- The user's manual expanded/collapsed choice is saved locally.
- Call Logs has a separate toggle immediately below Set Up Your Phone.
- The most recent 20 outgoing calls can be expanded below that toggle, and the records are available regardless of the For Admin Only switch.
- Call data continues to be saved only on the Android device.
- A separately installed debug app ID keeps the existing v46 and v47 data untouched; new preview installations start with their own empty call history.

Branch: feature/v48-collapsible-setup-and-call-logs

## Clearer week-by-week call history
The Call Logs section uses individual readable cards, with each dialed number, date, time, result, connected duration, and Week 1 / Week 2 / Week 3 / Week 4 label. Week numbers are computed from the earliest outgoing call stored on that phone, not from the 20-call display limit. The logs do not require turning on For Admin Only. Tests check the week boundaries.
