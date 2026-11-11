# DuoPad Pro v0.18.1 R16 — PC Search / Diagnostics

- PC filter initial action: **Search PC**; subsequent scans: **Search Again**.
- The app no longer automatically runs a full PC scan during startup.
- PC scan bounded to 15s; spinner clears on timeout/error and shows actionable Host/Windows Firewall advice.
- PC tab **Connect PC by IP** selects Wi-Fi Host connection on port 27845 (not TV ADB port 5555).
- PC discovery logs separate from mirror logs: start, LAN subnets, UDP phase, TCP fallback, verified hosts, connection failures and scan timing.
- Diagnostics UI now shows PC and mirror logs together with copy/clear controls; never records PINs, passwords, files or keystrokes.
- Requires DuoPad Windows Host running on same LAN, and Windows Firewall permitting TCP 27845 and UDP 27846 on Private networks.
- Local source contracts tested; GitHub Android APK compilation and hardware tests must be separately verified.
