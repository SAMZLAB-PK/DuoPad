import com.unipoint.core.network.PcDiscoveryProtocol

fun main() {
    val a = PcDiscoveryProtocol.parse("UNIPOINT_HOST/2|DESKTOP-ONE|27845|AUTH=1")
    check(a != null)
    check(a!!.name == "DESKTOP-ONE")
    check(a.port == 27845)
    check(a.authRequired)
    check(PcDiscoveryProtocol.parse("garbage") == null)
    println("PcDiscoveryProtocolTest: PASS")
}
