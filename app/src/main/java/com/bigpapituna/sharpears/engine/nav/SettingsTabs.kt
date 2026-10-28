package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.family.Audience
import com.bigpapituna.sharpears.engine.family.PlainLook

/**
 * 2.16.1, Settings in tabs (Roy: "the settings page is getting a bit too cluttered ... a few tabs per category"). Five tabs,
 * one word each: Playing, Look, Sound, Family, About. Every block of Settings ([Item]) sits in exactly one tab, in the order the
 * tab draws it; rare or dangerous blocks last (MIDI keyboard, the microphone's kept recordings, Progress with its Reset,
 * Testing). Nothing about a setting changed: its preference, default, gate and brief key are where they were; this only says
 * which tab draws it and whether this person sees it, by the same rules Settings asked before (the layout's
 * [Simple.settingsShows], a grown-up's own layout, the Plain look for grown-ups and big kids, the coach for grown-ups on a build
 * with it, the reminder for the phone's owner). A tab with nothing to show for this person is not drawn.
 *
 * Pure functions (SettingsTabsTest); ui/SettingsScreen.kt asks them.
 */
object SettingsTabs {
    enum class Tab(val label: String) {
        PLAYING("Playing"), LOOK("Look"), SOUND("Sound"), FAMILY("Family"), ABOUT("About");

        /** The debug route's spelling (`--es screen settings --es tab sound`). */
        val wire: String get() = name.lowercase()
    }

    /** A block of Settings: its tab, and the layout part that cuts it (none: shown in every layout). */
    enum class Item(val tab: Tab, val part: Simple.SettingsPart?) {
        // Playing: how lessons and songs answer you.
        ENCOURAGEMENT(Tab.PLAYING, Simple.SettingsPart.ENCOURAGEMENT),
        COACH_VOICE(Tab.PLAYING, Simple.SettingsPart.ENCOURAGEMENT),
        WRONG_KEYS(Tab.PLAYING, Simple.SettingsPart.WRONG_KEYS),
        REMINDER(Tab.PLAYING, Simple.SettingsPart.REMINDER),
        MIDI_TIMING(Tab.PLAYING, null),
        // Look: how the app and the falling notes look.
        LAYOUT(Tab.LOOK, Simple.SettingsPart.LAYOUT),
        PLAIN_LOOK(Tab.LOOK, Simple.SettingsPart.PLAIN_LOOK),
        KEYS(Tab.LOOK, Simple.SettingsPart.KEYS),
        FALLING_NOTES(Tab.LOOK, Simple.SettingsPart.FALLING_NOTES),
        // Sound: what the app plays and says, and what it hears.
        SOUND(Tab.SOUND, Simple.SettingsPart.SOUND),
        VOICE(Tab.SOUND, Simple.SettingsPart.VOICE),
        MICROPHONE(Tab.SOUND, Simple.SettingsPart.MICROPHONE),
        // Family: everyone on the phone, their briefs, and the file that carries them all.
        FAMILY(Tab.FAMILY, Simple.SettingsPart.FAMILY),
        BRIEFS(Tab.FAMILY, Simple.SettingsPart.BRIEFS),
        PROGRESS(Tab.FAMILY, Simple.SettingsPart.PROGRESS),
        // About: the app, privacy and licences, and Roy's testing.
        ABOUT(Tab.ABOUT, Simple.SettingsPart.ABOUT),
        TESTING(Tab.ABOUT, Simple.SettingsPart.TESTING),
    }

    /**
     * What this Settings screen has to draw with: [family] the Family block (and so Briefs and Testing), [mic] the microphone
     * (Show wrong keys), [midi] the MIDI keyboard's timing, [testing] a build with Help Roy test or the diary, [coach] a build with
     * the coach voice (src/afterdark).
     */
    data class Has(val family: Boolean = true, val mic: Boolean = true, val midi: Boolean = true,
                   val testing: Boolean = true, val coach: Boolean = false)

    /** Whether [item] shows for [audience] ([owner]: the phone's owner) in [layout] (already widened by "Show all settings"). */
    fun shows(item: Item, audience: Audience, owner: Boolean, layout: Layout, has: Has): Boolean {
        if (item.part != null && !Simple.settingsShows(item.part, layout)) return false
        return when (item) {
            Item.LAYOUT -> audience == Audience.GROWN_UP                       // a kid never has the layout
            Item.PLAIN_LOOK -> PlainLook.offered(audience)                     // grown-ups and big kids
            Item.COACH_VOICE -> has.coach && audience == Audience.GROWN_UP      // CoachGate.offered: an adult's profile only
            Item.REMINDER -> owner                                             // one alarm per phone, the owner's
            Item.WRONG_KEYS -> has.mic
            Item.MIDI_TIMING -> has.midi
            Item.FAMILY, Item.BRIEFS -> has.family
            Item.TESTING -> has.family && has.testing
            else -> true
        }
    }

    /** The blocks [tab] draws for this person, in order. */
    fun items(tab: Tab, audience: Audience, owner: Boolean, layout: Layout, has: Has): List<Item> =
        Item.entries.filter { it.tab == tab && shows(it, audience, owner, layout, has) }

    /** The tabs with something to show for this person, in order (never empty: Sound shows in every layout). */
    fun tabs(audience: Audience, owner: Boolean, layout: Layout, has: Has): List<Tab> =
        Tab.entries.filter { items(it, audience, owner, layout, has).isNotEmpty() }

    /** The tab to open: the one asked for (the last one this session, or the debug route's) when it shows, else the first. */
    fun pick(wanted: Tab?, shown: List<Tab>): Tab = wanted?.takeIf { it in shown } ?: shown.first()

    /** A tab by its name or label, any case (`--es tab sound`); null for anything else. */
    fun parse(s: String?): Tab? = Tab.entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) || it.label.equals(s?.trim(), ignoreCase = true) }

}
