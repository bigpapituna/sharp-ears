package com.bigpapituna.sharpears.engine.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 2.7: an arcade round in the pup's world: counted like a song in the sparkles ledger, no drop, its sticker once. */
class ArcadeWorldTest {
    private val day = LocalDate.of(2026, 10, 22)

    @Test fun aRoundCountsLikeASongAndItsStickerComesOnce() {
        var s = WorldState(seed = 3L)
        val o = World.gameRound(s, null, day)
        assertEquals(1, o.state.ledger.songs)
        assertTrue("no sticker drop for a round", o.reward.drops.isEmpty())
        assertNull(o.reward.level)
        s = o.state
        val won = World.gameRound(s, LevelStickers.arcade("frogs"), day)
        assertEquals("arcade-frogs", won.reward.level?.lessonId)
        assertTrue("lv:arcade-frogs" in won.state.tray)
        s = won.state
        assertNull("once", World.gameRound(s, LevelStickers.arcade("frogs"), day).reward.level)
        // Five a day count toward sparkles, songs and rounds together.
        repeat(6) { s = World.gameRound(s, null, day).state }
        assertEquals(Sparkles.SONGS_A_DAY, s.ledger.songSparkles)
        assertEquals(s, WorldState.decode(s.encode(), 0L))
    }
}
