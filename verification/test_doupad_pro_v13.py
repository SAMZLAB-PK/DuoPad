from pathlib import Path
root=Path(__file__).resolve().parents[1]
shell=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
video=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text()
pad=(root/"app/src/main/java/com/unipoint/presentation/ui/screens/android/ProControlScreens.kt").read_text()
build=(root/"app/build.gradle.kts").read_text()
assert 'var page by rememberSaveable { mutableStateOf(4) }' in shell
assert 'Text("Last connected"' in shell and 'Text("Select device"' in shell
assert 'listOf("Android TV","All","Saved","PC")' in shell
assert '"Tools" -> page=3' in shell
assert 'Triple("Tools",Icons.Default.Handyman,"tools")' in shell
assert 'Triple("Apps",Icons.Default.GridView,"apps")' in shell
assert 'Triple("Shell",Icons.Default.Terminal,"shellFull")' in shell
assert 'Triple("Info",Icons.Default.Info,"infoFull")' in shell
for name in ('Install','Upload to Downloads','File manager','Channels','Screen mirror','Gamepad','Screenshot','Screen record','Clear cache','Screensaver','Power Menu'):
    assert f'"{name}"' in shell
assert 'AudioTrack.Builder()' in video and 'AUDIO_READY: raw PCM stereo' in video
assert 'audio=${profile.audio} audio_codec=raw' in video
assert 'current.audio = adb.open("localabstract:scrcpy_$id")' in video
assert video.index('current.audio = adb.open(') < video.index('current.control = adb.open(')
assert 'fitSurface(width,height)' in video and 'surface.scaleX' in video
assert 'private fun createControls()' in video
assert 'saveScreenshot()' in video
assert 'startVoice()' in video
assert 'onConfigurationChanged' in video
assert 'PadButton("L3"' in pad and 'PadButton("R3"' in pad
assert 'Stick("L"' in pad and 'Stick("R"' in pad
assert 'Directions(size)' in pad and 'Face(size)' in pad
assert 'LB' in pad and 'LT' in pad and 'RT' in pad and 'RB' in pad
assert 'versionCode = 14' in build
print('DOUPAD_V013_DEVICES_TOOLS_GAMEPAD_MIRROR_AUDIO_SOURCE_PASS')
