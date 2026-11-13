# DuoPad Pro v0.18.7 R22 — Single Windows PC Workspace

## Fixed
- The existing screenshot's four-card Windows PC Workspace is the **only** PC dashboard. Removed duplicate Welcome to PC, secondary tabs, and Open Full PC Workspace action.
- Each card opens only its selected feature: Screen, Drives & Files, System Information, or Keyboard.
- System Information route now uses Info rather than Workspace.
- Full-screen PC Screen exits directly to the four-card dashboard; portrait orientation, system bars, and sleep behaviour restore after leaving.
- Windows Keyboard returns to the four-card dashboard and portrait orientation on exit.
- Cold app start shows Devices (not PC Workspace). Saved PC auto-reconnect remains behind the Scenes without changing navigation.
- Disconnected device cards no longer attempt a PC connection with an empty endpoint; they return to Devices with an explanation.
- Removed incorrect LAN-only/Host v4 notes; Host Pro v5 supports approved Tailscale tailnet connections.
- Error display no longer stays in an indefinite loading state for Info/Files after a failed request.
- Original user-supplied D logo and recent Compose painterResource startup crash hotfix preserved.

## Verification
- Automated source contracts and Gradle unit tests in GitHub Actions.
- GitHub Android Debug APK is for testing and is **not** a Play Store release. Publish a signed AAB using a persistent upload key, complete Data Safety and privacy policy, and perform real device acceptance tests (orientation, clean launch, reconnect, transfers, screen FPS, Android TV compatibility).
- targetSdk 36 meets Google's post-August-2026 general new-app requirement, but runtime policy issues still need testing.
- No guarantee of AnyDesk-level latency: desktop preview currently pulls JPEG frames over authenticated TCP.
