package com.bigpapituna.sharpears.engine.nav

/**
 * The grown-ups' navigation since 2.7 (CLAUDE.md "2.7.0 navigation"): five tabs, in rail and bar order. A kid's home is
 * the pup's map (ui/kid), never these tabs. ui/HomeShell.kt's HomeTab adds the icons.
 */
enum class NavTab(val label: String) {
    TODAY("Today"), SONGS("Songs"), LEARN("Learn"), PRACTICE("Practice"), YOU("You"),
}

/**
 * One name per place, fixed in 2.7 so a screen, a tile, a content description and the tour never call it two things:
 * the home is "Today", "Songs" is the Songs tab, the chord games are "Games", the Jam, Pup says and Clap along are the
 * "Play corner".
 */
object Names {
    const val TODAY = "Today"
    const val SONGS = "Songs"
    const val SONGS_BY_CHORDS = "Songs by chords"
    const val PLAY_CORNER = "Play corner"
    const val LEARN_CHORDS = "Learn chords"
    const val GAMES = "Games"
    const val PROGRESSIONS = "Progressions"
    const val TOOLS = "Tools"
}

/**
 * Every screen of MainActivity's `Screen` (by class name; the home as its tab, "Home.TODAY", or "KidHome" for a kid) and
 * the screens it opens, so NavTest can check that nothing is left without a way in: every screen is reached from the five
 * tabs, the kid's home, or the launch (the first-run questions and "Let's hear you play"). Kept by hand next to
 * MainActivity: NavTest also reads MainActivity's source, so a new Screen that is missing here fails the build's tests.
 */
object NavGraph {
    const val LAUNCH = "Launch"
    const val KID_HOME = "KidHome"
    fun tab(t: NavTab) = "Home.${t.name}"

    val OPENS: Map<String, Set<String>> = mapOf(
        // Today: Start walks Practice today; Continue opens the last song, song by chords or lesson; Explore opens Songs,
        // Learn (its chords at the top) and the Play corner; the mic banner opens Microphone setup; the quest opens where
        // each task is done.
        tab(NavTab.TODAY) to setOf("Daily", "Player", "PopSong", "Lesson", "MicSetup", tab(NavTab.SONGS), tab(NavTab.LEARN), "Play", "LearnChords"),
        tab(NavTab.SONGS) to setOf("Player", "PopSong", "Play"),
        tab(NavTab.LEARN) to setOf("Lesson", "LearnChords", "ChordGames", "Progressions", "Theory", "EarTraining", "SightRead"),
        tab(NavTab.PRACTICE) to setOf("Player"),
        // You: Settings from the gear; the world card; the family wall opens the app as someone else (the launch again).
        tab(NavTab.YOU) to setOf("Settings", "World", LAUNCH),
        "Settings" to setOf("MicSetup", "Privacy", "Licences"),
        "Privacy" to setOf("MicSetup"),
        "MicSetup" to setOf("Player"),
        "LearnChords" to setOf("Player", "PopSong", tab(NavTab.SONGS)),
        "Progressions" to setOf("Player"),
        "PopSong" to setOf("Player"),
        "World" to setOf("Player"),
        "Lesson" to setOf("Player", "Lesson", "Play", tab(NavTab.SONGS), "KidSongs", "Finale"),
        "Daily" to setOf("Play", tab(NavTab.SONGS), "KidSongs"),
        KID_HOME to setOf("Lesson", "Play", "KidSongs", "KidStickers", "Settings", "MicSetup", "Finale", LAUNCH),
        "KidSongs" to setOf("Player", "KidChords"),
        "KidChords" to setOf("PopSong"),
        "KidStickers" to setOf("Player", KID_HOME),
        "Finale" to setOf(KID_HOME),
        LAUNCH to setOf("Onboarding", "Placement", "Lesson", tab(NavTab.TODAY), KID_HOME),
        "Onboarding" to setOf("Placement", "Lesson", tab(NavTab.SONGS), tab(NavTab.LEARN)),
        "Placement" to setOf("Lesson", tab(NavTab.SONGS), tab(NavTab.LEARN), KID_HOME),
        "Player" to emptySet(), "ChordGames" to emptySet(), "Theory" to emptySet(), "EarTraining" to emptySet(),
        "SightRead" to emptySet(), "Licences" to emptySet(), "Play" to emptySet(),
    )

    /** Every place [from] leads to, [from] included, never going through [avoid] (the launch, say). */
    fun reachable(from: Collection<String>, avoid: Set<String> = emptySet()): Set<String> {
        val seen = LinkedHashSet<String>(from)
        val todo = ArrayDeque(from)
        while (todo.isNotEmpty()) OPENS[todo.removeFirst()].orEmpty().forEach { if (it !in avoid && seen.add(it)) todo.addLast(it) }
        return seen
    }
}
