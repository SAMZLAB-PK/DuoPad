from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt').read_text()
g=(root/'app/build.gradle.kts').read_text()
w=(root/'.github/workflows/build-apk.yml').read_text()
c=(root/'app/src/main/java/com/unipoint/core/util/MirrorCrashRecorder.kt').read_text()
assert 'val close=remoteButton("×"){requestMirrorExit()}' in s
assert 'worker.shutdown()' in s and 'inputWorker.shutdown()' in s
assert 'worker.shutdownNow()' not in s
assert 'inputWorker.shutdownNow()' not in s
assert 'STREAM_WORKER_INTERRUPTED' in s
assert 'VIDEO_FPS:' in s
assert 'codec.releaseOutputBuffer(result, System.nanoTime())' in s
assert 'codec.releaseOutputBuffer(result,true)' not in s
assert 'VideoProfile(720,30,2_400_000,true,true)' in s
assert 'VideoProfile(640,24,1_350_000,true,true)' in s
assert 'VideoProfile(640,12,750_000,true,true)' in s
assert 'mirrorWorker = thread.name.startsWith("doupad-")' in c
assert 'versionCode = 33' in g and 'versionName = "0.18.2-r17-pc-pin-handshake"', 'versionName = "0.18.6-r21-pc-host-pro"' in g
assert 'test_duopad_r12_exit_smooth.py' in w
print('R12_EXIT_SMOOTH_SOURCE_CHECK_PASS')
