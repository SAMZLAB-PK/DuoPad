package com.unipoint.presentation.ui.screens.pc

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.core.util.HidKeycodes
import com.unipoint.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val rows = listOf(
        listOf("Esc", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"),
        listOf("`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "Bksp"),
        listOf("Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]", "\\"),
        listOf("Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "Enter"),
        listOf("Shift", "Z", "X", "C", "V", "B", "N", "M", ",", ".", "/", "Shift"),
        listOf("Ctrl", "Win", "Alt", "Space", "Alt", "Ctrl", "←", "↑", "↓", "→")
    )

    var shiftPressed by remember { mutableStateOf(false) }
    var ctrlPressed by remember { mutableStateOf(false) }
    var altPressed by remember { mutableStateOf(false) }
    var winPressed by remember { mutableStateOf(false) }

    fun currentModifiers(): Int {
        var m = 0
        if (ctrlPressed) m = m or HidKeycodes.MOD_LEFT_CTRL
        if (shiftPressed) m = m or HidKeycodes.MOD_LEFT_SHIFT
        if (altPressed) m = m or HidKeycodes.MOD_LEFT_ALT
        if (winPressed) m = m or HidKeycodes.MOD_LEFT_GUI
        return m
    }

    fun pressKey(label: String) {
        when (label) {
            "Shift" -> { shiftPressed = !shiftPressed; return }
            "Ctrl" -> { ctrlPressed = !ctrlPressed; return }
            "Alt" -> { altPressed = !altPressed; return }
            "Win" -> { winPressed = !winPressed; return }
            "Caps" -> {
                viewModel.sendKey(HidKeycodes.KEY_CAPSLOCK, true, 0)
                viewModel.sendKey(HidKeycodes.KEY_CAPSLOCK, false, 0)
                return
            }
            "Space" -> {
                viewModel.sendKey(HidKeycodes.KEY_SPACE, true, currentModifiers())
                viewModel.sendKey(HidKeycodes.KEY_SPACE, false, 0)
                return
            }
        }

        val keycode = HidKeycodes.LABEL_TO_HID[label] ?: return
        val mods = currentModifiers()
        viewModel.sendKey(keycode, true, mods)
        viewModel.sendKey(keycode, false, 0)
        if (shiftPressed) shiftPressed = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Virtual Keyboard") },
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
                .padding(padding)
                .padding(8.dp)
                .fillMaxSize()
        ) {
            var text by remember { mutableStateOf("") }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    if (new.length > text.length) {
                        val added = new.substring(text.length)
                        viewModel.sendText(added)
                    }
                    text = ""
                },
                label = { Text("Type here (HID mapped)…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                singleLine = true
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = shiftPressed, onClick = { shiftPressed = !shiftPressed }, label = { Text("Shift") })
                FilterChip(selected = ctrlPressed, onClick = { ctrlPressed = !ctrlPressed }, label = { Text("Ctrl") })
                FilterChip(selected = altPressed, onClick = { altPressed = !altPressed }, label = { Text("Alt") })
                FilterChip(selected = winPressed, onClick = { winPressed = !winPressed }, label = { Text("Win") })
            }

            Spacer(Modifier.height(12.dp))

            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { label ->
                        val weight = when (label) {
                            "Space" -> 3f
                            "Bksp", "Enter", "Shift", "Caps", "Tab" -> 1.6f
                            else -> 1f
                        }
                        val selected = when (label) {
                            "Shift" -> shiftPressed
                            "Ctrl" -> ctrlPressed
                            "Alt" -> altPressed
                            "Win" -> winPressed
                            else -> false
                        }
                        if (selected) {
                            FilledTonalButton(
                                onClick = { pressKey(label) },
                                modifier = Modifier.weight(weight).height(42.dp),
                                contentPadding = PaddingValues(2.dp)
                            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                        } else {
                            OutlinedButton(
                                onClick = { pressKey(label) },
                                modifier = Modifier.weight(weight).height(42.dp),
                                contentPadding = PaddingValues(2.dp)
                            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
