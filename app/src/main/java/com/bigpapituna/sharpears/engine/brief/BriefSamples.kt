package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.ProfileKind
import java.time.LocalDate
import java.time.ZoneId

/**
 * Made-up findings for previews, the debug route (`--es screen brief`) and the screens' tests: a kid who does not read yet
 * and plays the Pond, in each period, and a grown-up's week. Every id is a real lesson, song or page. Nothing here is ever
 * shown for a real person.
 */
object BriefSamples {
    private fun day(today: LocalDate, back: Int, minutes: Int, practice: Int, sessions: Int, goal: Boolean) =
        DayStat(today.minusDays(back.toLong()).toString(), minutes, practice, sessions, goal)

    private fun stats(today: LocalDate, kid: Boolean) = Stats(
        days = listOf(
            day(today, 6, 8, 6, 1, true), day(today, 5, 0, 0, 0, false), day(today, 4, 12, 9, 2, true), day(today, 3, 6, 5, 1, true),
            day(today, 2, 0, 0, 0, false), day(today, 1, 15, 12, 2, true), day(today, 0, 10, 8, 1, true)),
        lessons = listOf(
            LessonStat("pond-3", 3, 4, 0, 2, 11), LessonStat("pond-4", 2, 2, 0, 1, 6),
            LessonStat("pond-5", null, 6, 1, 0, 14), LessonStat("pond-2", 3, 1, 0, 1, 3)),
        songs = listOf(SongStat("twinkle", 3, 2, 78), SongStat("hot-cross-buns", 1, 3, null)),
        skills = listOf(
            SkillSeries(SkillKind.STARS, listOf(2.0, 2.2, 2.1, 2.5)),
            SkillSeries(SkillKind.ACCURACY, listOf(null, 70.0, 74.0, 81.0)),
            SkillSeries(SkillKind.TIMING_MS, listOf(62.0, 55.0, null, 41.0)),
            SkillSeries(SkillKind.NEAR_SHARE, listOf(40.0, 45.0, 58.0, 64.0)),
            SkillSeries(SkillKind.LEFT_HAND, listOf(55.0, null, 61.0, 60.0)),
            SkillSeries(SkillKind.RIGHT_HAND, listOf(80.0, 84.0, 88.0, 90.0)),
            SkillSeries(SkillKind.PLAY_HINTS, listOf(30.0, 28.0, 22.0, 25.0)),
        ),
        toys = listOf(ToyStat(ToyKind.JAM, 3, 14), ToyStat(ToyKind.PUP_SAYS, 2, 6), ToyStat(ToyKind.CLAP_ALONG, 1, 3)),
        timeOfDay = listOf(PartStat(DayPart.MORNING, 0, 0), PartStat(DayPart.AFTERNOON, 12, 2), PartStat(DayPart.EVENING, 31, 5), PartStat(DayPart.NIGHT, 0, 0)),
        noisyRoom = if (kid) 2 else 0,
        hints = HintStats(glow = 6, point = 3, play = 2, octave = 1),
        totals = Totals(minutes = 51, practiceMinutes = 40, sessions = 7, daysPractised = 5, lessonsFinished = 4, newLessons = 2, songRuns = 4,
            stickers = 3, rareStickers = 1, questStars = 5, goalDays = 4, earRounds = 6, earCorrect = 5),
    )

    private fun range(period: Period, now: Long, zone: ZoneId): TimeRange {
        val day = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (period) {
            Period.SESSION -> TimeRange(now - 22 * 60_000L, now - 60_000L)
            Period.DAY -> TimeRange(day.atStartOfDay(zone).toInstant().toEpochMilli(), now)
            Period.WEEK -> TimeRange(day.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(), now)
        }
    }

