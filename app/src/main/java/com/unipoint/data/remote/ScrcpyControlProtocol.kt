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
    const val TYPE_UHID_CREATE = 12
    const val TYPE_UHID_INPUT = 13
    const val TYPE_UHID_DESTROY = 14

    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2
    const val ACTION_CANCEL = 3

    const val POINTER_ID_MOUSE = -1L
    const val POINTER_ID_FINGER = 0L

    const val BUTTON_PRIMARY = 1
    const val BUTTON_SECONDARY = 2
    const val BUTTON_TERTIARY = 4

    /** scrcpy reserves HID id 2 for its virtual mouse. */
    const val UHID_MOUSE_ID = 2

    /**
     * USB HID 1.11 mouse report descriptor used by scrcpy 3.3.1.
     * It exposes five buttons, relative X/Y, vertical wheel and horizontal pan.
     */
    val UHID_MOUSE_REPORT_DESCRIPTOR: ByteArray = intArrayOf(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x02, // Usage (Mouse)
        0xA1, 0x01, // Collection (Application)
        0x09, 0x01, // Usage (Pointer)
        0xA1, 0x00, // Collection (Physical)
        0x05, 0x09, // Usage Page (Buttons)
        0x19, 0x01, // Usage Minimum (1)
        0x29, 0x05, // Usage Maximum (5)
        0x15, 0x00, // Logical Minimum (0)
        0x25, 0x01, // Logical Maximum (1)
        0x95, 0x05, // Report Count (5)
        0x75, 0x01, // Report Size (1)
        0x81, 0x02, // Input (Data, Variable, Absolute)
        0x95, 0x01, // Report Count (1)
        0x75, 0x03, // Report Size (3)
        0x81, 0x01, // Input (Constant)
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x30, // Usage (X)
        0x09, 0x31, // Usage (Y)
        0x09, 0x38, // Usage (Wheel)
        0x15, 0x81, // Logical Minimum (-127)
        0x25, 0x7F, // Logical Maximum (127)
        0x75, 0x08, // Report Size (8)
        0x95, 0x03, // Report Count (3)
        0x81, 0x06, // Input (Data, Variable, Relative)
        0x05, 0x0C, // Usage Page (Consumer)
        0x0A, 0x38, 0x02, // Usage (AC Pan)
        0x15, 0x81, // Logical Minimum (-127)
        0x25, 0x7F, // Logical Maximum (127)
        0x75, 0x08, // Report Size (8)
        0x95, 0x01, // Report Count (1)
        0x81, 0x06, // Input (Data, Variable, Relative)
        0xC0, 0xC0
    ).map { it.toByte() }.toByteArray()

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

    fun uhidCreate(
        id: Int = UHID_MOUSE_ID,
        vendorId: Int = 0,
        productId: Int = 0,
        name: String = "DOUPAD Mouse",
        reportDescriptor: ByteArray = UHID_MOUSE_REPORT_DESCRIPTOR
    ): ByteArray {
        val nameBytes = name.encodeToByteArray().take(127).toByteArray()
        return ByteBuffer.allocate(1 + 2 + 2 + 2 + 1 + nameBytes.size + 2 + reportDescriptor.size)
            .order(ByteOrder.BIG_ENDIAN)
            .apply {
                put(TYPE_UHID_CREATE.toByte())
                putU16(id)
                putU16(vendorId)
                putU16(productId)
                put(nameBytes.size.toByte())
                put(nameBytes)
                putU16(reportDescriptor.size)
                put(reportDescriptor)
            }.array()
    }

    fun uhidInput(id: Int = UHID_MOUSE_ID, data: ByteArray): ByteArray =
        ByteBuffer.allocate(1 + 2 + 2 + data.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(TYPE_UHID_INPUT.toByte())
            putU16(id)
            putU16(data.size)
            put(data)
        }.array()

    fun uhidDestroy(id: Int = UHID_MOUSE_ID): ByteArray =
        ByteBuffer.allocate(3).order(ByteOrder.BIG_ENDIAN).apply {
            put(TYPE_UHID_DESTROY.toByte())
            putU16(id)
        }.array()

    /** Five-byte relative mouse report expected by scrcpy's UHID backend. */
    fun mouseReport(
        buttons: Int = 0,
        dx: Int = 0,
        dy: Int = 0,
        wheel: Int = 0,
        horizontalScroll: Int = 0
    ): ByteArray = byteArrayOf(
        (buttons and 0x1f).toByte(),
        dx.coerceIn(-127, 127).toByte(),
        dy.coerceIn(-127, 127).toByte(),
        wheel.coerceIn(-127, 127).toByte(),
        horizontalScroll.coerceIn(-127, 127).toByte()
    )

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
