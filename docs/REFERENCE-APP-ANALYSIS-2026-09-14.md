# DOUPAD Reference App Static Analysis — 2026-09-14

This report is based on static inspection of the user-supplied PalmPoint 6.2.0, atvTools 1.3.1 and AirSync Remote Beta 9 packages plus DOUPAD 3.0.1 source/APK. It is a clean-room feature/protocol analysis: third-party proprietary source code was not copied into DOUPAD.

## Architecture and feature matrix

| Area | PalmPoint 6.2.0 | atvTools 1.3.1 | AirSync Remote Beta 9 | DOUPAD 3.1.0 realtime |
|---|---|---|---|---|
| Primary transport | BLE HID + Wi-Fi connection service | ADB/native ADB + on-device helper server | UPnP/DLNA + vendor remote services | ADB/DADB + scrcpy 3.3.1 control/video; PC TCP host |
| Mouse path | HID reports / native `libphonemouse.so`; sensor cursor conversion | helper service direct input injection; HID when supported | `RemoteMouse`/`RemoteTouch`; `libairmouse_jni.so` | **persistent scrcpy binary control socket**; shell fallback only |
| Air mouse | Strong: `SensorService`, quaternion/native motion conversion, cursor speed/debounce | Not the main design | Strong: `AirMouse`, sensor service, anti-shake/hide-mouse handling | Existing touchpad; gyro air-mouse is a future clean-room module |
| Touchpad | Dedicated mouse modes, buttons, wheel mappings | Remote control widgets + helper `/touch` | Dedicated remote touch protocol | **120 Hz latest-position sender**, left/right click, scroll, Back separated |
| D-pad/media keys | Configurable button maps and media layouts | Compact/D-pad/full/numpad remote widgets | D-pad/media/number/volume/channel remotes | Existing D-pad/media UI; **keys now use persistent scrcpy fast path** |
| Keyboard/text | HID keyboard + history/config maps | helper control + ADB tooling | VIME virtual input method state machine | Existing keyboard dialog; **text now uses direct scrcpy text message**, shell fallback |
| Gamepad | Rich ABXY, triggers, joystick/button layouts | Remote widgets; focus is TV tooling | Game remote layouts | Existing gamepad UI/key mapping; not yet a full analog HID clone |
| Mirroring | Not the core static signature found | helper video/audio/mic TCP services, MediaCodec paths | native H.264 mirror engine (FFmpeg/Stagefright-era) | **H.264 scrcpy 3.3.1, 1280 cap, 60 fps target, 8 Mbps, MediaCodec** |
| Interactive mirror | Input and cursor features exist separately | helper `/touch` + stream service | remote touch + mirror service | **Yes: touch on live SurfaceView maps directly to TV coordinates over control socket** |
| Audio mirror | No clear core emphasis | Dedicated TCP audio/microphone services | native audio/video engine artifacts | Not enabled in this pass (`audio=false`) |
| Discovery | BLE scan + Wi-Fi connection/history | ADB/pairing oriented | UPnP/SSDP device discovery | Existing subnet ADB discovery + saved targets; PC UDP discovery |
| Reconnect/keepalive | connection service state handling | helper/server lifecycle | explicit KeepAlive/Ping/ReAccess controllers | Existing reconnect flow + **control retry cooldown/fallback** |
| App manager | Not core | Strong: packages, running apps, permissions, app-ops | remote app list/launch | Existing list/launch/force-stop/uninstall/download APK |
| File manager | Not core | Strong helper file endpoints and shared files | DLNA/file push features | Existing browse/push/pull/install APK/file manager |
| TV channels/inputs | Not core | Strong: channels + TV inputs + input switching | vendor live-TV remote concepts | Basic channel/media key control; direct TV input/channel DB tooling not yet cloned |
| Permissions/AppOps | Not core | Strong | Not core | Not yet exposed as dedicated UI |
| Clipboard | keyboard/input-centric | helper has clipboard listener | clipboard/VIME-era mechanisms | Existing clipboard utility; direct realtime input layer now available |
| Native code observed | `libphonemouse.so`, image processing | `libadb.so`, `liba.so` | H.264 mirror/video engine + air-mouse JNI libraries | No copied third-party native blobs; bundled scrcpy server retained under its license |

