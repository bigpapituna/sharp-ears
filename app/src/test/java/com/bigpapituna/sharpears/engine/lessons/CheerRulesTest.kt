package com.bigpapituna.sharpears.engine.lessons

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Note
import com.bigpapituna.sharpears.engine.tester.CheerPopEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.13, the cheer pops' rules (engine/lessons/CheerRules.kt): every trigger, every rate limit, the lines, the sounds. */
class CheerRulesTest {
    private fun coach(kid: Boolean = false, f: CheerFrequency = CheerFrequency.OFTEN, seed: Long = 7, memory: CheerMemory = CheerMemory()) =
        CheerCoach(kid, f, seed, memory).also { it.stepBegan("lesson-1", 0) }
    private fun at(ms: Long, busy: Boolean = false, tail: Boolean = false) = CheerMoment(ms, busy, tail)

    // ---- Triggers ----

    @Test fun runsPopAtFiveTenTwentyAndFortyWithTheNumber() {
        val c = coach()
        val pops = mutableListOf<Cheer>()
        // A note every 4 s: never inside a wait, never four in a minute.
        for (n in 1..45) c.right(at(n * 4_000L))?.let { pops += it }
        assertEquals(listOf(5, 10, 20, 40), pops.map { it.count })
        assertTrue(pops.all { it.trigger == CheerTrigger.RUN })
        // 20 and 40 always say the number.
        assertTrue(pops[2].line, "20" in pops[2].line)
        assertTrue(pops[3].line, "40" in pops[3].line)
        // A kid hears it as a word.
        val k = coach(kid = true)
        val kidPops = (1..45).mapNotNull { k.right(at(it * 4_000L)) }
        assertTrue(kidPops[3].line, kidPops[3].line.contains("forty", ignoreCase = true))
        assertTrue(kidPops.none { p -> p.line.any { it.isDigit() } })
    }

    @Test fun aWrongKeyStartsTheRunAgain() {
        val c = coach()
        for (n in 1..4) assertNull(c.right(at(n * 4_000L)))
        c.wrong(at(17_000))
        for (n in 1..4) assertNull("no run of five yet", c.right(at(17_000 + n * 4_000L))?.takeIf { it.trigger == CheerTrigger.RUN })
        // The save at the third note took the slot; the run of five comes once the wait is over.
        assertEquals(CheerTrigger.RUN, c.right(at(45_000))?.trigger)
    }

    @Test fun threeRightNotesAfterASlipAreASave() {
        val c = coach()
        c.right(at(4_000)); c.right(at(5_000))
        c.wrong(at(6_000))
        assertNull(c.right(at(7_000)))
        assertNull(c.right(at(8_000)))
        val save = c.right(at(9_000))
        assertEquals(CheerTrigger.RECOVERY, save?.trigger)
        assertTrue(save!!.line in CheerLines.family(CheerTrigger.RECOVERY, kid = false))
        // Once per slip: the fourth note says nothing more.
        assertNull(c.right(at(30_000)))
        // A second slip before three right notes starts the count again.
        c.wrong(at(31_000)); c.right(at(32_000)); c.right(at(33_000)); c.wrong(at(34_000)); c.right(at(50_000)); c.right(at(51_000))
        assertEquals(CheerTrigger.RECOVERY, c.right(at(52_000))?.trigger)
    }

    @Test fun halfwayOnceInALongStepOnly() {
        val c = coach()
        assertNull(c.progress(at(10_000), 0.5, long = false))
        assertNull(c.progress(at(10_000), 0.4, long = true))
        assertEquals(CheerTrigger.HALFWAY, c.progress(at(10_000), 0.52, long = true)?.trigger)
        assertNull("once a step", c.progress(at(40_000), 0.55, long = true))
        val late = coach()
        assertNull("past the middle it is no longer news", late.progress(at(10_000), 0.7, long = true))
        // Held back by a wait, it may still come a little later in the step.
        val held = coach()
        held.right(at(4_000)); held.right(at(5_000)); held.right(at(6_000)); held.right(at(7_000)); held.right(at(8_000))   // the run of five pops
        assertNull(held.progress(at(10_000), 0.5, long = true))
        assertEquals(CheerTrigger.HALFWAY, held.progress(at(24_000), 0.6, long = true)?.trigger)
    }

