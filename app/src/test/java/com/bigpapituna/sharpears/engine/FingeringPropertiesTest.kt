package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * Audit (docs/AUDIT-ENGINE.md): what Fingering.of works out is something a hand can play. Seeded, deterministic.
 * Book fingers (Exercise.fingers, the Practice tab's tables) are checked against their own words elsewhere
 * (FingersAgreeTest, ScalesTest); these are the fingers the app works out by itself.
 */
class FingeringPropertiesTest {
    private fun line(hand: Hand, midis: List<Int>): Song {
        val notes = midis.mapIndexed { i, m -> Note(m, i * 400L, 350, hand, 80) }
        return Song("f", "f", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), midis.size * 400L)
    }

    /** Single notes one after another, per hand, with the fingers given them. */
    private fun steps(song: Song, hand: Hand, f: Map<Note, Int>): List<Pair<Note, Note>> =
        groupOnsets(song.notes.filter { it.hand == hand }).zipWithNext()
            .filter { (a, b) -> a.notes.size == 1 && b.notes.size == 1 && f[a.notes[0]] != null && f[b.notes[0]] != null }
            .map { (a, b) -> a.notes[0] to b.notes[0] }

    /** The library and every lesson's worked-out hands: never the same finger on two different keys in a row. */
    @Test fun noFingerPlaysTwoKeysInARow() {
        val songs = File("src/main/assets/songs").listFiles { f -> f.extension == "mid" }!!.sortedBy { it.name }
            .map { SmfParser.parse(it.readBytes(), it.nameWithoutExtension, it.nameWithoutExtension) } +
            COURSE.flatMap { it.lessons }.flatMap { l -> l.steps.mapNotNull { it.exercise?.toSong(l.id, l.title) } } +
            (1..2).flatMap { level -> (0L until 30L).map { sightPiece(level, it).exercise.toSong("sight", "sight") } }
        var checked = 0
        for (s in songs) {
            val f = Fingering.of(s)
            for (hand in Hand.entries) {
                if ((if (hand == Hand.RIGHT) s.fingering else s.leftFingering) != null) continue        // book fingers
                for ((a, b) in steps(s, hand, f)) {
                    checked++
                    assertTrue("${s.id} $hand: ${noteName(a.midi)} and ${noteName(b.midi)} both with ${f[a]}", a.midi == b.midi || f[a] != f[b])
                }
            }
        }
        assertTrue(checked > 5_000)
    }

    /**
     * The left hand is the right hand mirrored: a left-hand line fingers as the right hand's mirror image (around D,
     * where the keyboard's black keys mirror too), finger for finger.
     */
    @Test fun theLeftHandIsTheRightHandMirrored() {
        val rnd = Random(5)
        repeat(300) {
            var m = 50 + rnd.nextInt(12)
            val left = List(12) { m.also { m = (m + rnd.nextInt(7) - 3).coerceIn(36, 72) } }
            val right = left.map { 124 - it }                                                   // mirrored around D4
            val fl = line(Hand.LEFT, left).let { s -> Fingering.of(s).let { f -> s.notes.map { f[it] } } }
            val fr = line(Hand.RIGHT, right).let { s -> Fingering.of(s).let { f -> s.notes.map { f[it] } } }
            assertEquals("$left", fr, fl)
        }
    }

    /**
     * A lesson's tune that fits a five-finger position shows one finger per key, as the C position teaches. Worked out,
     * Row, Row, Row Your Boat (pond-2, pond-12, rep-row, the concert shelf) put D and E under 1 and 2 halfway through and
     * F under 3, and the concert hall's echo of Ode to Joy put E under 2 and 3. (Library songs are worked out by the same
     * solver, which can still move the hand inside five keys: When the Saints; see docs/AUDIT-ENGINE.md.)
     */
    @Test fun aLessonTuneInsideFiveKeysTakesOneFingerPerKey() {
        val positions = (0..11).flatMap { r -> listOf(listOf(0, 2, 4, 5, 7), listOf(0, 2, 3, 5, 7)).map { s -> s.map { (it + r) % 12 }.toSet() } }
        var checked = 0
        for (lesson in COURSE.flatMap { it.lessons }) for ((i, step) in lesson.steps.withIndex()) {
            val ex = step.exercise ?: continue
            val song = ex.toSong(lesson.id, lesson.title)
            if (song.groups.any { it.anyOf }) continue
            val f = Fingering.of(song)
            for (hand in Hand.entries) {
                val notes = song.notes.filter { it.hand == hand }
                if (notes.size < 4) continue
                val low = notes.minOf { it.midi }
                if (notes.maxOf { it.midi } - low > 7 || positions.none { p -> low % 12 in p && notes.all { it.midi % 12 in p } }) continue
                checked++
                val byKey = notes.groupBy({ it.midi }, { f[it] }).mapValues { it.value.toSet() }
                assertTrue("${lesson.id} step $i $hand: $byKey", byKey.values.all { it.size == 1 })
            }
        }
        assertTrue("$checked", checked > 30)
    }
}
