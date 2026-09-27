package com.example.ai

import com.example.deterministic.DeterministicRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InterviewerAiTest {

    @Before
    fun setUp() {
        DeterministicRouter.resetSessionCounters()
    }

    private fun offlineInterviewer() = Interviewer(AiTextEngine { testConfig() })

    private fun onlineInterviewer(baseUrl: String) =
        Interviewer(AiTextEngine { testConfig(jevBaseUrl = baseUrl, jevKey = "vck_test") })

    private val memoryText = "Grandpa showed me his carpentry tools in the dusty workshop."

    private val elementsJson = """
        {"passageTitle":"The Dusty Workshop","formattedProse":"Grandpa showed me his carpentry tools.",
         "emotionalTone":"Nostalgic","assignedChapter":"Chapter 1: Early Days","createNewChapter":false,
         "newChapterDescription":"","newChapterTargetWords":1800,"storyArc":"Learning craft",
         "reflection":"Honest work builds character","charactersAndPerspectives":"Grandpa",
         "sensoryDetails":"Cedar and sawdust","writingTip":"Show, don't tell"}
    """.trimIndent()

    @Test
    fun `unconfigured engine degrades to deterministic analysis`() = runBlocking {
        val elements = offlineInterviewer().analyzeBookElements(memoryText, "Early Days")
        assertTrue(elements.formattedProse.isNotBlank())
        assertTrue(elements.storyArc.isNotBlank())
        assertEquals("Early Days", elements.assignedChapter)
        assertFalse(elements.createNewChapter)
    }

    @Test
    fun `ai analysis parses a full JSON response`() = runBlocking {
        val server = LocalHttpServer().respond("/v1/chat/completions", chatCompletion(elementsJson)).start()
        try {
            val elements = onlineInterviewer(server.baseUrl).analyzeBookElements(memoryText, "Early Days")
            assertEquals("The Dusty Workshop", elements.passageTitle)
            assertEquals("Nostalgic", elements.emotionalTone)
            assertEquals("Chapter 1: Early Days", elements.assignedChapter)
            assertEquals(1800, elements.newChapterTargetWords)
            assertEquals("Show, don't tell", elements.writingTip)
            assertFalse(elements.createNewChapter)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `ai analysis flags a genuinely new chapter`() = runBlocking {
        val newChapterJson = elementsJson
            .replace("\"Chapter 1: Early Days\"", "\"The War Years\"")
            .replace("\"createNewChapter\":false", "\"createNewChapter\":true")
        val server = LocalHttpServer().respond("/v1/chat/completions", chatCompletion(newChapterJson)).start()
        try {
            val elements = onlineInterviewer(server.baseUrl)
                .analyzeBookElements(memoryText, "Early Days", existingChapters = listOf("Chapter 1: Early Days"))
            assertEquals("The War Years", elements.assignedChapter)
            assertTrue(elements.createNewChapter)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `garbage model output falls back to deterministic analysis`() = runBlocking {
        val server = LocalHttpServer().respond("/v1/chat/completions", chatCompletion("not json at all")).start()
        try {
            val elements = onlineInterviewer(server.baseUrl).analyzeBookElements(memoryText, "Early Days")
            assertTrue(elements.formattedProse.isNotBlank())
            assertEquals("Early Days", elements.assignedChapter)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `chapter prompt uses the model when online`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("What did the workshop smell like?"))
            .start()
        try {
            val prompt = onlineInterviewer(server.baseUrl)
                .generateChapterPromptWithCraft("The Workshop", "sensory details")
            assertEquals("What did the workshop smell like?", prompt)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `chapter prompt fallback covers each craft focus`() = runBlocking {
        val offline = offlineInterviewer()
        assertTrue(offline.generateChapterPromptWithCraft("Ch 1", "sensory details").contains("sights, sounds, or smells"))
        assertTrue(offline.generateChapterPromptWithCraft("Ch 1", "character").contains("influential person"))
        assertTrue(offline.generateChapterPromptWithCraft("Ch 1", "reflection").contains("change the way you see"))
        assertTrue(offline.generateChapterPrompt("Ch 1").contains("What happened first"))
    }

    @Test
    fun `book summary uses the model when online and falls back offline`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("A life of quiet craft and family."))
            .start()
        try {
            val summary = onlineInterviewer(server.baseUrl)
                .synthesizeBookSummary("My Life", "Mike", 3, listOf("a", "b"))
            assertEquals("A life of quiet craft and family.", summary)
        } finally {
            server.stop()
        }
        val offlineSummary = offlineInterviewer().synthesizeBookSummary("My Life", "Mike", 3, listOf("a"))
        assertTrue(offlineSummary.contains("My Life"))
    }

    @Test
    fun `publishing critique uses the model online and falls back offline`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("Strong voice; tighten chapter two."))
            .start()
        try {
            val critique = onlineInterviewer(server.baseUrl)
                .synthesizePublishingCritique("My Life", "Mike", 3, 1200, listOf("passage"))
            assertEquals("Strong voice; tighten chapter two.", critique)
        } finally {
            server.stop()
        }
        val offlineCritique = offlineInterviewer()
            .synthesizePublishingCritique("My Life", "Mike", 3, 1200, listOf("passage"))
        assertTrue(offlineCritique.contains("My Life"))
    }

    @Test
    fun `follow up returns a deterministic question`() = runBlocking {
        val question = offlineInterviewer().followUp(memoryText, "Early Days")
        assertTrue(question.isNotBlank())
    }
}
