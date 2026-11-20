"""Focused v1.3.18 source preservation, UI, and version checks."""
from pathlib import Path
from zipfile import ZipFile
import re

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/com/example/dailysimcaller/MainActivity.kt').read_text()
build = (root / 'app/build.gradle.kts').read_text()
assert 'versionCode = 22' in build and 'versionName = "1.3.18-setup-clarity"' in build
assert 'If Android says access is restricted, tap Close, return here, and tap Next.' in main
assert main.count('ShutdownPowerHelper.testPowerMenu()') == 1
assert main.count('ShutdownPowerHelper.showRestartMenu()') == 2
assert 'var fixPermissionsOpened by rememberSaveable' in main
assert 'Text(setupLabel("Fix Permissions", opened = fixPermissionsOpened))' in main
assert 'colors = if (fixPermissionsOpened) usedColors' in main
assert 'granted -> "$label  \\u2713 Done"' in main
assert 'opened -> "$label - Opened"' in main
start = main.index('Text(setupLabel("Set Home App"')
power = main.index('ShutdownPowerHelper.testPowerMenu()')
helper = main.index('Text(setupLabel("Shut Down Helper"')
call = main.index('Text("Call Now")')
assert start < power < helper < call
for name in ('Step 1 of 4: Try Shut Down Helper', 'Step 2 of 4: Fix Permissions',
             'Step 3 of 4: Turn On Helper', 'Step 4 of 4: Restart Phone'):
    assert name in main
assert 'Finish Without Restarting' in main
assert 'LaunchedEffect(restrictedHelperStep)' in main
assert 'CallQueue.start' in main
with ZipFile('/mnt/data/DailySIMCaller_v1.3.17_FinalSetupRestart_Source.zip') as z:
    changed=[]
    for f in z.namelist():
        if f.endswith('/'):
            continue
        path = root / f.removeprefix('DailySIMCaller/')
        assert path.exists(), f
        if path.read_bytes() != z.read(f): changed.append(f)
assert sorted(changed) == sorted([
    'DailySIMCaller/BUILD_STATUS.md',
    'DailySIMCaller/app/build.gradle.kts',
    'DailySIMCaller/app/src/main/java/com/example/dailysimcaller/MainActivity.kt',
]), changed
print('PASS: v1.3.18 UI, restart/menu behavior preserved, and only the 3 intended existing files changed')
