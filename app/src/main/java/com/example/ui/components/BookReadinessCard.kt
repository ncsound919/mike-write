package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.data.BookReadinessReport
import com.example.data.ReadinessStep
import com.example.ui.theme.*

@Composable
fun BookReadinessCard(
    report: BookReadinessReport,
    modifier: Modifier = Modifier,
    onNavigateToDrafting: (() -> Unit)? = null
) {
    var expandedSteps by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .testTag("book_readiness_card")
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: Title, Status Badge, Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AmberGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Book Publication Readiness",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = OffWhiteText
                        )
                        Text(
                            text = "${report.completedStepsCount} of ${report.totalStepsCount} milestones achieved",
                            fontSize = 12.sp,
                            color = LightGrayMuted
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        report.completionPercentage >= 80 -> EmeraldVoice.copy(alpha = 0.2f)
                        report.completionPercentage >= 40 -> AmberGold.copy(alpha = 0.2f)
                        else -> SkyBlue.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = "${report.completionPercentage}%",
                        color = when {
                            report.completionPercentage >= 80 -> EmeraldVoice
                            report.completionPercentage >= 40 -> AmberGold
                            else -> SkyBlue
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Multi-segment Visual Readiness Bar
            Column {
                LinearProgressIndicator(
                    progress = { report.completionPercentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .testTag("readiness_progress_bar"),
                    color = when {
                        report.completionPercentage >= 80 -> EmeraldVoice
                        report.completionPercentage >= 40 -> AmberGold
                        else -> SkyBlue
                    },
                    trackColor = MidnightCard
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Status: ${report.readinessStatus}",
                        color = AmberGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Next: ${report.nextAction}",
                        color = LightGrayMuted,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Checklist Steps Toggle Button
            OutlinedButton(
                onClick = { expandedSteps = !expandedSteps },
                modifier = Modifier.fillMaxWidth().testTag("toggle_readiness_steps_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhiteText),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expandedSteps) "Hide Steps to Finish" else "View Steps to Finish (${report.totalStepsCount - report.completedStepsCount} remaining)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (expandedSteps) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Step-by-Step Breakdown
            if (expandedSteps) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    report.steps.forEach { step ->
                        ReadinessStepItem(step = step)
                    }
                }
            }
        }
    }
}

@Composable
fun ReadinessStepItem(step: ReadinessStep) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MidnightCard,
        border = BorderStroke(1.dp, if (step.isCompleted) EmeraldVoice.copy(alpha = 0.3f) else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (step.isCompleted) EmeraldVoice.copy(alpha = 0.2f) else DarkNavySurface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (step.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (step.isCompleted) EmeraldVoice else LightGrayMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (step.isCompleted) OffWhiteText else LightGrayMuted
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkNavySurface
                    ) {
                        Text(
                            text = step.category,
                            fontSize = 10.sp,
                            color = SkyBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = step.description,
                    fontSize = 12.sp,
                    color = LightGrayMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
