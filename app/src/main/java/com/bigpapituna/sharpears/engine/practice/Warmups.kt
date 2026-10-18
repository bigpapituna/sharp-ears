package com.bigpapituna.sharpears.engine.practice

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.noteName

/*
 * Warm-ups: generated exercises (five-finger patterns, Hanon-style runs, contrary motion, hand independence,
 * repeated notes, trills) in any major key, and a routine builder that strings a few together for 5, 10 or 15
 * minutes. Each is an Exercise in token notation, so the player can run it in wait mode.
 */

/** A warm-up: [level] 1 (easy) to 3, a tempo range in quarter notes per minute, and the exercise at its default tempo. */
data class Warmup(
    val id: String,
    val title: String,
    val description: String,
    val level: Int,
    val bpmMin: Int,
    val bpmMax: Int,
    val exercise: Exercise,
) {
    /** A comfortable first tempo: a third of the way up the range, rounded to 5. */
    val defaultBpm get() = ((bpmMin + (bpmMax - bpmMin) / 3) / 5) * 5

    fun at(bpm: Int): Exercise = exercise.copy(bpm = bpm.toDouble())

    /** How long one pass takes at [bpm], in seconds. */
    fun seconds(bpm: Int = defaultBpm): Double = exerciseBeats(exercise) * 60.0 / bpm
}

/** Beats in the longer hand's line of [e]. */
fun exerciseBeats(e: Exercise): Double = maxOf(lineBeats(e.right), lineBeats(e.left))

private fun lineBeats(spec: String): Double =
    spec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.sumOf { it.substringAfter(':').toDouble() }

private val MAJOR_STEPS = listOf(0, 2, 4, 5, 7, 9, 11)
private val FLAT_KEYS = setOf(5, 10, 3, 8, 1)

/** Semitones above the key's tonic of scale degree [d] (0 = tonic, 7 = the octave, -1 = the leading note below). */
private fun degree(d: Int): Int = 12 * Math.floorDiv(d, 7) + MAJOR_STEPS[Math.floorMod(d, 7)]

private fun tokens(notes: List<Int>, beats: Double, flats: Boolean, lastBeats: Double = beats) =
    notes.mapIndexed { i, m -> "${noteName(m, flats)}:${beat(if (i == notes.lastIndex) lastBeats else beats)}" }.joinToString(" ")

private fun keyName(root: Int) = scaleRootName(root, false)

// ---------------------------------------------------------------------------------------------------------
// Five-finger patterns

fun fiveFinger(root: Int, minor: Boolean = false): Warmup {
    val offsets = if (minor) listOf(0, 2, 3, 5, 7) else listOf(0, 2, 4, 5, 7)
    val shape = offsets + offsets.dropLast(1).reversed()                 // up to the fifth and back to the thumb
    val flats = if (minor) (root + 3) % 12 in FLAT_KEYS else root in FLAT_KEYS
    fun line(hand: Hand) = tokens(shape.map { handRoot(root, hand) + it }, 1.0, flats, 2.0)
    val name = scaleRootName(root, minor) + if (minor) " minor" else " major"
    return Warmup("five-finger-$root-${if (minor) "min" else "maj"}", "Five-finger pattern in $name",
        "Rest one finger on each of five neighbouring keys and walk up and back down. Keep the hand still and the fingers curved.",
        1, 50, 100,
        Exercise(right = line(Hand.RIGHT), left = line(Hand.LEFT), bpm = 70.0, flats = flats, fingers = "1 2 3 4 5 4 3 2 1",
            leftFingers = mirrored("1 2 3 4 5 4 3 2 1")))
}

/**
 * The left hand's fingers for a pattern the hands play in parallel over the same five keys: the thumb and little finger
 * trade places (the left hand's little finger sits on the bottom key). Without them the player worked the left hand's
 * fingers out alone, and repeated notes in B flat changed finger halfway through a key.
 */
internal fun mirrored(fingers: String) = fingers.split(" ").filter { it.isNotBlank() }.joinToString(" ") { "${6 - it.toInt()}" }

// ---------------------------------------------------------------------------------------------------------
// Hanon-style runs

/**
 * A Hanon-style pattern: eight scale degrees from the group's start, the next group one step higher, up the
 * key and back. Number 1 is Hanon's first exercise (C E F G A G F E, then D F G A B A G F). The others follow
 * the same plan with Hanon's emphasis on weaker fingers; they are written in this style, not copied from the book.
 */
