package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class SmfParserTest {

    // Format 1, 480 ticks per quarter.
    // Track 0: 120 bpm, 3/4, marker "A"; at tick 1920 tempo drops to 60 bpm, marker "B".
    // Track 1 (right hand): C4 D4 E4 F4 quarters at 120 bpm, then G4 one quarter at 60 bpm.
    //                       Uses running status and note-on velocity 0 as note-off.
    // Track 2 (left hand):  C3 for two quarters, closed with an explicit 0x80 note-off.
    private val fixture: ByteArray = smf(
        track(
            0, 0xFF, 0x51, 3, 0x07, 0xA1, 0x20,
            0, 0xFF, 0x58, 4, 3, 2, 24, 8,
            0, 0xFF, 0x06, 1, 'A'.code,
            1920, 0xFF, 0x51, 3, 0x0F, 0x42, 0x40,
            0, 0xFF, 0x06, 1, 'B'.code,
        ),
        track(
            0, 0x90, 60, 100,
            480, 60, 0, 0, 62, 100,
            480, 62, 0, 0, 64, 100,
            480, 64, 0, 0, 65, 100,
            480, 65, 0, 0, 67, 100,
            480, 67, 0,
        ),
        track(
            0, 0x90, 48, 80,
            960, 0x80, 48, 64,
        ),
    )

    @Test fun timingFollowsTempoMap() {
        val song = SmfParser.parse(fixture, "t", "t")
        val right = song.notes.filter { it.hand == Hand.RIGHT }
        assertEquals(listOf(60, 62, 64, 65, 67), right.map { it.midi })
        assertEquals(listOf(0L, 500L, 1000L, 1500L, 2000L), right.map { it.startMs })
        assertEquals(1000L, right.last().durationMs)          // one quarter at 60 bpm
        assertEquals(3000L, song.durationMs)
        assertEquals(120.0, song.bpm, 0.001)
        assertEquals(3 to 4, song.timeSignature)
    }

    @Test fun handRules() {
        val auto = SmfParser.parse(fixture, "t", "t")
        assertEquals(Hand.LEFT, auto.notes.single { it.midi == 48 }.hand)
        assertEquals(1000L, auto.notes.single { it.midi == 48 }.durationMs)

        val swapped = SmfParser.parse(fixture, "t", "t", HandRule.Tracks(right = 2, left = 1))
        assertEquals(Hand.RIGHT, swapped.notes.single { it.midi == 48 }.hand)
        assertEquals(Hand.LEFT, swapped.notes.single { it.midi == 60 }.hand)

        val split = SmfParser.parse(fixture, "t", "t", HandRule.MiddleCSplit)
        assertEquals(Hand.LEFT, split.notes.single { it.midi == 48 }.hand)
        assertTrue(split.notes.filter { it.midi >= 60 }.all { it.hand == Hand.RIGHT })
    }

    @Test fun groupsAndSections() {
        val song = SmfParser.parse(fixture, "t", "t")
        assertEquals(listOf(48, 60), song.groups.first().notes.map { it.midi })
        assertEquals(5, song.groups.size)
        assertEquals(listOf(Section("A", 0, 2000), Section("B", 2000, 3000)), song.sections)
    }

    @Test fun truncatedFileThrows() {
        assertThrows(MidiFormatException::class.java) { SmfParser.parse(fixture.copyOf(fixture.size - 10), "t", "t") }
        assertThrows(MidiFormatException::class.java) { SmfParser.parse("not midi".toByteArray(), "t", "t") }
    }

    @Test fun bundledFurEliseParses() {
        val song = SmfParser.parse(File("src/main/assets/songs/fur-elise.mid").readBytes(), "fur-elise", "Für Elise")
        println("fur-elise: ${song.notes.size} notes, ${song.durationMs} ms, ${song.bpm} bpm, ${song.timeSignature}, " +
            "right=${song.notes.count { it.hand == Hand.RIGHT }} left=${song.notes.count { it.hand == Hand.LEFT }}")
        assertTrue(song.notes.size > 500)
        assertTrue(song.notes.any { it.hand == Hand.LEFT } && song.notes.any { it.hand == Hand.RIGHT })
        assertEquals(76, song.notes.first().midi)              // the piece opens on E5
        assertTrue(song.durationMs in 120_000..300_000)
    }

    private fun smf(vararg tracks: ByteArray) = ByteArrayOutputStream().apply {
        write("MThd".toByteArray()); u32(6); u16(1); u16(tracks.size); u16(480)
        tracks.forEach { write(it) }
    }.toByteArray()

    /** Events as a flat list: delta, then the message bytes. Deltas are VLQ-encoded; end-of-track is appended. */
    private fun track(vararg events: Int): ByteArray {
        val body = ByteArrayOutputStream()
        splitEvents(events.toList()).forEach { (delta, payload) -> body.vlq(delta); payload.forEach(body::write) }
        body.write(byteArrayOf(0, 0xFF.toByte(), 0x2F, 0))
        val bytes = body.toByteArray()
        return ByteArrayOutputStream().apply { write("MTrk".toByteArray()); u32(bytes.size); write(bytes) }.toByteArray()
    }

    /**
     * Audit: an imported file is anything a phone holds. Every bundled file with seeded damage (bytes changed, cut,
     * doubled) either parses into a song the player can run (a finite tempo, notes on keys 0 to 127 in time order, none
     * before 0 or longer than the song) or is refused with MidiFormatException; nothing else may escape. A track length
     * past 2 GB went negative and threw out of the parser's bounds, and a zero division or a zero tempo made a song of
     * infinite tempo whose notes all sat at 0 or at the end of time.
     */
    @Test fun damagedFilesParseOrAreRefused() {
        val files = File("src/main/assets/songs").listFiles { f -> f.extension == "mid" }!!.sortedBy { it.name }
        val rnd = java.util.Random(7)
        var refused = 0; var parsed = 0
        for (f in files) {
            val good = f.readBytes()
            repeat(40) { n ->
                val b = good.copyOf()
                when (n % 4) {
                    0 -> repeat(1 + rnd.nextInt(4)) { b[rnd.nextInt(b.size)] = rnd.nextInt(256).toByte() }
                    1 -> b[8 + rnd.nextInt(6)] = rnd.nextInt(256).toByte()                                      // the header
                    2 -> repeat(1 + rnd.nextInt(3)) { b[18 + rnd.nextInt(4)] = rnd.nextInt(256).toByte() }          // the first track's length
                    else -> repeat(2) { val i = rnd.nextInt(b.size); b[i] = 0xFF.toByte(); if (i + 1 < b.size) b[i + 1] = 0x51 }
                }
                val damaged = if (n % 5 == 0) b.copyOf(rnd.nextInt(b.size)) else b
                val song = try { SmfParser.parse(damaged, "x", "x") } catch (e: MidiFormatException) { refused++; return@repeat }
                parsed++
                val what = "${f.name} #$n"
                assertTrue("$what bpm ${song.bpm}", song.bpm.isFinite() && song.bpm > 0)
                song.notes.firstOrNull { !(it.midi in 0..127 && it.startMs >= 0 && it.durationMs >= 1 && it.startMs + it.durationMs <= song.durationMs) }?.let { fail("$what: $it in ${song.durationMs} ms") }
                assertTrue(what, song.notes.zipWithNext().all { (a, c) -> a.startMs <= c.startMs })
                assertTrue("$what lasts ${song.durationMs} ms", song.durationMs < Long.MAX_VALUE / 4)
            }
        }
        assertTrue("refused $refused, parsed $parsed", refused > 0 && parsed > 0)
    }

    /** Splits a flat list into (delta, payload) using MIDI message lengths. */
    private fun splitEvents(flat: List<Int>): List<Pair<Int, List<Int>>> {
        val out = mutableListOf<Pair<Int, List<Int>>>()
        var i = 0
        var running = 0
        while (i < flat.size) {
            val delta = flat[i++]
            val first = flat[i]
            val len = when {
                first == 0xFF -> 3 + flat[i + 2]
                first >= 0x80 -> { running = first; if (first and 0xF0 in listOf(0xC0, 0xD0)) 2 else 3 }
                else -> if (running and 0xF0 in listOf(0xC0, 0xD0)) 1 else 2
            }
            out += delta to flat.subList(i, i + len)
            i += len
        }
        return out
    }

    private fun ByteArrayOutputStream.u16(v: Int) { write(v shr 8 and 0xFF); write(v and 0xFF) }
    private fun ByteArrayOutputStream.u32(v: Int) { u16(v shr 16); u16(v and 0xFFFF) }
    private fun ByteArrayOutputStream.vlq(v: Int) {
        var buffer = v and 0x7F
        var rest = v shr 7
        while (rest > 0) { buffer = (buffer shl 8) or 0x80 or (rest and 0x7F); rest = rest shr 7 }
        while (true) { write(buffer and 0xFF); if (buffer and 0x80 == 0) break; buffer = buffer shr 8 }
    }
}
