"""End-to-end socket protocol regression for PC Workspace.
Simulates Android client's little-endian packet framing and raw file payloads.
Does not require a display/Windows to verify metadata and file transfer.
"""
import importlib.util
import json
import socket
import struct
import tempfile
import threading
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("duopad_host_workspace_test", ROOT / "unipoint_host.py")
host = importlib.util.module_from_spec(spec)
spec.loader.exec_module(host)


class NoInput:
    def mouse_move(self, *args): pass
    def mouse_button(self, *args): pass
    def mouse_scroll(self, *args): pass
    def key_event(self, *args): pass
    def type_text(self, *args): pass


def request(sock, typ, value=None):
    payload = json.dumps(value or {}).encode() if typ not in (0x10, 0x20) else (value or b"")
    host.send_packet(sock, typ, payload)
    header = host.read_exact(sock, 3)
    if not header:
        return None
    got_type, size = struct.unpack("<BH", header)
    assert got_type == typ, (got_type, typ)
    answer = host.read_exact(sock, size)
    if typ in (0x10, 0x20):
        return answer
    return json.loads(answer.decode("utf-8"))


class PcWorkspaceProtocolTest(unittest.TestCase):
    def setUp(self):
        self.client, self.server = socket.socketpair()
        self.client.settimeout(5)
        self.handler = host.ClientHandler(self.server, ("127.0.0.1", 50100), NoInput(), "secure-pin-456")
        self.handler.start()

    def tearDown(self):
        self.client.close()
        self.handler.join(timeout=1)

    def authorize(self):
        self.assertEqual(request(self.client, 0x10, b"secure-pin-456"), b"OK")

    def test_auth_required_for_drives_and_info(self):
        host.send_packet(self.client, 0x30, b"{}")
        self.client.settimeout(.2)
        with self.assertRaises(socket.timeout):
            self.client.recv(3)
        self.client.settimeout(5)
        self.authorize()
        identity = request(self.client, 0x20)
        self.assertIn(b"AUTH=1", identity)
        result = request(self.client, 0x30)
        self.assertTrue(result["ok"])
        self.assertIn("name", result["info"])
        self.assertIn("installedSoftware", result["info"])
        self.assertIsInstance(result["info"]["installedSoftware"], list)
        self.assertIn("cpuModel", result["info"])
        self.assertIn("ramModules", result["info"])
        drives = request(self.client, 0x31)
        self.assertTrue(drives["ok"])
        self.assertTrue(len(drives["drives"]) > 0)

    def test_directory_download_and_upload(self):
        self.authorize()
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            test_file = folder / "my-data.bin"
            test_file.write_bytes(b"DuoPad PC test content\x00" * 500)
            response = request(self.client, 0x32, {"path": directory})
            self.assertTrue(response["ok"])
            self.assertIn("my-data.bin", [e["name"] for e in response["entries"]])
            self.assertFalse(request(self.client, 0x32, {"path": str(folder / "missing")})["ok"])
            dl = request(self.client, 0x33, {"path": str(test_file)})
            self.assertEqual(dl["size"], test_file.stat().st_size)
            self.assertEqual(host.read_exact(self.client, dl["size"]), test_file.read_bytes())
            content = b"upload to PC via DuoPad\x01" * 150
            ack = request(self.client, 0x34, {"folder": directory, "name": "uploaded.txt", "size": len(content)})
            self.assertTrue(ack["ready"])
            self.client.sendall(content)
            header = host.read_exact(self.client, 3)
            self.assertEqual(struct.unpack("<BH", header)[0], 0x34)
            length = struct.unpack("<BH", header)[1]
            saved = json.loads(host.read_exact(self.client, length))
            self.assertTrue(saved["ok"])
            self.assertEqual(Path(saved["path"]).read_bytes(), content)
            self.assertFalse(request(self.client, 0x34,
                {"folder": directory, "name": "../escape.txt", "size": 1})["ok"])

    def test_authenticated_phone_to_pc_mirror_frame(self):
        from pc_workspace import PhoneMirrorFrames
        # A phone must authenticate before it can publish a frame to the Host viewer.
        self.authorize()
        jpeg = bytes.fromhex("ffd8ffe000104a46494600010100000100010000ffda") + b"test" + bytes.fromhex("ffd9")
        metadata = {"size": len(jpeg), "width": 360, "height": 720, "name": "Android Q5"}
        host.send_packet(self.client, 0x50, json.dumps(metadata).encode("utf-8"))
        self.client.sendall(jpeg)
        answer_header = host.read_exact(self.client, 3)
        response_type, response_len = struct.unpack("<BH", answer_header)
        self.assertEqual(response_type, 0x50)
        result = json.loads(host.read_exact(self.client, response_len))
        self.assertTrue(result["ok"])
        image, width, height, seen, name = PhoneMirrorFrames.snapshot()
        self.assertEqual(image, jpeg)
        self.assertEqual((width,height), (360,720))
        self.assertEqual(name, "Android Q5")
        self.assertGreater(seen,0)

    def test_oversized_phone_frame_is_rejected_before_reading_payload(self):
        self.authorize()
        answer = request(self.client, 0x50,
            {"size": 10000000, "width": 1080, "height": 1920})
        self.assertFalse(answer["ok"])

    def test_preview_missing_dependencies_reports_error_without_crash(self):
        self.authorize()
        result = request(self.client, 0x40, {"width": 640, "quality": 50})
        # Linux CI/headless: desktop capture may be absent; must respond cleanly.
        if not result["ok"]:
            self.assertTrue(result.get("error"))
        else:
            self.assertIn("size", result)
            self.assertEqual(len(host.read_exact(self.client, result["size"])), result["size"])


if __name__ == "__main__":
    unittest.main()
