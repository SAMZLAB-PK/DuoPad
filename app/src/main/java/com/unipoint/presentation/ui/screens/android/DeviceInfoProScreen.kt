package com.unipoint.presentation.ui.screens.android

import androidx.compose.foundation.layout.*
import com.unipoint.presentation.ui.theme.DuoColors
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unipoint.presentation.viewmodel.MainViewModel

/**
 * Professional single-round-trip device dashboard.
 * Values come from the currently authorized device; no guessed model or specs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoProScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    var info by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        failure = null
        viewModel.deviceInfo().fold(
            onSuccess = { info=it },
            onFailure = { failure=it.message ?: "Unable to query device" }
        )
        loading = false
    }
    val ink=DuoColors.OnSurface
    val muted=DuoColors.OnSurfaceMuted
    val teal=DuoColors.Accent
    @Composable fun Detail(key:String,label:String) {
        Column(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(label,fontSize=11.sp,color=muted)
            Text(info[key].orEmpty().ifBlank { "Unavailable" },fontWeight=FontWeight.SemiBold,fontSize=14.sp,color=ink)
        }
    }
    @Composable fun Section(title:String, body:@Composable ColumnScope.()->Unit) {
        Card(
            modifier=Modifier.fillMaxWidth(),
            shape=RoundedCornerShape(16.dp),
            colors=CardDefaults.cardColors(containerColor=DuoColors.SurfaceMid)
        ) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(title,fontWeight=FontWeight.Bold,fontSize=15.sp,color=teal)
                HorizontalDivider(color=DuoColors.Hairline)
                body()
            }
        }
    }

    Scaffold(
        containerColor=DuoColors.Background,
        topBar={
            TopAppBar(
                title={Text("Device information",fontWeight=FontWeight.Bold)},
                navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},
                actions={IconButton(onClick={refresh++}){Icon(Icons.Default.Refresh,"Refresh")}},
                colors=TopAppBarDefaults.topAppBarColors(containerColor=DuoColors.Background)
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ){
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failure!=null) {
                Text(failure ?: "",color=MaterialTheme.colorScheme.error)
                OutlinedButton(onClick={refresh++}){Text("Retry")}
            }
            if(info.isNotEmpty()){
                Card(colors=CardDefaults.cardColors(containerColor=DuoColors.SurfaceHigh),shape=RoundedCornerShape(16.dp)){
                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.Tv,null,Modifier.size(42.dp),tint=teal)
                        Spacer(Modifier.width(12.dp))
                        Column{
                            Text(info["model"] ?: "Android Device",fontSize=20.sp,fontWeight=FontWeight.Bold,color=ink)
                            Text("${info["brand"].orEmpty()} • Android ${info["android"].orEmpty()}",fontSize=12.sp,color=muted)
                        }
                    }
                }
                Section("System") {
                    Detail("model","Model")
                    Detail("brand","Manufacturer")
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Box(Modifier.weight(1f)){Detail("android","Android version")}
                        Box(Modifier.weight(1f)){Detail("sdk","API level")}
                    }
                    Detail("uptime","Uptime")
                }
                Section("Display & hardware") {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Box(Modifier.weight(1f)){Detail("display","Screen resolution")}
                        Box(Modifier.weight(1f)){Detail("density","Density")}
                    }
                    Detail("ram","Installed memory")
                }
                Section("Storage & power") {
                    Detail("storage","Internal storage")
                    Detail("battery","Battery (if available)")
                }
                Section("Network") {
                    Detail("ip","LAN IP address")
                }
                Text("All values are read live from the selected device. Unavailable fields depend on device firmware and permissions.",
                    fontSize=11.sp,color=muted)
            }
        }
    }
}
