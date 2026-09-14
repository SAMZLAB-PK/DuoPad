package com.unipoint.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LatestPositionBufferTest {
    @Test
    fun latestPositionReplacesQueuedStalePositions() {
        val buffer = LatestPositionBuffer()

        buffer.offer(10, 20)
        buffer.offer(30, 40)
        buffer.offer(50, 60)

        assertEquals(PointerPosition(50, 60), buffer.poll())
        assertNull(buffer.poll())
    }

    @Test
    fun aNewPositionArrivingAfterDrainIsDeliveredSeparately() {
        val buffer = LatestPositionBuffer()

        buffer.offer(1, 2)
        assertEquals(PointerPosition(1, 2), buffer.poll())
        buffer.offer(3, 4)

        assertEquals(PointerPosition(3, 4), buffer.poll())
    }
}
