package com.unipoint.presentation.ui.screens.android

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.core.util.MirrorFramePacing
import com.unipoint.domain.model.AdbCommand
import com.unipoint.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream

/**
 * Screenshot-based mirror with tap → ADB input.
 * Continuous refresh targets about 450 ms per frame when capture is fast;
 * this is intentionally not a video/scrcpy stream.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenMirrorScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var imgW by remember { mutableIntStateOf(1920) }
    var imgH by remember { mutableIntStateOf(1080) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var live by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Starting…") }
    val cacheFile = remember { File(context.cacheDir, "mirror.png") }
    val captureGate = remember { Mutex() }

    suspend fun captureOnce(): Boolean = captureGate.withLock {
        loading = true
        val startedAt = System.currentTimeMillis()
        try {
            cacheFile.delete()
            val result = viewModel.executeAdbScreenshot(cacheFile.absolutePath)
            val ok = result.isSuccess && cacheFile.exists() && cacheFile.length() > 100
            if (ok) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath, opts)
                if (bmp != null) {
                    imgW = bmp.width
                    imgH = bmp.height
                    bitmap = bmp.asImageBitmap()
                    status = "${bmp.width}×${bmp.height}"
                } else status = "Decode failed"
            } else status = "No frame – check ADB"
            ok
        } catch (e: Exception) {
            status = "Mirror error: ${e.message ?: "capture failed"}"
            false
        } finally {
            loading = false
        }
    }

    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        while (isActive) {
            val startedAt = System.currentTimeMillis()
            captureOnce()
            val finishedAt = System.currentTimeMillis()
            kotlinx.coroutines.delay(
                MirrorFramePacing.delayAfterCapture(
                    captureStartedAtMs = startedAt,
                    captureFinishedAtMs = finishedAt
                )
            )
        }
    }

    fun saveToGallery() {
        if (!cacheFile.exists()) {
            Toast.makeText(context, "No screenshot yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val name = "unipoint_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UniPoint")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(cacheFile).use { it.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun mapTap(x: Float, y: Float) {
        if (viewSize.width == 0 || viewSize.height == 0) return
        // ContentScale.Fit mapping
        val scale = minOf(
            viewSize.width.toFloat() / imgW,
            viewSize.height.toFloat() / imgH
        )
        val dispW = imgW * scale
        val dispH = imgH * scale
        val offX = (viewSize.width - dispW) / 2f
        val offY = (viewSize.height - dispH) / 2f
        val rx = ((x - offX) / scale).toInt().coerceIn(0, imgW - 1)
        val ry = ((y - offY) / scale).toInt().coerceIn(0, imgH - 1)
        scope.launch {
            viewModel.executeAdb(AdbCommand.InputTap(rx, ry))
            status = "Tap $rx,$ry"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Screen Mirror")
                        Text(status, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { captureOnce() } }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { saveToGallery() }) {
                        Icon(Icons.Default.Download, "Save")
                    }
                    FilterChip(
                        selected = live,
                        onClick = { live = !live },
                        label = { Text(if (live) "LIVE" else "PAUSED") }
                    )
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .onSizeChanged { viewSize = it }
                .pointerInput(imgW, imgH, viewSize) {
                    detectTapGestures { offset ->
                        mapTap(offset.x, offset.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "TV screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (loading) CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(status, color = Color.White.copy(alpha = 0.7f))
                }
            }
            if (loading && bmp != null) {
                LinearProgressIndicator(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                )
            }
        }
    }
}
