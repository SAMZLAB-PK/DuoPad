from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
mouse=(root/"app/src/main/java/com/unipoint/presentation/ui/screens/android/AndroidMousePadScreen.kt").read_text()
game=(root/"app/src/main/java/com/unipoint/presentation/ui/screens/android/ProControlScreens.kt").read_text()
apps=(root/"app/src/main/java/com/unipoint/presentation/ui/screens/android/AppManagerScreen.kt").read_text()
video=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text()
build=(root/"app/build.gradle.kts").read_text()
assert 'listOf("Android TV","All","Saved","PC")' in ui
assert 'val saved=targets.distinctBy' in ui
assert 'Triple("Red",183' in ui and 'Triple("Green",184' in ui
assert 'Triple("Yellow",185' in ui and 'Triple("Blue",186' in ui
assert 'YouTube' in ui and 'Netflix' in ui and 'Prime' in ui
assert 'Live video · H.264 low-latency' in ui
assert 'mutableFloatStateOf(0.95f)' in mouse
assert 'Color(0xFFFCFAF8)' in mouse
assert 'BoxWithConstraints(' in game and 'maxWidth*(if(portrait)' in game
assert 'Semaphore(2)' in apps and 'queue.withPermit' in apps
assert 'VideoProfile(960, 24, 2_000_000, true, true)' in video
assert 'VideoProfile(720, 15, 1_200_000, true, false)' in video
assert 'video_codec=h264' in video
assert 'versionCode = 15' in build and '0.15.0-tvbox-encoder-beta' in build
print("DOUPAD_PRO_V06_SOURCE_CHECKS_PASS")
