from pathlib import Path
root=Path(__file__).resolve().parents[1]
net=(root/'app/src/main/java/com/unipoint/data/remote/NetworkPcDataSource.kt').read_text()
vm=(root/'app/src/main/java/com/unipoint/presentation/viewmodel/MainViewModel.kt').read_text()
repo=(root/'app/src/main/java/com/unipoint/data/repository/ConnectionRepositoryImpl.kt').read_text()
shell=(root/'app/src/main/java/com/unipoint/presentation/ui/DoupadShell.kt').read_text()
workflow=(root/'.github/workflows/build-apk.yml').read_text()
assert 'PC_HANDSHAKE_TCP_START' in net and 'PC_HANDSHAKE_TCP_OK' in net
assert 'PC_HANDSHAKE_IDENTITY_START' in net and 'PC_HANDSHAKE_IDENTITY_OK' in net
assert 'PC_HANDSHAKE_AUTH_START' in net and 'PC_HANDSHAKE_AUTH_OK' in net
assert 'PC_HANDSHAKE_VERIFY_OK' in net and 'PC_HANDSHAKE_AUTH_REJECTED' in net
assert 'val staleSocket = socket' in net
assert net.index('val staleSocket = socket') < net.index('staleSocket?.close()')
assert 'withContext(Dispatchers.IO) { disconnectAll() }' in repo
assert 'withTimeout(12_000L)' in vm and 'PC_CONNECT_TIMEOUT' in vm
assert 'Connecting to Windows Host and verifying PIN' in shell
assert 'enabled=!(connectRequested && status.state==ConnectionState.CONNECTING)' in shell
assert 'test_duopad_r17_pc_handshake.py' in workflow
print('R17 PC PIN handshake and spinner regression checks passed')
