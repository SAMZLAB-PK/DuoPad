package com.unipoint.presentation.ui.screens.android

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.unipoint.domain.model.InstalledApp
import com.unipoint.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dedicated APK installer. Selecting Install must never enter App Manager.
 * Android's document picker gives a content URI; ADB install needs a local file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallApkScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Choose an APK from your phone to install on the connected Android TV.") }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            var tmp: File? = null
            try {
                val name = withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: "selected.apk"
                }
                fileName = name
                if (!name.endsWith(".apk", ignoreCase = true)) {
                    status = "Please select an .apk file (not a ZIP or bundle)."
                    snack.showSnackbar(status)
                } else {
                    status = "Preparing $name…"
                    val copied = withContext(Dispatchers.IO) {
                        File.createTempFile("duopad-install-", ".apk", context.cacheDir).also { file ->
                            tmp = file
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                file.outputStream().use { output -> input.copyTo(output) }
                            } ?: error("Unable to read selected APK")
                            require(file.length() > 100L) { "APK is empty or unreadable" }
                        }
                    }
                    status = "Installing on TV…"
                    viewModel.executeAdbResult(AdbCommand.Install(copied.absolutePath))
                        .onSuccess { status = "Installed successfully: $name"; snack.showSnackbar(status) }
                        .onFailure { status = "Installation failed: ${it.message ?: "Unknown error"}"; snack.showSnackbar(status) }
                }
            } catch (e: Exception) {
                status = "Installation failed: ${e.message ?: "Could not read APK"}"
                snack.showSnackbar(status)
            } finally {
                withContext(Dispatchers.IO) { tmp?.delete() }
                busy = false
            }
        }
    }
    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Install APK") },
            navigationIcon = { IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back") } }
        ) },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.InstallMobile,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)
            Text("Install on connected TV",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(status,style=MaterialTheme.typography.bodyMedium)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = { apkPicker.launch(arrayOf("*/*")) },enabled = !busy,
                modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen,null)
                Spacer(Modifier.width(8.dp))
                Text(if(busy) "Installing…" else "Choose APK file")
            }
            fileName?.let { Text("Selected: $it",style=MaterialTheme.typography.bodySmall) }
            Text("Installation requires an authorized ADB connection. Android TV may reject incompatible or conflicting APKs.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * TV Channel controls and installed Live TV apps. Distinct from App Manager.
 * Not all Android TV boxes support GUIDE / TV_INPUT or have tuners.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelManagerScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val scope=rememberCoroutineScope()
    val snackbar=remember { SnackbarHostState() }
    var tvApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val matching=remember { Regex("tv|television|channel|iptv|tivimate|live|stream|tuner|pluto|plex|sling",RegexOption.IGNORE_CASE) }
    fun refresh() {
        scope.launch {
            loading=true
            viewModel.listApps()
                .onSuccess { apps ->
                    tvApps=apps.filter { matching.containsMatchIn(it.label) ||
                        matching.containsMatchIn(it.packageName) }
                        .sortedBy { it.label.lowercase() }
                }
                .onFailure { snackbar.showSnackbar(it.message ?: "Could not load TV apps") }
            loading=false
        }
    }
    fun key(code:Int) { viewModel.executeAdb(AdbCommand.InputKey(code)) }
    LaunchedEffect(Unit) { refresh() }
    Scaffold(
        topBar = { TopAppBar(
            title={Text("Channels & Live TV")},
            navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},
            actions={IconButton(onClick={refresh()}){Icon(Icons.Default.Refresh,"Refresh TV apps")}}
        ) },
        snackbarHost={SnackbarHost(snackbar)}
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("TV channel controls",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text("Use your TV's Live TV, Input or Guide controls. Available channels depend on the TV and installed apps.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={key(170)},modifier=Modifier.weight(1f)) { Text("Live TV") }
                OutlinedButton(onClick={key(178)},modifier=Modifier.weight(1f)) { Text("Inputs") }
                OutlinedButton(onClick={key(172)},modifier=Modifier.weight(1f)) { Text("Guide") }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick={key(167)},modifier=Modifier.weight(1f)) {
                    Icon(Icons.Default.KeyboardArrowDown,null);Spacer(Modifier.width(4.dp));Text("CH −")
                }
                FilledTonalButton(onClick={key(166)},modifier=Modifier.weight(1f)) {
                    Icon(Icons.Default.KeyboardArrowUp,null);Spacer(Modifier.width(4.dp));Text("CH +")
                }
            }
            HorizontalDivider()
            Text("Installed TV & channel apps",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!loading && tvApps.isEmpty()) Text("No recognizable TV/channel apps found. Use the controls above or open Apps.",
                style=MaterialTheme.typography.bodySmall)
            LazyColumn(modifier=Modifier.weight(1f),contentPadding=PaddingValues(bottom=12.dp)) {
                items(tvApps,key={it.packageName}) { app ->
                    ListItem(
                        headlineContent={Text(app.label,maxLines=1)},
                        supportingContent={Text(app.packageName,style=MaterialTheme.typography.bodySmall,maxLines=1)},
                        leadingContent={Icon(Icons.Default.LiveTv,null)},
                        trailingContent={TextButton(onClick={
                            // Package name originates from ADB pm list, not user input.
                            viewModel.executeAdb(AdbCommand.Shell(
                                "monkey -p ${app.packageName} -c android.intent.category.LAUNCHER 1"))
                        }){Text("Open")}}
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
