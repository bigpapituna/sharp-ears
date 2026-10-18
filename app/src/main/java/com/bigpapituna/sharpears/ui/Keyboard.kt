package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.KeyRect
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.skin.KeyboardSize
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.ui.skin.WrongFlash
import com.bigpapituna.sharpears.ui.skin.colourNotesOn
import com.bigpapituna.sharpears.ui.skin.drawAnimal
import com.bigpapituna.sharpears.ui.skin.drawNoteColour
import com.bigpapituna.sharpears.ui.skin.drawWrongKey
import com.bigpapituna.sharpears.ui.skin.inkOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import com.bigpapituna.sharpears.ui.skin.rememberFlashClock
import kotlinx.coroutines.delay

// Off-white and off-black keys (no pure white or black), slate edges to match the stage.
private val WhiteTop = Color(0xFFFFFDF9)
private val WhiteKey = Color(0xFFF5F1EA)
private val WhiteLow = Color(0xFFE2DCD2)
private val WhiteLip = Color(0xFFBFB6A9)
private val BlackTop = Color(0xFF3F3833)
private val BlackKey = Color(0xFF141110)
private val BlackFront = Color(0xFF4F4740)
private val KeyEdge = Color(0xFF110F0D)
private val KeyLabel = Color(0xFF6B6058)

/**
 * Multi-touch piano keyboard. Each finger maps to one key; sliding onto another key re-triggers.
 * [lit] keys are drawn in their color (pressed keys, or keys the song wants next). Lit keys sink 3 dp and
 * spring back when released; [flash] sends a glow and ripple out of a judged key; [sheen] is an occasional
 * idle glint. With the system's animations off, keys still go down and up but nothing else moves.
 *
 * 2.1 (ui/skin): with [colours] every key wears a pad of its Boomwhacker colour near the front (black keys striped in
 * their neighbours' colours), and a note-name label sits on the pad: the letter for a [reader], the key's animal for a
 * kid who cannot read yet. [keySize] BIG (kids) draws bigger labels and pads; the range the caller passes decides how
 * wide the keys are (KeyboardSize.range). [wrong] makes a wrong key glow yellow or red with a short shake.
 */
