# DOUPAD Preview 3.0 Design

## Goal
Convert the existing UniPoint/DOUPAD Android remote into the approved DOUPAD visual shell while preserving the existing real ADB Android/Google TV and Windows PC control engines.

## Visual contract
- Brand displayed everywhere as **DOUPAD**.
- Light, spacious interface with teal primary accent and dark navy text.
- Primary destinations: Home, Remote, Mirror, Tools.
- Home shows Android/Google TV and PC devices, connection state, add-device/manual-connect, and quick actions.
- Remote exposes D-pad, OK, Back/Home/Menu, volume, mute, channel, keyboard and touchpad entry.
- Mirror keeps the existing screenshot-based live mirror for Preview 3.0 and clearly labels it as device-dependent; no false claim of scrcpy-class video.
- Tools exposes existing real capabilities: apps, files, install APK, shell, device info, gamepad and power/reboot actions.

## Architecture
Keep the existing Kotlin + Jetpack Compose + Hilt + repository architecture. Presentation changes are isolated to Compose UI/theme/navigation and branding resources. Existing `ConnectionRepository`, `AdbDataSource`, `NetworkPcDataSource`, and host protocol remain the source of truth for actions.

## Compatibility
- minSdk 26
- compile/target SDK remain 35 for this preview source
- Java/Kotlin target 17
- Existing ADB default port 5555 and PC host port 27845 remain unchanged.

## Testing
Add source-contract tests for DOUPAD branding/navigation and retain existing unit tests. Final APK must only be claimed after Gradle tests and `assembleDebug` pass in an Android SDK enabled environment.
