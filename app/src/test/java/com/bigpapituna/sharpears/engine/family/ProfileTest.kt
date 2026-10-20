package com.bigpapituna.sharpears.engine.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {
    @Test fun kidDefaultsFollowThePlan() {
        val pre = Profile.kid("Mia", 3, reader = false)
        assertEquals(ProfileKind.KID, pre.kind)
        assertTrue(pre.isKid)
        assertEquals(5, pre.dailyGoalMin)
        assertEquals(listOf(4, 8, 12), pre.hintSeconds)
        assertTrue(pre.colourNotes)
        assertEquals(3.0, pre.micOffsetDb, 0.0)
        assertEquals(8, Profile.kid("Theo", 1, reader = true).dailyGoalMin)
    }

    @Test fun adultDefaultsFollowThePlan() {
        val a = Profile.adult("Sam", 2)
        assertFalse(a.isKid)
        assertEquals(10, a.dailyGoalMin)
        assertEquals(listOf(6, 12, 18), a.hintSeconds)
        assertFalse(a.colourNotes)
        assertEquals(0.0, a.micOffsetDb, 0.0)
    }

    @Test fun firstUnitDependsOnTheKind() {
        assertEquals("playground", Profile.kid("Mia", 0, false).firstUnitId)
        assertEquals("basics", Profile.adult("Sam", 0).firstUnitId)
        assertEquals("playground", Profile.firstUnitId(ProfileKind.KID))
    }

    @Test fun theOwnerIsRoyWithTheRedBand() {
        val o = Profile.owner()
        assertEquals("owner", o.id); assertEquals("Roy", o.name); assertEquals(0, o.avatar)
        assertTrue(o.isOwner); assertFalse(o.isKid)
    }

    @Test fun namesAreTidiedAndAvatarsKeptInRange() {
        assertEquals("Mia Rose", Profile.kid("  Mia   Rose ", 0, true).name)
        assertEquals(Profile.MAX_NAME, Profile.adult("x".repeat(40), 0).name.length)
        assertEquals(7, Profile.kid("Mia", 12, true).avatar)
        assertEquals(0, Profile.kid("Mia", -3, true).avatar)
    }

    @Test fun grownUpModeTakesTheAdultDefaultsAndKeepsWhoTheyAre() {
        val kid = Profile.kid("Mia", 5, reader = false, id = "p2", createdAt = 99)
        val grown = kid.withKind(ProfileKind.ADULT)
        assertEquals(ProfileKind.ADULT, grown.kind)
        assertEquals(listOf(6, 12, 18), grown.hintSeconds)
        assertEquals(10, grown.dailyGoalMin)
        assertFalse(grown.colourNotes)
        assertEquals(listOf("p2", "Mia", 5, 99L), listOf(grown.id, grown.name, grown.avatar, grown.createdAt))
        assertEquals(kid, kid.withKind(ProfileKind.KID))
    }

    @Test fun serialisationRoundTrips() {
        val all = listOf(
            Profile.kid("Mia", 5, reader = false, id = "p1", createdAt = 1_760_000_000_000),
            Profile.adult("Sam \"the hands\"", 7, id = "p2", createdAt = 5),
            Profile.owner(42).copy(hintSeconds = listOf(5, 9, 14), micOffsetDb = -1.5, colourNotes = true),
        )
        for (p in all) assertEquals(p, Profile.decode(p.encode()))
    }

    /**
     * Audit: a pre-reader switched to grown-up mode came back from the file as a reader (the grown-up defaults set
     * reader, and decode never read it back), so switching back to kid mode after a restart gave them letters and the
     * reader's goal.
     */
    @Test fun aGrownUpWhoWasAPreReaderStaysOneThroughTheFile() {
        val grown = Profile.kid("Mia", 5, reader = false, id = "p2").withKind(ProfileKind.ADULT)
        val back = Profile.decode(grown.encode())!!
        assertEquals(grown, back)
        assertEquals(Profile.KID_GOAL_PRE_READER, back.withKind(ProfileKind.KID).dailyGoalMin)
    }

    @Test fun olderOrBrokenTextReadsSafely() {
        assertNull(Profile.decode("not json"))
        assertNull(Profile.decode("{\"name\":\"no id\"}"))
        assertNull(Profile.decode("{\"id\":\"../evil\",\"name\":\"x\"}"))          // ids end up in file names
        val sparse = Profile.decode("{\"id\":\"p4\",\"kind\":\"KID\",\"reader\":false}")!!
        assertEquals(Profile.kid("Player", 0, false, id = "p4"), sparse)
    }
}
