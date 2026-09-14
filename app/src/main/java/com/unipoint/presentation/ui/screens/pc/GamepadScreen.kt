package com.unipoint.presentation.ui.screens.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gamepad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Left stick area
            Box(
                Modifier
                    .size(140.dp)
                    .align(Alignment.BottomStart)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            // Send axis values
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("L-Stick", style = MaterialTheme.typography.labelMedium)
            }

            // Right stick
            Box(
                Modifier
                    .size(140.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("R-Stick", style = MaterialTheme.typography.labelMedium)
            }

            // Face buttons
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GamepadButton("Y", Color(0xFFFFEB3B)) { }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GamepadButton("X", Color(0xFF2196F3)) { }
                    GamepadButton("B", Color(0xFFF44336)) { }
                }
                GamepadButton("A", Color(0xFF4CAF50)) { }
            }

            // D-Pad
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GamepadButton("↑", MaterialTheme.colorScheme.primary) { }
                Row {
                    GamepadButton("←", MaterialTheme.colorScheme.primary) { }
                    Spacer(Modifier.width(40.dp))
                    GamepadButton("→", MaterialTheme.colorScheme.primary) { }
                }
                GamepadButton("↓", MaterialTheme.colorScheme.primary) { }
            }
        }
    }
}

@Composable
private fun GamepadButton(label: String, color: Color, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(alpha = 0.3f)),
        modifier = Modifier.size(52.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
