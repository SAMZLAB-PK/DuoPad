package com.unipoint.presentation.ui.screens.android

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.domain.model.AdbCommand
import com.unipoint.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.launch

object RemoteKeyMapping {
    val defaults = linkedMapOf(
        "Up" to 19, "Down" to 20, "Left" to 21, "Right" to 22, "OK" to 23,
        "Back" to 4, "Home" to 3, "Menu" to 82,
        "Volume +" to 24, "Volume -" to 25, "Mute" to 164,
        "Channel +" to 166, "Channel -" to 167
    )

    private const val PREFS = "doupad_remote_mapping"
    fun resolve(context: Context, defaultCode: Int): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("code_$defaultCode", defaultCode)

    fun save(context: Context, defaultCode: Int, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("code_$defaultCode", value).apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbShellScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("Ready. Enter an ADB shell command or tap a hint.") }
    var running by remember { mutableStateOf(false) }
    val hints = listOf(
        "Device model" to "getprop ro.product.model",
        "Android version" to "getprop ro.build.version.release",
        "Battery" to "dumpsys battery | grep -E 'level|status|temperature'",
        "Storage" to "df -h /data",
        "Wi-Fi IP" to "ip route get 1.1.1.1",
        "User apps" to "pm list packages -3"
    )
    fun runCommand() {
        val cmd = command.trim()
        if (cmd.isEmpty() || running) return
        running = true
        scope.launch {
            output = viewModel.executeAdbAwait(cmd).ifBlank { "Command completed with no output." }
            running = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("ADB Shell", fontWeight = FontWeight.Bold); Text("DouPad Pro", fontSize = 11.sp) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { command="";output="Ready." }) { Icon(Icons.Default.DeleteSweep, "Clear") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Command", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = command, onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                placeholder = { Text("e.g. getprop ro.product.model") },
                trailingIcon = { IconButton(onClick = ::runCommand) { Icon(Icons.Default.PlayArrow, "Run") } }
            )
            Button(onClick = ::runCommand, enabled = command.isNotBlank() && !running, modifier = Modifier.fillMaxWidth()) {
                if (running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Terminal, null)
                Spacer(Modifier.width(8.dp)); Text(if (running) "Running…" else "Run command")
            }

            Text("Quick hints", fontWeight = FontWeight.SemiBold)
            hints.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, cmd) ->
                        AssistChip(
                            onClick = { command = cmd },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text("Output", fontWeight = FontWeight.SemiBold)
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF101821)
            ) {
                Text(
                    output, color = Color(0xFFE8F0F5), fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp, modifier = Modifier.padding(14.dp)
                )
            }
            Text(
                "Tip: commands run only on the currently connected Android TV/Box. A lost ADB socket is retried once automatically.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteMappingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var generation by remember { mutableIntStateOf(0) }
    val values = remember(generation) {
        RemoteKeyMapping.defaults.mapValues { (_, code) -> RemoteKeyMapping.resolve(context, code).toString() }.toMutableMap()
    }
    var editing by remember { mutableStateOf(values.toMap()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Button mapping", fontWeight = FontWeight.Bold); Text("Customize Android keycodes", fontSize = 11.sp) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = {
                        RemoteKeyMapping.reset(context)
                        generation++
                        editing = RemoteKeyMapping.defaults.mapValues { it.value.toString() }
                    }) { Text("Reset") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Changes apply immediately to the remote.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            RemoteKeyMapping.defaults.forEach { (label, defaultCode) ->
                val text = editing[label] ?: defaultCode.toString()
                OutlinedTextField(
                    value = text,
                    onValueChange = { raw ->
                        val clean = raw.filter(Char::isDigit).take(5)
                        editing = editing.toMutableMap().also { it[label] = clean }
                        clean.toIntOrNull()?.takeIf { it in 0..65535 }?.let {
                            RemoteKeyMapping.save(context, defaultCode, it)
                        }
                    },
                    label = { Text(label) },
                    supportingText = { Text("Default: $defaultCode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidGamepadScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val ink = Color(0xFF121212)
    val buttonColor = Color(0xFF393939)
    fun key(code:Int) = viewModel.executeAdb(AdbCommand.InputKey(code))

    @Composable fun PadButton(text:String,code:Int,modifier:Modifier=Modifier) {
        Surface(
            onClick={key(code)},modifier=modifier,
            shape=RoundedCornerShape(14.dp),color=buttonColor
        ) { Box(contentAlignment=Alignment.Center) {
            Text(text,color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp)
        } }
    }
    @Composable fun Stick(label:String,code:Int,size: androidx.compose.ui.unit.Dp) {
        var dx by remember { mutableFloatStateOf(0f) }
        var dy by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.size(size).pointerInput(label) {
                detectDragGestures(
                    onDragStart={dx=0f;dy=0f},
                    onDrag={change,amount-> change.consume(); dx+=amount.x;dy+=amount.y},
                    onDragEnd={
                        val horizontal=kotlin.math.abs(dx)>kotlin.math.abs(dy)
                        key(if(horizontal){if(dx>0)22 else 21}else{if(dy>0)20 else 19})
                        dx=0f;dy=0f
                    },
                    onDragCancel={dx=0f;dy=0f}
                )
            },contentAlignment=Alignment.Center
        ){
            Surface(Modifier.fillMaxSize(),shape=CircleShape,color=buttonColor,
                border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF858585))){}
            Surface(onClick={key(code)},modifier=Modifier.size(size*0.52f),
                shape=CircleShape,color=Color.White){
                Box(contentAlignment=Alignment.Center){
                    Text(label,color=ink,fontSize=28.sp,fontWeight=FontWeight.Bold)
                }
            }
        }
    }
    @Composable fun Directions(size: androidx.compose.ui.unit.Dp) {
        Box(Modifier.size(size),contentAlignment=Alignment.Center){
            Surface(Modifier.fillMaxSize(),shape=CircleShape,color=buttonColor){}
            val side=size*0.30f
            listOf(
                Triple("▲",19,Alignment.TopCenter),
                Triple("▼",20,Alignment.BottomCenter),
                Triple("◀",21,Alignment.CenterStart),
                Triple("▶",22,Alignment.CenterEnd)
            ).forEach { (label,code,pos) ->
                Surface(onClick={key(code)},modifier=Modifier.align(pos).size(side),
                    shape=RoundedCornerShape(11.dp),color=Color(0xFF6A6A6A)){
                    Box(contentAlignment=Alignment.Center){
                        Text(label,fontSize=14.sp,color=Color.White)
                    }
                }
            }
        }
    }
    @Composable fun Face(size: androidx.compose.ui.unit.Dp) {
        Box(Modifier.size(size),contentAlignment=Alignment.Center){
            val d=size*0.34f
            listOf(
                Triple("Y",100,Alignment.TopCenter),
                Triple("A",96,Alignment.BottomCenter),
                Triple("X",99,Alignment.CenterStart),
                Triple("B",97,Alignment.CenterEnd)
            ).forEach{(label,code,pos)->
                Surface(onClick={key(code)},modifier=Modifier.align(pos).size(d),
                    shape=CircleShape,color=buttonColor){
                    Box(contentAlignment=Alignment.Center){
                        Text(label,color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
    }
    Scaffold(
        containerColor=Color(0xFF141414),
        topBar={
            TopAppBar(
                title={Text("Gamepad",fontWeight=FontWeight.Bold,color=Color.White)},
                navigationIcon={IconButton(onClick=onBack){
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back",tint=Color.White)
                }},
                colors=TopAppBarDefaults.topAppBarColors(containerColor=Color(0xFF141414))
            )
        }
    ){pad->
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad).padding(10.dp)){
            val portrait=maxHeight>maxWidth
            val size=minOf(maxWidth*(if(portrait)0.42f else 0.24f),
                maxHeight*(if(portrait)0.23f else 0.40f),210.dp)
            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceBetween){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    listOf("LB" to 102,"LT" to 104,"RT" to 105,"RB" to 103).forEach{(label,code)->
                        PadButton(label,code,Modifier.width(62.dp).height(37.dp))
                    }
                }
                if(portrait){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically){
                        Stick("L",106,size)
                        Face(size)
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                        listOf("SELECT" to 109,"HOME" to 3,"START" to 108).forEach{(label,code)->
                            PadButton(label,code,Modifier.width(89.dp).height(39.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically){
                        Directions(size)
                        Stick("R",107,size)
                    }
                } else {
                    Row(Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceEvenly,
                        verticalAlignment=Alignment.CenterVertically){
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp),
                            horizontalAlignment=Alignment.CenterHorizontally){
                            Stick("L",106,size)
                            Directions(size*0.78f)
                        }
                        Column(horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.spacedBy(9.dp)){
                            listOf("SELECT" to 109,"HOME" to 3,"START" to 108).forEach{(label,code)->
                                PadButton(label,code,Modifier.width(85.dp).height(40.dp))
                            }
                        }
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp),
                            horizontalAlignment=Alignment.CenterHorizontally){
                            Face(size)
                            Stick("R",107,size*0.78f)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                    PadButton("L3",106,Modifier.width(70.dp).height(36.dp))
                    PadButton("BACK",4,Modifier.width(70.dp).height(36.dp))
                    PadButton("R3",107,Modifier.width(70.dp).height(36.dp))
                }
                Text("Directional stick emulation uses Android keycodes; true analog HID requires gamepad support.",
                    fontSize=10.sp,color=Color.LightGray,maxLines=2)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidKeyboardScreen(
    onBack: () -> Unit,
    onSwipeMode: (Int) -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    var text by remember { mutableStateOf("") }
    var shift by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val rows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("Q","W","E","R","T","Y","U","I","O","P"),
        listOf("A","S","D","F","G","H","J","K","L"),
        listOf("Z","X","C","V","B","N","M")
    )
    fun sendText(value: String) {
        if (value.isNotEmpty()) viewModel.executeAdb(AdbCommand.InputText(value))
    }
    fun key(code: Int) = viewModel.executeAdb(AdbCommand.InputKey(code))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Keyboard", fontWeight = FontWeight.Bold); Text("Swipe header to change remote mode", fontSize = 11.sp) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { key(67) }) { Icon(Icons.Default.Backspace, "Backspace") } },
                modifier = Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragEnd = {
                            if (drag > 90f) onSwipeMode(-1)
                            else if (drag < -90f) onSwipeMode(1)
                            drag = 0f
                        }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type a message, search or URL…") },
                trailingIcon = {
                    IconButton(onClick = { sendText(text);text="" }) { Icon(Icons.Default.Send, "Send") }
                },
                singleLine = true
            )
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { raw ->
                        val label = if (shift) raw else raw.lowercase()
                        OutlinedButton(
                            onClick = { sendText(label); if (shift) shift=false },
                            modifier = Modifier.weight(1f).height(46.dp),
                            contentPadding = PaddingValues(1.dp)
                        ) { Text(label, fontSize = 13.sp) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = shift, onClick = { shift=!shift }, label = { Text("Shift") }, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { sendText(" ") }, modifier = Modifier.weight(2f)) { Text("Space") }
                OutlinedButton(onClick = { key(66) }, modifier = Modifier.weight(1f)) { Text("Enter") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { key(21) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.KeyboardArrowLeft, "Left") }
                OutlinedButton(onClick = { key(19) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.KeyboardArrowUp, "Up") }
                OutlinedButton(onClick = { key(20) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.KeyboardArrowDown, "Down") }
                OutlinedButton(onClick = { key(22) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.KeyboardArrowRight, "Right") }
            }
        }
    }
}
