package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern
import com.bigpapituna.sharpears.engine.chords.PopSong.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The popular songs by chords (docs/CONTENT.md, "Popular songs by chords"): the chart language, the content's own rules
 * (the structural lyric guard among them) and the discovery helpers.
 */
class PopSongsTest {
    private val songs = POP_SONGS

    // ---- The chart language ----

    @Test fun repeatsWriteTheBarsOutAgain() {
        val bars = parseBars("(C G | Am F) x2 | Dm", 4)
        assertEquals(5, bars.size)
        assertEquals(bars[0], bars[2])
        assertEquals(listOf(2, 2), bars[1].chords.map { it.beats })
    }

    @Test fun beatSplitsAndSlashChords() {
        val bar = parseBars("F:1 Bb:1 C/E:2", 4).single()
        assertEquals(listOf(1, 1, 2), bar.chords.map { it.beats })
        val c = bar.chords[2].chord
        assertEquals(PopChord(0, ChordType.MAJOR, 4), c)
        assertTrue(c.slash)
        assertEquals(PopChord(10, ChordType.MAJOR), bar.chords[1].chord)
    }

    @Test fun everyChordTypeSuffixParses() {
        for (t in ChordType.entries) {
            assertEquals(t, parseChord("C" + t.suffix).type)
            assertEquals(t, parseChord("F#" + t.suffix + "/A#").type)
            assertEquals(t, parseChord("Eb" + t.suffix).type)
        }
        assertEquals(PopChord(1, ChordType.MIN7), parseChord("C#m7"))
        assertEquals(PopChord(10, ChordType.DIM7), parseChord("Bbdim7"))
        assertEquals(PopChord(8, ChordType.AUG), parseChord("G#aug"))
    }

