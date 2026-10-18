package com.bigpapituna.sharpears.engine.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boomwhacker colours, letters, animals and how the pup names a key (docs/KIDS-PLAN.md section 3). */
class NoteColoursTest {
    @Test fun whiteKeysFollowTheBoomwhackerOrderInEveryOctave() {
        val names = listOf(60, 62, 64, 65, 67, 69, 71).map { NoteColours.white(it)!!.name }
        assertEquals(listOf("red", "orange", "yellow", "green", "teal", "blue", "purple"), names)
        for (octave in -1..8) for (pc in listOf(0, 2, 4, 5, 7, 9, 11)) {
            val midi = 12 * (octave + 1) + pc
            if (midi in 0..127) assertEquals(NoteColours.white(60 + pc), NoteColours.white(midi))
        }
        assertEquals(NoteColours.colour(60), NoteColours.colour(36))
        assertEquals(7, NoteColours.WHITE.map { it.argb }.toSet().size)        // seven different colours
    }

    @Test fun animalsMatchTheirLetters() {
        assertEquals(listOf(Animal.CAT, Animal.DOG, Animal.ELEPHANT, Animal.FROG, Animal.GOAT, Animal.ANT, Animal.BEAR),
            listOf(48, 50, 52, 53, 55, 57, 59).map { NoteColours.animal(it) })
        // Each animal starts with its letter: the pup can teach the letter through the animal later.
        NoteColours.WHITE.forEach { assertEquals(it.letter.lowercaseChar(), it.animal.word.first()) }
        assertNull(NoteColours.animal(61))
    }

    @Test fun blackKeysAreStripesOfTheirNeighbours() {
        assertEquals(NoteColours.colour(60) to NoteColours.colour(62), NoteColours.stripes(61))   // C sharp: red and orange
        assertEquals(NoteColours.colour(65) to NoteColours.colour(67), NoteColours.stripes(66))   // F sharp: green and teal
        assertEquals(NoteColours.colour(69) to NoteColours.colour(71), NoteColours.stripes(70))   // A sharp: blue and purple
        assertNull(NoteColours.stripes(64))
        assertEquals("between red and orange", NoteColours.colourName(61))
        assertEquals("yellow", NoteColours.colourName(64))
        // The blend (for tiny places) sits halfway between the two.
        val b = NoteColours.blend(61)
        assertEquals(0xFFL, b shr 24)
        assertEquals(Math.round((0xE5 + 0xF2) / 2.0), (b shr 16) and 0xFF)
    }

    @Test fun lettersAndLabels() {
        assertEquals("C", NoteColours.letter(60)); assertEquals("C#", NoteColours.letter(61)); assertEquals("Db", NoteColours.letter(61, flats = true))
        assertEquals("Bb", NoteColours.letter(70, flats = true)); assertEquals("A#", NoteColours.letter(70))
        assertEquals(NoteLabel.Letter("E"), NoteColours.label(64, reader = true))
        assertEquals(NoteLabel.Creature(Animal.ELEPHANT), NoteColours.label(64, reader = false))
        assertEquals(NoteLabel.None, NoteColours.label(63, reader = false))
        assertEquals(NoteLabel.Letter("Eb"), NoteColours.label(63, reader = true, flats = true))
    }

    @Test fun spokenNames() {
        assertEquals("the red cat key", NoteColours.spokenName(60, reader = false))
        assertEquals("the red C key", NoteColours.spokenName(72, reader = true))
        assertEquals("the yellow elephant key", NoteColours.spokenName(64, reader = false))
        assertEquals("the purple bear key", NoteColours.spokenName(59, reader = false))
        assertEquals("the C sharp key", NoteColours.spokenName(61, reader = true))
        assertEquals("the B flat key", NoteColours.spokenName(70, reader = true, flats = true))
        assertEquals("the black key between the cat and the dog", NoteColours.spokenName(61, reader = false))
        assertEquals("the black key between the ant and the bear", NoteColours.spokenName(70, reader = false))
        // Spoken lines carry no symbols (the voice rule): no sharp sign, no digits.
        for (m in 48..72) for (r in listOf(true, false)) for (f in listOf(true, false)) {
            val s = NoteColours.spokenName(m, r, f)
            assertTrue(s, s.none { it == '#' || it.isDigit() })
        }
    }

    @Test fun everyColourGetsReadableInk() {
        // A letter on any colour (both halves of a stripe included) reaches 4.5:1 with the ink picked for it.
        for (c in NoteColours.WHITE) assertTrue("contrast on ${c.name}", NoteColours.contrast(c.argb, NoteColours.inkFor(c.argb)) >= 4.5)
        assertEquals(NoteColours.INK_DARK, NoteColours.inkFor(0xFFF4C430L))       // dark letters on yellow
        assertEquals(21.0, NoteColours.contrast(0xFF000000L, 0xFFFFFFFFL), 0.01)
    }

    @Test fun coloursStandOutOnTheLane() {
        // The lane is near black (0xFF110F0D): every note colour must glow on it, at least 4:1.
        for (c in NoteColours.WHITE) assertTrue(c.name, NoteColours.contrast(c.argb, 0xFF110F0DL) >= 4.0)
    }
}
