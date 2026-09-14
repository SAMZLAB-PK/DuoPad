package com.unipoint.presentation.ui.screens.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.domain.model.AdbCommand
import com.unipoint.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidRemoteScreen(
    onBack: () -> Unit,
    onOpenMousePad: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    fun key(code: Int) = viewModel.executeAdb(AdbCommand.InputKey(code))
    var showNumbers by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Remote", fontWeight = FontWeight.Bold)
                        Text("Android TV / Google TV", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF050B14), Color(0xFF0A1B2A), Color(0xFF07111F)))
            )
        ) {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RemoteAction(Icons.Default.PowerSettingsNew, "Power") { key(26) }
                            RemoteAction(Icons.Default.Home, "Home") { key(3) }
                            RemoteAction(Icons.AutoMirrored.Filled.ArrowBack, "Back") { key(4) }
                            RemoteAction(Icons.Default.Menu, "Menu") { key(82) }
                        }

                        DPad(
                            up = { key(19) }, down = { key(20) }, left = { key(21) }, right = { key(22) }, ok = { key(23) }
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ControlRail(
                                title = "VOLUME",
                                topIcon = Icons.Default.VolumeUp,
                                bottomIcon = Icons.Default.VolumeDown,
                                onTop = { key(24) },
                                onBottom = { key(25) },
                                centerIcon = Icons.Default.VolumeOff,
                                onCenter = { key(164) },
                                modifier = Modifier.weight(1f)
                            )
                            ControlRail(
                                title = "CHANNEL",
                                topIcon = Icons.Default.KeyboardArrowUp,
                                bottomIcon = Icons.Default.KeyboardArrowDown,
                                onTop = { key(166) },
                                onBottom = { key(167) },
                                centerIcon = Icons.Default.Tv,
                                onCenter = { key(172) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Media", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { key(88) }) { Icon(Icons.Default.SkipPrevious, "Previous") }
                            IconButton(onClick = { key(89) }) { Icon(Icons.Default.FastRewind, "Rewind") }
                            FilledIconButton(onClick = { key(85) }, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Default.PlayArrow, "Play / Pause")
                            }
                            IconButton(onClick = { key(90) }) { Icon(Icons.Default.FastForward, "Forward") }
                            IconButton(onClick = { key(87) }) { Icon(Icons.Default.SkipNext, "Next") }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickKey("Apps", Icons.Default.Apps, Modifier.weight(1f)) { key(187) }
                    QuickKey("Guide", Icons.Default.List, Modifier.weight(1f)) { key(172) }
                    QuickKey("Settings", Icons.Default.Settings, Modifier.weight(1f)) { key(176) }
                    QuickKey("Info", Icons.Default.Info, Modifier.weight(1f)) { key(165) }
                }

                Button(
                    onClick = onOpenMousePad,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Icon(Icons.Default.Mouse, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mouse / Touchpad", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { showNumbers = !showNumbers },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Dialpad, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (showNumbers) "Hide number pad" else "Number pad")
                }

                if (showNumbers) {
                    NumberPad(onKey = ::key)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun DPad(up: () -> Unit, down: () -> Unit, left: () -> Unit, right: () -> Unit, ok: () -> Unit) {
    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(220.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
            tonalElevation = 4.dp
        ) {}
        DPadButton(Icons.Default.KeyboardArrowUp, Modifier.align(Alignment.TopCenter), up)
        DPadButton(Icons.Default.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter), down)
        DPadButton(Icons.Default.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart), left)
        DPadButton(Icons.Default.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd), right)
        FilledTonalButton(
            onClick = ok,
            modifier = Modifier.size(86.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("OK", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun DPadButton(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(72.dp)) {
        Icon(icon, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RemoteAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(icon, label) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ControlRail(
    title: String,
    topIcon: ImageVector,
    bottomIcon: ImageVector,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    centerIcon: ImageVector,
    onCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onTop) { Icon(topIcon, null) }
            FilledTonalIconButton(onClick = onCenter, modifier = Modifier.size(44.dp)) { Icon(centerIcon, null) }
            IconButton(onClick = onBottom) { Icon(bottomIcon, null) }
        }
    }
}

@Composable
private fun QuickKey(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(15.dp),
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun NumberPad(onKey: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { n -> NumberButton("$n") { onKey(7 + n) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NumberButton("•") { onKey(56) }
                NumberButton("0") { onKey(7) }
                FilledTonalIconButton(onClick = { onKey(67) }, modifier = Modifier.size(58.dp)) {
                    Icon(Icons.Default.Backspace, "Backspace")
                }
            }
        }
    }
}

@Composable
private fun NumberButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(58.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}