class HanonPattern(val number: Int, val title: String, val about: String, val degrees: List<Int>, val level: Int)

val HANON_PATTERNS = listOf(
    HanonPattern(1, "Hanon style 1", "Five fingers in a rolling shape, one step higher each time. Even notes, no accents.",
        listOf(0, 2, 3, 4, 5, 4, 3, 2), 2),
    HanonPattern(2, "Hanon style 2", "A jump from the second finger to the little finger and back down. Keep the hand loose over the jump.",
        listOf(0, 2, 5, 4, 3, 4, 3, 2), 2),
    HanonPattern(3, "Hanon style 3", "Zig-zag between neighbours, which works the third and fourth fingers.",
        listOf(0, 2, 1, 3, 2, 4, 3, 2), 3),
    HanonPattern(4, "Hanon style 4", "Wider steps through the weaker fingers: thumb, third, fifth, then back through the fourth and second.",
        listOf(0, 2, 4, 3, 1, 3, 2, 1), 3),
    HanonPattern(5, "Hanon style 5", "A plain five-finger run up and down inside each group, shifted a step each time.",
        listOf(0, 1, 2, 3, 4, 3, 2, 1), 3),
)

/**
 * Hanon-style exercise [pattern] in the major key [root], both hands in parallel octaves (the left an octave
 * below the right). Sixteenth notes: [Warmup.bpmMin]..[Warmup.bpmMax] is quarter notes per minute.
 */
fun hanon(pattern: HanonPattern, root: Int = 0): Warmup {
    val p = pattern.degrees
    val ranks = p.distinct().sorted()
    val up = (0..6).map { s -> p.map { s + it } }                         // groups going up, one step each
    val down = (9 downTo 5).map { s -> p.map { s - it } }                 // the mirrored shape coming down
    val finalDegree = 0
    fun semis(hand: Hand, d: Int) = handRoot(root, hand) + degree(d)
    val flats = root in FLAT_KEYS
    fun line(hand: Hand): String {
        val notes = (up + down).flatten().map { semis(hand, it) } + semis(hand, finalDegree)
        return notes.mapIndexed { i, m -> "${noteName(m, flats)}:${if (i == notes.lastIndex) "4" else "0.25"}" }.joinToString(" ")
    }
    val fingers = (p.map { ranks.indexOf(it) + 1 }.repeatTimes(up.size) +
        p.map { ranks.size - ranks.indexOf(it) }.repeatTimes(down.size) + 1).joinToString(" ")
    return Warmup("hanon-${pattern.number}-$root", "${pattern.title} in ${keyName(root)}", pattern.about, pattern.level,
        40, if (pattern.level == 2) 80 else 92,
        Exercise(right = line(Hand.RIGHT), left = line(Hand.LEFT), bpm = 60.0, flats = flats, fingers = fingers, leftFingers = mirrored(fingers)))
}

private fun List<Int>.repeatTimes(n: Int): List<Int> = (0 until n).flatMap { this }

// ---------------------------------------------------------------------------------------------------------
// Contrary motion

/**
 * Keys where both hands can start on the same note and share the textbook fingering. Not F: its right hand crosses after
 * B flat (1 2 3 4), its left hand coming down after D (1 2 3), so "the same finger plays in both hands" was not true.
 */
val CONTRARY_ROOTS = listOf(0, 7, 2, 9, 4)

/** Both hands leave the tonic together, the right going up and the left down an octave, then come back. */
fun contraryScale(root: Int): Warmup {
    require(root in CONTRARY_ROOTS) { "contrary motion with the same fingering: C, G, D, A, E" }
    val start = handRoot(root, Hand.RIGHT)
    val upSteps = MAJOR_STEPS.map { start + it } + (start + 12)
    val downSteps = listOf(start) + (6 downTo 0).map { start - 12 + MAJOR_STEPS[it] }
    val flats = root in FLAT_KEYS
    fun line(out: List<Int>) = tokens(out + out.reversed().drop(1), 1.0, flats, 2.0)
    val table = scaleTable(root, false, Hand.RIGHT)
    val fingers = (table + table.reversed().drop(1)).joinToString(" ")
    return Warmup("contrary-$root", "Contrary motion scale in ${keyName(root)}",
        "Both hands start together on the tonic and move apart, the right up and the left down, then meet again. The same finger plays in both hands.",
        2, 50, 100,
        Exercise(right = line(upSteps), left = line(downSteps), bpm = 70.0, flats = flats, fingers = fingers, leftFingers = fingers))
}

