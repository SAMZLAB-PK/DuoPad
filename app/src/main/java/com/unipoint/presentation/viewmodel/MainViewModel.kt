package com.unipoint.presentation.viewmodel

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
    val snackbarMessage: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ConnectionRepository
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
    fun scanPc() = viewModelScope.launch {
        if (_uiState.value.isScanningPc) return@launch
        _uiState.update { it.copy(isScanningPc = true) }
        runCatching { repository.scanNetworkPcs() }
            .onFailure { showMessage(it.message ?: "PC scan failed") }
        _uiState.update { it.copy(isScanningPc = false) }
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
        _uiState.update { it.copy(mode = AppMode.PC) }
        repository.connectNetworkPc(ip, port, pin)
            .onSuccess {
                _uiState.update { it.copy(mode = AppMode.PC) }
                showMessage("Connected to PC: $ip")
                onSuccess?.invoke()
            }
            .onFailure { showMessage(it.message ?: "Network connect failed") }
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
        repository.scanAndroidDevices()
        _uiState.update { it.copy(isScanning = false) }
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
