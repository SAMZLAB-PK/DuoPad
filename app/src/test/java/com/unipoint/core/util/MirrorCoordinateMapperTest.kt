package com.unipoint.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MirrorCoordinateMapperTest {
    @Test
    fun centerMapsToCenterOfDevice() {
        assertEquals(
            DevicePoint(960, 540),
            MirrorCoordinateMapper.map(640f, 360f, 1280, 720, 1920, 1080)
        )
    }

    @Test
    fun coordinatesAreClampedToDeviceEdges() {
        assertEquals(
            DevicePoint(0, 1079),
            MirrorCoordinateMapper.map(-20f, 900f, 1280, 720, 1920, 1080)
        )
    }
}
