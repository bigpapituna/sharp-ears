package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.brief.ActionId
import com.bigpapituna.sharpears.engine.brief.ActionTaken
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.Candidate
import com.bigpapituna.sharpears.engine.brief.DayPart
import com.bigpapituna.sharpears.engine.brief.DayStat
import com.bigpapituna.sharpears.engine.brief.Direction
import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Flag
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.HintStats
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.LessonStat
import com.bigpapituna.sharpears.engine.brief.PartStat
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.Praise
import com.bigpapituna.sharpears.engine.brief.PraiseKind
import com.bigpapituna.sharpears.engine.brief.RecommendationKind
import com.bigpapituna.sharpears.engine.brief.SettingChange
import com.bigpapituna.sharpears.engine.brief.SkillKind
import com.bigpapituna.sharpears.engine.brief.SkillSeries
import com.bigpapituna.sharpears.engine.brief.SongStat
import com.bigpapituna.sharpears.engine.brief.Stats
import com.bigpapituna.sharpears.engine.brief.TimeRange
import com.bigpapituna.sharpears.engine.brief.ToyKind
import com.bigpapituna.sharpears.engine.brief.ToyStat
import com.bigpapituna.sharpears.engine.brief.Totals
import com.bigpapituna.sharpears.engine.brief.Trend
import com.bigpapituna.sharpears.engine.brief.TrendKind
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import java.util.Random

/** Random findings for the template writer's property tests: every kind, odd values, missing titles and params. */
object TemplateGen {
    val LESSONS: List<String> = COURSE.flatMap { it.lessons }.map { it.id }.shuffled(Random(11)).take(30) + listOf("old-lesson", "x-1")
    val SONGS = listOf("twinkle", "ode-to-joy", "minuet-g", "fur-elise", "user-alicia", "imported-1")
    val PAGES: List<String> = PLAYGROUND_PAGES.map { it.id }
    val ALL_IDS: List<String> = LESSONS + SONGS + PAGES

    /** A title that names its id and cannot be found inside another title: "Opus pondb ez". */
    fun titleOf(id: String): String =
        "Opus " + id.map { if (it.isDigit()) 'a' + (it - '0') else if (it.isLetter()) it else 'q' }.joinToString("") + " ez"

    /** Titles for every id except some the lookup does not know (the writer must cope). */
    val titles: (String) -> String = { id -> if (id == "x-1" || id == "imported-1") "" else titleOf(id) }

    const val BASE = 1_793_059_200_000L   // late October 2026

    private fun <T> Random.pick(l: List<T>): T = l[nextInt(l.size)]
    private fun Random.some(l: List<String>, max: Int = 3): List<String> = List(nextInt(max + 1)) { pick(l) }.distinct()
    private fun Random.value(): Long = when (nextInt(6)) { 0 -> 0; 1 -> 1; 2 -> 2; 3 -> (3 + nextInt(8)).toLong(); 4 -> (11 + nextInt(90)).toLong(); else -> nextInt(400).toLong() }

