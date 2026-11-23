package com.vinylcast.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.vinylcast.model.RecognitionState
import com.vinylcast.ui.components.TurntableDisc
import com.vinylcast.ui.components.VuMeter
import com.vinylcast.ui.theme.VinylColors

@Composable
fun TurntableScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val inputDevices by viewModel.inputDevices.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val audioLevels by viewModel.audioLevels.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val sonosDevices by viewModel.sonosDevices.collectAsState()
    val selectedSonos by viewModel.selectedSonos.collectAsState()
    val isDiscovering by viewModel.isDiscoveringSonos.collectAsState()
    val volume by viewModel.sonosVolume.collectAsState()
    val recognitionState by viewModel.recognitionState.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    val usbDevice = inputDevices.firstOrNull { it.isUsb }
    var sonosMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylColors.Background)
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
                    tint = VinylColors.AmberPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VinylCast",
                    color = VinylColors.IvoryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.toggleKeepScreenOn() }) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = "Display aktiv lassen",
                        tint = if (settings.keepScreenOn) VinylColors.AmberPrimary else VinylColors.MutedText
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Einstellungen",
                        tint = VinylColors.IvoryText
                    )
                }
            }
        }

        // Status Pills: USB Line-In + Sonos Speaker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // USB Status Pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VinylColors.Surface)
                    .border(1.dp, VinylColors.BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (usbDevice != null) VinylColors.EmeraldActive else VinylColors.AmberPrimary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = VinylColors.MutedText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = usbDevice?.name ?: "Kein USB-Adapter",
                    color = VinylColors.IvoryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Sonos Picker Pill
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VinylColors.Surface)
                        .border(1.dp, VinylColors.BorderSubtle, RoundedCornerShape(10.dp))
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
                            tint = if (selectedSonos != null) VinylColors.AmberPrimary else VinylColors.MutedText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedSonos?.roomName ?: if (isDiscovering) "Suche Sonos..." else "Sonos wählen",
                            color = VinylColors.IvoryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = VinylColors.AmberPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sonos neu suchen",
                            tint = VinylColors.MutedText,
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

        // Error / Status Banner
        val errorBanner = lastError ?: (recognitionState as? RecognitionState.Error)?.message
        AnimatedVisibility(visible = errorBanner != null) {
            Surface(
                color = VinylColors.WarmRed.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VinylColors.WarmRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { viewModel.dismissError() }
            ) {
                Text(
                    text = errorBanner.orEmpty(),
                    color = VinylColors.IvoryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Animated Turntable Platter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            TurntableDisc(
                isSpinning = isStreaming,
                coverUrl = currentTrack?.coverUrl,
                modifier = Modifier.fillMaxWidth(0.78f)
            )
        }

        // Now Playing / Recognized Track Card
        Surface(
            color = VinylColors.Surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VinylColors.BorderSubtle, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VinylColors.SurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    val coverUrl = currentTrack?.coverUrl
                    if (!coverUrl.isNullOrBlank()) {
                        Image(
                            painter = rememberAsyncImagePainter(model = coverUrl),
                            contentDescription = "Album Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = VinylColors.AmberPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack?.title ?: "Bereit für Vinyl",
                        color = VinylColors.IvoryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentTrack?.artist ?: if (isStreaming) "Live-Stream läuft – tippe auf Song erkennen" else "Plattenspieler über USB-C Line-In",
                        color = VinylColors.MutedText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!currentTrack?.album.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val yearSuffix = currentTrack?.releaseYear?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        Text(
                            text = "${currentTrack?.album}$yearSuffix",
                            color = VinylColors.AmberPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Stereo VU Meter
        VuMeter(
            levels = audioLevels,
            modifier = Modifier
                .border(1.dp, VinylColors.BorderSubtle, RoundedCornerShape(12.dp))
                .padding(12.dp)
        )

        // Primary Controls: Stream Toggle & Recognize Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.toggleStream() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) VinylColors.WarmRed else VinylColors.AmberPrimary,
                    contentColor = if (isStreaming) VinylColors.IvoryText else VinylColors.Background
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isStreaming) "Stoppen" else "Streamen",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            OutlinedButton(
                onClick = { viewModel.recognizeCurrentSong() },
                enabled = isStreaming && recognitionState !is RecognitionState.Recognizing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                if (recognitionState is RecognitionState.Recognizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = VinylColors.AmberPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Erkennt...", color = VinylColors.IvoryText, fontSize = 14.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (isStreaming) VinylColors.AmberPrimary else VinylColors.MutedText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Song erkennen",
                        color = if (isStreaming) VinylColors.IvoryText else VinylColors.MutedText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Sonos Volume Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VinylColors.Surface)
                .border(1.dp, VinylColors.BorderSubtle, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Lautstärke",
                tint = VinylColors.AmberPrimary,
                modifier = Modifier.size(20.dp)
            )
            Slider(
                value = volume.toFloat(),
                onValueChange = { viewModel.setSonosVolume(it.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = VinylColors.AmberPrimary,
                    activeTrackColor = VinylColors.AmberPrimary,
                    inactiveTrackColor = VinylColors.SurfaceElevated
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = "$volume%",
                color = VinylColors.MutedText,
                fontSize = 12.sp,
                modifier = Modifier.width(36.dp)
            )
        }
    }
}
