package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.pageOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The insights engine on synthetic histories: one test per rule (what makes it fire and what keeps it quiet), the adult
 * variant, the periods, and a property test over random histories (Findings never name a lesson, song or page that is
 * not in the events; the same input gives the same findings; they survive encode and decode).
 */
class InsightsTest {
    private val mia = Profile.kid("Mia", 1, reader = false, id = "p1")
    private val leo = Profile.kid("Leo", 2, reader = true, id = "p2")
    private val roy = Profile.owner()
    private fun d(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    private fun week(events: List<BriefEvent>, p: Profile = mia, current: CurrentSettings = CurrentSettings()) =
        Insights.compute(events, p, Period.WEEK, NOW, zone = ZONE, current = current)

    private fun Findings.flag(k: FlagKind) = flags.firstOrNull { it.kind == k }
    private fun Findings.fact(k: FactKind) = facts.firstOrNull { it.kind == k }
    private fun Findings.praise(k: PraiseKind) = praise.firstOrNull { it.kind == k }
    private fun Findings.trend(k: TrendKind) = trends.firstOrNull { it.kind == k }
    private fun Findings.candidate(a: ActionId, vararg p: Pair<String, String>) = candidates.firstOrNull { it.action == a && it.params == p.toMap() }

    // ---- practice habit ---------------------------------------------------------------------------------------------

    @Test fun practiceHabitCountsMinutesDaysSessionsAndTheBestTimeOfDay() {
        val f = week(history {
            session(d(15), 19, 15) { step("pond-1") }
            session(d(17), 18, 20) { step("pond-2") }
            session(d(19), 19, 10, minute = 30) { step("pond-3") }
            session(d(20), 17, 15, minute = 30) { step("pond-4") }
            session(d(21), 18, 10) { step("pond-5") }
            session(d(14), 19, 30)                               // the day before the week: not counted
        })
        assertEquals(TimeRange(at(d(15), 0), NOW), f.range)
        assertEquals(Fact(FactKind.PRACTICE_MINUTES, 70, 70), f.facts[0])
        assertEquals(Fact(FactKind.DAYS_PRACTISED, 5, 7), f.facts[1])
        assertEquals(Fact(FactKind.SESSIONS, 5), f.fact(FactKind.SESSIONS))
        assertEquals(Fact(FactKind.MOSTLY_EVENING, 70, 70), f.fact(FactKind.MOSTLY_EVENING))
        assertEquals(Praise(PraiseKind.CONSISTENT, 5), f.praise(PraiseKind.CONSISTENT))
        assertEquals(70, f.stats.timeOfDay.first { it.part == DayPart.EVENING }.minutes)
    }

    @Test fun consistencyIsComparedWithTheChildsOwnEarlierWeeks() {
        val f = week(history {
            session(d(2), 18, 10); session(d(5), 18, 10)          // two weeks back: 2 days
            session(d(9), 18, 10); session(d(12), 18, 10)         // last week: 2 days
            for (day in 16..20) session(d(day), 18, 12) { step("pond-1") }
        })
        val days = f.trend(TrendKind.DAYS)!!
        assertEquals(5.0, days.now, 0.0); assertEquals(2.0, days.before, 0.0)
        assertEquals(Direction.UP, days.direction); assertEquals(true, days.good); assertEquals(2, days.periods)
        assertEquals(Direction.UP, f.trend(TrendKind.MINUTES)!!.direction)
        assertNotNull(f.praise(PraiseKind.CONSISTENT))
    }

    @Test fun aNewChildHasNoTrendsBecauseThereIsNoPastToCompareWith() {
        val f = week(history { session(d(20), 18, 10) { step("playground-1") }; session(d(21), 18, 10) })
        assertTrue(f.trends.isEmpty())
        assertNull(f.flag(FlagKind.PRACTICE_DIPPED))
    }

    @Test fun aWeekWellUnderTheChildsUsualIsFlaggedWithATogetherSuggestion() {
        val f = week(history {
            for (start in listOf(1, 8)) for (k in 0 until 5) session(d(start + k), 18, 10)   // 5 days a week before
            session(d(20), 18, 10)
        })
        val flag = f.flag(FlagKind.PRACTICE_DIPPED)!!
        assertEquals(1L, flag.value); assertEquals(5L, flag.of)
        assertEquals(RecommendationKind.ROUTINE, f.candidate(ActionId.PRACTISE_TOGETHER)!!.recommendation)
        assertEquals(false, f.trend(TrendKind.DAYS)!!.good)
    }

    // ---- stuck --------------------------------------------------------------------------------------------------------

    @Test fun aLessonLetGoIsStuckWithTwoWaysToHelp() {
        val f = week(history { session(d(20), 18, 15) { step("pond-3", 2, tries = 3, letGo = true, stars = 1); done("pond-3", 1) } })
        assertEquals(Flag(FlagKind.STUCK_LESSON, 3, 1, lessons = listOf("pond-3")), f.flags.first())
        assertEquals(Candidate(RecommendationKind.UNSTICK, ActionId.SET_NEXT, mapOf("lesson" to "pond-3"), FlagKind.STUCK_LESSON), f.candidates[0])
        assertNotNull(f.candidate(ActionId.PRACTISE_TOGETHER, "lesson" to "pond-3"))
        assertNull("a stuck lesson is not also a stars-to-win-back lesson", f.flag(FlagKind.LOW_STARS))
    }

    @Test fun manyTriesThenThreeStarsIsPraiseNotAFlag() {
        val f = week(history { session(d(20), 18, 15) { step("pond-3", tries = 4, stars = 2); step("pond-3", 3); done("pond-3", 3) } })
        assertNull(f.flag(FlagKind.STUCK_LESSON))
        assertEquals(Praise(PraiseKind.STUCK_WITH_IT, 1, lessons = listOf("pond-3")), f.praise(PraiseKind.STUCK_WITH_IT))
    }

    @Test fun aLessonReplayedOnlyAtOneStarIsStuck() {
        val f = week(history {
            session(d(19), 18, 15) { done("hall-2", 1, first = true) }
            session(d(20), 18, 15) { done("hall-2", 1, first = false) }
        })
        assertEquals(listOf("hall-2"), f.flag(FlagKind.STUCK_LESSON)!!.lessons)
    }

    @Test fun aSongPlayedThreeTimesUnderTwoStarsIsStuckAndSuggestsADuet() {
        val f = week(history { session(d(20), 18, 20) { repeat(3) { song("twinkle", stars = 1) } } })
        assertEquals(Flag(FlagKind.STUCK_SONG, 3, songs = listOf("twinkle")), f.flag(FlagKind.STUCK_SONG))
        assertNotNull(f.candidate(ActionId.PRACTISE_TOGETHER, "song" to "twinkle"))
        val two = week(history { session(d(20), 18, 20) { song("twinkle", 1); song("twinkle", 1); song("twinkle", 2) } })
        assertNull(two.flag(FlagKind.STUCK_SONG))
    }

    @Test fun replayingInsteadOfMovingOn() {
        val f = week(history { session(d(20), 18, 20) { done("pond-1", 2, false); done("pond-2", 3, false); done("pond-1", 3, false) } })
        val flag = f.flag(FlagKind.REPLAYING_NOT_MOVING_ON)!!
        assertEquals(3L, flag.value); assertEquals(listOf("pond-1", "pond-2"), flag.lessons)
        assertEquals(Candidate(RecommendationKind.MOVE_ON, ActionId.SET_NEXT, emptyMap(), FlagKind.REPLAYING_NOT_MOVING_ON), f.candidate(ActionId.SET_NEXT))
        assertEquals(Fact(FactKind.LESSONS_REPLAYED, 3, lessons = listOf("pond-1", "pond-2")), f.fact(FactKind.LESSONS_REPLAYED))
        // One new lesson among the replays: moving on after all.
        val moving = week(history { session(d(20), 18, 20) { done("pond-1", 2, false); done("pond-2", 3, false); done("pond-1", 3, false); done("pond-3", 3) } })
        assertNull(moving.flag(FlagKind.REPLAYING_NOT_MOVING_ON))
    }

    // ---- skills -------------------------------------------------------------------------------------------------------

    @Test fun wrongKeysGettingCloserIsATrendAndPraise() {
        val f = week(history {
            session(d(10), 18, 15) { step("pond-1", wrong = 10, near = 1, far = 9) }
            session(d(20), 18, 15) { step("pond-1", wrong = 10, near = 8, far = 2) }
        })
        assertEquals(Fact(FactKind.NEAR_MISSES, 8, 10), f.fact(FactKind.NEAR_MISSES))
        val t = f.trend(TrendKind.NEAR_SHARE)!!
        assertEquals(80.0, t.now, 0.0); assertEquals(10.0, t.before, 0.0); assertEquals(true, t.good)
        assertEquals(Praise(PraiseKind.MISSES_CLOSER, 80), f.praise(PraiseKind.MISSES_CLOSER))
    }

    @Test fun timingAndAccuracyTrendsFromPerformRuns() {
        val f = week(history {
            session(d(10), 18, 15) { repeat(2) { song("ode", 2, RunMode.PERFORM, accuracy = 70, timing = -120) } }
            session(d(20), 18, 15) { song("ode", 3, RunMode.PERFORM, accuracy = 90, timing = 40); song("ode", 3, RunMode.PERFORM, accuracy = 92, timing = -40) }
        })
        assertEquals(Fact(FactKind.TIMING, 40, 2), f.fact(FactKind.TIMING))
        assertEquals(Direction.DOWN, f.trend(TrendKind.TIMING_MS)!!.direction)
        assertEquals(true, f.trend(TrendKind.TIMING_MS)!!.good)
        assertEquals(Praise(PraiseKind.TIMING_IMPROVED, 40), f.praise(PraiseKind.TIMING_IMPROVED))
        assertEquals(Praise(PraiseKind.ACCURACY_IMPROVED, 91), f.praise(PraiseKind.ACCURACY_IMPROVED))
    }

    @Test fun theLeftHandBehindTheRight() {
        val f = week(history {
            session(d(20), 18, 20) {
                song("minuet-g", 1, RunMode.PERFORM, Hands.LEFT, accuracy = 60); song("minuet-g", 2, RunMode.PERFORM, Hands.LEFT, accuracy = 62)
                song("minuet-g", 3, RunMode.PERFORM, Hands.RIGHT, accuracy = 95); song("ode", 3, RunMode.PERFORM, Hands.RIGHT, accuracy = 96)
            }
        })
        assertEquals(Fact(FactKind.HANDS, 61, 96), f.fact(FactKind.HANDS))
        assertEquals(Flag(FlagKind.LEFT_HAND_BEHIND, 61, 96, songs = listOf("minuet-g")), f.flag(FlagKind.LEFT_HAND_BEHIND))
        assertNotNull(f.candidate(ActionId.PRACTISE_TOGETHER, "song" to "minuet-g"))
        // Wait-mode runs count by notes against wrong keys: 20 notes, 0 wrong is 100.
        val even = week(history { session(d(20), 18, 20) { repeat(2) { song("a", hands = Hands.LEFT) }; repeat(2) { song("b", hands = Hands.RIGHT) } } })
        assertEquals(Fact(FactKind.HANDS, 100, 100), even.fact(FactKind.HANDS))
        assertNull(even.flag(FlagKind.LEFT_HAND_BEHIND))
    }

    @Test fun earGameCorrectnessAndItsTrend() {
        val f = week(history {
            session(d(10), 18, 10) { repeat(6) { ear("pitch", it < 2) } }
            session(d(20), 18, 10) { repeat(6) { ear("pitch", it < 5) } }
        })
        assertEquals(Fact(FactKind.EAR_ROUNDS, 5, 6), f.fact(FactKind.EAR_ROUNDS))
        assertEquals(Praise(PraiseKind.EAR_IMPROVED, 83), f.praise(PraiseKind.EAR_IMPROVED))
    }

    @Test fun clapAccuracyFromTheDrumSteps() {
        val f = week(history { session(d(20), 18, 10) { step("playground-7", game = "CLAP", score = 80); step("playground-7", 2, game = "CLAP", score = 60) } })
        assertEquals(Fact(FactKind.CLAP, 70, 2), f.fact(FactKind.CLAP))
    }

    @Test fun hintDependenceWhenTheAppOftenPlaysTheKey() {
        val f = week(history {
            session(d(20), 18, 20) {
                step("pond-2", hints = listOf(3, 3, 1)); step("pond-2", 2, hints = listOf(3, 3)); song("twinkle", hints = listOf(3))
            }
        })
        assertEquals(Flag(FlagKind.HINT_DEPENDENCE, 5, 6, lessons = listOf("pond-2"), songs = listOf("twinkle")), f.flag(FlagKind.HINT_DEPENDENCE))
        assertNotNull(f.candidate(ActionId.PRACTISE_TOGETHER, "lesson" to "pond-2"))
        assertEquals(HintStats(glow = 1, point = 0, play = 5), f.stats.hints)
        assertEquals(Fact(FactKind.HINTS, 5, 6), f.fact(FactKind.HINTS))
        // Glows and pointing are the hints doing their job: no flag.
        val light = week(history { session(d(20), 18, 20) { step("pond-2", hints = listOf(1, 2, 1, 2, 3)) } })
        assertNull(light.flag(FlagKind.HINT_DEPENDENCE))
    }

    @Test fun mostWrongKeysFarAwaySuggestsColourNotesWhenTheyAreOff() {
        val f = week(history { session(d(20), 18, 20) { step("find-c", wrong = 14, near = 2, far = 12) } }, roy)
        assertEquals(Flag(FlagKind.FAR_MISSES, 12, 14), f.flag(FlagKind.FAR_MISSES))
        assertNotNull(f.candidate(ActionId.SETTING, "setting" to "colourNotes", "value" to "true"))
        // A kid with colour notes on already: nothing to suggest.
        assertNull(week(history { session(d(20), 18, 20) { step("pond-1", wrong = 14, near = 2, far = 12) } }, mia).flag(FlagKind.FAR_MISSES))
    }

    // ---- readiness ----------------------------------------------------------------------------------------------------

    private fun letters(days: List<Int>, stars: Int = 3) = history {
        days.forEachIndexed { i, day -> session(d(day), 17, 15) { step(listOf("hall-1", "hall-2", "hall-3", "hall-4")[i % 4], stars = stars) } }
    }

    @Test fun aPreReaderPlayingTheLetterLessonsWellForTwoWeeksIsReadyForLetters() {
        assertEquals(setOf("hall-1", "hall-2", "hall-3", "hall-4"), Insights.LETTER_LESSONS)
        val f = week(letters(listOf(8, 12, 15, 20)))
        val flag = f.flag(FlagKind.READY_FOR_LETTERS)!!
        assertEquals(listOf("hall-1", "hall-2", "hall-3"), flag.lessons)
        assertEquals(Candidate(RecommendationKind.SETTINGS, ActionId.SETTING, mapOf("setting" to "reader", "value" to "true"), FlagKind.READY_FOR_LETTERS),
            f.candidate(ActionId.SETTING, "setting" to "reader", "value" to "true"))
        assertNull("a reader already reads", week(letters(listOf(8, 12, 15, 20)), leo).flag(FlagKind.READY_FOR_LETTERS))
        assertNull("one week is not two", week(letters(listOf(18, 19, 20, 21))).flag(FlagKind.READY_FOR_LETTERS))
        assertNull("one star is not ready", week(letters(listOf(8, 12, 15, 20), stars = 1)).flag(FlagKind.READY_FOR_LETTERS))
    }

    @Test fun theLastPageDoneIsReadyForTheRoadUntilTheRoadStarts() {
        val last = PLAYGROUND_PAGES.last()
        val done = history { session(d(20), 18, 15) { done(last.lastLessonId, 3); finale(last.id) } }
        val f = week(done)
        assertEquals(Flag(FlagKind.READY_FOR_ROAD, lessons = listOf(last.lastLessonId), page = last.id), f.flag(FlagKind.READY_FOR_ROAD))
        assertEquals(Candidate(RecommendationKind.NEXT_STEP, ActionId.SET_NEXT, emptyMap(), FlagKind.READY_FOR_ROAD), f.candidate(ActionId.SET_NEXT))
        assertEquals(Praise(PraiseKind.FINALE, page = last.id), f.praise(PraiseKind.FINALE))
        val onTheRoad = done + history { session(d(21), 18, 10) { step("sit") } }
        assertNull(week(onTheRoad).flag(FlagKind.READY_FOR_ROAD))
    }

    @Test fun cruisingKidsMayOpenTheNextPageEarly() {
        val f = week(history { session(d(20), 18, 20) { done("pond-1", 3); done("pond-2", 3); done("pond-3", 3) } })
        assertEquals(FlagKind.CRUISING, f.flag(FlagKind.CRUISING)!!.kind)
        assertEquals("pond", f.flag(FlagKind.CRUISING)!!.page)
        assertNotNull(f.candidate(ActionId.PACE_UNLOCK, "page" to "pond"))
        assertEquals(Praise(PraiseKind.THREE_STARS, 3, lessons = listOf("pond-3", "pond-2", "pond-1")), f.praise(PraiseKind.THREE_STARS))
        // A grown-up gets the flag but no page to unlock.
        val adult = week(history { session(d(20), 18, 20) { done("sit", 3); done("find-c", 3); done("fingers-right", 3) } }, roy)
        assertNotNull(adult.flag(FlagKind.CRUISING))
        assertNull(adult.candidates.firstOrNull { it.action == ActionId.PACE_UNLOCK })
    }

    @Test fun firstFinishesAtOneStarCanBeReplayedForStars() {
        val f = week(history { session(d(20), 18, 10) { done("pond-6", 1) } })
        assertEquals(Flag(FlagKind.LOW_STARS, 1, lessons = listOf("pond-6")), f.flag(FlagKind.LOW_STARS))
        assertNotNull(f.candidate(ActionId.REPLAY_FOR_STARS, "lesson" to "pond-6"))
    }

    @Test fun aSongMasteredInvitesANewOneFromTheGrownUp() {
        val songs = history { session(d(20), 18, 20) { repeat(3) { song("twinkle", 3) } } }
        val f = week(songs)
        assertEquals(Flag(FlagKind.MASTERED_SONG, 1, songs = listOf("twinkle")), f.flag(FlagKind.MASTERED_SONG))
        assertNotNull(f.candidate(ActionId.TEACHER_ASSIGN, "after" to "twinkle"))
        assertEquals(Praise(PraiseKind.CLEAN_SONG, 1, songs = listOf("twinkle")), f.praise(PraiseKind.CLEAN_SONG))
        assertNull(week(songs, roy).candidates.firstOrNull { it.action == ActionId.TEACHER_ASSIGN })
    }

    @Test fun aPageWithThreeWeakLessonsOffersAFreshStart() {
        val f = week(history { session(d(20), 18, 25) { done("pond-1", 1); done("pond-2", 1); step("pond-3", tries = 3, letGo = true, stars = 1) } })
        assertEquals("pond", f.flag(FlagKind.PAGE_STRUGGLE)!!.page)
        assertNotNull(f.candidate(ActionId.FRESH_START, "page" to "pond"))
    }

    @Test fun lessonsAllOverThePlaceWithFewStarsSuggestFocus() {
        val f = week(history {
            session(d(20), 18, 25) {
                step("playground-1", stars = 1); done("playground-1", 1); step("pond-1", stars = 1); done("pond-1", 1); step("hall-1", stars = 2); done("hall-1", 2)
            }
        })
        val flag = f.flag(FlagKind.SCATTERED)!!
        assertEquals(3L, flag.value); assertEquals("hall", flag.page)
        assertNotNull(f.candidate(ActionId.PACE_FOCUS, "page" to "hall"))
    }

    // ---- the room, the toys, the evening --------------------------------------------------------------------------

    @Test fun toyHeavySessions() {
        val f = week(history {
            session(d(19), 18, 10) { toy(ToyKind.JAM, 7) }
            session(d(20), 18, 10) { toy(ToyKind.PUP_SAYS, 4); toy(ToyKind.JAM, 3) }
            session(d(21), 18, 10) { step("pond-1") }
        })
        assertEquals(Flag(FlagKind.TOY_HEAVY, 2, 3), f.flag(FlagKind.TOY_HEAVY))
        assertEquals(Fact(FactKind.TOY_MINUTES, 14, 30), f.fact(FactKind.TOY_MINUTES))
        assertEquals(listOf(ToyStat(ToyKind.JAM, 2, 10), ToyStat(ToyKind.PUP_SAYS, 1, 4)), f.stats.toys)
        assertNotNull(f.candidate(ActionId.SET_NEXT))
    }

    private fun twice(screen: String) = history { session(d(19), 18, 10) { noisy(screen) }; session(d(20), 18, 10) { noisy(screen) } }

    @Test fun roomNoiseTwiceSuggestsTheQuietRoomSetting() {
        val f = week(twice("player") + history { session(d(21), 18, 10) { noisy("stage:KEY_HUNT") } })
        assertEquals(Flag(FlagKind.NOISY_ROOM, 3, 3), f.flag(FlagKind.NOISY_ROOM))
        assertEquals(3, f.stats.noisyRoom)
        assertNotNull(f.candidate(ActionId.SETTING, "setting" to "quietRoom", "value" to "true"))
        assertNull("an easier mic in a noisy room hears more noise", f.candidates.firstOrNull { it.params["setting"] == "micEasier" })
        // Wrong keys already not painted on this phone: nothing to switch, the flag stays for the writer.
        val off = week(twice("player"), current = CurrentSettings(wrongKeysShown = false))
        assertNotNull(off.flag(FlagKind.NOISY_ROOM))
        assertTrue(off.candidates.isEmpty())
        // Once is not a pattern.
        assertNull(week(history { session(d(20), 18, 10) { noisy() } }).flag(FlagKind.NOISY_ROOM))
    }

    @Test fun aStageHearingSoundsButNoKeySuggestsAnEasierMic() {
        assertNotNull(week(twice("stage:KEY_HUNT")).candidate(ActionId.SETTING, "setting" to "micEasier", "value" to "6"))
        assertNotNull(week(twice("stage:CLAP"), roy).candidate(ActionId.SETTING, "setting" to "micEasier", "value" to "3"))
        val loud = Profile.kid("Mia", 1, reader = false, id = "p1").copy(micOffsetDb = 9.0)
        assertTrue("already as easy as it goes", week(twice("stage"), loud).candidates.isEmpty())
    }

    @Test fun longDaysAtThePianoSuggestBedtimeUnlessItIsOn() {
        val long = history { session(d(19), 17, 40) { step("pond-1") }; session(d(20), 17, 45) { step("pond-2") } }
        val f = week(long)
        assertEquals(Flag(FlagKind.LONG_DAYS, 2, 30), f.flag(FlagKind.LONG_DAYS))
        assertNotNull(f.candidate(ActionId.SETTING, "setting" to "bedtime", "value" to "true"))
        assertNull(week(long, current = CurrentSettings(bedtimeOn = true)).flag(FlagKind.LONG_DAYS))
    }

    // ---- praise -------------------------------------------------------------------------------------------------------

    @Test fun goalStreakAndGoalDays() {
        val f = week(history { for (day in 18..21) session(d(day), 17, 10) { goal() } })
        assertEquals(Praise(PraiseKind.GOAL_STREAK, 4), f.praise(PraiseKind.GOAL_STREAK))
        assertEquals(Fact(FactKind.GOAL_DAYS, 4, 7), f.fact(FactKind.GOAL_DAYS))
        assertEquals(listOf(false, false, false, true, true, true, true), f.stats.days.map { it.goal })
        // A gap breaks the streak.
        assertNull(week(history { for (day in listOf(17, 18, 20, 21)) session(d(day), 17, 10) { goal() } }).praise(PraiseKind.GOAL_STREAK))
    }

    @Test fun theFirstChordEver() {
        assertTrue("pond-9" in Insights.CHORD_LESSONS && "night-10" in Insights.CHORD_LESSONS)
        val f = week(history { session(d(20), 18, 10) { done("pond-9", 2) } })
        assertEquals(Praise(PraiseKind.FIRST_CHORD, lessons = listOf("pond-9")), f.praise(PraiseKind.FIRST_CHORD))
        val second = history { session(d(1), 18, 10) { done("pond-9", 2) }; session(d(20), 18, 10) { done("night-10", 3) } }
        assertNull(week(second).praise(PraiseKind.FIRST_CHORD))
    }

    @Test fun stickersAndQuestDays() {
        val f = week(history {
            session(d(20), 18, 10) { sticker(StickerRarity.RARE); sticker(StickerRarity.SHINY); sticker(StickerRarity.RARE); quest("PLAY_SONGS"); quest("COPY_PUP"); quest("FIND_CS") }
        })
        assertEquals(Praise(PraiseKind.SHINY_STICKER, 1), f.praise(PraiseKind.SHINY_STICKER))
        assertEquals(Praise(PraiseKind.RARE_STICKER, 2), f.praise(PraiseKind.RARE_STICKER))
        assertEquals(Praise(PraiseKind.QUEST_DAY, 1), f.praise(PraiseKind.QUEST_DAY))
        assertEquals(Fact(FactKind.STICKERS, 3, 3), f.fact(FactKind.STICKERS))
        assertEquals(Fact(FactKind.QUEST_STARS, 3), f.fact(FactKind.QUEST_STARS))
        // The shiny one ranks before the rare ones.
        assertTrue(f.praise.indexOfFirst { it.kind == PraiseKind.SHINY_STICKER } < f.praise.indexOfFirst { it.kind == PraiseKind.RARE_STICKER })
    }

    @Test fun parentActionsAndSettingsAreRecordedInTheNextBrief() {
        val f = week(history {
            session(d(20), 18, 10) {
                action("SET_NEXT", mapOf("lesson" to "pond-3")); action("NOT_AN_ACTION", emptyMap()); setting("reader", "true")
            }
        })
        assertEquals(listOf(ActionId.SET_NEXT), f.actions.map { it.action })
        assertEquals(mapOf("lesson" to "pond-3"), f.actions.single().params)
        assertEquals(listOf("reader" to "true"), f.settings.map { it.key to it.value })
    }

    // ---- periods ------------------------------------------------------------------------------------------------------

    @Test fun theSessionNoteCoversTheLastSessionWithShortBreaksMerged() {
        val events = history {
            session(d(21), 15, 20) { step("pond-1") }
            session(d(21), 19, 10) { step("pond-2") }
            session(d(21), 19, 8, minute = 12) { done("pond-2", 3) }        // two minutes away: the same session
        }
        val f = Insights.compute(events, mia, Period.SESSION, NOW, zone = ZONE)
        assertEquals(at(d(21), 19), f.range.fromMs)
        assertEquals(1, f.stats.totals.sessions)
        assertEquals(18, f.stats.totals.minutes)
        assertEquals(listOf("pond-2"), f.stats.lessons.map { it.lessonId })
        assertEquals(Trend(TrendKind.MINUTES, 18.0, 20.0, Direction.STEADY, null, 1), f.trend(TrendKind.MINUTES))
    }

    @Test fun aSessionStillOpenRunsToNow() {
        val events = history { session(d(21), 19, 0, minute = 50, end = false) { step("pond-1") } }
        val now = at(d(21), 19, 54)
        val f = Insights.compute(events, mia, Period.SESSION, now, zone = ZONE)
        assertEquals(TimeRange(at(d(21), 19, 50), now), f.range)
        assertEquals(4, f.stats.totals.minutes)
        assertEquals(listOf("pond-1"), f.stats.lessons.map { it.lessonId })
        // With the provisional end data/Briefs.sessionSoFar gives, the practice counted so far shows too.
        val soFar = Insights.compute(events + BriefEvent.SessionEnded(now, 4 * 60_000L, 180), mia, Period.SESSION, now, zone = ZONE)
        assertEquals(Fact(FactKind.PRACTICE_MINUTES, 3, 4), soFar.facts.first())
        // Long after its last event a session with no end is over: it ends there.
        val later = Insights.compute(events, mia, Period.SESSION, NOW, zone = ZONE)
        assertEquals(at(d(21), 19, 50) + 30_000, later.range.toMs)
    }

    @Test fun theDayIsTodayFromMidnight() {
        val events = history { session(d(20), 18, 30) { done("pond-1") }; session(d(21), 7, 10) { done("pond-2") } }
        val f = Insights.compute(events, mia, Period.DAY, NOW, zone = ZONE)
        assertEquals(TimeRange(at(d(21), 0), NOW), f.range)
        assertEquals(listOf("pond-2"), f.stats.lessons.map { it.lessonId })
        assertEquals(Fact(FactKind.PRACTICE_MINUTES, 10, 10), f.facts.first())
        assertEquals(Trend(TrendKind.MINUTES, 10.0, 30.0, Direction.DOWN, false, 1), f.trend(TrendKind.MINUTES))
    }

    @Test fun nothingAtAllGivesEmptyFindingsForEveryPeriod() {
        for (p in Period.entries) {
            val f = Insights.compute(emptyList(), mia, p, NOW, zone = ZONE)
            assertTrue("$p", f.isEmpty)
            assertTrue(f.flags.isEmpty() && f.candidates.isEmpty() && f.praise.isEmpty())
        }
    }

    @Test fun eventsFromANewerAppAndFromTheFutureAreSkipped() {
        val events = history { session(d(20), 18, 10) { step("pond-1") } } +
            BriefEvent.Unknown(at(d(20), 18, 5), "hologram", mapOf("x" to 1L)) +
            BriefEvent.LessonDone(NOW + 60_000, "pond-9", 3, first = true)
        val f = week(events)
        assertNull(f.praise(PraiseKind.FIRST_CHORD))
        assertEquals(listOf("pond-1"), f.stats.lessons.map { it.lessonId })
    }

    @Test fun theFullReportStats() {
        val f = week(history {
            session(d(10), 18, 10) { step("pond-1", wrong = 6, near = 3, far = 3) }
            session(d(19), 8, 15, practiceMin = 12) { step("pond-1", tries = 2, stars = 2); done("pond-1", 2) }
            session(d(21), 18, 20) { step("pond-2", tries = 1, letGo = true, stars = 1, ms = 90_000); song("twinkle", 3); song("twinkle", 2) }
        })
        assertEquals((15..21).map { d(it).toString() }, f.stats.days.map { it.date })
        assertEquals(listOf(0, 0, 0, 0, 15, 0, 20), f.stats.days.map { it.minutes })
        assertEquals(listOf(0, 0, 0, 0, 12, 0, 20), f.stats.days.map { it.practiceMinutes })
        assertEquals(listOf(LessonStat("pond-1", 2, 2, 0, 1, 2), LessonStat("pond-2", null, 1, 1, 0, 2)), f.stats.lessons)
        assertEquals(listOf(SongStat("twinkle", 2, 3, null)), f.stats.songs)
        assertEquals(SkillKind.entries, f.stats.skills.map { it.kind })
        assertTrue(f.stats.skills.all { it.points.size == 4 })
        assertEquals(listOf(null, null, 50.0, null), f.stats.skills.first { it.kind == SkillKind.NEAR_SHARE }.points)
        assertEquals(15, f.stats.timeOfDay.first { it.part == DayPart.MORNING }.minutes)
        assertEquals(Totals(minutes = 35, practiceMinutes = 32, sessions = 2, daysPractised = 2, lessonsFinished = 1, newLessons = 1, songRuns = 2), f.stats.totals)
    }

    // ---- the adult variant ------------------------------------------------------------------------------------------

    @Test fun theAdultVariantHasTheSameShapeWithoutTheKidsFlags() {
        val last = PLAYGROUND_PAGES.last()
        val events = letters(listOf(8, 12, 15, 20)) + history {
            session(d(19), 17, 40) { done("pond-1", 1); done("pond-2", 1); step("pond-3", tries = 3, letGo = true, stars = 1) }
            session(d(21), 17, 45) { done(last.lastLessonId, 3); finale(last.id) }
        }
        val kid = week(events, mia)
        val kidFlags = kid.flags.map { it.kind }.toSet()
        assertTrue(kidFlags.containsAll(listOf(FlagKind.READY_FOR_LETTERS, FlagKind.READY_FOR_ROAD, FlagKind.LONG_DAYS, FlagKind.PAGE_STRUGGLE)))
        val adult = week(events, roy.copy(colourNotes = true))
        val adultFlags = adult.flags.map { it.kind }.toSet()
        for (k in listOf(FlagKind.READY_FOR_LETTERS, FlagKind.READY_FOR_ROAD, FlagKind.LONG_DAYS, FlagKind.PAGE_STRUGGLE)) assertFalse("$k", k in adultFlags)
        assertTrue(FlagKind.STUCK_LESSON in adultFlags)
        val settings = adult.candidates.filter { it.action == ActionId.SETTING }.map { it.params["setting"] }
        assertFalse("reader" in settings || "bedtime" in settings)
        assertTrue(adult.candidates.none { it.action in setOf(ActionId.FRESH_START, ActionId.PACE_UNLOCK, ActionId.PACE_FOCUS, ActionId.TEACHER_ASSIGN) })
        assertEquals(ProfileKind.ADULT, adult.profileKind)
        assertEquals(kid.facts.map { it.kind }, adult.facts.map { it.kind })
        assertEquals(kid.stats.copy(), adult.stats)
        assertEquals(kid.praise.map { it.kind } - PraiseKind.FINALE, adult.praise.map { it.kind } - PraiseKind.FINALE)
    }

    // ---- properties -------------------------------------------------------------------------------------------------

    /** Real ids from the course and the library, and a few the course does not know (an imported song, an old lesson). */
    private val lessonPool = COURSE.flatMap { it.lessons }.map { it.id }.shuffled(java.util.Random(7)).take(40) + listOf("old-lesson", "x-1")
    private val songPool = listOf("twinkle", "ode-to-joy", "minuet-g", "fur-elise", "user-alicia", "imported-1")

    private fun randomHistory(r: java.util.Random): List<BriefEvent> = history {
        repeat(r.nextInt(25)) {
            val day = TODAY.minusDays(r.nextInt(40).toLong())
            session(day, 6 + r.nextInt(16), 5 + r.nextInt(50), practiceMin = r.nextInt(40), end = r.nextInt(10) > 0) {
                repeat(r.nextInt(12)) {
                    val lesson = lessonPool[r.nextInt(lessonPool.size)]
                    when (r.nextInt(14)) {
                        0, 1, 2 -> step(lesson, r.nextInt(5), listOf("PLAY", "PERFORM", "READ", "LISTEN")[r.nextInt(4)], tries = r.nextInt(5),
                            letGo = r.nextInt(6) == 0, hints = List(r.nextInt(4)) { 1 + r.nextInt(3) }, stars = 1 + r.nextInt(3),
                            accuracy = if (r.nextBoolean()) r.nextInt(101) else null, timing = if (r.nextBoolean()) r.nextInt(400) - 200 else null,
                            hands = Hands.entries[r.nextInt(3)], wrong = r.nextInt(12), near = r.nextInt(6), far = r.nextInt(6),
                            game = if (r.nextInt(5) == 0) "CLAP" else null, score = r.nextInt(101))
                        3, 4 -> done(lesson, 1 + r.nextInt(3), r.nextBoolean())
                        5, 6 -> song(songPool[r.nextInt(songPool.size)], 1 + r.nextInt(3), RunMode.entries[r.nextInt(3)], Hands.entries[r.nextInt(3)],
                            accuracy = r.nextInt(101), timing = r.nextInt(300) - 150, near = r.nextInt(5), far = r.nextInt(5), hints = List(r.nextInt(3)) { 1 + r.nextInt(3) })
                        7 -> toy(ToyKind.entries[r.nextInt(3)], r.nextInt(15))
                        8 -> ear(listOf("pitch", "chord:build")[r.nextInt(2)], r.nextBoolean())
                        9 -> noisy(listOf("player", "stage:CLAP")[r.nextInt(2)])
                        10 -> goal()
                        11 -> sticker(StickerRarity.entries[r.nextInt(3)])
                        12 -> finale(PLAYGROUND_PAGES[r.nextInt(PLAYGROUND_PAGES.size)].id)
                        else -> action(ActionId.entries[r.nextInt(ActionId.entries.size)].name, mapOf("lesson" to lesson, "note" to "Play it slowly, Mia"))
                    }
                }
            }
        }
    }

    @Test fun findingsNeverNameALessonSongOrPageThatIsNotInTheEvents() {
        val r = java.util.Random(2026)
        repeat(150) { i ->
            val events = randomHistory(r)
            val lessons = events.mapNotNull { (it as? BriefEvent.LessonStep)?.lessonId ?: (it as? BriefEvent.LessonDone)?.lessonId ?: (it as? BriefEvent.ParentAction)?.params?.get("lesson") }.toSet()
            val songs = events.mapNotNull { (it as? BriefEvent.SongRun)?.songId }.toSet()
            val pages = events.mapNotNull { (it as? BriefEvent.FinalePlayed)?.pageId }.toSet() + lessons.mapNotNull { pageOf(it)?.id }
            for (p in listOf(mia, leo, roy)) for (period in Period.entries) {
                val f = Insights.compute(events, p, period, NOW, zone = ZONE)
                val where = "history $i, ${p.kind}, $period"
                assertTrue("$where lessons ${f.lessonIds() - lessons}", lessons.containsAll(f.lessonIds()))
                assertTrue("$where songs ${f.songIds() - songs}", songs.containsAll(f.songIds()))
                val named = f.flags.mapNotNull { it.page } + f.praise.mapNotNull { it.page } + f.candidates.mapNotNull { it.params["page"] }
                assertTrue("$where pages $named", pages.containsAll(named))
                assertEquals("$where: deterministic", f, Insights.compute(events.shuffled(r), p, period, NOW, zone = ZONE))
                assertEquals("$where: encode and decode", f, Findings.decode(f.encode()))
                assertFalse("$where: never a name", f.redacted().encode().contains("Mia") || f.redacted().encode().contains(p.name))
                assertTrue(f.candidates.size <= Insights.MAX_CANDIDATES)
                if (p.kind == ProfileKind.ADULT) assertTrue(f.candidates.none { it.params["setting"] in setOf("reader", "bedtime") })
            }
        }
    }
}
