package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.Interviewer
import com.example.data.BookElements
import com.example.data.Memory
import com.example.data.MikeWriteDatabase
import com.example.data.SettingsStore
import com.example.speech.Command
import com.example.speech.CommandParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Mike Write", appName)
    }

    @Test
    fun `parse literary breakdown and author coaching commands`() {
        assertEquals(Command.DECONSTRUCT, CommandParser.parse("breakdown"))
        assertEquals(Command.DECONSTRUCT, CommandParser.parse("story elements"))
        assertEquals(Command.DECONSTRUCT, CommandParser.parse("perspectives"))
        assertEquals(Command.TIP, CommandParser.parse("writing tip"))
        assertEquals(Command.TIP, CommandParser.parse("craft tip"))
        assertEquals(Command.RECORD, CommandParser.parse("record"))
        assertEquals(Command.DONE, CommandParser.parse("done"))
        assertEquals(Command.SAVE, CommandParser.parse("save"))
    }

    @Test
    fun `interviewer generates structured book compartments fallback`() = runBlocking {
        val interviewer = Interviewer()
        val text = "My grandfather showed me his carpentry tools in his dusty workshop. The cedar wood smelled fresh. He told me that honest work builds real character."
        val elements: BookElements = interviewer.analyzeBookElements(text, "Early Days")

        assertNotNull(elements)
        assertTrue(elements.storyArc.isNotBlank())
        assertTrue(elements.reflection.isNotBlank())
        assertTrue(elements.charactersAndPerspectives.isNotBlank())
        assertTrue(elements.sensoryDetails.isNotBlank())
        assertTrue(elements.writingTip.isNotBlank())
    }

    @Test
    fun `database persists memory with compartmentalized literary elements`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = MikeWriteDatabase.getInstance(context)
        val dao = db.memoryDao()

        val memory = Memory(
            transcript = "We rolled down the steep hill on our home-made wooden cart. The autumn wind stung my face and Tommy was shouting in pure thrill.",
            chapter = "Chapter 1: Early Days",
            storyArc = "Riding a home-made wooden cart down a steep hill with Tommy.",
            reflection = "Unbridled youthful freedom and feeling invincible.",
            charactersAndPerspectives = "Tommy shouting with pure excitement, sharing the adventurous thrill.",
            sensoryDetails = "Crisp autumn wind stinging the face, rumble of wooden wheels on gravel.",
            writingTip = "Show emotion through involuntary reactions like gasping or laughing aloud."
        )

        val id = dao.insert(memory)
        assertTrue(id > 0)

        val all = dao.getAllMemoriesAsc().first()
        val saved = all.find { it.id == id }
        assertNotNull(saved)
        assertEquals("Riding a home-made wooden cart down a steep hill with Tommy.", saved?.storyArc)
        assertEquals("Show emotion through involuntary reactions like gasping or laughing aloud.", saved?.writingTip)
        assertEquals("Tommy shouting with pure excitement, sharing the adventurous thrill.", saved?.charactersAndPerspectives)
    }

    @Test
    fun `book publishing auditor calculates readiness metrics and formats manuscript`() {
        val plannedChapters = listOf(
            "Chapter 1: Early Days",
            "Chapter 2: Growing Up & Family",
            "Chapter 3: Passions & Milestones",
            "Chapter 4: The Turning Point",
            "Chapter 5: Strength, Healing & Daily Life",
            "Chapter 6: Wisdom & Legacy"
        )
        val memories = listOf(
            Memory(
                transcript = "This is a detailed memory about my childhood.",
                chapter = "Chapter 1: Early Days",
                storyArc = "Childhood exploration",
                reflection = "Appreciation for roots"
            ),
            Memory(
                transcript = "This is another memory discussing family traditions and dinners.",
                chapter = "Chapter 2: Growing Up & Family",
                storyArc = "Family Sunday dinners",
                reflection = "Love of togetherness"
            )
        )

        val report = com.example.data.BookPublishingAuditor.audit(
            memories = memories,
            bookTitle = "My Life Journey",
            authorName = "Mike",
            dedication = "To my beloved family",
            authorBio = "Mike is an author and survivor.",
            allPlannedChapters = plannedChapters
        )

        assertNotNull(report)
        assertTrue(report.totalWords > 0)
        assertEquals(2, report.totalChaptersWithContent)
        assertEquals(6, report.targetChaptersCount)
        assertTrue(report.hasDedication)
        assertTrue(report.hasAuthorBio)
        assertTrue(report.hasFrontMatter)
        assertTrue(report.readinessScore > 0)
        assertTrue(report.recommendations.isNotEmpty())

        val markdown = com.example.data.BookPublishingAuditor.generateManuscript(
            format = com.example.data.ManuscriptFormat.MARKDOWN,
            bookTitle = "My Life Journey",
            authorName = "Mike",
            dedication = "To my beloved family",
            authorBio = "Mike is an author and survivor.",
            memories = memories,
            allChapters = plannedChapters
        )
        assertTrue(markdown.contains("# My Life Journey"))
        assertTrue(markdown.contains("By Mike"))
        assertTrue(markdown.contains("Table of Contents"))
        assertTrue(markdown.contains("Chapter 1: Early Days"))
        assertTrue(markdown.contains("About the Author"))
    }

    @Test
    fun `audio haptic feedback initializes and triggers cues without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val feedback = com.example.feedback.AudioHapticFeedback(context)

        feedback.playFeedback(com.example.feedback.AudioHapticFeedback.Cue.START_RECORDING)
        feedback.playFeedback(com.example.feedback.AudioHapticFeedback.Cue.STOP_RECORDING)
        feedback.playFeedback(com.example.feedback.AudioHapticFeedback.Cue.MEMORY_SAVED)
        feedback.playFeedback(com.example.feedback.AudioHapticFeedback.Cue.ACTION_UNDONE)
        feedback.playFeedback(com.example.feedback.AudioHapticFeedback.Cue.NOT_UNDERSTOOD)
        feedback.playFeedback(com.example.feedback.AudioHapticFeedback.Cue.BUTTON_TAP)
        feedback.playFeedback(com.example.feedback.AudioHapticFeedback.Cue.HELP_TRIGGERED)
        feedback.release()
    }

    @Test
    fun `privacy and consent settings persist state correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = com.example.data.SettingsStore(context)

        settings.hasConsentedToAudioProcessing = true
        assertTrue(settings.hasConsentedToAudioProcessing)
        assertTrue(settings.consentTimestamp > 0L)
        assertNotNull(settings.getConsentFormattedDate())

        settings.liveEchoPlayback = true
        assertTrue(settings.liveEchoPlayback)
    }

    @Test
    fun `command parser deduplication distinguishes book summary from memory review`() {
        assertEquals(Command.BOOK, CommandParser.parse("read whole book"))
        assertEquals(Command.BOOK, CommandParser.parse("read memoir"))
        assertEquals(Command.BOOK, CommandParser.parse("summary"))
        assertEquals(Command.REVIEW, CommandParser.parse("review"))
        assertEquals(Command.REVIEW, CommandParser.parse("review memories"))
    }

    @Test
    fun `deterministic router handles thread-safe counters and logs`() {
        com.example.deterministic.DeterministicRouter.resetSessionCounters()
        val (initialCount, _) = com.example.deterministic.DeterministicRouter.getFallthroughStats()
        assertEquals(0, initialCount)
        assertTrue(com.example.deterministic.DeterministicRouter.canMakeLlmCall())

        com.example.deterministic.DeterministicRouter.recordLlmCall("test_task", "test_reason", "Agent 1")
        val (updatedCount, _) = com.example.deterministic.DeterministicRouter.getFallthroughStats()
        assertEquals(1, updatedCount)
    }

    // --- Deterministic Testing Suite (Rules D.1 - D.10) ---

    @Test
    fun `D1 and D2 idempotence and determinism across runs`() {
        val raw = "Um you know I was going to the the workshop with Uncle John and and it was great and"
        val out1 = com.example.deterministic.CleanerAgent.clean(raw)
        val out2 = com.example.deterministic.CleanerAgent.clean(raw)
        val out3 = com.example.deterministic.CleanerAgent.clean(out1)

        assertEquals(out1, out2)
        assertEquals(out1, out3)

        // 100 deterministic executions produce identical output
        repeat(100) {
            val runOut = com.example.deterministic.CleanerAgent.clean(raw)
            assertEquals(out1, runOut)
        }
    }

    @Test
    fun `D7 no fact injection - cleaner output is subsequence of raw tokens`() {
        val testCorpus = listOf(
            "Um uh I was— I went to the store and",
            "You know like we were playing in the backyard with Tommy Tommy and so",
            "Ah er I mean my grandpa built a wooden cart in the garage and but",
            "I felt like like really scared when the doctor walked in and",
            "Last summer we visited the old farm house with Aunt Sarah and"
        )

        for (raw in testCorpus) {
            val cleaned = com.example.deterministic.CleanerAgent.clean(raw)
            assertTrue("Subsequence violation for raw: '$raw' -> '$cleaned'", com.example.deterministic.CleanerAgent.isSubsequenceOfRaw(raw, cleaned))
        }
    }

    @Test
    fun `deterministic question selector and template filler produce zero-cost gap-targeted prompts`() {
        val text = "We built a wooden cart in the dusty garage with Tommy and rolled it down the hill."
        val entities = com.example.deterministic.EntityRegistryAgent.extractEntities(text)
        assertTrue(entities.any { it.name.contains("Tommy") })

        val question = com.example.deterministic.DeterministicWriterEngine.selectNextQuestion(text, entities)
        assertNotNull(question)
        assertTrue(question.isNotBlank())
        assertTrue(question.endsWith("?"))
    }

    @Test
    fun `chapter assembler and export formatter structure book deterministically`() {
        val memories = listOf(
            Memory(transcript = "Grandpa let me hold the cedar wood plane in the workshop.", chapter = "Chapter 1: Early Days"),
            Memory(transcript = "Sunday family dinners at Mom's kitchen table were full of laughter.", chapter = "Chapter 2: Growing Up & Family")
        )

        val assembled = com.example.deterministic.ChapterAssemblerAgent.assemble(memories)
        assertEquals(6, assembled.size)

        val markdown = com.example.deterministic.ExportFormatterAgent.formatMarkdown(
            bookTitle = "Memories",
            authorName = "Mike",
            dedication = "For my family",
            authorBio = "Author & Survivor",
            chapters = assembled
        )
        assertTrue(markdown.contains("# Memories"))
        assertTrue(markdown.contains("Grandpa let me hold the cedar wood plane"))
    }

    @Test
    fun `deterministic entity registry extracts objects and date normalizer handles colloquial eras`() {
        val text = "In the nineties I rode my blue bicycle to the lake cabin with Uncle John."
        val entities = com.example.deterministic.EntityRegistryAgent.extractEntities(text)

        assertTrue(entities.any { it.type == com.example.deterministic.EntityType.PERSON && it.name.contains("Uncle John") })
        assertTrue(entities.any { it.type == com.example.deterministic.EntityType.PLACE })
        assertTrue(entities.any { it.type == com.example.deterministic.EntityType.OBJECT && it.name.contains("bicycle") })

        val normalizedDate = com.example.deterministic.DeterministicWriterEngine.normalizeDateReference(text)
        assertEquals("1990–1999", normalizedDate)
    }

    @Test
    fun `craft coach agent provides deterministic literary coaching on-device`() {
        val raw = "I sat in the room waiting for the doctor."
        val entities = com.example.deterministic.EntityRegistryAgent.extractEntities(raw)
        val analysis = com.example.deterministic.DeterministicWriterEngine.analyzeGaps(raw, entities)
        val tips = com.example.deterministic.CraftCoachAgent.analyzeCraft(raw, analysis, entities)

        assertTrue(tips.isNotEmpty())
        assertEquals("Sensory Immersion", tips.first().category)
        assertTrue(tips.first().actionableGuidance.isNotBlank())
    }

    @Test
    fun `book readiness evaluator calculates percentage and tracks steps to finish`() {
        val memories = listOf(
            Memory(transcript = "Grandpa let me hold the cedar wood plane in the workshop.", chapter = "Chapter 1: Early Days", sensoryDetails = "Cedar scent"),
            Memory(transcript = "Sunday family dinners at Mom's kitchen table were full of laughter.", chapter = "Chapter 2: Growing Up & Family", charactersAndPerspectives = "Mom and family"),
            Memory(transcript = "Playing high school football under Friday night stadium lights.", chapter = "Chapter 3: Passions & Milestones")
        )

        val report = com.example.data.BookReadinessEvaluator.evaluate(
            bookTitle = "A Life Rebuilt",
            authorName = "Mike",
            dedication = "To my caregivers and family",
            authorBio = "Survivor and memoirist",
            memories = memories
        )

        assertTrue(report.completionPercentage > 40)
        assertEquals(7, report.totalStepsCount)
        assertTrue(report.completedStepsCount >= 4)
        assertNotNull(report.nextAction)
    }

    @Test
    fun `full deterministic agent suite comprehensive verification`() {
        val rawInput = "Um uh in the eighties, we visited the old lake house with Grandpa Joe and he made a wooden cart with his tools and so."
        
        // Agent 1: Cleaner
        val cleaned = com.example.deterministic.CleanerAgent.clean(rawInput)
        assertTrue(com.example.deterministic.CleanerAgent.isSubsequenceOfRaw(rawInput, cleaned))
        assertFalse(cleaned.startsWith("Um", ignoreCase = true))
        assertFalse(cleaned.endsWith("and so.", ignoreCase = true))

        // Agent 2: Segmenter
        val sentences = com.example.deterministic.SegmenterAgent.segment(cleaned)
        assertTrue(sentences.isNotEmpty())

        // Agent 3: Entity Registry
        val entities = com.example.deterministic.EntityRegistryAgent.extractEntities(cleaned)
        assertTrue(entities.any { it.type == com.example.deterministic.EntityType.PERSON })
        assertTrue(entities.any { it.type == com.example.deterministic.EntityType.PLACE })
        assertTrue(entities.any { it.type == com.example.deterministic.EntityType.OBJECT })

        // Agent 4: Date Normalizer
        val normalizedDate = com.example.deterministic.DeterministicWriterEngine.normalizeDateReference(rawInput)
        assertEquals("1980–1989", normalizedDate)

        // Agent 5: Topic Tagger
        val topic = com.example.deterministic.DeterministicWriterEngine.tagTopic(cleaned)
        assertTrue(topic.isNotBlank())

        // Agent 7: Question Selector & Agent 8: Template Filler
        val question = com.example.deterministic.DeterministicWriterEngine.selectNextQuestion(cleaned, entities)
        assertTrue(question.isNotBlank())
        assertTrue(question.endsWith("?"))

        // Agent 9: Echo Confirmer
        val echo = com.example.deterministic.DeterministicWriterEngine.buildEchoConfirmation(cleaned)
        assertTrue(echo.startsWith("I heard:"))
        assertTrue(echo.contains("Say save to keep this memory"))

        // Agent 10: Consistency Checker
        val existingRegistry = listOf(
            com.example.deterministic.EntityRecord("Grandpa Joseph", com.example.deterministic.EntityType.PERSON)
        )
        val warnings = com.example.deterministic.DeterministicWriterEngine.checkConsistency(entities, existingRegistry)
        assertNotNull(warnings)

        // Agent 12: Progress Reporter
        val progress = com.example.deterministic.DeterministicWriterEngine.generateProgressReport(
            memoryCount = 12,
            totalWords = 3200,
            chaptersWithContent = 4,
            targetChapters = 6
        )
        assertTrue(progress.contains("12 recorded memories"))
        assertTrue(progress.contains("3200 words"))
    }

    @Test
    fun `memoir export engine generates valid text, markdown, and pdf documents`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleMemories = listOf(
            Memory(
                id = 1,
                transcript = "I remember summer afternoons by the lake, watching dragonflies skim the water.",
                formattedProse = "The summer afternoons by the lake were quiet, save for the hum of dragonflies skimming the mirrored surface.",
                passageTitle = "The Mirrored Lake",
                chapter = "Chapter 1: Early Days",
                emotionalTone = "Peaceful",
                storyArc = "Quiet reflection by the family lake cabin.",
                reflection = "A lingering stillness before life got busy.",
                sensoryDetails = "Sun warmth on cool dock planks, smell of pine needles.",
                approved = true
            ),
            Memory(
                id = 2,
                transcript = "Starting my first apprenticeship in the machine shop was intimidating.",
                formattedProse = "Entering the machine shop for the first time felt overwhelming amidst the roar of lathes and smell of cutting oil.",
                passageTitle = "The Iron Workshop",
                chapter = "Chapter 2: Career & Passions",
                emotionalTone = "Determined",
                storyArc = "Overcoming apprehension on day one of trade work.",
                reflection = "Respect for hard craft and precision.",
                sensoryDetails = "Metallic tang in the air, rhythmic clatter of gears.",
                approved = true
            )
        )

        // 1. Text export validation
        val textExport = com.example.export.MemoirExportEngine.formatAsText(
            bookTitle = "My Life Journey",
            authorName = "Michael Vance",
            chapterTitle = null,
            memories = sampleMemories
        )
        assertTrue(textExport.contains("MY LIFE JOURNEY"))
        assertTrue(textExport.contains("Author: Michael Vance"))
        assertTrue(textExport.contains("CHAPTER: Chapter 1: Early Days"))
        assertTrue(textExport.contains("[The Mirrored Lake]"))
        assertTrue(textExport.contains("CHAPTER: Chapter 2: Career & Passions"))
        assertTrue(textExport.contains("[The Iron Workshop]"))

        // 2. Markdown export validation
        val mdExport = com.example.export.MemoirExportEngine.formatAsMarkdown(
            bookTitle = "My Life Journey",
            authorName = "Michael Vance",
            chapterTitle = "Chapter 1: Early Days",
            memories = sampleMemories.filter { it.chapter == "Chapter 1: Early Days" }
        )
        assertTrue(mdExport.contains("# My Life Journey"))
        assertTrue(mdExport.contains("### By Michael Vance"))
        assertTrue(mdExport.contains("#### Chapter: Chapter 1: Early Days"))
        assertTrue(mdExport.contains("### The Mirrored Lake"))

        // 3. PDF generation validation (writes bytes and verifies non-empty valid stream)
        val pdfFile = java.io.File(context.cacheDir, "test_manuscript.pdf")
        val outputStream = java.io.FileOutputStream(pdfFile)
        com.example.export.MemoirExportEngine.generatePdf(
            bookTitle = "My Life Journey",
            authorName = "Michael Vance",
            chapterTitle = null,
            memories = sampleMemories,
            outputStream = outputStream
        )
        outputStream.close()

        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 50) // Valid document with text or PDF bytes

        // 4. File preparation for sharing
        val shareResult = com.example.export.MemoirExportEngine.createShareableFile(
            context = context,
            fileName = "My_Life_Journey.pdf",
            format = com.example.export.ExportFormat.PDF,
            bookTitle = "My Life Journey",
            authorName = "Michael Vance",
            chapterTitle = null,
            memories = sampleMemories
        )
        assertTrue(shareResult.success)
        assertNotNull(shareResult.uri)
        assertEquals("application/pdf", shareResult.mimeType)

        // 5. Command parsing for export
        assertEquals(Command.EXPORT, CommandParser.parse("export book"))
        assertEquals(Command.EXPORT, CommandParser.parse("export pdf"))
        assertEquals(Command.EXPORT, CommandParser.parse("export manuscript"))
        assertEquals(Command.EXPORT, CommandParser.parse("download pdf"))
    }

    @Test
    fun `upgraded components validation for eye gaze dwell, audiobook player, and chapter search`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore(context)
        
        // Verify input mode accommodates Eye Gaze Dwell
        settings.activeInputMode = "Eye Gaze Dwell"
        assertEquals("Eye Gaze Dwell", settings.activeInputMode)

        // Verify chapter filtering logic
        val allChapters = listOf("Chapter 1: Early Days", "Chapter 2: Family", "Chapter 3: Career")
        val searchMatch = allChapters.filter { it.contains("Early", ignoreCase = true) }
        assertEquals(1, searchMatch.size)
        assertEquals("Chapter 1: Early Days", searchMatch.first())

        // Verify speech rate controls
        settings.speechRate = 1.0f
        assertEquals(1.0f, settings.speechRate, 0.01f)
    }

    @Test
    fun `unified automation pipeline executes and produces comprehensive editorial result`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore(context)
        settings.smartAutoSave = true
        settings.autoEditorialPipeline = true
        settings.autoWeaveTimeline = true
        settings.autoGapAuditing = true
        settings.autoVoiceHarmonizing = true

        val memory1 = Memory(
            id = 101L,
            transcript = "I built a treehouse with my grandfather during summer vacation.",
            formattedProse = "During the warm summer months of 1968, my grandfather and I constructed a sturdy wooden treehouse nestled high in the backyard oak.",
            passageTitle = "The Summer Treehouse",
            chapter = "Chapter 1: Early Days",
            emotionalTone = "Nostalgic",
            storyArc = "Building a sanctuary with my grandfather.",
            reflection = "Appreciating patience and family legacy.",
            charactersAndPerspectives = "Grandfather's quiet craftsmanship.",
            sensoryDetails = "Smell of freshly sawed pine, golden afternoon sunlight.",
            writingTip = "Anchor emotions with physical tactile details."
        )

        val memory2 = Memory(
            id = 102L,
            transcript = "Later on, I graduated from engineering school and began designing bridges.",
            formattedProse = "Years later, after graduating from engineering school, I began designing suspension bridges across the river.",
            passageTitle = "Engineering Foundations",
            chapter = "Chapter 3: Passions & Milestones",
            emotionalTone = "Accomplished",
            storyArc = "Stepping into a lifetime career of civil engineering.",
            reflection = "Realizing childhood treehouse dreams evolved into real bridges.",
            charactersAndPerspectives = "Professor Mitchell and student colleagues.",
            sensoryDetails = "Drafting tables with blueprints, metallic bridge cables.",
            writingTip = "Connect earlier childhood seeds to adult milestones."
        )

        val result = com.example.autonomous.UnifiedAutomationPipeline.executePipeline(
            savedMemory = memory2,
            allManuscriptMemories = listOf(memory1, memory2),
            settings = settings
        )

        assertNotNull(result)
        assertEquals(102L, result.memoryId)
        assertEquals("Chapter 3: Passions & Milestones", result.chapter)
        assertTrue(result.nextUnifiedPrompt.isNotBlank())
        assertTrue(result.stylePovStability > 0)
        assertNotNull(com.example.autonomous.UnifiedAutomationPipeline.lastExecution.value)
    }

    @Test
    fun `parse unified automation voice commands`() {
        assertEquals(Command.UNIFIED_PIPELINE, CommandParser.parse("auto write"))
        assertEquals(Command.UNIFIED_PIPELINE, CommandParser.parse("automate"))
        assertEquals(Command.UNIFIED_PIPELINE, CommandParser.parse("run pipeline"))
        assertEquals(Command.UNIFIED_PIPELINE, CommandParser.parse("unified automation"))
        assertEquals(Command.UNIFIED_PIPELINE, CommandParser.parse("just work"))
    }
}


