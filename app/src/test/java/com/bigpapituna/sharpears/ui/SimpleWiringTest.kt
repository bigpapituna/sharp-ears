package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.7, Simple mode reaches every place Roy listed: each screen asks engine/nav/Simple.kt's rule (SimpleTest checks the rules)
 * and ends on the same "Show more". Read from the sources (these are composables).
 */
class SimpleWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(f: String) = File(root, f).readText()

    @Test fun everyPlaceAsksItsRule() {
        val rules = mapOf(
            "ui/ChordsScreen.kt" to listOf("Simple.packs(", "Simple.CHORDS", "Simple.progressions(", "SimpleMore("),
            "ui/ChordGamesScreen.kt" to listOf("Simple.games(", "SimpleMore("),
            "ui/LibraryScreen.kt" to listOf("Simple.popSongs(", "SimpleMore("),
            "ui/chords/PopSongPage.kt" to listOf("Simple.patterns("),
            "ui/practice/PracticeScreen.kt" to listOf("Simple.practiceShows(", "Simple.SCALE_ROOTS", "SimpleMore("),
            "ui/practice/WarmupsScreen.kt" to listOf("Simple.warmups("),
            "ui/LearnScreen.kt" to listOf("Simple.learnSideRows(", "SimpleMore("),
            "ui/TransportBar.kt" to listOf("Simple.inBar(", "MoreSheetButton("),
            "ui/SettingsScreen.kt" to listOf("Simple.settingsShows(", "Simple.SHOW_ALL_SETTINGS", "Simple.SETTING_TITLE"),
        )
        for ((file, calls) in rules) for (c in calls) assertTrue("$file does not use $c", c in src(file))
    }

    /** The switch: kept per profile, never a kid's, on Today's header, on You and in Settings; the plan and badge follow it. */
    @Test fun theSwitchIsWhereRoyAskedAndNeverAKids() {
        val activity = src("MainActivity.kt")
        assertTrue("Simple.isOn(current.isKid, prefs.simpleMode, current.level)" in activity)
        assertTrue("LocalSimple provides simple" in activity)
        assertTrue("chordCurriculum().queue(" in activity)                    // Today's plan and the Learn badge
        assertTrue("SimplePill()" in src("ui/TodayScreen.kt"))
        assertTrue("SimplePill()" in src("ui/profile/ProfileScreen.kt"))
        assertTrue("if (!me.isKid && shows(Simple.SettingsPart.SIMPLE))" in src("ui/SettingsScreen.kt"))
        assertTrue("simple = !kid && LocalSimple.current.on" in src("ui/PlayerScreen.kt"))
    }
}
