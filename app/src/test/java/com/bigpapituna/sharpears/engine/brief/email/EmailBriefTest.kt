package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.ActionId
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefSamples
import com.bigpapituna.sharpears.engine.brief.BriefTitles
import com.bigpapituna.sharpears.engine.brief.Candidate
import com.bigpapituna.sharpears.engine.brief.ClapStat
import com.bigpapituna.sharpears.engine.brief.EarStat
import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Flag
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.HandStat
import com.bigpapituna.sharpears.engine.brief.HesitationStat
import com.bigpapituna.sharpears.engine.brief.HintStats
import com.bigpapituna.sharpears.engine.brief.KeySlips
import com.bigpapituna.sharpears.engine.brief.NOW
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.PlayMix
import com.bigpapituna.sharpears.engine.brief.RecommendationKind
import com.bigpapituna.sharpears.engine.brief.SettingId
import com.bigpapituna.sharpears.engine.brief.SlipPair
import com.bigpapituna.sharpears.engine.brief.ZONE
import com.bigpapituna.sharpears.engine.brief.templates.CourseTitles
import com.bigpapituna.sharpears.engine.brief.templates.TemplateGen
import com.bigpapituna.sharpears.engine.brief.templates.TemplateWriter
import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * The email brief's model (design D): its parts in order, a day's shorter email, the first email's welcome and code, the
 * subject lines, why each suggestion helps, the text part; and over random findings, every line keeps the rules.
 */
class EmailBriefTest {
    private val songs = mapOf("twinkle" to "Twinkle, Twinkle, Little Star", "hot-cross-buns" to "Hot Cross Buns", "ode-to-joy" to "Ode to Joy")
    private val lookup = CourseTitles.lookup { songs[it] }
    private val titles = object : BriefTitles {
        override fun lesson(id: String) = lookup(id)
        override fun song(id: String) = lookup(id)
        override fun page(id: String) = lookup(id)
    }
    private val writer = TemplateWriter(lookup, seed = 3, clock = { NOW })

    /** The kid's sample week with the email's extra stats in it. */
    private fun week(): Findings = BriefSamples.kid(Period.WEEK, NOW, ZONE).let { f ->
        f.copy(stats = f.stats.copy(
            keySlips = listOf(KeySlips(62, 5, 0), KeySlips(64, 1, 0), KeySlips(67, 0, 2)), topSlip = SlipPair(62, 64, 5),
            notesDue = 412, skippedNotes = 4, hesitation = HesitationStat(1_100, 380, 67, 2_400), tempoSpreadMs = 38, tempoRuns = 3,
            left = HandStat(40, 88, 1_500), right = HandStat(372, 97, 900), hintsBefore = HintStats(2, 9, 4, 0),
            clap = ClapStat(14, 16, 1), ear = listOf(EarStat("pitch", 6, 5), EarStat("chord:build", 4, 3)), weeks = listOf(19, 22, 38, 40),
            sessionMinutes = 13, sessionMinutesBefore = 9, mix = PlayMix(30, 9, 3),
        ), facts = f.facts + Fact(FactKind.TIMING, 44, 4))
    }

    private fun email(f: Findings, first: Boolean = false, code: String? = if (first) "482913" else null, frequency: EmailFrequency = EmailFrequency.BOTH,
                      brief: Brief = writer.write(f, "Mia")) =
        EmailBriefs.build(f, brief, "Mia", avatar = 1, week = 43, frequency = frequency, first = first, titles = titles, code = code,
            extras = EmailExtras(path = PathProgress("pond", 7, 12, "hall"), bedtimeOn = false, phoneName = "Roy's phone"),
            summary = { id -> if (id == "pond-3") "Listening: copy four notes" else null }, zone = ZONE)

