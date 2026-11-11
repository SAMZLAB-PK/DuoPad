# DuoPad Pro v0.18.0 R15 — Windows PC Workspace

## Implemented on Android + Windows Host v4
- Discover PC by local broadcast (existing feature), establish authenticated PIN connection, mouse/touchpad, click, scroll and keyboard controls.
- View interactive PC desktop as a **pull-based JPEG preview** captured via `mss` + `Pillow`. Select Fast/Balanced/Sharp quality (720/960/1280px). Touch or drag controls Windows pointer. This is *not yet* 60fps H.264/RDP/desktop audio.
- Browse local disks and directories, see file sizes, use SAF picker to download a file to the phone, upload selected phone file to a chosen PC directory (1 GiB per file, atomic upload, no overwrite of an existing name).
- Read PC hostname, OS, CPU threads, RAM and drive capacity.
- Preserve Android TV mirroring and control paths from R14.
- Windows Host requires user to launch it visibly and prints a random 8-digit PIN. Host does NOT install hidden persistence. Host checks that incoming IP is private/loopback; Windows firewall rules limited to Private profile.
- The PC Host EXE can be built by GitHub Actions Windows workflow `build-pc-host.yml`, or launch with Python 3.10+ and one-click BAT.

## First-time steps
1. On Windows PC download the matching DuoPad-PC-Host package (or source ZIP). Open `START-DUOPAD-PC-HOST.bat` from the GitHub Windows artifact or `host/windows/Install-and-Run-DOUPAD.bat` from source.
2. Windows may request UAC **only to configure firewall rules once**. The PC Host itself runs unelevated in its own visible console and shows the pairing PIN.
3. On the Android phone install v0.18.0 R15. Connect to PC from Devices, enter PC's IP and 8-digit PIN. Choose **Tools → Windows PC Workspace**.
4. Screen / Files / Info tabs support live desktop view, transfer and system information.

## Security / limitations
- **LAN-only and plaintext:** authenticate with PIN; DO NOT forward port 27845 over WAN or use untrusted Wi-Fi. Host PIN offers access control but is not transport encryption. Use TLS plus certificate pinning for future Internet/Tailscale support.
- Remote control is intentionally user-initiated and the Windows Host must remain running visibly.
- JPEG preview aims for responsiveness without buffering but is not video streaming at 30–60fps; for production high-fps screen/audio use Windows Desktop Duplication API with GPU H.264 and a secure transport, or an established RDP/VNC client.
- Windows protected/UAC secure desktop may not be captured or controllable; protected media remains excluded.
- For security, per-session PIN must be re-entered when reconnecting saved PCs. No persistent plaintext PIN storage.
- App package remains `com.samzlab.duopad`.
