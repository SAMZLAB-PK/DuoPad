package com.unipoint.domain.model

import androidx.compose.runtime.Immutable

enum class AppMode { PC, ANDROID }

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
}

enum class PcConnectionType {
    BLUETOOTH_HID,   // No software on PC
    NETWORK          // Requires UniPoint Host
}

@Immutable
data class ConnectionStatus(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val address: String? = null,
    val latencyMs: Long? = null,
    val signalStrength: Int? = null, // 0-100
    val errorMessage: String? = null,
    val connectedSince: Long? = null
)

@Immutable
data class PcDevice(
    val id: String,
    val name: String,
    val address: String,
    val type: PcConnectionType,
    val isConnected: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

@Immutable
data class AndroidDevice(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 5555,
    val model: String? = null,
    val androidVersion: String? = null,
    val serial: String? = null,
    val isConnected: Boolean = false,
    val batteryLevel: Int? = null
)

@Immutable
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystem: Boolean,
    val isEnabled: Boolean
)

@Immutable
data class AppPresentation(
    val app: InstalledApp,
    val iconPath: String?
)

@Immutable
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0
)

sealed class InputEvent {
    data class MouseMove(val dx: Int, val dy: Int) : InputEvent()
    data class MouseClick(val button: MouseButton, val down: Boolean) : InputEvent()
    data class MouseScroll(val dy: Int) : InputEvent()
    data class KeyEvent(val keyCode: Int, val down: Boolean, val modifiers: Int = 0) : InputEvent()
    data class GamepadButton(val button: Int, val down: Boolean) : InputEvent()
    data class GamepadAxis(val axis: Int, val value: Float) : InputEvent()
    data class TextInput(val text: String) : InputEvent()
}

enum class MouseButton { LEFT, RIGHT, MIDDLE }

sealed class AdbCommand {
    data class Shell(val cmd: String) : AdbCommand()
    data class Install(val apkPath: String) : AdbCommand()
    data class Uninstall(val packageName: String) : AdbCommand()
    data class Push(val local: String, val remote: String) : AdbCommand()
    data class Pull(val remote: String, val local: String) : AdbCommand()
    data class Screenshot(val localPath: String) : AdbCommand()
    object Reboot : AdbCommand()
    data class InputKey(val keyCode: Int) : AdbCommand()
    data class InputText(val text: String) : AdbCommand()
    data class InputTap(val x: Int, val y: Int) : AdbCommand()
    data class InputSwipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val duration: Int = 300) : AdbCommand()
    object PrepareRealtimeInput : AdbCommand()
    data class PointerMove(val x: Int, val y: Int, val screenWidth: Int, val screenHeight: Int) : AdbCommand()
    data class PointerClick(val x: Int, val y: Int, val screenWidth: Int, val screenHeight: Int, val button: MouseButton) : AdbCommand()
    data class PointerScroll(val x: Int, val y: Int, val screenWidth: Int, val screenHeight: Int, val hScroll: Float = 0f, val vScroll: Float) : AdbCommand()
}
