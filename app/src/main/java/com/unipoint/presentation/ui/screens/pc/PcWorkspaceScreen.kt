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
    initialTab: String = "Workspace",
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
        mutableStateOf(initialTab.takeIf { it in listOf("Workspace", "Screen", "Files", "Info") } ?: "Workspace")
    }
    var pointerMode by rememberSaveable { mutableStateOf("On PC screen") }
    val activity = context as? Activity
    // Screen opens landscape and hides status bar; Files and Info stay portrait.
    // Reset orientation and bars when leaving to avoid locking the whole app.
    DisposableEffect(activity, tab) {
        val oldOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
                activity?.requestedOrientation = oldOrientation
                activity?.window?.let { window ->
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    WindowCompat.getInsetsController(window,window.decorView)
                        .show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
    }
    BackHandler(tab == "Screen") { tab = "Workspace" }
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
                    if (folder.isBlank()) driveData = client.drives()
                    else directory = client.directory(folder)
                }
                "Info" -> info = client.information()
                "Workspace" -> {
                    // No request until user chooses a feature; avoids extra host load
                    // and opens the real workspace dashboard after pairing.
                    message = "PC connected • Choose Screen, Files or Info"
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
            onExit={ tab="Workspace" },
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
                Text("DuoPad PC Workspace", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge)
                Text("$host:$port", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Workspace", "Screen", "Files", "Info").forEach { option ->
                FilterChip(selected = tab == option, onClick = {
                    tab = option
                    if (option == "Files") folder = ""
                }, label = { Text(option) }, modifier = Modifier.weight(1f))
            }
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
        when (tab) {
            "Workspace" -> {
                Text("Welcome to your PC", style=MaterialTheme.typography.headlineSmall,
                    fontWeight=FontWeight.Bold, modifier=Modifier.padding(start=16.dp,top=18.dp))
                Text("Choose what you'd like to control on $host",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier=Modifier.padding(horizontal=16.dp,vertical=9.dp))
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    ElevatedCard(onClick={tab="Screen"},modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.DesktopWindows,null,Modifier.size(29.dp))
                            Spacer(Modifier.width(15.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Live desktop",fontWeight=FontWeight.Bold)
                                Text("Full screen • Mouse control • Landscape",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight,null)
                        }
                    }
                    ElevatedCard(onClick={tab="Files"},modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderOpen,null,Modifier.size(29.dp))
                            Spacer(Modifier.width(15.dp))
                            Column(Modifier.weight(1f)){
                                Text("Drives and files",fontWeight=FontWeight.Bold)
                                Text("Browse • Upload • Download",style=MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight,null)
                        }
                    }
                    ElevatedCard(onClick={tab="Info"},modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Default.Memory,null,Modifier.size(29.dp))
                            Spacer(Modifier.width(15.dp))
                            Column(Modifier.weight(1f)){
                                Text("PC system information",fontWeight=FontWeight.Bold)
                                Text("Windows • CPU • Memory • Storage",style=MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight,null)
                        }
                    }
                    OutlinedButton(onClick=onKeyboard,modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.Keyboard,null)
                        Spacer(Modifier.width(8.dp))
                        Text("Windows on-screen keyboard")
                    }
                }
            }
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
                            } }, contentScale = ContentScale.Fit)
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
            "Info" -> {
                val data=info
                if(data==null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(14.dp))
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
                            Text(data.optString("cpu","Unknown processor"),style=MaterialTheme.typography.bodyMedium)
                            Text("${data.optString("cpuThreads","?")} logical threads  ·  ${data.optString("architecture","?")}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    ElevatedCard(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                            Text("SYSTEM & STORAGE",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                            Text("Drives detected: ${data.optString("drives","0")}")
                            Text("User folder: ${data.optString("home","Not available")}",
                                style=MaterialTheme.typography.bodySmall)
                            Text("For detailed drive capacity, open the Files tab.",
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
