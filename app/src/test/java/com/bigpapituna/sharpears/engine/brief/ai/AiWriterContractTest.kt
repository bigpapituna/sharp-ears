package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.TimeRange
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.brief.ai.AiFixtures.NAME
import com.bigpapituna.sharpears.engine.brief.ai.AiFixtures.NOW
import com.bigpapituna.sharpears.engine.brief.ai.AiFixtures.PERFECT_CARD
import com.bigpapituna.sharpears.engine.brief.ai.AiFixtures.PERFECT_WEEK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * The AI writer against a fake transport: a perfect reply, a reply naming a lesson not in the findings, malformed JSON,
 * a 429, a reply slower than the limit, and around them the cache, the quota, the name and the redaction.
 */
class AiWriterContractTest {
    private var now = NOW
    private val store = MemoryBudgetStore()
    private val logs: MutableList<String> = Collections.synchronizedList(ArrayList())

    private fun writer(t: AiTransport, limits: AiLimits = AiLimits(), budget: Budget = Budget(store, zone = java.time.ZoneId.of("America/Toronto"))) =
        AiWriter(t, "anthropic/claude-haiku-5.5", { now }, limits, budget, "p2", AiFixtures.TITLES, { AiFixtures.ROSTER }, { logs += it })

    private fun used(budget: Budget = Budget(store, zone = java.time.ZoneId.of("America/Toronto"))) = budget.used("p2", now)

    private fun replaced(vararg pairs: Pair<String, String>) = pairs.fold(PERFECT_WEEK) { s, (a, b) -> s.replace(a, b).also { check(it != s) { "no $a" } } }

    // ---- (a) a perfect reply --------------------------------------------------------------------------------------

    @Test fun aPerfectReplyBecomesTheBriefWithTheNameFilledInOnThePhone() {
        val t = FakeTransport { PERFECT_WEEK }
        val f = AiFixtures.week()
        val b = writer(t).write(f, NAME)!!
        assertEquals(Period.WEEK, b.period)
        assertEquals(Brief.Writer.AI, b.writtenBy)
        assertEquals(NOW, b.writtenAt)
        assertEquals("Mia played on most days this week and kept at a tricky rhythm.", b.headline)
        assertEquals(3, b.facts.size)
        assertEquals("Mia played on 5 days out of 7, up from about 2.", b.facts[0])
        assertEquals(1, b.notes.size)
        assertEquals(listOf(f.candidates[0], f.candidates[1]), b.recommendations.map { it.candidate })
        assertEquals("Sit in with Mia on Ta and ti-ti and play it together.", b.recommendations[1].text)
        assertEquals("Mia stuck with Copy the frogs through 4 tries and finished it with 3 stars.", b.praise)
        assertEquals(listOf("Skills", "Habits"), b.sections.map { it.title })
        assertEquals(1, used())

        val r = t.requests.single()
        assertEquals("anthropic/claude-haiku-5.5", r.model)
        assertTrue(r.json)
        assertEquals(12_000, r.timeoutMs)
        assertTrue("the weekly brief may retry once", r.retryOnce)
        assertTrue(r.user.contains(f.redacted().encode()))
        assertFalse("the raw findings never go", r.user.contains(f.encode()))
        assertNoName(r)
        logs.forEach { assertFalse(it, it.contains(NAME)) }
    }

    // ---- (b) a line naming a lesson that is not in the findings ---------------------------------------------------

    @Test fun aLineNamingALessonNotInTheFindingsIsDroppedAndTheRestKept() {
        val reply = replaced(
            "\"That came to 42 minutes of practice.\"" to "\"Twinkle all the way came next, with 42 minutes of practice.\"",
            "\"Ta and ti-ti is still settling." to "\"Hot Cross Buns and Ta and ti-ti are still settling.",
        )
        val b = writer(FakeTransport { reply }).write(AiFixtures.week(), NAME)!!
        assertEquals(2, b.facts.size)
        assertFalse(b.facts.any { "Twinkle all the way" in it })
        assertTrue("the only note named a song not in the findings", b.notes.isEmpty())
        assertEquals(2, b.recommendations.size)
        assertTrue(logs.any { "names a title not in the findings" in it })
    }