    fun findings(r: Random, kid: Boolean = r.nextBoolean(), period: Period = Period.entries[r.nextInt(3)], weeks: Int = r.nextInt(8)): Findings {
        val to = BASE + weeks * 7 * 86_400_000L + r.nextInt(86_400_000)
        val facts = FactKind.entries.filter { r.nextInt(5) < 2 }.map { Fact(it, r.value(), if (r.nextInt(4) == 0) null else r.value() + r.nextInt(30), r.some(LESSONS), r.some(SONGS)) }
        val trends = TrendKind.entries.filter { r.nextInt(3) == 0 }.map { k ->
            val dir = Direction.entries[r.nextInt(3)]
            Trend(k, r.nextInt(2000) / 10.0, r.nextInt(2000) / 10.0, dir, if (dir == Direction.STEADY) null else r.nextBoolean(), 1 + r.nextInt(4))
        }
        val flags = FlagKind.entries.filter { r.nextInt(4) == 0 }.map { Flag(it, r.value(), if (r.nextInt(4) == 0) null else r.value(), r.some(LESSONS), r.some(SONGS), if (r.nextInt(3) == 0) null else r.pick(PAGES)) }
        val candidates = List(r.nextInt(6)) {
            val params = HashMap<String, String>()
            if (r.nextBoolean()) params["lesson"] = r.pick(LESSONS)
            if (r.nextBoolean()) params["song"] = r.pick(SONGS)
            if (r.nextBoolean()) params["page"] = r.pick(PAGES)
            if (r.nextBoolean()) params["after"] = r.pick(SONGS)
            if (r.nextBoolean()) { params["setting"] = r.pick(listOf("reader", "bedtime", "colourNotes", "micEasier", "quietRoom", "nonsense")); params["value"] = r.pick(listOf("true", "false", "6", "6.5", "x")) }
            Candidate(r.pick(RecommendationKind.entries), r.pick(ActionId.entries), params, if (r.nextBoolean()) r.pick(FlagKind.entries) else null)
        }
        val praise = PraiseKind.entries.filter { r.nextInt(4) == 0 }.map { Praise(it, r.value(), null, r.some(LESSONS), r.some(SONGS), if (r.nextBoolean()) r.pick(PAGES) else null) }
        val stats = Stats(
            days = (0 until 7).map { DayStat("2026-10-${15 + it}", r.nextInt(40), r.nextInt(40), r.nextInt(4), r.nextBoolean()) },
            lessons = r.some(LESSONS, 5).map { LessonStat(it, if (r.nextBoolean()) 1 + r.nextInt(3) else null, r.nextInt(6), r.nextInt(3), r.nextInt(3), r.nextInt(20)) },
            songs = r.some(SONGS, 3).map { SongStat(it, 1 + r.nextInt(5), 1 + r.nextInt(3), if (r.nextBoolean()) r.nextInt(100) else null) },
            skills = SkillKind.entries.map { SkillSeries(it, List(4) { if (r.nextBoolean()) r.nextDouble() * 100 else null }) },
            toys = ToyKind.entries.filter { r.nextBoolean() }.map { ToyStat(it, 1 + r.nextInt(4), r.nextInt(15)) },
            timeOfDay = DayPart.entries.map { PartStat(it, r.nextInt(60), r.nextInt(4)) },
            noisyRoom = r.nextInt(5), hints = HintStats(r.nextInt(4), r.nextInt(4), r.nextInt(4), r.nextInt(2)),
            totals = Totals(sessions = r.nextInt(7), minutes = r.nextInt(300), practiceMinutes = r.nextInt(300)),
        )
        val actions = List(r.nextInt(3)) { ActionTaken(to - 1000, r.pick(ActionId.entries), mapOf("lesson" to r.pick(LESSONS), "note" to "Play it slowly", "song" to r.pick(SONGS), "page" to r.pick(PAGES), "setting" to r.pick(listOf("reader", "bedtime", "weird")), "value" to r.pick(listOf("true", "false")))) }
        val settings = List(r.nextInt(4)) { SettingChange(to - 500, r.pick(listOf("reader", "bedtime", "colourNotes", "micOffsetDb", "goalMin", "wrongFlash", "voice", "keyLabels", "somethingNew", "hintSeconds")), r.pick(listOf("true", "false", "10", "all", "3.0"))) }
        return Findings(
            if (kid) ProfileKind.KID else ProfileKind.ADULT, period, TimeRange(to - 7 * 86_400_000L, to), facts, trends, flags, candidates, praise,
            stats, r.nextBoolean(), if (r.nextInt(4) == 0) null else 5 + r.nextInt(25), actions, settings,
        )
    }

    /** Every string a brief shows a grown-up. */
    fun texts(b: Brief): List<String> = listOf(b.headline) + b.facts + b.notes + b.recommendations.map { it.text } + b.praise + b.sections.flatMap { listOf(it.title, it.body) }

    /** The one-minute read: headline, facts, notes, recommendations, praise. */
    fun minuteRead(b: Brief): List<String> = listOf(b.headline) + b.facts + b.notes + b.recommendations.map { it.text } + b.praise

    fun texts(k: KidCard): List<String> = k.lines + listOfNotNull(k.nextTry)

    /** Ids a brief may name: those the findings mention anywhere. */
    fun mentioned(f: Findings): Set<String> = f.lessonIds() + f.songIds() +
        f.flags.mapNotNull { it.page } + f.praise.mapNotNull { it.page } + f.candidates.mapNotNull { it.params["page"] } +
        f.actions.mapNotNull { it.params["page"] }
}
