"""R24 source gates: reverse Android-to-PC capture, monochrome appearance, embedded EXE."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
read=lambda path:(root/path).read_text(encoding="utf-8")
shell=read("app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt")
theme=read("app/src/main/java/com/unipoint/presentation/ui/theme/Theme.kt")
host=read("host/python/unipoint_host.py")
workspace=read("host/python/pc_workspace.py")
gui=read("host/python/duopad_host_gui.py")
service=read("app/src/main/java/com/unipoint/service/PhoneMirrorForegroundService.kt")
activity=read("app/src/main/java/com/unipoint/presentation/ui/PhoneMirrorActivity.kt")
manifest=read("app/src/main/AndroidManifest.xml")
workflow=read(".github/workflows/build-apk.yml")
sharing=read("app/src/main/java/com/unipoint/presentation/ui/DuoPadHostShare.kt")
pcui=read("app/src/main/java/com/unipoint/presentation/ui/screens/pc/PcWorkspaceScreen.kt")
assert 'onToggleAppearance' in shell
assert 'WbSunny' in shell and 'DarkMode' in shell
assert 'primary=Color(0xFF161616)' in theme and 'background=Color.White' in theme
assert 'startPhoneMirror()' in shell and 'Phone → PC Mirror' in shell
assert 'PhoneMirrorForegroundService' in manifest and 'PhoneMirrorActivity' in manifest
assert 'FOREGROUND_SERVICE_MEDIA_PROJECTION' in manifest
assert 'createScreenCaptureIntent()' in activity and 'result.resultCode == Activity.RESULT_OK' in activity
assert 'FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION' in service
assert 'registerCallback' in service and 'ACTION_STOP' in service
assert 'MediaProjection' in service and '0x50' in service and 'AUTH=1' in service
assert 'elif ptype in (0x30' in host and '0x50' in host
assert 'PhoneMirrorFrames.push' in workspace and 'MAX_FRAME_BYTES' in workspace
assert 'open_phone_mirror' in gui and 'Phone Mirror' in gui
assert '_intel_cpu_generation' in workspace and 'Win32_PhysicalMemory' in workspace
assert 'Get-Disk' in workspace and 'Get-PhysicalDisk' in workspace
assert 'diskInventory' in pcui and 'cpuGeneration' in pcui
assert 'pcSetup' in shell and 'Send PC Companion' in shell
assert 'DuoPad-PC-Companion-R24.zip' in sharing and workflow
assert 'actions/download-artifact@v4' in workflow and 'windows-latest' in workflow
for name in ('pc_workspace.py','unipoint_host.py','duopad_host_gui.py'):
    assert read("app/src/main/assets/pc-host/host/python/"+name) == read("host/python/"+name), name
print("DOUPAD_R24_REVERSE_MIRROR_MONOCHROME_EMBEDDED_HOST_PASS")
