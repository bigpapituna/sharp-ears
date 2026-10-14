package com.bigpapituna.sharpears.engine.practice

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.parseLine

/*
 * Data for the Warm-ups screen: a short loop of each warm-up's pattern for the mini keyboard preview, and the
 * pup's line introducing a routine.
 */

/** One onset of a preview: the keys struck together, per hand, and how many beats until the next one. */
data class PreviewStep(val right: List<Int>, val left: List<Int>, val beats: Double)

/**
 * A warm-up's pattern for a mini keyboard: the first [PreviewStep]s (one shape of the exercise, at most
 * [maxSteps]) and the white-key range [low]..[high] that holds them. The left hand is only included when it
 * does something different from the right (contrary motion, one hand holding a chord); a parallel left hand
 * would just repeat the picture an octave down.
 */
data class WarmupPreview(val low: Int, val high: Int, val steps: List<PreviewStep>) {
    val totalBeats get() = steps.sumOf { it.beats }
}

fun warmupPreview(w: Warmup, maxSteps: Int = 12): WarmupPreview {
    val right = parseLine(w.exercise.right, Hand.RIGHT, 1000.0)
    val left = parseLine(w.exercise.left, Hand.LEFT, 1000.0)
    val parallel = right.size == left.size && right.zip(left).all { (r, l) -> r.midi - l.midi == 12 && r.startMs == l.startMs }
    val notes = if (parallel || left.isEmpty()) right else right + left
    val onsets = notes.groupBy { it.startMs }.toSortedMap().entries.take(maxSteps + 1).toList()
    val steps = onsets.take(maxSteps).mapIndexed { i, (start, group) ->
        val next = onsets.getOrNull(i + 1)?.key ?: (start + group.maxOf { it.durationMs / 0.92 }.toLong())
        PreviewStep(group.filter { it.hand == Hand.RIGHT }.map { it.midi }.sorted(),
            group.filter { it.hand == Hand.LEFT }.map { it.midi }.sorted(), (next - start) / 1000.0)
    }
    val all = steps.flatMap { it.right + it.left }
    var low = all.min().let { if (isBlack(it)) it - 1 else it }
    var high = all.max().let { if (isBlack(it)) it + 1 else it }
    // At least six white keys, so a trill still sits on a keyboard and not on two lonely keys.
    while ((low..high).count { !isBlack(it) } < 6) {
        high += if (isBlack(high + 1)) 2 else 1
        if ((low..high).count { !isBlack(it) } < 6) low -= if (isBlack(low - 1)) 2 else 1
    }
    return WarmupPreview(low, high, steps)
}

/** How the pup names each kind of warm-up in a routine (singular, plural). */
private val KIND_WORDS = listOf(
    "five-finger" to ("a five-finger pattern" to "five-finger patterns"),
    "repeated" to ("repeated notes" to "repeated notes"),
    "scale" to ("the scale hands together" to "the scale hands together"),
    "trill" to ("a trill" to "trills"),
    "contrary" to ("contrary motion" to "contrary motion"),
    "independence" to ("a held chord" to "held chords"),
    "hanon" to ("a Hanon style run" to "Hanon style runs"),
)

private val MINUTE_WORDS = mapOf(5 to "Five", 10 to "Ten", 15 to "Fifteen")

/**
 * The pup's introduction to a routine of [minutes]: "Ten minutes: five-finger patterns, then a Hanon style
 * run, then contrary motion." Neighbouring warm-ups of one kind are named once (in the plural).
 */
fun routineIntro(minutes: Int, items: List<RoutineItem>): String {
    val kinds = mutableListOf<Pair<String, Int>>()
    for (item in items) {
        val kind = KIND_WORDS.firstOrNull { item.warmup.id.startsWith(it.first) }?.first ?: continue
        if (kinds.lastOrNull()?.first == kind) kinds[kinds.lastIndex] = kind to kinds.last().second + 1 else kinds += kind to 1
    }
    val words = kinds.map { (k, n) -> KIND_WORDS.first { it.first == k }.second.let { if (n > 1) it.second else it.first } }
    val head = (MINUTE_WORDS[minutes] ?: "$minutes") + " minutes"
    if (words.isEmpty()) return "$head of warm-ups."
    val body = when (words.size) {
        1 -> words[0]
        2, 3 -> words.joinToString(", then ")
        else -> words[0] + ", then " + listWords(words.drop(1))
    }
    return "$head: $body."
}