// ---------------------------------------------------------------------------------------------------------
// Hand independence

/**
 * One hand holds a triad for a bar (I, IV, V, I in the key) while the other walks four notes up and back.
 * [leftHolds]: the left hand holds and the right walks; otherwise the right holds and the left walks.
 */
fun handIndependence(root: Int, leftHolds: Boolean): Warmup {
    // I, IV and V as semitones above the tonic, in the inversions that stay close
    val chords = listOf(listOf(0, 4, 7), listOf(0, 5, 9), listOf(-1, 2, 7), listOf(0, 4, 7))
    val walks = listOf(listOf(0, 1, 2, 1), listOf(3, 4, 5, 4), listOf(4, 5, 6, 5), listOf(0, 1, 2, 0))
    val flats = root in FLAT_KEYS
    val hold = if (leftHolds) Hand.LEFT else Hand.RIGHT
    val walk = if (leftHolds) Hand.RIGHT else Hand.LEFT
    val held = chords.joinToString(" ") { c -> c.joinToString("+") { noteName(handRoot(root, hold) + it, flats) } + ":4" }
    val walking = walks.joinToString(" ") { w -> tokens(w.map { handRoot(root, walk) + degree(it) }, 1.0, flats) }
    return Warmup("independence-${if (leftHolds) "lh" else "rh"}-$root",
        "${if (leftHolds) "Left hand holds" else "Right hand holds"} a chord in ${keyName(root)}",
        "One hand holds the chord for the whole bar while the other walks four notes. Keep the held hand relaxed and quiet.",
        if (leftHolds) 3 else 2, 50, 90,
        Exercise(right = if (leftHolds) walking else held, left = if (leftHolds) held else walking, bpm = 60.0, flats = flats))
}

// ---------------------------------------------------------------------------------------------------------
// Repeated notes and trills

/** Each finger plays one key four times in a row, up the five-finger position and back. */
fun repeatedNotes(root: Int): Warmup {
    val offsets = listOf(0, 2, 4, 5, 7)
    val order = offsets + offsets.dropLast(1).reversed()
    val flats = root in FLAT_KEYS
    fun line(hand: Hand) = order.flatMap { o -> List(4) { handRoot(root, hand) + o } }.let { notes ->
        notes.mapIndexed { i, m -> "${noteName(m, flats)}:${if (i == notes.lastIndex) "2" else "0.5"}" }.joinToString(" ")
    }
    val fingers = (listOf(1, 2, 3, 4, 5, 4, 3, 2, 1)).flatMap { f -> List(4) { f } }.joinToString(" ")
    return Warmup("repeated-$root", "Repeated notes in ${keyName(root)}",
        "Play each key four times with the same finger before moving to the next. Let the finger bounce back from the key instead of pressing down.",
        1, 50, 90, Exercise(right = line(Hand.RIGHT), left = line(Hand.LEFT), bpm = 60.0, flats = flats, fingers = fingers, leftFingers = mirrored(fingers)))
}

/**
 * A trill between finger [a] and finger [b] (1..5, neighbours) of the five-finger position, in both hands: the left
 * hand's little finger sits on the bottom key, so its fingers [a] and [b] are the keys the right hand's [6 - a] and
 * [6 - b] play (before, the left hand trilled the right hand's keys, which are other fingers in the left hand).
 */
fun trill(root: Int, a: Int, b: Int): Warmup {
    require(b == a + 1 && a in 1..4) { "trills between neighbouring fingers" }
    val offsets = listOf(0, 2, 4, 5, 7)
    val flats = root in FLAT_KEYS
    fun line(hand: Hand): String {
        fun key(finger: Int) = handRoot(root, hand) + offsets[(if (hand == Hand.RIGHT) finger else 6 - finger) - 1]
        val notes = (0 until 8).flatMap { listOf(key(a), key(b)) } + key(a)
        return notes.mapIndexed { i, m -> "${noteName(m, flats)}:${if (i == notes.lastIndex) "2" else "0.25"}" }.joinToString(" ")
    }
    val fingers = ((0 until 8).flatMap { listOf(a, b) } + a).joinToString(" ")
    return Warmup("trill-$a$b-$root", "Trill with fingers $a and $b in ${keyName(root)}",
        "Rock between two neighbouring fingers as evenly as you can. Start slowly and keep the wrist loose.",
        if (a <= 2) 2 else 3, 50, 100, Exercise(right = line(Hand.RIGHT), left = line(Hand.LEFT), bpm = 60.0, flats = flats, fingers = fingers, leftFingers = fingers))
}

