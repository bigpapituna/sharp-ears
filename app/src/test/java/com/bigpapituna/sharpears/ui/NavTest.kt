package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.engine.nav.NavGraph
import com.bigpapituna.sharpears.engine.nav.NavTab
import com.bigpapituna.sharpears.engine.nav.Names
import com.bigpapituna.sharpears.ui.play.Toy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        assertEquals(setOf("Onboarding", "Placement", "FirstKid"), all - kids - fromTabs)
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

    /**
     * 2.8 (Roy: "Play corner." did nothing): every tile Today draws is named by a [Names] constant, has its place in
     * [NavGraph.TODAY_TILES], and that place is one Today opens in the graph; no tile's tap is empty, and MainActivity
     * wires each tile's action to a screen. The Play corner's sheet always has its three toys (Simple filters none).
     */
    @Test fun everyNamedTileOnTodayOpensAPlace() {
        val today = File("src/main/java/com/bigpapituna/sharpears/ui/TodayScreen.kt").readText().replace("\r\n", "\n")
        val tiles = Regex("""(?<!fun )ExploreTile\(([^,]+),""").findAll(today).map { it.groupValues[1].trim() }.toList()
        assertTrue("Today draws its Explore tiles: $tiles", tiles.size >= 3)
        val opens = NavGraph.OPENS.getValue(NavGraph.tab(NavTab.TODAY))
        val names = tiles.map { t ->
            assertTrue("Today's tile $t is named by a Names constant, not a literal", t.startsWith("Names."))
            val name = Names::class.java.getField(t.removePrefix("Names.")).get(null) as String
            val place = NavGraph.TODAY_TILES[name]
            assertNotNull("Today's tile \"$name\" has no place in NavGraph.TODAY_TILES", place)
            assertTrue("Today's tile \"$name\" opens $place, which the graph does not have Today open", place in opens)
            name
        }
        assertEquals("every tile in TODAY_TILES is drawn, once", NavGraph.TODAY_TILES.keys.toList().sorted(), names.sorted())
        assertTrue("an Explore tile with an empty tap", Regex("""ExploreTile\([^\n]*\)\s*\{\s*\}""").find(today) == null)
        assertTrue("the Play corner tile opens its sheet", Regex("""ExploreTile\(Names\.PLAY_CORNER[^\n]*\{[^}]*playCorner = true""").containsMatchIn(today))
        for (call in listOf("onSongs = { screen = Screen.Home(HomeTab.SONGS) }", "onChords = { screen = Screen.Home(HomeTab.LEARN) }",
            "onToy = { t -> screen = Screen.Play(t, s) }"))
            assertTrue("MainActivity's Today actions: $call", call in activity)
        assertEquals(3, Toy.entries.size)
        assertTrue("PlayRow draws every toy", "Toy.entries.forEach" in File("src/main/java/com/bigpapituna/sharpears/ui/play/PlayRow.kt").readText())
    }

    /**
     * 2.8, the launch chooser: part of the grown-up home, not a Screen of its own (so the graph above is unchanged). A kid's home
     * comes first, so a kid never meets it; a grown-up who never chose a layout sees it in place of the tabs, so before Today,
     * on a launch, a profile switch or a return from the first lesson alike. What's new and the tour wait under the tabs.
     */
    @Test fun theLayoutChooserComesBeforeTodayForAGrownUpWhoNeverChose() {
        // 2.14: the map is a little kid's home; a big kid gets the tabs, never the chooser (Simple.asks is false for every kid).
        val kidHome = activity.indexOf("is Screen.Home -> if (current.mapHome) {")
        // 2.17 (audit M2): a beginner is asked only after two lessons (FirstRun.asksLayout, Simple.asks inside it).
        val chooser = activity.indexOf("} else if (com.bigpapituna.sharpears.engine.nav.FirstRun.asksLayout(current.isKid, chosenLayout, current.level,")
        val tabs = activity.indexOf("HomeShell(s.tab,")
        val welcome = activity.indexOf("Welcome.next(current.isKid")
        assertTrue("$kidHome $chooser $tabs $welcome", kidHome in 1 until chooser && chooser < tabs && tabs < welcome)
        val here = activity.substring(chooser, tabs)
        assertTrue("LayoutChooser(" in here && "layoutMode.set(l)" in here)
        assertTrue("LayoutChooser" !in screens)
    }

    /** The debug routes keep working (CLAUDE.md lists them), with the three 2.7 adds. */
    @Test fun theDebugRoutesOpenTheNewPlaces() {
        for (r in listOf("today", "songs", "learnchords", "games", "progressions", "chords", "popsongs", "learn", "practice", "you", "settings"))
            assertTrue("--es screen $r", "route == \"$r\"" in activity)
    }
}