    @Test fun aBarMissedEarlierAndPlayedCleanIsTheHardPartPassed() {
        val memory = CheerMemory()
        val c = coach(memory = memory)
        // First try: bar 1 goes astray.
        c.right(at(4_000), bar = 0); c.wrong(at(5_000), bar = 1); c.right(at(6_000), bar = 1); c.right(at(7_000), bar = 2)
        assertTrue(memory.isHard("lesson-1", 1))
        // The retry: bar 1 clean; leaving it is the pop.
        c.stepBegan("lesson-1", 20_000)
        assertNull(c.right(at(24_000), bar = 0))
        assertNull(c.right(at(25_000), bar = 1))
        assertNull(c.right(at(26_000), bar = 1))
        val passed = c.right(at(27_000), bar = 2)
        assertEquals(CheerTrigger.HARD_PASSED, passed?.trigger)
        assertFalse("cheered once", memory.isHard("lesson-1", 1))
        // Not cheered when the bar went astray again on the way through.
        c.wrong(at(40_000), bar = 3)
        c.stepBegan("lesson-1", 60_000)
        c.right(at(64_000), bar = 3); c.wrong(at(65_000), bar = 3); c.right(at(66_000), bar = 3)
        assertNull(c.right(at(80_000), bar = 4)?.takeIf { it.trigger == CheerTrigger.HARD_PASSED })
        // The session's memory outlives the screen: the next visit to the same part may cheer it.
        memory.mark("song-x", 5)
        val next = coach(memory = memory).also { it.stepBegan("song-x", 100_000) }
        next.right(at(104_000), bar = 5)
        assertEquals(CheerTrigger.HARD_PASSED, next.right(at(105_000), bar = 6)?.trigger)
    }

    /**
     * A one-bar loop never moves to another bar: each wrap ends the pass, and the bar played last is judged then. A hard part
     * passed while a pop is not allowed stays hard, to be earned on a later clean pass.
     */
    @Test fun aLoopsWrapJudgesItsBarAndAHeldBackPassStaysHard() {
        val memory = CheerMemory()
        val c = coach(memory = memory)
        // Pass 1 of a loop on bar 4: a slip in it.
        c.right(at(4_000), bar = 4); c.wrong(at(5_000), bar = 4); c.right(at(6_000), bar = 4)
        assertNull(c.passEnded(at(7_000)))
        assertTrue(memory.isHard("lesson-1", 4))
        // Pass 2: clean (its third note is the save, which pops); the wrap, once the wait is over, is the hard part passed.
        c.right(at(8_000), bar = 4); assertEquals(CheerTrigger.RECOVERY, c.right(at(9_000), bar = 4)?.trigger)
        assertEquals(CheerTrigger.HARD_PASSED, c.passEnded(at(25_000))?.trigger)
        assertFalse(memory.isHard("lesson-1", 4))
        // Held back by the wait after a pop: still hard, and earned on the next clean pass.
        val d = coach(memory = memory)
        memory.mark("lesson-1", 2)
        d.forced(CheerTrigger.RUN, 5_000)
        d.right(at(6_000), bar = 2)
        assertNull("the wait holds it back", d.right(at(7_000), bar = 3))
        assertTrue(memory.isHard("lesson-1", 2))
        d.right(at(30_000), bar = 2)
        assertEquals(CheerTrigger.HARD_PASSED, d.right(at(31_000), bar = 3)?.trigger)
        assertFalse(memory.isHard("lesson-1", 2))
        // Nothing played since the last pass: nothing to judge.
        assertNull(coach(memory = memory).passEnded(at(50_000)))
    }

