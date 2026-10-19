package com.bigpapituna.sharpears.engine.playground

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.3 part B: the tap games' rules (TapQuiz), from the pup's example to the last round, with no dead ends. */
class TapGamesTest {
    private fun note(m: Int, ms: Int = 700) = listOf(TapSound(listOf(m), ms, gapMs = 0))
    private val up = TapRound(listOf(note(60), note(67)), TapChoice.UP)
    private val down = TapRound(listOf(note(69), note(62)), TapChoice.DOWN)

    /** Ticks [q] from [from] for [ms], collecting its cues. */
    private fun run(q: TapQuiz, from: Long, ms: Long, cues: MutableList<TapQuiz.Cue> = mutableListOf()): Long {
        var t = from
        while (t < from + ms) { t += 20; cues += q.tick(t) }
        return t
    }

    @Test fun theExampleThenEachRoundIsShownThenAsked() {
        val q = TapQuiz(up, listOf(down, up))
        val cues = q.start(0).toMutableList()
        val ex = cues.single() as TapQuiz.Cue.Example
        assertEquals(TapQuiz.Phase.EXAMPLE, q.phase)
        assertTrue("the pup answers after the sounds", ex.answerAtMs > up.soundMs)
        var now = run(q, 0, ex.ms + 40, cues)
        val show = cues.filterIsInstance<TapQuiz.Cue.Show>().single()
        assertTrue("the first round says Your turn", show.first && !show.again)
        assertEquals(0, show.index)
        // Taps while the round plays do not count.
        assertTrue(q.tap(TapChoice.DOWN, now).isEmpty())
        now = run(q, now, show.ms + 40, cues)
        assertEquals(TapQuiz.Phase.ASK, q.phase)
        assertTrue(cues.last() is TapQuiz.Cue.Ask)
        val right = q.tap(TapChoice.DOWN, now)
        assertEquals(TapQuiz.Cue.Right(0, TapChoice.DOWN, firstTry = true), right.single())
        assertEquals(1, q.finished)
        cues.clear()
        now = run(q, now, q.gapMs + 40, cues)
        assertEquals(TapQuiz.Cue.Show(1, first = false, again = false, leadMs = q.leadMs, ms = q.leadMs + up.soundMs), cues.single())
        now = run(q, now, q.leadMs + up.soundMs + 40)
        // A tap that is not it plays the round again; then the answer on the second try.
        val notYet = q.tap(TapChoice.DOWN, now)
        assertEquals(TapQuiz.Cue.NotYet(1, TapChoice.DOWN, 1), notYet[0])
        assertTrue((notYet[1] as TapQuiz.Cue.Show).again)
        now = run(q, now, q.leadMs + up.soundMs + 40)
        assertEquals(TapQuiz.Cue.Right(1, TapChoice.UP, firstTry = false), q.tap(TapChoice.UP, now).single())
        cues.clear()
        run(q, now, q.gapMs + 40, cues)
        assertEquals(TapQuiz.Cue.Done, cues.single())
        assertTrue(q.done)
        assertEquals(2, q.finished)
        val r = q.report()
        assertEquals(2, r.groups)
        assertEquals("one of two on the first try", 50, r.extraScore)
        assertEquals(0, r.wrongKeys)
    }

    @Test fun threeTriesAndThePupShowsTheAnswer() {
        val q = TapQuiz(null, listOf(up))
        var now = run(q, 0, 0, q.start(0).toMutableList())
        now = run(q, now, q.leadMs + 900 + up.soundMs + 40)
        // A tap, then a quiet stretch, then a tap: three tries.
        q.tap(TapChoice.DOWN, now)
        now = run(q, now, q.leadMs + up.soundMs + 40)
        val cues = mutableListOf<TapQuiz.Cue>()
        now = run(q, now, q.quietMs + 40, cues)
        assertEquals(TapQuiz.Cue.NotYet(0, null, 2), cues.first { it is TapQuiz.Cue.NotYet })
        now = run(q, now, q.leadMs + up.soundMs + 40)
        val last = q.tap(TapChoice.DOWN, now)
        assertEquals(TapQuiz.Cue.LetGo(0, TapChoice.UP), last[1])
        assertEquals(listOf(0), q.letGo)
        run(q, now, q.gapMs + 1_100)
        assertTrue(q.done)
        assertEquals(0, q.report().extraScore)
    }

    @Test fun aSilentChildReachesTheEnd() {
        val q = TapQuiz(up, List(6) { if (it % 2 == 0) up else down })
        q.start(0)
        val end = run(q, 0, 10 * 60_000)
        assertTrue(q.done)
        assertEquals((0..5).toList(), q.letGo)
        assertTrue("about half a minute a round at most: ${end}", q.letGo.size == 6)
    }

    @Test fun whichFingerThenPlaysTheKey() {
        val e4 = TapRound(listOf(listOf(TapSound(listOf(64), 900, finger = 3))), TapChoice.F3, play = 64)
        val q = TapQuiz(null, listOf(e4, e4))
        var now = run(q, 0, 0, q.start(0).toMutableList())
        now = run(q, now, q.leadMs + 900 + e4.soundMs + 40)
        val r = q.tap(TapChoice.F3, now)
        assertEquals(listOf(TapQuiz.Cue.Right(0, TapChoice.F3, true), TapQuiz.Cue.Play(0, 64, again = false)), r)
        assertEquals(TapQuiz.Phase.PLAY, q.phase)
        // Another key is a try; E in another octave is it.
        assertEquals(TapQuiz.Cue.PlayMiss(0, 65, 1), q.key(65, now).single())
        assertEquals(TapQuiz.Cue.Played(0, 64), q.key(76, now).first())
        now = run(q, now, q.gapMs + 40)
        now = run(q, now, q.leadMs + e4.soundMs + 40)
        q.tap(TapChoice.F3, now)
        // Nothing played: quiet stretches show the key again, the third lets it go.
        val cues = mutableListOf<TapQuiz.Cue>()
        now = run(q, now, 3 * q.playQuietMs + 100, cues)
        assertEquals(2, cues.count { it is TapQuiz.Cue.Play && it.again })
        assertTrue(cues.any { it is TapQuiz.Cue.PlayLetGo })
        run(q, now, q.gapMs + 1_000)
        assertTrue(q.done)
        assertEquals(listOf(1), q.keysLetGo)
        assertEquals("two taps right and one key played", 3, q.report().groups)
    }

    @Test fun choicesAndFingers() {
        assertEquals(listOf(1, 2, 3, 4, 5), TapKind.FINGER.choices.map { it.finger })
        assertEquals(TapChoice.F4, TapChoice.finger(4))
        assertEquals(null, TapChoice.UP.finger)
        // A round's sounds: its parts one after another, with a gap between.
        val r = TapRound(listOf(note(60, 500), note(62, 300)), TapChoice.UP)
        assertEquals(500 + 300 + TapRound.PART_GAP_MS, r.soundMs)
        assertEquals(500 + TapRound.PART_GAP_MS, r.partAt(1))
    }
}
