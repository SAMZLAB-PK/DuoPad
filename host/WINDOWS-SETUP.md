# UniPoint Pro — Windows Network Host

## Quick setup

1. Install Python 3.10 or newer and make sure the Windows `py` launcher is available.
2. Right-click `windows/Install-and-Run-UniPoint.bat` and choose **Run as administrator** once.
3. Keep the UniPoint Host console open.
4. Put the PC and phone on the same LAN/Wi-Fi.
5. Open UniPoint Pro. The PC should appear automatically under **PC Host**.

The default port is **TCP 27845**. The setup BAT creates a Windows Defender Firewall inbound rule for that port.

## Protocol verification

UniPoint Pro does not accept a PC merely because port 27845 is open. The Android client sends a protocol ping and requires an identity beginning with:

```text
UNIPOINT/2
```

This prevents false connections to unrelated services. The packet header is little-endian on both Android and Python host.

## PIN

The default BAT starts the host without a PIN. To run a PIN-protected host manually:

```bat
cd host\python
py unipoint_host.py --port 27845 --pin 1234
```

The Android device picker will show `UniPoint PC (PIN)` and prompt for the PIN.

## Windows dependencies

No third-party input package is required on Windows. Mouse and keyboard injection use the native Win32 API through Python `ctypes`.

## If the PC is not discovered

Check these in order:
- the host console says `Listening on 0.0.0.0:27845`;
- phone and PC are on the same subnet/LAN (guest Wi-Fi/AP isolation can block peer traffic);
- Windows network/firewall is not blocking inbound TCP 27845;
- VPN software is not forcing local LAN traffic through another interface;
- use the app's PC control panel/manual IP path if discovery is blocked by the network.


## Zero-config PC discovery
UniPoint Pro now discovers the Windows Host with UDP port **27846** and controls it over TCP **27845**. Run `host\windows\Install-and-Run-UniPoint.bat` as Administrator once so Windows Firewall allows both ports. The Android app also probes the last-known PC and common routed home subnets as a fallback.
