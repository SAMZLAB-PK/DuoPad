from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
strings = (ROOT / 'app/src/main/res/values/strings.xml').read_text(encoding='utf-8')
theme = (ROOT / 'app/src/main/java/com/unipoint/presentation/ui/theme/Theme.kt').read_text(encoding='utf-8')
nav = (ROOT / 'app/src/main/java/com/unipoint/presentation/ui/navigation/NavHost.kt').read_text(encoding='utf-8')
build = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')

assert '<string name="app_name">DOUPAD</string>' in strings
assert 'notification_title">DOUPAD Active<' in strings
assert 'darkTheme: Boolean = false' in theme
assert 'const val HOME = "home"' in nav
assert 'const val ANDROID_REMOTE = "android_remote"' in nav
assert 'const val SCREEN_MIRROR = "screen_mirror"' in nav
assert 'const val TOOLS = "tools"' in nav
assert 'versionName = "4.0.0-android-engine"' in build
assert 'versionCode = 10' in build
assert 'HOST_RELEASES_URL' in build
qr = (ROOT / 'app/src/main/java/com/unipoint/presentation/ui/screens/common/QrConnectScreen.kt').read_text(encoding='utf-8')
pcnet = (ROOT / 'app/src/main/java/com/unipoint/data/remote/NetworkPcDataSource.kt').read_text(encoding='utf-8')
repo = (ROOT / 'app/src/main/java/com/unipoint/data/repository/ConnectionRepositoryImpl.kt').read_text(encoding='utf-8')
assert 'ScanContract()' in qr
assert 'PcPairingUri.parse' in qr
assert 'PcDiscoveryProtocol.REQUESTS' in pcnet
assert 'PcDiscoveryPolicy.broadcastAddresses' in pcnet
assert 'pc_history_v1' in pcnet
assert 'PcReconnectPolicy.delaysMs' in repo

print('DOUPAD source contract: PASS')

shell = (ROOT / 'app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt').read_text(encoding='utf-8')
bt = (ROOT / 'app/src/main/java/com/unipoint/data/remote/BluetoothHidDataSource.kt').read_text(encoding='utf-8')
assert 'ActivityResultContracts.RequestMultiplePermissions()' in shell
assert 'Manifest.permission.BLUETOOTH_CONNECT' in shell
assert 'BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE' in shell
assert 'Bluetooth Direct' in shell
assert 'No PC app required' in shell
assert 'DOUPAD Host required for Wi-Fi' in shell
assert 'vm.scanPc()' in shell
assert 'state.pcDevices' in shell
assert 'BuildConfig.HOST_RELEASES_URL' in shell
assert 'BluetoothHidReports.REPORT_DESCRIPTOR' in bt
assert 'BluetoothHidReports.keyboardReport' in bt
assert 'BluetoothHidDevice.SUBCLASS1_COMBO' in bt
assert '"DOUPAD Input"' in bt
assert 'bluetooth.isConnected.collect' in repo
