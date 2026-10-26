package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.chords.POP_SONGS
import com.bigpapituna.sharpears.engine.nav.Layout
import com.bigpapituna.sharpears.engine.nav.Simple
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.14, levels and ages: a kid's age band, who sees which world, and the routing every screen asks (engine/family/Ages.kt).
 * Big kids (8 to 13) get the tabs in Simple with the pop charts first and stay kids for the gate; little kids keep the map.
 */
class AgesTest {
    private val little = Profile.kid("Mila", 5, reader = false, id = "p1")
    private val big = Profile.kid("Leo", 2, reader = true, id = "p2", band = AgeBand.BIG)
    private val grownUp = Profile.adult("Dana", 3, id = "p3")

    @Test fun theBandsAndTheirWords() {
        assertEquals(listOf("Little, 4 to 7", "Big kid, 8 to 13"), AgeBand.entries.map { it.answer })
        assertEquals("How old is Leo?", AgeBand.question("Leo"))
        assertEquals("How old are they?", AgeBand.question(""))
        val words = AgeBand.entries.map { it.answer } + AgeBand.entries.map(AgeBand::line) + AgeBand.line(null)
        for (w in words) {
            assertFalse("no dashes: $w", Regex("[–—]| - |\\d-\\d").containsMatchIn(w))
            assertFalse("no woofs: $w", "woof" in w.lowercase())
        }
    }

    @Test fun whoIsWho() {
        assertEquals(Audience.LITTLE_KID, little.audience)
        assertEquals(Audience.BIG_KID, big.audience)
        assertEquals(Audience.GROWN_UP, grownUp.audience)
        // A grown-up's band is never read, whatever it says.
        assertEquals(Audience.GROWN_UP, Audience.of(ProfileKind.ADULT, AgeBand.BIG))
        // Both bands are kids for the gate, the brief, bedtime and the mic.
        assertTrue(little.isKid && big.isKid && !grownUp.isKid)
        assertTrue(Ages.kidForGrownUps(Audience.BIG_KID) && Ages.kidForGrownUps(Audience.LITTLE_KID) && !Ages.kidForGrownUps(Audience.GROWN_UP))
    }

    /** The routing table: one row per question, one column per audience (little kid, big kid, grown-up). */
    @Test fun theRoutingTable() {
        fun row(rule: (Audience) -> Any) = Audience.entries.map(rule)
        assertEquals(listOf(true, false, false), row(Ages::mapHome))
        assertEquals(listOf(false, true, true), row(Ages::tabs))
        assertEquals(listOf(true, false, false), row(Ages::kidSkin))
        assertEquals(listOf(true, false, false), row(Ages::kidCheers))
        assertEquals(listOf("playground", "basics", "basics"), row(Ages::firstUnitId))
        assertEquals(listOf(false, true, false), row(Ages::allPopSongs))
        assertEquals(listOf(false, true, false), row(Ages::songsOpenOnChords))
        assertEquals(listOf(false, true, false), row(Ages::arcadeInTabs))
        assertEquals(listOf(false, true, true), row(Ages::showsLevels))
        assertEquals(listOf(true, false, false), row(Ages::gift))
        assertEquals(listOf(false, true, true), row(Ages::streakLine))
        assertEquals(listOf(0.85f, 1f, 1f), row(Ages::voiceSpeed))
        assertEquals(listOf(false, false, true), row { Ages.asksLayout(it, null) })
        assertEquals(listOf(false, false, false), row { Ages.asksLayout(it, Layout.FULL) })
        // The profile asks the same rules.
        assertEquals(listOf(true, false, false), listOf(little, big, grownUp).map { it.mapHome })
        assertEquals(listOf(true, false, false), listOf(little, big, grownUp).map { it.kidSkin })
        assertEquals(listOf("playground", "basics", "basics"), listOf(little, big, grownUp).map { it.firstUnitId })
    }

