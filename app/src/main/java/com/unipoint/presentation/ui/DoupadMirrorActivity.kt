package com.unipoint.presentation.ui

import android.app.Activity
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.TextView
import com.unipoint.core.util.MirrorCoordinateMapper
import com.unipoint.data.remote.ScrcpyControlProtocol
import dadb.AdbKeyPair
import dadb.AdbStream
import dadb.Dadb
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Low-latency scrcpy 3.3.1 video + direct touch client. Each surface owns its session. */
class DoupadMirrorActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var surface: SurfaceView
    private lateinit var status: TextView
    private val generation = AtomicInteger()
    private val worker = Executors.newSingleThreadExecutor()
    private val inputWorker = Executors.newSingleThreadExecutor()
    @Volatile private var session: Session? = null

    private class Session(val adb: Dadb) {
        @Volatile var closed = false
        var server: AdbStream? = null
        var video: AdbStream? = null
        var control: AdbStream? = null
        var codec: MediaCodec? = null
        var displayWidth = 0
        var displayHeight = 0

        @Synchronized fun close() {
            if (closed) return
            closed = true
            runCatching { control?.close() }
            runCatching { video?.close() }
            runCatching { server?.close() }
            runCatching { adb.close() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        status = TextView(this).apply {
            text = "Connecting live mirror… · Back to close"
            setTextColor(Color.WHITE)
            setPadding(24, 20, 24, 20)
        }
        surface = SurfaceView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { _, event ->
                sendTouch(event)
                true
            }
        }
        layout.addView(status)
        layout.addView(surface, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        surface.holder.addCallback(this)
    }

    private fun show(ticket: Int, message: String) = runOnUiThread {
        if (generation.get() == ticket) status.text = message
    }

    private fun sendTouch(event: MotionEvent) {
        val current = session ?: return
        if (current.closed || current.displayWidth <= 0 || current.displayHeight <= 0) return
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> ScrcpyControlProtocol.ACTION_DOWN
            MotionEvent.ACTION_MOVE -> ScrcpyControlProtocol.ACTION_MOVE
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> ScrcpyControlProtocol.ACTION_UP
            MotionEvent.ACTION_CANCEL -> ScrcpyControlProtocol.ACTION_CANCEL
            else -> return
        }
        val actionIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val pointerIndex = if (action == ScrcpyControlProtocol.ACTION_MOVE) 0 else actionIndex
        val pointerId = event.getPointerId(pointerIndex).toLong()
        val point = MirrorCoordinateMapper.map(
            x = event.getX(pointerIndex),
            y = event.getY(pointerIndex),
            viewWidth = surface.width,
            viewHeight = surface.height,
            deviceWidth = current.displayWidth,
            deviceHeight = current.displayHeight
        )
        val pressure = if (
            action == ScrcpyControlProtocol.ACTION_UP || action == ScrcpyControlProtocol.ACTION_CANCEL
        ) 0f else event.getPressure(pointerIndex).coerceIn(0.01f, 1f)
        val packet = ScrcpyControlProtocol.touch(
            action = action,
            pointerId = pointerId,
            x = point.x,
            y = point.y,
            screenWidth = current.displayWidth,
            screenHeight = current.displayHeight,
            pressure = pressure
        )
        inputWorker.execute {
            if (current.closed || session !== current) return@execute
            try {
                current.control?.sink?.write(packet)
                current.control?.sink?.flush()
            } catch (_: Exception) {
                current.close()
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val ticket = generation.incrementAndGet()
        worker.execute { stream(holder, ticket) }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        generation.incrementAndGet()
        session?.close()
    }

    override fun onDestroy() {
        generation.incrementAndGet()
        session?.close()
        inputWorker.shutdownNow()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun stream(holder: SurfaceHolder, ticket: Int) {
        var own: Session? = null
        var drain: Thread? = null
        try {
            if (generation.get() != ticket) return
            val endpoint = intent.getStringExtra("endpoint") ?: error("No TV address")
            val host = endpoint.substringBefore(':')
            val port = endpoint.substringAfter(':', "5555").toInt()
            val keyDir = File(filesDir, "adb")
            val keys = AdbKeyPair.read(File(keyDir, "adbkey"), File(keyDir, "adbkey.pub"))
            val adb = Dadb.create(host, port, keys, connectTimeout = 5000, socketTimeout = 10000)
            val current = Session(adb)
            own = current
            session = current
            if (generation.get() != ticket) return

            val local = File(cacheDir, "scrcpy-server.jar")
            assets.open("scrcpy-server.jar").use { input ->
                local.outputStream().use { output -> input.copyTo(output) }
            }
            adb.push(local, "/data/local/tmp/doupad-mirror.jar", 420)
            val id = "%08x".format(java.security.SecureRandom().nextInt(Int.MAX_VALUE))
            val command =
                "CLASSPATH=/data/local/tmp/doupad-mirror.jar app_process / com.genymobile.scrcpy.Server 3.3.1 " +
                    "scid=$id log_level=warn video=true audio=false control=true tunnel_forward=true " +
                    "send_dummy_byte=false send_device_meta=false send_codec_meta=true send_frame_meta=true " +
                    "video_codec=h264 max_size=1280 max_fps=60 video_bit_rate=8000000 cleanup=true"
            current.server = adb.open("shell:$command")

            for (attempt in 0 until 30) {
                if (current.closed || generation.get() != ticket) return
                try {
                    current.video = adb.open("localabstract:scrcpy_$id")
                    break
                } catch (e: Exception) {
                    if (attempt == 29) throw e
                    Thread.sleep(100)
                }
            }
            // scrcpy opens sockets in video -> audio -> control order. Audio is disabled,
            // so this second connection is the dedicated binary control channel.
            for (attempt in 0 until 30) {
                if (current.closed || generation.get() != ticket) return
                try {
                    current.control = adb.open("localabstract:scrcpy_$id")
                    break
                } catch (e: Exception) {
                    if (attempt == 29) throw e
                    Thread.sleep(100)
                }
            }

            val source = current.video?.source ?: error("TV video socket unavailable")
            check(current.control != null) { "TV control socket unavailable" }
            val codecId = source.readInt()
            val width = source.readInt()
            val height = source.readInt()
            require(codecId == 0x68323634 && width in 1..8192 && height in 1..8192) {
                "Unsupported video stream"
            }
            current.displayWidth = width
            current.displayHeight = height

            val codec = MediaCodec.createDecoderByType("video/avc")
            current.codec = codec
            codec.configure(MediaFormat.createVideoFormat("video/avc", width, height), holder.surface, null, 0)
            codec.start()
            show(ticket, "Live · $width × $height · 60 fps target · Touch enabled")

            runOnUiThread {
                if (generation.get() == ticket) {
                    val parent = surface.parent as LinearLayout
                    parent.post {
                        if (generation.get() == ticket) {
                            val availableW = parent.width
                            val availableH = parent.height - status.height
                            val scale = minOf(
                                availableW.toFloat() / width,
                                availableH.toFloat() / height
                            )
                            surface.layoutParams = LinearLayout.LayoutParams(
                                (width * scale).toInt(),
                                (height * scale).toInt()
                            ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
                        }
                    }
                }
            }

            drain = thread(name = "doupad-video-output") {
                val info = MediaCodec.BufferInfo()
                try {
                    while (!current.closed && generation.get() == ticket) {
                        val index = codec.dequeueOutputBuffer(info, 10000)
                        if (index >= 0) codec.releaseOutputBuffer(index, true)
                    }
                } catch (_: Exception) {
                    current.close()
                }
            }

            while (!current.closed && generation.get() == ticket) {
                val meta = source.readLong()
                val size = source.readInt()
                require(size in 1..16777216) { "Invalid video packet" }
                val bytes = source.readByteArray(size.toLong())
                var index = -1
                while (index < 0 && !current.closed && generation.get() == ticket) {
                    index = codec.dequeueInputBuffer(10000)
                }
                if (index < 0) break
                val input = codec.getInputBuffer(index) ?: error("Decoder buffer unavailable")
                require(size <= input.capacity()) { "Video frame exceeds decoder buffer" }
                input.clear()
                input.put(bytes)
                codec.queueInputBuffer(
                    index,
                    0,
                    size,
                    meta and 0x3fffffffffffffffL,
                    if (meta < 0) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                )
            }
        } catch (e: Exception) {
            show(
                ticket,
                "Mirror stopped: ${e.message ?: e.javaClass.simpleName}. Check TV authorization and try again."
            )
        } finally {
            own?.close()
            drain?.join(1000)
            runCatching { own?.codec?.stop() }
            runCatching { own?.codec?.release() }
            if (session === own) session = null
        }
    }
}
