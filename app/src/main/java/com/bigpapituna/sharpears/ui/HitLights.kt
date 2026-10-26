package com.bigpapituna.sharpears.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.KeyboardLayout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The soft orange-red of a wrong note: warm, never the red of an error. */
val WrongGlow = Color(0xFFEF7B4E)

/**
 * Feedback at the hit line instead of coloured keys: a short bright flash rising from a correct note's column
 * (its hand colour with a white core) with a few sparks, a soft pulse along the whole line for a wrong note, a
 * combo counter ("x12") at the lane's top right once [COMBO_MIN] notes in a row are right, and in Perform a
 * small grade word ("Perfect", "Good") floating up from the line.
 *
 * The player adds lights; NoteLane draws them. Only [clock] is read while drawing, and it ticks only while
 * something is moving, so a flash redraws the lane without recomposing anything. Sparks, grade words and the
 * combo's shards live in fixed pools: nothing is allocated per frame.
 *
 * Calls for the player: [hit] on a right note (with the Perform grade if there is one), [wrong] on a wrong key,
 * [streak] to follow an outside count (Perform's streak, where only misses break it), [resetCombo] on a seek.
 */
class HitLights(
    /** Debug: stretches every light (e.g. 8 for screenshots). */
    val slow: Float = 1f,
) {
    enum class Kind { HIT, WRONG, TOUCH }
    class Light(val midi: Int, val startNanos: Long, val kind: Kind, val color: Color)

    val items = ArrayList<Light>(8)
    /** Frame time while lights are alive; read in the draw phase only. */
    val clock = mutableLongStateOf(0L)
    /** Bumps on each new light (and combo change), so the frame loop wakes up. */
    val added = mutableIntStateOf(0)

    /** The system's "remove animations": no sparks, words or shards, the combo just changes. Set by NoteLane. */
    var still = false
    /** Measures the combo and grade words; set by NoteLane. Without it they are not drawn. */
    var measurer: TextMeasurer? = null
    /** Kids see no combo number ("x12"): stars, not numbers (KIDS-PLAN 3.3). The lights themselves stay. */
    var showCombo = true

    val hitNanos get() = (HIT_MS * slow * 1_000_000).toLong()
    private val touchNanos get() = (TOUCH_MS * slow * 1_000_000).toLong()
    val wrongNanos get() = (WRONG_MS * slow * 1_000_000).toLong()
    private val sparkNanos get() = (SPARK_MS * slow * 1_000_000).toLong()
    private val wordNanos get() = (WORD_MS * slow * 1_000_000).toLong()
    private val pulseNanos get() = (PULSE_MS * slow * 1_000_000).toLong()
    private val shatterNanos get() = (SHATTER_MS * slow * 1_000_000).toLong()

    /**
     * A right note on [midi]: the flash in [color] (its hand), sparks, the combo up one, and [grade] if given. [tickMs] (2.14,
     * Perform): the hit's offset from its note in song ms, marked under the hit line ([tick]).
     */
    fun hit(midi: Int, color: Color, grade: String? = null, now: Long = System.nanoTime(), tickMs: Double? = null) {
        add(Light(midi, now, Kind.HIT, color))
        if (!still) spark(midi, color, now)
        if (grade != null && !still) word(midi, grade, now)
        tickMs?.let { tick(midi, it, now) }
        setCombo(combo + 1, now)
    }

    // ---- Timing ticks (2.14): Perform's early or late mark at the hit line ---------------------------------------

    private val tMidi = IntArray(TICKS)
    private val tStart = LongArray(TICKS)
    private val tPos = FloatArray(TICKS)               // -1 (as early as the good window) .. 1 (as late)
    private var tNext = 0
    private val tickNanos get() = (TICK_MS * slow * 1_000_000).toLong()

    /**
     * A Perform hit [offsetMs] from its note (song ms): a small mark in the hit line's band at the key's column, left of the
     * column's centre when early, right when late, a white one on the beat. Never a word, never over the keys or a note to read.
     */
    fun tick(midi: Int, offsetMs: Double, now: Long = System.nanoTime()) {
        val j = tNext; tNext = (tNext + 1) % TICKS
        tMidi[j] = midi; tStart[j] = now; tPos[j] = com.bigpapituna.sharpears.engine.PerformTiming.tick(offsetMs)
        added.intValue++
    }

    private fun DrawScope.drawTicks(keys: KeyboardLayout, hitY: Float, low: Int, high: Int, now: Long) {
        val life = tickNanos.toFloat()
        val dp = density
        for (j in 0 until TICKS) {
            if (tStart[j] == 0L || tMidi[j] !in low..high) continue
            val t = ((now - tStart[j]) / life).coerceIn(0f, 1f)
            val a = if (t < 0.7f) 1f else (1f - t) / 0.3f
            val k = keys.rect(tMidi[j])
            val cx = (k.left + k.right) / 2f
            val y = hitY - 9f * dp
            // A faint centre notch for the beat, and the mark up to half a white key either side of it.
            drawLine(Ink.copy(alpha = 0.35f * a), Offset(cx, y - 4f * dp), Offset(cx, y + 4f * dp), strokeWidth = 1f * dp)
            val p = tPos[j]
            val colour = when { p < -TICK_ON -> PupGold; p > TICK_ON -> LeftHandColor; else -> Ink }
            val x = cx + p * keys.whiteWidth * 0.5f
            drawRoundRect(colour.copy(alpha = 0.9f * a), Offset(x - 1.5f * dp, y - 5f * dp), Size(3f * dp, 10f * dp), CornerRadius(1.5f * dp))
        }
    }

    /**
     * A note that is neither right nor wrong (Listen mode, the Jam, free play): a short soft glow up its column in
     * [color], no sparks, the combo untouched. Juice on every note (ui/motion/Juice.kt).
     */
    fun touch(midi: Int, color: Color, now: Long = System.nanoTime()) = add(Light(midi, now, Kind.TOUCH, color))

    /** A wrong key: the glow along the line; it breaks the combo unless [breaksCombo] is false (Perform). */
    fun wrong(now: Long = System.nanoTime(), breaksCombo: Boolean = true) {
        add(Light(-1, now, Kind.WRONG, WrongGlow))
        if (breaksCombo) setCombo(0, now)
    }

    /** Follows an outside streak count (e.g. Perform's streakNow): up pulses, down to zero shatters. */
    fun streak(n: Int, now: Long = System.nanoTime()) { if (n != combo) setCombo(n, now) }

    /** A new run (seek, restart): the combo goes away quietly. */
    fun resetCombo() { combo = 0; comboAt = 0L; shatterAt = 0L; added.intValue++ }

    private fun add(l: Light) {
        if (items.size >= 16) items.removeAt(0)
        items += l
        added.intValue++
    }

    // ---- Combo ----------------------------------------------------------------------------------------------

    /** Right notes in a row (or the outside streak). Shown from [COMBO_MIN]. */
    var combo = 0
        private set
    private var comboAt = 0L          // when it last went up (the pulse)
    private var shatterAt = 0L        // when a shown combo broke
    private var shattered = 0         // the number that broke

    private fun setCombo(n: Int, now: Long) {
        if (n == combo) return
        if (n < combo) {
            if (combo >= COMBO_MIN && !still) { shatterAt = now; shattered = combo }
        } else comboAt = now
        combo = n
        added.intValue++
    }

    // ---- Sparks: a pool of particles rising from a hit's column ------------------------------------------------

    private val rnd = java.util.Random(7)
    private val pMidi = IntArray(SPARKS)
    private val pStart = LongArray(SPARKS)            // 0 = free
    private val pX = FloatArray(SPARKS)               // offset from the column centre, in key widths
    private val pVx = FloatArray(SPARKS)              // dp per second
    private val pVy = FloatArray(SPARKS)              // dp per second, upward
    private val pR = FloatArray(SPARKS)               // radius, dp
    private val pColor = LongArray(SPARKS)
    private var pNext = 0

    private fun spark(midi: Int, color: Color, now: Long) {
        val n = 6 + rnd.nextInt(5)                     // 6 to 10
        repeat(n) { i ->
            val j = pNext; pNext = (pNext + 1) % SPARKS
            pMidi[j] = midi; pStart[j] = now
            pX[j] = rnd.nextFloat() - 0.5f
            pVx[j] = (rnd.nextFloat() - 0.5f) * 70f
            pVy[j] = 110f + rnd.nextFloat() * 170f
            pR[j] = 2.2f + rnd.nextFloat() * 2.6f
            // Every third spark is a white-hot one; the rest in the hand colour.
            pColor[j] = (if (i % 3 == 0) Color.White else color).value.toLong()
        }
    }

    // ---- Grade words ---------------------------------------------------------------------------------------

    private val wMidi = IntArray(WORDS)
    private val wStart = LongArray(WORDS)
    private val wText = arrayOfNulls<String>(WORDS)
    private var wNext = 0

    private fun word(midi: Int, grade: String, now: Long) {
        val j = wNext; wNext = (wNext + 1) % WORDS
        wMidi[j] = midi; wStart[j] = now; wText[j] = grade
    }

    /** Drops finished lights; true while anything is still moving. */
    fun prune(now: Long): Boolean {
        items.removeAll { now - it.startNanos > life(it.kind) }
        var alive = items.isNotEmpty()
        for (j in 0 until SPARKS) if (pStart[j] != 0L) { if (now - pStart[j] > sparkNanos) pStart[j] = 0L else alive = true }
        for (j in 0 until WORDS) if (wStart[j] != 0L) { if (now - wStart[j] > wordNanos) wStart[j] = 0L else alive = true }
        for (j in 0 until TICKS) if (tStart[j] != 0L) { if (now - tStart[j] > tickNanos) tStart[j] = 0L else alive = true }
        if (comboAt != 0L && now - comboAt < pulseNanos) alive = true
        if (shatterAt != 0L) { if (now - shatterAt > shatterNanos) shatterAt = 0L else alive = true }
        return alive
    }

    private fun life(k: Kind) = when (k) { Kind.HIT -> hitNanos; Kind.WRONG -> wrongNanos; Kind.TOUCH -> touchNanos }

    // ---- Drawing ---------------------------------------------------------------------------------------------

    private val texts = HashMap<String, TextLayoutResult>()
    private var comboText: TextLayoutResult? = null
    private var comboTextFor = -1
    private var shatterText: TextLayoutResult? = null
    private var shatterTextFor = -1

    private fun measure(s: String, px: Float, color: Color, density: Float, fontScale: Float, family: androidx.compose.ui.text.font.FontFamily) =
        measurer?.measure(s, TextStyle(color = color, fontSize = (px / density / fontScale).sp, fontFamily = family, fontWeight = FontWeight.SemiBold))

    private fun DrawScope.comboLayout(n: Int, px: Float): TextLayoutResult? {
        if (comboTextFor != n) { comboText = measure("x$n", px, Ink, density, fontScale, GeistMono); comboTextFor = n }
        return comboText
    }

    private fun DrawScope.shatterLayout(n: Int, px: Float): TextLayoutResult? {
        if (shatterTextFor != n) { shatterText = measure("x$n", px, Ink, density, fontScale, GeistMono); shatterTextFor = n }
        return shatterText
    }

    /** Draws every live light over the lane, whose hit line is at [hitY]. */
    fun DrawScope.draw(keys: KeyboardLayout, hitY: Float, low: Int, high: Int) {
        val now = clock.longValue
        added.intValue                                       // redraw when the combo changes with no clock running
        if (now == 0L) return
        for (l in items) {
            val t = ((now - l.startNanos).toFloat() / life(l.kind)).coerceIn(0f, 1f)
            if (l.kind == Kind.TOUCH) {
                if (l.midi !in low..high) continue
                // A soft column of the key's colour, rising a little and fading: seen, not graded.
                val k = keys.rect(l.midi)
                val cx = (k.left + k.right) / 2f
                val w = keys.whiteWidth * 0.9f
                val fade = (1f - t) * (1f - t)
                val rise = size.height * (0.12f + 0.16f * t)
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to l.color.copy(alpha = 0.55f * fade), startY = hitY - rise, endY = hitY),
                    Offset(cx - w / 2f, hitY - rise), Size(w, rise))
                drawCircle(Brush.radialGradient(listOf(l.color.copy(alpha = 0.5f * fade), Color.Transparent), center = Offset(cx, hitY), radius = w),
                    radius = w, center = Offset(cx, hitY))
            } else if (l.kind == Kind.WRONG) {
                // A soft band that swells and fades along the whole line.
                val a = sin(PI * t).toFloat() * 0.55f
                val h = size.height * 0.16f
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, l.color.copy(alpha = a * 0.6f), l.color.copy(alpha = a)), startY = hitY - h, endY = hitY),
                    Offset(0f, hitY - h), Size(size.width, h))
                drawRect(l.color.copy(alpha = a), Offset(0f, hitY - 5f), Size(size.width, 5f))
            } else {
                if (l.midi !in low..high) continue
                val k = keys.rect(l.midi)
                val cx = (k.left + k.right) / 2f
                val w = keys.whiteWidth * 1.1f
                val fade = 1f - t
                val rise = size.height * (0.25f + 0.30f * (1f - (1f - t) * (1f - t)))      // eases out as it climbs
                // A wide soft halo, then the beam: hand colour fading upward from a white base.
                val halo = maxOf(w * 1.6f, rise * 0.7f)
                drawCircle(Brush.radialGradient(listOf(l.color.copy(alpha = 0.45f * fade), Color.Transparent), center = Offset(cx, hitY), radius = halo),
                    radius = halo, center = Offset(cx, hitY))
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.55f to l.color.copy(alpha = 0.85f * fade), 1f to Color.White.copy(alpha = fade),
                    startY = hitY - rise, endY = hitY), Offset(cx - w / 2f, hitY - rise), Size(w, rise))
                // The white core at the base.
                val r = w * (0.7f + 0.3f * t)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = fade), l.color.copy(alpha = 0.7f * fade), Color.Transparent),
                    center = Offset(cx, hitY - 2f), radius = r), radius = r, center = Offset(cx, hitY - 2f))
            }
        }
        drawSparks(keys, hitY, low, high, now)
        drawTicks(keys, hitY, low, high, now)
        drawWords(keys, hitY, low, high, now)
        drawCombo(now)
    }

    private fun DrawScope.drawSparks(keys: KeyboardLayout, hitY: Float, low: Int, high: Int, now: Long) {
        val life = sparkNanos.toFloat()
        val dp = density
        for (j in 0 until SPARKS) {
            if (pStart[j] == 0L || pMidi[j] !in low..high) continue
            val age = now - pStart[j]
            if (age < 0 || age > life) continue
            val t = age / life                                   // 0..1
            val sec = age / 1_000_000_000f / slow
            val k = keys.rect(pMidi[j])
            val x = (k.left + k.right) / 2f + pX[j] * (k.right - k.left) + pVx[j] * sec * dp
            // Rises fast, then slows (a little drag), and fades out at the end.
            val y = hitY - 4f - pVy[j] * dp * (sec - 0.9f * sec * sec)
            val a = (1f - t) * (1f - t)
            drawCircle(Color(pColor[j].toULong()), pR[j] * dp * (1f - 0.4f * t), Offset(x, y), alpha = a)
        }
    }

    private fun DrawScope.drawWords(keys: KeyboardLayout, hitY: Float, low: Int, high: Int, now: Long) {
        if (measurer == null) return
        val life = wordNanos.toFloat()
        val px = 13f * density
        for (j in 0 until WORDS) {
            val s = wText[j] ?: continue
            if (wStart[j] == 0L || wMidi[j] !in low..high) continue
            val t = ((now - wStart[j]) / life).coerceIn(0f, 1f)
            val m = texts.getOrPut(s) { measure(s, px, if (s == PERFECT) Good else Ink, density, fontScale, Geist)!! }
            val k = keys.rect(wMidi[j])
            val cx = (k.left + k.right) / 2f
            val ease = 1f - (1f - t) * (1f - t)
            val y = hitY - 18f * density - 44f * density * ease - m.size.height
            val x = (cx - m.size.width / 2f).coerceIn(4f, size.width - m.size.width - 4f)
            val a = if (t < 0.15f) t / 0.15f else if (t > 0.6f) (1f - t) / 0.4f else 1f
            drawText(m, topLeft = Offset(x, y), alpha = a)
        }
    }

    private fun DrawScope.drawCombo(now: Long) {
        if (measurer == null || !showCombo) return
        val px = 22f * density
        val pad = 16f * density
        if (combo >= COMBO_MIN) {
            val m = comboLayout(combo, px) ?: return
            val pt = if (comboAt == 0L || still) 1f else ((now - comboAt).toFloat() / pulseNanos).coerceIn(0f, 1f)
            // A pop on each new note; the first appearance grows in from small.
            val s = if (combo == COMBO_MIN && pt < 1f) 0.5f + 0.5f * overshoot(pt) else 1f + 0.18f * (1f - pt) * (1f - pt)
            val padX = 12f * density
            val x = size.width - pad - padX - m.size.width
            val c = Offset(x + m.size.width / 2f, pad + m.size.height / 2f)
            scale(s, c) {
                // A snug pill behind the number, brighter for a moment on each new note.
                drawRoundRect(Accent, Offset(x - padX, pad - 2f * density), Size(m.size.width + 2 * padX, m.size.height + 4f * density),
                    CornerRadius(m.size.height.toFloat()), alpha = 0.22f + 0.35f * (1f - pt))
                drawText(m, topLeft = Offset(x, pad))
            }
        }
        if (shatterAt != 0L) {
            val m = shatterLayout(shattered, px) ?: return
            val t = ((now - shatterAt).toFloat() / shatterNanos).coerceIn(0f, 1f)
            val x = size.width - pad - 12f * density - m.size.width
            val c = Offset(x + m.size.width / 2f, pad + m.size.height / 2f)
            // The number cracks: it shrinks and fades while shards fly out and fall.
            if (t < 0.35f) scale(1f - 0.5f * t, c) { drawText(m, topLeft = Offset(x, pad), alpha = 1f - t / 0.35f) }
            val reach = m.size.width * 1.2f
            for (i in 0 until SHARDS) {
                val ang = SHARD_ANGLE[i]
                val d = reach * (0.4f + 0.6f * SHARD_SPEED[i]) * (1f - (1f - t) * (1f - t))
                val sx = c.x + cos(ang) * d
                val sy = c.y + sin(ang) * d + 30f * density * t * t       // they drop as they go
                val w = m.size.height * 0.28f * (1f - 0.5f * t)
                rotate(360f * t * (if (i % 2 == 0) 1f else -1f) + i * 40f, Offset(sx, sy)) {
                    drawRect(if (i % 3 == 0) WrongGlow else Ink, Offset(sx - w / 2f, sy - w / 3f), Size(w, w * 0.66f), alpha = 1f - t)
                }
            }
        }
    }

    private fun overshoot(t: Float): Float {
        val c = 1.9f; val u = t - 1f
        return 1f + (c + 1f) * u * u * u + c * u * u        // easeOutBack
    }

    companion object {
        const val HIT_MS = 260f
        const val TOUCH_MS = 220f
        const val WRONG_MS = 380f
        const val SPARK_MS = 620f
        const val WORD_MS = 700f
        const val PULSE_MS = 240f
        const val SHATTER_MS = 520f
        /** Right notes in a row before the combo shows. */
        const val COMBO_MIN = 5
        const val PERFECT = "Perfect"
        const val GOOD = "Good"
        private const val SPARKS = 64
        private const val WORDS = 6
        /** 2.14: timing ticks alive at once, how long each shows, and the share of the good window that still reads on the beat. */
        private const val TICKS = 8
        const val TICK_MS = 650f
        private const val TICK_ON = 0.33f
        private const val SHARDS = 9
        private val SHARD_ANGLE = FloatArray(SHARDS) { (it * 2 * PI / SHARDS + 0.3 * sin(it * 1.7)).toFloat() }
        private val SHARD_SPEED = FloatArray(SHARDS) { ((it * 37) % 10) / 10f }
    }
}
