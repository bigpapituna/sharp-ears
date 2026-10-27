package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.DayPart
import com.bigpapituna.sharpears.engine.brief.Direction
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.PraiseKind
import com.bigpapituna.sharpears.engine.brief.SettingId
import com.bigpapituna.sharpears.engine.brief.Trend
import com.bigpapituna.sharpears.engine.brief.TrendKind
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * The full report's sections for a day or a week: "Habits", "Skills", "Stuck and unstuck" and, only when there is
 * something to say, "What you did". Each is a short paragraph; a section with nothing to say is left out. A session note
 * has no sections.
 */
internal object SectionBank {
    val TIME_OF_DAY = listOf(
        l("Most of the playing falls {when}."),
        l("The piano is busiest {when}."),
        l("The playing clusters {when}."),
    )
    val DAYS_PLAYED = listOf(
        l("Played on {dayNames}."),
        l("The days with practice were {dayNames}."),
        l("Practice days: {dayNames}."),
    )
    val STARS_SPLIT = listOf(
        l("{three} of the {all} lessons played reached three stars."),
        l("Three stars on {three} of {all} lessons."),
        l("Of {all} lessons played, {three} finished with three stars."),
    )
    val UNSTUCK = listOf(
        l("A step that was tricky and then was not: {titles}."),
        l("{Titles} took several goes and finished well."),
        l("Unstuck: {titles}, now at two stars or better."),
    )
    val READER_OFF = listOf(
        l("The keys still show animals. The app suggests letters after about two weeks of letter lessons played well."),
        l("Animals are still on the keys. Letters come in once the letter lessons go well over a couple of weeks."),
        l("Reading letters is not due yet: the keys still show animals, and the app will say when the letter lessons are ready."),
    )
    val READER_ON = listOf(
        l("Letters are already on the keys."),
        l("The keys show letters, so reading is already part of playing."),
        l("Reader mode is on, with letters on the colour notes."),
    )
    val NO_PRACTICE_FACTS = listOf(
        l("The app saw some playing {span}, but not enough to report in numbers."),
        l("There is little to count {span}: a short visit, no practice minutes yet."),
        l("{Span} was too light to report in numbers."),
    )
    /** 2.9: the week's habits in one line, from the findings' daysPractised, longestGap and daysBackThisMonth. */
    val BACK = listOf(
        l("Back on {back} of the last seven days, the longest gap {gap}."),
        l("{Back} of the last seven days had practice, the longest gap {gap}."),
        l("The piano came out on {back} of the last seven days, the longest gap {gap}."),
    )
    val BACK_NO_GAP = listOf(
        l("Back on {back} of the last seven days, with no gap at all."),
        l("{Back} of the last seven days had practice, not one missed."),
        l("The piano came out on {back} of the last seven days, no day skipped."),
    )
    val NOTHING = listOf(
        l("Nothing to report {span}: the piano stayed closed."),
        l("The piano was quiet {span}, so there is nothing to report."),
        l("No playing {span}. The brief fills in when the music starts."),
    )
}

private val HABIT_FACTS = listOf(FactKind.PRACTICE_MINUTES, FactKind.DAYS_PRACTISED, FactKind.SESSIONS, FactKind.GOAL_DAYS)
private val SKILL_FACTS = listOf(FactKind.NEAR_MISSES, FactKind.TIMING, FactKind.HANDS, FactKind.EAR_ROUNDS, FactKind.CLAP, FactKind.HINTS)
private val MOSTLY = mapOf(
    FactKind.MOSTLY_MORNING to DayPart.MORNING, FactKind.MOSTLY_AFTERNOON to DayPart.AFTERNOON,
    FactKind.MOSTLY_EVENING to DayPart.EVENING, FactKind.MOSTLY_NIGHT to DayPart.NIGHT,
)

private fun whenOf(p: DayPart) = when (p) {
    DayPart.MORNING -> "in the morning"; DayPart.AFTERNOON -> "in the afternoon"
    DayPart.EVENING -> "in the evening"; DayPart.NIGHT -> "late at night"
}

private fun dayName(date: String): String? = runCatching { LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }.getOrNull()

