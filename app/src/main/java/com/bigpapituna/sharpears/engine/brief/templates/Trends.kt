package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Direction
import com.bigpapituna.sharpears.engine.brief.Trend
import com.bigpapituna.sharpears.engine.brief.TrendKind
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The trend lines: each kind has three phrasings for a move for the better, three for a move for the worse and four
 * frames for a steady one. A trend always compares the person with their own earlier periods ({earlier}), never with
 * anyone else.
 */
internal object TrendBank {
    private val better = HashMap<TrendKind, List<L>>()
    private val worse = HashMap<TrendKind, List<L>>()

    fun lines(t: Trend): List<L> = when {
        t.direction == Direction.STEADY -> STEADY
        t.good == true -> better.getValue(t.kind)
        else -> worse.getValue(t.kind)
    }

    fun sizes(): Map<String, Int> = better.entries.associate { "trend ${it.key}/BETTER" to it.value.size } +
        worse.entries.associate { "trend ${it.key}/WORSE" to it.value.size } + mapOf("trend STEADY (each kind)" to STEADY.size)

    private fun b(k: TrendKind, vararg ls: L) { better[k] = ls.toList() }
    private fun w(k: TrendKind, vararg ls: L) { worse[k] = ls.toList() }

    val STEADY = listOf(
        l("{Subject} held steady {span}, at {now} against {before} in {earlier}."),
        l("No big change in {subject}: {now} {span}, {before} in {earlier}."),
        l("{Subject} stayed about where it was: {now} {span}."),
        l("In line with {earlier}: {subject} was {now} {span}."),
    )

    /** What "worse" trends get as a gentle reason, in the full report. */
    val WOBBLE_HABIT = listOf(
        "Weeks and days vary, and one lighter stretch is not a pattern.",
        "A quieter patch is common, and the next few days will say more than this one.",
        "Practice comes in waves, and a dip often comes before the next push.",
        "Ups and downs in how often someone plays are ordinary.",
    )
    val WOBBLE_SKILL = listOf(
        "Skills wobble before they settle, and new material often pulls a number down for a while.",
        "A dip like this usually means harder music, not slower learning.",
        "Numbers move around while the hands learn something new, and they tend to come back.",
        "One stretch with a lower number is common when the pieces get harder.",
    )

