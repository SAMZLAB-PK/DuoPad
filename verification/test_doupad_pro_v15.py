from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text()
shell=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
build=(root/"app/build.gradle.kts").read_text()
assert 'getBooleanExtra("aceconn_mode",false)' in src
assert '"TV_BOX_COMPATIBILITY"' in src
assert 'VideoProfile(720,15,1_200_000,true,true,"c2.android.avc.encoder")' in src
assert 'VideoProfile(640,12,900_000,true,true,"OMX.google.h264.encoder")' in src
assert 'VideoProfile(720,15,1_200_000,true,true)' in src
assert 'video_encoder=$it' in src
assert 'VideoProfile(960, 24, 2_000_000, true, true)' in src
assert 'VISIBLE_VIDEO_SAMPLE:' in src
assert 'blackPixels=' in src
assert 'PixelCopy.request(surface,image' in src
assert 'DECODER_FORMAT_CHANGED:' in src
assert 'surfaceStartHandler.postDelayed({' in src
assert '},350L)' in src
assert 'session?.let { if(!it.closed) it.close() }' in src
assert 'surfaceStartHandler.removeCallbacksAndMessages(null)' in src
assert 'TV Box mode · Aceconn video fix' in shell
assert '.putExtra("aceconn_mode",true)' in shell
assert 'Audio forwarding is not enabled in this beta.' not in shell
assert 'versionCode = 15' in build
assert 'versionName = "0.15.0-tvbox-encoder-beta"' in build
print("DOUPAD_V015_ACE_DEVICE_ENCODER_AND_FLICKER_REGRESSION_PASS")
