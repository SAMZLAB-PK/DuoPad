package com.unipoint.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.domain.model.AppMode
import com.unipoint.domain.model.MouseButton
import com.unipoint.presentation.ui.components.*
import com.unipoint.presentation.ui.screens.android.AndroidModeContent
import com.unipoint.presentation.ui.screens.pc.PcModeContent
import com.unipoint.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenKeyboard: () -> Unit,
    onOpenGamepad: () -> Unit,
    onOpenAndroidRemote: () -> Unit,
    onOpenFileManager: () -> Unit = {},
    onOpenAppManager: () -> Unit = {},
    onOpenScreenMirror: () -> Unit = {},
    onOpenQr: () -> Unit = {},
    onBackToDevices: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DOUPAD") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.disconnect()
                        onBackToDevices()
                    }) {
                        Icon(Icons.Default.Devices, "Devices")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenQr) {
                        Icon(Icons.Default.QrCodeScanner, "QR Connect")
                    }
                    IconButton(onClick = { viewModel.disconnect() }) {
                        Icon(Icons.Default.LinkOff, "Disconnect")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ModeSwitcher(
                current = uiState.mode,
                onChange = viewModel::switchMode
            )

            ConnectionStatusCard(uiState.connectionStatus)

            Spacer(Modifier.height(16.dp))

            when (uiState.mode) {
                AppMode.PC -> PcModeContent(
                    onConnectBluetooth = viewModel::connectBluetooth,
                    onConnectNetwork = { ip, port, pin -> viewModel.connectNetwork(ip, port, pin) },
                    onMove = viewModel::sendMouseMove,
                    onLeftClick = { viewModel.sendMouseClick(MouseButton.LEFT) },
                    onRightClick = { viewModel.sendMouseClick(MouseButton.RIGHT) },
                    onScroll = viewModel::sendScroll,
                    onOpenKeyboard = onOpenKeyboard,
                    onOpenGamepad = onOpenGamepad
                )
                AppMode.ANDROID -> AndroidModeContent(
                    devices = uiState.androidDevices,
                    isScanning = uiState.isScanning,
                    onScan = viewModel::scanAndroid,
                    onConnect = viewModel::connectAndroid,
                    onOpenRemote = onOpenAndroidRemote,
                    onInstall = { /* file picker */ },
                    onShell = { viewModel.executeAdb(com.unipoint.domain.model.AdbCommand.Shell("echo hello")) },
                    onScreenshot = {
                        val f = java.io.File(context.cacheDir, "shot.png")
                        viewModel.executeAdb(com.unipoint.domain.model.AdbCommand.Screenshot(f.absolutePath))
                    },
                    onReboot = { viewModel.executeAdb(com.unipoint.domain.model.AdbCommand.Reboot) },
                    onOpenFiles = onOpenFileManager,
                    onOpenApps = onOpenAppManager,
                    onOpenMirror = onOpenScreenMirror,
                    onConnectIp = { viewModel.connectAndroidIp(it) }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
