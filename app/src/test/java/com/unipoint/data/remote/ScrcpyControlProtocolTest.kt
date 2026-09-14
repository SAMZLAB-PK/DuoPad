package com.unipoint.data.remote

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ScrcpyControlProtocolTest {
    @Test
    fun keyMessageUsesScrcpyBigEndianLayout() {
        val bytes = ScrcpyControlProtocol.key(action = 0, keyCode = 23, repeat = 2, metaState = 0x1000)
        assertEquals(14, bytes.size)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(0, b.get().toInt())
        assertEquals(0, b.get().toInt())
        assertEquals(23, b.int)
        assertEquals(2, b.int)
        assertEquals(0x1000, b.int)
    }

    @Test
    fun touchMessageContainsPointerPositionPressureAndButtons() {
        val bytes = ScrcpyControlProtocol.touch(
            action = 2,
            pointerId = ScrcpyControlProtocol.POINTER_ID_MOUSE,
            x = 640,
            y = 360,
            screenWidth = 1280,
            screenHeight = 720,
            pressure = 1f,
            actionButton = 1,
            buttons = 1
        )
        assertEquals(32, bytes.size)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(2, b.get().toInt())
        assertEquals(2, b.get().toInt())
        assertEquals(-1L, b.long)
        assertEquals(640, b.int)
        assertEquals(360, b.int)
        assertEquals(1280, b.short.toInt() and 0xffff)
        assertEquals(720, b.short.toInt() and 0xffff)
        assertEquals(0xffff, b.short.toInt() and 0xffff)
        assertEquals(1, b.int)
        assertEquals(1, b.int)
    }

    @Test
    fun scrollMessageUsesSignedFixedPointAxes() {
        val bytes = ScrcpyControlProtocol.scroll(
            x = 100,
            y = 200,
            screenWidth = 1920,
            screenHeight = 1080,
            hScroll = 0f,
            vScroll = -1f,
            buttons = 0
        )
        assertEquals(21, bytes.size)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(3, b.get().toInt())
        assertEquals(100, b.int)
        assertEquals(200, b.int)
        assertEquals(1920, b.short.toInt() and 0xffff)
        assertEquals(1080, b.short.toInt() and 0xffff)
        assertEquals(0, b.short.toInt())
        assertEquals(Short.MIN_VALUE.toInt(), b.short.toInt())
        assertEquals(0, b.int)
    }

    @Test
    fun textMessagePrefixesUtf8ByteLength() {
        val bytes = ScrcpyControlProtocol.text("A✓")
        val payload = "A✓".encodeToByteArray()
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(1, b.get().toInt())
        assertEquals(payload.size, b.int)
        val actual = ByteArray(payload.size)
        b.get(actual)
        assertContentEquals(payload, actual)
    }
}