/** The major scale, one octave up and down, hands together an octave apart: a gentle way to finish the finger work. */
fun scaleWarmup(root: Int): Warmup {
    val s = Scale(root, ScaleType.MAJOR)
    return Warmup("scale-$root", "${keyName(root)} major scale, hands together",
        "One octave up and back with both hands, an octave apart. Turn the thumb under smoothly and keep the notes even.",
        1, 50, 100, s.toExercise(Practice.BOTH, 70.0))
}

// ---------------------------------------------------------------------------------------------------------
// Catalog and routines

/** Every warm-up in the key [root], easiest first. Contrary motion only exists in [CONTRARY_ROOTS]. */
fun warmupCatalog(root: Int = 0): List<Warmup> = buildList {
    add(fiveFinger(root))
    add(fiveFinger(root, minor = true))
    add(repeatedNotes(root))
    add(scaleWarmup(root))
    add(trill(root, 1, 2))
    add(trill(root, 2, 3))
    add(handIndependence(root, leftHolds = false))
    if (root in CONTRARY_ROOTS) add(contraryScale(root))
    HANON_PATTERNS.forEach { add(hanon(it, root)) }
    add(trill(root, 3, 4))
    add(trill(root, 4, 5))
    add(handIndependence(root, leftHolds = true))
}.sortedBy { it.level }

/** One step of a routine: play [warmup] [reps] times at [bpm]. */
data class RoutineItem(val warmup: Warmup, val reps: Int, val bpm: Int) {
    val seconds get() = warmup.seconds(bpm) * reps
}

/** Keys the routine rotates through, easiest first. */
val ROUTINE_KEYS = listOf(0, 7, 5, 2, 9, 4, 10, 3)

/** The key a routine built with [seed] is in. */
fun routineRoot(seed: Int): Int = ROUTINE_KEYS[Math.floorMod(seed, ROUTINE_KEYS.size)]

private val ROUTINE_ORDER = listOf("five-finger", "repeated", "scale", "trill-12", "contrary", "independence-rh", "hanon-1", "trill-23", "hanon-2",
    "independence-lh", "hanon-3", "trill-34", "hanon-4", "trill-45", "hanon-5")

/**
 * A warm-up routine of about [minutes] (5, 10 or 15) for [level] 1..3 in the key [root] (default: by [seed]):
 * easy finger work first, harder patterns later, and each repeated to fill its share of the time. Longer
 * routines also take in more of the catalog.
 */
fun buildRoutine(minutes: Int, level: Int, seed: Int = 0, root: Int = routineRoot(seed)): List<RoutineItem> {
    val lvl = level.coerceIn(1, 3)
    val catalog = warmupCatalog(root).filter { it.level <= lvl }
    fun rank(w: Warmup): Int {
        val i = ROUTINE_ORDER.indexOfFirst { w.id.startsWith(it) }
        return if (i < 0) ROUTINE_ORDER.size else i
    }
    val ordered = catalog.sortedBy(::rank)
    val count = minOf(ordered.size, when { minutes <= 5 -> 3; minutes <= 10 -> 5; else -> 7 })
    // Spread the picks over the ordered list so a short routine still reaches the harder items.
    val picks = if (count >= ordered.size) ordered else List(count) { i -> ordered[i * ordered.size / count] }
    val share = minutes * 60.0 / picks.size
    return picks.map { w ->
        val bpm = (w.bpmMin + (w.bpmMax - w.bpmMin) * (lvl - 1) / 3) / 5 * 5
        val one = w.seconds(bpm) + 4.0                                     // a few seconds to reset between passes
        RoutineItem(w, (share / one).toInt().coerceIn(1, 8), bpm)
    }
}

/** Seconds a routine takes, including the gaps. */
fun routineSeconds(items: List<RoutineItem>): Double = items.sumOf { it.seconds + 4.0 * it.reps }
