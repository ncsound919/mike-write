package com.example.loop

import android.content.Context
import com.example.ai.Interviewer
import com.example.autonomous.AutonomousExpansionEngine
import com.example.autonomous.AutonomousManuscriptWeaver
import com.example.autonomous.AutonomousStyleHarmonizer
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.feedback.AudioHapticFeedback
import com.example.speech.Command
import com.example.speech.CommandParser
import com.example.speech.ListeningEngine
import com.example.speech.SpeechEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull

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
    var isRunning: Boolean = false
        private set
    var isRecording: Boolean = false
        private set

    private var lastPrompt: String = "Say record to dictate a story. Review to hear your book. Prompt me for an interview question. Or say breakdown to explore your story elements."
    private var pendingTranscript: String? = null
    private var latestPartialText: String? = null
    private var isAwaitingConfirmation: Boolean = false
    private var currentReviewIndex: Int = 0
    private var cachedReviewList: List<Memory> = emptyList()

    // Undo action snapshot
    private var lastSavedMemory: Memory? = null
    private var lastDiscardedTranscript: String? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        VoiceLoopBus.appendLog("Voice loop started")
        scope.launch {
            // Give TTS a split second to ensure audio focus
            delay(400)
            mainMenu()
        }
    }

    fun stopEverything() {
        isRunning = false
        isRecording = false
        latestPartialText = null
        speech.stop()
        listener.stop()
        feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)
        VoiceLoopBus.publish(LoopState.Idle)
        VoiceLoopBus.appendLog("Voice loop stopped")
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
        delay(200) // Brief breather for acoustics and user reflection
    }

    private fun listen() {
        if (!isRunning) return
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
                        feedback.playFeedback(AudioHapticFeedback.Cue.NOT_UNDERSTOOD)
                        delay(600)
                        listen()
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
                say("You can say: record, done, review, breakdown, writing tip, prompt me, chapter, book, save, undo, delete, repeat, slower, or faster.")
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
                    speech.stop()
                    listener.stop()
                    feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)
                    VoiceLoopBus.publish(LoopState.Idle)
                    say("Paused. Say record or help whenever you are ready.")
                    listen()
                }
            }
            Command.UNDO -> {
                handleUndo()
            }
            Command.SAVE, Command.YES -> {
                confirmSave()
            }
            Command.DELETE, Command.NO -> {
                confirmDelete()
            }
            Command.UNKNOWN -> {
                feedback.playFeedback(AudioHapticFeedback.Cue.NOT_UNDERSTOOD)
                // If we are awaiting confirmation and user speaks something else, guide them gently
                if (isAwaitingConfirmation) {
                    say("I heard: $utterance. Say save to keep it, or delete to discard.")
                    listen()
                } else {
                    say("I heard: $utterance. Say record to dictate, review to listen, or breakdown for literary insights.")
                    listen()
                }
            }
        }
    }

    private suspend fun handleUndo() {
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
        say("Mike Write is ready. Say record to tell a story, prompt me for a question, review to hear your book, or breakdown for story elements.")
        listen()
    }

    // ---- Recording Flow with Multi-Sentence Dictation ----

    private val stopPhraseRegex = Regex("(?i)\\b(that's it|that is all|that's all|all done|done|finished|stop recording|stop|finish|wrap up)\\b")

    suspend fun beginRecording() {
        if (isRecording) {
            finishRecording()
            return
        }
        isRecording = true
        pendingTranscript = null
        latestPartialText = null
        isAwaitingConfirmation = false
        listener.stop()
        speech.stop()

        val currentChap = settings.currentChapter
        feedback.playFeedback(AudioHapticFeedback.Cue.START_RECORDING)
        VoiceLoopBus.publish(LoopState.Recording(System.currentTimeMillis(), "Listening to your story..."))
        say("Recording memory in $currentChap. Speak your story freely. Say done or tap when finished.")

        // Brief acoustic breathing room before opening microphone
        delay(350)

        // Listen continuously for user speech until they say "done", "stop", tap, or pause
        listener.start(
            continuous = true,
            onPartial = { partial ->
                val trimmed = partial.trim()
                latestPartialText = trimmed
                val fullPreview = if (pendingTranscript.isNullOrBlank()) trimmed else "$pendingTranscript $trimmed"
                VoiceLoopBus.publish(LoopState.Recording(System.currentTimeMillis(), fullPreview))

                // Instant voice stop detection in live partial stream
                if (stopPhraseRegex.containsMatchIn(trimmed)) {
                    val cleaned = trimmed.replace(stopPhraseRegex, "").trim()
                    latestPartialText = if (cleaned.isNotBlank()) cleaned else null
                    scope.launch {
                        finishRecording()
                    }
                }
            },
            onResult = { resultSegment ->
                scope.launch {
                    val trimmed = resultSegment.trim()
                    if (trimmed.isBlank()) return@launch

                    val hasStop = stopPhraseRegex.containsMatchIn(trimmed) ||
                                  CommandParser.parse(trimmed) == Command.DONE ||
                                  CommandParser.parse(trimmed) == Command.STOP

                    val storyPart = trimmed.replace(stopPhraseRegex, "").trim()
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
                                delay(500)
                                listen()
                            }
                        }
                    }
                }
            }
        )
    }

    suspend fun finishRecording() {
        if (!isRecording && pendingTranscript.isNullOrBlank() && latestPartialText.isNullOrBlank()) {
            return
        }
        isRecording = false
        listener.stop()
        feedback.playFeedback(AudioHapticFeedback.Cue.STOP_RECORDING)

        // Brief delay so recognizer finishes processing final audio frame
        delay(180)

        // Integrate any in-flight partial text so no words are dropped
        val partial = latestPartialText?.trim().orEmpty().replace(stopPhraseRegex, "").trim()
        if (partial.isNotBlank()) {
            if (pendingTranscript.isNullOrBlank()) {
                pendingTranscript = partial
            } else if (!pendingTranscript!!.contains(partial)) {
                pendingTranscript = "$pendingTranscript $partial"
            }
        }
        latestPartialText = null

        var text = pendingTranscript?.trim()
        if (!text.isNullOrBlank()) {
            text = text.replace(stopPhraseRegex, "").trim()
        }

        if (text.isNullOrBlank()) {
            say("I did not catch anything. Say record to tell your story.")
            listen()
            return
        }

        // Apply Agent 1 (Cleaner) deterministically
        val cleanedText = com.example.deterministic.CleanerAgent.clean(text)
        pendingTranscript = if (cleanedText.isNotBlank()) cleanedText else text

        isAwaitingConfirmation = true
        val echoMsg = com.example.deterministic.DeterministicWriterEngine.buildEchoConfirmation(pendingTranscript!!)
        say(echoMsg)
        listen()
    }

    suspend fun confirmSave() {
        val text = pendingTranscript
        if (text.isNullOrBlank()) {
            say("There is nothing pending to save. Say record to dictate.")
            listen()
            return
        }

        VoiceLoopBus.publish(LoopState.Processing("Formatting prose and analyzing story elements..."))
        val chapter = settings.currentChapter

        // Fetch existing Room chapters for classification
        val existingRoomChapters = withContext(Dispatchers.IO) {
            db.chapterDao().getAllChapters().firstOrNull()?.map { it.title } ?: emptyList()
        }

        // Compartmentalize dictated story, format prose, and detect chapter placement/creation
        val elements = interviewer.analyzeBookElements(text, chapter, existingRoomChapters)

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

        // Generate intelligent AI follow-up for next thought
        VoiceLoopBus.publish(LoopState.Processing("Generating interviewer question..."))
        val followUp = interviewer.followUp(text, finalChapter)

        val announcement = if (isAutoCreated) {
            "Created new book chapter section: $finalChapter, and formatted your story! Author tip: ${elements.writingTip} $followUp Say record to answer, or review to hear your chapters."
        } else {
            "Formatted and saved story passage under $finalChapter. Author tip: ${elements.writingTip} $followUp Say record to answer, or review to hear your chapters."
        }

        say(announcement)
        listen()
    }

    suspend fun confirmDelete() {
        lastDiscardedTranscript = pendingTranscript
        lastSavedMemory = null
        pendingTranscript = null
        isAwaitingConfirmation = false
        feedback.playFeedback(AudioHapticFeedback.Cue.ACTION_UNDONE)
        say("Memory discarded. Say record to try again, or undo to restore.")
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
        VoiceLoopBus.appendLog("Craft-informed prompt ($chapter): $prompt")
        say("Here is a question for $chapter: $prompt. Say record whenever you are ready to answer.")
        listen()
    }

    suspend fun cycleChapter() {
        val chapters = listOf(
            "Chapter 1: Early Days",
            "Chapter 2: Growing Up & Family",
            "Chapter 3: Passions & Milestones",
            "Chapter 4: The Turning Point",
            "Chapter 5: Strength, Healing & Daily Life",
            "Chapter 6: Wisdom & Legacy"
        )
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

        say(summary)
        listen()
    }

    suspend fun readPublishingReadiness() {
        VoiceLoopBus.publish(LoopState.Processing("Analyzing manuscript readiness..."))
        val allMemories = withContext(Dispatchers.IO) {
            db.memoryDao().getAllMemoriesAsc().firstOrNull() ?: emptyList()
        }

        val chapters = listOf(
            "Chapter 1: Early Days",
            "Chapter 2: Growing Up & Family",
            "Chapter 3: Passions & Milestones",
            "Chapter 4: The Turning Point",
            "Chapter 5: Strength, Healing & Daily Life",
            "Chapter 6: Wisdom & Legacy"
        )

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

    fun destroy() {
        scope.cancel()
        speech.destroy()
        listener.destroy()
        isRunning = false
    }
}
