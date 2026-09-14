package com.unipoint.core.util

import kotlin.math.roundToInt

data class DevicePoint(val x: Int, val y: Int)

object MirrorCoordinateMapper {
    fun map(
        x: Float,
        y: Float,
        viewWidth: Int,
        viewHeight: Int,
        deviceWidth: Int,
        deviceHeight: Int
    ): DevicePoint {
        if (viewWidth <= 0 || viewHeight <= 0 || deviceWidth <= 0 || deviceHeight <= 0) {
            return DevicePoint(0, 0)
        }
        val dx = (x / viewWidth.toFloat() * deviceWidth)
            .roundToInt()
            .coerceIn(0, deviceWidth - 1)
        val dy = (y / viewHeight.toFloat() * deviceHeight)
            .roundToInt()
            .coerceIn(0, deviceHeight - 1)
        return DevicePoint(dx, dy)
    }
}
