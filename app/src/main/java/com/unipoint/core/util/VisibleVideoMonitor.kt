package com.unipoint.core.util

/**
 * Decision-only watchdog for the pixels actually displayed by a Mirror SurfaceView.
 * A MediaCodec output callback is not evidence of a visible TV image.
 *
 * Allow time for scrcpy / the TV home screen to become visible before trying another
 * profile. Once an image has appeared, require a longer, stable near-black period,
 * otherwise a dark scene or a brief channel switch would cause disruptive retries.
 * This does NOT decide whether protected content or TV firmware blocks capture.
 */
class VisibleVideoMonitor(
    private val startupBlackMinimumMs: Long = 10_000L,
    private val laterBlackMinimumMs: Long = 12_000L
) {
    data class Observation(
        val visible: Boolean,
        val firstVisible: Boolean,
        val recovered: Boolean,
        val blackSamples: Int,
        val blackDurationMs: Long,
        val stableBlackSamples: Int,
        val shouldRetry: Boolean
    )

    private var blackSinceMs = -1L
    private var previousFingerprint: Int? = null
    private var stableBlackSamples = 0
    private var blackSamples = 0
    private var hasSeenVisibleFrame = false
    private var retryIssued = false

    fun observe(blackPercent: Int, frameHash: Int, elapsedMs: Long): Observation {
        val visible = blackPercent <= 95
        val firstVisible = visible && !hasSeenVisibleFrame
        val recovered = visible && blackSamples > 0 && hasSeenVisibleFrame
        if (visible) {
            hasSeenVisibleFrame = true
            blackSinceMs = -1L
            blackSamples = 0
            stableBlackSamples = 0
            previousFingerprint = null
        } else if (blackPercent >= 98) {
            if (blackSamples == 0) {
                blackSinceMs = elapsedMs
                stableBlackSamples = 0
            }
            blackSamples++
            stableBlackSamples = if (previousFingerprint == frameHash) stableBlackSamples + 1 else 0
            previousFingerprint = frameHash
        } else {
            // A borderline dark picture is inconclusive. Do not reset the fact
            // that visible content was seen, but never retry on borderline pixels.
            blackSinceMs = -1L
            blackSamples = 0
            stableBlackSamples = 0
            previousFingerprint = null
        }

        val blackDurationMs = if (blackSinceMs >= 0L) (elapsedMs - blackSinceMs).coerceAtLeast(0L) else 0L
        val minimum = if (hasSeenVisibleFrame) laterBlackMinimumMs else startupBlackMinimumMs
        val minimumSamples = if (hasSeenVisibleFrame) 5 else 3
        val stableOrLong = stableBlackSamples >= 2 || blackSamples >= 7
        val shouldRetry = !retryIssued && blackSamples >= minimumSamples &&
            blackDurationMs >= minimum && stableOrLong
        if (shouldRetry) retryIssued = true
        return Observation(visible, firstVisible, recovered, blackSamples, blackDurationMs,
            stableBlackSamples, shouldRetry)
    }
}
