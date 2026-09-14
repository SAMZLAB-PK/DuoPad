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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.domain.model.AdbCommand
import com.unipoint.domain.model.InstalledApp
import com.unipoint.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.launch

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
    var downloading by remember { mutableStateOf<String?>(null) }
    var uninstallTarget by remember { mutableStateOf<InstalledApp?>(null) }


    fun refresh() {
        scope.launch {
            loading = true
            viewModel.listApps()
                .onSuccess { apps = it.sortedBy { a -> a.label.lowercase() } }
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
                    .padding(16.dp),
                singleLine = true
            )

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        LaunchedEffect(app.packageName) {
                            if (!iconPaths.containsKey(app.packageName) || app.versionName == null) {
                                viewModel.loadAppPresentation(app.packageName)?.let { presentation ->
                                    apps = apps.map { if (it.packageName == presentation.app.packageName) presentation.app else it }
                                    presentation.iconPath?.let { iconPaths[app.packageName] = it }
                                }
                            }
                        }
                        ListItem(
                            headlineContent = {
                                Text(app.label, fontWeight = FontWeight.Medium)
                            },
                            supportingContent = {
                                Text(
                                    listOfNotNull(app.packageName, app.versionName?.let { "v$it" }).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingContent = {
                                val path = iconPaths[app.packageName]
                                val bitmap = remember(path) {
                                    path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                                }
                                if (bitmap != null) {
                                    Image(bitmap, app.label, Modifier.size(44.dp))
                                } else {
                                    Icon(Icons.Default.Android, null, Modifier.size(44.dp))
                                }
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = {
                                        scope.launch {
                                            viewModel.executeAdb(
                                                AdbCommand.Shell("monkey -p ${app.packageName} -c android.intent.category.LAUNCHER 1")
                                            )
                                        }
                                    }) { Icon(Icons.Default.PlayArrow, "Launch") }

                                    IconButton(
                                        enabled = downloading == null,
                                        onClick = {
                                            scope.launch {
                                                downloading = app.packageName
                                                viewModel.downloadApp(app.packageName)
                                                    .onSuccess { snackbar.showSnackbar("Downloaded ${app.label} to Downloads/UniPoint") }
                                                    .onFailure { snackbar.showSnackbar("Download failed: ${it.message}") }
                                                downloading = null
                                            }
                                        }
                                    ) {
                                        if (downloading == app.packageName) {
                                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Download, "Download APK")
                                        }
                                    }

                                    IconButton(onClick = {
                                        scope.launch {
                                            viewModel.executeAdb(
                                                AdbCommand.Shell("am force-stop ${app.packageName}")
                                            )
                                            snackbar.showSnackbar("Force-stopped ${app.label}")
                                        }
                                    }) { Icon(Icons.Default.Stop, "Force Stop") }

                                    IconButton(onClick = { uninstallTarget = app }) {
                                        Icon(Icons.Default.Delete, "Uninstall", tint = MaterialTheme.colorScheme.error)
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
