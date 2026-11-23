package com.vinylcast.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VinylColors {
    val Background = Color(0xFF12100E)
    val Surface = Color(0xFF1C1815)
    val SurfaceElevated = Color(0xFF26201B)
    val BorderSubtle = Color(0xFF383029)
    val AmberPrimary = Color(0xFFD49A45)
    val AmberGlow = Color(0xFFE8B968)
    val IvoryText = Color(0xFFF4EFE6)
    val MutedText = Color(0xFFA89F94)
    val EmeraldActive = Color(0xFF52B788)
    val WarmRed = Color(0xFFD65A4F)
    val VinylBlack = Color(0xFF0C0B0A)
    val VinylGroove = Color(0xFF211E1B)
}

private val DarkHiFiColorScheme = darkColorScheme(
    primary = VinylColors.AmberPrimary,
    onPrimary = Color(0xFF1A1208),
    secondary = VinylColors.AmberGlow,
    onSecondary = Color(0xFF1A1208),
    background = VinylColors.Background,
    onBackground = VinylColors.IvoryText,
    surface = VinylColors.Surface,
    onSurface = VinylColors.IvoryText,
    surfaceVariant = VinylColors.SurfaceElevated,
    onSurfaceVariant = VinylColors.MutedText,
    outline = VinylColors.BorderSubtle,
    error = VinylColors.WarmRed
)

@Composable
fun VinylCastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkHiFiColorScheme,
        content = content
    )
}
