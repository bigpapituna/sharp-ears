package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.8, the three layouts (2.7's SimpleWiringTest) reach every place Roy listed: each screen asks engine/nav/Simple.kt's rule
 * with the person's layout (LayoutTest checks the rules) and ends on the same "Show more" with its three-way choice. Read from
 * the sources (these are composables).
 */
class LayoutWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(f: String) = File(root, f).readText()

    @Test fun everyPlaceAsksItsRule() {
        val rules = mapOf(
            "ui/ChordsScreen.kt" to listOf("Simple.packs(", "Simple.chords(", "Simple.progressions(", "LayoutMore(", "Place.LEARN_CHORDS", "Place.PROGRESSIONS"),
            "ui/ChordGamesScreen.kt" to listOf("Simple.games(", "LayoutMore(", "Place.GAMES"),
            "ui/LibraryScreen.kt" to listOf("Simple.popSongs(", "LayoutMore(", "Place.SONGS_BY_CHORDS",
                "Simple.songsShows(Simple.SongsPart.MY_SONGS, layout)", "Simple.songsShows(Simple.SongsPart.ADD_SONG, layout)"),
            "ui/chords/PopSongPage.kt" to listOf("Simple.patterns("),
            "ui/practice/PracticeScreen.kt" to listOf("Simple.practiceShows(", "Simple.SCALE_ROOTS", "LayoutMore(", "Place.PRACTICE"),
            "ui/practice/WarmupsScreen.kt" to listOf("Simple.warmups("),
            "ui/LearnScreen.kt" to listOf("Simple.learnSideRows(", "LayoutMore(", "Place.LEARN"),
            "engine/nav/SettingsTabs.kt" to listOf("Simple.settingsShows("),
            "ui/TransportBar.kt" to listOf("Simple.inBar(", "MoreSheetButton(", "Place.PLAYER"),
            "ui/SettingsScreen.kt" to listOf("Simple.shown(layout.layout, showAll)", "SettingsTabs.shows(", "Simple.SHOW_ALL_SETTINGS", "Simple.SETTING_TITLE", "LayoutPicker(", "Place.SETTINGS"),
        )
        for ((file, calls) in rules) for (c in calls) assertTrue("$file does not use $c", c in src(file))
        // No place reads 2.7's on/off switch any more: every rule takes the layout.
        for (file in rules.keys) assertFalse("$file still reads LocalSimple", "LocalSimple" in src(file))
        // "Show more", once shown, offers the three layouts (not 2.7's one Switch to Advanced).
        val more = src("ui/LayoutMode.kt").substringAfter("fun LayoutMore(")
        assertTrue("LayoutPicker(" in more && "Simple.cuts(place, m.layout)" in more)
    }

    /** The layout: kept per profile, never a kid's, on Today's header, on You and in Settings; the plan and badge follow it. */
    @Test fun theLayoutIsWhereRoyAskedAndNeverAKids() {
        val activity = src("MainActivity.kt")
        // 2.14: by audience: a little kid is never cut, a big kid starts in Simple (engine/family/Ages.kt, AgesTest).
        assertTrue("Simple.layoutOf(current.audience, chosenLayout, current.level)" in activity)
        assertTrue("LocalLayout provides layoutMode" in activity)
        // Told to the brief as 2.7's switch was (Briefs.setting), with the layout's name.
        assertTrue("Briefs.setting(\"layout\", l.wire)" in activity)
        assertTrue("chordCurriculum().queue(" in activity)                    // Today's plan and the Learn badge
        assertTrue("Simple.packs(it.packs, layout)" in activity)
        assertTrue("Simple.openingPattern(" in activity)                      // a song page opens on a pattern the layout offers
        assertTrue("LayoutPill()" in src("ui/TodayScreen.kt"))
        assertTrue("LayoutPill()" in src("ui/profile/ProfileScreen.kt"))
        // 2.16.1: the Look tab draws it through SettingsTabs, whose rule is a grown-up's only (SettingsTabsTest).
        assertTrue("if (on(SettingsTabs.Item.LAYOUT)) {" in src("ui/SettingsScreen.kt"))
        assertTrue("Item.LAYOUT -> audience == Audience.GROWN_UP" in src("engine/nav/SettingsTabs.kt"))
        assertTrue("layout = if (kid) com.bigpapituna.sharpears.engine.nav.Layout.FULL else LocalLayout.current.layout" in src("ui/PlayerScreen.kt"))
        // The debug extra still sets it: --es simple simple|advanced|full|unset (2.7's on|off|default too).
        assertTrue("intent.getStringExtra(\"simple\")" in activity && "Layout.parse(v)" in activity)
    }
}
