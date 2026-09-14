package com.unipoint.presentation.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.domain.model.AndroidDevice
import com.unipoint.domain.model.AppMode
import com.unipoint.domain.model.ConnectionState
import com.unipoint.domain.model.PcDevice
import com.unipoint.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onConnected: () -> Unit,
    onOpenPc: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var manualIp by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var securePc by remember { mutableStateOf<PcDevice?>(null) }
    var pcPin by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.scanAndroid()
        viewModel.scanPc()
        // A second quick pass catches a PC Host that was still starting up with the app.
        delay(2400)
        if (uiState.pcDevices.isEmpty()) viewModel.scanPc()
    }

    LaunchedEffect(uiState.pcDevices.isEmpty()) {
        if (uiState.pcDevices.isEmpty()) {
            repeat(3) {
                delay(6000)
                viewModel.scanPc()
            }
        }
    }

    LaunchedEffect(uiState.connectionStatus.state, uiState.mode) {
        if (uiState.connectionStatus.state == ConnectionState.CONNECTED && uiState.mode == AppMode.ANDROID) {
            delay(120)
            onConnected()
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    securePc?.let { pc ->
        AlertDialog(
            onDismissRequest = { securePc = null; pcPin = "" },
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text("PC Host PIN") },
            text = {
                OutlinedTextField(
                    value = pcPin,
                    onValueChange = { pcPin = it },
                    singleLine = true,
                    label = { Text(pc.address) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val (ip, port) = splitAddress(pc.address)
                    viewModel.connectNetwork(ip, port, pcPin, onSuccess = onOpenPc)
                    securePc = null
                    pcPin = ""
                }) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { securePc = null; pcPin = "" }) { Text("Cancel") }
            }
        )
    }

    ProDeviceBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("DOUPAD", fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Your devices, one friendly remote",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.scanAndroid(); viewModel.scanPc() },
                            enabled = !uiState.isScanning && !uiState.isScanningPc
                        ) {
                            if (uiState.isScanning || uiState.isScanningPc) {
                                CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, "Scan")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    HeroCard(
                        androidCount = uiState.androidDevices.size,
                        pcCount = uiState.pcDevices.size,
                        scanning = uiState.isScanning || uiState.isScanningPc
                    )
                }

                item { SectionTitle(Icons.Default.Tv, "Android / Google TV") }

                if (uiState.androidDevices.isEmpty()) {
                    item {
                        EmptyDeviceCard(
                            icon = Icons.Default.Tv,
                            title = if (uiState.isScanning) "Scanning your LAN…" else "No Android devices found",
                            subtitle = "Wireless debugging / ADB TCP must be enabled"
                        )
                    }
                } else {
                    items(uiState.androidDevices, key = { "android:${it.id}" }) { device ->
                        AndroidDeviceCard(device) { viewModel.connectAndroid(device) }
                    }
                }

                item {
                    if (showManual) {
                        CompactManualConnect(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            onConnect = { if (manualIp.isNotBlank()) viewModel.connectAndroidIp(manualIp) }
                        )
                    } else {
                        TextButton(onClick = { showManual = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AddLink, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Connect Android manually")
                        }
                    }
                }

                item { SectionTitle(Icons.Default.Computer, "PC Host") }

                if (uiState.pcDevices.isEmpty()) {
                    item {
                        EmptyDeviceCard(
                            icon = Icons.Default.Computer,
                            title = if (uiState.isScanningPc) "Looking for UniPoint Host…" else "No PC Host detected",
                            subtitle = "Start UniPoint Host on the PC. Discovery retries automatically.",
                            actionLabel = "Scan again",
                            onAction = { viewModel.scanPc() }
                        )
                    }
                } else {
                    items(uiState.pcDevices, key = { "pc:${it.id}" }) { pc ->
                        PcDeviceCard(pc) {
                            if (pc.name.contains("PIN", ignoreCase = true)) {
                                securePc = pc
                            } else {
                                val (ip, port) = splitAddress(pc.address)
                                viewModel.connectNetwork(ip, port, null, onSuccess = onOpenPc)
                            }
                        }
                    }
                }

                if (uiState.pcDevices.isEmpty()) {
                    item {
                        TextButton(
                            onClick = onOpenPc,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lan, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Can't find the PC? Open manual PC setup")
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

private fun splitAddress(address: String): Pair<String, Int> {
    val ip = address.substringBefore(':')
    val port = address.substringAfter(':', "27845").toIntOrNull() ?: 27845
    return ip to port
}

@Composable
private fun ProDeviceBackground(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pro-bg")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(tween(3200), repeatMode = RepeatMode.Reverse),
        label = "glow"
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF050B14), Color(0xFF081827), Color(0xFF07111F))
            )
        )
    ) {
        Box(
            Modifier.size(260.dp).offset(x = (-90).dp, y = (-80).dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
        )
        Box(
            Modifier.size(230.dp).align(Alignment.BottomEnd).offset(x = 90.dp, y = 70.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha * 0.75f))
        )
        content()
    }
}

@Composable
private fun HeroCard(androidCount: Int, pcCount: Int, scanning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        )
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Devices, null, tint = Color(0xFF04131A))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ready when your devices are", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (scanning) "Scanning Android + PC automatically…" else "$androidCount Android • $pcCount PC found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp, start = 2.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AndroidDeviceCard(device: AndroidDevice, onClick: () -> Unit) {
    DeviceCardBase(
        icon = Icons.Default.Tv,
        title = device.name.ifBlank { device.model ?: "Android Device" },
        subtitle = buildString {
            append("${device.ip}:${device.port}")
            device.androidVersion?.takeIf { it.isNotBlank() }?.let { append("  •  Android $it") }
        },
        connected = device.isConnected,
        onClick = onClick
    )
}

@Composable
private fun PcDeviceCard(device: PcDevice, onClick: () -> Unit) {
    DeviceCardBase(
        icon = if (device.name.contains("PIN", true)) Icons.Default.Lock else Icons.Default.Computer,
        title = device.name,
        subtitle = device.address + if (device.isConnected) "  •  Connected" else "  •  UniPoint Host verified",
        connected = device.isConnected,
        onClick = onClick
    )
}

@Composable
private fun DeviceCardBase(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    connected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactManualConnect(value: String, onValueChange: (String) -> Unit, onConnect: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Manual ADB IP:Port") },
        leadingIcon = { Icon(Icons.Default.Lan, null) },
        trailingIcon = {
            IconButton(onClick = onConnect) { Icon(Icons.Default.ArrowForward, "Connect") }
        },
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun EmptyDeviceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
