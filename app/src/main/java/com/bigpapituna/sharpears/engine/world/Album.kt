package com.bigpapituna.sharpears.engine.world

/** One slot on an album page: the sticker and how many of it are owned (0 is a gap). */
data class AlbumSlot(val sticker: Sticker, val count: Int) {
    val owned: Boolean get() = count > 0
    /** Copies beyond the first. */
    val doubles: Int get() = (count - 1).coerceAtLeast(0)
}

/** A place's page: its ten slots in order, with the gaps showing. */
data class AlbumPage(val place: Place, val slots: List<AlbumSlot>) {
    val collected: Int get() = slots.count { it.owned }
    val size: Int get() = slots.size
    val complete: Boolean get() = collected == size
    val gaps: List<Sticker> get() = slots.filter { !it.owned }.map { it.sticker }
    /** "7 of 10 in the pond". */
    val line: String get() = "$collected of $size ${place.inThe}"
}

/** The album: one page per place, built from what is owned. */
data class Album(val pages: List<AlbumPage>) {
    val collected: Int get() = pages.sumOf { it.collected }
    val size: Int get() = pages.sumOf { it.size }
    val complete: Boolean get() = pages.all { it.complete }
    fun page(place: Place): AlbumPage = pages.first { it.place == place }
    /** "12 of 30 stickers". */
    val line: String get() = "$collected of $size stickers"

    companion object {
        fun of(owned: Map<String, Int>): Album = Album(Place.entries.map { p ->
            AlbumPage(p, p.stickers.map { AlbumSlot(it, owned[it.id] ?: 0) })
        })
    }
}

/**
 * The dog house grows with the album: stage 1 a plain wooden house, 2 (from [STAGE_2] different stickers) a red
 * roof and a flower box, 3 (from [STAGE_3]) a chimney, a flag and a porch light.
 */
object House {
    const val STAGE_2 = 5
    const val STAGE_3 = 15

    fun stage(unique: Int): Int = when { unique >= STAGE_3 -> 3; unique >= STAGE_2 -> 2; else -> 1 }

    /** Different stickers still needed for the next stage, or null at the top. */
    fun toNext(unique: Int): Int? = when { unique < STAGE_2 -> STAGE_2 - unique; unique < STAGE_3 -> STAGE_3 - unique; else -> null }
}

/**
 * What the world scenes show, from what was collected: each place shows its own collected stickers where they belong
 * (the tulip in the flower bed, the frog on its lily pad, the piano on the stage), and the concert hall seats every
 * animal collected anywhere in the audience, in catalogue order.
 */
object Scenery {
    /** The stickers of [place] that appear in its scene. */
    fun items(place: Place, owned: Map<String, Int>): List<Sticker> = place.stickers.filter { (owned[it.id] ?: 0) > 0 }

    /** The concert audience: every animal sticker owned, garden first, then the pond. */
    fun audience(owned: Map<String, Int>): List<Sticker> = Stickers.ALL.filter { it.animal && (owned[it.id] ?: 0) > 0 }

    /** Seats in the concert hall (one per animal sticker). */
    val SEATS: Int = Stickers.ALL.count { it.animal }
}
