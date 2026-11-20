# Daily SIM Caller v1.3.16 - Connected Call Timer

Based on the complete v1.3.15 source (versionCode 19); this source has **versionCode 20**.

## Changes
- The main screen **Call status** area and the in-call screen show the same **Call time** counter.
- The counter remains at **00:00 while dialing or ringing** and begins when Android Telecom reports the call as **ACTIVE** (answered/connected).
- It updates every second; minutes and seconds are shown as MM:SS, and long calls as H:MM:SS.
- On an Activity reopen, the counter uses the shared monotonic start from Telecom's observed ACTIVE transition instead of restarting.
- The counter clears when the call disconnects or is removed. Each new call starts separately.
- The counter is display-only and does not change the queue's configured call length, dial timing, call ending, or speakerphone behavior.

## Preserved
- v1.3.15 Restricted Settings guide; Phone/Home role checks; Shut Down Helper; admin controls; Call Now; connected-call length; auto speaker; boot startup; keep screen awake; red Power Menu; shutdown helper.

## Build and verification
- Static integration checks and ZIP integrity tests passed.
- No Android SDK or cached Gradle distribution was available for a compile or signed APK in this environment. This ZIP is **Android Studio source**, not an installable APK.
- Sign the release APK using the original key/certificate to upgrade existing installations.

## On-phone test
1. Tap Call Now (using an authorized test number) and verify Call time is 00:00 while dialing or ringing.
2. Answer the call and check that Call time advances every second on the main screen and in-call controls.
3. Switch between those screens and confirm the timer continues without restarting.
4. End the call and check the counter clears; the next call begins at 00:00.
5. Check queue hang-up timing, role/permission checkmarks, startup after reboot, and power-menu button remain unchanged.
