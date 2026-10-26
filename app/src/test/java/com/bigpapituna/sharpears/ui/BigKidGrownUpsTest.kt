package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.engine.family.AgeBand
import com.bigpapituna.sharpears.engine.family.Ages
import com.bigpapituna.sharpears.engine.family.ParentGateRules
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.skin.Bedtime
import com.bigpapituna.sharpears.engine.skin.KidDay
import com.bigpapituna.sharpears.engine.skin.KidDoor
import com.bigpapituna.sharpears.engine.skin.KidDoors
import com.bigpapituna.sharpears.engine.skin.TimeCap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * 2.14 merge (levels-ages with parents): a big kid (8 to 13, Profile.bigKid) reads the grown-up screens, so the UI helper
 * `isKid()` and the screens' `kid` are false for them (the kid skin); every grown-ups' control still treats them as a kid
 * through the profile's own `isKid`: the parent gate, the daily time cap and its doors, wind down, the brief, the email and
 * the tester's profile kind. The rules on the JVM, then the wiring from the sources.
 */
class BigKidGrownUpsTest {
    private val bigKid = Profile.kid("Leo", 2, reader = true, id = "p2", band = AgeBand.BIG)
    private val owner = Profile.owner()
    private val today = LocalDate.of(2026, 10, 26)

    @Test fun aBigKidIsAKidForTheGrownUpsButNotForTheSkin() {
        assertTrue(bigKid.isKid)
        assertTrue(bigKid.bigKid)
        assertFalse(bigKid.kidSkin)
        assertFalse(bigKid.mapHome)
        assertTrue(Ages.kidForGrownUps(bigKid.audience))
        // Read back from the profiles file the same.
        val back = Profile.decode(bigKid.encode())!!
        assertTrue(back.isKid && back.bigKid && !back.kidSkin)
    }

    @Test fun aBigKidIsGated() {
        // The picker asks the gate before a grown-up's profile when a big kid is in the family, and not before the big kid's.
        assertTrue(ParentGateRules.pickerAsks(listOf(owner, bigKid), owner))
        assertFalse(ParentGateRules.pickerAsks(listOf(owner, bigKid), bigKid))
        // ParentGateController.guardForKids asks the profile, never the skin.
        val gate = src("ui/family/ParentGate.kt")
        assertTrue("if (profiles?.current?.isKid == true) guard(action) else action()" in gate)
        // The brief's actions ask the gate for any kid's brief.
        assertTrue("fun ParentGateController.guardBrief(about: Profile, action: () -> Unit) = if (about.isKid) guard(action)" in src("ui/brief/BriefPage.kt"))
        // Settings and the Microphone setup from You go through the gate for a kid, the big kid's tabs included.
        assertTrue("onSettings = { gate.guardForKids { openSettings(s) } }" in activity)
    }

    @Test fun aBigKidIsCappedAndWoundDown() {
        // The rules take the profile's isKid: a big kid's day closes like a little kid's.
        assertTrue(TimeCap.reached(20, 20 * 60_000L))
        assertFalse(Bedtime.songsOpen(bigKid.isKid, practisedSec = 600, goalMin = 10, bedtime = true, closeSongs = true))
        assertFalse(Bedtime.lessonOpen(bigKid.isKid, practisedSec = 600, goalMin = 10, today = today, bedtime = true))
        val capped = KidDay(capReached = true)
        for (door in listOf(KidDoor.LESSON, KidDoor.SONGS, KidDoor.TOYS, KidDoor.GAMES)) assertFalse("$door", KidDoors.open(door, capped))
        // MainActivity reads the cap and wind down for profile.isKid, and every screen a big kid's tabs reach has a door.
        assertTrue("if (profile.isKid) LaunchedEffect(profile.id, timeVersion) {" in activity)
        assertTrue("val songsAsleep = profile.isKid &&" in activity)
        val doors = Regex("(?s)val kidDoor = if \\(!profile\\.isKid\\) null else when \\(screen\\) \\{(.*?)\\n\\s*else -> null").find(activity)!!.groupValues[1]
        for (s in listOf("Screen.Lesson", "Screen.Daily", "Screen.LearnChords", "Screen.Progressions", "Screen.SightRead", "Screen.EarTraining",
                "Screen.Theory", "Screen.Player", "Screen.PopSong", "Screen.ChordGames", "Screen.Arcade", "Screen.ArcadeRound"))
            assertTrue("$s has a door", Regex("""\b${Regex.escape(s)}\b""").containsMatchIn(doors))
        // Practice today's lesson (a big kid's Start) ends at its next step once the time is up, as one from Learn does.
        assertTrue("if (profile.isKid && capSince >= 0) closedForToday(\"a plan's lesson step ended\")" in activity)
        // A lesson's bedtime asks every kid, the skin only draws it.
        val lesson = src("ui/LessonScreen.kt")
        assertTrue("val kid = who.kidSkin" in lesson && "val anyKid = who.isKid" in lesson)
        assertTrue("val songsOpen = !anyKid ||" in lesson)
        assertTrue("var asleep by remember { mutableStateOf(anyKid &&" in lesson)
        assertTrue("val bedtimeNow = anyKid &&" in lesson && "val nextAsleep = anyKid &&" in lesson)
        assertTrue("if (bedtimeNow) goodnight = true else (onNext ?: onBack)()" in lesson)
        // Family > the kid: the daily time for any kid, a big one too.
        assertTrue("if (expanded && p.isKid) KidTimeControls(" in src("ui/family/FamilySettings.kt"))
    }

    @Test fun aBigKidIsBriefedEmailedAndCountedAsAKid() {
        assertTrue("all.filter { it.isKid }.map { Kid(it, EmailSettings.of(app, it.id)) }" in src("reminder/EmailJob.kt"))
        assertTrue("roster.profiles.filter { it.isKid }" in activity)
        // The family card opens a brief without the gate: a grown-up's Today only, never a big kid's (who sees Today since 2.14).
        assertTrue("family = if (current.isKid) null else roster.profiles.filter { it.isKid }" in activity)
        // The pup's card for the brief follows a big kid's grown-up finish too.
        assertTrue("rememberKidCardStep(who, ready = anyKid && finished)" in src("ui/LessonScreen.kt"))
        // The brief's next lesson and the email's map page: the map is a little kid's, a big kid walks the main course.
        assertTrue("return if (who.mapHome) KidMap.next(" in src("ui/brief/AppEffects.kt"))
        assertTrue("val path = if (!profile.mapHome) null else runCatching {" in src("data/BriefEmail.kt"))
        // The tester's profile kind is the profile's: the cheer pops' kind too, not their word bank's.
        assertTrue("Tester.profile(profile.isKid, profile.id)" in activity)
        val pops = src("ui/pup/CheerPop.kt")
        assertTrue("Tester.cheerPop(c.trigger, profileKid)" in pops && "Tester.cheerPop(c.trigger, kid)" !in pops)
        assertTrue("profileKid = anyKid" in src("ui/LessonScreen.kt"))
        assertTrue("profileKid = com.bigpapituna.sharpears.ui.family.LocalProfile.current.isKid" in src("ui/PlayerScreen.kt"))
    }

    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(f: String) = File(root, f).readText().replace("\r\n", "\n")
    private val activity by lazy { src("MainActivity.kt") }
}
