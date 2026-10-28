package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.analytics.Tester
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.lessons.ChimeMode
import com.bigpapituna.sharpears.engine.lessons.ChimeNote
import com.bigpapituna.sharpears.engine.lessons.PopVariant
import com.bigpapituna.sharpears.engine.lessons.StepPopMotion
import com.bigpapituna.sharpears.engine.lessons.StepPopRules
import com.bigpapituna.sharpears.engine.lessons.StepStreak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A pop on screen: [seq] counts up so two pops in a row both play; [streak] is the run of clean steps it crowns, [variant]
 * the picture rolled for it and [chime] what its chime did. [line] (2.13): the cheer pop's step passed line, shown in a
 * bubble over the check (ui/pup/CheerPop.kt StepPopLine), never said; null with Encouragement off.
 */
@Immutable
data class StepPopCue(val seq: Int, val streak: Int, val variant: PopVariant, val chime: ChimeMode, val line: String? = null)

/**
 * The step pop (2.11) for one lesson: its [streak] of clean steps (engine/lessons/StepStreak), the pop to show ([cue],
 * drawn by [StepPop]) and the chime. Make one with [rememberStepPops]; LessonScreen and the debug screen
 * (`--es screen steppop`) both fire through [fire].
 */
@Stable
class StepPops internal constructor(
    private val sampler: Sampler, private val mic: MicInput, private val sound: () -> Boolean,
    private val scope: CoroutineScope, private val tonic: Int, private val telemetry: Boolean,
) {
    val streak = StepStreak()
    var cue by mutableStateOf<StepPopCue?>(null)
        private set
    private var chimeJob: Job? = null
    private var voiceJob: Job? = null
    private val waiting = ArrayList<() -> Unit>()

    /** The chime is ringing now (2.13: a cheer pop's sparkle never sounds on top of it). */
    val chiming: Boolean get() = chimeJob?.isActive == true

    /**
     * The pop for a clean step at [streak]: the picture rolled from [seed] (StepPopRules.variant: never the last one again,
     * the small ones early and the big ones from a streak of four; [force] picks one, for the debug screen), the chime by
     * the mic rule (StepPopRules.chimeMode: App sound off, nothing; a listening mic without headphones made deaf for it
     * first, so it is never heard as the learner's note), the log line and the tester event. Never waits on anything: the
     * page goes on as it would without it.
     */
    fun fire(streak: Int, seed: Long, force: PopVariant? = null, line: String? = null): StepPopCue {
        val mode = StepPopRules.chimeMode(micListening = mic.listening, headphones = mic.headphonesOn(), soundOn = sound())
        val variant = force ?: StepPopRules.variant(streak, cue?.variant, seed)
        val next = StepPopCue((cue?.seq ?: 0) + 1, streak, variant, mode, line)
        cue = next
        Log.d(TAG, "step pop: streak $streak, chime $mode, ${variant.name.lowercase()}")
        if (telemetry) Tester.stepPop(streak)
        if (mode != ChimeMode.SKIP) {
            // 2.16: a line of the build's extra never plays under the chime (extras/Extras.kt).
            com.bigpapituna.sharpears.extras.Extras.chime(StepPopRules.CHIME_MS.toLong())
            if (mode == ChimeMode.PLAY_DEAF) mic.deafFor(StepPopRules.DEAF_MS.toLong())
            val before = chimeJob
            chimeJob = scope.launch {
                before?.cancelAndJoin()            // its notes let go before these strike (two pops close together)
                playChime(sampler, StepPopRules.chime(tonic, streak))
            }
        }
        return next
    }

    /**
     * [say] once the chime is over when one is playing (the pup's line for the step never talks over it), else now. Lines
     * keep their order.
     */
    fun afterChime(say: () -> Unit) {
        if (chimeJob?.isActive != true && waiting.isEmpty()) { say(); return }
        waiting += say
        if (voiceJob?.isActive == true) return
        voiceJob = scope.launch { delay(StepPopRules.VOICE_AFTER_MS.toLong()); drain() }
    }

    /** Lines still waiting for the chime are said now: the step is being left, and its words come before the next one's. */
    fun flush() { voiceJob?.cancel(); drain() }

    private fun drain() { val w = waiting.toList(); waiting.clear(); w.forEach { it() } }
}

/**
 * A [StepPops] for this screen, new for each [key] (a lesson: a new lesson starts a new streak). [tonic]: the lesson's key
 * (StepPopRules.tonicOf). [sound]: Settings > App sound, read at each pop. [telemetry] false: the debug screen.
 */
