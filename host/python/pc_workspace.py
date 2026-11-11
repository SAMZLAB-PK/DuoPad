"""DuoPad PC Workspace v1: authenticated LAN companion protocol.

Transport: existing TCP 27845 packet envelope, [type:uint8][len:uint16 LE][JSON bytes].
Screen or file replies: JSON packet with size, followed by exact raw content bytes.
Each PC Workspace client uses its own authenticated socket. No shell commands,
no file deletion or hidden/background persistence. Do NOT expose this LAN-only
plaintext protocol to the Internet; use a future TLS/pinning transport for WAN.
"""
import ctypes
import io
import json
import os
import platform
import re
import shutil
import socket
import struct
import sys
import tempfile
import time
from pathlib import Path

MAX_FILE_BYTES = 1024 * 1024 * 1024  # explicit cap: 1 GiB per transfer
MAX_FRAME_BYTES = 3 * 1024 * 1024


def _reply(send_packet, conn, typ, value):
    data = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    if len(data) >= 65535:
        raise ValueError("Response metadata too long")
    send_packet(conn, typ, data)


def _valid_path(path):
    if not isinstance(path, str) or not path.strip() or "\x00" in path or len(path) > 4096:
        raise ValueError("Invalid path")
    return Path(path).expanduser().resolve(strict=False)


def _windows_drives():
    if not sys.platform.startswith("win"):
        return [str(Path.home()), "/"]
    bitmask = ctypes.windll.kernel32.GetLogicalDrives()
    return [f"{chr(65+i)}:\\" for i in range(26) if bitmask & (1 << i)]


def _machine_info():
    drives = _windows_drives()
    info = {"name": socket.gethostname(), "os": platform.platform(),
            "architecture": platform.machine(), "cpu": platform.processor() or platform.machine(),
            "cpuThreads": os.cpu_count(), "python": platform.python_version(),
            "user": os.environ.get("USERNAME") or os.environ.get("USER", ""),
            "home": str(Path.home()), "drives": len(drives), "capabilities":
            ["desktop-preview", "mouse-keyboard", "files", "drives", "system-info", "clipboard"]}
    if sys.platform.startswith("win"):
        class MemoryStatus(ctypes.Structure):
            _fields_ = [("dwLength", ctypes.c_ulong), ("dwMemoryLoad", ctypes.c_ulong),
                ("ullTotalPhys", ctypes.c_ulonglong), ("ullAvailPhys", ctypes.c_ulonglong),
                ("ullTotalPageFile", ctypes.c_ulonglong), ("ullAvailPageFile", ctypes.c_ulonglong),
                ("ullTotalVirtual", ctypes.c_ulonglong), ("ullAvailVirtual", ctypes.c_ulonglong),
                ("ullAvailExtendedVirtual", ctypes.c_ulonglong)]
        state = MemoryStatus()
        state.dwLength = ctypes.sizeof(MemoryStatus)
        if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(state)):
            info.update(ramTotal=state.ullTotalPhys, ramAvailable=state.ullAvailPhys)
    return info


def _drive_info():
    data = []
    for path in _windows_drives():
        try:
            usage = shutil.disk_usage(path)
            data.append({"path": path, "free": usage.free, "total": usage.total})
        except OSError:
            data.append({"path": path, "free": -1, "total": -1})
    return data


def _directory(path):
    target = _valid_path(path)
    if not target.is_dir():
        raise NotADirectoryError(str(target))
    entries = []
    for child in sorted(target.iterdir(), key=lambda p: (not p.is_dir(), p.name.casefold())):
        try:
            st = child.stat()
            entries.append({"name": child.name, "path": str(child), "isDir": child.is_dir(),
                            "size": st.st_size if child.is_file() else 0})
        except (PermissionError, OSError):
            continue
        if len(entries) >= 180:
            break
    return {"path": str(target), "parent": str(target.parent), "entries": entries,
            "truncated": len(entries) == 180}


def _make_unique(folder, safe_name):
    stem, suffix = os.path.splitext(safe_name)
    for n in range(10000):
        candidate = folder / (safe_name if n == 0 else f"{stem} ({n}){suffix}")
        if not candidate.exists():
            return candidate
    raise FileExistsError("Too many similarly named files")


