package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.TimeRange
import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * The template writer's rules, over thousands of random findings: every output exists and reads clean, the copy rules
 * hold (no dashes, sentence case, no scolding words, the kid card spoken-friendly), the name is used sparingly, no title
 * appears that the findings do not mention, a (findings, week) is deterministic and the next week reads differently.
 */
class TemplateWriterTest {
    private val clock = { 1_793_059_200_000L }
    private fun writer(seed: Long = 7) = TemplateWriter(TemplateGen.titles, seed, clock)
    private val dashes = Regex("[—–]")
    private val scolding = Regex("\\b(wrong|mistakes?|errors?|behind|fail\\w*)\\b", RegexOption.IGNORE_CASE)
    private val kidScolding = Regex("\\b(wrong|mistakes?|errors?|behind|percent|fail\\w*)\\b|%", RegexOption.IGNORE_CASE)

    private fun nextWeek(f: Findings) = f.copy(range = TimeRange(f.range.fromMs + WEEK, f.range.toMs + WEEK))
    private val WEEK = 7 * 86_400_000L

    private fun forEachFindings(n: Int, seed: Long, body: (Findings, Int) -> Unit) {
        val r = Random(seed)
        repeat(n) { i -> body(TemplateGen.findings(r), i) }
    }

    private fun cleanText(s: String, where: String) {
        assertTrue("$where: empty", s.isNotBlank())
        assertFalse("$where: dash in \"$s\"", dashes.containsMatchIn(s))
        assertFalse("$where: leftover token in \"$s\"", s.contains('{') || s.contains('}'))
        assertFalse("$where: double space in \"$s\"", s.contains("  "))
        assertFalse("$where: null in \"$s\"", s.contains("null"))
        assertFalse("$where: percent sign in \"$s\"", s.contains('%'))
        assertEquals("$where: trimmed", s.trim(), s)
    }

    private fun sentenceCase(s: String, where: String) {
        val first = s.first()
        assertTrue("$where: starts \"$s\"", first.isUpperCase() || first == '“' || first.isDigit())
        assertTrue("$where: ends \"$s\"", s.last() in ".!?”")
        // Sentence case: after the first word no capitalised word that starts a clause mid-sentence is required, but a
        // headline is never title case: it must not capitalise most of its words.
        val words = s.split(' ').filter { it.length > 3 && it[0].isLetter() }
        val caps = words.count { it[0].isUpperCase() }
        assertTrue("$where: title case? \"$s\"", words.isEmpty() || caps * 2 <= words.size + 1)
    }

    @Test fun everyBriefIsCompleteCleanAndWithinTheLimits() {
        forEachFindings(3000, 1) { f, i ->
            val name = if (i % 7 == 0) "" else "Mia"
            val b = writer().write(f, name)
            val where = "findings $i ${f.period} ${f.profileKind}"
            assertEquals(Brief.Writer.TEMPLATE, b.writtenBy)
            assertEquals(f.period, b.period)
            TemplateGen.texts(b).forEach { cleanText(it, where) }
            sentenceCase(b.headline, "$where headline")
            assertTrue("$where headline length ${b.headline.length}", b.headline.length <= 110)
            assertTrue("$where headline has no digits: \"${b.headline}\"", b.headline.none { it.isDigit() })
            assertTrue("$where facts ${b.facts.size}", b.facts.size in 1..4)
            b.facts.forEach { sentenceCase(it, "$where fact") }
            b.notes.forEach { sentenceCase(it, "$where note") }
            assertTrue("$where notes ${b.notes.size}", b.notes.size <= 4)
            assertTrue("$where recommendations ${b.recommendations.size}", b.recommendations.size <= 3)
            b.recommendations.forEach { assertTrue("$where rec length ${it.text.length}: ${it.text}", it.text.length <= 160); sentenceCase(it.text, "$where rec") }
            assertTrue("$where recs come from candidates", b.recommendations.all { it.candidate in f.candidates })
            sentenceCase(b.praise, "$where praise")
            if (f.flags.isEmpty()) assertTrue("$where no flags, no notes", b.notes.isEmpty())
            if (f.period == Period.SESSION) assertTrue("$where session has no sections", b.sections.isEmpty())
            b.sections.forEach {
                assertTrue("$where section ${it.title}", it.title in setOf("Habits", "Skills", "Stuck and unstuck", "What you did"))
                assertTrue("$where section body", it.body.isNotBlank())
            }
            val did = b.sections.any { it.title == "What you did" }
            assertEquals("$where: what you did only when something was done", (f.actions.isNotEmpty() || f.settings.isNotEmpty()) && f.period != Period.SESSION, did)
            TemplateGen.texts(b).forEach { assertFalse("$where: scolding in \"$it\"", scolding.containsMatchIn(it)) }
        }
    }

