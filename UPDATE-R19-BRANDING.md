# DuoPad Pro v0.18.4 R19 — Branding and PC diagnostics

Updated Home and Devices headers with the original black D app logo, bold uppercase DOUPAD, subtitle “Control your PC & TV from your phone”, and right-side three-dot menu. Existing single Light/Dark toggle remains immediately below the Devices header. Version code 31.

The supplied R18 PC logs show authenticated PC control but no PC_WORKSPACE_TCP_OK after secondary Workspace connection. The separate Workspace client now records a PC_WORKSPACE_CONNECT_FAILED event even if TCP connection fails prior to authentication, and cleans up partial sockets. This does not yet prove the LAN connectivity issue is resolved.

All other Android/TV mirroring and PC Workspace features preserved. Android compile and hardware verification are required.
