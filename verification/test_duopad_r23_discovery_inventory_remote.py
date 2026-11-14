"""R23 discovery, inventory, theme and remote controls regression contracts."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda name: (root / name).read_text(encoding="utf-8")
shell = read("app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt")
workspace = read("app/src/main/java/com/unipoint/presentation/ui/screens/pc/PcWorkspaceScreen.kt")
host = read("host/python/pc_workspace.py")
embedded = read("app/src/main/assets/pc-host/host/python/pc_workspace.py")
discovery = read("app/src/main/java/com/unipoint/data/remote/NetworkPcDataSource.kt")
theme = read("app/src/main/java/com/unipoint/presentation/ui/theme/Theme.kt")
activity = read("app/src/main/java/com/unipoint/presentation/ui/MainActivity.kt")

assert "LaunchedEffect(page) { if(page == 4) vm.scanNearby() }" in shell
assert "vm.scanPc()" in shell
assert "return@withContext sortDevices(found.values)" not in discovery
assert 'showManualPc' in shell and 'Enter PC address manually' in shell
assert 'openPcTool("Software")' in shell
assert '"Software" -> {' in workspace
assert "installedSoftware" in workspace and "installedSoftware" in host
assert "cpuManufacturer" in workspace and "cpuManufacturer" in host
assert "ramModules" in workspace and "ramModules" in host
assert "_windows_hardware" in host and "Win32_PhysicalMemory" in host
assert "GetCursorPos" in host
assert embedded == host, "Embedded Host must match shipped standalone Host"
assert 'Color(0xFFD1D5DB)' in theme
assert 'Triple("Green",184,Color(0xFF278B54))' in shell
assert "onClick={key(176)}" in shell and "onClick={key(178)}" in shell
assert 'animateFloatAsState' in shell
assert "AppUpdateManagerFactory.create(this)" in activity
print("DOUPAD_R23_DISCOVERY_INVENTORY_REMOTE_PASS")
