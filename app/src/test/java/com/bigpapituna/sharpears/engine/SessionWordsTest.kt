package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Practice today" between parts: chip labels, the quoted line (same words as the voice), the about line. */
class SessionWordsTest {
    private val lesson = SessionPart.Lesson("l1", "Sit, then shape your hand")
    private val loop = SessionPart.Loop("fur-elise", "Für Elise", 3)
    private val song = SessionPart.Song("twinkle", "Twinkle Twinkle Little Star", 0.5, 4.0)
    private val chords = SessionPart.Chords(listOf("a", "b", "c", "d", "e"))

    @Test fun titlesAreQuoted() {
        assertEquals("Next: the lesson “Sit, then shape your hand”", SessionWords.quoted(lesson))
        assertEquals("Next: bar 3 of “Für Elise”", SessionWords.quoted(loop))
        assertEquals("Next: “Twinkle Twinkle Little Star”", SessionWords.quoted(song))
        assertEquals("Next: chords", SessionWords.quoted(chords))
    }

    @Test fun quotesFromTheBareLineMatchTheParts() {
        listOf(lesson, loop, song, chords).forEach { assertEquals(SessionWords.quoted(it), SessionWords.quoted(it.next)) }
    }

    @Test fun quotesAddNoWords() {
        // The voice reads part.next; the screen shows the same words with quotes around the title.
        listOf(lesson, loop, song, chords).forEach { assertEquals(it.next, SessionWords.quoted(it).replace("“", "").replace("”", "")) }
    }

    @Test fun chipsAndAbout() {
        assertEquals("5 chords", SessionWords.chip(chords))
        assertEquals("Bar 3", SessionWords.chip(loop))
        assertEquals("Lesson", SessionWords.chip(lesson))
        assertEquals("The next lesson on your path. About 5 min.", SessionWords.about(lesson))
        assertEquals("Chords from your daily review, one at a time. About 4 min.", SessionWords.about(chords))
    }
}
