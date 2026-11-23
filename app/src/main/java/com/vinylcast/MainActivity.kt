package com.vinylcast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vinylcast.ui.MainViewModel
import com.vinylcast.ui.SettingsScreen
import com.vinylcast.ui.TurntableScreen
import com.vinylcast.ui.theme.VinylCastTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VinylCastTheme {
                val vm: MainViewModel = viewModel()
                val settings by vm.settings.collectAsState()
                var showSettings by remember { mutableStateOf(false) }

                // Apply Kiosk / Keep-Screen-On flag
                LaunchedEffect(settings.keepScreenOn) {
                    if (settings.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Request runtime permissions on launch
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
