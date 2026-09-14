# DOUPAD Realtime Control & Mirror Design

## Goal
Make Android TV / Google TV interaction feel immediate and professional by removing per-event ADB shell commands from the pointer/touch hot path and by making the live scrcpy mirror directly interactive.

## Reference-app findings
- PalmPoint combines Wi-Fi control with BLE HID, sensor/air-mouse handling, configurable mouse/keyboard/gamepad maps, D-pad/joystick UI, and dedicated connection services.
- atvTools pushes a device-side helper and uses direct input injection plus dedicated TCP video/audio/control paths; it exposes app, permission, channel, file, shell, screenshot, and TV-input operations.
- AirSync Remote uses device discovery + keepalive/reconnect, dedicated remote mouse/touch/keyboard/VIME components, sensor/air-mouse support, and a native H.264 mirror pipeline.

No proprietary implementation code from these apps will be copied. DOUPAD will reproduce useful behavior using its own Kotlin code and its existing Apache-2.0 scrcpy 3.3.1 server asset.

## Architecture
1. Add a small, testable `ScrcpyControlProtocol` serializer for key, text, touch, mouse and scroll messages.
2. Add a low-latency control channel inside `AdbDataSource`. It starts a control-only scrcpy server over the already authenticated DADB transport and keeps one buffered control socket open for realtime events.
3. Existing high-level ADB commands keep working. Key, text and tap operations prefer the direct channel and fall back to `input ...` shell commands when realtime control is unavailable.
4. Mouse Pad starts the realtime channel once, coalesces movement with `LatestPositionBuffer`, sends direct SDK mouse events at display-rate cadence, and only falls back to shell input if the direct channel fails.
5. Live Mirror starts scrcpy with video + control, opens video and control sockets, maps SurfaceView coordinates to device coordinates, and injects DOWN/MOVE/UP touch events directly.
6. Closing the Android connection closes the control socket and server stream cleanly.

## Mirror profile
- Codec: H.264/AVC for broad decoder support.
- Default max dimension: 1280.
- Target frame rate: 60 fps.
- Video bitrate: 8 Mbps.
- Audio remains off in this pass to prioritize interaction latency and reliability.

## Mouse behavior
- Movement is absolute on the TV but relative on the phone touchpad.
- Only the newest pending position is retained, so stale movement never queues behind a slow transport.
- Left, right, and middle mouse buttons use scrcpy mouse-pointer events.
- Scroll uses scrcpy scroll messages.
- Back remains an explicit Android Back action rather than pretending it is right-click.

## Error handling
- Realtime channel setup failure must never break normal ADB connection.
- Direct input failures close the broken realtime channel and transparently fall back to shell input for key/tap/move where a shell equivalent exists.
- Mirror session errors are shown in the activity status line and all streams/codecs are closed in `finally`.

## Testing
- Unit-test exact binary control message sizes and important fields.
- Unit-test mirror coordinate mapping and edge clamping.
- Keep existing frame-pacing and latest-position-buffer tests green.
- Build verification is required when an Android SDK is available; this environment may only support source/unit inspection if the SDK is absent.