    @Test fun eightNotesInTheGoodWindowAreSteadyTiming() {
        val c = coach()
        // Spread past the run pops' waits: run pops at notes 5 (t 8 s) take the slot; steady at 8 needs 15 s after it.
        val got = (1..8).mapNotNull { n -> c.right(at(if (n < 8) 3_000L + n * 500 else 30_000), onTime = true) }
        assertEquals(listOf(CheerTrigger.RUN, CheerTrigger.STEADY), got.map { it.trigger })
        // A note in the ok window starts the steady count again; untimed notes leave it alone.
        val d = coach()
        for (n in 1..7) d.right(at(1_000L + n * 100), onTime = true)
        d.right(at(1_900), onTime = false)
        for (n in 1..7) assertNull(d.right(at(60_000L + n * 100), onTime = true)?.takeIf { it.trigger == CheerTrigger.STEADY })
        assertEquals(CheerTrigger.STEADY, d.right(at(80_000), onTime = true)?.trigger)
        val e = coach()
        for (n in 1..12) assertNotEquals(CheerTrigger.STEADY, e.right(at(30_000L * n), onTime = null)?.trigger)
    }

    @Test fun fiveMinutesOfPracticeIsAFocusPopEveryFiveMinutes() {
        val c = coach()
        val pops = mutableListOf<Pair<Long, Cheer>>()
        var t = 0L
        while (t <= 11 * 60_000L) { c.tick(at(t), running = true)?.let { pops += t to it }; if (t % 9_000L == 0L) c.right(at(t + 1)); t += 500 }
        val focus = pops.filter { it.second.trigger == CheerTrigger.FOCUS }
        assertEquals(2, focus.size)
        assertEquals(5 * 60_000L, focus[0].first)
        assertTrue(focus[0].second.line, focus[0].second.line.startsWith("Five minutes"))
        assertTrue(focus[1].second.line, focus[1].second.line.startsWith("Ten minutes"))
        // A break of over two minutes starts the count again; a shorter one does not count as practice but keeps it.
        val d = coach()
        var u = 0L
        while (u < 4 * 60_000L) { d.tick(at(u), running = true); u += 500 }
        u += 3 * 60_000L
        var first: Long? = null
        while (u < 14 * 60_000L && first == null) { if (d.tick(at(u), running = true)?.trigger == CheerTrigger.FOCUS) first = u; u += 500 }
        assertEquals(7 * 60_000L + 5 * 60_000L, first)
        // Not running (paused, a listening step): no practice time.
        val e = coach()
        var w = 0L
        while (w < 6 * 60_000L) { assertNull(e.tick(at(w), running = false)); w += 500 }
    }

    @Test fun tenSecondsWithNothingPlayedIsOneNudgeAStep() {
        val c = coach()
        val got = (0..80).mapNotNull { i -> c.tick(at(i * 500L), running = true)?.let { i * 500L to it } }
        assertEquals(listOf(CheerTrigger.NUDGE), got.map { it.second.trigger })
        assertEquals(10_000L, got.single().first)
        // Something played starts the stall again; a demo, a hint or the voice holds it.
        val d = coach()
        for (i in 0..16) d.tick(at(i * 500L), running = true)
        d.right(at(8_100))
        for (i in 17..30) assertNull(d.tick(at(i * 500L), running = true))
        for (i in 31..60) assertNull(d.tick(at(i * 500L, busy = true), running = true))
        assertNull(d.tick(at(30_500), running = true))
        assertEquals(CheerTrigger.NUDGE, (62..90).firstNotNullOfOrNull { d.tick(at(it * 500L), running = true) }?.trigger)
        // A new step may nudge again.
        d.stepBegan("lesson-2", 50_000)
        assertEquals(CheerTrigger.NUDGE, (101..140).firstNotNullOfOrNull { d.tick(at(it * 500L), running = true) }?.trigger)
        // The kid's words: Roy's line is one of them.
        assertTrue("Find the next key, I'm right here." in CheerLines.family(CheerTrigger.NUDGE, kid = true))
    }

    // ---- Rate limits ----

