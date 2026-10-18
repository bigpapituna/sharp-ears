package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.2: a round that is a phrase (the copy games, the soft and loud tune) or a chord, and the judges that hear them. */
class PhraseGamesTest {
    private fun rounds(right: String, phrases: Boolean = true) =
        Exercise(right = right, bpm = 80.0, practice = Practice.RIGHT).let { StageRounds(it.toSong("t", "t"), it.practice, phrases = phrases, gapMs = 1_000) }

    /** Ticks [r] for [ms] from [now], returning the new time and the cues. */
    private fun tick(r: StageRounds, now: Long, ms: Long, cues: MutableList<RoundCoach.Cue> = mutableListOf()): Long {
        var t = now
        while (t < now + ms) { t += 20; r.tick(20.0, t).second?.let { cues += it } }
        return t
    }

    @Test fun aPhraseIsTheNotesBetweenRests() {
        val r = rounds("C4:1 D4:1 E4:1 C4:1 r:2 E4:1 D4:1 r:1 G4:2")
        assertEquals(3, r.rounds)
        assertEquals(listOf(60, 62, 64, 60), r.demoKeys())
        // Without phrases every note is its own round, as the garden's stages have them.
        assertEquals(7, rounds("C4:1 D4:1 E4:1 C4:1 r:2 E4:1 D4:1 r:1 G4:2", phrases = false).rounds)
        // Repeated notes are still separate notes of one phrase.
        assertEquals(1, rounds("E4:1 E4:1 F4:1 G4:1 G4:1").rounds)
    }

    @Test fun aPhraseIsShownWholeAndHeardNoteByNote() {
        val r = rounds("C4:1 D4:1 E4:1 r:2 G4:1 E4:1")
        val cues = mutableListOf<RoundCoach.Cue>()
        r.start(0)
        var now = tick(r, 0, r.coach.firstDemoMs + 100, cues)
        assertTrue(cues.first() is RoundCoach.Cue.Demo)
        assertEquals(RoundCoach.Phase.LISTEN, r.coach.phase)
        assertEquals(60, r.expectedKey())
        assertEquals(InputResult.GROUP_DONE, r.found(60, now))
        assertEquals("the round goes on listening", RoundCoach.Phase.LISTEN, r.coach.phase)
        assertEquals(0, r.done); assertEquals(1, r.doneInRound)
        assertEquals("a demo again shows the phrase from its start", listOf(60, 62, 64), r.demoKeys())
        // A quiet stretch counts from the last note heard, not from the round's start.
        now = tick(r, now, 6_000)
        assertEquals(0, r.coach.tries)
        assertEquals(InputResult.GROUP_DONE, r.found(62, now))
        r.found(64, now)
        assertEquals("the phrase is found", 1, r.done)
        assertEquals(RoundCoach.Phase.DONE, r.coach.phase)
        // The next phrase begins after the gap, with its own demo.
        cues.clear()
        now = tick(r, now, 1_100, cues)
        assertTrue(cues.single() is RoundCoach.Cue.Demo)
        assertEquals(listOf(67, 64), r.demoKeys())
    }

    @Test fun threeTriesLetTheRestOfThePhraseGo() {
        val r = rounds("C4:1 D4:1 E4:1 F4:1 r:2 G4:1")
        r.start(0)
        var now = tick(r, 0, r.coach.firstDemoMs + 100)
        r.found(60, now)
        assertNull(r.miss(now)); assertNull(r.miss(now))
        assertEquals(RoundCoach.Cue.LetGo(0), r.miss(now))
        assertEquals(listOf(0), r.letGo)
        assertEquals("D, E and F played in for the kid", 4, r.groupsDone)
        assertEquals(1, r.done)
        now = tick(r, now, 1_100)
        assertEquals(listOf(67), r.demoKeys())
        // The last phrase: nothing at all, three quiet stretches.
        tick(r, now, 40_000)
        assertEquals(listOf(0, 1), r.letGo)
        assertTrue(r.ended)
        assertEquals(5, r.report().groups)
        assertEquals("a let go is no slip", 0, r.report().wrongKeys)
    }

    @Test fun aChordFoundPlaysAllItsKeys() {
        val r = rounds("C4+E4+G4:2 r:2 C4+E4+G4:2", phrases = false)
        assertEquals(2, r.rounds)
        r.start(0)
        val now = tick(r, 0, r.coach.firstDemoMs + 100)
        assertEquals(listOf(60, 64, 67), r.demoKeys())
        assertEquals(InputResult.GROUP_DONE, r.found(60, now))
        assertEquals(1, r.done)
        tick(r, now, 40_000)
        assertEquals("the second let go, all three keys", listOf(1), r.letGo)
        assertEquals(2, r.report().groups)
    }

