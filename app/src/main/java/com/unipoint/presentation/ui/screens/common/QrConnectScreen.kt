package com.unipoint.presentation.ui.screens.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.unipoint.core.network.PcPairingUri
import com.unipoint.presentation.viewmodel.MainViewModel

/**
 * Public pairing contract:
 *   doupad://pc/<ip>:<port>?name=<pc>&pin=<optional-pin>
 *
 * Legacy unipoint://pc links and manual host[:port] strings remain accepted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrConnectScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    var status by remember { mutableStateOf("Scan the QR shown by DOUPAD Host on your PC") }
    var lastScanned by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf("") }

    fun connect(raw: String) {
        val endpoint = PcPairingUri.parse(raw)
        if (endpoint == null) {
            status = "That QR is not a valid DOUPAD PC pairing code"
            return
        }
        lastScanned = raw
        status = "Connecting to ${endpoint.name ?: endpoint.host}…"
        viewModel.connectNetwork(endpoint.host, endpoint.port, endpoint.pin, onSuccess = onConnected)
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            status = "Scan cancelled — you can scan again or enter the PC address manually"
        } else {
            connect(contents)
        }
    }

    fun launchScanner() {
        scanner.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan the QR shown in DOUPAD Host")
                setBeepEnabled(false)
                setOrientationLocked(false)
                setBarcodeImageEnabled(false)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect PC by QR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(76.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(18.dp))
            Text("Fast PC Pairing", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(22.dp))

            Button(onClick = ::launchScanner, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Scan DOUPAD Host QR")
            }

            Spacer(Modifier.height(22.dp))
            HorizontalDivider()
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text("PC IP or pairing link") },
                placeholder = { Text("192.168.1.20:27845") },
                leadingIcon = { Icon(Icons.Default.Link, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { connect(manual.trim()) },
                enabled = manual.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect manually")
            }

            lastScanned?.let {
                Spacer(Modifier.height(16.dp))
                Text("Last scanned: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
