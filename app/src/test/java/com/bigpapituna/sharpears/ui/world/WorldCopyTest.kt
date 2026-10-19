package com.bigpapituna.sharpears.ui.world

import com.bigpapituna.sharpears.engine.world.Album
import com.bigpapituna.sharpears.engine.world.Drop
import com.bigpapituna.sharpears.engine.world.DropSource
import com.bigpapituna.sharpears.engine.world.NextDrop
import com.bigpapituna.sharpears.engine.world.Outfit
import com.bigpapituna.sharpears.engine.world.Stickers
import com.bigpapituna.sharpears.engine.world.TreatJar
import com.bigpapituna.sharpears.engine.world.WearItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Everything the pup says in the world: no dashes, no nags, nothing sad, and the right line for each state. */
class WorldCopyTest {
    private fun all(): List<String> = buildList {
        for (n in listOf(0, 1, 12, 30)) add(albumLine(Album.of(Stickers.ALL.take(n).associate { it.id to 1 })))
        for (notes in listOf(0, 10, 160, TreatJar.CAPACITY)) for (kid in listOf(true, false)) for (t in listOf(0, 2)) add(jarLine(notes, kid, t))
        WearItem.entries.forEach { add(wearLine(it, true)) }
        add(wearLine(null, false))
        add(wearLine(WearItem.SUNGLASSES, on = false, stillWearing = true))
        for (u in listOf(0, 1, 2, 9, 30)) for (o in listOf(Outfit.NONE, Outfit(setOf(WearItem.BOW_TIE)))) add(wardrobeLine(o, u))
        for (u in listOf(0, 4, 5, 14, 15, 30)) add(houseLine(u))
        val s = Stickers.ALL[0]
        for (hatch in listOf(true, false)) for (rare in listOf(true, false)) for (lay in listOf(true, false)) add(oneMoreLine(NextDrop(s, rare, hatch, null, lay)))
        Stickers.ALL.forEach { st -> DropSource.entries.forEach { src -> add(dropTitle(Drop(st, src, isNew = true))); add(dropTitle(Drop(st, src, isNew = false))) } }
    }

    @Test fun noDashesNagsOrSadWords() {
        val banned = listOf("—", "–", " - ", "miss", "sad", "lost", "don't forget", "hurry", "abandon", "wrong")
        for (line in all()) for (b in banned) assertFalse("\"$line\" has \"$b\"", line.lowercase().contains(b))
    }

    @Test fun linesFitTheState() {
        assertEquals("14 of 70. 56 still hiding.", albumLine(Album.of(Stickers.ALL.take(14).associate { it.id to 1 })))
        assertEquals("37 more notes and it's full.", jarLine(163, kid = false, treats = 0))
        assertFalse("kids see no counts", jarLine(163, kid = true, treats = 0).any { it.isDigit() })
        assertTrue(jarLine(TreatJar.CAPACITY, kid = true, treats = 0).contains("Tip"))
        assertEquals("1 more sticker and the dog house grows.", houseLine(4))
        assertEquals("2 more stickers and I can wear the sunglasses.", wardrobeLine(Outfit(setOf(WearItem.BOW_TIE)), 1))
        assertEquals("It drops a pond sticker.", oneMoreLine(NextDrop(Stickers.byId("pond-2")!!, false, false, null, false)))
        assertEquals("Your mystery egg hatches when you finish it.", oneMoreLine(NextDrop(Stickers.ALL[0], true, true, Stickers.ALL[1], false)))
    }
}
