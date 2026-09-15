import com.unipoint.core.util.DevicePoint
import com.unipoint.core.util.MirrorCoordinateMapper
import com.unipoint.data.remote.ScrcpyControlProtocol
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun main() {
    val key = ScrcpyControlProtocol.key(0, 23, 2, 0x1000)
    check(key.size == 14)
    ByteBuffer.wrap(key).order(ByteOrder.BIG_ENDIAN).let { b ->
        check(b.get().toInt() == 0)
        check(b.get().toInt() == 0)
        check(b.int == 23)
        check(b.int == 2)
        check(b.int == 0x1000)
    }

    val touch = ScrcpyControlProtocol.touch(2, -1L, 640, 360, 1280, 720, 1f, 1, 1)
    check(touch.size == 32)
    ByteBuffer.wrap(touch).order(ByteOrder.BIG_ENDIAN).let { b ->
        check(b.get().toInt() == 2)
        check(b.get().toInt() == 2)
        check(b.long == -1L)
        check(b.int == 640 && b.int == 360)
        check((b.short.toInt() and 0xffff) == 1280)
        check((b.short.toInt() and 0xffff) == 720)
        check((b.short.toInt() and 0xffff) == 0xffff)
        check(b.int == 1 && b.int == 1)
    }

    val scroll = ScrcpyControlProtocol.scroll(100, 200, 1920, 1080, 0f, -1f)
    check(scroll.size == 21)

    val createMouse = ScrcpyControlProtocol.uhidCreate(name = "")
    ByteBuffer.wrap(createMouse).order(ByteOrder.BIG_ENDIAN).let { b ->
        check((b.get().toInt() and 0xff) == 12)
        check((b.short.toInt() and 0xffff) == 2)
        check((b.short.toInt() and 0xffff) == 0)
        check((b.short.toInt() and 0xffff) == 0)
        check((b.get().toInt() and 0xff) == 0)
        check((b.short.toInt() and 0xffff) == ScrcpyControlProtocol.UHID_MOUSE_REPORT_DESCRIPTOR.size)
        check(b.remaining() == ScrcpyControlProtocol.UHID_MOUSE_REPORT_DESCRIPTOR.size)
    }
    check(ScrcpyControlProtocol.UHID_MOUSE_REPORT_DESCRIPTOR.size == 67)

    val report = ScrcpyControlProtocol.mouseReport(buttons = 3, dx = 200, dy = -200, wheel = 2, horizontalScroll = -3)
    check(report.contentEquals(byteArrayOf(3, 127, -127, 2, -3)))
    val input = ScrcpyControlProtocol.uhidInput(data = report)
    ByteBuffer.wrap(input).order(ByteOrder.BIG_ENDIAN).let { b ->
        check((b.get().toInt() and 0xff) == 13)
        check((b.short.toInt() and 0xffff) == 2)
        check((b.short.toInt() and 0xffff) == 5)
        val actual = ByteArray(5); b.get(actual)
        check(actual.contentEquals(report))
    }
    val destroy = ScrcpyControlProtocol.uhidDestroy()
    check(destroy.contentEquals(byteArrayOf(14, 0, 2)))
    check(MirrorCoordinateMapper.map(640f, 360f, 1280, 720, 1920, 1080) == DevicePoint(960, 540))
    check(MirrorCoordinateMapper.map(-20f, 900f, 1280, 720, 1920, 1080) == DevicePoint(0, 1079))
    println("REALTIME_PROTOCOL_SMOKE_OK")
}
