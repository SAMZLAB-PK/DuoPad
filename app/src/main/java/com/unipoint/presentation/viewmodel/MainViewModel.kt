package com.unipoint.presentation.viewmodel

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.unipoint.core.util.PcDiagnosticRecorder
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unipoint.domain.model.*
import com.unipoint.domain.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val mode: AppMode = AppMode.PC,
    val connectionStatus: ConnectionStatus = ConnectionStatus(),
    val androidDevices: List<AndroidDevice> = emptyList(),
    val pcDevices: List<PcDevice> = emptyList(),
    val isScanning: Boolean = false,
    val isScanningPc: Boolean = false,
    val hasScannedPc: Boolean = false,
    val pcScanMessage: String? = null,
    val snackbarMessage: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ConnectionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }
        viewModelScope.launch {
            repository.discoveredAndroidDevices.collect { devices ->
                _uiState.update { it.copy(androidDevices = devices) }
            }
        }
        viewModelScope.launch {
            repository.discoveredPcDevices.collect { devices ->
                _uiState.update { it.copy(pcDevices = devices) }
            }
        }
    }

    fun switchMode(mode: AppMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    // ── PC ────────────────────────────────────────────────────
    /** Scan both protocols together; a Windows device requires the PC host running. */
    fun scanNearby() {
        scanAndroid()
        scanPc()
    }

    fun scanPc() = viewModelScope.launch {
        if (_uiState.value.isScanningPc) return@launch
        _uiState.update { it.copy(isScanningPc = true, pcScanMessage = "Searching for PC Host…") }
        PcDiagnosticRecorder.note(context, "PC_SCAN_REQUEST: user requested nearby Windows Host scan")
        try {
            withTimeout(15000L) { repository.scanNetworkPcs() }
            val online = _uiState.value.pcDevices.count { it.isReachable }
            val message = if (online > 0) "$online PC Host(s) found" else
                "No PC Host found. Start DuoPad PC Host, allow Windows firewall, or connect by IP."
            PcDiagnosticRecorder.note(context, "PC_SCAN_COMPLETE: online=$online")
            _uiState.update { it.copy(pcScanMessage = message) }
        } catch (e: TimeoutCancellationException) {
            PcDiagnosticRecorder.note(context, "PC_SCAN_TIMEOUT: exceeded 15s; check network, Host and firewall")
            _uiState.update { it.copy(pcScanMessage = "Search timed out after 15s. Check PC Host and firewall.") }
        } catch (e: Exception) {
            PcDiagnosticRecorder.note(context, "PC_SCAN_ERROR: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(200)}")
            _uiState.update { it.copy(pcScanMessage = "PC search failed: ${e.message ?: "Unknown error"}") }
        } finally {
            _uiState.update { it.copy(isScanningPc = false, hasScannedPc = true) }
        }
    }

    fun connectBluetooth() = viewModelScope.launch {
        repository.connectBluetoothHid()
            .onFailure { showMessage(it.message ?: "Bluetooth failed") }
    }

    fun connectNetwork(
        ip: String,
        port: Int = 27845,
        pin: String? = null,
        onSuccess: (() -> Unit)? = null
    ) = viewModelScope.launch {
        if (_uiState.value.connectionStatus.state == ConnectionState.CONNECTING) return@launch
        PcDiagnosticRecorder.note(context, "PC_CONNECT_REQUEST: $ip:$port, pinSupplied=${!pin.isNullOrBlank()}")
        _uiState.update { it.copy(mode = AppMode.PC) }
        try {
            // Bound even a slow HID cleanup + Windows PIN handshake; the UI must
            // never show an indefinite spinner if the host stops responding.
            withTimeout(12_000L) { repository.connectNetworkPc(ip, port, pin) }
                .onSuccess {
                    _uiState.update { it.copy(mode = AppMode.PC) }
                    PcDiagnosticRecorder.note(context, "PC_CONNECTED: $ip:$port")
                    showMessage("Connected to PC: $ip")
                    onSuccess?.invoke()
                }
                .onFailure {
                    PcDiagnosticRecorder.note(context, "PC_CONNECT_ERROR: $ip:$port; ${it.javaClass.simpleName}: ${it.message.orEmpty().take(180)}")
                    showMessage(it.message ?: "Network connect failed")
                }
        } catch (e: TimeoutCancellationException) {
            PcDiagnosticRecorder.note(context, "PC_CONNECT_TIMEOUT: $ip:$port after 12s, check TCP/PIN and Windows Host window")
            withContext(Dispatchers.IO) { repository.disconnectPc() }
            showMessage("PC connection timed out. Check the Windows Host PIN and Diagnostic Logs.")
        } catch (e: CancellationException) {
            withContext(Dispatchers.IO) { repository.disconnectPc() }
            throw e
        } catch (e: Exception) {
            PcDiagnosticRecorder.note(context, "PC_CONNECT_ERROR: $ip:$port; ${e.javaClass.simpleName}: ${e.message.orEmpty().take(180)}")
            withContext(Dispatchers.IO) { repository.disconnectPc() }
            showMessage("PC connection failed: ${e.message ?: "Unknown error"}")
        }
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        repository.sendInput(InputEvent.MouseMove(dx.toInt(), dy.toInt()))
    }

    fun sendMouseClick(button: MouseButton, down: Boolean = true) {
        repository.sendInput(InputEvent.MouseClick(button, down))
        if (down) {
            // auto release after short delay for tap
            viewModelScope.launch {
                kotlinx.coroutines.delay(40)
                repository.sendInput(InputEvent.MouseClick(button, false))
            }
        }
    }

    fun sendScroll(dy: Float) {
        repository.sendInput(InputEvent.MouseScroll(dy.toInt()))
    }

    fun sendKey(keyCode: Int, down: Boolean, mods: Int = 0) {
        repository.sendInput(InputEvent.KeyEvent(keyCode, down, mods))
    }

    fun sendText(text: String) {
        repository.sendInput(InputEvent.TextInput(text))
    }

    // ── Android ───────────────────────────────────────────────
    fun scanAndroid() = viewModelScope.launch {
        if (_uiState.value.isScanning) return@launch
        _uiState.update { it.copy(isScanning = true) }
        try {
            repository.scanAndroidDevices()
        } catch (e: Exception) {
            showMessage("TV scan failed: ${e.message}")
        } finally {
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    fun connectAndroid(device: AndroidDevice) = viewModelScope.launch {
        if (_uiState.value.connectionStatus.state == ConnectionState.CONNECTING) return@launch
        _uiState.update { it.copy(mode = AppMode.ANDROID) }
        repository.connectAndroid(device)
            .onSuccess {
                _uiState.update { it.copy(mode = AppMode.ANDROID) }
                // Warm the persistent scrcpy control channel without delaying navigation.
                viewModelScope.launch { repository.executeAdb(AdbCommand.PrepareRealtimeInput) }
                showMessage("Connected: ${device.name}")
            }
            .onFailure { showMessage(it.message ?: "ADB connect failed") }
    }

    fun connectAndroidIp(ipPort: String) = viewModelScope.launch {
        val parts = ipPort.trim().split(":")
        val ip = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 5555
        val device = AndroidDevice(id = "$ip:$port", name = ip, ip = ip, port = port)
        if (_uiState.value.connectionStatus.state == ConnectionState.CONNECTING) return@launch
        _uiState.update { it.copy(mode = AppMode.ANDROID) }
        repository.connectAndroid(device)
            .onSuccess {
                _uiState.update { it.copy(mode = AppMode.ANDROID) }
                // Prewarm keys, text, mouse and scroll on the fast binary path.
                viewModelScope.launch { repository.executeAdb(AdbCommand.PrepareRealtimeInput) }
                showMessage("Connected to $ip:$port")
            }
            .onFailure { showMessage(it.message ?: "ADB connect failed") }
    }

    fun executeAdb(cmd: AdbCommand) = viewModelScope.launch {
        repository.executeAdb(cmd)
            .onSuccess { showMessage(it) }
            .onFailure { showMessage(it.message ?: "Command failed") }
    }

    suspend fun listFiles(path: String) = repository.listFiles(path)

    suspend fun listApps() = repository.listInstalledApps()

    suspend fun loadAppDetails(packageName: String) = repository.appDetails(packageName)

    suspend fun deviceInfo() = repository.deviceInfo()

    suspend fun executeAdbAwait(cmd: String): String {
        return repository.executeAdb(AdbCommand.Shell(cmd)).getOrElse { it.message ?: "error" }
    }

    suspend fun executeAdbScreenshot(localPath: String): Result<String> {
        return repository.executeAdb(AdbCommand.Screenshot(localPath))
    }

    suspend fun loadAppIcon(packageName: String): String? {
        return repository.appIcon(packageName).getOrNull()
    }

    suspend fun loadAppPresentation(packageName: String): AppPresentation? {
        return repository.appPresentation(packageName).getOrNull()
    }

    suspend fun executeAdbResult(command: AdbCommand): Result<String> = repository.executeAdb(command)

    suspend fun downloadApp(packageName: String): Result<String> {
        return repository.downloadApp(packageName)
    }

    fun disconnect() {
        repository.disconnectAll()
    }

    fun disconnectAndroid() {
        repository.disconnectAndroid()
    }


    private fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