    @Test fun aRecommendationOutsideTheCandidatesIsDropped() {
        val reply = replaced(
            "\"candidateIndex\":1}" to "\"candidateIndex\":1},{\"text\":\"Try the next page.\",\"candidateIndex\":7},{\"text\":\"Again.\",\"candidateIndex\":0},{\"text\":\"Odd.\",\"candidateIndex\":\"x\"}",
        )
        val b = writer(FakeTransport { reply }).write(AiFixtures.week(), NAME)!!
        assertEquals(listOf(0, 1), b.recommendations.map { r -> AiFixtures.week().candidates.indexOf(r.candidate) })
        assertTrue(logs.any { "candidateIndex 7 is not a candidate" in it })
    }

    @Test fun theHeadlineThePraiseAndAtLeastOneFactAreRequired() {
        val noHeadline = PERFECT_WEEK.replace("\"headline\":", "\"title\":")
        val digits = replaced("most days this week" to "5 days this week")
        val tooLong = replaced("kept at a tricky rhythm." to "kept at a tricky rhythm, and also did a great many other wonderful things worth telling.")
        val noPraise = PERFECT_WEEK.replace("\"praise\":", "\"cheer\":")
        val noFacts = PERFECT_WEEK.replace(Regex("\"facts\":\\[[^\\]]*\\]"), "\"facts\":[\"Most kids find Ta and ti-ti hard.\"]")
        for (reply in listOf(noHeadline, digits, tooLong, noPraise, noFacts)) {
            assertNull(reply, writer(FakeTransport { reply }).write(AiFixtures.week(), NAME))
        }
    }

    @Test fun repliesAreTidiedDashesMarkdownAndPlaceholderSpellings() {
        val reply = "```json\n" + replaced(
            "kept at a tricky rhythm." to "kept at a tricky rhythm $EM well done.",
            "That came to 42 minutes" to "**That** came to 42 minutes",
            "\"{name} played on 5 days out of 7" to "\"{ Name } played on 5${EN}7 days, 5 days out of 7",
        ) + "\n```"
        val b = writer(FakeTransport { reply }).write(AiFixtures.week(), NAME)!!
        assertEquals("Mia played on most days this week and kept at a tricky rhythm, well done.", b.headline)
        assertEquals("That came to 42 minutes of practice.", b.facts[1])
        assertEquals("Mia played on 5 to 7 days, 5 days out of 7, up from about 2.", b.facts[0])
        val all = listOf(b.headline, b.praise) + b.facts + b.notes + b.recommendations.map { it.text } + b.sections.flatMap { listOf(it.title, it.body) }
        all.forEach { assertFalse(it, it.contains(EM) || it.contains(EN) || it.contains("{")) }
    }

    @Test fun comparisonsAndAdviceOutsideTheAppAreDropped() {
        val reply = replaced(
            "\"That came to 42 minutes of practice.\"" to "\"That is more than most kids her age manage.\"",
            "\"Two new lessons are done: Drum on a lily pad and Copy the frogs.\"" to "\"A piano teacher could help with rhythm.\"",
        )
        val b = writer(FakeTransport { reply }).write(AiFixtures.week(), NAME)!!
        assertEquals(1, b.facts.size)
        assertTrue(logs.any { "compares with others" in it } && logs.any { "advice outside the app" in it })
    }

    // ---- (c) malformed JSON ---------------------------------------------------------------------------------------

    @Test fun malformedJsonIsNull() {
        for (reply in listOf("{\"headline\": \"Mia had a", "Here is the brief you asked for.", "[1, 2, 3]", "")) {
            assertNull(writer(FakeTransport { reply }).write(AiFixtures.week(), NAME))
        }
        assertEquals("a 2xx reply was paid for, usable or not", 4, used())
    }

    // ---- (d) a 429 ------------------------------------------------------------------------------------------------

    @Test fun a429IsNullAndLeavesTheQuotaUntouched() {
        val t = FakeTransport { throw AiCallException(AiCallException.Kind.RATE_LIMITED, 429, "HTTP 429") }
        assertNull(writer(t).write(AiFixtures.week(), NAME))
        assertEquals(1, t.requests.size)
        assertEquals(0, used())
        // So do the other refusals that cost nothing.
        for (k in listOf(AiCallException.Kind.NOT_ALLOWED, AiCallException.Kind.NO_KEY, AiCallException.Kind.CLIENT, AiCallException.Kind.SERVER, AiCallException.Kind.NETWORK)) {
            assertNull(writer(FakeTransport { throw AiCallException(k, null, "no") }).write(AiFixtures.week(), NAME))
        }
        assertEquals(0, used())
    }

    // ---- (e) a reply slower than the limit ------------------------------------------------------------------------

