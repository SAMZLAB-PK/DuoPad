from pathlib import Path
root=Path(__file__).resolve().parents[1]
shell=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt').read_text()
new=(root/'app/src/main/java/com/unipoint/presentation/ui/screens/android/DeviceToolsScreens.kt').read_text()
build=(root/'app/build.gradle.kts').read_text()
w=(root/'.github/workflows/build-apk.yml').read_text()
assert 'Triple("Install",Icons.Default.InstallMobile,"install")' in shell
assert 'Triple("Channels",Icons.Default.LiveTv,"channels")' in shell
assert '"install"->{InstallApkScreen(onBack=back);return}' in shell
assert '"channels"->{ChannelManagerScreen(onBack=back);return}' in shell
assert '"apps"->{AppManagerScreen(onBack=back);return}' in shell
assert 'if(isTv) {page=3;detail="install"}' in shell
assert 'Triple("Install",Icons.Default.InstallMobile,"apps")' not in shell
assert 'Triple("Channels",Icons.Default.LiveTv,"apps")' not in shell
assert 'val currentlyDark = appearance == "dark"' in shell
assert 'onClick={onAppearanceChange(if(currentlyDark) "light" else "dark")}' in shell
assert 'IconButton(onClick={vm.scanNearby()}){Icon(Icons.Default.Refresh,"Refresh",tint=Ink)}' not in shell
assert 'contentDescription="Original DuoPad logo"' in shell
assert 'painterResource(R.drawable.ic_launcher)' in shell
assert 'fun InstallApkScreen(' in new and 'ActivityResultContracts.OpenDocument()' in new
assert 'AdbCommand.Install(copied.absolutePath)' in new
assert 'fun ChannelManagerScreen(' in new and 'KEYCODE' not in new
assert all(f'key({k})' in new for k in (170,178,172,167,166))
assert '"monkey -p ${app.packageName}' in new
assert 'versionCode = 31' in build and 'versionName = "0.18.2-r17-pc-pin-handshake"', 'versionName = "0.18.4-r19-branded-header"' in build
assert 'applicationId = "com.samzlab.duopad"' in build
assert 'test_duopad_r14_ui_navigation.py' in w
assert 'DuoPad-Pro-v0.18.4-R19-debug.apk' in w
print('R14_UI_NAVIGATION_THEME_BRANDING_PASS')
