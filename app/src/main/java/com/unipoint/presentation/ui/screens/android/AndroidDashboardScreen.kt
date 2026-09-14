package com.unipoint.presentation.ui.screens.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.domain.model.AdbCommand
import com.unipoint.domain.model.InstalledApp
import com.unipoint.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * atvTools-style hub after ADB connect:
 * bottom nav = Tools | Apps | Shell | Info | Remote
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidDashboardScreen(
    onBack: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenMirror: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    var tab by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    fun returnToDevices() {
        viewModel.disconnectAndroid()
        onBack()
    }
    BackHandler { returnToDevices() }
    val tabs = listOf("Tools", "Apps", "Shell", "Info", "Remote")
    val icons = listOf(
        Icons.Default.Build, Icons.Default.Apps, Icons.Default.Terminal,
        Icons.Default.Info, Icons.Default.SettingsRemote
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Android Device", fontWeight = FontWeight.Bold)
                        Text("Connected", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { returnToDevices() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Devices")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = {
                            if (i == 4) onOpenRemote() else tab = i
                        },
                        icon = { Icon(icons[i], label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> ToolsTab(viewModel, onOpenFiles, onOpenMirror)
                1 -> AppsTab(viewModel)
                2 -> ShellTab(viewModel)
                3 -> InfoTab(viewModel)
            }
        }
    }
}

@Composable
private fun ToolsTab(vm: MainViewModel, onOpenFiles: () -> Unit, onOpenMirror: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var shotPath by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pendingDanger by remember { mutableStateOf<String?>(null) }

    pendingDanger?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDanger = null },
            title = { Text(if (action == "power") "Power off device?" else "Reboot device?") },
            text = { Text("This will interrupt anything currently running on the connected device.") },
            confirmButton = {
                TextButton(onClick = {
                    if (action == "power") vm.executeAdb(AdbCommand.Shell("reboot -p"))
                    else vm.executeAdb(AdbCommand.Reboot)
                    pendingDanger = null
                }) { Text(if (action == "power") "Power off" else "Reboot") }
            },
            dismissButton = { TextButton(onClick = { pendingDanger = null }) { Text("Cancel") } }
        )
    }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                msg = "Installing APK…"
                val temp = File(ctx.cacheDir, "install-${System.currentTimeMillis()}.apk")
                val copyResult = runCatching {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            temp.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Cannot open selected APK")
                    }
                }
                if (copyResult.isFailure) {
                    msg = "Install failed: ${copyResult.exceptionOrNull()?.message}"
                } else {
                    vm.executeAdbResult(AdbCommand.Install(temp.absolutePath))
                        .onSuccess { msg = "APK installed successfully" }
                        .onFailure { msg = "Install failed: ${it.message}" }
                }
                temp.delete()
            }
        }
    }

    val tools = listOf(
        ToolItem(Icons.Default.GetApp, "Install APK") {
            apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
        },
        ToolItem(Icons.Default.Folder, "File manager", onOpenFiles),
        ToolItem(Icons.Default.ScreenshotMonitor, "Screen Mirror", onOpenMirror),
        ToolItem(Icons.Default.Screenshot, "Screenshot") {
            scope.launch {
                msg = "Capturing…"
                val path = File(ctx.cacheDir, "tv_shot.png").absolutePath
                File(path).delete()
                val result = vm.executeAdbScreenshot(path)
                if (result.isSuccess && File(path).exists() && File(path).length() > 100) {
                    shotPath = path
                    msg = "Screenshot captured successfully"
                } else {
                    msg = "Capture failed: ${result.exceptionOrNull()?.message ?: "check ADB connection"}"
                }
            }
        },
        ToolItem(Icons.Default.Cached, "Clear cache") {
            vm.executeAdb(AdbCommand.Shell("pm trim-caches 999G"))
        },
        ToolItem(Icons.Default.RestartAlt, "Reboot") { pendingDanger = "reboot" },
        ToolItem(Icons.Default.PowerSettingsNew, "Power off") { pendingDanger = "power" },
        ToolItem(Icons.Default.Home, "Home") {
            vm.executeAdb(AdbCommand.InputKey(3))
        },
        ToolItem(Icons.Default.Settings, "Settings") {
            vm.executeAdb(AdbCommand.Shell("am start -a android.settings.SETTINGS"))
        }
    )

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        msg?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tools) { t ->
                Card(
                    onClick = t.onClick,
                    modifier = Modifier.height(100.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(t.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(t.title, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        shotPath?.let { path ->
            val f = File(path)
            if (f.exists() && f.length() > 100) {
                Spacer(Modifier.height(16.dp))
                Text("Last screenshot", fontWeight = FontWeight.SemiBold)
                val bmp = remember(path, f.length()) { BitmapFactory.decodeFile(path) }
                bmp?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(200.dp))
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    try {
                        val name = "unipoint_${System.currentTimeMillis()}.png"
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                            if (android.os.Build.VERSION.SDK_INT >= 29) {
                                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UniPoint")
                            }
                        }
                        val uri = ctx.contentResolver.insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                        )
                        uri?.let { u ->
                            ctx.contentResolver.openOutputStream(u)?.use { out ->
                                java.io.FileInputStream(f).use { it.copyTo(out) }
                            }
                            msg = "Saved to Gallery (Pictures/UniPoint)"
                        }
                    } catch (e: Exception) {
                        msg = "Save failed: ${e.message}"
                    }
                }) { Text("Save to Gallery") }
            }
        }
    }
}

