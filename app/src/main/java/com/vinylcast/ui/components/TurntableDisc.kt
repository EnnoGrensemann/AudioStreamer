package com.vinylcast.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.vinylcast.ui.theme.VinylTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TurntableDisc(
    isSpinning: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = VinylTheme.colors
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isSpinning) {
        if (!isSpinning) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dtSec = (frameNanos - lastNanos) / 1_000_000_000f
                    rotationAngle = (rotationAngle + dtSec * 75f) % 360f
                }
                lastNanos = frameNanos
            }
        }
    }

    val tonearmAngle by animateFloatAsState(
        targetValue = if (isSpinning) 24f else 2f,
        animationSpec = tween(durationMillis = 650, easing = LinearEasing),
        label = "tonearmAngle"
    )

    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .rotate(rotationAngle)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f

            // Metallic platter rim
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.platterInner, colors.platterOuter, colors.platterInner),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            // Strobe dots on platter edge
            val dotRadius = outerRadius * 0.965f
            for (i in 0 until 36) {
                val rad = Math.toRadians(i * 10.0)
                val x = center.x + (dotRadius * cos(rad)).toFloat()
                val y = center.y + (dotRadius * sin(rad)).toFloat()
                drawCircle(
                    color = colors.amberPrimary.copy(alpha = 0.5f),
                    radius = 2.2f,
                    center = Offset(x, y)
                )
            }

            // Black vinyl disc
            val vinylRadius = outerRadius * 0.92f
            drawCircle(
                color = colors.vinylBlack,
                radius = vinylRadius,
                center = center
            )

            // Subtle light sheen across vinyl
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = center
                ),
                radius = vinylRadius,
                center = center
            )

            // Concentric grooves
            val grooveSteps = 14
            for (i in 1..grooveSteps) {
                val r = vinylRadius * (0.42f + (i.toFloat() / grooveSteps) * 0.54f)
                drawCircle(
                    color = colors.vinylGroove,
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.2f)
                )
            }

            // Center label
            drawCircle(
                color = colors.amberPrimary,
                radius = vinylRadius * 0.34f,
                center = center
            )
            // Inner label ring
            drawCircle(
                color = colors.background.copy(alpha = 0.25f),
                radius = vinylRadius * 0.28f,
                center = center,
                style = Stroke(width = 1.5f)
            )
        }

        // Center spindle hole
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(colors.background)
        )

        // Tonearm overlay (top-right pivot)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pivot = Offset(size.width * 0.87f, size.height * 0.14f)
            drawCircle(
                color = colors.platterOuter,
                radius = size.minDimension * 0.055f,
                center = pivot
            )
            drawCircle(
                color = colors.amberPrimary,
                radius = size.minDimension * 0.025f,
                center = pivot
            )

            rotate(degrees = tonearmAngle, pivot = pivot) {
                val armEnd = Offset(pivot.x - size.width * 0.09f, pivot.y + size.height * 0.44f)
                drawLine(
                    color = Color.Black.copy(alpha = 0.35f),
                    start = pivot + Offset(4f, 6f),
                    end = armEnd + Offset(4f, 6f),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = colors.tonearmMetal,
                    start = pivot,
                    end = armEnd,
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
                val headEnd = Offset(armEnd.x - size.width * 0.05f, armEnd.y + size.height * 0.06f)
                drawLine(
                    color = Color(0xFF28221D),
                    start = armEnd,
                    end = headEnd,
                    strokeWidth = 16f,
                    cap = StrokeCap.Butt
                )
                if (isSpinning) {
                    drawCircle(
                        color = colors.amberGlow,
                        radius = 4.5f,
                        center = headEnd
                    )
                }
            }
        }
    }
}
