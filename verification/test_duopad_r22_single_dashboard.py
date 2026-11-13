from pathlib import Path
base=Path(__file__).resolve().parents[1]
shell=(base/'app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt').read_text()
workspace=(base/'app/src/main/java/com/unipoint/presentation/ui/screens/pc/PcWorkspaceScreen.kt').read_text()
keyboard=(base/'app/src/main/java/com/unipoint/presentation/ui/screens/pc/KeyboardScreen.kt').read_text()
gradle=(base/'app/build.gradle.kts').read_text()
workflow=(base/'.github/workflows/build-apk.yml').read_text()
# Screenshots define the single, four-card PC Workspace dashboard.
assert 'Title("Windows PC Workspace","Control desktop, files, drives and PC information.")' in shell
for page in ("Screen","Files","Info","Keyboard"):
    assert 'openPcTool("'+page+'")' in shell, page
assert 'pcWorkspaceTab="Workspace"' not in shell
assert 'Open full PC Workspace' not in shell
assert 'Welcome to your PC' not in workspace
assert 'listOf("Workspace", "Screen", "Files", "Info")' not in workspace
assert '"Workspace" -> {' not in workspace
assert 'initialTab: String = "Screen"' in workspace
assert 'openPcTool(label)' in shell
assert 'if (!connected || state.mode != AppMode.PC)' in shell
# No background reconnect should change navigation.
marker='vm.connectNetwork(host,port,trustedPin,onSuccess={'
assert marker in shell
callback=shell.split(marker,1)[1].split('})',1)[0]
assert 'page=3' not in callback and 'detail="pcWorkspace"' not in callback
assert 'PC_AUTO_RECONNECTED:' in callback
assert 'page=4\n        detail=null\n        vm.scanAndroid()' in shell
# The PC screen is a dedicated full-screen landscape route.
assert 'BackHandler(tab == "Screen") { onBack() }' in workspace
assert 'onExit=onBack,' in workspace
assert 'activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT' in workspace
assert 'FLAG_KEEP_SCREEN_ON' in workspace
assert 'onDispose { activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }' in keyboard
# Only the chosen data panel renders after selection; errors must not spin forever.
assert 'Text("System overview"' in workspace
assert 'PC drives & files' in workspace
assert 'System information is unavailable. Tap Refresh to retry.' in workspace
assert 'Drives unavailable. Tap Refresh to retry.' in workspace
# Preserve logo crash fix and Play Store's 2026 target SDK threshold.
assert 'painterResource(R.drawable.ic_launcher)' not in shell
assert 'painterResource(R.drawable.duopad_original_logo)' in shell
assert 'targetSdk = 36' in gradle
assert 'versionCode = 34' in gradle
assert 'versionName = "0.18.7-r22-single-workspace"' in gradle
assert 'DuoPad-Pro-v0.18.7-R22-debug.apk' in workflow
print('DOUPAD_R22_SINGLE_PC_DASHBOARD_NAVIGATION_ORIENTATION_PASS')
