package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.engine.nav.NavGraph
import com.bigpapituna.sharpears.engine.nav.NavTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.7, the grown-ups' navigation: five tabs (Today, Songs, Learn, Practice, You), and no screen left without a way in.
 * MainActivity's screens are private composable state, so this reads its source: every `Screen` it declares is in the
 * navigation graph (engine/nav/Nav.kt), reached from the five tabs, the kid's map or the launch, and opened somewhere other
 * than a debug route or a restored route; every route a recreate saves is one it can restore.
 */
class NavTest {
    private val activity = File("src/main/java/com/bigpapituna/sharpears/MainActivity.kt").readText().replace("\r\n", "\n")

    /** The Screen classes MainActivity declares, in its sealed interface. */
    private val screens: List<String> = activity.substringAfter("private sealed interface Screen {").substringBefore("\n}\n").let { body ->
        Regex("""\n    data (?:class|object) (\w+)""").findAll(body).map { it.groupValues[1] }.toList()
    }

    /** The source a person reaches screens through: without the debug routes' block and the restore of a saved route. */
    private val ways: String = activity.let { s ->
        val debugStart = s.indexOf("val first: Screen = runCatching { when {")
        val debugEnd = s.indexOf("}.getOrElse { Log.d(TAG, \"debug route failed")
        val restoreStart = s.indexOf("private fun screenAt(")
        val restoreEnd = s.indexOf("override fun onStart()")
        assertTrue(debugStart in 1 until debugEnd && restoreStart in 1 until restoreEnd)
        s.substring(0, debugStart) + s.substring(debugEnd, restoreStart) + s.substring(restoreEnd)
    }

    @Test fun theFiveTabsAreTodaySongsLearnPracticeYou() {
        assertEquals(listOf("Today", "Songs", "Learn", "Practice", "You"), NavTab.entries.map { it.label })
        assertEquals(NavTab.entries.toList(), HomeTab.entries.map { it.nav })
        assertEquals(listOf("Today", "Songs", "Learn", "Practice", "You"), HomeTab.entries.map { it.label })
        // The grown-up home opens on Today; the Chords tab is gone.
        assertTrue("data class Home(val tab: HomeTab = HomeTab.TODAY" in activity)
        assertTrue("HomeTab.CHORDS" !in activity && "ChordsTab" !in activity)
    }

    @Test fun everyScreenIsInTheGraph() {
        assertTrue("found ${screens.size}", screens.size >= 20 && "Home" in screens && "LearnChords" in screens)
        val nodes = NavGraph.OPENS.keys + NavGraph.OPENS.values.flatten()
        for (s in screens - "Home") assertTrue("$s is not in engine/nav NavGraph", s in nodes)
        for (t in NavTab.entries) assertTrue(NavGraph.tab(t) in NavGraph.OPENS)
        assertTrue(NavGraph.KID_HOME in NavGraph.OPENS)
        // The graph names no screen MainActivity lost.
        val known = screens.toSet() - "Home" + NavTab.entries.map(NavGraph::tab) + NavGraph.KID_HOME + NavGraph.LAUNCH
        for (n in nodes) assertTrue("NavGraph names $n, which MainActivity has no Screen for", n in known)
    }

    @Test fun everyScreenIsReachedFromTheTabsTheKidsMapOrTheLaunch() {
        val all = screens.toSet() - "Home" + NavTab.entries.map(NavGraph::tab) + NavGraph.KID_HOME
        val reached = NavGraph.reachable(NavTab.entries.map(NavGraph::tab) + NavGraph.KID_HOME + NavGraph.LAUNCH)
        assertEquals(emptySet<String>(), all - reached)
        // A grown-up reaches every screen but the kids' own from the five tabs alone, without opening the app as someone else;
        // the first-run questions and "Let's hear you play" come with the launch.
        val kids = setOf("KidSongs", "KidChords", "KidStickers", "Finale", "Arcade", "ArcadeRound", NavGraph.KID_HOME)
        val fromTabs = NavGraph.reachable(NavTab.entries.map(NavGraph::tab), avoid = setOf(NavGraph.LAUNCH))
        assertEquals(setOf("Onboarding", "Placement"), all - kids - fromTabs)
        // 2.7's moves: chords at the top of Learn, songs by chords and the Play corner in Songs, Practice today from Today.
        assertTrue(NavGraph.OPENS.getValue(NavGraph.tab(NavTab.LEARN)).containsAll(listOf("LearnChords", "ChordGames", "Progressions")))
        assertTrue(NavGraph.OPENS.getValue(NavGraph.tab(NavTab.SONGS)).containsAll(listOf("PopSong", "Play")))
        assertTrue("Daily" in NavGraph.OPENS.getValue(NavGraph.tab(NavTab.TODAY)))
    }

    /** No orphan screens: each one is opened by the app itself, not only by a debug route or a restored route. */
    @Test fun everyScreenIsOpenedOutsideTheDebugRoutes() {
        for (s in screens - "Home") {
            val opened = Regex("""(?<!is )Screen\.$s\b(?!\s*->)""").findAll(ways).any()
            assertTrue("nothing outside the debug routes opens Screen.$s", opened)
        }
        // The Chords section's three tiles and Today's way to Learn chords (the quest's chords task).
        for (call in listOf("screen = Screen.LearnChords(s)", "screen = Screen.ChordGames(s)", "screen = Screen.Progressions(s)"))
            assertTrue(call, call in ways)
    }

    /** A recreate saves the open screen as a route (routeOf) and reopens it (screenAt): every part routeOf writes is read back. */
    @Test fun everySavedRouteIsRestored() {
        val routeOf = activity.substringAfter("private fun routeOf(s: Screen): String = when (s) {").substringBefore("\n}\n")
        val written = Regex("""/(\w+)""").findAll(routeOf).map { it.groupValues[1] }.toSet() +
            Regex(""""(\w+):""").findAll(routeOf).map { it.groupValues[1] }.toSet() + "onboarding"
        val screenAt = activity.substringAfter("private fun screenAt(").substringBefore("override fun onStart()")
        val read = Regex(""""(\w+)" ->""").findAll(screenAt).map { it.groupValues[1] }.toSet()
        assertEquals(emptySet<String>(), written - read)
        assertTrue(setOf("learnchords", "chordgames", "progressions").all { it in read })
        // The Songs tab's section survives a recreate ("home:SONGS:chords").
        assertTrue("\":chords\"" in routeOf && "== \"chords\"" in screenAt)
    }

    /** The debug routes keep working (CLAUDE.md lists them), with the three 2.7 adds. */
    @Test fun theDebugRoutesOpenTheNewPlaces() {
        for (r in listOf("today", "songs", "learnchords", "games", "progressions", "chords", "popsongs", "learn", "practice", "you", "settings"))
            assertTrue("--es screen $r", "route == \"$r\"" in activity)
    }
}
