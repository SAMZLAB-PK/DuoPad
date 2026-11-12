from pathlib import Path
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt').read_text()
g=(p/'app/build.gradle.kts').read_text()
w=(p/'.github/workflows/build-apk.yml').read_text()
assert 'private fun DuoPadBrandHeader(' in s
assert s.count('DuoPadBrandHeader(') == 3
assert 'text = "DOUPAD"' in s
assert 'fontWeight = FontWeight.Black' in s
assert 'fontFamily = FontFamily.SansSerif' in s
assert 'text = "Control your PC & TV from your phone"' in s
assert 'contentDescription = "Original DuoPad D logo"' in s
assert 'contentDescription = "DuoPad menu"' in s
assert 'onMore = { dialog = "settings" }' in s
assert 'onMore = { page = 5 }' in s
assert 'onAppearanceChange(if (currentlyDark) "light" else "dark")' in s
assert 'versionCode = 32' in g
assert 'versionName = "0.18.5-r20-pc-experience"' in g
assert 'DuoPad-Pro-v0.18.5-R20-debug.apk' in w
print('DOUPAD_R19_BRANDING_VERIFIED')
