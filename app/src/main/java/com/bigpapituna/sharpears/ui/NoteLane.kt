package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.LaneGeometry
import com.bigpapituna.sharpears.engine.LOOKAHEAD_MS
import com.bigpapituna.sharpears.engine.LOOKBEHIND_MS
import com.bigpapituna.sharpears.engine.Note
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.ui.skin.WrongFlash
import com.bigpapituna.sharpears.ui.skin.colourNotesOn
import com.bigpapituna.sharpears.ui.skin.drawAnimal
import com.bigpapituna.sharpears.ui.skin.drawNoteColour
import com.bigpapituna.sharpears.ui.skin.drawWrongCap
import com.bigpapituna.sharpears.ui.skin.drawWrongColumn
import com.bigpapituna.sharpears.ui.skin.inkOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import com.bigpapituna.sharpears.ui.skin.rememberFlashClock

val LaneBackground = Color(0xFF110F0D)
private val LaneBottom = Color(0xFF1B1714)
private val LaneGuide = Color(0xFF221D19)
private val HitLine = Color(0xFFE9E2DA)
private val LabelInk = Color(0xFF15100C)
private val BeatLine = Color(0xFF2A241F)
private val BeatLineBar = Color(0xFF3E352D)

/** How long the spark at the hit line lasts after a note starts. */
private const val SPARK_MS = 220.0

/** Letter on a falling note: every C carries its octave (C4) so the eye can find its place; others are just the name. */
private fun label(midi: Int, flats: Boolean) = noteName(midi, flats).let { if (midi % 12 == 0) it else it.filter { c -> !c.isDigit() } }

/**
 * Falling notes for [song] at [positionMs]. Columns line up with a [Keyboard] of the same width and range.
 * Each block shows its note name and, below it, the suggested finger ([fingers]); a note glows while it sounds
 * and sparks where it meets the keyboard. Vertical drags scrub: dragging down moves time forward.
 * [lights] adds the hit feedback on top: flashes, sparks, the combo counter and Perform grade words.
 *
 * Colour notes (2.1, ui/skin): with [colours] each block takes its Boomwhacker colour (black keys as stripes of their
 * two neighbours) with a thin hand-colour edge where it meets the keys, and the label is the letter for a [reader] or
 * the key's animal for a kid who cannot read yet. [wrong] lights a wrong key's column yellow or red for a moment.
 */
