package com.example.ai

import android.util.Log
import com.example.data.BookChapters
import com.example.data.BookElements
import com.example.deterministic.CleanerAgent
import com.example.deterministic.CraftCoachAgent
import com.example.deterministic.DeterministicRouter
import com.example.deterministic.DeterministicWriterEngine
import com.example.deterministic.EntityRecord
import com.example.deterministic.EntityRegistryAgent
import com.example.deterministic.EntityType
import com.example.deterministic.SegmenterAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Intelligent interviewer, literary editor, and memoir coach.
 *
 * Deterministic agents handle ~80% of turns (Cleaner, Segmenter, Entity Registry,
 * Date Normalizer, Topic Tagger, Question Selector, Echo Confirmer). Generative
 * calls go through [AiTextEngine], which prefers Gemini and falls back to Ollama;
 * when neither is configured every method degrades to a deterministic result.
 */
class Interviewer(
    private val textEngine: AiTextEngine = AiTextEngine { AiConfigProvider.default() }
) {

    private val recentQuestions = mutableListOf<String>()

    suspend fun analyzeBookElements(
        memoryText: String,
        chapter: String = "Life Journey",
        existingChapters: List<String> = emptyList()
    ): BookElements = withContext(Dispatchers.IO) {
        val cleanedText = CleanerAgent.clean(memoryText)
        val entities = EntityRegistryAgent.extractEntities(cleanedText)
        val sentences = SegmenterAgent.segment(cleanedText)

        if (!textEngine.isConfigured || !DeterministicRouter.canMakeLlmCall()) {
            return@withContext fallbackAnalysis(cleanedText, entities, chapter)
        }

        DeterministicRouter.recordLlmCall(
            task = "analyzeBookElements",
            reason = "Complex narrative passage with ${sentences.size} sentences requiring multi-layer story arc decomposition and chapter auto-classification",
            wouldBeAgent = "Agent 5/6 (Topic Tagger & Arc Extractor)"
        )

        val chaptersListStr = if (existingChapters.isNotEmpty()) {
            existingChapters.joinToString(", ") { "\"$it\"" }
        } else {
            BookChapters.STANDARD.joinToString(", ") { "\"$it\"" }
        }

        val prompt = """
            You are a world-class book editor and writing mentor assisting a first-time author dictating their memoir.
            Analyze the following raw dictated memory from current active chapter "$chapter":
            
            "$cleanedText"
            
            Existing book chapters in the author's database: [$chaptersListStr]
            
            Perform the following 3 tasks:
            1. FORMATTING AUTOMATION: Format the raw spoken transcript into manuscript-ready, polished literary prose with proper paragraph breaks, dialogue quotes (if speech is referenced), and punctuation. Remove filler sounds ("um", "you know") while preserving the author's authentic first-person voice and every factual detail.
            2. PASSAGE & CHAPTER CREATION AUTOMATION:
               - passageTitle: Create a catchy, concise headline title for this story passage.
               - emotionalTone: Identify the primary mood/tone (e.g., "Nostalgic & Warm", "Triumphant", "Solemn", "Heartfelt").
               - assignedChapter: Determine if this story belongs to one of the existing chapters listed above, OR if it represents a brand new major era/topic. If an existing chapter fits, use that exact chapter name. If none fit well, propose a concise new chapter name.
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

        val text = try {
            textEngine.generate(prompt, jsonMode = true)
        } catch (e: Exception) {
            Log.e("Interviewer", "Book elements analysis failed: ${e.message}")
            null
        }

        if (!text.isNullOrBlank()) {
            try {
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
            } catch (e: Exception) {
                Log.e("Interviewer", "Book elements JSON parse failed: ${e.message}")
            }
        }

        fallbackAnalysis(cleanedText, entities, chapter)
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

    suspend fun followUp(memory: String, chapter: String = "Life Journey"): String = withContext(Dispatchers.IO) {
        val cleaned = CleanerAgent.clean(memory)
        val entities = EntityRegistryAgent.extractEntities(cleaned)

        val deterministicQuestion = DeterministicWriterEngine.selectNextQuestion(
            rawMemory = cleaned,
            entities = entities,
            recentQuestions = recentQuestions
        )
        if (deterministicQuestion.isNotBlank()) {
            recentQuestions.add(deterministicQuestion)
            if (recentQuestions.size > 8) recentQuestions.removeAt(0)
            return@withContext deterministicQuestion
        }

        if (!textEngine.isConfigured || !DeterministicRouter.canMakeLlmCall()) {
            return@withContext "What do you remember most clearly about the people who were there with you?"
        }

        DeterministicRouter.recordLlmCall(
            task = "followUp",
            reason = "Deterministic question bank exhausted for topic in $chapter",
            wouldBeAgent = "Agent 7 (Question Selector Bank Extension)"
        )

        val systemPrompt = "You are Mike Write, a warm, patient, deeply respectful interviewer helping a paralyzed author dictate his autobiography. " +
                "Read the author's story and ask exactly ONE short, conversational follow-up question to help him delve deeper into character perspectives, emotions, or vivid sensory details. " +
                "Keep the question under 20 words. Speak directly to him with compassion. Avoid greeting him or adding preamble."

        val text = try {
            textEngine.generate("$systemPrompt\n\nAuthor's story in $chapter:\n\"$cleaned\"\n\nOne follow-up question:")
        } catch (e: Exception) {
            Log.e("Interviewer", "Follow-up generation failed: ${e.message}")
            null
        }

        if (!text.isNullOrBlank()) {
            return@withContext text.replace("\n", " ").trim('"', ' ')
        }

        "What do you remember most clearly about the people who were there with you?"
    }

    suspend fun generateChapterPrompt(chapter: String): String = generateChapterPromptWithCraft(chapter, null)

    suspend fun generateChapterPromptWithCraft(chapter: String, craftFocus: String?): String = withContext(Dispatchers.IO) {
        val craftInstruction = if (!craftFocus.isNullOrBlank()) {
            " Focus specifically on this storytelling craft need: $craftFocus."
        } else ""

        if (!textEngine.isConfigured) {
            return@withContext fallbackChapterPrompt(chapter, craftFocus)
        }

        val text = try {
            textEngine.generate("You are an interviewer for a life memoir. Ask one engaging opening question for a book chapter titled '$chapter'.$craftInstruction Keep it under 20 words, conversational, and direct.")
        } catch (e: Exception) {
            Log.e("Interviewer", "Chapter prompt failed: ${e.message}")
            null
        }

        if (!text.isNullOrBlank()) {
            return@withContext text.replace("\n", " ").trim('"', ' ')
        }

        fallbackChapterPrompt(chapter, craftFocus)
    }

    private fun fallbackChapterPrompt(chapter: String, craftFocus: String?): String = when {
        craftFocus?.contains("sensory", ignoreCase = true) == true ->
            "What physical sights, sounds, or smells immediately come to mind when you picture $chapter?"
        craftFocus?.contains("character", ignoreCase = true) == true ->
            "Who was the most influential person beside you in $chapter, and what made them memorable?"
        craftFocus?.contains("reflection", ignoreCase = true) == true ->
            "Looking back at $chapter, how did those experiences change the way you see yourself today?"
        else -> "Tell me about a memorable moment from $chapter. What happened first?"
    }

    suspend fun synthesizeBookSummary(bookTitle: String, author: String, memoryCount: Int, excerpts: List<String>): String = withContext(Dispatchers.IO) {
        if (!textEngine.isConfigured || excerpts.isEmpty()) {
            return@withContext "$bookTitle by $author currently contains $memoryCount recorded memories across your life journey."
        }

        val joined = excerpts.take(6).joinToString("\n- ")
        val text = try {
            textEngine.generate("Write a warm, inspiring 2-sentence literary narrative summary of a memoir titled '$bookTitle' by $author based on these passages:\n- $joined\nHighlight the core narrative arc and emotional themes. Keep it under 38 words for spoken playback.")
        } catch (e: Exception) {
            Log.e("Interviewer", "Summary generation failed: ${e.message}")
            null
        }

        if (!text.isNullOrBlank()) {
            return@withContext text.replace("\n", " ").trim('"', ' ')
        }

        "$bookTitle by $author contains $memoryCount recorded memories, capturing rich life stories, heartfelt narration, and enduring wisdom."
    }

    suspend fun synthesizePublishingCritique(
        bookTitle: String,
        author: String,
        chaptersCount: Int,
        totalWords: Int,
        samplePassages: List<String>
    ): String = withContext(Dispatchers.IO) {
        val sample = samplePassages.take(5).joinToString("\n- ")
        if (!textEngine.isConfigured || sample.isBlank()) {
            return@withContext "Your manuscript '$bookTitle' shows genuine voice and heartfelt depth. Focus next on expanding sensory details in earlier chapters and tying personal triumphs to universal life lessons for readers."
        }

        val text = try {
            textEngine.generate("You are a senior acquisitions book editor reviewing a life memoir manuscript titled '$bookTitle' by $author ($totalWords words, $chaptersCount chapters). Sample excerpts:\n- $sample\nProvide a concise 2-sentence editorial assessment and publishing readiness advice. Focus on voice authenticity, narrative momentum, and readers' emotional takeaway. Keep under 45 words.")
        } catch (e: Exception) {
            Log.e("Interviewer", "Publishing critique failed: ${e.message}")
            null
        }

        if (!text.isNullOrBlank()) {
            return@withContext text.replace("\n", " ").trim('"', ' ')
        }

        "Your manuscript '$bookTitle' shows genuine voice and heartfelt depth. Focus next on expanding sensory details in earlier chapters and tying personal triumphs to universal life lessons for readers."
    }
}
