package com.unipoint.data.remote

import android.content.Context
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import com.unipoint.domain.model.*
import dadb.AdbKeyPair
import dadb.AdbStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.security.SecureRandom
import javax.inject.Inject

class AdbDataSource constructor(
    private val context: Context
) {
    private val tag = "AdbDataSource"
    private var dadb: Dadb? = null
    private var cachedKeyPair: AdbKeyPair? = null
    private val adbMutex = Mutex()
    private val controlMutex = Mutex()
    private val archiveMutex = Mutex()
    @Volatile private var realtimeControl: AdbStream? = null
    @Volatile private var realtimeServer: AdbStream? = null
    @Volatile private var realtimeRetryAfterMs: Long = 0L
    @Volatile private var uhidMouseReady: Boolean = false
    @Volatile private var uhidLastX: Int = 960
    @Volatile private var uhidLastY: Int = 540
    @Volatile private var displayWidth: Int = 1920
    @Volatile private var displayHeight: Int = 1080
    private val appDetailsCache = ConcurrentHashMap<String, InstalledApp>()
    private val appCachePrefs = context.getSharedPreferences("unipoint_app_cache", Context.MODE_PRIVATE)

    private val _devices = MutableStateFlow<List<AndroidDevice>>(emptyList())
    val devices: StateFlow<List<AndroidDevice>> = _devices.asStateFlow()
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private fun keyPairOrNull(): AdbKeyPair? {
        cachedKeyPair?.let { return it }
        return try {
            val dir = File(context.filesDir, "adb")
            if (!dir.exists()) dir.mkdirs()
            val priv = File(dir, "adbkey")
            val pub = File(dir, "adbkey.pub")
            val kp = if (priv.exists() && pub.exists()) {
                AdbKeyPair.read(priv, pub)
            } else {
                try {
                    AdbKeyPair.generate(priv, pub)
                    AdbKeyPair.read(priv, pub)
                } catch (e: Exception) {
                    try { AdbKeyPair.readDefault() } catch (_: Exception) { null }
                }
            }
            cachedKeyPair = kp
            kp
        } catch (e: Exception) {
            Log.w(tag, "keyPair: ${e.message}")
            null
        }
    }

    /** Get local Wi‑Fi subnet e.g. "192.168.10" */
    private fun localSubnet(): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) return null
            val a = ip and 0xff
            val b = ip shr 8 and 0xff
            val c = ip shr 16 and 0xff
            "$a.$b.$c"
        } catch (_: Exception) {
            null
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs); true }
        } catch (_: Exception) { false }
    }

    private fun probeDevice(ip: String, port: Int): AndroidDevice? {
        return try {
            if (!isPortOpen(ip, port, 400)) return null
            val d = Dadb.create(ip, port, keyPairOrNull(), 800, 1500)
            val model = try {
                d.shell("getprop ro.product.model").allOutput.trim()
            } catch (_: Exception) { "Android" }
            val ver = try {
                d.shell("getprop ro.build.version.release").allOutput.trim()
            } catch (_: Exception) { null }
            d.close()
            AndroidDevice(
                id = "$ip:$port",
                name = model.ifBlank { "Device @$ip" },
                ip = ip,
                port = port,
                model = model,
                androidVersion = ver
            )
        } catch (_: Exception) {
            // Port open but ADB auth failed — still show as possible device
            if (isPortOpen(ip, port, 150)) {
                AndroidDevice(
                    id = "$ip:$port",
                    name = "ADB @$ip",
                    ip = ip,
                    port = port
                )
            } else null
        }
    }

    suspend fun discover(extraSubnets: List<String> = emptyList()): List<AndroidDevice> =
        withContext(Dispatchers.IO) {
            val primary = localSubnet()
            val subnets = (listOfNotNull(primary) +
                listOf("192.168.10", "192.168.1", "192.168.0", "192.168.31", "10.0.0") +
                extraSubnets
                ).distinct()

            Log.i(tag, "Scanning subnets: $subnets")
            val found = mutableListOf<AndroidDevice>()
            val ports = listOf(5555)

            // Parallel scan of priority IPs first (common gateways + .20)
            val priorityEnds = listOf(1, 20, 100, 101, 2, 3, 4, 5, 50, 200, 219)

            coroutineScope {
                for (subnet in subnets) {
                    // Priority hosts first
                    val priorityJobs = priorityEnds.map { end ->
                        async {
                            ports.mapNotNull { port -> probeDevice("$subnet.$end", port) }
                        }
                    }
                    priorityJobs.awaitAll().flatten().forEach { d ->
                        if (found.none { it.id == d.id }) found.add(d)
                    }
                    // Primary LAN gets a complete /24 sweep so TV boxes at arbitrary DHCP
                    // addresses (for example .149 or .219) are discoverable. Fallback
                    // subnets stay bounded so a disconnected phone does not scan forever.
                    val rest = (if (subnet == primary) (1..254) else (6..40))
                        .filter { it !in priorityEnds }
                    rest.chunked(if (subnet == primary) 24 else 8).forEach { chunk ->
                        val jobs = chunk.map { end ->
                            async {
                                ports.mapNotNull { port -> probeDevice("$subnet.$end", port) }
                            }
                        }
                        jobs.awaitAll().flatten().forEach { d ->
                            if (found.none { it.id == d.id }) found.add(d)
                        }
                    }
                    // If we found something on primary subnet, stop other subnets early
                    if (found.isNotEmpty() && subnet == primary) break
                }
            }

            _devices.value = found
            Log.i(tag, "Found ${found.size} devices")
            found
        }

    suspend fun connect(ip: String, port: Int = 5555): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect()
            appDetailsCache.clear()
            var finalIp = ip.trim().removePrefix("adb://").substringBefore("/")
            var finalPort = port
            if (finalIp.contains(":")) {
                finalPort = finalIp.substringAfter(":").toIntOrNull() ?: port
                finalIp = finalIp.substringBefore(":")
            }
            Log.i(tag, "Connecting $finalIp:$finalPort")

            if (!isPortOpen(finalIp, finalPort, 2500)) {
                return@withContext Result.failure(Exception(
                    "Cannot reach $finalIp:$finalPort — same Wi‑Fi? Wireless debugging ON?"
                ))
            }

            val d = Dadb.create(finalIp, finalPort, keyPairOrNull(), 5000, 8000)
            try {
                d.shell("echo unipoint-ok")
            } catch (e: Exception) {
                d.close()
                return@withContext Result.failure(Exception(
                    "Not authorized. TV pe Allow USB debugging dabao.\n(${e.message})"
                ))
            }
            dadb = d
            _isConnected.value = true
            // Add to list if missing
            val model = try {
                d.shell("getprop ro.product.model").allOutput.trim()
            } catch (_: Exception) { finalIp }
            runCatching {
                val rawSize = d.shell("wm size 2>/dev/null | tail -1").allOutput
                Regex("(\\d+)x(\\d+)").findAll(rawSize).lastOrNull()?.let { match ->
                    displayWidth = match.groupValues[1].toIntOrNull() ?: displayWidth
                    displayHeight = match.groupValues[2].toIntOrNull() ?: displayHeight
                }
            }
            val device = AndroidDevice(
                id = "$finalIp:$finalPort", name = model, ip = finalIp, port = finalPort,
                model = model, isConnected = true
            )
            _devices.value = (listOf(device) + _devices.value.filter { it.id != device.id })
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "connect", e)
            Result.failure(Exception("Connect failed: ${e.message}"))
        }
    }

    fun disconnect() {
        closeRealtimeControl()
        try { dadb?.close() } catch (_: Exception) {}
        dadb = null
        _isConnected.value = false
    }

    private fun closeRealtimeControl() {
        val stream = realtimeControl
        if (uhidMouseReady && stream != null) {
            runCatching {
                stream.sink.write(ScrcpyControlProtocol.uhidDestroy())
                stream.sink.flush()
            }
        }
        uhidMouseReady = false
        uhidLastX = displayWidth / 2
        uhidLastY = displayHeight / 2
        runCatching { stream?.close() }
        runCatching { realtimeServer?.close() }
        realtimeControl = null
        realtimeServer = null
    }

    private fun startRealtimeControlLocked(): Result<AdbStream> {
        realtimeControl?.let { return Result.success(it) }
        val nowMs = System.nanoTime() / 1_000_000L
        if (nowMs < realtimeRetryAfterMs) {
            return Result.failure(Exception("Realtime input retry cooldown"))
        }
        val d = dadb ?: return Result.failure(Exception("Not connected"))
        return try {
            val local = File(context.cacheDir, "scrcpy-server.jar")
            if (!local.exists() || local.length() < 50_000) {
                context.assets.open("scrcpy-server.jar").use { input ->
                    local.outputStream().use { output -> input.copyTo(output) }
                }
            }
            d.push(local, "/data/local/tmp/doupad-control.jar", 420)
            val scid = "%08x".format(SecureRandom().nextInt(Int.MAX_VALUE))
            val command =
                "CLASSPATH=/data/local/tmp/doupad-control.jar app_process / com.genymobile.scrcpy.Server 3.3.1 " +
                    "scid=$scid log_level=error video=false audio=false control=true tunnel_forward=true " +
                    "send_dummy_byte=false send_device_meta=false cleanup=true"
            realtimeServer = d.open("shell:$command")
            var stream: AdbStream? = null
            var lastError: Exception? = null
            for (attempt in 0 until 30) {
                try {
                    stream = d.open("localabstract:scrcpy_$scid")
                    break
                } catch (e: Exception) {
                    lastError = e
                    Thread.sleep(100)
                }
            }
            val ready = stream ?: throw lastError ?: Exception("scrcpy control socket unavailable")
            realtimeControl = ready
            realtimeRetryAfterMs = 0L
            uhidMouseReady = false
            Result.success(ready)
        } catch (e: Exception) {
            closeRealtimeControl()
            realtimeRetryAfterMs = System.nanoTime() / 1_000_000L + 2_500L
            Result.failure(Exception("Realtime input unavailable: ${e.message}", e))
        }
    }

    private fun ensureUhidMouseLocked(stream: AdbStream): Result<Unit> = try {
        if (!uhidMouseReady) {
            stream.sink.write(ScrcpyControlProtocol.uhidCreate())
            stream.sink.flush()
            uhidMouseReady = true
            uhidLastX = displayWidth / 2
            uhidLastY = displayHeight / 2
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception("UHID mouse unavailable: ${e.message}", e))
    }

    private fun writeUhidMoveLocked(
        stream: AdbStream,
        targetX: Int,
        targetY: Int,
        buttons: Int = 0
    ) {
        var dx = targetX - uhidLastX
        var dy = targetY - uhidLastY
        if (dx == 0 && dy == 0) {
            stream.sink.write(
                ScrcpyControlProtocol.uhidInput(
                    data = ScrcpyControlProtocol.mouseReport(buttons = buttons)
                )
            )
        } else {
            while (dx != 0 || dy != 0) {
                val stepX = dx.coerceIn(-127, 127)
                val stepY = dy.coerceIn(-127, 127)
                stream.sink.write(
                    ScrcpyControlProtocol.uhidInput(
                        data = ScrcpyControlProtocol.mouseReport(
                            buttons = buttons, dx = stepX, dy = stepY
                        )
                    )
                )
                dx -= stepX
                dy -= stepY
            }
        }
        uhidLastX = targetX
        uhidLastY = targetY
    }

    private suspend fun sendUhidMouse(
        x: Int,
        y: Int,
        buttons: Int = 0,
        click: Boolean = false,
        wheel: Int = 0,
        horizontalScroll: Int = 0
    ): Result<Unit> = withContext(Dispatchers.IO) {
        controlMutex.withLock {
            val stream = startRealtimeControlLocked().getOrElse { return@withLock Result.failure(it) }
            ensureUhidMouseLocked(stream).getOrElse {
                closeRealtimeControl()
                return@withLock Result.failure(it)
            }
            try {
                writeUhidMoveLocked(stream, x, y)
                if (click) {
                    stream.sink.write(
                        ScrcpyControlProtocol.uhidInput(
                            data = ScrcpyControlProtocol.mouseReport(buttons = buttons)
                        )
                    )
                    stream.sink.write(
                        ScrcpyControlProtocol.uhidInput(
                            data = ScrcpyControlProtocol.mouseReport(buttons = 0)
                        )
                    )
                } else if (wheel != 0 || horizontalScroll != 0) {
                    stream.sink.write(
                        ScrcpyControlProtocol.uhidInput(
                            data = ScrcpyControlProtocol.mouseReport(
                                wheel = wheel, horizontalScroll = horizontalScroll
                            )
                        )
                    )
                }
                stream.sink.flush()
                Result.success(Unit)
            } catch (e: Exception) {
                closeRealtimeControl()
                realtimeRetryAfterMs = System.nanoTime() / 1_000_000L + 750L
                Result.failure(Exception("UHID mouse lost: ${e.message}", e))
            }
        }
    }

    private suspend fun sendRealtime(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        controlMutex.withLock {
            val stream = startRealtimeControlLocked().getOrElse { return@withLock Result.failure(it) }
            try {
                stream.sink.write(bytes)
                stream.sink.flush()
                Result.success(Unit)
            } catch (e: Exception) {
                closeRealtimeControl()
                realtimeRetryAfterMs = System.nanoTime() / 1_000_000L + 750L
                Result.failure(Exception("Realtime input lost: ${e.message}", e))
            }
        }
    }

    suspend fun prepareRealtimeInput(): Result<Unit> = withContext(Dispatchers.IO) {
        controlMutex.withLock {
            val stream = startRealtimeControlLocked().getOrElse { return@withLock Result.failure(it) }
            // Creating the UHID mouse makes Android TV expose a real system pointer arrow.
            ensureUhidMouseLocked(stream)
        }
    }

    suspend fun pointerMove(x: Int, y: Int, screenWidth: Int, screenHeight: Int): Result<Unit> {
        displayWidth = screenWidth
        displayHeight = screenHeight
        val uhid = sendUhidMouse(x, y)
        if (uhid.isSuccess) return uhid

        val direct = sendRealtime(
            ScrcpyControlProtocol.touch(
                action = ScrcpyControlProtocol.ACTION_MOVE,
                pointerId = ScrcpyControlProtocol.POINTER_ID_MOUSE,
                x = x, y = y, screenWidth = screenWidth, screenHeight = screenHeight,
                pressure = 0f, actionButton = 0, buttons = 0
            )
        )
        if (direct.isSuccess) return direct
        return shell(
            "input mouse motionevent MOVE $x $y 2>/dev/null || input motionevent MOVE $x $y 2>/dev/null || true"
        ).map { }
    }

    suspend fun pointerClick(
        x: Int, y: Int, screenWidth: Int, screenHeight: Int, button: MouseButton
    ): Result<Unit> {
        displayWidth = screenWidth
        displayHeight = screenHeight
        val flag = when (button) {
            MouseButton.LEFT -> ScrcpyControlProtocol.BUTTON_PRIMARY
            MouseButton.RIGHT -> ScrcpyControlProtocol.BUTTON_SECONDARY
            MouseButton.MIDDLE -> ScrcpyControlProtocol.BUTTON_TERTIARY
        }
        val uhid = sendUhidMouse(x, y, buttons = flag, click = true)
        if (uhid.isSuccess) return uhid

        val bytes = ScrcpyControlProtocol.touch(
            ScrcpyControlProtocol.ACTION_DOWN, ScrcpyControlProtocol.POINTER_ID_MOUSE,
            x, y, screenWidth, screenHeight, 1f, flag, flag
        ) + ScrcpyControlProtocol.touch(
            ScrcpyControlProtocol.ACTION_UP, ScrcpyControlProtocol.POINTER_ID_MOUSE,
            x, y, screenWidth, screenHeight, 0f, flag, 0
        )
        val direct = sendRealtime(bytes)
        if (direct.isSuccess) return direct
        return when (button) {
            MouseButton.LEFT -> shell("input mouse tap $x $y 2>/dev/null || input tap $x $y").map { }
            MouseButton.RIGHT -> shell("input keyevent 4").map { }
            MouseButton.MIDDLE -> Result.failure(direct.exceptionOrNull() ?: Exception("Middle click unavailable"))
        }
    }

    suspend fun pointerScroll(
        x: Int, y: Int, screenWidth: Int, screenHeight: Int, hScroll: Float, vScroll: Float
    ): Result<Unit> {
        displayWidth = screenWidth
        displayHeight = screenHeight
        val wheel = when {
            vScroll > 0f -> 1
            vScroll < 0f -> -1
            else -> 0
        }
        val horizontal = when {
            hScroll > 0f -> 1
            hScroll < 0f -> -1
            else -> 0
        }
        val uhid = sendUhidMouse(x, y, wheel = wheel, horizontalScroll = horizontal)
        if (uhid.isSuccess) return uhid

        val direct = sendRealtime(
            ScrcpyControlProtocol.scroll(
                x, y, screenWidth, screenHeight,
                hScroll.coerceIn(-1f, 1f), vScroll.coerceIn(-1f, 1f), 0
            )
        )
        if (direct.isSuccess) return direct
        val delta = if (vScroll > 0) 260 else -260
        val center = screenHeight / 2
        return shell("input swipe ${screenWidth / 2} $center ${screenWidth / 2} ${center + delta} 160").map { }
    }

    suspend fun shell(cmd: String): Result<String> = withContext(Dispatchers.IO) {
        adbMutex.withLock {
            val d = dadb ?: return@withLock Result.failure(Exception("Not connected"))
            try { Result.success(d.shell(cmd).allOutput) } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun install(apkPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        adbMutex.withLock {
            val d = dadb ?: return@withLock Result.failure(Exception("Not connected"))
            try { d.install(File(apkPath)); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun uninstall(packageName: String): Result<Unit> =
        shell("pm uninstall $packageName").map { }

    suspend fun listApps(): Result<List<InstalledApp>> = withContext(Dispatchers.IO) {
        // Fast first paint: one package-manager call only. Proper labels/icons are
        // enriched lazily for visible rows by appDetails()/appIcon().
        val output = shell("pm list packages -3").getOrElse {
            shell("pm list packages").getOrElse { return@withContext Result.failure(it) }
        }
        val apps = output.lineSequence()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { pkg ->
                appDetailsCache[pkg] ?: InstalledApp(
                    packageName = pkg,
                    label = appCachePrefs.getString("label:$pkg", null) ?: humanizePackage(pkg),
                    versionName = appCachePrefs.getString("version:$pkg", null),
                    isSystem = false,
                    isEnabled = true
                ).also { appDetailsCache[pkg] = it }
            }
            .sortedBy { it.label.lowercase() }
            .toList()
        Result.success(apps)
    }

    suspend fun appDetails(packageName: String): Result<InstalledApp> = withContext(Dispatchers.IO) {
        appDetailsCache[packageName]?.let { return@withContext Result.success(it) }
        val cachedLabel = appCachePrefs.getString("label:$packageName", null)
        val cachedVersion = appCachePrefs.getString("version:$packageName", null)
        if (cachedLabel != null) {
            val cached = InstalledApp(packageName, cachedLabel, cachedVersion, false, true)
            appDetailsCache[packageName] = cached
            return@withContext Result.success(cached)
        }
        // Fast metadata path: no APK transfer. Proper label/icon enrichment happens in appPresentation().
        val dump = shell("dumpsys package $packageName 2>/dev/null | grep -m1 'versionName='")
            .getOrNull().orEmpty()
        val version = dump.substringAfter("versionName=", "").trim().lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
        val quick = InstalledApp(packageName, humanizePackage(packageName), version, false, true)
        appDetailsCache[packageName] = quick
        Result.success(quick)
    }

    private fun humanizePackage(packageName: String): String {
        val raw = packageName.substringAfterLast('.').replace('_', ' ').replace('-', ' ')
        return raw.split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { c -> c.uppercase() } }
            .ifBlank { packageName }
    }

    private suspend fun apkRemotePath(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        val pkg = packageName.trim()
        if (!Regex("[A-Za-z0-9_.$-]+").matches(pkg)) {
            return@withContext Result.failure(Exception("Invalid package name"))
        }
        val output = shell("pm path $pkg").getOrElse { return@withContext Result.failure(it) }
        val path = output.lineSequence()
            .firstOrNull { it.startsWith("package:") }
            ?.removePrefix("package:")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(Exception("APK path not found for $pkg"))
        Result.success(path)
    }

    private suspend fun ensureArchive(packageName: String): Result<File> = archiveMutex.withLock {
        val safe = packageName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val apk = File(context.cacheDir, "apk-cache/$safe.apk").apply { parentFile?.mkdirs() }
        if (apk.exists() && apk.length() > 1024) return@withLock Result.success(apk)
        val remote = apkRemotePath(packageName).getOrElse { return@withLock Result.failure(it) }
        pull(remote, apk.absolutePath).getOrElse {
            apk.delete()
            return@withLock Result.failure(it)
        }
        if (apk.length() <= 1024) {
            apk.delete()
            return@withLock Result.failure(Exception("Downloaded APK is empty"))
        }
        Result.success(apk)
    }

    suspend fun appPresentation(packageName: String): Result<AppPresentation> = withContext(Dispatchers.IO) {
        try {
            val safe = packageName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val iconDir = File(context.filesDir, "app-cache/icons").apply { mkdirs() }
            val iconFile = File(iconDir, "$safe.png")
            if (!iconFile.exists()) {
                File(context.cacheDir, "app-icons/$safe.png").takeIf { it.exists() && it.length() > 100 }
                    ?.copyTo(iconFile, overwrite = true)
            }
            val cachedLabel = appCachePrefs.getString("label:$packageName", null)
            val cachedVersion = appCachePrefs.getString("version:$packageName", null)
            if (cachedLabel != null && iconFile.exists() && iconFile.length() > 100) {
                val app = InstalledApp(packageName, cachedLabel, cachedVersion, false, true)
                appDetailsCache[packageName] = app
                return@withContext Result.success(AppPresentation(app, iconFile.absolutePath))
            }

            val apk = ensureArchive(packageName).getOrElse { return@withContext Result.failure(it) }
            val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_META_DATA)
                ?: return@withContext Result.failure(Exception("Unable to read APK metadata"))
            info.applicationInfo?.sourceDir = apk.absolutePath
            info.applicationInfo?.publicSourceDir = apk.absolutePath
            val label = info.applicationInfo?.loadLabel(context.packageManager)?.toString()?.trim()
                ?.takeIf { it.isNotBlank() } ?: humanizePackage(packageName)
            val version = info.versionName
            val app = InstalledApp(packageName, label, version, false, info.applicationInfo?.enabled ?: true)

            if (!iconFile.exists() || iconFile.length() <= 100) {
                info.applicationInfo?.loadIcon(context.packageManager)?.let { drawable ->
                    val size = 128
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(8, 8, size - 8, size - 8)
                    drawable.draw(canvas)
                    FileOutputStream(iconFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                    bitmap.recycle()
                }
            }
            appCachePrefs.edit().putString("label:$packageName", label)
                .putString("version:$packageName", version).apply()
            appDetailsCache[packageName] = app
            Result.success(AppPresentation(app, iconFile.takeIf { it.exists() && it.length() > 100 }?.absolutePath))
        } catch (e: Exception) {
            Result.failure(Exception("App enrichment failed: ${e.message}", e))
        }
    }

    suspend fun appIcon(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        val presentation = appPresentation(packageName).getOrElse { return@withContext Result.failure(it) }
        presentation.iconPath?.let { Result.success(it) }
            ?: Result.failure(Exception("Icon unavailable"))
    }

    suspend fun downloadApk(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val safe = packageName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val source = ensureArchive(packageName).getOrElse { return@withContext Result.failure(it) }
            val temp = File(context.cacheDir, "download-$safe.apk")
            source.copyTo(temp, overwrite = true)
            val fileName = "$safe.apk"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/UniPoint")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(Exception("Cannot create Downloads entry"))
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(temp).use { it.copyTo(out) }
                    } ?: throw Exception("Cannot write downloaded APK")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    temp.delete()
                    Result.success("Downloads/UniPoint/$fileName")
                } catch (e: Exception) {
                    context.contentResolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .resolve("UniPoint").apply { mkdirs() }
                val target = File(dir, fileName)
                temp.copyTo(target, overwrite = true)
                temp.delete()
                Result.success(target.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(Exception("APK download failed: ${e.message}", e))
        }
    }

    suspend fun listFiles(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        val clean = path.trimEnd('/').ifEmpty { "/" }
        val output = shell("ls -la \"$clean\" 2>/dev/null; echo __SPLIT__; ls -1p \"$clean\" 2>/dev/null")
            .getOrElse { return@withContext Result.failure(it) }
        val entries = mutableListOf<FileEntry>()
        val seen = mutableSetOf<String>()
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("total") || trimmed == "__SPLIT__") continue
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size >= 8 && (parts[0].firstOrNull() in listOf('d', '-', 'l'))) {
                val name = parts.drop(7).joinToString(" ")
                if (name == "." || name == ".." || !seen.add(name)) continue
                val isDir = parts[0].startsWith("d")
                val size = parts.getOrNull(4)?.toLongOrNull() ?: 0L
                val full = if (clean == "/") "/$name" else "$clean/$name"
                entries.add(FileEntry(name, full, isDir, size))
            } else {
                var name = trimmed.trimEnd('/')
                if (name.isEmpty() || name == "." || name == ".." || name.contains(' ')) continue
                if (!seen.add(name)) continue
                val isDir = trimmed.endsWith("/")
                val full = if (clean == "/") "/$name" else "$clean/$name"
                entries.add(FileEntry(name, full, isDir, 0))
            }
        }
        Result.success(entries.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() }))
    }

    suspend fun push(local: String, remote: String): Result<Unit> = withContext(Dispatchers.IO) {
        adbMutex.withLock {
            val d = dadb ?: return@withLock Result.failure(Exception("Not connected"))
            try { d.push(File(local), remote); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun pull(remote: String, local: String): Result<Unit> = withContext(Dispatchers.IO) {
        adbMutex.withLock {
            val d = dadb ?: return@withLock Result.failure(Exception("Not connected"))
            try {
                val target = File(local)
                target.parentFile?.mkdirs()
                d.pull(target, remote)
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    private fun isPng(bytes: ByteArray): Boolean {
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        )
        return bytes.size > 100 && bytes.copyOfRange(0, signature.size).contentEquals(signature)
    }

    suspend fun screenshot(localPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        adbMutex.withLock {
            val d = dadb ?: return@withLock Result.failure(Exception("Not connected"))
            val target = File(localPath).apply { parentFile?.mkdirs(); delete() }

            // Fast path: stream PNG bytes directly over ADB. This avoids /sdcard permission,
            // sync and pull quirks seen on some Android TV boxes.
            try {
                val stream = d.open("shell:screencap -p")
                val bytes = try { stream.source.readByteArray() } finally { stream.close() }
                if (isPng(bytes)) {
                    target.writeBytes(bytes)
                    return@withLock Result.success(Unit)
                }
            } catch (e: Exception) {
                Log.w(tag, "Direct screenshot failed; trying file fallback: ${e.message}")
            }

            val remote = "/sdcard/.doupad_${System.currentTimeMillis()}.png"
            try {
                val capture = d.shell("screencap -p '$remote' && sync")
                if (capture.exitCode != 0) {
                    return@withLock Result.failure(Exception(capture.allOutput.ifBlank { "screencap failed" }))
                }
                d.pull(target, remote)
                runCatching { d.shell("rm -f '$remote'") }
                val bytes = if (target.exists()) target.readBytes() else ByteArray(0)
                if (!isPng(bytes)) {
                    target.delete()
                    Result.failure(Exception("Screenshot returned no valid PNG image"))
                } else Result.success(Unit)
            } catch (e: Exception) {
                runCatching { d.shell("rm -f '$remote'") }
                target.delete()
                Result.failure(e)
            }
        }
    }

    suspend fun deviceInfo(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        val cmd = """
            echo __model__=$(getprop ro.product.model);
            echo __brand__=$(getprop ro.product.brand);
            echo __android__=$(getprop ro.build.version.release);
            echo __sdk__=$(getprop ro.build.version.sdk);
            echo __serial__=$(getprop ro.serialno);
            echo __display__=$(wm size 2>/dev/null | tail -1 | sed 's/.*: //');
            echo __density__=$(wm density 2>/dev/null | tail -1 | sed 's/.*: //');
            echo __storage__=$(df -h /data 2>/dev/null | tail -1);
            echo __ram__=$(cat /proc/meminfo 2>/dev/null | grep -m1 MemTotal);
            echo __battery__=$(dumpsys battery 2>/dev/null | grep -m1 'level:' | sed 's/.*level: *//');
            echo __ip__=$(ip route get 1.1.1.1 2>/dev/null | grep -o 'src [0-9.]*' | head -1 | cut -d' ' -f2);
            echo __uptime__=$(uptime 2>/dev/null)
        """.trimIndent().replace("\n", " ")
        val output = shell(cmd).getOrElse { return@withContext Result.failure(it) }
        val result = linkedMapOf<String, String>()
        val keys = listOf("model", "brand", "android", "sdk", "serial", "display", "density", "storage", "ram", "battery", "ip", "uptime")
        keys.forEach { key ->
            val prefix = "__${key}__="
            result[key] = output.lineSequence().firstOrNull { it.startsWith(prefix) }
                ?.removePrefix(prefix)?.trim()?.ifBlank { "-" } ?: "-"
        }
        result["storage"]?.split(Regex("\\s+"))?.let { f ->
            if (f.size >= 5) result["storage"] = "${f[3]} free / ${f[1]} • ${f[4]} used"
        }
        result["ram"] = result["ram"]?.let { raw ->
            Regex("(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { kb ->
                "%.1f GB".format(kb / 1024.0 / 1024.0)
            } ?: raw
        } ?: "-"
        result["battery"] = result["battery"]?.takeIf { it != "-" }?.let { "$it%" } ?: "-"
        result["uptime"] = result["uptime"]?.substringBefore(", load average")?.trim().orEmpty().ifBlank { "-" }
        Result.success(result)
    }

    suspend fun inputKey(keyCode: Int): Result<Unit> {
        val bytes = ScrcpyControlProtocol.key(ScrcpyControlProtocol.ACTION_DOWN, keyCode) +
            ScrcpyControlProtocol.key(ScrcpyControlProtocol.ACTION_UP, keyCode)
        val direct = sendRealtime(bytes)
        return if (direct.isSuccess) direct else shell("input keyevent $keyCode").map { }
    }

    suspend fun inputText(text: String): Result<Unit> {
        val direct = sendRealtime(ScrcpyControlProtocol.text(text))
        return if (direct.isSuccess) direct
        else shell("input text '${text.replace("'", "\\'").replace(" ", "%s")}'").map { }
    }

    suspend fun inputTap(x: Int, y: Int): Result<Unit> {
        val width = displayWidth
        val height = displayHeight
        val bytes = ScrcpyControlProtocol.touch(
            ScrcpyControlProtocol.ACTION_DOWN, ScrcpyControlProtocol.POINTER_ID_FINGER,
            x, y, width, height, 1f
        ) + ScrcpyControlProtocol.touch(
            ScrcpyControlProtocol.ACTION_UP, ScrcpyControlProtocol.POINTER_ID_FINGER,
            x, y, width, height, 0f
        )
        val direct = sendRealtime(bytes)
        return if (direct.isSuccess) direct else shell("input tap $x $y").map { }
    }
    suspend fun inputSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 300) =
        shell("input swipe $x1 $y1 $x2 $y2 $duration").map { }
    suspend fun reboot() = shell("reboot").map { }
}