private fun habits(c: Ctx, shown: Set<FactKind>, shownTrends: Set<TrendKind>): String {
    val f = c.f
    val out = ArrayList<String>()
    val used = HashSet(shownTrends)
    val moved = f.trends.filter { it.kind in HABIT_TRENDS && it.direction != Direction.STEADY }
    fun trend(t: Trend): String? = trendLine(c, t, 1)?.let { line ->
        used += t.kind
        if (t.good == false) line + " " + c.phrase("wobble habit", TrendBank.WOBBLE_HABIT.map { l(it) }, c.common())!! else line
    }
    // 2.9: "Back on five of the last seven days, the longest gap two days." One line in the week's habits; it stands in for
    // the days-practised fact.
    val back = f.stats.totals.daysPractised
    val backLine = if (c.period == Period.WEEK && back > 0) {
        val gap = f.stats.longestGap
        val vars = c.common() + mapOf("back" to words(back), "gap" to count(gap.toLong(), "day"))
        val month = f.stats.daysBackThisMonth.takeIf { it > 0 }?.let { " Played on " + count(it.toLong(), "day") + " this month." }.orEmpty()
        c.phrase("section back", if (gap == 0) SectionBank.BACK_NO_GAP else SectionBank.BACK, vars)?.let { it + month }
    } else null
    backLine?.let(out::add)
    for (kind in HABIT_FACTS) {
        if (kind in shown) continue
        if (kind == FactKind.DAYS_PRACTISED && backLine != null) continue
        val fact = f.facts.firstOrNull { it.kind == kind } ?: continue
        val matching = TREND_OF[kind]?.let { k -> moved.firstOrNull { it.kind == k && it.kind !in used } }
        (matching?.let(::trend) ?: factLine(c, fact, 1))?.let(out::add)
    }
    val mostly = f.facts.firstOrNull { it.kind in MOSTLY }
    if (mostly != null) { if (mostly.kind !in shown) factLine(c, mostly, 1)?.let(out::add) }
    else if (c.period == Period.WEEK) {
        val top = f.stats.timeOfDay.filter { it.minutes > 0 }.maxByOrNull { it.minutes }
        if (top != null && f.stats.timeOfDay.sumOf { it.minutes } >= 10) c.phrase("section time", SectionBank.TIME_OF_DAY, c.common() + ("when" to whenOf(top.part)))?.let(out::add)
    }
    if (c.period == Period.WEEK) {
        val days = f.stats.days.filter { it.practiceMinutes > 0 }.mapNotNull { dayName(it.date) }
        if (days.isNotEmpty() && days.size < 7) c.phrase("section days", SectionBank.DAYS_PLAYED, c.common() + ("dayNames" to joinList(days)))?.let(out::add)
    }
    for (t in moved.filter { it.kind !in used }.take(2)) trend(t)?.let(out::add)
    return out.take(6).joinToString(" ")
}

private fun skills(c: Ctx, shown: Set<FactKind>, shownTrends: Set<TrendKind>): String {
    val f = c.f
    val out = ArrayList<String>()
    for (kind in SKILL_FACTS) if (kind !in shown) f.facts.firstOrNull { it.kind == kind }?.let { fact -> factLine(c, fact, 1)?.let(out::add) }
    val played = f.stats.lessons.filter { it.stars != null }
    val three = played.count { it.stars == 3 }
    if (played.size >= 2 && three >= 1)
        c.phrase("section stars", SectionBank.STARS_SPLIT, c.common() + mapOf("three" to words(three), "all" to words(played.size)))?.let(out::add)
    val moved = f.trends.filter { it.kind !in HABIT_TRENDS && it.direction != Direction.STEADY && it.kind !in shownTrends }.sortedBy { if (it.good == true) 0 else 1 }.take(3)
    var worse = false
    for ((i, t) in moved.withIndex()) trendLine(c, t, 1 + i)?.let { out += it; if (t.good == false) worse = true }
    if (worse) out += c.phrase("wobble skill", TrendBank.WOBBLE_SKILL.map { l(it) }, c.common())!!
    val letters = f.flags.firstOrNull { it.kind == FlagKind.READY_FOR_LETTERS }
    if (letters != null) out += flagNote(c, letters, 1)
    else if (c.kid && c.period == Period.WEEK && f.stats.totals.sessions > 0)
        c.phrase("section reader", if (f.reader) SectionBank.READER_ON else SectionBank.READER_OFF, c.common())?.let(out::add)
    return out.take(8).joinToString(" ")
}

private fun stuck(c: Ctx): String {
    val f = c.f
    val out = ArrayList<String>()
    val seen = HashMap<FlagKind, Int>()
    for (flag in f.flags.filter { it.kind != FlagKind.READY_FOR_LETTERS }.take(5)) {
        val n = seen.merge(flag.kind, 1, Int::plus)!! - 1
        out += flagNote(c, flag, 1 + n)
    }
    f.praise.firstOrNull { it.kind == PraiseKind.STUCK_WITH_IT }?.let { p ->
        c.titles(p.lessons)?.let { titles -> c.phrase("section unstuck", SectionBank.UNSTUCK, c.common() + ("titles" to titles))?.let(out::add) }
    }
    return out.joinToString(" ")
}

