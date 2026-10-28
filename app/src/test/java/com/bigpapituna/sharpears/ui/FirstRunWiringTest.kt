package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 2.17 onboarding: the screens ask engine/nav/FirstRun.kt's rules where the audit placed each change. */
class FirstRunWiringTest {
    private val main = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(main, path).readText()

    @Test fun myChildOpensTheAddAKidFormWithTheGateOnlyOnceAKidIsThere() {
        val a = src("MainActivity.kt")
        assertTrue("onForChild = if (com.bigpapituna.sharpears.engine.nav.FirstRun.asksWho(profile.isOwner, roster.profiles.any { it.isKid })) ({" in a)
        assertTrue("if (com.bigpapituna.sharpears.engine.nav.FirstRun.addKidNeedsGate(roster.profiles.any { it.isKid })) gate.guard { screen = Screen.FirstKid }" in a)
        assertTrue("NewProfileFlow(kidOnly = true," in a)
        // The kid opens straight away (their first lesson or map), as a kid: the coach's wall reads the profile's own kind.
        assertTrue("if (made == null) screen = Screen.Onboarding" in a && "val made = profiles.create(p)" in a)
        val picker = src("ui/family/ProfilePicker.kt")
        assertTrue("var kind by rememberSaveable { mutableStateOf(if (kidOnly) ProfileKind.KID else null) }" in picker)
        assertTrue("if (!kidOnly) Column(Modifier.bringIntoViewRequester(askKind)" in picker)
    }

    @Test fun theOnboardingIsThreeCardsAndAsksForNoPermission() {
        val ob = src("ui/onboarding/OnboardingScreen.kt")
        assertTrue("private const val STEPS = 3" in ob)
        assertFalse("HabitStep" in ob || "reminderOn" in ob || "useMic" in ob)
        assertTrue("if (experience < 0) experience = FirstRun.SKIP_LEVEL.ordinal" in ob)          // L4
        assertTrue("PupSays(Pose.STANDING, line" in ob && "Celebration(" !in ob)                   // L1: calm
    }

    @Test fun theFirstPlayingStepAsksHowTheyPlayAndTheFinishOffersTheNudge() {
        val l = src("ui/LessonScreen.kt")
        assertTrue("FirstRun.asksInput(anyKid, deps.prefs.inputAsked || deps.prefs.micDefaultChosen, deps.midi.connected.value != null, step.kind)" in l)
        assertTrue("val lesson = remember(lessonIn, who.level, anyKid) { FirstRun.lessonFor(lessonIn, who.level, anyKid) }" in l)
        assertTrue("FirstRun.asksNudge(who.isOwner, anyKid, deps.prefs.nudgeAsked, deps.prefs.reminderOn)" in l)
        assertTrue("launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)" in l)
        assertTrue("FirstRun.SKIP_TO_PLAYING" in l && "FirstRun.FIRST_NOTE_TITLE" in l)
    }

    @Test fun firstNoteNamesWhereEachKeyCameFrom() {
        val p = src("ui/PlayerScreen.kt")
        assertTrue("com.bigpapituna.sharpears.analytics.Events.firstNoteHeard(src.wire)" in p)
        assertEquals(2, Regex("""src = com\.bigpapituna\.sharpears\.engine\.nav\.FirstRun\.Input\.(MIDI|MIC)""").findAll(p).count())
        val a = src("MainActivity.kt")
        assertTrue("com.bigpapituna.sharpears.analytics.Events.profilePrefs = prefs" in a)
        assertTrue("if (firstOpen && (progress.xp > 0 || progress.onboardingDone)) prefs.firstNoteSent = true" in a)
    }

    /**
     * 2.18, bug 4: Learn's lesson card said "3 steps" for lesson 1 where a Never grown-up gets five (2.17 puts middle C first).
     * The card shows the lesson as this player gets it (FirstRun.lessonFor, the same call LessonScreen makes), so its icons and
     * its count are the steps they play.
     */
    @Test fun theLessonCardCountsTheStepsThePlayerGets() {
        val learn = src("ui/LearnScreen.kt")
        val card = learn.lines().first { "is PathRow.Card -> LessonCard(" in it }
        assertTrue(card, "LessonCard(FirstRun.lessonFor(r.lesson, who.level, who.isKid)," in card)
        assertTrue("val lesson = remember(lessonIn, who.level, anyKid) { FirstRun.lessonFor(lessonIn, who.level, anyKid) }" in src("ui/LessonScreen.kt"))
        val sit = com.bigpapituna.sharpears.engine.COURSE.flatMap { it.lessons }.first { it.id == com.bigpapituna.sharpears.engine.nav.FirstRun.FIRST_LESSON }
        val never = com.bigpapituna.sharpears.engine.family.Level.NEVER
        assertEquals(sit.steps.size + 2, com.bigpapituna.sharpears.engine.nav.FirstRun.lessonFor(sit, never, kid = false).steps.size)
        assertEquals(sit.steps.size, com.bigpapituna.sharpears.engine.nav.FirstRun.lessonFor(sit, never, kid = true).steps.size)
    }
}
