# DuoPad Pro 0.17.1 R10 — Aceconn safer codec switching

Aceconn Diamond Pro Max showed VLC software-decoded video correctly.
R9 640p/12fps system-default streaming rendered visible, changing video frames.
However, tapping ENC while video was working caused rapid profile failures, followed
by a Java crash history entry without a stack trace. R10 protects manual switching:
- Ask confirmation before switching away from a recently visible picture.
- Close prior ADB in a background thread and await prior decoder teardown.
- Never retry against an old decoder still attached to the same Surface.
- Record exception class and cleanup timing for next diagnostics.
- Preserve Mi TV and existing app features; do not imply protected/DRM capture bypass.

Full Android build/hardware playback must be verified separately.