    @Test fun aWeeksEmailHasDesignDsPartsInOrder() {
        val f = week()
        val brief = writer.write(f, "Mia")
        val e = email(f, brief = brief)
        assertEquals(EmailPeriod.WEEK, e.period)
        assertNull("only the first email welcomes", e.welcome)
        with(e.postcard) {
            assertEquals("WEEK 43", stamp); assertEquals("blue", avatarColour); assertEquals(PupPose.DANCING, pose)
            assertEquals(brief.headline, headline)
            assertEquals(listOf("40 minutes", "5 of 7 days", "2 new lessons", "4 songs played"), chips)
            assertEquals(7, paws.size); assertTrue(paws.last().today); assertEquals(5, paws.count { it.practised })
            assertEquals("A paw is a day with practice; the daily goal is five minutes.", pawCaption)
        }
        assertEquals(listOf("What happened.", "The hard bit.", "Getting better."), e.oneMinute.parts.map { it.label })
        assertEquals(brief.notes.first(), e.oneMinute.parts[1].text)
        assertTrue(e.oneMinute.parts[2].text, e.oneMinute.parts[2].text.startsWith("Lessons are finishing with more stars: 2.5 on average, from about 2.1"))
        assertEquals("I noticed you kept going on Copy the frogs. That's sticking power.", e.oneMinute.sayThis!!.quote)

        val next = e.next!!
        assertEquals("What I'd do next", next.title)
        assertEquals(3, next.items.size)
        for (i in next.items) {
            assertEquals(EmailWords.DO_IN_APP, i.button)
            assertEquals("Grown-ups corner > Family > Mia, then ${i.action}", i.where)
            assertTrue(i.why, i.why.endsWith("."))
        }
        assertEquals(listOf("Set as next", "Practise together", "Show letters"), next.items.map { it.action })
        assertTrue(next.items[0].why, next.items[0].why.startsWith("Ta and ti-ti has not settled yet"))

        assertEquals(listOf(SectionId.LESSONS, SectionId.KEYS, SectionId.RHYTHM, SectionId.HABITS, SectionId.READINESS), e.details.sections.map { it.id })
        assertEquals(listOf("Lessons this week", "Keys and fingers", "Rhythm and listening", "Habits and trends", "Readiness and what changed"),
            e.details.sections.map { it.title })
        assertEquals(listOf(true, false, false, false, false), e.details.sections.map { it.open })
        val lessons = e.details.sections[0].blocks.filterIsInstance<Block.Rows>().single().rows
        assertEquals(Row("Copy the frogs", "Listening: copy four notes", "3 stars, 4 tries, finished twice", 3), lessons[0])
        assertEquals(Row("Ta and ti-ti", null, "not finished yet, 6 tries, a step let go", null, highlight = true), lessons[2])
        assertEquals(Row("Twinkle, Twinkle, Little Star", "Song", "played three times, best 2 stars, 78% in Perform", 2), lessons[4])

        val keys = e.details.sections[1].blocks
        val map = keys.filterIsInstance<Block.Keys>().single().keys
        assertEquals((60..72).toList(), map.map { it.midi })
        assertEquals("thirds of the most slips (D's five)", listOf(3, 1, 2), listOf(62, 64, 67).map { k -> map.first { it.midi == k }.heat })
        assertEquals(listOf(Tile("6", "near misses"), Tile("2", "far misses"), Tile("2", "slips per 100 notes")), keys.filterIsInstance<Block.Tiles>().single().tiles)
        val text = keys.filterIsInstance<Block.Text>().associate { it.label to it.text }
        assertEquals("E when D was wanted (five times). That is the key next door, the most common slip while fingers learn their places.", text["Most common slip:"])
        assertEquals("4 of 412, let go by the pup or missed in time.", text["Skipped notes:"])
        assertEquals("about 1.1 seconds to find each key, and 2.4 seconds on G, the key that takes longest.", text["Finding keys:"])
        assertEquals("right hand 97% clean, left hand 88% clean.", text["Hands:"])

        val rhythm = e.details.sections[2].blocks.filterIsInstance<Block.Text>().associate { it.label to it.text }
        assertEquals("notes landed 44 ms from the beat on average.", rhythm["Timing:"])
        assertEquals("in Perform the beat wandered about 38 ms from note to note.", rhythm["Steadiness:"])
        assertEquals("14 of 16 beats with a clap on the drum.", rhythm["Clapping:"])
        assertEquals("5 of 6 right in Perfect pitch; 3 of 4 right in Build it.", rhythm["Ear games:"])
        assertEquals("a key glowed six times, the pup pointed at a key three times and the app played the key twice. " +
            "Last week: glowed twice, pointed nine times and played four times.", rhythm["Hints:"])

        val habits = e.details.sections[3].blocks
        assertEquals(listOf(19, 22, 38, 40), habits.filterIsInstance<Block.Bars>().single().bars.map { it.value })
        assertEquals(listOf(MeterPart("lessons", 72), MeterPart("songs", 21), MeterPart("toys", 7)), habits.filterIsInstance<Block.Meter>().single().parts)
        val h = habits.filterIsInstance<Block.Text>().associate { it.label to it.text }
        assertEquals("evenings five times and afternoons twice.", h["When:"])
        assertEquals("about 13 minutes, up from 9.", h["Session length:"])
        assertEquals("the microphone heard the room twice instead of the piano.", h["Room:"])

        val ready = e.details.sections[4].blocks.filterIsInstance<Block.Text>().associate { it.label to it.text }
        assertTrue(ready["Reading letters:"]!!.startsWith("ready."))
        assertEquals("7 of 12 stones done on the pond. The concert hall opens after it.", ready["Next page:"])
        assertEquals("On Friday you turned colour notes on.", ready["You did:"])
        assertEquals("off.", ready["Bedtime after the goal:"])

        assertEquals(SignOff("Gold star at home", brief.praise, "Signed, the pup", PupPose.CROWN), e.signOff)
        assertEquals("Sent by Sharp Ears for Mia, from Roy's phone.", e.footer.lines[0])
        assertTrue(e.footer.lines.contains("A day's email comes each day Mia plays, and the week's on Sunday at 6 pm."))
        assertEquals("Mia-weekly-brief-2026-10-21.pdf", e.attachmentName)
        assertEquals(e.subjects.first(), e.subject)
    }

