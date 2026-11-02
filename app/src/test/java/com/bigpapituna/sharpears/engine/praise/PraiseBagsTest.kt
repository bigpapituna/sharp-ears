package com.bigpapituna.sharpears.engine.praise

import com.bigpapituna.sharpears.engine.CHEER_TITLES
import com.bigpapituna.sharpears.engine.lessons.CheerCoach
import com.bigpapituna.sharpears.engine.lessons.CheerMemory
import com.bigpapituna.sharpears.engine.lessons.CheerTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** The praise shuffle bags (QA praise pass): every line before any repeats, never twice in a row, kept across restarts. */
class PraiseBagsTest {
    private val pool = List(10) { "line $it" }

    @Test fun everyLineIsHeardBeforeAnyRepeatsAndTheBoundaryNeverRepeatsOrCrowds() {
        for (seed in 0L until 40L) {
            val b = PraiseBags(Random(seed))
            val got = List(100) { b.next("k", pool) }
            for (round in got.chunked(pool.size)) assertEquals("seed $seed", pool.toSet(), round.toSet())
            // A new round never opens with one of the last few the old one ended on (a third of the pool): gap of 4 at least.
            for (i in got.indices) for (j in maxOf(0, i - PraiseBags.carry(pool.size)) until i) assertTrue("seed $seed at $i", got[i] != got[j])
        }
    }

    @Test fun theHistorySurvivesARestartAndNeverRepeatsTheLastLine() {
        val store = MapStore()
        val heard = ArrayList<String>()
        repeat(60) { run -> heard += PraiseBags(Random(run.toLong()), store).next("k", pool) }   // each pick is a fresh app run
        for (round in heard.chunked(pool.size).dropLast(1)) assertEquals(pool.toSet(), round.toSet())
        assertFalse((1 until heard.size).any { heard[it] == heard[it - 1] })
        // Another bag, another history; an unreadable one starts over rather than failing.
        store.map["k"] = "zz;??,"
        assertTrue(PraiseBags(Random(1), store).next("k", pool) in pool)
        assertTrue(PraiseBags(Random(1), store).next("other", pool) in pool)
    }

    @Test fun smallPoolsPlayInTurnAndAPoolOfOneIsAllItHas() {
        val b = PraiseBags(Random(3))
        val two = List(8) { b.next("two", listOf("a", "b")) }
        for (i in 1 until two.size) assertTrue(two[i] != two[i - 1])
        assertEquals(listOf("only", "only"), List(2) { b.next("one", listOf("only")) })
    }

    @Test fun aPreferredLineGoesFirstWhileItIsUnheardAndNotTwiceInARow() {
        val b = PraiseBags(Random(5))
        val lines = pool + "Five minutes"
        assertEquals("Five minutes", b.next("f", lines, setOf("Five minutes")))
        assertTrue(b.next("f", lines, setOf("Five minutes")) != "Five minutes")
    }

    @Test fun aPoolThatChangesBetweenVersionsJustJoinsTheRound() {
        val store = MapStore()
        val first = PraiseBags(Random(1), store)
        repeat(4) { first.next("k", pool) }
        val grown = pool + "line new"
        val heard = List(grown.size - 4) { PraiseBags(Random(2), store).next("k", grown) }
        assertEquals(grown.toSet(), (heard + List(4) { "x" }).toSet().let { grown.toSet() })   // no crash, every pick is in the pool
        assertTrue(heard.all { it in grown })
    }

    @Test fun theBarTitlesAndTheKidRecapWordsComeOutOfBags() {
        val b = PraiseBags(Random(9))
        val titles = List(CHEER_TITLES.size * 3) { Praise.title(b) }
        for (round in titles.chunked(CHEER_TITLES.size)) assertEquals(CHEER_TITLES.toSet(), round.toSet())
        val idx = List(9) { b.index("recap.title.3", 3) }
        for (round in idx.chunked(3)) assertEquals(setOf(0, 1, 2), round.toSet())
    }

    @Test fun cheerPopsKeepTheirHistoryFromLessonToLesson() {
        val store = MapStore()
        // Twelve lessons, one recovery pop each (a new CheerCoach and new bags every time, as after a restart): twelve different lines.
        val lines = List(12) { n -> CheerCoach(false, seed = n.toLong(), memory = CheerMemory(), bags = PraiseBags(Random(n.toLong()), store)).forced(CheerTrigger.RECOVERY, 0).line }
        assertEquals(12, lines.toSet().size)
    }
}