@Composable
fun rememberStepPops(sampler: Sampler, mic: MicInput, sound: () -> Boolean, key: Any, tonic: Int, telemetry: Boolean = true): StepPops {
    val scope = rememberCoroutineScope()
    // 2.15: in the Plain look the chime needs "Step sound" too (PlainLook.stepChime), read at each pop.
    val plainNow = androidx.compose.runtime.rememberUpdatedState(LocalPlain.current)
    val chime: () -> Boolean = { com.bigpapituna.sharpears.engine.family.PlainLook.stepChime(plainNow.value.on, sound(), plainNow.value.stepSound) }
    return remember(key, tonic) { StepPops(sampler, mic, chime, scope, tonic, telemetry) }
}

/** The chime: its notes struck [StepPopRules.NOTE_GAP_MS] apart, a short fade, then let go (also when the screen goes). */
private suspend fun playChime(sampler: Sampler, notes: List<ChimeNote>) {
    var at = 0
    try {
        for (n in notes) { delay((n.atMs - at).toLong()); at = n.atMs; sampler.noteOn(n.midi, n.velocity) }
        delay((StepPopRules.CHIME_MS - StepPopRules.FADE_MS - at).toLong())
        for (k in 1..3) { notes.forEach { sampler.noteVolume(it.midi, 1f - k / 4f) }; delay(StepPopRules.FADE_MS / 3L) }
    } finally {
        notes.forEach { sampler.noteOff(it.midi) }
    }
}

// The pop's colours, all from the design system: the accent, the pup's gold, the stage's green, the left hand's blue and
// the stage's ink for the check.
private val popGreen = Good
private val popGold = PupGold
private val popPaper = listOf(Accent, PupGold, Good, LeftHandColor)
private val popInk = Ink

/**
 * The step pop over the step (2.11), one of four pictures, each gone in 600 ms (engine/lessons/StepPopMotion.kt has every
 * number): BURST (a green disc springing in with a white check drawing on, a gold ring spreading from 0.6 to 1.2, stars
 * flying out on a spring), CONFETTI (paper bits popping up from the step's bottom edge, falling with gravity, turning, round
 * the check), RAINBOW (an arc in the app's colours drawing on from left to right, the check popping at its end, sparkles
 * trailing the tip), PAW (a paw print stamping in with a squash and two bounces, the check inside it, a ring rippling out).
 * With the phone's animations off, every one is the check shown and faded over 150 ms. Put it over the screen, above the
 * step and under any bar; [area] is the part of the step in view (null: all of this).
 *
 * It takes no touches (nothing here handles a pointer) and never holds up a button or a page: everything moving is read
 * in the draw block from one frame clock, so the screen under it never recomposes for it. Decorative: the cheer bar's
 * words say the same thing. `--ef fxSlow n` slows it n times for a recording.
 */
@Composable
fun StepPop(cue: StepPopCue?, modifier: Modifier = Modifier, area: () -> Rect? = { null }) {
    // 2.15: the Plain look shows the plain check only: its disc, still, for the pop's time, then faded (no burst, confetti,
    // rainbow or paw, nothing moving).
    val plain = LocalPlain.current.on
    val still = reducedMotion()
    val t = remember { mutableFloatStateOf(-1f) }
    val star = remember { unitStar() }
    LaunchedEffect(cue?.seq) {
        if (cue == null) return@LaunchedEffect
        val total = if (still) StepPopMotion.CALM_MS else StepPopMotion.TOTAL_MS
        val slow = com.bigpapituna.sharpears.ui.playground.FxSlow.factor.coerceAtLeast(1f)
        val start = withFrameNanos { it }
        t.floatValue = 0f
        while (t.floatValue < total) withFrameNanos { now -> t.floatValue = (now - start) / 1_000_000f / slow }
        t.floatValue = -1f
    }
    // Its own layer: a frame of the pop re-records only this, never the screen under it.
    Canvas(modifier.graphicsLayer()) {
        val ms = t.floatValue
        val c0 = cue
        if (ms < 0f || c0 == null) return@Canvas
        val box = area() ?: Rect(Offset.Zero, size)
        if (still) { drawCheckDisc(box.center, 1f, StepPopMotion.calmAlpha(ms), 1f); return@Canvas }
        if (plain) { drawCheckDisc(box.center, 1f, StepPopMotion.fade(ms), 1f); return@Canvas }
        when (c0.variant) {
            PopVariant.BURST -> burst(ms, box.center, c0.seq, star)
            PopVariant.CONFETTI -> confetti(ms, box, c0.seq)
            PopVariant.RAINBOW -> rainbow(ms, box, star)
            PopVariant.PAW -> paw(ms, box.center)
        }
    }
}

