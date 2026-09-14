package com.unipoint.presentation.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unipoint.domain.model.*
import com.unipoint.presentation.viewmodel.MainViewModel
import com.unipoint.presentation.ui.screens.android.*
import com.unipoint.presentation.ui.screens.pc.KeyboardScreen
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Teal = Color(0xFF008C91)
private val Ink = Color(0xFF102033)
private val Muted = Color(0xFF667587)
private val Border = Color(0xFFE0E6ED)
private val Pale = Color(0xFFF2F5F8)
private val Background = Color(0xFFFBFCFD)
private data class Target(val name:String,val endpoint:String,val pc:Boolean)
private fun readTargets(context:Context):List<Target> = runCatching {
    val a=JSONArray(context.getSharedPreferences("doupad",0).getString("targets","[]"))
    (0 until a.length()).map { val o=a.getJSONObject(it);Target(o.getString("name"),o.getString("endpoint"),o.optBoolean("pc")) }
}.getOrDefault(emptyList())
private fun saveTargets(context:Context,items:List<Target>) {
    val a=JSONArray();items.forEach { a.put(JSONObject().put("name",it.name).put("endpoint",it.endpoint).put("pc",it.pc)) }
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
    var localError by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var targets by remember { mutableStateOf(readTargets(context)) }
    val status=state.connectionStatus
    val connected=status.state==ConnectionState.CONNECTED
    fun message(text:String){scope.launch { snack.showSnackbar(text) }}
    fun requireDevice():Boolean { if(!connected){dialog="connect";message("Connect your TV or PC first.")};return connected }
    fun requireTv():Boolean { if(!requireDevice())return false; if(state.mode!=AppMode.ANDROID){message("This tool requires an Android TV connection.");return false};return true }
    fun key(code:Int) { if(!requireDevice())return; if(state.mode==AppMode.ANDROID)vm.executeAdb(AdbCommand.InputKey(code)) else { val hid=when(code){19->82;20->81;21->80;22->79;23,66->40;4->41;3->74;else->{message("This button is available for Android TV only.");return}};vm.sendKey(hid,true);vm.sendKey(hid,false) } }
    fun showCommand(title:String,cmd:String) { if(!requireDevice())return;dialog=title;output="Running…";scope.launch { output=vm.executeAdbAwait(cmd).ifBlank { "Completed with no output." } } }
    fun open(target:Target) { if(connected&&status.address==target.endpoint){page=1;return};ip=target.endpoint.substringBefore(':');port=target.endpoint.substringAfter(':',if(target.pc)"27845" else "5555");pc=target.pc;dialog="connect" }
    val voice=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { command=it;dialog="keyboard" }
    }
    LaunchedEffect(status.state,status.address) { if(connected) {
        val target=Target(status.deviceName?:status.address?:"Device",status.address?:"",state.mode==AppMode.PC)
        if(target.endpoint.isNotBlank()){targets=listOf(target)+targets.filterNot { it.endpoint==target.endpoint };saveTargets(context,targets)}
    } }
    LaunchedEffect(state.snackbarMessage) { state.snackbarMessage?.let { snack.showSnackbar(it);vm.clearSnackbar() } }
    BackHandler(detail!=null||page!=0||dialog!=null){if(dialog!=null)dialog=null else if(detail!=null)detail=null else page=0}
    val back={detail=null}
    when(detail) {
        "apps"->{AppManagerScreen(onBack=back);return}
        "files"->{FileManagerScreen(onBack=back);return}
        "preview"->{ScreenMirrorScreen(onBack=back);return}
        "mouse"->{AndroidMousePadScreen(onBack=back);return}
        "pcKeyboard"->{KeyboardScreen(onBack=back);return}
    }
    Scaffold(containerColor=Background,snackbarHost={SnackbarHost(snack)},bottomBar={
        NavigationBar(containerColor=Color.White,tonalElevation=0.dp,modifier=Modifier.border(BorderStroke(0.5.dp,Border))) {
            listOf("Home" to Icons.Default.Home,"Remote" to Icons.Default.SettingsRemote,"Mirror" to Icons.Default.ScreenShare,"Tools" to Icons.Default.GridView).forEachIndexed { index,(label,icon)->
                NavigationBarItem(selected=page==index,onClick={page=index},icon={Icon(icon,label,Modifier.size(23.dp))},label={Text(label,fontSize=11.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Teal,selectedTextColor=Teal,indicatorColor=Color(0xFFE9F6F5),unselectedIconColor=Muted,unselectedTextColor=Muted))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=23.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            when(page) {
                0->{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("DOUPAD",fontSize=26.sp,fontWeight=FontWeight.ExtraBold,color=Ink);IconButton(onClick={dialog="settings"}){Icon(Icons.Default.Settings,"Settings")}}
                    Title("Your devices","Control all your screens from one place.")
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf("All","TV","PC").forEach{label->FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)},shape=CircleShape,colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Teal,selectedLabelColor=Color.White))}}
                    val visible=targets.filter {filter=="All"||(filter=="PC")==it.pc}
                    if(visible.isEmpty()) Surface(shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border),color=Color.White){Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){Icon(Icons.Default.Devices,null,Modifier.size(48.dp),tint=Teal);Text("Your first connection starts here",fontWeight=FontWeight.SemiBold);Text("Add a TV or PC on your Wi-Fi.\nYour saved devices will appear here.",fontSize=13.sp,color=Muted);Button(onClick={dialog="connect"},colors=ButtonDefaults.buttonColors(containerColor=Teal)){Text("Add your device")}}}
                    visible.forEach { target->val live=connected&&status.address==target.endpoint;Surface(onClick={open(target)},shape=RoundedCornerShape(17.dp),color=if(live)Color(0xFFEFFAFA) else Color.White,border=BorderStroke(1.dp,if(live)Teal else Border)){Row(Modifier.fillMaxWidth().padding(17.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Surface(shape=RoundedCornerShape(10.dp),color=Pale){Icon(if(target.pc)Icons.Default.Computer else Icons.Default.Tv,null,Modifier.padding(13.dp).size(47.dp),tint=Teal)};Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(target.name,fontSize=15.sp,fontWeight=FontWeight.Bold);Text(target.endpoint,fontSize=11.sp,color=Muted);Text(if(live)"● Connected" else "● Saved",fontSize=11.sp,color=if(live)Teal else Muted)};Icon(Icons.Default.ChevronRight,null,tint=Muted)}}}
                    OutlinedButton(onClick={dialog="connect"},modifier=Modifier.fillMaxWidth().height(76.dp),shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Border)){Icon(Icons.Default.AddCircleOutline,null,tint=Teal);Spacer(Modifier.width(12.dp));Column{Text("Add device",color=Ink,fontWeight=FontWeight.SemiBold);Text("Connect a new TV or PC",fontSize=11.sp,color=Muted)}}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Quick actions",fontWeight=FontWeight.Bold,fontSize=17.sp);IconButton(onClick={vm.scanAndroid();dialog="scan"}){Icon(Icons.Default.Radar,"Find TV",tint=Teal)}}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Quick("Remote",Icons.Default.SettingsRemote,Modifier.weight(1f)){page=1};Quick("Mirror",Icons.Default.ScreenShare,Modifier.weight(1f)){page=2};Quick("Send files",Icons.AutoMirrored.Filled.Send,Modifier.weight(1f)){if(requireTv())detail="files"};Quick("Install APK",Icons.Default.InstallMobile,Modifier.weight(1f)){if(requireTv())detail="apps"}}
                    Hint("Keep your phone and device on the same Wi-Fi network.")
                }
                1->{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={page=0}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Home")};Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(if(connected)status.deviceName?:"Remote control" else "Remote control",fontSize=18.sp,fontWeight=FontWeight.Bold);Text(if(connected)"● Connected" else "Choose a device to start",fontSize=11.sp,color=if(connected)Teal else Muted)};IconButton(onClick={dialog="settings"}){Icon(Icons.Default.MoreVert,"Device options")}}
                    var mode by rememberSaveable { mutableIntStateOf(0) }
                    TabRow(selectedTabIndex=mode,containerColor=Background,contentColor=Teal){listOf("D-pad","Touchpad","Air mouse").forEachIndexed{i,t->Tab(selected=mode==i,onClick={mode=i},text={Text(t,fontSize=12.sp)})}}
                    if(mode==0) Pad(::key) else if(mode==1) {
                        var dx by remember {mutableFloatStateOf(0f)};var dy by remember{mutableFloatStateOf(0f)}
                        Box(Modifier.fillMaxWidth().height(265.dp).clip(RoundedCornerShape(22.dp)).background(Pale).border(1.dp,Border,RoundedCornerShape(22.dp)).pointerInput(connected,state.mode){detectDragGestures(onDragStart={dx=0f;dy=0f},onDragEnd={if(state.mode==AppMode.ANDROID)key(if(kotlin.math.abs(dx)>kotlin.math.abs(dy)){if(dx>0)22 else 21}else if(dy>0)20 else 19)}){change,drag->change.consume();dx+=drag.x;dy+=drag.y;if(connected&&state.mode==AppMode.PC)vm.sendMouseMove(drag.x*2,drag.y*2)}}.pointerInput(connected){detectTapGestures{if(connected&&state.mode==AppMode.PC)vm.sendMouseClick(MouseButton.LEFT) else key(23)}},contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Default.TouchApp,null,Modifier.size(38.dp),tint=Teal);Text("Touch, swipe, control",fontWeight=FontWeight.SemiBold);Text(if(state.mode==AppMode.PC)"Move pointer • Tap to click" else "Swipe to navigate • Tap to select",fontSize=12.sp,color=Muted)}}
                    } else Surface(color=Pale,shape=RoundedCornerShape(22.dp)){Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Default.Air,null,Modifier.size(36.dp),tint=Teal);Text("Air mouse",fontWeight=FontWeight.Bold);Text("Use the precision mouse controls on your Android TV.",fontSize=12.sp,color=Muted);OutlinedButton(onClick={if(requireTv())detail="mouse"}){Text("Open mouse controls")};Text("Gyroscope steering is not enabled in this native beta.",fontSize=11.sp,color=Muted)}}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){RoundKey("Back",Icons.AutoMirrored.Filled.ArrowBack){key(4)};RoundKey("Home",Icons.Default.Home){key(3)};RoundKey("Menu",Icons.Default.Menu){key(82)}}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){Column(horizontalAlignment=Alignment.CenterHorizontally){Surface(color=Pale,shape=CircleShape,border=BorderStroke(1.dp,Border)){Column(horizontalAlignment=Alignment.CenterHorizontally){IconButton(onClick={key(24)}){Icon(Icons.Default.Add,"Volume up")};Icon(Icons.AutoMirrored.Filled.VolumeUp,null);IconButton(onClick={key(25)}){Icon(Icons.Default.Remove,"Volume down")}}};Text("Volume",fontSize=11.sp,color=Muted)};Column(verticalArrangement=Arrangement.spacedBy(15.dp)){RoundKey("Mute",Icons.AutoMirrored.Filled.VolumeOff){key(164)};RoundKey("Power",Icons.Default.PowerSettingsNew){if(requireTv())dialog="power"}};Column(horizontalAlignment=Alignment.CenterHorizontally){Surface(color=Pale,shape=CircleShape,border=BorderStroke(1.dp,Border)){Column(horizontalAlignment=Alignment.CenterHorizontally){IconButton(onClick={key(166)}){Icon(Icons.Default.KeyboardArrowUp,"Channel up")};Text("CH",fontSize=12.sp);IconButton(onClick={key(167)}){Icon(Icons.Default.KeyboardArrowDown,"Channel down")}}};Text("Channel",fontSize=11.sp,color=Muted)}}
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Quick("Keyboard",Icons.Default.Keyboard,Modifier.weight(1f)){if(requireDevice()){command="";dialog="keyboard"}};Quick("Microphone",Icons.Default.Mic,Modifier.weight(1f)){if(requireDevice())runCatching{voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))}.onFailure{message("No speech recognition app installed")}}}
                }
                2->{
                    Title("Screen mirror","View your device screen in real time.")
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Teal),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Tv,null,Modifier.size(18.dp));Text("  TV → Phone",fontSize=12.sp)};OutlinedButton(onClick={dialog="cast"},modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp)){Text("Phone → TV",fontSize=12.sp)}}
                    Surface(color=Pale,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.fillMaxWidth().height(210.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(Icons.Default.Tv,null,Modifier.size(54.dp),tint=Muted);Spacer(Modifier.height(12.dp));Text(if(connected)status.deviceName?:"TV screen" else "Your screen, right here",fontWeight=FontWeight.SemiBold);Text("Connect an Android TV to begin.",fontSize=12.sp,color=Muted)}}
                    OutlinedButton(onClick={if(requireDevice()){if(state.mode==AppMode.ANDROID){val intent=Intent(context,DoupadMirrorActivity::class.java).putExtra("endpoint",status.address);context.startActivity(intent)}else message("Live video currently requires Android TV")}},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Fullscreen,null);Text("Live mirror · Full screen")}
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Quick("Screenshot",Icons.Default.PhotoCamera,Modifier.weight(1f)){if(requireTv()){detail="preview"}};Quick("Record",Icons.Default.FiberManualRecord,Modifier.weight(1f)){dialog="record"}}
                    Text("Video quality",fontWeight=FontWeight.SemiBold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Auto","720p","1080p").forEach{q->FilterChip(selected=q=="Auto",onClick={message("This beta uses adaptive 1280-pixel video, up to 30 fps.")},label={Text(q)})}}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("Include TV audio",fontWeight=FontWeight.SemiBold);Text("Video-only in this beta",fontSize=12.sp,color=Muted)};Switch(checked=false,onCheckedChange={message("Audio forwarding is not enabled in this beta.")})}
                    Hint("Availability depends on the device. Protected video may appear blank.")
                }
                3->{
                    Title("Device tools","Get more from your devices.")
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
                                            "Shell" -> { command=""; output=""; dialog="shell" }
                                            else -> {
                                                dialog="Device info"; output="Loading…"
                                                scope.launch {
                                                    output=vm.deviceInfo().fold(
                                                        { info -> info.entries.joinToString("\n") { e -> "${e.key}: ${e.value}" } },
                                                        { error -> error.message ?: "Unavailable" }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if(row.size==1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text("More tools",fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Surface(shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Border),color=Color.White){Column{ToolRow("Gamepad","Use your phone as a gamepad",Icons.Default.SportsEsports){if(requireDevice())dialog="gamepad"};HorizontalDivider(color=Border);ToolRow("Button mapping","Customize remote keycodes",Icons.Default.Tune){dialog="mapping"};HorizontalDivider(color=Border);ToolRow("Power options","Sleep, restart or power off",Icons.Default.PowerSettingsNew){if(requireTv())dialog="power"}}}
                }
            }
        }
    }
    if(dialog!=null) AlertDialog(onDismissRequest={dialog=null},containerColor=Color.White,title={Text(when(dialog){"connect"->"Add a device";"keyboard"->"Keyboard";"settings"->"DOUPAD";"scan"->"Nearby devices";"shell"->"Shell";"power"->"Power options";"gamepad"->"Gamepad";else->dialog!!},fontWeight=FontWeight.Bold)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            when(dialog){
                "connect"->{Row{FilterChip(selected=!pc,onClick={pc=false;port="5555"},label={Text("Android TV")});Spacer(Modifier.width(8.dp));FilterChip(selected=pc,onClick={pc=true;port="27845"},label={Text("Windows PC")})};OutlinedTextField(ip,{ip=it},label={Text("IP address")},singleLine=true);OutlinedTextField(port,{port=it.filter(Char::isDigit)},label={Text("Port")},singleLine=true);if(pc)OutlinedTextField(pin,{pin=it},label={Text("Host pairing PIN")},singleLine=true);Text(if(pc)"Start the included DOUPAD PC host. Use its displayed IP, port and PIN." else "Enable network/USB debugging and accept the authorization prompt on your TV. Pairing-code TLS is not supported.",fontSize=12.sp,color=Muted);if(localError.isNotBlank())Text(localError,color=MaterialTheme.colorScheme.error);if(status.state==ConnectionState.CONNECTING)LinearProgressIndicator(Modifier.fillMaxWidth())}
                "scan"->{if(state.isScanning)LinearProgressIndicator(Modifier.fillMaxWidth());state.androidDevices.forEach{d->TextButton(onClick={ip=d.ip;port=d.port.toString();pc=false;dialog="connect"}){Text("${d.name}\n${d.ip}:${d.port}")}};if(!state.isScanning&&state.androidDevices.isEmpty())Text("No TVs found. Check network debugging or enter the IP manually.")}
                "keyboard"->{OutlinedTextField(command,{command=it},label={Text("Text to send")});Text("Focus a text field on your device first.",fontSize=12.sp,color=Muted)}
                "shell"->{OutlinedTextField(command,{command=it},label={Text("ADB command")});Button(onClick={scope.launch{output="Running…";output=vm.executeAdbAwait(command)}}){Text("Run")};if(output.isNotBlank())Text(output,fontSize=12.sp)}
                "settings"->{Text("DOUPAD 3.1.0 · Realtime control beta");Text("White / teal theme • No demo connected devices.",fontSize=12.sp);if(connected)TextButton(onClick={vm.disconnect();dialog=null}){Text("Disconnect ${status.deviceName}")};TextButton(onClick={targets=emptyList();saveTargets(context,targets);dialog=null}){Text("Clear saved device list")};Text("Live mirror uses scrcpy 3.3.1 under Apache 2.0. Physical device testing is still required.",fontSize=12.sp,color=Muted)}
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
            "connect"->{val p=port.toIntOrNull();if(ip.isBlank()||ip.contains(Regex("[\\s/:]"))||p==null||p !in 1..65535){localError="Enter a valid host and port."}else{localError="";if(pc)vm.connectNetwork(ip,p,pin.ifBlank{null}) else vm.connectAndroidIp("$ip:$p");dialog=null}}
            "keyboard"->{if(state.mode==AppMode.ANDROID)vm.executeAdb(AdbCommand.InputText(command)) else vm.sendText(command);dialog=null}
            "Confirm power action"->{showCommand("Power result",command)}
            else->dialog=null
        }
    }){Text(when(dialog){"connect"->"Connect";"keyboard"->"Send";"Confirm power action"->"Confirm";else->"Done"},color=Teal)}},dismissButton={TextButton(onClick={dialog=null}){Text("Close",color=Muted)}})
}