/** Settings keys the app logs, in words; a bool value says on or off. Unknown keys say only that a setting changed. */
private fun settingLine(key: String, value: String): String {
    val on = when (value) { "true" -> true; "false" -> false; else -> null }
    fun toggle(what: String, were: Boolean = false): String { val v = if (were) "were" else "was"; return if (on == null) "$what $v changed." else if (on) "$what $v turned on." else "$what $v turned off." }
    return when (key) {
        "reader" -> toggle("Reader mode")
        "colourNotes" -> toggle("Colour notes", were = true)
        // 2.14: the setting's honest name, its Songs switch and the daily time.
        "bedtime" -> toggle("Wind down after the goal")
        "bedtimeSongs" -> if (on == false) "Songs and the Jam stay open after the goal again." else "Songs and the Jam now close after the goal too."
        "capMin" -> value.toLongOrNull()?.let { if (it <= 0) "The daily time was turned off." else "The daily time was set to ${count(it, "minute")}." }
            ?: "The daily time was changed."
        "wrongFlash" -> toggle("Red key flashes", were = true)
        "reminderOn" -> toggle("The practice reminder")
        "sound" -> toggle("App sound")
        "voice" -> toggle("The voice")
        "naturalVoice" -> toggle("The natural voice")
        "haptics" -> toggle("Vibration")
        "beatLines" -> toggle("Beat lines", were = true)
        "ghostHand" -> toggle("The ghost hand")
        "noteNames" -> toggle("Note names", were = true)
        "fingerNumbers" -> toggle("Finger numbers", were = true)
        "feedbackLight" -> toggle("The feedback light")
        // 2.13: Settings > Playing > Encouragement (the cheer pops) and its "Say it aloud".
        "cheer" -> com.bigpapituna.sharpears.engine.lessons.CheerFrequency.entries.firstOrNull { it.wire == value }
            ?.let { "Encouragement was set to ${it.title}." } ?: "Encouragement was changed."
        "cheerAloud" -> toggle("Spoken encouragement")
        // 2.15: Settings > Playing > Plain look and its Step sound.
        "plainLook" -> toggle("The Plain look")
        "plainStepSound" -> toggle("The step sound")
        "goalMin" -> value.toLongOrNull()?.takeIf { it in 1..600 }?.let { "The daily goal was set to ${count(it, "minute")}." } ?: "The daily goal was changed."
        "micOffsetDb", "sensitivityDb" -> "The microphone sensitivity was changed."
        "hintSeconds" -> "The hint speed was changed."
        "kind" -> "The player type was changed."
        "keyLabels" -> "The key labels were changed."
        // 2.8: Simple, Advanced or Full (engine/nav/Layout.kt).
        "layout" -> com.bigpapituna.sharpears.engine.nav.Layout.parse(value)?.let { "The layout was set to ${it.title}." } ?: "The layout was changed."
        else -> "A setting was changed."
    }
}

private val ACTION_SETTING_KEYS = mapOf("reader" to "reader", "colourNotes" to "colourNotes", "bedtime" to "bedtime", "micOffsetDb" to "micEasier", "wrongFlash" to "quietRoom")

private fun didThings(c: Ctx): String {
    val f = c.f
    val out = LinkedHashSet<String>()
    for (a in f.actions.take(8)) out += actionDone(c, a)
    val viaAction = f.actions.filter { it.action == com.bigpapituna.sharpears.engine.brief.ActionId.SETTING }.mapNotNull { it.params["setting"] }.toSet()
    for (s in f.settings.take(12)) {
        val wire = ACTION_SETTING_KEYS[s.key]
        if (wire != null && SettingId.of(wire)?.wire in viaAction) continue
        out += settingLine(s.key, s.value)
    }
    return out.take(8).joinToString(" ")
}

/** The sections of a day or week brief, each only when it has something to say. */
internal fun sections(c: Ctx, shown: Set<FactKind> = emptySet(), shownTrends: Set<TrendKind> = emptySet()): List<Brief.Section> {
    if (c.period == Period.SESSION) return emptyList()
    return buildList {
        habits(c, shown, shownTrends).takeIf { it.isNotBlank() }?.let { add(Brief.Section("Habits", it)) }
        skills(c, shown, shownTrends).takeIf { it.isNotBlank() }?.let { add(Brief.Section("Skills", it)) }
        stuck(c).takeIf { it.isNotBlank() }?.let { add(Brief.Section("Stuck and unstuck", it)) }
        didThings(c).takeIf { it.isNotBlank() }?.let { add(Brief.Section("What you did", it)) }
    }
}
