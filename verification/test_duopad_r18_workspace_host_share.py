from pathlib import Path
root=Path(__file__).resolve().parents[1]
shell=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt').read_text()
workspace=(root/'app/src/main/java/com/unipoint/presentation/ui/screens/pc/PcWorkspaceScreen.kt').read_text()
client=(root/'app/src/main/java/com/unipoint/data/remote/PcWorkspaceClient.kt').read_text()
sharing=(root/'app/src/main/java/com/unipoint/presentation/ui/DuoPadHostShare.kt').read_text()
workflow=(root/'.github/workflows/build-apk.yml').read_text()
gradle=(root/'app/build.gradle.kts').read_text()
assert 'pcWorkspaceTab=label; detail="pcWorkspace"' in shell
assert 'pcWorkspaceTab="Files"' in shell and 'pcWorkspaceTab="Info"' in shell
assert 'initialTab=pcWorkspaceTab' in shell and 'rememberSaveable(initialTab)' in workspace
assert 'PC_WORKSPACE_' in client and 'INFO_OK' in client and 'DRIVES_OK' in client
assert 'SCREEN_FRAME_OK' in client and 'REQUEST_FAILED' in client
assert 'lastError != null' in workspace and 'Retry ${tab}' in workspace
assert 'val pcQrScanner = rememberLauncherForActivityResult(ScanContract())' in shell
assert 'PcPairingUri.parse(raw)' in shell
assert 'DuoPadHostShare.share(context)' in shell and 'ZipOutputStream' in sharing
assert 'Intent.ACTION_SEND' in sharing and 'FileProvider.getUriForFile' in sharing
assert 'Intent.FLAG_GRANT_READ_URI_PERMISSION' in sharing
for f in ['host/python/unipoint_host.py','host/python/pc_workspace.py','host/python/requirements.txt',
          'host/windows/Install-and-Run-DOUPAD.bat','host/windows/Run-DOUPAD-Host.bat',
          'host/windows/SETUP-FIREWALL-ONCE.bat','host/WINDOWS-SETUP.md']:
    assert (root/'app/src/main/assets/pc-host'/f).read_bytes()==(root/f).read_bytes(), f
assert 'versionCode = 32' in gradle
assert 'test_duopad_r18_workspace_host_share.py' in workflow
print('R18_PC_WORKSPACE_QR_SHARE_CHECKS_PASS')
