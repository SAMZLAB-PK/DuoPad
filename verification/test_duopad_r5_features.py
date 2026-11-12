from pathlib import Path

root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/com/unipoint'
shell=(java/'presentation/ui/DoupadShell.kt').read_text()
main=(java/'presentation/ui/MainActivity.kt').read_text()
theme=(java/'presentation/ui/theme/Theme.kt').read_text()
mirror=(java/'presentation/ui/DoupadMirrorActivity.kt').read_text()
diag=(java/'presentation/ui/screens/android/MirrorDiagnosticsScreen.kt').read_text()
recorder=(java/'core/util/MirrorCrashRecorder.kt').read_text()
build=(root/'app/build.gradle.kts').read_text()
assert 'Light' in shell and 'Dark' in shell
assert 'listOf("All","Android TV","PC","Saved")' in shell
assert '"saveDevice"' in shell and 'Save connected device?' in shell
assert 'automaticReconnect' in shell and 'SAVED_DEVICE_RECONNECT' in shell
assert 'reconnectAttempts < 3' in shell
assert 'saveTargets(context,targets)' in shell
assert 'val previous=targets.firstOrNull' in shell
assert 'TextButton(onClick={onAppearanceChange("bento")})' in shell
assert 'val currentlyDark=appearance=="dark"' in shell
assert 'appearancePrefs.edit().putString("appearance"' in main
assert 'HandmadeLight' in theme and 'GraphiteDark' in theme and 'BentoClayLight' in theme
assert (java/'presentation/ui/theme/Color.kt').exists()
assert (java/'presentation/ui/components/BentoCard.kt').exists()
assert 'fun clear(context: Context)' in recorder
assert 'Clear old logs' in diag and 'confirmClear' in diag
assert '"clearLogs"' in shell
assert 'AUTO_PROFILE_RETRY' in mirror
assert 'current.visibilityMonitor.observe(' in mirror
assert 'scheduleVisibleVideoSample(ticket,current,1500L)' in mirror
assert 'VideoProfile(640,24,1_200_000,true,true,null,true)' in mirror
assert 'videoEncoder' not in mirror  # avoid accidental bogus argument
assert any(token in build for token in ['versionCode = 18', 'versionCode = 19', 'versionCode = 20', 'versionCode = 21', 'versionCode = 22', 'versionCode = 23', 'versionCode = 24', 'versionCode = 25', 'versionCode = 26', 'versionCode = 27', 'versionCode = 28', 'versionCode = 29', 'versionCode = 31'])
assert 'applicationId = "com.samzlab.duopad"' in build
print('DOUPAD_R5_UI_SAVED_THEME_DIAGNOSTICS_MIRROR_SOURCE_PASS')
