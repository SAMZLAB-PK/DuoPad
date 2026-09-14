#!/usr/bin/env python3
"""
DOUPAD Host – Official companion for Network mode
===================================================
Works on Windows, Linux and macOS.

Protocol (little-endian binary):
  [1 byte type] [2 bytes length] [payload]

Types:
  0x01  Mouse Move     payload: dx:int16, dy:int16
  0x02  Mouse Button   payload: button:uint8 (1=L 2=R 3=M), down:uint8
  0x03  Mouse Scroll   payload: dy:int16
  0x04  Key            payload: keycode:uint16, down:uint8, mods:uint8
  0x05  Text           payload: utf-8 string
  0x10  Auth PIN       payload: utf-8 pin
  0x20  Ping
  0x21  Clipboard      payload: utf-8 text

Usage:
  python unipoint_host.py [--port 27845] [--pin 1234]
"""

from __future__ import annotations

import argparse
import socket
import struct
import sys
import threading
import time
import ipaddress
from urllib.parse import quote
from typing import Iterable, Optional

# ---------------------------------------------------------------------------
# Platform-specific input injection
# ---------------------------------------------------------------------------

IS_WINDOWS = sys.platform.startswith("win")
HAS_PYNPUT = False
if not IS_WINDOWS:
    try:
        from pynput.mouse import Button, Controller as MouseController
        from pynput.keyboard import Key, Controller as KeyboardController, KeyCode
        HAS_PYNPUT = True
    except ImportError:
        print("[!] pynput is optional on macOS/Linux. Install with: pip install pynput")

HID_TO_PYNPUT = {}
if HAS_PYNPUT:
    HID_TO_PYNPUT = {
        0x04: KeyCode.from_char('a'), 0x05: KeyCode.from_char('b'),
        0x06: KeyCode.from_char('c'), 0x07: KeyCode.from_char('d'),
        0x08: KeyCode.from_char('e'), 0x09: KeyCode.from_char('f'),
        0x0A: KeyCode.from_char('g'), 0x0B: KeyCode.from_char('h'),
        0x0C: KeyCode.from_char('i'), 0x0D: KeyCode.from_char('j'),
        0x0E: KeyCode.from_char('k'), 0x0F: KeyCode.from_char('l'),
        0x10: KeyCode.from_char('m'), 0x11: KeyCode.from_char('n'),
        0x12: KeyCode.from_char('o'), 0x13: KeyCode.from_char('p'),
        0x14: KeyCode.from_char('q'), 0x15: KeyCode.from_char('r'),
        0x16: KeyCode.from_char('s'), 0x17: KeyCode.from_char('t'),
        0x18: KeyCode.from_char('u'), 0x19: KeyCode.from_char('v'),
        0x1A: KeyCode.from_char('w'), 0x1B: KeyCode.from_char('x'),
        0x1C: KeyCode.from_char('y'), 0x1D: KeyCode.from_char('z'),
        0x1E: KeyCode.from_char('1'), 0x1F: KeyCode.from_char('2'),
        0x20: KeyCode.from_char('3'), 0x21: KeyCode.from_char('4'),
        0x22: KeyCode.from_char('5'), 0x23: KeyCode.from_char('6'),
        0x24: KeyCode.from_char('7'), 0x25: KeyCode.from_char('8'),
        0x26: KeyCode.from_char('9'), 0x27: KeyCode.from_char('0'),
        0x28: Key.enter, 0x29: Key.esc, 0x2A: Key.backspace,
        0x2B: Key.tab, 0x2C: Key.space,
        0x4A: Key.home, 0x4B: Key.page_up, 0x4C: Key.delete,
        0x4D: Key.end, 0x4E: Key.page_down,
        0x4F: Key.right, 0x50: Key.left, 0x51: Key.down, 0x52: Key.up,
        0xE0: Key.ctrl_l, 0xE1: Key.shift_l, 0xE2: Key.alt_l, 0xE3: Key.cmd,
    }


