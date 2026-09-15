import com.unipoint.core.bluetooth.BluetoothHidReports

fun main() {
    check(BluetoothHidReports.MOUSE_REPORT_ID != BluetoothHidReports.KEYBOARD_REPORT_ID)
    val mouse = BluetoothHidReports.mouseReport(buttons = 3, dx = 250, dy = -250, wheel = 7)
    check(mouse.size == 4)
    check(mouse[0].toInt() and 0xff == 3)
    check(mouse[1].toInt() == 127)
    check(mouse[2].toInt() == -127)
    check(mouse[3].toInt() == 7)

    val keyboard = BluetoothHidReports.keyboardReport(keyCode = 0x04, down = true, modifiers = 0x02)
    check(keyboard.size == 8)
    check(keyboard[0].toInt() and 0xff == 0x02)
    check(keyboard[1].toInt() == 0)
    check(keyboard[2].toInt() and 0xff == 0x04)
    check(keyboard.drop(3).all { it.toInt() == 0 })

    val released = BluetoothHidReports.keyboardReport(keyCode = 0x04, down = false, modifiers = 0x02)
    check(released.all { it.toInt() == 0 })

    check(BluetoothHidReports.REPORT_DESCRIPTOR.isNotEmpty())
    println("BLUETOOTH_HID_REPORTS_SMOKE_OK")
}
