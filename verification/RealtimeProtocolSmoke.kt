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
    check(MirrorCoordinateMapper.map(640f, 360f, 1280, 720, 1920, 1080) == DevicePoint(960, 540))
    check(MirrorCoordinateMapper.map(-20f, 900f, 1280, 720, 1920, 1080) == DevicePoint(0, 1079))
    println("REALTIME_PROTOCOL_SMOKE_OK")
}