@Composable
fun Keyboard(
    lowMidi: Int,
    highMidi: Int,
    lit: Map<Int, Color>,
    onNoteOn: (midi: Int, velocity: Int) -> Unit,
    onNoteOff: (midi: Int) -> Unit,
    modifier: Modifier = Modifier,
    pulsing: Map<Int, Color> = emptyMap(),
    /** Text drawn near the bottom of a key, e.g. fingering numbers. */
    labels: Map<Int, String> = emptyMap(),
    /**
     * Keys that were just judged: each new entry (or a new colour on a key) sends a soft glow and a ripple out
     * of that key in its colour, e.g. Good for a right note, WrongGlow for a wrong one. Only a change starts one.
     */
    flash: Map<Int, Color> = emptyMap(),
    /** A faint light that sweeps across the keys now and then; pass false while a song plays. */
    sheen: Boolean = true,
    /** Boomwhacker colour pads on the keys: follows the profile (kids on, grown-ups by Settings) unless a screen says. */
    colours: Boolean = colourNotesOn(),
    /** Letters on the pads; false swaps note-name labels for the key's animal (kids who cannot read yet). */
    reader: Boolean = com.bigpapituna.sharpears.ui.family.reader(),
    /** BIG for kids: larger labels and pads (pass KeyboardSize.range for the wider keys). */
    keySize: KeyboardSize = KeyboardSize.REGULAR,
    /** Wrong keys shown: the key glows yellow (a slip of up to two semitones) or red with a short shake. */
    wrong: WrongFlash? = null,
) {
    val still = reducedMotion()
    val motion = remember { KeyMotion() }
    // Pressed keys go down at once and spring back up on release (a little past their rest, then settle).
    SideEffect {
        var changed = false
        for (m in 0..127) {
            val t = if (m in lowMidi..highMidi && lit.containsKey(m)) 1f else 0f
            if (motion.target[m] != t) {
                motion.target[m] = t; changed = true
                if (t == 1f || still) { motion.pos[m] = t; motion.vel[m] = 0f }
            }
        }
        for ((m, c) in flash) if (m in 0..127 && motion.flashSeen[m] != c.value.toLong()) {
            motion.flashSeen[m] = c.value.toLong()
            if (!still) { motion.ripple(m, c, System.nanoTime()); changed = true }
        }
        for (m in 0..127) if (motion.flashSeen[m] != 0L && !flash.containsKey(m)) motion.flashSeen[m] = 0L
        if (changed) motion.wake.intValue++
    }
    LaunchedEffect(motion) {
        snapshotFlow { motion.wake.intValue }.collect {
            var last = 0L
            while (withFrameNanos { now ->
                    val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceAtMost(1f / 30f)
                    last = now
                    motion.step(dt, now).also { motion.tick.longValue = now }
                }) Unit
        }
    }
    // The sheen: one slow sweep every 7 to 11 seconds, and no frames at all while it waits.
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(sheen, still) {
        if (!sheen || still) { sweep.snapTo(0f); return@LaunchedEffect }
        val r = java.util.Random()
        while (true) {
            delay(7000L + r.nextInt(4000))
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
            sweep.snapTo(0f)
        }
    }
    val text = rememberTextMeasurer()
    val labelCache = remember(text) { HashMap<String, androidx.compose.ui.text.TextLayoutResult>() }
    val animalPath = remember { androidx.compose.ui.graphics.Path() }
    val wrongClock = rememberFlashClock(wrong)
    // Read only inside the Canvas, so the pulse redraws the keyboard without recomposing the screen.
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "pulse")
    // The geometry follows both the size and the range (a transpose can change the range under a laid-out keyboard).
    var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val layout = remember(box, lowMidi, highMidi) {
        if (box.width == 0 || box.height == 0) null else KeyboardLayout(lowMidi, highMidi, box.width.toFloat(), box.height.toFloat())
    }
    val currentLayout by rememberUpdatedState(layout)
    val on by rememberUpdatedState(onNoteOn)
    val off by rememberUpdatedState(onNoteOff)

    Box(
        modifier
            .semantics { contentDescription = "Piano keyboard ${noteName(lowMidi)} to ${noteName(highMidi)}" + if (colours) ", in colours" else "" }
            .onGloballyPositioned { c ->
                if (c.size != box) {
                    box = c.size
                    // Debug aid: window coordinates of each key, so adb taps hit known keys.
                    val l = KeyboardLayout(lowMidi, highMidi, c.size.width.toFloat(), c.size.height.toFloat())
                    val o = c.positionInWindow()
                    (l.whiteKeys + l.blackKeys).forEach {
                        Log.d(TAG, "keyRect ${noteName(it.midi)} = ${(o.x + it.centerX).toInt()},${(o.y + it.touchY).toInt()}")
                    }
                }
            }
            .pointerInput(lowMidi, highMidi) {
                awaitPointerEventScope {
                    val held = HashMap<PointerId, Int>()
                    while (true) {
                        val event = awaitPointerEvent()
                        val l = currentLayout ?: continue
                        for (change in event.changes) {
                            val key = if (change.pressed) l.keyAt(change.position.x, change.position.y) else null
                            val old = held[change.id]
                            if (key != old) {
                                // ponytail: two fingers on one key release it when either lifts; fine for a learning keyboard
                                if (old != null) { held.remove(change.id); off(old) }
                                if (key != null) { held[change.id] = key; on(key, 100) }
                            }
                            change.consume()
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.matchParentSize().clipToBounds()) {
            val l = layout ?: return@Canvas
            // The waiting keys pulse; with the phone's animations off they hold a steady glow (they kept pulsing).
            val a = if (pulsing.isEmpty() || still) 1f else pulse.value
            val now = motion.tick.longValue                   // ticks only while a key or ripple moves
            val sinkPx = 3.dp.toPx()
            drawRect(KeyEdge)                                  // shows through the gaps between keys
            val keyH = l.whiteKeys.firstOrNull()?.let { it.bottom - it.top } ?: size.height
            val whiteBody = Brush.verticalGradient(listOf(WhiteTop, WhiteKey, WhiteLow), startY = 0f, endY = keyH)
            // A wrong key shakes and glows (its pad and label move with it); the clock runs only while one does.
            val wrongNow = wrongClock.longValue
            fun shake(k: KeyRect) = if (wrong == null || wrongNow == 0L) 0f else wrong.shakePx(k.midi, wrongNow, still, (k.right - k.left) * 0.10f)
            fun wrongGlow(k: KeyRect, dx: Float) {
                if (wrong == null || wrongNow == 0L) return
                val g = wrong.glow(k.midi, wrongNow, still)
                if (g > 0f) wrong.tone(k.midi)?.let { drawWrongKey(k, g, it, dx) }
            }
            l.whiteKeys.forEach { k ->
                val dx = shake(k)
                translate(left = dx) {
                    drawWhiteKey(k, lit[k.midi], pulsing[k.midi]?.copy(alpha = a * 0.75f), whiteBody, motion.pos[k.midi], sinkPx)
                    if (colours) drawColourPad(k, motion.pos[k.midi], sinkPx, keySize.labelScale)
                }
                wrongGlow(k, dx)
            }
            val s = sweep.value
            if (s > 0f && s < 1f) {
                val band = l.whiteWidth * 3f
                val brush = motion.sheenBrush(band)
                translate(left = -band + s * (size.width + band)) { drawRect(brush, size = Size(band, keyH)) }
            }
            // The rail the keys tuck under: a soft shadow along the top edge.
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), endY = keyH * 0.08f), size = Size(size.width, keyH * 0.08f))
            l.blackKeys.forEach { k ->
                val dx = shake(k)
                translate(left = dx) {
                    drawBlackKey(k, lit[k.midi], pulsing[k.midi]?.copy(alpha = a * 0.85f), motion.pos[k.midi], sinkPx)
                    if (colours) drawColourPad(k, motion.pos[k.midi], sinkPx, keySize.labelScale)
                }
                wrongGlow(k, dx)
            }
            if (now != 0L) motion.drawRipples(this, l, now)
            val fontPx = 14.sp.toPx() * keySize.labelScale
            for ((midi, label) in labels) {
                if (midi !in lowMidi..highMidi) continue
                val k = l.rect(midi)
                val dx = shake(k)
                val sunk = motion.pos[midi].coerceIn(0f, 1f) * sinkPx
                if (colours) {
                    // On the pad: the letter in the ink that reads on that colour, or the key's animal for a pre-reader.
                    val pad = padRect(k, motion.pos[midi], sinkPx, keySize.labelScale)
                    val isName = label.first() in 'A'..'G'
                    val animal = if (isName && !reader) NoteColours.animal(midi) else null
                    if (animal != null) {
                        val icon = minOf(pad.width * 0.84f, pad.height * 0.84f)
                        drawAnimal(animal, Offset(pad.center.x + dx, pad.center.y), icon, inkOn(midi), noteColour(midi), animalPath)
                    } else if (!(isName && !reader)) {
                        val px = minOf(fontPx, pad.height * 0.62f)
                        val ink = inkOn(midi)
                        val m = labelCache.getOrPut("$label|pad|$px|${ink.value}") {
                            text.measure(label, TextStyle(color = ink, fontSize = (px / density / fontScale).sp, fontFamily = Geist, fontWeight = FontWeight.Bold))
                        }
                        drawText(m, topLeft = Offset(pad.center.x - m.size.width / 2f + dx, pad.center.y - m.size.height / 2f))
                    }
                    continue
                }
                // A lit white key is darker (hand colour, sunk), so its label turns light like a black key's.
                val light = k.black || lit[midi] != null
                val m = labelCache.getOrPut("$label|$light|$fontPx") {
                    text.measure(label, TextStyle(color = if (light) Ink else KeyLabel, fontSize = (fontPx / density / fontScale).sp, fontFamily = Geist, fontWeight = FontWeight.SemiBold))
                }
                val lip = (k.bottom - k.top) * if (k.black) 0.12f else 0.07f
                // Centred on the key, but kept inside it (and the keyboard), so "C2" on the first key is never cut.
                val x = (k.centerX - m.size.width / 2f).coerceIn(minOf(k.left + 3f, size.width - m.size.width), maxOf(k.left + 3f, minOf(k.right - m.size.width - 3f, size.width - m.size.width - 3f)))
                drawText(m, topLeft = Offset(x + dx, k.bottom - lip - m.size.height - 6f + sunk))
            }
        }
        // Remembered once; it reads the latest callbacks when TalkBack activates a key.
        val tap = remember { { m: Int -> on(m, 90); off(m) } }
        layout?.let { KeySemantics(it, tap, colours) }
    }
}

