package com.example.deterministic

import org.junit.Assert.*
import org.junit.Test

class DeterministicAgentsTest {

    // -------------------------------------------------------------
    // Agent 1: CleanerAgent
    // -------------------------------------------------------------
    @Test
    fun testCleanerAgentRemovesFillersAndFalseStarts() {
        val raw = "Um, you know, I was— I went to the store, like, actually and basically bought some milk."
        val cleaned = CleanerAgent.clean(raw)
        assertFalse(cleaned.contains("Um"))
        assertFalse(cleaned.contains("you know"))
        assertFalse(cleaned.contains("actually"))
        assertFalse(cleaned.contains("basically"))
        assertTrue(cleaned.contains("I went to the store"))

        // Blank check
        assertEquals("", CleanerAgent.clean("   "))

        // Repeated word deduplication
        val repeated = "We went went down to the the river."
        val deduped = CleanerAgent.clean(repeated)
        assertFalse(deduped.contains("went went"))
        assertFalse(deduped.contains("the the"))

        // Trailing connectives
        val trailing = "We had a wonderful picnic and"
        val cleanTrailing = CleanerAgent.clean(trailing)
        assertFalse(cleanTrailing.endsWith("and"))
    }

    // -------------------------------------------------------------
    // Agent 2: SegmenterAgent
    // -------------------------------------------------------------
    @Test
    fun testSegmenterAgentSplitsThoughtsAndPaces() {
        val stream = "My father worked at the mill for forty years. He came home with sawdust in his hair every evening. He always smiled when he opened the back door."
        val sentences = SegmenterAgent.segment(stream)
        assertTrue(sentences.isNotEmpty())
        assertTrue(sentences.size >= 3)

        // Empty string handling
        val emptySentences = SegmenterAgent.segment("")
        assertEquals(0, emptySentences.size)
    }

    // -------------------------------------------------------------
    // Agent 3: EntityRegistryAgent
    // -------------------------------------------------------------
    @Test
    fun testEntityRegistryAgentExtractsEntities() {
        val text = "Grandpa John took his wooden wheelchair to the hospital clinic on Elm Street to meet Dr. Miller."
        val entities = EntityRegistryAgent.extractEntities(text)
        assertTrue(entities.isNotEmpty())

        val names = entities.map { it.name }
        assertTrue(names.any { it.contains("Grandpa") || it.contains("Dr.") || it.contains("John") })
    }

    // -------------------------------------------------------------
    // Agent 4, 5, 7, 8, 9, 10, 12: DeterministicWriterEngine
    // -------------------------------------------------------------
    @Test
    fun testDeterministicWriterEngineDateNormalization() {
        assertEquals("Childhood Era", DeterministicWriterEngine.normalizeDateReference("When I was a kid we played ball"))
        assertEquals("College Years", DeterministicWriterEngine.normalizeDateReference("In college we studied engineering"))
        assertEquals("Summer (previous year)", DeterministicWriterEngine.normalizeDateReference("Last summer at the cabin"))
        assertEquals("1960–1969", DeterministicWriterEngine.normalizeDateReference("Back in the 60s"))
        assertEquals("1970–1979", DeterministicWriterEngine.normalizeDateReference("In the seventies"))
        assertEquals("Post-Injury Period", DeterministicWriterEngine.normalizeDateReference("After the accident everything changed"))
        assertEquals("Recovery Period", DeterministicWriterEngine.normalizeDateReference("In the hospital during rehab"))
        assertNull(DeterministicWriterEngine.normalizeDateReference("Just another regular sentence"))
    }

    @Test
    fun testDeterministicWriterEngineTopicDetection() {
        val childhoodText = "My grandfather and I built a wooden cart in the school yard during summer."
        val topic = DeterministicWriterEngine.tagTopic(childhoodText)
        assertEquals("Childhood & Roots", topic)

        val turningPointText = "The sudden accident took me to the hospital where the doctor performed surgery."
        val topicTurning = DeterministicWriterEngine.tagTopic(turningPointText)
        assertEquals("The Turning Point", topicTurning)

        val legacyText = "I want to share this wisdom and advice with future generations as a lasting legacy."
        val topicLegacy = DeterministicWriterEngine.tagTopic(legacyText)
        assertEquals("Wisdom & Legacy", topicLegacy)
    }

