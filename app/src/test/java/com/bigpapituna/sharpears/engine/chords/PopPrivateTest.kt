package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.BuildConfig
import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** privateOnly songs (engine/chords/PopSongsTemp.kt, docs/PLAN-2.8.md section 4): kept out of every public list and build. */
class PopPrivateTest {
    private val priv = ALL_POP_SONGS.filter { it.privateOnly }

    /**
     * Fails in any build without PRIVATE_BUILD=true while a privateOnly song exists, on purpose: the public build must not
     * ship until PopSongsTemp.kt (and TEMP_SONGS in PopSongs.kt) is deleted. Uses the real BuildConfig, so it fails on a
     * keyless checkout and passes on Roy's PC (scripts/set-private-build.ps1).
     */
    @Test fun privateSongsMustNotShipPublic() {
        assertTrue(
            "${priv.size} privateOnly songs (${priv.joinToString { it.id }}) exist but BuildConfig.PRIVATE_BUILD is false: " +
                "delete engine/chords/PopSongsTemp.kt before a public build",
            priv.isEmpty() || BuildConfig.PRIVATE_BUILD,
        )
    }

    @Test fun theSixAreRegisteredPrivateSketches() {
        val ids = listOf("billie-jean", "beat-it", "man-in-the-mirror", "human-nature", "rock-with-you", "the-way-you-make-me-feel")
        assertEquals(ids.toSet(), priv.map { it.id }.toSet())
        for (s in priv) assertEquals(s.id, Confidence.SKETCH, s.confidence)
    }

    @Test fun publicListsNeverHoldAPrivateSong() {
        assertTrue(priv.isNotEmpty() || ALL_POP_SONGS.none { it.privateOnly })
        assertTrue(POP_SONGS.none { it.privateOnly })
        assertTrue(PopFind.visible(ALL_POP_SONGS, privateBuild = false).none { it.privateOnly })
        assertEquals(ALL_POP_SONGS.size - priv.size, POP_SONGS.size)
    }

    @Test fun aPrivateBuildKeepsThem() {
        assertEquals(ALL_POP_SONGS, PopFind.visible(ALL_POP_SONGS, privateBuild = true))
        assertEquals(priv, PopFind.visible(ALL_POP_SONGS, privateBuild = true).filter { it.privateOnly })
    }

    @Test fun everyPublicListingTakesTheFilteredList() {
        val shown = PopFind.visible(ALL_POP_SONGS, privateBuild = false)
        for (chip in PopFind.chipsFor(shown)) assertTrue(chip.name, PopFind.under(shown, chip).none { it.privateOnly })
        val every = PopFind.Chip.entries.flatMap { PopFind.under(shown, it) }
        assertTrue(every.none { it.privateOnly })
    }
}