/**
 * Invisible nodes over each key so TalkBack reads and plays key names ("C4", "F#3"). They take no touches;
 * the Canvas above handles fingers. Skips recomposition while the layout is unchanged.
 */
@Composable
private fun KeySemantics(l: KeyboardLayout, tap: (Int) -> Unit, colours: Boolean) {
    val density = LocalDensity.current
    (l.whiteKeys + l.blackKeys).forEach { k ->
        with(density) {
            Box(
                Modifier.offset { IntOffset(k.left.toInt(), k.top.toInt()) }
                    .size((k.right - k.left).toDp(), (k.bottom - k.top).toDp())
                    .clearAndSetSemantics {
                        contentDescription = noteName(k.midi) + if (colours) ", ${NoteColours.colourName(k.midi)}" else ""
                        role = Role.Button
                        onClick { tap(k.midi); true }
                    },
            )
        }
    }
}

/**
 * Where a key's colour pad sits: near the front of the face (the part a finger lands on), inset from the sides,
 * sinking with the key. [scale] grows it for big keys.
 */
private fun padRect(k: KeyRect, d: Float, sinkPx: Float, scale: Float): androidx.compose.ui.geometry.Rect {
    val w = k.right - k.left; val h = k.bottom - k.top
    val front = h * if (k.black) 0.12f else 0.07f
    val sink = (d * sinkPx).coerceIn(-sinkPx * 0.4f, front * 0.8f)
    val inset = w * if (k.black) 0.14f else 0.12f
    val padH = minOf(h * 0.30f, (w - 2 * inset) * 1.05f * scale, h * 0.42f)
    val bottom = k.top + sink + h - front - w * 0.10f
    return androidx.compose.ui.geometry.Rect(k.left + inset, bottom - padH, k.right - inset, bottom)
}

