package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.loop.LoopState
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonRecord
import com.example.ui.theme.EmeraldVoice
import com.example.ui.theme.SkyBlue
import kotlin.math.sin

/**
 * Animated SoundWaveVisualizer:
 * Renders interactive audio equalizer bars that respond dynamically to live voice RMS audio levels
 * and the active engine loop state (Listening, Recording, Speaking, Processing, Idle).
 */
@Composable
fun SoundWaveVisualizer(
    state: LoopState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 18,
    maxBarHeight: Dp = 48.dp,
    minBarHeight: Dp = 8.dp,
    barWidth: Dp = 4.dp,
    barSpacing: Dp = 3.dp
) {
    val activeColor = when (state) {
        is LoopState.Listening -> EmeraldVoice
        is LoopState.Recording -> CrimsonRecord
        is LoopState.Speaking -> SkyBlue
        is LoopState.Processing -> AmberGold
        is LoopState.Idle -> AmberGold.copy(alpha = 0.4f)
        is LoopState.Error -> CrimsonRecord
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

    val isActive = state is LoopState.Listening || state is LoopState.Recording || state is LoopState.Speaking || state is LoopState.Processing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(maxBarHeight + 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val normalizedIdx = i.toFloat() / barCount
            val wave = if (isActive) {
                val sineFactor = (sin(phase + (normalizedIdx * Math.PI * 3)).toFloat() + 1f) / 2f
                val base = 0.2f + (sineFactor * 0.4f)
                val reactive = audioLevel.coerceIn(0f, 1f) * 0.7f
                (base + reactive).coerceIn(0.1f, 1.0f)
            } else {
                0.15f
            }

            val currentHeight = minBarHeight + (maxBarHeight - minBarHeight) * wave

            Box(
                modifier = Modifier
                    .padding(horizontal = barSpacing / 2)
                    .width(barWidth)
                    .height(currentHeight)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(activeColor)
            )
        }
    }
}