    /** A kid's findings for [period]. */
    fun kid(period: Period, now: Long, zone: ZoneId = ZoneId.systemDefault(), reader: Boolean = false): Findings {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val base = Findings(ProfileKind.KID, period, range(period, now, zone), stats = stats(today, true), reader = reader, goalMinutes = 5)
        return when (period) {
            Period.WEEK -> base.copy(
                facts = listOf(Fact(FactKind.PRACTICE_MINUTES, 40, 51), Fact(FactKind.DAYS_PRACTISED, 5, 7), Fact(FactKind.NEW_LESSONS, 2, null, lessons = listOf("pond-4", "pond-3")),
                    Fact(FactKind.GOAL_DAYS, 4, 7), Fact(FactKind.MOSTLY_EVENING, 31, 43)),
                trends = listOf(Trend(TrendKind.DAYS, 5.0, 2.0, Direction.UP, true, 2), Trend(TrendKind.STARS, 2.5, 2.1, Direction.UP, true, 3)),
                flags = listOf(Flag(FlagKind.STUCK_LESSON, 3, 1, lessons = listOf("pond-5")), Flag(FlagKind.READY_FOR_LETTERS, 28, 6, lessons = listOf("hall-1", "hall-2"))),
                candidates = listOf(
                    Candidate(RecommendationKind.UNSTICK, ActionId.SET_NEXT, mapOf("lesson" to "pond-5"), FlagKind.STUCK_LESSON),
                    Candidate(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, mapOf("lesson" to "pond-5"), FlagKind.STUCK_LESSON),
                    Candidate(RecommendationKind.SETTINGS, ActionId.SETTING, mapOf("setting" to "reader", "value" to "true"), FlagKind.READY_FOR_LETTERS)),
                praise = listOf(Praise(PraiseKind.STUCK_WITH_IT, 1, null, lessons = listOf("pond-3")), Praise(PraiseKind.CONSISTENT, 5)),
                actions = listOf(ActionTaken(now - 5 * 86_400_000L, ActionId.SETTING, mapOf("setting" to "colourNotes", "value" to "true"))),
            )
            Period.DAY -> base.copy(
                facts = listOf(Fact(FactKind.PRACTICE_MINUTES, 8, 10), Fact(FactKind.NEW_LESSONS, 1, null, lessons = listOf("pond-4")), Fact(FactKind.SONG_RUNS, 2, null, songs = listOf("twinkle"))),
                flags = listOf(Flag(FlagKind.MASTERED_SONG, 3, null, songs = listOf("twinkle")), Flag(FlagKind.PAGE_STRUGGLE, 3, null, lessons = listOf("pond-5"), page = "pond")),
                candidates = listOf(
                    Candidate(RecommendationKind.NEW_MUSIC, ActionId.TEACHER_ASSIGN, mapOf("after" to "twinkle"), FlagKind.MASTERED_SONG),
                    Candidate(RecommendationKind.FOCUS, ActionId.PACE_FOCUS, mapOf("page" to "pond"), FlagKind.SCATTERED),
                    Candidate(RecommendationKind.UNSTICK, ActionId.FRESH_START, mapOf("page" to "pond"), FlagKind.PAGE_STRUGGLE)),
                praise = listOf(Praise(PraiseKind.CLEAN_SONG, 1, null, songs = listOf("twinkle"))),
            )
            Period.SESSION -> base.copy(
                facts = listOf(Fact(FactKind.PRACTICE_MINUTES, 8, 10), Fact(FactKind.NEW_LESSONS, 1, null, lessons = listOf("pond-4")), Fact(FactKind.NEAR_MISSES, 5, 7)),
                flags = listOf(Flag(FlagKind.LOW_STARS, 1, null, lessons = listOf("pond-4")), Flag(FlagKind.NOISY_ROOM, 2, 1)),
                candidates = listOf(
                    Candidate(RecommendationKind.POLISH, ActionId.REPLAY_FOR_STARS, mapOf("lesson" to "pond-4"), FlagKind.LOW_STARS),
                    Candidate(RecommendationKind.SETTINGS, ActionId.SETTING, mapOf("setting" to "quietRoom", "value" to "true"), FlagKind.NOISY_ROOM)),
                praise = listOf(Praise(PraiseKind.NEW_LESSONS, 1, null, lessons = listOf("pond-4"))),
            )
        }
    }

    /** A grown-up's findings ("My week"): the same shape, none of the kid-only flags or actions. */
    fun adult(period: Period, now: Long, zone: ZoneId = ZoneId.systemDefault()): Findings {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return Findings(ProfileKind.ADULT, period, range(period, now, zone), stats = stats(today, false), goalMinutes = 10,
            facts = listOf(Fact(FactKind.PRACTICE_MINUTES, 74, 90), Fact(FactKind.DAYS_PRACTISED, 4, 7), Fact(FactKind.NEW_LESSONS, 1, null, lessons = listOf("pond-4"))),
            flags = listOf(Flag(FlagKind.STUCK_LESSON, 3, 1, lessons = listOf("pond-5")), Flag(FlagKind.LEFT_HAND_BEHIND, 60, 90)),
            candidates = listOf(
                Candidate(RecommendationKind.UNSTICK, ActionId.SET_NEXT, mapOf("lesson" to "pond-5"), FlagKind.STUCK_LESSON),
                Candidate(RecommendationKind.POLISH, ActionId.REPLAY_FOR_STARS, mapOf("lesson" to "pond-4"), FlagKind.LOW_STARS)),
            praise = listOf(Praise(PraiseKind.CONSISTENT, 4)))
    }

    /** A quiet week: nothing happened, for the empty states. */
    fun empty(period: Period, now: Long, zone: ZoneId = ZoneId.systemDefault(), kind: ProfileKind = ProfileKind.KID) =
        Findings(kind, period, range(period, now, zone), stats = Stats(days = stats(java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), false).days.map { it.copy(minutes = 0, practiceMinutes = 0, sessions = 0, goal = false) }))
}
