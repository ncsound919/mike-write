package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.Interviewer
import com.example.buddy.BuddyScreen
import com.example.caregiver.CaregiverScreen
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.loop.VoiceLoopController
import com.example.speech.AndroidSttEngine
import com.example.speech.SpeechEngine
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {

    private lateinit var loopController: VoiceLoopController
    private lateinit var database: MikeWriteDatabase
    private lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = MikeWriteDatabase.getInstance(this)
        settings = SettingsStore(this)
        val speech = SpeechEngine(this)
        val listener = AndroidSttEngine(this)
        val interviewer = Interviewer()

        loopController = VoiceLoopController(
            context = this,
            speech = speech,
            listener = listener,
            db = database,
            interviewer = interviewer,
            settings = settings
        )

        setContent {
            MikeWriteTheme {
                MainAppHost(
                    controller = loopController,
                    database = database
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            loopController.start()
        }
    }

    override fun onPause() {
        super.onPause()
        loopController.stopEverything()
    }

    override fun onDestroy() {
        super.onDestroy()
        loopController.destroy()
    }
}

enum class ActiveScreen {
    BUDDY,
    SETTINGS
}

@Composable
fun MainAppHost(
    controller: VoiceLoopController,
    database: MikeWriteDatabase
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(ActiveScreen.BUDDY) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            controller.start()
        }
    }

    var hasConsented by remember { mutableStateOf(controller.settings.hasConsentedToAudioProcessing) }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (hasConsented) {
            controller.start()
        }
    }

    val memories by database.memoryDao().getAllMemoriesDesc().collectAsStateWithLifecycle(initialValue = emptyList())

    if (!hasAudioPermission) {
        // High accessibility permission request screen
        Scaffold(
            containerColor = DeepNavy
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(45.dp))
                        .background(AmberGold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Microphone Access Required",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = OffWhiteText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Mike Write is an audible book-writing companion designed for hands-free storytelling. Please grant microphone access so your voice can be transcribed seamlessly.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = LightGrayMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("grant_mic_permission_button")
                ) {
                    Text(
                        text = "Enable Microphone",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else if (!hasConsented) {
        // Explicit In-App Audio & Privacy Consent Gate
        Scaffold(
            containerColor = DeepNavy
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(EmeraldVoice.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = EmeraldVoice,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Voice Recording & Privacy Consent",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = OffWhiteText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text(
                        text = "Mike Write records your voice solely to transcribe your memories into your private memoir. All drafts are stored on your device. Follow-up interview questions are processed with end-to-end encryption. Your voice recordings are never sold or shared with advertisers.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = LightGrayMuted,
                            lineHeight = 22.sp
                        ),
                        modifier = Modifier.padding(18.dp)
                    )
                }

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = {
                        controller.settings.hasConsentedToAudioProcessing = true
                        hasConsented = true
                        controller.start()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = DeepNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("accept_privacy_consent_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "I Agree & Consent",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                ActiveScreen.BUDDY -> {
                    BuddyScreen(
                        controller = controller,
                        memories = memories,
                        onOpenSettings = { currentScreen = ActiveScreen.SETTINGS }
                    )
                }
                ActiveScreen.SETTINGS -> {
                    CaregiverScreen(
                        controller = controller,
                        memories = memories,
                        onBackToBuddy = { currentScreen = ActiveScreen.BUDDY }
                    )
                }
            }
        }
    }
}
