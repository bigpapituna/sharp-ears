package com.bigpapituna.sharpears.ui.chords

import com.bigpapituna.sharpears.engine.chords.PopSong

/**
 * The one place the screens ask "which songs are there?". It is the sample list while engine/chords/PopSongs.kt
 * (`POP_SONGS`, popc) is not on this branch; the integrator makes it `POP_SONGS` and deletes PopSamples.kt
 * (see INTEGRATION.md). Nothing else in ui/chords names the samples.
 */
object PopCatalogue {
    val songs: List<PopSong> get() = POP_SAMPLES
}
