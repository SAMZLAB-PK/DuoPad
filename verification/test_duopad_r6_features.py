from pathlib import Path

root = Path(__file__).resolve().parents[1]
mirror = (root/'app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt').read_text()
monitor = (root/'app/src/main/java/com/unipoint/core/util/VisibleVideoMonitor.kt').read_text()
build = (root/'app/build.gradle.kts').read_text()

assert 'scheduleVisibleVideoSample(ticket,current,1500L)' in mirror
assert 'scheduleVisibleVideoSample(ticket,current,2500L)' in mirror
assert 'current.visibilityMonitor.observe(' in mirror
assert 'VIDEO_VISIBILITY_WATCHDOG' in mirror
assert 'VISIBLE_VIDEO_CONFIRMED' in mirror
assert 'AUTO_PROFILE_RETRY' in mirror
assert 'WINDOW_FOCUS:' in mirror
assert 'ACTIVITY_PAUSE:' in mirror
assert 'SURFACE_CHANGED:' in mirror
assert 'Executors.newCachedThreadPool' in mirror
assert 'WORKER_STARTED: surface ticket=' in mirror
assert 'sameAsPrevious=$unchanged' in mirror
assert 'listOf(1500L,3500L,7000L)' not in mirror
assert 'laterBlackMinimumMs: Long = 12_000L' in monitor
assert 'startupBlackMinimumMs: Long = 10_000L' in monitor
assert any(token in build for token in ['versionCode = 18', 'versionCode = 19', 'versionCode = 20', 'versionCode = 21', 'versionCode = 22', 'versionCode = 23', 'versionCode = 24', 'versionCode = 25', 'versionCode = 26', 'versionCode = 27', 'versionCode = 28', 'versionCode = 29', 'versionCode = 30'])
assert any(token in build for token in ['versionName = "0.16.2-aceconn-visibility-r6"', 'versionName = "0.16.3-r7-mirror-screen-awake"', 'versionName = "0.17.0-r8"', 'versionName = "0.17.0-r9"', 'versionName = "0.17.1-r10"', 'versionName = "0.17.2-r11"', 'versionName = "0.17.3-r12"', 'versionName = "0.17.4-r13"', 'versionName = "0.17.5-r14-navigation-theme"', 'versionName = "0.18.0-r15-pc-workspace"', 'versionName = "0.18.1-r16-pc-discovery-diagnostics"', 'versionName = "0.18.2-r17-pc-pin-handshake"', 'versionName = "0.18.3-r18-pc-workspace-qr-host-share"'])
print('DOUPAD_R6_CONTINUOUS_VISIBILITY_AND_RECOVERY_CHECKS_PASS')
