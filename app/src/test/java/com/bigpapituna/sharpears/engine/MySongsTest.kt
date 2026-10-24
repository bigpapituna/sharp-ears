package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.MySongs.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * My songs (2.8.1): a learner's own MIDI file made into an easy song (engine/MySongs.kt). The files here are built by the
 * test from made-up notes (no real song's notes are in the repo), plus the app's own public-domain Twinkle.
 *
 * The made-up file, 480 ticks a quarter, 140 bpm, 4/4: a conductor track named "Test Tune"; a tune high up (C6 to G6) that
 * starts on beat 2 of bar 3 with a grace note, a late note, a chord and a note under a held one; a bass far down (C1 and G1
 * quarters); a pad of high chords that never moves; drums on channel 10 from bar 1.
 */
class MySongsTest {
    private data class N(val key: Int, val at: Int, val len: Int, val ch: Int = 0, val vel: Int = 90)
    private class T(val name: String? = null, val notes: List<N> = emptyList(), val bpm: Int? = null, val sig: Pair<Int, Int>? = null)

    private val tune = listOf(
        N(86, 4300, 20),                       // a grace note: shorter than a 16th, goes
        N(84, 4320, 230),                      // C6 on beat 2 of bar 3
        N(86, 4570, 220),                      // D6, 10 ticks late: on the 16th at 4560
        N(84, 4800, 240), N(88, 4800, 240),    // a chord: its top, E6
        N(91, 5040, 960),                      // G6 held for a half note
        N(86, 5280, 240),                      // D6 under the held G6: accompaniment, goes
        N(88, 6000, 480), N(84, 6480, 480), N(86, 6960, 240), N(84, 7200, 960),
    )
    private val bass = (0 until 8).map { N(if (it % 2 == 0) 24 else 31, 3840 + it * 480, 400, ch = 1) }
    private val pad = (0 until 4).flatMap { i -> listOf(96, 100, 103).map { N(it, 3840 + i * 1920, 1900, ch = 2) } }
    private val drums = (0 until 32).map { N(listOf(36, 38, 42)[it % 3], it * 240, 100, ch = 9) }

    private fun file(bpm: Int = 140, tuneName: String = "Synth", padName: String = "Pad") = smf(
        T("Test Tune", bpm = bpm, sig = 4 to 4), T(tuneName, tune), T("Bass", bass), T(padName, pad), T("Drums", drums))

    // ---- the easy song ----

    @Test fun theTuneIsKeptOnTheGridTwoOctavesDownWithTheIntroCut() {
        val song = MySongs.song(file(), "test-tune-000000", "Test Tune", bothHands = false)
        assertTrue("melody only", song.notes.all { it.hand == Hand.RIGHT })
        // C6 D6 E6 G6 E6 C6 D6 C6 at middle C (two octaves down, nearest the built-in songs' middle): the grace note, the
        // chord's lower note and the note under the held G6 are gone.
        assertEquals(listOf(60, 62, 64, 67, 64, 60, 62, 60), song.notes.map { it.midi })
        // 140 bpm is slowed to 100 (a 16th is 150 ms); two bars of drums are cut, so beat 2 of the first bar is 600 ms.
        assertEquals(100.0, song.bpm, 0.0)
        assertEquals(4 to 4, song.timeSignature)
        assertEquals(listOf(600L, 900L, 1200L, 1500L, 2700L, 3300L, 3900L, 4200L), song.notes.map { it.startMs })
        assertEquals(listOf(300L, 300L, 300L, 1200L, 600L, 600L, 300L, 1200L), song.notes.map { it.durationMs })
        assertEquals(5400L, song.durationMs)
        assertTrue("one note at a time", song.notes.zipWithNext().all { (a, b) -> a.startMs + a.durationMs <= b.startMs })
        assertEquals(song.notes.size, song.groups.size)
        assertTrue(song.sections.isEmpty())
    }

    @Test fun bothHandsAddsTheBassLineAsTheLeftHandInItsRange() {
        val song = MySongs.song(file(), "t", "t", bothHands = true)
        val right = song.notes.filter { it.hand == Hand.RIGHT }
        val left = song.notes.filter { it.hand == Hand.LEFT }
        assertEquals(MySongs.song(file(), "t", "t", bothHands = false).notes, right)
        // C1 and G1 come up two octaves, to C3 and G3; the pad and the drums are never a hand.
        assertEquals(List(8) { if (it % 2 == 0) 48 else 55 }, left.map { it.midi })
        assertEquals((0 until 8).map { it * 600L }, left.map { it.startMs })
        assertTrue(left.all { it.midi in MySongs.LEFT_RANGE } && right.all { it.midi in MySongs.RIGHT_RANGE })
        // A left note and a right note on the same 16th are one group (both hands together).
        assertEquals(listOf(55, 60), song.groups.first { it.startMs == 600L }.notes.map { it.midi }.sorted())
    }

    @Test fun aSlowFileKeepsItsTempo() {
        assertEquals(80.0, MySongs.song(file(bpm = 80), "t", "t", bothHands = false).bpm, 0.001)
        assertEquals(100.0, MySongs.song(file(bpm = 100), "t", "t", bothHands = false).bpm, 0.001)
    }

    @Test fun theScanSuggestsTheTitleAndCountsBothLines() {
        val scan = MySongs.scan(file(), "something.mid")
        assertEquals("Test Tune", scan.title)
        assertEquals(8, scan.melodyNotes)
        assertEquals(8, scan.leftNotes)
        assertTrue(scan.hasLeft)
    }

    /** A track named for the tune wins outright, even over a line that moves more (karaoke files name it "Melody"). */
    @Test fun aTrackNamedMelodyIsTheTune() {
        val song = MySongs.song(file(padName = "Melody"), "t", "t", bothHands = false)
        assertEquals(List(4) { 67 }, song.notes.map { it.midi })                  // the pad's top G7, three octaves down
        // "Voice Oohs" is a choir, not the tune: the moving line still wins.
        assertEquals(60, MySongs.song(file(padName = "Voice Oohs"), "t", "t", false).notes.first().midi)
    }

    /** One polyphonic track (a piano part on one channel): the top note of each chord, and no left hand to offer. */
    @Test fun oneTrackOfChordsKeepsItsTopNotes() {
        val chords = listOf(listOf(60, 64, 67), listOf(62, 65, 69), listOf(64, 67, 71), listOf(60, 64, 72), listOf(59, 62, 67))
            .flatMapIndexed { i, keys -> keys.map { N(it, i * 480, 470) } }
        val bytes = smf(T("Chords", chords, bpm = 90))
        val scan = MySongs.scan(bytes, null)
        assertFalse(scan.hasLeft)
        assertEquals("Chords", scan.title)
        val song = MySongs.song(bytes, "t", "t", bothHands = true)
        assertEquals(listOf(67, 69, 71, 72, 67), song.notes.map { it.midi })
        assertEquals(90.0, song.bpm, 0.001)
    }

    /** The app's own Twinkle comes through whole: its right hand is the tune, its left hand Both hands, on the same beats. */
    @Test fun aBuiltInSongsFileComesThroughWhole() {
        val bytes = File("src/main/assets/songs/twinkle.mid").readBytes()
        val built = SmfParser.parse(bytes, "twinkle", "Twinkle")
        val easy = MySongs.song(bytes, "t", "t", bothHands = true)
        for (h in Hand.entries)
            assertEquals("$h", built.notes.filter { it.hand == h }.map { it.midi to it.startMs }, easy.notes.filter { it.hand == h }.map { it.midi to it.startMs })
        assertEquals(built.bpm, easy.bpm, 0.001)
    }

    /**
     * Security review finding 4 (2.8.3): the parser stops a file at 20 minutes of its own tempo, but My songs slows a fast
     * file to 100 bpm. A 300 bpm file of 19 minutes would last 57; the easy song stops at 20 minutes too.
     */
    @Test fun aLongFastFileStopsAtTwentyMinutes() {
        val quarters = (0 until 5_700).map { N(60 + it % 5 * 2, it * 480, 400) }
        val bytes = smf(T("Fast", quarters, bpm = 300, sig = 4 to 4))
        val song = MySongs.song(bytes, "t", "t", bothHands = false)
        assertEquals(100.0, song.bpm, 0.0)
        assertEquals(2_000, song.notes.size)                       // 20 minutes of quarters at 600 ms
        assertTrue(song.durationMs <= SmfParser.MAX_SONG_MS)
        assertEquals(2_000, MySongs.scan(bytes, null).melodyNotes)
    }

    // ---- refused files ----

    @Test fun filesThatAreNoSongAreRefusedWithAKindLine() {
        fun refused(bytes: ByteArray, name: String? = "x.mid"): Problem =
            try { MySongs.scan(bytes, name); fail("accepted"); error("") } catch (e: MySongs.Refused) { e.problem }
        // 2.8.4, QA D10: a file named .mid that does not read is damaged or cut short, not "a file that ends in .mid".
        assertEquals(Problem.DAMAGED, refused("hello, this is text".toByteArray()))
        assertEquals(Problem.DAMAGED, refused(file().copyOf(40)))
        assertEquals(Problem.DAMAGED, refused(file().copyOf(300), "Song.MIDI"))
        assertEquals("a MIDI header with no name", Problem.DAMAGED, refused(file().copyOf(10), null))
        assertEquals(Problem.NOT_MIDI, refused("hello, this is text".toByteArray(), "notes.txt"))
        assertEquals(Problem.NOT_MIDI, refused("hello, this is text".toByteArray(), null))
        assertEquals("It may be cut short or not a MIDI song.", Problem.DAMAGED.line)
        assertEquals(Problem.NO_NOTES, refused(smf(T("Empty", bpm = 120))))
        assertEquals(Problem.DRUMS_ONLY, refused(smf(T("Beat", drums))))
        assertEquals(Problem.TOO_BIG, refused(file() + ByteArray(MySongs.MAX_BYTES)))
        // Three notes are not a tune.
        assertEquals(Problem.NO_NOTES, refused(smf(T("Short", tune.take(3)))))
    }

    // ---- title and id ----

    @Test fun theTitleComesFromTheSequenceNameThenTheFileName() {
        assertEquals("Test Tune", MySongs.titleFrom("Test Tune", "other.mid"))
        assertEquals("Some Artist, Some Tune", MySongs.titleFrom("Untitled", "Some_Artist_-_Some_Tune.mid"))
        assertEquals("Some Tune", MySongs.titleFrom("Track 1", "Some Tune.MIDI"))
        assertEquals("Some Tune", MySongs.titleFrom("(C) 1999 by Someone", "Some Tune.mid"))
        assertEquals("Some Tune", MySongs.titleFrom("www.example.com", "Some Tune.kar"))
        assertEquals(MySongs.DEFAULT_TITLE, MySongs.titleFrom(null, "12345.mid"))
        assertEquals(MySongs.DEFAULT_TITLE, MySongs.titleFrom("Sequence", null))
        assertEquals(60, MySongs.titleFrom("A".repeat(100), null).length)
        // A slug of a file name reads as words, with capitals when it had none.
        assertEquals("Jingle Bells", MySongs.titleFrom(null, "jingle-bells.mid"))
        assertEquals("Jingle Bells", MySongs.titleFrom("Right", "jingle_bells.mid"))
    }

    /** The first track's name is the song's only when that track is the conductor or the only one; a part's name is not. */
    @Test fun aPartsNameIsNoTitle() {
        assertEquals("Jingle Bells", MySongs.scan(smf(T("Piano part", tune), T("Low", bass)), "jingle-bells.mid").title)
        assertEquals("Test Tune", MySongs.scan(file(), "jingle-bells.mid").title)
    }

    @Test fun theIdIsTheTitlesLettersAndAHashOfTheFile() {
        val a = file(); val b = file(bpm = 90)
        assertEquals(MySongs.idFor("Test Tune", a), MySongs.idFor("Test Tune", a.copyOf()))
        assertNotEquals(MySongs.idFor("Test Tune", a), MySongs.idFor("Test Tune", b))
        assertTrue(Regex("test-tune-[0-9a-f]{6}").matches(MySongs.idFor("Test Tune", a)))
        assertTrue(Regex("fur-elise-[0-9a-f]{6}").matches(MySongs.idFor("Für Élise", a)))
        assertTrue(Regex("song-[0-9a-f]{6}").matches(MySongs.idFor("月光", a)))
        assertTrue(MySongs.idFor("x".repeat(200), a).length <= 48)
    }

    // ---- the built-in songs set the ranges and the tempo ----

    @Test fun theRangesAndTheTempoAreTheBuiltInSongs() {
        val index = File("src/main/assets/songs/index.json").readText()
        @Suppress("UNCHECKED_CAST")
        val easy = ((MiniJson.read(index) as Map<String, Any?>)["songs"] as List<Map<String, Any?>>).filter { it["difficulty"] == "easy" }
        assertTrue(easy.size >= 20)
        for (s in easy) {
            val song = SmfParser.parse(File("src/main/assets/songs/${s["file"]}").readBytes(), s["id"] as String, "x")
            assertTrue("${s["id"]} right hand", song.notes.filter { it.hand == Hand.RIGHT }.all { it.midi in MySongs.RIGHT_RANGE })
            assertTrue("${s["id"]} left hand", song.notes.filter { it.hand == Hand.LEFT }.all { it.midi in MySongs.LEFT_RANGE })
            assertTrue("${s["id"]} at ${song.bpm}", song.bpm <= 120.0)
        }
        for (id in listOf("twinkle", "ode-to-joy", "mary-had-a-little-lamb"))
            assertEquals(id, MySongs.MAX_BPM, SmfParser.parse(File("src/main/assets/songs/$id.mid").readBytes(), id, id).bpm, 0.001)
    }

    // ---- the words ----

    @Test fun theCopyKeepsTheRules() {
        val w = MySongs.Words
        val lines = listOf(w.EMPTY, w.NOTE, w.EASY_LINE, w.BOTH_LINE, w.DELETE_LINE, w.SAVE_FAILED, w.notes(1), w.notes(214)) + Problem.entries.map { it.line }
        val labels = listOf(w.SECTION, w.ADD, w.DIALOG_TITLE, w.TITLE_LABEL, w.MELODY_ONLY, w.BOTH_HANDS, w.ADD_BUTTON, w.CANCEL, w.REFUSED_TITLE)
        for (l in lines + labels) {
            assertFalse(l, Regex("[—–·>]| - ").containsMatchIn(l))
            assertTrue("sentence case: $l", l.first().isUpperCase())
        }
        for (l in lines) assertTrue(l, l.endsWith("."))
        for (l in labels) assertFalse(l, l.endsWith("."))
        assertEquals("My songs", w.SECTION)
        assertEquals("Add a song from a file", w.ADD)
    }

    // ---- a tiny SMF writer (format 1) ----

    private fun smf(vararg tracks: T, ppq: Int = 480): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray()); out.u32(6); out.u16(1); out.u16(tracks.size); out.u16(ppq)
        for (t in tracks) {
            val events = mutableListOf<Triple<Int, Int, ByteArray>>()        // tick, order at that tick (meta, off, on), bytes
            t.name?.let { events += Triple(0, 0, byteArrayOf(0xFF.toByte(), 0x03, it.length.toByte()) + it.toByteArray(Charsets.ISO_8859_1)) }
            t.bpm?.let { val us = 60_000_000 / it; events += Triple(0, 0, byteArrayOf(0xFF.toByte(), 0x51, 3, (us shr 16).toByte(), (us shr 8).toByte(), us.toByte())) }
            t.sig?.let { (n, d) -> events += Triple(0, 0, byteArrayOf(0xFF.toByte(), 0x58, 4, n.toByte(), Integer.numberOfTrailingZeros(d).toByte(), 24, 8)) }
            for (n in t.notes) {
                events += Triple(n.at, 2, byteArrayOf((0x90 or n.ch).toByte(), n.key.toByte(), n.vel.toByte()))
                events += Triple(n.at + n.len, 1, byteArrayOf((0x80 or n.ch).toByte(), n.key.toByte(), 0))
            }
            val body = ByteArrayOutputStream()
            var last = 0
            for ((tick, _, b) in events.sortedWith(compareBy({ it.first }, { it.second }))) { body.vlq(tick - last); body.write(b); last = tick }
            body.write(byteArrayOf(0, 0xFF.toByte(), 0x2F, 0))
            out.write("MTrk".toByteArray()); out.u32(body.size()); out.write(body.toByteArray())
        }
        return out.toByteArray()
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