class PcWorkspaceService:
    def __init__(self):
        self.capture = None
        self.capture_ctx = None

    def close(self):
        if self.capture_ctx is not None:
            try:
                self.capture_ctx.__exit__(None, None, None)
            except Exception:
                pass
        self.capture = self.capture_ctx = None

    def _frame(self, options):
        try:
            import mss
            from PIL import Image
        except ImportError as exc:
            raise RuntimeError("PC desktop preview requires: py -m pip install mss pillow") from exc
        if self.capture is None:
            self.capture_ctx = mss.mss()
            self.capture = self.capture_ctx.__enter__()
        monitors = self.capture.monitors
        monitor_index = max(1, min(int(options.get("monitor", 1)), len(monitors) - 1))
        shot = self.capture.grab(monitors[monitor_index])
        frame = Image.frombytes("RGB", shot.size, shot.rgb)
        max_width = max(320, min(int(options.get("width", 960)), 1600))
        if frame.width > max_width:
            new_height = max(1, round(frame.height * max_width / frame.width))
            frame = frame.resize((max_width, new_height), Image.Resampling.BILINEAR)
        output = io.BytesIO()
        quality = max(30, min(int(options.get("quality", 58)), 80))
        frame.save(output, "JPEG", quality=quality, optimize=False)
        result = output.getvalue()
        if len(result) > MAX_FRAME_BYTES:
            raise ValueError("Desktop frame exceeds limit")
        return result, frame.width, frame.height

    def handle(self, conn, typ, payload, send_packet, read_exact):
        try:
            req = json.loads(payload.decode("utf-8")) if payload and typ not in (0x21,) else {}
            if typ == 0x30:
                _reply(send_packet, conn, typ, {"ok": True, "info": _machine_info()})
            elif typ == 0x31:
                _reply(send_packet, conn, typ, {"ok": True, "drives": _drive_info()})
            elif typ == 0x32:
                _reply(send_packet, conn, typ, dict(ok=True, **_directory(req["path"])))
            elif typ == 0x33:
                path = _valid_path(req["path"])
                if not path.is_file():
                    raise FileNotFoundError(str(path))
                size = path.stat().st_size
                if size > MAX_FILE_BYTES:
                    raise ValueError("Download exceeds 1 GiB limit")
                _reply(send_packet, conn, typ, {"ok": True, "size": size, "name": path.name})
                with path.open("rb") as source:
                    while True:
                        chunk = source.read(65536)
                        if not chunk: break
                        conn.sendall(chunk)
            elif typ == 0x34:
                folder = _valid_path(req["folder"])
                if not folder.is_dir():
                    raise NotADirectoryError(str(folder))
                # Accept filename only; never honor a traversal component.
                raw_name = req.get("name", "")
                name = Path(raw_name).name
                if name in ("", ".", "..") or name != raw_name or "\\" in raw_name:
                    raise ValueError("Invalid upload name")
                size = int(req.get("size", -1))
                if size < 0 or size > MAX_FILE_BYTES:
                    raise ValueError("Upload limit is 1 GiB")
                target = _make_unique(folder, name)
                _reply(send_packet, conn, typ, {"ok": True, "ready": True})
                # Atomic upload: write into same directory, rename only on success.
                with tempfile.NamedTemporaryFile(prefix=".duopad-", suffix=".part", dir=folder,
                                                 delete=False) as tmp:
                    temp_name = tmp.name
                    try:
                        left = size
                        while left:
                            data = read_exact(conn, min(65536, left))
                            if not data: raise ConnectionError("Upload interrupted")
                            tmp.write(data)
                            left -= len(data)
                    except BaseException:
                        tmp.close()
                        os.unlink(temp_name)
                        raise
                os.replace(temp_name, target)
                _reply(send_packet, conn, typ, {"ok": True, "path": str(target), "bytes": size})
            elif typ == 0x40:
                jpeg, width, height = self._frame(req)
                _reply(send_packet, conn, typ, {"ok": True, "size": len(jpeg),
                                               "width": width, "height": height, "format": "jpeg"})
                conn.sendall(jpeg)
            elif typ == 0x21:
                # Text clipboard bridge: Windows only; never execute remote commands.
                if not sys.platform.startswith("win"):
                    raise OSError("Clipboard supported only on Windows")
                import tkinter
                root = tkinter.Tk()
                try:
                    root.withdraw()
                    root.clipboard_clear()
                    root.clipboard_append(payload.decode("utf-8", errors="replace")[:100000])
                    root.update()
                finally:
                    root.destroy()
                _reply(send_packet, conn, typ, {"ok": True})
            elif typ == 0x22:
                if not sys.platform.startswith("win"):
                    raise OSError("Clipboard supported only on Windows")
                import tkinter
                root = tkinter.Tk()
                try:
                    root.withdraw()
                    value = root.clipboard_get()[:100000]
                finally:
                    root.destroy()
                _reply(send_packet, conn, typ, {"ok": True, "text": value})
        except Exception as exc:
            _reply(send_packet, conn, typ, {"ok": False,
                                           "error": str(exc)[:280]})
