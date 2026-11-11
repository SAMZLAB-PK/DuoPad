package com.unipoint.presentation.ui.screens.pc

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unipoint.data.remote.PcWorkspaceClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Local PC companion workspace: screen, files/drives, system info.
 * Keyboard and mouse controls travel through the existing authenticated input link.
 */
@Composable
fun PcWorkspaceScreen(
    host: String,
    port: Int,
    pin: String,
    onBack: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (Float) -> Unit,
    onText: (String) -> Unit,
    onKey: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(host, port, pin) { PcWorkspaceClient(host, port, pin) }
    DisposableEffect(client) { onDispose { client.close() } }
    var tab by remember { mutableStateOf("Screen") }
    var message by remember { mutableStateOf("Connecting to PC Host…") }
    var busy by remember { mutableStateOf(false) }
    var desktop by remember { mutableStateOf<Bitmap?>(null) }
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var driveData by remember { mutableStateOf<JSONObject?>(null) }
    var directory by remember { mutableStateOf<JSONObject?>(null) }
    var folder by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var keyboardText by remember { mutableStateOf("") }
    var selectedDownloadPath by remember { mutableStateOf<String?>(null) }
    var selectedUploadFolder by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableIntStateOf(55) }
    var previewWidth by remember { mutableIntStateOf(960) }

    val downloadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val remote = selectedDownloadPath
        selectedDownloadPath = null
        if (uri != null && remote != null) scope.launch {
            busy = true
            try {
                val bytes = client.download(context, remote, uri)
                message = "Saved ${bytes / 1024} KB to your phone"
            } catch (ex: Exception) { message = "Download failed: ${ex.message}" }
            finally { busy = false }
        }
    }
    val uploadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = selectedUploadFolder
        selectedUploadFolder = null
        if (uri != null && target != null) scope.launch {
            busy = true
            try {
                client.upload(context, uri, target)
                message = "Uploaded successfully to PC"
                reload++
            } catch (ex: Exception) { message = "Upload failed: ${ex.message}" }
            finally { busy = false }
        }
    }

    LaunchedEffect(tab, folder, reload, quality, previewWidth) {
        try {
            when (tab) {
                "Screen" -> {
                    message = "Live PC desktop preview · LAN"
                    var frameCount = 0
                    while (isActive) {
                        desktop = client.desktopFrame(previewWidth, quality)
                        frameCount++
                        if (frameCount == 1) message = "Live PC desktop connected"
                        delay(35) // pull only the newest frame, no queue/backlog
                    }
                }
                "Files" -> {
                    if (folder.isBlank()) driveData = client.drives()
                    else directory = client.directory(folder)
                }
                "Info" -> info = client.information()
            }
        } catch (_: CancellationException) {
            // Switching tabs cancels the previous stream without an error toast.
        } catch (ex: Exception) { message = "PC Workspace: ${ex.message}" }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("DuoPad PC Workspace", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge)
                Text("$host:$port", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Screen", "Files", "Info").forEach { option ->
                FilterChip(selected = tab == option, onClick = {
                    tab = option
                    if (option == "Files") folder = ""
                }, label = { Text(option) }, modifier = Modifier.weight(1f))
            }
        }
        when (tab) {
            "Screen" -> {
                Box(Modifier.fillMaxWidth().padding(12.dp).aspectRatio(16f/9f)
                    .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) {
                    val image = desktop
                    if (image != null) {
                        Image(image.asImageBitmap(), "Windows desktop", Modifier.fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures(onTap = { onLeftClick() }) }
                            .pointerInput(Unit) { detectDragGestures { change, drag ->
                                change.consume()
                                onMove(drag.x * 1.4f, drag.y * 1.4f)
                            } })
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Computer, null, Modifier.size(46.dp))
                            Text("Opening PC screen…")
                        }
                    }
                }
                Text("Touch = click · Drag = move pointer · PC Host must remain open",
                    Modifier.padding(horizontal = 15.dp), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onLeftClick, modifier = Modifier.weight(1f)) { Text("Left click") }
                    OutlinedButton(onClick = onRightClick, modifier = Modifier.weight(1f)) { Text("Right click") }
                    OutlinedButton(onClick = { onScroll(3f) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Scroll up")
                    }
                    OutlinedButton(onClick = { onScroll(-3f) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Scroll down")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Quality", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { quality = 45; previewWidth = 720 }) { Text("Fast") }
                    TextButton(onClick = { quality = 60; previewWidth = 960 }) { Text("Balanced") }
                    TextButton(onClick = { quality = 75; previewWidth = 1280 }) { Text("Sharp") }
                }
                OutlinedTextField(value = keyboardText, onValueChange = { keyboardText = it },
                    label = { Text("Type on Windows PC") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (keyboardText.isNotEmpty()) { onText(keyboardText); keyboardText = "" } },
                        modifier = Modifier.weight(1f)) { Text("Send text") }
                    OutlinedButton(onClick = { onKey(40) }) { Text("Enter") }
                    OutlinedButton(onClick = { onKey(41) }) { Text("Esc") }
                }
            }
            "Files" -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (folder.isBlank()) "This PC · Drives" else folder,
                        modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = {
                        if (folder.isBlank()) reload++
                        else folder = directory?.optString("parent").orEmpty().takeIf { it != folder } ?: ""
                    }) { Text(if (folder.isBlank()) "Refresh" else "Up") }
                }
                if (folder.isNotBlank()) {
                    Button(onClick = {
                        selectedUploadFolder = folder
                        uploadPicker.launch(arrayOf("*/*"))
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        Icon(Icons.Default.Upload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Upload file from phone")
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)) {
                    if (folder.isBlank()) {
                        val drives = driveData?.optJSONArray("drives")
                        if (drives == null) Text("Loading drives…")
                        else for (idx in 0 until drives.length()) {
                            val drive = drives.getJSONObject(idx)
                            val path = drive.getString("path")
                            ListItem(headlineContent = { Text(path) },
                                supportingContent = {
                                    val free = drive.optLong("free", -1)
                                    if (free >= 0) Text("${free / (1024L * 1024L * 1024L)} GB free")
                                }, leadingContent = { Icon(Icons.Default.Storage, null) },
                                modifier = Modifier.clickable { folder = path })
                            HorizontalDivider()
                        }
                    } else {
                        val items = directory?.optJSONArray("entries")
                        if (items == null) Text("Loading folder…")
                        else for (idx in 0 until items.length()) {
                            val item = items.getJSONObject(idx)
                            val isDirectory = item.getBoolean("isDir")
                            val path = item.getString("path")
                            val name = item.getString("name")
                            ListItem(
                                headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    if (!isDirectory) Text("${item.optLong("size") / 1024} KB")
                                },
                                leadingContent = { Icon(if (isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null) },
                                trailingContent = {
                                    Icon(if (isDirectory) Icons.Default.ChevronRight else Icons.Default.Download, null)
                                },
                                modifier = Modifier.clickable {
                                    if (isDirectory) folder = path else {
                                        selectedDownloadPath = path
                                        downloadPicker.launch(name)
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                        if (directory?.optBoolean("truncated") == true) {
                            Text("First 180 entries shown", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            "Info" -> {
                val data = info
                if (data == null) Text("Loading PC information…", Modifier.padding(14.dp))
                else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("name" to "PC Name", "os" to "Windows / OS", "cpu" to "Processor",
                        "cpuThreads" to "Logical CPU Threads", "architecture" to "Architecture",
                        "ramTotal" to "Installed Memory (bytes)", "ramAvailable" to "Available RAM (bytes)",
                        "home" to "User directory", "drives" to "Drive count").forEach { (key, label) ->
                        Text(label, fontWeight = FontWeight.Bold)
                        Text(data.optString(key, "Not available"), style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider()
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(message, Modifier.fillMaxWidth().padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
