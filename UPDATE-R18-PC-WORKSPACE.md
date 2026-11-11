# DuoPad Pro R18 — PC workspace navigation, QR pairing and Windows Host sharing

- Existing R17 TCP identity, PIN auth and verified control channel remain unchanged.
- Fixed bug where Screen, Files and Info buttons all opened workspace defaulting to Screen.
- PC Workspace can open directly on each selected tab; failed requests show error and Retry.
- PC Workspace client logs TCP connect, identity, PIN auth, Screen, Files, Drives, Info and transfer stage outcomes into the existing PC diagnostic log. PIN and file contents are never logged.
- QR scanner (existing ZXing dependency) is now exposed from main PC connect UI. Scan the QR printed by Host to connect using its IP, port and PIN.
- First-party Python Windows Host v4 source is bundled with Android APK as assets and can be shared as a ZIP from Android Share via Bluetooth or Quick Share. Windows user must accept the transfer, extract the ZIP and run Run-DOUPAD-Host.bat (Python 3.10+ required). No silent remote install.
- Windows control service not modified; prior Host v4 works, so a new Windows installation is not necessary for a device that already runs it.
- Note: PC desktop is JPEG preview capture, not a native 60fps remote desktop stream. Hardware test still needed. Do not expose plaintext LAN Host port to the Internet.
