package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.skin.NoteColours

/** What the kid's song list needs to know about a library song: its id, title, difficulty, where it came from and its keys. */
data class SongFacts(val id: String, val title: String, val difficulty: String, val imported: Boolean, val available: Boolean, val keys: List<Int>)

/**
 * The kid's songs (2.2): only the songs a kid can play by colour, every note one colour and one animal. That is an easy
 * song from the app's own library, played on white keys only (a black key is two colours striped, which a colour
 * reader cannot follow), and on the phone. The library's own order is kept (the nursery tunes come first there).
 */
object KidSongs {
    fun isKidSong(s: SongFacts): Boolean =
        s.available && !s.imported && s.difficulty == "easy" && s.keys.isNotEmpty() && s.keys.all { NoteColours.white(it) != null }

    fun pick(songs: List<SongFacts>): List<SongFacts> = songs.filter(::isKidSong)

    /**
     * The tune's opening for its tile, as keys to colour: the first [n] onsets of the right hand ([melody] as start ms
     * and key, any order), the highest key of each.
     */
    fun opening(melody: List<Pair<Long, Int>>, n: Int = 6): List<Int> =
        melody.groupBy({ it.first }, { it.second }).toSortedMap().values.map { it.max() }.take(n)
}
