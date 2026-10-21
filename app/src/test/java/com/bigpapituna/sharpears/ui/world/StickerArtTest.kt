package com.bigpapituna.sharpears.ui.world

import com.bigpapituna.sharpears.engine.world.LevelStickers
import com.bigpapituna.sharpears.engine.world.WearItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 2.5: every stone's sticker has its picture, and every wardrobe item a drawing box (headroom for hats). */
class StickerArtTest {
    @Test fun everyLevelStickerHasItsPicture() {
        assertEquals(LevelStickers.ALL.map { it.lessonId }.toSet(), LEVEL_STICKER_RES.keys)
        val dir = File("src/main/res/drawable-nodpi")
        LevelStickers.ALL.forEach { assertTrue(it.resName, File(dir, it.resName + ".webp").exists()) }
        // No picture is left from a sticker that is gone.
        val files = dir.listFiles { f -> f.name.startsWith("sticker_lv_") }!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(LevelStickers.ALL.map { it.resName }.toSet(), files)
    }

    @Test fun everyItemSitsInABoxAndOnlyHatsReachAboveHim() {
        for (i in WearItem.entries) {
            val b = Wear.box(i)
            assertTrue(i.id, b.width > 0 && b.height > 0)
            if (b.top < 0f) assertTrue("${i.id} reaches over his head", i.slot == com.bigpapituna.sharpears.engine.world.WearSlot.HEAD || i == WearItem.LION)
        }
        // Things under him are drawn behind his image; everything else in front.
        assertTrue(WearItem.WINGS in Wear.BACK_LAYER && WearItem.CAPE_RED in Wear.BACK_LAYER && WearItem.TOP_HAT !in Wear.BACK_LAYER)
        val o = com.bigpapituna.sharpears.engine.world.Outfit(setOf(WearItem.WINGS, WearItem.CROWN, WearItem.KNIGHT))
        assertEquals(listOf(WearItem.KNIGHT, WearItem.WINGS, WearItem.CROWN), Wear.front(o))
        assertEquals(listOf(WearItem.WINGS), Wear.behind(o))
        assertEquals(90f, Wear.above(o))
    }
}
