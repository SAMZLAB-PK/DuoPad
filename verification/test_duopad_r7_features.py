from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt').read_text()
gradle=(root/'app/build.gradle.kts').read_text()
workflow=(root/'.github/workflows/build-apk.yml').read_text()
assert 'import android.view.WindowManager' in s
assert s.count('window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)') == 2
assert s.count('window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)') == 2
assert 'override fun onPause()' in s and 'override fun onResume()' in s and 'override fun onDestroy()' in s
assert 'versionCode = 19' in gradle
assert 'versionName = "0.16.3-r7-mirror-screen-awake"' in gradle
assert 'test_duopad_r7_features.py' in workflow
assert 'DuoPad-Pro-v0.16.3-R7-debug.apk' in workflow
print('DOUPAD_R7_KEEP_SCREEN_ON_LIFECYCLE_CHECKS_PASS')
