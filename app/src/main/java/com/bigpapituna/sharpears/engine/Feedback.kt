package com.bigpapituna.sharpears.engine

import kotlin.math.abs

/**
 * Mistakes during one run of the transport: each wrong key is charged to the expected note nearest in pitch
 * (the one the player was reaching for), and a group that needed more than one try counts as a retry.
 */
class RunLog {
    /** Expected note (midi) -> how many wrong keys were played instead of it. */
    val missed = mutableMapOf<Int, Int>()
    var wrongKeys = 0; private set
    var retries = 0; private set
    /** Groups finished in wait mode. */
    var groups = 0; private set
    private var wrongThisGroup = false

    fun wrong(played: Int, expected: Collection<Int>) {
        wrongKeys++
        expected.minByOrNull { abs(it - played) }?.let { missed[it] = (missed[it] ?: 0) + 1 }
        if (!wrongThisGroup) { wrongThisGroup = true; retries++ }
    }

    fun groupDone() { wrongThisGroup = false; groups++ }

    fun reset() { missed.clear(); wrongKeys = 0; retries = 0; groups = 0; wrongThisGroup = false }

    fun report(recap: Recap?) = Report(missed.entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key to it.value },
        retries, wrongKeys, recap, groups)
}

/** What a run looked like: notes missed most first (midi to count), retries, and the Perform recap if any. */
data class Report(val missed: List<Pair<Int, Int>>, val retries: Int, val wrongKeys: Int, val recap: Recap?, val groups: Int = 0) {
    val clean get() = wrongKeys == 0 && (recap == null || recap.miss == 0)
}

/** A line to show and the same line as the voice should say it. */
data class Feedback(val text: String, val spoken: String)

private val SPOKEN_PC = listOf("C", "C sharp", "D", "E flat", "E", "F", "F sharp", "G", "A flat", "A", "B flat", "B")
private val SPOKEN_FLATS = listOf("C", "D flat", "D", "E flat", "E", "F", "G flat", "G", "A flat", "A", "B flat", "B")
private val WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

/**
 * A note as words: "F sharp", "B flat"; a C carries its octave, as on the falling notes: "C four". With [flats]
 * (a flat key) every black key is a flat: "D flat", "G flat".
 */
fun spokenNote(midi: Int, flats: Boolean = false): String =
    (if (flats) SPOKEN_FLATS else SPOKEN_PC)[midi % 12] + if (midi % 12 == 0) " " + WORDS[(midi / 12 - 1).coerceIn(0, 10)] else ""

/** A pitch class as words, no octave: "E flat". */
fun spokenPitch(pc: Int): String = SPOKEN_PC[((pc % 12) + 12) % 12]

fun times(n: Int) = when (n) { 1 -> "once"; 2 -> "twice"; in 3..10 -> "${WORDS[n]} times"; else -> "$n times" }

/** Millisecond figures read out as words by the voice. */
private fun spoken(text: String) = Feedback(text, text.replace(Regex("(\\d+) ms"), "$1 milliseconds"))

/**
 * The one feedback line after a Play or Perform run. [perform]: the step was scored against [pass] (percent).
 * [next]: another step follows, so a clean run says so. Order of concerns: failing the pass mark, a note
 * missed again and again, timing, then a few one-off slips.
 */
fun feedbackLine(r: Report, perform: Boolean, pass: Int = 80, next: Boolean = true, flats: Boolean = false): Feedback {
    val onward = if (next) " On to the next step." else ""
    val (worst, count) = r.missed.firstOrNull() ?: (0 to 0)
    val missedLine = if (count >= 2) "You missed ${spokenNote(worst, flats)} ${times(count)}." else null
    val recap = r.recap
    if (perform && recap != null) {
        val bias = recap.biasMs
        val timing = when {
            recap.good + recap.ok == 0 -> null
            bias <= -40 -> "A little rushed: you were ${-bias} ms early on average."
            bias >= 40 -> "A little late: you were $bias ms behind on average."
            else -> null
        }
        if (recap.accuracy < pass) {
            val why = missedLine ?: timing ?: if (recap.good + recap.ok == 0) "No notes were heard in time." else "${recap.miss} notes came late or not at all."
            return spoken("${recap.accuracy} percent, and you need $pass. $why Play it again.")
        }
        if (missedLine != null) return spoken("$missedLine Try that bar slowly.")
        if (timing != null) return spoken(timing)
        return spoken(if (r.clean) "Clean.$onward" else "${recap.accuracy} percent. Passed.$onward")
    }
    return when {
        r.wrongKeys == 0 -> spoken("Clean.$onward")
        missedLine != null -> spoken("$missedLine Try that bar slowly.")
        r.retries == 1 && r.missed.isNotEmpty() -> spoken("Nearly clean: one slip, on ${spokenNote(worst, flats)}.$onward")
        else -> spoken("Nearly clean: ${r.retries} notes needed a second try.$onward")
    }
}
