package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.Mastery
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.parseChord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.14, "I know these chords": Marc already plays chords, and on day 2 Songs I can play and One new chord away were empty.
 * The pick offers the chords the catalogue uses most, and writes each to the mastery store at the known box.
 */
class KnownChordsTest {
    private val songs = POP_SONGS

    @Test fun theChoicesAreThePlainChordsTheMostSongsUse() {
        val picks = KnownChords.choices(songs)
        assertEquals(KnownChords.MAX, picks.size)
        assertTrue(picks.all { it.type == ChordType.MAJOR || it.type == ChordType.MINOR })
        assertEquals(picks.distinct(), picks)
        // The six first chords lead, in the task's order; then the rest by the songs that use each, so no chord left out is
        // used by more songs than one offered after the six.
        assertEquals(listOf("C", "G", "F", "Am", "Dm", "Em"), picks.take(6).map { it.name })
        fun uses(c: Chord) = songs.count { c in PopFind.chordsOf(it) }
        val counts = picks.drop(6).map(::uses)
        assertEquals(counts.sortedDescending(), counts)
        val all = songs.flatMap { PopFind.chordsOf(it) }.filter { it.type == ChordType.MAJOR || it.type == ChordType.MINOR }.toSet()
        assertTrue((all - picks.toSet()).all { uses(it) <= counts.last() })
        assertTrue("D, A and E come next", listOf("D", "A", "E").map { parseChord(it)!! }.all { it in picks.drop(6).take(5) })
    }

    @Test fun pickingFillsTheTwoLists() {
        val picked = listOf("C", "G", "F", "Am").map { parseChord(it)!! }.toSet()
        val now = 1_000_000L
        val mastery = picked.associate { KnownChords.id(it) to KnownChords.seed(null, now) }
        val known = PopFind.known(mastery)
        assertEquals(picked, known)
        assertTrue(PopFind.browse(songs, PopFind.Chip.ALL, PopFind.Sort.CAN_PLAY, known).isNotEmpty())
        assertTrue(PopFind.browse(songs, PopFind.Chip.ALL, PopFind.Sort.ONE_AWAY, known).isNotEmpty())
        assertTrue(PopFind.playableCount(songs, known) > 0)
        assertEquals("chord:Am", KnownChords.id(parseChord("Am")!!))
        assertEquals("chord:Bb", KnownChords.id(Chord(10, ChordType.MAJOR)))
    }

    @Test fun aSeedNeverLowersABox() {
        val now = 5_000L
        val fresh = KnownChords.seed(null, now)
        assertEquals(PopFind.KNOWN_BOX, fresh.box); assertTrue(fresh.seen); assertEquals(now + KnownChords.DUE_AFTER_MS, fresh.dueAt)
        val higher = Mastery(5, 42L, seen = true)
        assertSame(higher, KnownChords.seed(higher, now))
        assertEquals(PopFind.KNOWN_BOX, KnownChords.seed(Mastery(1, 9L, seen = true), now).box)
        assertEquals(PopFind.KNOWN_BOX, KnownChords.seed(Mastery(4, 9L, seen = false), now).box)
    }

    @Test fun offeredToSomeAndALotWithNothingKnown() {
        val none = emptySet<Chord>()
        assertEquals(listOf(false, false, true, true), Level.entries.map { KnownChords.offered(it, none, dismissed = false) })
        assertFalse(KnownChords.offered(Level.A_LOT, setOf(Chord(0, ChordType.MAJOR)), dismissed = false))
        assertFalse(KnownChords.offered(Level.A_LOT, none, dismissed = true))
        assertEquals("Pick a few to see the songs they play.", KnownChords.countLine(0))
        assertEquals("You can play 1 song with these.", KnownChords.countLine(1))
        assertEquals("You can play 12 songs with these.", KnownChords.countLine(12))
        for (l in listOf(KnownChords.TITLE, KnownChords.LINE, KnownChords.OFFER, KnownChords.OFFER_LINE, KnownChords.EMPTY_LINE))
            assertFalse(l, Regex("[–—!]").containsMatchIn(l))
    }
}
