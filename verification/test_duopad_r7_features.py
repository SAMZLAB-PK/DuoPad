from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt').read_text()
gradle=(root/'app/build.gradle.kts').read_text()
workflow=(root/'.github/workflows/build-apk.yml').read_text()
assert 'import android.view.WindowManager' in s
assert s.count('window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)') == 2
assert s.count('window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)') == 2
assert 'override fun onPause()' in s and 'override fun onResume()' in s and 'override fun onDestroy()' in s
assert any(x in gradle for x in ['versionCode = 20', 'versionCode = 21', 'versionCode = 22', 'versionCode = 23', 'versionCode = 24', 'versionCode = 25'])
assert any(x in gradle for x in ['versionName = "0.17.0-r8"', 'versionName = "0.17.0-r9"', 'versionName = "0.17.1-r10"', 'versionName = "0.17.2-r11"', 'versionName = "0.17.3-r12"', 'versionName = "0.17.4-r13"'])
assert 'test_duopad_r7_features.py' in workflow
assert any(x in workflow for x in ['DuoPad-Pro-v0.17.0-R8-debug.apk','DuoPad-Pro-v0.17.0-R9-debug.apk', 'DuoPad-Pro-v0.17.1-R10-debug.apk', 'DuoPad-Pro-v0.17.2-R11-debug.apk', 'DuoPad-Pro-v0.17.3-R12-debug.apk', 'DuoPad-Pro-v0.17.4-R13-debug.apk'])
print('DOUPAD_R7_KEEP_SCREEN_ON_LIFECYCLE_CHECKS_PASS')
