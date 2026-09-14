# UniPoint Pro Final Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a stable UniPoint Pro debug build with reliable device switching, PC handshake/discovery, fast Android app browsing, stable capture, and branded UI.

**Architecture:** Retain existing layers, add transport serialization/caching and explicit discovery/handshake semantics, then polish Compose screens without adding new third-party dependencies.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, coroutines, Hilt, DADB, Python standard library host.

**Spec:** `docs/superpowers/specs/2026-09-12-unipoint-pro-final-design.md`

## Global Constraints
- No new Android runtime dependencies.
- PC default port remains 27845.
- ADB default port remains 5555.
- Keep minSdk 26 and target/compile SDK 35.
- Only claim APK success after Gradle exits 0.

---

### Task 1: Protocol verification and PC discovery
**Files:** `NetworkPcDataSource.kt`, repository interface/impl, `MainViewModel.kt`, `DeviceListScreen.kt`, Python host.
- [ ] Add handshake verification and LAN discovery.
- [ ] Expose PC device flow to UI.
- [ ] Add host protocol tests and run them.

### Task 2: ADB serialization and fast metadata
**Files:** `AdbDataSource.kt`, repository/viewmodel, `AppManagerScreen.kt`.
- [ ] Serialize DADB calls.
- [ ] Return cheap initial package list.
- [ ] Lazily fetch/cache APK label/version/icon for visible apps.
- [ ] Batch device info.

### Task 3: Capture stability and navigation
**Files:** `AdbDataSource.kt`, `ScreenMirrorScreen.kt`, `NavHost.kt`, `AndroidDashboardScreen.kt`.
- [ ] Unique screenshot path + cleanup.
- [ ] Pace mirror captures and prevent overlap.
- [ ] Disconnect before navigating Android dashboard back to Devices.

### Task 4: Pro UI polish
**Files:** `Theme.kt`, `DeviceListScreen.kt`, `AndroidRemoteScreen.kt`, dashboard.
- [ ] Force branded dark theme.
- [ ] Add animated background to picker.
- [ ] Rebuild remote control hierarchy and touch targets.

### Task 5: Build/export
**Files:** `BUILD-APK.bat`, project output.
- [ ] Add one-click Windows build/export script.
- [ ] Run host tests.
- [ ] Run Gradle compile/tests/assemble when dependencies are available.
- [ ] Export APK only from successful build.
