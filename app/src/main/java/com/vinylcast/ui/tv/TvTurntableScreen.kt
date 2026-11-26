package com.vinylcast.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vinylcast.audio.AudioOutputDeviceHelper
import com.vinylcast.model.AudioOutputDevice
import com.vinylcast.model.EqPreset
import com.vinylcast.model.EqSettings
import com.vinylcast.ui.components.TurntableDisc
import com.vinylcast.ui.components.VuMeter
import com.vinylcast.ui.theme.VinylTheme

@Composable
fun TvTurntableScreen(
    viewModel: TvViewModel,
    onExitToBackground: () -> Unit,
    onStopAndExit: () -> Unit
) {
    val colors = VinylTheme.colors
    val settings by viewModel.settings.collectAsState()
    val inputDevices by viewModel.inputDevices.collectAsState()
    val outputDevices by viewModel.outputDevices.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isAudioDetected by viewModel.isAudioDetected.collectAsState()
    val audioLevels by viewModel.audioLevels.collectAsState()
    val activeOutputLabel by viewModel.activeOutputLabel.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    val usbDevice = inputDevices.firstOrNull { it.isUsb }

    var dimMode by remember { mutableStateOf(false) }
    var showOutputDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val primaryFocusRequester = remember { FocusRequester() }
    val dimFocusRequester = remember { FocusRequester() }

    // Auto-start playback when TV app opens with USB adapter connected
    LaunchedEffect(Unit) {
        viewModel.tryAutoStart()
        runCatching { primaryFocusRequester.requestFocus() }
    }

    BackHandler {
        when {
            dimMode -> dimMode = false
            showOutputDialog -> showOutputDialog = false
            showEqDialog -> showEqDialog = false
            showSettingsDialog -> showSettingsDialog = false
            showExitDialog -> showExitDialog = false
            isStreaming -> showExitDialog = true
            else -> onStopAndExit()
        }
    }

    if (dimMode) {
        LaunchedEffect(Unit) {
            runCatching { dimFocusRequester.requestFocus() }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF060504))
                .focusRequester(dimFocusRequester)
                .focusable()
                .clickable { dimMode = false }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        dimMode = false
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.alpha(0.42f)
            ) {
                TurntableDisc(
                    isSpinning = isStreaming && isAudioDetected,
                    modifier = Modifier.size(340.dp)
                )
                Text(
                    text = if (isAudioDetected) "VinylCast · Wiedergabe aktiv" else "VinylCast · Bereit",
                    color = colors.textMuted,
                    fontSize = 14.sp
                )
                Text(
                    text = "Beliebige Taste zum Beenden des Dimm-Modus",
                    color = colors.textMuted.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
        return
    }

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

    val resolvedOutputName = remember(settings.preferredOutputDeviceId, outputDevices, activeOutputLabel) {
        val selected = settings.preferredOutputDeviceId?.let { id ->
            outputDevices.firstOrNull { it.id == id }
        }
        when {
            selected != null -> "${selected.typeLabel} (${selected.name})"
            activeOutputLabel.isNotBlank() -> "Auto · $activeOutputLabel"
            else -> {
                val autoBest = AudioOutputDeviceHelper.pickBestTvOutput(outputDevices)
                if (autoBest != null) "Auto · ${autoBest.typeLabel}" else "Auto · TOSLINK / TV-Audio"
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 42.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Large Animated Vinyl Turntable + Status Pill
        Column(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TurntableDisc(
                isSpinning = isStreaming && isAudioDetected,
                modifier = Modifier
                    .fillMaxWidth(0.88f)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = if (isStreaming && isAudioDetected) Icons.Default.GraphicEq else Icons.Default.Album,
                    contentDescription = null,
                    tint = statusDotColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusTitle,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Right Column: Controls & Status for 10-foot TV Remote UX
        Column(
            modifier = Modifier
                .weight(0.54f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            // Top Title + USB Line-In Badge
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
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "VinylCast TV",
                        color = colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (usbDevice != null) colors.emeraldActive else colors.amberPrimary)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (usbDevice != null) "USB Line-In bereit" else "Kein USB-Adapter",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Error Banner
            AnimatedVisibility(visible = lastError != null) {
                Surface(
                    color = colors.warmRed.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.warmRed.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .clickable { viewModel.dismissError() }
                ) {
                    Text(
                        text = lastError.orEmpty(),
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Stereo VU Meter
            VuMeter(levels = audioLevels)

            // Primary Start / Stop Button (Default Focus)
            TvPrimaryActionButton(
                label = if (isStreaming) "Wiedergabe stoppen" else "Wiedergabe starten",
                icon = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                containerColor = if (isStreaming) colors.warmRed else colors.amberPrimary,
                contentColor = if (isStreaming) Color.White else if (settings.darkMode) colors.background else Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(primaryFocusRequester),
                onClick = { viewModel.togglePlayback() }
            )

            // Audio Output Selector Card (TOSLINK / TV Audio)
            TvOptionCard(
                title = "Audio-Ausgang (TOSLINK)",
                subtitle = resolvedOutputName,
                icon = Icons.Default.Speaker,
                onClick = { showOutputDialog = true }
            )

            // Equalizer Card
            val eqSubtitle = if (settings.eq.preset == EqPreset.CUSTOM) {
                "Eigene (Bass ${formatDb(settings.eq.bassDb)} · Mid ${formatDb(settings.eq.midDb)} · Höhen ${formatDb(settings.eq.trebleDb)})"
            } else {
                settings.eq.preset.label
            }
            TvOptionCard(
                title = "Equalizer",
                subtitle = eqSubtitle,
                icon = Icons.Default.Tune,
                onClick = { showEqDialog = true }
            )

            // Quick Actions Row: Dim Mode, Theme, Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvSmallActionButton(
                    label = "Dimm-Modus",
                    icon = Icons.Default.Bedtime,
                    modifier = Modifier.weight(1f),
                    onClick = { dimMode = true }
                )
                TvSmallActionButton(
                    label = if (settings.darkMode) "Light Mode" else "Dark Mode",
                    icon = if (settings.darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.toggleDarkMode() }
                )
                TvSmallActionButton(
                    label = "Einstellungen",
                    icon = Icons.Default.Settings,
                    modifier = Modifier.weight(1f),
                    onClick = { showSettingsDialog = true }
                )
            }
        }
    }

    if (showOutputDialog) {
        TvOutputSelectionDialog(
            outputDevices = outputDevices,
            selectedDeviceId = settings.preferredOutputDeviceId,
            onSelect = { id ->
                viewModel.selectOutputDevice(id)
                showOutputDialog = false
            },
            onDismiss = { showOutputDialog = false }
        )
    }

    if (showEqDialog) {
        TvEqDialog(
            eq = settings.eq,
            onSelectPreset = { viewModel.selectEqPreset(it) },
            onUpdateBands = { b, m, t -> viewModel.updateEqBands(b, m, t) },
            onDismiss = { showEqDialog = false }
        )
    }

    if (showSettingsDialog) {
        TvSettingsDialog(
            gain = settings.gainMultiplier,
            keepScreenOn = settings.keepScreenOn,
            onGainChange = { viewModel.setGainMultiplier(it) },
            onToggleKeepScreenOn = { viewModel.toggleKeepScreenOn() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showExitDialog) {
        TvExitDialog(
            onContinueInBackground = {
                showExitDialog = false
                onExitToBackground()
            },
            onStopAndExit = {
                showExitDialog = false
                viewModel.stopPlayback()
                onStopAndExit()
            },
            onDismiss = { showExitDialog = false }
        )
    }
}

@Composable
private fun TvPrimaryActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = VinylTheme.colors
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) colors.textPrimary else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, color = contentColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TvOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val colors = VinylTheme.colors
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) colors.surfaceElevated else colors.surface)
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) colors.amberPrimary else colors.borderSubtle,
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (focused) colors.amberPrimary.copy(alpha = 0.2f) else colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.amberPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = colors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvSmallActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = VinylTheme.colors
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) colors.surfaceElevated else colors.surface)
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) colors.amberPrimary else colors.borderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.amberPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvDialogRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val colors = VinylTheme.colors
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) colors.surfaceElevated else colors.background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.amberPrimary else colors.borderSubtle,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, color = colors.textMuted, fontSize = 12.sp)
            }
        }
        if (selected) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = colors.amberPrimary)
        }
    }
}

