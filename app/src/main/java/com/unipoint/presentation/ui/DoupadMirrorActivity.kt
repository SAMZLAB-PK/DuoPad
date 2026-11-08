package com.unipoint.presentation.ui

import android.app.Activity
import android.app.AlertDialog
import com.unipoint.core.util.MirrorCrashRecorder
import com.unipoint.core.util.VisibleVideoMonitor
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.view.PixelCopy
import android.content.ContentValues
import android.provider.MediaStore
import android.content.Intent
import android.speech.RecognizerIntent
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.Toast
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaCodecList
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.unipoint.core.util.MirrorCoordinateMapper
import com.unipoint.data.remote.ScrcpyControlProtocol
import dadb.AdbKeyPair
import dadb.AdbStream
import dadb.Dadb
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
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
    // Stale ADB socket readers can remain blocked after Surface rotation.
    // A fixed-size executor silently queued a new session behind these readers
    // (SURFACE_CREATED without any subsequent STREAM_MODE in the user's log).
    // The cached pool starts each new Surface promptly; generation and Session.close
    // still prevent stale workers from updating the current video surface.
    private val worker = Executors.newCachedThreadPool { command ->
        Thread(command, "doupad-mirror-stream").apply { isDaemon = true }
    }
    private val inputWorker = Executors.newSingleThreadExecutor()
    @Volatile private var session: Session? = null
    @Volatile private var stage: String = "Not started"
    private var lastFitSignature = ""
    private val surfaceStartHandler = Handler(Looper.getMainLooper())
    @Volatile private var muted = false
    // Manual selection avoids confusing legitimate static/DRM black frames with
    // encoder failure. This setting only affects the opt-in TV Box mode.
    private var aceconnEncoderIndex=0
    @Volatile private var snapshotMode=false
    @Volatile private var snapshotAdb: Dadb?=null
    private var snapshotPreview: ImageView?=null
    private var lastVideoSampleHash:Int?=null
    // Set by the visibility watchdog when it decides the current profile is black.
    // Read by stream() so the next profile starts even if the old socket read is stuck.
    @Volatile private var pendingBlackRetry:String?=null
    // Never attach two MediaCodec instances to one SurfaceView concurrently.
    // Manual encoder switching must wait for the previous decoder to be released.
    private val activeVideoAttempt = AtomicReference<Thread?>(null)
    private val manualSwitchInProgress = AtomicBoolean(false)
    @Volatile private var lastVisibleVideoAtMs:Long=0L
    private var topControls:LinearLayout?=null
    private var leftControls:LinearLayout?=null
    private var rightControls:LinearLayout?=null
    private var bottomControls:LinearLayout?=null

    private fun checkpoint(ticket: Int, name: String) {
        stage = name
        Log.i("DuoPadMirror", "session=$ticket stage=$name")
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
        Log.e("DuoPadMirror", message, cause)
        runCatching {
            val file = File(filesDir, "doupad_mirror_diagnostics.txt")
            file.appendText("${System.currentTimeMillis()} session=$ticket ERROR $message\n")
            if (file.length() > 65_536L) file.writeText(file.readText().takeLast(32_768))
        }
        show(ticket, "Live mirror error: $message. Long-press this message to copy diagnostics.")
    }

    private class Session(val adb: Dadb) {
        @Volatile var closed = false
        private val closeOnce = AtomicBoolean(false)
        val codecLock = Any()
        var server: AdbStream? = null
        var video: AdbStream? = null
        var audio: AdbStream? = null
        var audioTrack: AudioTrack? = null
        var control: AdbStream? = null
        @Volatile var codec: MediaCodec? = null
        var displayWidth = 0
        var displayHeight = 0
        val visibilityMonitor = VisibleVideoMonitor()
        @Volatile var retryReason: String? = null

        // ADB close can block on older Dadb versions. Do not let that
        // block MediaCodec teardown or another mirror profile.
        fun close() {
            if (!closeOnce.compareAndSet(false, true)) return
            closed = true
            listOf(control, video, audio, server).forEachIndexed { index, stream ->
                if (stream != null) thread(name="doupad-adb-close-$index", isDaemon=true) {
                    runCatching { stream.close() }
                }
            }
            thread(name="doupad-adb-connection-close", isDaemon=true) {
                runCatching { adb.close() }
            }
            val player = audioTrack
            audioTrack = null
            if (player != null) thread(name="doupad-audio-release", isDaemon=true) {
                runCatching { player.pause() }
                runCatching { player.flush() }
                runCatching { player.release() }
            }
        }

        // Serialize all codec operations. A stale socket reader can no longer
        // submit input after manual teardown releases the Surface.
        fun releaseDecoder(): Boolean = synchronized(codecLock) {
            val decoder = codec ?: return@synchronized true
            codec = null
            runCatching { decoder.stop() }
            runCatching { decoder.release() }.isSuccess
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the phone screen awake for the entire foreground mirror session.
        // This does not require WAKE_LOCK permission or modify global display timeout.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Never force orientation during startup: some Android 15/16 device
        // configurations reject an orientation request before the first draw.
        MirrorCrashRecorder.started(this)
        // Do not read WindowInsetsController yet: DecorView is not attached.
        // Android 15/16 may throw NPE from PhoneWindow.getInsetsController().
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
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DuoPad Mirror diagnostics",diagnostics))
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
        MirrorCrashRecorder.note(this, "ACTIVITY_CONTENT_READY: content view installed")
        surface.holder.addCallback(this)
        createControls()
        root.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ ->
            session?.let { fitSurface(it.displayWidth,it.displayHeight) }
        }
        // Defer fullscreen flags until the DecorView is attached to a window.
        root.post {
            if (!isFinishing && !isDestroyed) enterImmersive()
        }
    }

    private fun enterImmersive() {
        // Fullscreen is cosmetic: NEVER let a vendor WindowInsets bug crash video.
        try {
            val decor = window.decorView
            if (!decor.isAttachedToWindow) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // The decor's own controller is safe only after attachment.
                val controller = decor.windowInsetsController ?: return
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                decor.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        } catch (error: RuntimeException) {
            MirrorCrashRecorder.note(this,
                "IMMERSIVE_OPTIONAL_FAILURE: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        MirrorCrashRecorder.note(this,
            "WINDOW_FOCUS: hasFocus=$hasFocus; generation=${generation.get()}; surfaceValid=${surface.holder.surface.isValid}")
        if (hasFocus) enterImmersive()
    }

    override fun onPause() {
        MirrorCrashRecorder.note(this,
            "ACTIVITY_PAUSE: generation=${generation.get()}; sessionOpen=${session?.closed == false}")
        // Restore normal phone timeout while the mirror Activity is backgrounded.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // Reapply after returning from notification shade, permission prompt, etc.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::surface.isInitialized) MirrorCrashRecorder.note(this,
            "ACTIVITY_RESUME: generation=${generation.get()}; surfaceValid=${surface.holder.surface.isValid}")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        MirrorCrashRecorder.note(this,
            "CONFIG_CHANGED: orientation=${newConfig.orientation}, surfaceAlive=${session?.closed==false}")
        enterImmersive()
        root.post {
            createControls()
            session?.let { fitSurface(it.displayWidth,it.displayHeight) }
        }
    }

    private fun show(ticket: Int, message: String) = runOnUiThread {
        if (generation.get() == ticket) {
            status.visibility = View.VISIBLE
            status.text = message
        }
    }

    private fun fitSurface(videoWidth: Int,videoHeight: Int) {
        val w=root.width;val h=root.height
        if(w<=0||h<=0||videoWidth<=0||videoHeight<=0)return
        // Do not resize the SurfaceHolder (would kill MediaCodec session).
        // Scale the already-attached SurfaceView instead, preserving aspect.
        val scale=minOf(w.toFloat()/videoWidth,h.toFloat()/videoHeight)
        surface.scaleX=(videoWidth*scale)/w
        surface.scaleY=(videoHeight*scale)/h
        val signature="${w}x${h} -> ${videoWidth}x${videoHeight}"
        if(lastFitSignature!=signature){
            lastFitSignature=signature
            MirrorCrashRecorder.note(this,
                "VIDEO_FIT: $signature; scaleX=${surface.scaleX}; scaleY=${surface.scaleY}")
        }
    }

    private fun dp(value:Int):Int=(value*resources.displayMetrics.density+0.5f).toInt()
    private fun remoteButton(label:String,action:()->Unit):TextView {
        val tile=GradientDrawable().apply{
            setColor(0xDD1F2531.toInt());cornerRadius=dp(13).toFloat()
        }
        return TextView(this).apply{
            text=label;textSize=20f;setTextColor(Color.WHITE)
            gravity=Gravity.CENTER;background=tile
            isClickable=true;contentDescription=label
            setOnClickListener{action()}
        }
    }
    private fun sendKey(code:Int){
        val current=session ?: return
        if(current.closed)return
        inputWorker.execute{
            if(current.closed)return@execute
            try{
                val control=current.control
                if(control!=null){
                    control.sink.write(ScrcpyControlProtocol.key(0,code))
                    control.sink.write(ScrcpyControlProtocol.key(1,code))
                    control.sink.flush()
                }else current.adb.shell("input keyevent $code")
            }catch(error:Exception){
                MirrorCrashRecorder.note(this,
                    "MIRROR_REMOTE_ERROR: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }
    /** Keep observing visual output after the initial 7 seconds (R5 stopped too early). */
    private fun scheduleVisibleVideoSample(ticket: Int, current: Session, delayMs: Long) {
        surfaceStartHandler.postDelayed({
            if (generation.get() == ticket && session === current && !current.closed &&
                current.retryReason == null && surface.holder.surface.isValid) {
                sampleVisibleVideo(ticket, current)
            }
        }, delayMs)
    }

    private fun sampleVisibleVideo(ticket:Int,current:Session){
        if (generation.get()!=ticket || session !== current || current.closed) return
        if(surface.width<=0||surface.height<=0||!surface.holder.surface.isValid)return
        val image=Bitmap.createBitmap(48,27,Bitmap.Config.ARGB_8888)
        try{
            PixelCopy.request(surface,image,{result->
                try {
                    if (generation.get()!=ticket || session !== current || current.closed) return@request
                    if(result==PixelCopy.SUCCESS){
                        var dark=0
                        var sampled=0
                        var fingerprint=1
                        for(y in 0 until image.height step 2){
                            for(x in 0 until image.width step 2){
                                val rgb=image.getPixel(x,y)
                                val peak=maxOf(Color.red(rgb),Color.green(rgb),Color.blue(rgb))
                                if(peak<18)dark++
                                sampled++
                                fingerprint=31*fingerprint+rgb
                            }
                        }
                        val ratio=if(sampled>0) dark*100/sampled else 0
                        val unchanged=lastVideoSampleHash==fingerprint
                        lastVideoSampleHash=fingerprint
                        val observation=current.visibilityMonitor.observe(
                            ratio, fingerprint, android.os.SystemClock.elapsedRealtime())
                        MirrorCrashRecorder.note(this,
                            "VISIBLE_VIDEO_SAMPLE: blackPixels=$ratio%, pixelCopy=SUCCESS, " +
                                "frameHash=${fingerprint.toUInt().toString(16)}, " +
                                "sameAsPrevious=$unchanged, " +
                                "blackSamples=${observation.blackSamples}, " +
                                "blackDurationMs=${observation.blackDurationMs}, " +
                                "stableBlackSamples=${observation.stableBlackSamples}, ticket=$ticket " +
                                "(static scenes/DRM may also appear black)")
                        if (observation.firstVisible || observation.recovered) {
                            MirrorCrashRecorder.note(this,
                                "VISIBLE_VIDEO_CONFIRMED: blackPixels=$ratio%, " +
                                    "first=${observation.firstVisible}, recovered=${observation.recovered}")
                        }
                        if (observation.visible) {
                            lastVisibleVideoAtMs=android.os.SystemClock.elapsedRealtime()
                            // Do not declare a good picture merely because MediaCodec
                            // produced output. Hide the status only after PixelCopy sees it.
                            status.visibility=View.GONE
                        } else if (observation.blackSamples >= 2) {
                            status.visibility=View.VISIBLE
                            status.text=if(intent.getBooleanExtra("aceconn_mode",false))
                                "TV video is black; checking for recovery before changing encoder…"
                            else "Decoder is active but video appears black. Try TV Box mode or TV Home."
                        }
                        if (intent.getBooleanExtra("aceconn_mode",false) &&
                            observation.shouldRetry && current.retryReason==null) {
                            val reason="BLACK_OUTPUT: persistent near-black PixelCopy " +
                                "samples=${observation.blackSamples}, " +
                                "durationMs=${observation.blackDurationMs}"
                            current.retryReason=reason
                            pendingBlackRetry=reason
                            MirrorCrashRecorder.note(this,
                                "AUTO_PROFILE_RETRY: $reason; switching compatibility profile " +
                                    "(DRM/protected playback cannot be bypassed)")
                            status.visibility=View.VISIBLE
                            status.text="Video remains black; trying the next compatibility profile…"
                            thread(name="doupad-black-output-retry",isDaemon=true) {
                                current.close()
                            }
                        }
                    } else {
                        MirrorCrashRecorder.note(this,
                            "VISIBLE_VIDEO_SAMPLE: PixelCopy result=$result ticket=$ticket")
                    }
                } finally {
                    image.recycle()
                    if (generation.get()==ticket && session===current && !current.closed &&
                        current.retryReason==null) scheduleVisibleVideoSample(ticket,current,2500L)
                }
            },Handler(Looper.getMainLooper()))
        }catch(error:Exception){
            image.recycle()
            MirrorCrashRecorder.note(this,"VISIBLE_VIDEO_SAMPLE_ERROR: ${error.message}")
            if (generation.get()==ticket && session===current && !current.closed)
                scheduleVisibleVideoSample(ticket,current,3000L)
        }
    }

    private fun saveScreenshot(){
        if(surface.width<=0||surface.height<=0)return
        val bitmap=Bitmap.createBitmap(surface.width,surface.height,Bitmap.Config.ARGB_8888)
        PixelCopy.request(surface,bitmap,{result->
            if(result==PixelCopy.SUCCESS){
                try{
                    val details=ContentValues().apply{
                        put(MediaStore.Images.Media.DISPLAY_NAME,"DuoPad-${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                        if(Build.VERSION.SDK_INT>=29)
                            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/DuoPad")
                    }
                    val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,details)
                    if(uri!=null){
                        contentResolver.openOutputStream(uri)?.use{
                            bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                        }
                        Toast.makeText(this,"Screenshot saved to Pictures/DuoPad",Toast.LENGTH_SHORT).show()
                    }
                }catch(e:Exception){
                    MirrorCrashRecorder.note(this,"SCREENSHOT_ERROR: ${e.message}")
                }
            }else Toast.makeText(this,"Screenshot unavailable",Toast.LENGTH_SHORT).show()
            bitmap.recycle()
        },Handler(Looper.getMainLooper()))
    }
    private fun startVoice(){
        runCatching{
            startActivityForResult(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),601)
        }.onFailure{
            Toast.makeText(this,"Voice recognition is unavailable",Toast.LENGTH_SHORT).show()
        }
    }
    @Deprecated("Android speech Activity result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=601||resultCode!=RESULT_OK)return
        val spoken=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull() ?:return
        val current=session ?: return
        inputWorker.execute{
            if(current.closed)return@execute
            runCatching{
                val ctl=current.control
                if(ctl!=null){
                    ctl.sink.write(ScrcpyControlProtocol.text(spoken))
                    ctl.sink.flush()
                }else{
                    // Android shell's input text accepts escaped spaces.
                    val safe=spoken.replace(Regex("[^a-zA-Z0-9 ._-]"),"")
                        .replace(" ","%s")
                    current.adb.shell("input text $safe")
                }
            }.onFailure{MirrorCrashRecorder.note(this,"VOICE_SEND_ERROR: ${it.message}")}
        }
    }
    private fun nextTvBoxEncoder(){
        if(!intent.getBooleanExtra("aceconn_mode",false))return
        if(manualSwitchInProgress.get()) {
            MirrorCrashRecorder.note(this,"MANUAL_SWITCH_IGNORED: switch already underway")
            return
        }
        // The Aceconn already shows software-decoded VLC video correctly. A
        // manual switch during visible playback is optional and potentially
        // disruptive, so require confirmation before closing a good stream.
        val recentVisible=android.os.SystemClock.elapsedRealtime()-lastVisibleVideoAtMs < 5_000L
        if(recentVisible){
            AlertDialog.Builder(this)
                .setTitle("Video is visible")
                .setMessage("The current Aceconn mirror is showing picture. Switching the encoder may interrupt playback. Switch anyway?")
                .setNegativeButton("Keep video",null)
                .setPositiveButton("Switch"){_,_->switchTvBoxEncoderSafely()}
                .show()
        }else switchTvBoxEncoderSafely()
    }

    private fun switchTvBoxEncoderSafely(){
        if (!manualSwitchInProgress.compareAndSet(false,true)) {
            MirrorCrashRecorder.note(this,"MANUAL_SWITCH_IGNORED: cleanup already in progress")
            status.visibility=View.VISIBLE
            status.text="Encoder switch already in progress."
            return
        }
        val names=listOf("system-default","c2.android.avc.encoder","OMX.google.h264.encoder","software-decoder")
        val nextIndex=(aceconnEncoderIndex+1)%names.size
        val ticket=generation.incrementAndGet()
        MirrorCrashRecorder.note(this,
            "MANUAL_ENCODER_SWITCH: selected=${names[nextIndex]}, ticket=$ticket")
        lastVideoSampleHash=null
        lastVisibleVideoAtMs=0L
        pendingBlackRetry=null
        status.visibility=View.VISIBLE
        status.text="Closing the previous decoder…"
        val previous=session
        thread(name="doupad-manual-switch-cleanup",isDaemon=true){
            try {
                previous?.close()
                MirrorCrashRecorder.note(this,"MANUAL_SWITCH_CLOSE_REQUESTED: ticket=$ticket")
                val released=previous?.releaseDecoder() ?: true
                if(!released){
                    MirrorCrashRecorder.note(this,"MANUAL_SWITCH_RELEASE_FAILED: old decoder release failed")
                    show(ticket,"Old decoder could not be released. Close and reopen Mirror.")
                    return@thread
                }
                MirrorCrashRecorder.note(this,"MANUAL_SWITCH_DECODER_RELEASED: ticket=$ticket")
                val oldAttempt=activeVideoAttempt.get()
                if(oldAttempt!=null && oldAttempt!==Thread.currentThread() && oldAttempt.isAlive){
                    oldAttempt.join(350L)
                    if(oldAttempt.isAlive) MirrorCrashRecorder.note(this,
                        "MANUAL_SWITCH_NETWORK_READER_STALE: decoder released; background ADB read still closing")
                }
                if(generation.get()!=ticket || isFinishing || isDestroyed || !surface.holder.surface.isValid){
                    MirrorCrashRecorder.note(this,"MANUAL_ENCODER_SWITCH_SKIPPED: stale surface ticket=$ticket")
                    return@thread
                }
                aceconnEncoderIndex=nextIndex
                MirrorCrashRecorder.note(this,"MANUAL_SWITCH_CLEANUP_OK: prior decoder released")
                runCatching{worker.execute{
                    MirrorCrashRecorder.note(this,"WORKER_STARTED: manual encoder switch, ticket=$ticket")
                    stream(surface.holder,ticket)
                }}.onFailure{
                    MirrorCrashRecorder.note(this,"MANUAL_ENCODER_SWITCH_ERROR: ${it.javaClass.simpleName}: ${it.message}")
                    show(ticket,"Cannot switch encoder: ${it.message}")
                }
            } catch (error: Exception) {
                MirrorCrashRecorder.note(this,"MANUAL_SWITCH_ERROR: ${error.javaClass.simpleName}: ${error.message}")
                show(ticket,"Encoder switch failed: ${error.message}")
            } finally {
                manualSwitchInProgress.set(false)
            }
        }
    }


    // Explicit alternative for unusual TV firmwares that return only black H.264
    // packets. This is a low-rate screenshot preview (NOT a live video stream).
    // Protected/DRM content remains protected; never attempt to bypass it.
    private fun toggleSnapshotFallback() {
        if (snapshotMode) {
            snapshotMode=false
            val ticket=generation.incrementAndGet()
            snapshotAdb?.let { adb -> thread(isDaemon=true){runCatching{adb.close()}} }
            snapshotAdb=null
            snapshotPreview?.let { root.removeView(it) }
            snapshotPreview=null
            MirrorCrashRecorder.note(this,"SNAPSHOT_MODE_STOP: returning to live H.264")
            createControls()
            status.visibility=View.VISIBLE
            status.text="Returning to live video…"
            surfaceStartHandler.postDelayed({
                if(generation.get()==ticket && surface.holder.surface.isValid && !isDestroyed){
                    worker.execute{stream(surface.holder,ticket)}
                }
            },350L)
            return
        }
        snapshotMode=true
        val ticket=generation.incrementAndGet()
        session?.close()
        session=null
        MirrorCrashRecorder.note(this,"SNAPSHOT_MODE_START: ADB screencap preview, up to 1 fps")
        val overlay=ImageView(this).apply{
            scaleType=ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        snapshotPreview=overlay
        root.addView(overlay,1,FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT))
        createControls()
        status.visibility=View.VISIBLE
        status.text="Screenshot compatibility preview · slower than live video"
        worker.execute {
            var adb: Dadb?=null
            try {
                val endpoint=intent.getStringExtra("endpoint") ?: error("No TV endpoint")
                val keyDir=File(filesDir,"adb")
                val keys=AdbKeyPair.read(File(keyDir,"adbkey"),File(keyDir,"adbkey.pub"))
                adb=Dadb.create(endpoint.substringBefore(':'),
                    endpoint.substringAfter(':',"5555").toInt(), keys,
                    connectTimeout=5000,socketTimeout=7000)
                snapshotAdb=adb
                var pictures=0
                while(snapshotMode && generation.get()==ticket && !isDestroyed){
                    val stream=adb!!.open("shell:screencap -p")
                    val bytes=try{stream.source.readByteArray()}finally{runCatching{stream.close()}}
                    val sig=byteArrayOf(0x89.toByte(),0x50,0x4e,0x47)
                    if(bytes.size>100 && bytes.copyOfRange(0,4).contentEquals(sig)){
                        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                        if(bitmap!=null){
                            pictures++
                            val number=pictures
                            runOnUiThread{
                                if(snapshotMode && generation.get()==ticket){
                                    snapshotPreview?.setImageBitmap(bitmap)
                                    status.visibility=View.GONE
                                    if(number==1 || number%10==0){
                                        MirrorCrashRecorder.note(this,
                                            "SNAPSHOT_FRAME: valid PNG, ${bitmap.width}x${bitmap.height}, frame=$number")
                                    }
                                }else bitmap.recycle()
                            }
                        }
                    }else{
                        MirrorCrashRecorder.note(this,"SNAPSHOT_INVALID: screencap returned ${bytes.size} bytes")
                        show(ticket,"TV screenshot not available; switch back to LIVE")
                    }
                    Thread.sleep(1200)
                }
            }catch(e:Exception){
                MirrorCrashRecorder.note(this,"SNAPSHOT_ERROR: ${e.javaClass.simpleName}: ${e.message}")
                show(ticket,"Screenshot preview unavailable: ${e.message}")
            }finally{
                runCatching{adb?.close()}
                if(snapshotAdb===adb)snapshotAdb=null
            }
        }
    }

    private fun createControls(){
        if(!::root.isInitialized)return
        listOfNotNull(topControls,leftControls,rightControls,bottomControls).forEach{
            root.removeView(it)
        }
        fun bar(vertical:Boolean):LinearLayout=LinearLayout(this).apply{
            orientation=if(vertical)LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
        }
        fun add(bar:LinearLayout,text:String,code:Int){
            val button=remoteButton(text){sendKey(code)}
            val dims=if(bar.orientation==LinearLayout.VERTICAL)
                LinearLayout.LayoutParams(dp(48),dp(48))
            else LinearLayout.LayoutParams(0,dp(43),1f)
            dims.setMargins(dp(3),dp(3),dp(3),dp(3))
            bar.addView(button,dims)
        }
        val header=bar(false)
        val back=remoteButton("‹"){finish()}
        val audio=remoteButton(if(muted)"🔇" else "♪"){
            muted=!muted
            session?.audioTrack?.setVolume(if(muted)0f else 1f)
            createControls()
        }
        val rotate=remoteButton("⟳"){
            requestedOrientation=
                if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        val shot=remoteButton("▣"){saveScreenshot()}
        val close=remoteButton("×"){finish()}
        val headerButtons=mutableListOf(back,audio,rotate,shot)
        if(intent.getBooleanExtra("aceconn_mode",false)){
            headerButtons.add(remoteButton(if(snapshotMode)"LIVE" else "SHOT"){
                toggleSnapshotFallback()
            }.apply{
                textSize=12f
                contentDescription="Toggle slow screenshot compatibility preview"
            })
            headerButtons.add(remoteButton("ENC"){nextTvBoxEncoder()}.apply{
                textSize=13f
                contentDescription="Try next Aceconn video encoder"
            })
        }
        headerButtons.add(close)
        headerButtons.forEach{button->
            val params=LinearLayout.LayoutParams(0,dp(39),1f)
            params.setMargins(dp(2),0,dp(2),0)
            header.addView(button,params)
        }
        topControls=header
        root.addView(header,FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,dp(43),Gravity.TOP).apply{
                leftMargin=dp(10);rightMargin=dp(10);topMargin=dp(8)
            })
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        if(landscape){
            val left=bar(true)
            listOf("↑" to 19,"←" to 21,"OK" to 23,"→" to 22,"↓" to 20)
                .forEach{(text,code)->add(left,text,code)}
            root.addView(left,FrameLayout.LayoutParams(
                dp(57),FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.START or Gravity.CENTER_VERTICAL))
            leftControls=left
            val right=bar(true)
            listOf("▣" to 82,"◁" to 4,"⌂" to 3,"🎤" to -1,"↩" to 4)
                .forEach{(label,code)->
                    if(code<0){
                        val b=remoteButton(label){startVoice()}
                        right.addView(b,LinearLayout.LayoutParams(dp(48),dp(48)).apply{
                            setMargins(dp(3),dp(3),dp(3),dp(3))
                        })
                    }else add(right,label,code)
                }
            root.addView(right,FrameLayout.LayoutParams(
                dp(57),FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.END or Gravity.CENTER_VERTICAL))
            rightControls=right
        } else {
            val bottom=bar(true)
            val arrows=bar(false)
            listOf("↑" to 19,"←" to 21,"OK" to 23,"→" to 22,"↓" to 20)
                .forEach{(label,code)->add(arrows,label,code)}
            val actions=bar(false)
            listOf("‹" to 4,"⌂" to 3,"☰" to 82,"Vol +" to 24,"Vol -" to 25)
                .forEach{(label,code)->add(actions,label,code)}
            bottom.addView(arrows,LinearLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,dp(51)))
            bottom.addView(actions,LinearLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,dp(51)))
            root.addView(bottom,FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,dp(110),Gravity.BOTTOM).apply{
                leftMargin=dp(7);rightMargin=dp(7);bottomMargin=dp(12)
            })
            bottomControls=bottom
        }
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
        lastVideoSampleHash=null
        // A new Surface must never share the old MediaCodec's socket/session.
        session?.let { if(!it.closed) it.close() }
        MirrorCrashRecorder.note(this,"SURFACE_CREATED: ticket=$ticket; delayed for surface stabilization")
        if(ticket>1)MirrorCrashRecorder.note(this,
            "SURFACE_RESTART: ticket=$ticket; old session invalidated")
        surfaceStartHandler.postDelayed({
            if(generation.get()!=ticket || isFinishing || isDestroyed || !holder.surface.isValid){
                MirrorCrashRecorder.note(this,"SURFACE_START_SKIPPED: stale ticket=$ticket")
            }else{
                MirrorCrashRecorder.note(this,
                    "SURFACE_START_DISPATCHED: ticket=$ticket, surfaceValid=${holder.surface.isValid}")
                runCatching {
                    worker.execute {
                        MirrorCrashRecorder.note(this,
                            "WORKER_STARTED: surface ticket=$ticket")
                        stream(holder,ticket)
                    }
                }.onFailure {
                        MirrorCrashRecorder.note(this,
                            "WORKER_START_FAILED: ${it.javaClass.simpleName}: ${it.message}")
                        show(ticket,"Mirror surface unavailable: ${it.message}")
                    }
            }
        },350L)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MirrorCrashRecorder.note(this,
            "SURFACE_CHANGED: generation=${generation.get()}, size=${width}x${height}, format=$format")
        // Leave SurfaceView unchanged: resizing while the codec is attached
        // can destroy the Surface and abort the scrcpy session.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val next=generation.incrementAndGet()
        MirrorCrashRecorder.note(this,
            "SURFACE_DESTROYED: generation=$next, closing active session")
        session?.close()
    }

    override fun onDestroy() {
        snapshotMode=false
        snapshotAdb?.let { adb -> thread(isDaemon=true){runCatching{adb.close()}} }
        snapshotAdb=null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        generation.incrementAndGet()
        session?.close()
        surfaceStartHandler.removeCallbacksAndMessages(null)
        inputWorker.shutdownNow()
        worker.shutdownNow()
        MirrorCrashRecorder.finish(this, "Mirror Activity onDestroy")
        super.onDestroy()
    }

    private data class VideoProfile(
        val maxSize: Int, val fps: Int, val bitRate: Int, val control: Boolean, val audio: Boolean,
        val encoder: String? = null, val softwareDecoder: Boolean = false
    )

    private fun stream(holder: SurfaceHolder, ticket: Int) {
        // Second preset deliberately disables scrcpy control socket. Some Android TV
        // firmware streams video reliably but rejects the second abstract socket.
        val aceconnMode=intent.getBooleanExtra("aceconn_mode",false)
        val smoothMode=intent.getBooleanExtra("smooth_tv_mode",false)
        val withAudio=intent.getBooleanExtra("tv_audio_mode",false)
        val profiles=if(aceconnMode) listOf(
            // Vendor encoder can produce blank frames on some Aceconn boxes.
            // Try a much lighter video-only capture before alternate codecs.
            VideoProfile(640,12,750_000,true,false),
            VideoProfile(720,12,850_000,true,false,"c2.android.avc.encoder"),
            VideoProfile(640,10,650_000,true,false,"OMX.google.h264.encoder"),
            VideoProfile(640,10,600_000,true,false,null,true)
        ).let { all -> all.drop(aceconnEncoderIndex.coerceIn(0,all.lastIndex)) +
            all.take(aceconnEncoderIndex.coerceIn(0,all.lastIndex)) }
        else if(smoothMode) listOf(
            VideoProfile(640,12,750_000,true,false),
            VideoProfile(720,15,1_050_000,true,false)
        ) else if(withAudio) listOf(
            VideoProfile(960,24,2_000_000,true,true),
            VideoProfile(720,15,1_200_000,true,true)
        ) else listOf(
            // Default uses fewer TV encoder resources than old 960p/24fps.
            VideoProfile(720,15,1_000_000,true,false),
            VideoProfile(640,12,750_000,true,false)
        )
        MirrorCrashRecorder.note(this,
            "STREAM_MODE: ${if(aceconnMode)"TV_BOX_COMPATIBILITY" else if(smoothMode)"SMOOTH_VIDEO_ONLY" else if(withAudio)"AUDIO_HIGH_QUALITY" else "STANDARD_LOW_LOAD"}; " +
                "profileCount=${profiles.size}; selectedIndex=${if(aceconnMode)aceconnEncoderIndex else 0}; " +
                "blackOutputRecovery=${if(aceconnMode) "sustained-black-auto-retry" else "off"}")
        for ((index, profile) in profiles.withIndex()) {
            if (generation.get() != ticket) return
            checkpoint(ticket,
                "Preparing H.264 stream ${index+1}/${profiles.size}: " +
                    "${profile.maxSize}p/${profile.fps}fps control=${profile.control} " +
                    "encoder=${profile.encoder ?: "system-default"}")
            pendingBlackRetry = null
            val attemptError = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
            val attemptDone = java.util.concurrent.CountDownLatch(1)
            // Each profile runs on its own thread. A black-output retry closes the session,
            // but a socket read that is blocked may not return promptly. The fallback must
            // not depend on that read unblocking, otherwise profile 2+ never starts
            // (R8 log: AUTO_PROFILE_RETRY followed by no "Preparing ... 2/4").
            val attemptThread=thread(name = "doupad-video-attempt-${index + 1}", isDaemon = true, start = false) {
                try {
                    streamAttempt(holder, ticket, profile)
                } catch (t: Throwable) {
                    attemptError.set(t)
                } finally {
                    activeVideoAttempt.compareAndSet(Thread.currentThread(),null)
                    attemptDone.countDown()
                }
            }
            activeVideoAttempt.set(attemptThread)
            attemptThread.start()
            var abandoned = false
            while (generation.get() == ticket && attemptDone.count > 0L) {
                if (pendingBlackRetry != null) {
                    // Give the attempt a short window to unwind, then move on regardless.
                    abandoned = !attemptDone.await(3, java.util.concurrent.TimeUnit.SECONDS)
                    break
                }
                attemptDone.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            if (generation.get() != ticket) return
            if(abandoned){
                // Do not start another MediaCodec while a previous decoder
                // still holds the same Surface. Prefer a safe failure to a
                // crash or a chain of bogus profile errors.
                MirrorCrashRecorder.note(this,"PROFILE_RETRY_DEFERRED: previous decoder still active")
                show(ticket,"Old decoder has not released the surface. Close and reopen Mirror.")
                return
            }
            val blackRetry = pendingBlackRetry
            val error: Throwable? = attemptError.get()
            // Only a clean end WITHOUT a black-output retry means this profile succeeded.
            if (blackRetry == null && error == null && !abandoned) return
            val detail = when {
                blackRetry != null -> blackRetry
                abandoned -> "previous profile did not stop in time"
                else -> error?.message ?: error?.javaClass?.simpleName ?: "Unknown video error"
            }
            android.util.Log.w("DuoPadVideo", "Video profile ${index + 1} failed: $detail", error)
            MirrorCrashRecorder.note(this, "PROFILE_RESULT: ${index + 1}/${profiles.size}; $detail")
            if (blackRetry == null && error != null) failure(ticket, error)
            if (index == profiles.lastIndex) {
                show(ticket,
                    "All compatible profiles failed: $detail. If TV Home is still black, " +
                        "TV firmware or protected content may block screen capture."
                )
            } else {
                MirrorCrashRecorder.note(this, "PROFILE_NEXT: ${index + 2}/${profiles.size}")
                show(ticket, "Trying the next video profile…")
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
            val encoderOption=profile.encoder?.let { " video_encoder=$it" } ?: ""
            val command =
                "CLASSPATH=/data/local/tmp/doupad-mirror.jar app_process / com.genymobile.scrcpy.Server 3.3.1 " +
                    "scid=$id log_level=warn video=true control=${profile.control} audio=${profile.audio} audio_codec=raw tunnel_forward=true " +
                    "send_dummy_byte=false send_device_meta=false send_codec_meta=true send_frame_meta=true " +
                    "video_codec=h264 max_size=${profile.maxSize} max_fps=${profile.fps} " +
                    "video_bit_rate=${profile.bitRate}$encoderOption cleanup=true"
            checkpoint(ticket, "Starting device-side video encoder")
            current.server = adb.open("shell:$command")
            // ADB shell is its own stream. Draining the server's stdout/stderr
            // prevents ADB backpressure and records actual encoder failures.
            thread(name = "doupad-scrcpy-server-output", isDaemon = true) {
                try {
                    val serverSource = current.server?.source ?: return@thread
                    var observed = 0
                    while (!current.closed && generation.get() == ticket) {
                        val line = serverSource.readUtf8Line() ?: break
                        if (line.isNotBlank()) {
                            if (observed++ < 30 || line.contains("error", true) ||
                                line.contains("exception", true) || line.contains("failed", true)) {
                                MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                    "SERVER: " + line.take(380))
                            }
                        }
                    }
                    if (!current.closed && generation.get() == ticket) {
                        MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                            "SERVER_STREAM_ENDED: device-side scrcpy process closed its shell output")
                    }
                } catch (error: Exception) {
                    if (!current.closed && generation.get() == ticket) {
                        MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                            "SERVER_LOG_READ_ERROR: ${error.javaClass.simpleName}: ${error.message}")
                    }
                }
            }

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
            if (profile.audio) {
                checkpoint(ticket, "Opening device audio socket")
                // scrcpy socket order is VIDEO -> AUDIO -> CONTROL.
                for (attempt in 0 until 20) {
                    if (current.closed || generation.get()!=ticket) return
                    try {
                        current.audio = adb.open("localabstract:scrcpy_$id")
                        break
                    } catch (e: Exception) {
                        if (attempt == 19) throw IOException("Audio socket unavailable", e)
                        Thread.sleep(100)
                    }
                }
            }
            if (profile.control) {
                // scrcpy input channel is opened AFTER the audio socket.
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
            if (profile.audio) check(current.audio != null) { "TV audio socket unavailable" }
            checkpoint(ticket, "Reading scrcpy codec metadata")
            val codecId = source.readInt()
            val width = source.readInt()
            val height = source.readInt()
            require(codecId == 0x68323634 && width in 1..8192 && height in 1..8192) {
                "Unsupported video stream"
            }
            current.displayWidth = width
            current.displayHeight = height
            MirrorCrashRecorder.note(this,
                "STREAM_VIDEO_DIMENSIONS: ${width}x${height}, encoder=${profile.encoder ?: "default"}")
            runOnUiThread {
                if(generation.get()==ticket)
                    root.post { fitSurface(width,height) }
            }

            if (profile.audio) {
                val audioSource=current.audio?.source ?: error("Audio socket missing")
                thread(name="doupad-raw-audio",isDaemon=true){
                    try {
                        val codecId=audioSource.readInt()
                        if(codecId==0 || codecId==1){
                            MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                "AUDIO_UNAVAILABLE: device reported codec status $codecId")
                        } else if(codecId==0x00726177){
                            val outputFormat=AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(48000)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build()
                            val outputAttributes=AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                .build()
                            val buffer=AudioTrack.getMinBufferSize(48000,
                                AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT)
                                .coerceAtLeast(8192)
                            val audioTrack=AudioTrack.Builder()
                                .setAudioAttributes(outputAttributes)
                                .setAudioFormat(outputFormat)
                                .setBufferSizeInBytes(buffer*2)
                                .setTransferMode(AudioTrack.MODE_STREAM)
                                .build()
                            current.audioTrack=audioTrack
                            audioTrack.setVolume(if(muted)0f else 1f)
                            audioTrack.play()
                            MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                "AUDIO_READY: raw PCM stereo / 48 kHz; playState=${audioTrack.playState}")
                            var audioPackets=0
                            var audioBytes=0L
                            while(!current.closed && generation.get()==ticket){
                                val header=audioSource.readLong()
                                val packetSize=audioSource.readInt()
                                require(packetSize in 1..1_048_576){"Invalid audio packet size: $packetSize"}
                                val bytes=audioSource.readByteArray(packetSize.toLong())
                                if(header<0) continue
                                var sent=0
                                while(sent<bytes.size && !current.closed){
                                    val n=audioTrack.write(bytes,sent,bytes.size-sent,
                                        AudioTrack.WRITE_BLOCKING)
                                    if(n<=0) {
                                        MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                            "AUDIO_WRITE_FAILED: result=$n; packets=$audioPackets")
                                        break
                                    }
                                    sent+=n
                                }
                                if(sent>0){
                                    audioPackets++
                                    audioBytes+=sent
                                    if(audioPackets==1 || audioPackets%200==0){
                                        MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                            "AUDIO_PLAYBACK: packets=$audioPackets, bytes=$audioBytes, " +
                                                "muted=$muted, playState=${audioTrack.playState}")
                                    }
                                }
                            }
                        } else {
                            MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                "AUDIO_UNSUPPORTED_CODEC: $codecId; video will continue")
                        }
                    } catch(e:Exception){
                        if(!current.closed && generation.get()==ticket)
                            MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                "AUDIO_ERROR: ${e.javaClass.simpleName}: ${e.message}")
                    }
                }
            }

            runOnUiThread {
                if (generation.get() == ticket) {
                    enterImmersive()
                    // Never change requested orientation or SurfaceView size here.
                    // Doing so invalidates the surface during stream setup.
                }
            }

            val softwareRequested = profile.softwareDecoder || intent.getBooleanExtra("safe_decoder", false)
            checkpoint(ticket, "Initializing ${if (softwareRequested) "software-first" else "automatic"} H.264 decoder (${width}×${height})")
            val format = MediaFormat.createVideoFormat("video/avc", width, height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !softwareRequested) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            format.setInteger(MediaFormat.KEY_PRIORITY, 0)
            val softwareCodecName = if (softwareRequested) {
                runCatching {
                    MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                        .filter { !it.isEncoder && it.supportedTypes.any { type -> type.equals("video/avc", true) } }
                        .firstOrNull { info ->
                            info.name.contains("google", true) ||
                                info.name.contains("c2.android", true) ||
                                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.isSoftwareOnly)
                        }?.name
                }.getOrNull()
            } else null
            checkpoint(ticket, "Video decoder selected: ${softwareCodecName ?: "Android system default"}")
            val codec = if (softwareCodecName != null) {
                MediaCodec.createByCodecName(softwareCodecName)
            } else MediaCodec.createDecoderByType("video/avc")
            synchronized(current.codecLock) {
                if(current.closed || generation.get()!=ticket) {
                    runCatching { codec.release() }
                    return
                }
                current.codec = codec
                codec.configure(format, holder.surface, null, 0)
                codec.start()
            }
            val firstFrame = AtomicBoolean(false)
            val packetCount = AtomicInteger(0)
            val configCount = AtomicInteger(0)
            val queuedCount = AtomicInteger(0)
            val renderedCount = AtomicInteger(0)
            checkpoint(ticket, "Waiting for first decoded video frame")
            // A readable codec header is NOT proof that a single H.264 frame
            // was produced. Abort and try a second profile instead of hanging.
            thread(name = "doupad-first-frame-watchdog", isDaemon = true) {
                try {
                    Thread.sleep(12000)
                    if (!current.closed && generation.get() == ticket && !firstFrame.get()) {
                        val snapshot = "FIRST_FRAME_TIMEOUT after 12s: packets=${packetCount.get()}, " +
                            "config=${configCount.get()}, queued=${queuedCount.get()}, " +
                            "rendered=${renderedCount.get()}"
                        MirrorCrashRecorder.note(this@DoupadMirrorActivity, snapshot)
                        show(ticket, snapshot + " • switching compatibility profile")
                        current.close()
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }

            drain = thread(name = "doupad-video-output") {
                val info = MediaCodec.BufferInfo()
                try {
                    while (!current.closed && generation.get() == ticket) {
                        val index=synchronized(current.codecLock) {
                            if(current.closed || generation.get()!=ticket) null else {
                                val result=codec.dequeueOutputBuffer(info,10000)
                                if(result==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                                    val newFormat=codec.outputFormat
                                    MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                        "DECODER_FORMAT_CHANGED: $newFormat")
                                }
                                if(result>=0) codec.releaseOutputBuffer(result,true)
                                result
                            }
                        } ?: break
                        if (index >= 0) {
                            val outputFrames=renderedCount.incrementAndGet()
                            if(outputFrames<=3 || outputFrames%60==0){
                                MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                    "VIDEO_OUTPUT: rendered=$outputFrames, packets=${packetCount.get()}, " +
                                        "queued=${queuedCount.get()}, outputSize=${info.size}")
                            }
                            if (firstFrame.compareAndSet(false, true)) {
                                MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                    "FIRST_FRAME_RENDERED: packets=${packetCount.get()}, " +
                                        "queued=${queuedCount.get()}, output=${renderedCount.get()}")
                                runOnUiThread {
                                    if(generation.get()==ticket) {
                                        status.visibility=View.VISIBLE
                                        status.text="Decoder started; verifying visible video…"
                                        // Keep watching throughout the session. R5 sampled
                                        // only three times, missing later black transitions.
                                        MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                                            "VIDEO_VISIBILITY_WATCHDOG: continuous 2.5s samples; " +
                                                "graceful black-output retry in TV Box mode")
                                        scheduleVisibleVideoSample(ticket,current,1500L)
                                    }
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
                val packetNumber = packetCount.incrementAndGet()
                val configPacket = meta < 0
                val keyFrame = (meta and (1L shl 62)) != 0L
                if (configPacket) configCount.incrementAndGet()
                if (packetNumber <= 5 || packetNumber % 100 == 0) {
                    MirrorCrashRecorder.note(this@DoupadMirrorActivity,
                        "VIDEO_PACKET #$packetNumber size=$size config=$configPacket key=$keyFrame")
                }
                val bytes = source.readByteArray(size.toLong())
                if (current.closed || generation.get()!=ticket) break
                var index = -1
                while (index < 0 && !current.closed && generation.get() == ticket) {
                    synchronized(current.codecLock) {
                        if(!current.closed && generation.get()==ticket) index=codec.dequeueInputBuffer(10000)
                    }
                }
                if (index < 0 || current.closed || generation.get()!=ticket) break
                synchronized(current.codecLock) {
                    if(!current.closed && generation.get()==ticket) {
                        val input=codec.getInputBuffer(index) ?: error("Decoder buffer unavailable")
                        require(size<=input.capacity()) { "Video frame exceeds decoder buffer" }
                        input.clear()
                        input.put(bytes)
                        codec.queueInputBuffer(index,0,size,meta and 0x3fffffffffffffffL,
                            if(configPacket) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else
                            if(keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        queuedCount.incrementAndGet()
                    }
                }
            }
            if (generation.get() == ticket && current.retryReason != null) {
                throw IOException("${current.retryReason}")
            }
            if (generation.get() == ticket && !firstFrame.get()) {
                throw IOException("No decoded frame: packets=${packetCount.get()}, " +
                    "config=${configCount.get()}, queued=${queuedCount.get()}, " +
                    "rendered=${renderedCount.get()}")
            }
        } catch (e: Exception) {
            if (generation.get() == ticket) {
                val why=(own?.retryReason ?: e.message)?.takeIf{it.isNotBlank()}
                    ?: "${e.javaClass.simpleName} during $stage"
                MirrorCrashRecorder.note(this,"STREAM_EXCEPTION: ${e.javaClass.name}: $why; cause=${e.cause?.javaClass?.simpleName ?: "none"}")
                throw IOException("Video pipeline: $why", e)
            }
        } finally {
            own?.close()
            drain?.join(500L)
            val released=own?.releaseDecoder() ?: true
            MirrorCrashRecorder.note(this,"DECODER_CLEANUP: released=$released, drainAlive=${drain?.isAlive==true}, ticket=$ticket")
            if (session === own) session = null
        }
    }
}
