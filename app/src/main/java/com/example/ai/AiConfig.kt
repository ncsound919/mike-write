package com.example.ai

import com.example.BuildConfig
import com.example.data.SettingsStore

/**
 * Resolved AI provider configuration.
 *
 * Precedence: runtime settings (entered by the caregiver) override the values
 * compiled from `.env` into [BuildConfig], which override hardcoded defaults.
 * A provider is only usable when its credential/endpoint is non-placeholder.
 */
data class AiConfig(
    val geminiKey: String,
    val ollamaUrl: String,
    val ollamaModel: String,
    val ollamaApiKey: String,
    // Vercel AI Gateway — shared by gateway text and Jev (needs a paid-capable key).
    val jevBaseUrl: String,
    val jevModel: String,
    val jevKey: String,
    val gatewayTextModel: String,
    // Jev tier 2 — local Jev / Dev-Brain (no key). Mirrors the fleet chain.
    val jevLocalUrl: String,
    val jevLocalModel: String
) {
    val geminiEnabled: Boolean get() = !isPlaceholder(geminiKey)
    val ollamaEnabled: Boolean get() = !isPlaceholder(ollamaUrl)

    private val gatewayConfigured: Boolean get() = !isPlaceholder(jevBaseUrl) && !isPlaceholder(jevKey)

    /** Gateway can serve language models well before the eval model is unlocked. */
    val gatewayTextEnabled: Boolean get() = gatewayConfigured
    val jevGatewayEnabled: Boolean get() = gatewayConfigured
    val jevLocalEnabled: Boolean get() = !isPlaceholder(jevLocalUrl)
    val jevEnabled: Boolean get() = jevGatewayEnabled || jevLocalEnabled

    val anyTextProvider: Boolean get() = geminiEnabled || gatewayTextEnabled || ollamaEnabled

    companion object {
        private val PLACEHOLDERS = setOf(
            "my_gemini_api_key", "my_ai_gateway_api_key", "my_ollama_base_url",
            "my_ollama_api_key", "my_jev_local_base_url",
            "your_key_here", "changeme"
        )

        fun isPlaceholder(value: String): Boolean {
            val v = value.trim()
            return v.isBlank() || v.lowercase() in PLACEHOLDERS
        }
    }
}

object AiConfigProvider {

    const val DEFAULT_OLLAMA_MODEL = "qwen3:4b"
    const val DEFAULT_TYPESAFE_BASE_URL = "https://ai-gateway.vercel.sh/typesafe"
    const val DEFAULT_TYPESAFE_MODEL = "typesafe-ai/jev"
    const val DEFAULT_GATEWAY_TEXT_MODEL = "openai/gpt-4o-mini"
    /** Bound on generated tokens so a slow/weak local model can't run away. */
    const val DEFAULT_MAX_OUTPUT_TOKENS = 900
    /** Emulator host-loopback alias to this machine; override for a physical device. */
    const val DEFAULT_JEV_LOCAL_URL = "http://10.0.2.2:8080"
    const val DEFAULT_JEV_LOCAL_MODEL = "jev-latest"

    /** BuildConfig-only configuration (used by tests and default construction). */
    fun default(): AiConfig = resolve("", "", "", "", "", "", "", "")

    /** Settings-over-BuildConfig configuration used by the running app. */
    fun from(settings: SettingsStore): AiConfig = resolve(
        geminiKeyOverride = settings.geminiApiKey,
        ollamaUrlOverride = settings.ollamaUrl,
        ollamaModelOverride = settings.ollamaModel,
        ollamaKeyOverride = settings.ollamaApiKey,
        jevBaseOverride = settings.jevBaseUrl,
        jevModelOverride = settings.jevModel,
        jevKeyOverride = settings.jevApiKey,
        jevLocalOverride = settings.jevLocalUrl
    )

    private fun resolve(
        geminiKeyOverride: String,
        ollamaUrlOverride: String,
        ollamaModelOverride: String,
        ollamaKeyOverride: String,
        jevBaseOverride: String,
        jevModelOverride: String,
        jevKeyOverride: String,
        jevLocalOverride: String
    ): AiConfig {
        val ollamaUrl = firstNonBlank(ollamaUrlOverride, buildConfig { BuildConfig.OLLAMA_BASE_URL })
        val ollamaModel = firstNonBlank(ollamaModelOverride, buildConfig { BuildConfig.OLLAMA_MODEL }, DEFAULT_OLLAMA_MODEL)
        val ollamaKey = firstNonBlank(ollamaKeyOverride, buildConfig { BuildConfig.OLLAMA_API_KEY })
        val jevBase = firstNonBlank(jevBaseOverride, buildConfig { BuildConfig.TYPESAFE_BASE_URL }, DEFAULT_TYPESAFE_BASE_URL)
        val jevModel = firstNonBlank(jevModelOverride, buildConfig { BuildConfig.TYPESAFE_MODEL }, DEFAULT_TYPESAFE_MODEL)
        val jevKey = firstNonBlank(jevKeyOverride, buildConfig { BuildConfig.AI_GATEWAY_API_KEY })
        val jevLocal = firstNonBlank(jevLocalOverride, buildConfig { BuildConfig.JEV_LOCAL_BASE_URL }, DEFAULT_JEV_LOCAL_URL)
        val jevLocalModel = firstNonBlank(buildConfig { BuildConfig.JEV_LOCAL_MODEL }, DEFAULT_JEV_LOCAL_MODEL)
        val gatewayTextModel = firstNonBlank(buildConfig { BuildConfig.AI_GATEWAY_TEXT_MODEL }, DEFAULT_GATEWAY_TEXT_MODEL)
        return AiConfig(
            geminiKey = firstNonBlank(geminiKeyOverride, buildConfig { BuildConfig.GEMINI_API_KEY }),
            ollamaUrl = ollamaUrl,
            ollamaModel = ollamaModel,
            ollamaApiKey = ollamaKey,
            jevBaseUrl = jevBase.trim().trimEnd('/'),
            jevModel = jevModel,
            jevKey = jevKey,
            gatewayTextModel = gatewayTextModel,
            jevLocalUrl = jevLocal.trim().trimEnd('/'),
            jevLocalModel = jevLocalModel
        )
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    private inline fun buildConfig(reader: () -> String): String = try {
        reader()
    } catch (e: Throwable) {
        ""
    }
}
