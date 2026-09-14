package com.unipoint.data.remote

import android.content.Context
import android.util.Log
import com.unipoint.core.network.PcConnectionHistoryCodec
import com.unipoint.core.network.PcDiscoveryPolicy
import com.unipoint.core.network.PcDiscoveryProtocol
import com.unipoint.core.network.PcHistoryEntry
import com.unipoint.core.network.PcHostIdentity
import com.unipoint.domain.model.InputEvent
import com.unipoint.domain.model.MouseButton
import com.unipoint.domain.model.PcConnectionType
import com.unipoint.domain.model.PcDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/** Binary client + resilient LAN discovery for the DOUPAD desktop host. */
class NetworkPcDataSource constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "NetworkPcDS"
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
    var onConnectionLost: (() -> Unit)? = null
    private val prefs = context.getSharedPreferences("unipoint_pc", Context.MODE_PRIVATE)
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var input: DataInputStream? = null

    private val lastLatency = AtomicLong(0)
    val latencyMs: Long get() = lastLatency.get()

    @Volatile
    var isConnected = false
        private set

    suspend fun discover(port: Int = PcDiscoveryPolicy.DEFAULT_TCP_PORT): List<PcDevice> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, PcDevice>()
        val history = historyEntries()
        val local = localSubnets()

        // 1) PalmPoint-style remembered PCs are visible immediately, even if currently offline.
        history.forEach { saved ->
            val id = "${saved.host}:${saved.port}"
            found[id] = PcDevice(
                id = id,
                name = saved.name + if (saved.authRequired) " (PIN)" else "",
                address = id,
                type = PcConnectionType.NETWORK,
                lastSeen = saved.lastSeen,
                requiresPin = saved.authRequired,
                isSaved = true,
                isReachable = false
            )
        }

        // Probe remembered endpoints first with relaxed timeouts; Windows may still be waking up.
        coroutineScope {
            history.map { saved ->
                async {
                    probeHost(
                        saved.host,
                        saved.port,
                        PcDiscoveryPolicy.SAVED_CONNECT_TIMEOUT_MS,
                        PcDiscoveryPolicy.SAVED_READ_TIMEOUT_MS
                    )?.copy(
                        name = saved.name + if (saved.authRequired) " (PIN)" else "",
                        requiresPin = saved.authRequired,
                        isSaved = true,
                        isReachable = true
                    )
                }
            }.awaitAll().filterNotNull().forEach { found[it.id] = it }
        }

        // 2) Multi-round broadcast discovery: limited broadcast + interface/directed broadcasts.
        udpDiscover(local).forEach { device -> found[device.id] = device }
        if (found.values.any { it.isReachable }) return@withContext sortDevices(found.values)

        // 3) TCP fallback on the actual LAN subnet, plus the last known subnet if it changed.
        val lastHost = history.firstOrNull()?.host ?: prefs.getString("last_pc", null)?.substringBefore(':')
        val subnets = PcDiscoveryPolicy.candidateSubnets(local, lastHost).take(4)
        val priority = listOf(1, 2, 5, 10, 20, 50, 100, 101, 150, 200, 254)
        for (subnet in subnets) {
            coroutineScope {
                priority.map { end ->
                    async {
                        probeHost(
                            "$subnet.$end",
                            port,
                            PcDiscoveryPolicy.PRIORITY_CONNECT_TIMEOUT_MS,
                            PcDiscoveryPolicy.PRIORITY_READ_TIMEOUT_MS
                        )
                    }
                }.awaitAll().filterNotNull().forEach { found[it.id] = it }
            }
        }
        if (found.values.any { it.isReachable }) return@withContext sortDevices(found.values)

        // 4) Exhaustive scan is last resort only, and never wastes time on unrelated default subnets.
        for (subnet in subnets.take(2)) {
            (1..254).filterNot { it in priority }.chunked(56).forEach { chunk ->
                coroutineScope {
                    chunk.map { end ->
                        async {
                            probeHost(
                                "$subnet.$end",
                                port,
                                PcDiscoveryPolicy.FULL_SCAN_CONNECT_TIMEOUT_MS,
                                PcDiscoveryPolicy.FULL_SCAN_READ_TIMEOUT_MS
                            )
                        }
                    }.awaitAll().filterNotNull().forEach { found[it.id] = it }
                }
                if (found.values.any { it.isReachable }) return@withContext sortDevices(found.values)
            }
        }
        sortDevices(found.values)
    }

    suspend fun connect(ip: String, port: Int, pin: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                disconnect()
                val cleanIp = ip.trim()
                val s = Socket().apply {
                    tcpNoDelay = true
                    keepAlive = true
                    soTimeout = 2500
                    connect(InetSocketAddress(cleanIp, port), 3500)
                }
                val out = DataOutputStream(s.getOutputStream())
                val inp = DataInputStream(s.getInputStream())

                val identity = pingIdentity(out, inp)
                    ?: run {
                        s.close()
                        return@withContext Result.failure(Exception("Not a DOUPAD Host on $cleanIp:$port"))
                    }
                val parsedIdentity = PcHostIdentity.parse(identity)
                    ?: run {
                        s.close()
                        return@withContext Result.failure(Exception("Unsupported DOUPAD Host identity"))
                    }
                val authRequired = parsedIdentity.authRequired
                if (authRequired && pin.isNullOrBlank()) {
                    s.close()
                    return@withContext Result.failure(Exception("PC Host requires a PIN"))
                }

                if (!pin.isNullOrBlank()) {
                    sendPacketTo(out, 0x10, pin.toByteArray(Charsets.UTF_8))
                    val response = readPacketFrom(inp)
                    if (response == null || response.first != 0x10.toByte() ||
                        !response.second.toString(Charsets.UTF_8).equals("OK", ignoreCase = true)
                    ) {
                        s.close()
                        return@withContext Result.failure(Exception("Authentication failed — check PC PIN"))
                    }
                }

                val verifyStart = System.currentTimeMillis()
                if (pingIdentity(out, inp) == null) {
                    s.close()
                    return@withContext Result.failure(Exception("DOUPAD Host handshake failed"))
                }

                socket = s
                output = out
                input = inp
                lastLatency.set(System.currentTimeMillis() - verifyStart)
                isConnected = true
                prefs.edit().putString("last_pc", "$cleanIp:$port").apply()
                saveHistory(
                    PcHistoryEntry(
                        host = cleanIp,
                        port = port,
                        name = parsedIdentity.name ?: historyEntries().firstOrNull { it.host == cleanIp && it.port == port }?.name ?: "DOUPAD PC",
                        authRequired = authRequired,
                        lastSeen = System.currentTimeMillis()
                    )
                )
                Log.i(tag, "Verified DOUPAD Host $cleanIp:$port")
                Result.success(Unit)
            } catch (e: Exception) {
                disconnect()
                Log.e(tag, "Connect error", e)
                Result.failure(Exception("PC connect failed: ${e.message}", e))
            }
        }

    fun disconnect() {
        try { output?.close() } catch (_: Exception) {}
        try { input?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        output = null
        input = null
        socket = null
        isConnected = false
    }

    fun send(event: InputEvent) {
        if (!isConnected) return
        when (event) {
            is InputEvent.MouseMove -> {
                val payload = ByteArray(4)
                writeInt16(payload, 0, event.dx)
                writeInt16(payload, 2, event.dy)
                sendPacket(0x01, payload)
            }
            is InputEvent.MouseClick -> {
                val btn = when (event.button) {
                    MouseButton.LEFT -> 1
                    MouseButton.RIGHT -> 2
                    MouseButton.MIDDLE -> 3
                }
                sendPacket(0x02, byteArrayOf(btn.toByte(), if (event.down) 1 else 0))
            }
            is InputEvent.MouseScroll -> {
                val payload = ByteArray(2)
                writeInt16(payload, 0, event.dy)
                sendPacket(0x03, payload)
            }
            is InputEvent.KeyEvent -> {
                val payload = ByteArray(4)
                writeInt16(payload, 0, event.keyCode)
                payload[2] = if (event.down) 1 else 0
                payload[3] = event.modifiers.toByte()
                sendPacket(0x04, payload)
            }
            is InputEvent.TextInput -> sendPacket(0x05, event.text.toByteArray(Charsets.UTF_8))
            else -> Unit
        }
    }

    suspend fun ping(): Long = withContext(Dispatchers.IO) {
        val out = output ?: return@withContext -1L
        val inp = input ?: return@withContext -1L
        val start = System.currentTimeMillis()
        if (pingIdentity(out, inp) == null) return@withContext -1L
        (System.currentTimeMillis() - start).also { lastLatency.set(it) }
    }

    fun sendClipboard(text: String) = sendPacket(0x21, text.toByteArray(Charsets.UTF_8))

    private fun udpDiscover(localSubnets: List<String>): List<PcDevice> {
        val found = linkedMapOf<String, PcDevice>()
        val verifiedAddresses = hashSetOf<String>()
        return try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 180
                val targets = linkedSetOf<InetAddress>()

                PcDiscoveryPolicy.broadcastAddresses(localSubnets).forEach { address ->
                    runCatching { InetAddress.getByName(address) }.getOrNull()?.let { targets += it }
                }
                // Also trust the interface-provided broadcast when Android exposes it.
                runCatching {
                    Collections.list(NetworkInterface.getNetworkInterfaces())
                        .filter { it.isUp && !it.isLoopback }
                        .flatMap { it.interfaceAddresses }
                        .mapNotNull { it.broadcast }
                        .forEach { targets += it }
                }

                repeat(PcDiscoveryPolicy.UDP_ROUNDS) {
                    PcDiscoveryProtocol.REQUESTS.forEach { request ->
                        val payload = request.toByteArray(Charsets.UTF_8)
                        targets.forEach { target ->
                            runCatching {
                                socket.send(DatagramPacket(payload, payload.size, target, PcDiscoveryProtocol.UDP_PORT))
                            }
                        }
                    }

                    val deadline = System.currentTimeMillis() + PcDiscoveryPolicy.UDP_ROUND_LISTEN_MS
                    val buffer = ByteArray(512)
                    while (System.currentTimeMillis() < deadline) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                            val announcement = PcDiscoveryProtocol.parse(raw) ?: continue
                            val ip = packet.address.hostAddress ?: continue
                            val key = "$ip:${announcement.port}"
                            if (!verifiedAddresses.add(key)) continue
                            val verified = probeHost(
                                ip,
                                announcement.port,
                                PcDiscoveryPolicy.VERIFY_CONNECT_TIMEOUT_MS,
                                PcDiscoveryPolicy.VERIFY_READ_TIMEOUT_MS
                            ) ?: continue
                            val device = verified.copy(
                                name = announcement.name + if (announcement.authRequired) " (PIN)" else "",
                                requiresPin = announcement.authRequired,
                                isReachable = true
                            )
                            found[device.id] = device
                        } catch (_: SocketTimeoutException) {
                            // Keep listening until this round's deadline.
                        }
                    }
                    if (found.isNotEmpty()) return@use
                }
            }
            found.values.toList()
        } catch (e: Exception) {
            Log.d(tag, "UDP discovery unavailable: ${e.message}")
            emptyList()
        }
    }

    private fun probeHost(
        ip: String,
        port: Int,
        connectTimeout: Int = PcDiscoveryPolicy.VERIFY_CONNECT_TIMEOUT_MS,
        readTimeout: Int = PcDiscoveryPolicy.VERIFY_READ_TIMEOUT_MS
    ): PcDevice? {
        return try {
            Socket().use { s ->
                s.tcpNoDelay = true
                s.keepAlive = true
                s.soTimeout = readTimeout
                s.connect(InetSocketAddress(ip, port), connectTimeout)
                val out = DataOutputStream(s.getOutputStream())
                val inp = DataInputStream(s.getInputStream())
                val identityText = pingIdentity(out, inp) ?: return null
                val identity = PcHostIdentity.parse(identityText) ?: return null
                PcDevice(
                    id = "$ip:$port",
                    name = (identity.name ?: "DOUPAD PC") + if (identity.authRequired) " (PIN)" else "",
                    address = "$ip:$port",
                    type = PcConnectionType.NETWORK,
                    requiresPin = identity.authRequired,
                    isReachable = true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pingIdentity(out: DataOutputStream, inp: DataInputStream): String? {
        sendPacketTo(out, 0x20, byteArrayOf())
        val response = readPacketFrom(inp) ?: return null
        if (response.first != 0x20.toByte()) return null
        val identity = response.second.toString(Charsets.UTF_8)
        return identity.takeIf { PcHostIdentity.parse(it) != null }
    }

    private fun historyEntries(): List<PcHistoryEntry> =
        PcConnectionHistoryCodec.decode(prefs.getString("pc_history_v1", null))

    private fun saveHistory(entry: PcHistoryEntry) {
        val merged = listOf(entry) + historyEntries().filterNot { it.host == entry.host && it.port == entry.port }
        prefs.edit().putString("pc_history_v1", PcConnectionHistoryCodec.encode(merged)).apply()
    }

    private fun sortDevices(devices: Collection<PcDevice>): List<PcDevice> = devices.sortedWith(
        compareByDescending<PcDevice> { it.isReachable }
            .thenByDescending { it.isConnected }
            .thenByDescending { it.lastSeen }
    )

    private fun sendPacket(type: Int, payload: ByteArray) {
        val out = output ?: return
        if (payload.size > 65535) return
        writer.execute {
            // Never deliver queued input to a newly connected PC.
            if (output !== out || !isConnected) return@execute
            try {
                sendPacketTo(out, type, payload)
            } catch (e: Exception) {
                Log.e(tag, "Send failed", e)
                if (output === out) {
                    disconnect()
                    onConnectionLost?.invoke()
                }
            }
        }
    }

    private fun sendPacketTo(out: DataOutputStream, type: Int, payload: ByteArray) {
        synchronized(out) {
            out.writeByte(type)
            writeUInt16LE(out, payload.size)
            if (payload.isNotEmpty()) out.write(payload)
            out.flush()
        }
    }

    private fun readPacketFrom(inp: DataInputStream): Pair<Byte, ByteArray>? = try {
        val type = inp.readByte()
        val len = readUInt16LE(inp)
        val payload = if (len > 0) ByteArray(len).also { inp.readFully(it) } else byteArrayOf()
        type to payload
    } catch (_: Exception) {
        null
    }

    private fun localSubnets(): List<String> {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { iface ->
                    val n = iface.name.lowercase()
                    when {
                        n.startsWith("wlan") || n.startsWith("wifi") -> 0
                        n.startsWith("eth") -> 1
                        else -> 2
                    }
                }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress ?: "" }
                .filter { isPrivateIpv4(it) }
                .mapNotNull { ip -> ip.substringBeforeLast('.', missingDelimiterValue = "").takeIf { it.isNotBlank() } }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isPrivateIpv4(ip: String): Boolean =
        ip.startsWith("10.") || ip.startsWith("192.168.") || Regex("172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(ip)

    private fun writeUInt16LE(out: DataOutputStream, value: Int) {
        out.writeByte(value and 0xFF)
        out.writeByte((value ushr 8) and 0xFF)
    }

    private fun readUInt16LE(input: DataInputStream): Int {
        val lo = input.readUnsignedByte()
        val hi = input.readUnsignedByte()
        return lo or (hi shl 8)
    }

    private fun writeInt16(buf: ByteArray, offset: Int, value: Int) {
        val clamped = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        buf[offset] = (clamped and 0xFF).toByte()
        buf[offset + 1] = ((clamped shr 8) and 0xFF).toByte()
    }
}
