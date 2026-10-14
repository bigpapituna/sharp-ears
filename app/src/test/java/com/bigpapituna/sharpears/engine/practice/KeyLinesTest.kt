package com.bigpapituna.sharpears.engine.practice

import com.bigpapituna.sharpears.engine.Practice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLinesTest {
    @Test fun keySignaturesOfTheMajorAndMinorKeys() {
        val major = (0..11).map { keySignature(it, ScaleType.MAJOR) }
        assertEquals(listOf(0, -5, 2, -3, 4, -1, 6, 1, -4, 3, -2, 5), major)
        assertEquals(0, keySignature(9, ScaleType.NATURAL_MINOR))          // A minor
        assertEquals(-6, keySignature(3, ScaleType.NATURAL_MINOR))         // E flat minor
        assertEquals(5, keySignature(8, ScaleType.HARMONIC_MINOR))         // G sharp minor
        assertEquals(0, keySignature(2, ScaleType.DORIAN))                 // D dorian: the white keys
        assertEquals(0, keySignature(7, ScaleType.MIXOLYDIAN))             // G mixolydian
        assertEquals(0, keySignature(0, ScaleType.CHROMATIC))
    }

    @Test fun scalesAreSpelledWithOneLetterEach() {
        assertEquals(listOf("E flat", "F", "G", "A flat", "B flat", "C", "D"), spelledScale(3, ScaleType.MAJOR))
        assertEquals(listOf("F sharp", "G sharp", "A sharp", "B", "C sharp", "D sharp", "E sharp"), spelledScale(6, ScaleType.MAJOR))
        assertEquals("G sharp", spelledScale(9, ScaleType.HARMONIC_MINOR)[6])
        assertEquals("F double sharp", spelledScale(8, ScaleType.HARMONIC_MINOR)[6])
        assertEquals(listOf("C", "D", "E", "G", "A"), spelledScale(0, ScaleType.MAJOR_PENTATONIC))
        assertEquals("E flat", spelledScale(9, ScaleType.BLUES)[3])
    }

    @Test fun theBriefsExampleLine() {
        assertEquals("E flat major has three flats. The thumb never lands on a black key here.",
            scaleLine(Scale(3, ScaleType.MAJOR), Practice.RIGHT))
    }

    @Test fun whiteKeyScalesNameTheThumbs() {
        assertEquals("C major has no sharps or flats. The right thumb plays C and F.", scaleLine(Scale(0, ScaleType.MAJOR), Practice.RIGHT))
        assertEquals("C major has no sharps or flats. The left thumb plays G and C.", scaleLine(Scale(0, ScaleType.MAJOR), Practice.LEFT))
        assertEquals("C major has no sharps or flats. Right thumb on C and F, left thumb on G and C.", scaleLine(Scale(0, ScaleType.MAJOR), Practice.BOTH))
    }

    @Test fun everyLineIsShortCleanAndInWords() {
        for (root in 0..11) for (type in ScaleType.entries) for (hands in Practice.entries) {
            val line = scaleLine(Scale(root, type), hands)
            check(line)
        }
        for (root in 0..11) for (type in ArpeggioType.entries) for (hands in Practice.entries) check(arpeggioLine(Arpeggio(root, type), hands))
    }

    private fun check(line: String) {
        assertTrue(line, line.length <= 160)
        assertFalse(line, Regex("[A-G][b#]").containsMatchIn(line))              // "E flat", never "Eb"
        assertFalse(line, line.contains(Char(0x2014)) || line.contains(Char(0x2013)))
        assertTrue(line, line.endsWith("."))
    }

    @Test fun arpeggioLines() {
        assertEquals("E flat major is E flat, G and B flat. The thumb stays on G each octave, never a black key.",
            arpeggioLine(Arpeggio(3, ArpeggioType.MAJOR), Practice.RIGHT))
        assertEquals(listOf("G", "B", "D", "F"), arpeggioTones(Arpeggio(7, ArpeggioType.DOMINANT_7)))
        assertEquals("C major is C, E and G. The right thumb plays C each octave.", arpeggioLine(Arpeggio(0, ArpeggioType.MAJOR), Practice.RIGHT))
    }

    @Test fun words() {
        assertEquals("three flats", signatureWords(-3))
        assertEquals("one sharp", signatureWords(1))
        assertEquals("C, F and G", listWords(listOf("C", "F", "G")))
        assertEquals("F sharp", spokenName("F#"))
        assertEquals("E flat major", spokenTitle("Eb", "Major"))
    }
}
