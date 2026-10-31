from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
adb=(root/"app/src/main/java/com/unipoint/data/remote/AdbDataSource.kt").read_text()
screens=(root/"app/src/main/java/com/unipoint/presentation/ui/screens/android/ProControlScreens.kt").read_text()
strings=(root/"app/src/main/res/values/strings.xml").read_text()
icon=(root/"app/src/main/res/drawable/ic_launcher.xml").read_text()
build=(root/"app/build.gradle.kts").read_text()
assert '<string name="app_name">DouPad Pro</string>' in strings
assert 'versionName = "0.8.0-startup-crash-beta"' in build and 'versionCode = 8' in build
assert 'listOf("Saved","All","TV","PC")' in ui
assert 'Live video · H.264 low-latency' in ui
assert 'AdbShellScreen' in ui and 'AndroidGamepadScreen' in ui and 'RemoteMappingScreen' in ui
assert 'AndroidKeyboardScreen' in ui and 'listOf("D-pad","Touchpad","Air mouse","Keyboard")' in ui
assert 'Icons.Default.Mic,"Voice input"' in ui
assert 'RemoteKeyMapping.resolve(context,code)' in ui
assert 'reconnectAuthorizedLocked' in adb and 'isRecoverableAdbError' in adb
assert 'Screenshot failed after ADB reconnect' in adb
assert 'object RemoteKeyMapping' in screens
assert 'fun AdbShellScreen' in screens
assert 'fun AndroidGamepadScreen' in screens
assert 'fun AndroidKeyboardScreen' in screens
assert '#0B1727' in icon and '#15C7C9' in icon
print("DOUPAD_PRO_V05_SOURCE_VERIFY_OK")
