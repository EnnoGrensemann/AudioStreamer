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
import androidx.compose.material.icons.filled.DarkMode
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
    val sonosDevices by viewModel.sonosDevices.collectAsState()
    val selectedSonos by viewModel.selectedSonos.collectAsState()
    val isDiscovering by viewModel.isDiscoveringSonos.collectAsState()
    val volume by viewModel.sonosVolume.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    val usbDevice = inputDevices.firstOrNull { it.isUsb }
    var sonosMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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

        // Status Pills: USB Line-In + Sonos Speaker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (usbDevice != null) colors.emeraldActive else colors.amberPrimary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = usbDevice?.name ?: "Kein USB-Adapter",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                        .clickable { sonosMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = null,
                            tint = if (selectedSonos != null) colors.amberPrimary else colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedSonos?.roomName ?: if (isDiscovering) "Suche Sonos..." else "Sonos wählen",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.amberPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sonos neu suchen",
                            tint = colors.textMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.discoverSonos() }
                        )
                    }
                }

                DropdownMenu(
                    expanded = sonosMenuExpanded,
                    onDismissRequest = { sonosMenuExpanded = false }
                ) {
                    if (sonosDevices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Keine Sonos-Geräte gefunden – neu suchen") },
                            onClick = {
                                sonosMenuExpanded = false
                                viewModel.discoverSonos()
                            }
                        )
                    } else {
                        sonosDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text("${device.roomName} (${device.modelName})") },
                                onClick = {
                                    viewModel.selectSonos(device)
                                    sonosMenuExpanded = false
                                }
                            )
                        }
                    }
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

        // Animated Turntable Platter (spins when streaming and audio signal is detected)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            TurntableDisc(
                isSpinning = isStreaming && isAudioDetected,
                modifier = Modifier.fillMaxWidth(0.78f)
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
        val statusSubtitle = when {
            !isStreaming -> "Plattenspieler über USB-C Line-In"
            isAudioDetected -> "Musik wird erkannt und an Sonos gestreamt"
            else -> "Stream aktiv – warte auf Signal vom Plattenspieler"
        }

        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isStreaming && isAudioDetected) Icons.Default.GraphicEq else Icons.Default.Album,
                        contentDescription = null,
                        tint = statusDotColor,
                        modifier = Modifier.size(26.dp)
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = statusSubtitle,
                        color = colors.textMuted,
                        fontSize = 13.sp,
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

        // Sonos Volume Control
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
                onValueChange = { viewModel.setSonosVolume(it.toInt()) },
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
