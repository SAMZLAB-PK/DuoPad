package com.unipoint.presentation.ui

import android.app.Activity
import com.unipoint.core.util.MirrorCrashRecorder
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
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log
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
    @Volatile private var stage: String = "Not started"

    private fun checkpoint(ticket: Int, name: String) {
        stage = name
        Log.i("DouPadMirror", "session=$ticket stage=$name")
        runCatching {
            File(filesDir, "doupad_mirror_diagnostics.txt").appendText(
                "${System.currentTimeMillis()} session=$ticket $name\n"
            )
        }
        show(ticket, name)
    }

    private fun failure(ticket: Int, cause: Throwable) {
        val detail = cause.message ?: cause.javaClass.simpleName
        val message = "$stage — $detail"
        Log.e("DouPadMirror", message, cause)
        runCatching {
            val file = File(filesDir, "doupad_mirror_diagnostics.txt")
            file.appendText("${System.currentTimeMillis()} session=$ticket ERROR $message\n")
            if (file.length() > 65_536L) file.writeText(file.readText().takeLast(32_768))
        }
        show(ticket, "Live mirror error: $message. Long-press this message to copy diagnostics.")
    }

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
        // Never force orientation during startup: some Android 15/16 device
        // configurations reject an orientation request before the first draw.
        MirrorCrashRecorder.started(this)
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
            setOnLongClickListener {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val diagnostics = runCatching {
                    File(filesDir, "doupad_mirror_diagnostics.txt").readText().takeLast(7000)
                }.getOrDefault(text.toString())
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DouPad Mirror diagnostics",diagnostics))
                android.widget.Toast.makeText(this@DoupadMirrorActivity,"Mirror diagnostics copied",android.widget.Toast.LENGTH_SHORT).show()
                true
            }
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
        // Surface is kept at a stable size across rotation. Do not resize it
        // while a video decoder is attached.
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
                if (current.control != null) {
                    current.control?.sink?.write(packet)
                    current.control?.sink?.flush()
                } else if (action == ScrcpyControlProtocol.ACTION_UP) {
                    // Video-only fallback retains tap support without another scrcpy socket.
                    current.adb.shell("input tap ${point.x} ${point.y}")
                }
            } catch (e: Exception) {
                if (current.control != null) {
                    // Keep the video stream alive if only the control channel fails.
                    runCatching { current.control?.close() }
                    current.control = null
                }
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val ticket = generation.incrementAndGet()
        MirrorCrashRecorder.note(this,"SURFACE_CREATED: surface ready; starting worker ticket=$ticket")
        runCatching { worker.execute { stream(holder, ticket) } }
            .onFailure {
                MirrorCrashRecorder.note(this,"WORKER_START_FAILED: ${it.javaClass.simpleName}: ${it.message}")
                show(ticket, "Mirror surface unavailable: ${it.message}")
            }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Leave SurfaceView unchanged: resizing while the codec is attached
        // can destroy the Surface and abort the scrcpy session.
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
        MirrorCrashRecorder.finish(this, "Mirror Activity onDestroy")
        super.onDestroy()
    }

    private data class VideoProfile(
        val maxSize: Int, val fps: Int, val bitRate: Int, val control: Boolean
    )

    private fun stream(holder: SurfaceHolder, ticket: Int) {
        // Second preset deliberately disables scrcpy control socket. Some Android TV
        // firmware streams video reliably but rejects the second abstract socket.
        val profiles = listOf(
            VideoProfile(960, 24, 2_000_000, false),
            VideoProfile(720, 15, 1_200_000, true)
        )
        for ((index, profile) in profiles.withIndex()) {
            if (generation.get() != ticket) return
            checkpoint(ticket,
                "Preparing H.264 stream ${index+1}/${profiles.size}: ${profile.maxSize}p / ${profile.fps}fps / control=${profile.control}")
            val result = runCatching { streamAttempt(holder, ticket, profile) }
            if (generation.get() != ticket) return
            if (result.isSuccess) return
            val error = result.exceptionOrNull()
            val detail = error?.message ?: error?.javaClass?.simpleName ?: "Unknown video error"
            android.util.Log.w("DouPadVideo", "Video profile ${index + 1} failed: $detail", error)
            if (error != null) failure(ticket, error)
            if (index == profiles.lastIndex) {
                show(ticket,
                    "Live video unavailable: $detail. Use Screenshot for this TV, or retry with a supported encoder."
                )
            } else {
                show(ticket, "Video mode failed; trying compatibility stream (720p / video-only)…")
                Thread.sleep(450)
            }
        }
    }

    private fun streamAttempt(holder: SurfaceHolder, ticket: Int, profile: VideoProfile) {
        var own: Session? = null
        var drain: Thread? = null
        try {
            if (generation.get() != ticket) return
            val endpoint = intent.getStringExtra("endpoint") ?: error("No TV address")
            val host = endpoint.substringBefore(':')
            val port = endpoint.substringAfter(':', "5555").toInt()
            val keyDir = File(filesDir, "adb")
            val keys = AdbKeyPair.read(File(keyDir, "adbkey"), File(keyDir, "adbkey.pub"))
            checkpoint(ticket, "Authenticating to selected Android device")
            val adb = Dadb.create(host, port, keys, connectTimeout = 5000, socketTimeout = 30000)
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
                    "scid=$id log_level=warn video=true audio=false control=${profile.control} tunnel_forward=true " +
                    "send_dummy_byte=false send_device_meta=false send_codec_meta=true send_frame_meta=true " +
                    "video_codec=h264 max_size=${profile.maxSize} max_fps=${profile.fps} " +
                    "video_bit_rate=${profile.bitRate} cleanup=true"
            checkpoint(ticket, "Starting device-side video encoder")
            current.server = adb.open("shell:$command")

            checkpoint(ticket, "Opening H.264 video socket")
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
            if (profile.control) {
                // Video and control are separate sockets. Keep the fallback video-only.
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
            }

            val source = current.video?.source ?: error("TV video socket unavailable")
            if (profile.control) check(current.control != null) { "TV control socket unavailable" }
            checkpoint(ticket, "Reading scrcpy codec metadata")
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
                    enterImmersive()
                    // Never change requested orientation or SurfaceView size here.
                    // Doing so invalidates the surface during stream setup.
                }
            }

            checkpoint(ticket, "Initializing hardware H.264 decoder (${width}×${height})")
            val format = MediaFormat.createVideoFormat("video/avc", width, height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            format.setInteger(MediaFormat.KEY_PRIORITY, 0)
            val codec = MediaCodec.createDecoderByType("video/avc")
            current.codec = codec
            codec.configure(format, holder.surface, null, 0)
            codec.start()
            val firstFrame = AtomicBoolean(false)
            checkpoint(ticket, "Waiting for first decoded video frame")

            drain = thread(name = "doupad-video-output") {
                val info = MediaCodec.BufferInfo()
                try {
                    while (!current.closed && generation.get() == ticket) {
                        val index = codec.dequeueOutputBuffer(info, 10000)
                        if (index >= 0) {
                            codec.releaseOutputBuffer(index, true)
                            if (firstFrame.compareAndSet(false, true)) {
                                Log.i("DouPadMirror", "H.264 first video frame rendered")
                                runOnUiThread {
                                    if (generation.get() == ticket) status.visibility = View.GONE
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    failure(ticket, IOException("H.264 decoder output failed: ${e.message}", e))
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
            if (generation.get() == ticket) {
                throw IOException("Video pipeline: ${e.message ?: e.javaClass.simpleName}", e)
            }
        } finally {
            own?.close()
            drain?.join(1000)
            runCatching { own?.codec?.stop() }
            runCatching { own?.codec?.release() }
            if (session === own) session = null
        }
    }
}
