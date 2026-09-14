package com.unipoint.presentation.ui.screens.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unipoint.presentation.ui.components.ProfessionalTouchpad

@Composable
fun PcModeContent(
    onConnectBluetooth: () -> Unit,
    onConnectNetwork: (ip: String, port: Int, pin: String?) -> Unit,
    onMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (Float) -> Unit,
    onOpenKeyboard: () -> Unit,
    onOpenGamepad: () -> Unit
) {
    var showIpDialog by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }

    Column {
        // Connection methods
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onConnectBluetooth,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Bluetooth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bluetooth HID")
            }
            FilledTonalButton(
                onClick = { showIpDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Wifi, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Network")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Professional Touchpad
        ProfessionalTouchpad(
            onMove = onMove,
            onLeftClick = onLeftClick,
            onRightClick = onRightClick,
            onScroll = onScroll
        )

        Spacer(Modifier.height(12.dp))

        // Mouse buttons
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onLeftClick, modifier = Modifier.weight(1f)) {
                Text("Left")
            }
            Button(onClick = onRightClick, modifier = Modifier.weight(1f)) {
                Text("Right")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Advanced controls
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onOpenKeyboard, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Keyboard, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Keyboard")
            }
            OutlinedButton(onClick = onOpenGamepad, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.SportsEsports, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Gamepad")
            }
        }

    }

    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Connect to Host") },
            text = {
                Column {
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text("PC address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("PIN (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showIpDialog = false
                    onConnectNetwork(ipInput.trim(), 27845, pinInput.ifBlank { null })
                }) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) { Text("Cancel") }
            }
        )
    }
}
