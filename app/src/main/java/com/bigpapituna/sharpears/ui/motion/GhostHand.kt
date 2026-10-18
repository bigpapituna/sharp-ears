package com.bigpapituna.sharpears.ui.motion

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.Note
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.ui.Geist
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.flow.first

/** One note for the ghost hand: which key, which hand and finger (1 thumb .. 5 little; null = not known), when. */
data class GhostNote(val midi: Int, val hand: Hand, val finger: Int?, val startMs: Long, val durationMs: Long)

/** The song's notes for [GhostHand], with fingers from engine/Fingering ([fingers]); [include] keeps the hands being practised. */
fun ghostNotes(song: Song, fingers: Map<Note, Int>, include: (Note) -> Boolean = { true }): List<GhostNote> =
    song.notes.filter(include).map { GhostNote(it.midi, it.hand, fingers[it], it.startMs, it.durationMs) }.sortedBy { it.startMs }

/** A warm skin tone for the ghost hand (drawn at 60%, so it reads as a hint, not a hand in the way). */
val GhostSkin = Color(0xFFD9A47C)
private val GhostEdge = Color(0xFF8E5F3E)
private val GhostShadow = Color(0xFF2A1A10)

/** The finger starts going down this long before its note reaches the line. */
internal const val LEAD_MS = 240.0
/** A finger stays down at most this long, so long notes do not pin the hand. */
internal const val HOLD_MS = 380.0
internal const val RELEASE_MS = 140.0
/** The hand fades in when its next note is this close, and out when nothing is coming. */
internal const val SHOW_AHEAD_MS = 3200.0

/** Where finger [finger] sits relative to the middle finger, in white keys (the right hand's thumb is on the left). */
internal fun fingerOffset(finger: Int, hand: Hand): Float = (finger - 3) * if (hand == Hand.RIGHT) 1f else -1f

internal fun holdOf(n: GhostNote) = minOf(n.durationMs.toDouble(), HOLD_MS)

/** First note of [notes] (sorted by start) still to be played or still held at [pos]; -1 when none is left. */
internal fun nextIndex(notes: List<GhostNote>, pos: Double): Int {
    var lo = 0; var hi = notes.size
    while (lo < hi) { val mid = (lo + hi) ushr 1; if (notes[mid].startMs < pos - HOLD_MS) lo = mid + 1 else hi = mid }
    var i = lo
    while (i < notes.size && notes[i].startMs + holdOf(notes[i]) <= pos) i++
    return if (i < notes.size) i else -1
}

/** How far down the finger for [n] is at [pos]: going down over [LEAD_MS], held while it sounds, then lifting. */
internal fun dipFor(n: GhostNote, pos: Double): Float {
    val dt = n.startMs - pos
    val held = holdOf(n)
    return when {
        dt > LEAD_MS -> 0f
        dt > 0 -> (1 - dt / LEAD_MS).toFloat().let { it * it }
        -dt < held -> 1f
        -dt < held + RELEASE_MS -> (1 + (dt + held) / RELEASE_MS).toFloat()
        else -> 0f
    }
}

/** Motion state of one hand, in plain fields: the frame loop writes them, the draw reads them. */
private class HandState(val hand: Hand) {
    var x = 0f; var v = 0f; var placed = false
    var alpha = 0f
    val dip = FloatArray(6)
    val next = BooleanArray(6)          // fingers of the next note(s)
    val black = BooleanArray(6)         // ... and whether that key is black (the finger reaches further in)
    val keyX = FloatArray(6)            // ... and that key's centre
    // The painted hand's pose: cross-fading from [from] to [to], [mix] 0..1.
    var from = HandPose.REST; var to = HandPose.REST; var mix = 1f

