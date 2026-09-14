package com.unipoint.presentation.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.unipoint.presentation.ui.screens.DeviceListScreen
import com.unipoint.presentation.ui.screens.HomeScreen
import com.unipoint.presentation.ui.screens.android.AndroidDashboardScreen
import com.unipoint.presentation.ui.screens.android.AndroidRemoteScreen
import com.unipoint.presentation.ui.screens.android.AndroidMousePadScreen
import com.unipoint.presentation.ui.screens.android.AppManagerScreen
import com.unipoint.presentation.ui.screens.android.FileManagerScreen
import com.unipoint.presentation.ui.screens.android.ScreenMirrorScreen
import com.unipoint.presentation.ui.screens.common.QrConnectScreen
import com.unipoint.presentation.ui.screens.pc.GamepadScreen
import com.unipoint.presentation.ui.screens.pc.KeyboardScreen

object Routes {
    const val DEVICES = "devices"
    const val HOME = "home"
    const val KEYBOARD = "keyboard"
    const val GAMEPAD = "gamepad"
    const val ANDROID_DASH = "android_dash"
    const val ANDROID_REMOTE = "android_remote"
    const val ANDROID_MOUSE = "android_mouse"
    const val FILE_MANAGER = "file_manager"
    const val APP_MANAGER = "app_manager"
    const val SCREEN_MIRROR = "screen_mirror"
    const val QR_CONNECT = "qr_connect"
    const val TOOLS = "tools"
}

@Composable
fun UniPointNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DEVICES) {
        composable(Routes.DEVICES) {
            DeviceListScreen(
                onConnected = {
                    navController.navigate(Routes.ANDROID_DASH)
                },
                onOpenPc = {
                    navController.navigate(Routes.HOME)
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenKeyboard = { navController.navigate(Routes.KEYBOARD) },
                onOpenGamepad = { navController.navigate(Routes.GAMEPAD) },
                onOpenAndroidRemote = { navController.navigate(Routes.ANDROID_REMOTE) },
                onOpenFileManager = { navController.navigate(Routes.FILE_MANAGER) },
                onOpenAppManager = { navController.navigate(Routes.APP_MANAGER) },
                onOpenScreenMirror = { navController.navigate(Routes.SCREEN_MIRROR) },
                onOpenQr = { navController.navigate(Routes.QR_CONNECT) },
                onBackToDevices = {
                    navController.navigate(Routes.DEVICES) {
                        popUpTo(Routes.DEVICES) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.KEYBOARD) {
            KeyboardScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.GAMEPAD) {
            GamepadScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ANDROID_DASH) {
            AndroidDashboardScreen(
                onBack = { navController.popBackStack() },
                onOpenRemote = { navController.navigate(Routes.ANDROID_REMOTE) },
                onOpenFiles = { navController.navigate(Routes.FILE_MANAGER) },
                onOpenMirror = { navController.navigate(Routes.SCREEN_MIRROR) }
            )
        }
        composable(Routes.ANDROID_REMOTE) {
            AndroidRemoteScreen(
                onBack = { navController.popBackStack() },
                onOpenMousePad = { navController.navigate(Routes.ANDROID_MOUSE) }
            )
        }
        composable(Routes.ANDROID_MOUSE) {
            AndroidMousePadScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.FILE_MANAGER) {
            FileManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.APP_MANAGER) {
            AppManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SCREEN_MIRROR) {
            ScreenMirrorScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TOOLS) {
            AndroidDashboardScreen(
                onBack = { navController.popBackStack() },
                onOpenRemote = { navController.navigate(Routes.ANDROID_REMOTE) },
                onOpenFiles = { navController.navigate(Routes.FILE_MANAGER) },
                onOpenMirror = { navController.navigate(Routes.SCREEN_MIRROR) }
            )
        }
        composable(Routes.QR_CONNECT) {
            QrConnectScreen(
                onBack = { navController.popBackStack() },
                onConnected = { navController.popBackStack() }
            )
        }
    }
}
