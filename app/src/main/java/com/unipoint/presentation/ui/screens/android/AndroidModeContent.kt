package com.unipoint.presentation.ui.screens.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unipoint.domain.model.AndroidDevice

@Composable
fun AndroidModeContent(
    devices: List<AndroidDevice>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onConnect: (AndroidDevice) -> Unit,
    onOpenRemote: () -> Unit,
    onInstall: () -> Unit,
    onShell: () -> Unit,
    onScreenshot: () -> Unit,
    onReboot: () -> Unit,
    onOpenFiles: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    onOpenMirror: () -> Unit = {},
    onConnectIp: (String) -> Unit = {}
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        FilledTonalButton(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isScanning
        ) {
            if (isScanning) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Scanning…")
            } else {
                Icon(Icons.Default.Radar, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan Android Devices")
            }
        }

        Spacer(Modifier.height(12.dp))

        var manualIp by remember { mutableStateOf("192.168.10.20:5555") }
        OutlinedTextField(
            value = manualIp,
            onValueChange = { manualIp = it },
            label = { Text("TV IP:Port (manual)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { onConnectIp(manualIp) }) { Text("Connect") }
            }
        )
        Text(
            "TV pe: Settings → Developer options → Wireless debugging ON, then Allow jab prompt aaye",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        if (devices.isEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Tv, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("No devices found", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Enable Wireless debugging on your Android TV / device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            devices.forEach { device ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onConnect(device) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (device.isConnected)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tv, null, Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${device.ip}:${device.port}", style = MaterialTheme.typography.bodySmall)
                            device.model?.let {
                                Text("$it • Android ${device.androidVersion ?: "?"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("Quick Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenRemote, Modifier.weight(1f)) {
                Icon(Icons.Default.SettingsRemote, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Remote")
            }
            OutlinedButton(onClick = onInstall, Modifier.weight(1f)) {
                Icon(Icons.Default.InstallMobile, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Install")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShell, Modifier.weight(1f)) {
                Icon(Icons.Default.Terminal, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Shell")
            }
            OutlinedButton(onClick = onScreenshot, Modifier.weight(1f)) {
                Icon(Icons.Default.Screenshot, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Screenshot")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenApps, Modifier.weight(1f)) {
                Icon(Icons.Default.Apps, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Apps")
            }
            OutlinedButton(onClick = onOpenFiles, Modifier.weight(1f)) {
                Icon(Icons.Default.Folder, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Files")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenMirror, Modifier.weight(1f)) {
                Icon(Icons.Default.ScreenshotMonitor, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Mirror")
            }
            OutlinedButton(onClick = onReboot, Modifier.weight(1f)) {
                Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reboot")
            }
        }
    }
}