    @Test fun theNameIsUsedAtMostTwiceInTheOneMinuteRead() {
        forEachFindings(3000, 2) { f, i ->
            val b = writer().write(f, "Mia")
            val uses = TemplateGen.minuteRead(b).sumOf { Regex("\\bMia\\b").findAll(it).count() }
            assertTrue("findings $i: Mia used $uses times in ${TemplateGen.minuteRead(b)}", uses <= 2)
            if (f.profileKind == ProfileKind.ADULT) assertEquals("an adult is never called by name", 0, uses)
        }
    }

    @Test fun noTitleAppearsThatTheFindingsDoNotMention() {
        forEachFindings(2000, 3) { f, i ->
            val ok = TemplateGen.mentioned(f)
            val strangers = TemplateGen.ALL_IDS.filter { it !in ok }.map { TemplateGen.titleOf(it) }
            val b = writer().write(f, "Mia")
            val all = TemplateGen.texts(b) + TemplateGen.texts(writer().kidCard(f, "Mia")) +
                listOf("how long", "what was hard", "what next", "praise", "ready for letters", "ready for the road", "what did she do").map { writer().answer(f, "Mia", it) }
            for (t in all) for (s in strangers) assertFalse("findings $i names a title it was not given ($s): \"$t\"", t.contains(s))
        }
    }

    @Test fun aGivenFindingsAndWeekIsDeterministicAndTheNextWeekReadsDifferently() {
        forEachFindings(1500, 4) { f, i ->
            val a = writer().write(f, "Mia"); val b = writer().write(f, "Mia")
            assertEquals("findings $i: same week, same words", a, b)
            assertEquals(writer().kidCard(f, "Mia"), writer().kidCard(f, "Mia"))
            val later = writer().write(nextWeek(f), "Mia")
            assertNotEquals("findings $i: next week reads the same", TemplateGen.texts(a), TemplateGen.texts(later))
            assertNotEquals("findings $i: kid card the same", TemplateGen.texts(writer().kidCard(f, "Mia")), TemplateGen.texts(writer().kidCard(nextWeek(f), "Mia")))
        }
    }

    @Test fun twoFamiliesWithDifferentSeedsDoNotReadAlike() {
        val r = Random(5)
        var different = 0
        repeat(300) {
            val f = TemplateGen.findings(r)
            if (writer(1).write(f, "Mia") != writer(2).write(f, "Mia")) different++
        }
        assertTrue("seeds should change wording: $different of 300", different > 200)
    }

    @Test fun theKidCardIsShortPositiveAndSpokenFriendly() {
        forEachFindings(3000, 6) { f, i ->
            val card = writer().kidCard(f, if (i % 5 == 0) "" else "Mia")
            val where = "findings $i ${f.period}"
            assertTrue("$where lines ${card.lines.size}", card.lines.size in 2..3)
            assertNotNull("$where nextTry", card.nextTry)
            assertEquals(Brief.Writer.TEMPLATE, card.writtenBy)
            for (line in TemplateGen.texts(card)) {
                cleanText(line, where)
                assertTrue("$where: ${line.length} chars: \"$line\"", line.length <= 70)
                assertTrue("$where: kid line not allowed: \"$line\"", kidOk(line))
                assertFalse("$where: scolding in \"$line\"", kidScolding.containsMatchIn(line))
                assertTrue("$where: digits in \"$line\"", line.none { it.isDigit() })
                assertTrue("$where: symbols in \"$line\"", line.none { it in "#&/%@*_=+<>[]{}|\\^~" })
                assertTrue("$where: starts a sentence: \"$line\"", line.first().isUpperCase())
            }
            assertTrue("$where: next time wording: ${card.nextTry}", card.nextTry!!.contains("ext time") || card.nextTry!!.contains("Let's") || card.nextTry!!.contains("let's"))
            assertTrue("$where: the name is used at most twice", card.lines.sumOf { Regex("\\bMia\\b").findAll(it).count() } <= 2)
        }
    }

    @Test fun theKidCardCarriesNoTitleThatIsTooLongOrFullOfDigits() {
        val long = TemplateWriter({ id -> if (id.startsWith("pond")) "Prelude Op. 28 No. 4 in the Key of a Very Long Winter" else "" }, 1, clock)
        val f = TemplateGen.findings(Random(9), kid = true, period = Period.SESSION).copy(
            praise = listOf(com.bigpapituna.sharpears.engine.brief.Praise(com.bigpapituna.sharpears.engine.brief.PraiseKind.THREE_STARS, 1, lessons = listOf("pond-1"))),
            candidates = listOf(com.bigpapituna.sharpears.engine.brief.Candidate(com.bigpapituna.sharpears.engine.brief.RecommendationKind.UNSTICK,
                com.bigpapituna.sharpears.engine.brief.ActionId.SET_NEXT, mapOf("lesson" to "pond-1"))),
        )
        val card = long.kidCard(f, "Mia")
        for (line in TemplateGen.texts(card)) { assertTrue(line, kidOk(line)); assertFalse(line, line.contains("Prelude")) }
    }