@Composable
fun NoteLane(
    song: Song,
    positionMs: Double,
    lowMidi: Int,
    highMidi: Int,
    onScrubStart: () -> Unit,
    onScrub: (deltaMs: Double) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: (Note) -> Boolean = { false },
    fingers: Map<Note, Int> = emptyMap(),
    showNames: Boolean = true,
    showFingers: Boolean = true,
    /** Notes starting before this time are not drawn (behind a seek point, during the lead-in). */
    hideBefore: Double? = null,
    /** Hit-line feedback (flash on a correct note, pulse on a wrong one), drawn over the notes. */
    lights: HitLights? = null,
    /** "Hear my run": the player's own notes, drawn as thin bright blocks over the song's (sorted by start). */
    played: List<Note>? = null,
    /** Faint lines across the lane at each beat (song ms per beat), stronger on each bar's first ([beatsPerBar]). */
    beatMs: Double? = null,
    beatsPerBar: Int = 4,
    /** Boomwhacker colours on the notes: follows the profile (kids on, grown-ups by Settings) unless a screen says. */
    colours: Boolean = colourNotesOn(),
    /** Letters on coloured notes; false draws the key's animal instead (kids who cannot read yet). */
    reader: Boolean = com.bigpapituna.sharpears.ui.family.reader(),
    /** Wrong keys shown: each one's column glows yellow or red for about 600 ms with a short shake. */
    wrong: WrongFlash? = null,
) {
    val playedLongest = remember(played) { played?.maxOfOrNull { it.durationMs } ?: 0L }
    val still = reducedMotion()
    val lightText = rememberTextMeasurer()
    if (lights != null) { lights.still = still; lights.measurer = lightText }
    // The key geometry, rebuilt only when the size or range changes (not every frame).
    val layoutCache = remember { arrayOfNulls<KeyboardLayout>(1) }
    // Ticks the lights' clock only while one is alive: the Canvas redraws, nothing recomposes.
    if (lights != null) LaunchedEffect(lights) {
        snapshotFlow { lights.added.intValue }.collect {
            while (withFrameNanos { now -> lights.prune(now).also { lights.clock.longValue = now } }) Unit
        }
    }
    val longest = remember(song) { song.notes.maxOfOrNull { it.durationMs } ?: 0L }
    val scrubDelta by rememberUpdatedState(onScrub)
    val scrubStart by rememberUpdatedState(onScrubStart)
    val scrubEnd by rememberUpdatedState(onScrubEnd)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // Labels are measured once per text and size, then reused every frame.
    val texts = remember(measurer) { HashMap<String, TextLayoutResult>() }
    val animalPath = remember { androidx.compose.ui.graphics.Path() }
    val wrongClock = rememberFlashClock(wrong)

    Canvas(
        modifier.clipToBounds().pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { scrubStart() },
                onDragEnd = { scrubEnd() },
                onDragCancel = { scrubEnd() },
            ) { change, dy ->
                change.consume()
                scrubDelta(dy / LaneGeometry(size.height.toFloat()).pxPerMs.toDouble())
            }
        }
    ) {
        drawRect(Brush.verticalGradient(listOf(LaneBackground, LaneBottom)))
        val keys = layoutCache[0]?.takeIf { it.width == size.width && it.height == size.height && it.lowMidi == lowMidi && it.highMidi == highMidi }
            ?: KeyboardLayout(lowMidi, highMidi, size.width, size.height).also { layoutCache[0] = it }
        val lane = LaneGeometry(size.height)
        val whiteW = keys.whiteKeys.firstOrNull()?.let { it.right - it.left } ?: 20f
        val radius = minOf(6.dp.toPx(), whiteW * 0.22f)
        val namePx = (whiteW * 0.42f).coerceIn(9.dp.toPx(), 15.dp.toPx())
        val fingerPx = (whiteW * 0.38f).coerceIn(9.dp.toPx(), 13.dp.toPx())
        fun text(s: String, px: Float, color: Color, bold: Boolean = true) = texts.getOrPut("$s|$px|${color.value}") {
            measurer.measure(s, TextStyle(color = color, fontSize = (px / density / fontScale).sp, fontFamily = Geist,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium))
        }

        // Beat lines: where each beat will meet the hit line, the first of each bar a little stronger.
        if (beatMs != null && beatMs > 0) {
            var b = kotlin.math.ceil((positionMs - LOOKBEHIND_MS) / beatMs).toLong()
            while (b * beatMs <= positionMs + LOOKAHEAD_MS) {
                val y = lane.hitLineY - ((b * beatMs - positionMs) * lane.pxPerMs).toFloat()
                val bar = Math.floorMod(b, beatsPerBar.toLong()) == 0L
                drawLine(if (bar) BeatLineBar else BeatLine, Offset(0f, y), Offset(size.width, y), strokeWidth = if (bar) 3f else 2f)
                b++
            }
        }
        // Wrong keys: the column of each glows in its tone (yellow near, red far) under the notes, shaking with its key.
        val wrongNow = wrongClock.longValue
        if (wrong != null && wrongNow != 0L) wrong.forEachLit(wrongNow, still) { m, g, tone ->
            if (m in lowMidi..highMidi) keys.rect(m).let { k -> drawWrongColumn(k.left, k.right, lane.hitLineY, g, tone, wrong.shakePx(m, wrongNow, still, (k.right - k.left) * 0.12f)) }
        }
        // Faint guides at each C so the eye can find octaves.
        keys.whiteKeys.filter { it.midi % 12 == 0 }.forEach {
            drawLine(LaneGuide, Offset(it.left, 0f), Offset(it.left, size.height), strokeWidth = 2f)
        }
        for (n in lane.visible(song, positionMs, longest)) {
            if (n.midi !in lowMidi..highMidi) continue
            if (hideBefore != null && n.startMs < hideBefore) continue
            val k = keys.rect(n.midi)
            val black = isBlack(n.midi)
            val inset = if (black) 1.5f else (k.right - k.left) * 0.09f
            val left = k.left + inset
            val w = k.right - k.left - 2 * inset
            val bottom = lane.bottomY(n, positionMs)
            val hand = if (n.hand == Hand.RIGHT) RightHandColor else LeftHandColor
            val faded = dimmed(n)                            // the hand the app plays for you
            val alpha = if (faded) 0.28f else 1f
            // In colour the block is the note's colour (its glow and spark too); the hand shows as a thin edge.
            val tint = if (colours) noteColour(n.midi) else hand
            val ink = (if (colours) inkOn(n.midi) else LabelInk).copy(alpha = if (faded) 0.45f else if (colours) 1f else 0.88f)
            // A pre-reader's coloured white key carries its animal instead of a letter (black keys: nothing, the stripes say it).
            val animal = if (colours && showNames && !reader) NoteColours.animal(n.midi) else null
            val iconPx = if (animal != null) minOf(w * 0.72f, 40.dp.toPx()) else 0f
            val labelText = song.spellings[n.midi % 12] ?: label(n.midi, song.preferFlats)
            // Names shrink to fit narrow (black-key) blocks: "D#" must stay inside its column.
            val name = if (showNames && (!colours || reader)) text(labelText, namePx, ink).let { t0 ->
                if (t0.size.width <= w - 2f) t0 else text(labelText, (namePx * (w - 2f) / t0.size.width).coerceAtLeast(7.dp.toPx()), ink)
            } else null
            val finger = if (showFingers && !faded) fingers[n]?.let { text("$it", fingerPx, LabelInk.copy(alpha = 0.6f), bold = false) } else null
            // Short notes still get a block tall enough for their letter and finger (it grows upward, like Artie's).
            val minH = (name?.size?.height ?: 0) + (finger?.size?.height ?: 0) + iconPx + 8f
            val top = minOf(lane.topY(n, positionMs) + 3f, bottom - minH)   // gap so repeated notes on one key stay separate
            val h = bottom - top
            val base = (if (black) hand.copy(red = hand.red * 0.82f, green = hand.green * 0.82f, blue = hand.blue * 0.82f) else hand).copy(alpha = alpha)
            val sounding = n.startMs <= positionMs && positionMs < n.startMs + n.durationMs
            val r = CornerRadius(radius)

            if (sounding && !faded) {
                val g = 4.dp.toPx()
                drawRoundRect(tint.copy(alpha = 0.30f), Offset(left - g, top - g), Size(w + 2 * g, h + 2 * g), CornerRadius(radius + g))
            }
            if (colours) drawNoteColour(n.midi, Offset(left, top), Size(w, h), r, alpha)
            else drawRoundRect(base, Offset(left, top), Size(w, h), r)
            // Light from above: a soft sheen on the top of the block and a bright leading edge at the bottom
            // (in colour, the edge is the hand's colour, so two hands still read apart).
            drawRoundRect(Color.White.copy(alpha = 0.16f * alpha), Offset(left, top), Size(w, minOf(h * 0.5f, 14.dp.toPx())), r)
            if (colours) drawRoundRect(hand.copy(alpha = alpha), Offset(left, bottom - 4f), Size(w, 4f), CornerRadius(2f))
            else drawRoundRect(Color.White.copy(alpha = 0.40f * alpha), Offset(left, bottom - 3f), Size(w, 3f), CornerRadius(1.5f))
            // Letter (or animal) at the bottom, where the note is played; the suggested finger just above it.
            var y = bottom - if (colours) 6f else 4f
            if (animal != null) {
                y -= iconPx
                drawAnimal(animal, Offset(left + w / 2f, y + iconPx / 2f), iconPx, ink, noteColour(n.midi).copy(alpha = alpha), animalPath)
            }
            name?.let {
                y -= it.size.height
                // On stripes the letter sits on a small badge of the blend, so it reads over both colours.
                if (colours && black) drawRoundRect(noteColour(n.midi).copy(alpha = alpha), Offset(left + 1f, y - 1f), Size(w - 2f, it.size.height + 2f), CornerRadius(4f))
                drawText(it, topLeft = Offset(left + (w - it.size.width) / 2f, y))
            }
            finger?.let { y -= it.size.height - 2f; drawText(it, topLeft = Offset(left + (w - it.size.width) / 2f, y)) }
            // Spark where the note meets the keyboard.
            val since = positionMs - n.startMs
            if (!faded && since in 0.0..SPARK_MS) {
                val fade = (1 - since / SPARK_MS).toFloat()
                val c = Offset((k.left + k.right) / 2f, lane.hitLineY - 2f)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * fade), tint.copy(alpha = 0.5f * fade), Color.Transparent),
                    center = c, radius = whiteW * 1.1f), radius = whiteW * 1.1f, center = c)
            }
        }
        if (played != null) {
            var i = com.bigpapituna.sharpears.engine.firstStartAtOrAfter(played, positionMs - LOOKBEHIND_MS - playedLongest)
            while (i < played.size && played[i].startMs < positionMs + LOOKAHEAD_MS) {
                val n = played[i++]
                if (n.midi !in lowMidi..highMidi) continue
                val k = keys.rect(n.midi)
                val w = (k.right - k.left) * 0.36f
                val bottom = lane.bottomY(n, positionMs)
                val top = lane.topY(n, positionMs)
                val x = (k.left + k.right) / 2f - w / 2f
                val now = n.startMs <= positionMs && positionMs < n.startMs + n.durationMs
                if (now) drawRoundRect(PlayedInk.copy(alpha = 0.25f), Offset(x - 4f, top - 4f), Size(w + 8f, bottom - top + 8f), CornerRadius(w))
                drawRoundRect(PlayedInk.copy(alpha = if (now) 1f else 0.9f), Offset(x, top), Size(w, bottom - top), CornerRadius(w / 2f))
            }
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, RightHandColor.copy(alpha = 0.18f)), startY = lane.hitLineY - 36f, endY = lane.hitLineY),
            Offset(0f, lane.hitLineY - 36f), Size(size.width, 36f))
        drawLine(HitLine, Offset(0f, lane.hitLineY - 2f), Offset(size.width, lane.hitLineY - 2f), strokeWidth = 4f)
        // Wrong keys: a bright cap on the hit line over each one's column, shaking with its key.
        if (wrong != null && wrongNow != 0L) wrong.forEachLit(wrongNow, still) { m, g, tone ->
            if (m in lowMidi..highMidi) keys.rect(m).let { k -> drawWrongCap(k.left, k.right, lane.hitLineY, g, tone, wrong.shakePx(m, wrongNow, still, (k.right - k.left) * 0.12f)) }
        }
        lights?.run { draw(keys, lane.hitLineY, lowMidi, highMidi) }
    }
}
