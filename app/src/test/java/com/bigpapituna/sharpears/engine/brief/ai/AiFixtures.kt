package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.brief.ActionId
import com.bigpapituna.sharpears.engine.brief.ActionTaken
import com.bigpapituna.sharpears.engine.brief.Candidate
import com.bigpapituna.sharpears.engine.brief.DayStat
import com.bigpapituna.sharpears.engine.brief.Direction
import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Flag
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.LessonStat
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.Praise
import com.bigpapituna.sharpears.engine.brief.PraiseKind
import com.bigpapituna.sharpears.engine.brief.RecommendationKind
import com.bigpapituna.sharpears.engine.brief.SettingChange
import com.bigpapituna.sharpears.engine.brief.SongStat
import com.bigpapituna.sharpears.engine.brief.Stats
import com.bigpapituna.sharpears.engine.brief.TimeRange
import com.bigpapituna.sharpears.engine.brief.Totals
import com.bigpapituna.sharpears.engine.brief.Trend
import com.bigpapituna.sharpears.engine.brief.TrendKind
import com.bigpapituna.sharpears.engine.family.ProfileKind
import java.util.concurrent.CopyOnWriteArrayList

/** The AI tests' world: Wednesday 21 October 2026, 8 pm in Ottawa; a kid called Mia, her brother Leo on the roster. */
object AiFixtures {
    const val NOW = 1_793_059_200_000L
    const val NAME = "Mia"
    val ROSTER = listOf("Roy", "Mia", "Leo")

    /** The songs the library knows, two of them imported from files named after a kid (ids as SongRepository.userSongId makes them). */
    val SONGS = mapOf(
        "twinkle" to "Twinkle, Twinkle, Little Star", "hot-cross-buns" to "Hot Cross Buns", "ode-to-joy" to "Ode to Joy",
        "user-mia-s-lullaby" to "Mia's lullaby", "user-leo-s-tune" to "Leo's tune",
    )
    val TITLES = Titles.course { SONGS }

    /**
     * CONTRACT.md's example week (a kid who does not read yet, stuck on Ta and ti-ti), plus a grown-up's action with a
     * teacher's note in it and a setting with free text: neither may reach the model.
     */
    fun week(toMs: Long = NOW) = Findings(
        ProfileKind.KID, Period.WEEK, TimeRange(1_792_472_400_000L, toMs),
        facts = listOf(
            Fact(FactKind.PRACTICE_MINUTES, 42, 50), Fact(FactKind.DAYS_PRACTISED, 5, 7),
            Fact(FactKind.NEW_LESSONS, 2, lessons = listOf("pond-4", "pond-3")),
        ),
        trends = listOf(Trend(TrendKind.DAYS, 5.0, 2.0, Direction.UP, true, 2)),
        flags = listOf(Flag(FlagKind.STUCK_LESSON, 3, 1, lessons = listOf("pond-5"))),
        candidates = listOf(
            Candidate(RecommendationKind.UNSTICK, ActionId.SET_NEXT, mapOf("lesson" to "pond-5"), FlagKind.STUCK_LESSON),
            Candidate(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, mapOf("lesson" to "pond-5"), FlagKind.STUCK_LESSON),
        ),
        praise = listOf(Praise(PraiseKind.STUCK_WITH_IT, 1, lessons = listOf("pond-3")), Praise(PraiseKind.CONSISTENT, 5)),
        stats = Stats(
            days = listOf(DayStat("2026-10-21", 10, 8, 1, true)),
            lessons = listOf(LessonStat("pond-3", 3, 4, 0, 1, 9), LessonStat("pond-4", 2, 2, 0, 1, 6), LessonStat("pond-5", null, 3, 1, 0, 7)),
            totals = Totals(minutes = 50, practiceMinutes = 42, sessions = 6, daysPractised = 5, lessonsFinished = 2, newLessons = 2),
        ),
        reader = false, goalMinutes = 5,
        actions = listOf(ActionTaken(1_792_900_000_000L, ActionId.TEACHER_ASSIGN, mapOf("song" to "twinkle", "note" to "Mia, try the left hand slowly!"))),
        settings = listOf(SettingChange(1_792_900_000_000L, "reader", "a note with spaces")),
    )

    /** A session of the same kid with songs whose titles carry names. */
    fun session() = Findings(
        ProfileKind.KID, Period.SESSION, TimeRange(1_793_037_600_000L, 1_793_038_440_000L),
        facts = listOf(Fact(FactKind.PRACTICE_MINUTES, 11, 14), Fact(FactKind.SONG_RUNS, 3, songs = listOf("user-mia-s-lullaby", "user-leo-s-tune"))),
        praise = listOf(Praise(PraiseKind.CLEAN_SONG, 1, songs = listOf("user-mia-s-lullaby"))),
        candidates = listOf(Candidate(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, mapOf("song" to "user-mia-s-lullaby"), FlagKind.STUCK_SONG)),
        stats = Stats(songs = listOf(SongStat("user-mia-s-lullaby", 2, 3, 95), SongStat("user-leo-s-tune", 1, 1, 40)), totals = Totals(minutes = 14, practiceMinutes = 11, sessions = 1)),
        reader = true, goalMinutes = 8,
    )

    /** A reply that follows every rule, for [week]. */
    const val PERFECT_WEEK = """{"headline":"{name} played on most days this week and kept at a tricky rhythm.",
"facts":["{name} played on 5 days out of 7, up from about 2.","That came to 42 minutes of practice.","Two new lessons are done: Drum on a lily pad and Copy the frogs."],
"notes":["Ta and ti-ti is still settling. That is normal: the app lets a step go on purpose, so nobody stays stuck."],
"recommendations":[{"text":"Make Ta and ti-ti the first stone tomorrow, while {name} is fresh.","candidateIndex":0},{"text":"Sit in with {name} on Ta and ti-ti and play it together.","candidateIndex":1}],
"praise":"{name} stuck with Copy the frogs through 4 tries and finished it with 3 stars.",
"sections":[{"title":"Skills","body":"Copying tunes and rhythm on the pond."},{"title":"Habits","body":"Short, regular visits on 5 days."}]}"""

    const val PERFECT_CARD = """{"lines":["You played with me on five days this week!","You kept going on Copy the frogs and won three stars!"],"nextTry":"Next time, let's try Ta and ti-ti, nice and slow."}"""
}

/** A transport that answers from [reply] and keeps every request. */
class FakeTransport(var reply: (AiRequest) -> String) : AiTransport {
    val requests = CopyOnWriteArrayList<AiRequest>()
    override fun complete(request: AiRequest): String { requests += request; return reply(request) }
}

/** The em and en dashes, made from their code points so that no source line holds one. */
val EM: String = 0x2014.toChar().toString()
val EN: String = 0x2013.toChar().toString()