    @Test fun aDaysEmailIsShorterAndShowsTheKeysOnlyWhenSomethingSlipped() {
        val day = BriefSamples.kid(Period.DAY, NOW, ZONE)
        val e = email(day)
        assertEquals(EmailPeriod.DAY, e.period)
        assertEquals("WED 21 OCT", e.postcard.stamp)
        assertTrue(e.details.sections.none { it.id == SectionId.KEYS })
        val habits = e.details.sections.first { it.id == SectionId.HABITS }
        assertEquals("Today's sessions", habits.title)
        assertTrue("no four-week chart", habits.blocks.none { it is Block.Bars })
        assertEquals("Lessons today", e.details.sections.first().title)
        assertTrue("nothing changed today", e.details.sections.none { it.id == SectionId.READINESS })
        val slipped = email(day.copy(stats = day.stats.copy(keySlips = listOf(KeySlips(60, 1, 0)), topSlip = SlipPair(60, 61, 1))))
        assertNotNull(slipped.details.sections.firstOrNull { it.id == SectionId.KEYS })
        assertTrue(slipped.footer.lines.contains("A day's email comes each day Mia plays, and the week's on Sunday at 6 pm."))
        assertTrue(e.preheader, e.preheader.startsWith("Today in one minute: 40 minutes, "))
    }