/** The key's colour: a pad near the front (stripes on a black key) and a faint wash of it up a white key's face. */
private fun DrawScope.drawColourPad(k: KeyRect, d: Float, sinkPx: Float, scale: Float) {
    val pad = padRect(k, d, sinkPx, scale)
    val w = k.right - k.left
    if (!k.black) drawRoundRect(noteColour(k.midi), Offset(k.left + 1.2f, k.top), Size(w - 2.4f, pad.top - k.top), CornerRadius(w * 0.10f), alpha = 0.10f)
    drawNoteColour(k.midi, pad.topLeft, pad.size, CornerRadius(minOf(pad.width, pad.height) * 0.28f))
    // A soft highlight on the top edge so the pad reads as a sticker on the key, not a hole in it.
    drawRoundRect(Color.White, pad.topLeft, Size(pad.width, pad.height * 0.22f), CornerRadius(minOf(pad.width, pad.height) * 0.28f), alpha = 0.18f)
}

/**
 * A white key with depth: a lit-from-above body, a darker front lip, and a press that sinks it (the face moves
 * down by up to [sinkPx] and darkens, the lip shrinks). [lit] is a pressed or sounding key's colour, [hint] a
 * pulsing "play me", [d] how far down the key is (1 pressed, 0 at rest, a little below 0 as it springs back).
 */
private fun DrawScope.drawWhiteKey(k: KeyRect, lit: Color?, hint: Color?, body: Brush, d: Float, sinkPx: Float) {
    val gap = 1.2f
    val x = k.left + gap; val w = k.right - k.left - 2 * gap
    val h = k.bottom - k.top
    val pressed = lit != null
    val lip0 = h * 0.07f
    val sink = (d * sinkPx).coerceIn(-sinkPx * 0.4f, lip0 * 0.8f)
    val faceH = h - lip0
    val r = CornerRadius(w * 0.10f)
    drawRoundRect(if (pressed) lit!!.darken(0.55f) else WhiteLip, Offset(x, k.top), Size(w, h), r)
    if (pressed) drawRoundRect(Brush.verticalGradient(listOf(lit!!.lighten(0.25f), lit), startY = k.top, endY = k.bottom), Offset(x, k.top + sink), Size(w, faceH), r)
    else drawRoundRect(body, Offset(x, k.top + sink), Size(w, faceH), r)
    // A key that is down is a touch darker, as if out of the light.
    if (d > 0.01f) drawRoundRect(Color.Black, Offset(x, k.top + sink), Size(w, faceH), r, alpha = 0.12f * d.coerceAtMost(1f))
    hint?.let { drawRoundRect(it, Offset(x, k.top + sink), Size(w, faceH), r) }
}