## Notable static findings

### PalmPoint
- App package contains BLE HID service/report-map classes, Wi-Fi connection service, sensor service, D-pad, joystick and trigger views.
- Assets expose configurable `mouse`, `keyboard`, `media`, `numeric`, `ppt`, `gamepad` and button-mapping profiles.
- `libphonemouse.so` exports cursor-speed, quaternion-to-mouse movement and debouncing-related symbols, explaining why its air cursor can feel much smoother than repeatedly launching Android shell input commands.

### atvTools
- The APK bundles an on-device `server.jar` plus native ADB-related libraries.
- Helper-side classes include input, notification and TV-channel services. Static endpoint strings include `/keyevent`, `/touch`, `/screenshot`, `/shell`, `/files`, `/permissions`, `/appops`, `/channels`, `/tvInputs` and `/switchInput`.
- The helper contains dedicated TCP video/audio/microphone/control server paths and direct Android input injection/HID capability. This architecture avoids spawning a shell process for every pointer event.

### AirSync Remote
- Uses UPnP/SSDP-style discovery and separate controllers for access, mirror, remote app, VIME and virtual input services.
- Contains dedicated `RemoteMouse`, `RemoteTouch`, `RemoteKeyboard`, `RemoteSensor`, `RemoteMedia` and pushing abstractions plus keepalive/re-access logic.
- Native libraries contain H.264/FFmpeg/Stagefright-era decoding and RTP/RTCP/video-engine symbols. The design ideas are useful, but the vendor-specific/legacy implementation is not appropriate to transplant into a modern DOUPAD build.

## DOUPAD 3.1.0 realtime changes made from the analysis

1. Added a scrcpy 3.3.x control-message serializer for key, text, touch and scroll messages.
2. Added a persistent control-only scrcpy server/session for normal remote input instead of executing a new `adb shell input ...` process per mouse move.
3. Mouse Pad now sends the latest cursor position over the persistent binary channel at up to ~120 Hz and drops stale positions rather than replaying a backlog.
4. Added true left/right mouse clicks, direct scroll packets and retained shell fallback for incompatible devices.
5. Android key events, typed text and taps now use the same low-latency control path when available.
6. Realtime control is pre-warmed after Android connection so the first D-pad/mouse/keyboard action does not pay the full server startup cost.
7. Failed control startup has a retry cooldown, preventing a bad device/network state from starting a new scrcpy process on every pointer sample.
8. Live mirror changed from 30 fps / 6 Mbps / video-only control-disabled to a 60 fps target / 8 Mbps / control-enabled session.
9. Live mirror opens a second scrcpy control socket and maps SurfaceView touch coordinates to the actual streamed device dimensions.
10. Added protocol/coordinate tests and source-level regression checks; PC host protocol tests remain passing.

## Deliberately not copied

No proprietary PalmPoint, atvTools or AirSync Java/Kotlin/native implementation was transplanted. DOUPAD uses independently written logic around its existing DADB/scrcpy architecture. Feature names, observed protocol concepts and architectural lessons were used only to decide what behavior DOUPAD should provide.

## Remaining high-value follow-ups

- Gyroscope air-mouse with calibration, dead-zone, smoothing and anti-shake inspired by the *behavior* observed in PalmPoint/AirSync.
- A DOUPAD companion/helper service for richer atvTools-style AppOps, permissions, TV inputs/channels and high-performance file operations.
- Optional scrcpy audio decode on Android versions/devices that support it.
- Hardware latency profiling and adaptive mirror presets (720p/60, 1080p/60, low-bandwidth) rather than one fixed bitrate.
