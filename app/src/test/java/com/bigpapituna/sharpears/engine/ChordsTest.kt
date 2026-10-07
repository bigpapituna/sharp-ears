package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordsTest {
    @Test fun everyTypeAndInversionAcrossTwelveRoots() {
        for (root in 0..11) for (type in ChordType.entries) for (inv in 0 until Chord(root, type).inversionCount) {
            val c = Chord(root, type)
            val v = c.voicing(inv)
            val what = "${c.name} inversion $inv: $v"
            assertEquals(what, type.intervals.size, v.size)
            assertEquals(what, v.sorted(), v)
            assertEquals(what, c.pitchClasses, v.map { it % 12 }.toSet())
            assertEquals(what, v.size, v.toSet().size)
            assertTrue(what, v.last() - v.first() < 24)
            // The lowest note of inversion k is chord tone k (root, third, fifth, ...), by pitch class.
            assertEquals(what, (root + type.intervals.sorted()[inv]) % 12, v.first() % 12)
            assertEquals(what, v.size, c.fingering(inv).size)
        }
    }

    @Test fun names() {
        assertEquals("C", Chord(0, ChordType.MAJOR).name)
        assertEquals("F#m7", Chord(6, ChordType.MIN7).name)
        assertEquals("Bbmaj7", Chord(10, ChordType.MAJ7).name)
        assertEquals(listOf(64, 67, 72), Chord(0, ChordType.MAJOR).voicing(1))
        assertEquals(listOf(67, 72, 76), Chord(0, ChordType.MAJOR).voicing(2))
    }

    @Test fun progressionsBecomeSongsWithSmoothVoiceLeading() {
        for (p in PROGRESSIONS) for (key in PROGRESSION_KEYS) {
            val s = progressionSong(p, key)
            assertEquals(s.title, p.degrees.size, s.groups.size)
            s.groups.forEachIndexed { i, g ->
                val chord = Chord((key + p.degrees[i].semitones) % 12, p.degrees[i].type)
                assertEquals("${s.title} bar $i", chord.pitchClasses, g.notes.map { it.midi % 12 }.toSet())
                assertEquals("${s.title} bar $i bass", chord.root, g.notes.single { it.hand == Hand.LEFT }.midi % 12)
            }
            // Same-size neighbours move at most a few semitones per voice on average.
            val rh = s.groups.map { g -> g.notes.filter { it.hand == Hand.RIGHT }.map { it.midi } }
            rh.zipWithNext().filter { (a, b) -> a.size == b.size }.forEach { (a, b) ->
                assertTrue("${s.title}: $a -> $b", a.zip(b).sumOf { (x, y) -> kotlin.math.abs(x - y) } <= 3 * a.size)
            }
        }
    }

    @Test fun drillAcceptsAnyOctaveAndOrderAndCountsStreaks() {
        val d = ChordDrill(listOf(Chord(0, ChordType.MAJOR), Chord(7, ChordType.MAJOR)), seed = 1)
        val first = d.current
        val notes = first.voicing(0).reversed()
        assertEquals(ChordDrill.Result.PART, d.input(notes[0] + 12))
        assertEquals(ChordDrill.Result.PART, d.input(notes[1] - 12))
        assertEquals(ChordDrill.Result.DONE, d.input(notes[2]))
        assertEquals(1, d.streak)
        assertTrue(d.current != first)
        val wrong = (0..11).first { it !in d.current.pitchClasses } + 60
        assertEquals(ChordDrill.Result.MISS, d.input(wrong))
        assertEquals(0, d.streak)
        d.current.voicing(0).forEach { d.input(it) }
        assertEquals(2, d.correct)
        assertEquals(0, d.streak)                     // a miss on that chord: correct but no streak
    }
}