/** BURST: the ring, the stars flying out, the disc and its check on top. */
private fun DrawScope.burst(ms: Float, c: Offset, seq: Int, star: Path) {
    val m = StepPopMotion
    m.ringAlpha(ms).takeIf { it > 0f }?.let { a -> drawCircle(popGold, m.RING_DP * density * m.ringScale(ms), c, alpha = a, style = Stroke(4f * density)) }
    val out = m.starOut(ms)
    val starA = m.starAlpha(ms)
    if (starA > 0f) for (i in 0 until m.STARS) {
        val deg = m.starAngle(i, seq)
        val rad = Math.toRadians(deg.toDouble())
        val dist = (m.STAR_FROM_DP + m.STAR_REACH_DP * out) * density
        val at = Offset(c.x + dist * sin(rad).toFloat(), c.y - dist * cos(rad).toFloat())
        val r = m.STAR_DP * density * (if (i % 2 == 0) 1f else m.STAR_SMALL) * m.starScale(ms)
        val spin = m.STAR_SPIN_DEG * ms / m.TOTAL_MS * (if (i % 2 == 0) 1f else -1f)
        drawStar(star, at, r, deg + spin, if (i % 2 == 0) popGold else popGreen, starA)
    }
    drawCheckDisc(c, m.discScale(ms), m.discAlpha(ms), m.check(ms))
}

/** CONFETTI: paper bits up from the bottom edge of the step and down again, turning and flipping; the check in the middle. */
private fun DrawScope.confetti(ms: Float, box: Rect, seq: Int) {
    val m = StepPopMotion
    val a = m.bitAlpha(ms)
    val w = m.BIT_W_DP * density
    val h = m.BIT_H_DP * density
    if (a > 0f) for (i in 0 until m.BITS) {
        val (bx, by) = m.bitAt(ms, i, seq)
        val at = Offset(box.center.x + bx * box.width, box.bottom - by * box.height)
        translate(at.x, at.y) {
            rotate(m.bitSpin(ms, i, seq), Offset.Zero) {
                scale(m.bitFlip(ms, i, seq), 1f, Offset.Zero) {
                    drawRect(popPaper[i % popPaper.size], Offset(-w / 2, -h / 2), Size(w, h), alpha = a)
                }
            }
        }
    }
    drawCheckDisc(box.center, m.discScale(ms), m.discAlpha(ms), m.check(ms))
}

/** RAINBOW: four bands drawn on as an arc from left to right, sparkles behind the tip, the check popping at the end. */
private fun DrawScope.rainbow(ms: Float, box: Rect, star: Path) {
    val m = StepPopMotion
    val r = minOf(box.width * m.ARC_W_SHARE, box.height * m.ARC_H_SHARE, m.ARC_MAX_DP * density)
    val band = r * m.BAND_SHARE
    // The arc's centre, low enough that the arc's top and its two ends sit evenly about the middle of the step.
    val ends = -sin(Math.toRadians(m.ARC_FROM_DEG.toDouble())).toFloat()        // how far above the centre the ends are, of r
    val c = Offset(box.center.x, box.center.y + r * (1f + ends) / 2f)
    val a = m.fade(ms)
    for (b in 0 until m.BANDS) {
        val drawn = m.bandDrawn(ms, b)
        if (drawn <= 0f) continue
        val rb = r - b * band
        drawArc(popPaper[b], m.ARC_FROM_DEG, m.ARC_SWEEP_DEG * drawn, useCenter = false, topLeft = Offset(c.x - rb, c.y - rb),
            size = Size(2 * rb, 2 * rb), alpha = a, style = Stroke(band * 1.08f, cap = StrokeCap.Butt))
    }
    // Sparkles trailing the tip, just outside the arc.
    val sa = m.sparkleAlpha(ms)
    if (sa > 0f) m.SPARKLE_LAG_DEG.forEachIndexed { k, lag ->
        val deg = m.tipDeg(ms) - lag
        if (deg < m.ARC_FROM_DEG) return@forEachIndexed
        val rad = Math.toRadians(deg.toDouble())
        val rr = r + band * 1.1f
        val at = Offset(c.x + rr * cos(rad).toFloat(), c.y + rr * sin(rad).toFloat())
        drawStar(star, at, m.SPARKLE_DP * density * (1f - 0.18f * k) * m.sparkleScale(ms, k), deg, if (k % 2 == 0) popGold else popInk, sa)
    }
    // The check at the end of the arc, on its middle bands.
    val end = Math.toRadians((m.ARC_FROM_DEG + m.ARC_SWEEP_DEG).toDouble())
    val rm = r - band * (m.BANDS - 1) / 2f
    drawCheckDisc(Offset(c.x + rm * cos(end).toFloat(), c.y + rm * sin(end).toFloat()), m.arcCheckScale(ms) * 0.85f, a, m.arcCheck(ms))
}

