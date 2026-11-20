#!/usr/bin/env python3
"""Source-only invariants; NOT an Android build or a device test."""
from pathlib import Path
import re
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/example/dailysimcaller/MainActivity.kt').read_text()
a=(p/'app/src/main/java/com/example/dailysimcaller/StartupMode.kt').read_text()
b=(p/'app/build.gradle.kts').read_text()
labels=[
    'Text("Automatically Open Missing Permission Screens")',
    'Text("Open App Info if Accessibility is restricted")',
    'Text("Enable shutdown helper in Accessibility settings")',
    'Text("Open Default Phone app Settings")',
    'Text("Open Default Home app Settings")',
    'Text("Power off after last queued call (experimental)"',
]
for label in labels:
    assert s.count(label)==1,(label,s.count(label))
assert [s.index(x) for x in labels]==sorted(s.index(x) for x in labels)
assert s.count('startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))')>=2
assert 'onClick = { beginGuidedSetup() }' in s
assert 'openDefaultAppSettings(home = false, guided = false)' in s
assert 'openDefaultAppSettings(home = true, guided = false)' in s
assert 'Text("Call Now")' in s
assert 'Auto-start authorized calls after reboot and unlock' in s
boot=s[s.index('            // BOOT_COMPLETED only arms'):s.index('            val csvPicker =')]
assert 'guided_setup_complete_v136' not in boot
assert 'guided_setup_started_v136' in boot
assert 'StartupMode.isHomeApp' in boot
assert 'StartupMode.isUnlocked' in boot
assert 'StartupMode.isDefaultPhoneApp' in boot
assert 'checkSelfPermission(Manifest.permission.CALL_PHONE)' in boot
assert 'StartupMode.consumePending' in boot
assert 'runQueue(fresh, duration, pauseTime, boot = true)' in boot
assert 'StartupMode.recordBootResult' in boot
assert 'ShutdownPowerHelper.isServiceConnected()' not in boot
assert 'if (intent?.action == StartupMode.ACTION_BOOT_READY) bootSignalCount++' in s
assert 'Text("Restart readiness: "' in s
assert 'Text("Last boot diagnostic: "' in s
assert 'putLong(LAST_BOOT_AT, System.currentTimeMillis())' in a
assert 'fun readiness(' in a and 'fun lastBootReport(' in a
assert 'versionCode = 15' in b
assert 'versionName = "1.3.11-shutdown-layout-boot-diagnostics"' in b
print('PASS: five permission shortcuts grouped above Power off; original actions retained')
print('PASS: reboot requires authorized conditions; optional shutdown helper not a call prerequisite')
print('PASS: boot event recorded, readiness and diagnostics displayed')
print('PASS: version code 15; restart idempotency checks present')
print('NOT TESTED: Android compilation, signing, device boot/Phone role/cellular calling')
