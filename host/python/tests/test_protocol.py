import importlib.util
import socket
import struct
import threading
import unittest
from pathlib import Path

HOST_PATH = Path(__file__).resolve().parents[1] / "unipoint_host.py"
spec = importlib.util.spec_from_file_location("unipoint_host", HOST_PATH)
host = importlib.util.module_from_spec(spec)
spec.loader.exec_module(host)

class DummyInjector:
    def mouse_move(self, dx, dy): pass
    def mouse_button(self, button, down): pass
    def mouse_scroll(self, dy): pass
    def key_event(self, keycode, down, mods): pass
    def type_text(self, text): pass


def recv_packet(sock):
    header = host.read_exact(sock, 3)
    if header is None:
        return None
    ptype, length = struct.unpack("<BH", header)
    payload = host.read_exact(sock, length) if length else b""
    return ptype, payload


class ProtocolTest(unittest.TestCase):
    def make_pair(self, pin=None):
        client, server = socket.socketpair()
        handler = host.ClientHandler(server, ("local", 1), DummyInjector(), pin)
        handler.start()
        self.addCleanup(client.close)
        return client, handler

    def test_identity_payload_includes_pc_name_without_breaking_legacy_signature(self):
        payload = host.build_identity_payload("Office PC", "1234")
        self.assertEqual(payload, b"UNIPOINT/2;AUTH=1;NAME=Office%20PC")

    def test_ping_returns_unipoint_signature(self):
        client, handler = self.make_pair()
        host.send_packet(client, 0x20)
        packet = recv_packet(client)
        self.assertEqual(packet[0], 0x20)
        self.assertTrue(packet[1].startswith(b"UNIPOINT/2;AUTH=0;NAME="))

    def test_secured_host_identifies_itself_before_auth(self):
        client, handler = self.make_pair(pin="1234")
        host.send_packet(client, 0x20)
        packet = recv_packet(client)
        self.assertEqual(packet[0], 0x20)
        self.assertTrue(packet[1].startswith(b"UNIPOINT/2;AUTH=1;NAME="))

    def test_pin_auth_then_ping(self):
        client, handler = self.make_pair(pin="1234")
        host.send_packet(client, 0x10, b"1234")
        self.assertEqual(recv_packet(client), (0x10, b"OK"))
        host.send_packet(client, 0x20)
        packet = recv_packet(client)
        self.assertEqual(packet[0], 0x20)
        self.assertTrue(packet[1].startswith(b"UNIPOINT/2;AUTH=1;NAME="))

    def test_discovery_payload_announces_host_port_and_auth(self):
        payload = host.build_discovery_response("DESKTOP-TEST", 27845, pin="1234")
        self.assertEqual(payload, b"DOUPAD_HOST/3|DESKTOP-TEST|27845|AUTH=1")

    def test_discovery_payload_without_pin_reports_auth_zero(self):
        payload = host.build_discovery_response("PC", 27845, pin=None)
        self.assertEqual(payload, b"DOUPAD_HOST/3|PC|27845|AUTH=0")


    def test_public_discovery_protocol_uses_doupad_brand_and_keeps_legacy_compatibility(self):
        self.assertEqual(host.DISCOVERY_REQUEST, b"DOUPAD_DISCOVER/3")
        self.assertIn(b"UNIPOINT_DISCOVER/2", host.DISCOVERY_REQUESTS)
        payload = host.build_discovery_response("DESKTOP-TEST", 27845, pin="1234")
        self.assertEqual(payload, b"DOUPAD_HOST/3|DESKTOP-TEST|27845|AUTH=1")

    def test_pairing_uri_contains_host_name_and_optional_pin(self):
        uri = host.build_pairing_uri("192.168.1.44", 27845, "Office PC", "43 21")
        self.assertEqual(uri, "doupad://pc/192.168.1.44:27845?name=Office%20PC&pin=43%2021")
        open_uri = host.build_pairing_uri("192.168.1.44", 27845, "Office PC", None)
        self.assertEqual(open_uri, "doupad://pc/192.168.1.44:27845?name=Office%20PC")

    def test_lan_ip_selection_filters_loopback_apipa_and_public_addresses(self):
        ips = host.select_lan_ipv4s(["127.0.0.1", "169.254.3.4", "8.8.8.8", "10.0.0.8", "192.168.1.22"])
        self.assertEqual(ips, ["192.168.1.22", "10.0.0.8"])

    def test_legacy_discovery_request_gets_legacy_response(self):
        probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        probe.bind(("127.0.0.1", 0))
        probe.settimeout(1.0)
        server = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        server.bind(("127.0.0.1", 0))
        port = server.getsockname()[1]
        server.close()
        responder = host.DiscoveryResponder(27845, None, udp_port=port)
        responder.start()
        try:
            self.assertTrue(responder.ready_event.wait(1.0))
            self.assertIsNone(responder.error)
            probe.sendto(host.LEGACY_DISCOVERY_REQUEST, ("127.0.0.1", port))
            data, _ = probe.recvfrom(512)
            self.assertTrue(data.startswith(b"UNIPOINT_HOST/2|"))
        finally:
            responder.stop()
            probe.close()

    def test_udp_discovery_responder_replies(self):
        probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        probe.bind(("127.0.0.1", 0))
        probe.settimeout(1.0)
        responder = host.DiscoveryResponder(27845, None, udp_port=0)
        # Bind an ephemeral port ourselves so we can learn it deterministically.
        server = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        server.bind(("127.0.0.1", 0))
        port = server.getsockname()[1]
        server.close()
        responder = host.DiscoveryResponder(27845, None, udp_port=port)
        responder.start()
        try:
            self.assertTrue(responder.ready_event.wait(1.0))
            self.assertIsNone(responder.error)
            probe.sendto(host.DISCOVERY_REQUEST, ("127.0.0.1", port))
            data, _ = probe.recvfrom(512)
            self.assertTrue(data.startswith(b"DOUPAD_HOST/3|"))
            self.assertIn(b"|27845|AUTH=0", data)
        finally:
            responder.stop()
            probe.close()

if __name__ == "__main__":
    unittest.main()
