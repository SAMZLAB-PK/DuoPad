package com.unipoint.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unipoint.domain.model.AppMode
import com.unipoint.domain.model.ConnectionState
import com.unipoint.domain.model.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSwitcher(
    current: AppMode,
    onChange: (AppMode) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        SegmentedButton(
            selected = current == AppMode.PC,
            onClick = { onChange(AppMode.PC) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            icon = { Icon(Icons.Default.Computer, null, Modifier.size(18.dp)) }
        ) { Text("PC") }

        SegmentedButton(
            selected = current == AppMode.ANDROID,
            onClick = { onChange(AppMode.ANDROID) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            icon = { Icon(Icons.Default.Tv, null, Modifier.size(18.dp)) }
        ) { Text("Android") }
    }
}

@Composable
fun ConnectionStatusCard(status: ConnectionStatus) {
    val bgColor by animateColorAsState(
        targetValue = when (status.state) {
            ConnectionState.CONNECTED -> Color(0xFF1B5E20).copy(alpha = 0.3f)
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Color(0xFFF57F17).copy(alpha = 0.25f)
            ConnectionState.ERROR -> Color(0xFFB71C1C).copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(400), label = "connBg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (status.state) {
                ConnectionState.CONNECTED -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF69F0AE), modifier = Modifier.size(28.dp))
                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                ConnectionState.ERROR -> Icon(Icons.Default.Error, null, tint = Color(0xFFFF5252), modifier = Modifier.size(28.dp))
                else -> Icon(Icons.Default.LinkOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    when (status.state) {
                        ConnectionState.CONNECTED -> "Connected"
                        ConnectionState.CONNECTING -> "Connecting…"
                        ConnectionState.RECONNECTING -> "Reconnecting…"
                        ConnectionState.ERROR -> "Connection Error"
                        else -> "Not Connected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                status.deviceName?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    status.latencyMs?.let {
                        Text("${it}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    status.address?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                status.errorMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF8A80))
                }
            }
        }
    }
}

@Composable
fun ProfessionalTouchpad(
    onMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                )
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(24.dp)
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isActive = true },
                    onDragEnd = { isActive = false },
                    onDragCancel = { isActive = false },
                    onDrag = { change, amount ->
                        change.consume()
                        onMove(amount.x, amount.y)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onLeftClick() },
                    onLongPress = { onRightClick() },
                    onDoubleTap = { onLeftClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isActive) Icons.Default.TouchApp else Icons.Default.Mouse,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 1f else 0.5f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isActive) "Tracking…" else "Touchpad",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Tap • Long press • Drag",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        modifier = modifier
    )
}