    @Test fun theFirstEmailWelcomesCarriesTheCodeAndSaysHowToChooseHowOften() {
        val e = email(week(), first = true)
        val w = e.welcome!!
        assertEquals("482913", w.code)
        assertTrue("2.8: one place for the family's address", w.codeLine.contains("Settings > Briefs and email"))
        assertTrue(w.howOften.where.contains("Daily and a Weekly switch"))
        assertTrue(e.footer.lines.any { it == "To change how often these come, or stop them: Settings > Briefs and email, in the app." })
        assertTrue(w.codeLine.contains("24 hours"))
        assertEquals(listOf(EmailFrequency.DAILY, EmailFrequency.WEEKLY, EmailFrequency.BOTH, EmailFrequency.OFF), w.howOften.choices.map { it.frequency })
        assertEquals("once a week, on Sunday at 6 pm.", w.howOften.choices[1].text)
        assertEquals("Nothing is sent between 9:30 pm and 7 am, and never more than two a day.", w.howOften.note)
        assertEquals("Mia's piano brief: your code is 482913", e.subject)
        assertTrue(e.preheader.contains("code"))
        assertTrue("no frequency line until one is chosen", e.footer.lines.none { it.startsWith("This comes") || it.startsWith("A day's email") })
        val never = email(BriefSamples.empty(Period.DAY, NOW, ZONE), first = true)
        assertTrue(never.welcome!!.paragraphs.any { it.contains("has not played yet") })
        assertTrue(never.details.sections.isEmpty())
        assertNull(never.next)
        assertNull(never.oneMinute.sayThis)
        assertEquals(PupPose.THINKING, never.postcard.pose)
    }

    @Test fun subjectsFitAPhoneAndKeepTheRules() {
        val f = week()
        val brief = writer.write(f, "Mia")
        val long = brief.copy(headline = "Mia " + "played and played and played ".repeat(5).trim() + ".")
        val dashed = brief.copy(headline = "Mia played six days " + Char(0x2014) + " and stuck with it.")
        for (b in listOf(brief, long, dashed)) {
            val e = email(f, brief = b)
            assertTrue(e.subjects.isNotEmpty())
            for (s in e.subjects) { assertTrue(s, s.length <= EmailWords.SUBJECT_MAX); assertTrue(s, EmailWords.clean(s)); assertTrue(s, s.contains("Mia")) }
        }
        assertEquals("the headline itself when it fits", "Mia played on five days this week", email(f, brief = brief.copy(headline = "Mia played on five days this week.")).subject)
        assertEquals("Mia's week at the piano, from the pup", email(f, brief = long).subject)
        assertEquals("Mia's week at the piano, from the pup", email(f, brief = dashed).subject)
        assertEquals("Mia's week: you had a quiet week", EmailWords("Mia", EmailPeriod.WEEK, titles).subjects("You had a quiet week.", false, null)[0])
    }

    @Test fun everySuggestionSaysWhyInOneSentence() {
        val w = EmailWords("Mia", EmailPeriod.WEEK, titles)
        val f = week().copy(flags = week().flags + Flag(FlagKind.HESITATES_ON, 67, 2_400, lessons = listOf("pond-3")))
        val candidates = buildList {
            for (flag in FlagKind.entries) for (a in ActionId.entries) add(Candidate(RecommendationKind.TOGETHER, a, mapOf("lesson" to "pond-5"), flag))
            for (a in ActionId.entries) add(Candidate(RecommendationKind.ROUTINE, a, emptyMap()))
            for (s in SettingId.entries) add(Candidate(RecommendationKind.SETTINGS, ActionId.SETTING, mapOf("setting" to s.wire, "value" to "true")))
            add(Candidate(RecommendationKind.SETTINGS, ActionId.SETTING, mapOf("setting" to "micEasier", "value" to "6"), FlagKind.NOISY_ROOM))
            add(Candidate(RecommendationKind.NEW_MUSIC, ActionId.TEACHER_ASSIGN, mapOf("after" to "twinkle"), FlagKind.MASTERED_SONG))
        }
        val whys = candidates.map { w.whyFor(it, f) }
        for (y in whys) {
            assertTrue(y, y.endsWith(".") && y.first().isUpperCase())
            assertFalse("one sentence: $y", Regex("[.!?] [A-Z]").containsMatchIn(y))
            assertTrue(y, y.length <= 160)
            assertTrue(y, EmailWords.clean(y))
        }
        assertEquals("G keeps a pause in front of it, and sitting beside Mia you can point to it until the hand knows the way.",
            w.whyFor(Candidate(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, mapOf("lesson" to "pond-3"), FlagKind.HESITATES_ON), f))
        assertEquals("Twinkle, Twinkle, Little Star has come out at three stars again and again, so a new one keeps the challenge fresh.",
            w.whyFor(Candidate(RecommendationKind.NEW_MUSIC, ActionId.TEACHER_ASSIGN, mapOf("after" to "twinkle"), FlagKind.MASTERED_SONG), f))
        assertTrue("every flag reads differently", FlagKind.entries.map { k -> w.whyFor(Candidate(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, emptyMap(), k), f) }.toSet().size >= FlagKind.entries.size - 1)
    }