@Composable private fun Title(title:String,subtitle:String){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text(title,fontSize=26.sp,fontWeight=FontWeight.Bold,color=Ink,letterSpacing=(-0.6).sp);Text(subtitle,fontSize=13.sp,color=Muted)}}
@Composable private fun Hint(text:String){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.Info,null,Modifier.size(16.dp),tint=Muted);Text(text,fontSize=11.sp,color=Muted)}}
@Composable private fun Quick(label:String,icon:ImageVector,modifier:Modifier=Modifier,action:()->Unit){Surface(onClick=action,modifier=modifier,shape=RoundedCornerShape(13.dp),color=Color.White,border=BorderStroke(1.dp,Border)){Column(Modifier.padding(horizontal=4.dp,vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(icon,null,Modifier.size(26.dp),tint=Teal);Text(label,fontSize=11.sp,fontWeight=FontWeight.Medium,maxLines=1)}}}
@Composable private fun RoundKey(label:String,icon:ImageVector,action:()->Unit){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){Surface(onClick=action,shape=CircleShape,color=Color.White,border=BorderStroke(1.dp,Border)){Box(Modifier.size(55.dp),contentAlignment=Alignment.Center){Icon(icon,label,tint=if(label=="Power")Color(0xFFDA3444) else Ink)}};Text(label,fontSize=11.sp,color=Muted)}}
@Composable private fun Pad(key:(Int)->Unit){BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){val size=minOf(maxWidth,264.dp);Box(Modifier.size(size).clip(CircleShape).background(Pale).border(1.dp,Border,CircleShape)){IconButton(onClick={key(19)},modifier=Modifier.align(Alignment.TopCenter).padding(top=12.dp)){Icon(Icons.Default.KeyboardArrowUp,"Up")};IconButton(onClick={key(20)},modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp)){Icon(Icons.Default.KeyboardArrowDown,"Down")};IconButton(onClick={key(21)},modifier=Modifier.align(Alignment.CenterStart).padding(start=12.dp)){Icon(Icons.Default.KeyboardArrowLeft,"Left")};IconButton(onClick={key(22)},modifier=Modifier.align(Alignment.CenterEnd).padding(end=12.dp)){Icon(Icons.Default.KeyboardArrowRight,"Right")};Surface(onClick={key(23)},modifier=Modifier.align(Alignment.Center).size(88.dp),shape=CircleShape,color=Teal){Box(contentAlignment=Alignment.Center){Text("OK",fontSize=20.sp,color=Color.White,fontWeight=FontWeight.SemiBold)}}}}}
@Composable private fun ToolTile(label:String,subtitle:String,icon:ImageVector,modifier:Modifier,action:()->Unit){Surface(onClick=action,modifier=modifier,shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Border),color=Color.White){Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Icon(icon,null,tint=Teal,modifier=Modifier.size(26.dp));Icon(Icons.Default.ChevronRight,null,tint=Muted,modifier=Modifier.size(16.dp))};Text(label,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(subtitle,fontSize=10.sp,color=Muted)}}}
@Composable private fun ToolRow(label:String,subtitle:String,icon:ImageVector,action:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){Icon(icon,null,Modifier.size(25.dp));Column(Modifier.weight(1f)){Text(label,fontSize=13.sp,fontWeight=FontWeight.SemiBold);Text(subtitle,fontSize=11.sp,color=Muted)};Icon(Icons.Default.ChevronRight,null,Modifier.size(16.dp),tint=Muted)}}
