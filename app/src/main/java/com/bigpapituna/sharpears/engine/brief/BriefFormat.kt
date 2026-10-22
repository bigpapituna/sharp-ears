package com.bigpapituna.sharpears.engine.brief

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Words and numbers for the screens and the export, pure so both say the same thing and JVM tests can hold them to the
 * copy rules (sentence case, no dashes, no symbol glyphs as separators). Everything here is about a [Findings]' stats and a
 * [Brief]'s notes; the prose itself comes from a writer.
 */
object BriefFormat {
    private val EN = Locale.ENGLISH

    fun minutes(n: Int): String = if (n == 1) "1 min" else "$n min"

    /** "Wed" for "2026-10-21". */
    fun dayShort(date: String): String =
        runCatching { LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, EN) }.getOrDefault(date.takeLast(2))

    /** "W" for the paw strip's initial. */
    fun dayInitial(date: String): String = dayShort(date).take(1)

    fun tries(n: Int): String = if (n == 1) "1 try" else "$n tries"

    fun stars(stars: Int?): String = when (stars) { null, 0 -> "not finished yet"; 1 -> "1 star"; else -> "$stars stars" }

    /** "Copy the frogs: 3 stars, 2 tries" (steps let go: ", the pup let 1 step go"). */
    fun lessonLine(s: LessonStat, titles: BriefTitles): String =
        titles.lesson(s.lessonId) + ": " + stars(s.stars) + ", " + tries(s.tries) +
            if (s.letGo > 0) ", the pup let ${s.letGo} step${if (s.letGo == 1) "" else "s"} go" else ""

    fun songLine(s: SongStat, titles: BriefTitles): String =
        titles.song(s.songId) + ": played " + com.bigpapituna.sharpears.engine.times(s.runs) +
            (if (s.bestStars > 0) ", best ${stars(s.bestStars)}" else "")

    fun toyLabel(t: ToyKind): String = when (t) { ToyKind.JAM -> "Jam"; ToyKind.PUP_SAYS -> "Pup says"; ToyKind.CLAP_ALONG -> "Clap along"; ToyKind.ARCADE -> "Games" }

    fun partLabel(p: DayPart): String = when (p) {
        DayPart.MORNING -> "Morning"; DayPart.AFTERNOON -> "Afternoon"; DayPart.EVENING -> "Evening"; DayPart.NIGHT -> "Night"
    }

    fun partHours(p: DayPart): String = when (p) {
        DayPart.MORNING -> "5 am to noon"; DayPart.AFTERNOON -> "noon to 5 pm"; DayPart.EVENING -> "5 to 9 pm"; DayPart.NIGHT -> "9 pm to 5 am"
    }

    /** The skill's name in a section title. */
    fun skillLabel(k: SkillKind): String = when (k) {
        SkillKind.STARS -> "Stars on lessons"
        SkillKind.ACCURACY -> "Accuracy in time"
        SkillKind.TIMING_MS -> "Distance from the beat"
        SkillKind.NEAR_SHARE -> "Misses next door"
        SkillKind.LEFT_HAND -> "Left hand"
        SkillKind.RIGHT_HAND -> "Right hand"
        SkillKind.EAR -> "Ear games"
        SkillKind.CLAP -> "Clapping the beat"
        SkillKind.PLAY_HINTS -> "Times the app played the key"
    }

    /** True when a rising line is an improvement (the timing distance and the hints are better when they fall). */
    fun upIsGood(k: SkillKind): Boolean = k != SkillKind.TIMING_MS && k != SkillKind.PLAY_HINTS

    /** The value as the full report prints it ("2.4", "86%", "40 ms"). */
    fun skillValue(k: SkillKind, v: Double): String = when (k) {
        SkillKind.STARS -> "%.1f".format(Locale.ROOT, v)
        SkillKind.TIMING_MS -> "${v.toInt()} ms"
        else -> "${v.toInt()}%"
    }

    /** One plain line for a skill's four weeks: "From 2.0 to 2.4 over four weeks." or "Not enough yet to draw a line." */
    fun skillSummary(s: SkillSeries): String {
        val measured = s.points.filterNotNull()
        if (measured.size < 2) return "Not enough yet to draw a line."
        val first = measured.first(); val last = measured.last()
        val from = skillValue(s.kind, first); val to = skillValue(s.kind, last)
        val moved = abs(last - first) >= minStep(s.kind)
        return if (!moved) "Steady around $to over the weeks." else "From $from to $to over the weeks."
    }

    private fun minStep(k: SkillKind) = when (k) {
        SkillKind.STARS -> 0.25; SkillKind.TIMING_MS -> 15.0; SkillKind.LEFT_HAND, SkillKind.RIGHT_HAND -> 8.0
        else -> 5.0
    }

    /** Hint steps as the report says them. Empty when no hints happened. */
    fun hintLines(h: HintStats): List<String> = buildList {
        if (h.glow > 0) add("The key glowed: ${h.glow} time${if (h.glow == 1) "" else "s"}")
        if (h.point > 0) add("The hand pointed: ${h.point} time${if (h.point == 1) "" else "s"}")
        if (h.play > 0) add("The app played the key: ${h.play} time${if (h.play == 1) "" else "s"}")
        if (h.octave > 0) add("Right note, wrong octave: ${h.octave} time${if (h.octave == 1) "" else "s"}")
    }

    fun noisyLine(n: Int): String = when (n) {
        0 -> "No noisy moments. The microphone heard the piano clearly."
        1 -> "1 noisy moment, when the microphone heard the room."
        else -> "$n noisy moments, when the microphone heard the room."
    }

    /** What an action taken says in the brief's "since last time" list ("You set Copy the frogs as the next stone"). */
    fun actionTaken(a: ActionTaken, titles: BriefTitles): String {
        val p = a.params
        val lesson = p["lesson"]?.let(titles::lesson)
        val page = p["page"]?.let(titles::page)?.let(com.bigpapituna.sharpears.engine.kid.KidPages::inSentence)
        return when (a.action) {
            ActionId.SET_NEXT -> if (lesson != null) "You set $lesson as the next stone." else "You put the next lesson first."
            ActionId.REPLAY_FOR_STARS -> "You replayed ${lesson ?: "a lesson"} for stars."
            ActionId.PRACTISE_TOGETHER -> "You practised together" + (lesson ?: p["song"]?.let(titles::song))?.let { " on $it." }.orEmpty().ifEmpty { "." }
            ActionId.FRESH_START -> "You started ${page ?: "a page"} again."
            ActionId.PACE_FOCUS -> "You focused on ${page ?: "one page"}."
            ActionId.PACE_UNLOCK -> "You opened the page after ${page ?: "this one"}."
            ActionId.SETTING -> settingTaken(p)
            ActionId.TEACHER_ASSIGN -> "You added ${p["song"]?.let(titles::song) ?: "a song"} to the shelf."
        }
    }

    private fun settingTaken(p: Map<String, String>): String {
        val on = p["value"] != "false"
        return when (SettingId.of(p["setting"])) {
            SettingId.READER -> if (on) "You switched the keys to letters." else "You switched the keys to animals."
            SettingId.BEDTIME -> if (on) "You turned bedtime on." else "You turned bedtime off."
            SettingId.COLOUR_NOTES -> if (on) "You turned colour notes on." else "You turned colour notes off."
            SettingId.MIC_EASIER -> "You made the microphone a little easier."
            SettingId.QUIET_ROOM -> "You quieted the wrong key lights."
            null -> "You changed a setting."
        }
    }

    /** "Written by AI at 6:42 pm", "Written on the phone yesterday at 6:42 pm", "Written on the phone on Mon 19 Oct at 6:42 pm". */
    fun writtenNote(brief: Brief, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val by = if (brief.writtenBy == Brief.Writer.AI) "by AI" else "on the phone"
        val written = Instant.ofEpochMilli(brief.writtenAt).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = written.toLocalDate()
        val at = clock(written.hour * 60 + written.minute)
        return "Written $by " + when {
            day == today -> "at $at"
            day == today.minusDays(1) -> "yesterday at $at"
            else -> "on ${day.dayOfWeek.getDisplayName(TextStyle.SHORT, EN)} ${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, EN)} at $at"
        }
    }

    /** What the findings cover: "Today", "Wed 21 Oct, 4:10 to 4:32 pm", "15 to 21 Oct". */
    fun rangeLabel(f: Findings, zone: ZoneId = ZoneId.systemDefault()): String {
        val from = Instant.ofEpochMilli(f.range.fromMs).atZone(zone)
        val to = Instant.ofEpochMilli(f.range.toMs).atZone(zone)
        fun d(z: java.time.ZonedDateTime) = "${z.dayOfMonth} ${z.month.getDisplayName(TextStyle.SHORT, EN)}"
        return when (f.period) {
            Period.SESSION -> "${from.dayOfWeek.getDisplayName(TextStyle.SHORT, EN)} ${d(from)}, ${clock(from.hour * 60 + from.minute)} to ${clock(to.hour * 60 + to.minute)}"
            Period.DAY -> "${from.dayOfWeek.getDisplayName(TextStyle.SHORT, EN)} ${d(from)}"
            Period.WEEK -> {
                val first = to.toLocalDate().minusDays(6)
                val a = if (first.month == to.month) "${first.dayOfMonth}" else "${first.dayOfMonth} ${first.month.getDisplayName(TextStyle.SHORT, EN)}"
                "$a to ${d(to)}"
            }
        }
    }

    /** "6:42 pm" from minutes after midnight (a clock face, so no "noon" word: the weekly picker has its own). */
    fun clock(minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(Locale.ROOT, h12, m, if (h < 12) "am" else "pm")
    }

    /** The heading words of a recommendation's card. */
    fun kindLabel(k: RecommendationKind): String = when (k) {
        RecommendationKind.UNSTICK -> "Getting unstuck"
        RecommendationKind.MOVE_ON -> "Moving on"
        RecommendationKind.POLISH -> "Polishing"
        RecommendationKind.TOGETHER -> "Together"
        RecommendationKind.ROUTINE -> "Routine"
        RecommendationKind.SETTINGS -> "A small change"
        RecommendationKind.NEXT_STEP -> "Next step"
        RecommendationKind.FOCUS -> "Focus"
        RecommendationKind.NEW_MUSIC -> "New music"
    }

    /** The periods' tab names. */
    fun periodLabel(p: Period): String = when (p) { Period.SESSION -> "Session"; Period.DAY -> "Today"; Period.WEEK -> "Week" }
}

