package com.bigpapituna.sharpears.ui.chords

import com.bigpapituna.sharpears.engine.chords.POP_SONGS
import com.bigpapituna.sharpears.engine.chords.PopSong

/**
 * The one place the screens ask "which songs are there?": the content's charts (engine/chords/PopSongs*.kt, 107 songs as
 * chords only; docs/CONTENT.md "Popular songs by chords" has the legal line). Ids are stable, so a saved route or a brief's
 * run id finds its song again.
 */
object PopCatalogue {
    val songs: List<PopSong> get() = POP_SONGS
}
