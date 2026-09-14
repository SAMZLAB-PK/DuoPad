package com.unipoint.core.network

import java.net.URLDecoder
import java.net.URLEncoder

/** A PC endpoint obtained from QR, manual entry, history or discovery. */
data class PcPairingEndpoint(
    val host: String,
    val port: Int = PcDiscoveryPolicy.DEFAULT_TCP_PORT,
    val pin: String? = null,
    val name: String? = null
)


data class PcHostIdentity(
    val authRequired: Boolean,
    val name: String? = null
) {
    companion object {
        fun parse(raw: String): PcHostIdentity? {
            val text = raw.trim()
            val signature = when {
                text.startsWith("DOUPAD/3") -> "DOUPAD/3"
                text.startsWith("UNIPOINT/2") -> "UNIPOINT/2"
                else -> return null
            }
            val params = text.removePrefix(signature).trimStart(';')
                .split(';')
                .mapNotNull { token ->
                    if (token.isBlank() || '=' !in token) return@mapNotNull null
                    token.substringBefore('=').uppercase() to token.substringAfter('=')
                }.toMap()
            val name = params["NAME"]?.takeIf { it.isNotBlank() }?.let { decodeValue(it) }
            return PcHostIdentity(authRequired = params["AUTH"] == "1", name = name)
        }

        private fun decodeValue(value: String): String = runCatching {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        }.getOrDefault(value)
    }
}

data class PcHistoryEntry(
    val host: String,
    val port: Int,
    val name: String,
    val authRequired: Boolean,
    val lastSeen: Long
)

/**
 * Stable public pairing contract.
 *
 * Current: doupad://pc/<host>:<port>?name=<url-encoded>&pin=<url-encoded>
 * Legacy UniPoint links remain accepted so existing users do not break.
 */
object PcPairingUri {
    private const val CURRENT_PREFIX = "doupad://pc/"
    private const val LEGACY_PREFIX = "unipoint://pc/"

    fun parse(raw: String): PcPairingEndpoint? {
        val text = raw.trim()
        if (text.isBlank()) return null

        return when {
            text.startsWith(CURRENT_PREFIX, ignoreCase = true) -> parseUri(text.substring(CURRENT_PREFIX.length))
            text.startsWith(LEGACY_PREFIX, ignoreCase = true) -> parseUri(text.substring(LEGACY_PREFIX.length))
            else -> parseManual(text)
        }
    }

    fun encode(endpoint: PcPairingEndpoint): String {
        val query = buildList {
            endpoint.name?.trim()?.takeIf { it.isNotEmpty() }?.let { add("name=${encodeValue(it)}") }
            endpoint.pin?.trim()?.takeIf { it.isNotEmpty() }?.let { add("pin=${encodeValue(it)}") }
        }
        return buildString {
            append(CURRENT_PREFIX)
            append(endpoint.host)
            append(':')
            append(endpoint.port)
            if (query.isNotEmpty()) {
                append('?')
                append(query.joinToString("&"))
            }
        }
    }

    private fun parseUri(rest: String): PcPairingEndpoint? {
        val hostPart = rest.substringBefore('?').trim()
        val query = rest.substringAfter('?', missingDelimiterValue = "")
        val base = parseManual(hostPart) ?: return null
        val params = query.split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val key = part.substringBefore('=').trim().lowercase()
                val value = decodeValue(part.substringAfter('=', ""))
                key to value
            }.toMap()
        return base.copy(
            pin = params["pin"]?.takeIf { it.isNotBlank() },
            name = params["name"]?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseManual(text: String): PcPairingEndpoint? {
        val clean = text.trim()
        if (clean.contains(" ") || clean.contains('/') || clean.contains('?')) return null
        val host = clean.substringBefore(':').trim()
        val portText = clean.substringAfter(':', missingDelimiterValue = "").trim()
        val port = if (portText.isBlank()) PcDiscoveryPolicy.DEFAULT_TCP_PORT else portText.toIntOrNull() ?: return null
        if (port !in 1..65535 || !isValidHost(host)) return null
        return PcPairingEndpoint(host = host, port = port)
    }

    private fun isValidHost(host: String): Boolean {
        if (host.isBlank() || host.length > 253) return false
        val ipv4 = host.split('.')
        if (ipv4.size == 4 && ipv4.all { part -> part.all(Char::isDigit) }) {
            return ipv4.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
        }
        return Regex("^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$").matches(host)
    }

    private fun encodeValue(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun decodeValue(value: String): String = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrDefault(value)
}

/** Pure discovery policy so the fast path can be tested without Android sockets. */
object PcDiscoveryPolicy {
    const val DEFAULT_TCP_PORT = 27845
    const val UDP_PORT = 27846
    const val UDP_ROUNDS = 3
    const val UDP_ROUND_LISTEN_MS = 550L
    const val VERIFY_CONNECT_TIMEOUT_MS = 650
    const val VERIFY_READ_TIMEOUT_MS = 1000
    const val SAVED_CONNECT_TIMEOUT_MS = 900
    const val SAVED_READ_TIMEOUT_MS = 1400
    const val PRIORITY_CONNECT_TIMEOUT_MS = 350
    const val PRIORITY_READ_TIMEOUT_MS = 700
    const val FULL_SCAN_CONNECT_TIMEOUT_MS = 220
    const val FULL_SCAN_READ_TIMEOUT_MS = 500

    fun broadcastAddresses(localSubnets: List<String>): List<String> = buildList {
        add("255.255.255.255")
        localSubnets.distinct().forEach { subnet ->
            if (Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$").matches(subnet)) add("$subnet.255")
        }
    }.distinct()

    fun candidateSubnets(localSubnets: List<String>, lastHost: String?): List<String> {
        val lastSubnet = lastHost
            ?.takeIf { it.count { ch -> ch == '.' } == 3 }
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
        val known = buildList {
            addAll(localSubnets)
            lastSubnet?.let(::add)
        }.filter { it.isNotBlank() }.distinct()
        return if (known.isNotEmpty()) known else listOf("192.168.1", "192.168.10", "192.168.0")
    }
}

/** Small, dependency-free SharedPreferences payload. */
object PcConnectionHistoryCodec {
    private const val MAX_ENTRIES = 10

    fun encode(entries: List<PcHistoryEntry>): String = entries
        .sortedByDescending { it.lastSeen }
        .distinctBy { "${it.host}:${it.port}" }
        .take(MAX_ENTRIES)
        .joinToString("\n") { entry ->
            listOf(
                encodeValue(entry.host),
                entry.port.toString(),
                if (entry.authRequired) "1" else "0",
                entry.lastSeen.toString(),
                encodeValue(entry.name)
            ).joinToString("|")
        }

    fun decode(raw: String?): List<PcHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 5)
            if (parts.size != 5) return@mapNotNull null
            val host = decodeValue(parts[0]).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val port = parts[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return@mapNotNull null
            val lastSeen = parts[3].toLongOrNull() ?: return@mapNotNull null
            val name = decodeValue(parts[4]).ifBlank { "DOUPAD PC" }
            PcHistoryEntry(host, port, name, parts[2] == "1", lastSeen)
        }.sortedByDescending { it.lastSeen }.take(MAX_ENTRIES).toList()
    }

    private fun encodeValue(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun decodeValue(value: String): String = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrDefault(value)
}

object PcReconnectPolicy {
    val delaysMs = listOf(500L, 1000L, 2000L, 4000L, 8000L)
    const val HEARTBEAT_INTERVAL_MS = 5000L
}