/** A black key: drop shadow on the whites, glossy body, lighter sloped front, a faint top highlight. Sinks like a white key. */
private fun DrawScope.drawBlackKey(k: KeyRect, lit: Color?, hint: Color?, d: Float, sinkPx: Float) {
    val x = k.left; val w = k.right - k.left; val h = k.bottom - k.top
    val pressed = lit != null
    val r = CornerRadius(w * 0.14f)
    val down = d.coerceIn(0f, 1f)
    drawRoundRect(Color.Black, Offset(x + w * 0.10f, k.top), Size(w, h + h * 0.05f), r, alpha = 0.40f - 0.15f * down)
    val front0 = h * 0.12f
    val sink = (d * sinkPx).coerceIn(-sinkPx * 0.4f, front0 * 0.7f)
    val faceH = h - front0
    drawRoundRect(if (pressed) lit!!.darken(0.5f) else BlackFront, Offset(x, k.top), Size(w, h), r)
    drawRoundRect(
        if (pressed) Brush.verticalGradient(listOf(lit!!.lighten(0.15f), lit.darken(0.8f)), startY = k.top, endY = k.bottom)
        else Brush.verticalGradient(listOf(BlackTop, BlackKey), startY = k.top, endY = k.bottom),
        Offset(x, k.top + sink), Size(w, faceH), r,
    )
    if (d > 0.01f) drawRoundRect(Color.Black, Offset(x, k.top + sink), Size(w, faceH), r, alpha = 0.18f * down)
    hint?.let { drawRoundRect(it, Offset(x, k.top + sink), Size(w, faceH), r) }
    drawRoundRect(Color.White, Offset(x + w * 0.18f, k.top + sink + h * 0.05f), Size(w * 0.64f, h * 0.30f), CornerRadius(w * 0.2f), alpha = 0.10f * (1f - 0.5f * down))
}

/**
 * Per-key motion in flat arrays, indexed by MIDI note, so nothing is allocated per frame: the press spring and
 * the ripples. [step] advances one frame and says whether anything still moves.
 */
private class KeyMotion {
    val pos = FloatArray(128)
    val vel = FloatArray(128)
    val target = FloatArray(128)
    val flashSeen = LongArray(128)
    val tick = mutableLongStateOf(0L)
    val wake = mutableIntStateOf(0)

    private val rMidi = IntArray(RIPPLES)
    private val rStart = LongArray(RIPPLES)
    private val rColor = LongArray(RIPPLES)
    private var rNext = 0
    private var ring: Stroke? = null
    private var sheen: Brush? = null
    private var sheenFor = -1f

    fun ripple(midi: Int, c: Color, now: Long) {
        val j = rNext; rNext = (rNext + 1) % RIPPLES
        rMidi[j] = midi; rStart[j] = now; rColor[j] = c.value.toLong()
    }

    fun step(dt: Float, now: Long): Boolean {
        var moving = false
        for (m in 0..127) {
            val t = target[m]
            if (pos[m] == t && vel[m] == 0f) continue
            // Under-damped spring: a quick return with a small bounce past the rest position.
            val a = SPRING_K * (t - pos[m]) - SPRING_C * vel[m]
            vel[m] += a * dt
            pos[m] += vel[m] * dt
            if (kotlin.math.abs(pos[m] - t) < 0.002f && kotlin.math.abs(vel[m]) < 0.02f) { pos[m] = t; vel[m] = 0f } else moving = true
        }
        for (j in 0 until RIPPLES) if (rStart[j] != 0L) { if (now - rStart[j] > RIPPLE_NS) rStart[j] = 0L else moving = true }
        return moving
    }

    fun sheenBrush(band: Float): Brush {
        if (band != sheenFor) {
            sheen = Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.22f), 1f to Color.Transparent, startX = 0f, endX = band)
            sheenFor = band
        }
        return sheen!!
    }

    fun drawRipples(scope: DrawScope, l: KeyboardLayout, now: Long) = with(scope) {
        val stroke = ring ?: Stroke(width = 2.5f * density).also { ring = it }
        for (j in 0 until RIPPLES) {
            if (rStart[j] == 0L || rMidi[j] !in l.lowMidi..l.highMidi) continue
            val t = ((now - rStart[j]).toFloat() / RIPPLE_NS).coerceIn(0f, 1f)
            val k = l.rect(rMidi[j])
            val c = Color(rColor[j].toULong())
            val fade = (1f - t) * (1f - t)
            val w = k.right - k.left
            // A glow on the key's face, and a ring spreading out from where the finger lands.
            drawRoundRect(c, Offset(k.left + 1.5f, k.top), Size(w - 3f, k.bottom - k.top), CornerRadius(w * 0.12f), alpha = 0.45f * fade)
            val center = Offset(k.centerX, k.touchY)
            val r = l.whiteWidth * (0.4f + 1.5f * (1f - (1f - t) * (1f - t)))
            drawCircle(c, r, center, alpha = 0.30f * fade)
            drawCircle(c, r, center, alpha = 0.8f * fade, style = stroke)
        }
    }

    companion object {
        const val RIPPLES = 12
        const val RIPPLE_NS = 480_000_000L
        const val SPRING_K = 700f          // stiffness (1/s^2)
        const val SPRING_C = 22f           // damping (1/s): a ratio of about 0.42
    }
}

private fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)
private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