    /** One frame. True while anything still moves. */
    fun step(notes: List<GhostNote>, pos: Double, dt: Float, keys: KeyboardLayout, still: Boolean, show: Boolean): Boolean {
        java.util.Arrays.fill(dip, 0f); java.util.Arrays.fill(next, false); java.util.Arrays.fill(black, false)
        val i = if (show) nextIndex(notes, pos) else -1
        val visible = i >= 0 && notes[i].startMs - pos < SHOW_AHEAD_MS
        var target = x
        if (i >= 0) {
            // The group starting with note i (a chord): the hand sits where all its fingers fit best.
            var sum = 0f; var n = 0; var j = i
            val t0 = notes[i].startMs
            while (j < notes.size && notes[j].startMs == t0) {
                val g = notes[j]
                if (g.midi in keys.lowMidi..keys.highMidi) {
                    val f = g.finger ?: 3
                    sum += keys.rect(g.midi).centerX - fingerOffset(f, hand) * keys.whiteWidth; n++
                    if (g.finger != null) { next[f] = true; black[f] = isBlack(g.midi); keyX[f] = keys.rect(g.midi).centerX }
                }
                j++
            }
            if (n > 0) target = sum / n
            // Fingers going down or held: notes from a little before the playhead to just after it.
            var k = i
            while (k > 0 && notes[k - 1].startMs >= pos - HOLD_MS - RELEASE_MS) k--
            while (k < notes.size && notes[k].startMs <= pos + LEAD_MS) {
                val f = notes[k].finger
                if (f != null) dip[f] = maxOf(dip[f], dipFor(notes[k], pos))
                k++
            }
        }
        var moving = false
        if (!placed || still) { x = target; v = 0f; placed = i >= 0 } else {
            // A soft spring (damping ratio about 0.85): the hand glides to its next place.
            val a = 110f * (target - x) - 17.8f * v
            v += a * dt; x += v * dt
            if (kotlin.math.abs(target - x) < 0.5f && kotlin.math.abs(v) < 2f) { x = target; v = 0f } else moving = true
        }
        // The painted pose: the finger most of the way down presses; a change cross-fades over 200 ms.
        var best = 0; var bestDip = 0.35f
        for (f in 1..5) if (dip[f] > bestDip) { bestDip = dip[f]; best = f }
        val pose = HandPose.press(best)
        if (pose != to) { from = if (mix >= 0.5f) to else from; to = pose; mix = 0f }
        if (still) mix = 1f
        else if (mix < 1f) { mix = minOf(1f, mix + dt / 0.2f); moving = true }
        val want = if (visible) 1f else 0f
        if (still) alpha = want
        else if (alpha != want) {
            alpha = if (want > alpha) minOf(want, alpha + dt / 0.25f) else maxOf(want, alpha - dt / 0.4f)
            moving = true
        }
        return moving
    }
}

/**
 * A translucent hand hovering over the keyboard that shows which finger plays next: it glides (spring) to the
 * next note's key, the right finger dips as the note reaches the hit line, and a chord dips several. Both
 * hands, the left one mirrored. Draw it over a [com.bigpapituna.sharpears.ui.Keyboard] of the same size and
 * range ([lowMidi]..[highMidi]) so the fingers land on the keys.
 *
 * [position] is read every frame without recomposing (pass `{ position }`); the frame loop sleeps while
 * nothing moves and the playhead stands still. [visible] fades the hands out. With animations off the hands
 * jump instead of gliding. Decorative: screen readers skip it (the lane already names each note).
 */
