package com.unipoint.presentation.ui

import android.Manifest
import com.unipoint.presentation.ui.theme.DuoColors
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import android.content.Intent
import com.unipoint.core.util.MirrorCrashRecorder
import com.unipoint.core.util.TrustedPcPairing
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.unipoint.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.unipoint.BuildConfig
import com.unipoint.core.network.PcPairingUri
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.unipoint.domain.model.*
import com.unipoint.presentation.viewmodel.MainViewModel
import com.unipoint.presentation.ui.screens.android.*
import com.unipoint.presentation.ui.screens.pc.KeyboardScreen
import com.unipoint.presentation.ui.screens.pc.PcWorkspaceScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Source of truth: user-approved Concept 04 reference (warm cream + pure ink).
private val Accent: Color @Composable get() = DuoColors.Accent
private val OnAccent: Color @Composable get() = DuoColors.OnAccent
private val Teal: Color @Composable get() = DuoColors.Accent
private val Ink: Color @Composable get() = DuoColors.OnSurface
private val Muted: Color @Composable get() = DuoColors.OnSurfaceMuted
private val Border: Color @Composable get() = DuoColors.Hairline
private val Pale: Color @Composable get() = DuoColors.SurfaceHigh
private val Background: Color @Composable get() = DuoColors.Background
private val Paper: Color @Composable get() = DuoColors.SurfaceMid
private val Online: Color get() = DuoColors.Success
private data class Target(val name:String,val endpoint:String,val pc:Boolean,val customName:String?=null)
private fun readTargets(context:Context):List<Target> = runCatching {
    val a=JSONArray(context.getSharedPreferences("doupad",0).getString("targets","[]"))
    (0 until a.length()).map { val o=a.getJSONObject(it);Target(o.getString("name"),o.getString("endpoint"),o.optBoolean("pc"),o.optString("customName").takeIf { it.isNotBlank() }) }
}.getOrDefault(emptyList())
private fun saveTargets(context:Context,items:List<Target>) {
    val a=JSONArray();items.forEach { a.put(JSONObject().put("name",it.name).put("endpoint",it.endpoint).put("pc",it.pc).put("customName",it.customName ?: "")) }
    context.getSharedPreferences("doupad",0).edit().putString("targets",a.toString()).apply()
}

