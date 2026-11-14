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
import subprocess
from functools import lru_cache
from concurrent.futures import ThreadPoolExecutor
import socket
import struct
import sys
import tempfile
import threading
import time
from pathlib import Path

MAX_FILE_BYTES = 1024 * 1024 * 1024  # explicit cap: 1 GiB per transfer
MAX_FRAME_BYTES = 3 * 1024 * 1024

# Authenticated JPEG frames from explicitly approved Android MediaProjection.
# The Host never initiates phone capture; the phone controls Start/Stop.
class PhoneMirrorFrames:
    _lock = threading.Lock()
    _jpeg = b""
    _width = 0
    _height = 0
    _seen_at = 0.0
    _source = ""

    @classmethod
    def push(cls, jpeg, width, height, source):
        if len(jpeg) > MAX_FRAME_BYTES or not jpeg.startswith(b"\xff\xd8"):
            raise ValueError("Invalid JPEG phone frame")
        if not (160 <= width <= 4096 and 160 <= height <= 4096):
            raise ValueError("Invalid phone capture dimensions")
        with cls._lock:
            cls._jpeg = jpeg
            cls._width, cls._height = width, height
            cls._seen_at = time.monotonic()
            cls._source = str(source)[:80]

    @classmethod
    def snapshot(cls):
        with cls._lock:
            return cls._jpeg, cls._width, cls._height, cls._seen_at, cls._source


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


def _read_windows_hardware_slow():
    """Read-only WMI/CIM inventory, cached to avoid restarting PowerShell per request."""
    if not sys.platform.startswith("win"):
        return {"cpuManufacturer": "", "cpuModel": "", "ramModules": []}
    script = (
        "$ErrorActionPreference='SilentlyContinue';"
        "$cpu=Get-CimInstance Win32_Processor | Select-Object -First 1 Name,Manufacturer,NumberOfCores;"
        "$ram=@(Get-CimInstance Win32_PhysicalMemory | Select-Object Manufacturer,PartNumber,Capacity,Speed,DeviceLocator);"
        "$physical=@(Get-PhysicalDisk);"
        "$disks=@(Get-Disk | ForEach-Object {"
        " $d=$_; $pm=$physical | Where-Object FriendlyName -eq $d.FriendlyName | Select-Object -First 1;"
        " $media=if($pm){[string]$pm.MediaType}else{'Unspecified'};"
        " $bus=[string]$d.BusType;"
        " $kind=if($bus -eq 'NVMe'){'NVMe SSD'}"
        " elseif($media -eq 'SSD' -or $d.FriendlyName -match 'SSD'){'SSD'}"
        " elseif($media -eq 'HDD'){'HDD'}else{'Unknown'};"
        " [pscustomobject]@{number=$d.Number;model=$d.FriendlyName;type=$kind;bus=$bus;media=$media;size=$d.Size}"
        " });"
        "@{cpuManufacturer=$cpu.Manufacturer;cpuModel=$cpu.Name;cpuCores=$cpu.NumberOfCores;"
        "ramModules=@($ram);diskInventory=@($disks)} | ConvertTo-Json -Compress -Depth 7"
    )
    try:
        process = subprocess.run(
            ["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
            capture_output=True, text=True, timeout=15,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)
        )
        result = json.loads(process.stdout) if process.returncode == 0 else {}
        return result if isinstance(result, dict) else {}
    except (OSError, subprocess.TimeoutExpired, ValueError):
        return {}



# CIM can spend seconds starting PowerShell on a cold Windows session.
# Never hold a control protocol reply hostage to that process.
_hardware_worker = ThreadPoolExecutor(max_workers=1, thread_name_prefix="DuoPadHardware")
_hardware_future = None


def _windows_hardware():
    global _hardware_future
    fallback = {"cpuManufacturer": "", "cpuModel": platform.processor() or "",
                "ramModules": [], "diskInventory": []}
    if not sys.platform.startswith("win"):
        return fallback
    # Windows registry CPU identification is fast and available even while CIM warms up.
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,
                            r"HARDWARE\DESCRIPTION\System\CentralProcessor\0") as key:
            for source, target in (("ProcessorNameString", "cpuModel"),
                                   ("VendorIdentifier", "cpuManufacturer")):
                try:
                    fallback[target] = str(winreg.QueryValueEx(key, source)[0]).strip()
                except OSError:
                    pass
    except OSError:
        pass
    if _hardware_future is None:
        _hardware_future = _hardware_worker.submit(_read_windows_hardware_slow)
    if _hardware_future.done():
        try:
            detailed = _hardware_future.result()
            if isinstance(detailed, dict):
                fallback.update({key: value for key, value in detailed.items()
                                 if value is not None and value != ""})
        except Exception:
            pass
    return fallback


