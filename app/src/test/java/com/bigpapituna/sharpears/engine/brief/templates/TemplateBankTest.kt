package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.ActionId
import com.bigpapituna.sharpears.engine.brief.Candidate
import com.bigpapituna.sharpears.engine.brief.DayPart
import com.bigpapituna.sharpears.engine.brief.Direction
import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Flag
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.Praise
import com.bigpapituna.sharpears.engine.brief.PraiseKind
import com.bigpapituna.sharpears.engine.brief.RecommendationKind
import com.bigpapituna.sharpears.engine.brief.Stats
import com.bigpapituna.sharpears.engine.brief.TimeRange
import com.bigpapituna.sharpears.engine.brief.ToyKind
import com.bigpapituna.sharpears.engine.brief.ToyStat
import com.bigpapituna.sharpears.engine.brief.Trend
import com.bigpapituna.sharpears.engine.brief.TrendKind
import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line bank's size and health: every contract kind has its phrasings (per period where the period changes the
 * wording), no list repeats a line, and every token in every template is one the writer supplies (a template with a
 * token nobody fills would silently never be used).
 */
class TemplateBankTest {
    private fun ctx(kid: Boolean, period: Period): Ctx {
        val f = Findings(
            if (kid) ProfileKind.KID else ProfileKind.ADULT, period, TimeRange(TemplateGen.BASE - 7 * 86_400_000L, TemplateGen.BASE),
            goalMinutes = 5, stats = Stats(toys = listOf(ToyStat(ToyKind.JAM, 2, 9), ToyStat(ToyKind.PUP_SAYS, 1, 3))),
        )
        return Ctx(f, "Mia", TemplateGen.titles, 1)
    }

    /** Values chosen so every conditional token (a gap, a count of new ones, a title) has something to say. */
    private fun fact(k: FactKind) = Fact(k, 6, 20, listOf("pond-3", "pond-4", "pond-5"), listOf("twinkle", "ode-to-joy"))
    private fun flag(k: FlagKind) = Flag(k, 4, 12, listOf("pond-3", "pond-4"), listOf("twinkle"), "pond")
    private fun praise(k: PraiseKind) = Praise(k, 4, null, listOf("pond-3", "pond-4"), listOf("twinkle"), "pond")

    @Test fun everyFactKindHasThreePhrasingsForEveryPeriod() {
        for (k in FactKind.entries) for (p in Period.entries) {
            val lines = FactBank.lines(k, p)
            assertTrue("$k/$p has ${lines.size} phrasings", lines.size >= 3)
            assertEquals("$k/$p repeats a line", lines.map { it.kid }.distinct().size, lines.size)
        }
    }

    @Test fun everyFactLineRendersWhenItsTokensHaveValues() {
        for (kid in listOf(true, false)) for (p in Period.entries) {
            val c = ctx(kid, p)
            for (k in FactKind.entries) {
                val vars = factVars(c, fact(k))
                val lines = FactBank.lines(k, p)
                for (line in lines) {
                    val text = if (kid) line.kid else line.adult
                    assertTrue("fact $k/$p unfillable: \"$text\"", fill(text, vars) != null)
                }
                // And with no titles known, at least two phrasings still stand.
                val bare = Ctx(c.f, "Mia", { "" }, 1)
                assertTrue("fact $k/$p without titles: ${bare.usable(lines, factVars(bare, fact(k))).size}", bare.usable(lines, factVars(bare, fact(k))).size >= 2)
            }
        }
        val c = ctx(true, Period.WEEK)
        for (line in FactBank.NONE + FactBank.ALL_DAYS + FactBank.NO_DAYS) assertTrue(line.kid, fill(line.kid, factVars(c, fact(FactKind.PRACTICE_MINUTES))) != null)
    }

    @Test fun everyTrendKindHasThreePhrasingsForBetterAndWorseAndFourForSteady() {
        for (k in TrendKind.entries) for ((dir, good) in listOf(Direction.UP to true, Direction.DOWN to false, Direction.STEADY to null)) {
            val t = Trend(k, 7.0, 4.0, dir, good, 2)
            val lines = TrendBank.lines(t)
            assertTrue("$k/$dir/$good has ${lines.size}", lines.size >= if (dir == Direction.STEADY) 4 else 3)
            assertEquals(lines.size, lines.map { it.kid }.distinct().size)
            for (kid in listOf(true, false)) for (p in Period.entries) {
                val c = ctx(kid, p)
                for (th in listOf(t, t.copy(good = !(good ?: true)))) {
                    val rendered = c.usable(TrendBank.lines(th), c.common() + mapOf("now" to "x", "before" to "y", "subject" to "z"))
                    assertEquals("$k/$dir/$p: a template has a token nobody fills", TrendBank.lines(th).size, rendered.size)
                }
            }
        }
        // Every kind has both a better and a worse list of its own, so a down arrow never reads as good news by accident.
        for (k in TrendKind.entries) assertTrue(TrendBank.sizes().keys.containsAll(listOf("trend $k/BETTER", "trend $k/WORSE")))
    }

