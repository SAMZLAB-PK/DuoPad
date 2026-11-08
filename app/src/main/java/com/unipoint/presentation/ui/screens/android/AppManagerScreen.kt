package com.unipoint.presentation.ui.screens.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.domain.model.AdbCommand
import com.unipoint.domain.model.InstalledApp
import com.unipoint.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val iconPaths = remember { mutableStateMapOf<String, String>() }
    val enrichment = remember { mutableStateMapOf<String, Boolean>() }
    val queue = remember { Semaphore(2) }
    var enrichmentDone by remember { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var uninstallTarget by remember { mutableStateOf<InstalledApp?>(null) }
    var expandedAppMenu by remember { mutableStateOf<String?>(null) }


    fun refresh() {
        scope.launch {
            loading = true
            viewModel.listApps()
                .onSuccess { result ->
                    apps = result.sortedBy { a -> a.label.lowercase() }
                    // Initial list is already useful; heavy icon sync must never block it.
                }
                .onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
            loading = false
        }
    }

    uninstallTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = { Text("Uninstall ${target.label}?") },
            text = { Text(target.packageName) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.executeAdbResult(AdbCommand.Uninstall(target.packageName))
                            .onSuccess { refresh() }
                            .onFailure { snackbar.showSnackbar("Uninstall failed: ${it.message}") }
                    }
                    uninstallTarget = null
                }) { Text("Uninstall", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { uninstallTarget = null }) { Text("Cancel") } }
        )
    }

    LaunchedEffect(Unit) { refresh() }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal=16.dp, vertical=8.dp),
                singleLine = true
            )

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text(
                    "${apps.size} apps · ${enrichmentDone.coerceAtMost(apps.size)} icon/name checks complete",
                    modifier=Modifier.padding(horizontal=16.dp,vertical=2.dp),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        LaunchedEffect(app.packageName) {
                            val packageId = app.packageName
                            if (enrichment.put(packageId, true) == null) {
                                queue.withPermit {
                                    try {
                                        viewModel.loadAppPresentation(packageId)?.let { presentation ->
                                            apps = apps.map { item ->
                                                if (item.packageName == packageId) presentation.app else item
                                            }
                                            presentation.iconPath?.let { iconPaths[packageId] = it }
                                        }
                                    } finally {
                                        enrichmentDone++
                                    }
                                }
                            }
                        }
                        ListItem(
                            headlineContent = {
                                Text(app.label, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    listOfNotNull(app.packageName, app.versionName?.let { "v$it" }).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                val path = iconPaths[app.packageName]
                                val bitmap = remember(path) {
                                    path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                                }
                                if (bitmap != null) {
                                    Image(bitmap, app.label, Modifier.size(38.dp))
                                } else {
                                    Icon(Icons.Default.Android, null, Modifier.size(38.dp))
                                }
                            },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { expandedAppMenu = app.packageName }) {
                                        Icon(Icons.Default.MoreVert, "Actions for ${app.label}")
                                    }
                                    DropdownMenu(
                                        expanded = expandedAppMenu == app.packageName,
                                        onDismissRequest = { expandedAppMenu = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Launch") },
                                            leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                            onClick = {
                                                expandedAppMenu = null
                                                viewModel.executeAdb(AdbCommand.Shell(
                                                    "monkey -p ${app.packageName} -c android.intent.category.LAUNCHER 1"))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (downloading == app.packageName) "Downloading…" else "Download APK") },
                                            leadingIcon = { Icon(Icons.Default.Download, null) },
                                            enabled = downloading == null,
                                            onClick = {
                                                expandedAppMenu = null
                                                scope.launch {
                                                    downloading = app.packageName
                                                    try {
                                                        viewModel.downloadApp(app.packageName)
                                                            .onSuccess { snackbar.showSnackbar("Downloaded ${app.label} to Downloads/DuoPad") }
                                                            .onFailure { snackbar.showSnackbar("Download failed: ${it.message}") }
                                                    } finally { downloading = null }
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Force stop") },
                                            leadingIcon = { Icon(Icons.Default.Stop, null) },
                                            onClick = {
                                                expandedAppMenu = null
                                                scope.launch {
                                                    viewModel.executeAdb(AdbCommand.Shell("am force-stop ${app.packageName}"))
                                                    snackbar.showSnackbar("Force-stopped ${app.label}")
                                                }
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Uninstall", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = { expandedAppMenu = null; uninstallTarget = app }
                                        )
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
