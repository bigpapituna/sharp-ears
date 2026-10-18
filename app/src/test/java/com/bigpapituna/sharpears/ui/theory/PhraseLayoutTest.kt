package com.bigpapituna.sharpears.ui.theory

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.theory.PhraseVisual
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.2.1 QA: a phrase on the staff never draws two notes over each other. On a phone the broken chords' two bars of
 * eighths put sixteen heads with their flags in a row narrower than they are; the staff now shrinks for dense phrases,
 * and stays big enough to read.
 */
class PhraseLayoutTest {
    private val phrases = COURSE.flatMap { it.lessons }.flatMap { l -> l.steps.flatMap { s -> s.scenes.mapNotNull { (it.visual as? PhraseVisual)?.let { v -> l.id to v } } } }

    @Test fun everyCoursePhraseFitsAPhoneWithRoomBetweenItsNotes() {
        assertTrue(phrases.size > 20)
        // A phone's lesson stage, in dp: 360 wide, less the screen's and the scene's margins.
        val width = 360f - 2 * 16f - 2 * 12f
        for ((id, v) in phrases) {
            val base = width / (if (v.hasRight && v.hasLeft) 22f else 20f)
            val weights = phraseOnsetWeights(v.notes.map { it.start }, v.span)
            val s = phraseStaffSpace(base, width, weights, v.keyFifths)
            val sig = kotlin.math.abs(v.keyFifths)
            val notesFrom = (3.4f + sig * 1.15f + (if (sig > 0) 0.8f else 0f) + 2.6f + 0.9f) * s
            val gap = weights.min() / weights.sum() * (width - notesFrom - 0.6f * s)
            // Room for a head and its flag; a very long phrase at the smallest staff (the whole Toccata opening) gets its heads touching, not stacked.
            val atFloor = s <= base * PHRASE_MIN_SCALE + 0.001f
            assertTrue("$id: notes ${gap / s} spaces apart", gap >= (if (atFloor) 1.1f else PHRASE_MIN_GAP) * s - 0.01f)
            assertTrue("$id: a staff space of $s dp is still readable", s >= 5f)
        }
    }

    @Test fun aSparsePhraseKeepsItsSize() {
        val weights = phraseOnsetWeights(listOf(0f, 1f, 2f, 3f), 4f)
        assertTrue(phraseStaffSpace(14f, 300f, weights, 0) == 14f)
    }
}
