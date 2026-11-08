package com.unipoint.core.util

/**
 * Conservative visual watchdog used ONLY by the opt-in TV Box compatibility mode.
 * A decoder producing a frame does not prove non-black content was captured.
 * The caller must sample at separated points in time and may then try another
 * non-DRM-bypassing encoder. This detector does not decide whether content is DRM.
 */
class BlackFrameRecovery(
    private val minimumBlackPercent: Int = 99,
    private val requiredSamples: Int = 2
) {
    var consecutiveBlackSamples: Int = 0
        private set

    fun observe(blackPercent: Int): Boolean {
        consecutiveBlackSamples = if (blackPercent >= minimumBlackPercent) {
            (consecutiveBlackSamples + 1).coerceAtMost(requiredSamples)
        } else 0
        return consecutiveBlackSamples >= requiredSamples
    }
}
