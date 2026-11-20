#!/usr/bin/env python3
"""Static validation only. No APK compilation, signature or hardware validation."""
from pathlib import Path
import xml.etree.ElementTree as ET
p = Path(__file__).resolve().parents[1]
m = ET.parse(p/'app/src/main/AndroidManifest.xml').getroot()
a = '{http://schemas.android.com/apk/res/android}'
app = m.find('application')
act = next(x for x in app.findall('activity') if x.get(a+'name') == '.MainActivity')
filt = act.findall('intent-filter')
def has_action_category(action, category):
    return any(
        any(q.get(a+'name') == action for q in n.findall('action')) and
        any(q.get(a+'name') == category for q in n.findall('category'))
        for n in filt
    )
assert has_action_category('android.intent.action.MAIN','android.intent.category.HOME')
assert has_action_category('android.intent.action.DIAL','android.intent.category.DEFAULT')
assert any(x.get(a+'name') == '.CallMonitorService' for x in app.findall('service'))
main = (p/'app/src/main/java/com/example/dailysimcaller/MainActivity.kt').read_text()
mode = (p/'app/src/main/java/com/example/dailysimcaller/StartupMode.kt').read_text()
boot = (p/'app/src/main/java/com/example/dailysimcaller/BootReceiver.kt').read_text()
job = (p/'app/src/main/java/com/example/dailysimcaller/BootPreparation.kt').read_text()
power = (p/'app/src/main/java/com/example/dailysimcaller/ShutdownPowerHelper.kt').read_text()
gradle = (p/'app/build.gradle.kts').read_text()
assert 'versionCode = 9' in gradle
assert 'versionName = "1.3.5-roles-settings-retry"' in gradle
assert 'applicationId = "com.example.dailysimcaller"' in gradle
assert 'Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS' in main
assert 'Settings.ACTION_HOME_SETTINGS' in main
assert 'openDefaultAppSettings(home = false, guided = true)' in main
assert 'openDefaultAppSettings(home = true, guided = true)' in main
assert 'openDefaultAppSettings(home = false, guided = guidedAwaiting == "phone")' in main
assert 'openDefaultAppSettings(home = true, guided = guidedAwaiting == "home")' in main
assert 'phone_role_denied_v135' in main and 'home_role_denied_v135' in main
assert '"phoneSettings" -> StartupMode.isDefaultPhoneApp(this@MainActivity)' in main
assert '"homeSettings" -> StartupMode.isHomeApp(this@MainActivity)' in main
assert 'guided_setup_started_v135' in main and 'guided_setup_complete_v135' in main
assert '!prefs.getBoolean("guided_setup_complete_v135", false)' in main
assert 'StartupMode.clearPending(this@MainActivity)' in main
assert 'permissionsLauncher.launch(missing.toTypedArray())' in main
assert 'Settings.ACTION_ACCESSIBILITY_SETTINGS' in main
assert 'StartupMode.consumePending(this@MainActivity)' in main
assert 'StartupMode.isUnlocked(this@MainActivity)' in main
assert 'StartupMode.isHomeApp(this@MainActivity)' in main
assert 'StartupMode.isDefaultPhoneApp(this@MainActivity)' in main
assert 'runQueue(fresh, duration, pauseTime, boot = true)' in main
assert 'V135_DEFAULTS_APPLIED' in mode and 'edit.putBoolean(V134_CONSENT, true).putBoolean(AUTO_ENABLED, true)' in mode
assert 'V135_DEFAULTS_APPLIED' in power and '.putBoolean(ENABLED, true)' in power
assert 'StartupMode.armAfterBoot(context)' in boot
assert 'CallQueue.start' not in boot and 'placeCall(' not in boot
assert 'CallQueue.start' not in job and 'placeCall(' not in job
print('PASS: v1.3.5 manifest, role/settings fallbacks, persisted defaults, guided flow and boot guards')
print('NOT TESTED: Android compilation, APK signature, role eligibility, reboot, live calls or physical power-off')
