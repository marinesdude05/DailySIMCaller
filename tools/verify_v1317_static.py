"""Static guard checks for the v1.3.17 guided restart-only UI."""
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
main = (root/'app/src/main/java/com/example/dailysimcaller/MainActivity.kt').read_text()
helper = (root/'app/src/main/java/com/example/dailysimcaller/ShutdownPowerHelper.kt').read_text()
build = (root/'app/build.gradle.kts').read_text()
assert 'versionCode = 21' in build
assert 'versionName = "1.3.17-setup-restart-menu"' in build
for label in ('Step 1 of 4: Try Shut Down Helper','Step 2 of 4: Fix Permissions',
              'Step 3 of 4: Turn On Helper','Step 4 of 4: Restart Phone'):
    assert label in main, label
assert 'restrictedHelperStep = 4' in main
assert 'restartMenuAutoOpened = true' in main
assert 'LaunchedEffect(restrictedHelperStep)' in main
assert 'Finish Without Restarting' in main
assert 'Open Power Menu' in main
assert main.count('ShutdownPowerHelper.showRestartMenu()') == 2
start = helper.index('fun showRestartMenu(): Boolean')
end = helper.index('fun queueStarted()', start)
section = helper[start:end]
assert 'openPowerMenu(testOnly = true)' in section
assert 'openPowerMenu(testOnly = false)' not in section
assert 'performGlobalAction' not in section
assert 'CallQueue.start' in main
assert 'ShutdownPowerHelper.testPowerMenu()' in main
with ZipFile('/mnt/data/files/DailySIMCaller_v1.3.16_ConnectedCallTimer_Source.zip') as z:
    original = {name.removeprefix('DailySIMCaller/'):z.read(name) for name in z.namelist()
                if name.startswith('DailySIMCaller/app/src/main/java/') and name.endswith('.kt')}
    changed=[name for name, data in original.items() if (root/name).read_bytes() != data]
assert set(changed) == {
    'app/src/main/java/com/example/dailysimcaller/MainActivity.kt',
    'app/src/main/java/com/example/dailysimcaller/ShutdownPowerHelper.kt'
}, changed
print('PASS: v1.3.17 guide, manual restart, no automatic power selection, version, and preserved Kotlin components')
