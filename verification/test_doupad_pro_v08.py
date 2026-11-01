from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
app=(root/"app/src/main/java/com/unipoint/UniPointApp.kt").read_text()
video=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text()
report=(root/"app/src/main/java/com/unipoint/presentation/ui/screens/android/MirrorDiagnosticsScreen.kt").read_text()
recorder=(root/"app/src/main/java/com/unipoint/core/util/MirrorCrashRecorder.kt").read_text()
assert 'MirrorCrashRecorder.install(this)' in app
assert 'MirrorCrashRecorder.launch(context,' in ui
assert 'context.startActivity(intent)' in ui
assert ui.index('MirrorCrashRecorder.launch(context,') < ui.index('context.startActivity(intent)')
assert 'MirrorCrashRecorder.started(this)' in video
assert 'MirrorCrashRecorder.finish(this' in video
assert 'SURFACE_CREATED' in video
assert 'isSoftwareOnly' in video
assert 'safe_decoder' in video
assert 'Live video · Compatibility decoder' in ui
assert 'PREVIOUS_MIRROR_UNEXPECTED_EXIT' in recorder
assert 'UNCAUGHT_EXCEPTION' in recorder
assert 'ApplicationExitInfo.REASON_CRASH_NATIVE' in report
assert 'versionCode = 16' in (root/"app/build.gradle.kts").read_text()
print("DOUPAD_PRO_V08_CRASH_RECORDER_PASS")
