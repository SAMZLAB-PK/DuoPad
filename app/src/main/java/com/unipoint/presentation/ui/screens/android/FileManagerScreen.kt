package com.unipoint.presentation.ui.screens.android

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.domain.model.AdbCommand
import com.unipoint.domain.model.FileEntry
import com.unipoint.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var currentPath by remember { mutableStateOf("/storage/emulated/0") }
    var files by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var transferring by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<FileEntry?>(null) }
    var renameFor by remember { mutableStateOf<FileEntry?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var deleteFor by remember { mutableStateOf<FileEntry?>(null) }

    fun load(path: String) {
        scope.launch {
            loading = true
            error = null
            viewModel.listFiles(path)
                .onSuccess {
                    files = it
                    currentPath = path
                }
                .onFailure { error = it.message ?: "Unable to list files" }
            loading = false
        }
    }

    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            transferring = true
            val displayName = withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "upload_${System.currentTimeMillis()}"
            }
            val safeName = displayName.substringAfterLast('/').ifBlank { "upload.bin" }
            val temp = File(context.cacheDir, "upload-${System.currentTimeMillis()}-$safeName")
            val copied = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Cannot open selected file")
                }
            }
            if (copied.isFailure) {
                transferring = false
                snackbar.showSnackbar("Upload failed: ${copied.exceptionOrNull()?.message}")
                return@launch
            }
            val remote = joinRemote(currentPath, safeName)
            viewModel.executeAdbResult(AdbCommand.Push(temp.absolutePath, remote))
                .onSuccess {
                    snackbar.showSnackbar("Uploaded $safeName")
                    load(currentPath)
                }
                .onFailure { snackbar.showSnackbar("Upload failed: ${it.message}") }
            temp.delete()
            transferring = false
        }
    }

    fun download(entry: FileEntry) {
        scope.launch {
            transferring = true
            val temp = File(context.cacheDir, "download-${System.currentTimeMillis()}-${entry.name}")
            viewModel.executeAdbResult(AdbCommand.Pull(entry.path, temp.absolutePath))
                .onSuccess {
                    val saved = runCatching { saveToDownloads(context, temp, entry.name) }
                    snackbar.showSnackbar(saved.fold(
                        onSuccess = { "Downloaded → $it" },
                        onFailure = { "Save failed: ${it.message}" }
                    ))
                }
                .onFailure { snackbar.showSnackbar("Download failed: ${it.message}") }
            temp.delete()
            transferring = false
        }
    }

    LaunchedEffect(Unit) { load(currentPath) }

    renameFor?.let { entry ->
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("New name") }
                )
            },
            confirmButton = {
                TextButton(enabled = renameValue.isNotBlank(), onClick = {
                    val target = joinRemote(currentPath, renameValue.trim())
                    scope.launch {
                        transferring = true
                        val cmd = "mv -- ${shellQuote(entry.path)} ${shellQuote(target)}"
                        val result = viewModel.executeAdbResult(AdbCommand.Shell(cmd))
                        transferring = false
                        result.onSuccess { load(currentPath) }
                            .onFailure { snackbar.showSnackbar("Rename failed: ${it.message}") }
                    }
                    renameFor = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text("Cancel") } }
        )
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(enabled = newFolderName.isNotBlank(), onClick = {
                    val target = joinRemote(currentPath, newFolderName.trim())
                    scope.launch {
                        transferring = true
                        val result = viewModel.executeAdbResult(AdbCommand.Shell("mkdir -p -- ${shellQuote(target)}"))
                        transferring = false
                        result.onSuccess { load(currentPath) }
                            .onFailure { snackbar.showSnackbar("Create folder failed: ${it.message}") }
                    }
                    newFolderName = ""
                    showNewFolder = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("Cancel") } }
        )
    }

    deleteFor?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Delete ${entry.name}?") },
            text = { Text(if (entry.isDirectory) "This will delete the folder and everything inside it." else "This file will be permanently deleted from the connected device.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        transferring = true
                        val command = if (entry.isDirectory) "rm -rf -- ${shellQuote(entry.path)}" else "rm -f -- ${shellQuote(entry.path)}"
                        val result = viewModel.executeAdbResult(AdbCommand.Shell(command))
                        transferring = false
                        result.onSuccess { load(currentPath) }
                            .onFailure { snackbar.showSnackbar("Delete failed: ${it.message}") }
                    }
                    deleteFor = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(currentPath, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showNewFolder = true }, enabled = !transferring) {
                        Icon(Icons.Default.CreateNewFolder, "New folder")
                    }
                    IconButton(onClick = { uploadPicker.launch(arrayOf("*/*")) }, enabled = !transferring) {
                        Icon(Icons.Default.Upload, "Upload")
                    }
                    IconButton(onClick = { load(currentPath) }, enabled = !transferring) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (transferring) LinearProgressIndicator(Modifier.fillMaxWidth())

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPath != "/") {
                    AssistChip(
                        onClick = {
                            val parent = currentPath.trimEnd('/').substringBeforeLast('/', "")
                            load(if (parent.isEmpty()) "/" else parent)
                        },
                        label = { Text("Up") },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, null, Modifier.size(18.dp)) }
                    )
                }
                AssistChip(onClick = { load("/storage/emulated/0") }, label = { Text("Internal") })
                AssistChip(onClick = { load("/data/local/tmp") }, label = { Text("Temp") })
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { load(currentPath) }) { Text("Retry") }
                    }
                }
                files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This folder is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(files, key = { it.path }) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { if (entry.isDirectory) load(entry.path) else menuFor = entry },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (entry.isDirectory) Icons.Default.Folder else iconForName(entry.name),
                                    null,
                                    Modifier.size(28.dp),
                                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                                    if (!entry.isDirectory) Text(formatSize(entry.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box {
                                    IconButton(onClick = { menuFor = entry }) { Icon(Icons.Default.MoreVert, "Actions") }
                                    DropdownMenu(expanded = menuFor?.path == entry.path, onDismissRequest = { menuFor = null }) {
                                        if (!entry.isDirectory) {
                                            DropdownMenuItem(
                                                text = { Text("Download") },
                                                leadingIcon = { Icon(Icons.Default.Download, null) },
                                                onClick = { menuFor = null; download(entry) }
                                            )
                                            if (entry.name.endsWith(".apk", ignoreCase = true)) {
                                                DropdownMenuItem(
                                                    text = { Text("Install on device") },
                                                    leadingIcon = { Icon(Icons.Default.InstallMobile, null) },
                                                    onClick = {
                                                        menuFor = null
                                                        scope.launch {
                                                            transferring = true
                                                            viewModel.executeAdbResult(
                                                                AdbCommand.Shell("pm install -r ${shellQuote(entry.path)}")
                                                            ).onSuccess { snackbar.showSnackbar("APK install command completed") }
                                                                .onFailure { snackbar.showSnackbar("Install failed: ${it.message}") }
                                                            transferring = false
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                            onClick = {
                                                menuFor = null
                                                renameValue = entry.name
                                                renameFor = entry
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                                            onClick = { menuFor = null; deleteFor = entry }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun joinRemote(parent: String, name: String): String =
    if (parent == "/") "/$name" else "${parent.trimEnd('/')}/$name"

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private fun iconForName(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
    "apk" -> Icons.Default.Android
    "jpg", "jpeg", "png", "webp", "gif" -> Icons.Default.Image
    "mp4", "mkv", "avi", "mov" -> Icons.Default.Movie
    "mp3", "aac", "flac", "wav" -> Icons.Default.AudioFile
    "pdf" -> Icons.Default.PictureAsPdf
    "zip", "rar", "7z" -> Icons.Default.FolderZip
    else -> Icons.Default.InsertDriveFile
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "File"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}

private suspend fun saveToDownloads(context: android.content.Context, source: File, name: String): String = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/UniPoint")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Cannot create Downloads file")
        try {
            context.contentResolver.openOutputStream(uri)?.use { out -> FileInputStream(source).use { it.copyTo(out) } }
                ?: error("Cannot write Downloads file")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            "Downloads/UniPoint/$name"
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).resolve("UniPoint").apply { mkdirs() }
        val target = File(dir, name)
        source.copyTo(target, overwrite = true)
        target.absolutePath
    }
}
