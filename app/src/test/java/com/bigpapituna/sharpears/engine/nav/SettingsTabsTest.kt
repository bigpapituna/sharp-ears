package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.family.Audience
import com.bigpapituna.sharpears.engine.nav.SettingsTabs.Item
import com.bigpapituna.sharpears.engine.nav.SettingsTabs.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 2.16.1: Settings in five tabs; every block in exactly one, each person seeing what Settings showed them before. */
class SettingsTabsTest {
    private val all = SettingsTabs.Has(coach = true)
    private val noCoach = SettingsTabs.Has(coach = false)

    @Test fun fiveTabsWithOneWordLabels() {
        assertEquals(listOf("Playing", "Look", "Sound", "Family", "About"), Tab.entries.map { it.label })
        Tab.entries.forEach { assertTrue(it.label, ' ' !in it.label && it.label.length <= 8) }
        assertEquals(listOf("playing", "look", "sound", "family", "about"), Tab.entries.map { it.wire })
    }

    @Test fun everySettingIsInExactlyOneTab() {
        // Every layout part of Settings has a block, and every block one tab (an enum value has one tab by construction):
        // across the tabs' item lists each block appears once.
        val parts = Item.entries.mapNotNull { it.part }.toSet()
        assertEquals(Simple.SettingsPart.entries.toSet(), parts)
        val listed = Tab.entries.flatMap { SettingsTabs.items(it, Audience.GROWN_UP, true, Layout.FULL, all) }
        assertEquals(Item.entries.toList(), listed)
        assertEquals(listed.size, listed.toSet().size)
        // The grouping itself.
        fun of(t: Tab) = Item.entries.filter { it.tab == t }
        assertEquals(listOf(Item.ENCOURAGEMENT, Item.COACH_VOICE, Item.WRONG_KEYS, Item.REMINDER, Item.MIDI_TIMING), of(Tab.PLAYING))
        assertEquals(listOf(Item.LAYOUT, Item.PLAIN_LOOK, Item.KEYS, Item.FALLING_NOTES), of(Tab.LOOK))
        assertEquals(listOf(Item.SOUND, Item.VOICE, Item.MICROPHONE), of(Tab.SOUND))
        assertEquals(listOf(Item.FAMILY, Item.BRIEFS, Item.PROGRESS), of(Tab.FAMILY))
        assertEquals(listOf(Item.ABOUT, Item.TESTING), of(Tab.ABOUT))
    }

    @Test fun rareAndDangerousBlocksAreLastInTheirTab() {
        for ((tab, last) in listOf(Tab.PLAYING to Item.MIDI_TIMING, Tab.FAMILY to Item.PROGRESS, Tab.ABOUT to Item.TESTING))
            assertEquals(tab.name, last, Item.entries.last { it.tab == tab })
    }

    /** The settings screen draws each block once, under its own tab's branch, through the rule. */
    @Test fun theScreenDrawsEveryBlockOnceInItsTab() {
        val s = File("src/main/java/com/bigpapituna/sharpears/ui/SettingsScreen.kt").readText()
        val branches = Tab.entries.associateWith { t ->
            val from = s.indexOf("SettingsTabs.Tab.${t.name} -> {")
            assertTrue("no branch for $t", from >= 0)
            val next = Tab.entries.getOrNull(t.ordinal + 1)?.let { s.indexOf("SettingsTabs.Tab.${it.name} -> {") } ?: s.indexOf("LayoutMore(Simple.Place.SETTINGS")
            s.substring(from, next)
        }
        for (item in Item.entries) {
            val call = "on(SettingsTabs.Item.${item.name})"
            assertEquals("$item drawn once", 1, Regex(Regex.escape(call)).findAll(s).count())
            assertTrue("$item in ${item.tab}", call in branches.getValue(item.tab))
        }
    }

    @Test fun aGrownUpSeesEveryTab() {
        for (layout in Layout.entries) assertTrue(layout.name, SettingsTabs.tabs(Audience.GROWN_UP, true, layout, all).containsAll(
            listOf(Tab.PLAYING, Tab.LOOK, Tab.SOUND, Tab.FAMILY)))
        assertEquals(Tab.entries, SettingsTabs.tabs(Audience.GROWN_UP, true, Layout.FULL, all))
    }

    /** What a layout cuts is cut in its tab too; Simple's essentials stay, and About waits for Show all settings. */
    @Test fun theLayoutsCutAsBefore() {
        val simple = Tab.entries.flatMap { SettingsTabs.items(it, Audience.GROWN_UP, true, Layout.SIMPLE, all) }
        assertFalse(Item.KEYS in simple || Item.FALLING_NOTES in simple || Item.WRONG_KEYS in simple || Item.PROGRESS in simple || Item.ABOUT in simple)
        assertTrue(listOf(Item.ENCOURAGEMENT, Item.COACH_VOICE, Item.LAYOUT, Item.PLAIN_LOOK, Item.SOUND, Item.VOICE, Item.MICROPHONE,
            Item.MIDI_TIMING, Item.REMINDER, Item.FAMILY, Item.BRIEFS, Item.TESTING).all { it in simple })
        val advanced = Tab.entries.flatMap { SettingsTabs.items(it, Audience.GROWN_UP, true, Layout.ADVANCED, all) }
        assertTrue(Item.KEYS in advanced && Item.FALLING_NOTES in advanced && Item.WRONG_KEYS in advanced)
        assertFalse(Item.PROGRESS in advanced || Item.ABOUT in advanced)
        // Without Testing in the build, Simple has no About tab until Show all settings (Full).
        val noTesting = SettingsTabs.Has(testing = false)
        assertFalse(Tab.ABOUT in SettingsTabs.tabs(Audience.GROWN_UP, true, Layout.SIMPLE, noTesting))
        assertTrue(Tab.ABOUT in SettingsTabs.tabs(Audience.GROWN_UP, true, Simple.shown(Layout.SIMPLE, more = true), noTesting))
    }

