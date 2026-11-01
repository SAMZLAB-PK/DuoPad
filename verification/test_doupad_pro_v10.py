from pathlib import Path
root=Path(__file__).resolve().parents[1]
video=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text()
build=(root/"app/build.gradle.kts").read_text()
assert 'doupad-first-frame-watchdog' in video
assert 'Thread.sleep(12000)' in video
assert 'FIRST_FRAME_TIMEOUT after 12s:' in video
assert 'current.close()' in video
assert 'SERVER:' in video and 'serverSource.readUtf8Line()' in video
assert 'VIDEO_PACKET #' in video
assert 'FIRST_FRAME_RENDERED:' in video
assert 'packetCount.incrementAndGet()' in video
assert 'MediaCodec.BUFFER_FLAG_CODEC_CONFIG' in video
assert 'MediaCodec.BUFFER_FLAG_KEY_FRAME' in video
assert 'if (generation.get() == ticket && !firstFrame.get())' in video
assert 'No decoded frame: packets=' in video
assert 'versionCode = 15' in build
assert 'versionName = "0.15.0-tvbox-encoder-beta"' in build
print("DOUPAD_PRO_V10_FRAME_WATCHDOG_CHECKS_PASS")