    /** The samples step 2 and Roy read: app/build/email-samples/ (the text part and the fallback HTML of a week, a day, a first email). */
    @Test fun writesTheSamples() {
        val out = java.io.File("build/email-samples").apply { mkdirs() }
        val samples = mapOf(
            "week" to email(week()), "day" to email(BriefSamples.kid(Period.DAY, NOW, ZONE)),
            "first" to email(BriefSamples.kid(Period.DAY, NOW, ZONE), first = true), "first-never-played" to email(BriefSamples.empty(Period.DAY, NOW, ZONE), first = true),
        )
        for ((name, e) in samples) {
            java.io.File(out, "$name.txt").writeText("Subject: ${e.subject}\nPreheader: ${e.preheader}\n\n" + PlainTextEmail.text(e))
            java.io.File(out, "$name.html").writeText(PlainTextEmail.html(e))
        }
        assertEquals(8, out.listFiles()!!.count { it.extension == "txt" || it.extension == "html" })
    }

    @Test fun theTextPartHasEveryPartInOrderAndTheFallbackHtmlEscapes() {
        val e = email(week(), first = true)
        val text = PlainTextEmail.text(e)
        val order = listOf("Welcome to Mia's piano brief", "Your code: 482913", "Choose how often", "WEEK 43", e.postcard.headline, "IN ONE MINUTE",
            "What happened.", "Say this at home:", "WHAT I'D DO NEXT", "1. ", "   Why: ", "   Do this in the app: Grown-ups corner > Family > Mia, then Set as next.",
            "THE DETAILS, IF YOU WANT THEM", "Lessons this week", "Keys and fingers", "Rhythm and listening", "Habits and trends",
            "Readiness and what changed", "GOLD STAR AT HOME", "Signed, the pup", "Sent by Sharp Ears for Mia")
        var at = -1
        for (s in order) { val i = text.indexOf(s, at + 1); assertTrue("\"$s\" after position $at", i > at); at = i }
        assertTrue(text.contains("Played on "))
        assertTrue(text.contains("Slips by the key that was wanted, darker for more. D 5 near, E 1 near, G 2 far."))
        assertFalse(text.contains("<"))
        val odd = EmailBriefs.build(week(), writer.write(week(), "Mia <3"), "Mia <3", 1, 43, EmailFrequency.WEEKLY, false, titles, zone = ZONE)
        val (html, plain) = PlainTextEmail.renderer.render(odd)
        assertTrue(html.contains("Mia &lt;3") && !html.contains("Mia <3"))
        assertTrue(plain.contains("Mia <3"))
        assertTrue(html.startsWith("<!doctype html>"))
    }

