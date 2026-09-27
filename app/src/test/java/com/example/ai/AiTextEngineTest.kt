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
class AiTextEngineTest {

    @Test
    fun `engine reports unconfigured and returns null without any provider`() {
        val engine = AiTextEngine { testConfig() }
        assertFalse(engine.isConfigured)
        assertNull(runBlocking { engine.generate("hello") })
    }

    @Test
    fun `gateway path returns generated content and advertises configured`() {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("hello world"))
            .start()
        try {
            val engine = AiTextEngine { testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test") }
            assertTrue(engine.isConfigured)
            assertEquals("hello world", runBlocking { engine.generate("hi") })
        } finally {
            server.stop()
        }
    }

    @Test
    fun `json mode injects a system instruction`() {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("{}"))
            .start()
        try {
            val engine = AiTextEngine { testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test") }
            runBlocking { engine.generate("give me json", jsonMode = true, maxTokens = 128) }
            val body = server.lastRequestBody("/v1/chat/completions")!!
            assertTrue(body.contains("single valid JSON object"))
            assertTrue(body.contains("\"max_tokens\":128"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `gateway failure falls back to native ollama`() {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", "boom", status = 500)
            .respond("/api/generate", """{"response":"local says hi"}""")
            .start()
        try {
            val engine = AiTextEngine {
                testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test", ollamaUrl = server.baseUrl)
            }
            assertEquals("local says hi", runBlocking { engine.generate("hi") })
        } finally {
            server.stop()
        }
    }

    @Test
    fun `native ollama failure falls back to the openai-compatible v1 endpoint`() {
        val server = LocalHttpServer()
            .respond("/api/generate", "boom", status = 500)
            .respond("/v1/chat/completions", chatCompletion("via openai compat"))
            .start()
        try {
            val engine = AiTextEngine { testConfig(ollamaUrl = server.baseUrl) }
            assertEquals("via openai compat", runBlocking { engine.generate("hi") })
        } finally {
            server.stop()
        }
    }

    @Test
    fun `all providers failing returns null`() {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", "boom", status = 500)
            .start()
        try {
            val engine = AiTextEngine { testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test") }
            assertNull(runBlocking { engine.generate("hi") })
        } finally {
            server.stop()
        }
    }

    @Test
    fun `blank model output is treated as no result`() {
        val server = LocalHttpServer()
            .respond("/v1/chat/completions", chatCompletion("   "))
            .start()
        try {
            val engine = AiTextEngine { testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test") }
            assertNull(runBlocking { engine.generate("hi") })
        } finally {
            server.stop()
        }
    }
}
