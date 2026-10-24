package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.GameKind
import com.bigpapituna.sharpears.engine.LessonItem
import com.bigpapituna.sharpears.engine.LessonPack
import com.bigpapituna.sharpears.engine.PROGRESSIONS
import com.bigpapituna.sharpears.engine.SONG_PACK_PREFIX
import com.bigpapituna.sharpears.engine.chords.POP_SONGS
import com.bigpapituna.sharpears.engine.chords.PopSong
import com.bigpapituna.sharpears.engine.chords.PopSongs
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.nav.Simple.Place
import com.bigpapituna.sharpears.engine.nav.Simple.PlayerControl
import com.bigpapituna.sharpears.engine.nav.Simple.PracticePart
import com.bigpapituna.sharpears.engine.nav.Simple.SettingsPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.8, the three layouts (2.7's SimpleTest): who is asked, 2.7's switch carried over, and what each place shows in Simple,
 * Advanced and Full, one row per screen part per layout. Simple is exactly 2.7's Simple; Full is 2.7's Advanced (everything).
 */
class LayoutTest {
    private val s = Layout.SIMPLE
    private val a = Layout.ADVANCED
    private val f = Layout.FULL

    // ---- choosing ----

    @Test fun beginnersStartSimpleTheChoiceWinsAndAKidIsNeverCutOrAsked() {
        assertEquals(s, Simple.default(Level.NEVER))
        assertEquals(s, Simple.default(Level.A_LITTLE))
        assertEquals(f, Simple.default(Level.SOME))
        assertEquals(f, Simple.default(Level.A_LOT))
        assertEquals(s, Simple.layoutOf(kid = false, chosen = null, level = Level.NEVER))
        assertEquals(f, Simple.layoutOf(kid = false, chosen = null, level = Level.A_LOT))
        for (l in Layout.entries) for (lv in Level.entries) assertEquals("the choice wins", l, Simple.layoutOf(kid = false, chosen = l, level = lv))
        for (lv in Level.entries) for (c in listOf(null) + Layout.entries) {
            assertEquals("a kid is never cut", f, Simple.layoutOf(kid = true, chosen = c, level = lv))
            assertFalse("a kid is never asked", Simple.asks(kid = true, chosen = c))
        }
        assertTrue("a grown-up who never chose is asked", Simple.asks(kid = false, chosen = null))
        for (l in Layout.entries) assertFalse(Simple.asks(kid = false, chosen = l))
    }

    @Test fun theSimpleSwitchCarriesOverAndTheWireNamesRoundTrip() {
        assertEquals(s, Layout.fromSimpleMode(true))
        assertEquals(f, Layout.fromSimpleMode(false))                  // 2.7's Advanced was the whole app
        assertEquals(listOf("simple", "advanced", "full"), Layout.entries.map { it.wire })
        for (l in Layout.entries) assertEquals(l, Layout.parse(l.wire))
        assertEquals(a, Layout.parse(" Advanced "))
        assertNull(Layout.parse("unset"))
        assertNull(Layout.parse(null))
    }

    @Test fun showMoreShowsAPlaceInFullOnce() {
        for (l in Layout.entries) {
            assertEquals(f, Simple.shown(l, more = true))
            assertEquals(l, Simple.shown(l, more = false))
        }
    }

    /** Which places each layout cuts (each has its "Show more"): Simple every one, Advanced the chord and player extras and Settings, Full none. */
    @Test fun whatEachLayoutCuts() {
        val rows = mapOf(                         //    Simple, Advanced, Full
            Place.LEARN to listOf(true, false, false),
            Place.LEARN_CHORDS to listOf(true, true, false),
            Place.GAMES to listOf(true, true, false),
            Place.PROGRESSIONS to listOf(true, true, false),
            Place.SONGS_BY_CHORDS to listOf(true, false, false),
            Place.SONG_PATTERNS to listOf(true, true, false),
            Place.PRACTICE to listOf(true, false, false),
            Place.PLAYER to listOf(true, true, false),
            Place.SETTINGS to listOf(true, true, false),
        )
        assertEquals(Place.entries.toSet(), rows.keys)
        for ((p, want) in rows) for ((i, l) in Layout.entries.withIndex()) assertEquals("$p in $l", want[i], Simple.cuts(p, l))
    }

    /** Each place's rule agrees with [Simple.cuts]: a place cut in a layout shows less there, one not cut shows all of it. */
    @Test fun eachRuleCutsExactlyWhereItsPlaceSays() {
        for (l in Layout.entries) {
            assertEquals("$l", Simple.cuts(Place.LEARN, l), !Simple.learnSideRows(l))
            assertEquals("$l", Simple.cuts(Place.LEARN_CHORDS, l), Simple.chords(l) != null)
            assertEquals("$l", Simple.cuts(Place.GAMES, l), Simple.games(GameKind.entries, l) != GameKind.entries.toList())
            assertEquals("$l", Simple.cuts(Place.PROGRESSIONS, l), Simple.progressions(PROGRESSIONS, l) != PROGRESSIONS)
            assertEquals("$l", Simple.cuts(Place.SONGS_BY_CHORDS, l), Simple.popSongs(POP_SONGS, l) != POP_SONGS)
            assertEquals("$l", Simple.cuts(Place.SONG_PATTERNS, l), Simple.patterns(PopSong.Pattern.entries, l) != PopSong.Pattern.entries.toList())
            assertEquals("$l", Simple.cuts(Place.PRACTICE, l), !PracticePart.entries.all { Simple.practiceShows(it, l) })
            assertEquals("$l", Simple.cuts(Place.PLAYER, l), !PlayerControl.entries.all { Simple.inBar(it, l) })
            assertEquals("$l", Simple.cuts(Place.SETTINGS, l), !SettingsPart.entries.all { Simple.settingsShows(it, l) })
        }
    }

    // ---- Learn chords ----

    @Test fun learnChordsKeepsTheSixChordsUntilFullAndAdvancedKeepsTheSongPacksWhole() {
        assertEquals(listOf("C", "F", "G", "Am", "Dm", "Em"), Simple.CHORDS.map { it.name })
        assertEquals(Simple.CHORDS, Simple.chords(s))
        assertEquals(Simple.CHORDS, Simple.chords(a))
        assertNull("every chord", Simple.chords(f))
        fun pack(id: String, vararg chords: Chord) = LessonPack(id, id, 1, emptyList(), chords.map { LessonItem.ChordItem(it) })
        val major = pack("major", Chord(0, ChordType.MAJOR), Chord(5, ChordType.MAJOR), Chord(7, ChordType.MAJOR), Chord(2, ChordType.MAJOR), Chord(10, ChordType.MAJOR))
        val sevenths = pack("sevenths", Chord(0, ChordType.MAJ7), Chord(7, ChordType.DOM7))
        val song = pack(SONG_PACK_PREFIX + "minuet-g", Chord(7, ChordType.MAJOR), Chord(2, ChordType.DOM7), Chord(4, ChordType.MINOR))
        val progressions = LessonPack("prog", "prog", 4, emptyList(), listOf(LessonItem.ProgressionItem(0, 0)))
        val all = listOf(major, sevenths, song, progressions)
        fun names(p: LessonPack) = p.items.map { (it as LessonItem.ChordItem).chord.name }
        // Simple: each pack cut to the six, the empty ones gone (2.7).
        val simple = Simple.packs(all, s)
        assertEquals(listOf("major", "song-minuet-g"), simple.map { it.id })
        assertEquals(listOf("C", "F", "G"), names(simple[0]))
        assertEquals(listOf("G", "Em"), names(simple[1]))
        // Advanced: the same cut, but a song's own chords stay whole.
        val advanced = Simple.packs(all, a)
        assertEquals(listOf("major", "song-minuet-g"), advanced.map { it.id })
        assertEquals(listOf("C", "F", "G"), names(advanced[0]))
        assertEquals(listOf("G", "D7", "Em"), names(advanced[1]))
        // Full: every pack as written.
        assertEquals(all, Simple.packs(all, f))
    }

    // ---- Games, Progressions ----

    @Test fun gamesAddOneAGameALayout() {
        assertEquals(listOf(GameKind.BUILD, GameKind.HEAR), Simple.games(GameKind.entries, s))
        assertEquals(listOf(GameKind.BUILD, GameKind.HEAR, GameKind.MAJOR_MINOR), Simple.games(GameKind.entries, a))
        assertEquals(GameKind.entries.toList(), Simple.games(GameKind.entries, f))
    }

    @Test fun progressionsKeepThePopOnesUntilFull() {
        val pop = listOf("I-V-vi-IV (pop)", "I-vi-IV-V (50s)")
        assertEquals(pop, Simple.progressions(PROGRESSIONS, s).map { it.name })
        assertEquals(pop, Simple.progressions(PROGRESSIONS, a).map { it.name })
        assertEquals(PROGRESSIONS, Simple.progressions(PROGRESSIONS, f))
        assertTrue(Simple.progressions(PROGRESSIONS, s).all { p -> p.degrees.all { it.type == ChordType.MAJOR || it.type == ChordType.MINOR } })
    }

    // ---- Songs by chords ----

    /** 2.8.4, QA D6: Simple listed 44 songs, Perfect in A flat and The Scientist (difficulty 4) among them. */
    @Test fun songsByChordsKeepTheEasySongsInEasyKeysInSimpleOnly() {
        val kept = Simple.popSongs(POP_SONGS, s)
        println("Simple songs by chords: ${kept.size} of ${POP_SONGS.size}: " + kept.joinToString { "${it.title} (${it.key}${if (it.minor) "m" else ""}, ${it.difficulty})" })
        assertTrue(kept.size >= 8 && kept.size < POP_SONGS.size)
        assertTrue(kept.all { it.difficulty <= 2 && Simple.easyKey(it) })
        assertTrue(POP_SONGS.filter { it !in kept }.none { it.difficulty <= 2 && Simple.easyKey(it) })
        for (id in listOf("perfect", "the-scientist", "someone-you-loved")) assertTrue(id, kept.none { it.id == id })
        // The keys: C, G and F major; A, E and D minor. A flat, D flat and the rest wait for Advanced.
        val keys = kept.map { if (it.minor) "${it.key}m" else "${it.key}" }.toSet()
        assertTrue(keys.toString(), keys.all { it in setOf("0", "7", "5", "9m", "4m", "2m") })
        assertEquals(POP_SONGS, Simple.popSongs(POP_SONGS, a))
        assertEquals(POP_SONGS, Simple.popSongs(POP_SONGS, f))
    }

    @Test fun songPagesOfferTwoPatternsThenFourThenSeven() {
        val all = PopSong.Pattern.entries
        assertEquals(listOf(PopSong.Pattern.WHOLE, PopSong.Pattern.HALVES), Simple.patterns(all, s))
        assertEquals(listOf(PopSong.Pattern.WHOLE, PopSong.Pattern.HALVES, PopSong.Pattern.QUARTERS, PopSong.Pattern.BROKEN), Simple.patterns(all, a))
        assertEquals(all.toList(), Simple.patterns(all, f))
        // A song that suggests a pattern the layout hides opens on Halves.
        assertEquals(PopSong.Pattern.HALVES, Simple.openingPattern(PopSong.Pattern.QUARTERS, s))
        assertEquals(PopSong.Pattern.QUARTERS, Simple.openingPattern(PopSong.Pattern.QUARTERS, a))
        assertEquals(PopSong.Pattern.HALVES, Simple.openingPattern(PopSong.Pattern.SHUFFLE, a))
        assertEquals(PopSong.Pattern.SHUFFLE, Simple.openingPattern(PopSong.Pattern.SHUFFLE, f))
        assertEquals(PopSong.Pattern.WHOLE, Simple.openingPattern(PopSong.Pattern.WHOLE, s))
    }

    // ---- Practice ----

    @Test fun practiceKeepsThreeMajorScalesAndOneWarmUpInSimpleOnly() {
        assertEquals(listOf(0, 7, 5), Simple.SCALE_ROOTS)        // C, G, F
        assertEquals(listOf("a"), Simple.warmups(listOf("a", "b", "c")))
        assertEquals(setOf(PracticePart.SCALES, PracticePart.WARMUPS), PracticePart.entries.filter { Simple.practiceShows(it, s) }.toSet())
        assertTrue(PracticePart.entries.all { Simple.practiceShows(it, a) })
        assertTrue(PracticePart.entries.all { Simple.practiceShows(it, f) })
    }

    // ---- My songs ----

    /** My songs is the learner's own list (2.8.1): it and its Add button show in Simple, Advanced and Full alike, with no "Show more". */
    @Test fun mySongsAndItsAddButtonShowInEveryLayout() {
        val rows = mapOf(                         //    Simple, Advanced, Full
            Simple.SongsPart.MY_SONGS to listOf(true, true, true),
            Simple.SongsPart.ADD_SONG to listOf(true, true, true),
        )
        assertEquals(Simple.SongsPart.entries.toSet(), rows.keys)
        for ((p, want) in rows) for ((i, l) in Layout.entries.withIndex()) assertEquals("$p in $l", want[i], Simple.songsShows(p, l))
    }

    // ---- Learn ----

    @Test fun learnKeepsThePathAndShowsTheSideRowsFromAdvanced() {
        assertFalse(Simple.learnSideRows(s))
        assertTrue(Simple.learnSideRows(a))
        assertTrue(Simple.learnSideRows(f))
    }

    // ---- the player ----

    @Test fun thePlayerKeepsModesHandsAndPlayAndAdvancedBringsBackLoopAndSpeed() {
        val always = listOf(PlayerControl.MODE, PlayerControl.HANDS, PlayerControl.PLAY, PlayerControl.TIMELINE, PlayerControl.MIC)
        val simpleBar = PlayerControl.entries.filter { Simple.inBar(it, s) }.toSet()
        assertTrue(simpleBar.containsAll(always))
        assertEquals(setOf(PlayerControl.LOOP, PlayerControl.SPEED, PlayerControl.TRANSPOSE, PlayerControl.METRONOME, PlayerControl.CHORDS, PlayerControl.DUET),
            PlayerControl.entries.toSet() - simpleBar)
        val advancedBar = PlayerControl.entries.filter { Simple.inBar(it, a) }.toSet()
        assertTrue(advancedBar.containsAll(always + PlayerControl.LOOP + PlayerControl.SPEED))
        assertEquals(setOf(PlayerControl.TRANSPOSE, PlayerControl.METRONOME, PlayerControl.CHORDS, PlayerControl.DUET), PlayerControl.entries.toSet() - advancedBar)
        assertTrue(PlayerControl.entries.all { Simple.inBar(it, f) })
    }

    // ---- Settings ----

    @Test fun settingsShowTheEssentialsThenThePlayingOnesThenAll() {
        val essentials = setOf(SettingsPart.FAMILY, SettingsPart.LAYOUT, SettingsPart.SOUND, SettingsPart.VOICE, SettingsPart.REMINDER,
            SettingsPart.MICROPHONE, SettingsPart.TESTING, SettingsPart.BRIEFS)
        assertEquals(essentials, SettingsPart.entries.filter { Simple.settingsShows(it, s) }.toSet())
        assertEquals(essentials + setOf(SettingsPart.KEYS, SettingsPart.FALLING_NOTES, SettingsPart.WRONG_KEYS, SettingsPart.BRIEFS),
            SettingsPart.entries.filter { Simple.settingsShows(it, a) }.toSet())
        assertTrue(SettingsPart.entries.all { Simple.settingsShows(it, f) })
    }

    // ---- the words ----

    /**
     * The copy rules (CLAUDE.md design system): no dashes or symbol glyphs; sentence case (a capital only to start a sentence
     * or for a layout or place name); lines end with a stop, the questions with their mark.
     */
    @Test fun theCopyKeepsTheRules() {
        val names = setOf("Simple", "Advanced", "Full", "You", "Settings")
        val lines = Layout.entries.flatMap { listOf(it.title, it.line) } + listOf(Layout.QUESTION, Layout.NOTE, Simple.SHOW_MORE,
            Simple.SHOW_ALL_SETTINGS, Simple.SWITCH_QUESTION, Simple.SWITCH_LINE, Simple.SETTING_TITLE, Simple.SETTING_LINE)
        for (l in lines) {
            assertFalse(l, Regex("[—–·>]| - ").containsMatchIn(l))
            val words = l.split(" ")
            for (i in 1 until words.size) {
                val w = words[i].trim('.', ',', ':', '?')
                val startsSentence = words[i - 1].endsWith(".") || words[i - 1].endsWith(":")
                if (w.firstOrNull()?.isUpperCase() == true) assertTrue("$l: $w", startsSentence || w in names)
            }
        }
        (Layout.entries.map { it.line } + listOf(Layout.NOTE, Simple.SWITCH_LINE, Simple.SETTING_LINE)).forEach { assertTrue(it, it.endsWith(".")) }
        assertTrue(Layout.QUESTION.endsWith("?") && Simple.SWITCH_QUESTION.endsWith("?"))
        assertEquals("How much do you want on screen?", Layout.QUESTION)
        assertEquals("Change any time on You or in Settings.", Layout.NOTE)
        // Each card starts with Roy's words for it.
        assertTrue(Layout.SIMPLE.line.startsWith("The first steps"))
        assertTrue(Layout.ADVANCED.line.startsWith("Lessons, songs and practice"))
        assertTrue(Layout.FULL.line.startsWith("Everything"))
        // The tour's third card and What's new name all three.
        for (n in listOf("Simple", "Advanced", "Full")) {
            assertTrue(n, n in Welcome.TOUR[2].line)
            assertTrue(n, Welcome.whatsNew("2.8.0").any { n in it })
        }
    }
}