@Composable
private fun TvOutputSelectionDialog(
    outputDevices: List<AudioOutputDevice>,
    selectedDeviceId: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = VinylTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "TV Audio-Ausgang wählen",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "USB-Kopfhörerausgänge (Cubilux) werden automatisch ausgeblendet, damit der Ton über TOSLINK an die Playbar geht.",
                color = colors.textMuted,
                fontSize = 12.sp
            )

            TvDialogRow(
                title = "Automatisch (TOSLINK bevorzugt)",
                subtitle = "Wählt optischen Ausgang oder TV-System-Audio",
                selected = selectedDeviceId == null,
                onClick = { onSelect(null) }
            )

            outputDevices.forEach { dev ->
                TvDialogRow(
                    title = dev.typeLabel,
                    subtitle = "${dev.name} (ID ${dev.id})",
                    selected = selectedDeviceId == dev.id,
                    onClick = { onSelect(dev.id) }
                )
            }
        }
    }
}

@Composable
private fun TvEqDialog(
    eq: EqSettings,
    onSelectPreset: (EqPreset) -> Unit,
    onUpdateBands: (Float, Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = VinylTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Equalizer",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Preset buttons (2 rows for clean D-Pad grid)
            val presets = EqPreset.entries.filter { it != EqPreset.CUSTOM }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.chunked(3).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            val selected = eq.preset == preset
                            var focused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.amberPrimary else colors.background)
                                    .border(
                                        width = if (focused) 2.5.dp else 1.dp,
                                        color = if (focused) colors.textPrimary else colors.borderSubtle,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .onFocusChanged { focused = it.isFocused }
                                    .focusable()
                                    .clickable { onSelectPreset(preset) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset.label,
                                    color = if (selected) colors.background else colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            TvStepControlRow(
                label = "Bass (120 Hz)",
                valueText = formatDb(eq.bassDb),
                onDecrease = { onUpdateBands((eq.bassDb - 1f).coerceAtLeast(-12f), eq.midDb, eq.trebleDb) },
                onIncrease = { onUpdateBands((eq.bassDb + 1f).coerceAtMost(12f), eq.midDb, eq.trebleDb) }
            )
            TvStepControlRow(
                label = "Mitten (1 kHz)",
                valueText = formatDb(eq.midDb),
                onDecrease = { onUpdateBands(eq.bassDb, (eq.midDb - 1f).coerceAtLeast(-12f), eq.trebleDb) },
                onIncrease = { onUpdateBands(eq.bassDb, (eq.midDb + 1f).coerceAtMost(12f), eq.trebleDb) }
            )
            TvStepControlRow(
                label = "Höhen (6.5 kHz)",
                valueText = formatDb(eq.trebleDb),
                onDecrease = { onUpdateBands(eq.bassDb, eq.midDb, (eq.trebleDb - 1f).coerceAtLeast(-12f)) },
                onIncrease = { onUpdateBands(eq.bassDb, eq.midDb, (eq.trebleDb + 1f).coerceAtMost(12f)) }
            )
        }
    }
}

