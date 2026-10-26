package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.Recap
import com.bigpapituna.sharpears.engine.RunWords
import com.bigpapituna.sharpears.engine.SessionPart
import com.bigpapituna.sharpears.engine.SessionWords
import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kid words on recaps and between session parts: stars not numbers, the name, the grown-up words unchanged. */
class KidWordsTest {
    private fun recap(good: Int, ok: Int, miss: Int, bias: Int = 0) = Recap(good, ok, miss, bias, longestStreak = good)

    /** No digits, no percent, no "New best", no dashes or symbol glyphs: what a kid sees and hears. */
    private fun assertKidSafe(s: String) {
        assertFalse(s, s.any { it.isDigit() })
        assertFalse(s, s.contains("%") || s.contains("New best") || s.contains("—") || s.contains("–") || s.contains(" - "))
        assertFalse(s, s.contains("{name}"))
    }

    @Test fun kidHeadlinesFollowTheStars() {
        assertTrue(RunWords.KID_TITLES[2].contains(RunWords.headline(ProfileKind.KID, recap(10, 0, 0), 0, newBest = true)))
        // 2.14: a song's stars are the song rule (two misses at most for two; three misses at 70% in time are one).
        assertTrue(RunWords.KID_TITLES[1].contains(RunWords.headline(ProfileKind.KID, recap(8, 0, 2), 0, newBest = false)))
        assertTrue(RunWords.KID_TITLES[0].contains(RunWords.headline(ProfileKind.KID, recap(7, 0, 3), 0, newBest = false)))
        assertTrue(RunWords.KID_TITLES[0].contains(RunWords.headline(ProfileKind.KID, recap(1, 0, 9), 0, newBest = true)))
        assertTrue(RunWords.KID_TITLES[2].contains(RunWords.headline(ProfileKind.KID, null, 0, false)))
        assertTrue(RunWords.KID_TITLES[0].contains(RunWords.headline(ProfileKind.KID, null, 12, false)))
        assertEquals("You did it!", RunWords.kidTitle(3, 0))
        assertEquals("The pup is dancing!", RunWords.kidTitle(3, 1))
        assertEquals(RunWords.kidTitle(3, 0), RunWords.kidTitle(3, 3))          // seeds wrap
    }

    @Test fun grownUpWordsAreUnchanged() {
        assertEquals("New best!", RunWords.headline(ProfileKind.ADULT, recap(8, 0, 2), 0, newBest = true))
        assertEquals("Clean run!", RunWords.headline(ProfileKind.ADULT, null, 0, false))
        assertEquals(RunWords.line(recap(5, 0, 5), 3, 1.0), RunWords.coaching(ProfileKind.ADULT, recap(5, 0, 5), 0, 3, 1.0, name = "Roy"))
        assertEquals(RunWords.waitLine(2, null), RunWords.coaching(ProfileKind.ADULT, null, 2, null, 1.0))
        // The celebrates rule stays: a best from 60%; kids party on three stars instead.
        assertTrue(RunWords.party(ProfileKind.ADULT, recap(6, 0, 4), 0, newBest = true))
        assertFalse(RunWords.party(ProfileKind.ADULT, recap(5, 0, 5), 0, newBest = true))
        assertFalse(RunWords.party(ProfileKind.KID, recap(6, 0, 4), 0, newBest = true))
        assertTrue(RunWords.party(ProfileKind.KID, recap(10, 0, 0), 0, newBest = false))
    }

    @Test fun kidsHearTheirNameOnEveryRecap() {
        for (stars in 1..3) for (seed in 0..2) {
            val line = RunWords.kidLine(stars, "Mia", seed)
            assertTrue(line, line.contains(", Mia"))
            assertKidSafe(line)
            val nameless = RunWords.kidLine(stars, null, seed)
            assertFalse(nameless, nameless.contains(",,") || nameless.contains(" ,") || nameless.contains(", ."))
            assertKidSafe(nameless)
        }
        assertEquals("You played it so well, Mia. The pup wants to hear it again!", RunWords.kidLine(3, "Mia"))
        assertEquals("You played it so well. The pup wants to hear it again!", RunWords.kidLine(3, "  "))
        assertTrue(RunWords.coaching(ProfileKind.KID, recap(1, 0, 9), 0, 4, 0.5, name = "Theo").contains("Theo"))
    }

    @Test fun everyKidLineIsSafe() {
        SkinLines.fixed().forEach(::assertKidSafe)
        assertTrue(SkinLines.fixed().contains("the red cat key"))
        assertTrue(SkinLines.fixed().size > 40)
        for (p in listOf(true, false)) for (l in listOf(true, false)) assertKidSafe(RunWords.kidStepLine(p, l, "Mia"))
        assertEquals("Almost, Mia! Let's try that again.", RunWords.kidStepLine(false, false, "Mia"))
        assertKidSafe(RunWords.kidLessonLine("Theo"))
    }

    @Test fun kidSessionWordsCarryNoCountsOrMinutes() {
        val parts = listOf(SessionPart.Chords(listOf("a", "b", "c")), SessionPart.Lesson("find-c", "Find C anywhere"),
            SessionPart.Loop("twinkle", "Twinkle", 3), SessionPart.Song("twinkle", "Twinkle", 1.0, 3.0))
        for (p in parts) {
            assertKidSafe(SessionWords.chip(p, kid = true))
            assertKidSafe(SessionWords.about(p, kid = true))
            assertKidSafe(SessionWords.quoted(p, kid = true))
            assertKidSafe(SessionWords.kidNext(p))
            assertFalse(SessionWords.kidNext(p).contains("“"))
            // Grown-ups keep their words.
            assertEquals(SessionWords.chip(p), SessionWords.chip(p, kid = false))
            assertEquals(SessionWords.about(p), SessionWords.about(p, kid = false))
            assertEquals(SessionWords.quoted(p), SessionWords.quoted(p, kid = false))
        }
        assertEquals("3 chords", SessionWords.chip(parts[0]))
        assertEquals("Chords", SessionWords.chip(parts[0], kid = true))
        assertEquals("Tricky bit", SessionWords.chip(parts[2], kid = true))
        assertEquals("Next: the tricky bit of Twinkle", SessionWords.kidNext(parts[2]))
    }

    @Test fun goodnightGoalAndPaws() {
        assertEquals("Goodnight, Mia! You played so well today. The pup needs a nap.", SessionWords.goodnight("Mia"))
        for (seed in 0..5) { assertTrue(SessionWords.goodnight("Theo", seed).contains("Theo")); assertKidSafe(SessionWords.goodnight(null, seed)) }
        assertEquals("The pup is ready to play!", SessionWords.kidGoal(0, 5))
        assertEquals("Keep playing! The pup is wide awake.", SessionWords.kidGoal(60, 5))
        assertEquals("Almost bedtime for the pup.", SessionWords.kidGoal(240, 5))
        assertEquals("All done for today. Goodnight, paws!", SessionWords.kidGoal(300, 5))
        for (s in listOf(0, 60, 240, 300, 900)) assertKidSafe(SessionWords.kidGoal(s, 8))
        assertKidSafe(SessionWords.pawLine(4, kid = true)); assertKidSafe(SessionWords.pawLine(0, kid = true))
        assertEquals("4 days this week", SessionWords.pawLine(4, kid = false))
        assertEquals("1 day this week", SessionWords.pawLine(1, kid = false))
        assertKidSafe(SessionWords.lessonsAsleep("Mia"))
    }
}
