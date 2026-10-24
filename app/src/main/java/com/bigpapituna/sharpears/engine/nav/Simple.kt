package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.GameKind
import com.bigpapituna.sharpears.engine.LessonItem
import com.bigpapituna.sharpears.engine.LessonPack
import com.bigpapituna.sharpears.engine.Progression
import com.bigpapituna.sharpears.engine.SONG_PACK_PREFIX
import com.bigpapituna.sharpears.engine.chords.PopSong
import com.bigpapituna.sharpears.engine.chords.PopSongs
import com.bigpapituna.sharpears.engine.family.Level

/**
 * The three layouts' rules (2.8; Simple mode in 2.7): what each place shows in [Layout.SIMPLE], [Layout.ADVANCED] and
 * [Layout.FULL]. Simple keeps each place to its first steps (exactly 2.7's Simple). Advanced is Simple plus Learn in full (the
 * course path with Theory, Ear training and Sight-read), Practice in full, every song by chords, the song packs whole, the loop
 * and speed on the player's bar, and Settings' keys, falling notes, wrong keys and briefs; the chord extras (every chord, game,
 * progression and pattern) wait for Full. Full is the whole app (2.7's Advanced). A kid is never asked and never cut (the kid
 * home is the simple one). It only filters what is shown: progress, stars and mastery are the same data in every layout. A
 * place a layout cuts ends on a quiet "Show more" that shows it in full once and offers the three layouts.
 *
 * Pure functions (LayoutTest); the screens ask them.
 */
object Simple {
    // ---- choosing ----

    /** Until someone chooses: Simple for a beginner (placement said Never or A little), Full for the rest (2.7's default). */
    fun default(level: Level): Layout = if (level == Level.NEVER || level == Level.A_LITTLE) Layout.SIMPLE else Layout.FULL

    /** The layout a person sees: a kid's is Full (nothing cut); a grown-up's is their choice, or the default from their level. */
    fun layoutOf(kid: Boolean, chosen: Layout?, level: Level): Layout = if (kid) Layout.FULL else chosen ?: default(level)

    /** The launch asks a grown-up who never chose (2.7's switch counts as a choice, see Prefs.layout); a kid never. */
    fun asks(kid: Boolean, chosen: Layout?): Boolean = !kid && chosen == null

    /** What a place shows: its "Show more" tapped, everything for this visit; else the person's layout. */
    fun shown(layout: Layout, more: Boolean): Layout = if (more) Layout.FULL else layout

    // ---- the places ----

    /** The places a layout cuts, each with its "Show more" (the player's is its More sheet, a song page's is the list's). */
    enum class Place { LEARN, LEARN_CHORDS, GAMES, PROGRESSIONS, SONGS_BY_CHORDS, SONG_PATTERNS, PRACTICE, PLAYER, SETTINGS }

    /** What Advanced still cuts: the chord extras, the player's extras and two settings groups (Progress, About). */
    val ADVANCED_CUTS: Set<Place> = setOf(Place.LEARN_CHORDS, Place.GAMES, Place.PROGRESSIONS, Place.SONG_PATTERNS, Place.PLAYER, Place.SETTINGS)

    /** Whether [layout] shows less than everything in [place]: Simple cuts every place, Advanced some, Full none. */
    fun cuts(place: Place, layout: Layout): Boolean = when (layout) {
        Layout.SIMPLE -> true
        Layout.ADVANCED -> place in ADVANCED_CUTS
        Layout.FULL -> false
    }

    const val SHOW_MORE = "Show more"
    const val SHOW_ALL_SETTINGS = "Show all settings"
    const val SWITCH_QUESTION = "Keep more on screen?"
    const val SWITCH_LINE = "Pick how much every place shows. Change it any time on You or in Settings."
    const val SETTING_TITLE = "Layout"
    const val SETTING_LINE = "How much each place shows. Simple: the first steps. Advanced: lessons, songs and practice. Full: everything."

    // ---- Learn chords ----

    /** The six beginner chords: C, F, G, Am, Dm, Em. */
    val CHORDS: List<Chord> = listOf(Chord(0, ChordType.MAJOR), Chord(5, ChordType.MAJOR), Chord(7, ChordType.MAJOR),
        Chord(9, ChordType.MINOR), Chord(2, ChordType.MINOR), Chord(4, ChordType.MINOR))

