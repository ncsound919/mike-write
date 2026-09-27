package com.example.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConfigTest {

    private fun config(
        geminiKey: String = "",
        ollamaUrl: String = "",
        ollamaKey: String = "",
        jevKey: String = "",
        jevLocalUrl: String = ""
    ) = AiConfig(
        geminiKey = geminiKey,
        ollamaUrl = ollamaUrl,
        ollamaModel = "minicpm5-fable",
        ollamaApiKey = ollamaKey,
        jevBaseUrl = "https://ai-gateway.vercel.sh/typesafe",
        jevModel = "typesafe-ai/jev",
        jevKey = jevKey,
        gatewayTextModel = "openai/gpt-4o-mini",
        jevLocalUrl = jevLocalUrl,
        jevLocalModel = "jev-latest"
    )

    @Test
    fun placeholderCredentialsDisableProviders() {
        val c = config(geminiKey = "MY_GEMINI_API_KEY", ollamaUrl = "MY_OLLAMA_BASE_URL", jevKey = "MY_AI_GATEWAY_API_KEY")
        assertFalse(c.geminiEnabled)
        assertFalse(c.ollamaEnabled)
        assertFalse(c.jevGatewayEnabled)
        assertFalse(c.jevEnabled)
        assertFalse(c.anyTextProvider)
    }

    @Test
    fun realCredentialsEnableProviders() {
        val c = config(
            geminiKey = "AIza-real",
            ollamaUrl = "https://ollama.com",
            ollamaKey = "094decf5.abc",
            jevKey = "vck_live"
        )
        assertTrue(c.geminiEnabled)
        assertTrue(c.ollamaEnabled)
        assertTrue(c.gatewayTextEnabled)
        assertTrue(c.jevGatewayEnabled)
        assertTrue(c.jevEnabled)
        assertTrue(c.anyTextProvider)
    }

    @Test
    fun gatewayAloneEnablesTextWithoutGeminiOrLocal() {
        val c = config(jevKey = "vck_live")
        assertFalse(c.geminiEnabled)
        assertFalse(c.ollamaEnabled)
        assertTrue(c.gatewayTextEnabled)
        assertTrue(c.anyTextProvider)
    }

    @Test
    fun ollamaCloudNeedsNoGemini() {
        val c = config(ollamaUrl = "https://ollama.com", ollamaKey = "094decf5.abc")
        assertFalse(c.geminiEnabled)
        assertTrue(c.anyTextProvider)
    }

    @Test
    fun localJevTierIsActiveWithoutAGatewayKey() {
        // This is the current fleet reality: the gateway key is free-tier and the
        // typesafe-ai/jev model is restricted, so the local tier keeps Jev active.
        val c = config(jevKey = "MY_AI_GATEWAY_API_KEY", jevLocalUrl = "http://10.0.2.2:8080")
        assertFalse(c.jevGatewayEnabled)
        assertTrue(c.jevLocalEnabled)
        assertTrue(c.jevEnabled)
    }

    @Test
    fun blankAndCaseInsensitivePlaceholdersAreRejected() {
        assertTrue(AiConfig.isPlaceholder(""))
        assertTrue(AiConfig.isPlaceholder("   "))
        assertTrue(AiConfig.isPlaceholder("my_gemini_api_key"))
        assertTrue(AiConfig.isPlaceholder("MY_OLLAMA_BASE_URL"))
        assertTrue(AiConfig.isPlaceholder("MY_JEV_LOCAL_BASE_URL"))
        assertFalse(AiConfig.isPlaceholder("https://ollama.com"))
        assertFalse(AiConfig.isPlaceholder("AIzaSyRealKey"))
    }
}
