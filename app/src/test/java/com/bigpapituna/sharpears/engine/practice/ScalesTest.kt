package com.bigpapituna.sharpears.engine.practice

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.parseLine
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalesTest {
    private val standardTypes = listOf(ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.HARMONIC_MINOR, ScaleType.MELODIC_MINOR)

    @Test fun everyScaleHasTheRightNotesAndPattern() {
        for (type in ScaleType.entries) for (root in 0..11) for (oct in 1..3) for (hand in Hand.entries) {
            val s = Scale(root, type, oct)
            val up = s.ascending(hand)
            val what = "${s.name} $hand $oct oct"
            assertEquals(what, type.steps.size * oct + 1, up.size)
            assertEquals(what, up, up.sorted())
            assertEquals(what, up.size, up.toSet().size)
            assertEquals(what, root, up.first() % 12)
            assertEquals(what, up.first() + 12 * oct, up.last())
            assertEquals(what, type.steps, up.take(type.steps.size).map { it - up.first() })
            assertTrue(what, up.first() in 36..71 && up.last() <= 108)
            assertEquals(what, up.size * 2 - 1, s.upAndDown(hand).size)
        }
    }

    @Test fun melodicMinorComesDownAsNaturalMinor() {
        val s = Scale(9, ScaleType.MELODIC_MINOR)                       // A melodic minor
        assertEquals(listOf(57, 59, 60, 62, 64, 66, 68, 69), s.ascending(Hand.RIGHT))
        assertEquals(listOf(69, 67, 65, 64, 62, 60, 59, 57), s.descending(Hand.RIGHT))
    }

    @Test fun textbookFingerings() {
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 4, 5), Scale(0, ScaleType.MAJOR).fingering(Hand.RIGHT))
        assertEquals(listOf(5, 4, 3, 2, 1, 3, 2, 1), Scale(0, ScaleType.MAJOR).fingering(Hand.LEFT))
        assertEquals(listOf(1, 2, 3, 4, 1, 2, 3, 4), Scale(5, ScaleType.MAJOR).fingering(Hand.RIGHT))
        assertEquals(listOf(2, 1, 2, 3, 1, 2, 3, 4), Scale(10, ScaleType.MAJOR).fingering(Hand.RIGHT))
        assertEquals(listOf(3, 2, 1, 4, 3, 2, 1, 3), Scale(10, ScaleType.MAJOR).fingering(Hand.LEFT))
        assertEquals(listOf(2, 3, 4, 1, 2, 3, 1, 2), Scale(6, ScaleType.MAJOR).fingering(Hand.RIGHT))
        assertEquals(listOf(4, 3, 2, 1, 4, 3, 2, 1), Scale(11, ScaleType.MAJOR).fingering(Hand.LEFT))
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 4, 1, 2, 3, 1, 2, 3, 4, 5), Scale(0, ScaleType.MAJOR, 2).fingering(Hand.RIGHT))
        assertEquals(listOf(5, 4, 3, 2, 1, 3, 2, 1, 4, 3, 2, 1, 3, 2, 1), Scale(0, ScaleType.MAJOR, 2).fingering(Hand.LEFT))
        assertEquals(listOf(2, 1, 2, 3, 1, 2, 3, 4, 1, 2, 3, 1, 2, 3, 4), Scale(10, ScaleType.MAJOR, 2).fingering(Hand.RIGHT))
        assertEquals(listOf(1, 2, 3, 4, 1, 2, 3, 1, 2, 3, 4, 1, 2, 3, 4), Scale(5, ScaleType.MAJOR, 2).fingering(Hand.RIGHT))
        // The way down is the same numbers backwards: the 15-note C scale fingering the course uses.
        assertEquals("1 2 3 1 2 3 4 5 4 3 2 1 3 2 1", Scale(0, ScaleType.MAJOR).fingeringUpAndDown(Hand.RIGHT).joinToString(" "))
        // Minor keys: A minor like C major, C sharp minor starts on the third finger.
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 4, 5), Scale(9, ScaleType.HARMONIC_MINOR).fingering(Hand.RIGHT))
        assertEquals(listOf(3, 4, 1, 2, 3, 1, 2, 3), Scale(1, ScaleType.NATURAL_MINOR).fingering(Hand.RIGHT))
    }

    @Test fun fingeringsHaveOneNumberPerNoteAndSensibleEnds() {
        for (type in ScaleType.entries) for (root in 0..11) for (oct in 1..2) for (hand in Hand.entries) {
            val s = Scale(root, type, oct)
            val what = "${s.name} $hand $oct oct"
            val up = s.fingering(hand)
            assertEquals(what, s.ascending(hand).size, up.size)
            assertTrue(what, up.all { it in 1..5 })
            val both = s.fingeringUpAndDown(hand)
            assertEquals(what, s.upAndDown(hand).size, both.size)
            assertTrue(what, both.all { it in 1..5 })
            if (s.standardFingering) {
                // The way down mirrors the way up: it returns to the finger it started on.
                assertEquals(what, up.first(), both.last())
                assertEquals(what, up.last(), both[up.size - 1])
            }
        }
    }

    @Test fun littleFingerNeverOnABlackKeyAndTheThumbOnlyWhereUnavoidable() {
        // Minor scales are judged on harmonic minor, the form with the most notes to place.
        val thumbOnBlack = mutableListOf<String>()
        for (type in standardTypes) for (root in 0..11) for (hand in Hand.entries) {
            val judge = if (type.minorFamily) Scale(root, ScaleType.HARMONIC_MINOR, 2) else Scale(root, type, 2)
            val f = Scale(root, type, 2).fingering(hand)
            judge.ascending(hand).zip(f).forEach { (m, finger) ->
                if (isBlack(m)) {
                    assertTrue("${judge.name} $hand $f", finger != 5)
                    if (finger == 1) thumbOnBlack += "${judge.name} $hand"
                }
            }
        }
        // F sharp major and minor have no better option in the right hand: B and E sharp (F) are white, so none expected.
        assertEquals(emptyList<String>(), thumbOnBlack)
    }

    @Test fun crossingsFollowTheHandShape() {
        for (type in standardTypes) for (root in 0..11) for (hand in Hand.entries) {
            val f = Scale(root, type, 2).fingering(hand)
            f.zipWithNext().forEach { (a, b) ->
                val what = "${Scale(root, type).name} $hand $f"
                if (hand == Hand.RIGHT) assertTrue(what, b == a + 1 || b == 1 || (a == 1 && b in 2..3))
                else assertTrue(what, b == a - 1 || (a == 1 && b in 3..4) || (a == 1 && b == 2) || (b == 1 && a in 2..3))
            }
        }
    }

    @Test fun arpeggiosHaveOneFingerPerNote() {
        for (type in ArpeggioType.entries) for (root in 0..11) for (oct in 1..2) for (hand in Hand.entries) {
            val a = Arpeggio(root, type, oct)
            val what = "${a.name} $hand $oct oct"
            val up = a.ascending(hand)
            assertEquals(what, type.intervals.size * oct + 1, up.size)
            assertEquals(what, up, up.sorted())
            val f = a.fingering(hand)
            assertEquals(what, up.size, f.size)
            assertTrue(what, f.all { it in 1..5 })
            assertEquals(what, up.size * 2 - 1, a.fingeringUpAndDown(hand).size)
            val tones = type.intervals.map { (root + it) % 12 }
            assertTrue(what, up.all { it % 12 in tones })
            // The thumb sits on a white key whenever the chord has one.
            if (tones.any { !isBlack(it) }) up.zip(f).forEach { (m, x) -> if (x == 1) assertFalse("$what: thumb on black $m in $f", isBlack(m)) }
        }
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 5), Arpeggio(0, ArpeggioType.MAJOR, 2).fingering(Hand.RIGHT))
        assertEquals(listOf(5, 3, 2, 1, 3, 2, 1), Arpeggio(0, ArpeggioType.MAJOR, 2).fingering(Hand.LEFT))
        assertEquals(listOf(1, 2, 3, 5), Arpeggio(9, ArpeggioType.MINOR, 1).fingering(Hand.RIGHT))
        assertEquals(listOf(3, 1, 2, 4), Arpeggio(3, ArpeggioType.MAJOR, 1).fingering(Hand.RIGHT))
    }

    @Test fun arpeggioChordTonesAreRight() {
        assertEquals(listOf(60, 64, 67, 72), Arpeggio(0, ArpeggioType.MAJOR).ascending(Hand.RIGHT))
        assertEquals(listOf(57, 60, 64, 69), Arpeggio(9, ArpeggioType.MINOR).ascending(Hand.RIGHT))
        assertEquals(listOf(60, 64, 67, 70, 72), Arpeggio(0, ArpeggioType.DOMINANT_7).ascending(Hand.RIGHT))
        assertEquals(listOf(60, 63, 66, 69, 72), Arpeggio(0, ArpeggioType.DIMINISHED_7).ascending(Hand.RIGHT))
    }

    @Test fun renderedExercisesParseAndPlayInThePlayer() {
        for (type in ScaleType.entries) for (root in listOf(0, 1, 6, 10)) for (practice in Practice.entries) for (oct in 1..2) {
            val s = Scale(root, type, oct)
            val ex = s.toExercise(practice, 80.0)
            val msPerBeat = 60_000 / ex.bpm
            val right = parseLine(ex.right, Hand.RIGHT, msPerBeat)
            val left = parseLine(ex.left, Hand.LEFT, msPerBeat)
            val what = s.title(practice)
            if (practice != Practice.LEFT) assertEquals(what, s.upAndDown(Hand.RIGHT), right.map { it.midi }) else assertTrue(what, right.isEmpty())
            if (practice != Practice.RIGHT) assertEquals(what, s.upAndDown(Hand.LEFT), left.map { it.midi }) else assertTrue(what, left.isEmpty())
            if (practice != Practice.LEFT) assertEquals(what, right.size, ex.fingers.split(" ").size)
            assertEquals(what, practice, ex.practice)
            assertEquals(what, right.size + left.size, ex.toSong("scale", what).notes.size)
        }
        for (type in ArpeggioType.entries) for (root in 0..11) {
            val a = Arpeggio(root, type, 2)
            val ex = a.toExercise(Practice.BOTH, 60.0, beatsPerNote = 0.5, upAndDown = false)
            assertEquals(a.ascending(Hand.RIGHT), parseLine(ex.right, Hand.RIGHT, 1000.0).map { it.midi })
            assertEquals(a.ascending(Hand.LEFT), parseLine(ex.left, Hand.LEFT, 1000.0).map { it.midi })
        }
    }

    @Test fun keyNamesAndSpelling() {
        assertEquals("Db major", Scale(1, ScaleType.MAJOR).name)
        assertEquals("C# natural minor", Scale(1, ScaleType.NATURAL_MINOR).name)
        assertEquals("Eb harmonic minor", Scale(3, ScaleType.HARMONIC_MINOR).name)
        assertTrue(Scale(10, ScaleType.MAJOR).toExercise(Practice.RIGHT, 80.0).right.startsWith("Bb3:1 C4:1"))
        assertTrue(Scale(6, ScaleType.MAJOR).toExercise(Practice.RIGHT, 80.0).right.startsWith("F#4:1 G#4:1"))
        assertTrue(Scale(2, ScaleType.NATURAL_MINOR).flats)                // D minor has a B flat
        assertTrue(Scale(7, ScaleType.DORIAN).flats)        // G dorian has a B flat too
        assertFalse(Scale(7, ScaleType.MIXOLYDIAN).flats)                  // G mixolydian is the C major notes
    }
}
