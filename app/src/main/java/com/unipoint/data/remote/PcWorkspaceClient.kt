package com.unipoint.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** Interactive LAN-only PC Workspace protocol. Requires a running DuoPad Host v4.
 * Separate authenticated socket from the mouse/keyboard control channel.
 * Private LAN only; do not route this plaintext channel directly over WAN.
 */
class PcWorkspaceClient(
    private val host: String,
    private val port: Int,
    private val pin: String
) {
    private val mutex = Mutex()
    private var socket: Socket? = null
    private var reader: DataInputStream? = null
    private var writer: DataOutputStream? = null

    fun close() {
        runCatching { socket?.close() }
        socket = null
        reader = null
        writer = null
    }

    private fun writePacket(type: Int, value: ByteArray = byteArrayOf()) {
        require(value.size <= 65535) { "Payload too large" }
        val out = writer ?: throw IOException("Not connected")
        out.writeByte(type)
        out.writeByte(value.size and 0xff)
        out.writeByte(value.size ushr 8)
        out.write(value)
        out.flush()
    }

    private fun readPacket(expected: Int): ByteArray {
        val input = reader ?: throw IOException("Not connected")
        val type = input.readUnsignedByte()
        val lo = input.readUnsignedByte()
        val hi = input.readUnsignedByte()
        val length = lo or (hi shl 8)
        if (type != expected) throw IOException("Unexpected PC host response: $type")
        return ByteArray(length).also { input.readFully(it) }
    }

    private fun jsonRequest(type: Int, payload: JSONObject = JSONObject()): JSONObject {
        writePacket(type, payload.toString().toByteArray(Charsets.UTF_8))
        val response = JSONObject(String(readPacket(type), Charsets.UTF_8))
        if (!response.optBoolean("ok", false)) {
            throw IOException(response.optString("error", "PC operation failed"))
        }
        return response
    }

    private fun connectIfNeeded() {
        if (socket?.isConnected == true && socket?.isClosed == false) return
        close()
        val conn = Socket().apply {
            tcpNoDelay = true
            soTimeout = 8000
            connect(InetSocketAddress(host, port), 4000)
        }
        socket = conn
        reader = DataInputStream(conn.getInputStream())
        writer = DataOutputStream(conn.getOutputStream())
        try {
            writePacket(0x20)
            val identity = String(readPacket(0x20), Charsets.UTF_8)
            if (!identity.startsWith("UNIPOINT/2") && !identity.startsWith("DOUPAD/")) {
                throw IOException("The PC does not run DuoPad Host")
            }
            if (identity.contains("AUTH=1")) {
                if (pin.isBlank()) throw IOException("Enter the PIN shown in the DuoPad PC Host window")
                writePacket(0x10, pin.toByteArray(Charsets.UTF_8))
                if (String(readPacket(0x10), Charsets.UTF_8) != "OK") {
                    throw IOException("Incorrect PC Host pairing PIN")
                }
            }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    private suspend fun <T> locked(action: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            connectIfNeeded()
            try { action() } catch (error: Exception) {
                close() // a partially-read transfer must never desynchronise the connection
                throw error
            }
        }
    }

    suspend fun information(): JSONObject = locked { jsonRequest(0x30).getJSONObject("info") }
    suspend fun drives(): JSONObject = locked { jsonRequest(0x31) }
    suspend fun directory(path: String): JSONObject = locked {
        jsonRequest(0x32, JSONObject().put("path", path))
    }

    suspend fun desktopFrame(width: Int = 960, quality: Int = 58): Bitmap = locked {
        val result = jsonRequest(0x40, JSONObject().put("width", width).put("quality", quality))
        val size = result.getInt("size")
        if (size !in 1..(3 * 1024 * 1024)) throw IOException("Invalid desktop frame size")
        val bytes = ByteArray(size)
        reader!!.readFully(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IOException("Could not decode PC desktop frame")
    }

    suspend fun download(context: Context, pcPath: String, destination: Uri): Long = locked {
        val result = jsonRequest(0x33, JSONObject().put("path", pcPath))
        val size = result.getLong("size")
        if (size < 0 || size > 1024L * 1024L * 1024L) throw IOException("Invalid transfer size")
        val output = context.contentResolver.openOutputStream(destination)
            ?: throw IOException("Cannot open the selected destination")
        output.use { out ->
            val buffer = ByteArray(65536)
            var remaining = size
            while (remaining > 0) {
                val read = reader!!.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw IOException("PC closed the download early")
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        size
    }

    suspend fun upload(context: Context, uri: Uri, targetFolder: String): String = locked {
        val (displayName, size) = context.contentResolver.query(uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) throw IOException("Cannot read selected file")
            val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "upload.bin" else "upload.bin"
            val bytes = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else -1L
            name to bytes
        } ?: throw IOException("Selected file has no size information")
        if (size !in 0..(1024L * 1024L * 1024L)) {
            throw IOException("Upload must have a known size below 1 GiB")
        }
        jsonRequest(0x34, JSONObject().put("folder", targetFolder)
            .put("name", displayName).put("size", size))
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot read selected file")
        input.use { source ->
            val buffer = ByteArray(65536)
            var remaining = size
            while (remaining > 0) {
                val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw IOException("Selected file ended unexpectedly")
                writer!!.write(buffer, 0, read)
                remaining -= read
            }
            writer!!.flush()
        }
        val response = JSONObject(String(readPacket(0x34), Charsets.UTF_8))
        if (!response.optBoolean("ok", false)) throw IOException(response.optString("error"))
        response.getString("path")
    }
}
