package com.bigpapituna.sharpears.engine.ear

import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.GameLine
import com.bigpapituna.sharpears.engine.Lit
import java.util.Random
import kotlin.math.abs

/**
 * Time signatures of the rhythm game. A tick is an eighth note; a beat is [ticksPerBeat] ticks (6/8 counts two
 * dotted-quarter beats). [bpm] is beats per minute.
 */
enum class Meter(val label: String, val beats: Int, val ticksPerBeat: Int, val bpm: Int) {
    FOUR_FOUR("4/4", 4, 2, 80),
    THREE_FOUR("3/4", 3, 2, 84),
    SIX_EIGHT("6/8", 2, 3, 56),
    ;
    val barTicks get() = beats * ticksPerBeat
}

/** One note or rest of a pattern: where it starts (in ticks) and how long it lasts. */
data class Slot(val startTick: Int, val ticks: Int, val rest: Boolean) {
    val name: String get() = when (ticks) {
        1 -> "eighth"; 2 -> "quarter"; 3 -> "dotted quarter"; 4 -> "half"; 6 -> "dotted half"
        else -> "$ticks eighths long"
    }.let { if (rest) "$it rest" else it }
}

/** A rhythm over [bars] bars of [meter], made of notes and rests that fill it exactly. */
class RhythmPattern(val meter: Meter, val bars: Int, val slots: List<Slot>, val bpm: Int = meter.bpm) {
    val totalTicks = bars * meter.barTicks
    /** Milliseconds per tick (eighth note). */
    val tickMs: Double = 60_000.0 / bpm / meter.ticksPerBeat
    val beatMs: Double = tickMs * meter.ticksPerBeat
    val totalMs: Long = timeOf(totalTicks)
    /** One bar of count-in clicks. */
    val countInMs: Long = Math.round(meter.beats * beatMs)

    init {
        var t = 0
        slots.forEach { require(it.startTick == t && it.ticks > 0) { "slots must be contiguous" }; t += it.ticks }
        require(t == totalTicks) { "slots fill $t of $totalTicks ticks" }
    }

    fun timeOf(tick: Int): Long = Math.round(tick * tickMs)
    val notes: List<Slot> = slots.filter { !it.rest }
    /** Onset of each note, ms from the start of the pattern: what the player must hit. */
    val noteTimesMs: List<Long> = notes.map { timeOf(it.startTick) }

    /** A click on every beat of every bar, the first of each bar accented. */
    fun beatCues(): List<Cue> = (0 until bars * meter.beats).map { Cue(Math.round(it * beatMs), click = true, accent = it % meter.beats == 0) }

    /** The pattern as the app plays it to the player: the beat clicks plus a [note] on every onset. */
    fun demoCues(note: Int = DEMO_NOTE): List<Cue> = (beatCues() + notes.map {
        val start = timeOf(it.startTick)
        Cue(start, note = note, holdMs = minOf(timeOf(it.startTick + it.ticks) - start - 30, 260L).toInt().coerceAtLeast(60))
    }).sortedWith(compareBy({ it.atMs }, { it.note != null }))

    /** One bar of clicks before the player starts. */
    fun countInCues(): List<Cue> = (0 until meter.beats).map { Cue(Math.round(it * beatMs), click = true, accent = it == 0) }

    /** "quarter, eighth, eighth, quarter rest, quarter" */
    fun words(): String = slots.joinToString(", ") { it.name }

    /** The slot holding time [ms] (a tap on a rest lands in a rest slot), or null past the end. */
    fun slotAt(ms: Long): Slot? = slots.firstOrNull { ms >= timeOf(it.startTick) - tickMs / 2 && ms < timeOf(it.startTick + it.ticks) - tickMs / 2 }

    companion object { const val DEMO_NOTE = 72 }
}

// Generating patterns --------------------------------------------------------------------------------------------

private class Template(val weight: Int, val parts: List<Pair<Int, Boolean>>, val beats: Int = 1, val minLevel: Int = 0,
    val notFirst: Boolean = false, val duple: Boolean = true)

private val TEMPLATES = listOf(
    // Two eighths per beat (4/4, 3/4)
    Template(6, listOf(2 to false)),                                            // quarter
    Template(4, listOf(1 to false, 1 to false)),                                // two eighths
    Template(2, listOf(2 to true), notFirst = true),                            // quarter rest
    Template(1, listOf(4 to false), beats = 2),                                 // half
    Template(2, listOf(1 to false, 1 to true), minLevel = 1),                   // eighth, eighth rest
    Template(2, listOf(3 to false, 1 to false), beats = 2, minLevel = 1),       // dotted quarter, eighth
    Template(2, listOf(1 to true, 1 to false), minLevel = 2, notFirst = true),  // eighth rest, eighth (offbeat)
    Template(2, listOf(1 to false, 2 to false, 1 to false), beats = 2, minLevel = 2),  // eighth, quarter, eighth (syncopation)
    // Three eighths per beat (6/8)
    Template(5, listOf(3 to false), duple = false),                             // dotted quarter
    Template(3, listOf(2 to false, 1 to false), duple = false),                 // quarter, eighth
    Template(2, listOf(1 to false, 2 to false), duple = false),                 // eighth, quarter
    Template(4, listOf(1 to false, 1 to false, 1 to false), duple = false),     // three eighths
    Template(1, listOf(3 to true), duple = false, notFirst = true),             // dotted quarter rest
    Template(1, listOf(1 to false, 2 to true), duple = false),                  // eighth, quarter rest
)

