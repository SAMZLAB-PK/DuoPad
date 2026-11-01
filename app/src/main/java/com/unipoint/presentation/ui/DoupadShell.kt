package com.unipoint.presentation.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import android.content.Intent
import com.unipoint.core.util.MirrorCrashRecorder
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.unipoint.BuildConfig
import com.unipoint.domain.model.*
import com.unipoint.presentation.viewmodel.MainViewModel
import com.unipoint.presentation.ui.screens.android.*
import com.unipoint.presentation.ui.screens.pc.KeyboardScreen
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Source of truth: user-approved Concept 04 reference (warm cream + pure ink).
private val Teal = Color(0xFF171717)
private val Ink = Color(0xFF121212)
private val Muted = Color(0xFF595653)
private val Border = Color(0xFFE3DEDA)
private val Pale = Color(0xFFF0ECE9)
private val Background = Color(0xFFF7F4F1)
private val Paper = Color(0xFFFCF9F6)
private val Online = Color(0xFF0CCB37)
private data class Target(val name:String,val endpoint:String,val pc:Boolean,val customName:String?=null)
private fun readTargets(context:Context):List<Target> = runCatching {
    val a=JSONArray(context.getSharedPreferences("doupad",0).getString("targets","[]"))
    (0 until a.length()).map { val o=a.getJSONObject(it);Target(o.getString("name"),o.getString("endpoint"),o.optBoolean("pc"),o.optString("customName").takeIf { it.isNotBlank() }) }
}.getOrDefault(emptyList())
private fun saveTargets(context:Context,items:List<Target>) {
    val a=JSONArray();items.forEach { a.put(JSONObject().put("name",it.name).put("endpoint",it.endpoint).put("pc",it.pc).put("customName",it.customName ?: "")) }
    context.getSharedPreferences("doupad",0).edit().putString("targets",a.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoupadShell(vm:MainViewModel=hiltViewModel()) {
    val context=LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    val snack=remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf(0) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    var pin by remember { mutableStateOf("") }
    var pc by remember { mutableStateOf(false) }
    var pcTransport by rememberSaveable { mutableStateOf("bluetooth") }
    var localError by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var scanFilter by rememberSaveable { mutableStateOf("Saved") }
    var remoteMode by rememberSaveable { mutableIntStateOf(0) }
    var connectRequested by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Target?>(null) }
    var renameText by remember { mutableStateOf("") }
    var menuTarget by remember { mutableStateOf<String?>(null) }
    var airMouseSelected by rememberSaveable { mutableStateOf(false) }
    var pendingQuickAction by rememberSaveable { mutableStateOf("Remote") }
    var targets by remember { mutableStateOf(readTargets(context)) }
    val status=state.connectionStatus
    val connected=status.state==ConnectionState.CONNECTED
    fun message(text:String){scope.launch { snack.showSnackbar(text) }}
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
                if(isTv) page=2 else {page=1;message("PC mirroring needs a compatible host.")}
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
                else {page=3;message("PC file transfer requires a supported host.")}
            }
            "Install APK" -> {
                if(isTv) {page=3;detail="apps"}
                else {page=3;message("APK installation is for Android devices only.")}
            }
            else -> page=1
        }
        pendingQuickAction="Remote"
    }
    fun openNearby(action:String) {
        pendingQuickAction=action
        scanFilter="Saved"
        page=4
        vm.scanNearby()
    }
    fun open(target:Target) {
        if(page != 4) pendingQuickAction="Remote"
        if(connected && status.address==target.endpoint){applySelectedAction();return}
        ip=target.endpoint.substringBefore(':')
        port=target.endpoint.substringAfter(':',if(target.pc)"27845" else "5555")
        pc=target.pc;pcTransport="wifi";localError=""
        connectRequested=true;dialog="connectingSaved"
        if(target.pc)vm.connectNetwork(ip,port.toIntOrNull()?:27845)
        else vm.connectAndroidIp("$ip:$port")
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
    LaunchedEffect(status.state,status.address) { if(connected) {
        val endpoint=status.address ?: ""
        if(endpoint.isNotBlank() && ":" in endpoint) {
            val previous=targets.firstOrNull { it.endpoint==endpoint }
            val actualName=status.deviceName?.takeIf { it.isNotBlank() } ?: previous?.name ?: endpoint
            val next=Target(actualName,endpoint,state.mode==AppMode.PC,previous?.customName)
            targets=listOf(next)+targets.filterNot { it.endpoint==endpoint }
            saveTargets(context,targets)
        }
    } }
    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snack.showSnackbar(it);vm.clearSnackbar() } }
    LaunchedEffect(dialog,pc,pcTransport) {
        if(dialog=="connect" && pc && pcTransport=="wifi") vm.scanPc()
    }
    BackHandler(detail!=null||page!=0||dialog!=null){if(dialog!=null)dialog=null else if(detail!=null)detail=null else page=0}
    val back={detail=null}
    when(detail) {
        "apps"->{AppManagerScreen(onBack=back);return}
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
        "pcKeyboard"->{KeyboardScreen(onBack=back);return}
    }
    Scaffold(containerColor=Background,snackbarHost={SnackbarHost(snack)},bottomBar={
        Surface(color=Background, shadowElevation=0.dp) {
            Row(
                modifier=Modifier.fillMaxWidth().navigationBarsPadding()
                    .height(70.dp).padding(horizontal=8.dp,vertical=5.dp),
                horizontalArrangement=Arrangement.SpaceEvenly,
                verticalAlignment=Alignment.CenterVertically
            ) {
                val tabs=listOf(
                    Triple("Home",Icons.Default.Home,0),
                    Triple("Devices",Icons.Default.Computer,4),
                    Triple("Tools",Icons.Default.Handyman,3),
                    Triple("Settings",Icons.Default.Settings,5)
                )
                tabs.forEach { (label,icon,destination) ->
                    val selected=page==destination
                    Column(
                        modifier=Modifier.weight(1f)
                            .clickable {
                                if(destination==4)openNearby("Remote")
                                else {page=destination;detail=null}
                            },
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            color=if(selected)Ink else Color.Transparent,
                            shape=CircleShape,
                            modifier=Modifier.width(59.dp).height(31.dp)
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                Icon(
                                    imageVector=icon,contentDescription=label,
                                    modifier=Modifier.size(22.dp),
                                    tint=if(selected)Color.White else Ink
                                )
                            }
                        }
                        Text(label,fontSize=11.sp,
                            fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium,
                            color=Ink,maxLines=1)
                    }
                }
            }
        }
    }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=15.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            when(page) {
                0->{
                    // Pixel-proportion reference: monochrome header, status, two
                    // real device rows and exactly four actions. No demo data.
                    Row(
                        Modifier.fillMaxWidth().padding(top=8.dp,bottom=3.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter=painterResource(id=R.drawable.ic_launcher),
                            contentDescription="DouPad app logo",
                            modifier=Modifier.size(45.dp)
                        )
                        Column(Modifier.weight(1f),
                            verticalArrangement=Arrangement.spacedBy(1.dp)) {
                            Text("DOUPAD",fontSize=22.sp,
                                fontWeight=FontWeight.Black,color=Ink,
                                letterSpacing=(-0.65).sp,lineHeight=23.sp)
                            Text("Control your PC & TV from your phone",
                                fontSize=10.sp,color=Ink,
                                fontWeight=FontWeight.Medium,maxLines=1)
                        }
                        IconButton(onClick={page=5},modifier=Modifier.size(36.dp)){
                            Icon(Icons.Default.MoreVert,"Settings",
                                tint=Ink,modifier=Modifier.size(22.dp))
                        }
                    }

                    Surface(
                        modifier=Modifier.fillMaxWidth().height(48.dp),
                        color=Pale,shape=RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal=13.dp),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(9.dp)
                        ) {
                            Surface(color=Color(0xFFE7E3E0),shape=CircleShape,
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
                                    colors=ButtonDefaults.buttonColors(containerColor=Ink),
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
                                    colors=ButtonDefaults.buttonColors(containerColor=Ink)
                                ){
                                    Text(if(live)"Open" else "Connect",
                                        fontSize=10.sp,fontWeight=FontWeight.Bold,
                                        color=Color.White,maxLines=1)
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
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={page=0;pendingQuickAction="Remote"}) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back") }
                        Column(Modifier.weight(1f)) {
                            Text("Devices",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Ink)
                            Text("Saved and nearby screens · $pendingQuickAction",fontSize=12.sp,color=Muted)
                        }
                        IconButton(onClick={vm.scanNearby()}) { Icon(Icons.Default.Refresh,"Scan again",tint=Teal) }
                    }
                    Surface(color=Pale,shape=RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Lock,null,Modifier.size(18.dp),tint=Teal)
                            Text("Saved devices were previously connected/authorized. Network scanning itself never sends an ADB authorization request.",fontSize=12.sp,color=Ink)
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        listOf("Saved","All","TV","PC").forEach { type ->
                            FilterChip(selected=scanFilter==type,onClick={scanFilter=type},label={Text(type,fontSize=11.sp)})
                        }
                    }
                    if(state.isScanning || state.isScanningPc){
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Searching without ADB authentication…",fontSize=12.sp,color=Muted)
                    }
                    val androidFound=state.androidDevices.distinctBy { it.id }
                    val pcFound=state.pcDevices.distinctBy { it.id }
                    val savedTargets=targets.distinctBy { it.endpoint }
                    fun targetButton(target:Target,subtitle:String,tint:Color=Teal): @Composable ()->Unit = {
                        OutlinedButton(onClick={open(target)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                            Icon(if(target.pc)Icons.Default.Computer else Icons.Default.Tv,null,Modifier.size(27.dp),tint=tint)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(target.customName ?: target.name,maxLines=1,fontWeight=FontWeight.SemiBold)
                                Text(subtitle,fontSize=11.sp,color=Muted,maxLines=1)
                            }
                            Icon(Icons.Default.ChevronRight,null)
                        }
                    }
                    if(scanFilter=="Saved"){
                        if(savedTargets.isEmpty() && !state.isScanning) Text("No saved authorized devices yet.",fontSize=12.sp,color=Muted)
                        savedTargets.forEach { target -> targetButton(target,target.endpoint+" · Saved / previously authorized")() }
                    } else {
                        if(scanFilter!="PC"){
                            androidFound.forEach { device ->
                                val saved=savedTargets.firstOrNull { it.endpoint==device.id }
                                val name=saved?.customName ?: saved?.name ?: "Android TV / Box"
                                targetButton(Target(name,device.id,false,saved?.customName),
                                    device.id + if(saved!=null)" · Saved" else " · Tap to authorize & connect",
                                    if(saved!=null)Teal else Muted)()
                            }
                            if(scanFilter=="TV" || scanFilter=="All"){
                                savedTargets.filter { !it.pc && androidFound.none { d->d.id==it.endpoint } }
                                    .forEach { target -> targetButton(target,target.endpoint+" · Saved / offline")() }
                            }
                        }
                        if(scanFilter!="TV"){
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
                    OutlinedButton(onClick={vm.scanNearby()},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.Refresh,null);Text("  Scan again")
                    }
                    OutlinedButton(onClick={pc=false;port="5555";dialog="connect"},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.Add,null);Text("  Connect by IP")
                    }
                }
                1->{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        IconButton(onClick={page=0}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Home")}
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                            Text(if(connected)status.deviceName?:"Remote control" else "Remote control",fontSize=18.sp,fontWeight=FontWeight.Bold)
                            Text(if(connected)"● Connected" else "Choose a device to start",fontSize=11.sp,color=if(connected)Teal else Muted)
                        }
                        IconButton(onClick={
                            if(requireDevice()) runCatching{
                                voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                            }.onFailure{message("No speech recognition app installed")}
                        }){Icon(Icons.Default.Mic,"Voice input",tint=Teal)}
                        IconButton(onClick={dialog="settings"}){Icon(Icons.Default.MoreVert,"Device options")}
                    }
                    TabRow(selectedTabIndex=remoteMode,containerColor=Background,contentColor=Teal){
                        listOf("D-pad","Touchpad","Air mouse","Keyboard").forEachIndexed { i,t ->
                            Tab(selected=remoteMode==i,onClick={
                                remoteMode=i
                                if(i==1 && requireTv()){airMouseSelected=false;detail="mouse"}
                                if(i==2 && requireTv()){airMouseSelected=true;detail="mouse"}
                                if(i==3 && requireTv())detail="androidKeyboard"
                            },text={Text(t,fontSize=11.sp,maxLines=1)})
                        }
                    }
                    if(remoteMode==0) Box(
                        Modifier.fillMaxWidth().pointerInput(Unit){
                            var total=0f
                            detectHorizontalDragGestures(
                                onDragStart={total=0f},
                                onHorizontalDrag={_,amount->total+=amount},
                                onDragEnd={
                                    if(total < -90f && requireTv()){remoteMode=1;airMouseSelected=false;detail="mouse"}
                                    else if(total > 90f && requireTv()){remoteMode=3;detail="androidKeyboard"}
                                }
                            )
                        }
                    ){Pad(::key)} else if(remoteMode==1) {
                        var dx by remember {mutableFloatStateOf(0f)};var dy by remember{mutableFloatStateOf(0f)}
                        Box(Modifier.fillMaxWidth().height(265.dp).clip(RoundedCornerShape(22.dp)).background(Pale).border(1.dp,Border,RoundedCornerShape(22.dp)).pointerInput(connected,state.mode){detectDragGestures(onDragStart={dx=0f;dy=0f},onDragEnd={if(state.mode==AppMode.ANDROID)key(if(kotlin.math.abs(dx)>kotlin.math.abs(dy)){if(dx>0)22 else 21}else if(dy>0)20 else 19)}){change,drag->change.consume();dx+=drag.x;dy+=drag.y;if(connected&&state.mode==AppMode.PC)vm.sendMouseMove(drag.x*2,drag.y*2)}}.pointerInput(connected){detectTapGestures{if(connected&&state.mode==AppMode.PC)vm.sendMouseClick(MouseButton.LEFT) else key(23)}},contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Default.TouchApp,null,Modifier.size(38.dp),tint=Teal);Text("Touch, swipe, control",fontWeight=FontWeight.SemiBold);Text(if(state.mode==AppMode.PC)"Move pointer • Tap to click" else "Swipe to navigate • Tap to select",fontSize=12.sp,color=Muted)}}
                    } else Surface(color=Pale,shape=RoundedCornerShape(22.dp)){Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Default.Air,null,Modifier.size(36.dp),tint=Teal);Text("Air mouse",fontWeight=FontWeight.Bold);Text("Use the precision mouse controls on your Android TV.",fontSize=12.sp,color=Muted);OutlinedButton(onClick={if(requireTv())detail="mouse"}){Text("Open mouse controls")};Text("Gyroscope steering is not enabled in this native beta.",fontSize=11.sp,color=Muted)}}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){RoundKey("Back",Icons.AutoMirrored.Filled.ArrowBack){key(4)};RoundKey("Home",Icons.Default.Home){key(3)};RoundKey("Menu",Icons.Default.Menu){key(82)}}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){Column(horizontalAlignment=Alignment.CenterHorizontally){Surface(color=Pale,shape=CircleShape,border=BorderStroke(1.dp,Border)){Column(horizontalAlignment=Alignment.CenterHorizontally){IconButton(onClick={key(24)}){Icon(Icons.Default.Add,"Volume up")};Icon(Icons.AutoMirrored.Filled.VolumeUp,null);IconButton(onClick={key(25)}){Icon(Icons.Default.Remove,"Volume down")}}};Text("Volume",fontSize=11.sp,color=Muted)};Column(verticalArrangement=Arrangement.spacedBy(15.dp)){RoundKey("Mute",Icons.AutoMirrored.Filled.VolumeOff){key(164)};RoundKey("Power",Icons.Default.PowerSettingsNew){if(requireTv())dialog="power"}};Column(horizontalAlignment=Alignment.CenterHorizontally){Surface(color=Pale,shape=CircleShape,border=BorderStroke(1.dp,Border)){Column(horizontalAlignment=Alignment.CenterHorizontally){IconButton(onClick={key(166)}){Icon(Icons.Default.KeyboardArrowUp,"Channel up")};Text("CH",fontSize=12.sp);IconButton(onClick={key(167)}){Icon(Icons.Default.KeyboardArrowDown,"Channel down")}}};Text("Channel",fontSize=11.sp,color=Muted)}}
                    if(remoteMode==0 && state.mode==AppMode.ANDROID){
                        Text("Color keys",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Ink)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            listOf(
                                Triple("Red",183,Color(0xFFE84F59)),
                                Triple("Green",184,Color(0xFF35A46B)),
                                Triple("Yellow",185,Color(0xFFE8BE38)),
                                Triple("Blue",186,Color(0xFF4483DE))
                            ).forEach { (label,code,tint) ->
                                Surface(onClick={key(code)},modifier=Modifier.weight(1f).height(37.dp),
                                    color=tint,shape=RoundedCornerShape(9.dp)){
                                    Box(contentAlignment=Alignment.Center){
                                        Text(label,color=if(label=="Yellow")Ink else Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Text("Media",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Ink)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                            listOf("⏮" to 88,"◀◀" to 89,"▶ / ❚❚" to 85,"▶▶" to 90,"⏭" to 87).forEach { (label,code) ->
                                OutlinedButton(onClick={key(code)},modifier=Modifier.weight(1f).height(36.dp),
                                    contentPadding=PaddingValues(1.dp),shape=RoundedCornerShape(9.dp)){
                                    Text(label,fontSize=11.sp,maxLines=1)
                                }
                            }
                        }
                        Text("Apps & shortcuts",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Ink)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            listOf(
                                "YouTube" to "com.google.android.youtube.tv",
                                "Netflix" to "com.netflix.ninja",
                                "Prime" to "com.amazon.amazonvideo.livingroom",
                                "Apps" to ""
                            ).forEach { (label,pkg) ->
                                OutlinedButton(onClick={
                                    if(requireTv()){
                                        if(pkg.isBlank())detail="apps"
                                        else scope.launch {
                                            val command = "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
                                            val result = vm.executeAdbAwait(command)
                                            if(result.contains("No activities") || result.contains("monkey aborted",true)){
                                                message("$label is not installed or cannot be opened on this TV.")
                                            }
                                        }
                                    }
                                },modifier=Modifier.weight(1f).height(36.dp),
                                    contentPadding=PaddingValues(1.dp),shape=RoundedCornerShape(9.dp)){
                                    Text(label,fontSize=10.sp,maxLines=1)
                                }
                            }
                        }
                    }
                    Hint("Swipe between remote modes. Color keys and app shortcuts depend on TV support.")
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
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Quick("Screenshot",Icons.Default.PhotoCamera,Modifier.weight(1f)){if(requireTv()){detail="preview"}};Quick("Record",Icons.Default.FiberManualRecord,Modifier.weight(1f)){dialog="record"}}
                    Text("Video streaming",fontWeight=FontWeight.SemiBold);Text("Hardware-decoded H.264 • up to 30 fps • adaptive setup",fontSize=12.sp,color=Muted)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("Include TV audio",fontWeight=FontWeight.SemiBold);Text("Video-only in this beta",fontSize=12.sp,color=Muted)};Switch(checked=false,onCheckedChange={message("Audio forwarding is not enabled in this beta.")})}
                    OutlinedButton(onClick={detail="mirrorLog"},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                        Icon(Icons.Default.BugReport,null);Spacer(Modifier.width(7.dp));Text("Mirror diagnostics & error log")
                    }
                    Hint("Availability depends on TV firmware. DRM-protected video may appear blank.")
                }
                3->{
                    Title("Tools","Get more from your devices.")
                    var search by rememberSaveable {mutableStateOf("")}
                    OutlinedTextField(value=search,onValueChange={search=it},placeholder={Text("Search tools…",fontSize=14.sp)},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(14.dp))
                    Text("Main tools",fontSize=17.sp,fontWeight=FontWeight.Bold)
                    val tools=listOf(Triple("Apps","Manage installed apps",Icons.Default.GridView),Triple("Files","Browse device files",Icons.Default.FolderOpen),Triple("Install APK","Install APK files",Icons.Default.InstallMobile),Triple("Transfers","Send and receive files",Icons.Default.SwapHoriz),Triple("Shell","Run commands",Icons.Default.Terminal),Triple("Device info","View device details",Icons.Default.Info))
                    tools.filter { it.first.contains(search,true) || it.second.contains(search,true) }.chunked(2).forEach { row ->
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            row.forEach { (label,sub,ic) ->
                                ToolTile(label,sub,ic,Modifier.weight(1f)) {
                                    if(requireTv()) {
                                        when(label) {
                                            "Apps","Install APK" -> detail="apps"
                                            "Files","Transfers" -> detail="files"
                                            "Shell" -> detail="shellFull"
                                            "Device info" -> detail="infoFull"
                                            else -> detail="infoFull"
                                        }
                                    }
                                }
                            }
                            if(row.size==1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text("More tools",fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Surface(shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Border),color=Color.White){Column{ToolRow("Gamepad","Use your phone as a full-screen gamepad",Icons.Default.SportsEsports){if(requireTv())detail="gamepadFull"};HorizontalDivider(color=Border);ToolRow("Button mapping","Customize and save remote keycodes",Icons.Default.Tune){if(requireTv())detail="mappingFull"};HorizontalDivider(color=Border);ToolRow("Power options","Sleep, restart or power off",Icons.Default.PowerSettingsNew){if(requireTv())dialog="power"}}}
                }
                5->{
                    Title("Settings","Control your connected devices and preferences.")
                    Surface(shape=RoundedCornerShape(15.dp),
                        border=BorderStroke(1.dp,Border),color=Color.White){
                        Column(Modifier.padding(horizontal=15.dp,vertical=14.dp),
                            verticalArrangement=Arrangement.spacedBy(7.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(11.dp)){
                                Surface(Modifier.size(38.dp),color=Ink,
                                    shape=RoundedCornerShape(10.dp)){
                                    Box(contentAlignment=Alignment.Center){
                                        Text("D",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Black)
                                    }
                                }
                                Column{
                                    Text("DouPad Pro",fontWeight=FontWeight.Bold,color=Ink)
                                    Text("Version ${BuildConfig.VERSION_NAME}",
                                        fontSize=11.sp,color=Muted)
                                }
                            }
                            Text("Light • Warm White • Minimal",
                                fontSize=11.sp,color=Muted)
                        }
                    }
                    Surface(shape=RoundedCornerShape(15.dp),
                        border=BorderStroke(1.dp,Border),color=Color.White){
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
                                    vm.disconnect();message("Disconnected")
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
    if(dialog!=null) AlertDialog(onDismissRequest={dialog=null},containerColor=Color.White,title={Text(when(dialog){"connect"->"Add a device";"connectingSaved"->"Connecting to device";"rename"->"Rename device";"keyboard"->"Keyboard";"settings"->"DouPad Pro";"clearSaved"->"Clear saved devices?";"scan"->"Nearby devices";"shell"->"Shell";"power"->"Power options";"gamepad"->"Gamepad";else->dialog!!},fontWeight=FontWeight.Bold)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            when(dialog){
                "clearSaved"->{
                    Text("Remove all saved TV and PC entries from this phone? Existing device authorization on your TV is not changed.",color=Muted,fontSize=13.sp)
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
                            Surface(shape=RoundedCornerShape(14.dp),color=Color(0xFFEFFAFA),border=BorderStroke(1.dp,Teal.copy(alpha=.35f))) {
                                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                        Icon(Icons.Default.Bluetooth,null,tint=Teal)
                                        Column { Text("Bluetooth Direct",fontWeight=FontWeight.Bold);Text("No PC app required",fontSize=12.sp,color=Teal) }
                                    }
                                    Text("Pair this phone from Windows, macOS or Linux Bluetooth settings. DouPad Pro acts as a mouse + keyboard.",fontSize=12.sp,color=Muted)
                                    Button(onClick={startBluetoothDirect()},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Teal)){Text("Start Bluetooth Direct")}
                                }
                            }
                        } else {
                            if(state.isScanningPc) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Looking for DouPad Pro Host on your network…",fontSize=12.sp,color=Muted)
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
                                        Text("DouPad Pro Host required for Wi-Fi",fontWeight=FontWeight.Bold)
                                        Text("Bluetooth Direct works without any PC software. For Wi-Fi control, run the small DouPad Pro Host on Windows, macOS or Linux.",fontSize=12.sp,color=Muted)
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick={vm.scanPc()},modifier=Modifier.weight(1f)){Text("Scan again")}
                                            Button(onClick={
                                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.HOST_RELEASES_URL))) }
                                                    .onFailure { message("Could not open the DouPad Pro Host download page.") }
                                            },modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Teal)){Text("Get Host")}
                                        }
                                        TextButton(onClick={pcTransport="bluetooth"},modifier=Modifier.fillMaxWidth()){Text("Use Bluetooth Direct instead")}
                                    }
                                }
                            }
                            Text("Manual Wi-Fi connection",fontWeight=FontWeight.SemiBold)
                            OutlinedTextField(ip,{ip=it},label={Text("PC IP address")},singleLine=true)
                            OutlinedTextField(port,{port=it.filter(Char::isDigit)},label={Text("Port")},singleLine=true)
                            OutlinedTextField(pin,{pin=it},label={Text("Host pairing PIN (optional)")},singleLine=true)
                        }
                    }
                    if(localError.isNotBlank())Text(localError,color=MaterialTheme.colorScheme.error)
                    if(connectRequested && status.state==ConnectionState.CONNECTING) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Waiting for device authorization. Approve the prompt on your TV if shown…",fontSize=12.sp,color=Muted)
                    }
                    if(connectRequested && status.state==ConnectionState.ERROR) {
                        Text(status.errorMessage ?: "Connection failed. Check debugging and try again.",fontSize=12.sp,color=MaterialTheme.colorScheme.error)
                        Text("Connection stays open here so you can read the error and retry.",fontSize=11.sp,color=Muted)
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
                            "No compatible devices found. TVs need an authorized ADB endpoint; Windows PCs need DouPad Pro Host running.",
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
                "settings"->{Text("DouPad Pro ${BuildConfig.VERSION_NAME}");Text("Android TV / Box + Windows remote and transfer controller.",fontSize=12.sp);if(connected)TextButton(onClick={vm.disconnect();dialog=null}){Text("Disconnect ${status.deviceName}")};TextButton(onClick={targets=emptyList();saveTargets(context,targets);dialog=null}){Text("Clear saved device list")};Text("Live mirror uses scrcpy 3.3.1 under Apache 2.0. Physical device testing is still required.",fontSize=12.sp,color=Muted)}
                "power"->{listOf("Sleep" to "input keyevent 223","Wake" to "input keyevent 224","Restart" to "reboot","Power off" to "reboot -p").forEach{(label,cmd)->OutlinedButton(onClick={command=cmd;dialog="Confirm power action"},modifier=Modifier.fillMaxWidth()){Text(label)}}}
                "Confirm power action"->Text("Run '$command' on ${status.deviceName}? Restart/shutdown disconnects the device.")
                "gamepad"->{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("A" to 96,"B" to 97,"X" to 99,"Y" to 100).forEach{(label,code)->OutlinedButton(onClick={key(code)},contentPadding=PaddingValues(12.dp)){Text(label)}}};Pad(::key);Text("Key-based game controls; compatibility depends on the game.",fontSize=11.sp,color=Muted)}
                "mapping"->Text("Custom mapping storage is not included in this native beta. Standard Android remote keycodes are used.")
                "cast"->Text("This beta supports TV → phone. Phone → TV casting requires a receiver integration and is not enabled yet.")
                "record"->Text("Recording is not enabled in this native beta. Live H.264 video and screenshots can be tested now.")
                else->Text(output.ifBlank{"No additional details."},fontSize=13.sp)
            }
        }
    },confirmButton={TextButton(onClick={
        when(dialog){
            "clearSaved"->{targets=emptyList();saveTargets(context,targets);dialog=null}
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
                    if(ip.isBlank()||ip.contains(Regex("[\s/:]"))||p==null||p !in 1..65535) localError="Enter a valid host and port."
                    else { localError="";connectRequested=true;if(pc)vm.connectNetwork(ip,p,pin.ifBlank{null}) else vm.connectAndroidIp("$ip:$p") }
                }
            }
            "keyboard"->{if(state.mode==AppMode.ANDROID)vm.executeAdb(AdbCommand.InputText(command)) else vm.sendText(command);dialog=null}
            "Confirm power action"->{showCommand("Power result",command)}
            else->dialog=null
        }
    }){Text(when(dialog){"rename"->"Save";"clearSaved"->"Clear";"connectingSaved"->if(status.state==ConnectionState.CONNECTING)"Connecting…" else "Retry";"connect"->if(pc&&pcTransport=="bluetooth")"Start Bluetooth" else if(connectRequested&&status.state==ConnectionState.ERROR)"Retry" else "Connect";"keyboard"->"Send";"Confirm power action"->"Confirm";else->"Done"},color=Teal)}},dismissButton={TextButton(onClick={dialog=null}){Text("Close",color=Muted)}})
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
@Composable private fun Quick(label:String,icon:ImageVector,modifier:Modifier=Modifier,action:()->Unit){Surface(onClick=action,modifier=modifier,shape=RoundedCornerShape(13.dp),color=Color.White,border=BorderStroke(1.dp,Border)){Column(Modifier.padding(horizontal=4.dp,vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(icon,null,Modifier.size(26.dp),tint=Teal);Text(label,fontSize=11.sp,fontWeight=FontWeight.Medium,maxLines=1)}}}
@Composable private fun RoundKey(label:String,icon:ImageVector,action:()->Unit){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){Surface(onClick=action,shape=CircleShape,color=Color.White,border=BorderStroke(1.dp,Border)){Box(Modifier.size(55.dp),contentAlignment=Alignment.Center){Icon(icon,label,tint=if(label=="Power")Color(0xFFDA3444) else Ink)}};Text(label,fontSize=11.sp,color=Muted)}}
@Composable private fun Pad(key:(Int)->Unit){BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){val size=minOf(maxWidth,264.dp);Box(Modifier.size(size).clip(CircleShape).background(Pale).border(1.dp,Border,CircleShape)){IconButton(onClick={key(19)},modifier=Modifier.align(Alignment.TopCenter).padding(top=12.dp)){Icon(Icons.Default.KeyboardArrowUp,"Up")};IconButton(onClick={key(20)},modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp)){Icon(Icons.Default.KeyboardArrowDown,"Down")};IconButton(onClick={key(21)},modifier=Modifier.align(Alignment.CenterStart).padding(start=12.dp)){Icon(Icons.Default.KeyboardArrowLeft,"Left")};IconButton(onClick={key(22)},modifier=Modifier.align(Alignment.CenterEnd).padding(end=12.dp)){Icon(Icons.Default.KeyboardArrowRight,"Right")};Surface(onClick={key(23)},modifier=Modifier.align(Alignment.Center).size(88.dp),shape=CircleShape,color=Teal){Box(contentAlignment=Alignment.Center){Text("OK",fontSize=20.sp,color=Color.White,fontWeight=FontWeight.SemiBold)}}}}}
@Composable private fun ToolTile(label:String,subtitle:String,icon:ImageVector,modifier:Modifier,action:()->Unit){Surface(onClick=action,modifier=modifier,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Border),color=Color.White){Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Icon(icon,null,tint=Teal,modifier=Modifier.size(26.dp));Icon(Icons.Default.ChevronRight,null,tint=Muted,modifier=Modifier.size(16.dp))};Text(label,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(subtitle,fontSize=10.sp,color=Muted)}}}
@Composable private fun ToolRow(label:String,subtitle:String,icon:ImageVector,action:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){Icon(icon,null,Modifier.size(25.dp));Column(Modifier.weight(1f)){Text(label,fontSize=13.sp,fontWeight=FontWeight.SemiBold);Text(subtitle,fontSize=11.sp,color=Muted)};Icon(Icons.Default.ChevronRight,null,Modifier.size(16.dp),tint=Muted)}}
