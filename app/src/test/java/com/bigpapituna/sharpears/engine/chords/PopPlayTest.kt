package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopPlayTest {
    private fun maj(r: Int) = PopChord(r, ChordType.MAJOR)
    private fun min(r: Int) = PopChord(r, ChordType.MINOR)

    /** C for a bar, F and G half a bar each, then a chorus bar of Am. 120 bpm, so a beat is 500 ms. */
    private fun song(beatsPerBar: Int = 4) = PopSong(
        id = "t", title = "T", artist = "A", year = 2000, styles = listOf(PopSong.Style.POP), key = 0, minor = false, bpm = 120,
        beatsPerBar = beatsPerBar,
        sections = listOf(
            PopSection("Verse", listOf(PopBar(maj(0)), PopBar(maj(5), maj(7)))),
            PopSection("Chorus", listOf(PopBar(min(9)))),
        ),
        pattern = Pattern.WHOLE, difficulty = 1, confidence = PopSong.Confidence.CHECKED,
    )

    private fun play(p: Pattern, key: Int = 0, bpm: Int = 120) = PopPlay.toSong(song(), p, key, bpm)
    private fun right(s: Song) = s.notes.filter { it.hand == Hand.RIGHT }
    private fun left(s: Song) = s.notes.filter { it.hand == Hand.LEFT }
    private fun strikeTimes(s: Song) = right(s).map { it.startMs }.distinct()

    @Test fun wholeIsOneStrikePerChord() {
        val s = play(Pattern.WHOLE)
        assertEquals(listOf(0L, 2000L, 3000L, 4000L), strikeTimes(s))
        assertEquals(12, right(s).size)
        assertEquals(listOf(48, 53, 55, 57), left(s).map { it.midi })
        assertEquals(6000L, s.durationMs)
        assertEquals(120.0, s.bpm, 0.0)
        assertEquals(4 to 4, s.timeSignature)
    }

    @Test fun chordsAreWholeGroupsOfTheirNotes() {
        val s = play(Pattern.WHOLE)
        assertEquals(listOf(60, 64, 67), s.groups.first().notes.filter { it.hand == Hand.RIGHT }.map { it.midi })
        assertEquals(listOf(48), s.groups.first().notes.filter { it.hand == Hand.LEFT }.map { it.midi })
        assertEquals(4, s.groups.size)
    }

    @Test fun halvesStrikeOnOneAndThreeAndOnEveryChordChange() {
        val s = play(Pattern.HALVES)
        // C: beats 1 and 3; F: its own two beats make one strike; G: the same; Am: beats 1 and 3.
        assertEquals(listOf(0L, 1000L, 2000L, 3000L, 4000L, 5000L), strikeTimes(s))
        assertEquals(listOf(0L, 1000L, 2000L, 3000L, 4000L, 5000L), left(s).map { it.startMs })
    }

    @Test fun quartersStrikeEveryBeatWithTheBassOnOneAndThree() {
        val s = play(Pattern.QUARTERS)
        assertEquals((0..11).map { it * 500L }, strikeTimes(s))
        assertEquals(36, right(s).size)
        assertEquals(listOf(0L, 1000L, 2000L, 3000L, 4000L, 5000L), left(s).map { it.startMs })
    }

    @Test fun brokenRocksRootFifthThirdFifthInTheLeftHandUnderOneHeldChord() {
        val s = play(Pattern.BROKEN)
        assertEquals(listOf(0L, 2000L, 3000L, 4000L), strikeTimes(s))
        // The C bar: C3 G3 E3 G3, a note per beat.
        assertEquals(listOf(48, 55, 52, 55), left(s).take(4).map { it.midi })
        assertEquals(listOf(0L, 500L, 1000L, 1500L), left(s).take(4).map { it.startMs })
        // F then G take two beats each and start again from their roots: F A... root F3, fifth C4; G3, D4.
        assertEquals(listOf(53, 60, 55, 62), left(s).drop(4).take(4).map { it.midi })
    }

    @Test fun balladRollsTheChordUpAndBackInEighths() {
        val s = play(Pattern.BALLAD_ARPEGGIO)
        val bar = right(s).take(8)
        assertEquals(listOf(60, 64, 67, 64, 60, 64, 67, 64), bar.map { it.midi })
        assertEquals((0..7).map { it * 250L }, bar.map { it.startMs })
        // One note at a time, so every right-hand group is a single note; the bass is held.
        assertTrue(right(s).groupBy { it.startMs }.values.all { it.size == 1 })
        assertEquals(listOf(48, 53, 55, 57), left(s).map { it.midi })
    }

    @Test fun shuffleIsLongShortOnEveryBeat() {
        val s = play(Pattern.SHUFFLE)
        assertEquals(listOf(0L, 333L, 500L, 833L, 1000L, 1333L, 1500L, 1833L), strikeTimes(s).take(8))
        val first = right(s).first { it.startMs == 0L }
        assertTrue(first.durationMs in 250L..333L)
    }

    @Test fun eighthsRockIsEightChordsAndFourRootsABar() {
        val s = play(Pattern.EIGHTHS_ROCK)
        assertEquals((0..7).map { it * 250L }, strikeTimes(s).take(8))
        assertEquals(listOf(0L, 500L, 1000L, 1500L), left(s).take(4).map { it.startMs })
    }

    @Test fun everyNoteEndsBeforeTheSongDoesAndNothingIsTooShort() {
        for (p in Pattern.entries) {
            val s = play(p)
            assertTrue("$p", s.notes.all { it.startMs + it.durationMs <= s.durationMs })
            assertTrue("$p", s.notes.all { it.durationMs >= 80 })
            assertTrue("$p", s.notes.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
        }
    }

    @Test fun sectionsAreCarriedWithTheirTimes() {
        val s = play(Pattern.WHOLE)
        assertEquals(listOf("Verse", "Chorus"), s.sections.map { it.name })
        assertEquals(0L to 4000L, s.sections[0].startMs to s.sections[0].endMs)
        assertEquals(4000L to 6000L, s.sections[1].startMs to s.sections[1].endMs)
    }

    @Test fun repeatedSectionNamesStayTheirOwnSections() {
        val base = song()
        val twice = base.copy(sections = base.sections + base.sections[1])
        val s = PopPlay.toSong(twice)
        assertEquals(listOf("Verse", "Chorus", "Chorus"), s.sections.map { it.name })
        assertEquals(6000L, s.sections[2].startMs)
        assertEquals(8000L, s.durationMs)
    }

    @Test fun transposingMovesEveryChordAndTheKeysSpelling() {
        val s = play(Pattern.WHOLE, key = 2)
        assertEquals(listOf(62, 66, 69), s.groups.first().notes.filter { it.hand == Hand.RIGHT }.map { it.midi })
        assertEquals(listOf(50, 55, 57, 59), left(s).map { it.midi })
        assertTrue(s.id.contains("-2-"))
        assertTrue(play(Pattern.WHOLE, key = 5).preferFlats)       // moved to F, the black keys read as flats
        assertTrue(!play(Pattern.WHOLE, key = 7).preferFlats)
    }

    @Test fun transposingKeepsTheRhythmExactly() {
        for (p in Pattern.entries) {
            assertEquals("$p", play(p).notes.map { it.startMs to it.durationMs }, play(p, key = 3).notes.map { it.startMs to it.durationMs })
        }
    }

    @Test fun shiftAndNearestStep() {
        assertEquals(2, PopPlay.shift(song(), 2))
        assertEquals(10, PopPlay.shift(song(), 10))
        assertEquals(-2, PopPlay.nearest(10))
        assertEquals(-6, PopPlay.nearest(6))
        assertEquals(5, PopPlay.nearest(5))
        assertEquals(0, PopPlay.nearest(0))
        assertEquals(0, PopPlay.nearest(12))
    }

    @Test fun chordNamesFollowTheKeysSpelling() {
        assertEquals("Bb", PopPlay.name(maj(10), flats = true))
        assertEquals("Am7", PopPlay.name(PopChord(9, ChordType.MIN7), false))
        assertEquals("C/E", PopPlay.name(PopChord(0, ChordType.MAJOR, bass = 4), false))
        assertEquals("Db/F", PopPlay.name(PopChord(1, ChordType.MAJOR, bass = 5), true))
        assertEquals("F", PopPlay.keyName(5, false))
        assertEquals("Dm", PopPlay.keyName(2, true))
        assertEquals("Gm", PopPlay.keyName(7, true))
        assertTrue(PopPlay.flats(2, minor = true))       // D minor reads like F major
        assertTrue(!PopPlay.flats(2, minor = false))
        assertTrue(PopPlay.flats(10, false))
    }

    @Test fun movingAChordMovesItsSlashBassToo() {
        val c = PopChord(0, ChordType.MAJOR, bass = 4)
        assertEquals(PopChord(2, ChordType.MAJOR, bass = 6), PopPlay.moved(c, 2))
        assertEquals(PopChord(11, ChordType.MAJOR, bass = 3), PopPlay.moved(c, -1))
        assertEquals(c, PopPlay.moved(c, 12))
    }

    @Test fun aSlashChordPutsItsBassInTheLeftHand() {
        val slash = song().copy(sections = listOf(PopSection("Verse", listOf(PopBar(PopChord(0, ChordType.MAJOR, bass = 4))))))
        val s = PopPlay.toSong(slash, Pattern.WHOLE)
        assertEquals(listOf(52), left(s).map { it.midi })
        assertEquals(listOf(60, 64, 67), right(s).map { it.midi })
        assertEquals(listOf(54), left(PopPlay.toSong(slash, Pattern.WHOLE, key = 2)).map { it.midi })
    }

    @Test fun waltzTimeBarsHoldThreeBeats() {
        val three = song(beatsPerBar = 3).copy(sections = listOf(PopSection("Verse", listOf(
            PopBar(listOf(PopBeat(maj(0), 3))), PopBar(listOf(PopBeat(maj(5), 3)))))))
        val s = PopPlay.toSong(three, Pattern.QUARTERS)
        assertEquals(3 to 4, s.timeSignature)
        assertEquals(3000L, s.durationMs)
        assertEquals(listOf(0L, 500L, 1000L, 1500L, 2000L, 2500L), strikeTimes(s))
    }

    @Test fun fingersAreOnePerRightHandNote() {
        for (p in Pattern.entries) {
            val s = play(p)
            assertEquals("$p", right(s).size, s.fingering!!.size)
            assertTrue("$p", s.fingering!!.all { it in 1..5 })
        }
        // C major root position is the dictionary's 1 3 5.
        assertEquals(listOf(1, 3, 5), play(Pattern.WHOLE).fingering!!.take(3))
    }

    @Test fun voiceLeadingKeepsTheRightHandSmooth() {
        val chords = play(Pattern.WHOLE).groups.map { g -> g.notes.filter { it.hand == Hand.RIGHT }.map { it.midi } }
        // F after C is the second inversion C F A, not a jump up to 65.
        assertEquals(listOf(60, 65, 69), chords[1])
        assertTrue(chords.all { it.min() >= 52 && it.max() <= 81 })
    }

    @Test fun barsListEachBarsChordsWithTheirBeats() {
        val bars = PopPlay.bars(song())
        assertEquals(3, bars.size)
        assertEquals(listOf(0, 0, 1), bars.map { it.section })
        assertEquals(listOf(0, 1, 0), bars.map { it.indexInSection })
        assertEquals(listOf(4), bars[0].slots.map { it.beats })
        assertEquals(listOf(2, 2), bars[1].slots.map { it.beats })
        assertEquals(listOf(0, 2), bars[1].slots.map { it.beatInBar })
        assertEquals(listOf(4, 6), bars[1].slots.map { it.startBeat })
        assertEquals(listOf(5, 7), bars[1].slots.map { it.chord.root })
        assertEquals(listOf(7, 9), PopPlay.bars(song(), by = 2)[1].slots.map { it.chord.root })
        assertEquals(12, PopPlay.beats(song()))
        assertEquals(1, PopPlay.barAt(song(), 2100.0))
        assertEquals(2, PopPlay.barAt(song(), 99_999.0))
        assertEquals(0, PopPlay.barAt(song(), -500.0))
    }

    @Test fun tempoChangesTheTimesNotTheNotes() {
        val a = play(Pattern.QUARTERS, bpm = 60)
        assertEquals(12_000L, a.durationMs)
        assertEquals(60.0, a.bpm, 0.0)
        assertEquals(play(Pattern.QUARTERS).notes.map { it.midi }, a.notes.map { it.midi })
    }

    @Test fun everyPatternHasAFeelLineAndAName() {
        for (p in Pattern.entries) {
            assertTrue(PopPlay.feel(p).length in 20..100)
            assertTrue(PopPlay.patternName(p).isNotBlank())
        }
    }

    // --- the band ---

    private fun bed(p: Pattern, key: Int = 0, bpm: Int = 120) = PopPlay.bandBed(song(), p, key, bpm)

    @Test fun theBandKicksOnOneAndThreeAndSnaresOnTwoAndFour() {
        val first = bed(Pattern.HALVES).drums.filter { it.atMs < 2000 }
        assertEquals(listOf(0L, 1000L), first.filter { it.kit == PopPlay.Kit.KICK }.map { it.atMs })
        assertEquals(listOf(500L, 1500L), first.filter { it.kit == PopPlay.Kit.SNARE }.map { it.atMs })
        assertEquals(listOf(0L, 500L, 1000L, 1500L), first.filter { it.kit == PopPlay.Kit.HAT }.map { it.atMs })
    }

    @Test fun theBassPlaysTheRootsAnOctaveUnderTheLeftHand() {
        val b = bed(Pattern.WHOLE)
        assertEquals(listOf(36, 41, 43, 45), b.bass.map { it.midi })
        assertEquals(listOf(0L, 2000L, 3000L, 4000L), b.bass.map { it.atMs })
        val ons = b.cues.filterIsInstance<BandCue.BassOn>()
        val offs = b.cues.filterIsInstance<BandCue.BassOff>()
        assertEquals(ons.size, offs.size)
        // Every note it starts it also ends, and a note ends before the next one starts.
        ons.forEach { on -> assertTrue(offs.any { it.midi == on.midi && it.atMs > on.atMs }) }
        assertTrue(b.cues.zipWithNext().all { (a, c) -> a.atMs <= c.atMs })
    }

    @Test fun halvesAndQuartersBassAlternateRootAndFifth() {
        assertEquals(listOf(36, 43, 36, 43), bed(Pattern.QUARTERS).bass.take(4).map { it.midi })
        assertEquals(listOf(36, 43), bed(Pattern.HALVES).bass.take(2).map { it.midi })
    }

    @Test fun shuffleBassWalksRootThirdFifthSixth() {
        assertEquals(listOf(36, 40, 43, 45), bed(Pattern.SHUFFLE).bass.take(4).map { it.midi })
    }

    @Test fun rockBassPumpsEighths() {
        val b = bed(Pattern.EIGHTHS_ROCK).bass.take(8)
        assertEquals((0..7).map { it * 250L }, b.map { it.atMs })
        assertTrue(b.all { it.midi == 36 })
    }

    @Test fun aSlashChordsBassIsTheBandsRoot() {
        val s = song().copy(sections = listOf(PopSection("Verse", listOf(PopBar(PopChord(0, ChordType.MAJOR, bass = 4))))))
        assertEquals(listOf(40), PopPlay.bandBed(s, Pattern.WHOLE).bass.map { it.midi })
    }

    @Test fun bedFollowsTheKeyAndTheTempo() {
        val b = bed(Pattern.WHOLE, key = 2, bpm = 60)
        assertEquals(listOf(38, 43, 45, 47), b.bass.map { it.midi })
        assertEquals(listOf(0L, 4000L, 6000L, 8000L), b.bass.map { it.atMs })
        assertEquals(12_000L, b.durationMs)
        assertEquals(60, b.bpm)
    }

    @Test fun cuesInGivesWhatThePlayheadPassedWithoutRepeatsOrGaps() {
        val b = bed(Pattern.HALVES)
        assertTrue(b.cuesIn(100.0, 100.0).isEmpty())
        assertTrue(b.cuesIn(500.0, 100.0).isEmpty())
        val one = b.cuesIn(0.0, 1.0)
        assertTrue(one.any { it is BandCue.Drum && it.kit == PopPlay.Kit.KICK })
        assertTrue(one.any { it is BandCue.BassOn })
        val all = (0 until 12).flatMap { b.cuesIn(it * 500.0, (it + 1) * 500.0) }
        assertEquals(b.cues.filter { it.atMs < 6000 }, all)
    }

    @Test fun aWaltzGrooveHasOneKickAndSoftBrushes() {
        val g = PopPlay.groove(Pattern.HALVES, 3)
        assertEquals(1, g.count { it.kit == PopPlay.Kit.KICK })
        assertEquals(listOf(1.0, 2.0), g.filter { it.kit == PopPlay.Kit.SNARE }.map { it.at })
        assertTrue(g.filter { it.kit == PopPlay.Kit.SNARE }.all { it.velocity < 60 })
    }

    @Test fun balladsPlayQuieterThanRock() {
        val soft = PopPlay.groove(Pattern.BALLAD_ARPEGGIO, 4).filter { it.kit == PopPlay.Kit.SNARE }.maxOf { it.velocity }
        val loud = PopPlay.groove(Pattern.EIGHTHS_ROCK, 4).filter { it.kit == PopPlay.Kit.SNARE }.maxOf { it.velocity }
        assertTrue(soft < loud)
    }

    @Test fun drumSoundsAreMadeAudibleAndTheSameEveryTime() {
        for (k in PopPlay.Kit.entries) {
            val s = BandSounds.samples(k)
            assertTrue("$k", s.size in 2_000..20_000)
            assertTrue("$k", s.maxOf { kotlin.math.abs(it) } in 0.2f..1.0f)
            assertTrue("$k", s.takeLast(32).all { kotlin.math.abs(it) < 0.05f })      // it has died away
            assertTrue("$k", s.contentEquals(BandSounds.samples(k)))
        }
    }
}