@Composable
fun GhostHand(
    notes: List<GhostNote>,
    position: () -> Double,
    lowMidi: Int,
    highMidi: Int,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val still = reducedMotion()
    val hands = remember(notes) { listOf(notes.filter { it.hand == Hand.RIGHT }, notes.filter { it.hand == Hand.LEFT }) }
    val states = remember { arrayOf(HandState(Hand.RIGHT), HandState(Hand.LEFT)) }
    val frame = remember { mutableLongStateOf(0L) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val keys = remember(box, lowMidi, highMidi) {
        if (box.width == 0 || box.height == 0) null else KeyboardLayout(lowMidi, highMidi, box.width.toFloat(), box.height.toFloat())
    }
    val pos by rememberUpdatedState(position)
    LaunchedEffect(keys, hands, visible, still) {
        val l = keys ?: return@LaunchedEffect
        var last = 0L
        var lastPos = Double.NaN
        while (true) {
            val busy = withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceAtMost(1f / 30f)
                last = now
                val p = pos()
                var moving = false
                for (h in 0..1) moving = states[h].step(hands[h], p, dt, l, still, visible) or moving
                frame.longValue = now
                (moving || p != lastPos).also { lastPos = p }
            }
            if (!busy) { snapshotFlow { pos() }.first { it != lastPos }; last = 0L }
        }
    }
    val text = rememberTextMeasurer()
    // Finger numbers, measured once.
    val numbers = remember(text) {
        Array(6) { f -> text.measure("$f", TextStyle(color = Color(0xFF3A2A1E), fontSize = 12.sp, fontFamily = Geist, fontWeight = FontWeight.SemiBold)) }
    }
    // Outlines: a thin edge, and a thicker one in the hand colour on the finger(s) that play next.
    val dens = LocalDensity.current.density
    val strokes = remember(dens) { arrayOf(Stroke(width = 1.5f * dens), Stroke(width = 2.5f * dens)) }
    val painted = rememberHandFrames()
    Canvas(modifier.clipToBounds().onSizeChanged { box = it }.clearAndSetSemantics { }) {
        frame.longValue
        val l = keys ?: return@Canvas
        val frames = painted.value
        for (s in states) {
            if (s.alpha <= 0.01f) continue
            // 60%, drawn straight onto the keys (no offscreen layer: that cost ~5 ms a frame on the emulator). The
            // painted hand when its frames are in; the drawn one while they load, or for good if they failed.
            if (frames != null) drawPainted(frames, s, l, numbers, 0.6f * s.alpha) else drawHand(s, l, numbers, strokes, 0.6f * s.alpha)
        }
    }
}

/** The painted hand for [s]: middle finger at its glide position, the pressing finger's frame cross-fading in. */
private fun DrawScope.drawPainted(frames: HandFrames, s: HandState, l: KeyboardLayout, numbers: Array<TextLayoutResult>, a: Float) {
    val h = size.height
    val w = l.whiteWidth
    val right = s.hand == Hand.RIGHT
    val handColor = if (right) RightHandColor else LeftHandColor
    // Fingertips up the keys, so the thumb (lower on the hand) still reaches the front of its key. The player's keys
    // are short for their width, so the hand is shortened to match (foreshortened, as the keys are).
    val drop = handTip(right, HandPose.REST, 1, 3, 0f, 0f, w).y
    val squash = (h * 0.5f / drop).coerceIn(0.4f, 1f)
    var ay = h * 0.86f - drop * squash
    var ax = s.x
    val f = when (s.to) { HandPose.PRESS_1 -> 1; HandPose.PRESS_2 -> 2; HandPose.PRESS_3 -> 3; HandPose.PRESS_4 -> 4; HandPose.PRESS_5 -> 5; else -> 0 }
    if (f > 0 && s.next[f]) {
        // The pressing finger lands on its key, reaching in for a black one.
        ax += (s.keyX[f] - handTip(right, s.to, f, 3, ax, ay, w, squash = squash).x) * s.mix
        if (s.black[f]) ay -= h * 0.25f * s.mix
    }
    drawPaintedHand(frames, right, s.from, s.to, s.mix, 3, ax, ay, w, alpha = a, squash = squash)
    // The next finger(s): a soft spot of the hand colour on the tip as it presses, and the finger's number.
    for (k in 1..5) {
        if (!s.next[k]) continue
        val tip = handTip(right, s.from, s.to, s.mix, k, 3, ax, ay, w, squash = squash)
        val d = s.dip[k]
        drawCircle(handColor, w * (0.34f + 0.12f * d), tip, alpha = (0.35f + 0.4f * d) * minOf(1f, a / 0.6f))
        val t = numbers[k]
        drawText(t, topLeft = Offset(tip.x - t.size.width / 2f, tip.y - t.size.height / 2f), alpha = minOf(1f, a / 0.6f))
    }
}

