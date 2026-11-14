package com.unipoint.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import com.unipoint.presentation.ui.PhoneMirrorActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-approved MediaProjection sender. Connects only to the PC endpoint selected by
 * the user and authenticates using the existing DuoPad Host PIN before each frame.
 * Frames are never persisted and do not use a public relay.
 */
class PhoneMirrorForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.samzlab.duopad.PHONE_MIRROR_START"
        const val ACTION_STOP = "com.samzlab.duopad.PHONE_MIRROR_STOP"
        private const val CHANNEL = "duopad_phone_mirror"
        private const val NOTIFICATION_ID = 180
    }
    private val stopping = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val frames = Channel<Frame>(Channel.CONFLATED)
    private data class Frame(val jpeg: ByteArray, val width: Int, val height: Int)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var frameTime = 0L
    private var connection: Socket? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL, "DuoPad phone screen sharing", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(): Notification {
        val stop = Intent(this, PhoneMirrorForegroundService::class.java).apply { action=ACTION_STOP }
        val pending = PendingIntent.getService(this, 0, stop,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("DuoPad · Sharing phone to PC")
            .setContentText("Phone screen is visible in your paired PC Host. Tap Stop to end.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop sharing", pending)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || projection != null) return START_NOT_STICKY
        if(Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification())
        val data: Intent? = if(Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra("projection_data", Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra("projection_data")
        if(data == null) { stopSelf(); return START_NOT_STICKY }
        val host = intent.getStringExtra("host").orEmpty()
        val port = intent.getIntExtra("port",27845)
        val pin = intent.getStringExtra("pin").orEmpty()
        if(host.isBlank() || port !in 1..65535) { stopSelf(); return START_NOT_STICKY }
        try {
            val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(intent.getIntExtra("projection_result",-1), data)
            if(projection == null) { stopSelf(); return START_NOT_STICKY }
            captureThread=HandlerThread("DuoPadPhoneMirrorCapture").also { it.start() }
            val handler=Handler(captureThread!!.looper)
            projection!!.registerCallback(object: MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            },handler)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.getRealMetrics(metrics)
            val scale = minOf(1f, 720f/metrics.widthPixels)
            val width=(metrics.widthPixels*scale).toInt().coerceAtLeast(160)
            val height=(metrics.heightPixels*scale).toInt().coerceAtLeast(160)
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
            reader!!.setOnImageAvailableListener({ source ->
                val image=source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val now=SystemClock.elapsedRealtime()
                    if(now-frameTime < 160L || stopping.get()) return@setOnImageAvailableListener
                    frameTime=now
                    val plane=image.planes[0]
                    val paddedWidth=plane.rowStride/plane.pixelStride
                    val bitmap=Bitmap.createBitmap(paddedWidth,height,Bitmap.Config.ARGB_8888)
                    plane.buffer.rewind()
                    bitmap.copyPixelsFromBuffer(plane.buffer)
                    val cropped=if(paddedWidth != width) Bitmap.createBitmap(bitmap,0,0,width,height) else bitmap
                    val out=ByteArrayOutputStream(110000)
                    cropped.compress(Bitmap.CompressFormat.JPEG,53,out)
                    val dataBytes=out.toByteArray()
                    cropped.recycle()
                    if(cropped !== bitmap) bitmap.recycle()
                    if(dataBytes.size in 10..(900*1024)) frames.trySend(Frame(dataBytes,width,height))
                } catch (error:Exception) {
                    Log.w("DuoPadPhoneMirror","Frame capture: ${error.message}")
                } finally { image.close() }
            },handler)
            display=projection!!.createVirtualDisplay("DuoPad Phone to PC",
                width,height,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface,null,handler)
            scope.launch { streamToPc(host,port,pin) }
        } catch(error:Exception) {
            Log.e("DuoPadPhoneMirror","Capture failed",error)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun writePacket(out:DataOutputStream, type:Int, bytes:ByteArray) {
        require(bytes.size <= 65535)
        out.writeByte(type)
        out.writeByte(bytes.size and 0xff)
        out.writeByte((bytes.size shr 8) and 0xff)
        out.write(bytes)
        out.flush()
    }

    private fun readPacket(input:DataInputStream, type:Int):ByteArray {
        val got=input.readUnsignedByte()
        val size=input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
        if(got != type || size > 4096) throw IOException("Unexpected PC Host response")
        return ByteArray(size).also { input.readFully(it) }
    }

    private suspend fun streamToPc(host:String,port:Int,pin:String) = withContext(Dispatchers.IO) {
        try {
            val sock=Socket()
            connection=sock
            sock.soTimeout=6000
            sock.connect(InetSocketAddress(host,port),4000)
            val input=DataInputStream(sock.getInputStream())
            val output=DataOutputStream(sock.getOutputStream())
            writePacket(output,0x20,byteArrayOf())
            val identity=String(readPacket(input,0x20),Charsets.UTF_8)
            if(!identity.startsWith("DOUPAD/") && !identity.startsWith("UNIPOINT/"))
                throw IOException("DuoPad Host is required")
            if(identity.contains("AUTH=1")) {
                if(pin.isBlank()) throw IOException("PC Host PIN is required")
                writePacket(output,0x10,pin.toByteArray(Charsets.UTF_8))
                if(String(readPacket(input,0x10),Charsets.UTF_8) != "OK")
                    throw IOException("PC Host pairing PIN rejected")
            }
            for(frame in frames) {
                if(stopping.get()) break
                val header=JSONObject().put("size",frame.jpeg.size)
                    .put("width",frame.width).put("height",frame.height)
                    .put("name",android.os.Build.MODEL).toString().toByteArray(Charsets.UTF_8)
                writePacket(output,0x50,header)
                output.write(frame.jpeg)
                output.flush()
                val response=JSONObject(String(readPacket(input,0x50),Charsets.UTF_8))
                if(!response.optBoolean("ok")) throw IOException(response.optString("error","Mirror rejected"))
            }
        } catch (error:Exception) {
            Log.e("DuoPadPhoneMirror","Phone to PC stream ended",error)
        } finally { stopSelf() }
    }

    override fun onDestroy() {
        if(stopping.compareAndSet(false,true)) {
            frames.close()
            try { connection?.close() } catch (_:Exception) {}
            try { display?.release() } catch (_:Exception) {}
            try { reader?.close() } catch (_:Exception) {}
            try { projection?.stop() } catch (_:Exception) {}
            try { captureThread?.quitSafely() } catch (_:Exception) {}
            scope.cancel()
        }
        super.onDestroy()
    }
}
