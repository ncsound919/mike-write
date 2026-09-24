package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autonomous.UnifiedAutomationPipeline
import com.example.loop.VoiceLoopController
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * UnifiedAutomationBar:
 * A dedicated, senior-friendly "Just Works" automation monitor card displayed prominently in BuddyScreen.
 *
 * Shows the live status of the autonomous background writing pipeline:
 * - Smart Auto-Save status
 * - Background Timeline & Gap Auditing
 * - One-tap unified execution button
 * - Live pipeline insights (last auto-created chapter, sequence stability, gap suggestions)
 */
@Composable
fun UnifiedAutomationBar(
    controller: VoiceLoopController,
    fontScale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val lastExecution by UnifiedAutomationPipeline.lastExecution.collectAsState()
    val isAutomating by UnifiedAutomationPipeline.isAutomating.collectAsState()

    var autoSaveEnabled by remember { mutableStateOf(controller.settings.smartAutoSave) }
    var pipelineEnabled by remember { mutableStateOf(controller.settings.autoEditorialPipeline) }
    var expandedDetails by remember { mutableStateOf(false) }

    val statusBorderColor by animateColorAsState(
        targetValue = if (isAutomating) EmeraldVoice else AmberGold.copy(alpha = 0.5f),
        animationSpec = tween(400),
        label = "statusBorder"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .testTag("unified_automation_bar"),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(1.2.dp, statusBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header Row: Status badge & Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isAutomating) EmeraldDark else AmberGold.copy(alpha = 0.2f))
                            .border(1.dp, if (isAutomating) EmeraldVoice else AmberGold, CircleShape)
                    ) {
                        if (isAutomating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = EmeraldVoice,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Unified Automation",
                                tint = AmberGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "JUST WORKS PIPELINE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    color = AmberGold
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (pipelineEnabled) EmeraldDark else DarkNavySurface,
                                border = BorderStroke(0.5.dp, if (pipelineEnabled) EmeraldVoice else LightGrayMuted)
                            ) {
                                Text(
                                    text = if (isAutomating) "ACTIVE" else if (pipelineEnabled) "UNIFIED ON" else "PAUSED",
                                    color = if (pipelineEnabled) EmeraldVoice else LightGrayMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isAutomating) "Autonomous editor analyzing & weaving..." else "Auto formats, weaves timeline & audits gaps",
                            style = MaterialTheme.typography.bodySmall.copy(color = OffWhiteText),
                            fontSize = (12 * fontScale).sp
                        )
                    }
                }

                // Quick Action Button: Run Now
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            controller.runUnifiedPipeline()
                        }
                    },
                    enabled = !isAutomating,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AmberGold,
                        contentColor = DeepNavy
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("run_unified_automation_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Run Automation",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Run",
                        fontWeight = FontWeight.Bold,
                        fontSize = (12 * fontScale).sp
                    )
                }
            }

            // Quick Automation Toggles Row
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Smart Auto Save Chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (autoSaveEnabled) AmberGold.copy(alpha = 0.15f) else DarkNavySurface,
                    border = BorderStroke(1.dp, if (autoSaveEnabled) AmberGold else BorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val next = !autoSaveEnabled
                            autoSaveEnabled = next
                            controller.settings.smartAutoSave = next
                        }
                        .testTag("toggle_smart_autosave")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (autoSaveEnabled) Icons.Default.CheckCircle else Icons.Default.Save,
                            contentDescription = null,
                            tint = if (autoSaveEnabled) AmberGold else LightGrayMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (autoSaveEnabled) "Auto-Save: On" else "Auto-Save: Off",
                            fontSize = (11 * fontScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (autoSaveEnabled) OffWhiteText else LightGrayMuted
                        )
                    }
                }

                // Unified Pipeline Chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (pipelineEnabled) AmberGold.copy(alpha = 0.15f) else DarkNavySurface,
                    border = BorderStroke(1.dp, if (pipelineEnabled) AmberGold else BorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val next = !pipelineEnabled
                            pipelineEnabled = next
                            controller.settings.autoEditorialPipeline = next
                        }
                        .testTag("toggle_unified_pipeline")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (pipelineEnabled) Icons.Default.CheckCircle else Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (pipelineEnabled) AmberGold else LightGrayMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (pipelineEnabled) "Editorial: On" else "Editorial: Off",
                            fontSize = (11 * fontScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (pipelineEnabled) OffWhiteText else LightGrayMuted
                        )
                    }
                }

                // Expand details toggle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkNavySurface,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { expandedDetails = !expandedDetails }
                        .testTag("toggle_pipeline_details")
                ) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Icon(
                            imageVector = if (expandedDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand automation details",
                            tint = AmberGoldLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expandable details block
            AnimatedVisibility(visible = expandedDetails || lastExecution != null) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)
                    Spacer(Modifier.height(8.dp))

                    val exec = lastExecution
                    if (exec != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Last Autonomous Action: ${exec.passageTitle}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = AmberGoldLight,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                fontSize = (11 * fontScale).sp
                            )
                            Text(
                                text = "POV: ${exec.stylePovStability}%",
                                style = MaterialTheme.typography.bodySmall.copy(color = EmeraldVoice),
                                fontSize = (11 * fontScale).sp
                            )
                        }

                        if (exec.topGapPrompt != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Next Prompt: \"${exec.topGapPrompt}\"",
                                style = MaterialTheme.typography.bodySmall.copy(color = OffWhiteText),
                                fontSize = (11 * fontScale).sp,
                                maxLines = 2
                            )
                        }
                    } else {
                        Text(
                            text = "Record a memory or tap Run to trigger the unified autonomous background workflow.",
                            style = MaterialTheme.typography.bodySmall.copy(color = LightGrayMuted),
                            fontSize = (11 * fontScale).sp
                        )
                    }
                }
            }
        }
    }
}