@Composable
private fun TvSettingsDialog(
    gain: Float,
    keepScreenOn: Boolean,
    onGainChange: (Float) -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = VinylTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "TV-Einstellungen",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            TvStepControlRow(
                label = "Eingangs-Verstärkung (Gain)",
                valueText = "${gain.toInt()}x",
                onDecrease = { onGainChange((gain - 1f).coerceAtLeast(1f)) },
                onIncrease = { onGainChange((gain + 1f).coerceAtMost(16f)) }
            )

            TvDialogRow(
                title = "TV-Bildschirm aktiv lassen",
                subtitle = if (keepScreenOn) "Aktiv (verhindert TV-Bildschirmschoner)" else "Aus (TV darf abdunkeln, Ton läuft weiter)",
                selected = keepScreenOn,
                onClick = onToggleKeepScreenOn
            )
        }
    }
}

@Composable
private fun TvStepControlRow(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    val colors = VinylTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.background)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TvMiniStepButton(icon = Icons.Default.Remove, onClick = onDecrease)
            Text(
                text = valueText,
                color = colors.amberPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            TvMiniStepButton(icon = Icons.Default.Add, onClick = onIncrease)
        }
    }
}

@Composable
private fun TvMiniStepButton(icon: ImageVector, onClick: () -> Unit) {
    val colors = VinylTheme.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) colors.amberPrimary else colors.surfaceElevated)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.textPrimary else colors.borderSubtle,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (focused) colors.background else colors.textPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TvExitDialog(
    onContinueInBackground: () -> Unit,
    onStopAndExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = VinylTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "VinylCast TV beenden?",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Die Wiedergabe über TOSLINK läuft noch.",
                color = colors.textMuted,
                fontSize = 13.sp
            )
            TvDialogRow(
                title = "Im Hintergrund weiterlaufen lassen",
                subtitle = "App schließen, Vinyl-Ton spielt weiter",
                onClick = onContinueInBackground
            )
            TvDialogRow(
                title = "Wiedergabe stoppen & beenden",
                subtitle = "USB-Aufnahme und Ton beenden",
                onClick = onStopAndExit
            )
        }
    }
}

private fun formatDb(valueDb: Float): String {
    val sign = if (valueDb > 0.05f) "+" else ""
    return "$sign${"%.0f".format(valueDb)} dB"
}
