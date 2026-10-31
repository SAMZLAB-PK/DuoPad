package com.unipoint.presentation.ui.screens.android

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
    fun key(code: Int) = viewModel.executeAdb(AdbCommand.InputKey(code))
    val ink = Color(0xFF102033)
    val teal = Color(0xFF008C91)

    Scaffold(
        containerColor = Color(0xFFFBFCFD),
        topBar = {
            TopAppBar(
                title = { Column { Text("Gamepad", fontWeight = FontWeight.Bold, color = ink); Text("Android TV / Box", fontSize = 11.sp) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp)) {
            val portrait = maxHeight > maxWidth
            val wheel = minOf(maxWidth * (if (portrait) 0.44f else 0.34f), maxHeight * 0.56f, 206.dp)
            val faceKey = minOf(wheel * 0.31f, 52.dp)
            val arrowKey = minOf(wheel * 0.32f, 56.dp)
            Column(Modifier.fillMaxSize(),verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { key(102) },contentPadding=PaddingValues(horizontal=10.dp)) { Text("L1",fontSize=12.sp) }
                    TextButton(onClick = { key(109) },contentPadding=PaddingValues(horizontal=3.dp)) { Text("SELECT",fontSize=11.sp) }
                    TextButton(onClick = { key(108) },contentPadding=PaddingValues(horizontal=3.dp)) { Text("START",fontSize=11.sp) }
                    OutlinedButton(onClick = { key(103) },contentPadding=PaddingValues(horizontal=10.dp)) { Text("R1",fontSize=12.sp) }
                }

                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(wheel),contentAlignment=Alignment.Center) {
                        Surface(Modifier.fillMaxSize(),shape=CircleShape,color=Color(0xFFE9F0F5)) { }
                        IconButton(onClick={key(19)},modifier=Modifier.align(Alignment.TopCenter).size(arrowKey)){
                            Icon(Icons.Default.KeyboardArrowUp,"Up",Modifier.size(32.dp),tint=ink)
                        }
                        IconButton(onClick={key(20)},modifier=Modifier.align(Alignment.BottomCenter).size(arrowKey)){
                            Icon(Icons.Default.KeyboardArrowDown,"Down",Modifier.size(32.dp),tint=ink)
                        }
                        IconButton(onClick={key(21)},modifier=Modifier.align(Alignment.CenterStart).size(arrowKey)){
                            Icon(Icons.Default.KeyboardArrowLeft,"Left",Modifier.size(32.dp),tint=ink)
                        }
                        IconButton(onClick={key(22)},modifier=Modifier.align(Alignment.CenterEnd).size(arrowKey)){
                            Icon(Icons.Default.KeyboardArrowRight,"Right",Modifier.size(32.dp),tint=ink)
                        }
                        FilledIconButton(onClick={key(23)},modifier=Modifier.size(wheel*0.36f),
                            colors=IconButtonDefaults.filledIconButtonColors(containerColor=teal)){
                            Text("OK",color=Color.White,fontWeight=FontWeight.Bold,fontSize=15.sp)
                        }
                    }
                    Box(Modifier.size(wheel),contentAlignment=Alignment.Center) {
                        listOf(
                            Triple("Y",100,Alignment.TopCenter),
                            Triple("A",96,Alignment.BottomCenter),
                            Triple("X",99,Alignment.CenterStart),
                            Triple("B",97,Alignment.CenterEnd)
                        ).forEach { (name,code,position) ->
                            FilledIconButton(
                                onClick={key(code)},modifier=Modifier.align(position).size(faceKey),
                                colors=IconButtonDefaults.filledIconButtonColors(
                                    containerColor=when(name){"A"->Color(0xFFDCF5EB);"B"->Color(0xFFFFE6E7);"X"->Color(0xFFDEEDF9);else->Color(0xFFF7EACD)},
                                    contentColor=ink
                                )
                            ) { Text(name,fontWeight=FontWeight.Bold,fontSize=15.sp) }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    OutlinedButton(onClick={key(4)}) { Icon(Icons.AutoMirrored.Filled.ArrowBack,null);Spacer(Modifier.width(4.dp));Text("Back") }
                    OutlinedButton(onClick={key(3)}) { Icon(Icons.Default.Home,null);Spacer(Modifier.width(4.dp));Text("Home") }
                    OutlinedButton(onClick={key(82)}) { Icon(Icons.Default.Menu,null);Spacer(Modifier.width(4.dp));Text("Menu") }
                }
                Text(
                    "Works with Android keycodes. Some games require a paired HID gamepad.",
                    fontSize=11.sp,color=Color(0xFF667587),modifier=Modifier.fillMaxWidth()
                )
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
