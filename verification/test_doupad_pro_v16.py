from pathlib import Path

root=Path(__file__).resolve().parents[1]
mirror=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text()
build=(root/"app/build.gradle.kts").read_text()

assert 'aceconnEncoderIndex=0' in mirror
assert 'private fun nextTvBoxEncoder()' in mirror
assert 'MANUAL_ENCODER_SWITCH:' in mirror
assert 'MANUAL_ENCODER_SWITCH_SKIPPED:' in mirror
assert 'MANUAL_ENCODER_SWITCH_ERROR:' in mirror
assert 'Try next Aceconn video encoder' in mirror
assert 'headerButtons.add(remoteButton("ENC")' in mirror
assert 'session?.let { if(!it.closed) it.close() }' in mirror
assert 'current?.close()' in mirror
assert '},600L)' in mirror
assert 'generation.incrementAndGet()' in mirror
assert 'VideoProfile(960, 24, 2_000_000, true, true)' in mirror
assert '.drop(aceconnEncoderIndex)' in mirror
assert 'lastVideoSampleHash' in mirror
assert 'frameHash=' in mirror
assert 'sameAsPrevious=' in mirror
assert 'listOf(2500L,6500L,11000L)' in mirror
assert 'blackPixels=' in mirror
assert 'Video appears black.' in mirror
assert 'versionCode = 16' in build
assert 'versionName = "0.16.0-aceconn-encoder-switch-beta"' in build

print('DOUPAD_PRO_V16_ENCODER_SWITCH_AND_MULTISAMPLE_PASS')
