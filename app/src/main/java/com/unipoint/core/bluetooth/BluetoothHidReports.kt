package com.unipoint.core.bluetooth

/** Pure HID report definitions/builders so report behavior can be tested without Android. */
object BluetoothHidReports {
    const val MOUSE_REPORT_ID = 1
    const val KEYBOARD_REPORT_ID = 2

    /** Composite mouse + boot-style keyboard descriptor with separate report IDs. */
    val REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        // Mouse
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x02,             // Usage (Mouse)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), MOUSE_REPORT_ID.toByte(),
        0x09, 0x01,             // Usage (Pointer)
        0xA1.toByte(), 0x00,    // Collection (Physical)
        0x05, 0x09,             // Usage Page (Buttons)
        0x19, 0x01,             // Usage Minimum 1
        0x29, 0x03,             // Usage Maximum 3
        0x15, 0x00,             // Logical Minimum 0
        0x25, 0x01,             // Logical Maximum 1
        0x95.toByte(), 0x03,    // Report Count 3
        0x75, 0x01,             // Report Size 1
        0x81.toByte(), 0x02,    // Input Data,Var,Abs
        0x95.toByte(), 0x01,
        0x75, 0x05,
        0x81.toByte(), 0x03,    // Padding
        0x05, 0x01,
        0x09, 0x30,             // X
        0x09, 0x31,             // Y
        0x09, 0x38,             // Wheel
        0x15, 0x81.toByte(),    // -127
        0x25, 0x7F,             // 127
        0x75, 0x08,
        0x95.toByte(), 0x03,
        0x81.toByte(), 0x06,    // Input Data,Var,Rel
        0xC0.toByte(),
        0xC0.toByte(),

        // Keyboard
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x06,             // Usage (Keyboard)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), KEYBOARD_REPORT_ID.toByte(),
        0x05, 0x07,             // Usage Page (Keyboard)
        0x19, 0xE0.toByte(),    // Left Control
        0x29, 0xE7.toByte(),    // Right GUI
        0x15, 0x00,
        0x25, 0x01,
        0x75, 0x01,
        0x95.toByte(), 0x08,
        0x81.toByte(), 0x02,    // Modifier bitmap
        0x95.toByte(), 0x01,
        0x75, 0x08,
        0x81.toByte(), 0x01,    // Reserved byte
        0x95.toByte(), 0x05,
        0x75, 0x01,
        0x05, 0x08,             // LEDs
        0x19, 0x01,
        0x29, 0x05,
        0x91.toByte(), 0x02,
        0x95.toByte(), 0x01,
        0x75, 0x03,
        0x91.toByte(), 0x01,
        0x95.toByte(), 0x06,
        0x75, 0x08,
        0x15, 0x00,
        0x25, 0x65,
        0x05, 0x07,
        0x19, 0x00,
        0x29, 0x65,
        0x81.toByte(), 0x00,    // Six key slots
        0xC0.toByte()
    )

    fun mouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int): ByteArray = byteArrayOf(
        (buttons and 0x07).toByte(),
        dx.coerceIn(-127, 127).toByte(),
        dy.coerceIn(-127, 127).toByte(),
        wheel.coerceIn(-127, 127).toByte()
    )

    fun keyboardReport(keyCode: Int, down: Boolean, modifiers: Int = 0): ByteArray {
        if (!down) return ByteArray(8)
        return byteArrayOf(
            (modifiers and 0xff).toByte(),
            0,
            (keyCode and 0xff).toByte(),
            0, 0, 0, 0, 0
        )
    }
}
