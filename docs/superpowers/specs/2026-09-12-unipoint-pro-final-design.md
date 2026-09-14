# UniPoint Pro Final Design

## Goal
Make device switching predictable, PC network control verifiable, Android device/app information responsive, mirror/screenshot stable, and the UI consistently dark/professional.

## Architecture
- Keep the existing Compose + Hilt + repository architecture.
- Serialize ADB transport operations with a mutex so concurrent UI requests cannot trample the single DADB connection.
- Make the initial app list cheap, then lazily enrich visible apps with APK-derived label/version/icon metadata cached on disk/in memory.
- PC connections must complete a protocol PING handshake before being reported connected; add LAN host discovery against the same handshake.
- Returning from an Android dashboard to Devices disconnects Android first so DeviceList cannot immediately route back to the old device.
- Mirror capture is single-flight and paced; screenshots use a unique remote path and clean it up.
- Force the branded dark theme and use animated gradient/glow surfaces on high-level screens.

## Success criteria
1. Back from Android dashboard returns to a stable device picker.
2. A random open TCP port cannot be treated as a UniPoint PC host.
3. PC hosts can be discovered on the local /24 subnet and connected from the device list.
4. Android app list appears quickly; visible rows progressively receive proper labels/versions/icons.
5. Device info is fetched in one batched shell command.
6. Mirror does not issue overlapping ADB captures and runs at a conservative cadence.
7. Remote has a clear TV-remote hierarchy with large D-pad/OK and grouped controls.
8. Build script exports `UniPoint-Pro-debug.apk` after successful Gradle verification.