    @Test fun theNextNoteOrTheSameInAnotherOctave() {
        fun s(by: HeardBy, midi: Int?) = StageSound(0, by, midi, final = true)
        assertEquals(Answer.Found(64), NoteJudge.judge(s(HeardBy.MATCHER, 64), 64))
        assertEquals(Answer.Found(64), NoteJudge.judge(s(HeardBy.NAMED, 76), 64))
        assertEquals(Answer.Miss(MissWhy.OTHER_NOTE, 65), NoteJudge.judge(s(HeardBy.MATCHER, 65), 64))
        assertEquals(Answer.Heard, NoteJudge.judge(StageSound(0, HeardBy.PITCH, guess = 64.0, final = true), 64))
        assertEquals(Answer.Heard, NoteJudge.judge(s(HeardBy.ONSET, null), 64))
        assertEquals(Answer.Heard, NoteJudge.judge(s(HeardBy.MATCHER, 64), null))
    }

    @Test fun theChordNeedsAllThreeTogether() {
        val j = ChordJudge(StageKeys.C_CHORD, windowMs = 1_200)
        fun hit(at: Long, midi: Int, by: HeardBy = HeardBy.MATCHER) = j.judge(StageSound(at, by, midi, final = true))
        assertEquals(Answer.Heard, hit(1_000, 60))
        assertEquals(Answer.Heard, hit(1_010, 64))
        assertEquals(setOf(60, 64), j.lit)
        assertEquals(Answer.Found(60), hit(1_060, 67))
        assertTrue("a new moment", j.lit.isEmpty())
        // Rolled too slowly: the first key is forgotten.
        hit(5_000, 60); hit(6_000, 64)
        assertEquals(5_000L, j.partialSince(6_200))
        assertEquals(Answer.Heard, hit(6_300, 67))
        assertEquals(setOf(64, 67), j.lit)
        j.reset()
        // A key outside the chord on its own is a try; one struck with the chord's keys belongs to that moment.
        assertEquals(Answer.Miss(MissWhy.NOT_CHORD, 62), hit(9_000, 62, HeardBy.NAMED))
        hit(10_000, 60)
        assertEquals(Answer.Heard, hit(10_050, 62, HeardBy.NAMED))
        assertNull(j.partialSince(10_100))
    }

    @Test fun aSoftAndLoudTuneIsGradedAsAWhole() {
        val t = TuneLoudness()
        listOf(-30.0, -29.0, -31.0, -60.0).forEach(t::note)                  // one odd note does not move the middle
        val usual = t.phraseDone(0)!!
        assertEquals(-30.0, t.anchorDb!!, 0.0)
        assertEquals(-30.0, usual.levelDb, 0.0)
        listOf(-38.0, -37.0, -39.0).forEach(t::note)
        assertEquals(TuneVerdict(Loudness.SOFT, Loudness.SOFT, match = true, levelDb = -38.0), t.phraseDone(1))
        listOf(-31.0, -30.0, -29.0).forEach(t::note)
        val notLoud = t.phraseDone(2)!!
        assertEquals(Loudness.LOUD, notLoud.target); assertEquals(false, notLoud.match)
        listOf(-20.0, -21.0).forEach(t::note)
        val woke = t.phraseDone(3)!!
        assertTrue("loud in a soft round wakes him", woke.woke && !woke.match)
        assertNull("nothing heard, nothing to grade", t.phraseDone(1))
        t.note(-40.0); t.skipped()
        assertEquals(0, t.notes)
    }

    @Test fun theEarLetsEveryKeyOfAChordThrough() {
        val chords = StageEar(chords = true)
        assertEquals(listOf(60), chords.strike(60, -20.0, 1_000).map { it.midi })
        assertEquals(listOf(64), chords.strike(64, -20.0, 1_000).map { it.midi })
        assertEquals(listOf(67), chords.strike(67, -20.0, 1_050).map { it.midi })
        assertTrue("the same key again is the same strike", chords.strike(67, -20.0, 1_100).isEmpty())
        // The garden's ear keeps one sound a moment.
        val one = StageEar()
        assertEquals(1, one.strike(60, -20.0, 1_000).size)
        assertTrue(one.strike(64, -20.0, 1_000).isEmpty())
    }
}
