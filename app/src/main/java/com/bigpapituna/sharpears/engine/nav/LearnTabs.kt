package com.bigpapituna.sharpears.engine.nav

/**
 * 2.18.3, Learn in tabs (Roy: "easier tabs where you could press different learning modes, such as theory, ear training, sight
 * read, and also the current main learning road"). Four tabs across the top of Learn: Road (the course path, the default),
 * Theory (the academy's path), Ear training (the workout and the games) and Sight reading (the levels). They were three rows
 * under the Chords section before, easy to scroll past.
 *
 * Who sees what is the rule the side rows had, moved, not changed: a kid who cannot read yet has the road only (the pup's world
 * is the whole path for them), and the layout's [Simple.learnSideRows] cuts the other three in Simple (its "Show more" brings
 * them). With the road alone there is no tab row at all.
 *
 * Pure functions (LearnTabsTest); ui/LearnScreen.kt asks them. The last tab is kept per profile (Prefs.learnTab).
 */
object LearnTabs {
    enum class Tab(val label: String) {
        ROAD("Road"), THEORY("Theory"), EAR("Ear training"), SIGHT("Sight reading");

        /** The preference's and the debug route's spelling (`--es screen learn --es tab ear`). */
        val wire: String get() = when (this) { ROAD -> "road"; THEORY -> "theory"; EAR -> "ear"; SIGHT -> "sight" }
    }

    /** The tabs this person has, in order: always the road; the other three for grown-ups and readers in a layout with them. */
    fun tabs(kid: Boolean, reader: Boolean, layout: Layout): List<Tab> =
        if ((!kid || reader) && Simple.learnSideRows(layout)) Tab.entries else listOf(Tab.ROAD)

    /** The tab to open: the one asked for (the last one used, or the debug route's) when this person has it, else the road. */
    fun pick(wanted: Tab?, shown: List<Tab>): Tab = wanted?.takeIf { it in shown } ?: Tab.ROAD

    /** A tab by its wire name, enum name or label, any case ("ear", "EAR", "Ear training"); null for anything else. */
    fun parse(s: String?): Tab? {
        val t = s?.trim() ?: return null
        return Tab.entries.firstOrNull { it.wire.equals(t, true) || it.name.equals(t, true) || it.label.equals(t, true) }
    }
}