    @Test fun neverInTheFirstThreeSecondsOfAStep() {
        val c = coach()
        for (n in 1..5) assertNull(c.right(at(n * 500L)))
        val d = coach()
        for (n in 1..4) d.right(at(n * 500L))
        assertNotNull(d.right(at(3_000)))
        // A fresh run (a retry) starts the quiet again.
        d.stepBegan("lesson-1", 100_000)
        for (n in 1..5) assertNull(d.right(at(100_000L + n * 500)))
    }

    @Test fun theWaitBetweenPopsIsFifteenSecondsForGrownUpsTwelveForKidsDoubleWithLess() {
        assertEquals(15_000L, CheerCoach.gapMs(kid = false, CheerFrequency.OFTEN))
        assertEquals(12_000L, CheerCoach.gapMs(kid = true, CheerFrequency.OFTEN))
        assertEquals(30_000L, CheerCoach.gapMs(kid = false, CheerFrequency.LESS))
        assertEquals(24_000L, CheerCoach.gapMs(kid = true, CheerFrequency.LESS))
        assertEquals(4, CheerCoach.perMinute(CheerFrequency.OFTEN))
        assertEquals(2, CheerCoach.perMinute(CheerFrequency.LESS))
        for ((kid, f) in listOf(false to CheerFrequency.OFTEN, true to CheerFrequency.OFTEN, false to CheerFrequency.LESS, true to CheerFrequency.LESS)) {
            val c = coach(kid = kid, f = f)
            c.forced(CheerTrigger.RUN, 10_000)
            val gap = CheerCoach.gapMs(kid, f)
            assertFalse("$kid $f", c.allowed(at(10_000 + gap - 1)))
            assertTrue("$kid $f", c.allowed(at(10_000 + gap)))
        }
    }

    @Test fun fourAMinuteAtMostTwoWithLess() {
        val c = coach(kid = true)
        for (t in listOf(3_000L, 15_000L, 27_000L, 39_000L)) { assertTrue(c.allowed(at(t))); c.forced(CheerTrigger.RUN, t) }
        assertFalse("a fifth in the minute", c.allowed(at(51_000)))
        assertTrue(c.allowed(at(63_000)))
        val less = coach(kid = true, f = CheerFrequency.LESS)
        for (t in listOf(3_000L, 27_000L)) { assertTrue(less.allowed(at(t))); less.forced(CheerTrigger.RUN, t) }
        assertFalse("a third in the minute", less.allowed(at(51_000)))
        assertTrue(less.allowed(at(63_000)))
    }

    @Test fun neverOverTheVoiceOrADemoNorInTheTailNorWhenOff() {
        val c = coach()
        for (n in 1..4) c.right(at(n * 1_000L))
        assertNull(c.right(at(5_000, busy = true)))
        val d = coach()
        for (n in 1..4) d.right(at(n * 1_000L))
        assertNull(d.right(at(5_000, tail = true)))
        val off = coach(f = CheerFrequency.OFF)
        var t = 0L
        repeat(2_000) { assertNull(off.right(at(t))); assertNull(off.tick(at(t), running = true)); assertNull(off.progress(at(t), 0.5, true)); t += 500 }
        assertNull("Off keeps the step pop but not its line", off.stepPassed(5_000))
    }

    @Test fun aStepPassedGivesTheStepPopItsLineWithoutAWait() {
        val c = coach(kid = true)
        val s = c.stepPassed(100)
        assertEquals(CheerTrigger.STEP_PASSED, s?.trigger)
        assertTrue(s!!.line in CheerLines.family(CheerTrigger.STEP_PASSED, kid = true))
        // It counts as a pop for the next one's wait.
        assertFalse(c.allowed(at(5_000)))
        assertTrue(c.allowed(at(12_100)))
    }

    // ---- Lines, entries, holds ----

