package com.unipoint.presentation.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.unipoint.service.PhoneMirrorForegroundService
import com.unipoint.presentation.ui.theme.UniPointTheme

/** Explicit user-consented Android screen capture for an already paired Windows Host. */
class PhoneMirrorActivity : ComponentActivity() {
    private val capturePermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val address = intent.getStringExtra("host").orEmpty()
            val port = intent.getIntExtra("port", 27845)
            val pin = intent.getStringExtra("pin").orEmpty()
            if(address.isNotBlank() && port in 1..65535) {
                val service = Intent(this, PhoneMirrorForegroundService::class.java).apply {
                    action = PhoneMirrorForegroundService.ACTION_START
                    putExtra("projection_result", result.resultCode)
                    putExtra("projection_data", result.data)
                    putExtra("host", address)
                    putExtra("port", port)
                    putExtra("pin", pin)
                }
                ContextCompat.startForegroundService(this, service)
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UniPointTheme(appearance = "light") {
                Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.Center) {
                        Icon(Icons.Default.ScreenShare,"Phone screen",modifier=Modifier.size(55.dp))
                        Spacer(Modifier.height(18.dp))
                        Text("Mirror phone to PC",style=MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(9.dp))
                        Text("Your phone will ask you to approve screen sharing. DuoPad PC Host must be running and paired. You can stop sharing from the notification.",
                            style=MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(22.dp))
                        Button(onClick={
                            val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            capturePermission.launch(manager.createScreenCaptureIntent())
                        }) { Text("Start screen sharing") }
                        OutlinedButton(onClick={
                            stopService(Intent(this@PhoneMirrorActivity,PhoneMirrorForegroundService::class.java))
                            finish()
                        }) {
                            Icon(Icons.Default.StopCircle,null)
                            Spacer(Modifier.width(6.dp))
                            Text("Stop sharing")
                        }
                    }
                }
            }
        }
    }
}
