package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.progress.Progression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuetTest {
    @Test fun bothPlayersGetTheWholeRun() {
        val d = Duet(leftId = "p1", rightId = "owner")
        assertEquals(listOf(DuetCredit("p1", Hand.LEFT, 2, 40), DuetCredit("owner", Hand.RIGHT, 2, 40)), d.credit(stars = 2, xp = 40))
        assertEquals(3, d.credit(7, 10)[0].stars)
        assertEquals(0, d.credit(-1, -5)[1].stars)
        assertEquals(0, d.credit(1, -5)[1].xp)
    }

    @Test fun handsPartnersAndSwapping() {
        val d = Duet("p1", "p2")
        assertEquals(Hand.LEFT, d.handOf("p1")); assertEquals(Hand.RIGHT, d.handOf("p2")); assertNull(d.handOf("p3"))
        assertEquals("p2", d.partnerOf("p1")); assertEquals("p1", d.partnerOf("p2")); assertNull(d.partnerOf("p3"))
        assertEquals(Duet("p2", "p1"), d.swapped())
        assertEquals(Duet("me", "you"), Duet.of("me", "you", Hand.LEFT))
        assertEquals(Duet("you", "me"), Duet.of("me", "you"))
        assertTrue(runCatching { Duet("p1", "p1") }.isFailure)
    }

    @Test fun thePartnersRunIsJudgedAgainstTheirOwnProgress() {
        val first = Duet.partnerRun("ode", accuracy = 80, partnerBest = null, partnerCompleted = false)
        assertTrue(first.newBest); assertTrue(first.firstCompletion)
        val worse = Duet.partnerRun("ode", 70, partnerBest = 90, partnerCompleted = true)
        assertFalse(worse.newBest); assertFalse(worse.firstCompletion)
        val wait = Duet.partnerRun("ode", null, partnerBest = 90, partnerCompleted = false)
        assertFalse(wait.newBest); assertTrue(wait.firstCompletion)
        // The XP the partner's store pays follows from that event, like the player's own.
        assertTrue(Progression.xpFor(first) > Progression.xpFor(worse))
    }

    @Test fun theRecapNamesBothHands() {
        assertEquals("Mia played the left hand and Theo the right. Two stars each!", Duet.recapLine("Mia", "Theo", 2))
        assertEquals("Mia played the left hand and Roy the right. Good playing, both of you.", Duet.recapLine("Mia", "Roy", 0))
        assertEquals("A played the left hand and B the right. Three stars each!", Duet.recapLine("A", "B", 5))
    }
}