    @Test fun unknownSpellingsFailLoudly() {
        for (bad in listOf("Cmaj9", "H", "Cmin", "C-7", "Am7/X", "x2", "C7#9", "Csus", "c")) {
            assertThrows(bad, IllegalStateException::class.java) { parseChord(bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { parseBars("C:3 G:2", 4) }      // five beats
        assertThrows(IllegalArgumentException::class.java) { parseBars("C G F", 4) }        // four does not split by three
        assertThrows(IllegalArgumentException::class.java) {                               // a section with no chart line
            pop("t", "T", "A", 2000, listOf(Style.POP), "C", 100, Pattern.WHOLE, 1, Confidence.SKETCH, "", "Verse, Chorus", "Verse: C")
        }
        assertThrows(IllegalArgumentException::class.java) {                               // a chart line never played
            pop("t", "T", "A", 2000, listOf(Style.POP), "C", 100, Pattern.WHOLE, 1, Confidence.SKETCH, "", "Verse", "Verse: C\nChorus: F")
        }
        assertThrows(IllegalArgumentException::class.java) {                               // a song with a bad chord names it
            pop("t", "T", "A", 2000, listOf(Style.POP), "C", 100, Pattern.WHOLE, 1, Confidence.SKETCH, "", "Verse", "Verse: C | Fmaj9")
        }
    }

    @Test fun orderRepeatsAndTagsAreDroppedFromTheName() {
        val s = pop("t", "T", "A", 2000, listOf(Style.POP), "F#m", 100, Pattern.WHOLE, 1, Confidence.SKETCH, "",
            "Verse x2, Chorus#up", "Verse: F#m | A\nChorus#up: Gm Bb", beatsPerBar = 2)
        assertEquals(listOf("Verse", "Verse", "Chorus"), s.sections.map { it.name })
        assertEquals(6, s.key); assertTrue(s.minor); assertEquals(2, s.beatsPerBar)
        assertEquals(listOf(1, 1), s.sections[2].bars.single().chords.map { it.beats })
    }

    // ---- The content ----

    @Test fun everySongParses() {
        assertTrue("${songs.size} songs", songs.size >= 80)
        for (s in songs) {
            assertTrue(s.id, s.sections.isNotEmpty() && s.sections.all { it.bars.isNotEmpty() })
            assertTrue(s.id, s.key in 0..11 && s.bpm in 40..220 && s.beatsPerBar in 2..4 && s.year in 1800..2026)
            assertTrue(s.id, s.title.isNotBlank() && s.artist.isNotBlank() && s.styles.isNotEmpty())
        }
    }

    @Test fun beatsFillEveryBar() {
        for (s in songs) for (sec in s.sections) for (b in sec.bars) {
            assertEquals("${s.id} ${sec.name}", s.beatsPerBar, b.chords.sumOf { it.beats })
            assertTrue("${s.id} ${sec.name}", b.chords.all { it.beats > 0 })
        }
    }

    @Test fun idsAreUniqueAndKebabCase() {
        assertEquals(songs.size, songs.map { it.id }.toSet().size)
        for (s in songs) assertTrue(s.id, Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(s.id))
    }

    /** The structural lyric guard: a section has a name from this set, never a line of the song. */
    @Test fun sectionNamesComeFromTheAllowedSet() {
        for (s in songs) for (sec in s.sections) assertTrue("${s.id}: ${sec.name}", ALLOWED_SECTION.matches(sec.name))
    }

    /** The rest of the lyric guard: a note is about the chords, never the words. */
    @Test fun notesAreAboutChordsOnly() {
        for (s in songs) {
            val n = s.note
            assertTrue("${s.id} has no note", n.isNotBlank() && n.length <= 200)
            assertFalse("${s.id} quotes", n.any { it in "\"“”‘’«»" })
            assertNull("${s.id} single quotes", Regex("(^|[^A-Za-z])'|'([^A-Za-z]|$)").find(n))
            assertNull("${s.id} talks about the words", BANNED_WORDS.find(n))
            assertTrue("${s.id} repeats its title", Regex(Regex.escape(s.title), RegexOption.IGNORE_CASE).findAll(n).count() <= 1)
            assertFalse("${s.id} dash", (n + s.title + s.artist).any { it == '—' || it == '–' })
        }
    }

    @Test fun guardCatchesWhatItShould() {
        assertTrue(BANNED_WORDS.containsMatchIn("Sing it loud"))
        assertTrue(BANNED_WORDS.containsMatchIn("the words fall"))
        assertFalse(BANNED_WORDS.containsMatchIn("a singer's swing"))
        assertFalse(ALLOWED_SECTION.matches("Middle eight"))
        assertTrue(ALLOWED_SECTION.matches("Verse 2"))
        assertTrue(ALLOWED_SECTION.matches("Pre-chorus"))
    }

    @Test fun difficultyFollowsTheRule() {
        for (s in songs) {
            assertTrue(s.id, s.difficulty in 1..4)
            assertEquals(s.id, PopSongs.difficultyByRule(s), s.difficulty)
        }
    }

    @Test fun difficultyRuleCountsChordsColoursAndSpeed() {
        assertEquals(1, PopSongs.difficultyByRule(song("a", "C", "C | F | G | C")))
        assertEquals(2, PopSongs.difficultyByRule(song("b", "C", "C | F | G | Am | Dm")))
        assertEquals(3, PopSongs.difficultyByRule(song("c", "C", "C | F | G | Am | Dm | Em | E7 | Bb")))
        assertEquals(4, PopSongs.difficultyByRule(song("d", "C", "C F | G/B Am | Dm7 Em7 | Fmaj7 G7 | Bb Gsus4")))
    }

    @Test fun styleAndDifficultyMinimums() {
        for (st in Style.entries) {
            val min = if (st == Style.KIDS || st == Style.HOLIDAY) 5 else 6
            assertTrue("$st has ${PopSongs.byStyle(st).size}", PopSongs.byStyle(st).size >= min)
        }
        assertTrue(songs.count { it.difficulty <= 2 } >= 30)
    }

    @Test fun fourChordSetIsBigEnough() {
        val four = PopSongs.fourChordSongs()
        assertTrue("${four.size}", four.size >= 25)
        for (s in four) assertFalse(s.id, s.minor)
    }

    /** Every chord's notes (and bass) sit in the key, unless the note says why not. */
    @Test fun chordsAreDiatonicOrExplained() {
        for (s in songs) {
            if (EXCUSES.any { it in s.note.lowercase() }) continue
            val scale = scaleOf(s)
            for (c in s.chords) {
                val pcs = Chord(c.root, c.type).pitchClasses + listOfNotNull(c.bass)
                assertTrue("${s.id}: $c is outside the key and the note does not say why", scale.containsAll(pcs))
            }
        }
    }

    // ---- The helpers ----

    private fun song(id: String, key: String, chart: String, styles: List<Style> = listOf(Style.POP), year: Int = 2000, d: Int = 1) =
        pop(id, id, "Test", year, styles, key, 100, Pattern.WHOLE, d, Confidence.SKETCH, "", "Verse", "Verse: $chart")

    @Test fun fourChordSongsAreOnlyOneFourFiveSixInAMajorKey() {
        val yes = song("yes", "G", "G | D/F# | Em7 | Cadd9 | Dsus4 | D7 | Gmaj7")
        val three = song("three", "A", "A | D | E")
        val ii = song("ii", "C", "C | Dm | G | C")
        val minor = song("minor", "Am", "Am | F | C | G")
        val borrowed = song("borrowed", "C", "C | Bb | F | C")
        assertEquals(listOf(yes, three), PopSongs.fourChordSongs(listOf(yes, three, ii, minor, borrowed)))
        assertTrue(PopSongs.isFourChord(yes))
        assertFalse(PopSongs.isFourChord(minor))
    }

    @Test fun playableWithKnownChords() {
        val a = song("a", "C", "C | G/B | Am | F")
        val b = song("b", "C", "C | G | Am | F | Dm")
        val c = song("c", "C", "C | E7 | Am | D7")
        val mastered = setOf(Chord(0, ChordType.MAJOR), Chord(7, ChordType.MAJOR), Chord(9, ChordType.MINOR), Chord(17, ChordType.MAJOR))
        val p = PopSongs.playableWith(mastered, listOf(a, b, c))
        assertEquals(listOf(a), p.ready)                                    // the slash bass is the left hand's job
        assertEquals(listOf(b to Chord(2, ChordType.MINOR)), p.oneAway)    // C needs two new chords
        assertTrue(PopSongs.playableWith(emptySet(), listOf(a)).ready.isEmpty())
        val fourChords = PopSongs.playableWith(mastered).ready
        assertTrue(fourChords.isNotEmpty())
        for (s in fourChords) assertTrue(s.id, PopSongs.chordsOf(s).all { it in setOf(Chord(0, ChordType.MAJOR), Chord(7, ChordType.MAJOR), Chord(9, ChordType.MINOR), Chord(5, ChordType.MAJOR)) })
    }

    @Test fun byStyleGroupsAndFilters() {
        val a = song("a", "C", "C", listOf(Style.POP, Style.BALLAD))
        val b = song("b", "C", "C", listOf(Style.KIDS))
        assertEquals(listOf(a), PopSongs.byStyle(Style.BALLAD, listOf(a, b)))
        val map = PopSongs.byStyle(listOf(a, b))
        assertEquals(setOf(Style.POP, Style.BALLAD, Style.KIDS), map.keys)
        assertEquals(listOf(b), map[Style.KIDS])
        assertNull(map[Style.ROCK])
        for (st in Style.entries) assertNotNull(PopSongs.byStyle()[st])
    }

    @Test fun beginnersGetEasyFourChordAndNewFirst() {
        val hard = song("hard", "C", "C", d = 3)
        val easyOld = song("easy-old", "C", "C | F | G", year = 1960)
        val easyNew = song("easy-new", "C", "C | F | G", year = 2020)
        val easyOdd = song("easy-odd", "C", "C | Bb", year = 2024)
        assertEquals(listOf(easyNew, easyOld, easyOdd, hard), PopSongs.sortedForBeginners(listOf(hard, easyOld, easyOdd, easyNew)))
        val all = PopSongs.sortedForBeginners()
        assertEquals(songs.size, all.size)
        assertEquals(all.map { it.difficulty }.sorted(), all.map { it.difficulty })
    }

    @Test fun transposedMovesKeyRootsAndBass() {
        val s = song("t", "C", "C G/B | Am:3 F:1")
        val up = PopSongs.transposed(s, 2)
        assertEquals(2, up.key)
        assertEquals(listOf(PopChord(2, ChordType.MAJOR), PopChord(9, ChordType.MAJOR, 1), PopChord(11, ChordType.MINOR), PopChord(7, ChordType.MAJOR)), up.chords)
        assertEquals(listOf(2, 2, 3, 1), up.sections.flatMap { it.bars.flatMap { b -> b.chords.map { it.beats } } })
        assertEquals(10, PopSongs.transposed(s, -2).key)
        assertEquals(s, PopSongs.transposed(s, 12))
        for (x in songs) assertEquals(x.id, x, PopSongs.transposed(PopSongs.transposed(x, 5), -5))
        for (x in songs) assertEquals(x.id, PopSongs.isFourChord(x), PopSongs.isFourChord(PopSongs.transposed(x, 7)))
    }

    private companion object {
        val ALLOWED_SECTION = Regex("(Intro|Verse|Pre-chorus|Chorus|Post-chorus|Bridge|Interlude|Solo|Breakdown|Outro|Tag|Refrain)( [1-9])?")
        val BANNED_WORDS = Regex("\\b(sing|sings|lyric|lyrics|words)\\b", RegexOption.IGNORE_CASE)
        val EXCUSES = listOf("borrowed", "secondary", "passing", "modal", "chromatic", "blues")

        /** Major scale, or for a minor key the natural minor with its raised seventh (the major five). */
        fun scaleOf(s: PopSong): Set<Int> =
            (if (s.minor) listOf(0, 2, 3, 5, 7, 8, 10, 11) else listOf(0, 2, 4, 5, 7, 9, 11)).map { Math.floorMod(s.key + it, 12) }.toSet()
    }
}
