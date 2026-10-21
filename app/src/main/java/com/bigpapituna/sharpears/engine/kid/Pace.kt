package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.MiniJson

/** A lesson pinned as someone's next stone until [untilMs] (epoch ms): the parent brief's "Set as next". */
data class Pin(val lessonId: String, val untilMs: Long) {
    fun activeAt(now: Long) = now < untilMs
}

/**
 * A grown-up's say in one person's walk (the parent brief's actions, 2.4), read by [KidMap] and the app's "next lesson":
 * [pin] is a lesson that is the next stone for now (it outranks the path until it is played or its day is over), [focus] a
 * Playground page after which the pages are hidden for now (focus mode), [early] pages the path walks on past as if their
 * finale had played (the next page opened early). Each comes from an action card, behind the gate, and is reversible.
 */
data class Pace(val pin: String? = null, val focus: String? = null, val early: Set<String> = emptySet()) {
    companion object { val NONE = Pace() }
}

/** A song a grown-up put on a kid's shelf (Teacher mode), with their note (stays on the phone) and the song mastered before it. */
data class ShelfSong(val songId: String, val note: String, val atMs: Long, val after: String? = null)

/** The kid's shelf of assigned songs: newest first, one entry per song, at most [MAX]; stored as one JSON line. */
object Shelf {
    const val MAX = 3

    /** [song] on top of [shelf]: an older entry for the same song is replaced, the oldest past [MAX] drops off. */
    fun add(shelf: List<ShelfSong>, song: ShelfSong): List<ShelfSong> = (listOf(song) + shelf.filter { it.songId != song.songId }).take(MAX)

    fun encode(shelf: List<ShelfSong>): String = MiniJson.write(shelf.map { s ->
        linkedMapOf<String, Any?>("song" to s.songId, "note" to s.note, "at" to s.atMs).apply { s.after?.let { put("after", it) } }
    })

    /** Tolerant: a damaged or empty value reads as an empty shelf, an entry without a song is skipped. */
    fun decode(text: String?): List<ShelfSong> {
        if (text.isNullOrBlank()) return emptyList()
        val list = runCatching { MiniJson.read(text) as? List<*> }.getOrNull() ?: return emptyList()
        return list.mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val song = (m["song"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ShelfSong(song, m["note"] as? String ?: "", (m["at"] as? Number)?.toLong() ?: 0L, m["after"] as? String)
        }.take(MAX)
    }
}
