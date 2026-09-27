package com.example.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserSafetyTest {

    @Test
    fun embeddedCommandWordsInStorySpeechDoNotTriggerCommands() {
        // Regression: these previously parsed as NO / YES / BACK / SAVE and could
        // delete or prematurely save a pending memoir passage.
        assertEquals(Command.UNKNOWN, CommandParser.parse("I have no idea what happened next"))
        assertEquals(Command.UNKNOWN, CommandParser.parse("I wasn't sure about it at the time"))
        assertEquals(Command.UNKNOWN, CommandParser.parse("we can go back to that day"))
        assertEquals(Command.UNKNOWN, CommandParser.parse("she said yes to him"))
        assertEquals(Command.UNKNOWN, CommandParser.parse("I will keep this memory forever"))
        assertEquals(Command.UNKNOWN, CommandParser.parse("the same old record player"))
    }

    @Test
    fun explicitSoloCommandsStillWork() {
        assertEquals(Command.NO, CommandParser.parse("no"))
        assertEquals(Command.YES, CommandParser.parse("yes"))
        assertEquals(Command.DELETE, CommandParser.parse("delete"))
        assertEquals(Command.SAVE, CommandParser.parse("save"))
        assertEquals(Command.RECORD, CommandParser.parse("record"))
        assertEquals(Command.DONE, CommandParser.parse("done"))
        // Speech-to-text often adds terminal punctuation.
        assertEquals(Command.RECORD, CommandParser.parse("Record."))
        assertEquals(Command.DONE, CommandParser.parse("Done!"))
        assertEquals(Command.PROMPT, CommandParser.parse("Prompt me?"))
    }

    @Test
    fun multiWordPhrasesStillMatchAsPrefixOrSuffix() {
        assertEquals(Command.PROMPT, CommandParser.parse("prompt me"))
        assertEquals(Command.AUTO_SEQUENCE, CommandParser.parse("auto sequence"))
        assertEquals(Command.FIND_GAPS, CommandParser.parse("find gaps"))
        assertEquals(Command.EXPORT, CommandParser.parse("export manuscript"))
    }

    @Test
    fun rewordCommandsParse() {
        assertEquals(Command.REWORD, CommandParser.parse("reword"))
        assertEquals(Command.REWORD, CommandParser.parse("rewrite this"))
        assertEquals(Command.REWORD, CommandParser.parse("rephrase"))
        assertEquals(Command.REWORD, CommandParser.parse("polish this passage"))
        assertEquals(Command.REWORD, CommandParser.parse("make it better"))
        // A plain story sentence mentioning rewriting is not a command.
        assertEquals(Command.UNKNOWN, CommandParser.parse("I had to rewrite the letter twice"))
    }
}
