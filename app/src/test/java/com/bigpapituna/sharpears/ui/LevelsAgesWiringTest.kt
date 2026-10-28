package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.14, levels and ages: the screens ask the engine's rules (engine/family/Ages.kt, engine/nav/DayOne.kt,
 * engine/kid/KidLevels.kt, engine/chords/KnownChords.kt, Stars.three, Comeback.resume). Read from the sources, as the other
 * wiring tests do: the rules themselves are tested on the JVM (AgesTest, DayOneTest, KidLevelsTest, KnownChordsTest,
 * StarsTest, ComebackResumeTest).
 */
class LevelsAgesWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(f: String) = File(root, f).readText().replace("\r\n", "\n")
    private val activity = src("MainActivity.kt")

    @Test fun aBigKidGetsTheTabsAndStaysAKidForTheGrownUps() {
        // The map is a little kid's home; the tabs, the layout and the Songs tab follow the audience.
        assertTrue("is Screen.Home -> if (current.mapHome) {" in activity)
        assertTrue("Simple.layoutOf(current.audience, chosenLayout, current.level)" in activity)
        assertTrue("Ages.songsOpenOnChords(current.audience)" in activity && "audience = current.audience" in activity)
        assertTrue("Ages.arcadeInTabs(current.audience)" in activity && "screen = Screen.Arcade(s)" in activity)
        assertTrue("onArcade" in src("ui/play/PlayRow.kt"))
        // A band change reopens the app; the voice and the levels follow the audience.
        assertTrue("listOf(it.id, it.kind, it.reader, it.level, it.band)" in activity && "openBand != now.band" in activity)
        assertTrue("Ages.voiceSpeed(profile.audience)" in activity)
        assertTrue("ProgressOverlays(progress, holdLevelUp = current.kidSkin" in activity)
        // Still a kid for the gate, the brief, bedtime, the mic and the tester's kind.
        // (2.14 merge: parents closes the gate on every switch of player, kids or not: ParentGateRules.closesOnOpen.)
        for (s in listOf("ParentGateRules.closesOnOpen(openId, profile.id, recreated)", "Tester.profile(profile.isKid, profile.id)",
                "mic.forProfile(profile.isKid, profile.micOffsetDb", "Bedtime.sayGoodnight(profile.isKid", "brief = if (current.isKid) null",
                "onSettings = { gate.guardForKids { openSettings(s) } }"))
            assertTrue(s, s in activity)
        // The skin's places ask the kid skin, not "a kid".
        assertTrue("fun isKid(): Boolean = LocalProfile.current.kidSkin" in src("ui/family/KidsMode.kt"))
        assertTrue("val kid = who.kidSkin" in src("ui/LessonScreen.kt"))
        assertTrue("val kid = profile.kidSkin" in src("ui/PlayerScreen.kt"))
        assertTrue("LocalProfile.current.kidSkin, key = original.id" in src("ui/PlayerScreen.kt"))      // the cheer pops' bank
        assertTrue("CheerRules.aloud(me.kidSkin" in src("ui/SettingsScreen.kt"))
        assertTrue("val kid = kidOverride ?: profile.kidSkin" in src("ui/chords/PopSongPage.kt"))
        // Quests stay per kind everywhere (Today and the map ask them by isKid).
        assertTrue("recordQuest(QuestEvent.SongFinished(meta.id), profile.isKid)" in src("ui/PlayerScreen.kt"))
        // The new-kid flow asks the band, then reading, then lessons for a reader; Family can change the band.
        val flow = src("ui/family/ProfilePicker.kt")
        assertTrue("AgeBand.question(clean)" in flow && "Placement.kidQuestion(clean)" in flow && "band = band ?:" in flow)
        assertTrue("it.withBand(" in src("ui/family/FamilySettings.kt"))
    }

    @Test fun dayOneFollowsPlacement() {
        assertTrue("DayOne.chordsFor(level, played," in activity && "lessonFirst = !profile.mapHome && com.bigpapituna.sharpears.engine.nav.DayOne.lessonFirst(level)" in activity)
        assertTrue("DayOne.micBanner(progress.useMic, micAllowed, prefs.micBannerClosed)" in activity)
        // 2.17: the fourth card is gone; someone never asked how they play is asked unless MIDI is in (FirstRun.checkAsksMic).
        assertTrue("askMic = com.bigpapituna.sharpears.engine.nav.FirstRun.checkAsksMic(progress.useMic, profile.isKid, prefs.inputAsked || prefs.micDefaultChosen, midi.connected.value != null)" in activity)
        assertTrue("plain = com.bigpapituna.sharpears.engine.nav.DayOne.plainChords(placed())" in activity)
        assertTrue("ChordWords.ask(current)" in src("ui/ChordsScreen.kt") && "ChordWords.keysLine(current)" in src("ui/ChordsScreen.kt"))
        assertTrue("continueMeta(easy = easyFirstSong())" in activity && "startHere = easyFirstSong()" in activity)
        assertTrue("ContinueTile.PopSong(it, fresh = true)" in activity)
        assertTrue("progress.seedFromPlacement(" in activity && "openProgress?.let { seedLevel(now, it) }" in activity)
    }

    @Test fun kidsArePlacedAndTheGardenCanBeSkipped() {
        assertTrue("GardenSkip.offered(current.audience" in activity && "SkipGardenCard(" in activity)
        assertTrue("GardenSkip.LEVEL_AFTER" in activity && "kidStore.gardenSkipAsked = true" in activity)
        assertTrue("KidPop.shows(current.audience, current.reader)" in activity && "Screen.KidChords(s, chip)" in activity)
        assertTrue("KidPop.songs(songs, chip)" in src("ui/chords/PopSongsScreen.kt"))
    }

    @Test fun knownChordsFillTheTwoLists() {
        assertTrue("KnownChordsSheet(" in activity && "lessons.know(picked)" in activity)
        assertTrue("KnownChords.offered(placed()" in activity && "KnownChordsRow(" in activity)
        assertTrue("onKnownChords = { knownChordsOpen = true }" in activity)
        assertTrue("onKnownChords" in src("ui/chords/PopSongsScreen.kt") && "KnownChords.EMPTY_LINE" in src("ui/chords/PopSongsScreen.kt"))
    }

    @Test fun starsAreChasedAndSkipsAreNeverCelebrated() {
        val lesson = src("ui/LessonScreen.kt")
        // The skip sheet before Next passes a graded step; a skip makes the finish neutral; "Try for 3 stars" to the first graded step.
        assertTrue("LessonStars.SKIP_TITLE" in lesson && "LessonStars.SKIP_LINE" in lesson && "skippedGraded++" in lesson)
        assertTrue("LessonStars.finish(skippedGraded)" in lesson && "LessonStars.NEUTRAL_TITLE" in lesson)
        assertTrue("if (skippedGraded == 0) voice.speak(" in lesson)            // a skip says no unrecorded line
        assertTrue("LessonStars.tryForThree(lessonStars, lesson.steps)" in lesson && "onRetry(com.bigpapituna.sharpears.engine.kid.LessonStars.firstGraded(lesson.steps))" in lesson)
        assertTrue("stars = finishStars" in lesson)
        // 2.14 merge: under a grown-up's stars, the lesson's own 3-star rule (the song recap says the song's: depth's TimingPanel).
        assertTrue("Stars.lessonLines(stepStars.toList())" in lesson && "reward = grownStars" in lesson)
        assertTrue("SongStars.ruleLines(recap)" in src("ui/player/TimingPanel.kt"))
        assertTrue("LessonStars.ofRun(report, tally?.letGos ?: 0)" in lesson)
        // (2.14 merge: both first ask parents' daily time, KidDoors; a capped kid gets the goodbye instead.)
        assertTrue("navCount.intValue++; screen = Screen.Lesson(l, s.back, step)" in activity && "onRetry = { step ->" in activity)
        assertTrue("onReplay = { st -> if (capSince >= 0)" in activity && "else path.lesson(st.lessonId)" in activity && "LessonStars.firstGraded(it.steps)" in activity)
        val celebrate = src("ui/Celebrate.kt")
        assertTrue("class FinishStars(" in celebrate && "pup.play(PupMood.WAG)" in celebrate)
        // The 3-star rule lives in one engine function.
        val stars = src("engine/skin/Stars.kt")
        assertTrue("fun three(" in stars && "three(r, 0, letGos) -> 3" in stars && "three(null, wrongKeys, letGos) -> 3" in stars)
    }

    @Test fun theComebackOpensAStepAndSpeaksToItsAudience() {
        assertTrue("Comeback.resume(com.bigpapituna.sharpears.engine.Comeback.parseLeft(prefs.openStep)" in activity)
        // Only a lesson not finished keeps its step: a replay of a finished one never overwrites the step left elsewhere.
        assertEquals(2, activity.split("if (!prefs.lessonDone(l.id)) prefs.openStep = com.bigpapituna.sharpears.engine.Comeback.encodeLeft(l.id, it)").size - 1)
        assertFalse("|| it > 0) prefs.openStep" in activity)
        assertTrue("Comeback.BIG_KID_TITLE" in activity && "Comeback.streakLine(" in activity && "Ages.streakLine(current.audience)" in activity)
        // Never a list: the old fallback to the Songs tab is gone.
        val back = activity.substringAfter("welcomeBack = if (!comebackOpen").substringBefore("knownChords = if")
        assertFalse("null -> screen = Screen.Home(HomeTab.SONGS)" in back)
        assertTrue("screen = Screen.Lesson(it, home, step)" in activity)
    }
}