private fun DrawScope.drawHand(s: HandState, l: KeyboardLayout, numbers: Array<TextLayoutResult>, strokes: Array<Stroke>, a: Float) {
    val h = size.height
    val w = l.whiteWidth
    val dir = if (s.hand == Hand.RIGHT) 1f else -1f
    val handColor = if (s.hand == Hand.RIGHT) RightHandColor else LeftHandColor
    val fw = kotlin.math.min(w * 0.62f, 30f * density)
    val palmTop = h * 0.88f
    // The palm: mostly below the keys, its knuckles showing at the bottom edge.
    val pl = s.x + dir * -1.55f * w
    val pr = s.x + dir * 2.5f * w
    drawRoundRect(GhostSkin, Offset(kotlin.math.min(pl, pr), palmTop), Size(kotlin.math.abs(pr - pl), h * 0.6f), CornerRadius(w * 0.9f), alpha = a)
    drawRoundRect(GhostEdge, Offset(kotlin.math.min(pl, pr), palmTop), Size(kotlin.math.abs(pr - pl), h * 0.6f), CornerRadius(w * 0.9f), style = strokes[0], alpha = a)
    // Fingers 2..5 point up the keys; their tips reach the front of the white keys, further for a black key.
    for (f in 2..5) {
        val len = h * when (f) { 2 -> 0.46f; 3 -> 0.52f; 4 -> 0.48f; else -> 0.38f } + (if (s.black[f]) h * 0.12f else 0f)
        val d = s.dip[f]
        val cx = s.x + fingerOffset(f, s.hand) * w
        val ww = fw * (1f + 0.10f * d)
        val top = palmTop + h * 0.04f - len + h * 0.05f * d
        finger(cx, top, ww, palmTop + h * 0.02f - top, d, s.next[f], handColor, strokes, a)
        number(s, f, cx, top, ww, numbers, a)
    }
    // The thumb comes out low on the inner side, angled out.
    val tx = s.x + fingerOffset(1, s.hand) * w
    val d = s.dip[1]
    val len = h * 0.30f + (if (s.black[1]) h * 0.12f else 0f)
    val top = palmTop + h * 0.10f - len + h * 0.05f * d
    rotate(-18f * dir * (1f - 0.5f * d), Offset(tx, palmTop + h * 0.12f)) {
        finger(tx, top, fw * 1.08f * (1f + 0.1f * d), palmTop + h * 0.16f - top, d, s.next[1], handColor, strokes, a)
        number(s, 1, tx, top, fw * 1.08f, numbers, a)
    }
}

private fun DrawScope.finger(cx: Float, top: Float, w: Float, len: Float, dip: Float, next: Boolean, handColor: Color, strokes: Array<Stroke>, a: Float) {
    val r = CornerRadius(w / 2f)
    val face = lerp(GhostSkin, handColor, 0.45f * dip)
    // A soft shadow down and to the side, so the hand lifts off the white keys (less of it as the finger presses).
    val off = (1f - 0.6f * dip) * 3f * density
    drawRoundRect(GhostShadow, Offset(cx - w / 2f + off, top + off * 1.4f), Size(w, len), r, alpha = 0.35f * a)
    drawRoundRect(face, Offset(cx - w / 2f, top), Size(w, len), r, alpha = a)
    drawRoundRect(if (next) handColor else GhostEdge, Offset(cx - w / 2f, top), Size(w, len), r,
        style = if (next) strokes[1] else strokes[0], alpha = a)
    // The press: a soft spot where the fingertip meets the key.
    if (dip > 0.02f) drawCircle(handColor, w * (0.55f + 0.25f * dip), Offset(cx, top + w * 0.45f), alpha = 0.55f * dip * a)
}

private fun DrawScope.number(s: HandState, f: Int, cx: Float, top: Float, w: Float, numbers: Array<TextLayoutResult>, a: Float) {
    if (!s.next[f]) return
    val t = numbers[f]
    drawText(t, topLeft = Offset(cx - t.size.width / 2f, top + w * 0.45f - t.size.height / 2f), alpha = minOf(1f, a / 0.6f))
}
