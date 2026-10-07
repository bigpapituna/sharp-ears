package com.bigpapituna.sharpears.engine

enum class Hand { LEFT, RIGHT }

data class Note(val midi: Int, val startMs: Long, val durationMs: Long, val hand: Hand, val velocity: Int)

/** Notes struck together (a chord or a two-hand strike). Wait mode advances one group at a time. */
data class OnsetGroup(val startMs: Long, val notes: List<Note>)

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
)

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
