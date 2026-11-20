# Test matrix - Cloud Mobile C9 pilot

Test only with company-owned or explicitly authorized endpoints. Capture firmware version, SIM/carrier, APK version and test date with each result. Do not use an emulator to confirm real SIM telephony.

| Case | Procedure | Pass criteria | Status |
| --- | --- | --- | --- |
| T01 Build | Run `:app:testDebugUnitTest :app:assembleDebug` | All tasks pass | Prior screenshot build success; CI not run |
| T02 Install | Open versioned APK HTTPS link on reset C9 | App installed with normal Android confirmation in minimal steps | Not tested |
| T03 Upgrade | Update from signed APK of same app/signing identity | Settings persist and app opens | Not tested |
| T04 CSV sync | Change canonical authorized list, refresh on old and new C9 | Same hash, count and version; stale failure visible | Drive refresh reported; central sync not tested |
| T05 Manual queue | Run now on 2-3 controlled destinations | Each called once in order; no duplicate overlap | Not tested |
| T06 Timed connected call | Set 30-60 second duration | Measured end timing within agreed tolerance, states logged | Not tested |
| T07 Pause | Set 10-second pause | Next dial starts after pause from previous termination | Not tested |
| T08 No answer | Make an unreachable/no-answer test call | Bounded timeout; next step logged correctly | Not tested |
| T09 Cancel | Stop while call active / between calls | No extra new calls; state consistent | Not tested |
| T10 Restart | Reboot during waiting and after partial run | No accidental replay or duplicate session | Not tested |
| T11 Triggers | Delay after power-on / 10 AM schedule | Trigger status correct, obeys Android background restrictions | Not tested |
| T12 20 cycles | Perform controlled repeated cycles and inspect durable history | Exactly 20 completed entries max; failures not counted as complete | Not tested |
| T13 Completion | Complete approved test list | TEST COMPLETE displayed, log persisted | Not tested |
| T14 Power-off | Attempt only with validated OEM enterprise interface | Device powers down after logged completion | Blocked/unknown OEM support |
| T15 Scale | Two devices, then five, then 20+ | Reproducible installation and test logs per phone | Not tested |

Update actual timestamps, firmware and pass/fail evidence in BUILD_STATUS, not by merely changing this table to PASS.
