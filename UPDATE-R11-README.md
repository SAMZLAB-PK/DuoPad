# DuoPad Pro v0.17.2 R11 — Manual Encoder Switch Hang Fix
Session.close immediately marks shutdown and closes ADB channels on workers.
MediaCodec operations use one lock so a stale reader cannot submit input during release.
Manual switch waits for the old decoder release rather than for blocked network reads.
Repeated ENC presses are suppressed until the current switch finishes.
Diagnostics: MANUAL_SWITCH_DECODER_RELEASED, DECODER_CLEANUP, MANUAL_SWITCH_NETWORK_READER_STALE.
Aceconn firmware/DRM restrictions are not bypassed.