/** Reusable DuoPad header matching the user's original D-logo and DOUPAD title. */
@Composable
private fun DuoPadBrandHeader(
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 57.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.duopad_original_logo),
            contentDescription = "Original DuoPad D logo",
            modifier = Modifier.size(49.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = "DOUPAD",
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.75).sp,
                lineHeight = 30.sp,
                color = Ink,
                maxLines = 1
            )
            Text(
                text = "Control your PC & TV from your phone",
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.18).sp,
                lineHeight = 12.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMore, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "DuoPad menu",
                tint = Ink,
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoupadShell(
    appearance: String = "light",
    onAppearanceChange: (String) -> Unit = {},
    vm: MainViewModel = hiltViewModel()
) {
    val context=LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    val snack=remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf(4) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    var pin by remember { mutableStateOf("") }
    var pcWorkspaceTab by rememberSaveable { mutableStateOf("Screen") }
    var pc by remember { mutableStateOf(false) }
    var pcTransport by rememberSaveable { mutableStateOf("bluetooth") }
    var localError by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var scanFilter by rememberSaveable { mutableStateOf("All") }
    var remoteMode by rememberSaveable { mutableIntStateOf(0) }
    var showNumberPad by rememberSaveable { mutableStateOf(false) }
    var showRemoteExtras by rememberSaveable { mutableStateOf(false) }
    var connectRequested by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Target?>(null) }
    var renameText by remember { mutableStateOf("") }
    var menuTarget by remember { mutableStateOf<String?>(null) }
    var airMouseSelected by rememberSaveable { mutableStateOf(false) }
    var pendingQuickAction by rememberSaveable { mutableStateOf("Tools") }
    var targets by remember { mutableStateOf(readTargets(context)) }
    var pendingSaveTarget by remember { mutableStateOf<Target?>(null) }
    var lastPromptedEndpoint by remember { mutableStateOf<String?>(null) }
    var automaticReconnect by remember { mutableStateOf(true) }
    var pcAutoAttempted by rememberSaveable { mutableStateOf(false) }
    var reconnectAttempts by remember { mutableIntStateOf(0) }

    val status=state.connectionStatus
    val connected=status.state==ConnectionState.CONNECTED
    fun message(text:String){scope.launch { snack.showSnackbar(text) }}
    // The QR scanner already existed in an unused navigation screen. Make it
    // reachable from the actual PC discovery/connect interface.
    val pcQrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (!raw.isNullOrBlank()) {
            val endpoint = PcPairingUri.parse(raw)
            if (endpoint == null) message("Not a valid DuoPad PC pairing QR code")
            else {
                pc=true; pcTransport="wifi"
                ip=endpoint.host; port=endpoint.port.toString(); pin=endpoint.pin.orEmpty()
                connectRequested=true; dialog=null
                vm.connectNetwork(endpoint.host,endpoint.port,endpoint.pin,onSuccess={
                    connectRequested=false; dialog=null; pcWorkspaceTab="Screen"; page=3; detail=null
                })
            }
        }
    }
    fun scanPcQr() {
        pcQrScanner.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan the QR shown by DuoPad PC Host")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setCaptureActivity(DuoPadPortraitCaptureActivity::class.java)
        })
    }
    val discoverableLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val bluetoothPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if(grants.values.all { it }) {
            vm.switchMode(AppMode.PC)
            vm.connectBluetooth()
            runCatching {
                discoverableLauncher.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,180)
                )
            }
            dialog=null
            message("Bluetooth Direct is ready. On the PC, open Bluetooth settings and pair with this phone.")
        } else message("Bluetooth permission is required for Direct mode.")
    }
    fun startBluetoothDirect() {
        val permissions=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S) arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) else emptyArray()
        val missing=permissions.filter { ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED }
        if(missing.isNotEmpty()) bluetoothPermissionLauncher.launch(missing.toTypedArray()) else {
            vm.switchMode(AppMode.PC)
            vm.connectBluetooth()
            runCatching {
                discoverableLauncher.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,180)
                )
            }
            dialog=null
            message("Bluetooth Direct is ready. On the PC, open Bluetooth settings and pair with this phone.")
        }
    }
    fun requireDevice():Boolean { if(!connected){dialog="connect";message("Connect your TV or PC first.")};return connected }
    fun requireTv():Boolean { if(!requireDevice())return false; if(state.mode!=AppMode.ANDROID){message("This tool requires an Android TV connection.");return false};return true }
    fun key(code:Int) {
        if(!requireDevice())return
        val mapped = if(state.mode==AppMode.ANDROID) RemoteKeyMapping.resolve(context,code) else code
        if(state.mode==AppMode.ANDROID) vm.executeAdb(AdbCommand.InputKey(mapped))
        else {
            val hid=when(code){19->82;20->81;21->80;22->79;23,66->40;4->41;3->74;else->{message("This button is available for Android TV only.");return}}
            vm.sendKey(hid,true);vm.sendKey(hid,false)
        }
    }
    fun showCommand(title:String,cmd:String) { if(!requireDevice())return;dialog=title;output="Running…";scope.launch { output=vm.executeAdbAwait(cmd).ifBlank { "Completed with no output." } } }
    fun applySelectedAction() {
        val isTv = state.mode == AppMode.ANDROID
        when(pendingQuickAction) {
            "Mirror" -> {
                if(isTv) page=2 else {pcWorkspaceTab="Screen";page=3;detail="pcWorkspace"}
            }
            "Mouse Pad", "Air Mouse" -> {
                page=1
                if(isTv) {
                    airMouseSelected=pendingQuickAction=="Air Mouse"
                    remoteMode=if(airMouseSelected)2 else 1
                    detail="mouse"
                } else if(pendingQuickAction=="Mouse Pad") {
                    remoteMode=1
                } else message("Air Mouse is available for supported Android TV devices.")
            }
            "Send files" -> {
                if(isTv) {page=3;detail="files"}
                else {pcWorkspaceTab="Files";page=3;detail="pcWorkspace"}
            }
            "Install APK" -> {
                if(isTv) {page=3;detail="install"}
                else {page=3;message("APK installation is for Android devices only.")}
            }
            "Remote" -> page=1
            "Tools" -> page=3
            else -> page=3
        }
        pendingQuickAction="Tools"
    }
    fun openNearby(action:String) {
        pendingQuickAction=action
        scanFilter="All"
        page=4
        vm.scanNearby()
    }
    fun open(target:Target) {
        automaticReconnect=targets.any { it.endpoint==target.endpoint }
        reconnectAttempts=0
        if(page != 4) pendingQuickAction="Tools"
        if(connected && status.address==target.endpoint){applySelectedAction();return}
        ip=target.endpoint.substringBefore(':')
        port=target.endpoint.substringAfter(':',if(target.pc)"27845" else "5555")
        pc=target.pc;pcTransport="wifi";localError=""
        if(target.pc) {
            val trustedPin = TrustedPcPairing.read(context,target.endpoint)
            if(!trustedPin.isNullOrBlank()){
                pin=trustedPin
                connectRequested=true
                dialog="connectingSaved"
                vm.connectNetwork(ip,port.toIntOrNull()?:27845,trustedPin,onSuccess={
                    connectRequested=false
                    dialog=null
                    pcWorkspaceTab="Screen"
                    page=3
                    detail=null
                })
            }else{
                connectRequested=false
                pin=""
                dialog="connect"
            }
        } else {
            connectRequested=true;dialog="connectingSaved"
            vm.connectAndroidIp("$ip:$port")
        }
    }
    val voice=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { command=it;dialog="keyboard" }
    }
    LaunchedEffect(status.state) {
        if (connected && connectRequested && (dialog == "connect" || dialog == "connectingSaved")) {
            connectRequested = false
            dialog = null
            applySelectedAction()
        }
    }
    // A successful connection is NOT silently added to Saved. Ask once, and only
    // automatically reconnect entries the user explicitly approved earlier.
    LaunchedEffect(status.state,status.address) {
        if (connected) {
            reconnectAttempts=0
            val endpoint=status.address.orEmpty()
            if (endpoint.isNotBlank() && ':' in endpoint) {
                if(state.mode==AppMode.PC){
                    // User requested auto-save after successful authenticated PC connection.
                    // Encrypt PIN with Android Keystore. Never store it in targets JSON.
                    if(pin.isNotBlank()) TrustedPcPairing.save(context,endpoint,pin)
                    val previousPc=targets.firstOrNull { it.endpoint==endpoint }
                    val saved=previousPc ?: Target(
                        status.deviceName?.takeIf{it.isNotBlank()} ?: endpoint.substringBefore(':'),
                        endpoint,true
                    )
                    targets=listOf(saved)+targets.filterNot{it.endpoint==endpoint}
                    saveTargets(context,targets)
                    automaticReconnect=true
                    lastPromptedEndpoint=endpoint
                } else {
                val previous=targets.firstOrNull { it.endpoint==endpoint }
                if (previous!=null) {
                    targets=listOf(previous)+targets.filterNot { it.endpoint==endpoint }
                    saveTargets(context,targets)
                } else if (lastPromptedEndpoint != endpoint) {
                    lastPromptedEndpoint=endpoint
                    pendingSaveTarget=Target(
                        status.deviceName?.takeIf { it.isNotBlank() } ?: endpoint,
                        endpoint,state.mode==AppMode.PC
                    )
                    dialog="saveDevice"
                }
                }
            }
        }
    }
    // Best-effort reconnect for explicitly saved, previously authorized devices.
    // Bounded retries avoid an endless loop for an offline host or revoked ADB key.
    LaunchedEffect(status.state,targets.firstOrNull()?.endpoint,automaticReconnect) {
        val target=targets.firstOrNull()
        if (automaticReconnect && target!=null &&
            status.state in listOf(ConnectionState.DISCONNECTED, ConnectionState.ERROR) &&
            reconnectAttempts < 3) {
            val attempt=reconnectAttempts
            delay(if(attempt==0) 1300L else 2500L * (attempt+1))
            if (!automaticReconnect || connected || targets.firstOrNull()?.endpoint != target.endpoint) return@LaunchedEffect
            reconnectAttempts++
            MirrorCrashRecorder.note(context,
                "SAVED_DEVICE_RECONNECT: ${target.endpoint}; attempt=$reconnectAttempts/3")
            val host=target.endpoint.substringBefore(':')
            val port=target.endpoint.substringAfter(':',if(target.pc) "27845" else "5555").toIntOrNull()
            if(port!=null) {
                if(target.pc){
                    // One automatic attempt on launch, then repository owns reconnect.
                    // Prevent simultaneous handshakes and reconnection storms.
                    if(pcAutoAttempted) return@LaunchedEffect
                    pcAutoAttempted=true
                    val trustedPin=TrustedPcPairing.read(context,target.endpoint) ?: return@LaunchedEffect
                    pin=trustedPin;pc=true;pcTransport="wifi"
                    // Background reconnect must never hijack the foreground UI.
                    // Keep Devices page visible until the user selects the PC.
                    vm.connectNetwork(host,port,trustedPin,onSuccess={
                        MirrorCrashRecorder.note(context,"PC_AUTO_RECONNECTED: saved host connected; navigation unchanged")
                    })
                } else vm.connectAndroidIp("$host:$port")
            }
        }
    }
    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snack.showSnackbar(it);vm.clearSnackbar() } }
    LaunchedEffect(dialog,pc,pcTransport) {
        if(dialog=="connect" && pc && pcTransport=="wifi") vm.scanPc()
    }
    LaunchedEffect(Unit) {
        // Fresh app launch always starts at Devices. Saved PC reconnect may run,
        // but must not open PC Workspace without an explicit user tap.
        page=4
        detail=null
        vm.scanAndroid()
    }
    BackHandler(detail!=null||page!=4||dialog!=null){if(dialog!=null)dialog=null else if(detail!=null)detail=null else page=4}
    val back={detail=null}
    when(detail) {
        "apps"->{AppManagerScreen(onBack=back);return}
        "install"->{InstallApkScreen(onBack=back);return}
        "channels"->{ChannelManagerScreen(onBack=back);return}
        "files"->{FileManagerScreen(onBack=back);return}
        "preview"->{ScreenMirrorScreen(onBack=back);return}
        "mouse"->{AndroidMousePadScreen(
            onBack={detail=null;remoteMode=0},
            airMouse=airMouseSelected,
            onSwipeMode={direction->
                if(!airMouseSelected) {
                    if(direction>0){airMouseSelected=true;remoteMode=2;detail="mouse"}
                    else {detail=null;remoteMode=0}
                } else {
                    if(direction>0){remoteMode=3;detail="androidKeyboard"}
                    else {airMouseSelected=false;remoteMode=1;detail="mouse"}
                }
            }
        );return}
        "androidKeyboard"->{AndroidKeyboardScreen(onBack={detail=null;remoteMode=0},onSwipeMode={direction->
            if(direction<0){airMouseSelected=true;remoteMode=2;detail="mouse"}
            else {detail=null;remoteMode=0}
        });return}
        "shellFull"->{AdbShellScreen(onBack=back);return}
        "infoFull"->{DeviceInfoProScreen(onBack=back);return}
        "mirrorLog"->{MirrorDiagnosticsScreen(onBack=back);return}
        "gamepadFull"->{AndroidGamepadScreen(onBack=back);return}
        "mappingFull"->{RemoteMappingScreen(onBack=back);return}
        "pcKeyboard"->{KeyboardScreen(onBack={detail=null});return}
        "pcWorkspace"->{
            val pcEndpoint = status.address.orEmpty()
            val pcHost = pcEndpoint.substringBefore(':')
            val pcPort = pcEndpoint.substringAfter(':',"27845").toIntOrNull() ?: 27845
            PcWorkspaceScreen(
                host=pcHost, port=pcPort, pin=pin, initialTab=pcWorkspaceTab,
                onBack=back,
                onKeyboard={detail="pcKeyboard"},
                onMove={dx,dy->vm.sendMouseMove(dx,dy)},
                onLeftClick={vm.sendMouseClick(MouseButton.LEFT)},
                onRightClick={vm.sendMouseClick(MouseButton.RIGHT)},
                onScroll={delta->vm.sendScroll(delta)},
                onText={value->vm.sendText(value)},
                onKey={keyCode->vm.sendKey(keyCode,true);vm.sendKey(keyCode,false)}
            )
            return
        }
    }
    Scaffold(containerColor=Background,snackbarHost={SnackbarHost(snack)},bottomBar={
        if(page!=4 && connected){
            Surface(color=Background){
                Row(Modifier.fillMaxWidth().navigationBarsPadding().height(65.dp),
                    horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                    (if(state.mode==AppMode.PC) listOf(
                        Triple("Tools",Icons.Default.Handyman,"tools"),
                        Triple("Screen",Icons.Default.Computer,"pcWorkspace"),
                        Triple("Files",Icons.Default.FolderOpen,"pcWorkspace"),
                        Triple("Info",Icons.Default.Info,"pcWorkspace")
                    ) else listOf(
                        Triple("Tools",Icons.Default.Handyman,"tools"),
                        Triple("Apps",Icons.Default.GridView,"apps"),
                        Triple("Shell",Icons.Default.Terminal,"shellFull"),
                        Triple("Info",Icons.Default.Info,"infoFull")
                    )).forEach { (label,icon,target) ->
                        Column(Modifier.weight(1f).clickable {
                            if(target=="tools"){page=3;detail=null}
                            else if(state.mode==AppMode.PC) {pcWorkspaceTab=label; detail="pcWorkspace"}
                            else if(requireTv())detail=target
                        },horizontalAlignment=Alignment.CenterHorizontally){
                            Surface(color=if(page==3&&target=="tools")DuoColors.AccentContainer else Color.Transparent,
                                shape=CircleShape,modifier=Modifier.width(56.dp).height(29.dp)){
                                Box(contentAlignment=Alignment.Center){
                                    Icon(icon,label,Modifier.size(21.dp),
                                        tint=if(page==3&&target=="tools")DuoColors.OnAccentContainer else Muted)
                                }
                            }
                            Text(label,color=if(page==3&&target=="tools")Ink else Muted,fontSize=11.sp,fontWeight=if(page==3&&target=="tools")FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=15.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            when(page) {
                0->{
                    DuoPadBrandHeader(
                        onMore = { page = 5 },
                        modifier = Modifier.padding(top = 8.dp, bottom = 3.dp)
                    )

                    Surface(
                        modifier=Modifier.fillMaxWidth().height(48.dp),
                        color=Pale,shape=RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal=13.dp),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(9.dp)
                        ) {
                            Surface(color=DuoColors.SurfaceHighest,shape=CircleShape,
                                modifier=Modifier.size(32.dp)) {
                                Box(contentAlignment=Alignment.Center) {
                                    Icon(Icons.Default.Wifi,"Wi-Fi",Modifier.size(20.dp),tint=Ink)
                                }
                            }
                            Surface(Modifier.size(8.dp),shape=CircleShape,color=Online){}
                            Text(
                                if(connected)"Connected to ${status.deviceName ?: "device"}"
                                else "Ready to connect",
                                fontSize=12.sp,color=Ink,fontWeight=FontWeight.Bold,
                                maxLines=1
                            )
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween
                    ){
                        Text("Saved Devices",fontWeight=FontWeight.ExtraBold,
                            fontSize=17.sp,color=Ink,letterSpacing=(-0.35).sp)
                        TextButton(onClick={openNearby("Remote")},
                            contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){
                            Text("See All",fontSize=11.sp,color=Ink,
                                fontWeight=FontWeight.SemiBold)
                        }
                    }
                    val saved=targets.distinctBy { it.endpoint }
                    if(saved.isEmpty()){
                        Surface(shape=RoundedCornerShape(15.dp),color=Paper,
                            border=BorderStroke(0.8.dp,Border),shadowElevation=1.dp){
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(9.dp)){
                                Icon(Icons.Default.Computer,null,tint=Ink,modifier=Modifier.size(24.dp))
                                Text("No saved devices yet",modifier=Modifier.weight(1f),
                                    fontSize=12.sp,color=Muted)
                                Button(onClick={openNearby("Remote")},
                                    colors=ButtonDefaults.buttonColors(containerColor=Accent),
                                    shape=RoundedCornerShape(9.dp),
                                    contentPadding=PaddingValues(horizontal=10.dp)){
                                    Text("Add device",fontSize=10.sp)
                                }
                            }
                        }
                    }
                    saved.take(2).forEach { target ->
                        val live=connected && status.address==target.endpoint
                        Surface(
                            modifier=Modifier.fillMaxWidth().height(66.dp),
                            shape=RoundedCornerShape(16.dp),
                            color=Paper,border=BorderStroke(0.8.dp,Border),
                            shadowElevation=1.dp
                        ){
                            Row(
                                Modifier.fillMaxSize().padding(start=11.dp,end=7.dp),
                                verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(8.dp)
                            ){
                                Icon(
                                    if(target.pc)Icons.Default.Computer else Icons.Default.Tv,
                                    contentDescription=null,modifier=Modifier.size(24.dp),tint=Ink
                                )
                                Column(Modifier.weight(1f),
                                    verticalArrangement=Arrangement.spacedBy(2.dp)){
                                    Text(target.customName ?: target.name,
                                        fontSize=12.sp,fontWeight=FontWeight.Bold,color=Ink,
                                        maxLines=1)
                                    Text(
                                        "${if(target.pc)"Windows" else "Android TV"} · ${target.endpoint.substringBefore(':')}",
                                        fontSize=10.sp,color=Muted,maxLines=1)
                                }
                                Button(
                                    onClick={open(target)},
                                    modifier=Modifier.height(31.dp),
                                    shape=RoundedCornerShape(9.dp),
                                    contentPadding=PaddingValues(horizontal=11.dp),
                                    colors=ButtonDefaults.buttonColors(containerColor=Accent)
                                ){
                                    Text(if(live)"Open" else "Connect",
                                        fontSize=10.sp,fontWeight=FontWeight.Bold,
                                        color=OnAccent,maxLines=1)
                                }
                                Box {
                                    IconButton(
                                        onClick={menuTarget=target.endpoint},
                                        modifier=Modifier.size(29.dp)
                                    ){
                                        Icon(Icons.Default.MoreVert,"Device options",
                                            Modifier.size(19.dp),tint=Ink)
                                    }
                                    DropdownMenu(
                                        expanded=menuTarget==target.endpoint,
                                        onDismissRequest={menuTarget=null}
                                    ){
                                        DropdownMenuItem(
                                            text={Text("Rename")},
                                            onClick={
                                                menuTarget=null;renameTarget=target
                                                renameText=target.customName ?: target.name
                                                dialog="rename"
                                            }
                                        )
                                        DropdownMenuItem(
                                            text={Text("Remove from saved")},
                                            onClick={
                                                menuTarget=null
                                                targets=targets.filterNot{it.endpoint==target.endpoint}
                                                if(target.pc)TrustedPcPairing.forget(context,target.endpoint)
                                                saveTargets(context,targets)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "Quick Actions",fontSize=17.sp,fontWeight=FontWeight.ExtraBold,
                        color=Ink,letterSpacing=(-0.35).sp,
                        modifier=Modifier.padding(top=4.dp)
                    )
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        ConceptAction("Mouse Pad",Icons.Default.Mouse,Modifier.weight(1f)){
                            openNearby("Mouse Pad")
                        }
                        ConceptAction("Air Mouse",Icons.Default.NearMe,Modifier.weight(1f)){
                            openNearby("Air Mouse")
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        ConceptAction("Remote",Icons.Default.SettingsRemote,Modifier.weight(1f)){
                            openNearby("Remote")
                        }
                        ConceptAction("Mirror",Icons.Default.CastConnected,Modifier.weight(1f)){
                            openNearby("Mirror")
                        }
                    }
                }
                4->{
                    DuoPadBrandHeader(
                        onMore = { dialog = "settings" },
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        val currentlyDark = appearance == "dark"
                        FilledTonalButton(
                            onClick = { onAppearanceChange(if (currentlyDark) "light" else "dark") },
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                if (currentlyDark) Icons.Default.WbSunny else Icons.Default.DarkMode,
                                if (currentlyDark) "Switch to light theme" else "Switch to dark theme",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (currentlyDark) "Light" else "Dark", fontSize = 12.sp)
                        }
                    }
                    Text("Last connected",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Ink)
                    val lastConnected=targets.firstOrNull()
                    if(lastConnected!=null){
                        Surface(onClick={open(lastConnected)},modifier=Modifier.fillMaxWidth(),
                            shape=RoundedCornerShape(17.dp),color=Pale){
                            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
                                Icon(if(lastConnected.pc)Icons.Default.Computer else Icons.Default.Tv,
                                    null,Modifier.size(29.dp),tint=Ink)
                                Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)){
                                    Text(lastConnected.customName?:lastConnected.name,
                                        fontWeight=FontWeight.Bold,color=Ink,fontSize=17.sp)
                                    Text(lastConnected.endpoint.substringBefore(':'),color=Muted,fontSize=12.sp)
                                }
                                Icon(Icons.Default.ChevronRight,"Open device",tint=Ink)
                            }
                        }
                    }
                    Text("Select device",fontSize=18.sp,fontWeight=FontWeight.Bold,
                        color=Ink,modifier=Modifier.padding(top=9.dp))
                    Text("Choose a device to connect. Save after connecting for faster access; saved devices retry automatically.",
                        fontSize=12.sp,color=Muted)
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        listOf("All","Android TV","PC","Saved").forEach { type ->
                            FilterChip(selected=scanFilter==type,onClick={scanFilter=type},
                                colors=FilterChipDefaults.filterChipColors(
                                    selectedContainerColor=MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor=MaterialTheme.colorScheme.onSecondaryContainer),
                                label={Text(type,fontSize=11.sp)})
                        }
                    }
                    if(state.isScanning || state.isScanningPc){
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(if(state.isScanningPc) "Searching for PC Host (up to 15 seconds)…" else "Searching for Android TVs…",
                            fontSize=12.sp,color=Muted)
                    }
                    if(scanFilter=="PC" && !state.isScanningPc && state.pcScanMessage!=null){
                        Text(state.pcScanMessage!!,fontSize=12.sp,color=Muted)
                    }
                    val androidFound=state.androidDevices.distinctBy { it.id }
                    val pcFound=state.pcDevices.distinctBy { it.id }
                    val savedTargets=targets.distinctBy { it.endpoint }
                    val defaultTargetTint=Teal
                    fun targetButton(target:Target,subtitle:String,tint:Color=defaultTargetTint): @Composable ()->Unit = {
                        val isSaved=targets.any { it.endpoint==target.endpoint }
                        OutlinedButton(onClick={open(target)},modifier=Modifier.fillMaxWidth(),
                            shape=RoundedCornerShape(17.dp),
                            border=BorderStroke(1.dp,Border),
                            contentPadding=PaddingValues(horizontal=12.dp,vertical=9.dp)) {
                            Icon(if(target.pc)Icons.Default.Computer else Icons.Default.Tv,null,Modifier.size(25.dp),tint=tint)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(target.customName ?: target.name,maxLines=1,fontWeight=FontWeight.SemiBold,color=Ink)
                                Text(subtitle,fontSize=11.sp,color=Muted,maxLines=1)
                                Text(if(isSaved) "★ Saved · automatic reconnect" else "Connect, then save this device",
                                    fontSize=10.sp,color=if(isSaved)DuoColors.Secondary else Muted,maxLines=1)
                            }
                            IconButton(onClick={
                                if(isSaved) {
                                    targets=targets.filterNot{it.endpoint==target.endpoint}
                                    if(target.pc)TrustedPcPairing.forget(context,target.endpoint)
                                    saveTargets(context,targets)
                                    message("Removed from saved devices")
                                } else if(connected && status.address==target.endpoint) {
                                    targets=listOf(target)+targets.filterNot{it.endpoint==target.endpoint}
                                    saveTargets(context,targets)
                                    message("Device saved")
                                } else message("Connect first, then save the authorized device")
                            },modifier=Modifier.size(37.dp)) {
                                Icon(if(isSaved) Icons.Default.Star else Icons.Default.StarBorder,
                                    if(isSaved) "Remove saved device" else "Save device",
                                    tint=if(isSaved)DuoColors.Secondary else Muted)
                            }
                            Icon(Icons.Default.ChevronRight,null,tint=Teal)
                        }
                    }
                    if(scanFilter=="Saved"){
                        if(savedTargets.isEmpty() && !state.isScanning) Text("No saved authorized devices yet.",fontSize=12.sp,color=Muted)
                        savedTargets.forEach { target -> targetButton(target,target.endpoint+" · Saved / previously authorized")() }
                    } else {
                        if(scanFilter!="PC"){
                            androidFound.forEach { device ->
                                val saved=savedTargets.firstOrNull { it.endpoint==device.id }
                                val name=saved?.customName ?: saved?.name ?: device.name
                                targetButton(Target(name,device.id,false,saved?.customName),
                                    device.id + if(saved!=null)" · Saved" else " · Tap to authorize & connect",
                                    if(saved!=null)Teal else Muted)()
                            }
                            if(scanFilter=="Android TV" || scanFilter=="All"){
                                savedTargets.filter { !it.pc && androidFound.none { d->d.id==it.endpoint } }
                                    .forEach { target -> targetButton(target,target.endpoint+" · Saved / offline")() }
                            }
                        }
                        if(scanFilter!="Android TV"){
                            pcFound.forEach { host ->
                                val saved=savedTargets.firstOrNull { it.endpoint==host.address }
                                val name=saved?.customName ?: saved?.name ?: host.name
                                targetButton(Target(name,host.address,true,saved?.customName),
                                    host.address + if(host.isReachable)" · PC host online" else " · Saved PC",
                                    if(host.isReachable)Teal else Muted)()
                            }
                            if(scanFilter=="PC" || scanFilter=="All"){
                                savedTargets.filter { it.pc && pcFound.none { h->h.address==it.endpoint } }
                                    .forEach { target -> targetButton(target,target.endpoint+" · Saved / offline")() }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick={ if(scanFilter=="PC") vm.scanPc() else vm.scanNearby() },
                        enabled=if(scanFilter=="PC") !state.isScanningPc else !state.isScanning && !state.isScanningPc,
                        modifier=Modifier.fillMaxWidth()
                    ){
                        Icon(Icons.Default.Refresh,null)
                        Text("  " + if(scanFilter=="PC") {
                            if(state.hasScannedPc) "Search Again" else "Search PC"
                        } else "Scan again")
                    }
                    OutlinedButton(onClick={
                        pc=scanFilter=="PC"; pcTransport="wifi";
                        port=if(pc) "27845" else "5555"; dialog="connect"
                    },modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.Add,null);Text(if(scanFilter=="PC") "  Connect PC by IP" else "  Connect by IP")
                    }
                    Surface(onClick={detail="mirrorLog"},modifier=Modifier.fillMaxWidth(),
                        shape=RoundedCornerShape(19.dp),color=Pale,border=BorderStroke(1.dp,Border)) {
                        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport,"Diagnostics",tint=Ink)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Diagnostic logs",fontWeight=FontWeight.Bold,color=Ink)
                                Text("PC discovery, connections, mirror events and errors",fontSize=11.sp,color=Muted)
                            }
                            TextButton(onClick={dialog="clearLogs"},contentPadding=PaddingValues(4.dp)) {
                                Icon(Icons.Default.DeleteOutline,"Clear old diagnostic logs",Modifier.size(18.dp),tint=Teal)
                                Spacer(Modifier.width(3.dp))
                                Text("Clear",fontSize=11.sp,color=Teal)
                            }
                        }
                    }
                }
                1->{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        IconButton(onClick={page=0},modifier=Modifier.size(39.dp)){
                            Icon(Icons.AutoMirrored.Filled.ArrowBack,"Home")
                        }
                        Image(painter=painterResource(R.drawable.duopad_original_logo),
                            contentDescription="DuoPad logo",modifier=Modifier.size(32.dp))
                        Column(Modifier.weight(1f)){
                            Text(status.deviceName ?: "Remote control",fontSize=17.sp,
                                fontWeight=FontWeight.Bold,maxLines=1)
                            Text(if(connected)"● Connected" else "Choose a device",
                                fontSize=10.sp,color=if(connected)Teal else Muted)
                        }
                        IconButton(onClick={
                            if(requireDevice())runCatching{
                                voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                            }.onFailure{message("Voice recognition unavailable")}
                        },modifier=Modifier.size(38.dp)){
                            Icon(Icons.Default.Mic,"Voice",tint=Teal)
                        }
                        IconButton(onClick={dialog="settings"},modifier=Modifier.size(38.dp)){
                            Icon(Icons.Default.MoreVert,"Options")
                        }
                    }
                    // Modes remain usable without opening an oversized second page.
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                        listOf("D-pad","Touch","Air","Keyboard").forEachIndexed{i,label->
                            val selected=remoteMode==i
                            Surface(onClick={
                                remoteMode=i
                                if(i==1 && requireTv()){airMouseSelected=false;detail="mouse"}
                                if(i==2 && requireTv()){airMouseSelected=true;detail="mouse"}
                                if(i==3 && requireTv())detail="androidKeyboard"
                            },modifier=Modifier.weight(1f).height(35.dp),
                                shape=RoundedCornerShape(10.dp),
                                color=if(selected)DuoColors.AccentContainer else Paper,
                                border=BorderStroke(1.dp,Border)){
                                Box(contentAlignment=Alignment.Center){
                                    Text(label,fontSize=11.sp,maxLines=1,
                                        fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,
                                        color=if(selected)DuoColors.OnAccentContainer else Ink)
                                }
                            }
                        }
                    }
                    if(remoteMode==0){
                        Pad(::key,196.dp)
                    }else if(remoteMode==1){
                        var dx by remember{mutableFloatStateOf(0f)}
                        var dy by remember{mutableFloatStateOf(0f)}
                        Box(Modifier.fillMaxWidth().height(175.dp)
                            .clip(RoundedCornerShape(16.dp)).background(Pale)
                            .border(1.dp,Border,RoundedCornerShape(16.dp))
                            .pointerInput(connected,state.mode){
                                detectDragGestures(onDragStart={dx=0f;dy=0f},onDragEnd={
                                    if(state.mode==AppMode.ANDROID)key(
                                        if(kotlin.math.abs(dx)>kotlin.math.abs(dy))
                                            if(dx>0)22 else 21 else if(dy>0)20 else 19)
                                }){change,drag->
                                    change.consume();dx+=drag.x;dy+=drag.y
                                    if(connected && state.mode==AppMode.PC)
                                        vm.sendMouseMove(drag.x*2,drag.y*2)
                                }
                            }.pointerInput(connected){
                                detectTapGestures{
                                    if(connected&&state.mode==AppMode.PC)
                                        vm.sendMouseClick(MouseButton.LEFT) else key(23)
                                }
                            },contentAlignment=Alignment.Center){
                            Column(horizontalAlignment=Alignment.CenterHorizontally){
                                Icon(Icons.Default.TouchApp,null,Modifier.size(30.dp),tint=Teal)
                                Text("Touch or swipe",fontSize=12.sp,color=Muted)
                            }
                        }
                    }else{
                        Surface(color=Pale,shape=RoundedCornerShape(16.dp),
                            modifier=Modifier.fillMaxWidth().height(125.dp)){
                            Box(contentAlignment=Alignment.Center){
                                OutlinedButton(onClick={if(requireTv())detail=if(remoteMode==2)"mouse" else "androidKeyboard"}){
                                    Text("Open ${if(remoteMode==2)"air mouse" else "keyboard"}")
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                        CompactRemoteKey("Back",Icons.AutoMirrored.Filled.ArrowBack){key(4)}
                        CompactRemoteKey("Home",Icons.Default.Home){key(3)}
                        CompactRemoteKey("Menu",Icons.Default.Menu){key(82)}
                        CompactRemoteKey("Mute",Icons.AutoMirrored.Filled.VolumeOff){key(164)}
                        CompactRemoteKey("Power",Icons.Default.PowerSettingsNew){if(requireTv())dialog="power"}
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceEvenly){
                        VolumeKnob(onUp={key(24)},onDown={key(25)},onMute={key(164)})
                        Column(horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Text("Channel",fontSize=11.sp,color=Muted)
                            Row(verticalAlignment=Alignment.CenterVertically){
                                IconButton(onClick={key(167)}){Icon(Icons.Default.Remove,"Channel down")}
                                Text("CH",fontSize=13.sp,fontWeight=FontWeight.Bold)
                                IconButton(onClick={key(166)}){Icon(Icons.Default.Add,"Channel up")}
                            }
                            OutlinedButton(onClick={showNumberPad=!showNumberPad},
                                modifier=Modifier.height(35.dp),
                                contentPadding=PaddingValues(horizontal=12.dp)){
                                Icon(Icons.Default.Dialpad,null,Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if(showNumberPad)"Hide 123" else "123",fontSize=12.sp)
                            }
                        }
                    }
                    if(showNumberPad && state.mode==AppMode.ANDROID){
                        listOf(listOf(1,2,3),listOf(4,5,6),listOf(7,8,9),listOf(-1,0,-2))
                            .forEach{line->
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                                    line.forEach{digit->
                                        Surface(onClick={
                                            when(digit){
                                                -1->key(67) // delete
                                                -2->key(66) // enter
                                                else->key(7+digit) // KEYCODE_0 is 7
                                            }
                                        },modifier=Modifier.weight(1f).height(39.dp),
                                            shape=RoundedCornerShape(10.dp),color=Paper,
                                            border=BorderStroke(1.dp,Border)){
                                            Box(contentAlignment=Alignment.Center){
                                                Text(when(digit){-1->"⌫";-2->"OK";else->digit.toString()},
                                                    fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                    }
                    if(!showNumberPad && state.mode==AppMode.ANDROID){
                        OutlinedButton(onClick={showRemoteExtras=!showRemoteExtras},
                            modifier=Modifier.fillMaxWidth().height(34.dp),
                            contentPadding=PaddingValues(horizontal=8.dp)){
                            Text(if(showRemoteExtras)"Hide media & shortcuts" else "More: media, colors & shortcuts",
                                fontSize=11.sp)
                        }
                        if(showRemoteExtras){
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                                listOf("Red" to 183,"Green" to 184,"Yellow" to 185,"Blue" to 186)
                                    .forEach{(label,code)->
                                        OutlinedButton(onClick={key(code)},modifier=Modifier.weight(1f),
                                            contentPadding=PaddingValues(2.dp)){
                                            Text(label,fontSize=10.sp,maxLines=1)
                                        }
                                    }
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                                listOf("|◀" to 88,"◀◀" to 89,"▶❚❚" to 85,"▶▶" to 90,"▶|" to 87)
                                    .forEach{(label,code)->
                                        OutlinedButton(onClick={key(code)},modifier=Modifier.weight(1f),
                                            contentPadding=PaddingValues(2.dp)){
                                            Text(label,fontSize=11.sp,maxLines=1)
                                        }
                                    }
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                                listOf("YouTube" to "com.google.android.youtube.tv",
                                    "Netflix" to "com.netflix.ninja",
                                    "Prime" to "com.amazon.amazonvideo.livingroom")
                                    .forEach{(label,pkg)->
                                        OutlinedButton(onClick={
                                            if(requireTv())scope.launch{
                                                vm.executeAdbAwait("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
                                            }
                                        },modifier=Modifier.weight(1f),contentPadding=PaddingValues(2.dp)){
                                            Text(label,fontSize=10.sp,maxLines=1)
                                        }
                                    }
                            }
                        }
                    }
                }
                2->{
                    Title("Screen mirror","View your device screen in real time.")
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Teal),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Tv,null,Modifier.size(18.dp));Text("  TV → Phone",fontSize=12.sp)};OutlinedButton(onClick={dialog="cast"},modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp)){Text("Phone → TV",fontSize=12.sp)}}
                    Surface(color=Pale,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.fillMaxWidth().height(210.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(Icons.Default.Tv,null,Modifier.size(54.dp),tint=Muted);Spacer(Modifier.height(12.dp));Text(if(connected)status.deviceName?:"TV screen" else "Your screen, right here",fontWeight=FontWeight.SemiBold);Text("Connect an Android TV to begin.",fontSize=12.sp,color=Muted)}}
                    OutlinedButton(onClick={
                        if(requireTv()) {
                            val endpoint = status.address
                            // Write a durable breadcrumb before the Android Activity launch.
                            // This survives a fatal native-video decoder crash on the next app start.
                            MirrorCrashRecorder.launch(context, !endpoint.isNullOrBlank())
                            try {
                                val intent=Intent(context,DoupadMirrorActivity::class.java)
                                    .putExtra("endpoint",endpoint)
                                context.startActivity(intent)
                            } catch (error: Exception) {
                                MirrorCrashRecorder.note(context,
                                    "START_ACTIVITY_FAILED: ${error.javaClass.simpleName}: ${error.message}")
                                MirrorCrashRecorder.finish(context,"launch failed")
                                message("Unable to open mirror: ${error.message}")
                            }
                        }
                    },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                        Icon(Icons.Default.Fullscreen,null);Text("Live video · H.264 low-latency")
                    }
                    OutlinedButton(onClick={
                        if(requireTv()){
                            val endpoint=status.address
                            MirrorCrashRecorder.launch(context,!endpoint.isNullOrBlank())
                            MirrorCrashRecorder.note(context,"Compatibility stream: software-first decoder requested")
                            try {
                                context.startActivity(
                                    Intent(context,DoupadMirrorActivity::class.java)
                                        .putExtra("endpoint",endpoint)
                                        .putExtra("safe_decoder",true)
                                )
                            } catch(error:Exception){
                                MirrorCrashRecorder.note(context,
                                    "SAFE_MODE_LAUNCH_FAILED: ${error.javaClass.simpleName}: ${error.message}")
                                MirrorCrashRecorder.finish(context,"compatibility launch failed")
                                message("Could not open compatibility stream: ${error.message}")
                            }
                        }
                    },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                        Icon(Icons.Default.Shield,null);Spacer(Modifier.width(8.dp));Text("Live video · Compatibility decoder")
                    }
                    OutlinedButton(onClick={
                        if(requireTv()){
                            val endpoint=status.address
                            MirrorCrashRecorder.launch(context,!endpoint.isNullOrBlank())
                            MirrorCrashRecorder.note(context,"HQ + audio mode: high TV encoder load")
                            context.startActivity(Intent(context,DoupadMirrorActivity::class.java)
                                .putExtra("endpoint",endpoint)
                                .putExtra("tv_audio_mode",true))
                        }
                    },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                        Icon(Icons.AutoMirrored.Filled.VolumeUp,null);Spacer(Modifier.width(8.dp))
                        Text("High quality + TV audio (higher load)")
                    }
                    OutlinedButton(onClick={
                        if(requireTv()){
                            val endpoint=status.address
                            MirrorCrashRecorder.launch(context,!endpoint.isNullOrBlank())
                            MirrorCrashRecorder.note(context,"Smooth TV mode requested: low-load video-only stream")
                            context.startActivity(Intent(context,DoupadMirrorActivity::class.java)
                                .putExtra("endpoint",endpoint)
                                .putExtra("smooth_tv_mode",true))
                        }
                    },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                        Icon(Icons.Default.Speed,null);Spacer(Modifier.width(8.dp))
                        Text("Smooth TV mode · video only (640p/12fps)")
                    }
                    OutlinedButton(onClick={
                        if(requireTv()){
                            val endpoint=status.address
                            MirrorCrashRecorder.launch(context,!endpoint.isNullOrBlank())
                            MirrorCrashRecorder.note(context,
                                "Aceconn mode requested: alternate device-side H.264 encoders")
                            try{
                                context.startActivity(
                                    Intent(context,DoupadMirrorActivity::class.java)
                                        .putExtra("endpoint",endpoint)
                                        .putExtra("aceconn_mode",true)
                                )
                            }catch(error:Exception){
                                MirrorCrashRecorder.note(context,
                                    "ACE_CONTEXT_FAILED: ${error.javaClass.simpleName}: ${error.message}")
                                MirrorCrashRecorder.finish(context,"TV-box mode launch failed")
                                message("Could not open TV-box mode: ${error.message}")
                            }
                        }
                    },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                        Icon(Icons.Default.Tv,null);Spacer(Modifier.width(8.dp))
                        Text("TV Box mode · Aceconn compatibility")
                    }
                    Text("Smooth mode reduces load on the TV. Aceconn mode tries alternate encoders; the in-mirror SHOT button offers slow screenshot preview if live video stays black. DRM-protected content cannot be captured.",
                        fontSize=11.sp,color=Muted)
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Quick("Screenshot",Icons.Default.PhotoCamera,Modifier.weight(1f)){if(requireTv()){detail="preview"}};Quick("Record",Icons.Default.FiberManualRecord,Modifier.weight(1f)){dialog="record"}}
                    Text("Video streaming",fontWeight=FontWeight.SemiBold);Text("Adaptive H.264 · 640–720p · 10–15fps · optimized for TV load",fontSize=12.sp,color=Muted)
                    Text("TV audio: enabled on supported Android versions; silent or protected content may not be capturable.",
                        fontSize=12.sp,color=Muted)
                    OutlinedButton(onClick={detail="mirrorLog"},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                        Icon(Icons.Default.BugReport,null);Spacer(Modifier.width(7.dp));Text("Mirror diagnostics & error log")
                    }
                    Hint("Availability depends on TV firmware. DRM-protected video may appear blank.")
                }
                3->{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={page=4}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Devices",tint=Ink)}
                        Image(painter=painterResource(R.drawable.duopad_original_logo),
                            contentDescription="DuoPad logo",modifier=Modifier.size(30.dp))
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                            Text(status.deviceName ?: "Connected device",
                                fontWeight=FontWeight.Bold,fontSize=18.sp,color=Ink,maxLines=1)
                            Text(if(connected)"CONNECTED" else "DISCONNECTED",fontSize=11.sp,
                                fontWeight=FontWeight.SemiBold,color=Muted)
                        }
                        IconButton(onClick={dialog="settings"}){Icon(Icons.Default.MoreVert,"Device options")}
                    }
                    if(state.mode==AppMode.PC) {
                        Title("Windows PC Workspace","Control desktop, files, drives and PC information.")
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            ToolTile("PC screen","",Icons.Default.Computer,Modifier.weight(1f)) {
                                pcWorkspaceTab="Screen"; detail="pcWorkspace"
                            }
                            ToolTile("PC drives & files","",Icons.Default.FolderOpen,Modifier.weight(1f)) {
                                pcWorkspaceTab="Files"; detail="pcWorkspace"
                            }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            ToolTile("System information","",Icons.Default.Memory,Modifier.weight(1f)) {
                                pcWorkspaceTab="Info"; detail="pcWorkspace"
                            }
                            ToolTile("Keyboard","",Icons.Default.Keyboard,Modifier.weight(1f)) {
                                detail="pcKeyboard"
                            }
                        }
                        Hint("DuoPad PC Host Pro v5 must be running on your PC. Connect securely over local Wi-Fi or your private Tailscale network.")
                    } else {
                    val mainTools=listOf(
                        Triple("Install",Icons.Default.InstallMobile,"install"),
                        Triple("Upload to Downloads",Icons.Default.UploadFile,"files"),
                        Triple("File manager",Icons.Default.FolderOpen,"files"),
                        Triple("Channels",Icons.Default.LiveTv,"channels"),
                        Triple("Screen mirror",Icons.Default.CastConnected,"mirror"),
                        Triple("Gamepad",Icons.Default.SportsEsports,"gamepadFull"),
                        Triple("Screenshot",Icons.Default.PhotoCamera,"preview"),
                        Triple("Screen record",Icons.Default.Videocam,"record")
                    )
                    mainTools.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            row.forEach { (label,ic,action) ->
                                ToolTile(label,"",ic,Modifier.weight(1f)){
                                    if(requireTv()){
                                        when(action){
                                            "mirror"->{page=2;detail=null}
                                            "record"->dialog="record"
                                            else->detail=action
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        ToolTile("Clear cache","",Icons.Default.DeleteSweep,Modifier.weight(1f)){
                            if(requireTv())dialog="clearCache"
                        }
                        ToolTile("Screensaver","",Icons.Default.AutoAwesome,Modifier.weight(1f)){
                            if(requireTv())showCommand("Screensaver","am start -a android.intent.action.DREAMING_STARTED")
                        }
                        ToolTile("Power Menu","",Icons.Default.PowerSettingsNew,Modifier.weight(1f)){
                            if(requireTv())dialog="power"
                        }
                    }
                    OutlinedButton(onClick={page=1},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.SettingsRemote,null)
                        Spacer(Modifier.width(8.dp))
                        Text("Remote control")
                    }
                    } // TV-only cards
                }
                5->{
                    Title("Settings","Control your connected devices and preferences.")
                    Surface(shape=RoundedCornerShape(15.dp),
                        border=BorderStroke(1.dp,Border),color=Paper){
                        Column(Modifier.padding(horizontal=15.dp,vertical=14.dp),
                            verticalArrangement=Arrangement.spacedBy(7.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(11.dp)){
                                Surface(Modifier.size(38.dp),color=Accent,
                                    shape=RoundedCornerShape(10.dp)){
                                    Box(contentAlignment=Alignment.Center){
                                        Image(painter=painterResource(R.drawable.duopad_original_logo),
                                            contentDescription="DuoPad logo",modifier=Modifier.size(37.dp))
                                    }
                                }
                                Column{
                                    Text("DuoPad Pro",fontWeight=FontWeight.Bold,color=Ink)
                                    Text("Version ${BuildConfig.VERSION_NAME}",
                                        fontSize=11.sp,color=Muted)
                                }
                            }
                            Text("Appearance · ${when(appearance){"dark"->"Midnight Graphite";"bento"->"Bento Clay";else->"Handmade Light"}}",
                                fontSize=11.sp,color=Muted)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                val isDark=appearance=="dark"
                                FilledTonalButton(onClick={onAppearanceChange(if(isDark) "light" else "dark")}) {
                                    Icon(if(isDark)Icons.Default.WbSunny else Icons.Default.DarkMode,
                                        null,Modifier.size(17.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(if(isDark)"Switch to Light" else "Switch to Dark",fontSize=12.sp)
                                }
                                TextButton(onClick={onAppearanceChange("bento")}){
                                    Text("Bento Clay",fontSize=11.sp)
                                }
                            }
                        }
                    }
                    Surface(shape=RoundedCornerShape(15.dp),
                        border=BorderStroke(1.dp,Border),color=Paper){
                        Column{
                            ToolRow("Devices","Discover and connect TVs or PCs",Icons.Default.Devices){
                                openNearby("Remote")
                            }
                            HorizontalDivider(color=Border)
                            ToolRow("Mirror diagnostics","View or copy video error logs",Icons.Default.BugReport){
                                detail="mirrorLog"
                            }
                            HorizontalDivider(color=Border)
                            ToolRow("Saved device names","Rename devices from their cards",Icons.Default.Edit){
                                openNearby("Remote")
                            }
                            if(connected){
                                HorizontalDivider(color=Border)
                                ToolRow("Disconnect","End current device connection",Icons.Default.LinkOff){
                                    automaticReconnect=false;vm.disconnect();message("Disconnected")
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick={dialog="clearSaved"},modifier=Modifier.fillMaxWidth(),
                        shape=RoundedCornerShape(12.dp)){
                        Icon(Icons.Default.DeleteOutline,null,Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Clear saved devices")
                    }
                }
            }
        }
    }
    if(dialog!=null) AlertDialog(onDismissRequest={dialog=null},containerColor=Pale,title={Text(when(dialog){"connect"->"Add a device";"saveDevice"->"Save connected device?";"connectingSaved"->"Connecting to device";"rename"->"Rename device";"keyboard"->"Keyboard";"settings"->"DuoPad Pro";"clearSaved"->"Clear saved devices?";"clearLogs"->"Clear old diagnostic logs?";"clearCache"->"Clear app caches?";"scan"->"Nearby devices";"shell"->"Shell";"power"->"Power options";"gamepad"->"Gamepad";else->dialog!!},fontWeight=FontWeight.Bold)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            when(dialog){
                "clearSaved"->{
                    Text("Remove all saved TV and PC entries from this phone? Existing device authorization on your TV is not changed.",color=Muted,fontSize=13.sp)
                }
                "clearLogs"->Text("Delete local mirror event logs? Android's system exit history will remain available.",fontSize=13.sp,color=Muted)
                "saveDevice"->{
                    Text("Save ${pendingSaveTarget?.name.orEmpty()} (${pendingSaveTarget?.endpoint.orEmpty()}) for automatic reconnect on the same network?",
                        fontSize=13.sp,color=Ink)
                    Text("Only save devices you own or are authorized to control. ADB authorization and PC host security are still required.",
                        fontSize=12.sp,color=Muted)
                }
                "rename"->{
                    Text(renameTarget?.endpoint ?: "",fontSize=12.sp,color=Muted)
                    OutlinedTextField(renameText,{renameText=it.take(45)},label={Text("Device name")},singleLine=true)
                    Text("The detected model remains unchanged. Only the saved name is updated.",fontSize=11.sp,color=Muted)
                }
                "connectingSaved"->{
                    Text("Connecting to $ip:$port",fontSize=13.sp,color=Muted)
                    if(status.state==ConnectionState.CONNECTING) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Establishing connection…",fontSize=12.sp,color=Muted)
                    }
                    if(status.state==ConnectionState.ERROR) {
                        Text(status.errorMessage ?: "Connection failed.",fontSize=12.sp,color=MaterialTheme.colorScheme.error)
                        Text("Tap Retry or Close.",fontSize=11.sp,color=Muted)
                    }
                }
                "connect"->{
                    Row{
                        FilterChip(selected=!pc,onClick={pc=false;port="5555"},label={Text("Android TV")})
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected=pc,onClick={pc=true;port="27845"},label={Text("PC")})
                    }
                    if(!pc) {
                        OutlinedTextField(ip,{ip=it},label={Text("IP address")},singleLine=true)
                        OutlinedTextField(port,{port=it.filter(Char::isDigit)},label={Text("Port")},singleLine=true)
                        Text("Enable network/USB debugging and accept the authorization prompt on your TV. Pairing-code TLS is not supported.",fontSize=12.sp,color=Muted)
                    } else {
                        Text("Choose how you want to connect",fontWeight=FontWeight.SemiBold)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected=pcTransport=="bluetooth",onClick={pcTransport="bluetooth"},label={Text("Bluetooth Direct")})
                            FilterChip(selected=pcTransport=="wifi",onClick={pcTransport="wifi"},label={Text("Wi-Fi")})
                        }
                        if(pcTransport=="bluetooth") {
                            Surface(shape=RoundedCornerShape(14.dp),color=DuoColors.AccentSoft,border=BorderStroke(1.dp,Teal.copy(alpha=.35f))) {
                                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                        Icon(Icons.Default.Bluetooth,null,tint=Teal)
                                        Column { Text("Bluetooth Direct",fontWeight=FontWeight.Bold);Text("No PC app required",fontSize=12.sp,color=Teal) }
                                    }
                                    Text("Pair this phone from Windows, macOS or Linux Bluetooth settings. DuoPad Pro acts as a mouse + keyboard.",fontSize=12.sp,color=Muted)
                                    Button(onClick={startBluetoothDirect()},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Teal)){Text("Start Bluetooth Direct")}
                                }
                            }
                        } else {
                            if(state.isScanningPc) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Looking for DuoPad Pro Host on your network…",fontSize=12.sp,color=Muted)
                            }
                            state.pcDevices.take(6).forEach { host ->
                                OutlinedButton(onClick={
                                    val h=host.address.substringBefore(':')
                                    val p=host.address.substringAfter(':',"27845").toIntOrNull()?:27845
                                    ip=h;port=p.toString()
                                    if(host.requiresPin) pin="" else { vm.connectNetwork(h,p,null);dialog=null }
                                },modifier=Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Computer,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) { Text(host.name,maxLines=1);Text(host.address+if(host.isReachable)" · Online" else " · Saved",fontSize=11.sp,color=Muted) }
                                }
                            }
                            if(!state.isScanningPc && state.pcDevices.none { it.isReachable }) {
                                Surface(shape=RoundedCornerShape(14.dp),color=Pale,border=BorderStroke(1.dp,Border)) {
                                    Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Text("DuoPad Pro Host required for Wi-Fi",fontWeight=FontWeight.Bold)
                                        Text("Bluetooth Direct works without any PC software. For Wi-Fi control, run the small DuoPad Pro Host on Windows, macOS or Linux.",fontSize=12.sp,color=Muted)
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick={vm.scanPc()},enabled=!state.isScanningPc,modifier=Modifier.weight(1f)){
                                                Text(if(state.hasScannedPc) "Search Again" else "Search PC")
                                            }
                                            Button(onClick={
                                                runCatching { DuoPadHostShare.share(context) }
                                                    .onFailure { message("Could not share the Windows Host package: ${it.message}") }
                                            },modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Teal)){Text("Get Host")}
                                        }
                                        TextButton(onClick={scanPcQr()},modifier=Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.QrCodeScanner,null,Modifier.size(18.dp))
                                            Spacer(Modifier.width(7.dp)); Text("Scan Host QR Code")
                                        }
                                        TextButton(onClick={pcTransport="bluetooth"},modifier=Modifier.fillMaxWidth()){Text("Use Bluetooth Direct instead")}
                                    }
                                }
                            }
                            Text("Manual Wi-Fi connection",fontWeight=FontWeight.SemiBold)
                            OutlinedTextField(ip,{ip=it},label={Text("PC IP address")},singleLine=true)
                            OutlinedTextField(port,{port=it.filter(Char::isDigit)},label={Text("Port")},singleLine=true)
                            OutlinedTextField(pin,{pin=it},label={Text("Host pairing PIN (optional)")},singleLine=true)
                            OutlinedButton(onClick={scanPcQr()},modifier=Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.QrCodeScanner,null,Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp)); Text("Connect by QR Code")
                            }
                            OutlinedButton(onClick={
                                runCatching { DuoPadHostShare.share(context) }
                                    .onFailure { message("Windows Host share failed: ${it.message}") }
                            },modifier=Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Share,null,Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp)); Text("Send Windows Host to PC · Bluetooth / Quick Share")
                            }
                            Text("On Windows, accept the ZIP, extract it and run Run-DOUPAD-Host.bat. User approval is required; automatic installation is not supported.",
                                fontSize=11.sp,color=Muted)
                        }
                    }
                    if(localError.isNotBlank())Text(localError,color=MaterialTheme.colorScheme.error)
                    if(connectRequested && status.state==ConnectionState.CONNECTING) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(if(pc) "Connecting to Windows Host and verifying PIN…" else
                            "Waiting for device authorization. Approve the prompt on your TV if shown…",
                            fontSize=12.sp,color=Muted)
                    }
                    if(connectRequested && status.state==ConnectionState.ERROR) {
                        Text(status.errorMessage ?: "Connection failed. Check debugging and try again.",fontSize=12.sp,color=MaterialTheme.colorScheme.error)
                        Text(if(pc) "Check PC Diagnostic Logs and the Host window, then tap Retry." else
                            "Connection stays open here so you can read the error and retry.",
                            fontSize=11.sp,color=Muted)
                    }
                }
                "scan"->{
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("All","Android","PC").forEach { type ->
                            FilterChip(selected=scanFilter==type,onClick={scanFilter=type},label={Text(type)})
                        }
                    }
                    if (state.isScanning || state.isScanningPc) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Scanning Android ADB devices and Windows hosts…",fontSize=12.sp,color=Muted)
                    }
                    if (scanFilter!="PC") {
                        state.androidDevices.distinctBy { it.id }.forEach { device ->
                            OutlinedButton(
                                onClick={
                                    ip=device.ip;port=device.port.toString();pc=false
                                    localError="";connectRequested=false;dialog="connect"
                                },
                                modifier=Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Tv,null,Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(device.name,maxLines=1)
                                    Text("${device.ip}:${device.port} · Android TV / Box",fontSize=11.sp,color=Muted)
                                }
                                Icon(Icons.Default.ChevronRight,null,Modifier.size(18.dp))
                            }
                        }
                    }
                    if (scanFilter!="Android") {
                        state.pcDevices.distinctBy { it.id }.forEach { device ->
                            OutlinedButton(
                                onClick={
                                    ip=device.address.substringBefore(':')
                                    port=device.address.substringAfter(':',"27845")
                                    pc=true;pcTransport="wifi";pin=""
                                    localError="";connectRequested=false;dialog="connect"
                                },
                                modifier=Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Computer,null,Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(device.name,maxLines=1)
                                    Text(device.address + if(device.isReachable)" · PC host online" else " · Saved PC (offline)",fontSize=11.sp,color=Muted)
                                }
                                Icon(Icons.Default.ChevronRight,null,Modifier.size(18.dp))
                            }
                        }
                    }
                    if (!state.isScanning && !state.isScanningPc) {
                        val noAndroid=scanFilter=="PC" || state.androidDevices.isEmpty()
                        val noPc=scanFilter=="Android" || state.pcDevices.isEmpty()
                        if(noAndroid&&noPc) Text(
                            "No compatible devices found. TVs need an authorized ADB endpoint; Windows PCs need DuoPad Pro Host running.",
                            fontSize=12.sp,color=Muted
                        )
                    }
                    OutlinedButton(onClick={vm.scanNearby()},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.Refresh,null,Modifier.size(18.dp))
                        Text("  Scan again")
                    }
                }
                "keyboard"->{OutlinedTextField(command,{command=it},label={Text("Text to send")});Text("Focus a text field on your device first.",fontSize=12.sp,color=Muted)}
                "shell"->{OutlinedTextField(command,{command=it},label={Text("ADB command")});Button(onClick={scope.launch{output="Running…";output=vm.executeAdbAwait(command)}}){Text("Run")};if(output.isNotBlank())Text(output,fontSize=12.sp)}
                "settings"->{Text("DuoPad Pro ${BuildConfig.VERSION_NAME}");Text("Android TV / Box + Windows remote and transfer controller.",fontSize=12.sp);if(connected)TextButton(onClick={automaticReconnect=false;vm.disconnect();dialog=null}){Text("Disconnect ${status.deviceName}")};TextButton(onClick={targets=emptyList();TrustedPcPairing.forgetAll(context);saveTargets(context,targets);dialog=null}){Text("Clear saved device list")};Text("Live mirror uses scrcpy 3.3.1 under Apache 2.0. Physical device testing is still required.",fontSize=12.sp,color=Muted)}
                "power"->{listOf("Sleep" to "input keyevent 223","Wake" to "input keyevent 224","Restart" to "reboot","Power off" to "reboot -p").forEach{(label,cmd)->OutlinedButton(onClick={command=cmd;dialog="Confirm power action"},modifier=Modifier.fillMaxWidth()){Text(label)}}}
                "Confirm power action"->Text("Run '$command' on ${status.deviceName}? Restart/shutdown disconnects the device.")
                "gamepad"->{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("A" to 96,"B" to 97,"X" to 99,"Y" to 100).forEach{(label,code)->OutlinedButton(onClick={key(code)},contentPadding=PaddingValues(12.dp)){Text(label)}}};Pad(::key);Text("Key-based game controls; compatibility depends on the game.",fontSize=11.sp,color=Muted)}
                "mapping"->Text("Custom mapping storage is not included in this native beta. Standard Android remote keycodes are used.")
                "cast"->Text("This beta supports TV → phone. Phone → TV casting requires a receiver integration and is not enabled yet.")
                "record"->Text("Screen recording is not yet available in this build. Live H.264 mirror and screenshot capture are separate tools.")
                "clearCache"->Text("Ask Android to release reclaimable application caches on the connected TV. App data will not be erased.")
                else->Text(output.ifBlank{"No additional details."},fontSize=13.sp)
            }
        }
    },confirmButton={TextButton(enabled=!(connectRequested && status.state==ConnectionState.CONNECTING),onClick={
        when(dialog){
            "clearSaved"->{targets=emptyList();TrustedPcPairing.forgetAll(context);saveTargets(context,targets);dialog=null}
            "clearLogs"->{MirrorCrashRecorder.clear(context);com.unipoint.core.util.PcDiagnosticRecorder.clear(context);dialog=null;message("Diagnostic logs cleared") }
            "saveDevice"->{
                pendingSaveTarget?.let { target ->
                    targets=listOf(target)+targets.filterNot { it.endpoint==target.endpoint }
                    saveTargets(context,targets)
                    automaticReconnect=true
                }
                pendingSaveTarget=null
                dialog=null
            }
            "rename"->{
                val target=renameTarget
                if(target!=null){
                    val alias=renameText.trim().takeIf { it.isNotEmpty() && it!=target.name }
                    targets=targets.map { if(it.endpoint==target.endpoint)it.copy(customName=alias) else it }
                    saveTargets(context,targets)
                }
                renameTarget=null;dialog=null
            }
            "connectingSaved"->{
                connectRequested=true
                if(pc)vm.connectNetwork(ip,port.toIntOrNull()?:27845,pin.ifBlank{null})
                else vm.connectAndroidIp("$ip:$port")
            }
            "connect"->{
                if(pc && pcTransport=="bluetooth") startBluetoothDirect()
                else {
                    val p=port.toIntOrNull()
                    if(ip.isBlank()||ip.contains(Regex("""[\s/:]"""))||p==null||p !in 1..65535) localError="Enter a valid host and port."
                    else {
                        localError="";connectRequested=true
                        if(pc) vm.connectNetwork(ip,p,pin.ifBlank{null},onSuccess={
                            connectRequested=false
                            dialog=null
                            // Keep one dashboard: the four PC Workspace cards.
                            pcWorkspaceTab="Screen"
                            page=3
                            detail=null
                        })
                        else vm.connectAndroidIp("$ip:$p")
                    }
                }
            }
            "keyboard"->{if(state.mode==AppMode.ANDROID)vm.executeAdb(AdbCommand.InputText(command)) else vm.sendText(command);dialog=null}
            "Confirm power action"->{showCommand("Power result",command)}
            "clearCache"->{showCommand("Clear cache","pm trim-caches 1G")}
            else->dialog=null
        }
    }){Text(when(dialog){"rename"->"Save";"saveDevice"->"Save device";"clearLogs"->"Clear";"clearSaved"->"Clear";"clearCache"->"Confirm";"connectingSaved"->if(status.state==ConnectionState.CONNECTING)"Connecting…" else "Retry";"connect"->if(pc&&pcTransport=="bluetooth")"Start Bluetooth" else if(connectRequested&&status.state==ConnectionState.ERROR)"Retry" else "Connect";"keyboard"->"Send";"Confirm power action"->"Confirm";else->"Done"},color=Teal)}},dismissButton={TextButton(onClick={dialog=null}){Text("Close",color=Muted)}})
}

