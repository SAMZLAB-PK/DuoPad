from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
build = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
adb = (ROOT / 'app/src/main/java/com/unipoint/data/remote/AdbDataSource.kt').read_text(encoding='utf-8')
protocol = (ROOT / 'app/src/main/java/com/unipoint/data/remote/ScrcpyControlProtocol.kt').read_text(encoding='utf-8')
mirror = (ROOT / 'app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt').read_text(encoding='utf-8')
manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')

assert 'versionName = "4.0.0-android-engine"' in build
assert 'versionCode = 10' in build

# Android-device discovery must not stop at .40; boxes commonly get higher DHCP addresses.
assert '1..254' in adb, 'primary LAN ADB scan must cover the complete /24 address range'

# A real mouse cursor requires UHID when supported, with SDK/touch fallback.
for symbol in ('uhidCreate', 'uhidInput', 'uhidDestroy', 'UHID_MOUSE_REPORT_DESCRIPTOR'):
    assert symbol in protocol, f'missing scrcpy UHID primitive: {symbol}'
assert 'ensureUhidMouseLocked' in adb, 'realtime input must initialize UHID mouse'
assert 'ScrcpyControlProtocol.uhidInput' in adb, 'mouse moves must send relative UHID reports'
assert 'ScrcpyControlProtocol.touch' in adb, 'SDK/touch fallback must remain available'

# Smooth default mirror: one H.264 pipeline, conservative 30fps/4Mbps baseline and immersive UI.
assert 'max_fps=30' in mirror
assert 'video_bit_rate=4000000' in mirror
assert 'WindowInsets.Type.systemBars()' in mirror
assert 'SYSTEM_UI_FLAG_IMMERSIVE_STICKY' in mirror

# Screenshot should stream bytes directly and validate PNG before the remote-file fallback.
assert 'readByteArray()' in adb, 'screenshot fast path must read binary PNG directly'
assert 'isPng' in adb, 'screenshot fast path must validate PNG signature'
assert 'screencap -p' in adb, 'screenshot compatibility fallback must remain'

assert 'keyboardHidden' in manifest, 'mirror activity must survive rotation without recreation churn'
print('ENGINE4_SOURCE_VERIFY_OK')
