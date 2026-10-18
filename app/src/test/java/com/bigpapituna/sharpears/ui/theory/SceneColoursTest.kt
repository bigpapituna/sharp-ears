package com.bigpapituna.sharpears.ui.theory

import androidx.compose.ui.graphics.Color
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.ui.LeftHandColor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2.2.1 QA: with colour notes on, a lesson scene lights each key in the colour the player's lane and keys give it. The
 * concert hall's first lesson said "C is a red cat and D an orange dog" over five keys all lit in the accent.
 */
class SceneColoursTest {
    private val accent = Color(0xFFC8693A)

    @Test fun litKeysTakeTheirNoteColourWithColourNotesOn() {
        for (midi in 48..84) for (lit in listOf(accent, LeftHandColor, accent.copy(alpha = 0.4f))) {
            val c = sceneNoteColour(midi, lit, colourNotes = true)
            assertEquals("key $midi alpha", lit.alpha, c.alpha, 0.01f)
            assertEquals("key $midi", Color(NoteColours.colour(midi)).copy(alpha = lit.alpha), c)
        }
        // C red, D orange, E yellow, F green, G teal: what the hall's scene says.
        assertEquals(listOf("red", "orange", "yellow", "green", "teal"), listOf(60, 62, 64, 65, 67).map { m ->
            NoteColours.WHITE.first { Color(it.argb) == sceneNoteColour(m, accent, true) }.name
        })
    }

    @Test fun withoutColourNotesAndForContextKeysNothingChanges() {
        for (midi in 48..84) {
            assertEquals(accent, sceneNoteColour(midi, accent, colourNotes = false))
            val dim = DimKey
            assertEquals("a dim context key stays dim", dim.copy(alpha = 0.5f), sceneNoteColour(midi, dim.copy(alpha = 0.5f), colourNotes = true))
        }
    }
}
