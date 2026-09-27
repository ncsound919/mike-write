package com.example.loop

import android.content.Context
import com.example.ai.AiConfigProvider
import com.example.ai.AiTextEngine
import com.example.ai.Interviewer
import com.example.ai.JevClient
import com.example.ai.RewordingEngine
import com.example.autonomous.AutonomousExpansionEngine
import com.example.autonomous.AutonomousManuscriptWeaver
import com.example.autonomous.AutonomousStyleHarmonizer
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.feedback.AudioHapticFeedback
import com.example.data.BookChapters
import com.example.speech.Command
import com.example.speech.CommandParser
import com.example.speech.ListeningEngine
import com.example.speech.RecordingStopDetector
import com.example.speech.SpeechEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VoiceLoopController(
    private val context: Context,
    val speech: SpeechEngine,
    val listener: ListeningEngine,
    val db: MikeWriteDatabase,
    val interviewer: Interviewer,
    val settings: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val feedback = AudioHapticFeedback(context)
    val rewordEngine = RewordingEngine(
        AiTextEngine { AiConfigProvider.from(settings) },
        JevClient { AiConfigProvider.from(settings) }
    )
    private val stateMutex = Mutex()
    @Volatile
    var isRunning: Boolean = false
        private set
    @Volatile
    var isRecording: Boolean = false
        private set

    /** True while the author has explicitly paused listening; nothing re-arms the mic. */
    @Volatile
    var isPaused: Boolean = false
        private set

    // finishRecording can be triggered concurrently (volume switch, tap, and the
    // final STT result). Without this guard the same draft could be saved twice.
    @Volatile
    private var isFinalizingRecording: Boolean = false

    @Volatile
    private var lastPrompt: String = "Say record to dictate a story. Review to hear your book. Prompt me for an interview question. Or say breakdown to explore your story elements."
    @Volatile
    private var pendingTranscript: String? = null
    @Volatile
    private var latestPartialText: String? = null
    @Volatile
    private var isAwaitingConfirmation: Boolean = false
    // AI reword pending state
    @Volatile
    private var isAwaitingReword: Boolean = false
    @Volatile
    private var pendingRewordText: String? = null
    @Volatile
    private var pendingRewordMemoryId: Long? = null
    @Volatile
    private var pendingRewordIsDraft: Boolean = false
    @Volatile
    private var currentReviewIndex: Int = 0
    @Volatile
    private var cachedReviewList: List<Memory> = emptyList()

    // Undo action snapshot
    @Volatile
    private var lastSavedMemory: Memory? = null
    @Volatile
    private var lastDiscardedTranscript: String? = null

    init {
        scope.launch {
            VoiceLoopBus.switchActions.collect { action ->
                handleSwitchAction(action)
            }
        }
    }

    private suspend fun handleSwitchAction(action: com.example.loop.AccessibilitySwitchAction) {
        VoiceLoopBus.appendLog("Handling Switch Action: $action")
        when (action) {
            com.example.loop.AccessibilitySwitchAction.TOGGLE_RECORD_OR_CONFIRM -> {
                if (isPaused) {
                    resumeListening()
                } else if (isAwaitingConfirmation) {
                    confirmSave()
                } else if (isRecording) {
                    finishRecording()
                } else if (listener.isListening) {
                    // Already mid-turn; ignore extra presses.
                } else {
                    // Idle: this is the wake trigger for a voice-command turn.
                    wakeListening()
                }
            }
            com.example.loop.AccessibilitySwitchAction.STOP_OR_CANCEL -> {
                if (isAwaitingConfirmation) {
                    confirmDelete()
                } else if (isRecording) {
                    isRecording = false
                    listener.stop()
                    feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)
                    say("Recording cancelled.")
                    listen()
                } else {
                    speech.stop()
                    VoiceLoopBus.publish(LoopState.Idle)
                }
            }
            com.example.loop.AccessibilitySwitchAction.NEXT_ITEM -> {
                advanceReview(1)
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        VoiceLoopBus.appendLog("Voice loop started")
        scope.launch {
            // Await real TTS engine readiness asynchronously
            speech.awaitReady()
            mainMenu()
        }
    }

    fun stopEverything() {
        isRunning = false
        isRecording = false
        isPaused = false
        latestPartialText = null
        isAwaitingReword = false
        pendingRewordText = null
        pendingRewordMemoryId = null
        pendingRewordIsDraft = false
        speech.stop()
        listener.stop()
        feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)
        VoiceLoopBus.publish(LoopState.Idle)
        VoiceLoopBus.appendLog("Voice loop stopped")
    }

    /** Stops the mic and stays idle until the author resumes via orb/switch. */
    fun pauseListening() {
        isPaused = true
        listener.stop()
        speech.stop()
        feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)
        VoiceLoopBus.publish(LoopState.Idle)
        VoiceLoopBus.appendLog("Voice loop paused by author")
    }

    fun resumeListening() {
        if (!isRunning) {
            start()
            return
        }
        isPaused = false
        VoiceLoopBus.appendLog("Voice loop resumed")
        listen()
    }

    /** Wake trigger: opens a single listening turn (volume key / switch). */
    fun wakeListening() {
        if (!isRunning) {
            start()
            return
        }
        if (isPaused) isPaused = false
        if (!isRecording && !listener.isListening) {
            VoiceLoopBus.appendLog("Wake: opening a listening turn")
            listen()
        }
    }

    /** Idle silence: stop the mic and stay silent until the next wake trigger. */
    private fun idleAfterSilence() {
        if (isRecording) return
        listener.stop()
        VoiceLoopBus.publish(LoopState.Idle)
        VoiceLoopBus.appendLog("Idle: microphone off (wake with the volume key)")
    }

    fun stopAudiobookPlayback() {
        speech.stop()
        VoiceLoopBus.publish(LoopState.Idle)
        feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
        if (isRunning) {
            listen()
        }
    }

    suspend fun say(text: String) {
        lastPrompt = text
        VoiceLoopBus.publish(LoopState.Speaking(text))
        speech.speak(text)
    }

    private fun listen() {
        if (!isRunning || isPaused) return
        VoiceLoopBus.publish(LoopState.Listening("Listening for voice commands..."))
        listener.start(
            continuous = false,
            onPartial = { partial ->
                VoiceLoopBus.appendLog("Partial heard: $partial")
                // Instant Barge-In detection
                if (partial.contains("stop", ignoreCase = true) || partial.contains("quiet", ignoreCase = true)) {
                    speech.stop()
                    feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)
                }
            },
            onResult = { utterance ->
                scope.launch {
                    handleUtterance(utterance)
                }
            },
            onError = { err ->
                scope.launch {
                    if (isRunning) {
                        VoiceLoopBus.appendLog("STT pause or error: $err")
                        // Benign silence / timeouts are not "not understood" and must
                        // not fire the NACK earcon — that harsh buzz on every quiet
                        // moment was the odd feedback reported when stopping.
                        val benign = err.contains("silence", true) ||
                            err.contains("timeout", true) ||
                            err.contains("no match", true) ||
                            err.contains("no speech", true) ||
                            err.contains("empty", true)
                        if (!benign) {
                            feedback.playFeedback(AudioHapticFeedback.Cue.NOT_UNDERSTOOD)
                        }
                        if (settings.alwaysListening) {
                            // Opt-in: back off, then re-arm the recognizer.
                            delay(1200)
                            listen()
                        } else {
                            // Default: go quiet. The recognizer's own chime would
                            // otherwise repeat forever while idle.
                            idleAfterSilence()
                        }
                    }
                }
            }
        )
    }

    suspend fun handleUtterance(utterance: String) {
        val command = CommandParser.parse(utterance)
        VoiceLoopBus.appendLog("Parsed command: $command (from '$utterance')")

        when (command) {
            Command.HELP -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.HELP_TRIGGERED)
                say("You can say: record, done, review, reword, breakdown, writing tip, prompt me, chapter, book, save, undo, delete, repeat, slower, or faster.")
                listen()
            }
            Command.RECORD -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.START_RECORDING)
                beginRecording()
            }
            Command.DONE -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)
                finishRecording()
            }
            Command.REVIEW -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                reviewLatest()
            }
            Command.NEXT -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                advanceReview(1)
            }
            Command.BACK -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                advanceReview(-1)
            }
            Command.DECONSTRUCT -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                readStoryBreakdown()
            }
            Command.REWORD -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                rewordTarget()
            }
            Command.TIP -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                readWritingTip()
            }
            Command.REPEAT -> {
                say(lastPrompt)
                listen()
            }
            Command.PROMPT -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                requestAiInterviewPrompt()
            }
            Command.CHAPTER -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                cycleChapter()
            }
            Command.BOOK -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                readBookSummary()
            }
            Command.READINESS -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                readPublishingReadiness()
            }
            Command.EXPORT -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                exportBookPrompt()
            }
            Command.PLAYBACK -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                playbackLiveDraft()
            }
            Command.AUTO_SEQUENCE -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                runAutoSequence()
            }
            Command.FIND_GAPS -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                runFindGaps()
            }
            Command.HARMONIZE -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                runHarmonizeVoice()
            }
            Command.UNIFIED_PIPELINE -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.BUTTON_TAP)
                runUnifiedPipeline()
            }
            Command.SLOWER -> {
                val newRate = (settings.speechRate - 0.15f).coerceAtLeast(0.6f)
                settings.speechRate = newRate
                speech.setSpeechRate(newRate)
                say("Speaking slower. Speech rate set to ${(newRate * 100).toInt()} percent.")
                listen()
            }
            Command.FASTER -> {
                val newRate = (settings.speechRate + 0.15f).coerceAtMost(1.5f)
                settings.speechRate = newRate
                speech.setSpeechRate(newRate)
                say("Speaking faster. Speech rate set to ${(newRate * 100).toInt()} percent.")
                listen()
            }
            Command.STOP -> {
                if (isRecording) {
                    feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)
                    finishRecording()
                } else {
                    // Pause for real: stop the mic and DO NOT re-arm it.
                    pauseListening()
                    say("Paused. Tap the microphone or press the volume key when you want to start again.")
                    VoiceLoopBus.publish(LoopState.Idle)
                }
            }
            Command.UNDO -> {
                handleUndo()
            }
            Command.SAVE, Command.YES -> {
                if (isAwaitingReword) applyReword() else confirmSave()
            }
            Command.DELETE, Command.NO -> {
                if (isAwaitingReword) discardReword() else confirmDelete()
            }
            Command.UNKNOWN -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.NOT_UNDERSTOOD)
                // If we are awaiting confirmation and user speaks something else, guide them gently
                if (isAwaitingReword) {
                    say("I heard: $utterance. Say save to use the reworded passage, or delete to keep your original.")
                    listen()
                } else if (isAwaitingConfirmation) {
                    say("I heard: $utterance. Say save to keep it, or delete to discard.")
                    listen()
                } else {
                    say("I heard: $utterance. Say record to dictate, review to listen, or breakdown for literary insights.")
                    listen()
                }
            }
        }
    }

    private suspend fun handleUndo() = stateMutex.withLock {
        feedback.playFeedback(AudioHapticFeedback.Cue.ACTION_UNDONE)
        when {
            // Case 1: Pending unconfirmed draft exists -> discard it
            isAwaitingConfirmation && pendingTranscript != null -> {
                lastDiscardedTranscript = pendingTranscript
                pendingTranscript = null
                isAwaitingConfirmation = false
                say("Scratch that. Unsaved draft cleared. Say record to try again.")
                listen()
            }
            // Case 2: Just saved a memory -> remove from database and restore as pending draft
            lastSavedMemory != null -> {
                val mem = lastSavedMemory!!
                withContext(Dispatchers.IO) {
                    db.memoryDao().deleteById(mem.id)
                }
                pendingTranscript = mem.transcript
                isAwaitingConfirmation = true
                lastSavedMemory = null
                say("Undone. Removed ${mem.transcript.take(30)} from your book and restored it to active draft. Say save to keep or delete to discard.")
                listen()
            }
            // Case 3: Just discarded a draft -> restore it
            lastDiscardedTranscript != null -> {
                pendingTranscript = lastDiscardedTranscript
                lastDiscardedTranscript = null
                isAwaitingConfirmation = true
                say("Restored discarded draft: $pendingTranscript. Say save to keep it, or delete to discard.")
                listen()
            }
            else -> {
                say("Nothing to undo. Say record to dictate or help for commands.")
                listen()
            }
        }
    }

    private suspend fun mainMenu() {
        say("Mike Write is ready. Tap the microphone to record, or press the volume key, then say record, review, or prompt me. The microphone sleeps when you are quiet.")
        listen()
    }

    // ---- Recording Flow with Multi-Sentence Dictation ----

    suspend fun beginRecording() {
        // Toggle: if already recording, finish instead. Done before taking the
        // mutex because finishRecording() itself calls confirmSave(), which also
        // acquires stateMutex (it is not reentrant).
        if (isRecording) {
            finishRecording()
            return
        }
        stateMutex.withLock {
            if (isRecording) return
            isRecording = true
            isPaused = false
            pendingTranscript = null
            latestPartialText = null
            isAwaitingConfirmation = false
            isAwaitingReword = false
            pendingRewordText = null
            pendingRewordMemoryId = null
            pendingRewordIsDraft = false
        }
        listener.stop()
        speech.stop()

        val currentChap = settings.currentChapter
        feedback.playFeedback(AudioHapticFeedback.Cue.START_RECORDING)
        VoiceLoopBus.publish(LoopState.Recording(System.currentTimeMillis(), "Listening to your story..."))
        say("Recording memory in $currentChap. Speak your story freely. Say done or tap when finished.")

        // Listen continuously for user speech until they say "done", "stop", tap, or pause
        listener.start(
            continuous = true,
            onPartial = { partial ->
                val trimmed = partial.trim()
                latestPartialText = trimmed
                val fullPreview = if (pendingTranscript.isNullOrBlank()) trimmed else "$pendingTranscript $trimmed"
                VoiceLoopBus.publish(LoopState.Recording(System.currentTimeMillis(), fullPreview))
                // Partials are shown live only. They never end dictation and never
                // delete words: "we finished the meal" is story, not a command.
            },
            onResult = { resultSegment ->
                scope.launch {
                    val trimmed = resultSegment.trim()
                    if (trimmed.isBlank()) return@launch

                    val hasStop = RecordingStopDetector.isStopCommand(trimmed) ||
                                  CommandParser.parse(trimmed) == Command.DONE ||
                                  CommandParser.parse(trimmed) == Command.STOP

                    val storyPart = if (hasStop) {
                        RecordingStopDetector.stripTrailingStopPhrase(trimmed)
                    } else {
                        trimmed
                    }
                    if (storyPart.isNotBlank()) {
                        pendingTranscript = if (pendingTranscript.isNullOrBlank()) {
                            storyPart
                        } else {
                            "$pendingTranscript $storyPart"
                        }
                    }
                    latestPartialText = null

                    if (hasStop) {
                        finishRecording()
                    } else {
                        VoiceLoopBus.publish(LoopState.Recording(System.currentTimeMillis(), pendingTranscript.orEmpty()))

                        // If live echo playback is enabled, read back the captured phrase
                        if (settings.liveEchoPlayback && storyPart.isNotBlank()) {
                            VoiceLoopBus.appendLog("Live sentence playback: '$storyPart'")
                            speech.speak(storyPart)
                        }
                    }
                }
            },
            onError = { err ->
                scope.launch {
                    if (!isRecording) return@launch
                    VoiceLoopBus.appendLog("Recording STT error/signal: $err")
                    when (err) {
                        "silence_timeout", "silence_or_timeout" -> {
                            val hasContent = !pendingTranscript.isNullOrBlank() || !latestPartialText.isNullOrBlank()
                            if (hasContent) {
                                VoiceLoopBus.appendLog("Auto-completing recording after natural speech silence")
                                finishRecording()
                            } else {
                                isRecording = false
                                say("I did not hear a story. Say record whenever you want to dictate.")
                                listen()
                            }
                        }
                        "initial_silence_timeout" -> {
                            isRecording = false
                            say("I did not hear a story. Say record whenever you are ready.")
                            listen()
                        }
                        else -> {
                            val hasContent = !pendingTranscript.isNullOrBlank() || !latestPartialText.isNullOrBlank()
                            if (hasContent) {
                                finishRecording()
                            } else {
                                isRecording = false
                                feedback.playFeedback(AudioHapticFeedback.Cue.NOT_UNDERSTOOD)
                                listen()
                            }
                        }
                    }
                }
            }
        )
    }

    suspend fun finishRecording() {
        if (isFinalizingRecording) return
        if (!isRecording && pendingTranscript.isNullOrBlank() && latestPartialText.isNullOrBlank()) {
            return
        }
        isFinalizingRecording = true
        try {
            finishRecordingInternal()
        } finally {
            isFinalizingRecording = false
        }
    }

    private suspend fun finishRecordingInternal() {
        isRecording = false
        listener.stop()
        feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)

        // Integrate any in-flight partial text so no words are dropped. Only an
        // explicit trailing phrase is removed here; bare words like "done" are
        // never stripped from a story sentence.
        val partial = RecordingStopDetector.stripExplicitTrailingPhrase(latestPartialText?.trim().orEmpty())
        if (partial.isNotBlank()) {
            if (pendingTranscript.isNullOrBlank()) {
                pendingTranscript = partial
            } else if (!pendingTranscript!!.contains(partial)) {
                pendingTranscript = "$pendingTranscript $partial"
            }
        }
        latestPartialText = null

        val text = pendingTranscript?.trim()

        if (text.isNullOrBlank()) {
            say("I did not catch anything. Say record to tell your story.")
            listen()
            return
        }

        // Apply Agent 1 (Cleaner) deterministically
        val cleanedText = com.example.deterministic.CleanerAgent.clean(text)
        pendingTranscript = if (cleanedText.isNotBlank()) cleanedText else text

        // Unified Automation: If Smart Auto Save is enabled, seamlessly transition straight to formatting & saving
        // without blocking senior/paralyzed authors on manual verbal confirmation, while announcing and allowing undo anytime!
        if (settings.smartAutoSave) {
            VoiceLoopBus.appendLog("Unified Automation: Smart Auto-Save automatically proceeding to confirmSave()")
            confirmSave()
        } else {
            isAwaitingConfirmation = true
            val echoMsg = com.example.deterministic.DeterministicWriterEngine.buildEchoConfirmation(pendingTranscript!!)
            say(echoMsg)
            listen()
        }
    }

    suspend fun confirmSave() = stateMutex.withLock {
        val text = pendingTranscript
        if (text.isNullOrBlank()) {
            say("There is nothing pending to save. Say record to dictate.")
            listen()
            return@withLock
        }

        VoiceLoopBus.publish(LoopState.Processing("Formatting prose and analyzing story elements..."))
        val chapter = settings.currentChapter

        try {
            // Fetch existing Room chapters for classification
            val existingRoomChapters = withContext(Dispatchers.IO) {
                db.chapterDao().getAllChapters().firstOrNull()?.map { it.title } ?: emptyList()
            }

            // Compartmentalize dictated story, format prose, and detect chapter placement/creation
            val elements = interviewer.analyzeBookElements(text, chapter, existingRoomChapters)
            // Stay silent if the author stopped mid-analysis.
            if (!isRunning) return@withLock

            // Automated Chapter Creation Integration
            var isAutoCreated = false
            val finalChapter = if (elements.assignedChapter.isNotBlank()) elements.assignedChapter else chapter

            if (elements.createNewChapter && elements.assignedChapter.isNotBlank()) {
                val existing = withContext(Dispatchers.IO) {
                    db.chapterDao().getChapterByTitle(elements.assignedChapter)
                }
                if (existing == null) {
                    val newChapEntity = com.example.data.ChapterEntity(
                        title = elements.assignedChapter,
                        description = elements.newChapterDescription.ifBlank { "Auto-created story theme section" },
                        targetWordCount = elements.newChapterTargetWords,
                        orderIndex = existingRoomChapters.size
                    )
                    withContext(Dispatchers.IO) {
                        db.chapterDao().insertChapter(newChapEntity)
                    }
                    isAutoCreated = true
                    settings.currentChapter = elements.assignedChapter
                    VoiceLoopBus.appendLog("Auto-created new Room book chapter: ${elements.assignedChapter}")
                }
            }

            val newMemory = Memory(
                createdAt = System.currentTimeMillis(),
                transcript = text,
                formattedProse = elements.formattedProse.ifBlank { text },
                passageTitle = elements.passageTitle.ifBlank { "Story Passage in $finalChapter" },
                emotionalTone = elements.emotionalTone.ifBlank { "Reflective" },
                chapter = finalChapter,
                isAutoChapterCreated = isAutoCreated || elements.createNewChapter,
                prompt = lastPrompt,
                approved = true,
                storyArc = elements.storyArc,
                reflection = elements.reflection,
                charactersAndPerspectives = elements.charactersAndPerspectives,
                sensoryDetails = elements.sensoryDetails,
                writingTip = elements.writingTip
            )

            val insertedId = withContext(Dispatchers.IO) {
                db.memoryDao().insert(newMemory)
            }
            val memoryWithId = newMemory.copy(id = insertedId)
            lastSavedMemory = memoryWithId
            lastDiscardedTranscript = null

            feedback.playFeedback(AudioHapticFeedback.Cue.MEMORY_SAVED)
            pendingTranscript = null
            isAwaitingConfirmation = false

            // Fetch all memories to feed background unified automation pipeline
            val allMemories = withContext(Dispatchers.IO) {
                db.memoryDao().getAllMemoriesAsc().firstOrNull() ?: listOf(memoryWithId)
            }

            // Unified Automation: Auto-execute timeline weaving, gap auditing, and voice harmonizing in background
            val announcement = if (settings.autoEditorialPipeline) {
                VoiceLoopBus.publish(LoopState.Processing("Autonomous Pipeline: Weaving timeline & auditing gaps..."))
                val autoResult = try {
                    com.example.autonomous.UnifiedAutomationPipeline.executePipeline(
                        savedMemory = memoryWithId,
                        allManuscriptMemories = allMemories,
                        settings = settings
                    )
                } catch (e: Exception) {
                    VoiceLoopBus.appendLog("Auto-pipeline warning: ${e.message}")
                    com.example.autonomous.UnifiedAutomationPipeline.AutomationExecutionResult(
                        memoryId = memoryWithId.id,
                        chapter = finalChapter,
                        passageTitle = memoryWithId.passageTitle ?: "Story Passage",
                        isAutoChapterCreated = isAutoCreated,
                        timelineInversionsDetected = 0,
                        manuscriptGapsCount = 0,
                        topGapPrompt = null,
                        stylePovStability = 100,
                        detectedStyleIssuesCount = 0,
                        writingTip = elements.writingTip,
                        nextUnifiedPrompt = "Story saved to $finalChapter! Say record to continue or review to listen."
                    )
                }
                val baseAnnouncement = if (isAutoCreated) {
                    "Created new book chapter: $finalChapter, and formatted story! "
                } else {
                    "Saved story passage to $finalChapter. "
                }
                baseAnnouncement + autoResult.nextUnifiedPrompt
            } else {
                // Generate intelligent AI follow-up for next thought
                VoiceLoopBus.publish(LoopState.Processing("Generating interviewer question..."))
                val followUp = try {
                    interviewer.followUp(text, finalChapter)
                } catch (e: Exception) {
                    "What happened next in this part of your life?"
                }
                if (isAutoCreated) {
                    "Created new book chapter section: $finalChapter, and formatted your story! Author tip: ${elements.writingTip} $followUp Say record to answer, or review to hear your chapters."
                } else {
                    "Formatted and saved story passage under $finalChapter. Author tip: ${elements.writingTip} $followUp Say record to answer, or review to hear your chapters."
                }
            }

            say(announcement)
            listen()
        } catch (e: Exception) {
            VoiceLoopBus.appendLog("Error in confirmSave: ${e.message}")
            // Resilient emergency fallback: preserve story unconditionally in local database
            try {
                val emergencyMemory = Memory(
                    createdAt = System.currentTimeMillis(),
                    transcript = text,
                    formattedProse = text,
                    passageTitle = "Story Passage in $chapter",
                    emotionalTone = "Reflective",
                    chapter = chapter,
                    approved = true
                )
                withContext(Dispatchers.IO) {
                    db.memoryDao().insert(emergencyMemory)
                }
                pendingTranscript = null
                isAwaitingConfirmation = false
                feedback.playFeedback(AudioHapticFeedback.Cue.MEMORY_SAVED)
                say("Saved your story to $chapter. Say record to continue.")
            } catch (dbEx: Exception) {
                feedback.playFeedback(AudioHapticFeedback.Cue.NOT_UNDERSTOOD)
                say("Unable to save story right now. Your text is kept in memory.")
            }
            listen()
        }
    }

    suspend fun confirmDelete() = stateMutex.withLock {
        lastDiscardedTranscript = pendingTranscript
        lastSavedMemory = null
        pendingTranscript = null
        isAwaitingConfirmation = false
        feedback.playFeedback(AudioHapticFeedback.Cue.ACTION_UNDONE)
        say("Memory discarded. Say record to try again, or undo to restore.")
        listen()
    }

    // ---- AI Reword Flow ----

    /**
     * Rewords the active draft, or the passage with [memoryId], or the most recent
     * passage. Uses the Gemini→Ollama text chain and Jev faithfulness scoring.
     */
    suspend fun rewordTarget(memoryId: Long? = null) {
        VoiceLoopBus.publish(LoopState.Processing("Rewording with AI..."))

        var sourceText: String?
        var targetId: Long?

        if (memoryId != null) {
            val mem = withContext(Dispatchers.IO) { db.memoryDao().getMemoryById(memoryId) }
            sourceText = mem?.formattedProse?.takeIf { it.isNotBlank() } ?: mem?.transcript
            targetId = memoryId
        } else if (!pendingTranscript.isNullOrBlank()) {
            sourceText = pendingTranscript
            targetId = null
        } else {
            val latest = withContext(Dispatchers.IO) { db.memoryDao().getLatestMemory() }
            sourceText = latest?.formattedProse?.takeIf { it.isNotBlank() } ?: latest?.transcript
            targetId = latest?.id
        }

        if (sourceText.isNullOrBlank()) {
            say("There is no passage to reword yet. Say record to dictate one first.")
            listen()
            return
        }

        val outcome = rewordEngine.reword(sourceText, settings.currentChapter)
        // If the author stopped while the AI call was in flight, stay silent.
        if (!isRunning) return
        if (outcome.rewordedText.isNullOrBlank()) {
            say("I could not reword that passage. ${outcome.error ?: "Please try again later."}")
            listen()
            return
        }

        pendingRewordText = outcome.rewordedText
        pendingRewordMemoryId = targetId
        pendingRewordIsDraft = targetId == null
        isAwaitingReword = true

        if (settings.autoApplyReword && outcome.shouldApply) {
            applyReword()
            return
        }

        val notes = outcome.notes?.let { "$it. " } ?: ""
        val faithful = outcome.faithfulProbability?.let { "Jev faithfulness ${(it * 100).toInt()} percent. " } ?: ""
        say("Here is a reworded version. $notes$faithful Say save to use it, or delete to keep your original. ${outcome.rewordedText}")
        listen()
    }

    suspend fun applyReword() = stateMutex.withLock {
        val reworded = pendingRewordText
        val id = pendingRewordMemoryId
        val isDraft = pendingRewordIsDraft

        isAwaitingReword = false
        pendingRewordText = null
        pendingRewordMemoryId = null
        pendingRewordIsDraft = false

        if (reworded.isNullOrBlank()) {
            say("There is nothing to apply.")
            listen()
            return@withLock
        }

        if (isDraft || id == null) {
            // No saved memory yet: replace the active draft and let the normal
            // save confirmation flow persist it.
            pendingTranscript = reworded
            isAwaitingConfirmation = true
            say("Applied the reworded passage to your active draft. Say save to keep it, or delete to discard.")
            listen()
        } else {
            val mem = withContext(Dispatchers.IO) { db.memoryDao().getMemoryById(id) }
            if (mem == null) {
                say("I could not find that passage to update.")
                listen()
            } else {
                withContext(Dispatchers.IO) { db.memoryDao().update(mem.copy(formattedProse = reworded)) }
                feedback.playFeedback(AudioHapticFeedback.Cue.MEMORY_SAVED)
                say("Reworded passage saved. Your original words remain in the transcript. Say review to hear it.")
                listen()
            }
        }
    }

    suspend fun discardReword() {
        isAwaitingReword = false
        pendingRewordText = null
        pendingRewordMemoryId = null
        pendingRewordIsDraft = false
        feedback.playFeedback(AudioHapticFeedback.Cue.ACTION_UNDONE)
        say("Kept your original words. Say reword to try again.")
        listen()
    }

    // ---- Review & Deconstruct Flow ----

    suspend fun reviewLatest() {
        VoiceLoopBus.publish(LoopState.Processing("Loading memories..."))
        val memories = withContext(Dispatchers.IO) {
            db.memoryDao().getAllMemoriesDesc().firstOrNull() ?: emptyList()
        }

        if (memories.isEmpty()) {
            say("Your book has no recorded memories yet. Say record to tell your first story!")
            listen()
            return
        }

        cachedReviewList = memories
        currentReviewIndex = 0
        readCurrentReviewItem()
    }

    private suspend fun readCurrentReviewItem() {
        if (cachedReviewList.isEmpty()) {
            say("No memories to review.")
            listen()
            return
        }

        val mem = cachedReviewList.getOrNull(currentReviewIndex) ?: cachedReviewList.first()
        val num = cachedReviewList.size - currentReviewIndex
        val total = cachedReviewList.size
        say("Memory $num of $total in ${mem.chapter ?: "Book"}: ${mem.transcript}. Say next, back, breakdown, or record.")
        listen()
    }

    private suspend fun advanceReview(delta: Int) {
        if (cachedReviewList.isEmpty()) {
            reviewLatest()
            return
        }

        val newIndex = currentReviewIndex - delta // desc order: -1 moves to earlier memory
        if (newIndex in cachedReviewList.indices) {
            currentReviewIndex = newIndex
            readCurrentReviewItem()
        } else {
            say("End of memories. Say record to add more, or repeat to hear again.")
            listen()
        }
    }

    suspend fun readStoryBreakdown() {
        val mem = cachedReviewList.getOrNull(currentReviewIndex) ?: withContext(Dispatchers.IO) {
            db.memoryDao().getLatestMemory()
        }

        if (mem == null) {
            say("No memory recorded yet to break down. Say record to share a story.")
            listen()
            return
        }

        val arc = mem.storyArc ?: "Core narrative scene."
        val perspectives = mem.charactersAndPerspectives ?: "Author and key figures."
        val tip = mem.writingTip ?: "Show details rather than just telling."

        say("Story breakdown: Scene Arc: $arc. Perspectives and characters: $perspectives. Author tip: $tip. Say record or review.")
        listen()
    }

    suspend fun readWritingTip() {
        val mem = cachedReviewList.getOrNull(currentReviewIndex) ?: withContext(Dispatchers.IO) {
            db.memoryDao().getLatestMemory()
        }

        val tip = mem?.writingTip ?: "Classic author tip: Describe physical movements, facial expressions, and atmospheric sounds to place readers right beside you."
        say("Author craft tip: $tip. Say record to apply this to your next scene.")
        listen()
    }

    // ---- Chapter & AI Prompt Flow ----

    suspend fun requestAiInterviewPrompt() {
        VoiceLoopBus.publish(LoopState.Processing("Analyzing story gaps with Craft Coach..."))
        val chapter = settings.currentChapter

        // Fetch existing chapter memories from DB
        val chapterMemories = withContext(Dispatchers.IO) {
            db.memoryDao().getMemoriesForChapter(chapter).firstOrNull() ?: emptyList()
        }

        // Run CraftCoachAgent to find specific storytelling gap
        val craftReport = com.example.deterministic.CraftCoachAgent.evaluateStory(chapterMemories)
        val craftFocus = craftReport.recommendations.firstOrNull()

        val prompt = interviewer.generateChapterPromptWithCraft(chapter, craftFocus)
        if (!isRunning) return
        VoiceLoopBus.appendLog("Craft-informed prompt ($chapter): $prompt")
        say("Here is a question for $chapter: $prompt. Say record whenever you are ready to answer.")
        listen()
    }

    suspend fun cycleChapter() {
        val chapters = BookChapters.STANDARD
        val currentIndex = chapters.indexOf(settings.currentChapter)
        val nextChapter = if (currentIndex in 0 until chapters.size - 1) {
            chapters[currentIndex + 1]
        } else {
            chapters.first()
        }
        settings.currentChapter = nextChapter
        say("Switched to $nextChapter. Say prompt me for a question, or record to start.")
        listen()
    }

    suspend fun readBookSummary() {
        VoiceLoopBus.publish(LoopState.Processing("Synthesizing book..."))
        val allMemories = withContext(Dispatchers.IO) {
            db.memoryDao().getAllMemoriesAsc().firstOrNull() ?: emptyList()
        }

        if (allMemories.isEmpty()) {
            say("${settings.bookTitle} currently has zero memories recorded. Say record to begin.")
            listen()
            return
        }

        val excerpts = allMemories.map { it.transcript }
        val summary = interviewer.synthesizeBookSummary(
            bookTitle = settings.bookTitle,
            author = settings.authorName,
            memoryCount = allMemories.size,
            excerpts = excerpts
        )
        if (!isRunning) return

        say(summary)
        listen()
    }

    suspend fun readPublishingReadiness() {
        VoiceLoopBus.publish(LoopState.Processing("Analyzing manuscript readiness..."))
        val allMemories = withContext(Dispatchers.IO) {
            db.memoryDao().getAllMemoriesAsc().firstOrNull() ?: emptyList()
        }

        val chapters = BookChapters.STANDARD

        val report = com.example.data.BookPublishingAuditor.audit(
            memories = allMemories,
            bookTitle = settings.bookTitle,
            authorName = settings.authorName,
            dedication = settings.dedication,
            authorBio = settings.authorBio,
            allPlannedChapters = chapters
        )

        val spokenStatus = buildString {
            append("Publishing Readiness for ${settings.bookTitle}: ")
            append("${report.readinessScore} percent complete. ")
            append("Stage: ${report.readinessStage}. ")
            append("Total manuscript length is ${report.totalWords} words, estimated at ${report.estimatedPages} printed pages across ${report.totalChaptersWithContent} of ${report.targetChaptersCount} active chapters. ")
            if (report.recommendations.isNotEmpty()) {
                append("Next step: ${report.recommendations.first()}")
            }
        }

        say(spokenStatus)
        listen()
    }

    suspend fun exportBookPrompt() {
        VoiceLoopBus.publish(LoopState.Processing("Preparing export..."))
        val allMemories = withContext(Dispatchers.IO) {
            db.memoryDao().getAllMemoriesAsc().firstOrNull() ?: emptyList()
        }

        if (allMemories.isEmpty()) {
            say("Cannot export yet because your book has zero recorded memories. Say record to tell a story.")
            listen()
            return
        }

        val safeTitle = settings.bookTitle.replace(Regex("[^a-zA-Z0-9_]"), "_").take(25)
        val fileName = "${safeTitle}_FullBook.pdf"

        val result = com.example.export.MemoirExportEngine.saveToStorage(
            context = context,
            fileName = fileName,
            format = com.example.export.ExportFormat.PDF,
            bookTitle = settings.bookTitle,
            authorName = settings.authorName,
            chapterTitle = null,
            memories = allMemories
        )

        if (result.success) {
            say("Success! Your memoir has been rendered and saved as a formatted PDF book to your Documents folder under Mike Write. You can also export text files from the Settings screen.")
        } else {
            say("Export encountered an issue: ${result.message}")
        }
        listen()
    }

    suspend fun playbackLiveDraft() {
        val currentDraft = pendingTranscript?.trim()
        if (!currentDraft.isNullOrBlank()) {
            say("Current active draft: $currentDraft. Say done when finished, or continue speaking.")
            if (isRunning) {
                beginRecording()
            }
            return
        }

        // Otherwise check the latest recorded memory
        val latest = withContext(Dispatchers.IO) {
            db.memoryDao().getLatestMemory()
        }
        if (latest != null) {
            say("Most recent passage in ${latest.chapter ?: "your book"}: ${latest.transcript}. Say record to add more, or review to explore chapters.")
            listen()
        } else {
            say("No active draft or memories yet. Say record to begin dictating.")
            listen()
        }
    }

    suspend fun runAutoSequence() {
        VoiceLoopBus.publish(LoopState.Processing("Weaving chronological timeline..."))
        val allMemories = withContext(Dispatchers.IO) {
            db.memoryDao().getAllMemoriesAsc().firstOrNull() ?: emptyList()
        }

        if (allMemories.isEmpty()) {
            say("No memories recorded yet to sequence. Say record to dictate your first story.")
            listen()
            return
        }

        val analysis = AutonomousManuscriptWeaver.analyzeTimeline(allMemories)
        val spoken = if (analysis.hasInversions) {
            "Timeline analysis complete across ${analysis.scenes.size} scenes. Detected ${analysis.inversionCount} chronological jumps. For example, ${analysis.proposedOrder.first().title} is identified as the earliest foundational moment. Say review to listen chronologically."
        } else {
            "Timeline analysis complete. All ${analysis.scenes.size} scenes are in natural chronological sequence. Say review to hear your chapters in order."
        }

        say(spoken)
        listen()
    }

    suspend fun runFindGaps() {
        VoiceLoopBus.publish(LoopState.Processing("Auditing narrative gaps..."))
        val allMemories = withContext(Dispatchers.IO) {
            db.memoryDao().getAllMemoriesAsc().firstOrNull() ?: emptyList()
        }

        if (allMemories.isEmpty()) {
            say("No memories to analyze yet. Say record to dictate a story.")
            listen()
            return
        }

        val report = AutonomousExpansionEngine.auditManuscriptGaps(allMemories)
        val prompt = report.topAutonomousPrompt ?: "What story from your life deserves a place in this chapter?"
        lastPrompt = prompt

        val spoken = if (report.totalGapsFound > 0) {
            "Manuscript gap audit complete. Health score is ${report.healthScore} out of 100 with ${report.highPriorityGaps} priority gaps. Top prompt: $prompt. Say record to answer now."
        } else {
            "Manuscript gap audit complete with a perfect health score of 100. Every scene has vivid atmosphere, reflection, and character presence. Say record to keep going."
        }

        say(spoken)
        listen()
    }

    suspend fun runHarmonizeVoice() {
        VoiceLoopBus.publish(LoopState.Processing("Auditing voice and style consistency..."))
        val allMemories = withContext(Dispatchers.IO) {
            db.memoryDao().getAllMemoriesAsc().firstOrNull() ?: emptyList()
        }

        if (allMemories.isEmpty()) {
            say("No memories recorded yet to harmonize. Say record to dictate a story.")
            listen()
            return
        }

        val scorecard = AutonomousStyleHarmonizer.auditStyleHealth(allMemories)
        val spoken = if (scorecard.detectedIssues.isNotEmpty()) {
            "Style audit complete. Point-of-view stability is ${scorecard.povStabilityPercent} percent. Detected ${scorecard.detectedIssues.size} stylistic refinements including oral fillers and tense consistency. Say record to dictate more or review in the Caregiver tab."
        } else {
            "Style audit complete. Point-of-view stability is 100 percent with zero oral crutches detected. Your prose flows with authentic literary clarity."
        }

        say(spoken)
        listen()
    }

    suspend fun runUnifiedPipeline() {
        VoiceLoopBus.publish(LoopState.Processing("Executing Unified Automation Pipeline..."))
        val allMemories = withContext(Dispatchers.IO) {
            db.memoryDao().getAllMemoriesAsc().firstOrNull() ?: emptyList()
        }

        if (allMemories.isEmpty()) {
            say("No memories recorded yet. Say record to dictate your first story and the pipeline will automatically format, weave, and organize it.")
            listen()
            return
        }

        val latest = allMemories.last()
        val result = com.example.autonomous.UnifiedAutomationPipeline.executePipeline(
            savedMemory = latest,
            allManuscriptMemories = allMemories,
            settings = settings
        )

        val spokenSummary = buildString {
            append("Unified Pipeline execution complete across ${allMemories.size} memories. ")
            if (result.timelineInversionsDetected > 0) {
                append("Detected ${result.timelineInversionsDetected} timeline variations. ")
            } else {
                append("Timeline sequence is consistent. ")
            }
            append("POV stability is ${result.stylePovStability} percent. ")
            if (result.topGapPrompt != null) {
                append("Next suggested topic: ${result.topGapPrompt}. ")
            }
            append("Say record to continue, or review to listen.")
        }

        say(spokenSummary)
        listen()
    }

    fun destroy() {
        scope.cancel()
        speech.destroy()
        listener.destroy()
        feedback.release()
        isRunning = false
    }
}
