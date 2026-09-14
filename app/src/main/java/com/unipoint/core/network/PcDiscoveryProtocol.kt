package com.unipoint.core.network

data class PcDiscoveryAnnouncement(
    val name: String,
    val port: Int,
    val authRequired: Boolean
)

object PcDiscoveryProtocol {
    const val UDP_PORT = 27846
    const val REQUEST = "UNIPOINT_DISCOVER/2"
    private const val PREFIX = "UNIPOINT_HOST/2|"

    fun parse(raw: String): PcDiscoveryAnnouncement? {
        val text = raw.trim()
        if (!text.startsWith(PREFIX)) return null
        val parts = text.split('|')
        if (parts.size < 4) return null
        val port = parts[2].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val name = parts[1].trim().ifBlank { "UniPoint PC" }
        val auth = parts[3].substringAfter("AUTH=", "0") == "1"
        return PcDiscoveryAnnouncement(name, port, auth)
    }
}
