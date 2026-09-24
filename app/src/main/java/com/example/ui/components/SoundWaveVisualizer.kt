package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.loop.LoopState
import com.example.ui.theme.*
import kotlin.math.sin

/**
 * Animated SoundWaveVisualizer:
 * Hardware-accelerated Canvas equalizer bars responding dynamically
 * to live voice RMS audio levels and active engine loop state (Listening, Recording, Speaking, Processing, Idle).
 * Uses smooth vertical gradients and symmetric harmonic ripples.
 */
@Composable
fun SoundWaveVisualizer(
    state: LoopState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    maxBarHeight: Dp = 50.dp,
    minBarHeight: Dp = 6.dp,
    barWidth: Dp = 4.5.dp,
    barSpacing: Dp = 3.5.dp
) {
    val (primaryColor, secondaryColor, accentTipColor) = when (state) {
        is LoopState.Listening -> Triple(EmeraldVoiceLight, EmeraldVoice, EmeraldVoice.copy(alpha = 0.4f))
        is LoopState.Recording -> Triple(CrimsonRecordLight, CrimsonRecord, AmberGold)
        is LoopState.Speaking -> Triple(SkyBlueLight, SkyBlue, EmeraldVoiceLight)
        is LoopState.Processing -> Triple(AmberGoldLight, AmberGold, GoldenSun)
        is LoopState.Idle -> Triple(AmberGold.copy(alpha = 0.5f), AmberGold.copy(alpha = 0.3f), AmberGold.copy(alpha = 0.1f))
        is LoopState.Error -> Triple(CrimsonRecord, CrimsonRecord.copy(alpha = 0.6f), CrimsonRecord.copy(alpha = 0.2f))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val isActive = state is LoopState.Listening || state is LoopState.Recording ||
            state is LoopState.Speaking || state is LoopState.Processing

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(maxBarHeight + 12.dp)
    ) {
        val totalBarsWidth = (barCount * barWidth.toPx()) + ((barCount - 1) * barSpacing.toPx())
        val startX = (size.width - totalBarsWidth) / 2f
        val centerY = size.height / 2f
        val maxH = maxBarHeight.toPx()
        val minH = minBarHeight.toPx()
        val bWidth = barWidth.toPx()
        val bSpacing = barSpacing.toPx()

        for (i in 0 until barCount) {
            val normalizedIdx = i.toFloat() / barCount
            val centerDist = kotlin.math.abs(0.5f - normalizedIdx) * 2f // 0 at center, 1 at edges
            val bellCurve = (1f - (centerDist * 0.45f)).coerceIn(0.4f, 1.0f)

            val wave = if (isActive) {
                val harmonic1 = (sin(phase + (normalizedIdx * Math.PI * 3)).toFloat() + 1f) / 2f
                val harmonic2 = (sin((phase * 1.5) - (normalizedIdx * Math.PI * 2)).toFloat() + 1f) / 2f
                val base = 0.2f + (harmonic1 * 0.35f) + (harmonic2 * 0.15f)
                val reactive = audioLevel.coerceIn(0f, 1f) * 0.85f * bellCurve
                (base + reactive).coerceIn(0.12f, 1.0f)
            } else {
                0.15f * bellCurve
            }

            val currentHeight = (minH + (maxH - minH) * wave).coerceAtMost(maxH)
            val x = startX + i * (bWidth + bSpacing)
            val y = centerY - (currentHeight / 2f)

            val brush = Brush.verticalGradient(
                colors = listOf(accentTipColor, primaryColor, secondaryColor, accentTipColor),
                startY = y,
                endY = y + currentHeight
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(bWidth, currentHeight),
                cornerRadius = CornerRadius(bWidth / 2f, bWidth / 2f)
            )
        }
    }
}
