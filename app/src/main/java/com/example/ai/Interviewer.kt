package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.BookElements
import com.example.deterministic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Intelligent interviewer, literary editor, and memoir coach.
 * Utilizes the Deterministic Writing Engine for ~80% of turns (Cleaner, Segmenter,
 * Entity Registry, Gap Question Selector, Echo Confirmer) and reserves Gemini LLM
 * for nuanced, complex synthesis.
 */
class Interviewer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val recentQuestions = mutableListOf<String>()

    /**
     * Deconstructs and compartmentalizes raw dictated memory into classic literary layers,
     * formats the raw speech into polished manuscript prose, and determines chapter placement or creation.
     */
    suspend fun analyzeBookElements(
        memoryText: String,
        chapter: String = "Life Journey",
        existingChapters: List<String> = emptyList()
    ): BookElements = withContext(Dispatchers.IO) {
        // Step 1: Deterministic cleanup & segmentation
        val cleanedText = CleanerAgent.clean(memoryText)
        val entities = EntityRegistryAgent.extractEntities(cleanedText)
        val sentences = SegmenterAgent.segment(cleanedText)

        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || !DeterministicRouter.canMakeLlmCall()) {
            return@withContext fallbackAnalysis(cleanedText, entities, chapter)
        }

        // Fallthrough to LLM for multi-perspective breakdown, formatting automation & chapter creation
        DeterministicRouter.recordLlmCall(
            task = "analyzeBookElements",
            reason = "Complex narrative passage with ${sentences.size} sentences requiring multi-layer story arc decomposition and chapter auto-classification",
            wouldBeAgent = "Agent 5/6 (Topic Tagger & Arc Extractor)"
        )

        try {
            val chaptersListStr = if (existingChapters.isNotEmpty()) {
                existingChapters.joinToString(", ") { "\"$it\"" }
            } else {
                "\"Chapter 1: Early Days\", \"Chapter 2: Growing Up & Family\", \"Chapter 3: Passions & Milestones\", \"Chapter 4: The Turning Point\", \"Chapter 5: Wisdom & Legacy\""
            }

            val prompt = """
                You are a world-class book editor and writing mentor assisting a first-time author dictating their memoir.
                Analyze the following raw dictated memory from current active chapter "$chapter":
                
                "$cleanedText"
                
                Existing book chapters in the author's database: [$chaptersListStr]
                
                Perform the following 3 tasks:
                1. FORMATTING AUTOMATION: Format the raw spoken transcript into manuscript-ready, polished literary prose with proper paragraph breaks, dialogue quotes (if speech is referenced), and punctuation. Remove filler sounds ("um", "you know") while preserving the author's authentic first-person voice and every factual detail.
                2. PASSAGE & CHAPTER CREATION AUTOMATION:
                   - passageTitle: Create a catchy, concise headline title for this story passage (e.g., "The Day I Met Sarah at the Train Depot").
                   - emotionalTone: Identify the primary mood/tone (e.g., "Nostalgic & Warm", "Triumphant", "Solemn", "Heartfelt").
                   - assignedChapter: Determine if this story belongs to one of the existing chapters listed above, OR if it represents a brand new major era/topic. If an existing chapter fits, use that exact chapter name. If none fit well, propose a concise new chapter name (e.g. "Chapter 7: Military Days overseas").
                   - createNewChapter: Set to true ONLY if you proposed a brand new chapter name not in the existing chapters list. Otherwise set to false.
                   - newChapterDescription: If createNewChapter is true, provide a brief 1-sentence theme focus for the new chapter.
                   - newChapterTargetWords: Target word count for the new chapter (integer, e.g. 2000).
                3. LITERARY BREAKDOWN:
                   - storyArc: The core narrative scene or action event (what physically occurred).
                   - reflection: The narrator's internal reflections, emotional meaning, and retrospective insight.
                   - charactersAndPerspectives: Who was involved and their perspectives, emotions, or relationships to the author.
                   - sensoryDetails: Key physical sensations, atmosphere, sights, sounds, or textures to bring the scene to life.
                   - writingTip: Exactly ONE encouraging, classic writing craft tip tailored to help him expand this scene into full book form.
                
                Respond in valid JSON format only matching this exact structure:
                {
                  "passageTitle": "...",
                  "formattedProse": "...",
                  "emotionalTone": "...",
                  "assignedChapter": "...",
                  "createNewChapter": false,
                  "newChapterDescription": "...",
                  "newChapterTargetWords": 2000,
                  "storyArc": "...",
                  "reflection": "...",
                  "charactersAndPerspectives": "...",
                  "sensoryDetails": "...",
                  "writingTip": "..."
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseString = response.body?.string()
                if (!responseString.isNullOrBlank()) {
                    val root = JSONObject(responseString)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val text = candidates.getJSONObject(0)
                            .optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text")

                        if (!text.isNullOrBlank()) {
                            val json = JSONObject(text.trim())
                            val assigned = json.optString("assignedChapter").trim().ifBlank { chapter }
                            val isNew = json.optBoolean("createNewChapter", false) && !existingChapters.contains(assigned)

                            return@withContext BookElements(
                                passageTitle = json.optString("passageTitle").trim().ifBlank { "Passage in $assigned" },
                                formattedProse = json.optString("formattedProse").trim().ifBlank { cleanedText },
                                emotionalTone = json.optString("emotionalTone").trim().ifBlank { "Reflective" },
                                assignedChapter = assigned,
                                createNewChapter = isNew,
                                newChapterDescription = json.optString("newChapterDescription").trim(),
                                newChapterTargetWords = json.optInt("newChapterTargetWords", 2000),
                                storyArc = json.optString("storyArc").trim(),
                                reflection = json.optString("reflection").trim(),
                                charactersAndPerspectives = json.optString("charactersAndPerspectives").trim(),
                                sensoryDetails = json.optString("sensoryDetails").trim(),
                                writingTip = json.optString("writingTip").trim()
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Interviewer", "Book elements analysis failed: ${e.message}")
        }

        return@withContext fallbackAnalysis(cleanedText, entities, chapter)
    }

    private fun fallbackAnalysis(text: String, entities: List<EntityRecord>, currentChapter: String): BookElements {
        val sentences = SegmenterAgent.segment(text)
        val arc = if (sentences.isNotEmpty()) sentences.first() else text
        val rest = if (sentences.size > 1) sentences.drop(1).joinToString(" ") else "Personal retrospective reflection."
        val people = entities.filter { it.type == EntityType.PERSON }.map { it.name }
        val places = entities.filter { it.type == EntityType.PLACE }.map { it.name }
        val objects = entities.filter { it.type == EntityType.OBJECT }.map { it.name }

        val charactersDesc = if (people.isNotEmpty()) {
            "Shared with ${people.joinToString(", ")}, capturing their presence and emotional vantage point."
        } else {
            "Author's vantage point and the people sharing this pivotal life moment."
        }

        val sensoryDesc = if (places.isNotEmpty() || objects.isNotEmpty()) {
            val anchors = (places + objects).joinToString(", ")
            "Atmosphere grounded by $anchors with ambient sounds and textures."
        } else {
            "Atmosphere and emotional resonance of the setting."
        }

        val analysis = DeterministicWriterEngine.analyzeGaps(text, entities)
        val craftTips = CraftCoachAgent.analyzeCraft(text, analysis, entities)
        val writingTip = if (craftTips.isNotEmpty()) {
            "${craftTips.first().category}: ${craftTips.first().actionableGuidance}"
        } else {
            "First-time author tip: Remember to 'show, don't just tell'—describe physical gestures, facial expressions, and ambient sounds to pull readers right beside you."
        }

        val passageTitle = if (people.isNotEmpty()) {
            "Story of ${people.first()} in $currentChapter"
        } else if (places.isNotEmpty()) {
            "Memories of ${places.first()}"
        } else {
            "Life Moment in $currentChapter"
        }

        return BookElements(
            passageTitle = passageTitle,
            formattedProse = text,
            emotionalTone = "Reflective & Heartfelt",
            assignedChapter = currentChapter,
            createNewChapter = false,
            newChapterDescription = "",
            newChapterTargetWords = 2000,
            storyArc = arc,
            reflection = rest,
            charactersAndPerspectives = charactersDesc,
            sensoryDetails = sensoryDesc,
            writingTip = writingTip
        )
    }

    /**
     * Generates a compassionate single-sentence follow up question based on the user's latest memory.
     * Enforces the Deterministic Question Selector & Template Filler first.
     */
    suspend fun followUp(memory: String, chapter: String = "Life Journey"): String = withContext(Dispatchers.IO) {
        val cleaned = CleanerAgent.clean(memory)
        val entities = EntityRegistryAgent.extractEntities(cleaned)

        // Routing Rule: Step 1 (Transformation) + Step 2 (Selection) + Step 3 (Slot-fill)
        val deterministicQuestion = DeterministicWriterEngine.selectNextQuestion(
            rawMemory = cleaned,
            entities = entities,
            recentQuestions = recentQuestions
        )

        // If we have a great deterministic question, use it immediately (zero cost, 0ms latency)
        if (deterministicQuestion.isNotBlank()) {
            recentQuestions.add(deterministicQuestion)
            if (recentQuestions.size > 8) recentQuestions.removeAt(0)
            return@withContext deterministicQuestion
        }

        // Step 4: Fallthrough to LLM if and only if selection bank exhausted
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || !DeterministicRouter.canMakeLlmCall()) {
            return@withContext "What do you remember most clearly about the people who were there with you?"
        }

        DeterministicRouter.recordLlmCall(
            task = "followUp",
            reason = "Deterministic question bank exhausted for topic in $chapter",
            wouldBeAgent = "Agent 7 (Question Selector Bank Extension)"
        )

        try {
            val systemPrompt = "You are Mike Write, a warm, patient, deeply respectful interviewer helping a paralyzed author dictate his autobiography. " +
                    "Read the author's story and ask exactly ONE short, conversational follow-up question to help him delve deeper into character perspectives, emotions, or vivid sensory details. " +
                    "Keep the question under 20 words. Speak directly to him with compassion. Avoid greeting him or adding preamble."

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemPrompt\n\nAuthor's story in $chapter:\n\"$cleaned\"\n\nOne follow-up question:")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseString = response.body?.string()
                if (!responseString.isNullOrBlank()) {
                    val root = JSONObject(responseString)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val text = parts?.optJSONObject(0)?.optString("text")?.trim()
                        if (!text.isNullOrBlank()) {
                            val cleanText = text.replace("\n", " ").trim('"', ' ')
                            return@withContext cleanText
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Interviewer", "Gemini call failed, using graceful prompt: ${e.message}")
        }

        return@withContext "What do you remember most clearly about the people who were there with you?"
    }

    /**
     * Generates a thought-provoking topic starter prompt for a specific chapter,
     * incorporating craft coach guidance when available.
     */
    suspend fun generateChapterPrompt(chapter: String): String = generateChapterPromptWithCraft(chapter, null)

    suspend fun generateChapterPromptWithCraft(chapter: String, craftFocus: String?): String = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
        val craftInstruction = if (!craftFocus.isNullOrBlank()) {
            " Focus specifically on this storytelling craft need: $craftFocus."
        } else ""

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val fallback = when {
                craftFocus?.contains("sensory", ignoreCase = true) == true ->
                    "What physical sights, sounds, or smells immediately come to mind when you picture $chapter?"
                craftFocus?.contains("character", ignoreCase = true) == true ->
                    "Who was the most influential person beside you in $chapter, and what made them memorable?"
                craftFocus?.contains("reflection", ignoreCase = true) == true ->
                    "Looking back at $chapter, how did those experiences change the way you see yourself today?"
                else -> "Tell me about a memorable moment from $chapter. What happened first?"
            }
            return@withContext fallback
        }

        try {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "You are an interviewer for a life memoir. Ask one engaging opening question for a book chapter titled '$chapter'.$craftInstruction Keep it under 20 words, conversational, and direct.")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseString = response.body?.string()
                if (!responseString.isNullOrBlank()) {
                    val root = JSONObject(responseString)
                    val text = root.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                        ?.optJSONObject(0)?.optString("text")?.trim()
                    if (!text.isNullOrBlank()) {
                        return@withContext text.replace("\n", " ").trim('"', ' ')
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Interviewer", "Gemini chapter prompt failed: ${e.message}")
        }

        return@withContext "Tell me about a memorable moment from $chapter. What happened first?"
    }

    /**
     * Generates an audible book summary or chapter synthesis formatted with literary narrative flow.
     */
    suspend fun synthesizeBookSummary(bookTitle: String, author: String, memoryCount: Int, excerpts: List<String>): String = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || excerpts.isEmpty()) {
            return@withContext "$bookTitle by $author currently contains $memoryCount recorded memories across your life journey."
        }

        try {
            val joined = excerpts.take(6).joinToString("\n- ")
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Write a warm, inspiring 2-sentence literary narrative summary of a memoir titled '$bookTitle' by $author based on these passages:\n- $joined\nHighlight the core narrative arc and emotional themes. Keep it under 38 words for spoken playback.")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseString = response.body?.string()
                val text = JSONObject(responseString ?: "{}").optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                    ?.optJSONObject(0)?.optString("text")?.trim()
                if (!text.isNullOrBlank()) {
                    return@withContext text.replace("\n", " ").trim('"', ' ')
                }
            }
        } catch (e: Exception) {
            Log.e("Interviewer", "Summary generation failed: ${e.message}")
        }

        return@withContext "$bookTitle by $author contains $memoryCount recorded memories, capturing rich life stories, heartfelt narration, and enduring wisdom."
    }

    /**
     * Synthesizes an editorial critique and publishing roadmap assessing pacing, thematic depth, and chapter balance.
     */
    suspend fun synthesizePublishingCritique(
        bookTitle: String,
        author: String,
        chaptersCount: Int,
        totalWords: Int,
        samplePassages: List<String>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
        val sample = samplePassages.take(5).joinToString("\n- ")
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || sample.isBlank()) {
            return@withContext "Your manuscript '$bookTitle' shows genuine voice and heartfelt depth. Focus next on expanding sensory details in earlier chapters and tying personal triumphs to universal life lessons for readers."
        }

        try {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "You are a senior acquisitions book editor reviewing a life memoir manuscript titled '$bookTitle' by $author ($totalWords words, $chaptersCount chapters). Sample excerpts:\n- $sample\nProvide a concise 2-sentence editorial assessment and publishing readiness advice. Focus on voice authenticity, narrative momentum, and readers' emotional takeaway. Keep under 45 words.")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseString = response.body?.string()
                val text = JSONObject(responseString ?: "{}").optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                    ?.optJSONObject(0)?.optString("text")?.trim()
                if (!text.isNullOrBlank()) {
                    return@withContext text.replace("\n", " ").trim('"', ' ')
                }
            }
        } catch (e: Exception) {
            Log.e("Interviewer", "Publishing critique failed: ${e.message}")
        }

        return@withContext "Your manuscript '$bookTitle' shows genuine voice and heartfelt depth. Focus next on expanding sensory details in earlier chapters and tying personal triumphs to universal life lessons for readers."
    }

    private fun getNextFallbackPrompt(): String {
        return "What do you remember most clearly about the people who were there with you?"
    }
}
