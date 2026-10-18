package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.play.PupSaysGame.Answer
import com.bigpapituna.sharpears.engine.play.PupSaysGame.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PupSaysTest {
    private val mia = PupSaysPlayer("p1", "Mia")
    private val theo = PupSaysPlayer("p2", "Theo")
    private val roy = PupSaysPlayer("owner", "Roy")

    /** The pup plays, then the player copies the whole pattern. */
    private fun copy(g: PupSaysGame): Answer {
        g.ready(); g.demoDone()
        var last: Answer? = null
        g.pattern.forEach { last = g.input(it) }
        return last!!
    }

    private fun miss(g: PupSaysGame): Answer {
        g.ready(); g.demoDone()
        return g.input(g.pool.first { it != g.pattern[0] })
    }

    @Test fun thePatternStartsAtTwoAndGrowsByOneKeepingItsStart() {
        val g = PupSaysGame(listOf(mia), kid = true, seed = 7)
        assertEquals(Phase.DEMO, g.phase)                                           // one player: no hand-off
        var before = emptyList<Int>()
        for (n in 2..12) {
            val p = g.pattern
            assertEquals(n, p.size)
            assertEquals(before, p.take(before.size))
            before = p
            val a = copy(g)
            assertTrue(a is Answer.Passed && a.length == n && a.best)
            g.next()
            assertEquals(Phase.DEMO, g.phase)
        }
        assertEquals(12, g.current.best)
    }

    @Test fun kidsCopyCToGAndGrownUpsTheOctave() {
        for (seed in 1L..30L) {
            val kid = PupSaysGame(listOf(mia), kid = true, seed)
            assertTrue(kid.melody(40).all { it in 60..67 && it in PupSaysGame.KID_POOL })
            assertEquals(60, kid.melody(1)[0])
            val adult = PupSaysGame(listOf(roy), kid = false, seed)
            assertTrue(adult.melody(40).all { it in 60..72 && it in PupSaysGame.ADULT_POOL })
            assertTrue(adult.melody(40).zipWithNext().none { (a, b) -> a == b })
        }
        assertTrue((1L..30L).any { s -> PupSaysGame(listOf(roy), false, s).melody(40).any { it > 67 } })
    }

    @Test fun threeMissesAndYouAreOutWithTheBestKept() {
        val g = PupSaysGame(listOf(mia), kid = true, seed = 3)
        copy(g); g.next(); copy(g); g.next()                                       // copied 2 and 3
        assertEquals(4, g.pattern.size)
        val m1 = miss(g) as Answer.Missed
        assertEquals(1, m1.misses); assertFalse(m1.out)
        assertEquals("Oops! Two more tries.", m1.line)
        g.next()
        assertEquals(Phase.DEMO, g.phase)                                           // the pup plays it again
        assertEquals(4, g.pattern.size)                                             // same length after a miss
        // A miss half-way through the pattern still counts.
        g.demoDone(); g.input(g.pattern[0])
        val m2 = g.input(g.pool.first { it != g.pattern[1] }) as Answer.Missed
        assertEquals(2, m2.misses); assertEquals("Oops! One more try.", m2.line)
        g.next()
        val m3 = miss(g) as Answer.Missed
        assertTrue(m3.out)
        assertEquals("That was your last try. Your best: 3 notes!", m3.line)
        g.next()
        assertTrue(g.over)
        assertEquals(listOf(PupSaysResult("p1", 3)), g.results())
        assertTrue(g.input(60) is Answer.Ignored)
    }

    @Test fun turnsAlternateAndOnlyTheCurrentPlayersListeningCounts() {
        val g = PupSaysGame(listOf(mia, theo), kid = true, seed = 11)
        assertEquals(Phase.HANDOFF, g.phase)
        assertEquals("Mia, your turn.", g.whoseTurnLine())
        // Theo hits keys while the pup waits for Mia: nothing counts, the pup says whose turn it is.
        val early = g.input(g.pattern[0]) as Answer.Ignored
        assertTrue(early.line!!.contains("Mia, your turn"))
        g.ready()
        val listen = g.input(g.pattern[0]) as Answer.Ignored                        // while the pup plays
        assertTrue(listen.line!!.contains("Listen first, Mia"))
        assertEquals(0, g.copied)
        assertTrue(g.micNotes().isEmpty())                                          // the mic is off meanwhile
        g.demoDone()
        assertEquals(PupSaysGame.KID_POOL.toSet(), g.micNotes())
        g.pattern.forEach { g.input(it) }
        assertEquals(Phase.RESULT, g.phase)
        assertTrue(g.input(60) is Answer.Ignored)                                   // between turns
        g.next()
        assertEquals(Phase.HANDOFF, g.phase)
        assertEquals(theo, g.current.player)
        assertEquals(2, g.pattern.size)                                             // Theo starts at two
        assertEquals("Theo, your turn.", g.whoseTurnLine())
        copy(g); g.next()
        assertEquals(mia, g.current.player)
        assertEquals(3, g.pattern.size)                                             // Mia moved on to three
    }

    @Test fun aPlayerWhoIsOutIsSkippedAndTheLastOneKeepsGoingWithoutHandOffs() {
        val g = PupSaysGame(listOf(mia, theo, roy), kid = true, seed = 5)
        repeat(3) {
            copy(g); g.next()                                                        // Mia copies
            val m = miss(g) as Answer.Missed; g.next()                               // Theo misses
            assertTrue(m.line.startsWith("Oops! Theo, ") || m.line.startsWith("Theo, that was your last try"))
            copy(g); g.next()                                                        // Roy copies
        }
        assertTrue(g.seats[1].out)
        assertEquals(mia, g.current.player)
        copy(g); g.next()                                                            // Mia: 5 notes
        assertEquals(roy, g.current.player)                                         // Theo is skipped
        miss(g); g.next(); miss(g); g.next()                                         // Roy, then Mia
        miss(g); g.next(); miss(g); g.next()                                         // Roy, then Mia
        miss(g); g.next()                                                            // Roy's third: out
        assertTrue(g.seats[2].out)
        assertEquals(Phase.HANDOFF, g.phase)                                         // over to Mia
        assertEquals(mia, g.current.player)
        copy(g); g.next()                                                            // Mia: 6 notes
        assertEquals(Phase.DEMO, g.phase)                                            // alone now: no hand-off
        miss(g); g.next()
        assertTrue(g.over)
        // Everyone who played gets a result, whatever their best.
        assertEquals(listOf(PupSaysResult("p1", 6), PupSaysResult("p2", 0), PupSaysResult("owner", 4)), g.results())
        assertEquals(setOf("p1"), PupSaysGame.crown(g.results()))
    }

    @Test fun theCrownGoesToTheBestRunAndTiesShareIt() {
        assertEquals(setOf("p2"), PupSaysGame.crown(listOf(PupSaysResult("p1", 4), PupSaysResult("p2", 6))))
        assertEquals(setOf("p1", "p2"), PupSaysGame.crown(listOf(PupSaysResult("p1", 5), PupSaysResult("p2", 5), PupSaysResult("p3", 2))))
        assertTrue(PupSaysGame.crown(listOf(PupSaysResult("p1", 0))).isEmpty())
        assertTrue(PupSaysGame.crown(emptyList()).isEmpty())
    }

    @Test fun stoppingKeepsEveryonesBestAndPlayersMustBeDistinct() {
        val g = PupSaysGame(listOf(mia, theo), kid = false, seed = 2)
        copy(g); g.next()
        g.stop()
        assertTrue(g.over)
        assertEquals(listOf(2, 0), g.results().map { it.best })
        assertTrue(runCatching { PupSaysGame(listOf(mia, mia), true) }.isFailure)
        assertTrue(runCatching { PupSaysGame(emptyList(), true) }.isFailure)
        assertTrue(runCatching { PupSaysGame(listOf(mia, theo, roy, PupSaysPlayer("p4", "Ada")), true) }.isFailure)
    }

    @Test fun copyingOnTheMicPassesThePattern() {
        // The pattern played on a (synthesized) piano, heard by the matcher listening to the pool, one key per group.
        val g = PupSaysGame(listOf(mia), kid = false, seed = 9)
        repeat(4) { copy(g); g.next() }
        g.demoDone()
        val p = g.pattern
        assertEquals(6, p.size)
        val audio = PianoSynth.render(p.mapIndexed { i, m -> SynthNote(m, 600.0 + i * 420) }, 600.0 + p.size * 420 + 800)
        val heard = MicRig.strikes(audio, g.micNotes(), g.micGroups()).map { it.midi }
        assertEquals(p, heard)
        val answers = heard.map { g.input(it) }
        assertTrue(answers.last() is Answer.Passed)
    }
}
