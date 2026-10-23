package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Security review finding 4 (2.8.3): any bytes a learner picks reach SmfParser, My songs, the chord labeler and the
 * metronome. 200 seeded inputs (noise behind a MIDI header, random events in well framed chunks, real songs damaged,
 * real songs with a hostile track added) and cuts of every bundled song go through all of them. Each is refused the
 * designed way (MidiFormatException from the parser, MySongs.Refused with its kind line) or comes out bounded: a time
 * signature music has, 20 to 300 bpm, at most 20 minutes and 20,000 notes, at most MAX_LABEL_BARS bars, a beat of 25 ms
 * to 12 s. Nothing else may escape, and no input may take long.
 */
class MidiFuzzTest {
    private val songs = File("src/main/assets/songs").listFiles { f -> f.extension == "mid" }!!.sortedBy { it.name }.map { it.readBytes() }

    @Test(timeout = 120_000) fun seededRandomAndDamagedFilesAreRefusedOrBounded() {
        val rnd = java.util.Random(2026)
        val inputs = List(200) { n ->
            when (n % 4) {
                0 -> noise(rnd)
                1 -> randomSong(rnd, big = n % 20 == 1)
                2 -> damaged(songs[rnd.nextInt(songs.size)], rnd)
                else -> withHostileTrack(songs[rnd.nextInt(songs.size)], rnd)
            }
        }
        val t = Tally()
        inputs.forEachIndexed { n, bytes -> check(bytes, "input $n (${bytes.size} bytes)", t) }
        println("fuzz: $t")
        assertTrue("some files read: $t", t.read > 20)
        assertTrue("some files refused: $t", t.refused > 20)
        assertTrue("the note cap was reached: $t", t.atNoteCap > 0)
        assertTrue("the length cap was reached: $t", t.atLengthCap > 0)
    }

    @Test(timeout = 120_000) fun cutsOfEveryBundledSongAreRefusedOrBounded() {
        val t = Tally()
        for ((i, good) in songs.withIndex()) {
            val cuts = (listOf(0, 4, 8, 13, 14, 18, 22, 23) + (1..15).map { good.size * it / 16 }).filter { it < good.size }.distinct()
            for (cut in cuts) check(good.copyOf(cut), "song $i cut at $cut", t)
            check(good, "song $i whole", t)
        }
        println("cuts: $t")
        assertEquals("every whole song reads", songs.size, t.read - t.readCut)
    }

    private class Tally {
        var read = 0; var readCut = 0; var refused = 0; var atNoteCap = 0; var atLengthCap = 0; var slowestMs = 0L
        override fun toString() = "read $read, refused $refused, at the note cap $atNoteCap, at the length cap $atLengthCap, slowest $slowestMs ms"
    }

