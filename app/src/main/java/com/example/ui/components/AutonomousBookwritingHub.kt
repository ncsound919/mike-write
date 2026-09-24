package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autonomous.AutonomousExpansionEngine
import com.example.autonomous.AutonomousManuscriptWeaver
import com.example.autonomous.AutonomousStyleHarmonizer
import com.example.data.Memory
import com.example.loop.VoiceLoopController
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Autonomous Bookwriting Hub:
 * Provides interactive oversight and execution for the 3 autonomous writing features:
 * 1. Autonomous Timeline & Narrative Weaver (Chronological scene sequencing and transition bridging)
 * 2. Autonomous Editorial Gap & Expansion Engine (Literary deficit auditing and targeted interview queue)
 * 3. Autonomous Style Harmonizer (POV stability, tense correction, and oral crutch pruning)
 */
@Composable
fun AutonomousBookwritingHub(
    controller: VoiceLoopController,
    memories: List<Memory>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeSubSection by remember { mutableIntStateOf(0) } // 0 = Timeline Weaver, 1 = Gap Expansion, 2 = Style Harmonizer

    // Computed analyses
    val timelineAnalysis = remember(memories) { AutonomousManuscriptWeaver.analyzeTimeline(memories) }
    val gapReport = remember(memories) { AutonomousExpansionEngine.auditManuscriptGaps(memories) }
    val styleScorecard = remember(memories) { AutonomousStyleHarmonizer.auditStyleHealth(memories) }
    val pipelineExecution by com.example.autonomous.UnifiedAutomationPipeline.lastExecution.collectAsState()
    val isAutomating by com.example.autonomous.UnifiedAutomationPipeline.isAutomating.collectAsState()

    var generatedBridges by remember { mutableStateOf<List<AutonomousManuscriptWeaver.NarrativeBridge>>(emptyList()) }
    var isGeneratingBridges by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Hub Overview Card ---
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCard),
            border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = AmberGold.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberGold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AUTONOMOUS BOOKWRITING",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = AmberGold
                                )
                            )
                            Text(
                                text = "Intelligent manuscript weaving, gap expansion & style polish",
                                style = MaterialTheme.typography.bodySmall.copy(color = LightGrayMuted)
                            )
                        }
                    }

                    // Composite Autonomy Score
                    val compositeScore = (
                        (if (timelineAnalysis.hasInversions) 75 else 100) +
                        gapReport.healthScore +
                        styleScorecard.overallStyleIndex
                    ) / 3

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (compositeScore >= 80) EmeraldDark else DarkNavySurface,
                        border = BorderStroke(1.dp, if (compositeScore >= 80) EmeraldVoice else AmberGold)
                    ) {
                        Text(
                            text = "$compositeScore% Ready",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (compositeScore >= 80) EmeraldVoice else AmberGold,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 4-way Feature Navigation Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        Triple("Timeline Weaver", Icons.Default.Timeline, timelineAnalysis.inversionCount),
                        Triple("Gap Expansion", Icons.Default.Search, gapReport.totalGapsFound),
                        Triple("Style Harmonizer", Icons.Default.Brush, styleScorecard.detectedIssues.size),
                        Triple("Unified Pipeline", Icons.Default.AutoAwesome, if (pipelineExecution != null) 1 else 0)
                    )

                    tabs.forEachIndexed { index, (label, icon, badgeCount) ->
                        val isSelected = activeSubSection == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { activeSubSection = index }
                                .testTag("autonomy_tab_$index"),
                            color = if (isSelected) AmberGold else DarkNavySurface,
                            border = BorderStroke(1.dp, if (isSelected) AmberGold else BorderSubtle),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) DeepNavy else AmberGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    if (badgeCount > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) DeepNavy else if (index == 3) EmeraldVoice else CrimsonRecord,
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = if (index == 3) "✓" else badgeCount.toString(),
                                                    color = if (isSelected) AmberGold else Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DeepNavy else OffWhiteText,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Active Feature Section Body ---
        when (activeSubSection) {
            0 -> TimelineWeaverSection(
                controller = controller,
                analysis = timelineAnalysis,
                memories = memories,
                generatedBridges = generatedBridges,
                isGeneratingBridges = isGeneratingBridges,
                onGenerateBridges = {
                    scope.launch {
                        isGeneratingBridges = true
                        val bridges = AutonomousManuscriptWeaver.generateNarrativeBridges(
                            timelineAnalysis.proposedOrder,
                            controller.interviewer
                        )
                        generatedBridges = bridges
                        isGeneratingBridges = false
                        Toast.makeText(context, "Synthesized ${bridges.size} narrative bridges!", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            1 -> GapExpansionSection(
                controller = controller,
                report = gapReport,
                onPromptSeniorWriter = { prompt ->
                    scope.launch {
                        controller.say(prompt)
                        Toast.makeText(context, "Speaking autonomous expansion question aloud...", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            2 -> StyleHarmonizerSection(
                controller = controller,
                scorecard = styleScorecard,
                memories = memories
            )

            3 -> UnifiedPipelineSection(
                controller = controller,
                execution = pipelineExecution,
                isAutomating = isAutomating,
                memories = memories
            )
        }
    }
}

// -------------------------------------------------------------
// FEATURE 1: AUTONOMOUS TIMELINE WEAVER SECTION
// -------------------------------------------------------------
@Composable
private fun TimelineWeaverSection(
    controller: VoiceLoopController,
    analysis: AutonomousManuscriptWeaver.SequenceAnalysis,
    memories: List<Memory>,
    generatedBridges: List<AutonomousManuscriptWeaver.NarrativeBridge>,
    isGeneratingBridges: Boolean,
    onGenerateBridges: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Autonomous Chronology & Scene Weaver",
                        fontWeight = FontWeight.Bold,
                        color = AmberGold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = analysis.summaryExplanation,
                        color = LightGrayMuted,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = onGenerateBridges,
                    enabled = !isGeneratingBridges && analysis.proposedOrder.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("generate_bridges_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isGeneratingBridges) "Weaving..." else "Weave Bridges", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Inversions Alert Banner
            if (analysis.hasInversions) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CrimsonDark,
                    border = BorderStroke(1.dp, CrimsonRecord)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonRecord, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${analysis.inversionCount} scenes are recorded out of chronological life order. Tap below to preview the sequenced manuscript flow.",
                            color = OffWhiteText,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Timeline Scenes Flow
            Text("PROPOSED CHRONOLOGICAL READING SEQUENCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightGrayMuted)

            analysis.proposedOrder.take(6).forEachIndexed { index, scene ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MidnightCard,
                    border = BorderStroke(1.dp, if (scene.originalIndex != index) AmberGold.copy(alpha = 0.5f) else BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AmberGold.copy(alpha = 0.2f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = AmberGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = scene.title,
                                    fontWeight = FontWeight.Bold,
                                    color = OffWhiteText,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                if (scene.detectedYear != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SkyBlue.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = scene.detectedYear.toString(),
                                            color = SkyBlue,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (scene.detectedLifeStage != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = EmeraldDark
                                    ) {
                                        Text(
                                            text = scene.detectedLifeStage,
                                            color = EmeraldVoice,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Chapter: ${scene.chapter} • ${scene.text.take(60)}...",
                                color = LightGrayMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Generated Bridges Preview
            AnimatedVisibility(visible = generatedBridges.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AUTONOMOUS SCENE BRIDGES (READY FOR MANUSCRIPT)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldVoice)
                    generatedBridges.take(3).forEach { bridge ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldDark.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, EmeraldVoice.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = EmeraldVoice, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "\"${bridge.bridgeSentence}\"",
                                    color = OffWhiteText,
                                    fontSize = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// FEATURE 2: AUTONOMOUS GAP EXPANSION SECTION
// -------------------------------------------------------------
@Composable
private fun GapExpansionSection(
    controller: VoiceLoopController,
    report: AutonomousExpansionEngine.GapAnalysisReport,
    onPromptSeniorWriter: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Autonomous Editorial Gap Engine",
                        fontWeight = FontWeight.Bold,
                        color = AmberGold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Health Score: ${report.healthScore}/100 • ${report.totalGapsFound} narrative gaps detected",
                        color = LightGrayMuted,
                        fontSize = 12.sp
                    )
                }

                // Severity chip
                val badgeColor = if (report.highPriorityGaps > 0) CrimsonRecord else EmeraldVoice
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, badgeColor)
                ) {
                    Text(
                        text = "${report.highPriorityGaps} High Priority",
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (report.gaps.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MidnightCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldVoice, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "No narrative holes detected! The manuscript has strong sensory atmosphere, internal reflection, and steady pacing.",
                            color = OffWhiteText,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Text("PRIORITIZED AUTONOMOUS INTERVIEW QUEUE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightGrayMuted)

                report.gaps.take(5).forEach { gap ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MidnightCard,
                        border = BorderStroke(1.dp, if (gap.severity == "High") CrimsonRecord.copy(alpha = 0.6f) else BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (gap.severity == "High") CrimsonDark else AmberGold.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = gap.type.label,
                                            color = if (gap.severity == "High") CrimsonRecord else AmberGold,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = gap.title,
                                        fontWeight = FontWeight.Bold,
                                        color = OffWhiteText,
                                        fontSize = 13.sp
                                    )
                                }

                                IconButton(
                                    onClick = { onPromptSeniorWriter(gap.autonomousInterviewPrompt) },
                                    modifier = Modifier.size(32.dp).testTag("ask_gap_prompt_${gap.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Ask Aloud",
                                        tint = AmberGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Text(
                                text = gap.diagnosis,
                                color = LightGrayMuted,
                                fontSize = 11.sp
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkNavySurface,
                                border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "\"${gap.autonomousInterviewPrompt}\"",
                                        color = AmberGoldLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// FEATURE 3: AUTONOMOUS STYLE HARMONIZER SECTION
// -------------------------------------------------------------
@Composable
private fun StyleHarmonizerSection(
    controller: VoiceLoopController,
    scorecard: AutonomousStyleHarmonizer.StyleHealthScorecard,
    memories: List<Memory>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isHarmonizing by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Autonomous Voice & Style Harmonizer",
                        fontWeight = FontWeight.Bold,
                        color = AmberGold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = scorecard.summaryStatement,
                        color = LightGrayMuted,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            isHarmonizing = true
                            withContext(Dispatchers.IO) {
                                val results = AutonomousStyleHarmonizer.batchHarmonize(memories)
                                val updatedMemories = memories.map { mem ->
                                    val r = results.find { it.memoryId == mem.id }
                                    if (r != null && r.changesCount > 0) {
                                        mem.copy(formattedProse = r.harmonizedText)
                                    } else mem
                                }
                                controller.db.memoryDao().updateAll(updatedMemories)
                            }
                            isHarmonizing = false
                            Toast.makeText(context, "Manuscript harmonized successfully!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isHarmonizing && scorecard.detectedIssues.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldVoice, contentColor = DeepNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("batch_harmonize_button")
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isHarmonizing) "Polishing..." else "Harmonize All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Style Health Metrics Triad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val metrics = listOf(
                    Triple("POV Purity", "${scorecard.povStabilityPercent}%", EmeraldVoice),
                    Triple("Oral Crutches", "${scorecard.oralCrutchFrequency} found", if (scorecard.oralCrutchFrequency == 0) EmeraldVoice else AmberGold),
                    Triple("Style Index", "${scorecard.overallStyleIndex}/100", AmberGold)
                )

                metrics.forEach { (label, value, color) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = MidnightCard,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = label, fontSize = 10.sp, color = LightGrayMuted, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(text = value, fontSize = 13.sp, color = color, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // Detected Refinements List
            if (scorecard.detectedIssues.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MidnightCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldVoice, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "First-person perspective and tense consistency are pristine across all chapters.",
                            color = OffWhiteText,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Text("DETECTED STYLISTIC REFINEMENTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightGrayMuted)

                scorecard.detectedIssues.take(5).forEach { issue ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MidnightCard,
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AmberGold.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = issue.issueCategory,
                                    color = AmberGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "\"${issue.originalSnippet}\" → \"${issue.suggestedHarmonization}\"",
                                    fontWeight = FontWeight.Bold,
                                    color = OffWhiteText,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = issue.explanation,
                                    color = LightGrayMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// FEATURE 4: UNIFIED "JUST WORKS" PIPELINE SECTION
// -------------------------------------------------------------
@Composable
private fun UnifiedPipelineSection(
    controller: VoiceLoopController,
    execution: com.example.autonomous.UnifiedAutomationPipeline.AutomationExecutionResult?,
    isAutomating: Boolean,
    memories: List<Memory>
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var autoSave by remember { mutableStateOf(controller.settings.smartAutoSave) }
    var autoEditorial by remember { mutableStateOf(controller.settings.autoEditorialPipeline) }
    var autoTimeline by remember { mutableStateOf(controller.settings.autoWeaveTimeline) }
    var autoGaps by remember { mutableStateOf(controller.settings.autoGapAuditing) }
    var autoHarmonize by remember { mutableStateOf(controller.settings.autoVoiceHarmonizing) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Automation Control Master Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
            border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unified Autonomous Pipeline",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OffWhiteText
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Chains timeline weaving, gap auditing, style polishing, and chapter routing into one fluid background pipeline so authoring 'just works'.",
                            style = MaterialTheme.typography.bodySmall.copy(color = LightGrayMuted)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                controller.runUnifiedPipeline()
                                Toast.makeText(context, "Executing unified autonomous pipeline...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isAutomating && memories.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isAutomating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = DeepNavy,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Pipeline", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                Spacer(Modifier.height(14.dp))

                // Automation Toggles
                Text(
                    text = "AUTOMATION PIPELINE MODULES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AmberGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(Modifier.height(8.dp))

                val toggles = listOf(
                    Triple("Smart Auto-Save", "Auto-saves dictated memories without blocking verbal confirmation", autoSave) to {
                        val next = !autoSave
                        autoSave = next
                        controller.settings.smartAutoSave = next
                    },
                    Triple("Auto Editorial Pipeline", "Runs background analysis & next-topic discovery immediately on save", autoEditorial) to {
                        val next = !autoEditorial
                        autoEditorial = next
                        controller.settings.autoEditorialPipeline = next
                    },
                    Triple("Timeline Weaving", "Detects chronological inversions & orders scenes naturally", autoTimeline) to {
                        val next = !autoTimeline
                        autoTimeline = next
                        controller.settings.autoWeaveTimeline = next
                    },
                    Triple("Gap & Deficit Auditing", "Identifies missing life milestones & generates follow-up questions", autoGaps) to {
                        val next = !autoGaps
                        autoGaps = next
                        controller.settings.autoGapAuditing = next
                    },
                    Triple("Voice & Style Harmonizing", "Polishes point-of-view stability & strips oral crutches", autoHarmonize) to {
                        val next = !autoHarmonize
                        autoHarmonize = next
                        controller.settings.autoVoiceHarmonizing = next
                    }
                )

                toggles.forEach { (meta, onToggle) ->
                    val (title, desc, isChecked) = meta
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = title, fontWeight = FontWeight.SemiBold, color = OffWhiteText, fontSize = 13.sp)
                            Text(text = desc, color = LightGrayMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { onToggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepNavy,
                                checkedTrackColor = AmberGold
                            )
                        )
                    }
                }
            }
        }

        // Live Execution Telemetry Card
        if (execution != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, EmeraldVoice.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldVoice)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Last Pipeline Telemetry Result",
                            fontWeight = FontWeight.Bold,
                            color = OffWhiteText,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Passage: ${execution.passageTitle} (${execution.chapter})",
                        fontWeight = FontWeight.Bold,
                        color = AmberGoldLight,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "• Inversions Detected: ${execution.timelineInversionsDetected}\n• Manuscript Gaps: ${execution.manuscriptGapsCount}\n• Style POV Stability: ${execution.stylePovStability}%\n• Style Refinements: ${execution.detectedStyleIssuesCount}",
                        color = OffWhiteText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    if (execution.topGapPrompt != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkNavySurface,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Next Suggested Topic: \"${execution.topGapPrompt}\"",
                                color = OffWhiteText,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

