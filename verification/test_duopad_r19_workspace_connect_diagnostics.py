from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/unipoint/data/remote/PcWorkspaceClient.kt').read_text()
assert 'log("TCP_START")' in s
assert 'conn.connect(InetSocketAddress(host, port), 3500)' in s
assert 'log("TCP_OK local=' in s
assert 'log("CONNECT_FAILED ${error.javaClass.simpleName}' in s
assert s.index('try {\n            conn.tcpNoDelay') < s.index('conn.connect(InetSocketAddress(host, port), 3500)')
assert 'runCatching { conn.close() }' in s
print('DOUPAD_R19_WORKSPACE_SOCKET_FAILURE_DIAGNOSTICS_PASS')
