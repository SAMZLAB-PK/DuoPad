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
assert 'versionCode = 18' in build
assert 'versionName = "0.16.2-aceconn-visibility-r6"' in build
print('DOUPAD_R6_CONTINUOUS_VISIBILITY_AND_RECOVERY_CHECKS_PASS')
