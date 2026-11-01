package com.unipoint.presentation.ui.screens.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    airMouse: Boolean = false,
    onSwipeMode: (Int) -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sensorManager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val gyro = remember(sensorManager) { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    var motionEnabled by remember(airMouse) { mutableStateOf(airMouse) }
    var headerSwipe by remember { mutableFloatStateOf(0f) }
    val latestPosition = remember { LatestPositionBuffer() }
    var screenW by remember { mutableIntStateOf(1920) }
    var screenH by remember { mutableIntStateOf(1080) }
    var x by remember { mutableFloatStateOf(screenW / 2f) }
    var y by remember { mutableFloatStateOf(screenH / 2f) }
    var sensitivity by remember { mutableFloatStateOf(0.95f) }
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

    // Air Mouse uses gyroscope angular velocity while this screen is foreground.
    // Sensors are released immediately when navigating back or turning motion off.
    DisposableEffect(motionEnabled, gyro, sensitivity, screenW, screenH) {
        if (!motionEnabled || gyro == null) {
            if (motionEnabled && gyro == null) status = "No gyroscope on this phone. Use touchpad instead."
            onDispose { }
        } else {
            var previousTime = 0L
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
                    val dt = if (previousTime == 0L) 0f
                        else ((event.timestamp - previousTime) / 1_000_000_000f).coerceIn(0f, 0.055f)
                    previousTime = event.timestamp
                    if (dt == 0f) return
                    val yaw = event.values[1]
                    val pitch = event.values[0]
                    if (kotlin.math.abs(yaw) < 0.015f && kotlin.math.abs(pitch) < 0.015f) return
                    x = (x - yaw * dt * screenW * sensitivity * 0.6f)
                        .coerceIn(0f, (screenW - 1).toFloat())
                    y = (y - pitch * dt * screenH * sensitivity * 0.6f)
                        .coerceIn(0f, (screenH - 1).toFloat())
                    latestPosition.offer(x.toInt(), y.toInt())
                }
            }
            sensorManager.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager.unregisterListener(listener) }
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
            val result = viewModel.executeAdbResult(
                AdbCommand.PointerMove(position.x, position.y, screenW, screenH)
            )
            if(result.isFailure) {
                status = "Pointer unavailable: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                delay(180)
            }
            // ~120 Hz input sampling; the TV/mirror may render at a lower refresh rate.
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            delay((8L - elapsedMs).coerceAtLeast(0L))
        }
    }

    fun click(button: MouseButton) {
        scope.launch {
            val px = x.toInt().coerceIn(0, screenW - 1)
            val py = y.toInt().coerceIn(0, screenH - 1)
            val result=viewModel.executeAdbResult(
                AdbCommand.PointerClick(px, py, screenW, screenH, button)
            )
            if(result.isFailure)status="Click failed: ${result.exceptionOrNull()?.message}"
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
        containerColor = Color(0xFFFCFAF8),
        topBar = {
            TopAppBar(
                modifier = Modifier.pointerInput(airMouse) {
                    detectHorizontalDragGestures(
                        onDragStart = { headerSwipe = 0f },
                        onHorizontalDrag = { _, amount -> headerSwipe += amount },
                        onDragEnd = {
                            if (headerSwipe < -90f) onSwipeMode(1)
                            else if (headerSwipe > 90f) onSwipeMode(-1)
                            headerSwipe = 0f
                        }
                    )
                },
                title = {
                    Column {
                        Text(if (airMouse) "Air Mouse" else "Mouse Pad", fontWeight = FontWeight.Bold)
                        Text("Android TV / Google TV", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(Color(0xFFFCFAF8)).padding(padding)
        ) {
            Column(
                Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (airMouse) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("Motion control", modifier=Modifier.weight(1f),color=Color(0xFF171717))
                        Switch(checked=motionEnabled && gyro != null,
                            onCheckedChange={motionEnabled=it},enabled=gyro != null)
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2EFEB))
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
                            Text(if(airMouse)"Tilt phone to move cursor" else "Touchpad",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("${x.toInt()} × ${y.toInt()}  •  ${screenW}×${screenH}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pointer speed", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = sensitivity,
                            onValueChange = { sensitivity = it },
                            valueRange = 0.45f..2.5f,
                            steps = 7
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { click(MouseButton.LEFT) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Default.TouchApp, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Left",maxLines=1)
                    }
                    FilledTonalButton(onClick = { click(MouseButton.RIGHT) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Default.Mouse, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Right",maxLines=1)
                    }
                    FilledTonalButton(
                        onClick = { viewModel.executeAdb(AdbCommand.InputKey(4)) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Back")
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { scroll(1) }, modifier = Modifier.weight(1f).height(44.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, null); Spacer(Modifier.width(5.dp)); Text("Up",maxLines=1)
                    }
                    OutlinedButton(onClick = { scroll(-1) }, modifier = Modifier.weight(1f).height(44.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, null); Spacer(Modifier.width(5.dp)); Text("Down",maxLines=1)
                    }
                }
            }
        }
    }
}
