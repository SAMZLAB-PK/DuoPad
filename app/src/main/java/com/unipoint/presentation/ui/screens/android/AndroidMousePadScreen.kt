package com.unipoint.presentation.ui.screens.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.core.util.LatestPositionBuffer
import com.unipoint.domain.model.AdbCommand
import com.unipoint.domain.model.MouseButton
import com.unipoint.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidMousePadScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val latestPosition = remember { LatestPositionBuffer() }
    var screenW by remember { mutableIntStateOf(1920) }
    var screenH by remember { mutableIntStateOf(1080) }
    var x by remember { mutableFloatStateOf(screenW / 2f) }
    var y by remember { mutableFloatStateOf(screenH / 2f) }
    var sensitivity by remember { mutableFloatStateOf(2.0f) }
    var status by remember { mutableStateOf("Move your finger to control the TV pointer") }

    LaunchedEffect(Unit) {
        val raw = viewModel.executeAdbAwait("wm size 2>/dev/null | tail -1")
        Regex("(\\d+)x(\\d+)").find(raw)?.let { m ->
            screenW = m.groupValues[1].toIntOrNull() ?: 1920
            screenH = m.groupValues[2].toIntOrNull() ?: 1080
            x = screenW / 2f
            y = screenH / 2f
        }
        val realtime = viewModel.executeAdbResult(AdbCommand.PrepareRealtimeInput)
        status = if (realtime.isSuccess) {
            "Realtime control ready · drag to move"
        } else {
            "Compatibility mode · realtime control unavailable"
        }
    }

    // One low-latency sender only. The single-slot buffer always keeps the newest
    // position, so even if Wi-Fi stalls briefly the cursor never replays stale moves.
    LaunchedEffect(Unit) {
        while (isActive) {
            val position = latestPosition.poll()
            if (position == null) {
                delay(2)
                continue
            }
            val startedAt = System.nanoTime()
            viewModel.executeAdbResult(
                AdbCommand.PointerMove(position.x, position.y, screenW, screenH)
            )
            // ~120 Hz input sampling; the TV/mirror may render at a lower refresh rate.
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            delay((8L - elapsedMs).coerceAtLeast(0L))
        }
    }

    fun click(button: MouseButton) {
        scope.launch {
            val px = x.toInt().coerceIn(0, screenW - 1)
            val py = y.toInt().coerceIn(0, screenH - 1)
            viewModel.executeAdbResult(
                AdbCommand.PointerClick(px, py, screenW, screenH, button)
            )
        }
    }

    fun scroll(amount: Int) {
        scope.launch {
            val px = x.toInt().coerceIn(0, screenW - 1)
            val py = y.toInt().coerceIn(0, screenH - 1)
            viewModel.executeAdbResult(
                AdbCommand.PointerScroll(
                    x = px,
                    y = py,
                    screenWidth = screenW,
                    screenHeight = screenH,
                    vScroll = if (amount > 0) 1f else -1f
                )
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mouse Pad", fontWeight = FontWeight.Bold)
                        Text("Android TV / Google TV", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF050B14), Color(0xFF081827), Color(0xFF07111F)))
            ).padding(padding)
        ) {
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f))
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .clickable { click(MouseButton.LEFT) }
                            .pointerInput(sensitivity, screenW, screenH) {
                                detectDragGestures(
                                    onDragStart = { status = "Pointer active" },
                                    onDragEnd = { status = "Tap anywhere for left click" }
                                ) { change, dragAmount ->
                                    change.consume()
                                    x = (x + dragAmount.x * sensitivity).coerceIn(0f, (screenW - 1).toFloat())
                                    y = (y + dragAmount.y * sensitivity).coerceIn(0f, (screenH - 1).toFloat())
                                    latestPosition.offer(x.toInt().coerceIn(0, screenW - 1), y.toInt().coerceIn(0, screenH - 1))
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Mouse, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(10.dp))
                            Text("Touchpad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("${x.toInt()} × ${y.toInt()}  •  ${screenW}×${screenH}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .76f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pointer speed", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = sensitivity,
                            onValueChange = { sensitivity = it },
                            valueRange = 0.8f..4.0f,
                            steps = 7
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { click(MouseButton.LEFT) }, modifier = Modifier.weight(1f).height(54.dp)) {
                        Icon(Icons.Default.TouchApp, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Left click")
                    }
                    FilledTonalButton(onClick = { click(MouseButton.RIGHT) }, modifier = Modifier.weight(1f).height(54.dp)) {
                        Icon(Icons.Default.Mouse, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Right click")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.executeAdb(AdbCommand.InputKey(4)) },
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Back")
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { scroll(1) }, modifier = Modifier.weight(1f).height(50.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, null); Spacer(Modifier.width(5.dp)); Text("Scroll up")
                    }
                    OutlinedButton(onClick = { scroll(-1) }, modifier = Modifier.weight(1f).height(50.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, null); Spacer(Modifier.width(5.dp)); Text("Scroll down")
                    }
                }
            }
        }
    }
}
