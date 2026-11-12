# DuoPad v0.18.5 R20 — PC experience

- PC Info shows RAM in GB instead of bytes.
- PC screen opens into landscape display mode and hides the status bar, while preserving touch-to-click and drag-to-move mouse controls over the desktop. Back returns to Info/portrait.
- QR scanning remains portrait-locked.
- PC is saved automatically after successful authenticated connection; verified PC PIN is encrypted with AndroidKeyStore AES-256 GCM (not stored in the saved-target JSON). Opening a saved PC reuses its trusted credentials without discovery. Automatic reconnect is bounded and requires that the Host's PIN remains valid; the Host's generated PIN changes after restart unless fixed at setup.
- Removing saved PC deletes encrypted PIN.
- PC Java exception signatures recorded in local PC diagnostics to identify the user's three reported crashes without copying credentials.
- Does not claim 30+ FPS desktop streaming or that the PC crashes are definitively resolved until device testing.
