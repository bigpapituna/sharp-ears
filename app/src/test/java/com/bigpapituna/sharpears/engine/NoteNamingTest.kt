package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteNamingTest {
    @Test fun flatKeysNameBlackKeysAsFlats() {
        assertEquals("Bb4", noteName(70, flats = true))
        assertEquals("A#4", noteName(70, flats = false))
        assertEquals("A#4", noteName(70))
        assertEquals("Eb3", noteName(51, true))
        assertEquals("C4", noteName(60, true))                                     // white keys never change
    }

    @Test fun theVoiceSaysFlatsInFlatKeys() {
        assertEquals("B flat", spokenNote(70, flats = true))
        assertEquals("D flat", spokenNote(61, flats = true))
        assertEquals("G flat", spokenNote(66, flats = true))
        assertEquals("C sharp", spokenNote(61))                                    // no key: the usual spelling
        assertEquals("F sharp", spokenNote(66))
        assertEquals("C four", spokenNote(60, flats = true))
        val r = Report(listOf(70 to 3), retries = 3, wrongKeys = 3, recap = null)
        assertEquals("You missed B flat three times. Try that bar slowly.", feedbackLine(r, perform = false, flats = true).text)
    }

    @Test fun flatLessonsAndAFlatKeySignatureSetTheSong() {
        val keys = COURSE.first { it.id == "keys" }.lessons
        assertEquals(listOf("key-g", "key-f", "key-d", "key-bb", "key-em", "key-dm"), keys.map { it.id })
        val f = keys.first { it.id == "key-f" }
        assertTrue(f.steps.mapNotNull { it.exercise }.all { it.toSong("x", "x").preferFlats })
        assertFalse(keys.first { it.id == "key-g" }.steps[1].exercise!!.toSong("x", "x").preferFlats)
        // Tunes are melody only, 8 to 16 notes, four bars.
        for (l in keys) {
            val tune = l.steps.last().exercise!!
            val n = tune.right.split(" ").size
            assertTrue("${l.id}: $n notes", n in 8..16)
            assertEquals("${l.id}: four bars", 16.0, tune.right.split(" ").sumOf { it.substringAfter(':').toDouble() }, 1e-9)
            assertEquals(StepKind.PERFORM, l.steps.last().kind)
        }
        // Scales carry the fingering by the book: B flat major starts on finger 4.
        val bb = keys.first { it.id == "key-bb" }.steps[1].exercise!!.toSong("b", "b")
        assertEquals(listOf(4, 1, 2, 3, 1, 2, 3, 4), Fingering.of(bb).let { f -> bb.notes.take(8).map { f[it] } })
        assertTrue(SmfParser.parse(smf(keySig = -1), "f", "f").preferFlats)        // F major in the file
        assertFalse(SmfParser.parse(smf(keySig = 2), "d", "d").preferFlats)        // D major
        assertFalse(SmfParser.parse(smf(keySig = null), "c", "c").preferFlats)
    }

    /** One track: an optional key signature, then one middle C. */
    private fun smf(keySig: Int?): ByteArray {
        val ks = if (keySig == null) byteArrayOf() else byteArrayOf(0, 0xFF.toByte(), 0x59, 2, keySig.toByte(), 0)
        val body = ks + byteArrayOf(0, 0x90.toByte(), 60, 80, 0x60, 0x80.toByte(), 60, 0, 0, 0xFF.toByte(), 0x2F, 0)
        val header = byteArrayOf('M'.code.toByte(), 'T'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(), 0, 0, 0, 6, 0, 0, 0, 1, 0, 96)
        val len = body.size
        return header + byteArrayOf('M'.code.toByte(), 'T'.code.toByte(), 'r'.code.toByte(), 'k'.code.toByte(),
            (len ushr 24).toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()) + body
    }
}