private fun meterFor(level: Int, rnd: Random) = when (level) {
    0 -> Meter.FOUR_FOUR
    1 -> if (rnd.nextBoolean()) Meter.THREE_FOUR else Meter.FOUR_FOUR
    else -> listOf(Meter.SIX_EIGHT, Meter.SIX_EIGHT, Meter.THREE_FOUR, Meter.FOUR_FOUR)[rnd.nextInt(4)]
}

private fun tryPattern(level: Int, rnd: Random): RhythmPattern {
    val meter = meterFor(level, rnd)
    val bars = if (level == 0) 1 else 2
    val raw = mutableListOf<Pair<Int, Boolean>>()
    for (bar in 0 until bars) {
        var beat = 0
        while (beat < meter.beats) {
            val options = TEMPLATES.filter {
                it.minLevel <= level && it.duple == (meter.ticksPerBeat == 2) && it.beats <= meter.beats - beat && !(it.notFirst && bar == 0 && beat == 0)
            }
            var r = rnd.nextInt(options.sumOf { it.weight })
            val t = options.first { r -= it.weight; r < 0 }
            raw += t.parts
            beat += t.beats
        }
    }
    // Neighbouring rests become one longer rest.
    val slots = mutableListOf<Slot>()
    var tick = 0
    for ((len, rest) in raw) {
        val last = slots.lastOrNull()
        if (rest && last != null && last.rest) slots[slots.lastIndex] = last.copy(ticks = last.ticks + len)
        else slots += Slot(tick, len, rest)
        tick += len
    }
    return RhythmPattern(meter, bars, slots)
}

/** A pattern that starts on a note, has at least three notes, is not mostly rests and not four plain quarters. */
internal fun generatePattern(level: Int, rnd: Random, prev: RhythmPattern?): RhythmPattern {
    repeat(300) {
        val p = tryPattern(level, rnd)
        val restTicks = p.slots.filter { it.rest }.sumOf { it.ticks }
        val ok = !p.slots.first().rest && p.notes.size >= 3 && restTicks * 10 <= p.totalTicks * 3 && p.slots.any { it.ticks != 2 } &&
            (prev == null || prev.meter != p.meter || prev.slots != p.slots)
        if (ok) return p
    }
    return RhythmPattern(Meter.FOUR_FOUR, 1, listOf(Slot(0, 2, false), Slot(2, 1, false), Slot(3, 1, false), Slot(4, 2, true), Slot(6, 2, false)))
}

// Grading --------------------------------------------------------------------------------------------------------

/** How one expected note was hit: within 80 ms, within 160 ms, tapped but further off, or not tapped. */
enum class Grade { GOOD, OK, OFF, MISSED }

/** [errorMs] is the tap minus the note: negative is early, positive is late. */
data class NoteGrade(val index: Int, val expectedMs: Long, val tapMs: Long?, val errorMs: Long?, val grade: Grade)

data class RhythmResult(val notes: List<NoteGrade>, val extraTaps: List<Long>) {
    val good get() = notes.count { it.grade == Grade.GOOD }
    val ok get() = notes.count { it.grade == Grade.OK }
    val off get() = notes.count { it.grade == Grade.OFF }
    val missed get() = notes.count { it.grade == Grade.MISSED }
    /** Two points for a good note, one for an ok one. */
    val points get() = good * 2 + ok
    val maxPoints get() = notes.size * 2
    /** Average signed timing error of the taps that landed on a note: positive means the player tends to be late. */
    val bias: Long? get() = notes.mapNotNull { it.errorMs }.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it) }
    /** A round is cleared at 70% of the points with at most one stray tap. */
    val passed get() = points * 10 >= maxPoints * 7 && extraTaps.size <= 1
}

const val GOOD_MS = 80L
const val OK_MS = 160L

/** The grade for one timing error, ignoring which note it belongs to. */
fun gradeFor(errorMs: Long) = when { abs(errorMs) <= GOOD_MS -> Grade.GOOD; abs(errorMs) <= OK_MS -> Grade.OK; else -> Grade.OFF }

/**
 * Matches [taps] to the [expected] onsets (all in ms from the start of the pattern) and grades each note. The
 * closest tap and note are paired first, so one tap is never used twice; a tap that pairs with nothing is an
 * extra (on a rest, or a double tap). Pure: no clock, no sound.
 */
