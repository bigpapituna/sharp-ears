package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.practice.warmupCatalog
import com.bigpapituna.sharpears.engine.skin.Bites
import com.bigpapituna.sharpears.engine.theory.FingerNumbersVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.2.1 QA: the fingers the app shows agree with the fingers it asks for. Roy saw the hint hand press with finger 2 on a
 * Mary note labelled 4; the same class of error was all over the course: a scene or a caption named one finger and the
 * falling notes (and so the ghost hand and the hint hand, which read the same [Fingering.of]) showed another ("Low C with
 * your little finger" on a C marked 1; "Two octaves use 1 2 3, 1 2 3 4" over 1 2 3 4 5; the Practice tab's left-hand
 * scales by the book, the player's worked out).
 */
class FingersAgreeTest {
    private val lessons = COURSE.flatMap { it.lessons }

    /** What the player shows for one hand of [ex]: each note's key and finger, in time order. */
    private fun lane(ex: Exercise, hand: Hand): List<Pair<Int, Int?>> {
        val song = ex.toSong("t", "t")
        val f = Fingering.of(song)
        return song.notes.filter { it.hand == hand }.map { it.midi to f[it] }
    }

    /** Every exercise a lesson plays in the player, as a grown-up and as a kid (the bite). */
    private fun exercises(l: CourseLesson) = l.steps.filter { it.exercise != null && it.game == null }.flatMap { s ->
        listOfNotNull(s.exercise, Bites.kidStep(s).exercise)
    }

    @Test fun everyFingerNumbersSceneShowsTheFingersItsLessonPlays() {
        val failures = mutableListOf<String>()
        for (l in lessons) for (step in l.steps) for (scene in step.scenes) {
            val v = scene.visual as? FingerNumbersVisual ?: continue
            for (right in listOf(true, false)) {
                val hand = if (right) Hand.RIGHT else Hand.LEFT
                // The scene's single presses of this hand, in order: the line it teaches.
                val taught = v.steps.mapNotNull { s -> s.presses.filter { it.right == right }.singleOrNull() }.map { it.key to it.finger }
                if (taught.isEmpty()) continue
                // Wherever an exercise of the lesson plays the scene's line, it plays it with the scene's fingers. (Shorter
                // stretches of it are not compared: a tune may take the same three keys in another hand position.)
                for (ex in exercises(l)) for (window in lane(ex, hand).windowed(taught.size)) {
                    if (window.map { it.first } == taught.map { it.first } && window.map { it.second } != taught.map { it.second })
                        failures += "${l.id}: \"${scene.caption}\" plays ${taught.map { noteName(it.first) + "/" + it.second }}, the notes show ${window.map { it.second }}"
                }
            }
        }
        assertTrue(failures.distinct().joinToString("\n"), failures.isEmpty())
    }

    /** What a lesson's words say about fingers, checked against every exercise of the lesson (the whole and the bite). */
    @Test fun whatTheWordsSayAboutFingersIsWhatTheNotesShow() {
        fun lesson(id: String) = lessons.first { it.id == id }
        fun fingersOn(id: String, hand: Hand, pc: Int) =
            exercises(lesson(id)).flatMap { lane(it, hand) }.filter { Math.floorMod(it.first, 12) == pc }.map { it.second }.toSet()
        // "Low C with your little finger", "Its little finger sits on the C below the middle", "Your left little finger holds low C".
        for (id in listOf("playground-9", "pond-7", "pond-8", "hall-5", "hall-10", "fingers-left")) assertEquals(id, setOf(5), fingersOn(id, Hand.LEFT, 0))
        // "Play middle C with your right thumb", "tap it on middle C with your thumb".
        for (id in listOf("sit", "rhythm-quarters")) assertEquals(id, setOf(1), lane(exercises(lesson(id)).first(), Hand.RIGHT).map { it.second }.toSet())
        // "Fingers 1, 3 and 5 play it" (triads); "Your thumb, middle finger and little finger press together" (the pond's chord).
        for (id in listOf("triads", "pond-9")) for (ex in exercises(lesson(id))) {
            val song = ex.toSong("t", "t"); val f = Fingering.of(song)
            song.groups.filter { g -> g.notes.size == 3 }.forEach { g ->
                assertEquals("$id ${g.notes.map { noteName(it.midi) }}", listOf(1, 3, 5), g.notes.sortedBy { it.midi }.map { f[it] })
            }
        }
        // The key lessons and five-finger positions name the finger on their black key.
        assertEquals(setOf(4), fingersOn("five-f", Hand.RIGHT, 10)); assertEquals(setOf(2), fingersOn("five-f", Hand.LEFT, 10))
        assertEquals(setOf(3), fingersOn("five-d", Hand.RIGHT, 6)); assertEquals(setOf(3), fingersOn("five-d", Hand.LEFT, 6))
        assertEquals(setOf(3), fingersOn("five-a", Hand.RIGHT, 1)); assertEquals(setOf(3), fingersOn("five-a", Hand.LEFT, 1))
        assertEquals(setOf(3), fingersOn("black-keys", Hand.RIGHT, 6))     // "F sharp under your third finger"
        // "Two octaves use the fingering 1 2 3, 1 2 3 4, 1 2 3, 1 2 3 4 5."
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 4, 1, 2, 3, 1, 2, 3, 4, 5), lane(exercises(lesson("scale-two")).first(), Hand.RIGHT).map { it.second })
        // "The thumb tucks under after G."
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 5), lane(exercises(lesson("arpeggios")).first(), Hand.RIGHT).map { it.second }.take(7))
        // Hanon: "up to finger 5 and back down to finger 2", every group.
        assertEquals(List(4) { listOf(1, 2, 3, 4, 5, 4, 3, 2) }.flatten(), lane(exercises(lesson("hanon-1")).first(), Hand.RIGHT).map { it.second })
        // "Here the hand stays still over each chord: bottom, middle, top, middle."
        assertEquals(List(7) { listOf(1, 3, 5, 3) }.flatten() + 1, lane(exercises(lesson("t2-broken")).first(), Hand.RIGHT).map { it.second })
        // "Your hand opens wide, from finger 5 to finger 1" (root, fifth, octave: 5 2 1).
        assertEquals(List(4) { listOf(5, 2, 1) }.flatten(), lane(exercises(lesson("lh-pop")).first(), Hand.LEFT).map { it.second })
        // "Slide your hand up for it" (E, I, E, I, O): the thumb comes back to C.
        assertEquals(listOf(4, 4, 4, 1, 2, 2, 1, 3, 3, 2, 2, 1), lane(MACDONALD_X, Hand.RIGHT).map { it.second })
        // "Your thumb reaches down to the G below middle C, then comes back up" (and down to B at the end).
        assertEquals(listOf(1, 1, 2, 3, 1, 3, 2, 1, 1, 1, 2, 3, 1, 1), lane(YANKEE_X, Hand.RIGHT).map { it.second })
        // "Keep your right hand in the C position: thumb on C, little finger on G" (Ode to Joy, wherever it is played).
        assertEquals(listOf(3, 3, 4, 5, 5, 4, 3, 2, 1, 1, 2, 3, 3, 2, 2), lane(ODE_LINE, Hand.RIGHT).map { it.second })
    }

    @Test fun aKidsBiteShowsTheFingersOfTheWholeLine() {
        for (l in lessons) for (s in l.steps) {
            val ex = s.exercise ?: continue
            val bite = Bites.kidStep(s).exercise ?: continue
            if (bite == ex || s.game != null) continue
            for (hand in Hand.entries) {
                val part = lane(bite, hand)
                assertEquals("${l.id} $hand", lane(ex, hand).take(part.size).map { it.second }, part.map { it.second })
            }
        }
        // The course's own bites (Exercise.bite) too: Lightly Row's first phrase put G under 4, the whole line under 5.
        for (ex in listOf(TWINKLE, MARY_X, BUNS_X, ODE_LINE, LIGHTLY_X.copy(phraseEnd = 8.0), YANKEE_X.copy(phraseEnd = 8.0), MACDONALD_X.copy(phraseEnd = 8.0)))
            for (hand in Hand.entries) {
                val part = lane(ex.bite(), hand)
                assertEquals("${ex.right} $hand", lane(ex, hand).take(part.size).map { it.second }, part.map { it.second })
            }
    }

    @Test fun bookFingersCoverEveryNoteOfTheirHand() {
        val all = lessons.flatMap { l -> l.steps.mapNotNull { it.exercise }.map { l.id to it } } +
            (0..11).flatMap { warmupCatalog(it) }.map { it.id to it.exercise }
        for ((id, ex) in all) {
            val song = ex.toSong("t", "t")
            if (ex.fingers.isNotBlank()) assertEquals("$id right", song.notes.count { it.hand == Hand.RIGHT }, ex.fingers.split(" ").count { it.isNotBlank() })
            if (ex.leftFingers.isNotBlank()) assertEquals("$id left", song.notes.count { it.hand == Hand.LEFT }, ex.leftFingers.split(" ").count { it.isNotBlank() })
        }
    }
}