/** Chart maths for the small trend lines, pure: where each point goes in a box, with gaps where a week had too little. */
object Spark {
    /** A point in a box (x from the left, y from the top). */
    data class Pt(val x: Float, val y: Float)

    /**
     * The points of [values] (oldest first) in a [w] by [h] box with [pad] around it: spread evenly left to right, scaled
     * between the lowest and highest measured value (a flat line sits in the middle), null stays null (a gap).
     */
    fun points(values: List<Double?>, w: Float, h: Float, pad: Float = 0f): List<Pt?> {
        val measured = values.filterNotNull()
        if (values.isEmpty()) return emptyList()
        val lo = measured.minOrNull() ?: 0.0; val hi = measured.maxOrNull() ?: 0.0
        val span = hi - lo
        val innerW = (w - 2 * pad).coerceAtLeast(0f); val innerH = (h - 2 * pad).coerceAtLeast(0f)
        return values.mapIndexed { i, v ->
            if (v == null) null else Pt(
                pad + if (values.size == 1) innerW / 2 else innerW * i / (values.size - 1),
                pad + if (span < 1e-9) innerH / 2 else (innerH * (1 - (v - lo) / span)).toFloat(),
            )
        }
    }

    /** The runs of consecutive points a line may be drawn through (a gap splits it); a lone point is a run of one (draw a dot). */
    fun runs(points: List<Pt?>): List<List<Pt>> = buildList {
        var run = ArrayList<Pt>()
        for (p in points) { if (p == null) { if (run.isNotEmpty()) add(run); run = ArrayList() } else run += p }
        if (run.isNotEmpty()) add(run)
    }

    /** Bar heights in 0..1 for the seven-day strip: minutes against the biggest day or the goal, whichever is larger (a day with none is 0). */
    fun dayFractions(days: List<DayStat>, goalMinutes: Int?): List<Float> {
        val top = maxOf(days.maxOfOrNull { it.practiceMinutes } ?: 0, goalMinutes ?: 0, 1)
        return days.map { (it.practiceMinutes.toFloat() / top).coerceIn(0f, 1f) }
    }

    /** Shares for a bar list (toys, time of day): each value's fraction of the biggest, so the longest bar fills. */
    fun shares(values: List<Int>): List<Float> {
        val top = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        return values.map { (it.toFloat() / top).coerceIn(0f, 1f) }
    }
}