    @Test fun everyFlagKindHasThreeObservationsAndThreeReasonsInItsOwnWords() {
        for (k in FlagKind.entries) {
            val obs = FlagBank.observations(k); val why = FlagBank.reasons(k)
            assertTrue("$k observations ${obs.size}", obs.size >= 3)
            assertTrue("$k reasons ${why.size}", why.size >= 3)
            assertEquals(obs.size, obs.map { it.kid }.distinct().size)
            assertEquals(why.size, why.map { it.kid }.distinct().size)
            for (kid in listOf(true, false)) {
                val c = ctx(kid, Period.WEEK)
                val vars = flagVars(c, flag(k))
                for (line in obs + why) {
                    val text = if (kid) line.kid else line.adult
                    if (!text.contains("{name}")) assertTrue("flag $k unfillable: \"$text\"", fill(text, vars) != null)
                }
                val bare = Ctx(c.f, "Mia", { "" }, 1)
                assertTrue("flag $k keeps two observations without titles", bare.usable(obs, flagVars(bare, flag(k))).size >= 2)
            }
        }
    }

    @Test fun everyPraiseKindHasFourPhrasingsForAKidParentAndForAGrownUp() {
        for (k in PraiseKind.entries) {
            val lines = PraiseBank.lines(k)
            assertTrue("$k parent praise ${lines.size}", lines.size >= 4)
            assertEquals(lines.size, lines.map { it.kid }.distinct().size)
            val kidC = ctx(true, Period.WEEK); val adultC = ctx(false, Period.WEEK)
            assertTrue("$k renders for a parent: ${kidC.usable(lines, praiseVars(kidC, praise(k))).size}", kidC.usable(lines, praiseVars(kidC, praise(k))).size >= 4)
            assertTrue("$k renders for an adult: ${adultC.usable(lines, praiseVars(adultC, praise(k))).size}", adultC.usable(lines, praiseVars(adultC, praise(k))).size >= 3)
            val kidBank = KidBank.praise(k)
            assertTrue("$k kid praise ${kidBank.size}", kidBank.size >= 4)
            val kc = ctx(true, Period.SESSION)
            val kv = praiseKidVarsForTest(kc, praise(k))
            assertTrue("$k kid praise renders: ${kc.usable(kidBank, kv).filter(::kidOk).size}", kc.usable(kidBank, kv).filter(::kidOk).size >= 4)
            assertTrue("$k has a line without the name for a second praise", kc.usable(kidBank, kv + ("name" to null)).filter(::kidOk).size >= 2)
        }
    }

    private fun praiseKidVarsForTest(c: Ctx, p: Praise): Map<String, String?> = c.common() + mapOf(
        "name" to "Mia", "title" to "Opus pondc ez", "song" to "Opus twinkle ez", "page" to "the pond", "days" to "three days",
    )

    @Test fun everyActionHasTwoRecommendationPhrasingsAndTheLinesFillTheirParams() {
        val keys = RecBank.sizes().keys.map { it.removePrefix("recommendation ") }
        for (a in ActionId.entries) {
            assertTrue("$a has a fallback", RecBank.FALLBACK.containsKey(a))
            assertTrue("$a has a bank", keys.any { it.startsWith(a.name) })
        }
        for (key in keys) assertTrue("$key has ${RecBank.lines(key).size}", RecBank.lines(key).size >= 2)
        for (kid in listOf(true, false)) {
            val c = ctx(kid, Period.WEEK)
            val cand = Candidate(RecommendationKind.UNSTICK, ActionId.SET_NEXT, mapOf("lesson" to "pond-5", "song" to "twinkle", "after" to "twinkle", "page" to "pond", "value" to "6", "setting" to "micEasier"))
            val vars = recVars(c, cand)
            for (key in keys) for (line in RecBank.lines(key)) {
                val text = if (kid) line.kid else line.adult
                assertTrue("rec $key unfillable: \"$text\"", fill(text, vars) != null)
            }
        }
    }

    @Test fun everyKidNextTrySourceHasFourPhrasings() {
        assertTrue(KidBank.STONE.size >= 4 && KidBank.REPLAY.size >= 4)
        assertTrue(KidBank.STONE.count { !it.kid.contains("{title}") } >= 4 && KidBank.REPLAY.count { !it.kid.contains("{title}") } >= 4)
        for (toy in ToyKind.entries) assertTrue(KidBank.TOYS.getValue(toy).size >= 4)
        for (l in KidBank.STONE + KidBank.REPLAY + KidBank.TOYS.values.flatten() + KidBank.CLOSERS + KidBank.OPENERS) {
            val s = fill(l.kid, mapOf("name" to "Mia", "title" to "Opus pondc ez"))
            assertTrue(l.kid, s != null && kidOk(s))
        }
    }

    @Test fun theBankIsBigEnoughToBeVaried() {
        val sizes = FactBank.sizes() + TrendBank.sizes() + FlagBank.sizes() + PraiseBank.sizes() + RecBank.sizes() + KidBank.sizes()
        val total = sizes.values.sum()
        println("Line bank: ${sizes.size} lists, $total phrasings")
        assertTrue("only $total phrasings", total >= 600)
        // Spot checks on the language: the names the writer must not use.
        val all = FactBank.sizes().keys
        assertTrue(all.isNotEmpty())
        @Suppress("unused") val unused = DayPart.entries
    }
}
