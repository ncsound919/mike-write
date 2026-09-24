package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.loop.VoiceLoopController
import com.example.ui.theme.*
import kotlinx.coroutines.launch

data class TourStep(
    val stepNumber: Int,
    val totalSteps: Int,
    val badge: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val spokenNarration: String,
    val bullets: List<TourBullet>,
    val tryActionLabel: String? = null,
    val onTryAction: (suspend () -> Unit)? = null
)

data class TourBullet(
    val icon: ImageVector,
    val headline: String,
    val description: String,
    val tagColor: Color = AmberGold
)

/**
 * Onboarding Tour Guide & Interactive Walkthrough for first-time authors and caregivers.
 * Features senior-accessible high contrast typography, audible TTS narration for each step,
 * visual step illustrations, interactive "Try It Now" shortcuts, and persistent completion state.
 */
@Composable
fun OnboardingTourGuideDialog(
    controller: VoiceLoopController,
    onDismiss: () -> Unit,
    fontScale: Float = 1.0f
) {
    val scope = rememberCoroutineScope()
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var isNarratingAloud by remember { mutableStateOf(true) }

    val steps = remember(controller) {
        listOf(
            TourStep(
                stepNumber = 1,
                totalSteps = 5,
                badge = "WELCOME TO MIKE WRITE",
                title = "Your Personal Memoir Companion",
                subtitle = "Turn everyday spoken memories into a beautifully structured, published memoir — completely hands-free.",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                iconColor = AmberGold,
                spokenNarration = "Welcome to Mike Write. I am your personal voice biographer. You speak naturally, and I will capture, structure, and polish your life stories into a magnificent memoir.",
                bullets = listOf(
                    TourBullet(
                        icon = Icons.Default.Mic,
                        headline = "Speak Naturally",
                        description = "Dictate childhood stories, career milestones, family wisdom, or turning points without touching a keyboard."
                    ),
                    TourBullet(
                        icon = Icons.Default.AutoAwesome,
                        headline = "7-Agent Writing Engine",
                        description = "Our deterministic pipeline automatically weaves your stories, harmonizes tone, and organizes chapters."
                    ),
                    TourBullet(
                        icon = Icons.Default.OfflineBolt,
                        headline = "100% Offline & Private",
                        description = "Everything runs securely on your device with local Room database persistence and optional Gemini AI enhancements."
                    )
                )
            ),
            TourStep(
                stepNumber = 2,
                totalSteps = 5,
                badge = "VOICE ENGINE & COMMANDS",
                title = "The Big Voice Companion Orb",
                subtitle = "Control the entire studio with simple voice commands or by tapping the luminous center orb.",
                icon = Icons.Default.GraphicEq,
                iconColor = EmeraldVoice,
                spokenNarration = "Step 2: The Voice Companion. Tap the glowing center orb or simply say 'Record' to start dictating. When you finish, say 'Done'. You can also say 'Prompt me' for interview questions or 'Review' to hear your book aloud.",
                bullets = listOf(
                    TourBullet(
                        icon = Icons.Default.Mic,
                        headline = "Say \"Record\" or Tap Orb",
                        description = "Begins capturing your spoken story with real-time waveform visualization.",
                        tagColor = EmeraldVoice
                    ),
                    TourBullet(
                        icon = Icons.Default.CheckCircle,
                        headline = "Say \"Done\" or \"Save\"",
                        description = "Finalizes your passage, applies literary formatting, and attaches it to the active chapter.",
                        tagColor = AmberGold
                    ),
                    TourBullet(
                        icon = Icons.Default.Psychology,
                        headline = "Say \"Prompt me\"",
                        description = "Mike Write asks you an insightful question about your childhood, career, family, or lessons learned.",
                        tagColor = StoryPurple
                    ),
                    TourBullet(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        headline = "Say \"Review\" or \"Playback\"",
                        description = "Listen to your written manuscript read aloud like a personal audiobook.",
                        tagColor = SkyBlue
                    )
                ),
                tryActionLabel = "Try 'Prompt me' Question",
                onTryAction = {
                    controller.handleUtterance("Prompt me")
                }
            ),
            TourStep(
                stepNumber = 3,
                totalSteps = 5,
                badge = "ACCESSIBILITY & ADAPTIVE MODES",
                title = "Built for Every Physical Ability",
                subtitle = "Choose from four distinct input modalities tailored for seniors, tremors, low vision, and motor paralysis.",
                icon = Icons.Default.AccessibilityNew,
                iconColor = SkyBlue,
                spokenNarration = "Step 3: Universal Accessibility. Mike Write features four input modes: Hands-Free Voice Loop, Push to Talk, Physical Switch Scanning, and Eye Gaze Dwell selection.",
                bullets = listOf(
                    TourBullet(
                        icon = Icons.Default.MicNone,
                        headline = "Voice Loop (Always Listening)",
                        description = "Zero physical touch required. Uses wake commands and continuous conversational turn-taking.",
                        tagColor = EmeraldVoice
                    ),
                    TourBullet(
                        icon = Icons.Default.TouchApp,
                        headline = "Push-to-Talk",
                        description = "Simple tactile control: tap once to record, tap again to save your memory.",
                        tagColor = SkyBlue
                    ),
                    TourBullet(
                        icon = Icons.Default.Sensors,
                        headline = "Switch Scan Assist (Hardware Switches)",
                        description = "Auto-scans UI options. Confirm with a single external switch or your Volume Down button.",
                        tagColor = AmberGold
                    ),
                    TourBullet(
                        icon = Icons.Default.Visibility,
                        headline = "Eye Gaze Dwell Mode",
                        description = "Gaze or hover on any button for 1.8 seconds to trigger actions without clicking.",
                        tagColor = PerspectiveTeal
                    )
                )
            ),
            TourStep(
                stepNumber = 4,
                totalSteps = 5,
                badge = "LITERARY BREAKDOWN & AUTONOMY",
                title = "Smart Manuscript Weaver",
                subtitle = "Watch your raw spoken words transform into compartmentalized literary craft elements.",
                icon = Icons.Default.AutoAwesome,
                iconColor = StoryPurple,
                spokenNarration = "Step 4: Autonomous Writing Pipeline. Every passage is analyzed for story arcs, inner reflection, multiple perspectives, sensory atmosphere, and craft suggestions.",
                bullets = listOf(
                    TourBullet(
                        icon = Icons.Default.AutoStories,
                        headline = "Literary Breakdown",
                        description = "Each card shows Story Arc, Inner Narration, People & Perspectives, and Sensory Atmosphere.",
                        tagColor = StoryPurple
                    ),
                    TourBullet(
                        icon = Icons.Default.Timeline,
                        headline = "Chronological Weaver",
                        description = "Automatically chronologizes and detects narrative gaps across chapters.",
                        tagColor = ReflectionBlue
                    ),
                    TourBullet(
                        icon = Icons.Default.Brush,
                        headline = "Voice Harmonizer",
                        description = "Preserves your unique authentic voice while eliminating repetition and awkward filler phrases.",
                        tagColor = CraftGreen
                    )
                ),
                tryActionLabel = "Run Auto-Write Demo",
                onTryAction = {
                    controller.runUnifiedPipeline()
                }
            ),
            TourStep(
                stepNumber = 5,
                totalSteps = 5,
                badge = "STUDIO & PUBLISHING",
                title = "Export, Audiobooks & Caregiver Hub",
                subtitle = "Share your finished memoir with your family or export to PDF, Text, and Audio formats.",
                icon = Icons.Default.WorkspacePremium,
                iconColor = AmberGold,
                spokenNarration = "Step 5: Studio and Publishing. Open the Studio button at any time to organize chapters, export print-ready manuscripts, generate audiobooks, or adjust senior font sizes. You are now ready to begin writing!",
                bullets = listOf(
                    TourBullet(
                        icon = Icons.Default.Download,
                        headline = "Multi-Format Export",
                        description = "Export individual chapters or the complete book as PDF, Text, and formatted Markdown.",
                        tagColor = AmberGold
                    ),
                    TourBullet(
                        icon = Icons.Default.Hearing,
                        headline = "Audiobook Player",
                        description = "Play back your entire book with variable speeds (0.8x, 1.0x, 1.2x) and chapter seeking.",
                        tagColor = SkyBlue
                    ),
                    TourBullet(
                        icon = Icons.Default.FormatSize,
                        headline = "Senior Text Size Stepper",
                        description = "Tap the 'A / A+ / A++' button in the top bar anytime to adjust text scaling to your comfort.",
                        tagColor = EmeraldVoice
                    )
                )
            )
        )
    }

    val step = steps[currentStepIndex]

    // Read step aloud automatically if narration is enabled
    LaunchedEffect(currentStepIndex, isNarratingAloud) {
        if (isNarratingAloud) {
            controller.speech.speak(step.spokenNarration)
        }
    }

    Dialog(
        onDismissRequest = {
            controller.speech.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
            border = BorderStroke(1.5.dp, step.iconColor.copy(alpha = 0.8f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp)
                .testTag("onboarding_tour_guide_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Tour Navigation Bar & Progress Indicator
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = step.iconColor.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, step.iconColor.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "STEP ${step.stepNumber} OF ${step.totalSteps}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = step.iconColor,
                                    letterSpacing = 1.2.sp
                                ),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // TTS Narration Toggle
                            IconButton(
                                onClick = {
                                    isNarratingAloud = !isNarratingAloud
                                    if (!isNarratingAloud) {
                                        controller.speech.stop()
                                    } else {
                                        scope.launch {
                                            controller.speech.speak(step.spokenNarration)
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isNarratingAloud) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Toggle Narration",
                                    tint = if (isNarratingAloud) step.iconColor else LightGrayMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Close / Skip Button
                            TextButton(
                                onClick = {
                                    controller.speech.stop()
                                    onDismiss()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Skip Tour",
                                    color = LightGrayMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = (12 * fontScale).sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Step Progress Dots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 0 until step.totalSteps) {
                            val isCurrent = i == currentStepIndex
                            val isCompleted = i < currentStepIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when {
                                            isCurrent -> step.iconColor
                                            isCompleted -> step.iconColor.copy(alpha = 0.5f)
                                            else -> BorderSubtle
                                        }
                                    )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Step Content Body (Scrollable)
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "tour_step_content"
                ) { currentTourStep ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Illustrated Icon Badge
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(currentTourStep.iconColor.copy(alpha = 0.25f), MidnightCard)
                                    )
                                )
                                .border(2.dp, currentTourStep.iconColor, CircleShape)
                        ) {
                            Icon(
                                imageVector = currentTourStep.icon,
                                contentDescription = null,
                                tint = currentTourStep.iconColor,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = currentTourStep.badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = currentTourStep.iconColor,
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = currentTourStep.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Serif,
                                color = OffWhiteText,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            fontSize = (20 * fontScale).sp
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = currentTourStep.subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = LightGrayMuted,
                                textAlign = TextAlign.Center
                            ),
                            fontSize = (13 * fontScale).sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(Modifier.height(14.dp))

                        // Bullets List
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            currentTourStep.bullets.forEach { bullet ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MidnightCard,
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(bullet.tagColor.copy(alpha = 0.15f))
                                                .border(1.dp, bullet.tagColor.copy(alpha = 0.5f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = bullet.icon,
                                                contentDescription = null,
                                                tint = bullet.tagColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bullet.headline,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    color = OffWhiteText,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                fontSize = (13 * fontScale).sp
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = bullet.description,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = LightGrayMuted,
                                                    lineHeight = (17 * fontScale).sp
                                                ),
                                                fontSize = (11.5 * fontScale).sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Optional "Try It Now" Action
                        if (currentTourStep.tryActionLabel != null && currentTourStep.onTryAction != null) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        currentTourStep.onTryAction.invoke()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, currentTourStep.iconColor),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = currentTourStep.iconColor
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tour_try_action_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = currentTourStep.tryActionLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (12.5 * fontScale).sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Bottom Step Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = {
                                controller.speech.stop()
                                currentStepIndex--
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhiteText),
                            modifier = Modifier.testTag("tour_previous_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Step",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Back",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (13 * fontScale).sp
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            controller.speech.stop()
                            if (currentStepIndex < steps.size - 1) {
                                currentStepIndex++
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = step.iconColor,
                            contentColor = DeepNavy
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("tour_next_button")
                    ) {
                        Text(
                            text = if (currentStepIndex < steps.size - 1) "Next Step" else "Start Writing Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = (13.5 * fontScale).sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (currentStepIndex < steps.size - 1) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
                            contentDescription = "Next Step",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
