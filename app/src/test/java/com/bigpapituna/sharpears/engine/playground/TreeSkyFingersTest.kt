package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Fingering
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.noteNumber
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.3 part B, fingers agree everywhere (as FingersAgreeTest for the course): what the treehouse's words say about
 * fingers is what its falling notes, its painted hand, the pup's finger calls and the which-finger rounds show. The
 * falling notes, the ghost hand and the hint hand read Fingering.of; the copy stage's painted hand and calls read the
 * same; every treehouse exercise writes its fingers out, so none of them is a guess.
 */
class TreeSkyFingersTest {
    private val tree = TREEHOUSE.lessons

    private fun lane(ex: Exercise, hand: Hand): List<Pair<Int, Int?>> {
        val song = ex.toSong("t", "t")
        val f = Fingering.of(song)
        return song.notes.filter { it.hand == hand }.map { it.midi to f[it] }
    }

    private fun exercises(id: String) = tree.first { it.id == id }.steps.mapNotNull { it.exercise }

    /** The fingers [hand] puts on key [name] across lesson [id]'s exercises. */
    private fun fingersOn(id: String, hand: Hand, name: String) =
        exercises(id).flatMap { lane(it, hand) }.filter { it.first == noteNumber(name) }.map { it.second }.toSet()

    @Test fun everyTreehouseExerciseWritesItsFingersOut() {
        for (l in tree) for (s in l.steps) {
            val ex = s.exercise ?: continue
            val song = ex.toSong("t", "t")
            val r = song.notes.count { it.hand == Hand.RIGHT }; val left = song.notes.count { it.hand == Hand.LEFT }
            // The hands the child plays (the band's hand is worked out like any song's).
            if (r > 0 && ex.practice != Practice.LEFT) assertEquals("${l.id} \"${s.text}\" right", r, ex.fingers.split(' ').count { it.isNotBlank() })
            if (left > 0 && ex.practice != Practice.RIGHT) assertEquals("${l.id} \"${s.text}\" left", left, ex.leftFingers.split(' ').count { it.isNotBlank() })
        }
    }

    @Test fun whatTheWordsSayIsWhatTheNotesShow() {
        // "Your thumb is one", "thumb on C, finger two on D, finger three on E".
        assertEquals(setOf(1), fingersOn("treehouse-1", Hand.RIGHT, "C4")); assertEquals(setOf(2), fingersOn("treehouse-1", Hand.RIGHT, "D4"))
        assertEquals(setOf(3), fingersOn("treehouse-1", Hand.RIGHT, "E4"))
        // "On the left, little finger five sits on C", "your thumb walks from G", "five sits on C" (the echo), the tune low down.
        for (id in listOf("treehouse-2", "treehouse-8", "treehouse-10", "treehouse-11")) assertEquals(id, setOf(5), fingersOn(id, Hand.LEFT, "C3"))
        for (id in listOf("treehouse-2", "treehouse-10", "treehouse-11")) assertEquals(id, setOf(1), fingersOn(id, Hand.LEFT, "G3"))
        // "Tuck your thumb under to F, then two plays G"; "finger three crosses over your thumb to E".
        assertEquals(setOf(1), fingersOn("treehouse-3", Hand.RIGHT, "F4")); assertEquals(setOf(2), fingersOn("treehouse-3", Hand.RIGHT, "G4"))
        assertEquals(setOf(3), fingersOn("treehouse-3", Hand.RIGHT, "E4"))
        // "Four plays F and five plays G."
        assertEquals(setOf(4), fingersOn("treehouse-4", Hand.RIGHT, "F4")); assertEquals(setOf(5), fingersOn("treehouse-4", Hand.RIGHT, "G4"))
        // "Thumb on C, three on E, five on G."
        assertEquals(setOf(1), fingersOn("treehouse-5", Hand.RIGHT, "C4")); assertEquals(setOf(3), fingersOn("treehouse-5", Hand.RIGHT, "E4"))
        assertEquals(setOf(5), fingersOn("treehouse-5", Hand.RIGHT, "G4"))
        // "Put your thumb on G": G A B C D under one to five.
        for ((k, f) in listOf("G4", "A4", "B4", "C5", "D5").zip(1..5)) assertEquals(k, setOf(f), fingersOn("treehouse-6", Hand.RIGHT, k))
        // "Your fourth finger finds a black key. It is called B flat."
        assertEquals(setOf(4), fingersOn("treehouse-7", Hand.RIGHT, "Bb4")); assertEquals(setOf(1), fingersOn("treehouse-7", Hand.RIGHT, "F4"))
        // "Two, three, four!"
        assertEquals(setOf(2), fingersOn("treehouse-9", Hand.RIGHT, "D4")); assertEquals(setOf(4), fingersOn("treehouse-9", Hand.RIGHT, "F4"))
        // Hot Cross Buns with both hands: the left little finger on low C, the thumb on G.
        assertEquals(setOf(5), fingersOn("treehouse-12", Hand.LEFT, "C3")); assertEquals(setOf(1), fingersOn("treehouse-12", Hand.LEFT, "G3"))
        assertEquals(setOf(1), exercises("treehouse-12").first().let { ex -> lane(ex, Hand.RIGHT).filter { it.first == 60 }.map { it.second }.toSet() })
    }

    /** The which-finger rounds press each key with the finger its exercise (and so the player) gives it. */
    @Test fun theHandPressesWithTheExercisesFinger() {
        for (l in tree) for (s in l.steps) {
            val game = StageGames.of(s) as? StageGame.Tap ?: continue
            val ex = s.exercise!!
            val hand = if (ex.left.isNotBlank()) Hand.LEFT else Hand.RIGHT
            val lane = lane(ex, hand)
            assertEquals(lane.map { it.first }, game.rounds.map { it.play })
            assertEquals(lane.map { it.second }, game.rounds.map { it.parts.single().single().finger })
            assertTrue(game.rounds.all { r -> r.parts.single().single().right == (hand == Hand.RIGHT) })
            // The example agrees with the lessons' words (E under three; low C under five).
            val ex0 = game.example.parts.single().single()
            assertEquals(if (hand == Hand.RIGHT) 3 else 5, ex0.finger)
            assertEquals(if (hand == Hand.RIGHT) "E4" else "C3", noteName(ex0.keys.single()))
        }
    }

    /** The pup's calls in the copy games say the fingers the notes show, phrase by phrase. */
    @Test fun theCallsSayTheFingersTheNotesShow() {
        for (l in tree) for (s in l.steps) {
            if (StageGames.of(s) !is StageGame.Copy) continue
            val ex = s.exercise!!
            val song = ex.toSong("t", "t")
            val f = Fingering.of(song)
            val phrases = TreeSkyLines.phrases(song)
            assertEquals(phrases.map { p -> TreeSkyLines.fingerCall(p.map { f.getValue(it) }) }, TreeSkyLines.calls(ex))
            // Each phrase stays in one hand, so the painted hand that plays it is the one the fingers belong to.
            assertTrue(l.id, phrases.all { p -> p.map { it.hand }.toSet().size == 1 })
        }
    }
}
