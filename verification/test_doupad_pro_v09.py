from pathlib import Path
root = Path(__file__).resolve().parents[1]
video = (root / "app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text()
build = (root / "app/build.gradle.kts").read_text()
assert "setContentView(root)" in video
assert "ACTIVITY_CONTENT_READY" in video
assert "root.post {" in video
assert "decor.isAttachedToWindow" in video
assert "decor.windowInsetsController" in video
assert "window.insetsController" not in video
assert "IMMERSIVE_OPTIONAL_FAILURE" in video
oncreate = video.split("override fun onCreate(", 1)[1].split("private fun enterImmersive()", 1)[0]
assert "enterImmersive()\n\n        root =" not in oncreate
assert oncreate.index("setContentView(root)") < oncreate.index("root.post {")
assert "MirrorCrashRecorder.started(this)" in oncreate
assert "versionCode = 9" in build
assert 'versionName = "0.9.0-immersive-startup-fix-beta"' in build
print("DOUPAD_PRO_V09_IMMERSIVE_STARTUP_CHECKS_PASS")
