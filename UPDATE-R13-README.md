# DuoPad Pro R13 - Mirror usability and audio

- Every scrcpy video profile requests PCM audio, including Aceconn and Mi TV smooth modes.
- Audio forwarding is best-effort: Android <11, locked Android 11, DRM-restricted apps and vendor firmware may not expose audio. Video continues when scrcpy reports unavailable audio.
- TV Box attempts 640p/24fps then software-decoder 640p/24fps, 720p/20fps, and retains 640p/12fps for fallback.
- Decoder logs measure FPS and input/output backlog. Actual quality is device-dependent; test on real devices.
- Player buttons overlay near the displayed video edges; auto-hide after 4.3 sec, tap picture to re-show.
- First Back reveals controls; second Back within 2.8s exits. X closes immediately.
- Android 13+ system Back supported via OnBackInvokedCallback.
- Passively found Android TV endpoints show previously verified name, else reverse-DNS hostname when available. Exact model requires authorized ADB shell if no LAN hostname.
- Auto encoder retry releases previous MediaCodec even if an ADB read remains blocked; retry only for a session with no previous visible content, avoiding fruitless switches on protected playback.
- Existing DuoPad branding/package com.samzlab.duopad preserved.

No mechanism can guarantee mirroring of hardware-protected/DRM video on Aceconn. Its VLC software-decoded path is the current reliable workaround.