    @Test
    fun testDeterministicWriterEngineGapAnalysis() {
        val textWithoutSensory = "I met with the supervisor at nine o'clock and handed over the report."
        val analysis = DeterministicWriterEngine.analyzeGaps(textWithoutSensory, emptyList())
        assertTrue(analysis.gaps.contains(CoverageGap.MISSING_SENSORY))

        val richText = "The cold winter wind stung my cheeks as I felt deep joy walking down the cobblestone street with Tommy."
        val richAnalysis = DeterministicWriterEngine.analyzeGaps(richText, listOf(EntityRecord("Tommy", EntityType.PERSON)))
        assertFalse(richAnalysis.gaps.contains(CoverageGap.MISSING_SENSORY))
        assertFalse(richAnalysis.gaps.contains(CoverageGap.MISSING_EMOTION))
    }

    @Test
    fun testDeterministicWriterEngineQuestionSelectorAndTemplates() {
        val question = DeterministicWriterEngine.selectNextQuestion(
            rawMemory = "I worked at the old factory.",
            entities = listOf(EntityRecord("Father", EntityType.PERSON))
        )
        assertNotNull(question)
        assertTrue(question.isNotBlank())

        val echo = DeterministicWriterEngine.buildEchoConfirmation(
            cleanedText = "I learned how to fish on Lake Michigan with my father."
        )
        assertNotNull(echo)
        assertTrue(echo.contains("Lake Michigan"))
    }

    @Test
    fun testDeterministicWriterEngineConsistencyAndProgress() {
        val entities = listOf(
            EntityRecord("Grandpa John", EntityType.PERSON)
        )
        val registry = listOf(
            EntityRecord("Grandpa Johnny", EntityType.PERSON)
        )
        val warnings = DeterministicWriterEngine.checkConsistency(entities, registry)
        assertNotNull(warnings)

        val report = DeterministicWriterEngine.generateProgressReport(
            memoryCount = 12,
            totalWords = 1500,
            chaptersWithContent = 3,
            targetChapters = 6
        )
        assertTrue(report.contains("12 recorded memories"))
        assertTrue(report.contains("1500 words"))
    }

    // -------------------------------------------------------------
    // Agent 6: CraftCoachAgent
    // -------------------------------------------------------------
    @Test
    fun testCraftCoachAgentGeneratesTargetedAdvice() {
        val analysis = CoverageAnalysis(
            gaps = listOf(CoverageGap.MISSING_SENSORY, CoverageGap.MISSING_PERSON),
            detectedTopic = "Childhood & Roots"
        )
        val recommendations = CraftCoachAgent.analyzeCraft(
            text = "I went to the clinic with Doctor Miller.",
            analysis = analysis,
            entities = listOf(EntityRecord("Doctor Miller", EntityType.PERSON))
        )
        assertNotNull(recommendations)
        assertTrue(recommendations.isNotEmpty())
    }

    // -------------------------------------------------------------
    // Agent 6: ChapterAssemblerAgent
    // -------------------------------------------------------------
    @Test
    fun testChapterAssemblerAgentOrganizesPassages() {
        val suggested = ChapterAssemblerAgent.suggestChapter(
            text = "When I was seven years old playing with my grandfather.",
            currentChapter = "Chapter 1: Early Days"
        )
        assertEquals("Chapter 1: Early Days", suggested)

        val assembled = ChapterAssemblerAgent.assemble(emptyList())
        assertTrue(assembled.isNotEmpty())
        assertEquals("Chapter 1: Early Days", assembled.first().chapterTitle)
    }

    // -------------------------------------------------------------
    // Agent 11: ExportFormatterAgent
    // -------------------------------------------------------------
    @Test
    fun testExportFormatterAgentFormatsManuscript() {
        val chapters = ChapterAssemblerAgent.assemble(emptyList())
        val formatted = ExportFormatterAgent.formatMarkdown(
            bookTitle = "My Life Story",
            authorName = "Mike",
            dedication = "To my family",
            authorBio = "Writer and engineer",
            chapters = chapters
        )
        assertTrue(formatted.contains("My Life Story"))
        assertTrue(formatted.contains("Mike"))
        assertTrue(formatted.contains("Dedication"))
    }

    // -------------------------------------------------------------
    // DeterministicRouter & Budget Guardrails
    // -------------------------------------------------------------
    @Test
    fun testDeterministicRouterBudgetAndFallthrough() {
        DeterministicRouter.resetSessionCounters()
        assertTrue(DeterministicRouter.canMakeLlmCall())

        for (i in 1..20) {
            DeterministicRouter.recordLlmCall(
                task = "Task $i",
                reason = "Complex synthesis required",
                wouldBeAgent = "Agent 20"
            )
        }

        assertFalse(DeterministicRouter.canMakeLlmCall())
        val logs = DeterministicRouter.getFallthroughLogs()
        assertEquals(20, logs.size)
        assertEquals("Task 1", logs.first().task)

        DeterministicRouter.resetSessionCounters()
        assertTrue(DeterministicRouter.canMakeLlmCall())
    }
}
