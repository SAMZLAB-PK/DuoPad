package com.unipoint.data.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.unipoint.core.bluetooth.BluetoothHidReports
import com.unipoint.core.util.HidKeycodes
import com.unipoint.domain.model.InputEvent
import com.unipoint.domain.model.MouseButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class BluetoothHidDataSource @Inject constructor(
    private val context: Context
) {
    private val tag = "BluetoothHidDS"
    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null
    private var isRegistered = false
    private var resumed = false

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val REPORT_DESCRIPTOR = BluetoothHidReports.REPORT_DESCRIPTOR


    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    @SuppressLint("MissingPermission")
    suspend fun start(): Result<Unit> {
        return try {
            if (!hasBluetoothPermission()) {
                return Result.failure(Exception("Bluetooth permission missing. Allow Bluetooth in Settings."))
            }
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return Result.failure(Exception("Bluetooth not available"))
            val adapter = manager.adapter
                ?: return Result.failure(Exception("No Bluetooth adapter"))
            if (!adapter.isEnabled) {
                return Result.failure(Exception("Turn ON Bluetooth first"))
            }
            if (isRegistered && hidDevice != null) return Result.success(Unit)

            suspendCancellableCoroutine { cont ->
                resumed = false
                fun safeResume(r: Result<Unit>) {
                    if (!resumed && cont.isActive) { resumed = true; cont.resume(r) }
                }
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        try {
                            if (profile != BluetoothProfile.HID_DEVICE || proxy == null) {
                                safeResume(Result.failure(Exception("HID not supported on this phone")))
                                return
                            }
                            hidDevice = proxy as BluetoothHidDevice
                            val sdp = BluetoothHidDeviceAppSdpSettings(
                                "DOUPAD Input", "DOUPAD Bluetooth Mouse + Keyboard", "SAMZ Labs",
                                BluetoothHidDevice.SUBCLASS1_COMBO, REPORT_DESCRIPTOR
                            )
                            val inQos = BluetoothHidDeviceAppQosSettings(
                                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                                800, 9, 0, 0, -1
                            )
                            val cb = object : BluetoothHidDevice.Callback() {
                                override fun onAppStatusChanged(d: BluetoothDevice?, registered: Boolean) {
                                    isRegistered = registered
                                    if (registered) safeResume(Result.success(Unit))
                                }
                                override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                                    when (state) {
                                        BluetoothProfile.STATE_CONNECTED -> {
                                            hostDevice = device
                                            _isConnected.value = true
                                            _connectedDeviceName.value = device?.name ?: "PC"
                                        }
                                        BluetoothProfile.STATE_DISCONNECTED -> {
                                            hostDevice = null
                                            _isConnected.value = false
                                            _connectedDeviceName.value = null
                                        }
                                    }
                                }
                            }
                            val ok = hidDevice?.registerApp(sdp, null, inQos, { it.run() }, cb) ?: false
                            if (!ok) safeResume(Result.failure(Exception("HID register failed on this device")))
                        } catch (e: Exception) {
                            safeResume(Result.failure(e))
                        }
                    }
                    override fun onServiceDisconnected(profile: Int) {
                        hidDevice = null; isRegistered = false
                    }
                }
                try {
                    if (!adapter.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE)) {
                        safeResume(Result.failure(Exception("Cannot open HID profile")))
                    }
                } catch (e: Exception) {
                    safeResume(Result.failure(e))
                }
                cont.invokeOnCancellation { try { stop() } catch (_: Exception) {} }
            }
        } catch (e: Exception) {
            Log.e(tag, "start crashed", e)
            Result.failure(Exception("Bluetooth error: ${e.message}"))
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try { hidDevice?.unregisterApp() } catch (_: Exception) {}
        try {
            val m = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            hidDevice?.let { m?.adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        } catch (_: Exception) {}
        hidDevice = null; hostDevice = null; isRegistered = false
        _isConnected.value = false; _connectedDeviceName.value = null
    }

    fun send(event: InputEvent) {
        try {
            when (event) {
                is InputEvent.MouseMove -> sendMouse(0, event.dx, event.dy, 0)
                is InputEvent.MouseClick -> {
                    val btn = when (event.button) {
                        MouseButton.LEFT -> 0x01
                        MouseButton.RIGHT -> 0x02
                        MouseButton.MIDDLE -> 0x04
                    }
                    sendMouse(if (event.down) btn else 0, 0, 0, 0)
                }
                is InputEvent.MouseScroll -> sendMouse(0, 0, 0, event.dy)
                is InputEvent.KeyEvent -> sendKeyboard(event.keyCode, event.down, event.modifiers)
                is InputEvent.TextInput -> event.text.forEach { c ->
                    val (k, m) = HidKeycodes.charToHid(c)
                    if (k != 0) {
                        sendKeyboard(k, true, m)
                        sendKeyboard(k, false, 0)
                    }
                }
                else -> {}
            }
        } catch (e: Exception) { Log.w(tag, "send", e) }
    }

    @SuppressLint("MissingPermission")
    private fun sendMouse(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        val device = hostDevice ?: return
        val hid = hidDevice ?: return
        try {
            hid.sendReport(
                device,
                BluetoothHidReports.MOUSE_REPORT_ID,
                BluetoothHidReports.mouseReport(buttons, dx, dy, wheel)
            )
        } catch (e: Exception) { Log.w(tag, "report", e) }
    }

    @SuppressLint("MissingPermission")
    private fun sendKeyboard(keyCode: Int, down: Boolean, modifiers: Int) {
        val device = hostDevice ?: return
        val hid = hidDevice ?: return
        try {
            hid.sendReport(
                device,
                BluetoothHidReports.KEYBOARD_REPORT_ID,
                BluetoothHidReports.keyboardReport(keyCode, down, modifiers)
            )
        } catch (e: Exception) {
            Log.w(tag, "keyboard report", e)
        }
    }
}
