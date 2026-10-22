package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.Mastery

/**
 * Finding a song by its chords (popular-song chord mode): the style chips, the sorts, "songs I can play" and "one new
 * chord away", the four-chord badge and the count the Chords tab shows. Pure, so it is tested on the JVM; the screens
 * only draw what it returns. Whichever list of songs it is given (the sample list or the real `POP_SONGS`).
 */
object PopFind {
    /** A chord counts as known once its Leitner box is this high: played cleanly three times running (due again in 4 days). */
    const val KNOWN_BOX = 3

    /** The style chips, in order: "All" first, then what Roy asked for. A song may be in several. */
    enum class Chip(val label: String, val styles: Set<PopSong.Style>) {
        ALL("All", emptySet()),
        GREATEST_HITS("Greatest hits", setOf(PopSong.Style.GREATEST_HITS)),
        YACHT_ROCK("Yacht rock", setOf(PopSong.Style.YACHT_ROCK)),
        EIGHTIES("80s", setOf(PopSong.Style.EIGHTIES_ROCK)),
        POP("Pop", setOf(PopSong.Style.POP)),
        ROCK("Rock", setOf(PopSong.Style.ROCK)),
        COUNTRY("Country", setOf(PopSong.Style.COUNTRY)),
        SOUL("Soul and ballads", setOf(PopSong.Style.SOUL, PopSong.Style.BALLAD)),
        KIDS("Kids", setOf(PopSong.Style.KIDS)),
        HOLIDAY("Holiday", setOf(PopSong.Style.HOLIDAY)),
        WORSHIP("Worship", setOf(PopSong.Style.WORSHIP)),
    }

    /** The ways to order or narrow the list. */
    enum class Sort(val label: String) {
        EASIEST("Easiest first"),
        NEWEST("Newest"),
        CAN_PLAY("Songs I can play"),
        ONE_AWAY("One new chord away"),
    }

    /** The chips to show: All and the nine Roy asked for, always (an empty one says so); Worship only when a song has it. */
    fun chipsFor(songs: List<PopSong>): List<Chip> =
        Chip.entries.filter { it != Chip.WORSHIP || songs.any { s -> PopSong.Style.WORSHIP in s.styles } }

    /** The chords the profile has learned, from the Leitner store's "chord:<name>" ids. */
    fun known(mastery: Map<String, Mastery>, minBox: Int = KNOWN_BOX): Set<Chord> =
        mastery.entries.filter { it.value.seen && it.value.box >= minBox && it.key.startsWith("chord:") }
            .mapNotNull { com.bigpapituna.sharpears.engine.parseChord(it.key.removePrefix("chord:")) }.toSet()

    /** The distinct chords of a song as dictionary chords (a slash chord is its root chord; the bass is the left hand's). */
    fun chordsOf(song: PopSong): List<Chord> = song.chords.map { Chord(it.root, it.type) }.distinct()

    /** The chords of [song] that [known] does not hold, in order of first use. */
    fun missing(song: PopSong, known: Set<Chord>): List<Chord> = chordsOf(song).filter { it !in known }

    fun canPlay(song: PopSong, known: Set<Chord>) = missing(song, known).isEmpty()

    fun oneAway(song: PopSong, known: Set<Chord>) = missing(song, known).size == 1

    /**
     * The "four chords" badge: a major-key song whose chords, bass notes aside, are exactly the I, IV, V and vi of its key
     * (the pop progression of hundreds of songs). A song with a seventh on one of them, or a fifth chord, does not wear it.
     */
    fun fourChord(song: PopSong): Boolean {
        if (song.minor) return false
        val degrees = chordsOf(song).map { Math.floorMod(it.root - song.key, 12) to it.type }.toSet()
        return degrees == setOf(0 to ChordType.MAJOR, 5 to ChordType.MAJOR, 7 to ChordType.MAJOR, 9 to ChordType.MINOR)
    }

    /** Easiest first: by the song's difficulty, then fewer chords, then slower, then by title. */
    val easiest: Comparator<PopSong> = compareBy<PopSong>({ it.difficulty }, { chordsOf(it).size }, { it.bpm }, { it.title.lowercase() })

    /** Newest first, ties by title. */
    val newest: Comparator<PopSong> = compareByDescending<PopSong> { it.year }.thenBy { it.title.lowercase() }

    /** [songs] under [chip], ordered or narrowed by [sort] for a profile that [known] the chords. */
    fun browse(songs: List<PopSong>, chip: Chip, sort: Sort, known: Set<Chord>): List<PopSong> {
        val styled = if (chip == Chip.ALL) songs else songs.filter { s -> s.styles.any { it in chip.styles } }
        return when (sort) {
            Sort.EASIEST -> styled.sortedWith(easiest)
            Sort.NEWEST -> styled.sortedWith(newest)
            Sort.CAN_PLAY -> styled.filter { canPlay(it, known) }.sortedWith(easiest)
            Sort.ONE_AWAY -> styled.filter { oneAway(it, known) }.sortedWith(easiest)
        }
    }

    /** "You can play N songs": how many of [songs] use only chords in [known]. */
    fun playableCount(songs: List<PopSong>, known: Set<Chord>): Int = songs.count { canPlay(it, known) }

    /**
     * Songs that use only chords from [chords] (a finished pack's), easiest first, the ones that use more of them first.
     * "Songs that use these chords". A pack of one chord finds nothing: a song needs at least [minChords] of its own.
     */
    fun using(songs: List<PopSong>, chords: Set<Chord>, minChords: Int = 2, limit: Int = 6): List<PopSong> =
        songs.filter { s -> chordsOf(s).let { it.size >= minChords && chords.containsAll(it) } }
            .sortedWith(compareByDescending<PopSong> { chordsOf(it).size }.then(easiest)).take(limit)

    /** Songs that use [chord] (the page of a chord can offer them). */
    fun withChord(songs: List<PopSong>, chord: Chord): List<PopSong> = songs.filter { chord in chordsOf(it) }.sortedWith(easiest)

    /** The song by id. */
    fun byId(songs: List<PopSong>, id: String): PopSong? = songs.firstOrNull { it.id == id }

    /** The bars (1 to 4) the difficulty draws; never a number on a kid's screen. */
    fun bars(song: PopSong): Int = song.difficulty.coerceIn(1, 4)

    /** The confidence as the words a tile and a page quietly show ("checked", "close enough", "a sketch"). */
    fun confidenceWord(c: PopSong.Confidence): String = when (c) {
        PopSong.Confidence.CHECKED -> "checked"
        PopSong.Confidence.LIKELY -> "close enough"
        PopSong.Confidence.SKETCH -> "a sketch"
    }
}
