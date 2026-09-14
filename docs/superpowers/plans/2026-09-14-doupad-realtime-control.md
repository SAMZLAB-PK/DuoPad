# DOUPAD Realtime Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace shell-per-event Android input with a persistent scrcpy control channel and make the live mirror interactive at low latency.

**Architecture:** Keep DADB as the authenticated transport. Add a pure binary scrcpy protocol serializer, then a persistent control socket managed by `AdbDataSource`; reuse the same serializer in `DoupadMirrorActivity` so video and touch share one scrcpy session.

**Tech Stack:** Kotlin, Android, DADB, Okio BufferedSink/BufferedSource, scrcpy server 3.3.1, MediaCodec, Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-09-14-doupad-realtime-control-design.md`

## Global Constraints
- Do not copy proprietary PalmPoint, atvTools, or AirSync implementation code.
- Reuse DOUPAD's existing `scrcpy-server.jar` and `scrcpy-LICENSE`.
- Keep package/application identity `com.samz.doupad` and visible product name DOUPAD.
- Preserve shell-command fallbacks for compatibility.
- Do not regress existing app/file manager features.

---

### Task 1: Scrcpy control message serializer
**Files:**
- Create: `app/src/main/java/com/unipoint/data/remote/ScrcpyControlProtocol.kt`
- Test: `app/src/test/java/com/unipoint/data/remote/ScrcpyControlProtocolTest.kt`

**Interfaces:**
- Produces: `ScrcpyControlProtocol.key(...)`, `text(...)`, `touch(...)`, `scroll(...)` returning `ByteArray`.

- [ ] Write failing tests for key, touch, scroll and UTF-8 text message layout.
- [ ] Run the tests and verify failure because the serializer does not exist.
- [ ] Implement the serializer with big-endian binary layout and fixed-point pressure/scroll encoding.
- [ ] Re-run tests and keep them green.

### Task 2: Mirror coordinate mapping
**Files:**
- Create: `app/src/main/java/com/unipoint/core/util/MirrorCoordinateMapper.kt`
- Test: `app/src/test/java/com/unipoint/core/util/MirrorCoordinateMapperTest.kt`

**Interfaces:**
- Produces: `MirrorCoordinateMapper.map(x, y, viewWidth, viewHeight, deviceWidth, deviceHeight)`.

- [ ] Write failing center/edge mapping tests.
- [ ] Verify they fail because the mapper does not exist.
- [ ] Implement clamped coordinate mapping.
- [ ] Re-run tests.

### Task 3: Persistent realtime Android control channel
**Files:**
- Modify: `app/src/main/java/com/unipoint/domain/model/Models.kt`
- Modify: `app/src/main/java/com/unipoint/data/remote/AdbDataSource.kt`
- Modify: `app/src/main/java/com/unipoint/data/repository/ConnectionRepositoryImpl.kt`

**Interfaces:**
- Add `AdbCommand.PrepareRealtimeInput`, `PointerMove`, `PointerButton`, `PointerScroll`.
- `AdbDataSource` lazily starts scrcpy control-only mode and writes protocol messages to one buffered sink.

- [ ] Add model command variants.
- [ ] Add control-session lifecycle fields and setup/teardown in `AdbDataSource`.
- [ ] Route pointer/key/text/tap commands through realtime control with shell fallback.
- [ ] Wire new commands in repository.

### Task 4: Professional Mouse Pad
**Files:**
- Modify: `app/src/main/java/com/unipoint/presentation/ui/screens/android/AndroidMousePadScreen.kt`

**Interfaces:**
- Consumes Task 3 realtime commands.

- [ ] Prepare realtime input when screen opens.
- [ ] Keep latest-position coalescing and send direct pointer moves at about 60 Hz.
- [ ] Implement actual left/right/middle click and direct scrolling.
- [ ] Keep an explicit Back button and clear connection status text.

### Task 5: Interactive live mirror
**Files:**
- Modify: `app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt`

**Interfaces:**
- Consumes Task 1 serializer and Task 2 coordinate mapper.

- [ ] Start scrcpy with `control=true`, 60 fps target and 8 Mbps H.264.
- [ ] Open second scrcpy socket for control.
- [ ] Map SurfaceView touch coordinates to device pixels.
- [ ] Send DOWN/MOVE/UP/CANCEL directly to the control socket.
- [ ] Close control stream with the existing video/server resources.

### Task 6: Verification and packaging
**Files:**
- Update: `TEST-THIS-BUILD.md`
- Create: `REFERENCE-APP-ANALYSIS.md`

- [ ] Run available unit/source verification.
- [ ] Run Android Gradle tests/build if an SDK is available; otherwise record the exact environment limitation.
- [ ] Package the modified source tree for the user.
