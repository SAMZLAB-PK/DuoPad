# DuoPad Pro v0.17.0 R9
R8 captured AUTO_PROFILE_RETRY, but no subsequent video profile was started.
R9 makes the stream loop recognize black-output retry and bounds shutdown waiting
to three seconds before proceeding to the next available profile.
Adds PROFILE_RESULT / PROFILE_NEXT diagnostic events and more frequent frame logs.
Aceconn live capture is still dependent on its firmware restrictions.
