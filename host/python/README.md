# UniPoint Host

Official companion application for **Network mode**.

## Quick Start

```bash
# Install dependency
pip install pynput

# Run (default port 27845)
python unipoint_host.py

# With PIN protection
python unipoint_host.py --pin 1234

# Custom port
python unipoint_host.py --port 30000 --pin 9999
```

## Supported Platforms
- Windows 10 / 11
- Linux (X11 / most Wayland)
- macOS (requires Accessibility permission)

## Protocol
Binary little-endian packets – see source for full specification.
Matches exactly the `NetworkPcDataSource` on the Android side.

## Firewall
Allow inbound TCP on the chosen port (default **27845**).
