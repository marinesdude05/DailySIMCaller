#!/usr/bin/env python3
"""Source-only guardrails; does not prove an Android build or phone behavior."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/com/example/dailysimcaller/MainActivity.kt').read_text()
retry=(root/'app/src/main/java/com/example/dailysimcaller/CallStartRetry.kt').read_text()
boot=(root/'app/src/main/java/com/example/dailysimcaller/BootReceiver.kt').read_text()
queue=(root/'app/src/main/java/com/example/dailysimcaller/CallQueue.kt').read_text()
gradle=(root/'app/build.gradle.kts').read_text()
assert 'versionCode = 23' in gradle and '1.3.19-auto-retry' in gradle
assert 'fun startCallsWithRetry(' in main
assert 'CallStartRetry.waitForFreshNumbers(' in main
assert 'lifecycleScope.launch {' in main
assert 'var retryingCalls' in main and 'Text("Stop Trying")' in main
assert 'callRetryJob?.cancel()' in main
assert 'StartupMode.clearPending(this@MainActivity)' in main
assert 'if (!boot) StartupMode.clearPending(this@MainActivity)' in main
assert 'val started = runQueue(fresh, callDuration, callPause, boot)' in main
assert main.index('val fresh = CallStartRetry.waitForFreshNumbers(') < main.index('val started = runQueue(fresh, callDuration, callPause, boot)')
assert 'shouldContinue = { canRetryCall(boot) }' in main
assert 'StartupMode.isUnlocked(this@MainActivity)' in main
assert 'StartupMode.isHomeApp(this@MainActivity)' in main
assert 'No calls started' in retry
assert 'e: CancellationException' in main
assert '10L' in retry and '20L' in retry and '30L' in retry and '60L' in retry
assert 'isTemporary(e)' in retry and 'require(fresh.isNotEmpty())' in retry
assert 'val hasCallSource = remoteCsvUrl.isNotBlank() || csvUri.isNotBlank() || list.isNotEmpty()' in main
assert 'CallQueue.start' not in boot and 'placeCall(' not in boot
assert 'fun start(' in queue and 'telecom.placeCall(' in queue
for label in ('Help Me Set Up','Fix Permissions','Set Phone App','Set Home App','Power Menu','Call Now','Shut Down Helper'):
    assert label in main
assert 'Step 4 of 4: Restart Phone' in main
assert 'ShutdownPowerHelper.showRestartMenu()' in main
print('PASS: version 1.3.19, foreground retry, one-shot boot safety, stop control, fresh-list guard, prior UI preserved')
print('NOT TESTED: Android Gradle compilation, signing, call or reboot on device')
