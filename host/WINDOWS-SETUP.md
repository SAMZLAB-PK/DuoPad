# DOUPAD — Windows Network Host

## Quick setup

1. Install Python 3.10 or newer and make sure the Windows `py` launcher is available.
2. Double-click `windows/Run-DOUPAD-Host.bat`.
3. On the first run, Windows will ask for Administrator permission once so DOUPAD can add its private/domain firewall rules.
4. Keep the DOUPAD Host console open while using Network PC mode.
5. Put the PC and phone on the same LAN/Wi-Fi. The app should discover the PC automatically; QR and manual pairing remain available if a network blocks broadcast discovery.

The default ports are **TCP 27845** for control and **UDP 27846** for discovery.

## What the Host shows

The Host prints:

- discovery state (`READY` or a concrete bind error);
- one or more LAN addresses such as `192.168.1.20:27845`;
- a `doupad://pc/...` pairing URI;
- a terminal QR code when the small `qrcode` Python package is available.

## Protocol verification

DOUPAD never trusts an arbitrary open TCP port. The Android client sends a binary identity ping and accepts only DOUPAD/legacy-compatible host signatures. Current LAN discovery uses `DOUPAD_DISCOVER/3` / `DOUPAD_HOST/3`; the previous `UNIPOINT_DISCOVER/2` discovery request remains supported for beta compatibility.

## PIN

The default launcher starts without a PIN. For PIN-protected pairing:

```bat
cd host\python
py unipoint_host.py --port 27845 --pin 1234
```

The QR code includes the PIN and discovered PC cards show a lock indicator.

## If a PC is not discovered

1. Check the Host console says `LAN discovery: UDP 27846 (READY)`.
2. Confirm phone and PC are on the same private LAN; guest Wi-Fi/AP isolation can intentionally block peer traffic.
3. Scan the Host QR in DOUPAD. QR pairing bypasses broadcast discovery entirely.
4. If QR/direct IP also fails, Windows Firewall or VPN routing is blocking TCP 27845.
5. Saved PCs remain in the app and can be retried even while offline.

DOUPAD also uses directed broadcast, remembered endpoints, LAN TCP probing, heartbeat health checks and automatic reconnect so a short Wi-Fi interruption does not force a fresh setup.
