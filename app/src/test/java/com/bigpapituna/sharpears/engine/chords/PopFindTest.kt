package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.Mastery
import com.bigpapituna.sharpears.engine.chords.PopSong.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopFindTest {
    private fun maj(r: Int) = PopChord(r, ChordType.MAJOR)
    private fun min(r: Int) = PopChord(r, ChordType.MINOR)

    private fun song(
        id: String, chords: List<PopChord>, key: Int = 0, minor: Boolean = false, year: Int = 2000, difficulty: Int = 1,
        styles: List<Style> = listOf(Style.POP), bpm: Int = 100, title: String = id,
    ) = PopSong(id, title, "A", year, styles, key, minor, bpm, sections = listOf(PopSection("Verse", chords.map { PopBar(it) })),
        pattern = PopSong.Pattern.HALVES, difficulty = difficulty, confidence = PopSong.Confidence.CHECKED)

    private val c = Chord(0, ChordType.MAJOR)
    private val f = Chord(5, ChordType.MAJOR)
    private val g = Chord(7, ChordType.MAJOR)
    private val am = Chord(9, ChordType.MINOR)
    private val dm = Chord(2, ChordType.MINOR)

    private val fourChords = song("four", listOf(maj(0), maj(7), min(9), maj(5)))
    private val threeChords = song("three", listOf(maj(0), maj(5), maj(7)))
    private val withDm = song("dm", listOf(maj(0), min(2), maj(5), maj(7)), difficulty = 2, year = 1990)
    private val hard = song("hard", listOf(PopChord(0, ChordType.MAJ7), PopChord(2, ChordType.MIN7), PopChord(7, ChordType.DOM7)), difficulty = 4, year = 2010)
    private val all = listOf(fourChords, threeChords, withDm, hard)

    // --- the chords a song needs (the content's rule, PopSongs.chordsOf) ---

    @Test fun slashBassesDoNotMakeAFifthChord() {
        val s = song("slash", listOf(maj(0), PopChord(7, ChordType.MAJOR, bass = 11), min(9), PopChord(5, ChordType.MAJOR, bass = 0)))
        assertEquals(listOf(c, g, am, f), PopFind.chordsOf(s))
        // The badge is the content's one rule (PopSongs.isFourChord): the slash basses do not take it away.
        assertTrue(PopSongs.isFourChord(s))
        assertFalse(PopSongs.isFourChord(song("minor", listOf(min(9), maj(5), maj(0), maj(7)), key = 9, minor = true)))
    }

    // --- what a profile knows ---

    @Test fun knownChordsComeFromTheLeitnerBoxes() {
        val m = mapOf(
            "chord:C" to Mastery(box = 3, dueAt = 0, seen = true),
            "chord:Am" to Mastery(box = 5, dueAt = 0, seen = true),
            "chord:F" to Mastery(box = 2, dueAt = 0, seen = true),          // not yet
            "chord:G" to Mastery(box = 4, dueAt = 0, seen = false),         // never seen
            "prog:0:0" to Mastery(box = 5, dueAt = 0, seen = true),         // a progression, not a chord
            "chord:Nonsense" to Mastery(box = 5, dueAt = 0, seen = true),
        )
        assertEquals(setOf(c, am), PopFind.known(m))
        assertEquals(setOf(c, am, f), PopFind.known(m, minBox = 2))
        assertEquals(emptySet<Chord>(), PopFind.known(emptyMap()))
    }

    @Test fun canPlayNeedsEveryChordAndOneAwayExactlyOneMissing() {
        val known = setOf(c, g, am)
        assertFalse(PopFind.canPlay(fourChords, known))
        assertTrue(PopFind.oneAway(fourChords, known))
        assertEquals(listOf(f), PopFind.missing(fourChords, known))
        assertTrue(PopFind.canPlay(fourChords, known + f))
        assertFalse(PopFind.oneAway(fourChords, known + f))
        assertFalse(PopFind.oneAway(hard, known))                    // three missing
    }

    @Test fun playableCountCountsEverySongWhoseChordsAreAllKnown() {
        assertEquals(0, PopFind.playableCount(all, emptySet()))
        assertEquals(1, PopFind.playableCount(all, setOf(c, f, g)))               // the three-chord song
        assertEquals(2, PopFind.playableCount(all, setOf(c, f, g, am)))           // and the four-chord song
        assertEquals(3, PopFind.playableCount(all, setOf(c, f, g, am, dm)))       // and the one with Dm
    }

    // --- the browse screen ---

    @Test fun easiestFirstIsTheContentsOrderDifficultyFourChordsNewestTitle() {
        val a = song("a", listOf(maj(0), maj(5)), difficulty = 1, title = "B song")
        val b = song("b", listOf(maj(0), maj(5)), difficulty = 1, title = "A song")
        val newer = song("newer", listOf(maj(0), maj(5)), difficulty = 1, year = 2020)
        val notFour = song("notfour", listOf(maj(0), min(2)), difficulty = 1, year = 2024)
        val harder = song("h", listOf(maj(0)), difficulty = 2)
        val sorted = PopFind.browse(listOf(harder, notFour, a, newer, b), PopFind.Chip.ALL, PopFind.Sort.EASIEST, emptySet()).map { it.id }
        assertEquals(listOf("newer", "b", "a", "notfour", "h"), sorted)
        assertEquals(PopSongs.sortedForBeginners(listOf(harder, notFour, a, newer, b)).map { it.id }, sorted)
    }

    @Test fun newestGoesByYearThenTitle() {
        assertEquals(listOf("hard", "four", "three", "dm"), PopFind.browse(all, PopFind.Chip.ALL, PopFind.Sort.NEWEST, emptySet()).map { it.id })
    }

    @Test fun canPlayAndOneAwayNarrowTheList() {
        val known = setOf(c, f, g)
        assertEquals(listOf("three"), PopFind.browse(all, PopFind.Chip.ALL, PopFind.Sort.CAN_PLAY, known).map { it.id })
        // fourChords needs only Am; withDm needs only Dm: both are one chord away, easiest first.
        assertEquals(listOf("four", "dm"), PopFind.browse(all, PopFind.Chip.ALL, PopFind.Sort.ONE_AWAY, known).map { it.id })
        assertTrue(PopFind.browse(all, PopFind.Chip.ALL, PopFind.Sort.CAN_PLAY, emptySet()).isEmpty())
    }

    @Test fun chipsFilterByStyleAndASongMayBeInSeveral() {
        val mixed = listOf(
            song("hit", listOf(maj(0)), styles = listOf(Style.GREATEST_HITS, Style.SOUL)),
            song("ballad", listOf(maj(0)), styles = listOf(Style.BALLAD)),
            song("kids", listOf(maj(0)), styles = listOf(Style.KIDS)),
            song("eighties", listOf(maj(0)), styles = listOf(Style.EIGHTIES_ROCK, Style.ROCK)),
        )
        fun ids(chip: PopFind.Chip) = PopFind.browse(mixed, chip, PopFind.Sort.EASIEST, emptySet()).map { it.id }.sorted()
        assertEquals(listOf("ballad", "hit"), ids(PopFind.Chip.SOUL))
        assertEquals(listOf("hit"), ids(PopFind.Chip.GREATEST_HITS))
        assertEquals(listOf("kids"), ids(PopFind.Chip.KIDS))
        assertEquals(listOf("eighties"), ids(PopFind.Chip.EIGHTIES))
        assertEquals(listOf("eighties"), ids(PopFind.Chip.ROCK))
        assertEquals(emptyList<String>(), ids(PopFind.Chip.HOLIDAY))
        assertEquals(4, ids(PopFind.Chip.ALL).size)
    }

    @Test fun aChipAndASortWorkTogether() {
        val s = listOf(
            song("a", listOf(maj(0), maj(5)), styles = listOf(Style.KIDS)),
            song("b", listOf(maj(0), maj(5), maj(7)), styles = listOf(Style.KIDS)),
            song("c", listOf(maj(0), maj(5)), styles = listOf(Style.ROCK)),
        )
        assertEquals(listOf("a"), PopFind.browse(s, PopFind.Chip.KIDS, PopFind.Sort.CAN_PLAY, setOf(c, f)).map { it.id })
        assertEquals(listOf("b"), PopFind.browse(s, PopFind.Chip.KIDS, PopFind.Sort.ONE_AWAY, setOf(c, f)).map { it.id })
    }

    @Test fun theChipsAreAllAndTheNineAskedForWithWorshipOnlyWhenThereIsSome() {
        val labels = PopFind.chipsFor(all).map { it.label }
        assertEquals(listOf("All", "Greatest hits", "Yacht rock", "80s", "Pop", "Rock", "Country", "Soul and ballads", "Kids", "Holiday"), labels)
        val withWorship = all + song("w", listOf(maj(0)), styles = listOf(Style.WORSHIP))
        assertTrue(PopFind.chipsFor(withWorship).any { it == PopFind.Chip.WORSHIP })
    }

    @Test fun chipsCarryHowManySongsTheyHold() {
        val mixed = listOf(
            song("hit", listOf(maj(0)), styles = listOf(Style.GREATEST_HITS, Style.SOUL, Style.BALLAD)),
            song("ballad", listOf(maj(0)), styles = listOf(Style.BALLAD)),
            song("kids", listOf(maj(0)), styles = listOf(Style.KIDS)),
        )
        val counts = PopFind.chipCounts(mixed)
        assertEquals(3, counts[PopFind.Chip.ALL])
        assertEquals(2, counts[PopFind.Chip.SOUL])                  // the hit in two of its styles counts once
        assertEquals(1, counts[PopFind.Chip.GREATEST_HITS])
        assertEquals(0, counts[PopFind.Chip.HOLIDAY])
        assertEquals(null, counts[PopFind.Chip.WORSHIP])            // no chip at all
        assertEquals("Soul and ballads 2", PopFind.chipLabel(PopFind.Chip.SOUL, 2))
        assertEquals(PopFind.chipsFor(mixed), counts.keys.toList())
    }

    @Test fun songsThatUseTheseChordsFindsOnlySongsMadeOfThem() {
        assertEquals(listOf("three"), PopFind.using(all, setOf(c, f, g)).map { it.id })
        assertEquals(listOf("four", "three"), PopFind.using(all, setOf(c, f, g, am)).map { it.id })
        assertTrue(PopFind.using(all, setOf(c)).isEmpty())
        assertEquals(1, PopFind.using(all, setOf(c, f, g, am), limit = 1).size)
    }

    @Test fun songsWithAChordAreEasiestFirst() {
        // Both difficulty 1 and both four-chord songs by the content's rule (C, F and G are I, IV and V), so by title.
        assertEquals(listOf("four", "three", "dm"), PopFind.withChord(all, f).map { it.id })
        assertEquals(listOf("hard"), PopFind.withChord(all, Chord(7, ChordType.DOM7)).map { it.id })
    }

    @Test fun difficultyBarsStayBetweenOneAndFourAndConfidenceHasWords() {
        assertEquals(1, PopFind.bars(song("z", listOf(maj(0)), difficulty = 0)))
        assertEquals(4, PopFind.bars(song("z", listOf(maj(0)), difficulty = 9)))
        assertEquals("checked", PopFind.confidenceWord(PopSong.Confidence.CHECKED))
        assertEquals("close enough", PopFind.confidenceWord(PopSong.Confidence.LIKELY))
        assertEquals("a sketch", PopFind.confidenceWord(PopSong.Confidence.SKETCH))
        assertEquals("three", PopFind.byId(all, "three")!!.id)
        assertEquals(null, PopFind.byId(all, "nope"))
    }
}
