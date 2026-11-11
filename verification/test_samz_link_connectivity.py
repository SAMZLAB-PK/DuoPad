from pathlib import Path
root = Path(__file__).resolve().parents[1]
adb = (root / "app/src/main/java/com/unipoint/data/remote/AdbDataSource.kt").read_text()
vm = (root / "app/src/main/java/com/unipoint/presentation/viewmodel/MainViewModel.kt").read_text()
ui = (root / "app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
assert 'for (attempt in 1..5)' in adb
assert 'shell("echo samz-link-ready")' in adb
assert 'keyPairOrNull() ?: return@withContext' in adb
assert 'fun scanNearby()' in vm and 'scanAndroid()\n        scanPc()' in vm
assert 'state.pcDevices.distinctBy' in ui and 'state.androidDevices.distinctBy' in ui
assert 'if(connectRequested && status.state==ConnectionState.ERROR)' in ui
assert 'vm.connectNetwork(ip,p,pin.ifBlank{null},onSuccess={' in ui
print("SAMZ LINK CONNECTIVITY SOURCE VERIFY OK")