    @Test fun neverOneOfTheLastSixLinesAndTheSameSeedTheSameLines() {
        for (kid in listOf(true, false)) for (t in CheerTrigger.entries) {
            val c = coach(kid = kid, seed = 11)
            val lines = (0 until 200).map { c.forced(t, 1_000L * it).line }
            for (i in lines.indices) assertFalse("$t $kid ${lines[i]}", lines[i] in lines.subList(maxOf(0, i - CheerCoach.RECENT), i))
            val again = coach(kid = kid, seed = 11)
            assertEquals(lines, (0 until 200).map { again.forced(t, 1_000L * it).line })
        }
        // The last six are across families: a recovery after five runs avoids them all too.
        val c = coach(seed = 3)
        val shown = (0 until 6).map { c.forced(CheerTrigger.RECOVERY, 1_000L * it).line }
        assertEquals(shown, c.lines)
        val other = coach(seed = 4)
        assertNotEquals(shown, (0 until 6).map { other.forced(CheerTrigger.RECOVERY, 1_000L * it).line })
    }

    @Test fun theEntryNeverRepeatsAndTheSideAlternates() {
        val c = coach(seed = 5)
        val pops = (0 until 60).map { c.forced(CheerTrigger.RUN, 1_000L * it) }
        for (i in 1 until pops.size) {
            assertNotEquals(pops[i - 1].entry, pops[i].entry)
            assertNotEquals(pops[i - 1].other, pops[i].other)
        }
        assertEquals(CheerEntry.entries.toSet(), pops.map { it.entry }.toSet())
    }

    @Test fun aPopStaysOnePointSixSecondsLongerLinesUpToTwoPointFour() {
        assertEquals(1_600L, CheerCoach.holdMs("Nice save."))
        assertEquals(2_400L, CheerCoach.holdMs("Don't stop. You're doing really well, keep it up."))
        for (kid in listOf(true, false)) for (l in CheerLines.all(kid)) assertTrue(l, CheerCoach.holdMs(l) in 1_600L..2_400L)
    }

    // ---- Sounds and the voice ----

    @Test fun theSparkleAndTheVoiceFollowTheMicRule() {
        // The voice: a kid's, or a grown-up's with Say it aloud; never over the learner's turn while the mic listens through the speaker.
        assertEquals(ChimeMode.SKIP, CheerRules.voiceMode(spoken = false, micListening = false, headphones = false, betweenNotes = true))
        assertEquals(ChimeMode.PLAY, CheerRules.voiceMode(true, micListening = false, headphones = false, betweenNotes = false))
        assertEquals(ChimeMode.PLAY, CheerRules.voiceMode(true, micListening = true, headphones = true, betweenNotes = false))
        assertEquals(ChimeMode.SKIP, CheerRules.voiceMode(true, micListening = true, headphones = false, betweenNotes = false))
        assertEquals(ChimeMode.PLAY_DEAF, CheerRules.voiceMode(true, micListening = true, headphones = false, betweenNotes = true))
        // The sparkle: App sound and the step pop's chime first, then the same rule.
        assertEquals(ChimeMode.SKIP, CheerRules.soundMode(soundOn = false, chiming = false, micListening = false, headphones = false, betweenNotes = true))
        assertEquals(ChimeMode.SKIP, CheerRules.soundMode(soundOn = true, chiming = true, micListening = false, headphones = false, betweenNotes = true))
        assertEquals(ChimeMode.PLAY, CheerRules.soundMode(true, false, micListening = false, headphones = false, betweenNotes = false))
        assertEquals(ChimeMode.SKIP, CheerRules.soundMode(true, false, micListening = true, headphones = false, betweenNotes = false))
        assertEquals(ChimeMode.PLAY_DEAF, CheerRules.soundMode(true, false, micListening = true, headphones = false, betweenNotes = true))
        assertEquals(ChimeMode.PLAY, CheerRules.soundMode(true, false, micListening = true, headphones = true, betweenNotes = false))
        // Kids hear it by default, grown-ups only once they turn it on.
        assertTrue(CheerRules.aloud(kid = true, aloud = null))
        assertFalse(CheerRules.aloud(kid = false, aloud = null))
        assertTrue(CheerRules.aloud(kid = false, aloud = true))
        assertFalse(CheerRules.aloud(kid = true, aloud = false))
    }

