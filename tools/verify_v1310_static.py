#!/usr/bin/env python3
"""Static layout-only change check, not Android compilation or on-device testing."""
from pathlib import Path
import zipfile
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/example/dailysimcaller/MainActivity.kt').read_text()
g=(p/'app/build.gradle.kts').read_text()
labels=[
    'Text("Automatically Open Missing Permission Screens")',
    'Text("Open App Info if Accessibility is restricted")',
    'Text("Open Default Phone app Settings")',
    'Text("Open Default Home app Settings")',
    'Text("Power off after last queued call (experimental)"',
]
for label in labels:
    assert s.count(label)==1, (label, s.count(label))
indices=[s.index(x) for x in labels]
assert indices == sorted(indices), indices
assert 'onClick = { beginGuidedSetup() }' in s
assert 'startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)' in s
assert 'openDefaultAppSettings(home = false, guided = false)' in s
assert 'openDefaultAppSettings(home = true, guided = false)' in s
assert 'Text("Call Now")' in s
assert 'Auto-start authorized calls after reboot and unlock' in s
assert 'versionCode = 14' in g
assert 'versionName = "1.3.10-accessibility-button-first"' in g
print('PASS: Accessibility App Info is above Default Phone and Home; all remain before power off')
print('NOT TESTED: compilation, signing, real device reboot/call behavior')