    @Test fun aReplySlowerThanTheLimitIsNull() {
        val slow = FakeTransport { Thread.sleep(3_000); PERFECT_WEEK }
        val limits = AiLimits(sessionMs = 150, weekMs = 150, kidCardMs = 150)
        val started = System.nanoTime()
        assertNull(writer(slow, limits).write(AiFixtures.week(), NAME))                 // a week may retry: up to 2 x 150 ms
        assertNull(writer(slow, limits).kidCard(AiFixtures.session(), NAME))
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("waited $ms ms", ms < 1_500)
        assertEquals("a call we stopped waiting for may still be billed", 2, used())
        assertTrue(logs.any { "no reply within 300 ms" in it } && logs.any { "no reply within 150 ms" in it })
    }

    // ---- the cache and the quota ----------------------------------------------------------------------------------

    @Test fun reopeningReadsTheCacheRefreshCallsAgainAndCounts() {
        val t = FakeTransport { PERFECT_WEEK }
        val w = writer(t)
        val first = w.write(AiFixtures.week(), NAME)!!
        now += 60_000
        val again = w.write(AiFixtures.week(toMs = now), NAME)!!                // a minute later: same findings, "now" moved
        assertEquals(1, t.requests.size)
        assertEquals(first, again)
        assertEquals("the cached brief keeps the time it was written", NOW, again.writtenAt)
        val refreshed = w.refresh(AiFixtures.week(toMs = now), NAME)!!
        assertEquals(2, t.requests.size)
        assertEquals(now, refreshed.writtenAt)
        assertEquals(2, used())
        // New findings are a new brief.
        w.write(AiFixtures.week().copy(goalMinutes = 6), NAME)
        assertEquals(3, t.requests.size)
        // A fresh writer on the same store (the app reopened) still reads the cache.
        writer(t).write(AiFixtures.week().copy(goalMinutes = 6), NAME)
        assertEquals(3, t.requests.size)
    }

    @Test fun overTheWeeksQuotaWriteIsNullWithoutACallButTheCacheStillServes() {
        val t = FakeTransport { PERFECT_WEEK }
        val budget = Budget(store, callsPerWeek = 1, zone = java.time.ZoneId.of("America/Toronto"))
        val w = writer(t, budget = budget)
        assertNotNull(w.write(AiFixtures.week(), NAME))
        assertNull(w.write(AiFixtures.week().copy(goalMinutes = 6), NAME))
        assertEquals(1, t.requests.size)
        assertNotNull("the brief already written is still there", w.write(AiFixtures.week(), NAME))
        assertNull(w.refresh(AiFixtures.week(), NAME))
        assertEquals(1, t.requests.size)
        assertTrue(logs.any { "calls are used" in it })
    }

    @Test fun nothingHappenedIsNotWorthACall() {
        val t = FakeTransport { PERFECT_WEEK }
        val empty = Findings(ProfileKind.KID, Period.DAY, TimeRange(NOW - 1000, NOW))
        assertNull(writer(t).write(empty, NAME))
        assertNull(writer(t).kidCard(empty, NAME))
        assertTrue(t.requests.isEmpty())
    }

    @Test fun aTransportThatThrowsSomethingElseIsNullFreeAndLogsOnlyTheType() {
        val t = FakeTransport { throw IllegalStateException("secret sk-or-v1-abcdefabcdef in a message") }
        assertNull(writer(t).write(AiFixtures.week(), NAME))
        assertEquals(0, used())
        assertTrue(logs.any { "IllegalStateException" in it })
        assertFalse(logs.any { "secret" in it || "sk-or" in it })
    }

    @Test fun aSessionNoteHasNoSectionsAndUsesTheShortLimit() {
        val t = FakeTransport { """{"headline":"A short session with two songs.","facts":["{name} practised for 11 minutes."],"notes":[],"recommendations":[{"text":"Play {name}'s lullaby together.","candidateIndex":0}],"praise":"{name}'s lullaby got three stars.","sections":[{"title":"Skills","body":"Two songs."}]}""" }
        val b = writer(t).write(AiFixtures.session(), NAME)
        assertNotNull(b)
        assertTrue(b!!.sections.isEmpty())
        assertEquals(3_000, t.requests.single().timeoutMs)
        assertFalse(t.requests.single().retryOnce)
    }

    // ---- names ----------------------------------------------------------------------------------------------------

