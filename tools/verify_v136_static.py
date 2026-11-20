#!/usr/bin/env python3
"""Static verification only. Requires Android Studio for compile/hardware testing."""
from pathlib import Path
import xml.etree.ElementTree as ET
p=Path(__file__).resolve().parents[1]
a='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(p/'app/src/main/AndroidManifest.xml').getroot()
app=manifest.find('application')
main=next(x for x in app.findall('activity') if x.get(a+'name')=='.MainActivity')
filters=main.findall('intent-filter')
for cat,action in [('android.intent.category.HOME','android.intent.action.MAIN'),('android.intent.category.DEFAULT','android.intent.action.DIAL')]:
    assert any(any(n.get(a+'name')==action for n in f.findall('action')) and any(n.get(a+'name')==cat for n in f.findall('category')) for f in filters)
services=app.findall('service')
assert any(s.get(a+'name')=='.CallMonitorService' and s.get(a+'permission')=='android.permission.BIND_INCALL_SERVICE' for s in services)
source=(p/'app/src/main/java/com/example/dailysimcaller/MainActivity.kt').read_text()
boot=(p/'app/src/main/java/com/example/dailysimcaller/BootPreparation.kt').read_text()
shutdown=(p/'app/src/main/java/com/example/dailysimcaller/ShutdownPowerHelper.kt').read_text()
gradle=(p/'app/build.gradle.kts').read_text()
for phrase in ['openPhoneAppInfo(guided = true)','retryPhoneRole()','Settings.ACTION_APPLICATION_DETAILS_SETTINGS','phoneAppInfo','guided_setup_complete_v136','ShutdownPowerHelper.connectionStatus','BootPreparation.schedule(this@MainActivity)']:
    assert phrase in source, phrase
assert 'Scheduled; waiting for Android and network' in boot
assert 'Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES' in shutdown
assert 'versionCode = 10' in gradle
assert 'versionName = "1.3.6-guided-role-recovery"' in gradle
assert source.index('if (autoOnBoot && !StartupMode.isHomeApp') < source.index('openPhoneAppInfo(guided = true)')
print('PASS: v1.3.6 guide, role recovery, accessibility status and preparation changes (STATIC only)')
print('NOT TESTED: compile, signing, device-specific Phone role, reboot dialing, physical shutdown')
