package com.example.caregiver

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.example.data.BookPublishingAuditor
import com.example.data.ManuscriptFormat
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.loop.LoopState
import com.example.loop.VoiceLoopBus
import com.example.loop.VoiceLoopController
import com.example.ui.components.ExportChapterDialog
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverScreen(
    controller: VoiceLoopController,
    memories: List<Memory>,
    onBackToBuddy: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = controller.settings

    var speechRate by remember { mutableStateOf(settings.speechRate) }
    var currentChapter by remember { mutableStateOf(settings.currentChapter) }
    var authorName by remember { mutableStateOf(settings.authorName) }
    var bookTitle by remember { mutableStateOf(settings.bookTitle) }
    var dedication by remember { mutableStateOf(settings.dedication) }
    var authorBio by remember { mutableStateOf(settings.authorBio) }
    var activeInputMode by remember { mutableStateOf(settings.activeInputMode) }
    var liveEchoPlayback by remember { mutableStateOf(settings.liveEchoPlayback) }
    var fontSizeScale by remember { mutableFloatStateOf(settings.fontSizeScale) }
    var highContrastMode by remember { mutableStateOf(settings.highContrastMode) }
    var readingTheme by remember { mutableStateOf(settings.readingTheme) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Settings, 1 = Book & Transcripts, 2 = Publishing Readiness, 3 = Diagnostics

    val chapterRepo = remember { com.example.data.ChapterRepository(controller.db.chapterDao()) }
    LaunchedEffect(Unit) {
        chapterRepo.ensureDefaultChaptersExist()
    }
    val roomChapters by chapterRepo.allChapters.collectAsState(initial = emptyList())

    val chapters = if (roomChapters.isNotEmpty()) {
        roomChapters.map { it.title }
    } else {
        listOf(
            "Chapter 1: Early Days",
            "Chapter 2: Growing Up & Family",
            "Chapter 3: Passions & Milestones",
            "Chapter 4: The Turning Point",
            "Chapter 5: Strength, Healing & Daily Life",
            "Chapter 6: Wisdom & Legacy"
        )
    }

    val inputModes = listOf("Voice Loop (Always Listening)", "Push-to-Talk", "Switch Scan Assist", "Eye Gaze Dwell")

    var isRunningMicTest by remember { mutableStateOf(false) }
    val audioLevel by VoiceLoopBus.audioLevels.collectAsState()
    val systemLogs by VoiceLoopBus.systemLogs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    var showAddChapterDialog by remember { mutableStateOf(false) }
    var newChapterTitle by remember { mutableStateOf("") }
    var newChapterDescription by remember { mutableStateOf("") }
    var newChapterTargetWordCount by remember { mutableStateOf("2000") }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportTargetChapter by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = DeepNavy,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Settings Hub",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OffWhiteText
                            )
                        )
                        Text(
                            text = "Configure voice, manuscript, and publishing settings",
                            style = MaterialTheme.typography.bodySmall.copy(color = LightGrayMuted)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToBuddy,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Buddy Screen",
                            tint = AmberGold
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onBackToBuddy,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AmberGold,
                            contentColor = DeepNavy
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back to Buddy", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavySurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Segmented Scrollable Tab Bar
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkNavySurface,
                contentColor = AmberGold,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AmberGold
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Settings & Mic", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Book (${memories.size})", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Publishing", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Diagnostics", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Legal & Policy", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Gavel, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    text = { Text("Autonomy Hub", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                )
            }

            // ---- Interconnected Sticky Caregiver Remote Bar ----
            var showRemotePromptDialog by remember { mutableStateOf(false) }
            var remotePromptText by remember { mutableStateOf("") }
            var expandedChapterRemoteMenu by remember { mutableStateOf(false) }

            val loopState by com.example.loop.VoiceLoopBus.state.collectAsState()
            val (statusText, statusBadgeColor) = when (loopState) {
                is LoopState.Idle -> "READY" to AmberGold
                is LoopState.Listening -> "LISTENING" to EmeraldVoice
                is LoopState.Speaking -> "SPEAKING" to SkyBlue
                is LoopState.Recording -> "RECORDING" to CrimsonRecord
                is LoopState.Processing -> "THINKING" to AmberGoldLight
                is LoopState.Error -> "ERROR" to CrimsonRecord
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MidnightCard,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Senior Status Indicator Chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkNavySurface)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusBadgeColor)
                        )
                        Text(
                            text = "Senior Buddy: $statusText",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OffWhiteText
                        )
                    }

                    // Right: Quick Remote Controls (Prompt Senior, Chapter Focus, Loop Toggle)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Quick Prompt Elder Button
                        Button(
                            onClick = { showRemotePromptDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("remote_prompt_senior_button")
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Prompt Elder", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Chapter Switch Dropdown
                        Box {
                            OutlinedButton(
                                onClick = { expandedChapterRemoteMenu = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhiteText),
                                border = BorderStroke(1.dp, BorderSubtle),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("remote_chapter_selector")
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (currentChapter.length > 12) currentChapter.take(10) + "…" else currentChapter,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                            }

                            DropdownMenu(
                                expanded = expandedChapterRemoteMenu,
                                onDismissRequest = { expandedChapterRemoteMenu = false },
                                modifier = Modifier.background(DarkNavySurface)
                            ) {
                                chapters.forEach { chap ->
                                    DropdownMenuItem(
                                        text = { Text(chap, color = OffWhiteText, fontSize = 12.sp) },
                                        onClick = {
                                            currentChapter = chap
                                            settings.currentChapter = chap
                                            expandedChapterRemoteMenu = false
                                            com.example.loop.VoiceLoopBus.appendLog("Caregiver changed chapter focus to: $chap")
                                            Toast.makeText(context, "Chapter focus updated to: $chap", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        // Start / Stop Loop Toggle
                        IconButton(
                            onClick = {
                                if (controller.isRunning) {
                                    controller.stopEverything()
                                    Toast.makeText(context, "Voice Loop Paused", Toast.LENGTH_SHORT).show()
                                } else {
                                    controller.start()
                                    Toast.makeText(context, "Voice Loop Started", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp).testTag("remote_loop_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (controller.isRunning) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Toggle Voice Loop",
                                tint = if (controller.isRunning) CrimsonRecord else EmeraldVoice,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Dialog for Prompting Senior Writer Remote
            if (showRemotePromptDialog) {
                // Generate quick craft suggestion
                val craftReport = remember(memories) { com.example.deterministic.CraftCoachAgent.evaluateStory(memories) }
                val recommendedFocus = craftReport.recommendations.firstOrNull() ?: "Ask about vivid sensory details"

                AlertDialog(
                    onDismissRequest = { showRemotePromptDialog = false },
                    containerColor = DarkNavySurface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = AmberGold)
                            Spacer(Modifier.width(8.dp))
                            Text("Prompt Senior Writer Aloud", color = AmberGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Send an audible question to Mike for ${settings.currentChapter}. Mike's Senior Buddy app will read this question aloud and listen for his story answer.",
                                color = LightGrayMuted,
                                fontSize = 12.sp
                            )

                            // Craft Coach Suggestion Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MidnightCard,
                                border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Craft Coach Tip: $recommendedFocus",
                                        fontSize = 11.sp,
                                        color = OffWhiteText,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = remotePromptText,
                                onValueChange = { remotePromptText = it },
                                placeholder = { Text("e.g., Tell me about your old workshop and what tools Grandpa Joe used...", color = LightGrayMuted, fontSize = 13.sp) },
                                label = { Text("Spoken Interview Question", color = LightGrayMuted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AmberGold,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedTextColor = OffWhiteText,
                                    unfocusedTextColor = OffWhiteText
                                ),
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth().testTag("remote_prompt_text_input")
                            )

                            // Preset Quick Questions
                            Text("Quick Inspiration Prompts:", fontSize = 11.sp, color = LightGrayMuted, fontWeight = FontWeight.Bold)
                            val presets = listOf(
                                "What is your fondest memory from ${settings.currentChapter}?",
                                "Who was there with you and what do you remember about them?",
                                "What sights, sounds, or smells do you recall from that day?"
                            )
                            presets.forEach { preset ->
                                OutlinedButton(
                                    onClick = { remotePromptText = preset },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SkyBlue),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(preset, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (remotePromptText.isNotBlank()) {
                                    scope.launch {
                                        com.example.loop.VoiceLoopBus.appendLog("Caregiver sent remote prompt: '$remotePromptText'")
                                        controller.say("Caregiver prompt for ${settings.currentChapter}: $remotePromptText")
                                        Toast.makeText(context, "Prompt sent to Senior Buddy!", Toast.LENGTH_SHORT).show()
                                        showRemotePromptDialog = false
                                        remotePromptText = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                            modifier = Modifier.testTag("send_remote_prompt_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Speak Prompt Aloud", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemotePromptDialog = false }) {
                            Text("Cancel", color = LightGrayMuted)
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Settings & Mic
                    val readinessReport = remember(bookTitle, authorName, dedication, authorBio, memories) {
                        com.example.data.BookReadinessEvaluator.evaluate(
                            bookTitle = bookTitle,
                            authorName = authorName,
                            dedication = dedication,
                            authorBio = authorBio,
                            memories = memories
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Project Readiness Progress & Next Steps
                        com.example.ui.components.BookReadinessCard(report = readinessReport)

                        // Section 1: Microphone Test & Calibration
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, tint = AmberGold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Microphone Health & Test",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Speak aloud to test input levels for Mike's microphone and verify sensitivity.",
                                    color = LightGrayMuted,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(16.dp))

                                // Visual Level Bar
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Acoustic Input Level", color = LightGrayMuted, fontSize = 12.sp)
                                        Text("${(audioLevel * 100).toInt()}%", color = AmberGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { audioLevel },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(5.dp)),
                                        color = if (audioLevel > 0.7f) CrimsonRecord else EmeraldVoice,
                                        trackColor = MidnightCard
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            isRunningMicTest = !isRunningMicTest
                                            if (isRunningMicTest) {
                                                scope.launch {
                                                    controller.say("Microphone test initiated. Please speak now.")
                                                }
                                            } else {
                                                controller.speech.stop()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isRunningMicTest) CrimsonRecord else AmberGold,
                                            contentColor = DeepNavy
                                        ),
                                        modifier = Modifier.testTag("run_mic_test_button")
                                    ) {
                                        Icon(if (isRunningMicTest) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (isRunningMicTest) "Stop Test" else "Run Mic & Audio Test")
                                    }
                                }
                            }
                        }

                        // Section 2: Book Profile & Metadata
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Book, contentDescription = null, tint = SkyBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Book Title & Author",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = bookTitle,
                                    onValueChange = {
                                        bookTitle = it
                                        settings.bookTitle = it
                                    },
                                    label = { Text("Book Title") },
                                    modifier = Modifier.fillMaxWidth().testTag("book_title_field"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AmberGold,
                                        focusedLabelColor = AmberGold,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedTextColor = OffWhiteText,
                                        unfocusedTextColor = OffWhiteText
                                    )
                                )

                                Spacer(Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = authorName,
                                    onValueChange = {
                                        authorName = it
                                        settings.authorName = it
                                    },
                                    label = { Text("Author / Buddy's Name") },
                                    modifier = Modifier.fillMaxWidth().testTag("author_name_field"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AmberGold,
                                        focusedLabelColor = AmberGold,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedTextColor = OffWhiteText,
                                        unfocusedTextColor = OffWhiteText
                                    )
                                )

                                Spacer(Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = dedication,
                                    onValueChange = {
                                        dedication = it
                                        settings.dedication = it
                                    },
                                    label = { Text("Book Dedication (Front Matter)") },
                                    modifier = Modifier.fillMaxWidth().testTag("dedication_field"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AmberGold,
                                        focusedLabelColor = AmberGold,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedTextColor = OffWhiteText,
                                        unfocusedTextColor = OffWhiteText
                                    )
                                )

                                Spacer(Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = authorBio,
                                    onValueChange = {
                                        authorBio = it
                                        settings.authorBio = it
                                    },
                                    label = { Text("About the Author (Back Matter)") },
                                    modifier = Modifier.fillMaxWidth().testTag("author_bio_field"),
                                    maxLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AmberGold,
                                        focusedLabelColor = AmberGold,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedTextColor = OffWhiteText,
                                        unfocusedTextColor = OffWhiteText
                                    )
                                )
                            }
                        }

                        // Room Chapter Schema Manager Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = AmberGold)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Book Chapter Schema (Room DB)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = OffWhiteText
                                        )
                                    }

                                    Button(
                                        onClick = { showAddChapterDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("add_room_chapter_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("New Chapter", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Organize voice-recorded story passages into distinct database sections.",
                                    color = LightGrayMuted,
                                    fontSize = 13.sp
                                )

                                Spacer(Modifier.height(16.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    roomChapters.forEachIndexed { idx, chap ->
                                        val chapterMemoryCount = memories.count { it.chapter == chap.title }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MidnightCard,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = chap.title,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = OffWhiteText
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = AmberGold.copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = "${chap.status} • ${chap.targetWordCount} words",
                                                                fontSize = 10.sp,
                                                                color = AmberGold,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    if (chap.description.isNotBlank()) {
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(
                                                            text = chap.description,
                                                            fontSize = 12.sp,
                                                            color = LightGrayMuted
                                                        )
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = "$chapterMemoryCount recorded story passages",
                                                        fontSize = 11.sp,
                                                        color = PerspectiveTeal
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            chapterRepo.deleteChapter(chap)
                                                            Toast.makeText(context, "Deleted chapter from Room", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete chapter",
                                                        tint = LightGrayMuted,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showAddChapterDialog) {
                            AlertDialog(
                                onDismissRequest = { showAddChapterDialog = false },
                                containerColor = DarkNavySurface,
                                title = {
                                    Text("Add New Book Chapter Section", color = AmberGold, fontWeight = FontWeight.Bold)
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(
                                            value = newChapterTitle,
                                            onValueChange = { newChapterTitle = it },
                                            label = { Text("Chapter Title (e.g. Chapter 7: Legacy)", color = LightGrayMuted) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AmberGold,
                                                unfocusedBorderColor = BorderSubtle,
                                                focusedTextColor = OffWhiteText,
                                                unfocusedTextColor = OffWhiteText
                                            ),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("new_chapter_title_input")
                                        )

                                        OutlinedTextField(
                                            value = newChapterDescription,
                                            onValueChange = { newChapterDescription = it },
                                            label = { Text("Chapter Description & Theme Focus", color = LightGrayMuted) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AmberGold,
                                                unfocusedBorderColor = BorderSubtle,
                                                focusedTextColor = OffWhiteText,
                                                unfocusedTextColor = OffWhiteText
                                            ),
                                            minLines = 2,
                                            modifier = Modifier.fillMaxWidth().testTag("new_chapter_desc_input")
                                        )

                                        OutlinedTextField(
                                            value = newChapterTargetWordCount,
                                            onValueChange = { newChapterTargetWordCount = it },
                                            label = { Text("Target Word Count", color = LightGrayMuted) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AmberGold,
                                                unfocusedBorderColor = BorderSubtle,
                                                focusedTextColor = OffWhiteText,
                                                unfocusedTextColor = OffWhiteText
                                            ),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("new_chapter_target_words_input")
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (newChapterTitle.isNotBlank()) {
                                                scope.launch {
                                                    val newChap = com.example.data.ChapterEntity(
                                                        title = newChapterTitle.trim(),
                                                        description = newChapterDescription.trim(),
                                                        targetWordCount = newChapterTargetWordCount.toIntOrNull() ?: 2000,
                                                        orderIndex = roomChapters.size
                                                    )
                                                    chapterRepo.insertChapter(newChap)
                                                    showAddChapterDialog = false
                                                    newChapterTitle = ""
                                                    newChapterDescription = ""
                                                    Toast.makeText(context, "Saved chapter section to Room DB", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                                        modifier = Modifier.testTag("confirm_save_chapter_button")
                                    ) {
                                        Text("Save to Room DB", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAddChapterDialog = false }) {
                                        Text("Cancel", color = LightGrayMuted)
                                    }
                                }
                            )
                        }

                        // Section 3: Speech Synthesis & Accessibility Speed
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = EmeraldVoice)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Audible Voice Speed",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Adjust TTS speech rate to suit Mike's auditory processing comfort.",
                                    color = LightGrayMuted,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(16.dp))

                                Text(
                                    text = "Speed: ${"%.2f".format(speechRate)}x (${(speechRate * 100).toInt()}%)",
                                    color = AmberGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Slider(
                                    value = speechRate,
                                    onValueChange = {
                                        speechRate = it
                                        settings.speechRate = it
                                        controller.speech.setSpeechRate(it)
                                    },
                                    valueRange = 0.5f..1.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AmberGold,
                                        activeTrackColor = AmberGold,
                                        inactiveTrackColor = MidnightCard
                                    ),
                                    modifier = Modifier.testTag("speech_rate_slider")
                                )

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            controller.say("Hello Mike. This is how I will read your memories and interview questions.")
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Preview Voice")
                                }

                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                                Spacer(Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Live Dictation Echo",
                                            color = OffWhiteText,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "Read aloud each sentence in real-time as you speak it into your story draft.",
                                            color = LightGrayMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Switch(
                                        checked = liveEchoPlayback,
                                        onCheckedChange = {
                                            liveEchoPlayback = it
                                            settings.liveEchoPlayback = it
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AmberGold,
                                            checkedTrackColor = AmberGold.copy(alpha = 0.4f),
                                            uncheckedThumbColor = LightGrayMuted,
                                            uncheckedTrackColor = MidnightCard
                                        ),
                                        modifier = Modifier.testTag("live_echo_playback_switch")
                                    )
                                }
                            }
                        }

                        // Section: Visual Accessibility & Senior Readability
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FormatSize, contentDescription = null, tint = AmberGold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Visual Accessibility & Senior Readability",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Scale manuscript text and optimize color contrast for effortless reading by seniors.",
                                    color = LightGrayMuted,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(16.dp))

                                Text(
                                    text = "Text Size Scale: ${"%.2f".format(fontSizeScale)}x (${(fontSizeScale * 100).toInt()}%)",
                                    color = AmberGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Slider(
                                    value = fontSizeScale,
                                    onValueChange = {
                                        fontSizeScale = it
                                        settings.fontSizeScale = it
                                    },
                                    valueRange = 1.0f..1.6f,
                                    steps = 2,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AmberGold,
                                        activeTrackColor = AmberGold,
                                        inactiveTrackColor = MidnightCard
                                    ),
                                    modifier = Modifier.testTag("font_size_scale_slider")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Standard (1.0x)", color = LightGrayMuted, fontSize = 11.sp)
                                    Text("Large (1.25x)", color = AmberGold, fontSize = 11.sp)
                                    Text("Extra Large (1.5x)", color = AmberGold, fontSize = 11.sp)
                                }

                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                                Spacer(Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Ultra High Contrast Mode",
                                            color = OffWhiteText,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "Maximize border borders and text contrast for low vision reading.",
                                            color = LightGrayMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Switch(
                                        checked = highContrastMode,
                                        onCheckedChange = {
                                            highContrastMode = it
                                            settings.highContrastMode = it
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AmberGold,
                                            checkedTrackColor = AmberGold.copy(alpha = 0.4f),
                                            uncheckedThumbColor = LightGrayMuted,
                                            uncheckedTrackColor = MidnightCard
                                        ),
                                        modifier = Modifier.testTag("high_contrast_mode_switch")
                                    )
                                }
                            }
                        }

                        // Section 4: Active Chapter
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = AmberGold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Current Chapter Topic",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(12.dp))

                                var expandedChapter by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { expandedChapter = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhiteText),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(currentChapter, fontWeight = FontWeight.SemiBold)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expandedChapter,
                                        onDismissRequest = { expandedChapter = false },
                                        modifier = Modifier.background(MidnightCard)
                                    ) {
                                        chapters.forEach { chap ->
                                            DropdownMenuItem(
                                                text = { Text(chap, color = OffWhiteText) },
                                                onClick = {
                                                    currentChapter = chap
                                                    settings.currentChapter = chap
                                                    expandedChapter = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section 5: Input & Accessibility Mode
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = SkyBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Assistive Navigation Mode",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(12.dp))

                                var expandedMode by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { expandedMode = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhiteText),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(activeInputMode, fontWeight = FontWeight.SemiBold)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expandedMode,
                                        onDismissRequest = { expandedMode = false },
                                        modifier = Modifier.background(MidnightCard)
                                    ) {
                                        inputModes.forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(mode, color = OffWhiteText) },
                                                onClick = {
                                                    activeInputMode = mode
                                                    settings.activeInputMode = mode
                                                    expandedMode = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                    }
                }

                1 -> {
                    // Transcripts & Book Review
                    var selectedChapterFilter by remember { mutableStateOf("All Chapters") }

                    val filteredMemories = remember(memories, searchQuery, selectedChapterFilter) {
                        memories.filter { mem ->
                            val matchesSearch = searchQuery.isBlank() ||
                                mem.transcript.contains(searchQuery, ignoreCase = true) ||
                                (mem.storyArc?.contains(searchQuery, ignoreCase = true) == true) ||
                                (mem.charactersAndPerspectives?.contains(searchQuery, ignoreCase = true) == true) ||
                                (mem.sensoryDetails?.contains(searchQuery, ignoreCase = true) == true)
                            val matchesChapter = selectedChapterFilter == "All Chapters" ||
                                (mem.chapter ?: "Chapter 1: Early Days") == selectedChapterFilter
                            matchesSearch && matchesChapter
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        // Header actions: Export Formats & Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settings.bookTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AmberGold
                                    )
                                )
                                Text(
                                    text = "By ${settings.authorName} • ${filteredMemories.size} of ${memories.size} passages",
                                    style = MaterialTheme.typography.bodySmall.copy(color = LightGrayMuted)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        exportTargetChapter = if (selectedChapterFilter == "All Chapters") null else selectedChapterFilter
                                        showExportDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                                    modifier = Modifier.testTag("export_book_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Export PDF / Text", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Search & Chapter Filter Controls
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search stories, characters, sensory anchors...", color = LightGrayMuted, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LightGrayMuted, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = LightGrayMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberGold,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = OffWhiteText,
                                unfocusedTextColor = OffWhiteText
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("transcript_search_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        // Chapter Filter Chips
                        val allFilterOptions = listOf("All Chapters") + chapters
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allFilterOptions) { chap ->
                                FilterChip(
                                    selected = selectedChapterFilter == chap,
                                    onClick = { selectedChapterFilter = chap },
                                    label = { Text(if (chap.length > 22) chap.take(20) + "…" else chap, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberGold,
                                        selectedLabelColor = DeepNavy,
                                        containerColor = DarkNavySurface,
                                        labelColor = LightGrayMuted
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (selectedChapterFilter == chap) AmberGold else BorderSubtle,
                                        enabled = true,
                                        selected = selectedChapterFilter == chap
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        val reviewReadinessReport = remember(settings.bookTitle, settings.authorName, settings.dedication, settings.authorBio, memories) {
                            com.example.data.BookReadinessEvaluator.evaluate(
                                bookTitle = settings.bookTitle,
                                authorName = settings.authorName,
                                dedication = settings.dedication,
                                authorBio = settings.authorBio,
                                memories = memories
                            )
                        }
                        com.example.ui.components.BookReadinessCard(report = reviewReadinessReport)

                        Spacer(Modifier.height(16.dp))

                        if (memories.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No memories transcribed yet. Switch to Buddy Mode to dictate.",
                                    color = LightGrayMuted,
                                    fontSize = 15.sp
                                )
                            }
                        } else if (filteredMemories.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No memories match your filter criteria.",
                                    color = LightGrayMuted,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            var memoryToEdit by remember { mutableStateOf<Memory?>(null) }
                            var editedTranscript by remember { mutableStateOf("") }
                            var editedChapter by remember { mutableStateOf("") }
                            var showEditDialog by remember { mutableStateOf(false) }

                            if (showEditDialog && memoryToEdit != null) {
                                AlertDialog(
                                    onDismissRequest = { showEditDialog = false },
                                    containerColor = DarkNavySurface,
                                    title = { Text("Edit Story Passage", color = AmberGold, fontWeight = FontWeight.Bold) },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            OutlinedTextField(
                                                value = editedTranscript,
                                                onValueChange = { editedTranscript = it },
                                                label = { Text("Spoken Story Transcript", color = LightGrayMuted) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = AmberGold,
                                                    unfocusedBorderColor = BorderSubtle,
                                                    focusedTextColor = OffWhiteText,
                                                    unfocusedTextColor = OffWhiteText
                                                ),
                                                minLines = 3,
                                                maxLines = 6,
                                                modifier = Modifier.fillMaxWidth().testTag("edit_transcript_input")
                                            )

                                            OutlinedTextField(
                                                value = editedChapter,
                                                onValueChange = { editedChapter = it },
                                                label = { Text("Assigned Chapter", color = LightGrayMuted) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = AmberGold,
                                                    unfocusedBorderColor = BorderSubtle,
                                                    focusedTextColor = OffWhiteText,
                                                    unfocusedTextColor = OffWhiteText
                                                ),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth().testTag("edit_chapter_input")
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                val target = memoryToEdit
                                                if (target != null && editedTranscript.isNotBlank()) {
                                                    scope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            controller.db.memoryDao().update(
                                                                target.copy(
                                                                    transcript = editedTranscript.trim(),
                                                                    chapter = editedChapter.trim()
                                                                )
                                                            )
                                                        }
                                                        Toast.makeText(context, "Memory updated", Toast.LENGTH_SHORT).show()
                                                        showEditDialog = false
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                                            modifier = Modifier.testTag("save_edit_memory_button")
                                        ) {
                                            Text("Save Changes", fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showEditDialog = false }) {
                                            Text("Cancel", color = LightGrayMuted)
                                        }
                                    }
                                )
                            }

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredMemories, key = { it.id }) { memory ->
                                    var showDetails by remember { mutableStateOf(false) }

                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = AmberGold.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = memory.chapter ?: "Prologue",
                                                        color = AmberGold,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }

                                                Row {
                                                    IconButton(
                                                        onClick = {
                                                            memoryToEdit = memory
                                                            editedTranscript = memory.transcript
                                                            editedChapter = memory.chapter ?: "Chapter 1: Early Days"
                                                            showEditDialog = true
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Edit story passage",
                                                            tint = AmberGold,
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { showDetails = !showDetails },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.AutoStories,
                                                            contentDescription = "Show story compartments",
                                                            tint = StoryPurple,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                controller.say(memory.transcript)
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = SkyBlue, modifier = Modifier.size(18.dp))
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                withContext(Dispatchers.IO) {
                                                                    controller.db.memoryDao().delete(memory)
                                                                }
                                                                Toast.makeText(context, "Memory deleted", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = CrimsonRecord, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(8.dp))

                                            Text(
                                                text = memory.transcript,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    color = OffWhiteText,
                                                    lineHeight = 22.sp
                                                )
                                            )

                                            // Extracted Entity Tag Chips
                                            val memoryEntities = remember(memory.transcript) {
                                                com.example.deterministic.EntityRegistryAgent.extractEntities(memory.transcript)
                                            }
                                            if (memoryEntities.isNotEmpty()) {
                                                Spacer(Modifier.height(8.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Entities:", fontSize = 10.sp, color = LightGrayMuted, fontWeight = FontWeight.Bold)
                                                    androidx.compose.foundation.lazy.LazyRow(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        items(memoryEntities) { entity ->
                                                            val chipColor = when (entity.type) {
                                                                com.example.deterministic.EntityType.PERSON -> PerspectiveTeal
                                                                com.example.deterministic.EntityType.PLACE -> SensoryAmber
                                                                com.example.deterministic.EntityType.OBJECT -> SkyBlue
                                                                com.example.deterministic.EntityType.EVENT -> StoryPurple
                                                            }
                                                            Surface(
                                                                shape = RoundedCornerShape(12.dp),
                                                                color = chipColor.copy(alpha = 0.15f),
                                                                border = BorderStroke(1.dp, chipColor.copy(alpha = 0.4f)),
                                                                modifier = Modifier.clickable {
                                                                    searchQuery = entity.name
                                                                }
                                                            ) {
                                                                Text(
                                                                    text = "${entity.name}",
                                                                    color = chipColor,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Detailed Compartments
                                            AnimatedVisibility(visible = showDetails) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

                                                    if (!memory.storyArc.isNullOrBlank()) {
                                                        Text(
                                                            text = "Story Arc: ${memory.storyArc}",
                                                            color = StoryPurple,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    if (!memory.reflection.isNullOrBlank()) {
                                                        Text(
                                                            text = "Narration / Reflection: ${memory.reflection}",
                                                            color = ReflectionBlue,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    if (!memory.charactersAndPerspectives.isNullOrBlank()) {
                                                        Text(
                                                            text = "Perspectives: ${memory.charactersAndPerspectives}",
                                                            color = PerspectiveTeal,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    if (!memory.sensoryDetails.isNullOrBlank()) {
                                                        Text(
                                                            text = "Sensory Setting: ${memory.sensoryDetails}",
                                                            color = SensoryAmber,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    if (!memory.writingTip.isNullOrBlank()) {
                                                        Text(
                                                            text = "Writing Tip: ${memory.writingTip}",
                                                            color = CraftGreen,
                                                            fontSize = 12.sp,
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
                }

                2 -> {
                    // Publishing Readiness & Book Composition
                    val report = remember(memories, bookTitle, authorName, dedication, authorBio) {
                        BookPublishingAuditor.audit(
                            memories = memories,
                            bookTitle = bookTitle,
                            authorName = authorName,
                            dedication = dedication,
                            authorBio = authorBio,
                            allPlannedChapters = chapters
                        )
                    }

                    var editorialCritique by remember { mutableStateOf<String?>(null) }
                    var isAnalyzingEditorial by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Header Score Banner
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Manuscript Publishing Readiness",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = OffWhiteText
                                        )
                                        Text(
                                            text = report.readinessStage,
                                            color = AmberGold,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AmberGold.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "${report.readinessScore}%",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 20.sp,
                                            color = AmberGold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                                LinearProgressIndicator(
                                    progress = { report.readinessScore / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = AmberGold,
                                    trackColor = DarkNavySurface.copy(alpha = 0.6f)
                                )

                                Spacer(Modifier.height(16.dp))

                                // Quick Metrics Grid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MidnightCard),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("Total Words", fontSize = 11.sp, color = LightGrayMuted)
                                            Text("${report.totalWords}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SkyBlue)
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MidnightCard),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("Est. Book Pages", fontSize = 11.sp, color = LightGrayMuted)
                                            Text("${report.estimatedPages} pgs", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EmeraldVoice)
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MidnightCard),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("Chapters Covered", fontSize = 11.sp, color = LightGrayMuted)
                                            Text("${report.totalChaptersWithContent}/${report.targetChaptersCount}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AmberGold)
                                        }
                                    }
                                }
                            }
                        }

                        // Interconnected Knowledge Graph & Character Index
                        val allManuscriptEntities = remember(memories) {
                            val combined = memories.joinToString(" ") { it.transcript }
                            com.example.deterministic.EntityRegistryAgent.extractEntities(combined)
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Hub, contentDescription = null, tint = PerspectiveTeal)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Story Knowledge Graph & Index",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = OffWhiteText
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = PerspectiveTeal.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "${allManuscriptEntities.size} Entities",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PerspectiveTeal,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Tap any person, place, or object to jump directly to all relevant transcript passages in the Book tab.",
                                    color = LightGrayMuted,
                                    fontSize = 12.sp
                                )

                                Spacer(Modifier.height(14.dp))

                                if (allManuscriptEntities.isEmpty()) {
                                    Text(
                                        text = "No characters or places detected yet. Dictate story passages to auto-build the index.",
                                        fontSize = 12.sp,
                                        color = LightGrayMuted
                                    )
                                } else {
                                    val people = allManuscriptEntities.filter { it.type == com.example.deterministic.EntityType.PERSON }
                                    val places = allManuscriptEntities.filter { it.type == com.example.deterministic.EntityType.PLACE }
                                    val objects = allManuscriptEntities.filter { it.type == com.example.deterministic.EntityType.OBJECT }
                                    val events = allManuscriptEntities.filter { it.type == com.example.deterministic.EntityType.EVENT }

                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (people.isNotEmpty()) {
                                            Text("People & Relationships (${people.size}):", fontSize = 11.sp, color = PerspectiveTeal, fontWeight = FontWeight.Bold)
                                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(people) { p ->
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = PerspectiveTeal.copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, PerspectiveTeal.copy(alpha = 0.5f)),
                                                        modifier = Modifier.clickable {
                                                            searchQuery = p.name
                                                            selectedTab = 1
                                                        }
                                                    ) {
                                                        Text(
                                                            text = "${p.name} (${p.mentions}x)",
                                                            fontSize = 11.sp,
                                                            color = OffWhiteText,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (places.isNotEmpty()) {
                                            Text("Places & Settings (${places.size}):", fontSize = 11.sp, color = SensoryAmber, fontWeight = FontWeight.Bold)
                                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(places) { pl ->
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = SensoryAmber.copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, SensoryAmber.copy(alpha = 0.5f)),
                                                        modifier = Modifier.clickable {
                                                            searchQuery = pl.name
                                                            selectedTab = 1
                                                        }
                                                    ) {
                                                        Text(
                                                            text = "${pl.name} (${pl.mentions}x)",
                                                            fontSize = 11.sp,
                                                            color = OffWhiteText,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (objects.isNotEmpty()) {
                                            Text("Objects & Instruments (${objects.size}):", fontSize = 11.sp, color = SkyBlue, fontWeight = FontWeight.Bold)
                                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(objects) { obj ->
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = SkyBlue.copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, SkyBlue.copy(alpha = 0.5f)),
                                                        modifier = Modifier.clickable {
                                                            searchQuery = obj.name
                                                            selectedTab = 1
                                                        }
                                                    ) {
                                                        Text(
                                                            text = "${obj.name} (${obj.mentions}x)",
                                                            fontSize = 11.sp,
                                                            color = OffWhiteText,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (events.isNotEmpty()) {
                                            Text("Milestones & Events (${events.size}):", fontSize = 11.sp, color = StoryPurple, fontWeight = FontWeight.Bold)
                                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(events) { ev ->
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = StoryPurple.copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, StoryPurple.copy(alpha = 0.5f)),
                                                        modifier = Modifier.clickable {
                                                            searchQuery = ev.name
                                                            selectedTab = 1
                                                        }
                                                    ) {
                                                        Text(
                                                            text = "${ev.name} (${ev.mentions}x)",
                                                            fontSize = 11.sp,
                                                            color = OffWhiteText,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Publication Export Formats (Markdown & Formatted Text)
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Publish, contentDescription = null, tint = AmberGold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Publication Formats & Delivery",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Export publication-ready files for typesetting, Amazon KDP, IngramSpark, or print publishers.",
                                    color = LightGrayMuted,
                                    fontSize = 13.sp
                                )

                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            exportTargetChapter = null
                                            showExportDialog = true
                                        },
                                        modifier = Modifier.weight(1f).testTag("export_pdf_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy)
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("PDF Book", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            exportTargetChapter = null
                                            showExportDialog = true
                                        },
                                        modifier = Modifier.weight(1f).testTag("export_proof_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = SkyBlue, contentColor = DeepNavy)
                                    ) {
                                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save Files", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Editorial Acquisitions Critique with Gemini
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, tint = SkyBlue)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Editorial & Literary Critique",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = OffWhiteText
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                isAnalyzingEditorial = true
                                                val critique = controller.interviewer.synthesizePublishingCritique(
                                                    bookTitle = bookTitle,
                                                    author = authorName,
                                                    chaptersCount = report.totalChaptersWithContent,
                                                    totalWords = report.totalWords,
                                                    samplePassages = memories.map { it.transcript }
                                                )
                                                editorialCritique = critique
                                                isAnalyzingEditorial = false
                                            }
                                        },
                                        enabled = !isAnalyzingEditorial,
                                        modifier = Modifier.testTag("request_critique_button"),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = SkyBlue.copy(alpha = 0.2f),
                                            contentColor = SkyBlue
                                        )
                                    ) {
                                        if (isAnalyzingEditorial) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SkyBlue)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Evaluating...", fontSize = 12.sp)
                                        } else {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Consult Editor", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                if (editorialCritique != null) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MidnightCard),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlue.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text(
                                                text = editorialCritique ?: "",
                                                color = OffWhiteText,
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = "— Mike Write Literary & Publishing Advisor",
                                                color = SkyBlue,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Generate professional editorial feedback evaluating pacing, voice authenticity, and narrative flow across your draft.",
                                        color = LightGrayMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // Chapter Completion Breakdown
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = EmeraldVoice)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Chapter Composition Matrix",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(14.dp))

                                chapters.forEach { chap ->
                                    val stat = report.chapterBreakdown[chap] ?: com.example.data.ChapterStats(0, 0, 0)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = chap,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = if (stat.passageCount > 0) OffWhiteText else LightGrayMuted
                                            )
                                            Text(
                                                text = "${stat.passageCount} passages • ${stat.wordCount} words • ~${stat.estimatedReadMinutes} min read",
                                                fontSize = 11.sp,
                                                color = if (stat.passageCount > 0) SkyBlue else LightGrayMuted.copy(alpha = 0.6f)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (stat.passageCount > 0) EmeraldVoice.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
                                        ) {
                                            Text(
                                                text = if (stat.passageCount > 0) "Drafted" else "Empty",
                                                color = if (stat.passageCount > 0) EmeraldVoice else LightGrayMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                                }
                            }
                        }

                        // Recommendations / Publishing Checklist
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Checklist, contentDescription = null, tint = AmberGold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Publishing Checklist",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(12.dp))

                                report.recommendations.forEach { rec ->
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowRight,
                                            contentDescription = null,
                                            tint = AmberGold,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = rec,
                                            color = OffWhiteText,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }

                3 -> {
                    // System Diagnostics & Accessibility Logs
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Live Accessibility & Speech Diagnostics",
                            fontWeight = FontWeight.Bold,
                            color = OffWhiteText,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Inspect voice state triggers, utterance parser events, and accessibility loops.",
                            color = LightGrayMuted,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF070B12)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 8.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                reverseLayout = true
                            ) {
                                items(systemLogs.reversed()) { log ->
                                    Text(
                                        text = "> $log",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = if (log.contains("error", ignoreCase = true)) CrimsonRecord else EmeraldVoice,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                4 -> {
                    // Legal, Compliance, Privacy Policy & Rights Guide
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Section 1: In-App Privacy Disclosure & Audio Data Safety
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldVoice)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Audio & Data Safety Declaration",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Mike Write stores voice transcripts and book chapters directly in an on-device SQLite (Room) database. Audio stream buffers are processed in real-time for speech-to-text dictation and are never sold or used for ad profiling.",
                                    color = LightGrayMuted,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Play Console Data Safety Form Guidance:",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = AmberGold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "• Personal Info (Audio / Voice recordings): Disclose for 'App functionality' (voice dictation & assistive book authoring).\n• Health / Memoir content: Ephemeral capture; all drafts remain under author control.\n• Encryption in transit: All cloud AI follow-up calls utilize standard TLS encryption.",
                                    color = OffWhiteText,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Section 2: Accessibility Service Declaration
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Accessibility, contentDescription = null, tint = SkyBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Play Store Accessibility Declaration",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "• isAccessibilityTool: Set to true in accessibility_service_config.xml.\n• Core Purpose: Solely dedicated to empowering quadriplegic and motor-impaired storytellers to author memoirs hands-free via voice loop and switch hardware.",
                                    color = OffWhiteText,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        // Section 3: Legal Copyright, Releases & Publishing Rights
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Gavel, contentDescription = null, tint = AmberGold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Author Copyright & Rights Protection",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "• Sole Ownership: Copyright belongs entirely to $authorName (© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} $authorName). The app operates strictly as an authoring instrument.\n• Third-Party Release Notice: If recounting intimate memories involving family or third parties, obtain signed personal release forms prior to broad distribution.\n• U.S. Copyright Office Registration: Register the completed manuscript at copyright.gov before public distribution to preserve statutory damage protections.\n• Audiobook Master Rights: Voice synthesis and human narration rights remain 100% with the author.",
                                    color = LightGrayMuted,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        // Section 4: Deterministic Agents & LLM Fallthrough Telemetry
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = EmeraldVoice)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Deterministic Agents & Cost Monitor",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = OffWhiteText
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                val (sessionCalls, totalFallthroughs) = com.example.deterministic.DeterministicRouter.getFallthroughStats()
                                Text(
                                    text = "Active Agents: Cleaner, Segmenter, Entity Registry, Date Normalizer, Topic Tagger, Chapter Assembler, Question Selector, Template Filler, Echo Confirmer, Consistency Checker, Export Formatter, Progress Reporter.\n• Target Split: 80% On-Device Deterministic / 20% LLM Fallthrough\n• Session LLM Calls: $sessionCalls / 20 budget limit\n• Recorded Fallthroughs: $totalFallthroughs",
                                    color = LightGrayMuted,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                    }
                }
                5 -> {
                    // Autonomous Bookwriting Hub
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        com.example.ui.components.AutonomousBookwritingHub(
                            controller = controller,
                            memories = memories
                        )
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        ExportChapterDialog(
            bookTitle = settings.bookTitle,
            authorName = settings.authorName,
            allChapters = chapters,
            memories = memories,
            initialChapter = exportTargetChapter,
            onDismiss = { showExportDialog = false }
        )
    }
}
