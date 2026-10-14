package com.bigpapituna.sharpears.engine

/**
 * A song's opening, drawn small on its library tile as falling notes (the player's stage in miniature). Each bar
 * is placed in 0..1 of the tile: [x] and [w] across (by pitch), [y] and [h] up from the hit line (by time), so the
 * first note sits lowest, the way it falls into the keys. Deterministic: the same file always draws the same tile.
 */
data class ArtNote(val x: Float, val w: Float, val y: Float, val h: Float, val right: Boolean)

/** What a library row shows about a song once its file is read: the opening as [notes] and the length. */
data class SongArt(val notes: List<ArtNote>, val durationMs: Long) {
    companion object {
        /** At most this many notes on a tile: enough to read as a phrase, few enough to stay a picture. */
        const val MAX_NOTES = 18

        /**
         * The first [bars] bars of [song] (at most [MAX_NOTES] notes). Pitch runs across the tile with a column of
         * margin each side; time runs bottom to top over the window.
         */
        fun of(song: Song, bars: Int = 3): SongArt {
            if (song.notes.isEmpty()) return SongArt(emptyList(), song.durationMs)
            val first = song.notes.minOf { it.startMs }
            val barMs = 60_000.0 / song.bpm.coerceAtLeast(1.0) * song.timeSignature.first.coerceAtLeast(1) * 4.0 / song.timeSignature.second.coerceAtLeast(1)
            val bound = (barMs * bars).coerceIn(1500.0, 9000.0)
            val shown = song.notes.filter { it.startMs - first < bound }.sortedWith(compareBy({ it.startMs }, { it.midi })).take(MAX_NOTES)
            // When the note cap cuts the bars short, the window shrinks to the notes kept, so they fill the tile.
            val window = ((shown.maxOf { it.startMs + it.durationMs / 2 } - first) * 1.1).coerceIn(1200.0, bound)
            // Columns by pitch, with wide leaps (the bass far under the tune) closed up to two steps, so the
            // contour shows and the tile has no empty middle.
            val column = HashMap<Int, Int>()
            var prev = -1
            for (p in shown.map { it.midi }.distinct().sorted()) {
                column[p] = if (prev < 0) 0 else column.getValue(prev) + minOf(p - prev, 2)
                prev = p
            }
            val used = column.values.max() + 1
            val span = (used + 2).coerceAtLeast(7)                         // a narrow tune is not stretched into fat bars
            val pad = (span - used) / 2f
            val w = (1f / span).coerceIn(0.07f, 0.16f)
            val notes = shown.map { n ->
                val y = ((n.startMs - first) / window).toFloat()
                val h = minOf((n.durationMs / window).toFloat().coerceAtLeast(0.035f), 1f - y) * 0.86f
                ArtNote(x = ((column.getValue(n.midi) + pad) / span).coerceIn(0f, 1f - w), w = w, y = y, h = h, right = n.hand == Hand.RIGHT)
            }
            return SongArt(notes, song.durationMs)
        }

        /** "easy" 1, "medium" 2, "hard" 3; imported songs carry no rating (0). */
        fun stars(difficulty: String) = when (difficulty) { "easy" -> 1; "medium" -> 2; "hard" -> 3; else -> 0 }

        /** A song's length as "m:ss", rounded to the nearest second. */
        fun length(ms: Long): String { val s = ((ms + 500) / 1000).coerceAtLeast(0); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }
    }
}
