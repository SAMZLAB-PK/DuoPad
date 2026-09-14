package com.unipoint.core.util

import kotlin.math.max

object MirrorFramePacing {
    const val TARGET_FRAME_PERIOD_MS = 450L

    /**
     * Pace from the beginning of one capture to the beginning of the next.
     * A slow capture consumes the whole period and receives no extra delay.
     */
    fun delayAfterCapture(captureStartedAtMs: Long, captureFinishedAtMs: Long, framePeriodMs: Long = TARGET_FRAME_PERIOD_MS): Long {
        require(framePeriodMs >= 0) { "framePeriodMs must be non-negative" }
        return max(0L, framePeriodMs - (captureFinishedAtMs - captureStartedAtMs))
    }
}
