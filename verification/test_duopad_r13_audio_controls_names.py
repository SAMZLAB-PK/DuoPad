from pathlib import Path
root = Path(__file__).resolve().parents[1]
mirror=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt').read_text()
adb=(root/'app/src/main/java/com/unipoint/data/remote/AdbDataSource.kt').read_text()
shell=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt').read_text()
workflow=(root/'.github/workflows/build-apk.yml').read_text()
build=(root/'app/build.gradle.kts').read_text()
assert 'val profiles=if(aceconnMode)' in mirror
for profile in ['VideoProfile(640,24,1_200_000,true,true)',
                'VideoProfile(640,24,1_200_000,true,true,null,true)',
                'VideoProfile(720,20,1_250_000,true,true,"c2.android.avc.encoder")',
                'VideoProfile(640,12,750_000,true,true)',
                'VideoProfile(720,30,2_400_000,true,true)',
                'VideoProfile(640,24,1_350_000,true,true)']:
    assert profile in mirror, profile
assert 'audioRequested=true;' in mirror and 'audio_codec=raw' in mirror
assert 'AUDIO_UNAVAILABLE:' in mirror and 'AUDIO_READY:' in mirror
assert 'MIRROR_DOUBLE_BACK_EXIT' in mirror
assert 'MIRROR_CONTROLS_AUTO_HIDDEN' in mirror
assert 'positionControlsNearVideo()' in mirror
assert 'override fun onBackPressed()' in mirror
assert 'onBackInvokedDispatcher.registerOnBackInvokedCallback(' in mirror
assert 'onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)' in mirror
assert 'val close=remoteButton("×"){requestMirrorExit()}' in mirror
assert 'val back=remoteButton("‹"){handleMirrorBack()}' in mirror
assert 'AUTO_PROFILE_DECODER_RELEASED:' in mirror
assert 'PROFILE_RETRY_OLD_SOCKET_STALE:' in mirror
assert 'decodeBacklogFrames=' in mirror
assert 'private fun localHostname(ip: String): String?' in adb
assert 'duopad_verified_device_names' in adb
assert 'val name=saved?.customName ?: saved?.name ?: device.name' in shell
assert 'versionCode = 31' in build
assert 'versionName = "0.18.4-r19-branded-header"' in build
assert 'DuoPad-Pro-v0.18.4-R19-debug.apk' in workflow
print('R13_AUDIO_DOUBLE_BACK_VISIBLE_FRAME_DISCOVERY_TEST_PASS')
