package com.bigpapituna.sharpears.engine.arcade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 2.14, the kid arcade (Mila, Theo): one "More games soon!" tile, what each star means, and a reader's first Balloon pop at level 1. */
class ArcadeFinishHubTest {
    private fun src(p: String) = File("src/main/java/com/bigpapituna/sharpears/$p").readText()

    @Test fun everyStarSaysWhatItNeedsInAFewKidWords() {
        for (g in ArcadeGame.entries) {
            val words = (1..3).map { ArcadeLines.starMeaning(g, it) }
            assertEquals(g.id, 3, words.toSet().size)
            for (w in words) assertTrue("${g.id}: $w", w.first().isUpperCase() && w.endsWith("!") && w.none { it.isDigit() } && w.length <= 20)
        }
        assertEquals(listOf("You started!", "Halfway up!", "The clouds!"), (1..3).map { ArcadeLines.starMeaning(ArcadeGame.TOWER, it) })
        assertEquals(listOf("You played!", "Most of the song!", "The whole song!"), (1..3).map { ArcadeLines.starMeaning(ArcadeGame.FIREWORKS, it) })
        assertEquals(listOf("You played!", "Most of them!", "Nearly all!"), (1..3).map { ArcadeLines.starMeaning(ArcadeGame.BALLOONS, it) })
        // Shown, never said: not in the voice's catalog; but the copy test reads them.
        assertTrue(ArcadeLines.all().containsAll((1..3).map { ArcadeLines.starMeaning(ArcadeGame.BALLOONS, it) } + ArcadeLines.MORE_SOON))
        assertFalse(ArcadeLines.spoken().contains(ArcadeLines.MORE_SOON) || ArcadeLines.spoken().contains("Most of them!"))
        assertTrue(ArcadeLines.MORE_SOON.endsWith("!") && ArcadeLines.MORE_SOON_LABEL.endsWith("."))
        // The finish draws a caption under each star, from the table.
        assertTrue("ArcadeLines.starMeaning(game, i + 1)" in src("ui/arcade/ArcadeFinish.kt"))
    }

    @Test fun lockedGamesCollapseIntoOneTile() {
        val hub = src("ui/arcade/ArcadeHub.kt")
        assertTrue("items(ArcadeGame.entries.take(open)" in hub)
        assertEquals("one tile for all the locked ones", 1, Regex("""item\(key = "more-soon"\)""").findAll(hub).count())
        assertTrue("if (open < ArcadeGame.entries.size)" in hub && "ArcadeLines.MORE_SOON" in hub)
        // The wrapped present is drawn once, on the one tile.
        assertEquals(2, Regex("""\bpresent\(""").findAll(hub).count())          // its definition and the one call
    }

    @Test fun aReadersFirstBalloonPopStartsAtLevelOneWithLetters() {
        assertEquals(1, ArcadeStart.level(ArcadeGame.BALLOONS, GameRecord(), reader = true))
        assertEquals(0, ArcadeStart.level(ArcadeGame.BALLOONS, GameRecord(), reader = false))
        assertEquals("only Balloon pop", 0, ArcadeStart.level(ArcadeGame.GARDEN, GameRecord(), reader = true))
        assertEquals("once played, the book decides", 0, ArcadeStart.level(ArcadeGame.BALLOONS, GameRecord(rounds = 1, level = 0), reader = true))
        assertEquals(3, ArcadeStart.level(ArcadeGame.BALLOONS, GameRecord(rounds = 5, level = 3), reader = true))
        // Level 1 is the C position's five keys; a reader sees letters on the balloons (ArcadeLines.intro says so).
        assertEquals(ArcadeKeys.FIVE, ArcadePlans.balloonKeys(1))
        assertTrue(ArcadeLines.intro(ArcadeGame.BALLOONS, reader = true, level = 1).contains("letter"))
        // A round played at level 1 moves on from level 1 (three stars: 2), not from the record's 0.
        val played = RoundResult(ArcadeGame.BALLOONS, 1, offered = 20, hits = 20, helped = 0, slips = 0, lines = 0, clean = 0, keys = 5, durationMs = 70_000)
        assertEquals(2, ArcadeBook.record(ArcadeState(), played).second.levelAfter)
        // The screen asks the rule with the profile's reading.
        assertTrue("ArcadeStart.level(s.game, arcadeStore.state.of(s.game), current.reader)" in src("MainActivity.kt"))
    }
}
