package com.example.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
class JevClientTest {

    private fun questions() = JSONObject().apply {
        put("faithful", JSONObject().apply {
            put("type", "noul")
            put("instructions", "Does it preserve meaning?")
        })
        put("quality", JSONObject().apply {
            put("type", "score")
            put("instructions", "Rate readability")
        })
    }

    private fun state() = JSONObject().apply {
        put("action", "memoir_reword")
        put("original", "a")
        put("reworded", "b")
    }

    // --- JevOutcome is a pure value type: verify every wire shape it tolerates ---

    @Test
    fun `probability reads every supported wire key`() {
        val answers = JSONObject(
            """{"a":{"noul":0.7},"b":{"probability":0.5},"c":{"value":0.2},"d":{"score":0.3},"e":{"choice":"x"}}"""
        )
        val outcome = JevOutcome(true, "test", answers)
        assertEquals(0.7, outcome.probability("a")!!, 1e-9)
        assertEquals(0.5, outcome.probability("b")!!, 1e-9)
        assertEquals(0.2, outcome.probability("c")!!, 1e-9)
        assertEquals(0.3, outcome.probability("d")!!, 1e-9)
        assertNull(outcome.probability("e"))
        assertNull(outcome.probability("missing"))
        assertNull(JevOutcome(false, "offline").probability("a"))
    }

    @Test
    fun `score and choice only read their own keys`() {
        val answers = JSONObject("""{"f":{"score":2.0},"g":{"choice":"  Yes  "},"h":{"noul":0.9}}""")
        val outcome = JevOutcome(true, "test", answers)
        assertEquals(2.0, outcome.score("f")!!, 1e-9)
        assertNull(outcome.score("h"))
        assertNull(outcome.score("missing"))
        assertEquals("Yes", outcome.choice("g"))
        assertNull(outcome.choice("h"))
    }

    // --- decideSystemOne tier chain ---

    @Test
    fun `no tier configured returns offline without any call`() = runBlocking {
        val client = JevClient { testConfig() }
        val outcome = client.decideSystemOne(state(), questions())
        assertFalse(outcome.ok)
        assertEquals("offline", outcome.source)
        assertTrue(outcome.error!!.contains("not configured"))
    }

    @Test
    fun `local tier answers and normalizes probability and score`() {
        val server = LocalHttpServer()
            .respond("/v1/systemone", """{"answers":{"faithful":{"noul":0.81},"quality":{"score":2.0}}}""")
            .start()
        try {
            val client = JevClient { testConfig(jevLocalUrl = server.baseUrl) }
            val outcome = runBlocking { client.decideSystemOne(state(), questions()) }
            assertTrue(outcome.ok)
            assertEquals("localjev", outcome.source)
            assertEquals(0.81, outcome.probability("faithful")!!, 1e-9)
            assertEquals(2.0, outcome.score("quality")!!, 1e-9)
            // Local tier receives the caller's native "noul" questions unchanged.
            assertTrue(server.lastRequestBody("/v1/systemone")!!.contains("\"noul\""))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `gateway tier answers and translates noul questions to boolean`() {
        val server = LocalHttpServer()
            .respond("/v1/evaluate", """{"answers":{"faithful":{"noul":0.9}}}""")
            .start()
        try {
            val client = JevClient { testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test") }
            val outcome = runBlocking { client.decideSystemOne(state(), questions()) }
            assertTrue(outcome.ok)
            assertEquals("vercel", outcome.source)
            assertEquals(0.9, outcome.probability("faithful")!!, 1e-9)
            val body = server.lastRequestBody("/v1/evaluate")!!
            assertTrue(body.contains("\"type\":\"boolean\""))
            assertFalse(body.contains("\"type\":\"noul\""))
            assertEquals("Bearer vck_test", server.lastAuthHeader("/v1/evaluate"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `gateway failure falls back to the local tier`() {
        val server = LocalHttpServer()
            .respond("/v1/evaluate", """{"error":"restricted"}""", status = 403)
            .respond("/v1/systemone", """{"answers":{"faithful":{"noul":0.6}}}""")
            .start()
        try {
            val client = JevClient {
                testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test", jevLocalUrl = server.baseUrl)
            }
            val outcome = runBlocking { client.decideSystemOne(state(), questions()) }
            assertTrue(outcome.ok)
            assertEquals("localjev", outcome.source)
            assertEquals(0.6, outcome.probability("faithful")!!, 1e-9)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `both tiers failing returns offline with the last error`() {
        val server = LocalHttpServer()
            .respond("/v1/evaluate", "nope", status = 500)
            .respond("/v1/systemone", "nope", status = 500)
            .start()
        try {
            val client = JevClient {
                testConfig(jevBaseUrl = server.baseUrl, jevKey = "vck_test", jevLocalUrl = server.baseUrl)
            }
            val outcome = runBlocking { client.decideSystemOne(state(), questions()) }
            assertFalse(outcome.ok)
            assertEquals("offline", outcome.source)
            assertTrue(outcome.error!!.contains("HTTP 500"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `response without answers object is treated as offline`() {
        val server = LocalHttpServer()
            .respond("/v1/systemone", """{"unexpected":true}""")
            .start()
        try {
            val client = JevClient { testConfig(jevLocalUrl = server.baseUrl) }
            val outcome = runBlocking { client.decideSystemOne(state(), questions()) }
            assertFalse(outcome.ok)
            assertTrue(outcome.error!!.contains("missing answers"))
        } finally {
            server.stop()
        }
    }
}
