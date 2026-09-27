package com.example.ai

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Minimal loopback HTTP server for exercising the network-backed AI engines without
 * MockWebServer. Responses are registered by exact path prefix before [start].
 */
class LocalHttpServer {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val responses = LinkedHashMap<String, Pair<Int, String>>()
    private val bodies = ConcurrentHashMap<String, MutableList<String>>()
    private val authHeaders = ConcurrentHashMap<String, MutableList<String>>()

    fun respond(path: String, body: String, status: Int = 200): LocalHttpServer {
        responses[path] = status to body
        return this
    }

    fun start(): LocalHttpServer {
        responses.forEach { (path, response) ->
            server.createContext(path) { exchange ->
                val received = exchange.requestBody.use { it.readBytes() }
                bodies.getOrPut(path) { CopyOnWriteArrayList() }.add(String(received, Charsets.UTF_8))
                exchange.requestHeaders.getFirst("Authorization")?.let {
                    authHeaders.getOrPut(path) { CopyOnWriteArrayList() }.add(it)
                }
                val bytes = response.second.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(response.first, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.executor = null
        server.start()
        return this
    }

    fun lastRequestBody(path: String): String? = bodies[path]?.lastOrNull()

    fun lastAuthHeader(path: String): String? = authHeaders[path]?.lastOrNull()

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun stop() = server.stop(0)
}

/** Builds an [AiConfig] with only the providers a test wants enabled. */
fun testConfig(
    geminiKey: String = "",
    ollamaUrl: String = "",
    ollamaModel: String = "qwen3:4b",
    ollamaApiKey: String = "",
    jevBaseUrl: String = "",
    jevKey: String = "",
    jevLocalUrl: String = ""
): AiConfig = AiConfig(
    geminiKey = geminiKey,
    ollamaUrl = ollamaUrl,
    ollamaModel = ollamaModel,
    ollamaApiKey = ollamaApiKey,
    jevBaseUrl = jevBaseUrl,
    jevModel = "typesafe-ai/jev",
    jevKey = jevKey,
    gatewayTextModel = "openai/gpt-4o-mini",
    jevLocalUrl = jevLocalUrl,
    jevLocalModel = "jev-latest"
)

/** Wraps an OpenAI-compatible chat completion response around [content]. */
fun chatCompletion(content: String): String =
    """{"choices":[{"message":{"role":"assistant","content":${org.json.JSONObject.quote(content)}}}]}"""
