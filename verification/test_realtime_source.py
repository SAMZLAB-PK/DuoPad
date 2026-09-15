from pathlib import Path
root = Path(__file__).resolve().parents[1]
mouse = (root / 'app/src/main/java/com/unipoint/presentation/ui/screens/android/AndroidMousePadScreen.kt').read_text()
mirror = (root / 'app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt').read_text()
adb = (root / 'app/src/main/java/com/unipoint/data/remote/AdbDataSource.kt').read_text()
protocol = (root / 'app/src/main/java/com/unipoint/data/remote/ScrcpyControlProtocol.kt').read_text()
vm = (root / 'app/src/main/java/com/unipoint/presentation/viewmodel/MainViewModel.kt').read_text()

assert 'AdbCommand.PrepareRealtimeInput' in mouse, 'mouse pad must prewarm persistent realtime control'
assert 'AdbCommand.PointerMove' in mouse, 'mouse movement must use realtime pointer command'
assert 'AdbCommand.PointerClick' in mouse, 'mouse clicks must use realtime pointer command'
assert 'AdbCommand.PointerScroll' in mouse, 'scroll must use realtime pointer command'
assert 'input mouse motionevent MOVE' not in mouse, 'mouse screen must not shell each move'

assert 'control=true' in mirror and 'control=false' not in mirror, 'mirror must enable scrcpy control'
assert 'max_fps=30' in mirror, 'mirror must use the smooth 30 fps TV profile'
assert 'video_bit_rate=4000000' in mirror, 'mirror must use a 4 Mbps target bitrate'
assert 'current.control=adb.open("localabstract:scrcpy_$id")' in mirror.replace(' ', ''), 'mirror must open second control socket'
assert 'setOnTouchListener' in mirror, 'mirror surface must send touch events'
assert 'ScrcpyControlProtocol.touch' in mirror, 'mirror must serialize touch via scrcpy protocol'

assert 'video=false audio=false control=true' in adb, 'mouse pad realtime service must be control-only'
assert 'realtimeRetryAfterMs' in adb, 'failed realtime setup must have a retry cooldown'
assert 'stream.sink.write(bytes)' in adb, 'realtime commands must use persistent socket'
input_tap = adb.split('suspend fun inputTap', 1)[1].split('suspend fun inputSwipe', 1)[0]
assert 'wm size' not in input_tap, 'tap fast path must not shell just to discover display size'
assert vm.count('AdbCommand.PrepareRealtimeInput') >= 2, 'Android connect paths must prewarm realtime control'
assert 'fun touch(' in protocol and 'fun scroll(' in protocol and 'fun key(' in protocol, 'protocol serializer incomplete'
print('REALTIME_SOURCE_VERIFY_OK')
