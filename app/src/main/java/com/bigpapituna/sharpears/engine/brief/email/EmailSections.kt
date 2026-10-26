package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.GameKind
import com.bigpapituna.sharpears.engine.brief.BriefFormat
import com.bigpapituna.sharpears.engine.brief.BriefTitles
import com.bigpapituna.sharpears.engine.brief.DayPart
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.HintStats
import com.bigpapituna.sharpears.engine.brief.LessonStat
import com.bigpapituna.sharpears.engine.brief.SongStat
import com.bigpapituna.sharpears.engine.brief.TrendKind
import com.bigpapituna.sharpears.engine.ear.EarKind
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.kid.KidPages
import com.bigpapituna.sharpears.engine.times
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * "The details, if you want them" (design D): five tap-to-open sections, the first open. A week's email has all five
 * (each says plainly when it has nothing); a day's is shorter: its lessons, the keys only when something slipped, rhythm
 * and listening when there was any, today's sessions instead of the four-week chart, and what changed only when something
 * did. Numbers from the findings' Stats (CONTRACT.md); the words here.
 */
internal class EmailSections(
    private val w: EmailWords,
    private val f: Findings,
    private val titles: BriefTitles,
    private val summary: (String) -> String?,
    private val extras: EmailExtras,
    private val zone: ZoneId,
) {
    private val week = w.period == EmailPeriod.WEEK
    private val s = f.stats
    private val span = if (week) "this week" else "today"
    private val name = w.name
    private val played = s.lessons.isNotEmpty() || s.songs.isNotEmpty() || s.notesDue > 0

    fun all(): List<DetailSection> {
        if (f.isEmpty) return emptyList()
        return listOfNotNull(lessons(), keys(), rhythm(), habits(), readiness()).mapIndexed { i, sec -> sec.copy(open = i == 0) }
    }

    private fun section(id: SectionId, title: String, blocks: List<Block>) = DetailSection(id, title, open = false, blocks = blocks)

    // ---- lessons ----

    private fun lessons(): DetailSection? {
        val hard = f.flags.filter { it.kind == FlagKind.STUCK_LESSON || it.kind == FlagKind.LOW_STARS }.flatMap { it.lessons }.toSet()
        val rows = s.lessons.map { l ->
            Row(titles.lesson(l.lessonId).ifBlank { "A lesson" }, summary(l.lessonId)?.trim()?.takeIf { it.isNotEmpty() }, lessonValue(l), l.stars,
                highlight = l.lessonId in hard)
        } + s.songs.map { so -> Row(titles.song(so.songId).ifBlank { "A song" }, "Song", songValue(so), so.bestStars) }
        val title = if (week) "Lessons this week" else "Lessons today"
        if (rows.isEmpty()) return if (week) section(SectionId.LESSONS, title, listOf(Block.Text("No lessons or songs $span; the time went to play and games."))) else null
        return section(SectionId.LESSONS, title, listOfNotNull(
            Block.Rows(rows),
            if (s.lessons.any { it.letGo > 0 }) Block.Text("\"Let go\" means the pup moved on after three tries, so nobody stays stuck; the lesson stays open to play again.", small = true) else null,
        ))
    }

    private fun lessonValue(l: LessonStat): String = listOfNotNull(
        when (l.stars) { null, 0 -> "not finished yet"; 1 -> "1 star"; else -> "${l.stars} stars" },
        l.tries.takeIf { it > 0 }?.let { BriefFormat.tries(it) },
        l.letGo.takeIf { it > 0 }?.let { if (it == 1) "a step let go" else "$it steps let go" },
        l.finishes.takeIf { it > 1 }?.let { "finished ${times(it)}" },
    ).joinToString(", ")

    private fun songValue(so: SongStat): String = listOfNotNull(
        "played ${times(so.runs)}", "best ${if (so.bestStars == 1) "1 star" else "${so.bestStars} stars"}",
        so.bestAccuracy?.let { "$it% in Perform" },
    ).joinToString(", ")

    // ---- keys and fingers ----

    private fun keys(): DetailSection? {
        val near = s.keySlips.sumOf { it.near }; val far = s.keySlips.sumOf { it.far }
        val wrong = near + far
        if (wrong == 0 && (!week || !played)) return null
        val title = "Keys and fingers"
        if (wrong == 0) return section(SectionId.KEYS, title, listOfNotNull(
            Block.Text("No slips $span: every key went where it was meant to."), hands(),
        ))
        val over = if (s.notesDue > 0) ", over ${s.notesDue} notes" else ""
        return section(SectionId.KEYS, title, listOfNotNull(
            Block.Text("Which keys slipped$over. A near miss is the key next door; a far miss is further away.", small = true),
            Block.Keys(keyCells(), "Slips by the key that was wanted, darker for more."),
            Block.Tiles(listOfNotNull(Tile("$near", if (near == 1) "near miss" else "near misses"), Tile("$far", if (far == 1) "far miss" else "far misses"),
                s.notesDue.takeIf { it > 0 }?.let { Tile("${(wrong * 100.0 / it).roundToInt()}", "slips per 100 notes") })),
            s.topSlip?.let { t -> Block.Text(slipLine(t.expected, t.played, t.count), label = "Most common slip:") },
            s.skippedNotes.takeIf { it > 0 && s.notesDue > 0 }?.let { Block.Text("${s.skippedNotes} of ${s.notesDue}, let go by the pup or missed in time.", label = "Skipped notes:") },
            s.hesitation?.takeIf { it.notes >= MIN_HESITATION_NOTES }?.let { h ->
                val worst = if (h.worstKey != null && h.worstMs != null && h.worstMs > h.meanMs) ", and ${EmailWords.seconds(h.worstMs.toDouble())} on ${EmailWords.keyName(h.worstKey)}, the key that takes longest" else ""
                Block.Text("about ${EmailWords.seconds(h.meanMs.toDouble())} to find each key$worst.", label = "Finding keys:")
            },
            hands(),
        ))
    }

    private fun hands(): Block? {
        val parts = listOfNotNull(s.right?.clean?.let { "right hand $it% clean" }, s.left?.clean?.let { "left hand $it% clean" })
        return if (parts.isEmpty()) null else Block.Text(parts.joinToString(", ") + ".", label = "Hands:")
    }

    private fun slipLine(expected: Int, played: Int, count: Int): String {
        val d = abs(expected - played)
        val wanted = EmailWords.keyName(expected)
        val struck = EmailWords.keyName(played)
        val octave = d != 0 && d % 12 == 0
        val what = if (octave) "$wanted an octave ${if (played > expected) "up" else "down"}" else struck
        val why = when {
            octave -> "The right note in another octave: the ear has it, the hand is finding the place."
            d <= 2 -> "That is the key next door, the most common slip while fingers learn their places."
            else -> "A slip that far is the hand still finding where the keys are."
        }
        return "$what when $wanted was wanted (${times(count)}). $why"
    }

    /** Every key from the C at or below the lowest key wanted to the C at or above the highest, at least an octave. */
    private fun keyCells(): List<KeyCell> {
        val lowest = s.keySlips.minOf { it.midi }; val highest = s.keySlips.maxOf { it.midi }
        val lo = lowest - Math.floorMod(lowest, 12)
        var hi = if (Math.floorMod(highest, 12) == 0) highest else highest + 12 - Math.floorMod(highest, 12)
        if (hi - lo < 12) hi = lo + 12
        val by = s.keySlips.associateBy { it.midi }
        val max = s.keySlips.maxOf { it.total }.coerceAtLeast(1)
        return (lo..hi).map { k ->
            val ks = by[k]
            val total = ks?.total ?: 0
            KeyCell(k, EmailWords.keyName(k), isBlack(k), ks?.near ?: 0, ks?.far ?: 0, if (total == 0) 0 else ceil(total * 3.0 / max).toInt().coerceIn(1, 3))
        }
    }

    // ---- rhythm and listening ----

    private fun rhythm(): DetailSection? {
        val before = { k: TrendKind -> f.trends.firstOrNull { it.kind == k }?.before?.roundToInt() }
        val blocks = listOfNotNull(
            f.facts.firstOrNull { it.kind == FactKind.TIMING }?.let { t ->
                Block.Text("notes landed ${t.value} ms from the beat on average" + (before(TrendKind.TIMING_MS)?.let { "; about $it ms ${earlier()}" } ?: "") + ".", label = "Timing:")
            },
            s.tempoSpreadMs?.let { ms ->
                Block.Text("in Perform the beat wandered about $ms ms from note to note" + (before(TrendKind.TEMPO_SPREAD)?.let { "; about $it ms ${earlier()}" } ?: "") + ".", label = "Steadiness:")
            },
            s.clap?.let { c -> Block.Text("${c.onBeat} of " + EmailWords.count(c.beats, "beat") + " with a clap on the drum.", label = "Clapping:") }
                ?: f.facts.firstOrNull { it.kind == FactKind.CLAP }?.let { Block.Text("a clap on ${it.value}% of the drum's beats.", label = "Clapping:") },
            s.ear.takeIf { it.isNotEmpty() }?.let { ear ->
                Block.Text(ear.take(4).joinToString("; ") { "${it.correct} of ${it.rounds} right in ${earTitle(it.game)}" } + ".", label = "Ear games:")
            },
            hintsLine(),
        )
        val title = "Rhythm and listening"
        if (blocks.isEmpty()) return if (week) section(SectionId.RHYTHM, title, listOf(Block.Text("No timed playing, clapping or ear games $span."))) else null
        return section(SectionId.RHYTHM, title, blocks)
    }

    private fun earlier() = if (week) "in earlier weeks" else "on recent days"

    private fun hintsLine(): Block? {
        val h = s.hints
        if (h.glow + h.point + h.play == 0 && s.hintsBefore == null) return null
        val now = hintParts(h, "a key glowed", "the pup pointed at a key", "the app played the key")
        val text = (if (now.isEmpty()) "none needed $span" else joinAnd(now)) +
            (s.hintsBefore?.let { b -> ". ${if (week) "Last week" else "The day before"}: " + hintParts(b, "glowed", "pointed", "played").let { if (it.isEmpty()) "none" else joinAnd(it) } } ?: "") + "."
        return Block.Text(text, label = "Hints:")
    }

    private fun hintParts(h: HintStats, glow: String, point: String, play: String) = listOfNotNull(
        h.glow.takeIf { it > 0 }?.let { "$glow ${times(it)}" }, h.point.takeIf { it > 0 }?.let { "$point ${times(it)}" },
        h.play.takeIf { it > 0 }?.let { "$play ${times(it)}" },
    )

    private fun earTitle(id: String): String =
        EarKind.entries.firstOrNull { it.id == id }?.title ?: GameKind.entries.firstOrNull { "chord:" + it.id == id }?.title ?: "the ear games"

    // ---- habits ----

    private fun habits(): DetailSection? {
        val blocks = ArrayList<Block>()
        if (week) {
            if (s.weeks.size == 4 && s.weeks.any { it > 0 }) {
                val top = s.weeks.max().coerceAtLeast(1)
                val labels = listOf("3 weeks ago", "2 weeks ago", "Last week", "This week")
                blocks += Block.Bars(s.weeks.mapIndexed { i, m -> Bar(labels[i], m, m.toFloat() / top) }, "Minutes of practice per week, the last four weeks",
                    joinAnd(s.weeks.map { "$it" }) + " minutes.")
            }
            whenLine()?.let { blocks += Block.Text(it, label = "When:") }
            s.sessionMinutes?.let { m ->
                val b = s.sessionMinutesBefore
                val change = when { b == null -> ""; m > b -> ", up from $b"; m < b -> ", down from $b"; else -> ", about the same as before" }
                blocks += Block.Text("about ${EmailWords.count(m, "minute")}$change.", label = "Session length:")
            }
        } else {
            val t = s.totals
            if (t.sessions > 0) blocks += Block.Text("${EmailWords.words(t.sessions)} ${if (t.sessions == 1) "session" else "sessions"} today, " +
                "${EmailWords.count(t.minutes, "minute")} on screen with ${t.practiceMinutes} practising" + (whenToday()?.let { ", $it" } ?: "") + ".", label = "Sessions:")
        }
        mix()?.let { (meter, line) -> blocks += meter; blocks += Block.Text(line, label = "Lessons and play:") }
        if (s.noisyRoom > 0) blocks += Block.Text("the microphone heard the room ${times(s.noisyRoom)} instead of the piano.", label = "Room:")
        if (blocks.isEmpty()) return null
        return section(SectionId.HABITS, if (week) "Habits and trends" else "Today's sessions", blocks)
    }

    private fun whenLine(): String? {
        val parts = s.timeOfDay.filter { it.sessions > 0 }.sortedWith(compareBy({ -it.sessions }, { it.part.ordinal }))
        if (parts.isEmpty()) return null
        return joinAnd(parts.map { "${PARTS.getValue(it.part)} ${times(it.sessions)}" }) + "."
    }

    private fun whenToday(): String? {
        val parts = s.timeOfDay.filter { it.sessions > 0 }.sortedBy { it.part.ordinal }
        if (parts.isEmpty()) return null
        return "in the " + joinAnd(parts.map { PART_OF.getValue(it.part) })
    }

    /** The mix as a meter and a line, percents adding up to 100 (largest remainders). */
    private fun mix(): Pair<Block, String>? {
        val m = s.mix
        val raw = listOf("lessons" to m.lessons, "songs" to m.songs, "toys" to m.toys).filter { it.second > 0 }
        val total = raw.sumOf { it.second }
        if (total == 0) return null
        val exact = raw.map { it.second * 100.0 / total }
        val pct = exact.map { it.toInt() }.toMutableList()
        exact.withIndex().sortedByDescending { it.value - it.value.toInt() }.take(100 - pct.sum()).forEach { pct[it.index]++ }
        val parts = raw.mapIndexed { i, (label, _) -> MeterPart(label, pct[i]) }
        return Block.Meter(parts) to joinAnd(parts.map { "${it.percent}% ${it.label}" }) + "."
    }

    // ---- readiness and what changed ----

    private fun readiness(): DetailSection? {
        val kid = f.kid()
        val flags = f.flags.map { it.kind }.toSet()
        val lines = ArrayList<Block>()
        if (kid) lines += Block.Text(when {
            f.reader -> "yes, the keys show letters."
            FlagKind.READY_FOR_LETTERS in flags -> "ready. The letter lessons have gone well for two weeks, so the keys can show letters instead of animals."
            else -> "not yet, so the keys show animals. The app will say when the letter lessons show it is time."
        }, label = "Reading letters:")
        if (FlagKind.READY_FOR_ROAD in flags) lines += Block.Text("the playground is finished, and the big kids' road is next.", label = "Next step:")
        if (FlagKind.CRUISING in flags) lines += Block.Text("new lessons are coming easily, nearly three stars each.", label = "Pace:")
        extras.path?.let { p ->
            val page = titles.page(p.pageId).takeIf { it.isNotBlank() }?.let(KidPages::inSentence) ?: "this page"
            val next = p.nextPageId?.let(titles::page)?.takeIf { it.isNotBlank() }?.let { " $it opens after it." }.orEmpty()
            lines += Block.Text("${p.done} of " + EmailWords.count(p.total, "stone") + " done on $page.$next", label = "Next page:")
        }
        if (f.actions.isNotEmpty()) lines += Block.Text(f.actions.sortedBy { it.at }.joinToString(" ") { a ->
            val day = Instant.ofEpochMilli(a.at).atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            "On $day " + BriefFormat.actionTaken(a, titles).replaceFirstChar { it.lowercase() }
        }, label = "You did:")
        val changed = f.settings.groupBy { it.key }.mapNotNull { (_, l) -> l.maxBy { it.at }.let { settingLine(it.key, it.value) } }
        if (changed.isNotEmpty()) lines += Block.Text(changed.joinToString("; ") + ".", label = "Changed:")
        if (kid) extras.bedtimeOn?.let { lines += Block.Text(if (it) "on." else "off.", label = "Wind down after the goal:") }
        val somethingChanged = f.actions.isNotEmpty() || changed.isNotEmpty() || flags.any { it in READY }
        if (lines.isEmpty() || (!week && !somethingChanged)) return null
        return section(SectionId.READINESS, "Readiness and what changed", lines)
    }

    private fun settingLine(key: String, value: String): String? {
        val on = value == "true"
        return when (key) {
            "reader" -> if (on) "the keys show letters" else "the keys show animals"
            "colourNotes" -> "colour notes ${if (on) "on" else "off"}"
            "bedtime" -> "wind down after the goal ${if (on) "on" else "off"}"
            "bedtimeSongs" -> if (on) "Songs and the Jam close after the goal too" else "Songs and the Jam stay open after the goal"
            "capMin" -> value.toIntOrNull()?.let { if (it <= 0) "the daily time is off" else "the daily time is ${EmailWords.count(it, "minute")}" }
            "goalMin" -> value.toIntOrNull()?.let { "the daily goal is ${EmailWords.count(it, "minute")}" }
            "micOffsetDb" -> "the microphone's sensitivity changed"
            "wrongFlash" -> if (on) "wrong keys light up again on the phone" else "wrong keys no longer light up on the phone"
            "hintSeconds" -> "the hints' timing changed"
            else -> null
        }
    }

    companion object {
        /** Notes timed before "finding keys" says anything. */
        const val MIN_HESITATION_NOTES = 5
        private val READY = setOf(FlagKind.READY_FOR_LETTERS, FlagKind.READY_FOR_ROAD, FlagKind.CRUISING)
        private val PARTS = mapOf(DayPart.MORNING to "mornings", DayPart.AFTERNOON to "afternoons", DayPart.EVENING to "evenings", DayPart.NIGHT to "late evenings")
        private val PART_OF = mapOf(DayPart.MORNING to "morning", DayPart.AFTERNOON to "afternoon", DayPart.EVENING to "evening", DayPart.NIGHT to "late evening")

        /** "a", "a and b", "a, b and c". */
        fun joinAnd(items: List<String>): String = when (items.size) {
            0 -> ""; 1 -> items[0]
            else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
        }
    }
}
