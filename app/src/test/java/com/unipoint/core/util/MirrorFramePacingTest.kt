package com.unipoint.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MirrorFramePacingTest {
    @Test
    fun fastCaptureWaitsOnlyForTheRemainderOfTheFramePeriod() {
        assertEquals(350L, MirrorFramePacing.delayAfterCapture(1000L, 1100L, 450L))
    }

    @Test
    fun slowCaptureDoesNotAddAnExtraDelay() {
        assertEquals(0L, MirrorFramePacing.delayAfterCapture(1000L, 1600L, 450L))
    }
}
