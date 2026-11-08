from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt').read_text()
g=(root/'app/build.gradle.kts').read_text()
w=(root/'.github/workflows/build-apk.yml').read_text()
for token in ['activeVideoAttempt','MANUAL_SWITCH_CLEANUP_OK', 'MANUAL_SWITCH_DECODER_RELEASED',
              'PROFILE_RETRY_OLD_SOCKET_STALE', 'oldAttempt.join(350L)',
              'activeVideoAttempt.compareAndSet', 'lastVisibleVideoAtMs',
              'Video is visible', 'STREAM_EXCEPTION:']:
    assert token in s, token
assert 'current?.close()' not in s[s.index('private fun switchTvBoxEncoderSafely()'):s.index('private fun toggleSnapshotFallback()')]
assert 'versionCode = 25' in g and 'versionName = "0.17.4-r13"' in g
assert 'test_duopad_r10_decoder_lifecycle.py' in w
assert 'DuoPad-Pro-v0.17.4-R13-debug.apk' in w
print('R10_DECODER_LIFECYCLE_SOURCE_CHECK_PASS')
