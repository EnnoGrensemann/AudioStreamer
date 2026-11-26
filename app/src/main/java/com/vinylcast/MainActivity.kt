package com.vinylcast

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vinylcast.ui.MainViewModel
import com.vinylcast.ui.SettingsScreen
import com.vinylcast.ui.TurntableScreen
import com.vinylcast.ui.theme.VinylCastTheme
import com.vinylcast.ui.tv.TvTurntableScreen
import com.vinylcast.ui.tv.TvViewModel

class MainActivity : ComponentActivity() {

    private val usbAttachedTrigger = mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            usbAttachedTrigger.intValue += 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { }

            LaunchedEffect(Unit) {
                val required = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (required.isNotEmpty()) {
                    permissionLauncher.launch(required.toTypedArray())
                }
            }

            if (BuildConfig.IS_TV) {
                val tvVm: TvViewModel = viewModel()
                val settings by tvVm.settings.collectAsState()
                val usbTrigger by usbAttachedTrigger

                LaunchedEffect(usbTrigger) {
                    if (usbTrigger > 0) {
                        tvVm.onUsbDeviceAttached()
                    }
                }

                LaunchedEffect(settings.keepScreenOn) {
                    if (settings.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                VinylCastTheme(darkTheme = settings.darkMode) {
                    TvTurntableScreen(
                        viewModel = tvVm,
                        onExitToBackground = { moveTaskToBack(true) },
                        onStopAndExit = { finish() }
                    )
                }
            } else {
                val vm: MainViewModel = viewModel()
                val settings by vm.settings.collectAsState()
                var showSettings by remember { mutableStateOf(false) }

                LaunchedEffect(settings.darkMode) {
                    val style = if (settings.darkMode) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }

                LaunchedEffect(settings.keepScreenOn) {
                    if (settings.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                VinylCastTheme(darkTheme = settings.darkMode) {
                    if (showSettings) {
                        SettingsScreen(
                            viewModel = vm,
                            onBack = { showSettings = false }
                        )
                    } else {
                        TurntableScreen(
                            viewModel = vm,
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}
