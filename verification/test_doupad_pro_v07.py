from pathlib import Path
root=Path(__file__).resolve().parents[1]
video=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text()
screen=(root/"app/src/main/java/com/unipoint/presentation/ui/screens/android/DeviceInfoProScreen.kt").read_text()
ui=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
build=(root/"app/build.gradle.kts").read_text()
assert 'VideoProfile(960, 24, 2_000_000, true, true)' in video
assert 'VideoProfile(720, 15, 1_200_000, true, false)' in video
assert 'KEY_LOW_LATENCY' in video
assert 'AtomicBoolean(false)' in video
assert 'firstFrame.compareAndSet(false, true)' in video
assert 'doupad_mirror_diagnostics.txt' in video
assert 'archiveLocks' in (root/"app/src/main/java/com/unipoint/data/remote/AdbDataSource.kt").read_text()
assert 'Dadb.create(host, lastConnectedPort, keys' in (root/"app/src/main/java/com/unipoint/data/remote/AdbDataSource.kt").read_text()
assert 'setOnLongClickListener' in video
assert 'fun DeviceInfoProScreen(' in screen
assert 'MirrorDiagnosticsScreen' in ui
assert 'Detail("ram"' in screen
assert 'Detail("ip"' in screen
assert '"infoFull"->{DeviceInfoProScreen' in ui
assert 'versionCode = 13' in build
assert 'versionName = "0.13.0-connected-tools-audio-beta"' in build
print("DOUPAD_PRO_V07_VIDEO_DIAGNOSTICS_PASS")
