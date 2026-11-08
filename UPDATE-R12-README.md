# DuoPad Pro v0.17.3 R12 — Mirror exit crash and Smooth TV FPS

R11 report: pressing X at 01:36:54 coincided with a Java crash. Root cause not conclusively proven without the Java stack trace, but `worker.shutdownNow()` interrupted a waiting worker and could throw an uncaught InterruptedException. R12 changes this to safe shutdown and guards the streaming waits.

- X and Back close via idempotent requestMirrorExit(), invalidate generation and close ADB session first.
- Graceful worker executor shutdown (no forced interrupt). Catch interruptions in profile retry waits and sleep for extra safety.
- Crash recorder includes late doupad worker exceptions after the Mirror Activity finishes.
- Smooth TV mode now 720p/30fps 2.4Mbps video-only, fallback 640p/24fps 1.35Mbps.
- Default mode now 720p/24fps or 640p/20fps; Aceconn 640p/12fps working mode unchanged.
- Render every H.264 decoder output with `releaseOutputBuffer(index, System.nanoTime())` instead of using the TV device-origin PTS (unsynchronised with the phone). This addresses an actual timing defect in the old live mirror pipeline.
- Measure and log decoded VIDEO_FPS every 5s.
- Protected-video/firmware hardware overlay capture limitations remain. VLC software video is confirmed; do not claim all third-party apps can mirror.

GitHub debug APK build required. Hardware verification required.