private data class ToolItem(val icon: ImageVector, val title: String, val onClick: () -> Unit)

@Composable
private fun AppsTab(vm: MainViewModel) {
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var uninstallTarget by remember { mutableStateOf<InstalledApp?>(null) }
    val iconPaths = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()

    uninstallTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = { Text("Uninstall ${target.label}?") },
            text = { Text(target.packageName) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        vm.executeAdbResult(AdbCommand.Uninstall(target.packageName))
                            .onSuccess {
                                apps = apps.filterNot { it.packageName == target.packageName }
                                message = "${target.label} uninstalled"
                            }
                            .onFailure { message = "Uninstall failed: ${it.message}" }
                    }
                    uninstallTarget = null
                }) { Text("Uninstall", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { uninstallTarget = null }) { Text("Cancel") } }
        )
    }

    LaunchedEffect(Unit) {
        loading = true
        apps = vm.listApps().getOrElse { emptyList() }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val filteredApps = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            label = { Text("Search ${apps.size} apps") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        message?.let {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                LaunchedEffect(app.packageName) {
                    if (!iconPaths.containsKey(app.packageName) || app.versionName == null) {
                        vm.loadAppPresentation(app.packageName)?.let { presentation ->
                            apps = apps.map { if (it.packageName == presentation.app.packageName) presentation.app else it }
                            presentation.iconPath?.let { iconPaths[app.packageName] = it }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconPath = iconPaths[app.packageName]
                        if (iconPath != null) {
                            val icon = remember(iconPath) {
                                BitmapFactory.decodeFile(iconPath)?.asImageBitmap()
                            }
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Icon(Icons.Default.Android, null, Modifier.size(42.dp))
                            }
                        } else {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Android, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.label.ifBlank { "Unknown app" },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            app.versionName?.let {
                                Text("v$it", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    downloading = app.packageName
                                    val result = vm.downloadApp(app.packageName)
                                    downloading = null
                                    message = result.fold(
                                        onSuccess = { "Downloaded ${app.label} → $it" },
                                        onFailure = { "Download failed: ${it.message}" }
                                    )
                                }
                            },
                            enabled = downloading == null
                        ) {
                            if (downloading == app.packageName) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, "Download APK")
                            }
                        }

                        IconButton(onClick = {
                            scope.launch {
                                vm.executeAdb(AdbCommand.Shell("am force-stop ${app.packageName}"))
                                message = "${app.label} stopped"
                            }
                        }) {
                            Icon(Icons.Default.StopCircle, "Force stop")
                        }

                        IconButton(onClick = { uninstallTarget = app }) {
                            Icon(Icons.Default.Delete, "Uninstall")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellTab(vm: MainViewModel) {
    var cmd by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            output.ifBlank { "Shell ready. Type a command below." },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cmd,
                onValueChange = { cmd = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Shell command") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                scope.launch {
                    val r = vm.executeAdbAwait(cmd)
                    output = (output + "\n$ $cmd\n$r").takeLast(8000)
                    cmd = ""
                }
            }) { Text("Run") }
        }
    }
}

@Composable
private fun InfoTab(vm: MainViewModel) {
    var info by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadInfo() {
        loading = true
        error = null
        vm.deviceInfo()
            .onSuccess { info = it }
            .onFailure { error = it.message ?: "Unable to read device information" }
        loading = false
    }

    LaunchedEffect(Unit) { loadInfo() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Device overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(info["model"]?.takeIf { it != "-" }, info["ip"]?.takeIf { it != "-" }).joinToString(" • ").ifBlank { "Live device details" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { scope.launch { loadInfo() } }, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "Refresh")
            }
        }

        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        val pairs = listOf(
            "Model" to "model", "Brand" to "brand",
            "Android" to "android", "SDK" to "sdk",
            "Display" to "display", "Density" to "density",
            "RAM" to "ram", "Battery" to "battery",
            "IP address" to "ip", "Serial" to "serial"
        )
        pairs.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { (label, key) ->
                    CompactInfoTile(label, info[key] ?: "-", Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        CompactInfoTile("Storage", info["storage"] ?: "-", Modifier.fillMaxWidth())
        CompactInfoTile("Uptime", info["uptime"] ?: "-", Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CompactInfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value.ifBlank { "-" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}
