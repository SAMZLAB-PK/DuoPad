package com.unipoint.presentation.ui.screens.android

import android.content.ClipData
import com.unipoint.presentation.ui.theme.DuoColors
import android.content.ClipboardManager
import android.content.Context
import com.unipoint.core.util.PcDiagnosticRecorder
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
import androidx.compose.material.icons.filled.DeleteOutline
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
    var confirmClear by remember { mutableStateOf(false) }
    val diagnostics=remember(refresh) {
        val breadcrumbs = runCatching {
            File(context.filesDir,"doupad_mirror_diagnostics.txt").readText().takeLast(15000)
        }.getOrDefault("No local mirror logs yet.")
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
        val pcLog = runCatching { PcDiagnosticRecorder.file(context).readText().takeLast(16000) }
            .getOrDefault("No PC scan/connection logs yet. Tap Search PC to start diagnostics.")
        "PC DISCOVERY AND CONNECTION LOG\n$pcLog\n\nMIRROR EVENT LOG\n$breadcrumbs\n\nANDROID PROCESS EXIT HISTORY\n$previousExits"
    }
    Scaffold(
        topBar={
            TopAppBar(
                title={Text("Diagnostic logs")},
                navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},
                actions={
                    IconButton(onClick={refresh++}){Icon(Icons.Default.Refresh,"Refresh")}
                    IconButton(onClick={confirmClear=true}){
                        Icon(Icons.Default.DeleteOutline,"Clear PC and mirror logs")
                    }
                }
            )
        }
    ) { padding ->
        if(confirmClear) {
            AlertDialog(
                onDismissRequest={confirmClear=false},
                title={Text("Clear diagnostic logs?")},
                text={Text("Deletes the local PC connection/discovery and mirror logs. Android process-exit history is managed by the OS.")},
                confirmButton={TextButton(onClick={
                    com.unipoint.core.util.MirrorCrashRecorder.clear(context)
                    PcDiagnosticRecorder.clear(context)
                    refresh++
                    confirmClear=false
                }){Text("Clear logs")}},
                dismissButton={TextButton(onClick={confirmClear=false}){Text("Cancel")}}
            )
        }
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Shows PC scan phases, found hosts, connection errors, mirror events, and Android process exits. Logs stay on this phone; passwords and PINs are never logged.",
                style=MaterialTheme.typography.bodySmall,color=DuoColors.OnSurfaceMuted)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                Button(onClick={
                    val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("DuoPad diagnostics",diagnostics))
                },modifier=Modifier.weight(1f)){
                    Icon(Icons.Default.ContentCopy,null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy log")
                }
                OutlinedButton(onClick={confirmClear=true},modifier=Modifier.weight(1f)) {
                    Icon(Icons.Default.DeleteOutline,null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear old logs")
                }
            }
            Surface(modifier=Modifier.fillMaxSize(),color=DuoColors.Console,shape=MaterialTheme.shapes.medium) {
                Text(diagnostics,
                    color=DuoColors.ConsoleText,
                    fontFamily=FontFamily.Monospace,
                    style=MaterialTheme.typography.bodySmall,
                    modifier=Modifier.verticalScroll(rememberScrollState()).padding(12.dp))
            }
        }
    }
}
