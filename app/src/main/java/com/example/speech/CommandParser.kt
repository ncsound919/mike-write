package com.example.speech

enum class Command {
    RECORD,
    DONE,
    STOP,
    NEXT,
    BACK,
    REVIEW,
    HELP,
    UNDO,
    SAVE,
    DELETE,
    YES,
    NO,
    REPEAT,
    SLOWER,
    FASTER,
    PROMPT,
    CHAPTER,
    BOOK,
    DECONSTRUCT, // Command to hear literary layers and craft elements breakdown
    TIP,         // Command to hear writing craft coaching tip
    READINESS,   // Command to hear publishing readiness and chapter completion metrics
    PLAYBACK,    // Command to hear active live draft playback
    EXPORT,      // Command to export manuscript as PDF or Text
    AUTO_SEQUENCE, // Autonomous Manuscript Weaver: chronological timeline sequencing
    FIND_GAPS,     // Autonomous Expansion Engine: find literary gaps and expansion prompts
    HARMONIZE,    // Autonomous Style Harmonizer: voice consistency and style health
    UNKNOWN
}

object CommandParser {
    private val map: Map<Command, List<String>> = mapOf(
        Command.AUTO_SEQUENCE to listOf("auto sequence", "sequence book", "chronology", "order memories", "order scenes", "timeline", "sort memories"),
        Command.FIND_GAPS to listOf("find gaps", "story gaps", "expansion prompts", "missing stories", "narrative gaps", "book gaps", "expand book"),
        Command.HARMONIZE to listOf("harmonize voice", "harmonize style", "style check", "voice consistency", "polish book", "harmonize manuscript", "harmonize"),
        Command.RECORD to listOf("record", "start recording", "new memory", "tell a story", "start", "write", "dictate", "speak"),
        Command.DONE to listOf("done", "finished", "that's it", "stop recording", "complete", "finish", "all done"),
        Command.STOP to listOf("stop", "cancel", "quiet", "silence", "pause", "shut up", "hold on"),
        Command.PLAYBACK to listOf("playback draft", "playback", "play back", "listen back", "read back", "read draft", "play draft", "hear draft", "hear what i wrote"),
        Command.EXPORT to listOf("export manuscript", "export book", "export pdf", "download pdf", "download book", "save file", "export"),
        Command.NEXT to listOf("next memory", "next", "continue", "forward", "skip"),
        Command.BACK to listOf("go back", "last memory", "back", "previous"),
        Command.REVIEW to listOf("read whole book", "read book", "review", "read", "hear"),
        Command.HELP to listOf("help", "what can i say", "options", "commands", "how does this work"),
        Command.UNDO to listOf("undo", "scratch that", "go back a step", "erase that"),
        Command.SAVE to listOf("save memory", "save draft", "keep this", "save", "keep", "approve"),
        Command.DELETE to listOf("delete", "throw away", "discard", "trash it"),
        Command.YES to listOf("yes", "yeah", "yep", "correct", "sure", "sounds good", "okay"),
        Command.NO to listOf("no", "nope", "wrong", "cancel that", "negative"),
        Command.REPEAT to listOf("repeat", "say again", "again", "what did you say"),
        Command.SLOWER to listOf("slower", "slow down", "speak slower"),
        Command.FASTER to listOf("faster", "speed up", "speak faster"),
        Command.PROMPT to listOf("prompt me", "ask me a question", "interview me", "question", "interview"),
        Command.CHAPTER to listOf("chapter", "next chapter", "change chapter", "new chapter"),
        Command.BOOK to listOf("read whole book", "read memoir", "entire book", "summary"),
        Command.DECONSTRUCT to listOf("breakdown", "deconstruct", "story elements", "perspectives", "analyze", "explain story", "layers"),
        Command.TIP to listOf("writing tip", "tip", "craft tip", "advice", "coach me", "writing advice"),
        Command.READINESS to listOf("readiness", "publishing readiness", "book status", "progress", "word count", "pages", "how ready is my book", "manuscript status")
    )

    fun parse(utterance: String): Command {
        val t = utterance.lowercase().trim()
        if (t.isBlank()) return Command.UNKNOWN

        return map.entries
            .firstOrNull { (_, words) ->
                words.any { word ->
                    t == word ||
                    t.startsWith("$word ") ||
                    t.endsWith(" $word") ||
                    t.contains(" $word ")
                }
            }?.key ?: Command.UNKNOWN
    }
}
