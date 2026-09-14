package verification

import com.unipoint.core.network.PcConnectionHistoryCodec
import com.unipoint.core.network.PcDiscoveryPolicy
import com.unipoint.core.network.PcHistoryEntry
import com.unipoint.core.network.PcPairingUri
import com.unipoint.core.network.PcHostIdentity
import com.unipoint.core.network.PcReconnectPolicy
import com.unipoint.core.network.PcDiscoveryProtocol

fun main() {
    val endpoint = PcPairingUri.parse("doupad://pc/192.168.1.44:27845?name=Office%20PC&pin=4321")
        ?: error("DOUPAD URI must parse")
    check(endpoint.host == "192.168.1.44")
    check(endpoint.port == 27845)
    check(endpoint.pin == "4321")
    check(endpoint.name == "Office PC")

    val legacy = PcPairingUri.parse("unipoint://pc/192.168.10.20:27845?pin=1234")
        ?: error("legacy URI must remain compatible")
    check(legacy.host == "192.168.10.20")
    check(legacy.pin == "1234")

    val manual = PcPairingUri.parse("192.168.50.7:27845") ?: error("manual host:port must parse")
    check(manual.host == "192.168.50.7")
    check(manual.port == 27845)
    check(PcPairingUri.parse("192.168.1.3:70000") == null)
    check(PcPairingUri.parse("999.999.999.999:27845") == null)
    check(PcPairingUri.parse("not a host") == null)

    val qr = PcPairingUri.encode(endpoint)
    check(qr.startsWith("doupad://pc/192.168.1.44:27845?"))
    check("pin=4321" in qr)

    val history = listOf(
        PcHistoryEntry("192.168.1.44", 27845, "Office PC", true, 111L),
        PcHistoryEntry("192.168.10.5", 27845, "Living Room | PC", false, 222L)
    )
    val decoded = PcConnectionHistoryCodec.decode(PcConnectionHistoryCodec.encode(history))
    check(decoded == history.sortedByDescending { it.lastSeen })

    val broadcasts = PcDiscoveryPolicy.broadcastAddresses(listOf("192.168.1", "192.168.10"))
    check("255.255.255.255" in broadcasts)
    check("192.168.1.255" in broadcasts)
    check("192.168.10.255" in broadcasts)

    val candidates = PcDiscoveryPolicy.candidateSubnets(
        localSubnets = listOf("192.168.50"),
        lastHost = "192.168.1.99"
    )
    check(candidates.take(2) == listOf("192.168.50", "192.168.1"))
    check("192.168.10" !in candidates) { "common defaults should not waste time when LAN subnet is known" }

    check(PcDiscoveryPolicy.UDP_ROUNDS >= 2)
    check(PcDiscoveryPolicy.VERIFY_CONNECT_TIMEOUT_MS >= 500)
    check(PcDiscoveryPolicy.VERIFY_READ_TIMEOUT_MS >= 800)

    check(PcDiscoveryProtocol.REQUEST == "DOUPAD_DISCOVER/3")
    check("UNIPOINT_DISCOVER/2" in PcDiscoveryProtocol.REQUESTS)
    check(PcDiscoveryProtocol.parse("DOUPAD_HOST/3|Office PC|27845|AUTH=1")?.authRequired == true)
    check(PcDiscoveryProtocol.parse("UNIPOINT_HOST/2|Old PC|27845|AUTH=0")?.name == "Old PC")

    val identity = PcHostIdentity.parse("DOUPAD/3;AUTH=1;NAME=Office%20PC") ?: error("identity")
    check(identity.authRequired)
    check(identity.name == "Office PC")
    check(PcHostIdentity.parse("UNIPOINT/2;AUTH=0")?.name == null)

    check(PcReconnectPolicy.delaysMs == listOf(500L, 1000L, 2000L, 4000L, 8000L))
    check(PcReconnectPolicy.HEARTBEAT_INTERVAL_MS >= 4000L)

    println("PC_CONNECTIVITY_SMOKE_OK")
}
