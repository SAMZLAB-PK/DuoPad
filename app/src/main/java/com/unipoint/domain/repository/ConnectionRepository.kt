package com.unipoint.domain.repository

import com.unipoint.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {

    val connectionStatus: Flow<ConnectionStatus>
    val discoveredAndroidDevices: Flow<List<AndroidDevice>>
    val discoveredPcDevices: Flow<List<PcDevice>>

    // PC
    suspend fun connectBluetoothHid(): Result<Unit>
    suspend fun scanNetworkPcs()
    suspend fun connectNetworkPc(ip: String, port: Int, pin: String? = null): Result<Unit>
    fun sendInput(event: InputEvent)
    fun disconnectPc()

    // Android ADB
    suspend fun scanAndroidDevices()
    suspend fun connectAndroid(device: AndroidDevice): Result<Unit>
    suspend fun executeAdb(command: AdbCommand): Result<String>
    suspend fun listInstalledApps(): Result<List<InstalledApp>>
    suspend fun appDetails(packageName: String): Result<InstalledApp>
    suspend fun appIcon(packageName: String): Result<String>
    suspend fun appPresentation(packageName: String): Result<AppPresentation>
    suspend fun downloadApp(packageName: String): Result<String>
    suspend fun deviceInfo(): Result<Map<String, String>>
    suspend fun listFiles(path: String): Result<List<FileEntry>>
    fun disconnectAndroid()

    fun disconnectAll()
}
