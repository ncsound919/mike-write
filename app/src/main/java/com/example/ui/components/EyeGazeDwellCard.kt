package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * EyeGazeDwellCard:
 * A high-accessibility dwell target for paralyzed authors using eye-tracking hardware,
 * head-mouse pointing devices, or stylus hover.
 *
 * When focused or hovered for [dwellDurationMs] (default 1800ms),
 * it animates an interactive circular countdown progress ring and automatically triggers [onDwellTriggered]
 * without requiring any physical click or press.
 */
@Composable
fun EyeGazeDwellCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color = AmberGold,
    dwellDurationMs: Long = 1800L,
    modifier: Modifier = Modifier,
    testTag: String = "dwell_card",
    onDwellTriggered: () -> Unit
) {
    var isDwellActive by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dwellJob by remember { mutableStateOf<Job?>(null) }

    fun startDwell() {
        if (isDwellActive) return
        isDwellActive = true
        dwellJob?.cancel()
        dwellJob = scope.launch {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = dwellDurationMs.toInt(), easing = LinearEasing)
            )
            // Trigger upon completion if coroutine is still active
            if (isActive) {
                onDwellTriggered()
            }
            delay(300)
            if (isActive) {
                isDwellActive = false
                progress.snapTo(0f)
            }
        }
    }

    fun cancelDwell() {
        dwellJob?.cancel()
        dwellJob = null
        isDwellActive = false
        scope.launch {
            progress.animateTo(0f, animationSpec = tween(150))
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isDwellActive) MidnightCard else DarkNavySurface,
        border = BorderStroke(
            width = if (isDwellActive) 2.5.dp else 1.dp,
            color = if (isDwellActive) accentColor else BorderSubtle
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // If user clicks, either manual or simulates dwell click
                if (!isDwellActive) {
                    startDwell()
                } else {
                    cancelDwell()
                }
            }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OffWhiteText,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = LightGrayMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Dwell Circular Progress Indicator
                Box(
                    modifier = Modifier
                        .size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { if (isDwellActive) progress.value else 0f },
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        strokeWidth = 3.5.dp,
                        trackColor = BorderSubtle.copy(alpha = 0.4f)
                    )
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Gaze Target",
                        tint = if (isDwellActive) accentColor else LightGrayMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isDwellActive) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = accentColor,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
