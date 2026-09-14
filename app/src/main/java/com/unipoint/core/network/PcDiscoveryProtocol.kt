package com.unipoint.core.network

data class PcDiscoveryAnnouncement(
    val name: String,
    val port: Int,
    val authRequired: Boolean
)

object PcDiscoveryProtocol {
    const val UDP_PORT = PcDiscoveryPolicy.UDP_PORT
    const val REQUEST = "DOUPAD_DISCOVER/3"
    const val LEGACY_REQUEST = "UNIPOINT_DISCOVER/2"
    val REQUESTS: List<String> = listOf(REQUEST, LEGACY_REQUEST)

    private const val PREFIX = "DOUPAD_HOST/3|"
    private const val LEGACY_PREFIX = "UNIPOINT_HOST/2|"

    fun parse(raw: String): PcDiscoveryAnnouncement? {
        val text = raw.trim()
        val prefix = when {
            text.startsWith(PREFIX) -> PREFIX
            text.startsWith(LEGACY_PREFIX) -> LEGACY_PREFIX
            else -> return null
        }
        val parts = text.removePrefix(prefix).split('|')
        if (parts.size < 3) return null
        val name = parts[0].trim().ifBlank { "DOUPAD PC" }
        val port = parts[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val auth = parts[2].substringAfter("AUTH=", "0") == "1"
        return PcDiscoveryAnnouncement(name, port, auth)
    }
}
