package com.bigpapituna.sharpears.engine

/** A note the player played: key, when in song time, how long (when the key's release is known) and how hard. */
data class PlayedNote(val midi: Int, val atMs: Long, val durationMs: Long, val velocity: Int)

/** The notes of a song's last run, as played, for "Hear my run". Stored as files/runs/<songId>.json. */
data class RunRecording(val songId: String, val notes: List<PlayedNote>, /** The run's playback speed (song ms per real ms). */ val speed: Double = 1.0) {
    val isEmpty get() = notes.isEmpty()

    fun toJson(): String = MiniJson.write(mapOf(
        "songId" to songId,
        "speed" to speed,
        "notes" to notes.map { mapOf("m" to it.midi, "t" to it.atMs, "d" to it.durationMs, "v" to it.velocity) },
    ))

    /** The played notes as [Note]s for the lane (one hand colour; the lane draws them thin). */
    fun asNotes(): List<Note> = notes.map { Note(it.midi, it.atMs, it.durationMs, Hand.RIGHT, it.velocity) }.sortedBy { it.startMs }

    companion object {
        /** A strike with no release (the mic hears onsets only) sounds this long. */
        const val DEFAULT_MS = 350L

        fun fromJson(text: String): RunRecording {
            val o = MiniJson.read(text) as Map<*, *>
            val notes = (o["notes"] as List<*>).map { n ->
                n as Map<*, *>
                PlayedNote((n["m"] as Number).toInt(), (n["t"] as Number).toLong(), (n["d"] as Number).toLong(), (n["v"] as Number).toInt())
            }
            return RunRecording(o["songId"] as String, notes, (o["speed"] as? Number)?.toDouble() ?: 1.0)
        }
    }
}

/**
 * Collects a run's notes as they are played: [on] at the strike (song time), [off] at the release. Keys still
 * down when the run is taken keep [RunRecording.DEFAULT_MS].
 */
class RunRecorder {
    private val notes = mutableListOf<PlayedNote>()
    private val open = HashMap<Int, Int>()           // key -> index of its note still held

    fun clear() { notes.clear(); open.clear() }
    val size get() = notes.size

    fun on(midi: Int, atMs: Double, velocity: Int) {
        open[midi] = notes.size
        notes += PlayedNote(midi, atMs.toLong(), RunRecording.DEFAULT_MS, velocity)
    }

    fun off(midi: Int, atMs: Double) {
        val i = open.remove(midi) ?: return
        val n = notes[i]
        notes[i] = n.copy(durationMs = (atMs.toLong() - n.atMs).coerceIn(60L, 8000L))
    }

    fun take(songId: String, speed: Double = 1.0) = RunRecording(songId, notes.toList(), speed)
}