class WindowsNativeInput:
    """Dependency-free Windows input injection using user32."""
    MOUSEEVENTF_MOVE = 0x0001
    MOUSEEVENTF_LEFTDOWN = 0x0002
    MOUSEEVENTF_LEFTUP = 0x0004
    MOUSEEVENTF_RIGHTDOWN = 0x0008
    MOUSEEVENTF_RIGHTUP = 0x0010
    MOUSEEVENTF_MIDDLEDOWN = 0x0020
    MOUSEEVENTF_MIDDLEUP = 0x0040
    MOUSEEVENTF_WHEEL = 0x0800
    KEYEVENTF_KEYUP = 0x0002

    SPECIAL_VK = {
        0x28: 0x0D,  # Enter
        0x29: 0x1B,  # Esc
        0x2A: 0x08,  # Backspace
        0x2B: 0x09,  # Tab
        0x2C: 0x20,  # Space
        0x39: 0x14,  # Caps lock
        0x3A: 0x70, 0x3B: 0x71, 0x3C: 0x72, 0x3D: 0x73,
        0x3E: 0x74, 0x3F: 0x75, 0x40: 0x76, 0x41: 0x77,
        0x42: 0x78, 0x43: 0x79, 0x44: 0x7A, 0x45: 0x7B,
        0x4A: 0x24, 0x4B: 0x21, 0x4C: 0x2E, 0x4D: 0x23, 0x4E: 0x22,
        0x4F: 0x27, 0x50: 0x25, 0x51: 0x28, 0x52: 0x26,
        0xE0: 0xA2, 0xE1: 0xA0, 0xE2: 0xA4, 0xE3: 0x5B,
    }

    def __init__(self):
        import ctypes
        self.ctypes = ctypes
        self.user32 = ctypes.windll.user32

    @staticmethod
    def hid_to_vk(keycode: int) -> Optional[int]:
        if 0x04 <= keycode <= 0x1D:
            return ord('A') + (keycode - 0x04)
        if 0x1E <= keycode <= 0x26:
            return ord('1') + (keycode - 0x1E)
        if keycode == 0x27:
            return ord('0')
        return WindowsNativeInput.SPECIAL_VK.get(keycode)

    def mouse_move(self, dx: int, dy: int):
        self.user32.mouse_event(self.MOUSEEVENTF_MOVE, int(dx), int(dy), 0, 0)

    def mouse_button(self, button: int, down: bool):
        flags = {
            (1, True): self.MOUSEEVENTF_LEFTDOWN, (1, False): self.MOUSEEVENTF_LEFTUP,
            (2, True): self.MOUSEEVENTF_RIGHTDOWN, (2, False): self.MOUSEEVENTF_RIGHTUP,
            (3, True): self.MOUSEEVENTF_MIDDLEDOWN, (3, False): self.MOUSEEVENTF_MIDDLEUP,
        }.get((button, down))
        if flags:
            self.user32.mouse_event(flags, 0, 0, 0, 0)

    def mouse_scroll(self, dy: int):
        self.user32.mouse_event(self.MOUSEEVENTF_WHEEL, 0, 0, int(dy) * 120, 0)

    def key_event(self, keycode: int, down: bool, mods: int):
        vk = self.hid_to_vk(keycode)
        if vk is not None:
            self.user32.keybd_event(vk, 0, 0 if down else self.KEYEVENTF_KEYUP, 0)

    def type_text(self, text: str):
        # VkKeyScanW covers normal keyboard text and automatically tells us
        # which modifiers are required for a character.
        for ch in text:
            packed = self.user32.VkKeyScanW(ord(ch))
            if packed == -1:
                continue
            vk = packed & 0xFF
            shift_state = (packed >> 8) & 0xFF
            modifiers = []
            if shift_state & 1: modifiers.append(0x10)  # Shift
            if shift_state & 2: modifiers.append(0x11)  # Ctrl
            if shift_state & 4: modifiers.append(0x12)  # Alt
            for mod in modifiers: self.user32.keybd_event(mod, 0, 0, 0)
            self.user32.keybd_event(vk, 0, 0, 0)
            self.user32.keybd_event(vk, 0, self.KEYEVENTF_KEYUP, 0)
            for mod in reversed(modifiers): self.user32.keybd_event(mod, 0, self.KEYEVENTF_KEYUP, 0)


