package com.unipoint.presentation.ui.screens.pc

import android.app.Activity
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.positionChange
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.layout.ContentScale
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
    initialTab: String = "Screen",
    onBack: () -> Unit,
    onKeyboard: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onScroll: (Float) -> Unit,
    onText: (String) -> Unit,
    onKey: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(host, port, pin) { PcWorkspaceClient(context.applicationContext, host, port, pin) }
    DisposableEffect(client) { onDispose { client.close() } }
    var tab by rememberSaveable(initialTab) {
        mutableStateOf(initialTab.takeIf { it in listOf("Screen", "Files", "Info", "Software") } ?: "Info")
    }
    var pointerMode by rememberSaveable { mutableStateOf("On PC screen") }
    val activity = context as? Activity
    // Screen opens landscape and hides status bar; Files and Info stay portrait.
    // Reset orientation and bars when leaving to avoid locking the whole app.
    DisposableEffect(activity, tab) {
        if (tab == "Screen") {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity?.window?.let { window ->
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window,window.decorView)
                    .hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }
        onDispose {
            if (tab == "Screen") {
                // Returning from fullscreen must restore portrait, not auto-rotate
                // back into landscape while the phone is still held sideways.
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                activity?.window?.let { window ->
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    WindowCompat.getInsetsController(window,window.decorView)
                        .show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
    }
    BackHandler(tab == "Screen") { onBack() }
    var message by remember { mutableStateOf("Connecting to PC Host…") }
    var busy by remember { mutableStateOf(false) }
    var desktop by remember { mutableStateOf<Bitmap?>(null) }
    var info by remember { mutableStateOf<JSONObject?>(null) }
    var driveData by remember { mutableStateOf<JSONObject?>(null) }
    var directory by remember { mutableStateOf<JSONObject?>(null) }
    var folder by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var lastError by remember { mutableStateOf<String?>(null) }
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
        lastError = null
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
                    if (folder.isBlank()) {
                        driveData = null
                        driveData = client.drives()
                        message = "PC drives ready"
                    } else {
                        directory = null
                        directory = client.directory(folder)
                        message = "PC folder loaded"
                    }
                }
                "Info", "Software" -> {
                    info = null
                    info = client.information()
                    message = "System information updated"
                    if (tab == "Info") {
                        // Windows CIM hardware inspection is warmed in background by Host.
                        // Recheck RAM slots without blocking the first information screen.
                        for (attempt in 0 until 3) {
                            if ((info?.optJSONArray("ramModules")?.length() ?: 0) > 0) break
                            delay(2400)
                            info = client.information()
                        }
                    }
                }
            }
        } catch (_: CancellationException) {
            // Switching tabs cancels the previous stream without an error toast.
        } catch (ex: Exception) {
            lastError = ex.message ?: ex.javaClass.simpleName
            message = "PC ${tab} failed: ${lastError}"
        }
    }

    if (tab == "Screen") {
        DuoPadPcFullScreen(
            desktop=desktop,
            mode=pointerMode,
            onModeChange={ pointerMode=it },
            onExit=onBack,
            onKeyboard=onKeyboard,
            onLeftClick=onLeftClick,
            onRightClick=onRightClick,
            onMove=onMove,
            onScroll=onScroll,
            onQualityChange={ newQuality, newWidth ->
                quality=newQuality
                previewWidth=newWidth
            },
            lastError=lastError,
            onRetry={ reload++ }
        )
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(when(tab) {
                    "Files" -> "PC drives & files"
                    "Info" -> "System information"
                    "Software" -> "Installed software"
                    else -> "PC Screen"
                }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("$host:$port", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        if (lastError != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("PC ${tab} unavailable: ${lastError}",
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { reload++ }) { Text("Retry ${tab}") }
                }
            }
        }
        // The screenshot's 4-card Windows PC Workspace is the ONLY dashboard.
        // This route displays one selected function (Files or Info), never a
        // second navigation dashboard or redundant Screen controls.
        when (tab) {
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
                        if (drives == null) Text(if(lastError==null) "Loading drives…" else "Drives unavailable. Tap Refresh to retry.")
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
                        if (items == null) Text(if(lastError==null) "Loading folder…" else "Folder unavailable. Tap Refresh to retry.")
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
                                    if (isDirectory) { directory=null; folder = path } else {
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
            "Software" -> {
                val apps = info?.optJSONArray("installedSoftware")
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Installed software", style=MaterialTheme.typography.headlineSmall,
                        fontWeight=FontWeight.Bold)
                    Text("Read-only Windows uninstall-registry inventory. Portable and Store apps may not appear.",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                    when {
                        info == null && lastError == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        apps == null -> Text("Update DuoPad PC Host to view installed software.")
                        apps.length() == 0 -> Text("No registered desktop software found.")
                        else -> {
                            Text("${apps.length()} registered entries", style=MaterialTheme.typography.labelMedium)
                            for (index in 0 until apps.length()) {
                                val app = apps.optJSONObject(index) ?: continue
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(horizontal=14.dp,vertical=11.dp)) {
                                        Text(app.optString("name","Unknown application"),
                                            fontWeight=FontWeight.SemiBold,maxLines=2)
                                        val detail = listOf(app.optString("version"),app.optString("publisher"))
                                            .filter { it.isNotBlank() }.joinToString(" · ")
                                        if(detail.isNotBlank()) Text(detail,
                                            style=MaterialTheme.typography.bodySmall,
                                            color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Info" -> {
                val data=info
                if(data==null) {
                    if(lastError==null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(14.dp))
                    else Text("System information is unavailable. Tap Refresh to retry.",
                        modifier=Modifier.padding(16.dp))
                }
                else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("System overview",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text("Live data from your authenticated Windows Host",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                    val totalBytes=data.optLong("ramTotal",-1)
                    val freeBytes=data.optLong("ramAvailable",-1)
                    fun readable(bytes:Long):String = if(bytes<0)"N/A" else
                        String.format(java.util.Locale.US,"%.1f GB",bytes.toDouble()/1073741824.0)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        ElevatedCard(Modifier.weight(1f)){
                            Column(Modifier.padding(15.dp)){
                                Icon(Icons.Default.Memory,"RAM")
                                Text("Installed RAM",style=MaterialTheme.typography.labelMedium)
                                Text(readable(totalBytes),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                            }
                        }
                        ElevatedCard(Modifier.weight(1f)){
                            Column(Modifier.padding(15.dp)){
                                Icon(Icons.Default.Speed,"Available RAM")
                                Text("Free memory",style=MaterialTheme.typography.labelMedium)
                                Text(readable(freeBytes),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                    ElevatedCard(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text("COMPUTER",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                            Text(data.optString("name","Unknown PC"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                            Text(data.optString("os","Unknown Windows"),style=MaterialTheme.typography.bodyMedium)
                            HorizontalDivider()
                            Text("PROCESSOR",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                            Text(data.optString("cpuModel",data.optString("cpu","Unknown processor")),
                                style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
                            val cpuGeneration = data.optString("cpuGeneration")
                            if(cpuGeneration.isNotBlank()) Text(cpuGeneration,
                                style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            val cpuMaker = data.optString("cpuManufacturer")
                            if(cpuMaker.isNotBlank()) Text("Manufacturer: $cpuMaker",style=MaterialTheme.typography.bodySmall)
                            Text("${data.optString("cpuThreads","?")} logical threads  ·  ${data.optString("architecture","?")}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    val modules = data.optJSONArray("ramModules")
                    if(modules != null && modules.length() > 0) {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("PHYSICAL RAM MODULES",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                                for(index in 0 until modules.length()) {
                                    val module = modules.optJSONObject(index) ?: continue
                                    val capacity = module.optLong("Capacity",-1)
                                    val brand = module.optString("Manufacturer","Unknown brand").ifBlank { "Unknown brand" }
                                    val part = module.optString("PartNumber").trim()
                                    Text("$brand · ${readable(capacity)}",fontWeight=FontWeight.SemiBold)
                                    Text(listOf(module.optString("DeviceLocator"),part,
                                            module.optString("Speed").takeIf { it.isNotBlank() }?.let { "$it MHz" }.orEmpty())
                                            .filter { it.isNotBlank() }.joinToString(" · "),
                                        style=MaterialTheme.typography.bodySmall)
                                    if(index < modules.length()-1) HorizontalDivider()
                                }
                            }
                        }
                    }
                    val physical = data.optJSONArray("diskInventory")
                    if(physical != null && physical.length() > 0) {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                                Text("PHYSICAL DRIVES",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                                for(index in 0 until physical.length()) {
                                    val drive=physical.optJSONObject(index) ?: continue
                                    val driveType=drive.optString("type","Unknown").ifBlank { "Unknown" }
                                    val driveSize=readable(drive.optLong("size",-1))
                                    Text("$driveType · $driveSize",fontWeight=FontWeight.Bold)
                                    Text(drive.optString("model","Unknown model"),
                                        style=MaterialTheme.typography.bodyMedium)
                                    val bus=drive.optString("bus")
                                    if(bus.isNotBlank()) Text("Bus: $bus",
                                        style=MaterialTheme.typography.bodySmall)
                                    if(index < physical.length()-1) HorizontalDivider()
                                }
                            }
                        }
                    } else {
                        Text("Physical drive type inventory is loading or unavailable.",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    ElevatedCard(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                            Text("SYSTEM & STORAGE",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                            Text("Drives detected: ${data.optString("drives","0")}")
                            Text("User folder: ${data.optString("home","Not available")}",
                                style=MaterialTheme.typography.bodySmall)
                            Text("For detailed drive capacity, return to the PC Workspace and open PC drives & files.",
                                color=MaterialTheme.colorScheme.onSurfaceVariant,
                                style=MaterialTheme.typography.labelSmall)
                        }
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


@Composable
private fun DuoPadPcFullScreen(
    desktop: Bitmap?,
    mode: String,
    onModeChange: (String) -> Unit,
    onExit: () -> Unit,
    onKeyboard: () -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onScroll: (Float) -> Unit,
    onQualityChange: (Int,Int) -> Unit,
    lastError: String?,
    onRetry: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(desktop!=null){
            val pointer=if(mode=="On PC screen"){
                Modifier.pointerInput(mode) { detectTapGestures(onTap={onLeftClick()}) }
                    .pointerInput(mode) { detectDragGestures { change, drag ->
                        change.consume()
                        onMove(drag.x*1.25f,drag.y*1.25f)
                    } }
            }else Modifier
            Image(desktop.asImageBitmap(),"Live Windows desktop",
                modifier=Modifier.fillMaxSize().then(pointer),
                contentScale=ContentScale.Fit)
        }else{
            Text(lastError ?: "Connecting to Windows desktop…",
                modifier=Modifier.align(Alignment.Center).padding(15.dp),
                color=Color.White,textAlign=TextAlign.Center)
        }
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color(0xD9141B28)).padding(horizontal=9.dp,vertical=3.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(7.dp)
        ){
            IconButton(onClick=onExit){
                Icon(Icons.Default.ArrowBack,"Back to PC Workspace",tint=Color.White)
            }
            Text("PC Screen",color=Color.White,fontWeight=FontWeight.Bold,
                modifier=Modifier.weight(1f),maxLines=1)
            TextButton(onClick={onModeChange(if(mode=="On PC screen") "Phone touchpad" else "On PC screen")}){
                Text(if(mode=="On PC screen")"PC mouse" else "Phone touchpad",
                    color=Color.White)
            }
            IconButton(onClick=onKeyboard){
                Icon(Icons.Default.Keyboard,"Windows keyboard",tint=Color.White)
            }
            Box{
                IconButton(onClick={menuOpen=true}){
                    Icon(Icons.Default.Tune,"Quality",tint=Color.White)
                }
                DropdownMenu(expanded=menuOpen,onDismissRequest={menuOpen=false}){
                    DropdownMenuItem(text={Text("Fast 720p")},onClick={
                        menuOpen=false;onQualityChange(45,720)
                    })
                    DropdownMenuItem(text={Text("Balanced 960p")},onClick={
                        menuOpen=false;onQualityChange(60,960)
                    })
                    DropdownMenuItem(text={Text("Sharp 1280p")},onClick={
                        menuOpen=false;onQualityChange(75,1280)
                    })
                }
            }
        }
        if(mode=="Phone touchpad"){
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.75f).height(110.dp)
                .padding(bottom=4.dp).clip(RoundedCornerShape(19.dp))
                .background(Color(0xD9212D41))
                .pointerInput(mode){detectTapGestures(onTap={onLeftClick()})}
                .pointerInput(mode){detectDragGestures{change,drag ->
                    change.consume()
                    onMove(drag.x*1.5f,drag.y*1.5f)
                }},
                contentAlignment=Alignment.Center){
                Text("DRAG HERE TO MOVE WINDOWS CURSOR  •  TAP TO CLICK",
                    color=Color.White,style=MaterialTheme.typography.labelSmall,
                    textAlign=TextAlign.Center,modifier=Modifier.padding(12.dp))
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end=12.dp,bottom=10.dp)
            .background(Color(0xC9161B28),RoundedCornerShape(25.dp)),
            verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onLeftClick){Icon(Icons.Default.Mouse,"Left click",tint=Color.White)}
            IconButton(onClick=onRightClick){Icon(Icons.Default.TouchApp,"Right click",tint=Color.White)}
            IconButton(onClick={onScroll(3f)}){
                Icon(Icons.Default.KeyboardArrowUp,"Scroll up",tint=Color.White)
            }
            IconButton(onClick={onScroll(-3f)}){
                Icon(Icons.Default.KeyboardArrowDown,"Scroll down",tint=Color.White)
            }
            if(lastError!=null)
                IconButton(onClick=onRetry){Icon(Icons.Default.Refresh,"Reconnect",tint=Color.White)}
        }
    }
}