    @Test fun kidsSeeOnlyTheirItems() {
        // A little kid is Full (nothing cut) but never the layout, the Plain look, the coach or the owner's reminder.
        val little = Tab.entries.flatMap { SettingsTabs.items(it, Audience.LITTLE_KID, false, Layout.FULL, all) }
        assertEquals(Item.entries - setOf(Item.LAYOUT, Item.PLAIN_LOOK, Item.COACH_VOICE, Item.REMINDER), little)
        // Look still has keys and falling notes for a little kid.
        assertEquals(listOf(Item.KEYS, Item.FALLING_NOTES), SettingsTabs.items(Tab.LOOK, Audience.LITTLE_KID, false, Layout.FULL, all))
        // A big kid is in Simple: the Plain look but no layout or coach; About only through Testing.
        val big = Tab.entries.flatMap { SettingsTabs.items(it, Audience.BIG_KID, false, Layout.SIMPLE, all) }
        assertEquals(listOf(Item.ENCOURAGEMENT, Item.MIDI_TIMING, Item.PLAIN_LOOK, Item.SOUND, Item.VOICE, Item.MICROPHONE,
            Item.FAMILY, Item.BRIEFS, Item.TESTING), big)
        assertFalse(Item.COACH_VOICE in big || Item.LAYOUT in big || Item.REMINDER in big)
        // A tab that would be empty is not drawn: a big kid without Testing has no About tab, a little kid's Look is there.
        assertEquals(listOf(Tab.PLAYING, Tab.LOOK, Tab.SOUND, Tab.FAMILY),
            SettingsTabs.tabs(Audience.BIG_KID, false, Layout.SIMPLE, SettingsTabs.Has(testing = false, coach = true)))
        for (a in Audience.entries) for (l in Layout.entries) for (h in listOf(all, noCoach, SettingsTabs.Has(false, false, false, false, false)))
            SettingsTabs.tabs(a, false, l, h).forEach { t -> assertTrue("$a $l $t", SettingsTabs.items(t, a, false, l, h).isNotEmpty()) }
    }

    @Test fun theCoachIsOnlyInABuildWithIt() {
        for (a in Audience.entries) for (l in Layout.entries)
            assertFalse("$a $l", SettingsTabs.shows(Item.COACH_VOICE, a, true, l, noCoach))
        assertTrue(SettingsTabs.shows(Item.COACH_VOICE, Audience.GROWN_UP, false, Layout.SIMPLE, all))
        assertFalse(SettingsTabs.shows(Item.COACH_VOICE, Audience.BIG_KID, false, Layout.FULL, all))
        assertFalse(SettingsTabs.shows(Item.COACH_VOICE, Audience.LITTLE_KID, false, Layout.FULL, all))
        // The door on each build: the setting is drawn through Extras.SettingsPart, which is empty without the extra.
        val main = File("src/main/java/com/bigpapituna/sharpears/ui/SettingsScreen.kt").readText()
        assertTrue("coach = com.bigpapituna.sharpears.extras.Extras.BUILT" in main)
        assertTrue("if (on(SettingsTabs.Item.COACH_VOICE)) com.bigpapituna.sharpears.extras.Extras.SettingsPart(prefs) { Sub(it) }" in main)
    }

    @Test fun theOwnersReminderAndWhatTheScreenHas() {
        assertTrue(SettingsTabs.shows(Item.REMINDER, Audience.GROWN_UP, true, Layout.SIMPLE, all))
        assertFalse(SettingsTabs.shows(Item.REMINDER, Audience.GROWN_UP, false, Layout.FULL, all))
        val bare = SettingsTabs.Has(family = false, mic = false, midi = false, testing = true)
        for (i in listOf(Item.FAMILY, Item.BRIEFS, Item.TESTING, Item.WRONG_KEYS, Item.MIDI_TIMING))
            assertFalse(i.name, SettingsTabs.shows(i, Audience.GROWN_UP, true, Layout.FULL, bare))
    }

    @Test fun theOpenTabIsTheLastOneWhenItShows() {
        val shown = listOf(Tab.PLAYING, Tab.LOOK, Tab.SOUND, Tab.FAMILY)
        assertEquals(Tab.PLAYING, SettingsTabs.pick(null, shown))
        assertEquals(Tab.SOUND, SettingsTabs.pick(Tab.SOUND, shown))
        assertEquals("a tab this person does not have: the first", Tab.PLAYING, SettingsTabs.pick(Tab.ABOUT, shown))
        assertEquals(Tab.SOUND, SettingsTabs.parse("sound"))
        assertEquals(Tab.FAMILY, SettingsTabs.parse(" Family "))
        assertNull(SettingsTabs.parse("nope"))
        assertNull(SettingsTabs.parse(null))
    }

    /** The session keeps the tab (MainActivity), and the debug route can name one. */
    @Test fun theSessionRemembersTheTab() {
        val a = File("src/main/java/com/bigpapituna/sharpears/MainActivity.kt").readText()
        assertTrue("rememberSaveable { mutableStateOf(settingsTabStart) }" in a)
        assertTrue("tabState = settingsTab" in a)
        assertTrue("SettingsTabs.parse(intent.getStringExtra(\"tab\"))" in a)
        // openSettings resets the scroll and Show all settings, never the tab.
        val open = a.substringAfter("fun openSettings(back: Screen) {").substringBefore("}")
        assertFalse("settingsTab" in open)
    }
}
