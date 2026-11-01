from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt").read_text()
adb=(root/"app/src/main/java/com/unipoint/data/remote/AdbDataSource.kt").read_text()
repo=(root/"app/src/main/java/com/unipoint/data/repository/ConnectionRepositoryImpl.kt").read_text()
mouse=(root/"app/src/main/java/com/unipoint/presentation/ui/screens/android/AndroidMousePadScreen.kt").read_text()
mirror=(root/"app/src/main/java/com/unipoint/presentation/ui/DoupadMirrorActivity.kt").read_text()
assert 'customName' in ui and 'saveTargets(context,targets)' in ui
assert 'dialog="connectingSaved"' in ui and 'vm.connectAndroidIp("$ip:$port")' in ui
assert 'Modifier.fillMaxWidth().height(68.dp)' in ui
assert 'airMouseSelected=true;detail="mouse"' in ui
assert 'airMouseSelected=false;detail="mouse"' in ui
assert 'authorizedModelName = model' in adb
assert 'val verifiedName = adb.connectedModelName()' in repo
assert 'Sensor.TYPE_GYROSCOPE' in mouse and 'sensorManager.unregisterListener(listener)' in mouse
assert 'AdbCommand.PointerMove' in mouse
assert 'MirrorCrashRecorder.started(this)' in mirror and 'requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE' not in mirror
assert 'Never change requested orientation or SurfaceView size here.' in mirror
assert 'worker.execute' in mirror
print("SAMZ LINK 0.3 SOURCE CHECKS: PASS")
