package com.bigpapituna.sharpears.engine.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterTest {
    private fun family(): Roster {
        var r = Roster.fresh()
        r = r.add(Profile.kid("Mia", 1, reader = false))!!
        r = r.add(Profile.kid("Theo", 2, reader = true))!!
        return r
    }

    @Test fun anEmptyPhoneHasTheOwnerAlone() {
        val r = Roster.decode(null)
        assertEquals(listOf("owner"), r.profiles.map { it.id })
        assertEquals("Roy", r.current.name)
        assertFalse(r.needsPicker)
    }

    @Test fun addingGivesFreshIdsAndSwitches() {
        val r = family()
        assertEquals(listOf("owner", "p1", "p2"), r.profiles.map { it.id })
        assertEquals("p2", r.currentId)
        assertTrue(r.needsPicker)
        assertNull(r.add(Profile.kid("   ", 0, true)))
        val quiet = r.add(Profile.adult("Sam", 4), switch = false)!!          // added from Settings: whoever plays keeps playing
        assertEquals("p2", quiet.currentId)
        assertEquals("p3", quiet.profiles.last().id)
    }

    @Test fun eightAtMost() {
        var r = Roster.fresh()
        repeat(7) { r = r.add(Profile.kid("Kid $it", it, true))!! }
        assertEquals(8, r.profiles.size)
        assertTrue(r.isFull)
        assertNull(r.add(Profile.adult("One more", 0)))
    }

    @Test fun theOwnerCannotBeDeleted() {
        val r = family()
        assertSame(r, r.delete("owner"))
        assertSame(r, r.delete("nobody"))
    }

    @Test fun deletingTheCurrentProfileSwitchesToTheFirstLeft() {
        val r = family().switchTo("p1").delete("p1")
        assertEquals(listOf("owner", "p2"), r.profiles.map { it.id })
        assertEquals("owner", r.currentId)
        val other = family().switchTo("p2").delete("p1")
        assertEquals("p2", other.currentId)
    }

    @Test fun idsAreNotHandedOutAgainWhileHigherOnesLive() {
        val r = family().delete("p1").add(Profile.adult("Sam", 3))!!
        assertEquals("p3", r.currentId)
    }

    @Test fun switchingToSomeoneUnknownChangesNothing() {
        val r = family()
        assertEquals("p2", r.switchTo("p9").currentId)
        assertEquals("owner", r.switchTo("owner").current.id)
    }

    @Test fun renameAndKind() {
        val r = family().rename("p1", "  Mia  Rose ").setKind("p1", ProfileKind.ADULT)
        assertEquals("Mia Rose", r["p1"]!!.name)
        assertEquals(ProfileKind.ADULT, r["p1"]!!.kind)
        assertEquals(listOf(6, 12, 18), r["p1"]!!.hintSeconds)
        assertEquals("Theo", r.rename("p2", "  ")["p2"]!!.name)                 // a blank name is refused
        assertEquals(ProfileKind.ADULT, r.setKind("owner", ProfileKind.KID)["owner"]!!.kind)   // the owner stays a grown-up
    }

    @Test fun theRosterRoundTrips() {
        val r = family().switchTo("p1")
        assertEquals(r, Roster.decode(r.encode()))
    }

    @Test fun aDamagedRosterStillHasTheOwnerFirstAndAtMostEight() {
        val kids = (1..10).map { Profile.kid("Kid $it", 0, true, id = "p$it") }
        val r = Roster.decode(Roster(kids + Profile.owner(), "p10").encode())
        assertEquals(8, r.profiles.size)
        assertEquals("owner", r.profiles.first().id)
        assertEquals("owner", r.currentId)                                   // p10 did not make the cut
        assertEquals(listOf("owner"), Roster.decode("{garbage").profiles.map { it.id })
    }
}
