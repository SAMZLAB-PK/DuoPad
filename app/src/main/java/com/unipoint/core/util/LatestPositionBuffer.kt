package com.unipoint.core.util

import java.util.concurrent.atomic.AtomicReference

data class PointerPosition(val x: Int, val y: Int)

/**
 * A single-slot, latest-position-wins queue. Offering a new position never
 * creates a backlog behind a slow ADB request.
 */
class LatestPositionBuffer {
    private val latest = AtomicReference<PointerPosition?>(null)

    fun offer(x: Int, y: Int) {
        latest.set(PointerPosition(x, y))
    }

    fun poll(): PointerPosition? = latest.getAndSet(null)
}
