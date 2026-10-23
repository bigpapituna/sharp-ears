package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonsTest {
    private fun chord(n: String) = LessonItem.ChordItem(parseChord(n)!!)

    @Test fun parsesEveryChordNameItPrints() {
        for (r in 0..11) for (t in ChordType.entries) {
            val c = Chord(r, t)
            assertEquals(c, parseChord(c.name))
        }
        assertEquals(Chord(10, ChordType.MAJOR), parseChord("A#"))
        assertEquals(null, parseChord("H7"))
    }

    @Test fun leitnerBoxesAndDueDates() {
        var m = Mastery()
        val utc = java.time.ZoneOffset.UTC
        m = m.after(true, 0, utc); assertEquals(Mastery(1, DAY_MS, true), m)
        m = m.after(true, DAY_MS, utc); assertEquals(Mastery(2, 3 * DAY_MS, true), m)
        m = m.after(false, 3 * DAY_MS, utc); assertEquals(Mastery(1, 4 * DAY_MS, true), m)
        repeat(10) { m = m.after(true, 0) }
        assertEquals(5, m.box)
    }

    /**
     * Audit: a chord's next review was 24 hours on to the minute, so a chord practised at 8 in the evening was not due
     * the next morning (nor at the 18:30 reminder), and one practised the night before the clocks went forward skipped a
     * whole day. It is due at the start of its day, in the phone's time zone.
     */
    @Test fun aChordIsDueOnItsCalendarDay() {
        val zone = java.time.ZoneId.of("America/Toronto")
        fun at(s: String) = java.time.LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
        val cur = Curriculum(listOf(LessonPack("p", "p", 1, emptyList(), listOf(chord("C")))))
        fun due(m: Mastery, now: String) = cur.queue(mapOf("chord:C" to m), at(now), newLimit = 0).map { it.id }
        val evening = Mastery().after(true, at("2026-10-20T20:00"), zone)
        assertEquals(emptyList<String>(), due(evening, "2026-10-20T23:00"))
        assertEquals(listOf("chord:C"), due(evening, "2026-10-21T08:00"))
        val spring = Mastery().after(true, at("2026-03-07T23:30"), zone)
        assertEquals(listOf("chord:C"), due(spring, "2026-03-08T09:00"))
        val box3 = Mastery(2, 0, true).after(true, at("2026-12-30T21:00"), zone)
        assertEquals(emptyList<String>(), due(box3, "2027-01-02T23:59"))
        assertEquals(listOf("chord:C"), due(box3, "2027-01-03T00:00"))
    }

    /**
     * Audit: the Chords tab read every stored "box,dueAt" with toInt/toLong, and a box past 6 indexed past the Leitner
     * table on the next wrong answer, so one broken value in an imported backup crashed the tab each time it opened.
     */
    @Test fun storedMasteryNeverBreaksTheQueue() {
        assertEquals(Mastery(3, 1_700_000_000_000, true), decodeMastery("3,1700000000000"))
        for (bad in listOf("3,x", "x,5", "3,", ",", "1,2,3", "", "3")) assertNull(bad, decodeMastery(bad))
        assertEquals(5, decodeMastery("9,0")!!.box)
        assertEquals(0, decodeMastery("-4,0")!!.box)
        for (box in -3..12) for (right in listOf(true, false)) assertTrue(Mastery(box, 0, true).after(right, 0).box in 0..5)
    }

    @Test fun dueQueueAcrossSimulatedDays() {
        val basics = LessonPack("basics", "Basics", 1, emptyList(), listOf("C", "F", "G", "Am").map(::chord))
        val sevenths = LessonPack("sevenths", "Sevenths", 3, listOf("basics"), listOf("Cmaj7", "G7").map(::chord))
        val cur = Curriculum(listOf(basics, sevenths))
        val mastery = mutableMapOf<String, Mastery>()
        var now = 0L

        // Day 0: only the unlocked pack's items, new ones capped.
        assertEquals(listOf("chord:C", "chord:F", "chord:G"), cur.queue(mastery, now, newLimit = 3).map { it.id })
        assertFalse(cur.unlocked(sevenths, mastery))
        // Practice everything right every day; F always wrong.
        repeat(12) {
            for (item in cur.queue(mastery, now, newLimit = 10)) {
                mastery[item.id] = (mastery[item.id] ?: Mastery()).after(item.id != "chord:F", now, java.time.ZoneOffset.UTC)
            }
            now += DAY_MS
        }
        // Easy chords climbed and are not due every day; F stays at box 0, due daily.
        assertTrue(mastery["chord:C"]!!.box >= 4)
        assertEquals(0, mastery["chord:F"]!!.box)
        assertTrue("chord:F" in cur.queue(mastery, now).map { it.id })
        // Average box of basics: (C,G,Am high, F 0) >= 3, so sevenths unlocked and its items appeared.
        assertTrue(cur.unlocked(sevenths, mastery))
        assertTrue(mastery.containsKey("chord:G7"))
        val counts = cur.counts(basics, mastery, now)
        assertEquals(0, counts.new)
        assertEquals(3, counts.mastered)
        assertTrue(counts.due >= 1)
    }

    @Test fun labelsTheChordsOfAProgression() {
        // ii–V–I in C plus I–V–vi–IV in G: the labeler must name each bar's chord.
        val jazz = progressionSong(PROGRESSIONS[1], 0)
        assertEquals(listOf("Dm7", "G7", "Cmaj7", "Cmaj7"), labelBars(jazz).take(4).map { it.name })
        val pop = progressionSong(PROGRESSIONS[0], 7)
        assertEquals(listOf("G", "D", "Em", "C"), labelBars(pop).take(4).map { it.name })
        assertEquals(listOf("G", "D", "Em", "C"), songPack(pop).items.map { (it as LessonItem.ChordItem).chord.name })
    }

    @Test fun chordTrackFollowsSongTimeAndShowsTheNextChange() {
        val c = Chord(0, ChordType.MAJOR); val f = Chord(5, ChordType.MAJOR); val g = Chord(7, ChordType.MAJOR)
        val t = ChordTrack(listOf(c, c, null, f, g), barMs = 1000.0)
        assertEquals(c to f, t.at(0.0))
        assertEquals("a bar without notes keeps the chord before it", c to f, t.at(2500.0))
        assertEquals(f to g, t.at(3000.0))
        assertEquals(g to null, t.at(9000.0))
        assertTrue(ChordTrack(listOf(null, null), 1000.0).isEmpty)
    }

    /**
     * Security review finding 4 (2.8.3): the labeler is bounded by the song's caps, not by what a file says. A 20-minute
     * song of 20,000 notes in 1/32 at 300 bpm (48,000 bars of 25 ms, labelled up to MAX_LABEL_BARS), half of them held to
     * the end, labels in well under a second; the per-bar filter it replaced took bars times notes, minutes at launch.
     * Its metronome clicks one beat every 25 ms, never a burst.
     */
    @Test fun aTwentyMinuteSongLabelsInWellUnderASecond() {
        val rnd = java.util.Random(4)
        val gap = SmfParser.MAX_SONG_MS / SmfParser.MAX_NOTES
        val notes = List(SmfParser.MAX_NOTES) { i ->
            val start = i * gap
            Note(36 + rnd.nextInt(60), start, if (i % 2 == 0) SmfParser.MAX_SONG_MS - start else 40L, if (i % 2 == 0) Hand.LEFT else Hand.RIGHT, 80)
        }
        val song = Song("long", "long", 300.0, 1 to 32, notes, groupOnsets(notes), emptyList(), SmfParser.MAX_SONG_MS)
        val started = System.nanoTime()
        val bars = labelBarsAligned(song)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("20 minutes, ${notes.size} notes, ${bars.size} bars labelled in $ms ms")
        assertEquals(MAX_LABEL_BARS, bars.size)
        assertTrue(bars.all { it != null })
        assertTrue("labelled in $ms ms", ms < 500)
        val m = Metronome(song.bpm, song.timeSignature)
        assertEquals(25.0, m.beatMs, 1e-9)
        var clicks = 0; var t = 0.0
        while (t < SmfParser.MAX_SONG_MS) {
            val next = minOf(t + 16.7, SmfParser.MAX_SONG_MS.toDouble())
            clicks += m.clicks(t, next).also { assertTrue(it.size <= 1) }.size
            t = next
        }
        assertEquals("one click a beat, none twice", (SmfParser.MAX_SONG_MS / 25).toInt(), clicks)
    }

    /** A bar of no length, of no tempo or of a damaged time signature labels nothing instead of dividing by it. */
    @Test fun aSongWithNoRealBarIsNotLabelled() {
        val notes = parseLine("C4+E4+G4:4", Hand.RIGHT, 500.0)
        fun song(bpm: Double, sig: Pair<Int, Int>) = Song("s", "s", bpm, sig, notes, groupOnsets(notes), emptyList(), 2_000)
        for (s in listOf(song(120.0, 4 to 0), song(120.0, 0 to 4), song(120.0, -3 to 4), song(0.0, 4 to 4), song(Double.NaN, 4 to 4),
                         song(120.0, 1 to Int.MIN_VALUE), song(120.0, 1 to 1_048_576), song(1e12, 4 to 4)))
            assertEquals("${s.bpm} ${s.timeSignature}", emptyList<Chord?>(), labelBarsAligned(s))
        assertEquals(listOf("C"), labelBars(song(120.0, 4 to 4)).map { it.name })
    }

    /**
     * The sweep gives what clipping every note to every bar gave (the 2.8.2 code, kept here as the reference): every
     * bundled song, every progression in every key, and seeded random songs with held notes and odd meters.
     */
    @Test fun theSweepLabelsAsEveryBarFilteredOnItsOwnDid() {
        val songs = java.io.File("src/main/assets/songs").listFiles { f -> f.extension == "mid" }!!.sortedBy { it.name }
            .map { SmfParser.parse(it.readBytes(), it.nameWithoutExtension, "x") }.toMutableList()
        for (p in PROGRESSIONS) for (key in 0..11) songs += progressionSong(p, key)
        val rnd = java.util.Random(11)
        repeat(60) { n ->
            val sig = listOf(4 to 4, 3 to 4, 6 to 8, 2 to 2, 5 to 4, 7 to 8, 1 to 32, 12 to 16)[n % 8]
            val bpm = 40.0 + rnd.nextInt(160) + rnd.nextDouble()
            val barMs = sig.first * (4.0 / sig.second) * 60_000 / bpm
            val notes = List(1 + rnd.nextInt(300)) {
                val start = if (rnd.nextInt(6) == 0) (rnd.nextInt(40) * barMs).toLong() else rnd.nextInt(120_000).toLong()
                val length = when (rnd.nextInt(5)) { 0 -> (barMs * (1 + rnd.nextInt(12))).toLong(); 1 -> 1L + rnd.nextInt(30); else -> 1L + rnd.nextInt(3_000) }
                Note(21 + rnd.nextInt(88), start, length, Hand.RIGHT, 80)
            }.sortedBy { it.startMs }
            songs += Song("r$n", "r", bpm, sig, notes, groupOnsets(notes), emptyList(), notes.maxOf { it.startMs + it.durationMs })
        }
        for (s in songs) {
            assertEquals(s.id, perBar(s), labelBarsAligned(s))
            assertEquals(s.id, perBar(s.transposed(5)), labelBarsAligned(s.transposed(5)))
        }
    }

    /** labelBarsAligned as it was through 2.8.2: every bar filters every note. */
    private fun perBar(song: Song): List<Chord?> {
        val barMs = barMs(song)
        if (barMs <= 0 || song.notes.isEmpty()) return emptyList()
        val bars = (song.durationMs / barMs).toInt() + 1
        return (0 until bars).map { b ->
            val from = b * barMs; val to = from + barMs
            labelBar(song.notes.filter { it.startMs < to && it.startMs + it.durationMs > from }.map { n ->
                val s = maxOf(n.startMs.toDouble(), from); val e = minOf((n.startMs + n.durationMs).toDouble(), to)
                n.copy(durationMs = (e - s).toLong())
            }.filter { it.durationMs >= barMs * 0.05 })
        }
    }

    @Test fun alignedBarsKeepEmptyBarsInPlace() {
        val notes = parseLine("C4+E4+G4:4 r:4 F4+A4+C5:4", Hand.RIGHT, 500.0)
        val song = Song("s", "s", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 6000)
        val bars = labelBarsAligned(song)
        assertEquals(listOf("C", null, "F"), bars.take(3).map { it?.name })
        assertEquals(2000.0, barMs(song), 1e-9)
        assertEquals(listOf("C", "F"), labelBars(song).map { it.name })
    }
}
