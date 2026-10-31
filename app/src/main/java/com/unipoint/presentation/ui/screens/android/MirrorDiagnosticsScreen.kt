package com.unipoint.presentation.ui.screens.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorDiagnosticsScreen(onBack:()->Unit) {
    val context=LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val diagnostics=remember(refresh) {
        val breadcrumbs = runCatching {
            File(context.filesDir,"doupad_mirror_diagnostics.txt").readText().takeLast(15000)
        }.getOrDefault("No local mirror startup marker. Please verify v0.8 is installed.")
        val previousExits = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val manager = context.getSystemService(ActivityManager::class.java)
                manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                    .take(5)
                    .joinToString("\n") { info ->
                        val reason = when (info.reason) {
                            ApplicationExitInfo.REASON_CRASH -> "Java crash"
                            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
                            ApplicationExitInfo.REASON_ANR -> "App not responding"
                            ApplicationExitInfo.REASON_LOW_MEMORY -> "Low memory"
                            ApplicationExitInfo.REASON_USER_REQUESTED -> "User exit"
                            else -> "System reason ${info.reason}"
                        }
                        "${DateFormat.getDateTimeInstance().format(Date(info.timestamp))}: $reason; ${info.description.orEmpty().take(350)}"
                    }.ifBlank { "No historical process exits" }
            }.getOrDefault("Exit reasons unavailable on this device")
        } else "Android OS exit reasons require Android 11+"
        "MIRROR EVENT LOG\n$breadcrumbs\n\nANDROID PROCESS EXIT HISTORY\n$previousExits"
    }
    Scaffold(
        topBar={
            TopAppBar(
                title={Text("Mirror diagnostics")},
                navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},
                actions={
                    IconButton(onClick={refresh++}){Icon(Icons.Default.Refresh,"Refresh")}
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Shows startup breadcrumbs, encoder/decoder events and Android process exits, even when the video screen closes unexpectedly. Data stays on this phone.",
                style=MaterialTheme.typography.bodySmall,color=Color(0xFF667587))
            Button(onClick={
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("DouPad mirror diagnostics",diagnostics))
            },modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Default.ContentCopy,null)
                Spacer(Modifier.width(8.dp))
                Text("Copy diagnostic log")
            }
            Surface(modifier=Modifier.fillMaxSize(),color=Color(0xFF111A27),shape=MaterialTheme.shapes.medium) {
                Text(diagnostics,
                    color=Color.White,
                    fontFamily=FontFamily.Monospace,
                    style=MaterialTheme.typography.bodySmall,
                    modifier=Modifier.verticalScroll(rememberScrollState()).padding(12.dp))
            }
        }
    }
}
