package com.unipoint.presentation.ui.screens.common

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.unipoint.presentation.viewmodel.MainViewModel

/**
 * QR payload format (simple & robust):
 *   unipoint://pc/<ip>:<port>?pin=<pin>
 *   unipoint://android/<ip>:<port>
 *
 * The Host can display a QR with this content.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QrConnectScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var lastScanned by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Point camera at UniPoint QR") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR to Connect") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!cameraPermission.status.isGranted) {
                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))
                Text("Camera permission required for QR scanning")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                    Text("Grant Camera Permission")
                }
            } else {
                // In production integrate ZXing or CameraX barcode scanner here.
                // For the starter we provide a manual fallback + clear contract.
                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(status, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))

                // Manual entry as reliable fallback while camera scanner is wired
                var manual by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("Or paste QR content / IP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val result = parseAndConnect(manual.trim(), viewModel)
                        status = result
                        if (result.startsWith("Connected")) onConnected()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect")
                }

                lastScanned?.let {
                    Spacer(Modifier.height(16.dp))
                    Text("Last scanned: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Parse UniPoint QR / manual string and initiate connection.
 */
fun parseAndConnect(raw: String, viewModel: MainViewModel): String {
    return try {
        when {
            raw.startsWith("unipoint://pc/") -> {
                val rest = raw.removePrefix("unipoint://pc/")
                val (hostPart, query) = rest.split("?", limit = 2).let {
                    it[0] to it.getOrNull(1)
                }
                val (ip, portStr) = hostPart.split(":", limit = 2)
                val port = portStr.toIntOrNull() ?: 27845
                val pin = query?.substringAfter("pin=")?.substringBefore("&")
                viewModel.connectNetwork(ip, port, pin)
                "Connecting to PC $ip:$port …"
            }
            raw.startsWith("unipoint://android/") -> {
                val hostPart = raw.removePrefix("unipoint://android/")
                val (ip, portStr) = hostPart.split(":", limit = 2).let {
                    it[0] to (it.getOrNull(1) ?: "5555")
                }
                // Re-use existing android connect path via IP
                "Android QR connect: $ip:$portStr (use Scan + manual for now)"
            }
            raw.matches(Regex("""\d{1,3}(\.\d{1,3}){3}(:\d+)?""")) -> {
                val parts = raw.split(":")
                val ip = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 27845
                viewModel.connectNetwork(ip, port, null)
                "Connecting to $ip:$port …"
            }
            else -> "Unrecognized format. Use: unipoint://pc/<ip>:<port>?pin=xxxx"
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