    @Test fun aTitleHoldingTheNameGoesAsThePlaceholderAndAnotherKidsNameNotAtAll() {
        val t = FakeTransport { """{"headline":"A lovely session with a song of {name}'s own.","facts":["{name} played {name}'s lullaby to the end twice."],"notes":[],"recommendations":[],"praise":"{name}'s lullaby got three stars."}""" }
        val b = writer(t).write(AiFixtures.session(), NAME)!!
        val r = t.requests.single()
        assertTrue("the title goes under a stand-in id", r.user.contains("song hidden-1: {name}'s lullaby"))
        assertTrue(r.user.contains("\"song\":\"hidden-1\""))
        assertFalse(r.user.contains("leo", ignoreCase = true))
        assertNoName(r)
        assertEquals("Mia played Mia's lullaby to the end twice.", b.facts.single())
        assertEquals("Mia's lullaby got three stars.", b.praise)
    }

    // ---- the pup's card -------------------------------------------------------------------------------------------

    @Test fun thePupsCardIsPositiveSpokenFriendlyAndForKidsOnly() {
        val t = FakeTransport { PERFECT_CARD }
        val card = writer(t).kidCard(AiFixtures.week(), NAME)!!
        assertEquals(listOf("You played with me on five days this week!", "You kept going on Copy the frogs and won three stars!"), card.lines)
        assertEquals("Next time, let's try Ta and ti-ti, nice and slow.", card.nextTry)
        assertEquals(Brief.Writer.AI, card.writtenBy)
        val r = t.requests.single()
        assertEquals(3_000, r.timeoutMs)
        assertEquals(Prompts.kidCard(), r.system)

        val mixed = """{"lines":["That rhythm was hard, but you tried!","You played 42 notes!","Your letters were lovely.","You played with me on five days!","Copy the frogs was fun with you."],"nextTry":"Let's play again."}"""
        val c2 = writer(FakeTransport { mixed }).kidCard(AiFixtures.week().copy(goalMinutes = 6), NAME)!!
        assertEquals(listOf("You played with me on five days!", "Copy the frogs was fun with you."), c2.lines)
        assertNull("the next time line must start with next time", c2.nextTry)
        assertTrue(logs.any { "not a positive" in it } && logs.any { "a number over ten" in it } && logs.any { "letters for a child who does not read yet" in it })

        val one = """{"lines":["You played!","That was wrong."],"nextTry":"Next time, let's try it."}"""
        assertNull(writer(FakeTransport { one }).kidCard(AiFixtures.week().copy(goalMinutes = 7), NAME))

        val adult = FakeTransport { PERFECT_CARD }
        assertNull(writer(adult).kidCard(AiFixtures.week().copy(profileKind = ProfileKind.ADULT), NAME))
        assertTrue("no call for a grown-up", adult.requests.isEmpty())
    }

    // ---- the answer -----------------------------------------------------------------------------------------------

    @Test fun anAnswerIsPlainTextFromTheSameFindingsWithNamesTakenOutOfTheQuestion() {
        val t = FakeTransport { "Ta and ti-ti is still settling for {name}: the app let one step go on purpose, so nobody stays stuck." }
        val a = writer(t).answer(AiFixtures.week(), NAME, "Why is Mia stuck on the rhythm one? Is Leo better at it?")
        assertEquals("Ta and ti-ti is still settling for Mia: the app let one step go on purpose, so nobody stays stuck.", a)
        val r = t.requests.single()
        assertFalse(r.json)
        assertTrue(r.user.endsWith("Question:\nWhy is {name} stuck on the rhythm one? Is someone else better at it?"))
        assertTrue(r.user.contains(AiFixtures.week().redacted().encode()))
        assertNoName(r)
        assertFalse(r.user.contains("Leo"))
        assertEquals(Prompts.answer(ProfileKind.KID), r.system)

        assertNull(writer(FakeTransport { "Leo is ahead of most kids her age." }).answer(AiFixtures.week(), NAME, "How is she doing?"))
        assertNull(writer(FakeTransport { "x" }).answer(AiFixtures.week(), NAME, "   "))
        assertEquals("A long question is cut before it goes", Prompts.QUESTION_MAX,
            FakeTransport { "Fine." }.also { writer(it).answer(AiFixtures.week(), NAME, "a".repeat(2_000)) }.requests.single().user.substringAfter("Question:\n").length)
    }

    private fun assertNoName(r: AiRequest) {
        assertFalse("the name never leaves the phone", Regex("(?i)(?<![a-z])mia(?![a-z])").containsMatchIn(r.system + r.user))
    }
}