    init {
        b(TrendKind.MINUTES,
            l("Practice {span} came to {now}, up from {before} in {earlier}."),
            l("More time at the keys: {now} {span} against {before} in {earlier}."),
            l("Practice time grew to {now} {span}, from {before} in {earlier}."))
        w(TrendKind.MINUTES,
            l("Practice time {span} was {now}, down from {before} in {earlier}."),
            l("A lighter {spanNoun}: {now} against {before} in {earlier}."),
            l("Time at the piano eased to {now} {span}, from {before} in {earlier}."))
        b(TrendKind.DAYS,
            l("{Now} at the piano {span}, up from {before} in {earlier}."),
            l("More days at the piano: {now} against {before} in {earlier}."),
            l("The piano came out on {now} {span}, where {earlier} averaged {before}."))
        w(TrendKind.DAYS,
            l("{Now} at the piano {span}, down from {before} in {earlier}."),
            l("Fewer practice days: {now} against {before} in {earlier}."),
            l("The piano came out on {now} {span}, where {earlier} averaged {before}."))
        b(TrendKind.SESSIONS,
            l("{Now} {span}, up from {before} in {earlier}."),
            l("More sessions: {now} against {before} in {earlier}."),
            l("The piano came out in {now} {span}, where {earlier} had {before}."))
        w(TrendKind.SESSIONS,
            l("{Now} {span}, down from {before} in {earlier}."),
            l("Fewer sessions: {now} against {before} in {earlier}."),
            l("Sessions eased to {now} {span}, from {before} in {earlier}."))
        b(TrendKind.STARS,
            l("Lesson stars averaged {now} {span}, up from {before} in {earlier}."),
            l("Stars are climbing: {now} on average {span} against {before} in {earlier}."),
            l("Lessons finished with {now} on average {span}, better than {before} in {earlier}."))
        w(TrendKind.STARS,
            l("Lesson stars averaged {now} {span}, down from {before} in {earlier}."),
            l("Stars dipped to {now} on average {span}, from {before} in {earlier}."),
            l("A tougher stretch for stars: {now} on average {span} against {before} in {earlier}."))
        b(TrendKind.ACCURACY,
            l("Perform accuracy reached {now} {span}, up from {before} in {earlier}."),
            l("Playing in time is landing better: {now} {span} against {before} in {earlier}."),
            l("Accuracy in Perform climbed to {now} {span}, from {before} in {earlier}."))
        w(TrendKind.ACCURACY,
            l("Perform accuracy was {now} {span}, down from {before} in {earlier}."),
            l("Playing along with the beat was harder: {now} {span} against {before} in {earlier}."),
            l("Accuracy in Perform eased to {now} {span}, from {before} in {earlier}."))
        b(TrendKind.TIMING_MS,
            l("Timing tightened to {now} from the beat {span}, from {before} in {earlier}."),
            l("Closer to the beat: {now} {span}, where {earlier} averaged {before}."),
            l("Notes are landing nearer the beat: {now} {span} against {before} in {earlier}."))
        w(TrendKind.TIMING_MS,
            l("Timing loosened to {now} from the beat {span}, from {before} in {earlier}."),
            l("Further from the beat: {now} {span}, where {earlier} averaged {before}."),
            l("The beat was harder to hit: {now} off {span} against {before} in {earlier}."))
        b(TrendKind.NEAR_SHARE,
            l("When a key was missed it was usually the one next door: {now} {span}, up from {before} in {earlier}."),
            l("Slips are landing closer to the target: {now} were near misses {span}, against {before} in {earlier}."),
            l("The hand is finding the neighbourhood of the right key: {now} near misses {span}, from {before} in {earlier}."))
        w(TrendKind.NEAR_SHARE,
            l("Near misses were {now} of the slips {span}, down from {before} in {earlier}."),
            l("More far misses {span}: near misses fell to {now}, from {before} in {earlier}."),
            l("The hand roamed further from the target {span}: {now} near misses against {before} in {earlier}."))
        b(TrendKind.WRONG_PER_RUN,
            l("Slips per run fell to {now} {span}, from {before} in {earlier}."),
            l("Cleaner runs: {now} per graded run {span} against {before} in {earlier}."),
            l("Fewer slips per run, {now} {span} against {before} in {earlier}."))
        w(TrendKind.WRONG_PER_RUN,
            l("Slips per run rose to {now} {span}, from {before} in {earlier}."),
            l("More slips per graded run: {now} {span} against {before} in {earlier}."),
            l("Runs had more slips {span}, {now} each, where {earlier} averaged {before}."))
        b(TrendKind.LEFT_HAND,
            l("Left hand hit rate rose to {now} {span}, from {before} in {earlier}."),
            l("The left hand is catching up: {now} {span} against {before} in {earlier}."),
            l("Left-hand notes landed {now} of the time {span}, up from {before} in {earlier}."))
        w(TrendKind.LEFT_HAND,
            l("Left hand hit rate was {now} {span}, down from {before} in {earlier}."),
            l("The left hand had a harder stretch: {now} {span} against {before} in {earlier}."),
            l("Left-hand notes landed {now} of the time {span}, from {before} in {earlier}."))
        b(TrendKind.RIGHT_HAND,
            l("Right hand hit rate rose to {now} {span}, from {before} in {earlier}."),
            l("The right hand is sharper: {now} {span} against {before} in {earlier}."),
            l("Right-hand notes landed {now} of the time {span}, up from {before} in {earlier}."))
        w(TrendKind.RIGHT_HAND,
            l("Right hand hit rate was {now} {span}, down from {before} in {earlier}."),
            l("The right hand had a harder stretch: {now} {span} against {before} in {earlier}."),
            l("Right-hand notes landed {now} of the time {span}, from {before} in {earlier}."))
        b(TrendKind.EAR,
            l("Ear game results climbed to {now} right {span}, from {before} in {earlier}."),
            l("Ears are sharpening: {now} right {span} against {before} in {earlier}."),
            l("Ear rounds went {now} right {span}, up from {before} in {earlier}."))
        w(TrendKind.EAR,
            l("Ear game results were {now} right {span}, down from {before} in {earlier}."),
            l("The ear games were harder: {now} right {span} against {before} in {earlier}."),
            l("Ear rounds went {now} right {span}, from {before} in {earlier}."))
        b(TrendKind.CLAP,
            l("Clapping on the beat reached {now} {span}, up from {before} in {earlier}."),
            l("More claps landed on the drum's beats: {now} {span} against {before} in {earlier}."),
            l("Clap along improved to {now} {span}, from {before} in {earlier}."))
        w(TrendKind.CLAP,
            l("Clapping on the beat was {now} {span}, down from {before} in {earlier}."),
            l("Fewer claps landed on the drum's beats: {now} {span} against {before} in {earlier}."),
            l("Clap along eased to {now} {span}, from {before} in {earlier}."))
        b(TrendKind.PLAY_HINTS,
            l("The app played the key in {now} of stuck moments {span}, down from {before} in {earlier}."),
            l("Less help needed: the app played the key in {now} of stuck moments {span} against {before} in {earlier}."),
            l("Hints eased: {now} of stuck moments ended with the app playing the key, from {before} in {earlier}."))
        w(TrendKind.PLAY_HINTS,
            l("The app played the key in {now} of stuck moments {span}, up from {before} in {earlier}."),
            l("More help needed: the app played the key in {now} of stuck moments {span} against {before} in {earlier}."),
            l("Hints went further {span}: {now} of stuck moments ended with the app playing the key, from {before} in {earlier}."))
        b(TrendKind.HESITATION_MS,
            l("Keys came quicker {span}: {now} to find each, against {before} in {earlier}."),
            l("Less searching for keys {span}, {now} per note, down from {before} in {earlier}."),
            l("The wait for each key fell to {now} {span}, from {before} in {earlier}."))
        w(TrendKind.HESITATION_MS,
            l("Finding each key took a little longer {span}: {now} against {before} in {earlier}."),
            l("More searching for keys {span}, {now} per note, up from {before} in {earlier}."),
            l("The wait for each key grew to {now} {span}, from {before} in {earlier}."))
        b(TrendKind.TEMPO_SPREAD,
            l("A steadier beat {span}: notes wandered {now}, against {before} in {earlier}."),
            l("The tempo held tighter {span}, {now} from note to note, down from {before} in {earlier}."),
            l("The beat steadied to {now} of wobble {span}, from {before} in {earlier}."))
        w(TrendKind.TEMPO_SPREAD,
            l("The beat wandered a little more {span}: {now} against {before} in {earlier}."),
            l("The tempo was looser {span}, {now} from note to note, up from {before} in {earlier}."),
            l("Notes drifted more around the beat {span}: {now}, from {before} in {earlier}."))
    }
}

