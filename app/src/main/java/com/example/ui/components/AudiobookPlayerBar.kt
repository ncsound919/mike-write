package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.loop.LoopState
import com.example.ui.theme.*

/**
 * AudiobookPlayerBar:
 * Floating bottom interactive player bar that emerges whenever Mike Write is reading aloud,
 * offering immediate pause, stop, speed adjustments, and visual reading equalizer.
 */
@Composable
fun AudiobookPlayerBar(
    loopState: LoopState,
    speechRate: Float,
    onStopPlayback: () -> Unit,
    onToggleSpeed: () -> Unit,
    onRepeatPlayback: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isSpeaking = loopState is LoopState.Speaking

    AnimatedVisibility(
        visible = isSpeaking,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        val spokenText = if (loopState is LoopState.Speaking) loopState.text else ""

        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = DarkNavySurface,
            border = BorderStroke(1.5.dp, SkyBlue),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        Surface(
                            shape = CircleShape,
                            color = SkyBlue.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, SkyBlue),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Reading Aloud",
                                    tint = SkyBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "READING ALOUD",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = SkyBlue,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.2.sp
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = EmeraldDark
                                ) {
                                    Text(
                                        text = "LIVE AUDIOBOOK",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldVoice,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = spokenText.ifBlank { "Reading manuscript aloud..." },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OffWhiteText,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Replay/Repeat button if provided
                        if (onRepeatPlayback != null) {
                            IconButton(
                                onClick = onRepeatPlayback,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .testTag("player_repeat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Repeat Passage",
                                    tint = SkyBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Speed Chip Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MidnightCard,
                            border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleSpeed() }
                                .testTag("player_speed_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = AmberGold,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1fx", speechRate),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberGold
                                )
                            }
                        }

                        // Stop Playback Button (Large high-contrast touch target)
                        Button(
                            onClick = onStopPlayback,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonDark,
                                contentColor = CrimsonRecord
                            ),
                            border = BorderStroke(1.dp, CrimsonRecord),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("player_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Reading",
                                tint = CrimsonRecord,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
