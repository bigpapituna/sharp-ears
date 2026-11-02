package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.voice.AssetIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetIndexTest {
    // QA 2026-11-02 B-02: the voice listing came back short (596 of 3,975) and those lines went to the phone's voice.
    @Test fun aNameTheListingMissedIsFoundOnDiskAndAMissingOneStaysMissing() {
        val onDisk = (0 until 3975).map { "k$it.ogg" }.toSet()
        var opens = 0
        val rescued = mutableListOf<String>()
        val index = AssetIndex({ onDisk.take(596) }, { opens++; it in onDisk }, { rescued += it })
        assertTrue("listed", "k5.ogg" in index)
        assertEquals(0, opens)
        assertTrue("left out of the short listing, but on disk", "k3000.ogg" in index)
        assertTrue("asked once, then kept", "k3000.ogg" in index)
        assertFalse("a line nobody recorded", "made-at-run-time.ogg" in index)
        assertFalse("made-at-run-time.ogg" in index)
        assertEquals(2, opens)
        assertEquals(listOf("k3000.ogg"), rescued)
    }

    @Test fun aListingThatFailsStillFindsEveryFile() {
        val index = AssetIndex({ error("asset manager busy") }, { it == "a.ogg" })
        assertTrue("a.ogg" in index)
        assertFalse("b.ogg" in index)
        assertEquals(0, index.prime())
    }
}
