package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.7, the arcade in the parent brief: a game round on the wire, the week's games (the best first) in the stats and as a
 * fact the templates turn into "Played Frog hop to three stars this week.", and the arcade's minutes among the toys.
 */
class ArcadeBriefTest {
    private val mia = Profile.kid("Mia", 1, reader = false, id = "p1")
    private fun d(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    @Test fun aGameRoundRoundTripsOnTheWire() {
        val e = BriefEvent.GameRound(1_792_000_000_000, "frogs", 3, 10, 2, 61_000)
        assertEquals("""{"t":1792000000000,"e":"game","game":"frogs","stars":3,"keys":10,"level":2,"ms":61000}""", e.encode())
        assertEquals(e, BriefEvent.decode(e.encode()))
        // Level and time are left out at their defaults.
        assertEquals("""{"t":1,"e":"game","game":"balloons","stars":1,"keys":3}""", BriefEvent.GameRound(1, "balloons", 1, 3).encode())
        assertEquals(ToyKind.ARCADE, ToyKind.of("arcade"))
    }

    @Test fun theWeekNamesTheBestGameAndItsStars() {
        val events = history {
            session(d(19), 17, 12) { game("balloons", 2); game("frogs", 2); toy(ToyKind.ARCADE, 4) }
            session(d(20), 17, 10) { game("frogs", 3); game("balloons", 1) }
        }
        val f = Insights.compute(events, mia, Period.WEEK, NOW, zone = ZONE)
        assertEquals(listOf(GameStat("frogs", 2, 3), GameStat("balloons", 2, 2)), f.stats.games)
        assertEquals(Fact(FactKind.GAMES, 4, 3, songs = listOf("frogs", "balloons")), f.facts.first { it.kind == FactKind.GAMES })
        assertTrue(f.stats.toys.any { it.toy == ToyKind.ARCADE && it.minutes == 4 })
        // The findings survive their own encoding (the AI's and the cache's way).
        assertEquals(f.stats.games, Findings.decode(f.encode())!!.stats.games)
        // No rounds: no fact, no games.
        val quiet = Insights.compute(history { session(d(20), 17, 10) { toy(ToyKind.JAM, 3) } }, mia, Period.WEEK, NOW, zone = ZONE)
        assertNull(quiet.facts.firstOrNull { it.kind == FactKind.GAMES })
        assertTrue(quiet.stats.games.isEmpty())
    }
}
