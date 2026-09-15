package com.unipoint.presentation.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
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
    private lateinit var root: FrameLayout
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
        enterImmersive()

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surface = SurfaceView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { _, event ->
                sendTouch(event)
                true
            }
        }
        status = TextView(this).apply {
            text = "Connecting live mirror… · Back to close"
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(24, 20, 24, 20)
        }
        root.addView(
            surface,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        root.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )
        setContentView(root)
        surface.holder.addCallback(this)
    }

    private fun enterImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enterImmersive()
        session?.let { current ->
            if (current.displayWidth > 0 && current.displayHeight > 0) {
                root.post { fitSurface(current.displayWidth, current.displayHeight) }
            }
        }
    }

    private fun show(ticket: Int, message: String) = runOnUiThread {
        if (generation.get() == ticket) {
            status.visibility = View.VISIBLE
            status.text = message
        }
    }

    private fun fitSurface(videoWidth: Int, videoHeight: Int) {
        val availableW = root.width
        val availableH = root.height
        if (availableW <= 0 || availableH <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val scale = minOf(
            availableW.toFloat() / videoWidth.toFloat(),
            availableH.toFloat() / videoHeight.toFloat()
        )
        surface.layoutParams = FrameLayout.LayoutParams(
            (videoWidth * scale).toInt().coerceAtLeast(1),
            (videoHeight * scale).toInt().coerceAtLeast(1),
            Gravity.CENTER
        )
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

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        session?.let { current ->
            if (current.displayWidth > 0 && current.displayHeight > 0) {
                fitSurface(current.displayWidth, current.displayHeight)
            }
        }
    }

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
                    "video_codec=h264 max_size=1280 max_fps=30 video_bit_rate=4000000 cleanup=true"
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

            runOnUiThread {
                if (generation.get() == ticket) {
                    requestedOrientation = if (width >= height) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }
                    enterImmersive()
                    root.post {
                        if (generation.get() == ticket) {
                            fitSurface(width, height)
                            status.visibility = View.GONE
                        }
                    }
                }
            }

            val codec = MediaCodec.createDecoderByType("video/avc")
            current.codec = codec
            codec.configure(MediaFormat.createVideoFormat("video/avc", width, height), holder.surface, null, 0)
            codec.start()

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
