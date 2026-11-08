from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt').read_text()
g=(root/'app/build.gradle.kts').read_text()
w=(root/'.github/workflows/build-apk.yml').read_text()
assert 'manualSwitchInProgress.compareAndSet(false,true)' in s
assert 'MANUAL_SWITCH_IGNORED' in s
assert 'closeOnce.compareAndSet(false, true)' in s
assert 'fun releaseDecoder(): Boolean = synchronized(codecLock)' in s
assert 'MANUAL_SWITCH_DECODER_RELEASED:' in s
assert 'MANUAL_SWITCH_NETWORK_READER_STALE:' in s
assert 'synchronized(current.codecLock)' in s
assert 'if (current.closed || generation.get()!=ticket) break' in s
assert 'own?.releaseDecoder()' in s
assert 'DECODER_CLEANUP:' in s
assert s.index('own?.close()\n            drain?.join(500L)') < s.index('val released=own?.releaseDecoder()')
assert 'versionCode = 25' in g and 'versionName = "0.17.4-r13"' in g
assert 'test_duopad_r11_nonblocking_switch.py' in w
assert 'DuoPad-Pro-v0.17.4-R13-debug.apk' in w
print('R11_NONBLOCKING_SWITCH_SOURCE_CHECK_PASS')
