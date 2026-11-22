# Start here - Daily SIM Caller v1.3.20 source

## Employee instructions (after the owner approves a signed APK)

1. Open **Daily SIM Caller**. In **Setup**, look for orange exclamation points.
2. Tap each orange row, approve the required Android setting yourself, then come back. Green checks mean **Done**.
3. For shut down access, use **Help Me Set Up** if Android shows restricted access. Follow Steps 1-4; tap **Check Helper Next** at Step 3.
4. **Fix Permissions** opens App Info if Android blocks the Shut Down Helper. It does not grant access itself.
5. **Battery Settings** is optional but may help reliable restart behavior. **Set Home App** changes the normal home screen.
6. Tap **Call Now** only when your supervisor has approved the phone list and calling. **Stop Trying** cancels a pending Google Sheets retry.

## Build and release

1. This source uses application ID `com.example.dailysimcaller`, version code **24**, version name **1.3.20-permission-checklist**.
2. Open this folder in Android Studio, wait for Gradle sync, and compile the app.
3. Build a **signed release APK using the same original signing key** as the installed version. Do not upload or commit the keystore.
4. Before distribution, check the full setup list, actual permission statuses, manual Power Menu, Call Now, retry, cancel, post-reboot calls, and stop controls on a Cloud Mobile C9 using permitted test numbers.
5. Keep v1.3.18 available. Do not update HBKInstall26 until the **exact signed APK** has been independently tested and approved.

See [v1.3.20 release notes](RELEASE_NOTES_v1.3.20.md) and [build status](BUILD_STATUS.md).
