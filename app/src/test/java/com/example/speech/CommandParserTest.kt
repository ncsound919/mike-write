package com.example.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {

    @Test
    fun parseCoreCommands() {
        assertEquals(Command.RECORD, CommandParser.parse("record"))
        assertEquals(Command.RECORD, CommandParser.parse("start recording"))
        assertEquals(Command.DONE, CommandParser.parse("done"))
        assertEquals(Command.DONE, CommandParser.parse("finished"))
        assertEquals(Command.SAVE, CommandParser.parse("save"))
        assertEquals(Command.DELETE, CommandParser.parse("delete"))
        assertEquals(Command.HELP, CommandParser.parse("help"))
        assertEquals(Command.REVIEW, CommandParser.parse("review"))
        assertEquals(Command.PROMPT, CommandParser.parse("prompt me"))
        assertEquals(Command.CHAPTER, CommandParser.parse("chapter"))
        assertEquals(Command.SLOWER, CommandParser.parse("slower"))
        assertEquals(Command.FASTER, CommandParser.parse("faster"))
        assertEquals(Command.STOP, CommandParser.parse("stop"))
        assertEquals(Command.UNDO, CommandParser.parse("undo"))
        assertEquals(Command.YES, CommandParser.parse("yes"))
        assertEquals(Command.NO, CommandParser.parse("no"))
        assertEquals(Command.READINESS, CommandParser.parse("readiness"))
        assertEquals(Command.READINESS, CommandParser.parse("how ready is my book"))
        assertEquals(Command.PLAYBACK, CommandParser.parse("playback"))
        assertEquals(Command.PLAYBACK, CommandParser.parse("hear draft"))
        assertEquals(Command.PLAYBACK, CommandParser.parse("read back"))
        assertEquals(Command.EXPORT, CommandParser.parse("export"))
        assertEquals(Command.EXPORT, CommandParser.parse("export book"))
        assertEquals(Command.EXPORT, CommandParser.parse("export pdf"))
        assertEquals(Command.EXPORT, CommandParser.parse("download pdf"))
        assertEquals(Command.EXPORT, CommandParser.parse("export manuscript"))
        assertEquals(Command.AUTO_SEQUENCE, CommandParser.parse("auto sequence"))
        assertEquals(Command.AUTO_SEQUENCE, CommandParser.parse("sequence book"))
        assertEquals(Command.AUTO_SEQUENCE, CommandParser.parse("timeline"))
        assertEquals(Command.FIND_GAPS, CommandParser.parse("find gaps"))
        assertEquals(Command.FIND_GAPS, CommandParser.parse("story gaps"))
        assertEquals(Command.FIND_GAPS, CommandParser.parse("expansion prompts"))
        assertEquals(Command.HARMONIZE, CommandParser.parse("harmonize voice"))
        assertEquals(Command.HARMONIZE, CommandParser.parse("style check"))
        assertEquals(Command.HARMONIZE, CommandParser.parse("polish book"))
    }
}
