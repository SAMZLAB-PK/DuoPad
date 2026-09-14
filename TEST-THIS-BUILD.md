# DOUPAD 3.0.1 native test beta

This build implements the approved white/teal direction with four native screens: Home, Remote, Mirror and Tools. It is a test beta, not full feature parity with Air Sync Z, PalmPoint or atvTools. Device-specific behavior still needs testing on a physical phone and TV.

## Install and connect

1. Install DOUPAD-3.0.1-test.apk on an Android 8.0+ phone. Allow installation from your browser or file manager when Android asks.
2. Keep the phone and Android TV on the same Wi-Fi network.
3. Enable developer options and network debugging on the TV. Enter the TV IP and ADB port (commonly 5555) in Home → Add device. Accept the debugging authorization on the TV.
4. If the TV only offers pairing-code wireless debugging, this beta cannot pair with that TLS endpoint. A classic authorized ADB TCP endpoint is required.
5. Test Remote → arrows, OK, Back, Home, volume and text entry. Select a text field on the TV before sending text.
6. Test Mirror → Live mirror. The stream is H.264 video only, up to 1280 pixels and 30 fps. Use Android Back to stop. The video surface does not inject touch input in this beta.
7. Tools → Apps and Files use the TV's ADB connection. APK installation, file access and system actions remain subject to Android permissions. Use a small nonessential file/APK for your first transfer.

## PC setup

The source ZIP contains `host/python/unipoint_host.py`, `requirements.txt` and Windows launch scripts. Install Python 3 (Windows uses the built-in input backend; macOS/Linux need the requirements), then run:

    python unipoint_host.py --pin 1234

Use your own PIN. In the app choose Windows PC and enter the host's IP, port 27845 and PIN. Allow the host through the private-network firewall. The host protocol is for a trusted local network. Mouse, keyboard and basic navigation use this host; Android tools and TV mirroring require a TV connection.

## Included functionality

- Real connection state, saved device endpoints and TV discovery.
- TV remote, navigation touchpad, keyboard, phone speech-to-text when an installed recognizer is available.
- PC mouse/keyboard through the included companion host.
- Native live video using the unmodified scrcpy 3.3.1 server.
- Existing screenshot preview, Android apps/APK management, file transfers, shell, device info and confirmed power actions.
- Key-based game buttons; game compatibility varies.

## Not enabled in this beta

Phone-to-TV casting, audio forwarding, screen recording, gyroscope steering, custom button mapping, TLS pairing-code ADB and PC screen mirroring. These entries explain their status in the app. This build does not claim to reproduce every function of the three reference apps.

## Feedback to send

Phone model/Android version, TV model/Android version, steps taken, exact error text and a screenshot or short video. For installation errors, include the complete installer message. A successful build and signature check do not verify installation or live TV/PC behavior.

## Rebuild

Use JDK 17, Android SDK 35 and Build Tools 35.0.0. From the project folder run:

    ./gradlew :app:assembleDebug :app:testDebugUnitTest

The application ID is `com.samz.doupad.beta`; it installs separately from earlier package IDs. Debug signing is for testing. The source archive excludes generated build outputs, local SDK paths and signing secrets.

## Third-party notice

The bundled scrcpy server is version 3.3.1, distributed under Apache License 2.0. Its license is included in `app/src/main/assets/scrcpy-LICENSE`.
Official project: https://github.com/Genymobile/scrcpy
Server SHA-256: a0f70b20aa4998fbf658c94118cd6c8dab6ab344d70bc1ebcbb8
