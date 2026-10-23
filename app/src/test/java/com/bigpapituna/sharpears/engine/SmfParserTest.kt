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

    // ---- security review finding 4 (2.8.3): what a damaged or hostile file says is held to what music has ----

    /** One C4 quarter at 480 ticks a quarter, after the conductor's [events]. */
    private fun withConductor(vararg events: Int) = smf(track(*events), track(0, 0x90, 60, 100, 480, 60, 0))

    @Test fun anOddTimeSignatureIsLeftOutSoFourFourStays() {
        fun sig(beats: Int, exponent: Int) = SmfParser.parse(withConductor(0, 0xFF, 0x58, 4, beats, exponent, 24, 8), "t", "t").timeSignature
        assertEquals(4 to 4, sig(3, 31))          // 1 shl 31 was Int.MIN_VALUE: the metronome's loop never ended
        assertEquals(4 to 4, sig(3, 20))          // a denominator of a million: millions of bars a minute
        assertEquals(4 to 4, sig(3, 6))           // a 64th note beat
        assertEquals(4 to 4, sig(0, 2))
        assertEquals(4 to 4, sig(200, 2))         // read as a signed byte, -56 beats
        assertEquals(4 to 4, sig(33, 2))
        assertEquals(7 to 8, sig(7, 3))
        assertEquals(1 to 32, sig(1, 5))
        assertEquals(32 to 1, sig(32, 0))
        // A good signature is not undone by a bad one after it.
        assertEquals(3 to 4, SmfParser.parse(withConductor(0, 0xFF, 0x58, 4, 3, 2, 24, 8, 0, 0xFF, 0x58, 4, 3, 31, 24, 8), "t", "t").timeSignature)
    }

    @Test fun aZeroTempoIsLeftOutAndTemposAreHeldBetween20And300Bpm() {
        fun song(a: Int, b: Int, c: Int) = SmfParser.parse(withConductor(0, 0xFF, 0x51, 3, a, b, c), "t", "t")
        val zero = song(0, 0, 0)
        assertEquals(120.0, zero.bpm, 1e-9)                         // the default stays
        assertEquals(500L, zero.notes.single().durationMs)
        assertEquals(20.0, song(0xFF, 0xFF, 0xFF).bpm, 1e-9)        // 3.6 bpm
        assertEquals(3000L, song(0xFF, 0xFF, 0xFF).notes.single().durationMs)
        assertEquals(300.0, song(0, 0, 1).bpm, 1e-9)                // one microsecond a quarter
        assertEquals(200L, song(0, 0, 1).notes.single().durationMs)
    }

    @Test fun aHugeDeltaTimeEndsTheSongAtTwentyMinutes() {
        val max = 0x0FFFFFFF                      // the longest delta a file can hold: over 6 days at 120 bpm and 480 ticks
        val bytes = smf(track(
            0, 0x90, 60, 100, 480, 60, 0,         // C4, the first half second
            0, 62, 100,                           // D4 from 500 ms, held across the gap
            max, 64, 100, max, 64, 0,             // E4, days in: gone
            max, 62, 0,
            max, 0xFF, 0x06, 1, 'Z'.code,         // a marker after the end: gone
        ))
        val started = System.nanoTime()
        val song = SmfParser.parse(bytes, "t", "t")
        assertEquals(listOf(60, 62), song.notes.map { it.midi })
        assertEquals(SmfParser.MAX_SONG_MS, song.durationMs)
        assertEquals(SmfParser.MAX_SONG_MS - 500, song.notes.last().durationMs)
        assertTrue(song.sections.isEmpty())
        assertEquals(SmfParser.MAX_SONG_MS / 2_000 + 1, labelBarsAligned(song).size.toLong())   // 601 bars of 2 s, not millions
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000)
    }

    @Test fun aGiantNoteCountKeepsTheEarliestNotes() {
        // 25,000 notes 24 ticks (25 ms) apart: the first 20,000 stay, in time order.
        val events = mutableListOf(0, 0x90, 60, 100)
        for (i in 0 until 25_000) { events += listOf(24, 60 + i % 12, 0); if (i < 24_999) events += listOf(0, 60 + (i + 1) % 12, 100) }
        val song = SmfParser.parse(smf(track(*events.toIntArray())), "t", "t")
        assertEquals(SmfParser.MAX_NOTES, song.notes.size)
        assertEquals((SmfParser.MAX_NOTES - 1) * 25L, song.notes.last().startMs)
    }

    @Test fun thousandsOfTempoChangesCostLittle() {
        // 40,000 tempo changes against 20,000 notes: the tempo in force was a linear search per note (800 million steps).
        val tempos = (0 until 40_000).flatMap { i -> listOf(12, 0xFF, 0x51, 3, 0x07, if (i % 2 == 0) 0xA1 else 0x00, 0x20) }
        val notes = mutableListOf(0, 0x90, 60, 100)
        for (i in 0 until 20_000) { notes += listOf(24, 60, 0); if (i < 19_999) notes += listOf(0, 60, 100) }
        val bytes = smf(track(*tempos.toIntArray()), track(*notes.toIntArray()))
        assertTrue(bytes.size <= SmfParser.MAX_BYTES)
        val started = System.nanoTime()
        val song = SmfParser.parse(bytes, "t", "t")
        val ms = (System.nanoTime() - started) / 1_000_000
        assertEquals(20_000, song.notes.size)
        assertTrue(song.notes.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
        assertTrue("parsed in $ms ms", ms < 2_000)
    }

    /** The tempo clock works in Double: a tick count times microseconds per quarter wrapped Long past about 5e11 ticks. */
    @Test fun theTempoClockNeverWrapsAndRunsBothWays() {
        val slow = SmfParser.TempoClock(listOf(0L to 3_000_000), 1)
        assertEquals(1.2e16, slow.ms(4_000_000_000_000L).toDouble(), 1e6)      // 4e12 ticks x 3e6 us passes Long's range
        val map = SmfParser.TempoClock(listOf(0L to 500_000, 1_920L to 1_000_000, 2_400L to 250_000), 480)
        val ticks = listOf(0L, 960L, 1_920L, 2_400L, 2_640L, 2_880L)
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L, 3_125L, 3_250L), ticks.map(map::ms))
        for (t in ticks) assertEquals(t, map.tick(map.ms(t).toDouble()))
    }

    @Test fun atMost128TracksAreReadAndNoFileOver512Kb() {
        val tracks = Array(130) { i -> track(0, 0x90, 30 + i % 60, 100, 480, 30 + i % 60, 0) }
        val data = SmfParser.read(smf(*tracks))
        assertEquals((0 until SmfParser.MAX_TRACKS).toList(), data.notes.map { it.track }.distinct().sorted())
        assertThrows(MidiFormatException::class.java) { SmfParser.read(fixture + ByteArray(SmfParser.MAX_BYTES)) }
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
