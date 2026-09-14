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
assert 'versionName = "3.1.0-realtime"' in build
print('DOUPAD source contract: PASS')