    @Test fun recommendationsAreTiedToCandidatesAndFillTheirParams() {
        val f = TemplateGen.findings(Random(12), kid = true, period = Period.WEEK).copy(
            candidates = listOf(
                com.bigpapituna.sharpears.engine.brief.Candidate(com.bigpapituna.sharpears.engine.brief.RecommendationKind.UNSTICK,
                    com.bigpapituna.sharpears.engine.brief.ActionId.SET_NEXT, mapOf("lesson" to "pond-5")),
                com.bigpapituna.sharpears.engine.brief.Candidate(com.bigpapituna.sharpears.engine.brief.RecommendationKind.TOGETHER,
                    com.bigpapituna.sharpears.engine.brief.ActionId.PRACTISE_TOGETHER, mapOf("lesson" to "pond-5")),
            ),
        )
        val b = writer().write(f, "Mia")
        assertEquals(2, b.recommendations.size)
        assertTrue(b.recommendations.all { it.text.contains(TemplateGen.titleOf("pond-5")) })
        assertEquals(f.candidates, b.recommendations.map { it.candidate })
    }

    @Test fun anAdultReadsAsYouWithoutTheKidsWords() {
        val r = Random(21)
        var you = 0
        repeat(1500) {
            val f = TemplateGen.findings(r, kid = false)
            val b = writer().write(f, "Roy")
            for (t in TemplateGen.texts(b)) {
                assertFalse("an adult is not 'the child': \"$t\"", Regex("\\b(child|kids?|Mia|Roy)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) && !t.contains("kids' road"))
                if (Regex("\\b[Yy]ou(r)?\\b").containsMatchIn(t)) you++
            }
        }
        assertTrue("an adult brief should say 'you' often: $you", you > 500)
    }

    @Test fun answerIsNeverNullAndAnswersTheRecognisedQuestions() {
        val questions = listOf(
            "How long did she play?", "what did they do", "What was hard?", "what should we do next", "What should I praise?",
            "is she ready for letters", "Is he ready for the road?", "how many days", "tell me about the weather", "", "   ", "¿Qué?",
            "she is stuck on a song", "any problems?", "how are the hands", "ready to read?",
        )
        val r = Random(31)
        repeat(600) { i ->
            val f = TemplateGen.findings(r)
            for (q in questions) {
                val a = writer().answer(f, "Mia", q)
                cleanText(a, "findings $i, question \"$q\"")
                assertTrue("answer is short: ${a.length}", a.length <= 700)
                assertFalse("no scolding in an answer: $a", scolding.containsMatchIn(a))
            }
        }
        val f = TemplateGen.findings(Random(3), kid = true, period = Period.WEEK)
        val polite = writer().answer(f, "Mia", "what is the capital of France")
        assertTrue(polite, polite.startsWith("I can only answer about what the app saw this week."))
        assertTrue("it adds the headline", polite.length > 60)
        assertEquals(Intent.WHAT_HARD, intentOf("What was the hardest part?"))
        assertEquals(Intent.READY_LETTERS, intentOf("Is she ready for letters yet?"))
        assertEquals(Intent.READY_ROAD, intentOf("ready for the road?"))
        assertEquals(Intent.HOW_LONG, intentOf("How long did he practise?"))
        assertEquals(Intent.WHAT_PRAISE, intentOf("what should I praise"))
        assertEquals(Intent.WHAT_NEXT, intentOf("What next?"))
        assertEquals(Intent.WHAT_DID, intentOf("what did she do this week"))
    }

    @Test fun emptyFindingsStillGetAWarmBriefAndCard() {
        for (kind in ProfileKind.entries) for (period in Period.entries) {
            val f = Findings(kind, period, TimeRange(TemplateGen.BASE - WEEK, TemplateGen.BASE))
            val b = writer().write(f, "Mia")
            TemplateGen.texts(b).forEach { cleanText(it, "empty $kind $period") }
            assertTrue(b.recommendations.isEmpty() && b.notes.isEmpty())
            val card = writer().kidCard(f, "Mia")
            assertTrue(TemplateGen.texts(card).all(::kidOk))
            assertTrue(writer().answer(f, "Mia", "what next").isNotBlank())
        }
    }

    @Test fun aWriterWithNoTitlesAtAllStillWritesWellFormedBriefs() {
        val bare = TemplateWriter({ "" }, 3, clock)
        forEachFindings(1200, 8) { f, i ->
            val b = bare.write(f, "Mia")
            TemplateGen.texts(b).forEach { cleanText(it, "bare $i") }
            TemplateGen.texts(bare.kidCard(f, "Mia")).forEach { assertTrue(it, kidOk(it)) }
            assertTrue("a recommendation never goes missing for want of a title", b.recommendations.size == minOf(3, f.candidates.map { recText(Ctx(f, "Mia", { "" }, 3), it) }.distinct().size))
        }
    }
}
