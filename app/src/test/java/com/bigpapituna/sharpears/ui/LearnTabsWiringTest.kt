package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.18.3, read from the sources (composables): Learn's tabs open each mode's home inside Learn and its lesson, game or level
 * as its own screen that comes back to the tab; and the parent gate is one tap for now, with the hold and the sum kept.
 */
class LearnTabsWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(f: String) = File(root, f).readText()
    private val activity = src("MainActivity.kt")

    @Test fun eachTabDrawsItsHomeAndOpensItsOwnScreen() {
        assertTrue("TheoryHome(theory, onOpen = { id -> screen = Screen.Theory(s, id) })" in activity)
        assertTrue("EarHub(earStore, onGame = { k, l -> screen = Screen.EarTraining(s, EarStart(k, l)) }" in activity)
        assertTrue("onWorkout = { screen = Screen.EarTraining(s, EarStart(workout = true)) }" in activity)
        assertTrue("onOpen = { n -> screen = Screen.SightRead(s, n) }" in activity)
        // The tab is kept per profile; a debug route's tab is saved once.
        assertTrue("startTab = remember(current.id) { prefs.learnTab }, onTab = { prefs.learnTab = it }" in activity)
        assertTrue("learnTabStart?.let { prefs.learnTab = it; learnTabStart = null }" in activity)
        // The old side rows are gone.
        val learn = src("ui/LearnScreen.kt")
        assertFalse("SideRow(" in learn)
        assertTrue("LearnTabs.tabs(kid, reader," in learn)
    }

    @Test fun leavingATabsLessonOrGameGoesBackToTheTab() {
        val theory = src("ui/theory/TheoryScreen.kt")
        assertTrue("fun toPath() { voice.stop(); if (fromTab != null) onBack() else nav = Nav.Path }" in theory)
        val ear = src("ui/ear/EarTrainingScreen.kt")
        assertTrue("fun toHub() { voice.stop(); if (start != null) onBack() else mode = Mode.Hub }" in ear)
        assertTrue("startLesson = s.lesson" in activity)
    }

    @Test fun theGateIsOneTapAndTheHoldAndSumWaitToComeBack() {
        val gate = src("ui/family/ParentGate.kt")
        val body = gate.substringAfter("fun ParentGate(").substringBefore("\n}\n")
        assertTrue("OneTapConfirm(onPass, onCancel)" in body)
        assertFalse(body.lines().any { "HoldThePup {" in it && !it.trim().startsWith("//") && !it.trim().startsWith("(was:") })
        assertTrue("// ponytail: one-tap gate for Roy's testing; restore HoldThePup + SumPad before kids get the app" in body)
        assertTrue("I'm a grown-up" in gate && "Not now" in gate)
        // Kept for the way back: the hold, the sum pad, the controller's two minutes and its lockout.
        assertTrue("internal fun HoldThePup(" in gate && "private fun SumPad(" in gate)
        assertTrue("fun passed()" in gate && "fun close()" in gate && "fun wrong()" in gate)
        assertEquals(1, gate.split("fun ParentGateHost(").size - 1)
    }
}