    /** Every path a picked file takes, each either refused the designed way or bounded. */
    private fun check(bytes: ByteArray, what: String, t: Tally) {
        val t0 = System.nanoTime()
        try {
            val data = try { SmfParser.read(bytes) } catch (_: MidiFormatException) { null }
            if (data == null) t.refused++ else {
                t.read++
                if ("cut at" in what) t.readCut++
                assertTrue("$what notes ${data.notes.size}", data.notes.size <= SmfParser.MAX_NOTES)
                if (data.notes.size == SmfParser.MAX_NOTES) t.atNoteCap++
                val (beats, beat) = data.timeSignature
                assertTrue("$what signature ${data.timeSignature}", beats in 1..32 && beat in listOf(1, 2, 4, 8, 16, 32))
                assertTrue("$what tempos", data.tempos.all { 60_000_000.0 / it.second in 20.0..300.0 })
                for (n in data.notes) {
                    assertTrue("$what $n", n.key in 0..127 && n.endTick >= n.startTick && data.ms(n.startTick) in 0..SmfParser.MAX_SONG_MS)
                    assertTrue("$what $n", data.ms(n.endTick) <= SmfParser.MAX_SONG_MS + 1)
                }
                if (data.notes.any { data.ms(it.endTick) >= SmfParser.MAX_SONG_MS - 1 }) t.atLengthCap++
                bounded(SmfParser.parse(bytes, "x", "x"), what)
                bounded(SmfParser.parse(bytes, "x", "x", HandRule.MiddleCSplit), what)
            }
            val easy = try { MySongs.scan(bytes, "x.mid"); MySongs.song(bytes, "x", "x", bothHands = true) } catch (_: MySongs.Refused) { null }
            easy?.let { bounded(it, "$what (my song)") }
        } catch (e: Throwable) {
            if (e is AssertionError) throw e
            throw AssertionError("$what threw $e", e)
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        t.slowestMs = maxOf(t.slowestMs, ms)
        assertTrue("$what took $ms ms", ms < 5_000)
    }

    /** What the player, the launch pass and the metronome need of a song, whatever file it came from. */
    private fun bounded(song: Song, what: String) {
        assertTrue("$what bpm ${song.bpm}", song.bpm in 20.0..300.0)
        assertTrue("$what ${song.notes.size} notes", song.notes.size <= SmfParser.MAX_NOTES)
        assertTrue("$what lasts ${song.durationMs} ms", song.durationMs in 0..SmfParser.MAX_SONG_MS + 1)
        song.notes.firstOrNull { !(it.midi in 0..127 && it.startMs >= 0 && it.durationMs >= 1 && it.startMs + it.durationMs <= song.durationMs) }
            ?.let { fail("$what: $it in ${song.durationMs} ms") }
        assertTrue(what, song.notes.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
        val bars = labelBarsAligned(song)
        assertTrue("$what ${bars.size} bars", bars.size <= MAX_LABEL_BARS)
        songPack(song, bars = bars)
        ChordTrack(bars, barMs(song)).at(song.durationMs / 2.0)
        val m = Metronome(song.bpm, song.timeSignature)
        assertTrue("$what beat ${m.beatMs} ms", m.beatMs in 25.0..12_000.0)
        assertTrue(what, m.clicks(-m.barMs, 0.0).size <= 32)
        assertTrue(what, m.clicks(0.0, song.durationMs.toDouble()).size <= Metronome.MAX_CLICKS)
    }

    // ---- the inputs ----

    /** A MIDI header (any format, track count and division) and chunks of noise, their lengths true or not. */
    private fun noise(rnd: java.util.Random): ByteArray = Out().apply {
        ascii("MThd"); u32(6); u16(rnd.nextInt(3)); u16(rnd.nextInt(300)); u16(rnd.nextInt(0x10000))
        repeat(1 + rnd.nextInt(4)) {
            val len = rnd.nextInt(2_000)
            ascii(if (rnd.nextInt(5) == 0) "XXXX" else "MTrk"); u32(if (rnd.nextInt(4) == 0) rnd.nextInt() else len)
            repeat(len) { u8(rnd.nextInt(256)) }
        }
    }.bytes()

    /**
     * Well framed tracks of random events: notes, tempos (zero, the slowest, the fastest), time signatures of any two bytes,
     * names, markers, controllers, sysex, and delta times from none to the longest a file can hold. [big]: 30,000 short
     * notes, past the note cap.
     */
    private fun randomSong(rnd: java.util.Random, big: Boolean): ByteArray = Out().apply {
        val tracks = 1 + rnd.nextInt(5)
        ascii("MThd"); u32(6); u16(rnd.nextInt(2)); u16(tracks); u16(if (big) 480 else listOf(1, 24, 96, 480, 960, 0x7FFF)[rnd.nextInt(6)])
        repeat(tracks) { track ->
            val body = Out()
            if (big) repeat(30_000 / tracks) {
                val key = rnd.nextInt(128); val ch = rnd.nextInt(16)
                body.vlq(rnd.nextInt(4)); body.u8(0x90 or ch); body.u8(key); body.u8(1 + rnd.nextInt(127))
                body.vlq(1 + rnd.nextInt(4)); body.u8(0x80 or ch); body.u8(key); body.u8(0)
            } else repeat(rnd.nextInt(400)) {
                body.vlq(when (rnd.nextInt(20)) { 0 -> 0x0FFFFFFF; 1 -> rnd.nextInt(0x0FFFFFFF); in 2..8 -> 0; else -> rnd.nextInt(480) })
                val ch = rnd.nextInt(16)
                when (rnd.nextInt(12)) {
                    0, 1, 2, 3 -> { body.u8(0x90 or ch); body.u8(rnd.nextInt(128)); body.u8(rnd.nextInt(128)) }
                    4, 5 -> { body.u8(0x80 or ch); body.u8(rnd.nextInt(128)); body.u8(0) }
                    6 -> { body.meta(0x51, listOf(byteArrayOf(0, 0, 0), byteArrayOf(-1, -1, -1), byteArrayOf(0, 0, 1), ByteArray(3) { rnd.nextInt(256).toByte() })[rnd.nextInt(4)]) }
                    7 -> body.meta(0x58, ByteArray(4) { rnd.nextInt(256).toByte() })
                    8 -> body.meta(if (rnd.nextBoolean()) 0x06 else 0x03, ByteArray(rnd.nextInt(300)) { (32 + rnd.nextInt(95)).toByte() })
                    9 -> when (rnd.nextInt(3)) {
                        0 -> { body.u8(0xB0 or ch); body.u8(rnd.nextInt(128)); body.u8(rnd.nextInt(128)) }
                        1 -> { body.u8(0xC0 or ch); body.u8(rnd.nextInt(128)) }
                        else -> { body.u8(0xE0 or ch); body.u8(rnd.nextInt(128)); body.u8(rnd.nextInt(128)) }
                    }
                    10 -> { body.u8(0xF0); val n = rnd.nextInt(40); body.vlq(n); repeat(n) { body.u8(rnd.nextInt(128)) } }
                    else -> body.meta(0x59, byteArrayOf(rnd.nextInt(256).toByte(), rnd.nextInt(2).toByte()))
                }
            }
            body.vlq(0); body.meta(0x2F, ByteArray(0))
            ascii("MTrk"); u32(body.size()); write(body.bytes())
            if (track == 0 && rnd.nextInt(8) == 0) { ascii("JUNK"); u32(3); u8(1); u8(2); u8(3) }    // an unknown chunk
        }
    }.bytes()

    /** A real song with a few bytes changed, some bytes of noise put in, or cut short. */
    private fun damaged(good: ByteArray, rnd: java.util.Random): ByteArray {
        var b = good.copyOf()
        repeat(1 + rnd.nextInt(8)) { b[rnd.nextInt(b.size)] = rnd.nextInt(256).toByte() }
        if (rnd.nextInt(3) == 0) { val at = rnd.nextInt(b.size); b = b.copyOf(at) + ByteArray(1 + rnd.nextInt(16)) { rnd.nextInt(256).toByte() } + b.copyOfRange(at, b.size) }
        if (rnd.nextInt(4) == 0) b = b.copyOf(rnd.nextInt(b.size))
        return b
    }

    /**
     * A real song plus one more track (the header's count raised to match) of what review finding 4 named: a time
     * signature exponent of 31 or 20, a zero tempo, the fastest and slowest tempos, the longest delta times, a note held
     * across all of it.
     */
    private fun withHostileTrack(good: ByteArray, rnd: java.util.Random): ByteArray {
        val body = Out()
        val hostile = listOf<(Out) -> Unit>(
            { it.vlq(0); it.meta(0x58, byteArrayOf(3, 31, 24, 8)) },
            { it.vlq(0); it.meta(0x58, byteArrayOf(-56, 20, 24, 8)) },
            { it.vlq(0); it.meta(0x51, byteArrayOf(0, 0, 0)) },
            { it.vlq(0); it.meta(0x51, byteArrayOf(0, 0, 1)) },
            { it.vlq(0); it.meta(0x51, byteArrayOf(-1, -1, -1)) },
            { it.vlq(0x0FFFFFFF); it.meta(0x06, "far".toByteArray()) },
            { it.vlq(rnd.nextInt(2_000)); it.u8(0x91); it.u8(40 + rnd.nextInt(40)); it.u8(90) },
            { it.vlq(rnd.nextInt(2_000)); it.u8(0x81); it.u8(40 + rnd.nextInt(40)); it.u8(0) },
        )
        repeat(4 + rnd.nextInt(30)) { hostile[rnd.nextInt(hostile.size)](body) }
        body.vlq(0); body.meta(0x2F, ByteArray(0))
        val out = Out(); out.write(good)
        out.ascii("MTrk"); out.u32(body.size()); out.write(body.bytes())
        val b = out.bytes()
        val count = ((b[10].toInt() and 0xFF) shl 8 or (b[11].toInt() and 0xFF)) + 1
        b[10] = (count shr 8).toByte(); b[11] = count.toByte()
        return b
    }

    private class Out {
        private val b = ByteArrayOutputStream()
        fun u8(v: Int) = b.write(v and 0xFF)
        fun u16(v: Int) { u8(v shr 8); u8(v) }
        fun u32(v: Int) { u16(v shr 16); u16(v) }
        fun ascii(s: String) = b.write(s.toByteArray(Charsets.US_ASCII))
        fun write(bytes: ByteArray) = b.write(bytes)
        fun size() = b.size()
        fun bytes(): ByteArray = b.toByteArray()
        fun vlq(v: Int) {
            var buffer = v and 0x7F
            var rest = v ushr 7
            while (rest > 0) { buffer = (buffer shl 8) or 0x80 or (rest and 0x7F); rest = rest ushr 7 }
            while (true) { u8(buffer); if (buffer and 0x80 == 0) break; buffer = buffer ushr 8 }
        }
        fun meta(type: Int, data: ByteArray) { u8(0xFF); u8(type); vlq(data.size); write(data) }
    }
}
