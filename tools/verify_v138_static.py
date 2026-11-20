#!/usr/bin/env python3
"""Source-structure checks only, NOT Android compilation or hardware tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
p=Path(__file__).resolve().parents[1]
a='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(p/'app/src/main/AndroidManifest.xml').getroot()
app=manifest.find('application')
main=next(x for x in app.findall('activity') if x.get(a+'name')=='.MainActivity')
filters=main.findall('intent-filter')
for cat, action in [('android.intent.category.HOME','android.intent.action.MAIN'),('android.intent.category.DEFAULT','android.intent.action.DIAL')]:
    assert any(any(n.get(a+'name')==action for n in f.findall('action')) and any(n.get(a+'name')==cat for n in f.findall('category')) for f in filters)
assert any(s.get(a+'name')=='.CallMonitorService' and s.get(a+'permission')=='android.permission.BIND_INCALL_SERVICE' for s in app.findall('service'))
assert any(r.get(a+'name')=='.BootReceiver' for r in app.findall('receiver'))
assert not any(r.get(a+'name')=='.DailyCallReceiver' for r in app.findall('receiver'))
assert not any(n.get(a+'name')=='android.permission.SCHEDULE_EXACT_ALARM' for n in manifest.findall('uses-permission'))
source=(p/'app/src/main/java/com/example/dailysimcaller/MainActivity.kt').read_text()
boot=(p/'app/src/main/java/com/example/dailysimcaller/BootReceiver.kt').read_text()
alarm=(p/'app/src/main/java/com/example/dailysimcaller/DailyAlarm.kt').read_text()
gradle=(p/'app/build.gradle.kts').read_text()
assert 'Text("Call Now")' in source
for forbidden in ['Call Now + Enable Daily 10 AM', 'Enable Daily Reminder Only', 'Cancel Daily Reminder', 'Daily reminder: 10:00 AM', 'DailyAlarm.schedule(', 'DailyAlarm.requestExactAlarmAccess(', 'daily_enabled', 'isDailyOn']:
    assert forbidden not in source, forbidden
assert 'persistSettings()' in source
assert 'runQueue(fresh, duration!!, gap!!)' in source
assert 'Auto-start authorized calls after reboot and unlock' in source
assert 'StartupMode.isPending(this@MainActivity)' in source
assert 'StartupMode.armAfterBoot(context)' in boot
assert 'BootPreparation.schedule(context)' in boot
assert 'DailyAlarm.clearLegacyReminder(this)' in source
assert 'DailyAlarm.clearLegacyReminder(context)' in boot
assert 'FLAG_NO_CREATE' in alarm and 'getBroadcast(' in alarm
assert 'versionCode = 12' in gradle
assert 'versionName = "1.3.8-settings-button-layout"' in gradle
print('PASS: v1.3.8 reminder removal, manual calling and reboot path source checks')
phone = source.index('Text("Open Default Phone app Settings")')
home = source.index('Text("Open Default Home app Settings")')
access = source.index('Text("Open App Info if Accessibility is restricted")')
setup = source.index('Text("Guided setup requests Phone and Notifications permissions')
assert phone < home < access < setup
assert source.count('Text("Open App Info if Accessibility is restricted")') == 1
print('PASS: settings buttons are grouped in Phone, Home, Accessibility order')
print('NOT TESTED: Android compilation, signing, hardware reboot auto-calling')
