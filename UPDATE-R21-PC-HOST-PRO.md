# DuoPad Pro R21 — PC Host Pro, reliable heartbeat, remote access

Changes:
- Fix a confirmed crash: `NetworkPcDataSource.ping()` catches SocketException/IO errors when the Windows Host closes a control socket. The heartbeat returns disconnected instead of causing an uncaught Java process exit.
- Open the PC Workspace home dashboard, not Info, after connection.
- Windows info dashboard reorganized into professional system cards and GB values.
- PC Screen: fullscreen landscape, on-screen keep-awake flag, mode toggle for touching the displayed PC screen vs using the phone as a touchpad, visible keyboard, and quality controls.
- On-screen Windows keyboard opens in landscape and preserves key mappings.
- Dedicated portrait QR capture activity.
- User-provided D logo replaces old bitmap/vector launcher via a drawable.
- New Windows Host Pro GUI EXE build from a stable authenticated TCP/UDP host, QR, persistent PIN, activity, Tailscale IP.
- Internet remote control over Tailscale tailnet (100.64/10 range) instead of raw public port forwarding. Windows firewall must be configured by owner, and both devices must authenticate into same tailnet.

Known limits: No built-in AnyDesk-class relay or guaranteed 30/60fps low-latency H.264. Existing PC screen preview uses JPEG request/response, and speed depends on host/network. Protected Android video capture also remains device-dependent.
