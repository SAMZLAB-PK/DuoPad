import com.unipoint.core.util.BlackFrameRecovery

fun main() {
    val monitor=BlackFrameRecovery()
    check(!monitor.observe(100)) { "A single black frame must not trigger a retry" }
    check(monitor.consecutiveBlackSamples==1)
    check(!monitor.observe(20)) { "Real content should reset the detector" }
    check(monitor.consecutiveBlackSamples==0)
    check(!monitor.observe(99))
    check(monitor.observe(100)) { "Two sustained black samples should trigger a retry" }
    println("BLACK_FRAME_RECOVERY_SMOKE_PASS")
}