private fun one(x: Double) = String.format(Locale.ROOT, "%.1f", x)

/** A trend's value in its own unit; [about] marks a mean of earlier periods. */
private fun amount(kind: TrendKind, x: Double, about: Boolean): String {
    val r = x.roundToInt().toLong()
    val body = when (kind) {
        TrendKind.MINUTES -> count(r, "minute")
        TrendKind.DAYS -> count(r, "day")
        TrendKind.SESSIONS -> count(r, "session")
        TrendKind.STARS -> one(x) + " stars"
        TrendKind.TIMING_MS, TrendKind.TEMPO_SPREAD -> "$r ms"
        TrendKind.HESITATION_MS -> one(x / 1000) + " seconds"
        TrendKind.WRONG_PER_RUN -> one(x) + " slips"
        else -> percent(r)
    }
    return if (about) "about $body" else body
}

private fun subject(kind: TrendKind) = when (kind) {
    TrendKind.MINUTES -> "practice time"
    TrendKind.DAYS -> "days at the piano"
    TrendKind.SESSIONS -> "the number of sessions"
    TrendKind.STARS -> "lesson stars"
    TrendKind.ACCURACY -> "Perform accuracy"
    TrendKind.TIMING_MS -> "timing"
    TrendKind.NEAR_SHARE -> "the share of near misses"
    TrendKind.WRONG_PER_RUN -> "slips per run"
    TrendKind.LEFT_HAND -> "the left hand's hit rate"
    TrendKind.RIGHT_HAND -> "the right hand's hit rate"
    TrendKind.EAR -> "ear game results"
    TrendKind.CLAP -> "clapping on the beat"
    TrendKind.PLAY_HINTS -> "how often the app plays the key"
    TrendKind.HESITATION_MS -> "the time to find each key"
    TrendKind.TEMPO_SPREAD -> "how much the beat wanders"
}

/** One trend as a sentence; null when [t] has no earlier period to compare with. */
internal fun trendLine(c: Ctx, t: Trend, extra: Int = 0): String? {
    if (t.periods <= 0) return null
    val vars = c.common() + mapOf(
        "now" to amount(t.kind, t.now, about = false), "before" to amount(t.kind, t.before, about = true),
        "subject" to subject(t.kind),
    )
    val slot = "trend ${t.kind} ${if (t.direction == Direction.STEADY) "steady" else t.good.toString()}"
    return c.phrase(slot, TrendBank.lines(t), vars, extra)
}

/** The trend kinds the habit section talks about; the rest belong to skills. */
internal val HABIT_TRENDS = setOf(TrendKind.MINUTES, TrendKind.DAYS, TrendKind.SESSIONS)

/** The trend that says more about the same number than its fact does ("six days, up from two"). */
internal val TREND_OF = mapOf(
    com.bigpapituna.sharpears.engine.brief.FactKind.PRACTICE_MINUTES to TrendKind.MINUTES,
    com.bigpapituna.sharpears.engine.brief.FactKind.DAYS_PRACTISED to TrendKind.DAYS,
    com.bigpapituna.sharpears.engine.brief.FactKind.SESSIONS to TrendKind.SESSIONS,
)
