# DuoPad Pro v0.17.0 R8

## Changes
- Application ID: `com.samzlab.duopad` (new Android package; existing beta data does not migrate automatically).
- Consistent DuoPad name across user-facing app strings, download folder, and connected-tool screens. Kotlin internal package namespaces and existing on-wire host protocol remain unchanged for compatibility.
- App Manager: one overflow menu per app: Launch, Download APK, Force stop, Uninstall (with confirmation).
- Tools: slim, fixed-height 94dp cards with two-line overflow handling.
- Remote: compact D-pad, 123 keypad (0–9, delete, enter), 0–10 volume scale markings, +/- volume controls, channel and media actions.
- Shell: 26 read-only quick commands in a dropdown with compact chips; output copy and selection support.
- Mirror: reduced default TV load at 720p/15fps, 1Mbps, video-only; Smooth mode 640p/12fps, 750kbps video-only. Optional Aceconn screenshot preview via `SHOT` at a slow update rate. Keeps screen awake while foregrounded.

## Notes
- Aceconn live H.264 may still be black if vendor firmware or protected content blocks capture. Screenshot preview is lower-rate and may also be black/unavailable. Protected content is not bypassed.
- Mirror optimizations need testing on Aceconn Diamond Pro Max and Mi TV hardware.
- Debug builds are signed with GitHub's debug key, not for Play Store production release.