@Composable private fun ConceptAction(
    label:String,icon:ImageVector,modifier:Modifier=Modifier,action:()->Unit
){
    Surface(
        onClick=action,modifier=modifier.height(83.dp),
        shape=RoundedCornerShape(14.dp),
        color=Pale,border=BorderStroke(0.8.dp,Border)
    ) {
        Column(
            modifier=Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.Center
        ) {
            Icon(icon,contentDescription=null,modifier=Modifier.size(25.dp),tint=Ink)
            Spacer(Modifier.height(8.dp))
            Text(label,fontSize=12.sp,fontWeight=FontWeight.Bold,
                color=Ink,maxLines=1)
        }
    }
}
@Composable private fun Title(title:String,subtitle:String){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(title,fontSize=26.sp,fontWeight=FontWeight.Bold,color=Ink,letterSpacing=(-0.6).sp);Text(subtitle,fontSize=13.sp,color=Muted)}}
@Composable private fun Hint(text:String){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.Info,null,Modifier.size(16.dp),tint=Muted);Text(text,fontSize=11.sp,color=Muted)}}
@Composable private fun Quick(label:String,icon:ImageVector,modifier:Modifier=Modifier,action:()->Unit){Surface(onClick=action,modifier=modifier,shape=RoundedCornerShape(13.dp),color=Paper,border=BorderStroke(1.dp,Border)){Column(Modifier.padding(horizontal=4.dp,vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(icon,null,Modifier.size(26.dp),tint=Teal);Text(label,fontSize=11.sp,fontWeight=FontWeight.Medium,maxLines=1)}}}
@Composable private fun RoundKey(label:String,icon:ImageVector,action:()->Unit){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){Surface(onClick=action,shape=CircleShape,color=Paper,border=BorderStroke(1.dp,Border)){Box(Modifier.size(55.dp),contentAlignment=Alignment.Center){Icon(icon,label,tint=if(label=="Power")DuoColors.Danger else Ink)}};Text(label,fontSize=11.sp,color=Muted)}}
@Composable private fun Pad(key:(Int)->Unit,diameter:androidx.compose.ui.unit.Dp=264.dp){BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){val size=minOf(maxWidth,diameter);Box(Modifier.size(size).clip(CircleShape).background(Pale).border(1.dp,Border,CircleShape)){IconButton(onClick={key(19)},modifier=Modifier.align(Alignment.TopCenter).padding(top=12.dp)){Icon(Icons.Default.KeyboardArrowUp,"Up")};IconButton(onClick={key(20)},modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp)){Icon(Icons.Default.KeyboardArrowDown,"Down")};IconButton(onClick={key(21)},modifier=Modifier.align(Alignment.CenterStart).padding(start=12.dp)){Icon(Icons.Default.KeyboardArrowLeft,"Left")};IconButton(onClick={key(22)},modifier=Modifier.align(Alignment.CenterEnd).padding(end=12.dp)){Icon(Icons.Default.KeyboardArrowRight,"Right")};Surface(onClick={key(23)},modifier=Modifier.align(Alignment.Center).size(size*0.34f),shape=CircleShape,color=Teal){Box(contentAlignment=Alignment.Center){Text("OK",fontSize=20.sp,color=OnAccent,fontWeight=FontWeight.SemiBold)}}}}}
@Composable private fun ToolTile(label:String,subtitle:String,icon:ImageVector,modifier:Modifier,action:()->Unit){
    Surface(onClick=action,modifier=modifier.height(94.dp),shape=RoundedCornerShape(14.dp),
        border=BorderStroke(1.dp,Border),color=Paper){
        Column(Modifier.fillMaxSize().padding(horizontal=11.dp,vertical=10.dp),
            verticalArrangement=Arrangement.SpaceBetween){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Icon(icon,null,tint=Teal,modifier=Modifier.size(23.dp))
                Icon(Icons.Default.ChevronRight,null,tint=Muted,modifier=Modifier.size(16.dp))
            }
            Text(label,fontWeight=FontWeight.SemiBold,fontSize=12.sp,lineHeight=14.sp,
                maxLines=2,overflow=TextOverflow.Ellipsis,color=Ink)
        }
    }
}
@Composable private fun ToolRow(label:String,subtitle:String,icon:ImageVector,action:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){Icon(icon,null,Modifier.size(25.dp));Column(Modifier.weight(1f)){Text(label,fontSize=13.sp,fontWeight=FontWeight.SemiBold);Text(subtitle,fontSize=11.sp,color=Muted)};Icon(Icons.Default.ChevronRight,null,Modifier.size(16.dp),tint=Muted)}}


