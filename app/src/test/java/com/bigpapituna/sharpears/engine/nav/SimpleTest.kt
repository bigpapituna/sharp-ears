package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.GameKind
import com.bigpapituna.sharpears.engine.LessonItem
import com.bigpapituna.sharpears.engine.LessonPack
import com.bigpapituna.sharpears.engine.PROGRESSIONS
import com.bigpapituna.sharpears.engine.chords.POP_SONGS
import com.bigpapituna.sharpears.engine.chords.PopSong
import com.bigpapituna.sharpears.engine.chords.PopSongs
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.nav.Simple.PlayerControl
import com.bigpapituna.sharpears.engine.nav.Simple.PracticePart
import com.bigpapituna.sharpears.engine.nav.Simple.SettingsPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.7, Simple mode: the default from placement, a kid never, and what each place shows while it is on. */
class SimpleTest {
    @Test fun beginnersStartSimpleAndTheChoiceWins() {
        assertTrue(Simple.defaultOn(Level.NEVER))
        assertTrue(Simple.defaultOn(Level.A_LITTLE))
        assertFalse(Simple.defaultOn(Level.SOME))
        assertFalse(Simple.defaultOn(Level.A_LOT))
        assertTrue(Simple.isOn(kid = false, chosen = null, level = Level.NEVER))
        assertFalse(Simple.isOn(kid = false, chosen = null, level = Level.A_LOT))
        assertFalse(Simple.isOn(kid = false, chosen = false, level = Level.NEVER))     // switched to Advanced
        assertTrue(Simple.isOn(kid = false, chosen = true, level = Level.A_LOT))       // switched to Simple
        for (l in Level.entries) for (c in listOf(null, true, false)) assertFalse("a kid never", Simple.isOn(kid = true, chosen = c, level = l))
    }

    @Test fun learnChordsKeepsTheSixBeginnerChordsAndThePacksThatUseThem() {
        assertEquals(listOf("C", "F", "G", "Am", "Dm", "Em"), Simple.CHORDS.map { it.name })
        fun pack(id: String, vararg chords: Chord) = LessonPack(id, id, 1, emptyList(), chords.map { LessonItem.ChordItem(it) })
        val major = pack("major", Chord(0, ChordType.MAJOR), Chord(5, ChordType.MAJOR), Chord(7, ChordType.MAJOR), Chord(2, ChordType.MAJOR), Chord(10, ChordType.MAJOR))
        val sevenths = pack("sevenths", Chord(0, ChordType.MAJ7), Chord(7, ChordType.DOM7))
        val song = pack("song", Chord(7, ChordType.MAJOR), Chord(2, ChordType.DOM7), Chord(4, ChordType.MINOR))
        val progressions = LessonPack("prog", "prog", 4, emptyList(), listOf(LessonItem.ProgressionItem(0, 0)))
        val kept = Simple.packs(listOf(major, sevenths, song, progressions))
        assertEquals(listOf("major", "song"), kept.map { it.id })
        assertEquals(listOf("C", "F", "G"), kept[0].items.map { (it as LessonItem.ChordItem).chord.name })
        assertEquals(listOf("G", "Em"), kept[1].items.map { (it as LessonItem.ChordItem).chord.name })
    }

    @Test fun gamesAndProgressionsKeepTheFirstOnes() {
        assertEquals(listOf(GameKind.BUILD, GameKind.HEAR), Simple.games(GameKind.entries))
        val pop = Simple.progressions(PROGRESSIONS)
        assertEquals(listOf("I-V-vi-IV (pop)", "I-vi-IV-V (50s)"), pop.map { it.name })
        assertTrue(pop.all { p -> p.degrees.all { it.type == ChordType.MAJOR || it.type == ChordType.MINOR } })
    }

    @Test fun songsByChordsKeepFourChordAndEasiestSongsAndTwoPatterns() {
        val kept = Simple.popSongs(POP_SONGS)
        assertTrue(kept.isNotEmpty() && kept.size < POP_SONGS.size)
        assertTrue(kept.all { PopSongs.isFourChord(it) || it.difficulty == 1 })
        assertTrue(POP_SONGS.filter { it !in kept }.none { PopSongs.isFourChord(it) || it.difficulty == 1 })
        assertEquals(listOf(PopSong.Pattern.WHOLE, PopSong.Pattern.HALVES), Simple.patterns(PopSong.Pattern.entries.toList()))
    }

    @Test fun practiceKeepsThreeMajorScalesAndOneWarmUp() {
        assertEquals(listOf(0, 7, 5), Simple.SCALE_ROOTS)        // C, G, F
        assertEquals(listOf("a"), Simple.warmups(listOf("a", "b", "c")))
        assertEquals(setOf(PracticePart.SCALES, PracticePart.WARMUPS), PracticePart.entries.filter { Simple.practiceShows(it, true) }.toSet())
        assertTrue(PracticePart.entries.all { Simple.practiceShows(it, false) })
    }

    @Test fun learnKeepsThePathAndHidesTheSideRows() {
        assertFalse(Simple.learnSideRows(simple = true))
        assertTrue(Simple.learnSideRows(simple = false))
    }

    @Test fun thePlayerKeepsModesHandsAndPlayAndPutsTheRestInMore() {
        val bar = PlayerControl.entries.filter { Simple.inBar(it, true) }.toSet()
        assertTrue(bar.containsAll(listOf(PlayerControl.MODE, PlayerControl.HANDS, PlayerControl.PLAY, PlayerControl.TIMELINE, PlayerControl.MIC)))
        assertEquals(setOf(PlayerControl.LOOP, PlayerControl.SPEED, PlayerControl.TRANSPOSE, PlayerControl.METRONOME, PlayerControl.CHORDS, PlayerControl.DUET),
            PlayerControl.entries.toSet() - bar)
        assertTrue(PlayerControl.entries.all { Simple.inBar(it, false) })
    }

    @Test fun settingsKeepTheEssentials() {
        assertEquals(setOf(SettingsPart.FAMILY, SettingsPart.SIMPLE, SettingsPart.SOUND, SettingsPart.VOICE, SettingsPart.REMINDER, SettingsPart.MICROPHONE, SettingsPart.TESTING),
            SettingsPart.entries.filter { Simple.settingsShows(it, true) }.toSet())
        assertTrue(SettingsPart.entries.all { Simple.settingsShows(it, false) })
    }

    /** The copy rules (CLAUDE.md design system): no dashes or symbol glyphs; lines end with a stop, the question with its mark. */
    @Test fun theCopyKeepsTheRules() {
        val lines = listOf(Simple.NAME, Simple.SHOW_MORE, Simple.SHOW_ALL_SETTINGS, Simple.SWITCH_QUESTION, Simple.SWITCH_LINE, Simple.SWITCH,
            Simple.SETTING_TITLE, Simple.SETTING_LINE)
        for (l in lines) assertFalse(l, Regex("[—–·>]| - ").containsMatchIn(l))
        assertTrue(Simple.SWITCH_LINE.endsWith(".") && Simple.SETTING_LINE.endsWith("."))
        assertTrue(Simple.SWITCH_QUESTION.endsWith("?"))
        // The tour's third card and What's new say it (Roy's brief).
        assertTrue("Simple" in Welcome.TOUR[2].line && "Advanced" in Welcome.TOUR[2].line)
        assertTrue(Welcome.whatsNew("2.7.0").any { "Simple" in it && "Advanced" in it })
    }
}
