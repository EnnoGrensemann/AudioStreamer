package com.vinylcast.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinylcast.model.AudioLevels
import com.vinylcast.ui.theme.VinylColors
import kotlin.math.log10

@Composable
fun VuMeter(
    levels: AudioLevels,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VinylColors.Surface)
            .let { it },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        VuChannelRow(label = "L", rms = levels.leftRms)
        VuChannelRow(label = "R", rms = levels.rightRms)
    }
}

@Composable
private fun VuChannelRow(label: String, rms: Float) {
    // Convert linear RMS to perceptual 0..1 scale (-54 dB to 0 dB)
    val db = if (rms > 0.0001f) 20f * log10(rms) else -60f
    val normalized = ((db + 54f) / 54f).coerceIn(0f, 1f)
    val animatedLevel by animateFloatAsState(
        targetValue = normalized,
        animationSpec = tween(durationMillis = 80),
        label = "vu_$label"
    )

    val segments = 24
    val litCount = (animatedLevel * segments).toInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = VinylColors.MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(14.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (i in 0 until segments) {
                val active = i < litCount
                val color = when {
                    !active -> VinylColors.SurfaceElevated
                    i >= segments - 4 -> VinylColors.WarmRed
                    i >= segments - 9 -> VinylColors.AmberPrimary
                    else -> VinylColors.EmeraldActive
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
    }
}
