"""Static source/UI guard checks only; does NOT compile or test Android APK."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/example/dailysimcaller/MainActivity.kt').read_text()
v=(root/'app/build.gradle.kts').read_text()
assert 'versionCode = 25' in v and 'versionName = "1.3.21-ordered-permissions"' in v
assert 'Battery Settings' not in s and 'PowerManager' not in s
assert 'restrictedHelperStep' not in s and 'restartMenuAutoOpened' not in s
for obsolete in ('Step 1 of 4', 'Step 2 of 4','Step 3 of 4','Step 4 of 4'):
    assert obsolete not in s
ordered=['Text("Help Me Set Up")','"Fix Permissions  \\u22ee"',
         'SetupPermissionRow("Phone Permission"','SetupPermissionRow("Notifications"',
         'SetupPermissionRow("Set Phone App"','SetupPermissionRow("Set Home App"',
         'SetupPermissionRow("Shut Down Helper"','Text("Power Menu")','Text("Call Now")']
assert [s.index(x) for x in ordered]==sorted(s.index(x) for x in ordered), 'Incorrect UI order'
assert s.count('SetupPermissionRow("Shut Down Helper"') == 1
assert s.count('SetupPermissionRow("Set Phone App"') == 1
assert s.count('SetupPermissionRow("Set Home App"') == 1
assert 'rememberInfiniteTransition(label = "threeDotsGuide")' in s
assert 'repeatMode = RepeatMode.Reverse' in s
assert 'Text("Open App Info")' in s
assert 'Allow restricted settings' in s
assert 'Settings.ACTION_APPLICATION_DETAILS_SETTINGS' in s
assert 'Toast.makeText' in s
assert 'permissionsLauncher.launch(arrayOf(nextPermission))' in s
assert s.index('if (!StartupMode.isDefaultPhoneApp(this@MainActivity))') < s.index('if (autoOnBoot && !StartupMode.isHomeApp(this@MainActivity))') < s.index('if (ShutdownPowerHelper.isEnabled(this@MainActivity) &&')
assert 'runQueue(fresh, duration, pauseTime, boot = true)' in s or 'startCallsWithRetry(true' in s
assert 'callRetryJob' in s and 'startCallsWithRetry' in s
assert 'StartupMode.isHomeApp' in s and 'ShutdownPowerHelper.testPowerMenu()' in s
assert 'android.permission.SYSTEM_ALERT_WINDOW' not in (root/'app/src/main/AndroidManifest.xml').read_text()
print('PASS: v1.3.21 UI order, no numbered guide/battery, flashing in-app three dots, ordered permissions and preserved call hooks')
print('NOT TESTED: Android compilation, signed APK, Cloud Mobile C9 or system Settings overflow menu')
