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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinylcast.ui.theme.VinylColors

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val inputDevices by viewModel.inputDevices.collectAsState()

    var host by remember(settings.acrHost) { mutableStateOf(settings.acrHost) }
    var accessKey by remember(settings.acrAccessKey) { mutableStateOf(settings.acrAccessKey) }
    var accessSecret by remember(settings.acrAccessSecret) { mutableStateOf(settings.acrAccessSecret) }
    var portText by remember(settings.streamPort) { mutableStateOf(settings.streamPort.toString()) }
    var savedFeedback by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VinylColors.Background)
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
                    tint = VinylColors.IvoryText
                )
            }
            Text(
                text = "Einstellungen",
                color = VinylColors.IvoryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ACRCloud Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(VinylColors.Surface)
                .border(1.dp, VinylColors.BorderSubtle, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ACRCloud Song-Erkennung",
                color = VinylColors.AmberPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Kostenlosen Account auf console.acrcloud.com anlegen (Audio & Video Recognition) und die Zugangsdaten hier eintragen.",
                color = VinylColors.MutedText,
                fontSize = 12.sp
            )

            OutlinedTextField(
                value = host,
                onValueChange = { host = it; savedFeedback = false },
                label = { Text("ACR Host (z.B. identify-eu-west-1.acrcloud.com)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = accessKey,
                onValueChange = { accessKey = it; savedFeedback = false },
                label = { Text("Access Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = accessSecret,
                onValueChange = { accessSecret = it; savedFeedback = false },
                label = { Text("Access Secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
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
                    viewModel.saveAcrCredentials(host, accessKey, accessSecret)
                    portText.toIntOrNull()?.let { viewModel.saveStreamPort(it) }
                    savedFeedback = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VinylColors.AmberPrimary,
                    contentColor = VinylColors.Background
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (savedFeedback) "Gespeichert" else "Speichern",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Audio Input Device Selection
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(VinylColors.Surface)
                .border(1.dp, VinylColors.BorderSubtle, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Audio-Eingang (Line-In)",
                color = VinylColors.AmberPrimary,
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
                    colors = RadioButtonDefaults.colors(selectedColor = VinylColors.AmberPrimary)
                )
                Text(
                    text = "Automatisch (USB-C Adapter bevorzugt)",
                    color = VinylColors.IvoryText,
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
                        colors = RadioButtonDefaults.colors(selectedColor = VinylColors.AmberPrimary)
                    )
                    Column {
                        Text(
                            text = device.name,
                            color = VinylColors.IvoryText,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${device.typeLabel} (ID ${device.id})",
                            color = VinylColors.MutedText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
