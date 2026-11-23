package com.vinylcast.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VinylPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val borderSubtle: Color,
    val amberPrimary: Color,
    val amberGlow: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val emeraldActive: Color,
    val warmRed: Color,
    val vinylBlack: Color,
    val vinylGroove: Color,
    val platterOuter: Color,
    val platterInner: Color,
    val tonearmMetal: Color
)

private val DarkPalette = VinylPalette(
    background = Color(0xFF12100E),
    surface = Color(0xFF1C1815),
    surfaceElevated = Color(0xFF26201B),
    borderSubtle = Color(0xFF383029),
    amberPrimary = Color(0xFFD49A45),
    amberGlow = Color(0xFFE8B968),
    textPrimary = Color(0xFFF4EFE6),
    textMuted = Color(0xFFA89F94),
    emeraldActive = Color(0xFF52B788),
    warmRed = Color(0xFFD65A4F),
    vinylBlack = Color(0xFF0C0B0A),
    vinylGroove = Color(0xFF211E1B),
    platterOuter = Color(0xFF473E35),
    platterInner = Color(0xFF2D2722),
    tonearmMetal = Color(0xFFC2B7A8)
)

private val LightPalette = VinylPalette(
    background = Color(0xFFF5F0E6),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEAE3D5),
    borderSubtle = Color(0xFFD7CEC0),
    amberPrimary = Color(0xFFB87A1E),
    amberGlow = Color(0xFFD49A45),
    textPrimary = Color(0xFF1E1914),
    textMuted = Color(0xFF6E655A),
    emeraldActive = Color(0xFF2D8A61),
    warmRed = Color(0xFFC4453B),
    vinylBlack = Color(0xFF161412),
    vinylGroove = Color(0xFF2B2723),
    platterOuter = Color(0xFFB8AFA1),
    platterInner = Color(0xFF8C8274),
    tonearmMetal = Color(0xFF7D7468)
)

val LocalVinylColors = staticCompositionLocalOf { DarkPalette }

object VinylTheme {
    val colors: VinylPalette
        @Composable get() = LocalVinylColors.current
}

@Composable
fun VinylCastTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.amberPrimary,
            onPrimary = Color(0xFF1A1208),
            secondary = palette.amberGlow,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textMuted,
            outline = palette.borderSubtle,
            error = palette.warmRed
        )
    } else {
        lightColorScheme(
            primary = palette.amberPrimary,
            onPrimary = Color.White,
            secondary = palette.amberGlow,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textMuted,
            outline = palette.borderSubtle,
            error = palette.warmRed
        )
    }

    CompositionLocalProvider(LocalVinylColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