@lru_cache(maxsize=1)
def _installed_software(limit=140):
    """List visible uninstall-registry entries; never call Win32_Product (which can repair MSI)."""
    if not sys.platform.startswith("win"):
        return []
    try:
        import winreg
    except ImportError:
        return []
    results = {}
    uninstall = r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"
    for hive, view in (
        (winreg.HKEY_LOCAL_MACHINE, winreg.KEY_WOW64_64KEY),
        (winreg.HKEY_LOCAL_MACHINE, winreg.KEY_WOW64_32KEY),
        (winreg.HKEY_CURRENT_USER, winreg.KEY_WOW64_64KEY),
        (winreg.HKEY_CURRENT_USER, winreg.KEY_WOW64_32KEY),
    ):
        try:
            key = winreg.OpenKey(hive, uninstall, 0, winreg.KEY_READ | view)
        except OSError:
            continue
        with key:
            for index in range(winreg.QueryInfoKey(key)[0]):
                try:
                    with winreg.OpenKey(key, winreg.EnumKey(key, index)) as item:
                        def field(name, default=""):
                            try:
                                return str(winreg.QueryValueEx(item, name)[0]).strip()
                            except OSError:
                                return default
                        name = field("DisplayName")[:110]
                        if not name or field("SystemComponent") == "1" or field("ParentKeyName"):
                            continue
                        version = field("DisplayVersion")[:45]
                        publisher = field("Publisher")[:80]
                        results[(name.casefold(), version)] = {
                            "name": name, "version": version, "publisher": publisher
                        }
                except (OSError, ValueError):
                    continue
    return sorted(results.values(), key=lambda item: item["name"].casefold())[:limit]


def _intel_cpu_generation(name):
    """Report generation only when Intel's numbered Core model identifies it."""
    match = re.search(r"\bi([3579])-([0-9]{4,5})\b", name or "", re.I)
    if not match:
        return ""
    sku = match.group(2)
    generation = int(sku[:2]) if len(sku) == 5 else int(sku[0])
    if generation < 2 or generation > 14:
        return ""
    suffix = "th" if generation % 100 in (11, 12, 13) or generation % 10 not in (1,2,3) else {
        1: "st", 2: "nd", 3: "rd"}[generation % 10]
    return f"Intel Core i{match.group(1)} · {generation}{suffix} Gen"


def _machine_info():
    drives = _windows_drives()
    info = {"name": socket.gethostname(), "os": platform.platform(),
            "architecture": platform.machine(), "cpu": platform.processor() or platform.machine(),
            "cpuThreads": os.cpu_count(), "python": platform.python_version(),
            "user": os.environ.get("USERNAME") or os.environ.get("USER", ""),
            "home": str(Path.home()), "drives": len(drives), "capabilities":
            ["desktop-preview", "mouse-keyboard", "files", "drives", "system-info", "clipboard"]}
    info.update(_windows_hardware())
    info["cpuGeneration"] = _intel_cpu_generation(info.get("cpuModel", ""))
    software = _installed_software()
    info["installedSoftware"] = software
    info["softwareCount"] = len(software)
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
        if sys.platform.startswith("win"):
            # mss captures the desktop but normally omits the Windows hardware cursor.
            # Overlay a high-contrast pointer using GetCursorPos in monitor coordinates.
            try:
                from PIL import ImageDraw
                class Point(ctypes.Structure):
                    _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]
                point = Point()
                if ctypes.windll.user32.GetCursorPos(ctypes.byref(point)):
                    monitor = monitors[monitor_index]
                    local_x = point.x - int(monitor["left"])
                    local_y = point.y - int(monitor["top"])
                    if 0 <= local_x < int(monitor["width"]) and 0 <= local_y < int(monitor["height"]):
                        px = round(local_x * frame.width / int(monitor["width"]))
                        py = round(local_y * frame.height / int(monitor["height"]))
                        size = max(12, round(frame.width / 85))
                        draw = ImageDraw.Draw(frame)
                        poly = [(px,py),(px,py+size),(px+size//3,py+size*3//4),
                                (px+size//2,py+size),(px+size*3//4,py+size*9//10),
                                (px+size//2,py+size*2//3),(px+size*3//4,py+size//2)]
                        draw.polygon(poly,fill="white",outline="black",width=2)
            except (OSError, AttributeError, ValueError, ImportError, ZeroDivisionError):
                pass
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
            elif typ == 0x50:
                # One authenticated metadata packet followed by an exact-size JPEG.
                # Bound bytes before allocating and reply with back-pressure.
                size = int(req.get("size", 0))
                width, height = int(req.get("width", 0)), int(req.get("height", 0))
                if not (10 <= size <= MAX_FRAME_BYTES):
                    raise ValueError("Phone frame exceeds the permitted size")
                jpeg = read_exact(conn, size)
                if jpeg is None or len(jpeg) != size:
                    raise ConnectionError("Incomplete phone mirror frame")
                PhoneMirrorFrames.push(jpeg, width, height, req.get("name", "Android phone"))
                _reply(send_packet, conn, typ, {"ok": True})
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
