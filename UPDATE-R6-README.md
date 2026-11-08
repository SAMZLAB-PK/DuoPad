# DuoPad Pro v0.16.2 R6 — Aceconn intermittent black-video investigation and recovery

## Why R6
R5 produced video, audio and hundreds of decoded frames on the Aceconn TV box, yet visible PixelCopy samples varied from 0–33% black to 98–100% black. The R5 watchdog stopped after 1.5, 3.5 and 7 seconds, missing later transitions. The logs did **not** establish a hard decoder failure or a process crash.

## Changes
- Continue sampling visible SurfaceView frames every 2.5 seconds throughout an active mirror session (after first 1.5 seconds), instead of stopping at 7 seconds.
- Do not hide the mirror connection status merely because MediaCodec produced an output; wait for a non-black PixelCopy sample.
- TV Box mode: use conservative thresholds before auto-profile fallback (initial sustained black >=10 seconds, or later sustained black >=12 seconds after visible content). At least three/five samples and either stable black fingerprints or a longer sequence are required.
- STANDARD mode keeps auto-profile fallback off; shows a helpful notice if decoded video remains black.
- Record visibility confirmation/recovery and black durations, window focus, activity pause/resume, surface changes, so next session can distinguish rotation/background from encoder failure.
- Fix a surface-restart stall: R5 used only two fixed workers, and a new SURFACE_CREATED could be queued behind stuck old ADB readers for >16 seconds. Use a per-demand cached worker pool, with generation guards and detailed dispatch/start logs.
- Keep R5 Light/Dark/Bento theme, saved-device handling, and diagnostic deletion untouched.
- Update Windows APK output to v0.16.2 and make GitHub workflow use current source tests and correct artifact name.

## Testing status
Python source checks, Kotlin pure-logic smoke tests, XML reference checks and Kotlin escape tests passed locally. No Android SDK/physical Aceconn box available in this environment; **full APK compilation and visible hardware playback remain unverified**.

## Testing on Aceconn
1. Put the box on Android TV Home (not DRM-protected streaming app).
2. Open DuoPad -> Mirror -> TV Box Compatibility. Do not press ENC initially.
3. Leave mirror open for at least 30 seconds while looking at the phone screen.
4. If picture becomes black, keep the session open rather than closing immediately; log should show continued VISIBLE_VIDEO_SAMPLE records and potential AUTO_PROFILE_RETRY.
5. Rotate the phone if needed and share entries for SURFACE_CHANGED, ACTIVITY_PAUSE, WINDOW_FOCUS and AUTO_PROFILE_RETRY.
