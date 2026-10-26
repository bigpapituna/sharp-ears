package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.Mastery
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.plural

/**
 * "I know these chords" (2.14). Marc plays chords already; on day 2 "Songs I can play" and "One new chord away" were both
 * empty, because the app only counts a chord known once its Leitner box reaches [PopFind.KNOWN_BOX] through practice. One
 * screen lets someone who said some or a lot pick the chords they already play; each is written to the mastery store at that
 * box (never lowering one that is higher), so the two lists fill on day 1 and the triad packs can open. Pure (KnownChordsTest).
 */
object KnownChords {
    /** How many chords the screen offers. */
    const val MAX = 16

    /** The six first chords, offered first in this order: C, G, F, Am, Dm, Em. */
    val FIRST: List<Chord> = listOf(Chord(0, ChordType.MAJOR), Chord(7, ChordType.MAJOR), Chord(5, ChordType.MAJOR),
        Chord(9, ChordType.MINOR), Chord(2, ChordType.MINOR), Chord(4, ChordType.MINOR))

    /**
     * The chords to offer: the six first chords ([FIRST]), then the other plain major and minor chords the catalogue's songs
     * use, the ones the most songs use first (a slash chord counts as its root chord, [PopFind.chordsOf]), ties major first,
     * then by root: D, A, E, B flat and so on.
     */
    fun choices(songs: List<PopSong>, max: Int = MAX): List<Chord> = (FIRST + ranked(songs)).distinct().take(max)

    /** The catalogue's plain major and minor chords by how many songs use each. */
    fun ranked(songs: List<PopSong>): List<Chord> =
        songs.flatMap { PopFind.chordsOf(it).distinct() }
            .filter { it.type == ChordType.MAJOR || it.type == ChordType.MINOR }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Chord, Int>> { it.value }.thenBy { it.key.type.ordinal }.thenBy { it.key.root })
            .map { it.key }

    /** Offered on Today and in the empty lists to someone who said some or a lot and has no chord known yet, until they pick or say no. */
    fun offered(level: Level, known: Set<Chord>, dismissed: Boolean): Boolean =
        (level == Level.SOME || level == Level.A_LOT) && known.isEmpty() && !dismissed

    /** The mastery store's id of a chord ("chord:Am"), as the chord packs and [PopFind.known] read it. */
    fun id(c: Chord): String = "chord:" + c.name

    /**
     * What [stored] becomes once the chord is said to be known at [now]: at least [PopFind.KNOWN_BOX], seen, and due again when
     * that box would be (four days on). A higher box is kept as it was.
     */
    fun seed(stored: Mastery?, now: Long, box: Int = PopFind.KNOWN_BOX): Mastery =
        if (stored != null && stored.seen && stored.box >= box) stored
        else Mastery(box, now + DUE_AFTER_MS, seen = true)

    /** The box's four days ([com.bigpapituna.sharpears.engine.BOX_DAYS] at box 3). */
    const val DUE_AFTER_MS = 4 * 86_400_000L

    const val TITLE = "Which chords do you know?"
    const val LINE = "Tap the ones you can play without looking them up. The songs that use only these go to Songs I can play."
    const val SAVE = "Save"
    const val OFFER = "I know some chords"
    const val OFFER_LINE = "Already play chords? Pick the ones you know and see the songs you can play today."
    const val EMPTY_LINE = "Know some chords already? Pick them and the songs they play show up here."

    /** "You can play 12 songs with these." under the picks, as they change. */
    fun countLine(songs: Int): String = if (songs == 0) "Pick a few to see the songs they play." else "You can play ${plural(songs, "song")} with these."
}