@Composable private fun CompactRemoteKey(label:String,icon:ImageVector,action:()->Unit){
    Column(horizontalAlignment=Alignment.CenterHorizontally){
        Surface(onClick=action,modifier=Modifier.size(42.dp),color=Paper,shape=CircleShape,
            border=BorderStroke(1.dp,Border)){
            Box(contentAlignment=Alignment.Center){
                Icon(icon,label,Modifier.size(19.dp),
                    tint=if(label=="Power")DuoColors.Danger else Ink)
            }
        }
        Text(label,fontSize=9.sp,color=Muted,maxLines=1)
    }
}
@Composable private fun VolumeKnob(onUp:()->Unit,onDown:()->Unit,onMute:()->Unit){
    Column(horizontalAlignment=Alignment.CenterHorizontally){
        Box(Modifier.size(106.dp),contentAlignment=Alignment.Center){
            Surface(onClick=onMute,modifier=Modifier.size(65.dp),shape=CircleShape,
                color=Paper,border=BorderStroke(2.dp,Teal)){
                Column(horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.Center){
                    Icon(Icons.AutoMirrored.Filled.VolumeUp,"Mute",Modifier.size(22.dp),tint=Teal)
                    Text("VOL",fontSize=10.sp,fontWeight=FontWeight.Bold,color=Ink)
                }
            }
            (0..10).forEachIndexed { index, level ->
                // Scale markings, not an invented readout of the TV's current volume.
                val angle=(-150.0 + index*30.0)*PI/180.0
                Text(level.toString(),fontSize=9.sp,color=Muted,
                    modifier=Modifier.align(Alignment.Center)
                        .offset(x=(45*cos(angle)).dp,y=(45*sin(angle)).dp))
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onDown,modifier=Modifier.size(34.dp)){
                Icon(Icons.Default.Remove,"Volume down")
            }
            Text("Volume",fontSize=10.sp,color=Muted)
            IconButton(onClick=onUp,modifier=Modifier.size(34.dp)){
                Icon(Icons.Default.Add,"Volume up")
            }
        }
    }
}
