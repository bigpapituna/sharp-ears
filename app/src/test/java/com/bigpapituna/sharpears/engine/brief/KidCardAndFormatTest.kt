package com.bigpapituna.sharpears.engine.brief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class KidCardAndFormatTest {
    private val zone = ZoneId.of("America/Toronto")
    private fun card(lines: List<String>, next: String? = null) = KidCard(lines, next, Brief.Writer.AI, 0L)

    // ---- the child's card: what the voice says ----

    @Test fun theSpokenTextIsTheLinesThenTheNextTry() {
        val c = card(listOf("Wow, Mia! You played so well.", "You finished Copy the frogs!"), "Next time, let's try Ta and ti-ti again!")
        assertEquals("Wow, Mia! You played so well. You finished Copy the frogs! Next time, let's try Ta and ti-ti again!", KidCardWords.spoken(c))
    }

    @Test fun dashesSymbolsAndEmojiNeverReachTheVoiceOrTheCard() {
        val c = card(listOf("You were great — really great", "Mia & Theo – star players #1", "Fun 🎉 time"), "next time: try again - slowly")
        val spoken = KidCardWords.spoken(c)
        assertFalse(spoken, Regex("[—–&#🎉]| - ").containsMatchIn(spoken))
        assertEquals("You were great, really great.", KidCardWords.lines(c)[0])
        assertTrue(KidCardWords.lines(c)[1].startsWith("Mia and Theo"))
        assertFalse("  " in spoken)
        assertTrue(KidCardWords.lines(c).all { it.first().isUpperCase() && it.last() in ".!?" })
    }

    @Test fun aCardShowsThreeLinesAtMostAndLongOnesAreCutAtASentence() {
        val long = "You played the song really beautifully today. " + "And then you kept going and going until the very end of it all, which is wonderful to hear from you."
        val c = card(listOf(long, "b", "c", "d", "e"))
        assertEquals(KidCardWords.MAX_LINES, KidCardWords.lines(c).size)
        assertEquals("You played the song really beautifully today.", KidCardWords.lines(c)[0])
        assertTrue(KidCardWords.lines(card(listOf("w ".repeat(200)))).single().length <= KidCardWords.MAX_LINE + 1)
    }

    @Test fun emptyLinesGoAndATailWithoutNextTryIsFine() {
        val c = card(listOf("", "  ", "Hello"), "   ")
        assertEquals(listOf("Hello."), KidCardWords.lines(c))
        assertNull(KidCardWords.nextTry(c))
        assertEquals("Hello.", KidCardWords.spoken(c))
    }

    @Test fun theNameInALineSurvivesAsIs() {
        assertEquals("Mia's turn!", KidCardWords.lines(card(listOf("Mia's turn!"))).single())
    }

    @Test fun aCardIsDueOncePerRealSession() {
        assertTrue(KidCardWords.due(300, 1000L, null))
        assertFalse("quiet session", KidCardWords.due(30, 1000L, null))
        assertFalse("already shown", KidCardWords.due(300, 1000L, 1000L))
        assertTrue("a newer session", KidCardWords.due(300, 2000L, 1000L))
        assertFalse("no session", KidCardWords.due(300, null, null))
    }

    // ---- format ----

    @Test fun lessonAndSongLinesSayStarsAndTries() {
        assertEquals("Pond 3: 3 stars, 4 tries", BriefFormat.lessonLine(LessonStat("pond-3", 3, 4, 0, 2, 11), IdTitles))
        assertEquals("Pond 5: not finished yet, 1 try, the pup let 1 step go", BriefFormat.lessonLine(LessonStat("pond-5", null, 1, 1, 0, 3), IdTitles))
        assertEquals("Twinkle: played three times, best 2 stars", BriefFormat.songLine(SongStat("twinkle", 3, 2, 70), IdTitles))
        assertEquals("Twinkle: played once", BriefFormat.songLine(SongStat("twinkle", 1, 0, null), IdTitles))
    }

    @Test fun writtenNoteSaysWhoAndWhen() {
        val at = java.time.LocalDateTime.of(2026, 10, 21, 18, 42).atZone(zone).toInstant().toEpochMilli()
        fun brief(by: Brief.Writer, written: Long) = Brief(Period.WEEK, "h", emptyList(), emptyList(), emptyList(), "p", emptyList(), by, written)
        assertEquals("Written by AI at 6:42 pm", BriefFormat.writtenNote(brief(Brief.Writer.AI, at), at + 60_000, zone))
        assertEquals("Written on the phone at 6:42 pm", BriefFormat.writtenNote(brief(Brief.Writer.TEMPLATE, at), at + 60_000, zone))
        assertEquals("Written on the phone yesterday at 6:42 pm", BriefFormat.writtenNote(brief(Brief.Writer.TEMPLATE, at), at + 86_400_000, zone))
        assertEquals("Written by AI on Wed 21 Oct at 6:42 pm", BriefFormat.writtenNote(brief(Brief.Writer.AI, at), at + 5 * 86_400_000L, zone))
        assertEquals("12:05 am", BriefFormat.clock(5)); assertEquals("12:30 pm", BriefFormat.clock(750))
    }

    @Test fun theRangeLabelsReadLikeADiary() {
        val now = java.time.LocalDateTime.of(2026, 10, 21, 20, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("15 to 21 Oct", BriefFormat.rangeLabel(BriefSamples.kid(Period.WEEK, now, zone), zone))
        assertEquals("Wed 21 Oct", BriefFormat.rangeLabel(BriefSamples.kid(Period.DAY, now, zone), zone))
        assertEquals("Wed 21 Oct, 7:38 pm to 7:59 pm", BriefFormat.rangeLabel(BriefSamples.kid(Period.SESSION, now, zone), zone))
        val monthEnd = java.time.LocalDateTime.of(2026, 11, 3, 20, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("28 Oct to 3 Nov", BriefFormat.rangeLabel(BriefSamples.kid(Period.WEEK, monthEnd, zone), zone))
    }

    @Test fun skillSummariesAreHonestAboutThin_Data() {
        assertEquals("Not enough yet to draw a line.", BriefFormat.skillSummary(SkillSeries(SkillKind.STARS, listOf(null, 2.0, null, null))))
        assertEquals("From 2.0 to 2.5 over the weeks.", BriefFormat.skillSummary(SkillSeries(SkillKind.STARS, listOf(2.0, 2.2, null, 2.5))))
        assertEquals("Steady around 2.1 over the weeks.", BriefFormat.skillSummary(SkillSeries(SkillKind.STARS, listOf(2.0, 2.1, 2.0, 2.1))))
        assertEquals("From 62 ms to 41 ms over the weeks.", BriefFormat.skillSummary(SkillSeries(SkillKind.TIMING_MS, listOf(62.0, null, null, 41.0))))
        assertFalse(BriefFormat.upIsGood(SkillKind.TIMING_MS)); assertFalse(BriefFormat.upIsGood(SkillKind.PLAY_HINTS)); assertTrue(BriefFormat.upIsGood(SkillKind.EAR))
    }

    @Test fun everyActionTakenReadsAsAKindSentence() {
        for (a in ActionId.entries) for (p in listOf(emptyMap(), mapOf("lesson" to "pond-5", "page" to "pond", "song" to "twinkle", "setting" to "reader", "value" to "true"))) {
            val s = BriefFormat.actionTaken(ActionTaken(0, a, p), IdTitles)
            assertTrue(s, s.startsWith("You ") && s.endsWith(".") && !s.contains('—') && "  " !in s)
        }
        for (s in SettingId.entries) assertTrue(BriefFormat.actionTaken(ActionTaken(0, ActionId.SETTING, mapOf("setting" to s.wire, "value" to "false")), IdTitles).startsWith("You "))
    }

    @Test fun everyKindHasAHeadingAndLabel() {
        for (k in RecommendationKind.entries) assertTrue(BriefFormat.kindLabel(k).first().isUpperCase())
        for (k in SkillKind.entries) assertTrue(BriefFormat.skillLabel(k).isNotEmpty())
        assertEquals(listOf("Session", "Today", "Week"), Period.entries.map(BriefFormat::periodLabel))
        assertEquals("Wed", BriefFormat.dayShort("2026-10-21")); assertEquals("W", BriefFormat.dayInitial("2026-10-21"))
        assertEquals("1 min", BriefFormat.minutes(1)); assertEquals("12 min", BriefFormat.minutes(12))
    }

    @Test fun hintAndNoiseLinesCountPlainly() {
        assertTrue(BriefFormat.hintLines(HintStats()).isEmpty())
        assertEquals(listOf("The key glowed: 1 time", "The app played the key: 2 times"), BriefFormat.hintLines(HintStats(glow = 1, play = 2)))
        assertTrue(BriefFormat.noisyLine(0).startsWith("No noisy")); assertTrue(BriefFormat.noisyLine(1).startsWith("1 noisy moment,")); assertTrue(BriefFormat.noisyLine(4).startsWith("4 noisy moments"))
    }

    // ---- chart maths ----

    @Test fun sparkPointsSpreadAcrossTheBoxAndKeepGaps() {
        val p = Spark.points(listOf(10.0, null, 20.0, 30.0), 100f, 50f, pad = 5f)
        assertEquals(4, p.size); assertNull(p[1])
        assertEquals(5f, p[0]!!.x, 0.01f); assertEquals(95f, p[3]!!.x, 0.01f)
        assertEquals(45f, p[0]!!.y, 0.01f); assertEquals(5f, p[3]!!.y, 0.01f)       // the highest value is at the top
        assertTrue(p[2]!!.y < p[0]!!.y)
    }

    @Test fun aFlatOrSingleSeriesSitsInTheMiddle() {
        val flat = Spark.points(listOf(5.0, 5.0, 5.0), 90f, 40f)
        assertTrue(flat.all { it!!.y == 20f })
        assertEquals(45f, Spark.points(listOf(7.0), 90f, 40f).single()!!.x, 0.01f)
        assertTrue(Spark.points(emptyList(), 90f, 40f).isEmpty())
        assertTrue(Spark.points(listOf(null, null), 90f, 40f).all { it == null })
    }

    @Test fun runsSplitAtGapsAndKeepLonePoints() {
        val p = Spark.points(listOf(1.0, 2.0, null, 3.0, null, null), 100f, 40f)
        assertEquals(listOf(2, 1), Spark.runs(p).map { it.size })
        assertTrue(Spark.runs(emptyList()).isEmpty())
    }

    @Test fun dayBarsAreScaledToTheBiggestDayOrTheGoal() {
        val days = listOf(DayStat("2026-10-19", 5, 4, 1, false), DayStat("2026-10-20", 0, 0, 0, false), DayStat("2026-10-21", 10, 8, 1, true))
        assertEquals(listOf(0.5f, 0f, 1f), Spark.dayFractions(days, 5))
        assertEquals(listOf(0.4f, 0f, 0.8f), Spark.dayFractions(days, 10))
        assertEquals(listOf(0f, 0f), Spark.dayFractions(listOf(DayStat("a", 0, 0, 0, false), DayStat("b", 0, 0, 0, false)), null))
        assertEquals(listOf(1f, 0.5f, 0f), Spark.shares(listOf(14, 7, 0)))
        assertEquals(listOf(0f), Spark.shares(listOf(0)))
    }

    @Test fun theSamplesAreWellFormed() {
        val now = 1_800_000_000_000L
        for (p in Period.entries) {
            val f = BriefSamples.kid(p, now, zone)
            assertFalse(f.isEmpty)
            assertEquals(7, f.stats.days.size)
            assertTrue(f.candidates.all { c -> c.params["lesson"]?.let { it in f.lessonIds() } != false })
            assertEquals(f, Findings.decode(f.encode()))
        }
        assertTrue(BriefSamples.empty(Period.WEEK, now).isEmpty)
    }
}