    @Test fun theSparkleIsTwoSoftHighNotesInKey() {
        val c = CheerRules.sparkle(0)
        assertEquals(listOf(91, 96), c.map { it.midi })            // G6, C7
        assertTrue(c.all { it.velocity == 55 && it.velocity < StepPopRules.VELOCITIES.min() })
        assertEquals(listOf(0, CheerRules.SPARKLE_GAP_MS), c.map { it.atMs })
        assertEquals(listOf(93, 98), CheerRules.sparkle(2).map { it.midi })   // D: A6, D7
        for (pc in 0..11) assertTrue(CheerRules.sparkle(pc).all { it.midi in 84..108 })
        assertEquals(CheerRules.SPARKLE_MS + StepPopRules.ROOM_TAIL_MS, CheerRules.SPARKLE_DEAF_MS)
    }

    // ---- Where ----

    @Test fun thePupFitsOrThePopIsSkipped() {
        assertEquals(56f, CheerPlace.pupDp(90f))
        assertEquals(48f, CheerPlace.pupDp(90f, maxDp = 48f))
        assertEquals(40f, CheerPlace.pupDp(48f))
        assertNull(CheerPlace.pupDp(40f))
        assertEquals(72f, CheerPlace.laneHeightDp(200f), 0.01f)
    }

    @Test fun aSongsPopTakesTheCornerWithFewerNotesComingThrough() {
        // C3..C6; a tune up high on the right, nothing low.
        val notes = (0 until 20).map { Note(79 + it % 5, it * 400L, 380L, Hand.RIGHT, 80) }
        val both = setOf(CheerSide.START, CheerSide.END)
        assertEquals(CheerSide.START, CheerPlace.laneSides(notes, 48, 84, 0.0, 1.0, 1_600, 0.3f, both, preferStart = false).first())
        // Nothing coming either side: the pops alternate.
        assertEquals(CheerSide.END, CheerPlace.laneSides(emptyList(), 48, 84, 0.0, 1.0, 1_600, 0.3f, both, preferStart = false).first())
        assertEquals(CheerSide.START, CheerPlace.laneSides(emptyList(), 48, 84, 0.0, 1.0, 1_600, 0.3f, both, preferStart = true).first())
        // The chord names take the left corner: the right it is, notes or not; with the video too, no room.
        assertEquals(listOf(CheerSide.END), CheerPlace.laneSides(notes, 48, 84, 0.0, 1.0, 1_600, 0.3f, setOf(CheerSide.END), preferStart = true))
        assertTrue(CheerPlace.laneSides(notes, 48, 84, 0.0, 1.0, 1_600, 0.3f, emptySet(), preferStart = true).isEmpty())
        // Notes long gone or far ahead do not count.
        val later = listOf(Note(83, 60_000, 400, Hand.RIGHT, 80))
        assertEquals(CheerSide.END, CheerPlace.laneSides(later, 48, 84, 0.0, 1.0, 1_600, 0.3f, both, preferStart = false).first())
    }

    // ---- Settings and the tester event ----

    @Test fun oftenIsTheDefaultLessAndOffTheOthers() {
        assertEquals(CheerFrequency.OFTEN, CheerFrequency.DEFAULT)
        assertEquals(listOf("Often", "Less", "Off"), CheerFrequency.entries.map { it.title })
        assertEquals(CheerFrequency.LESS, CheerFrequency.parse("less"))
        assertEquals(CheerFrequency.OFF, CheerFrequency.parse("off"))
        assertEquals(CheerFrequency.OFTEN, CheerFrequency.parse(null))
        assertEquals(CheerFrequency.OFTEN, CheerFrequency.parse("sometimes"))
    }

    @Test fun theTesterEventCarriesTheFamilyAndTheAudienceOnly() {
        val e = CheerPopEvent.of(CheerTrigger.HARD_PASSED, kid = true)
        assertEquals("cheer_pop", e.name)
        assertEquals(mapOf("trigger" to "hard_passed", "profile_kind" to "kid"), e.props)
        assertEquals("adult", CheerPopEvent.of(CheerTrigger.RUN, kid = false).props["profile_kind"])
        assertEquals(CheerTrigger.entries.size, CheerTrigger.entries.map { it.wire }.toSet().size)
    }
}
