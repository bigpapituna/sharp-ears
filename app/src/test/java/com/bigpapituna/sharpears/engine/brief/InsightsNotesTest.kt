package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The email brief's extra data in the insights engine: the note-by-note measures of each run (RunDetail) and the wrong-key
 * pairs become Stats (the slip map, the most common slip, notes never played, hesitation, tempo steadiness, each hand,
 * last period's hints, claps, ear games by game, weekly minutes, session length, the mix), three facts, two trends, two
 * flags (HESITATES_ON, LEFT_HAND_LAGS) and two praise kinds (STEADIER_TEMPO, FINDS_KEYS_FASTER).
 */
class InsightsNotesTest {
    private val mia = Profile.kid("Mia", 1, reader = false, id = "p1")    // a kid: colour notes on by default
    private val roy = Profile.owner()                                     // a grown-up: colour notes off
    private fun d(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    private fun week(events: List<BriefEvent>, p: Profile = mia) = Insights.compute(events, p, Period.WEEK, NOW, zone = ZONE)
    private fun Findings.flag(k: FlagKind) = flags.firstOrNull { it.kind == k }
    private fun Findings.fact(k: FactKind) = facts.firstOrNull { it.kind == k }
    private fun Findings.trend(k: TrendKind) = trends.firstOrNull { it.kind == k }
    private fun Findings.praise(k: PraiseKind) = praise.firstOrNull { it.kind == k }

    private fun waits(meanMs: Int, n: Int, slow: List<KeyMs> = emptyList(), skipped: Int = 0,
                      left: HandNotes = HandNotes(), right: HandNotes = HandNotes(n, n, meanMs, n)) =
        RunDetail(skippedNotes = skipped, hesitationMs = meanMs, hesitationN = n, slowKeys = slow, left = left, right = right)

    @Test fun eachRunsNotesBecomeTheFullReportsStats() {
        val f = week(history {
            session(d(15), 18, 20) { step("pond-1", notes = 12, ms = 120_000, detail = waits(800, 12, listOf(KeyMs(67, 2_400), KeyMs(60, 900)), skipped = 2)) }
            session(d(17), 18, 20) {
                step("pond-2", notes = 10, ms = 180_000, detail = waits(1_400, 10, listOf(KeyMs(67, 2_000), KeyMs(64, 1_800)),
                    left = HandNotes(4, 3, 2_000, 4), right = HandNotes(6, 6, 1_000, 6)))
                wrongKey(62, 64, 3, lesson = "pond-2"); wrongKey(62, 61, 1, lesson = "pond-2"); wrongKey(67, 55, 2, lesson = "pond-2")
                step("pond-4", game = "CLAP", notes = 14, rounds = 16, score = 87)
            }
            session(d(19), 18, 20) {
                song("twinkle", mode = RunMode.PERFORM, accuracy = 90, notes = 30, detail = RunDetail(spreadMs = 40, skippedNotes = 1,
                    right = HandNotes(31, 30)))
                song("ode-to-joy", mode = RunMode.PERFORM, accuracy = 80, notes = 10, detail = RunDetail(spreadMs = 80))
                wrongKey(62, 64, 2, song = "twinkle")
                toy(ToyKind.JAM, 3)
                ear("pitch", true); ear("pitch", false); ear("chord:build", true)
            }
        })
        val s = f.stats
        assertEquals(listOf(KeySlips(62, 6, 0), KeySlips(67, 0, 2)), s.keySlips)
        assertEquals(SlipPair(62, 64, 5), s.topSlip)
        assertEquals(3, s.skippedNotes)
        assertEquals(12 + 10 + 31, s.notesDue)
        assertEquals("(800 x 12 + 1400 x 10) / 22, rounded; G's slowest finds were 2.4 and 2.0 s", HesitationStat(1_073, 22, worstKey = 67, worstMs = 2_200), s.hesitation)
        assertEquals("weighted by the notes graded", (40 * 30 + 80 * 10) / 40, s.tempoSpreadMs); assertEquals(2, s.tempoRuns)
        assertEquals(HandStat(4, null, 2_000), s.left)                      // under eight notes: no clean percent yet
        assertEquals("48 of 49 clean; (800 x 12 + 1000 x 6) / 18", HandStat(49, 98, 867), s.right)
        assertEquals(ClapStat(14, 16, 1), s.clap)
        assertEquals(listOf(EarStat("pitch", 2, 1), EarStat("chord:build", 1, 1)), s.ear)
        assertEquals(4, s.weeks.size); assertEquals(60, s.weeks.last())
        assertEquals(20, s.sessionMinutes); assertNull("a new child has no sessions before", s.sessionMinutesBefore)
        assertEquals(PlayMix(lessons = 2 + 3 + 1, songs = 3, toys = 3), s.mix)
        assertEquals(Fact(FactKind.SKIPPED_NOTES, 3, 53), f.fact(FactKind.SKIPPED_NOTES))
        assertEquals(Fact(FactKind.HESITATION, 1_073, 22), f.fact(FactKind.HESITATION))
        assertEquals(Fact(FactKind.TEMPO_SPREAD, 50, 2), f.fact(FactKind.TEMPO_SPREAD))
        assertEquals("the new facts come after the old ones", FactKind.TEMPO_SPREAD, f.facts.last().kind)
        assertEquals(f, Findings.decode(f.encode()))
    }

    @Test fun aKeyPausedOnAgainAndAgainIsFlaggedButASlowDayIsNot() {
        fun slowOnG(p: Profile, usual: Int) = week(history {
            for (day in 16..20) session(d(day), 18, 10) {
                step("pond-3", detail = waits(usual, 8, listOf(KeyMs(67, 3_000), KeyMs(60, usual))))
            }
        }, p)
        val f = slowOnG(mia, 700)
        assertEquals(Flag(FlagKind.HESITATES_ON, 67, 3_000, lessons = listOf("pond-3")), f.flag(FlagKind.HESITATES_ON))
        // Mia's notes are already coloured: the grown-up sits in. Roy's are not: colour them first.
        assertEquals(listOf(Candidate(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, mapOf("lesson" to "pond-3"), FlagKind.HESITATES_ON)),
            f.candidates.filter { it.because == FlagKind.HESITATES_ON })
        assertEquals(listOf(ActionId.SETTING, ActionId.PRACTISE_TOGETHER),
            slowOnG(roy, 700).candidates.filter { it.because == FlagKind.HESITATES_ON }.map { it.action })
        assertNull("every key slow: no one key to name", slowOnG(mia, 2_000).flag(FlagKind.HESITATES_ON))
        val twice = week(history {
            for (day in 19..20) session(d(day), 18, 10) { step("pond-3", detail = waits(700, 10, listOf(KeyMs(67, 3_000)))) }
        })
        assertNull("two runs are not a habit", twice.flag(FlagKind.HESITATES_ON))
    }

    @Test fun aLeftHandSlowToFindItsKeysIsFlaggedUnlessItsHitRateIsAlreadyBehind() {
        fun lagging(leftHits: Int) = week(history {
            for (day in 18..20) session(d(day), 18, 10) {
                song("minuet-g", hands = Hands.BOTH, notes = 20, detail = waits(0, 0, left = HandNotes(6, 6, 2_100, 6), right = HandNotes(14, 14, 800, 14)).copy(hesitationMs = 1_190, hesitationN = 20))
                song("scale-left", hands = Hands.LEFT, notes = leftHits, near = 20 - leftHits)
                song("scale-right", hands = Hands.RIGHT, notes = 20)
            }
        })
        val f = lagging(leftHits = 19)
        assertEquals(Flag(FlagKind.LEFT_HAND_LAGS, 2_100, 800, songs = listOf("minuet-g")), f.flag(FlagKind.LEFT_HAND_LAGS))
        assertTrue(f.candidates.any { it.because == FlagKind.LEFT_HAND_LAGS && it.params == mapOf("song" to "minuet-g") })
        val behind = lagging(leftHits = 8)
        assertNotNull(behind.flag(FlagKind.LEFT_HAND_BEHIND))
        assertNull("one left-hand flag at a time", behind.flag(FlagKind.LEFT_HAND_LAGS))
    }

    @Test fun aSteadierBeatAndQuickerKeysAreTrendsAndPraiseInTheirPlace() {
        val f = week(history {
            for (day in 8..12) session(d(day), 18, 10) {
                song("twinkle", mode = RunMode.PERFORM, accuracy = 80, notes = 20, detail = RunDetail(spreadMs = 70))
                step("pond-1", detail = waits(2_000, 12))
            }
            for (day in 16..20) session(d(day), 18, 10) {
                song("twinkle", mode = RunMode.PERFORM, accuracy = 80, notes = 20, detail = RunDetail(spreadMs = 35))
                step("pond-1", detail = waits(900, 12))
            }
        })
        assertEquals(Trend(TrendKind.TEMPO_SPREAD, 35.0, 70.0, Direction.DOWN, true, 1), f.trend(TrendKind.TEMPO_SPREAD))
        assertEquals(Trend(TrendKind.HESITATION_MS, 900.0, 2_000.0, Direction.DOWN, true, 1), f.trend(TrendKind.HESITATION_MS))
        assertEquals(Praise(PraiseKind.STEADIER_TEMPO, 35), f.praise(PraiseKind.STEADIER_TEMPO))
        assertEquals(Praise(PraiseKind.FINDS_KEYS_FASTER, 900), f.praise(PraiseKind.FINDS_KEYS_FASTER))
        val order = f.praise.map { it.kind }
        assertEquals(order.sortedBy { PRAISE_ORDER.indexOf(it) }, order)
        assertTrue("praise order covers every kind", PRAISE_ORDER.toSet() == PraiseKind.entries.toSet() && PRAISE_ORDER.size == PraiseKind.entries.size)
    }

    @Test fun lastPeriodsHintsComeAlongAndAWaitListCutShortCountsItsWaitsAll() {
        val f = week(history {
            session(d(12), 18, 10) { step("pond-1", hints = listOf(2, 2, 3)) }
            session(d(20), 18, 10) {
                step("pond-2", hints = List(32) { HintDepth.GLOW }, detail = RunDetail(hintSteps = listOf(40, 3, 1)))
                song("twinkle", hints = listOf(HintDepth.PLAY))
            }
        })
        assertEquals(HintStats(glow = 40, point = 3, play = 2), f.stats.hints)
        assertEquals(HintStats(point = 2, play = 1), f.stats.hintsBefore)
        assertEquals(Fact(FactKind.HINTS, 2, 45), f.fact(FactKind.HINTS))
        assertNull("no playing the week before: no comparison", week(history { session(d(20), 18, 10) { step("pond-2", hints = listOf(1)) } }).stats.hintsBefore)
    }
}
