import com.unipoint.core.util.VisibleVideoMonitor

fun main() {
    val monitor = VisibleVideoMonitor()
    val blackHash = 0xabcdef
    check(!monitor.observe(100, blackHash, 0L).shouldRetry)
    check(!monitor.observe(100, blackHash, 2_500L).shouldRetry)
    check(!monitor.observe(100, blackHash, 5_000L).shouldRetry)
    check(!monitor.observe(100, blackHash, 7_500L).shouldRetry)
    check(monitor.observe(100, blackHash, 10_000L).shouldRetry) {
        "Startup black beyond warm-up and multiple stable samples should recover"
    }
    check(!monitor.observe(100, blackHash, 12_500L).shouldRetry) {
        "One session may issue only one automatic retry"
    }

    val slowStart = VisibleVideoMonitor()
    check(!slowStart.observe(100, blackHash, 1_500L).shouldRetry)
    check(!slowStart.observe(100, blackHash, 4_000L).shouldRetry)
    check(slowStart.observe(0, 12, 6_500L).firstVisible) {
        "Delayed TV image must appear without premature profile cycling"
    }
    check(!slowStart.observe(98, 12, 9_000L).shouldRetry)
    check(!slowStart.observe(98, 12, 11_500L).shouldRetry)
    check(!slowStart.observe(98, 12, 14_000L).shouldRetry)
    check(!slowStart.observe(98, 12, 16_500L).shouldRetry)
    check(!slowStart.observe(98, 12, 19_000L).shouldRetry)
    check(slowStart.observe(98, 12, 21_500L).shouldRetry) {
        "A sustained stable late black screen should trigger a controlled retry"
    }

    val recovered = VisibleVideoMonitor()
    recovered.observe(100, blackHash, 0L)
    recovered.observe(100, blackHash, 2_500L)
    check(recovered.observe(33, 31, 5_000L).firstVisible)
    check(!recovered.observe(98, 31, 7_500L).shouldRetry)
    check(recovered.observe(1, 42, 10_000L).recovered)
    check(!recovered.observe(0, 43, 12_500L).shouldRetry)
    println("VISIBLE_VIDEO_MONITOR_SMOKE_PASS")
}
