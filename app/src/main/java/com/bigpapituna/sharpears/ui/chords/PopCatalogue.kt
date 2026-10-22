package com.bigpapituna.sharpears.ui.chords

import com.bigpapituna.sharpears.BuildConfig
import com.bigpapituna.sharpears.engine.chords.ALL_POP_SONGS
import com.bigpapituna.sharpears.engine.chords.PopFind
import com.bigpapituna.sharpears.engine.chords.PopSong

/**
 * The one place the screens ask "which songs are there?": the content's charts (engine/chords/PopSongs*.kt, 107 songs as
 * chords only; docs/CONTENT.md "Popular songs by chords" has the legal line). Ids are stable, so a saved route or a brief's
 * run id finds its song again. The privateOnly charts (PopSongsTemp.kt) are here only when BuildConfig.PRIVATE_BUILD is
 * true; the engine stays Android-free, so the flag is read here and passed in.
 */
object PopCatalogue {
    val songs: List<PopSong> by lazy { PopFind.visible(ALL_POP_SONGS, BuildConfig.PRIVATE_BUILD) }
}