    @Test fun aBigKidIsInSimpleWithEverySongByChords() {
        for (lv in Level.entries) {
            assertEquals(Layout.SIMPLE, Simple.layoutOf(Audience.BIG_KID, null, lv))
            assertEquals("a little kid is never cut", Layout.FULL, Simple.layoutOf(Audience.LITTLE_KID, Layout.SIMPLE, lv))
            assertEquals(Simple.layoutOf(false, null, lv), Simple.layoutOf(Audience.GROWN_UP, null, lv))
        }
        assertEquals("their own choice holds", Layout.ADVANCED, Simple.layoutOf(Audience.BIG_KID, Layout.ADVANCED, Level.NEVER))
        // Simple keeps a grown-up beginner to the easy songs; a big kid gets them all.
        assertTrue(Simple.popSongs(POP_SONGS, Layout.SIMPLE, Audience.GROWN_UP).size < POP_SONGS.size)
        assertEquals(POP_SONGS, Simple.popSongs(POP_SONGS, Layout.SIMPLE, Audience.BIG_KID))
    }

    @Test fun aBigKidsDefaultsAndPlacementWalkTheMainCourse() {
        assertEquals(Profile.KID_GOAL_BIG, big.dailyGoalMin)
        assertFalse("letters, not colours, for 8 to 13", big.colourNotes)
        assertEquals(Profile.ADULT_HINTS, big.hintSeconds)
        assertEquals(Profile.KID_MIC_OFFSET_DB, big.micOffsetDb, 0.0)
        // No lessons: Basics; a few: Beginner; lots: Intermediate. Never the Playground's pages.
        val plans = Level.kid.map { Placement.planFor(it, ProfileKind.KID, reader = true, band = AgeBand.BIG) }
        assertEquals(listOf("basics", "beginner", "intermediate"), plans.map { it.startUnitId })
        assertTrue(plans.all { it.kind == ProfileKind.KID && it.homeFirst == HomeFirst.COURSE })
        assertEquals("Begin at Beginner. Basics stays open.", plans[1].line)
        // A little kid's placement is the pages, as before.
        assertEquals("pond-1", Placement.planFor(Level.A_LITTLE, ProfileKind.KID, reader = true).startLessonId)
        assertEquals(Placement.planFor(Level.SOME, ProfileKind.KID, true, AgeBand.BIG), Placement.planOf(big.copy(level = Level.SOME)))
        // The check's words follow the band.
        assertEquals("Let's begin at Basics.", PlacementCheck.suggest(Level.A_LITTLE, 0, ProfileKind.KID, band = AgeBand.BIG).line)
        assertEquals("Let's begin at the garden.", PlacementCheck.suggest(Level.A_LITTLE, 0, ProfileKind.KID).line)
    }

    /** The migration: every kid saved before 2.14 has no band and reads as a little kid; the band round-trips. */
    @Test fun oldKidsAreLittleAndTheBandIsSaved() {
        val old = """{"id":"p4","name":"Theo","avatar":1,"kind":"KID","reader":true,"dailyGoalMin":8,"hintSeconds":[4,8,12],"colourNotes":true,"micOffsetDb":3.0,"createdAt":0,"level":"SOME"}"""
        val read = Profile.decode(old)!!
        assertEquals(AgeBand.LITTLE, read.band); assertEquals(Audience.LITTLE_KID, read.audience)
        assertEquals(Level.SOME, read.level)
        assertEquals(big, Profile.decode(big.encode()))
        assertEquals(AgeBand.BIG, Profile.decode(big.encode())!!.band)
        assertEquals(AgeBand.LITTLE, AgeBand.parse("nonsense"))
        // A roster keeps it too.
        val roster = Roster.fresh().add(big, switch = false)!!
        assertEquals(AgeBand.BIG, Roster.decode(roster.encode()).profiles.last().band)
    }

    @Test fun changingTheBandKeepsWhoTheyAre() {
        val grown = little.copy(level = Level.A_LITTLE).withBand(AgeBand.BIG)
        assertEquals(AgeBand.BIG, grown.band); assertEquals(Level.A_LITTLE, grown.level); assertEquals("Mila", grown.name)
        assertFalse(grown.colourNotes); assertEquals(Profile.ADULT_HINTS, grown.hintSeconds)
        val back = grown.withBand(AgeBand.LITTLE)
        assertTrue(back.colourNotes); assertEquals(Profile.KID_HINTS, back.hintSeconds)
        assertEquals(big, big.withBand(AgeBand.BIG))
        // Grown-up mode keeps the band for a later switch back; the audience follows the kind.
        assertEquals(Audience.GROWN_UP, big.withKind(ProfileKind.ADULT).audience)
        assertEquals(Audience.BIG_KID, big.withKind(ProfileKind.ADULT).withKind(ProfileKind.KID).audience)
    }
}
