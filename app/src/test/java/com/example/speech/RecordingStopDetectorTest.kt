package com.example.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStopDetectorTest {

    @Test
    fun bareCommandsOnlyCountWhenSpokenAlone() {
        assertTrue(RecordingStopDetector.isStopCommand("done"))
        assertTrue(RecordingStopDetector.isStopCommand("Done."))
        assertTrue(RecordingStopDetector.isStopCommand("stop"))
        assertTrue(RecordingStopDetector.isStopCommand("finished"))
    }

    @Test
    fun explicitPhrasesMatchWholeOrTrailingShortSegments() {
        assertTrue(RecordingStopDetector.isStopCommand("that's it"))
        assertTrue(RecordingStopDetector.isStopCommand("all done"))
        assertTrue(RecordingStopDetector.isStopCommand("stop recording"))
        assertTrue(RecordingStopDetector.isStopCommand("okay that's it"))
        assertTrue(RecordingStopDetector.isStopCommand("I am done"))
    }

    @Test
    fun ordinaryStorySentencesAreNotStopCommands() {
        // Regression: these previously ended dictation AND deleted a word from the story.
        assertFalse(RecordingStopDetector.isStopCommand("I was done with work by noon"))
        assertFalse(RecordingStopDetector.isStopCommand("we finished the meal together"))
        assertFalse(RecordingStopDetector.isStopCommand("we stopped at the store"))
        assertFalse(RecordingStopDetector.isStopCommand("the story is complete"))
        assertFalse(RecordingStopDetector.isStopCommand("please stop"))
        assertFalse(RecordingStopDetector.isStopCommand(""))
    }

    @Test
    fun stripTrailingStopPhraseRemovesOnlyARealTrailingCommand() {
        assertEquals("", RecordingStopDetector.stripTrailingStopPhrase("that's it"))
        assertEquals("and", RecordingStopDetector.stripTrailingStopPhrase("and that's all"))
        assertEquals("", RecordingStopDetector.stripTrailingStopPhrase("done"))
        // Story text is left fully intact.
        assertEquals(
            "I was done with work",
            RecordingStopDetector.stripTrailingStopPhrase("I was done with work")
        )
        assertEquals(
            "we finished the meal",
            RecordingStopDetector.stripTrailingStopPhrase("we finished the meal")
        )
    }

    @Test
    fun stripExplicitTrailingPhraseNeverRemovesBareWords() {
        assertEquals("and", RecordingStopDetector.stripExplicitTrailingPhrase("and that's it"))
        // A bare "stop" mid-narration must survive partial-text integration.
        assertEquals("we stopped", RecordingStopDetector.stripExplicitTrailingPhrase("we stopped"))
        assertEquals("and then stop", RecordingStopDetector.stripExplicitTrailingPhrase("and then stop"))
    }
}
