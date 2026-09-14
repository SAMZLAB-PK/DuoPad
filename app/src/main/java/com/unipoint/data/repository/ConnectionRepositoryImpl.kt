package com.unipoint.data.repository

import com.unipoint.data.remote.AdbDataSource
import com.unipoint.data.remote.BluetoothHidDataSource
import com.unipoint.data.remote.NetworkPcDataSource
import com.unipoint.domain.model.*
import com.unipoint.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val bluetooth: BluetoothHidDataSource,
    private val network: NetworkPcDataSource,
    private val adb: AdbDataSource
) : ConnectionRepository {

    private val _status = MutableStateFlow(ConnectionStatus())
    override val connectionStatus: Flow<ConnectionStatus> = _status.asStateFlow()

    private val _androidDevices = MutableStateFlow<List<AndroidDevice>>(emptyList())
    override val discoveredAndroidDevices: Flow<List<AndroidDevice>> = _androidDevices.asStateFlow()

    private val _pcDevices = MutableStateFlow<List<PcDevice>>(emptyList())
    override val discoveredPcDevices: Flow<List<PcDevice>> = _pcDevices.asStateFlow()

    @Volatile private var activePcType: PcConnectionType? = null
    init {
        network.onConnectionLost = {
            if (activePcType == PcConnectionType.NETWORK) {
                activePcType = null
                _status.value = ConnectionStatus(state = ConnectionState.ERROR, errorMessage = "PC disconnected. Reconnect to continue.")
            }
        }
    }

    override suspend fun connectBluetoothHid(): Result<Unit> {
        _status.value = ConnectionStatus(state = ConnectionState.CONNECTING, deviceName = "Bluetooth HID")
        val result = bluetooth.start()
        if (result.isSuccess) {
            activePcType = PcConnectionType.BLUETOOTH_HID
            _status.value = ConnectionStatus(
                state = ConnectionState.CONNECTED,
                deviceName = bluetooth.connectedDeviceName.value ?: "Bluetooth Device",
                connectedSince = System.currentTimeMillis()
            )
        } else {
            _status.value = ConnectionStatus(
                state = ConnectionState.ERROR,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
        return result
    }

    override suspend fun scanNetworkPcs() {
        _pcDevices.value = network.discover()
    }

    override suspend fun connectNetworkPc(ip: String, port: Int, pin: String?): Result<Unit> {
        disconnectAll()
        _status.value = ConnectionStatus(state = ConnectionState.CONNECTING, deviceName = ip)
        val result = network.connect(ip, port, pin)
        if (result.isSuccess) {
            activePcType = PcConnectionType.NETWORK
            _status.value = ConnectionStatus(
                state = ConnectionState.CONNECTED,
                deviceName = "DOUPAD PC",
                address = "$ip:$port",
                latencyMs = network.latencyMs,
                connectedSince = System.currentTimeMillis()
            )
            _pcDevices.update { list ->
                val connected = PcDevice("$ip:$port", "DOUPAD PC", "$ip:$port", PcConnectionType.NETWORK, true)
                listOf(connected) + list.filter { it.id != connected.id }.map { it.copy(isConnected = false) }
            }
        } else {
            _status.value = ConnectionStatus(
                state = ConnectionState.ERROR,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
        return result
    }

    override fun sendInput(event: InputEvent) {
        when (activePcType) {
            PcConnectionType.BLUETOOTH_HID -> bluetooth.send(event)
            PcConnectionType.NETWORK -> network.send(event)
            null -> {}
        }
    }

    override fun disconnectPc() {
        bluetooth.stop()
        network.disconnect()
        activePcType = null
        _pcDevices.update { list -> list.map { it.copy(isConnected = false) } }
        _status.value = ConnectionStatus(state = ConnectionState.DISCONNECTED)
    }

    override suspend fun scanAndroidDevices() {
        val list = adb.discover()
        _androidDevices.value = list
    }

    override suspend fun connectAndroid(device: AndroidDevice): Result<Unit> {
        disconnectAll()
        _status.value = ConnectionStatus(state = ConnectionState.CONNECTING, deviceName = device.name)
        val result = adb.connect(device.ip, device.port)
        if (result.isSuccess) {
            _status.value = ConnectionStatus(
                state = ConnectionState.CONNECTED,
                deviceName = device.name,
                address = "${device.ip}:${device.port}",
                connectedSince = System.currentTimeMillis()
            )
            _androidDevices.update { list ->
                list.map { if (it.id == device.id) it.copy(isConnected = true) else it }
            }
        } else {
            _status.value = ConnectionStatus(
                state = ConnectionState.ERROR,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
        return result
    }

    override suspend fun executeAdb(command: AdbCommand): Result<String> {
        return when (command) {
            is AdbCommand.Shell -> adb.shell(command.cmd)
            is AdbCommand.Install -> adb.install(command.apkPath).map { "Installed" }
            is AdbCommand.Uninstall -> adb.uninstall(command.packageName).map { "Uninstalled" }
            is AdbCommand.Push -> adb.push(command.local, command.remote).map { "Pushed" }
            is AdbCommand.Pull -> adb.pull(command.remote, command.local).map { "Pulled" }
            is AdbCommand.Screenshot -> adb.screenshot(command.localPath).map { "Screenshot saved: ${command.localPath}" }
            is AdbCommand.Reboot -> adb.reboot().map { "Rebooting…" }
            is AdbCommand.InputKey -> adb.inputKey(command.keyCode).map { "OK" }
            is AdbCommand.InputText -> adb.inputText(command.text).map { "OK" }
            is AdbCommand.InputTap -> adb.inputTap(command.x, command.y).map { "OK" }
            is AdbCommand.InputSwipe -> adb.inputSwipe(command.x1, command.y1, command.x2, command.y2, command.duration).map { "OK" }
            is AdbCommand.PrepareRealtimeInput -> adb.prepareRealtimeInput().map { "Realtime input ready" }
            is AdbCommand.PointerMove -> adb.pointerMove(command.x, command.y, command.screenWidth, command.screenHeight).map { "OK" }
            is AdbCommand.PointerClick -> adb.pointerClick(command.x, command.y, command.screenWidth, command.screenHeight, command.button).map { "OK" }
            is AdbCommand.PointerScroll -> adb.pointerScroll(command.x, command.y, command.screenWidth, command.screenHeight, command.hScroll, command.vScroll).map { "OK" }
        }
    }

    override suspend fun listInstalledApps(): Result<List<InstalledApp>> = adb.listApps()

    override suspend fun appDetails(packageName: String): Result<InstalledApp> = adb.appDetails(packageName)

    override suspend fun appIcon(packageName: String): Result<String> = adb.appIcon(packageName)

    override suspend fun appPresentation(packageName: String): Result<AppPresentation> = adb.appPresentation(packageName)

    override suspend fun downloadApp(packageName: String): Result<String> = adb.downloadApk(packageName)

    override suspend fun deviceInfo(): Result<Map<String, String>> = adb.deviceInfo()

    override suspend fun listFiles(path: String): Result<List<FileEntry>> = adb.listFiles(path)

    override fun disconnectAndroid() {
        adb.disconnect()
        _androidDevices.update { list -> list.map { it.copy(isConnected = false) } }
        _status.value = ConnectionStatus(state = ConnectionState.DISCONNECTED)
    }

    override fun disconnectAll() {
        disconnectPc()
        disconnectAndroid()
    }
}
