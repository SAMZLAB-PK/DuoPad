# DuoPad Pro v0.18.2 R17 — PC PIN connection diagnostics

PC discovery in R16 correctly found host 192.168.10.201:27845.
After entering the PIN, the reported UI spinner persisted without confirmed auth.
Without the post-PIN PC log, the exact failing stage is not yet proven.

Changes:
- Distinct handshake diagnostics for TCP, identity, PIN auth and post-auth verify.
  Do not record the PIN value.
- Connection bounded to 12 seconds with explicit timeout UI feedback.
- Socket closes before input/output streams (avoids blocking close on active read).
- Bluetooth/ADB disconnect cleanup runs on IO to prevent main-thread blocking.
- Connect button disabled while a handshake is active; PC-specific messaging.
- Keep existing DuoPad Host v4 protocol; no Windows Host upgrade needed.

Debug APK is not production-signed. Host LAN protocol currently uses plaintext;
only use on a trusted local network. Do not publish ports 27845/27846 to WAN.