class InputInjector:
    def __init__(self):
        self.native = WindowsNativeInput() if IS_WINDOWS else None
        if HAS_PYNPUT:
            self.mouse = MouseController()
            self.keyboard = KeyboardController()
        else:
            self.mouse = self.keyboard = None
        if IS_WINDOWS:
            print("[+] Windows native input backend active (no pip dependency)")

    def mouse_move(self, dx: int, dy: int):
        if self.native:
            self.native.mouse_move(dx, dy)
        elif self.mouse:
            self.mouse.move(dx, dy)

    def mouse_button(self, button: int, down: bool):
        if self.native:
            self.native.mouse_button(button, down)
            return
        if not self.mouse:
            return
        btn = {1: Button.left, 2: Button.right, 3: Button.middle}.get(button)
        if btn is None:
            return
        self.mouse.press(btn) if down else self.mouse.release(btn)

    def mouse_scroll(self, dy: int):
        if self.native:
            self.native.mouse_scroll(dy)
        elif self.mouse:
            self.mouse.scroll(0, dy)

    def key_event(self, keycode: int, down: bool, mods: int):
        if self.native:
            self.native.key_event(keycode, down, mods)
            return
        if not self.keyboard:
            return
        key = HID_TO_PYNPUT.get(keycode)
        if key is None:
            return
        try:
            self.keyboard.press(key) if down else self.keyboard.release(key)
        except Exception:
            pass

    def type_text(self, text: str):
        if self.native:
            self.native.type_text(text)
        elif self.keyboard:
            self.keyboard.type(text)


# ---------------------------------------------------------------------------
# Protocol helpers
# ---------------------------------------------------------------------------

def read_exact(sock: socket.socket, n: int) -> Optional[bytes]:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


def send_packet(sock: socket.socket, ptype: int, payload: bytes = b""):
    header = struct.pack("<BH", ptype, len(payload))
    sock.sendall(header + payload)


DISCOVERY_PORT = 27846
DISCOVERY_REQUEST = b"DOUPAD_DISCOVER/3"
LEGACY_DISCOVERY_REQUEST = b"UNIPOINT_DISCOVER/2"
DISCOVERY_REQUESTS = {DISCOVERY_REQUEST, LEGACY_DISCOVERY_REQUEST}


def build_identity_payload(hostname: str, pin: Optional[str]) -> bytes:
    auth = "1" if pin else "0"
    safe_name = quote(hostname or "DOUPAD PC", safe="")
    # Keep the legacy signature prefix so older beta clients still accept this host.
    return f"UNIPOINT/2;AUTH={auth};NAME={safe_name}".encode("utf-8")


def build_discovery_response(hostname: str, tcp_port: int, pin: Optional[str], legacy: bool = False) -> bytes:
    safe_name = (hostname or "DOUPAD PC").replace("|", "-")[:64]
    auth = "1" if pin else "0"
    prefix = "UNIPOINT_HOST/2" if legacy else "DOUPAD_HOST/3"
    return f"{prefix}|{safe_name}|{tcp_port}|AUTH={auth}".encode("utf-8")


def build_pairing_uri(host: str, tcp_port: int, hostname: str, pin: Optional[str]) -> str:
    query = [f"name={quote(hostname or 'DOUPAD PC', safe='')}"]
    if pin:
        query.append(f"pin={quote(pin, safe='')}")
    return f"doupad://pc/{host}:{tcp_port}?" + "&".join(query)


