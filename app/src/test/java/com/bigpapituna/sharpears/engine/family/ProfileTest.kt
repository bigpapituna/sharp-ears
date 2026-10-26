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

    @Test fun theOwnerHasNoNameUntilGivenOneAndTheRedBand() {
        val o = Profile.owner()
        assertEquals("owner", o.id); assertEquals(Profile.UNSET_NAME, o.name); assertEquals(0, o.avatar)
        assertTrue(o.isOwner); assertFalse(o.isKid)
        assertFalse(o.named); assertNull(o.greetName)
        assertEquals("Dana", OwnerName.apply(o, "  Dana ").greetName)
        assertNull(OwnerName.apply(OwnerName.apply(o, "Dana"), "   ").greetName)
        assertEquals(Profile.UNSET_NAME, OwnerName.apply(OwnerName.apply(o, "Dana"), "").name)
    }

    @Test fun theNameFlagRoundTripsAndOldTextIsReadFairly() {
        val dana = OwnerName.apply(Profile.owner(5), "Dana")
        assertEquals(dana, Profile.decode(dana.encode()))
        assertEquals(Profile.owner(5), Profile.decode(Profile.owner(5).encode()))
        // From before 2.14 (no flag): the owner called Roy never chose it; any other name, and every other profile, was typed.
        assertFalse(Profile.decode("""{"id":"owner","name":"Roy","kind":"ADULT"}""")!!.named)
        assertFalse(Profile.decode("""{"id":"owner","kind":"ADULT"}""")!!.named)
        assertTrue(Profile.decode("""{"id":"owner","name":"Priya","kind":"ADULT"}""")!!.named)
        assertTrue(Profile.decode("""{"id":"p2","name":"Roy","kind":"ADULT"}""")!!.named)
        assertTrue(Profile.decode("""{"id":"p3","name":"Mia","kind":"KID"}""")!!.named)
        // The owner is asked once, after the first-run flow, and a kid never.
        val roy = Profile.decode("""{"id":"owner","name":"Roy","kind":"ADULT"}""")!!
        assertTrue(OwnerName.askDue(roy, asked = false, onboardingDone = true))
        assertFalse(OwnerName.askDue(roy, asked = true, onboardingDone = true))
        assertFalse(OwnerName.askDue(roy, asked = false, onboardingDone = false))
        assertFalse(OwnerName.askDue(Profile.kid("Mia", 1, true, id = "p2"), asked = false, onboardingDone = true))
    }

    /** 2.14: every greeting reads the name, and none is "Hi there", never a name nobody typed. */
    @Test fun greetingsReadTheNameOrSayHiThere() {
        assertEquals("Hi, Dana.", Greeting.hi("Dana")); assertEquals("Hi there.", Greeting.hi(null)); assertEquals("Hi there.", Greeting.hi("  "))
        assertEquals("Welcome back, Dana.", Greeting.welcomeBack("Dana")); assertEquals("Welcome back.", Greeting.welcomeBack(null))
        assertEquals("Hi, Dana.", Greeting.layout("Dana", returning = false)); assertEquals("Welcome back, Dana.", Greeting.layout("Dana", returning = true))
        assertEquals("Hi there.", Greeting.layout(null, returning = false)); assertEquals("Welcome back.", Greeting.layout(null, returning = true))
        assertEquals("Good evening, Dana.", Greeting.timeOfDay("Good evening", "Dana")); assertEquals("Good evening.", Greeting.timeOfDay("Good evening", null))
        assertEquals("Hi there. One card.", Greeting.tour(null, "One card."))
        assertEquals("Dana, here is your week.", Greeting.myWeek("Dana")); assertEquals("Here is your week.", Greeting.myWeek(null))
        assertEquals("Dana, here is how your playing went, with one or two things to try.", Greeting.myWeekCard("Dana"))
        assertEquals("Here is how your playing went, with one or two things to try.", Greeting.myWeekCard(null))
        // Copy rules: sentence case, no dashes, every line ends with a stop.
        for (l in listOf(Greeting.hi(null), Greeting.welcomeBack(null), Greeting.myWeek(null), Greeting.myWeekCard(null), Greeting.myWeek("Dana")))
            assertTrue(l, l.endsWith(".") && "—" !in l && "–" !in l)
    }

    @Test fun namesAreTidiedAndAvatarsKeptInRange() {
        assertEquals("Mia Rose", Profile.kid("  Mia   Rose ", 0, true).name)
        assertEquals(Profile.MAX_NAME, Profile.adult("x".repeat(40), 0).name.length)
        assertEquals(7, Profile.kid("Mia", 12, true).avatar)
        assertEquals(0, Profile.kid("Mia", -3, true).avatar)
    }

    /** Audit: a name cut at 20 UTF-16 units could keep half an emoji, a broken glyph that the backup wrote as "?". */
    @Test fun aLongNameIsNeverCutInsideAnEmoji() {
        val name = Profile.cleanName("a".repeat(19) + "🎹 Mia")
        assertEquals("a".repeat(19), name)
        assertEquals("Zoë 🎹", Profile.cleanName("Zoë 🎹"))
        assertEquals(name, Profile.decode(Profile.kid(name, 0, true, id = "p1").encode())!!.name)
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
