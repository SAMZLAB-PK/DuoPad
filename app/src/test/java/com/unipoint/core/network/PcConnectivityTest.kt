package com.unipoint.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PcConnectivityTest {
    @Test
    fun pairingUriSupportsDoupadLegacyAndManualEndpoints() {
        val current = PcPairingUri.parse("doupad://pc/192.168.1.44:27845?name=Office%20PC&pin=4321")!!
        assertEquals("192.168.1.44", current.host)
        assertEquals("Office PC", current.name)
        assertEquals("4321", current.pin)
        assertEquals("192.168.10.20", PcPairingUri.parse("unipoint://pc/192.168.10.20:27845")!!.host)
        assertEquals(27845, PcPairingUri.parse("192.168.50.7")!!.port)
        assertNull(PcPairingUri.parse("192.168.1.3:70000"))
        assertNull(PcPairingUri.parse("999.999.999.999:27845"))
    }

    @Test
    fun historyRoundTripsNamesAndAuthState() {
        val input = listOf(PcHistoryEntry("192.168.1.4", 27845, "Living Room | PC", true, 123L))
        assertEquals(input.sortedByDescending { it.lastSeen }, PcConnectionHistoryCodec.decode(PcConnectionHistoryCodec.encode(input)))
    }

    @Test
    fun discoveryUsesDirectedBroadcastAndAvoidsUnrelatedDefaultSubnets() {
        val targets = PcDiscoveryPolicy.broadcastAddresses(listOf("192.168.50"))
        assertTrue("255.255.255.255" in targets)
        assertTrue("192.168.50.255" in targets)
        assertEquals(
            listOf("192.168.50", "192.168.1"),
            PcDiscoveryPolicy.candidateSubnets(listOf("192.168.50"), "192.168.1.99")
        )
        assertTrue(PcDiscoveryPolicy.VERIFY_CONNECT_TIMEOUT_MS >= 500)
        assertTrue(PcDiscoveryPolicy.VERIFY_READ_TIMEOUT_MS >= 800)
    }
    @Test
    fun discoveryProtocolIsBrandedAndBackwardCompatible() {
        assertEquals("DOUPAD_DISCOVER/3", PcDiscoveryProtocol.REQUEST)
        assertTrue("UNIPOINT_DISCOVER/2" in PcDiscoveryProtocol.REQUESTS)
        assertEquals(true, PcDiscoveryProtocol.parse("DOUPAD_HOST/3|Office PC|27845|AUTH=1")?.authRequired)
        assertEquals("Old PC", PcDiscoveryProtocol.parse("UNIPOINT_HOST/2|Old PC|27845|AUTH=0")?.name)
    }

    @Test
    fun hostIdentitySupportsCurrentAndLegacySignatures() {
        val current = PcHostIdentity.parse("DOUPAD/3;AUTH=1;NAME=Office%20PC")!!
        assertEquals(true, current.authRequired)
        assertEquals("Office PC", current.name)
        assertEquals(false, PcHostIdentity.parse("UNIPOINT/2;AUTH=0")!!.authRequired)
    }

}
