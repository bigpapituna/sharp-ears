package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.practice.Arpeggio
import com.bigpapituna.sharpears.engine.practice.ArpeggioType
import com.bigpapituna.sharpears.engine.practice.Scale
import com.bigpapituna.sharpears.engine.practice.ScaleType
import com.bigpapituna.sharpears.engine.practice.arpeggioTones
import com.bigpapituna.sharpears.engine.practice.fiveFinger
import com.bigpapituna.sharpears.engine.practice.keySignature
import com.bigpapituna.sharpears.engine.practice.spelledScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.2.1 QA: a note is written the way the pup names it. Chord games, the chord dictionary and the progression
 * pictures spelled every black key by its pitch class (E major as E Ab B, C minor as C D# G, C7 as C E G A#); the
 * player wrote a scale's or arpeggio's black keys by one flats switch (D harmonic minor's C# as Db, F#7 as Gb Bb Db E)
 * while the pup said "C sharp"; D flat mixolydian had "six sharps"; the E flat minor warm-up was written in sharps.
 */
class SpellingTest {
    private fun tones(c: Chord) = c.voicing().map { c.toneName(it) }

    @Test fun chordTonesAreSpelledByLetterFromTheRoot() {
        assertEquals(listOf("E", "G#", "B"), tones(Chord(4, ChordType.MAJOR)))
        assertEquals(listOf("B", "D#", "F#"), tones(Chord(11, ChordType.MAJOR)))
        assertEquals(listOf("C", "Eb", "G"), tones(Chord(0, ChordType.MINOR)))
        assertEquals(listOf("C#", "E", "G#"), tones(Chord(1, ChordType.MINOR)))
        assertEquals(listOf("Eb", "Gb", "Bb"), tones(Chord(3, ChordType.MINOR)))
        assertEquals(listOf("C", "Eb", "Gb"), tones(Chord(0, ChordType.DIM)))
        assertEquals(listOf("C", "E", "G#"), tones(Chord(0, ChordType.AUG)))
        assertEquals(listOf("E", "G#", "B", "D"), tones(Chord(4, ChordType.DOM7)))
        assertEquals(listOf("Bb", "D", "F", "Ab"), tones(Chord(10, ChordType.DOM7)))
        assertEquals("a double flat comes back plain", listOf("C", "Eb", "Gb", "A"), tones(Chord(0, ChordType.DIM7)))
        // The dictionary's words for that top note: a seventh in a dim7, a sixth in a 6 chord.
        assertEquals("a diminished seventh", com.bigpapituna.sharpears.ui.intervalWord(9, ChordType.DIM7))
        assertEquals("a sixth", com.bigpapituna.sharpears.ui.intervalWord(9, ChordType.SIX))
        // Every chord: the spelling sounds the note it names, and the letters climb a third (or a step) at a time.
        for (root in 0..11) for (type in ChordType.entries) {
            val c = Chord(root, type)
            for (inv in 0 until c.inversionCount) for (m in c.voicing(inv)) assertEquals("$c $m", m, c.spell(m).midi)
            val letters = c.voicing().map { c.spell(it).letter }
            // Where a double sharp or flat would be needed (C# aug's G##, Eb dim's Bbb) the plain key stands in.
            val doubles = setOf("C#aug", "F#aug", "Baug", "Ebdim", "Abdim")
            if (type.intervals.size == 3 && type != ChordType.SUS2 && type != ChordType.SUS4 && c.name !in doubles)
                assertEquals("${c.name}: $letters", listOf(0, 2, 4).map { (letters[0] + it) % 7 }, letters)
        }
    }

    @Test fun theBuildGameNamesTheThirdItAsks() {
        val e = Chord(4, ChordType.MAJOR)
        val g = (0..2).flatMap { level -> (1L..300L).map { level to it } }.firstNotNullOf { (level, seed) ->
            ChordGame(GameKind.BUILD, level, seed = seed).also { g -> while (!g.finished && g.target != e) g.skip() }.takeIf { !it.finished }
        }
        g.input(61)
        val hint = g.input(61).line!!
        assertTrue(hint.text, hint.text.endsWith("The third is G#."))
        assertTrue(hint.spoken, hint.spoken.endsWith("The third is G sharp."))
        assertEquals("G#, the third.", g.input(68).line!!.text)
        assertEquals("E major: E, G#, B.", g.input(71).line!!.text)
    }

    @Test fun progressionPicturesSpellEachChordFromItsRoot() {
        for (p in PROGRESSIONS) for (key in PROGRESSION_KEYS) for (step in progressionVisual(p, key).chords) {
            val letters = step.notes.map { it.letter }.toSet()
            assertEquals("${p.name} in $key, ${step.name}: ${step.notes.map { it.label }}", step.notes.size, letters.size)
        }
        val blues = progressionVisual(PROGRESSIONS.first { it.name == "12-bar blues" }, 0)
        assertTrue(blues.chords.first().notes.map { it.label }.toString(), "B♭" in blues.chords.first().notes.map { it.label })
    }

    /** What the player writes on a note: a scale's own spelling where it differs from the flats switch. */
    private fun shown(song: Song, midi: Int) = song.spellings[midi % 12] ?: noteName(midi, song.preferFlats).dropLastWhile { it.isDigit() }

    @Test fun thePlayerWritesAScaleAndAnArpeggioAsThePupSaysThem() {
        for (root in 0..11) {
            for (type in ScaleType.entries) {
                val s = Scale(root, type)
                val song = s.toExercise(Practice.RIGHT, 80.0).toSong("t", "t")
                val said = type.steps.zip(spelledScale(root, type)).associate { (st, n) -> (root + st) % 12 to n }
                for (n in song.notes) {
                    val name = said[n.midi % 12] ?: continue
                    val written = name.replace(" sharp", "#").replace(" flat", "b")
                    if (isBlack(n.midi) && written.length == 2) assertEquals("${s.rootName} ${type.label}", written, shown(song, n.midi))
                }
            }
            for (type in ArpeggioType.entries) {
                val a = Arpeggio(root, type)
                val song = a.toExercise(Practice.RIGHT, 80.0).toSong("t", "t")
                val said = type.intervals.zip(arpeggioTones(a)).associate { (st, n) -> (root + st) % 12 to n.replace(" sharp", "#").replace(" flat", "b") }
                for (n in song.notes) said[n.midi % 12]?.takeIf { isBlack(n.midi) && it.length == 2 }
                    ?.let { assertEquals(a.name, it, shown(song, n.midi)) }
            }
        }
        val dHarmonic = Scale(2, ScaleType.HARMONIC_MINOR).toExercise(Practice.RIGHT, 80.0).toSong("t", "t")
        assertEquals("C#", shown(dHarmonic, 61))
        assertEquals("Bb", shown(dHarmonic, 70))
    }

    @Test fun keySignaturesAndWarmUpsFollowTheKeysName() {
        assertEquals("D flat mixolydian: G flat major's six flats", -6, keySignature(1, ScaleType.MIXOLYDIAN))
        assertFalse("F sharp 7 is written in sharps", Arpeggio(6, ArpeggioType.DOMINANT_7).flats)
        assertFalse("G sharp diminished 7 too", Arpeggio(8, ArpeggioType.DIMINISHED_7).flats)
        val ebMinor = fiveFinger(3, minor = true).exercise.right
        assertTrue(ebMinor, "Gb4" in ebMinor && "F#" !in ebMinor)
    }
}
