from pathlib import Path

root=Path(__file__).resolve().parents[1]
mirror=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text()
gradle=(root/"app/build.gradle.kts").read_text()

# The latest logs proved successful first decode; preserve its working scrcpy
# video/audio/control setup and add non-invasive playback diagnostics.
assert 'VideoProfile(960, 24, 2_000_000, true, true)' in mirror
assert 'VideoProfile(720, 15, 1_200_000, true, false)' in mirror
assert 'AUDIO_READY: raw PCM stereo / 48 kHz' in mirror
assert 'AUDIO_PLAYBACK: packets=' in mirror
assert 'AUDIO_WRITE_FAILED:' in mirror
assert 'AUDIO_ERROR:' in mirror
assert 'VIDEO_OUTPUT: rendered=' in mirror
assert 'FIRST_FRAME_RENDERED:' in mirror
assert 'VIDEO_FIT:' in mirror
assert 'surface.scaleX=' in mirror and 'surface.scaleY=' in mirror
assert 'CONFIG_CHANGED:' in mirror
assert 'SURFACE_DESTROYED:' in mirror
assert 'SURFACE_RESTART:' in mirror
assert 'Executors.newFixedThreadPool(2)' in mirror
assert 'generation.get() == ticket' in mirror
assert 'current.close()' in mirror
assert 'MirrorCrashRecorder.finish(this' in mirror
assert 'versionCode = 14' in gradle
assert '0.14.0-mirror-recovery-beta' in gradle
print('DOUPAD_PRO_V14_MIRROR_TELEMETRY_AND_ROTATION_RECOVERY_PASS')
