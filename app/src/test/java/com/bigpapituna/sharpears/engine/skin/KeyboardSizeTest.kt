package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.Note
import com.bigpapituna.sharpears.engine.fitRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kids' keys show just the octave the notes need, so each key is about twice as wide. */
class KeyboardSizeTest {
    private fun notes(vararg m: Int) = m.map { Note(it, 0L, 100L, Hand.RIGHT, 80) }

    @Test fun regularIsThePlayersOwnRange() {
        for (set in listOf(notes(60, 64, 67), notes(48, 79), notes(30, 100), notes(65))) {
            assertEquals(fitRange(set), KeyboardSize.REGULAR.rangeOf(set))
        }
    }

    @Test fun bigShowsOneOctaveForAFiveFingerTune() {
        assertEquals(60..72, KeyboardSize.BIG.range(listOf(64, 62, 60)))           // Hot Cross Buns
        assertEquals(60..72, KeyboardSize.BIG.range(listOf(60, 62, 64, 65, 67)))   // up to G
        assertEquals(72..84, KeyboardSize.BIG.range(listOf(72)))                   // a lone C: C to C
        assertEquals(48..72, KeyboardSize.BIG.range(listOf(48, 60, 64)))           // left paw: two octaves
        assertEquals(fitRange(notes(30, 100)), KeyboardSize.BIG.range(listOf(30, 100)))   // past five octaves: the player's window
    }

    @Test fun bigKeysAreAboutTwiceAsWide() {
        val big = KeyboardSize.BIG.range(listOf(60, 64, 67)).let { KeyboardLayout(it.first, it.last, 2000f, 300f) }
        val reg = KeyboardSize.REGULAR.range(listOf(60, 64, 67)).let { KeyboardLayout(it.first, it.last, 2000f, 300f) }
        assertTrue(big.whiteWidth / reg.whiteWidth > 1.8f)
        assertTrue(KeyboardSize.BIG.heightShare > KeyboardSize.REGULAR.heightShare)
    }

    @Test fun everyNoteStaysOnTheKeyboard() {
        val r = java.util.Random(4)
        repeat(300) {
            val ms = List(1 + r.nextInt(8)) { 36 + r.nextInt(48) }
            val range = KeyboardSize.BIG.range(ms)
            assertTrue(ms.all { it in range })
            assertTrue(range.first % 12 == 0 && range.last % 12 == 0)
        }
        assertEquals(KeyboardSize.BIG, KeyboardSize.forKid(true)); assertEquals(KeyboardSize.REGULAR, KeyboardSize.forKid(false))
    }
}