    @Test fun overRandomFindingsEveryLineKeepsTheRules() {
        val r = Random(2026)
        val gen = TemplateWriter(TemplateGen.titles, seed = 9, clock = { NOW })
        val randomTitles = object : BriefTitles {
            override fun lesson(id: String) = TemplateGen.titles(id)
            override fun song(id: String) = TemplateGen.titles(id)
            override fun page(id: String) = TemplateGen.titles(id)
        }
        repeat(400) { i ->
            val period = if (r.nextBoolean()) Period.DAY else Period.WEEK
            val base = TemplateGen.findings(r, period = period)
            val keys = List(r.nextInt(5)) { KeySlips(40 + r.nextInt(50), r.nextInt(6), r.nextInt(4)) }.distinctBy { it.midi }
            val f = base.copy(stats = base.stats.copy(
                keySlips = keys, topSlip = keys.firstOrNull()?.let { SlipPair(it.midi, it.midi + r.nextInt(25) - 12, 1 + r.nextInt(9)) },
                notesDue = r.nextInt(500), skippedNotes = r.nextInt(20),
                hesitation = if (r.nextBoolean()) HesitationStat(r.nextInt(5_000), r.nextInt(400), if (r.nextBoolean()) 40 + r.nextInt(50) else null, r.nextInt(9_000)) else null,
                tempoSpreadMs = if (r.nextBoolean()) r.nextInt(150) else null, left = if (r.nextBoolean()) HandStat(r.nextInt(50), if (r.nextBoolean()) r.nextInt(101) else null, null) else null,
                right = if (r.nextBoolean()) HandStat(r.nextInt(50), r.nextInt(101), r.nextInt(3_000)) else null,
                hintsBefore = if (r.nextBoolean()) HintStats(r.nextInt(9), r.nextInt(9), r.nextInt(9)) else null,
                clap = if (r.nextBoolean()) ClapStat(r.nextInt(16), 16, 1) else null, ear = if (r.nextBoolean()) listOf(EarStat(listOf("pitch", "chord:mm", "odd")[r.nextInt(3)], 6, r.nextInt(7))) else emptyList(),
                weeks = if (r.nextBoolean()) List(4) { r.nextInt(120) } else emptyList(), sessionMinutes = if (r.nextBoolean()) r.nextInt(40) else null,
                sessionMinutesBefore = if (r.nextBoolean()) r.nextInt(40) else null, mix = PlayMix(r.nextInt(60), r.nextInt(30), r.nextInt(30)),
            ))
            val first = r.nextInt(5) == 0
            val e = EmailBriefs.build(f, gen.write(f, "Noor"), "Noor", r.nextInt(8), 1 + r.nextInt(52), EmailFrequency.entries[r.nextInt(4)], first,
                randomTitles, code = if (first) "123456" else null, zone = ZONE)
            val where = "email $i, $period, ${f.profileKind}"
            for (s in e.texts()) assertTrue("$where: \"$s\"", EmailWords.clean(s))
            assertTrue(where, e.subject.length <= EmailWords.SUBJECT_MAX && e.preheader.length <= EmailWords.PREHEADER_MAX)
            e.next?.items?.forEach { item ->
                assertTrue(where, item.why.isNotBlank())
                if (item.candidate != null) assertTrue(where, item.where!!.startsWith("Grown-ups corner > Family > Noor, then "))
                if (f.profileKind == ProfileKind.ADULT) assertTrue(where, item.candidate == null || item.candidate.action !in setOf(ActionId.FRESH_START, ActionId.PACE_FOCUS, ActionId.PACE_UNLOCK, ActionId.TEACHER_ASSIGN))
            }
            assertTrue(where, e.next == null || e.next!!.items.size <= 3)
            if (e.details.sections.isNotEmpty()) assertEquals(where, listOf(true) + List(e.details.sections.size - 1) { false }, e.details.sections.map { it.open })
            assertEquals(where, e.details.sections.map { it.id }.sorted(), e.details.sections.map { it.id })
            e.details.sections.flatMap { it.blocks }.filterIsInstance<Block.Meter>().forEach { assertEquals(where, 100, it.parts.sumOf { p -> p.percent }) }
            if (period == Period.DAY) assertTrue(where, e.details.sections.flatMap { it.blocks }.none { it is Block.Bars })
            val text = PlainTextEmail.text(e)
            assertTrue(where, text.contains(e.postcard.headline) && text.contains(e.signOff.text))
        }
    }
}
