package com.bigpapituna.sharpears.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.engine.skin.WrongTone
import com.bigpapituna.sharpears.ui.family.colourNotes

/**
 * Colour notes on or off for what is drawn below. Null (the default) follows the profile ([colourNotes]: on for kids,
 * Settings for grown-ups); a screen where colours would give an answer away can provide false.
 */
val LocalColourNotes = compositionLocalOf<Boolean?> { null }

/** Whether NoteLane and Keyboard draw Boomwhacker colours here. */
@Composable @ReadOnlyComposable
fun colourNotesOn(): Boolean = LocalColourNotes.current ?: colourNotes()

/** A white key's colour; a black key's blend (its stripes come from [stripeColours]). */
fun noteColour(midi: Int): Color = Color(NoteColours.colour(midi))

/** A black key's (lower, upper) stripe colours, or null for a white key. */
fun stripeColours(midi: Int): Pair<Color, Color>? = NoteColours.stripes(midi)?.let { (a, b) -> Color(a) to Color(b) }

/** The letter ink with the better contrast on [midi]'s colour (on a black key, on the blend of its stripes). */
fun inkOn(midi: Int): Color = Color(NoteColours.inkFor(NoteColours.colour(midi)))

/** A slip to a neighbour: warm yellow, brighter than the E key's so the glow reads as light, not as a note. */
val WrongNear = Color(0xFFFFD24A)
/** Further off: red, warm enough to sit on the stage. */
val WrongFar = Color(0xFFFF5247)

val WrongTone.colour: Color get() = if (this == WrongTone.NEAR) WrongNear else WrongFar

/**
 * A rounded block in diagonal stripes of [a] and [b] (a black key's two neighbours), each stripe [stripe] px wide.
 * One repeating hard-stop gradient anchored at the block's corner, so the stripes travel with a falling note and a
 * frame needs no clipping (cheap on the stage, which redraws every frame while a song plays).
 */
fun DrawScope.drawStriped(a: Color, b: Color, topLeft: Offset, size: Size, corner: CornerRadius, stripe: Float, alpha: Float = 1f) {
    val brush = Brush.linearGradient(0f to a, 0.5f to a, 0.5f to b, 1f to b,
        start = topLeft, end = Offset(topLeft.x + stripe * 1.4142f, topLeft.y + stripe * 1.4142f), tileMode = TileMode.Repeated)
    drawRoundRect(brush, topLeft, size, corner, alpha = alpha)
}

/** A note's body: its colour, or stripes for a black key. */
fun DrawScope.drawNoteColour(midi: Int, topLeft: Offset, size: Size, corner: CornerRadius, alpha: Float = 1f) {
    val s = stripeColours(midi)
    if (s == null) drawRoundRect(noteColour(midi), topLeft, size, corner, alpha = alpha)
    else drawStriped(s.first, s.second, topLeft, size, corner, stripe = (minOf(size.width, size.height) * 0.22f).coerceIn(3f, 14f), alpha = alpha)
}