def select_lan_ipv4s(candidates: Iterable[str]):
    result = []
    for raw in candidates:
        try:
            ip = ipaddress.ip_address(raw)
        except ValueError:
            continue
        if ip.version != 4 or not ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_unspecified or ip.is_multicast:
            continue
        text = str(ip)
        if text not in result:
            result.append(text)

    def rank(value: str):
        if value.startswith("192.168."):
            return (0, value)
        if value.startswith("172."):
            return (1, value)
        if value.startswith("10."):
            return (2, value)
        return (3, value)

    return sorted(result, key=rank)


def get_lan_ipv4s():
    candidates = []
    try:
        for item in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET, socket.SOCK_STREAM):
            candidates.append(item[4][0])
    except OSError:
        pass
    # UDP connect selects the OS route without sending application data.
    try:
        probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        probe.connect(("8.8.8.8", 80))
        candidates.append(probe.getsockname()[0])
        probe.close()
    except OSError:
        pass
    return select_lan_ipv4s(candidates)


def print_pairing_qr(uri: str):
    try:
        import qrcode
        qr = qrcode.QRCode(border=1)
        qr.add_data(uri)
        qr.make(fit=True)
        print("\n  Scan this QR in DOUPAD:")
        qr.print_ascii(invert=True)
    except Exception:
        # QR is a convenience; the pairing URI remains a reliable fallback.
        pass


class DiscoveryResponder(threading.Thread):
    """Small UDP responder used by the Android client for instant LAN discovery."""

    def __init__(self, tcp_port: int, pin: Optional[str], udp_port: int = DISCOVERY_PORT):
        super().__init__(daemon=True)
        self.tcp_port = tcp_port
        self.pin = pin
        self.udp_port = udp_port
        self._stop_event = threading.Event()
        self.ready_event = threading.Event()
        self.error: Optional[str] = None
        self.sock: Optional[socket.socket] = None

    def stop(self):
        self._stop_event.set()
        if self.sock:
            try:
                self.sock.close()
            except OSError:
                pass

    def run(self):
        hostname = socket.gethostname() or "DOUPAD PC"
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock = sock
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            try:
                sock.bind(("0.0.0.0", self.udp_port))
            except OSError as exc:
                self.error = str(exc)
                self.ready_event.set()
                print(f"[!] UDP discovery unavailable on {self.udp_port}: {exc}")
                return
            sock.settimeout(0.5)
            self.ready_event.set()
            print(f"[+] UDP discovery listening on 0.0.0.0:{self.udp_port}")
            while not self._stop_event.is_set():
                try:
                    data, addr = sock.recvfrom(512)
                except socket.timeout:
                    continue
                except OSError:
                    break
                request = data.strip()
                if request in DISCOVERY_REQUESTS:
                    try:
                        payload = build_discovery_response(
                            hostname, self.tcp_port, self.pin, legacy=(request == LEGACY_DISCOVERY_REQUEST)
                        )
                        sock.sendto(payload, addr)
                    except OSError:
                        pass
        finally:
            try:
                sock.close()
            except OSError:
                pass


# ---------------------------------------------------------------------------
# Client handler
# ---------------------------------------------------------------------------

