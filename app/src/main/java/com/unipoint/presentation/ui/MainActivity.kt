package com.unipoint.presentation.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.unipoint.presentation.ui.navigation.UniPointNavHost
import com.unipoint.presentation.ui.theme.UniPointTheme
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results ignored – user can deny */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appearancePrefs = getSharedPreferences("doupad", MODE_PRIVATE)
        val playUpdateAvailable = mutableStateOf(false)
        // Google Play verifies the installed track/version. A sideloaded APK might not have availability data.
        runCatching {
            AppUpdateManagerFactory.create(this).appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    playUpdateAvailable.value = true
                }
            }
        }
        setContent {
            var appearance by remember { mutableStateOf(
                appearancePrefs.getString("appearance", "light") ?: "light"
            ) }
            val dark = appearance == "dark"
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            UniPointTheme(appearance = appearance) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DoupadShell(
                        appearance = appearance,
                        onAppearanceChange = { newAppearance ->
                            if (newAppearance in listOf("light", "dark", "bento")) {
                                appearance = newAppearance
                                appearancePrefs.edit().putString("appearance", newAppearance).apply()
                            }
                        }
                    )
                    if (playUpdateAvailable.value) {
                        AlertDialog(
                            onDismissRequest = { playUpdateAvailable.value = false },
                            title = { Text("DuoPad Pro update available") },
                            text = { Text("A newer version is available on Google Play. Update to receive fixes and improvements.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    playUpdateAvailable.value = false
                                    startActivity(Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                                }) { Text("Open Play Store") }
                            },
                            dismissButton = {
                                TextButton(onClick = { playUpdateAvailable.value = false }) { Text("Later") }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).forEach {
                if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                    needed.add(it)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.CAMERA)

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
