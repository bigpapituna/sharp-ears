package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.nav.Layout
import com.bigpapituna.sharpears.engine.theory.Clef
import com.bigpapituna.sharpears.engine.theory.p
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.14 depth: the staff sight-reading ladder (levels 3 to 8), its cards, the drill's score and who opens it. */
class SightStaffTest {
    @Test fun eightLevelsTheFirstTwoArePieces() {
        assertEquals((1..8).toList(), SightLevel.entries.map { it.n })
        assertEquals(listOf(1, 2), SightLevel.entries.filter { !it.staff }.map { it.n })
        assertEquals(SightLevel.RHYTHM, SightLevel.of(99))
        SightLevel.entries.forEach { l -> assertFalse(l.line, l.line.contains('—') || l.line.contains('–')) }
    }

    @Test fun eachLevelDrawsFromItsRangeAndStaff() {
        fun range(level: Int) = SightStaff.cards(level, 7, 400).flatMap { it.notes }.map { it.pitches.first() to it.staff }
        range(3).forEach { (pt, clef) -> assertTrue(pt.code, pt.midi in p("C4").midi..p("G4").midi && clef == Clef.TREBLE && pt.accidental == 0) }
        range(4).forEach { (pt, clef) -> assertTrue(pt.code, pt.midi in p("C4").midi..p("A5").midi && clef == Clef.TREBLE) }
        range(5).forEach { (pt, clef) -> assertTrue(pt.code, pt.midi in p("E2").midi..p("C4").midi && clef == Clef.BASS) }
        val grand = range(6)
        assertTrue(grand.any { it.second == Clef.BASS } && grand.any { it.second == Clef.TREBLE })
        grand.forEach { (pt, clef) -> assertTrue(pt.code, if (clef == Clef.TREBLE) pt.midi >= 60 else pt.midi <= 60) }
        // Every note of levels 3 to 6 is reached in 400 cards.
        assertEquals(SightStaff.TREBLE.size, range(4).map { it.first }.distinct().size)
    }

    @Test fun sharpsAndFlatsAreSpelledTheWayMusicIsWritten() {
        val notes = SightStaff.cards(7, 3, 600).map { it.notes.single().pitches.single() }
        val sharps = notes.filter { it.accidental == 1 }; val flats = notes.filter { it.accidental == -1 }
        assertTrue("${sharps.size} sharps, ${flats.size} flats", sharps.size > 50 && flats.size > 50)
        assertTrue(sharps.none { it.letterName in setOf("E", "B") })
        assertTrue(flats.none { it.letterName in setOf("F", "C") })
        assertTrue(notes.any { isBlack(it.midi) })
    }

    @Test fun neverTheSameKeyTwiceInARowAndSeeded() {
        for (level in 3..7) SightStaff.cards(level, 11, 300).zipWithNext().forEach { (a, b) -> assertNotEquals(a.keys, b.keys) }
        assertEquals(SightStaff.cards(4, 42, 30).map { it.code }, SightStaff.cards(4, 42, 30).map { it.code })
        assertNotEquals(SightStaff.cards(4, 42, 30).map { it.code }, SightStaff.cards(4, 43, 30).map { it.code })
    }

    @Test fun rhythmBarsFillFourBeatsAndGradeLikePerform() {
        val cards = SightStaff.cards(8, 5, SightStaff.RHYTHM_BARS)
        cards.forEach { c -> assertEquals(c.code, 4.0, c.notes.sumOf { it.beats.toDouble() }, 1e-9) }
        cards.zipWithNext().forEach { (a, b) -> assertNotEquals(a.notes.map { it.beats to it.rest }, b.notes.map { it.beats to it.rest }) }
        assertTrue(cards.all { c -> c.keys.all { it in 60..67 } })
        SightStaff.RHYTHM.forEach { assertEquals(4.0, it.sumOf { b -> kotlin.math.abs(b) }, 1e-9) }
        // The timeline: bar 2's first note starts one 72 bpm bar (3333 ms) in.
        val groups = SightStaff.rhythmGroups(cards.take(2))
        assertEquals(cards[0].keys.size + cards[1].keys.size, groups.size)
        assertEquals(3333L, groups[cards[0].keys.size].startMs)
        val score = PerformScore(groups, { true })
        groups.forEach { g -> score.input(g.notes.first().midi, g.startMs + 40.0) }
        score.advance(1e9)
        assertEquals(groups.size, score.recap().good)
        assertTrue(SightStaff.RHYTHM_BARS * SightStaff.rhythmBarMs >= SightStaff.DRILL_MS)
    }

    @Test fun theDrillScore() {
        val s = DrillScore(24, 2)
        assertEquals(s, DrillScore.decode(s.encode()))
        assertNull(DrillScore.decode("x"))
        assertTrue(s.beats(null)); assertTrue(s.beats(DrillScore(23, 0))); assertTrue(s.beats(DrillScore(24, 3)))
        assertFalse(s.beats(DrillScore(24, 2))); assertFalse(s.beats(DrillScore(25, 9)))
        assertEquals("24 right, 2 wrong", s.label)
        assertEquals("9 right, none wrong", DrillScore(9, 0).label)
    }

    @Test fun whoOpensTheStaffLevels() {
        // Grown-ups: Advanced and Full open them, and Some or A lot do in any layout.
        assertTrue(SightLevels.staffOpen(false, true, Level.NEVER, Layout.ADVANCED))
        assertTrue(SightLevels.staffOpen(false, true, Level.A_LITTLE, Layout.FULL))
        assertFalse(SightLevels.staffOpen(false, true, Level.A_LITTLE, Layout.SIMPLE))
        assertTrue(SightLevels.staffOpen(false, true, Level.SOME, Layout.SIMPLE))
        // Kids: a reader who has had lessons.
        assertTrue(SightLevels.staffOpen(true, true, Level.SOME, Layout.SIMPLE))
        assertTrue(SightLevels.staffOpen(true, true, Level.A_LOT, Layout.SIMPLE))
        assertFalse(SightLevels.staffOpen(true, true, Level.A_LITTLE, Layout.FULL))
        assertFalse(SightLevels.staffOpen(true, false, Level.SOME, Layout.FULL))
        // 2.14 merge, a big kid (8 to 13): the grown-ups' rule, a layout past Simple or a parent's "Lots" (Some).
        assertTrue(SightLevels.staffOpen(true, true, Level.SOME, Layout.SIMPLE, bigKid = true))
        assertTrue(SightLevels.staffOpen(true, true, Level.NEVER, Layout.FULL, bigKid = true))
        assertTrue(SightLevels.staffOpen(true, false, Level.SOME, Layout.SIMPLE, bigKid = true))
        assertFalse(SightLevels.staffOpen(true, true, Level.A_LITTLE, Layout.SIMPLE, bigKid = true))
        assertEquals(8, SightLevels.open(true, true, Level.A_LITTLE, Layout.ADVANCED, bigKid = true).size)
        assertEquals(listOf(1, 2), SightLevels.open(true, true, Level.NEVER, Layout.FULL).map { it.n })
        assertEquals(8, SightLevels.open(false, true, Level.A_LOT, Layout.ADVANCED).size)
    }
}
