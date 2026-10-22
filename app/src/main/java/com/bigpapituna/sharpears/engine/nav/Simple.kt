package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.GameKind
import com.bigpapituna.sharpears.engine.LessonItem
import com.bigpapituna.sharpears.engine.LessonPack
import com.bigpapituna.sharpears.engine.Progression
import com.bigpapituna.sharpears.engine.chords.PopSong
import com.bigpapituna.sharpears.engine.chords.PopSongs
import com.bigpapituna.sharpears.engine.family.Level

/**
 * Simple mode (2.7, Roy): one switch per grown-up that keeps each place to its first steps; Advanced is the full app. On by
 * default for someone who said they play Never or A little, off for Some and A lot; a kid never sees it (the kid home is the
 * simple one). It only filters what is shown: progress, stars and mastery are the same data either way. Each place keeps a
 * quiet "Show more" at its bottom that shows the full place once and offers to switch to Advanced.
 *
 * The rules per place live here as pure functions (SimpleTest); the screens ask them.
 */
object Simple {
    // ---- the switch ----

    /** Simple by default for a beginner: placement said Never or A little. */
    fun defaultOn(level: Level): Boolean = level == Level.NEVER || level == Level.A_LITTLE

    /** Whether Simple is on: a kid never; else what the person chose, or the default from their level until they choose. */
    fun isOn(kid: Boolean, chosen: Boolean?, level: Level): Boolean = !kid && (chosen ?: defaultOn(level))

    const val NAME = "Simple"
    const val SHOW_MORE = "Show more"
    const val SHOW_ALL_SETTINGS = "Show all settings"
    const val SWITCH_QUESTION = "Switch to Advanced?"
    const val SWITCH_LINE = "Advanced shows everything in every place. Simple mode can come back any time, on Today, You or in Settings."
    const val SWITCH = "Switch"
    const val SETTING_TITLE = "Simple mode"
    const val SETTING_LINE = "Each place shows its first steps only: six chords, the first games, three scales. Off, the whole app."

    // ---- Learn chords ----

    /** The six beginner chords: C, F, G, Am, Dm, Em. */
    val CHORDS: List<Chord> = listOf(Chord(0, ChordType.MAJOR), Chord(5, ChordType.MAJOR), Chord(7, ChordType.MAJOR),
        Chord(9, ChordType.MINOR), Chord(2, ChordType.MINOR), Chord(4, ChordType.MINOR))

    /**
     * The packs that use the beginner chords, each cut to those chords (Major triads to C, F and G; a song's pack to the six
     * it has); packs left with none (sevenths, the progressions) go. The daily review draws from these while Simple is on.
     */
    fun packs(all: List<LessonPack>): List<LessonPack> = all.mapNotNull { p ->
        val items = p.items.filter { it is LessonItem.ChordItem && it.chord in CHORDS }
        if (items.isEmpty()) null else p.copy(items = items)
    }

    // ---- Games, Progressions ----

    val GAMES: List<GameKind> = listOf(GameKind.BUILD, GameKind.HEAR)

    fun games(all: List<GameKind>): List<GameKind> = all.filter { it in GAMES }

    /** The pop progressions: the ones of plain major and minor chords (no sevenths), so I V vi IV and I vi IV V. */
    fun progressions(all: List<Progression>): List<Progression> =
        all.filter { p -> p.degrees.all { it.type == ChordType.MAJOR || it.type == ChordType.MINOR } }

    // ---- Songs by chords ----

    /** Four-chord songs and the easiest ones (difficulty 1). */
    fun popSongs(all: List<PopSong>): List<PopSong> = all.filter { PopSongs.isFourChord(it) || it.difficulty == 1 }

    /** The patterns a song page offers: one chord a bar, or two. */
    val PATTERNS: List<PopSong.Pattern> = listOf(PopSong.Pattern.WHOLE, PopSong.Pattern.HALVES)

    fun patterns(all: List<PopSong.Pattern>): List<PopSong.Pattern> = all.filter { it in PATTERNS }

    // ---- Practice ----

    /** C, G and F major (roots as semitones above C), in that order. */
    val SCALE_ROOTS: List<Int> = listOf(0, 7, 5)

    /** Practice's sections: Scales and Warm-ups; Tools (the metronome and the tuner) wait in Advanced. */
    enum class PracticePart { SCALES, ARPEGGIOS, SCALE_TYPES, WARMUPS, ROUTINE, TOOLS }

    fun practiceShows(part: PracticePart, simple: Boolean): Boolean = !simple || part == PracticePart.SCALES || part == PracticePart.WARMUPS

    /** One warm-up: the first of the catalogue (five fingers, in C). */
    fun <T> warmups(all: List<T>): List<T> = all.take(1)

    // ---- Learn ----

    /** Learn's rows beside the course: Theory, Ear training and Sight-read wait in Advanced; the path and the chords stay. */
    fun learnSideRows(simple: Boolean): Boolean = !simple

    // ---- the player ----

    /** What the player's bar holds. */
    enum class PlayerControl { MODE, HANDS, PLAY, TIMELINE, MIC, VIDEO, BACKING, FLAG, LOOP, SPEED, TRANSPOSE, METRONOME, CHORDS, DUET }

    /** In Simple these go into one More sheet; Listen, Wait and Perform, the hands, play, the timeline and the mic stay. */
    val PLAYER_MORE: Set<PlayerControl> = setOf(PlayerControl.LOOP, PlayerControl.SPEED, PlayerControl.TRANSPOSE,
        PlayerControl.METRONOME, PlayerControl.CHORDS, PlayerControl.DUET)

    fun inBar(c: PlayerControl, simple: Boolean): Boolean = !simple || c !in PLAYER_MORE

    // ---- Settings ----

    enum class SettingsPart { FAMILY, PROGRESS, SIMPLE, KEYS, FALLING_NOTES, WRONG_KEYS, SOUND, VOICE, REMINDER, BRIEFS, MICROPHONE, ABOUT, TESTING }

    /**
     * The essentials Simple keeps: family, voice, sound, the microphone and the reminder (and the switch itself), plus
     * Testing while the tester build exists (Roy, 2026-10-22: "add help Roy test in simple mode as well").
     */
    val SETTINGS_ESSENTIALS: Set<SettingsPart> = setOf(SettingsPart.FAMILY, SettingsPart.SIMPLE, SettingsPart.SOUND, SettingsPart.VOICE, SettingsPart.BRIEFS,
        SettingsPart.REMINDER, SettingsPart.MICROPHONE, SettingsPart.TESTING)

    fun settingsShows(part: SettingsPart, simple: Boolean): Boolean = !simple || part in SETTINGS_ESSENTIALS
}