    /** The chords the dictionary and free practice offer: the six until Full, then every chord (null). */
    fun chords(layout: Layout): List<Chord>? = if (layout == Layout.FULL) null else CHORDS

    /**
     * The packs. Simple cuts each to the beginner chords (Major triads to C, F and G; a song's pack to the six it has); packs
     * left with none (sevenths, the progressions) go. Advanced does the same but keeps the song packs ("Chords of <song>")
     * whole: a song's own chords, Roy's 2.7 open question. Full keeps all. The daily review draws from these.
     */
    fun packs(all: List<LessonPack>, layout: Layout): List<LessonPack> = if (layout == Layout.FULL) all else all.mapNotNull { p ->
        if (layout == Layout.ADVANCED && p.id.startsWith(SONG_PACK_PREFIX)) return@mapNotNull p
        val items = p.items.filter { it is LessonItem.ChordItem && it.chord in CHORDS }
        if (items.isEmpty()) null else p.copy(items = items)
    }

    // ---- Games, Progressions ----

    val GAMES: List<GameKind> = listOf(GameKind.BUILD, GameKind.HEAR)

    /** Advanced's games: the three chord games (Build it, Hear it, Major or minor); Intervals and Play it back wait for Full. */
    val ADVANCED_GAMES: List<GameKind> = GAMES + GameKind.MAJOR_MINOR

    fun games(all: List<GameKind>, layout: Layout): List<GameKind> = when (layout) {
        Layout.SIMPLE -> all.filter { it in GAMES }
        Layout.ADVANCED -> all.filter { it in ADVANCED_GAMES }
        Layout.FULL -> all
    }

    /** The pop progressions until Full: the ones of plain major and minor chords (no sevenths), so I V vi IV and I vi IV V. */
    fun progressions(all: List<Progression>, layout: Layout): List<Progression> = if (layout == Layout.FULL) all
        else all.filter { p -> p.degrees.all { it.type == ChordType.MAJOR || it.type == ChordType.MINOR } }

    // ---- Songs by chords ----

    /**
     * Simple's keys (2.8.4, QA D6): C, G and F major, A, E and D minor. At most one sharp or flat, and the keys the six
     * chords live in (Simple Practice's scales are C, G and F).
     */
    val EASY_MAJOR_KEYS: Set<Int> = setOf(0, 7, 5)
    val EASY_MINOR_KEYS: Set<Int> = setOf(9, 4, 2)

    fun easyKey(song: PopSong): Boolean = Math.floorMod(song.key, 12) in (if (song.minor) EASY_MINOR_KEYS else EASY_MAJOR_KEYS)

    /**
     * Simple: the first steps, the easy songs (difficulty 1 or 2) in an easy key ([easyKey]). 2.7 kept every four-chord song
     * whatever its difficulty or key, so Simple listed Perfect in A flat and The Scientist (difficulty 4). Advanced and Full:
     * every song.
     */
    fun popSongs(all: List<PopSong>, layout: Layout): List<PopSong> =
        if (layout == Layout.SIMPLE) all.filter { it.difficulty <= 2 && easyKey(it) } else all

    // ---- My songs ----

    /** The library's own list (2.8.1): My songs, and its "Add a song from a file". */
    enum class SongsPart { MY_SONGS, ADD_SONG }

    /** The layouts each part shows in: all three, since My songs is the learner's own list (never cut, no "Show more"). */
    val SONGS_PARTS: Map<SongsPart, Set<Layout>> = SongsPart.entries.associateWith { Layout.entries.toSet() }

    fun songsShows(part: SongsPart, layout: Layout): Boolean = layout in SONGS_PARTS.getValue(part)

    /** The patterns a song page offers in Simple: one chord a bar, or two. */
    val PATTERNS: List<PopSong.Pattern> = listOf(PopSong.Pattern.WHOLE, PopSong.Pattern.HALVES)

    /** Advanced adds the plain ones (four a bar, broken); the ballad, shuffle and rock feels wait for Full. */
    val ADVANCED_PATTERNS: List<PopSong.Pattern> = PATTERNS + listOf(PopSong.Pattern.QUARTERS, PopSong.Pattern.BROKEN)

