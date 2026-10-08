package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonsTest {
    private fun chord(n: String) = LessonItem.ChordItem(parseChord(n)!!)

    @Test fun parsesEveryChordNameItPrints() {
        for (r in 0..11) for (t in ChordType.entries) {
            val c = Chord(r, t)
            assertEquals(c, parseChord(c.name))
        }
        assertEquals(Chord(10, ChordType.MAJOR), parseChord("A#"))
        assertEquals(null, parseChord("H7"))
    }

    @Test fun leitnerBoxesAndDueDates() {
        var m = Mastery()
        m = m.after(true, 0); assertEquals(Mastery(1, DAY_MS, true), m)
        m = m.after(true, DAY_MS); assertEquals(Mastery(2, 3 * DAY_MS, true), m)
        m = m.after(false, 3 * DAY_MS); assertEquals(Mastery(1, 4 * DAY_MS, true), m)
        repeat(10) { m = m.after(true, 0) }
        assertEquals(5, m.box)
    }

    @Test fun dueQueueAcrossSimulatedDays() {
        val basics = LessonPack("basics", "Basics", 1, emptyList(), listOf("C", "F", "G", "Am").map(::chord))
        val sevenths = LessonPack("sevenths", "Sevenths", 3, listOf("basics"), listOf("Cmaj7", "G7").map(::chord))
        val cur = Curriculum(listOf(basics, sevenths))
        val mastery = mutableMapOf<String, Mastery>()
        var now = 0L

        // Day 0: only the unlocked pack's items, new ones capped.
        assertEquals(listOf("chord:C", "chord:F", "chord:G"), cur.queue(mastery, now, newLimit = 3).map { it.id })
        assertFalse(cur.unlocked(sevenths, mastery))
        // Practice everything right every day; F always wrong.
        repeat(12) {
            for (item in cur.queue(mastery, now, newLimit = 10)) {
                mastery[item.id] = (mastery[item.id] ?: Mastery()).after(item.id != "chord:F", now)
            }
            now += DAY_MS
        }
        // Easy chords climbed and are not due every day; F stays at box 0, due daily.
        assertTrue(mastery["chord:C"]!!.box >= 4)
        assertEquals(0, mastery["chord:F"]!!.box)
        assertTrue("chord:F" in cur.queue(mastery, now).map { it.id })
        // Average box of basics: (C,G,Am high, F 0) >= 3, so sevenths unlocked and its items appeared.
        assertTrue(cur.unlocked(sevenths, mastery))
        assertTrue(mastery.containsKey("chord:G7"))
        val counts = cur.counts(basics, mastery, now)
        assertEquals(0, counts.new)
        assertEquals(3, counts.mastered)
        assertTrue(counts.due >= 1)
    }

    @Test fun labelsTheChordsOfAProgression() {
        // ii–V–I in C plus I–V–vi–IV in G: the labeler must name each bar's chord.
        val jazz = progressionSong(PROGRESSIONS[1], 0)
        assertEquals(listOf("Dm7", "G7", "Cmaj7", "Cmaj7"), labelBars(jazz).take(4).map { it.name })
        val pop = progressionSong(PROGRESSIONS[0], 7)
        assertEquals(listOf("G", "D", "Em", "C"), labelBars(pop).take(4).map { it.name })
        assertEquals(listOf("G", "D", "Em", "C"), songPack(pop).items.map { (it as LessonItem.ChordItem).chord.name })
    }

    @Test fun chordTrackFollowsSongTimeAndShowsTheNextChange() {
        val c = Chord(0, ChordType.MAJOR); val f = Chord(5, ChordType.MAJOR); val g = Chord(7, ChordType.MAJOR)
        val t = ChordTrack(listOf(c, c, null, f, g), barMs = 1000.0)
        assertEquals(c to f, t.at(0.0))
        assertEquals("a bar without notes keeps the chord before it", c to f, t.at(2500.0))
        assertEquals(f to g, t.at(3000.0))
        assertEquals(g to null, t.at(9000.0))
        assertTrue(ChordTrack(listOf(null, null), 1000.0).isEmpty)
    }

    @Test fun alignedBarsKeepEmptyBarsInPlace() {
        val notes = parseLine("C4+E4+G4:4 r:4 F4+A4+C5:4", Hand.RIGHT, 500.0)
        val song = Song("s", "s", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 6000)
        val bars = labelBarsAligned(song)
        assertEquals(listOf("C", null, "F"), bars.take(3).map { it?.name })
        assertEquals(2000.0, barMs(song), 1e-9)
        assertEquals(listOf("C", "F"), labelBars(song).map { it.name })
    }
}
