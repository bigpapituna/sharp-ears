package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.parseTokens
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.3 part B: the night sky's beat stars, the jam, higher or lower and fast or slow; the copy games' phrases and calls. */
class TreeSkyGamesTest {
    @Test fun aKeyOnTheBeatLightsItsStarOnce() {
        val b = BeatStars(listOf(0.0, 1.0, 2.0, 3.0), bpm = 60.0)
        assertEquals(1_000.0, b.beatMs, 0.0)
        assertEquals(0, b.heard(120.0))
        assertNull("already lit", b.heard(-100.0))
        assertNull("between beats", b.heard(1_500.0))
        assertEquals(2, b.heard(2_000.0 - 240))
        assertNull("too late for beat four", b.heard(3_000.0 + 260))
        assertEquals(setOf(0, 2), b.lit)
        assertEquals(5, b.keys)
        assertEquals(-240.0, b.offset(1_760.0), 0.001)
        val r = b.report()
        assertEquals(2, r.groups)
        assertEquals(50, r.extraScore)
        // A bar of count-in, then the four beats.
        assertEquals(8_000.0, b.lengthMs, 0.0)
        b.reset()
        assertTrue(b.lit.isEmpty())
        // Every other click: stars on one and three only.
        val half = BeatStars(parseTokens("C4:2 C4:2 C4:2").map { it.startBeat }, bpm = 84.0)
        assertNull(half.heard(half.beatMs))
        assertEquals(1, half.heard(2 * half.beatMs))
    }

    @Test fun theJamDrawsAStarForEveryBlackKey() {
        val j = StarJam(bars = 2, bpm = 60.0)
        j.start()
        assertEquals(8_000.0, j.lengthMs, 0.0)
        val a = j.heard(66, 1_000.0)!!
        val b = j.heard(82, 4_000.0)!!
        assertNull("a white key draws nothing", j.heard(64, 5_000.0))
        assertEquals(1, j.whites)
        assertTrue("across the sky as the band plays", b.x > a.x)
        assertTrue("higher keys sit higher", b.y > a.y)
        assertNull(j.tick(7_000.0))
        assertEquals(StarJam.Tick.End, j.tick(8_000.0))
        assertEquals(2, j.report().groups)
        assertEquals(100, j.report().extraScore)
        // Nothing played: two nudges, then the third quiet stretch ends it.
        val quiet = StarJam(bars = 16, bpm = 60.0)
        quiet.start()
        val ticks = (1..30).mapNotNull { quiet.tick(it * 1_000.0) }
        assertEquals(listOf(StarJam.Tick.Nudge, StarJam.Tick.Nudge, StarJam.Tick.LetGo), ticks.take(3))
        assertEquals(0, quiet.report().extraScore)
    }

    @Test fun higherOrLowerJudgesByTheKeysHeight() {
        val up = PitchRound(64, Way.HIGHER)
        fun s(by: HeardBy, midi: Int? = null, guess: Double? = null) = StageSound(0, by, midi, guess, final = true)
        assertEquals(Answer.Found(67), HigherLowerJudge.judge(s(HeardBy.MATCHER, 67), up))
        assertEquals(Answer.Found(88), HigherLowerJudge.judge(s(HeardBy.NAMED, 88), up))
        assertEquals(Answer.Miss(MissWhy.MIDDLE, 64), HigherLowerJudge.judge(s(HeardBy.MATCHER, 64), up))
        assertEquals(Answer.Miss(MissWhy.OTHER_SIDE, 60), HigherLowerJudge.judge(s(HeardBy.MATCHER, 60), up))
        assertEquals("a guess too close to call", Answer.Heard, HigherLowerJudge.judge(s(HeardBy.PITCH, guess = 64.8), up))
        assertEquals(Answer.Found(67), HigherLowerJudge.judge(s(HeardBy.PITCH, guess = 66.9), up))
        assertEquals(Answer.Heard, HigherLowerJudge.judge(s(HeardBy.ONSET), up))
        val down = PitchRound(69, Way.LOWER)
        assertEquals(Answer.Found(60), HigherLowerJudge.judge(s(HeardBy.MATCHER, 60), down))
        assertEquals(Answer.Miss(MissWhy.OTHER_SIDE, 71), HigherLowerJudge.judge(s(HeardBy.MATCHER, 71), down))
        // The pup's demonstration goes the right way, on a white key; the stage asks about twelve keys at most, his own among them.
        for (r in listOf(up, down, PitchRound(62, Way.HIGHER), PitchRound(71, Way.LOWER))) {
            assertTrue(r.toString(), (r.demo > r.ref) == (r.way == Way.HIGHER) && !com.bigpapituna.sharpears.engine.isBlack(r.demo))
            assertTrue(r.keys.size <= 12 && r.ref in r.keys && r.demo in r.keys)
            // The exercise's group is every key the right way, the demonstration first.
            val g = higherLowerExercise(listOf(r)).toSong("h", "h").groups.single()
            assertTrue(g.anyOf)
            assertEquals(r.demo, g.notes.first().midi)
            assertTrue(g.notes.all { (it.midi > r.ref) == (r.way == Way.HIGHER) })
        }
    }

