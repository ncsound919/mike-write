package com.example.buddy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Memory
import com.example.loop.LoopState
import com.example.loop.VoiceLoopBus
import com.example.loop.VoiceLoopController
import com.example.ui.components.AudiobookPlayerBar
import com.example.ui.components.ChapterSwitcherDialog
import com.example.ui.components.ExportChapterDialog
import com.example.ui.components.SoundWaveVisualizer
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun BuddyScreen(
    controller: VoiceLoopController,
    memories: List<Memory>,
    onOpenSettings: () -> Unit
) {
    val loopState by VoiceLoopBus.state.collectAsState()
    val lastSpoken by VoiceLoopBus.lastSpoken.collectAsState()
    val lastRecognized by VoiceLoopBus.lastRecognized.collectAsState()
    val audioLevel by VoiceLoopBus.audioLevels.collectAsState()
    val scope = rememberCoroutineScope()

    // Dynamic Font Scaling State for Seniors
    var fontScale by remember { mutableStateOf(controller.settings.fontSizeScale) }

    // Breathing pulsation for listening & speaking
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val currentChapter = controller.settings.currentChapter

    val allChapters = listOf(
        "Chapter 1: Early Days",
        "Chapter 2: Career & Passions",
        "Chapter 3: Family & Love",
        "Chapter 4: Turning Points",
        "Chapter 5: Hardships & Resilience",
        "Chapter 6: Wisdom & Legacy"
    )

    // Selected memory for inspecting compartmentalized literary breakdown
    var inspectingMemory by remember { mutableStateOf<Memory?>(null) }
    var showRawTranscriptMap by remember { mutableStateOf(mapOf<Long, Boolean>()) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showChapterSwitcher by remember { mutableStateOf(false) }
    var filterByCurrentChapterOnly by remember { mutableStateOf(false) }

    // Real Input Mode state (Voice Loop, Push-to-Talk, Switch Scan Assist, Eye Gaze Dwell)
    var activeInputMode by remember { mutableStateOf(controller.settings.activeInputMode) }
    var scanIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeInputMode) {
        if (activeInputMode == "Switch Scan Assist") {
            while (true) {
                kotlinx.coroutines.delay(2500)
                scanIndex = (scanIndex + 1) % 5
            }
        }
    }

    LaunchedEffect(activeInputMode, scanIndex) {
        VoiceLoopBus.switchActions.collect { action ->
            if (activeInputMode == "Switch Scan Assist" && action == com.example.loop.AccessibilitySwitchAction.TOGGLE_RECORD_OR_CONFIRM) {
                when (scanIndex) {
                    0 -> {
                        if (loopState is LoopState.Recording || controller.isRecording) {
                            controller.finishRecording()
                        } else {
                            controller.beginRecording()
                        }
                    }
                    1 -> controller.handleUtterance("Prompt me")
                    2 -> controller.handleUtterance("Review")
                    3 -> controller.handleUtterance("Readiness")
                    4 -> showExportDialog = true
                }
            }
        }
    }

    // Aesthetic color state based on engine loop
    val (statusLabel, statusColor, statusBg, statusIcon) = when (val s = loopState) {
        is LoopState.Idle -> Quadruple("READY", AmberGold, DarkNavySurface, Icons.Default.MicNone)
        is LoopState.Listening -> Quadruple("LISTENING…", EmeraldVoice, EmeraldDark, Icons.Default.GraphicEq)
        is LoopState.Speaking -> Quadruple("SPEAKING", SkyBlue, MidnightCard, Icons.AutoMirrored.Filled.VolumeUp)
        is LoopState.Recording -> Quadruple("RECORDING MEMORY", CrimsonRecord, CrimsonDark, Icons.Default.FiberManualRecord)
        is LoopState.Processing -> Quadruple("THINKING & ANALYZING…", AmberGoldLight, MidnightCard, Icons.Default.AutoAwesome)
        is LoopState.Error -> Quadruple("ERROR", CrimsonRecord, CrimsonDark, Icons.Default.Warning)
    }

    val animatedBg by animateColorAsState(targetValue = statusBg, label = "bgColor")

    val displayedMemories = if (filterByCurrentChapterOnly) {
        memories.filter { it.chapter.equals(currentChapter, ignoreCase = true) }
    } else {
        memories
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = DeepNavy,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MIKE WRITE",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                    color = AmberGold
                                )
                            )
                            // Clickable active chapter badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MidnightCard,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showChapterSwitcher = true }
                                    .testTag("active_chapter_header_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = AmberGold,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = currentChapter,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OffWhiteText,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Switch chapter",
                                        tint = AmberGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Senior Text Size Stepper (1.0x -> 1.25x -> 1.5x)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MidnightCard,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val nextScale = when (fontScale) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            else -> 1.0f
                                        }
                                        fontScale = nextScale
                                        controller.settings.fontSizeScale = nextScale
                                    }
                                    .testTag("text_size_stepper_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FormatSize,
                                        contentDescription = "Text Size",
                                        tint = AmberGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = when (fontScale) {
                                            1.5f -> "A++"
                                            1.25f -> "A+"
                                            else -> "A"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = AmberGold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MidnightCard,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (activeInputMode != "Voice Loop (Always Listening)") AmberGold else BorderSubtle),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val modes = listOf(
                                            "Voice Loop (Always Listening)",
                                            "Push-to-Talk",
                                            "Switch Scan Assist",
                                            "Eye Gaze Dwell"
                                        )
                                        val next = modes[(modes.indexOf(activeInputMode) + 1) % modes.size]
                                        activeInputMode = next
                                        controller.settings.activeInputMode = next
                                    }
                                    .testTag("input_mode_stepper_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (activeInputMode) {
                                            "Push-to-Talk" -> Icons.Default.TouchApp
                                            "Switch Scan Assist" -> Icons.Default.Sensors
                                            "Eye Gaze Dwell" -> Icons.Default.Visibility
                                            else -> Icons.Default.Mic
                                        },
                                        contentDescription = "Input Mode",
                                        tint = AmberGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = when (activeInputMode) {
                                            "Push-to-Talk" -> "PTT"
                                            "Switch Scan Assist" -> "Scan"
                                            "Eye Gaze Dwell" -> "Dwell"
                                            else -> "Loop"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = AmberGold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onOpenSettings,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MidnightCard,
                                    contentColor = OffWhiteText
                                ),
                                modifier = Modifier.testTag("settings_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = AmberGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Studio",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hero Book Cover & Manuscript Stats Banner
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(125.dp)
                    ) {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.memoir_book_hero_1789952367883),
                            contentDescription = "Memoir Book Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.50f)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, DarkNavySurface)
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AmberGold.copy(alpha = 0.25f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "LIFETIME MEMOIR • ${memories.size} PASSAGES TRANSCRIBED",
                                    fontSize = (10 * fontScale).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AmberGold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = controller.settings.bookTitle,
                                fontSize = (19 * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = OffWhiteText
                            )
                            Text(
                                text = "By ${controller.settings.authorName}",
                                fontSize = (12 * fontScale).sp,
                                color = LightGrayMuted
                            )
                        }
                    }
                }

                // Main Big Status Card with Embedded SoundWaveVisualizer
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(animatedBg, DarkNavySurface)
                            )
                        )
                        .border(2.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(28.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Status Badge
                        Surface(
                            shape = CircleShape,
                            color = statusColor.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (13 * fontScale).sp,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }

                        // Interactive Microphone / Voice Orb with real RMS level
                        val animatedScale = if (loopState is LoopState.Listening || loopState is LoopState.Recording) {
                            (pulseScale + (audioLevel * 0.35f)).coerceIn(1.0f, 1.5f)
                        } else 1.0f

                        val isOrbScanned = activeInputMode == "Switch Scan Assist" && scanIndex == 0

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(125.dp)
                                .scale(animatedScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(statusColor.copy(alpha = 0.4f), Color.Transparent)
                                    )
                                )
                                .border(
                                    width = if (isOrbScanned) 5.dp else 3.dp,
                                    color = if (isOrbScanned) AmberGold else statusColor,
                                    shape = CircleShape
                                )
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Toggle voice recording"
                                ) {
                                    scope.launch {
                                        if (loopState is LoopState.Recording || controller.isRecording) {
                                            controller.finishRecording()
                                        } else {
                                            controller.beginRecording()
                                        }
                                    }
                                }
                                .testTag("big_voice_orb_button")
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = "Voice State Icon",
                                tint = OffWhiteText,
                                modifier = Modifier.size(52.dp)
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Real-time SoundWaveVisualizer
                        SoundWaveVisualizer(
                            state = loopState,
                            audioLevel = audioLevel,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        // Spoken or Listening Headline
                        val displayPrompt = when (val s = loopState) {
                            is LoopState.Speaking -> s.text
                            is LoopState.Listening -> lastSpoken.ifBlank { "Listening for your voice. Say 'record' or 'help'..." }
                            is LoopState.Recording -> {
                                if (s.partialText.isNotBlank()) "\"${s.partialText}\""
                                else "Listening to your story... (Say 'done' or tap below when finished)"
                            }
                            is LoopState.Processing -> s.task
                            is LoopState.Idle -> "Tap or say 'Record' to dictate your memory."
                            is LoopState.Error -> s.message
                        }

                        Text(
                            text = displayPrompt,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = OffWhiteText,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (30 * fontScale).sp,
                                textAlign = TextAlign.Center
                            ),
                            fontSize = (20 * fontScale).sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        // Explicit high-visibility button while recording
                        if (loopState is LoopState.Recording || controller.isRecording) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        controller.finishRecording()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.testTag("finish_recording_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Catch Memory",
                                    tint = DeepNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Done Recording • Catch Memory",
                                    color = DeepNavy,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (15 * fontScale).sp
                                )
                            }
                        }

                        AnimatedVisibility(visible = lastRecognized.isNotBlank()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 14.dp)
                            ) {
                                Text(
                                    text = "Last heard:",
                                    style = MaterialTheme.typography.labelMedium.copy(color = LightGrayMuted)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "\"$lastRecognized\"",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = AmberGoldLight,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    ),
                                    fontSize = (15 * fontScale).sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Real Active Input Mode Accessibility Banner
                if (activeInputMode == "Switch Scan Assist") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AmberGold.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Tapping banner activates currently scanned target
                                scope.launch {
                                    when (scanIndex) {
                                        0 -> {
                                            if (loopState is LoopState.Recording || controller.isRecording) {
                                                controller.finishRecording()
                                            } else {
                                                controller.beginRecording()
                                            }
                                        }
                                        1 -> controller.handleUtterance("Prompt me")
                                        2 -> controller.handleUtterance("Review")
                                        3 -> controller.handleUtterance("Readiness")
                                        4 -> showExportDialog = true
                                    }
                                }
                            }
                            .testTag("switch_scan_trigger_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = AmberGold, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SWITCH SCAN: ${
                                        when(scanIndex) {
                                            0 -> "1/5 Dictate Story (Voice Orb)"
                                            1 -> "2/5 Prompt Me (AI Question)"
                                            2 -> "3/5 Review Book Aloud"
                                            3 -> "4/5 Manuscript Readiness"
                                            else -> "5/5 Export Memoir"
                                        }
                                    }",
                                    color = AmberGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (13 * fontScale).sp
                                )
                                Text(
                                    text = "Press Volume Down key, Switch, or Tap here to trigger",
                                    color = OffWhiteText.copy(alpha = 0.85f),
                                    fontSize = (11 * fontScale).sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                } else if (activeInputMode == "Push-to-Talk") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SkyBlue.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlue.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "PUSH-TO-TALK ACTIVE: Tap voice orb to record, tap again to save memory.",
                                color = SkyBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (12 * fontScale).sp
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // Audibly Navigable Voice Command Bar
                Text(
                    text = "VOICE COMMANDS YOU CAN SPEAK ANYTIME",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = LightGrayMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )

                val commandChips = listOf(
                    Triple("Record", "Start dictating", Icons.Default.Mic),
                    Triple("Playback", "Hear live draft", Icons.Default.Hearing),
                    Triple("Auto sequence", "Timeline weaver", Icons.Default.Timeline),
                    Triple("Find gaps", "Missing stories", Icons.Default.Search),
                    Triple("Harmonize", "Polish style", Icons.Default.Brush),
                    Triple("Done", "Finish story", Icons.Default.CheckCircle),
                    Triple("Breakdown", "Story elements", Icons.Default.AutoAwesome),
                    Triple("Tip", "Writing coach", Icons.Default.Lightbulb),
                    Triple("Save", "Keep memory", Icons.Default.Save),
                    Triple("Prompt me", "Ask AI question", Icons.Default.Psychology),
                    Triple("Review", "Hear book aloud", Icons.Default.PlayArrow),
                    Triple("Readiness", "Book progress", Icons.Default.Assessment),
                    Triple("Export", "PDF / text file", Icons.Default.Download),
                    Triple("Chapter", "Switch chapter", Icons.AutoMirrored.Filled.MenuBook),
                    Triple("Undo", "Scratch that", Icons.AutoMirrored.Filled.Undo),
                    Triple("Help", "Audible guide", Icons.AutoMirrored.Filled.Help)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(commandChips) { (title, subtitle, icon) ->
                        val isScanned = activeInputMode == "Switch Scan Assist" && (
                            (title == "Prompt me" && scanIndex == 1) ||
                            (title == "Review" && scanIndex == 2) ||
                            (title == "Readiness" && scanIndex == 3) ||
                            (title == "Export" && scanIndex == 4)
                        )

                        Card(
                            modifier = Modifier
                                .width(135.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch {
                                        controller.handleUtterance(title)
                                    }
                                }
                                .testTag("command_chip_${title.lowercase().replace(" ", "_")}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isScanned) AmberGold.copy(alpha = 0.25f) else MidnightCard
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isScanned) 2.5.dp else 1.dp,
                                color = if (isScanned) AmberGold else BorderSubtle
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (isScanned) AmberGold else AmberGold,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "\"$title\"",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isScanned) AmberGold else OffWhiteText,
                                    fontSize = (14 * fontScale).sp
                                )
                                Text(
                                    text = subtitle,
                                    color = LightGrayMuted,
                                    fontSize = (11 * fontScale).sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Passages Section Header with Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MANUSCRIPT PASSAGES (${displayedMemories.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = LightGrayMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showChapterSwitcher = true },
                            modifier = Modifier.testTag("switch_chapter_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Chapters",
                                color = AmberGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.testTag("export_chapter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Export",
                                color = AmberGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(
                            onClick = {
                                scope.launch { controller.readBookSummary() }
                            },
                            modifier = Modifier.testTag("read_memoir_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Read Book",
                                color = AmberGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Filter Pill Row: All vs Active Chapter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !filterByCurrentChapterOnly,
                        onClick = { filterByCurrentChapterOnly = false },
                        label = { Text("All Chapters (${memories.size})", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold,
                            selectedLabelColor = DeepNavy,
                            containerColor = MidnightCard,
                            labelColor = OffWhiteText
                        )
                    )

                    FilterChip(
                        selected = filterByCurrentChapterOnly,
                        onClick = { filterByCurrentChapterOnly = true },
                        label = { Text("This Chapter Only", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold,
                            selectedLabelColor = DeepNavy,
                            containerColor = MidnightCard,
                            labelColor = OffWhiteText
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (displayedMemories.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MidnightCard.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = AmberGold.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "No passages in this view",
                                color = OffWhiteText,
                                fontWeight = FontWeight.Bold,
                                fontSize = (16 * fontScale).sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Say \"Record\" or tap the center microphone to begin dictating.",
                                color = LightGrayMuted,
                                textAlign = TextAlign.Center,
                                fontSize = (13 * fontScale).sp
                            )
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        displayedMemories.take(8).forEach { mem ->
                            val isExpanded = inspectingMemory?.id == mem.id

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(
                                        1.dp,
                                        if (isExpanded) AmberGold else BorderSubtle,
                                        RoundedCornerShape(20.dp)
                                    ),
                                colors = CardDefaults.cardColors(containerColor = DarkNavySurface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    val showRaw = showRawTranscriptMap[mem.id] ?: false
                                    val displayTitle = mem.passageTitle.orEmpty().ifBlank { "Passage in ${mem.chapter ?: "Book"}" }
                                    val displayText = if (showRaw) mem.transcript else (mem.formattedProse.orEmpty().ifBlank { mem.transcript })

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = displayTitle,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (16 * fontScale).sp,
                                                color = OffWhiteText
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = AmberGold.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = mem.chapter ?: "Prologue",
                                                        color = AmberGold,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                if (!mem.emotionalTone.isNullOrBlank()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = SkyBlue.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = mem.emotionalTone,
                                                            color = SkyBlue,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                if (mem.isAutoChapterCreated) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = StoryPurple.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = "✨ Auto Chapter",
                                                            color = StoryPurple,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Story deconstruct button
                                            IconButton(
                                                onClick = {
                                                    inspectingMemory = if (isExpanded) null else mem
                                                },
                                                modifier = Modifier.size(36.dp).testTag("inspect_memory_${mem.id}")
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.AutoStories,
                                                    contentDescription = "View literary elements",
                                                    tint = if (isExpanded) AmberGold else StoryPurple,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        controller.say("Memory: $displayText")
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp).testTag("play_memory_${mem.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayCircle,
                                                    contentDescription = "Listen to memory",
                                                    tint = SkyBlue,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    // Toggle bar for Formatted Manuscript vs Raw Voice Speech
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (showRaw) "RAW SPEECH TRANSCRIPT" else "MANUSCRIPT FORMATTED PROSE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (showRaw) LightGrayMuted else AmberGoldLight,
                                            letterSpacing = 1.sp
                                        )

                                        TextButton(
                                            onClick = {
                                                showRawTranscriptMap = showRawTranscriptMap.toMutableMap().apply {
                                                    put(mem.id, !showRaw)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showRaw) Icons.Default.AutoAwesome else Icons.Default.RecordVoiceOver,
                                                contentDescription = null,
                                                tint = AmberGold,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (showRaw) "Formatted Prose" else "Raw Speech",
                                                fontSize = 11.sp,
                                                color = AmberGold
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MidnightCard.copy(alpha = 0.6f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                    ) {
                                        Text(
                                            text = displayText,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = OffWhiteText,
                                                lineHeight = (22 * fontScale).sp
                                            ),
                                            fontSize = (15 * fontScale).sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }

                                    // Compartmentalized literary sections breakdown
                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

                                            Text(
                                                text = "LITERARY COMPARTMENTS & CRAFT ELEMENTS",
                                                color = AmberGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                letterSpacing = 1.sp
                                            )

                                            if (!mem.storyArc.isNullOrBlank()) {
                                                LiteraryElementBadge(
                                                    label = "Story Arc & Action",
                                                    text = mem.storyArc,
                                                    color = StoryPurple,
                                                    icon = Icons.Default.Timeline
                                                )
                                            }

                                            if (!mem.reflection.isNullOrBlank()) {
                                                LiteraryElementBadge(
                                                    label = "Inner Narration & Reflection",
                                                    text = mem.reflection,
                                                    color = ReflectionBlue,
                                                    icon = Icons.Default.Psychology
                                                )
                                            }

                                            if (!mem.charactersAndPerspectives.isNullOrBlank()) {
                                                LiteraryElementBadge(
                                                    label = "People & Perspectives",
                                                    text = mem.charactersAndPerspectives,
                                                    color = PerspectiveTeal,
                                                    icon = Icons.Default.Groups
                                                )
                                            }

                                            if (!mem.sensoryDetails.isNullOrBlank()) {
                                                LiteraryElementBadge(
                                                    label = "Sensory Atmosphere & Setting",
                                                    text = mem.sensoryDetails,
                                                    color = SensoryAmber,
                                                    icon = Icons.Default.Visibility
                                                )
                                            }

                                            if (!mem.writingTip.isNullOrBlank()) {
                                                LiteraryElementBadge(
                                                    label = "First-Time Author Craft Tip",
                                                    text = mem.writingTip,
                                                    color = CraftGreen,
                                                    icon = Icons.Default.Lightbulb
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }

        // Floating Audiobook Player Bar
        AudiobookPlayerBar(
            loopState = loopState,
            speechRate = controller.settings.speechRate,
            onStopPlayback = {
                controller.stopAudiobookPlayback()
            },
            onToggleSpeed = {
                val nextRate = when (controller.settings.speechRate) {
                    0.8f -> 1.0f
                    1.0f -> 1.2f
                    else -> 0.8f
                }
                controller.settings.speechRate = nextRate
                controller.speech.setSpeechRate(nextRate)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showChapterSwitcher) {
        ChapterSwitcherDialog(
            currentChapter = currentChapter,
            allChapters = allChapters,
            memories = memories,
            onSelectChapter = { newChap ->
                controller.settings.currentChapter = newChap
                showChapterSwitcher = false
                scope.launch {
                    controller.say("Switched to $newChap. What would you like to record?")
                }
            },
            onAuditionChapter = { chap ->
                showChapterSwitcher = false
                val chapMems = memories.filter { it.chapter.equals(chap, ignoreCase = true) }
                scope.launch {
                    if (chapMems.isEmpty()) {
                        controller.say("No passages recorded in $chap yet.")
                    } else {
                        val text = chapMems.joinToString(" ") { it.formattedProse ?: it.transcript }
                        controller.say("Reading $chap: $text")
                    }
                }
            },
            onDismiss = { showChapterSwitcher = false }
        )
    }

    if (showExportDialog) {
        ExportChapterDialog(
            bookTitle = controller.settings.bookTitle,
            authorName = controller.settings.authorName,
            allChapters = allChapters,
            memories = memories,
            initialChapter = currentChapter,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
fun LiteraryElementBadge(
    label: String,
    text: String,
    color: Color,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label.uppercase(),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                color = OffWhiteText,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