    fun patterns(all: List<PopSong.Pattern>, layout: Layout): List<PopSong.Pattern> = when (layout) {
        Layout.SIMPLE -> all.filter { it in PATTERNS }
        Layout.ADVANCED -> all.filter { it in ADVANCED_PATTERNS }
        Layout.FULL -> all
    }

    /** The pattern a song page opens on: the one asked for, or Halves when the layout does not offer it. */
    fun openingPattern(p: PopSong.Pattern, layout: Layout): PopSong.Pattern =
        if (p in patterns(PopSong.Pattern.entries, layout)) p else PopSong.Pattern.HALVES

    // ---- Practice ----

    /** C, G and F major (roots as semitones above C), in that order: Simple's scales. */
    val SCALE_ROOTS: List<Int> = listOf(0, 7, 5)

    /** Practice's sections: Simple has Scales and Warm-ups; arpeggios, the other scales and Tools wait for Advanced. */
    enum class PracticePart { SCALES, ARPEGGIOS, SCALE_TYPES, WARMUPS, ROUTINE, TOOLS }

    fun practiceShows(part: PracticePart, layout: Layout): Boolean =
        layout != Layout.SIMPLE || part == PracticePart.SCALES || part == PracticePart.WARMUPS

    /** Simple's one warm-up: the first of the catalogue (five fingers, in C). */
    fun <T> warmups(all: List<T>): List<T> = all.take(1)

    // ---- Learn ----

    /** Learn's rows beside the course (Theory, Ear training, Sight-read): from Advanced on; Simple keeps the path and the chords. */
    fun learnSideRows(layout: Layout): Boolean = layout != Layout.SIMPLE

    // ---- the player ----

    /** What the player's bar holds. */
    enum class PlayerControl { MODE, HANDS, PLAY, TIMELINE, MIC, VIDEO, BACKING, FLAG, LOOP, SPEED, TRANSPOSE, METRONOME, CHORDS, DUET }

    /** In Simple these go into one More sheet; Listen, Wait and Perform, the hands, play, the timeline and the mic stay. */
    val PLAYER_MORE: Set<PlayerControl> = setOf(PlayerControl.LOOP, PlayerControl.SPEED, PlayerControl.TRANSPOSE,
        PlayerControl.METRONOME, PlayerControl.CHORDS, PlayerControl.DUET)

    /** Advanced brings the loop and the speed back to the bar (practice tools); the rest stay in More. */
    val ADVANCED_MORE: Set<PlayerControl> = PLAYER_MORE - setOf(PlayerControl.LOOP, PlayerControl.SPEED)

    fun inBar(c: PlayerControl, layout: Layout): Boolean = when (layout) {
        Layout.SIMPLE -> c !in PLAYER_MORE
        Layout.ADVANCED -> c !in ADVANCED_MORE
        Layout.FULL -> true
    }

    // ---- Settings ----

    enum class SettingsPart { FAMILY, PROGRESS, LAYOUT, KEYS, FALLING_NOTES, WRONG_KEYS, SOUND, VOICE, REMINDER, BRIEFS, MICROPHONE, ABOUT, TESTING }

    /**
     * The essentials Simple keeps: family, voice, sound, the microphone and the reminder (and the layout itself), plus
     * Testing while the tester build exists (Roy, 2026-10-22: "add help Roy test in simple mode as well").
     */
    val SETTINGS_ESSENTIALS: Set<SettingsPart> = setOf(SettingsPart.FAMILY, SettingsPart.LAYOUT, SettingsPart.SOUND, SettingsPart.VOICE, SettingsPart.BRIEFS,
        SettingsPart.REMINDER, SettingsPart.MICROPHONE, SettingsPart.TESTING)

    /** Advanced adds how playing looks (keys, falling notes, wrong keys); Progress and About wait for Full. */
    val SETTINGS_ADVANCED: Set<SettingsPart> = SETTINGS_ESSENTIALS + setOf(SettingsPart.KEYS, SettingsPart.FALLING_NOTES,
        SettingsPart.WRONG_KEYS)

    fun settingsShows(part: SettingsPart, layout: Layout): Boolean = when (layout) {
        Layout.SIMPLE -> part in SETTINGS_ESSENTIALS
        Layout.ADVANCED -> part in SETTINGS_ADVANCED
        Layout.FULL -> true
    }
}
