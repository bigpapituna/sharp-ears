package com.bigpapituna.sharpears.theory

import com.bigpapituna.sharpears.engine.theory.CIRCLE_MAJORS
import com.bigpapituna.sharpears.engine.theory.CIRCLE_MINORS
import com.bigpapituna.sharpears.engine.theory.CircleVisual
import com.bigpapituna.sharpears.engine.theory.Clef
import com.bigpapituna.sharpears.engine.theory.Key
import com.bigpapituna.sharpears.engine.theory.Pitch
import com.bigpapituna.sharpears.engine.theory.ScaleKind
import com.bigpapituna.sharpears.engine.theory.StaffNote
import com.bigpapituna.sharpears.engine.theory.StaffVisual
import com.bigpapituna.sharpears.engine.theory.NoteValue
import com.bigpapituna.sharpears.engine.theory.consonant
import com.bigpapituna.sharpears.engine.theory.diatonicTriads
import com.bigpapituna.sharpears.engine.theory.interval
import com.bigpapituna.sharpears.engine.theory.invert
import com.bigpapituna.sharpears.engine.theory.keyFifths
import com.bigpapituna.sharpears.engine.theory.keySignature
import com.bigpapituna.sharpears.engine.theory.keySignaturePositions
import com.bigpapituna.sharpears.engine.theory.ledgerLines
import com.bigpapituna.sharpears.engine.theory.lineNames
import com.bigpapituna.sharpears.engine.theory.p
import com.bigpapituna.sharpears.engine.theory.scale
import com.bigpapituna.sharpears.engine.theory.spaceNames
import com.bigpapituna.sharpears.engine.theory.staffPosition
import com.bigpapituna.sharpears.engine.theory.stepLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTheoryTest {
    @Test fun pitchesParseAndSpell() {
        assertEquals(60, p("C4").midi)
        assertEquals(66, p("F#4").midi)
        assertEquals(46, p("Bb2").midi)
        assertEquals(p("F#4"), Pitch.of(66))
        assertEquals(p("Gb4"), Pitch.of(66, flats = true))
        assertEquals("B flat", p("Bb3").spoken)
        assertEquals("C♯", p("C#5").label)
        assertEquals(p("E#4").midi, p("F4").midi)
    }

    @Test fun staffPositionsTreble() {
        val t = Clef.TREBLE
        assertEquals(0, staffPosition(p("E4"), t))       // bottom line
        assertEquals(2, staffPosition(p("G4"), t))       // the G line the clef curls round
        assertEquals(5, staffPosition(p("C5"), t))       // third space
        assertEquals(8, staffPosition(p("F5"), t))       // top line
        assertEquals(-2, staffPosition(p("C4"), t))      // middle C, one ledger line below
        assertEquals(10, staffPosition(p("A5"), t))      // one ledger line above
        assertEquals(staffPosition(p("C4"), t), staffPosition(p("C#4"), t))   // a sharp sits on its letter's line
        assertEquals(listOf("E", "G", "B", "D", "F"), lineNames(t))
        assertEquals(listOf("F", "A", "C", "E"), spaceNames(t))
    }

    @Test fun staffPositionsBass() {
        val b = Clef.BASS
        assertEquals(0, staffPosition(p("G2"), b))
        assertEquals(6, staffPosition(p("F3"), b))       // the F line between the dots
        assertEquals(10, staffPosition(p("C4"), b))      // middle C, one ledger line above
        assertEquals(-2, staffPosition(p("E2"), b))
        assertEquals(listOf("G", "B", "D", "F", "A"), lineNames(b))
        assertEquals(listOf("A", "C", "E", "G"), spaceNames(b))
    }

    @Test fun ledgerLinesReachTheNote() {
        assertEquals(emptyList<Int>(), ledgerLines(0))
        assertEquals(emptyList<Int>(), ledgerLines(-1))      // just below the staff: no line needed
        assertEquals(listOf(-2), ledgerLines(-2))
        assertEquals(listOf(-2), ledgerLines(-3))            // B3 hangs under middle C's line
        assertEquals(listOf(10, 12), ledgerLines(12))         // C6 in the treble
        assertEquals(listOf(10, 12), ledgerLines(13))
    }

    /** All twelve majors: (tonic, signature), positive sharps, negative flats. */
    private val majors = listOf("C" to 0, "G" to 1, "D" to 2, "A" to 3, "E" to 4, "B" to 5, "F#" to 6, "Db" to -5, "Ab" to -4, "Eb" to -3, "Bb" to -2, "F" to -1)
    private val minors = listOf("A" to 0, "E" to 1, "B" to 2, "F#" to 3, "C#" to 4, "G#" to 5, "D#" to 6, "Bb" to -5, "F" to -4, "C" to -3, "G" to -2, "D" to -1)

    @Test fun keySignaturesForAllTwelveMajors() {
        majors.forEach { (k, n) -> assertEquals("$k major", n, keyFifths(p("${k}4"))) }
        assertEquals(-6, keyFifths(p("Gb4")))
        assertEquals(7, keyFifths(p("C#4")))
        assertEquals(-7, keyFifths(p("Cb4")))
        assertEquals(listOf("F", "C"), keySignature(2).map { it.letterName })
        assertTrue(keySignature(2).all { it.accidental == 1 })
        assertEquals(listOf("B", "E", "A", "D"), keySignature(-4).map { it.letterName })
        assertTrue(keySignature(-4).all { it.accidental == -1 })
        assertEquals("FCGDAEB", keySignature(7).joinToString("") { it.letterName })
        assertEquals("BEADGCF", keySignature(-7).joinToString("") { it.letterName })
    }

    @Test fun keySignaturesForAllTwelveMinors() {
        minors.forEach { (k, n) -> assertEquals("$k minor", n, keyFifths(p("${k}4"), minor = true)) }
        assertEquals(-6, keyFifths(p("Eb4"), minor = true))
    }

    @Test fun everyKeyScaleUsesItsSignature() {
        // The notes of each major scale are altered exactly as its key signature says (and natural minor likewise).
        (majors.map { it.first to false } + minors.map { it.first to true }).forEach { (k, minor) ->
            val s = scale(p("${k}4"), if (minor) ScaleKind.NATURAL_MINOR else ScaleKind.MAJOR).dropLast(1)
            val sig = keySignature(keyFifths(p("${k}4"), minor)).associate { it.letter to it.accidental }
            s.forEach { n -> assertEquals("$k ${if (minor) "minor" else "major"}: ${n.code}", sig[n.letter] ?: 0, n.accidental) }
            assertEquals("one note per letter", 7, s.map { it.letter }.toSet().size)
        }
    }

    @Test fun signaturesZigzagOnTheStaff() {
        // Treble sharps: F5 C5 G5 D5 A4 E5 B4; their positions must be where those notes sit.
        val sharps = keySignaturePositions(7, Clef.TREBLE)
        assertEquals(listOf("F5", "C5", "G5", "D5", "A4", "E5", "B4").map { staffPosition(p(it), Clef.TREBLE) }, sharps)
        val flats = keySignaturePositions(-7, Clef.TREBLE)
        assertEquals(listOf("B4", "E5", "A4", "D5", "G4", "C5", "F4").map { staffPosition(p(it), Clef.TREBLE) }, flats)
        assertEquals(listOf("F3", "C3", "G3").map { staffPosition(p(it), Clef.BASS) }, keySignaturePositions(3, Clef.BASS))
        assertEquals(listOf("B2", "E3").map { staffPosition(p(it), Clef.BASS) }, keySignaturePositions(-2, Clef.BASS))
    }

    @Test fun circleOfFifthsOrder() {
        assertEquals(listOf("C", "G", "D", "A", "E", "B", "F♯", "D♭", "A♭", "E♭", "B♭", "F"), CIRCLE_MAJORS.map { it.tonic.label })
        // Each step clockwise is a perfect fifth up (7 half steps) and one more sharp (or one fewer flat).
        CIRCLE_MAJORS.zipWithNext().forEach { (a, b) -> assertEquals(7, Math.floorMod(b.tonic.midi - a.tonic.midi, 12)) }
        assertEquals(7, Math.floorMod(CIRCLE_MAJORS.first().tonic.midi - CIRCLE_MAJORS.last().tonic.midi, 12))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, -5, -4, -3, -2, -1), CIRCLE_MAJORS.map { it.fifths })
        assertEquals(listOf("A", "E", "B", "F♯", "C♯", "G♯", "D♯", "B♭", "F", "C", "G", "D"), CIRCLE_MINORS.map { it.tonic.label })
        CIRCLE_MAJORS.zip(CIRCLE_MINORS).forEach { (maj, min) -> assertEquals(maj.fifths, min.fifths); assertEquals(maj, min.relative) }
        assertEquals(12, CIRCLE_MAJORS.map { Math.floorMod(it.tonic.midi, 12) }.toSet().size)
    }

    @Test fun circleTurnsTheShortWay() {
        val v = CircleVisual(listOf(11, 1))
        assertEquals(0f, v.angle(0), 0.001f)
        assertEquals(-1f, v.angle(v.stopAt(0) + v.turnMs), 0.001f)       // C to F: one step back, not eleven forward
        assertEquals(1f, v.angle(v.stopAt(1) + v.turnMs), 0.001f)        // F to G: two steps forward
    }

    @Test fun scalesAreSpelledOneLetterEach() {
        assertEquals("C4 D4 E4 F4 G4 A4 B4 C5", scale(p("C4")).joinToString(" ") { it.code })
        assertEquals("F4 G4 A4 Bb4 C5 D5 E5 F5", scale(p("F4")).joinToString(" ") { it.code })
        assertEquals("A4 B4 C5 D5 E5 F5 G5 A5", scale(p("A4"), ScaleKind.NATURAL_MINOR).joinToString(" ") { it.code })
        assertEquals("A4 B4 C5 D5 E5 F5 G#5 A5", scale(p("A4"), ScaleKind.HARMONIC_MINOR).joinToString(" ") { it.code })
        assertEquals("A4 B4 C5 D5 E5 F#5 G#5 A5", scale(p("A4"), ScaleKind.MELODIC_MINOR).joinToString(" ") { it.code })
        assertEquals("C4 D4 Eb4 F4 G4 Ab4 Bb4 C5", scale(p("C4"), ScaleKind.NATURAL_MINOR).joinToString(" ") { it.code })
        assertEquals(listOf("W", "W", "H", "W", "W", "W", "H"), stepLabels(ScaleKind.MAJOR))
        ScaleKind.entries.forEach { assertEquals(12, it.steps.sum()) }
    }

    @Test fun intervalsAreNamedByLettersAndHalfSteps() {
        assertEquals("major 3rd", interval(p("C4"), p("E4")).name)
        assertEquals("minor 3rd", interval(p("C4"), p("Eb4")).name)
        assertEquals("perfect 5th", interval(p("C4"), p("G4")).name)
        assertEquals("perfect 4th", interval(p("D4"), p("G4")).name)
        assertEquals("augmented 4th", interval(p("C4"), p("F#4")).name)
        assertEquals("diminished 5th", interval(p("B3"), p("F4")).name)
        assertEquals("minor 6th", interval(p("C4"), p("Ab4")).name)
        assertEquals("major 7th", interval(p("C4"), p("B4")).name)
        assertEquals("octave", interval(p("C4"), p("C5")).name)
        assertTrue(consonant(7)); assertTrue(consonant(4)); assertFalse(consonant(1)); assertFalse(consonant(6)); assertFalse(consonant(11))
    }

    @Test fun triadsOfAMajorKey() {
        val c = diatonicTriads(p("C4"))
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), c.map { it.numeral })
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "B°"), c.map { it.symbol })
        assertEquals("D F# A", diatonicTriads(p("G4"))[4].notes.joinToString(" ") { it.code.dropLast(1) })   // V of G is D major
        assertEquals("E4 G4 C5", invert(c[0].notes, 1).joinToString(" ") { it.code })
        assertEquals("G4 C5 E5", invert(c[0].notes, 2).joinToString(" ") { it.code })
    }

    @Test fun relativeKeysShareSignatures() {
        assertEquals(Key(p("A3"), minor = true), Key(p("C4")).relative)
        assertEquals(Key(p("F4")), Key(p("D4"), minor = true).relative)
    }

    @Test fun staffNotesLandInRhythm() {
        val v = StaffVisual(Clef.TREBLE, listOf(StaffNote(listOf(p("C5")), NoteValue.HALF, tie = true), StaffNote(listOf(p("C5"))), StaffNote(listOf(p("D5")))), bpm = 60)
        assertEquals(v.arrival(0) + 2000, v.arrival(1))
        assertEquals(v.arrival(1) + 1000, v.arrival(2))
        assertEquals("a tied note is not struck again", 2, v.cues.size)
        assertTrue("the tie holds for both values", v.cues[0].holdMs > 2500)
    }
}