/** PAW: the ripple from the landing, the paw stamping down and bouncing, the check inside its pad. */
private fun DrawScope.paw(ms: Float, c: Offset) {
    val m = StepPopMotion
    m.rippleAlpha(ms).takeIf { it > 0f }?.let { a -> drawCircle(popGold, m.PAW_RIPPLE_DP * density * m.rippleScale(ms), c, alpha = a, style = Stroke(3f * density)) }
    val (sx, sy) = m.pawScale(ms)
    val a = m.pawAlpha(ms)
    if (a <= 0f || sx <= 0f) return
    val u = m.PAW_DP * density
    translate(c.x, c.y) {
        scale(sx, sy, Offset.Zero) {
            // The main pad, and four toes round its top, the outer ones turned out.
            drawOval(Accent, Offset(-1.0f * u, -0.27f * u), Size(2.0f * u, 1.65f * u), alpha = a)
            for ((x, y, turn) in listOf(Triple(-1.12f, -0.5f, -22f), Triple(-0.4f, -1.05f, -6f), Triple(0.4f, -1.05f, 6f), Triple(1.12f, -0.5f, 22f)))
                rotate(turn, Offset(x * u, y * u)) { drawOval(Accent, Offset((x - 0.31f) * u, (y - 0.4f) * u), Size(0.62f * u, 0.8f * u), alpha = a) }
            drawCheck(Offset(0f, 0.55f * u), 0.95f * u, m.pawCheck(ms), a)
        }
    }
}

/** A star of radius [r] at [at], turned [deg]. */
private fun DrawScope.drawStar(star: Path, at: Offset, r: Float, deg: Float, color: Color, alpha: Float) {
    translate(at.x, at.y) { rotate(deg, Offset.Zero) { scale(r, r, Offset.Zero) { drawPath(star, color, alpha = alpha) } } }
}

/** The green disc with the white check, [scale] of its size, [alpha] opaque, [drawn] of the check drawn on (0..1). */
private fun DrawScope.drawCheckDisc(c: Offset, scale: Float, alpha: Float, drawn: Float) {
    if (alpha <= 0f || scale <= 0f) return
    val r = StepPopMotion.DISC_DP * density * scale
    drawCircle(popGreen, r, c, alpha = alpha)
    drawCheck(c, r, drawn, alpha)
}

/** The check's two strokes (the short one down, the long one up) in a circle of radius [r] round [c], [drawn] of it drawn on. */
private fun DrawScope.drawCheck(c: Offset, r: Float, drawn: Float, alpha: Float) {
    if (drawn <= 0f || alpha <= 0f) return
    val a = Offset(c.x - 0.42f * r, c.y + 0.02f * r)
    val b = Offset(c.x - 0.12f * r, c.y + 0.32f * r)
    val e = Offset(c.x + 0.45f * r, c.y - 0.30f * r)
    val l1 = hypot(b.x - a.x, b.y - a.y)
    val l2 = hypot(e.x - b.x, e.y - b.y)
    val len = (l1 + l2) * drawn
    val w = 0.16f * r
    if (len <= l1) drawLine(popInk, a, a + (b - a) * (len / l1), w, StrokeCap.Round, alpha = alpha)
    else {
        drawLine(popInk, a, b, w, StrokeCap.Round, alpha = alpha)
        drawLine(popInk, b, b + (e - b) * ((len - l1) / l2), w, StrokeCap.Round, alpha = alpha)
    }
}

/** A five-pointed star of radius 1 round the origin, a point straight up. */
private fun unitStar(): Path = Path().apply {
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) 1f else 0.45f
        val a = Math.PI * k / 5
        val x = (r * sin(a)).toFloat()
        val y = (-r * cos(a)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
