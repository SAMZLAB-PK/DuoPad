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
assert 'listOf("light" to "Light", "dark" to "Dark", "bento" to "Bento Clay")' in shell
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
assert 'VideoProfile(640,12,950_000,true,false,null,true)' in mirror
assert 'videoEncoder' not in mirror  # avoid accidental bogus argument
assert any(token in build for token in ['versionCode = 18', 'versionCode = 19'])
assert 'applicationId = "com.samz.samzlink"' in build
print('DOUPAD_R5_UI_SAVED_THEME_DIAGNOSTICS_MIRROR_SOURCE_PASS')
