from pathlib import Path
root=Path(__file__).resolve().parents[1]
vm=(root/'app/src/main/java/com/unipoint/presentation/viewmodel/MainViewModel.kt').read_text()
net=(root/'app/src/main/java/com/unipoint/data/remote/NetworkPcDataSource.kt').read_text()
shell=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt').read_text()
screen=(root/'app/src/main/java/com/unipoint/presentation/ui/screens/android/MirrorDiagnosticsScreen.kt').read_text()
log=(root/'app/src/main/java/com/unipoint/core/util/PcDiagnosticRecorder.kt').read_text()
devices=(root/'app/src/main/java/com/unipoint/presentation/ui/screens/DeviceListScreen.kt').read_text()
workflow=(root/'.github/workflows/build-apk.yml').read_text()
assert 'hasScannedPc: Boolean = false' in vm
assert 'withTimeout(15000L)' in vm
assert 'finally {' in vm and 'isScanningPc = false, hasScannedPc = true' in vm
assert 'PC_SCAN_TIMEOUT' in vm and 'PC_SCAN_COMPLETE' in vm
assert 'PC_DISCOVERY_START' in net and 'PC_DISCOVERY_UDP_BEGIN' in net
assert 'PC_DISCOVERY_TCP_BEGIN' in net and 'PC_DISCOVERY_FULL_SCAN' in net
assert 'PC_HOST_FOUND' in net and 'PC_CONNECT_FAILED' in net
assert 'duopad_pc_diagnostics.txt' in log and 'MAX' not in log
assert 'PC DISCOVERY AND CONNECTION LOG' in screen and 'PcDiagnosticRecorder.clear(context)' in screen
assert 'DuoPad diagnostics' in screen
assert 'LaunchedEffect(Unit) {' in shell and 'vm.scanAndroid()' in shell  # R22 resets page to Devices before scan
assert 'if(state.hasScannedPc) "Search Again" else "Search PC"' in shell
assert 'pc=scanFilter=="PC"' in shell
assert 'actionLabel = if (uiState.hasScannedPc) "Search Again" else "Search PC"' in devices
assert 'test_duopad_r16_pc_search_diagnostics.py' in workflow
print('R16 PC scan timeout / Search PC / diagnostics checks passed')
