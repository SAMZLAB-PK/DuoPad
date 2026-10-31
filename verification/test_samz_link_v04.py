from pathlib import Path
root=Path(__file__).resolve().parents[1]
adb=(root/"app/src/main/java/com/unipoint/data/remote/AdbDataSource.kt").read_text()
ui=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
scan=adb.split("private fun probeDevice(",1)[1].split("suspend fun discover(",1)[0]
assert "isPortOpen(ip, port, 360)" in scan
for forbidden in ("Dadb.create(", "keyPairOrNull(", ".shell(", "AdbKeyPair", "adb.connect("):
    assert forbidden not in scan, "Discovery attempted ADB authentication: "+forbidden
assert "Dadb.create(finalIp, finalPort, keyPair" in adb
assert 'Quick("Remote"' in ui and 'openNearby("Remote")' in ui
assert 'openNearby("Mirror")' in ui
assert 'openNearby("Send files")' in ui
assert 'openNearby("Install APK")' in ui
assert '4->{' in ui and 'Text("Nearby devices"' in ui
assert 'targetButton(Target(name,device.id,false' in ui or 'targetButton(Target(name,device.id,false,saved?.customName)' in ui
assert 'targetButton(Target(name,host.address,true' in ui
assert 'applySelectedAction()' in ui
print("SAMZ_LINK_V04_PASSIVE_DISCOVERY_VERIFY_OK")
