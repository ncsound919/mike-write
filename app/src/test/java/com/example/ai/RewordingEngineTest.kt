package com.example.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RewordingEngineTest {

    private fun engine(text: AiConfig, jev: JevClient): RewordingEngine =
        RewordingEngine(AiTextEngine { text }, jev)

    @Test
    fun `blank passage is rejected before any provider call`() = runBlocking {
        val outcome = engine(testConfig(), JevClient { testConfig() }).reword("   ", "Chapter 1")
        assertNull(outcome.rewordedText)
        assertFalse(outcome.shouldApply)
        assertEquals("Nothing to reword", outcome.error)
    }

    @Test
    fun `unconfigured text engine reports the missing provider`() = runBlocking {
        val outcome = engine(testConfig(), JevClient { testConfig() }).reword("Original text", "Chapter 1")
        assertNull(outcome.rewordedText)
        assertTrue(outcome.error!!.contains("No AI text provider configured"))
    }

    @Test
    fun `failed text generation is surfaced`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", "boom", status = 500)
            .start()
        try {
            val textCfg = testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test")
            val outcome = engine(textCfg, JevClient { textCfg }).reword("Original text", "Chapter 1")
            assertNull(outcome.rewordedText)
            assertEquals("AI text generation failed", outcome.error)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `unparseable model output is surfaced`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("""{"notes":"no reworded text"}"""))
            .start()
        try {
            val textCfg = testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test")
            val outcome = engine(textCfg, JevClient { textCfg }).reword("Original text", "Chapter 1")
            assertNull(outcome.rewordedText)
            assertEquals("AI returned unparseable output", outcome.error)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `faithful high quality rewrite is approved with jev attribution`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("""{"rewordedText":"Better text","notes":"tightened"}"""))
            .respond("/v1/systemone", """{"answers":{"faithful":{"noul":0.9},"quality":{"score":2.0}}}""")
            .start()
        try {
            val textCfg = testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test")
            val jevCfg = testConfig(jevLocalUrl = server.baseUrl)
            val outcome = engine(textCfg, JevClient { jevCfg }).reword("Original text", "Chapter 1")
            assertEquals("Better text", outcome.rewordedText)
            assertEquals("tightened", outcome.notes)
            assertEquals("ai+jev(localjev)", outcome.source)
            assertEquals(0.9, outcome.faithfulProbability!!, 1e-9)
            assertEquals(2.0, outcome.qualityScore!!, 1e-9)
            assertTrue(outcome.shouldApply)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `offline jev still surfaces the rewrite for the author to decide`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("""{"rewordedText":"Better text"}"""))
            .start()
        try {
            val textCfg = testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test")
            val outcome = engine(textCfg, JevClient { testConfig() }).reword("Original text", "Chapter 1")
            assertEquals("Better text", outcome.rewordedText)
            assertEquals("ai", outcome.source)
            assertNull(outcome.faithfulProbability)
            assertNull(outcome.qualityScore)
            assertTrue(outcome.shouldApply)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `unfaithful rewrite is blocked when jev says meaning changed`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("""{"rewordedText":"Changed meaning"}"""))
            .respond("/v1/systemone", """{"answers":{"faithful":{"noul":0.2},"quality":{"score":3.0}}}""")
            .start()
        try {
            val textCfg = testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test")
            val jevCfg = testConfig(jevLocalUrl = server.baseUrl)
            val outcome = engine(textCfg, JevClient { jevCfg }).reword("Original text", "Chapter 1")
            assertEquals(0.2, outcome.faithfulProbability!!, 1e-9)
            assertFalse(outcome.shouldApply)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `low readability score is blocked`() = runBlocking {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("""{"rewordedText":"Meh"}"""))
            .respond("/v1/systemone", """{"answers":{"faithful":{"noul":0.9},"quality":{"score":0.5}}}""")
            .start()
        try {
            val textCfg = testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test")
            val jevCfg = testConfig(jevLocalUrl = server.baseUrl)
            val outcome = engine(textCfg, JevClient { jevCfg }).reword("Original text", "Chapter 1")
            assertEquals(0.5, outcome.qualityScore!!, 1e-9)
            assertFalse(outcome.shouldApply)
        } finally {
            server.stop()
        }
    }
}
