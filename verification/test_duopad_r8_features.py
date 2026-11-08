from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/unipoint'
shell=(src/'presentation/ui/DoupadShell.kt').read_text()
app=(src/'presentation/ui/screens/android/AppManagerScreen.kt').read_text()
shellScreen=(src/'presentation/ui/screens/android/ProControlScreens.kt').read_text()
mirror=(src/'presentation/ui/DoupadMirrorActivity.kt').read_text()
gradle=(root/'app/build.gradle.kts').read_text()
assert 'applicationId = "com.samzlab.duopad"' in gradle
assert any(x in gradle for x in ['versionCode = 20', 'versionCode = 21', 'versionCode = 22', 'versionCode = 23', 'versionCode = 24', 'versionCode = 25']) and any(x in gradle for x in ['versionName = "0.17.0-r8"', 'versionName = "0.17.0-r9"', 'versionName = "0.17.1-r10"', 'versionName = "0.17.2-r11"', 'versionName = "0.17.3-r12"', 'versionName = "0.17.4-r13"'])
assert 'DropdownMenu(' in app and 'expandedAppMenu' in app
assert 'Text("Force stop")' in app and 'Text("Uninstall"' in app
assert 'modifier=modifier.height(94.dp)' in shell
assert 'showNumberPad' in shell and 'CompactRemoteKey' in shell and 'VolumeKnob' in shell
assert 'Icons.Default.Dialpad' in shell
assert 'DuoPad logo' in shell
assert 'presetsExpanded' in shellScreen and 'SelectionContainer' in shellScreen
assert 'ClipData.newPlainText("DuoPad Shell output", output)' in shellScreen
assert 'smooth_tv_mode' in mirror and 'smooth_tv_mode' in shell
assert 'snapshotMode' in mirror and 'SNAPSHOT_FRAME' in mirror
assert 'VideoProfile(640,12,750_000,true,true)' in mirror
print('DOUPAD_R8_BRANDING_UI_MIRROR_SOURCE_PASS')
