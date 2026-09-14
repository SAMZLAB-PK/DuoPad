# DOUPAD 3.1.0 realtime — hardware test checklist

Build the debug APK on the normal Android Studio machine, install it on the phone, then connect the same Android TV/Google TV used for DOUPAD 3.0.1.

1. Connect to the TV and immediately press D-pad directions. The first input should not have the old shell-start pause after the background pre-warm completes.
2. Open Mouse Pad. Drag rapidly in circles for 15–20 seconds. The cursor should follow the newest finger position without replaying an old movement trail. Test left click, right click, Back and both scroll directions.
3. Open Live mirror. Confirm video starts, status shows `60 fps target · Touch enabled`, then tap and drag directly on the mirrored picture. Touch location on the TV should match the phone preview.
4. Test mirror for at least five minutes, rotate/open several TV apps, and watch for decoder freezes or disconnects. If a weak Wi-Fi link stutters at 8 Mbps, record the TV model, phone model, Wi-Fi band and measured signal quality for adaptive bitrate tuning.
5. Test Home, Back, volume, channel, media keys and keyboard text. They should continue working if realtime control is available and should fall back to shell if it is not.
6. Reconnect/disconnect repeatedly and verify no orphan mirror/control session leaves the next connection unusable.

Do not judge the final latency from source/static tests: the phone + TV hardware test is the authoritative performance check.