    @Test fun fastAndSlowAgainstYourUsualPace() {
        val t = TempoTune()
        listOf(0L, 500, 1_000, 1_500, 2_000).forEach(t::note)
        assertEquals(TempoVerdict(Pace.USUAL, null, true, 500.0), t.phraseDone(0))
        assertEquals(500.0, t.anchorMs!!, 0.0)
        listOf(0L, 300, 620, 900, 1_200).forEach(t::note)
        assertEquals(Pace.FAST, t.phraseDone(1)!!.heard)
        listOf(0L, 700, 1_400, 2_100, 2_800).forEach(t::note)
        val slow = t.phraseDone(2)!!
        assertEquals(Pace.SLOW, slow.heard); assertTrue(slow.match)
        // In between: neither.
        listOf(0L, 520, 1_040).forEach(t::note)
        assertEquals(Pace.USUAL, t.phraseDone(1)!!.heard)
        // One note heard is nothing to grade; a phrase let go neither.
        t.note(0)
        assertNull(t.phraseDone(1))
        t.note(0); t.note(100); t.skipped()
        assertEquals(0, t.notes)
        // Without a usual pace of its own (the first phrase went by), fixed lines stand in.
        val fresh = TempoTune()
        assertEquals(Pace.FAST, fresh.grade(300.0)); assertEquals(Pace.SLOW, fresh.grade(1_000.0)); assertEquals(Pace.USUAL, fresh.grade(600.0))
    }

    /** The copy games' phrases are StageRounds' rounds, and the pup's calls are their fingers in words. */
    @Test fun copyPhrasesAndCalls() {
        for (l in TREEHOUSE.lessons + NIGHT_SKY.lessons) for (s in l.steps) {
            if (StageGames.of(s) !is StageGame.Copy) continue
            val ex = s.exercise!!
            val song = ex.toSong("c", "c")
            val phrases = TreeSkyLines.phrases(song)
            val r = StageRounds(song, ex.practice, phrases = true)
            assertEquals("${l.id}: rounds", r.rounds, phrases.size)
            assertEquals("${l.id}: the first demo", phrases.first().map { it.midi }, r.demoKeys())
            assertTrue("${l.id}: three or four notes a round", phrases.all { it.size in 3..5 })
            assertEquals(phrases.size, TreeSkyLines.calls(ex).size)
        }
        assertEquals("One, two, three!", TreeSkyLines.fingerCall(listOf(1, 2, 3)))
        assertEquals("Five, four, three!", TreeSkyLines.fingerCall(listOf(5, 4, 3)))
        val walk = Exercise(right = "C4:1 D4:1 E4:1 r:2 E4:1 D4:1 C4:1", practice = Practice.RIGHT, fingers = "1 2 3 3 2 1")
        assertEquals(listOf("One, two, three!", "Three, two, one!"), TreeSkyLines.calls(walk))
    }

    /** Loud and quiet one note at a time: the tune game's rules over one-note phrases. */
    @Test fun loudAndQuietOneNoteAtATime() {
        val t = TuneLoudness(listOf(null, Loudness.LOUD, Loudness.SOFT))
        t.note(-30.0); t.phraseDone(0)
        t.note(-20.0)
        assertTrue(t.phraseDone(1)!!.match)
        t.note(-30.0)
        assertEquals(Loudness.MEDIUM, t.phraseDone(2)!!.heard)
        val rounds = StageRounds(Exercise(right = "C4:2 r:2 C4:2 r:2 C4:2", practice = Practice.RIGHT).toSong("l", "l"), Practice.RIGHT, phrases = true)
        assertEquals(3, rounds.rounds)
    }
}
