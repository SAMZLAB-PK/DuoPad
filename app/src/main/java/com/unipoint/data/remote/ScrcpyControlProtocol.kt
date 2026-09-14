package com.unipoint.data.remote

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Binary writer for the scrcpy 3.3.x control protocol used by DOUPAD's bundled server. */
object ScrcpyControlProtocol {
    const val TYPE_INJECT_KEYCODE = 0
    const val TYPE_INJECT_TEXT = 1
    const val TYPE_INJECT_TOUCH_EVENT = 2
    const val TYPE_INJECT_SCROLL_EVENT = 3

    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2
    const val ACTION_CANCEL = 3

    const val POINTER_ID_MOUSE = -1L
    const val POINTER_ID_FINGER = 0L

    const val BUTTON_PRIMARY = 1
    const val BUTTON_SECONDARY = 2
    const val BUTTON_TERTIARY = 4

    fun key(action: Int, keyCode: Int, repeat: Int = 0, metaState: Int = 0): ByteArray =
        ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN).apply {
            put(TYPE_INJECT_KEYCODE.toByte())
            put(action.toByte())
            putInt(keyCode)
            putInt(repeat)
            putInt(metaState)
        }.array()

    fun text(text: String): ByteArray {
        val utf8 = text.encodeToByteArray()
        return ByteBuffer.allocate(1 + 4 + utf8.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(TYPE_INJECT_TEXT.toByte())
            putInt(utf8.size)
            put(utf8)
        }.array()
    }

    fun touch(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        actionButton: Int = 0,
        buttons: Int = 0
    ): ByteArray = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN).apply {
        put(TYPE_INJECT_TOUCH_EVENT.toByte())
        put(action.toByte())
        putLong(pointerId)
        putInt(x)
        putInt(y)
        putU16(screenWidth)
        putU16(screenHeight)
        putU16(unsignedFixedPoint16(pressure))
        putInt(actionButton)
        putInt(buttons)
    }.array()

    fun scroll(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        hScroll: Float,
        vScroll: Float,
        buttons: Int = 0
    ): ByteArray = ByteBuffer.allocate(21).order(ByteOrder.BIG_ENDIAN).apply {
        put(TYPE_INJECT_SCROLL_EVENT.toByte())
        putInt(x)
        putInt(y)
        putU16(screenWidth)
        putU16(screenHeight)
        putShort(signedFixedPoint16(hScroll))
        putShort(signedFixedPoint16(vScroll))
        putInt(buttons)
    }.array()

    private fun ByteBuffer.putU16(value: Int) {
        putShort((value.coerceIn(0, 0xffff) and 0xffff).toShort())
    }

    private fun unsignedFixedPoint16(value: Float): Int {
        val clamped = value.coerceIn(0f, 1f)
        return if (clamped >= 1f) 0xffff else (clamped * 0x10000).roundToInt().coerceIn(0, 0xffff)
    }

    private fun signedFixedPoint16(value: Float): Short {
        val clamped = value.coerceIn(-1f, 1f)
        if (clamped <= -1f) return Short.MIN_VALUE
        if (clamped >= 1f) return Short.MAX_VALUE
        return (clamped * 0x8000).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
