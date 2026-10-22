package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.Stats
import com.bigpapituna.sharpears.engine.brief.TimeRange
import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.7: the brief can say "played Frog hop to three stars": the games fact's phrasings, filled for a parent and a kid. */
class ArcadeFactTest {
    private fun ctx(kid: Boolean) = Ctx(Findings(if (kid) ProfileKind.KID else ProfileKind.ADULT, Period.WEEK, TimeRange(0, 7 * 86_400_000L),
        stats = Stats()), "Mia", { "" }, 1)

    @Test fun theGamesFactNamesTheGameAndItsStars() {
        val fact = Fact(FactKind.GAMES, 4, 3, songs = listOf("frogs", "balloons"))
        for (kid in listOf(false, true)) {
            val vars = factVars(ctx(kid), fact)
            val lines = FactBank.lines(FactKind.GAMES, Period.WEEK).map { fill(if (kid) it.kid else it.adult, vars) }
            assertTrue(lines.all { it != null })
            assertEquals("Played Frog hop to three stars this week.", lines.first())
            assertTrue(lines.any { it!!.startsWith("Four game rounds in the arcade this week") })
        }
    }
}
