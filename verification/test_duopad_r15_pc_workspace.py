from pathlib import Path
root=Path(__file__).resolve().parents[1]
server=(root/'host/python/unipoint_host.py').read_text()
workspace=(root/'host/python/pc_workspace.py').read_text()
client=(root/'app/src/main/java/com/unipoint/data/remote/PcWorkspaceClient.kt').read_text()
screen=(root/'app/src/main/java/com/unipoint/presentation/ui/screens/pc/PcWorkspaceScreen.kt').read_text()
shell=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt').read_text()
gradle=(root/'app/build.gradle.kts').read_text()
workflow=(root/'.github/workflows/build-apk.yml').read_text()
hostworkflow=(root/'.github/workflows/build-pc-host.yml').read_text()
assert any(x in gradle for x in ['versionCode = 27', 'versionCode = 28', 'versionCode = 29', 'versionCode = 30', 'versionCode = 28', 'versionCode = 29', 'versionCode = 30'])
assert any(x in gradle for x in ['versionName = "0.18.0-r15-pc-workspace"', 'versionName = "0.18.1-r16-pc-discovery-diagnostics"', 'versionName = "0.18.2-r17-pc-pin-handshake"', 'versionName = "0.18.3-r18-pc-workspace-qr-host-share"', 'versionName = "0.18.1-r16-pc-discovery-diagnostics"', 'versionName = "0.18.2-r17-pc-pin-handshake"', 'versionName = "0.18.3-r18-pc-workspace-qr-host-share"'])
assert 'PcWorkspaceService' in server and 'secrets.randbelow(100_000_000)' in server
assert 'client_ip.is_private or client_ip.is_loopback' in server
assert 'parser.add_argument("--no-pin"' in server
assert 'ptype in (0x30, 0x31, 0x32, 0x33, 0x34, 0x40' in server
assert '0x33' in workspace and '0x34' in workspace and '0x40' in workspace
assert 'tempfile.NamedTemporaryFile' in workspace and 'MAX_FILE_BYTES' in workspace
assert 'from PIL import Image' in workspace and 'import mss' in workspace
assert 'private val mutex = Mutex()' in client and 'connectIfNeeded()' in client
assert 'reader!!.readFully(bytes)' in client and 'reader!!.read(buffer' in client
assert 'fun PcWorkspaceScreen(' in screen and 'desktopFrame' in screen
assert 'CreateDocument' in screen and 'OpenDocument' in screen
assert '"pcWorkspace"' in shell and 'PcWorkspaceScreen(' in shell
assert 'if(target.pc) {' in shell and 'pin=""' in shell
assert 'test_duopad_r15_pc_workspace.py' in workflow
assert any(x in workflow for x in ['DuoPad-Pro-v0.18.0-R15-debug.apk', 'DuoPad-Pro-v0.18.2-R17-debug.apk', 'DuoPad-Pro-v0.18.3-R18-debug.apk','DuoPad-Pro-v0.18.2-R17-debug.apk', 'DuoPad-Pro-v0.18.3-R18-debug.apk'])
assert 'PyInstaller' in hostworkflow and 'python -m unittest discover' in hostworkflow
print('DUOPAD_R15_PC_WORKSPACE_CONTRACT_PASS')