class ClientHandler(threading.Thread):
    def __init__(self, conn: socket.socket, addr, injector: InputInjector, pin: Optional[str]):
        super().__init__(daemon=True)
        self.conn = conn
        self.addr = addr
        self.injector = injector
        self.pin = pin
        self.authenticated = pin is None  # no pin → auto auth

    def run(self):
        print(f"[+] Client connected: {self.addr}")
        try:
            while True:
                header = read_exact(self.conn, 3)
                if header is None:
                    break
                ptype, length = struct.unpack("<BH", header)
                payload = read_exact(self.conn, length) if length else b""
                if payload is None and length > 0:
                    break
                self.handle(ptype, payload or b"")
        except Exception as e:
            print(f"[!] Client error {self.addr}: {e}")
        finally:
            self.conn.close()
            print(f"[-] Client disconnected: {self.addr}")

    def handle(self, ptype: int, payload: bytes):
        # Auth
        if ptype == 0x10:
            received_pin = payload.decode("utf-8", errors="ignore")
            if self.pin is None or received_pin == self.pin:
                self.authenticated = True
                send_packet(self.conn, 0x10, b"OK")
                print(f"[*] Authenticated: {self.addr}")
            else:
                send_packet(self.conn, 0x10, b"FAIL")
                print(f"[!] Bad PIN from {self.addr}")
                self.conn.close()
            return

        # Identity ping is allowed before authentication so Android can discover
        # UniPoint hosts without treating arbitrary open TCP ports as PCs.
        if ptype == 0x20:
            send_packet(self.conn, 0x20, build_identity_payload(socket.gethostname() or "DOUPAD PC", self.pin))
            return

        if not self.authenticated:
            return

        if ptype == 0x01 and len(payload) >= 4:          # Mouse Move
            dx, dy = struct.unpack("<hh", payload[:4])
            self.injector.mouse_move(dx, dy)

        elif ptype == 0x02 and len(payload) >= 2:        # Mouse Button
            button, down = payload[0], payload[1]
            self.injector.mouse_button(button, bool(down))

        elif ptype == 0x03 and len(payload) >= 2:        # Scroll
            dy = struct.unpack("<h", payload[:2])[0]
            self.injector.mouse_scroll(dy)

        elif ptype == 0x04 and len(payload) >= 4:        # Key
            keycode, down, mods = struct.unpack("<HBB", payload[:4])
            self.injector.key_event(keycode, bool(down), mods)

        elif ptype == 0x05:                              # Text
            text = payload.decode("utf-8", errors="ignore")
            self.injector.type_text(text)

        elif ptype == 0x21:                              # Clipboard (receive)
            text = payload.decode("utf-8", errors="ignore")
            print(f"[clip] {text[:80]}…")
            # Could set system clipboard here with pyperclip


# ---------------------------------------------------------------------------
# Server
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="DOUPAD Host")
    parser.add_argument("--port", type=int, default=27845)
    parser.add_argument("--pin", type=str, default=None, help="Optional security PIN")
    parser.add_argument("--bind", type=str, default="0.0.0.0")
    args = parser.parse_args()

    injector = InputInjector()
    discovery = DiscoveryResponder(args.port, args.pin)
    discovery.start()
    discovery.ready_event.wait(1.5)

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((args.bind, args.port))
    server.listen(5)

    hostname = socket.gethostname() or "DOUPAD PC"
    lan_ips = get_lan_ipv4s()
    print("=" * 58)
    print("  DOUPAD Host  v3.2")
    print(f"  TCP control:      {args.bind}:{args.port}")
    discovery_state = "READY" if not discovery.error else f"BLOCKED: {discovery.error}"
    print(f"  LAN discovery:    UDP {DISCOVERY_PORT} ({discovery_state})")
    if lan_ips:
        for ip in lan_ips:
            print(f"  Connect address:  {ip}:{args.port}")
    else:
        print("  Connect address:  could not determine LAN IPv4")
    print(f"  PIN protection:   {'enabled' if args.pin else 'off'}")
    print("  Keep this window open while using Network PC mode.")
    print("=" * 58)
    if lan_ips:
        pairing_uri = build_pairing_uri(lan_ips[0], args.port, hostname, args.pin)
        print(f"\n  Pairing URI: {pairing_uri}")
        print_pairing_qr(pairing_uri)

    try:
        while True:
            conn, addr = server.accept()
            conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            ClientHandler(conn, addr, injector, args.pin).start()
    except KeyboardInterrupt:
        print("\n[+] Shutting down")
    finally:
        discovery.stop()
        server.close()


if __name__ == "__main__":
    main()
