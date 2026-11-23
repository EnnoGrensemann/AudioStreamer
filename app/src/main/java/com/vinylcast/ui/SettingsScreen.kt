package com.vinylcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinylcast.ui.theme.VinylTheme

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val colors = VinylTheme.colors
    val settings by viewModel.settings.collectAsState()
    val inputDevices by viewModel.inputDevices.collectAsState()

    var portText by remember(settings.streamPort) { mutableStateOf(settings.streamPort.toString()) }
    var savedFeedback by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    tint = colors.textPrimary
                )
            }
            Text(
                text = "Einstellungen",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Appearance Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Darstellung & Display",
                color = colors.amberPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleDarkMode() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Light Mode",
                        color = colors.textPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Helles Design statt dunklem Hi-Fi-Theme",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = !settings.darkMode,
                    onCheckedChange = { viewModel.toggleDarkMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.amberPrimary
                    )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleKeepScreenOn() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Display aktiv lassen",
                        color = colors.textPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Verhindert automatisches Ausschalten",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = settings.keepScreenOn,
                    onCheckedChange = { viewModel.toggleKeepScreenOn() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.amberPrimary
                    )
                )
            }
        }

        // Stream Port Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Netzwerk-Stream",
                color = colors.amberPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit); savedFeedback = false },
                label = { Text("Lokaler HTTP-Stream-Port (Standard: 8765)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    portText.toIntOrNull()?.let { viewModel.saveStreamPort(it) }
                    savedFeedback = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.amberPrimary,
                    contentColor = if (settings.darkMode) colors.background else Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (savedFeedback) "Gespeichert" else "Port speichern",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Audio Input Device Selection
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Audio-Eingang (Line-In)",
                color = colors.amberPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectPreferredInputDevice(null) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.preferredInputDeviceId == null,
                    onClick = { viewModel.selectPreferredInputDevice(null) },
                    colors = RadioButtonDefaults.colors(selectedColor = colors.amberPrimary)
                )
                Text(
                    text = "Automatisch (USB-C Adapter bevorzugt)",
                    color = colors.textPrimary,
                    fontSize = 14.sp
                )
            }

            inputDevices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectPreferredInputDevice(device.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.preferredInputDeviceId == device.id,
                        onClick = { viewModel.selectPreferredInputDevice(device.id) },
                        colors = RadioButtonDefaults.colors(selectedColor = colors.amberPrimary)
                    )
                    Column {
                        Text(
                            text = device.name,
                            color = colors.textPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${device.typeLabel} (ID ${device.id})",
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
