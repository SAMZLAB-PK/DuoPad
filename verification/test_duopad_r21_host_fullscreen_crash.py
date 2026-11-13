from pathlib import Path

base=Path(__file__).resolve().parents[1]
src=base/'app/src/main/java/com/unipoint'
pc=(src/'data/remote/NetworkPcDataSource.kt').read_text()
shell=(src/'presentation/ui/DoupadShell.kt').read_text()
workspace=(src/'presentation/ui/screens/pc/PcWorkspaceScreen.kt').read_text()
keyboard=(src/'presentation/ui/screens/pc/KeyboardScreen.kt').read_text()
manifest=(base/'app/src/main/AndroidManifest.xml').read_text()
logo=(base/'app/src/main/res/drawable/ic_launcher.xml').read_text()
host=(base/'host/python/unipoint_host.py').read_text()
host_gui=(base/'host/python/duopad_host_gui.py').read_text()
workflow=(base/'.github/workflows/build-pc-host.yml').read_text()
gradle=(base/'app/build.gradle.kts').read_text()
assert 'catch (error: Exception)' in pc and 'PC_HEARTBEAT_IO_ERROR' in pc
assert 'if (output === out) disconnect()' in pc
assert 'pcWorkspaceTab="Workspace"' in shell
assert 'onKeyboard={detail="pcKeyboard"}' in shell
assert 'setCaptureActivity(DuoPadPortraitCaptureActivity::class.java)' in shell
assert 'DuoPadPortraitCaptureActivity' in manifest
assert 'SCREEN_ORIENTATION_SENSOR_LANDSCAPE' in workspace
assert 'FLAG_KEEP_SCREEN_ON' in workspace
assert 'Phone touchpad' in workspace and 'On PC screen' in workspace
assert 'DuoPadPcFullScreen(' in workspace
assert 'Windows On-Screen Keyboard' in keyboard
assert 'COMPUTER' in workspace and 'SYSTEM & STORAGE' in workspace
assert 'duopad_original_logo' in logo
# Compose painterResource cannot decode a <bitmap> XML drawable. It must load
# the original PNG directly, otherwise the app repeatedly crashes on startup.
assert 'painterResource(id = R.drawable.duopad_original_logo)' in shell
assert shell.count('painterResource(R.drawable.duopad_original_logo)') == 3
assert 'painterResource(R.drawable.ic_launcher)' not in shell
assert 'painterResource(id = R.drawable.ic_launcher)' not in shell

assert (base/'app/src/main/res/drawable-nodpi/duopad_original_logo.png').is_file()
assert 'def serve(' in host and 'stop_event' in host
assert '100.64.0.0/10' in host
assert 'class HostApp:' in host_gui and 'def load_pin' in host_gui
assert 'DuoPad-PC-Host-Pro' in workflow
assert 'versionCode = 33' in gradle and '0.18.6-r21-pc-host-pro' in gradle
print('DOUPAD_R21_PC_HOST_BRANDING_FULLSCREEN_HEARTBEAT_OK')