fun gradeTaps(expected: List<Long>, taps: List<Long>, matchWindowMs: Long = 320): RhythmResult {
    val minGap = expected.zipWithNext { a, b -> b - a }.minOrNull() ?: Long.MAX_VALUE
    val window = maxOf(OK_MS + 40, minOf(matchWindowMs, if (minGap == Long.MAX_VALUE) matchWindowMs else minGap * 6 / 10))
    data class Pair2(val note: Int, val tap: Int, val diff: Long)
    val pairs = expected.indices.flatMap { n -> taps.indices.map { t -> Pair2(n, t, taps[t] - expected[n]) } }
        .filter { abs(it.diff) <= window }.sortedWith(compareBy({ abs(it.diff) }, { it.note }, { it.tap }))
    val noteTap = arrayOfNulls<Int>(expected.size)
    val tapUsed = BooleanArray(taps.size)
    for (p in pairs) if (noteTap[p.note] == null && !tapUsed[p.tap]) { noteTap[p.note] = p.tap; tapUsed[p.tap] = true }
    val grades = expected.mapIndexed { i, e ->
        val t = noteTap[i]
        if (t == null) NoteGrade(i, e, null, null, Grade.MISSED)
        else { val err = taps[t] - e; NoteGrade(i, e, taps[t], err, gradeFor(err)) }
    }
    return RhythmResult(grades, taps.filterIndexed { i, _ -> !tapUsed[i] })
}

// The game -------------------------------------------------------------------------------------------------------

/**
 * Rhythm tap. Each round the app plays a pattern with the beat clicking (the screen runs the cues from [demo]),
 * counts a bar in ([countIn]), and the player taps it over [tapPulse]'s clicks; the screen hands the tap times,
 * measured from the start of the pattern, to [grade]. Level 0: 4/4, quarters and eighths, one bar. Level 1: 4/4
 * or 3/4 with dotted notes and eighth rests, two bars. Level 2: adds 6/8, offbeats and syncopation.
 */
class RhythmTapGame(level: Int, seed: Long = System.nanoTime(), rounds: Int = 10) : TrainerBase(EarKind.RHYTHM, level, rounds) {
    private val rnd = Random(seed)
    var pattern: RhythmPattern = generatePattern(level, rnd, null); private set
    /** Set once the round's taps are graded. */
    var result: RhythmResult? = null; private set

    override val answering get() = Answering.TAPS

    init { newRound() }

    override fun newRound() { pattern = generatePattern(level, rnd, pattern); result = null }

    override fun prompt() = GameLine("Tap the rhythm", "Listen, then tap the rhythm.")
    override fun toPlay() = emptyList<Step>()
    override fun lit(): Map<Int, Lit> = emptyMap()
    override fun micNotes() = emptySet<Int>()
    override fun micGroups() = emptyList<Set<Int>>()
    override fun describe() = "${pattern.meter.label} at ${pattern.bpm}: ${pattern.words()}"

    /** What the player hears first: beat clicks and the pattern's notes. */
    fun demo() = pattern.demoCues()
    fun countIn() = pattern.countInCues()
    /** The clicks that keep the beat while the player taps. */
    fun tapPulse() = pattern.beatCues()
    /** Where the grid should show the player's tap, graded by the nearest note (before the round is graded). */
    fun liveGrade(tapMs: Long): Grade {
        val nearest = pattern.noteTimesMs.minByOrNull { abs(it - tapMs) } ?: return Grade.OFF
        return gradeFor(tapMs - nearest)
    }

    /**
     * Grades the round: [tapsMs] from the start of the pattern (the first beat after the count-in), minus [offsetMs]
     * for output latency. Ends the round.
     */
    fun grade(tapsMs: List<Long>, offsetMs: Long = 0): ChordGame.Answer {
        if (finished || roundOver) return ignored()
        val r = gradeTaps(pattern.noteTimesMs, tapsMs.map { it - offsetMs }.sorted())
        result = r
        return endRound(r.passed, explain(r))
    }

    private fun explain(r: RhythmResult): GameLine {
        val n = r.notes.size
        val hits = r.good + r.ok
        val head = when {
            r.good == n && r.extraTaps.isEmpty() -> "Spot on: all $n notes within 80 milliseconds."
            r.passed -> "Good: $hits of $n notes on the beat."
            else -> "Not quite: $hits of $n notes on the beat."
        }
        val issue = issue(r)
        val spokenIssue = issue?.replace(" ms ", " milliseconds ")
        val words = "It was: ${pattern.words()}."
        return GameLine(listOfNotNull(head, issue, words).joinToString(" "), listOfNotNull(head, spokenIssue).joinToString(" "))
    }

    private fun issue(r: RhythmResult): String? {
        r.notes.firstOrNull { it.grade == Grade.MISSED }?.let { return "You missed note ${it.index + 1}, the ${pattern.notes[it.index].name}." }
        r.notes.filter { it.grade == Grade.OFF }.maxByOrNull { abs(it.errorMs!!) }?.let {
            return "Note ${it.index + 1} was ${abs(it.errorMs!!)} ms ${if (it.errorMs > 0) "late" else "early"}."
        }
        r.extraTaps.firstOrNull()?.let { t ->
            return if (pattern.slotAt(t)?.rest == true) "You tapped on a rest." else "You tapped one extra time."
        }
        val bias = r.bias
        if (bias != null && abs(bias) >= 40 && r.notes.size >= 3) return "You tend to tap about ${abs(bias)} ms ${if (bias > 0) "late" else "early"}."
        return null
    }
}
