#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/example/dailysimcaller/MainActivity.kt').read_text()
b=(p/'app/build.gradle.kts').read_text()
assert 'versionCode = 16' in b
assert 'versionName = "1.3.12-simple-setup"' in b
labels=['"Help Me Set Up"','"Set Phone App"','"Set Home App"','Text("Power Menu")','"Fix Permissions"','"Set Power Access"','Text("Power off after last queued call (experimental)"']
assert all(x in s for x in labels)
assert [s.index(x) for x in labels]==sorted(s.index(x) for x in labels),"wrong setup button order"
assert 'setupLabel("Set Phone App", granted = phoneReady' in s
assert 'setupLabel("Set Home App", granted = homeReady' in s
assert 'setupLabel("Set Power Access", granted = powerAccessReady' in s
assert 'else if' not in s[s.index('fun setupLabel'):s.index('suspend fun refreshCsv')]
assert 'opened -> "$label - Opened"' in s
assert r'granted -> "$label  \u2713 Done"' in s
assert 'Text("Speakerphone turns on automatically for each new call; use the button to toggle it during a call.")' not in s
assert 'Text("Call Now")' in s and 'ShutdownPowerHelper.testPowerMenu()' in s
assert 'StartupMode.setEnabled(this@MainActivity, checked)' in s
assert 'ShutdownPowerHelper.setEnabled(this@MainActivity, enabled)' in s
assert 'StartupMode.consumePending' in s
assert 'runQueue(fresh, duration, pauseTime, boot = true)' in s
assert 'val powerAccessReady = remember(resumeCount, guidedPulse)' in s
assert 'Text(if (showDetails) "Hide Details" else "More Details")' in s
assert 'DailyAlarm.clearLegacyReminder(this)' in s
print('PASS: setup button order, actual verified checkmarks, visited states and labels')
print('PASS: v1.3.12 version and core call / boot / shutdown code references preserved')
print('NOT TESTED: APK compilation, signing, live phone calling, device reboot')
