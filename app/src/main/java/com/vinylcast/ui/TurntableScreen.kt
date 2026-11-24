package com.vinylcast.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinylcast.model.OutputTarget
import com.vinylcast.ui.components.TurntableDisc
import com.vinylcast.ui.components.VuMeter
import com.vinylcast.ui.theme.VinylTheme

@Composable
fun TurntableScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val colors = VinylTheme.colors
    val settings by viewModel.settings.collectAsState()
    val inputDevices by viewModel.inputDevices.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isAudioDetected by viewModel.isAudioDetected.collectAsState()
    val audioLevels by viewModel.audioLevels.collectAsState()
    val peakDb by viewModel.peakDb.collectAsState()
    val captureInfo by viewModel.captureInfo.collectAsState()
    val connectedClients by viewModel.connectedClients.collectAsState()
    val sonosDevices by viewModel.sonosDevices.collectAsState()
    val selectedOutput by viewModel.selectedOutput.collectAsState()
    val isDiscovering by viewModel.isDiscoveringSonos.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    val usbDevice = inputDevices.firstOrNull { it.isUsb }
    var outputMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = colors.amberPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VinylCast",
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))
                // Compact USB indicator badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (usbDevice != null) colors.emeraldActive else colors.amberPrimary)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (usbDevice != null) "Line-In" else "Kein USB",
                        color = colors.textPrimary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.toggleDarkMode() }) {
                    Icon(
                        imageVector = if (settings.darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (settings.darkMode) "Light Mode aktivieren" else "Dark Mode aktivieren",
                        tint = colors.amberPrimary
                    )
                }
                IconButton(onClick = { viewModel.toggleKeepScreenOn() }) {
                    Icon(
                        imageVector = Icons.Default.ScreenLockPortrait,
                        contentDescription = "Display aktiv lassen",
                        tint = if (settings.keepScreenOn) colors.amberPrimary else colors.textMuted
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Einstellungen",
                        tint = colors.textPrimary
                    )
                }
            }
        }

        // Large Output Selector (Sonos or Local/Bluetooth)
        Box(modifier = Modifier.fillMaxWidth()) {
            val outputTitle = when (val out = selectedOutput) {
                is OutputTarget.Sonos -> out.device.roomName
                OutputTarget.LocalDevice -> "Handy / Bluetooth"
            }
            val outputSubtitle = when (val out = selectedOutput) {
                is OutputTarget.Sonos -> out.device.modelName
                OutputTarget.LocalDevice -> "Lokale Wiedergabe (Bluetooth / Lautsprecher)"
            }
            val outputIcon = when (selectedOutput) {
                is OutputTarget.Sonos -> Icons.Default.Speaker
                OutputTarget.LocalDevice -> Icons.Default.BluetoothAudio
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                    .clickable(enabled = !isStreaming) { outputMenuExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = outputIcon,
                            contentDescription = null,
                            tint = colors.amberPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = outputTitle,
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = outputSubtitle,
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Ausgabe wählen",
                        tint = colors.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { viewModel.discoverSonos() },
                        enabled = !isDiscovering && !isStreaming,
                        modifier = Modifier.size(34.dp)
                    ) {
                        if (isDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.amberPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sonos neu suchen",
                                tint = colors.amberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = outputMenuExpanded,
                onDismissRequest = { outputMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Default.BluetoothAudio, contentDescription = null, tint = colors.amberPrimary)
                    },
                    text = {
                        Column {
                            Text("Handy / Bluetooth", fontWeight = FontWeight.SemiBold)
                            Text("Lokaler Lautsprecher oder Bluetooth-Box", fontSize = 11.sp, color = colors.textMuted)
                        }
                    },
                    onClick = {
                        viewModel.selectOutput(OutputTarget.LocalDevice)
                        outputMenuExpanded = false
                    }
                )
                sonosDevices.forEach { device ->
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Default.Speaker, contentDescription = null, tint = colors.amberPrimary)
                        },
                        text = {
                            Column {
                                Text(device.roomName, fontWeight = FontWeight.SemiBold)
                                Text(device.modelName, fontSize = 11.sp, color = colors.textMuted)
                            }
                        },
                        onClick = {
                            viewModel.selectOutput(OutputTarget.Sonos(device))
                            outputMenuExpanded = false
                        }
                    )
                }
            }
        }

        // Error Banner
        AnimatedVisibility(visible = lastError != null) {
            Surface(
                color = colors.warmRed.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.warmRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { viewModel.dismissError() }
            ) {
                Text(
                    text = lastError.orEmpty(),
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Animated Turntable Platter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            TurntableDisc(
                isSpinning = isStreaming && isAudioDetected,
                modifier = Modifier.fillMaxWidth(0.75f)
            )
        }

        // Playback Detection Status Card
        val statusDotColor = when {
            !isStreaming -> colors.textMuted
            isAudioDetected -> colors.emeraldActive
            else -> colors.amberPrimary
        }
        val statusTitle = when {
            !isStreaming -> "Bereit für Vinyl"
            isAudioDetected -> "Wiedergabe aktiv"
            else -> "Kein Audio-Signal (Stille)"
        }
        val dbText = "${peakDb.toInt()} dB"
        val linkText = when (selectedOutput) {
            OutputTarget.LocalDevice -> "Lokal aktiv"
            is OutputTarget.Sonos -> if (connectedClients > 0) "Sonos verbunden" else "Warte auf Sonos..."
        }
        val statusSubtitle = when {
            !isStreaming -> "Plattenspieler über USB-C Line-In"
            captureInfo.isNotBlank() -> "$dbText · $linkText · $captureInfo"
            else -> "$dbText · $linkText"
        }

        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isStreaming && isAudioDetected) Icons.Default.GraphicEq else Icons.Default.Album,
                        contentDescription = null,
                        tint = statusDotColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusTitle,
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusSubtitle,
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Stereo VU Meter
        VuMeter(
            levels = audioLevels,
            modifier = Modifier
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                .padding(12.dp)
        )

        // Primary Stream Toggle Button
        Button(
            onClick = { viewModel.toggleStream() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isStreaming) colors.warmRed else colors.amberPrimary,
                contentColor = if (isStreaming) Color.White else if (settings.darkMode) colors.background else Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(
                imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isStreaming) "Stream stoppen" else "Stream starten",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        // Volume Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Lautstärke",
                tint = colors.amberPrimary,
                modifier = Modifier.size(20.dp)
            )
            Slider(
                value = volume.toFloat(),
                onValueChange = { viewModel.setOutputVolume(it.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = colors.amberPrimary,
                    activeTrackColor = colors.amberPrimary,
                    inactiveTrackColor = colors.surfaceElevated
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = "$volume%",
                color = colors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.width(36.dp)
            )
        }
    }
}
