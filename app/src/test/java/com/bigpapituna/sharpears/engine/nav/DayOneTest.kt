package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.SessionPart
import com.bigpapituna.sharpears.engine.SessionSong
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.planSession
import com.bigpapituna.sharpears.engine.progress.PlacementLevel
import com.bigpapituna.sharpears.engine.progress.Progression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.14, day one follows placement: Dana (never) gets her first lesson before any chord, and plain chord words; Sam (a lot)
 * gets no "Find a C", no microphone banner for MIDI, no Twinkle as an easy first song, and a level that is not "Newcomer".
 */
class DayOneTest {
    private val chords = List(5) { "chord:C$it" }
    private val lesson = "basics-1" to "Sit, then shape your hand"

    @Test fun aBeginnersPlanOpensOnTheLessonAndTheChordsWaitForTwoLessons() {
        for (lv in listOf(Level.NEVER, Level.A_LITTLE)) {
            assertTrue(DayOne.lessonFirst(lv))
            assertEquals(emptyList<String>(), DayOne.chordsFor(lv, lessonsPlayed = 0, chords))
            assertEquals(emptyList<String>(), DayOne.chordsFor(lv, lessonsPlayed = 1, chords))
            assertEquals(chords, DayOne.chordsFor(lv, lessonsPlayed = DayOne.CHORDS_AFTER_LESSONS, chords))
            // Day one: the lesson, then the song; no chord part at all.
            val day1 = planSession(DayOne.chordsFor(lv, 0, chords), lesson, null, lessonFirst = DayOne.lessonFirst(lv))
            assertEquals(listOf(SessionPart.Lesson(lesson.first, lesson.second)), day1.parts)
            // Two lessons later: the lesson first, then the chords.
            val later = planSession(DayOne.chordsFor(lv, 2, chords), lesson, SessionSong("twinkle", "Twinkle", 1.0, null), lessonFirst = true)
            assertEquals(listOf(SessionPart.Lesson::class, SessionPart.Chords::class, SessionPart.Song::class), later.parts.map { it::class })
        }
        for (lv in listOf(Level.SOME, Level.A_LOT)) {
            assertFalse(DayOne.lessonFirst(lv))
            assertEquals(chords, DayOne.chordsFor(lv, 0, chords))
            val plan = planSession(chords, lesson, null, lessonFirst = DayOne.lessonFirst(lv))
            assertEquals(listOf(SessionPart.Chords::class, SessionPart.Lesson::class), plan.parts.map { it::class })
        }
        // The old order is the default.
        assertEquals(listOf(SessionPart.Chords::class, SessionPart.Lesson::class), planSession(chords, lesson, null).parts.map { it::class })
    }

    @Test fun easySongsMicAndTheCheckFollowTheAnswer() {
        assertEquals(listOf(true, true, false, false), Level.entries.map(DayOne::easySongDefault))
        assertEquals(listOf(true, true, false, false), Level.entries.map(DayOne::plainChords))
        // The banner: only for someone who chose the microphone, until it is allowed or put away.
        assertTrue(DayOne.micBanner(useMic = true, micAllowed = false, closed = false))
        assertFalse("MIDI or the screen's keys: no banner", DayOne.micBanner(useMic = false, micAllowed = false, closed = false))
        assertFalse(DayOne.micBanner(useMic = true, micAllowed = true, closed = false))
        assertFalse(DayOne.micBanner(useMic = true, micAllowed = false, closed = true))
        // The check asks for the microphone unless the screen's keys or MIDI were chosen; a kid's always listens.
        assertTrue(DayOne.checkAsksMic(useMic = true, kid = false))
        assertFalse(DayOne.checkAsksMic(useMic = false, kid = false))
        assertTrue(DayOne.checkAsksMic(useMic = false, kid = true))
    }

    @Test fun chordsInPlainWords() {
        assertEquals("Play a C chord", ChordWords.ask(Chord(0, ChordType.MAJOR)))
        assertEquals("Play an A minor chord", ChordWords.ask(Chord(9, ChordType.MINOR)))
        assertEquals("Play an E minor chord", ChordWords.ask(Chord(4, ChordType.MINOR)))
        assertEquals("Play an F chord", ChordWords.ask(Chord(5, ChordType.MAJOR)))
        assertEquals("Play a G chord", ChordWords.ask(Chord(7, ChordType.MAJOR)))
        assertEquals("Play a D minor chord", ChordWords.ask(Chord(2, ChordType.MINOR)))
        assertEquals("Play a B flat chord", ChordWords.ask(Chord(10, ChordType.MAJOR)))
        assertEquals("Play an E flat chord", ChordWords.ask(Chord(3, ChordType.MAJOR)))
        assertEquals("C, E and G are lit. Any octave counts.", ChordWords.keysLine(Chord(0, ChordType.MAJOR)))
        assertEquals("D, F sharp and A are lit. Any octave counts.", ChordWords.keysLine(Chord(2, ChordType.MAJOR)))
        assertEquals("B flat, D and F are lit. Any octave counts.", ChordWords.keysLine(Chord(10, ChordType.MAJOR)))
        for (c in (0..11).flatMap { listOf(Chord(it, ChordType.MAJOR), Chord(it, ChordType.MINOR)) }) {
            val words = ChordWords.ask(c) + " " + ChordWords.keysLine(c)
            assertFalse("no octave numbers or 'any order': $words", Regex("\\d|any order|#").containsMatchIn(words))
        }
    }

    /** The placement's level seed: a floor of XP, never lowered; "a lot" is not a Newcomer. */
    @Test fun theLevelSeedFromPlacement() {
        assertEquals(listOf(1, 3, 6, 10), Level.entries.map(PlacementLevel::levelFor))
        assertEquals(listOf(0, 283, 1118, 2700), Level.entries.map(PlacementLevel::xpFor))
        for (lv in Level.entries) assertEquals(PlacementLevel.levelFor(lv), Progression.levelFor(PlacementLevel.xpFor(lv)))
        assertEquals("Newcomer", Progression.title(Progression.levelFor(PlacementLevel.xpFor(Level.NEVER))))
        assertNotEquals("Newcomer", Progression.title(Progression.levelFor(PlacementLevel.xpFor(Level.A_LOT))))
        assertEquals("Steady Hands", Progression.title(PlacementLevel.levelFor(Level.A_LOT)))
        // A floor: more XP than the seed is kept, less is raised.
        assertEquals(2700, PlacementLevel.seeded(40, Level.A_LOT))
        assertEquals(5000, PlacementLevel.seeded(5000, Level.A_LOT))
        assertEquals(0, PlacementLevel.seeded(0, Level.NEVER))
    }
}
