package com.bigpapituna.sharpears.engine

enum class Hand { LEFT, RIGHT }

data class Note(val midi: Int, val startMs: Long, val durationMs: Long, val hand: Hand, val velocity: Int)

/**
 * Notes struck together (a chord or a two-hand strike). Wait mode advances one group at a time.
 * [anyOf] (2.1, the Playground's range exercises): the notes are alternatives, not a chord. Any one of them completes
 * the group ("any key above middle C", "either key of the two-black group"); the first note is the one the app plays
 * when it demonstrates. Plain groups (every library song and lesson) keep needing all their notes.
 */
data class OnsetGroup(val startMs: Long, val notes: List<Note>, val anyOf: Boolean = false)

data class Section(val name: String, val startMs: Long, val endMs: Long)

data class Song(
    val id: String,
    val title: String,
    val bpm: Double,
    val timeSignature: Pair<Int, Int>,
    val notes: List<Note>,
    val groups: List<OnsetGroup>,
    val sections: List<Section>,
    val durationMs: Long,
    /** Name black keys as flats (B flat, not A sharp): from a flat key signature, or set by a lesson. */
    val preferFlats: Boolean = false,
    /** Fixed right-hand fingers, one per right-hand note in time order (a lesson's scale); null: worked out. */
    val fingering: List<Int>? = null,
    /** Fixed left-hand fingers, one per left-hand note in time order (a scale's book fingering, "little finger on low C"); null: worked out. */
    val leftFingering: List<Int>? = null,
    /** Black keys a scale spells against [preferFlats], by pitch class: D harmonic minor's raised seventh is "C#", not "Db". */
    val spellings: Map<Int, String> = emptyMap(),
)

/**
 * The same song [semitones] higher (or lower): every note moves, so what is shown, played, expected and labelled
 * all follow. Notes are kept on the piano (21..108). Flat spelling follows the new key's black keys.
 */
fun Song.transposed(semitones: Int): Song {
    if (semitones == 0) return this
    fun move(n: Note) = n.copy(midi = (n.midi + semitones).coerceIn(21, 108))
    val moved = notes.map(::move)
    // Any-of groups are not chords, so they keep their own grouping (regrouping would make every alternative required).
    val groups = if (groups.none { it.anyOf }) groupOnsets(moved) else groups.map { g -> g.copy(notes = g.notes.map(::move)) }
    return copy(notes = moved, groups = groups, preferFlats = flatKey(moved))
}

/**
 * Whether the notes read better with flats: the major key whose scale holds the most of them (its relative minor
 * shares it) is F, B flat, E flat, A flat or D flat.
 */
fun flatKey(notes: List<Note>): Boolean {
    if (notes.isEmpty()) return false
    val count = IntArray(12).also { c -> notes.forEach { c[it.midi % 12]++ } }
    val major = listOf(0, 2, 4, 5, 7, 9, 11)
    val tonic = (0..11).maxBy { t -> major.sumOf { count[(t + it) % 12] } * 12 - t }   // ties go to the lower tonic
    return tonic in setOf(5, 10, 3, 8, 1)
}

/** How to decide which hand plays each note. */
sealed interface HandRule {
    data class Tracks(val right: Int, val left: Int) : HandRule
    data object MiddleCSplit : HandRule
    /** First note-bearing track is the right hand, second the left; one track falls back to the middle-C split. */
    data object Auto : HandRule
}

const val GROUP_WINDOW_MS = 30L
const val MIDDLE_C = 60

fun groupOnsets(notes: List<Note>): List<OnsetGroup> {
    val groups = mutableListOf<OnsetGroup>()
    var current = mutableListOf<Note>()
    for (n in notes.sortedWith(compareBy({ it.startMs }, { it.midi }))) {
        if (current.isNotEmpty() && n.startMs - current.first().startMs > GROUP_WINDOW_MS) {
            groups += OnsetGroup(current.first().startMs, current)
            current = mutableListOf()
        }
        current += n
    }
    if (current.isNotEmpty()) groups += OnsetGroup(current.first().startMs, current)
    return groups
}
