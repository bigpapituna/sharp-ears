package com.bigpapituna.sharpears.ui.playground

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.Answer
import com.bigpapituna.sharpears.engine.playground.HeardBy
import com.bigpapituna.sharpears.engine.playground.Loudness
import com.bigpapituna.sharpears.engine.playground.MissWhy
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.engine.playground.SoftLoud
import com.bigpapituna.sharpears.engine.playground.SoftLoudGame
import com.bigpapituna.sharpears.engine.playground.Verdict
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import com.bigpapituna.sharpears.ui.pup.Pose

internal val NightTop = Color(0xFF111A33)
private val NightBottom = Color(0xFF2B2346)
private val Moon = Color(0xFFF6EBCB)
internal val Cushion = Color(0xFF7A3E2C)

/** How the pup is: asleep, stirring (a medium note in a soft round), jumping awake (a loud one), or yawning back to sleep. */
internal enum class Nap { ASLEEP, STIR, AWAKE, YAWN }

/**
 * Lesson 11, "Soft and loud": the pup naps on his cushion under the moon. The first middle C sets the level ("the way
 * you always play"); then each round wants soft (he sleeps on, the Zs drift) or loud (he jumps awake, then yawns back to
 * sleep), and is shown first: the app plays middle C softly or loudly. The note matcher listens for the keys around
 * middle C (any of them will do, StageKeys.SOFT_LOUD; a key it does not listen for is named and counts too); the
 * verdict is its rough guess from the strike's rise ([SoftLoudGame]), shown on a meter from soft to loud. A round not
 * matched in two tries is let go by the game, a round with nothing heard by the stage after three quiet stretches.
 * Without a microphone, two pads stand in: soft and loud.
 */
@Composable
internal fun SoftLoudStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val offset = LocalProfile.current.micOffsetDb
    // 2.3: the workshop's heavy arm and soft arm play their own rounds (loud, soft, loud, soft).
    val game = remember(ex) { SoftLoudGame(rounds = SoftLoud.roundsFor(lessonId), offsetDb = offset) }
    var seq by remember { mutableIntStateOf(0) }
    var lastRise by remember { mutableStateOf<Double?>(null) }
    var nap by remember { mutableStateOf(Nap.ASLEEP) }
    val jump = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val still = reducedMotion()

    fun react(v: Verdict) {
        scope.launch {
            when {
                v.woke -> {
                    nap = Nap.AWAKE
                    if (!still) { jump.snapTo(0f); jump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 260f)) }
                    delay(1500); nap = Nap.YAWN; delay(1100)
                    if (!still) jump.animateTo(0f, tween(400))
                    nap = Nap.ASLEEP
                }
                v.target == Loudness.SOFT && v.heard == Loudness.MEDIUM -> { nap = Nap.STIR; delay(1200); nap = Nap.ASLEEP }
                else -> nap = Nap.ASLEEP
            }
        }
    }
    /** The prompt for the round the game is on now. */
    fun prompt(): String = when {
        game.done -> "Goodnight. That was lovely."
        game.round < 0 -> "First, play middle C your usual way."
        game.target == Loudness.SOFT -> "Soft, so I keep sleeping."
        else -> "Now loud. Wake me up!"
    }
    val st = rememberStage(ex, "$lessonId-softloud", lessonId, "soft_loud", deps, inject, onEnded,
        keys = { StageKeys.SOFT_LOUD },
        demoKeys = { listOf(60) },
        // The demo plays middle C the way the round wants it: medium for the first note, then soft or loud.
        velocity = { when { game.done || game.round < 0 -> 72; game.target == Loudness.SOFT -> 26; else -> 120 } },
        judge = { s, _ ->
            val rise = s.riseDb
            if ((s.by != HeardBy.MATCHER && s.by != HeardBy.NAMED) || rise == null || game.done) Answer.Heard
            else {
                val v = game.heard(rise)
                lastRise = rise; seq++
                Log.d(TAG, "playground: soft-loud ${s.by.name.lowercase()} ${s.midi?.let(::noteName)} rise ${"%.1f".format(rise)} dB -> ${v.heard}, " +
                    "wanted ${v.target}, match=${v.match} advance=${v.advance}")
                react(v)
                if (v.advance) Answer.Found(60) else Answer.Miss(if (v.target == Loudness.LOUD) MissWhy.TOO_SOFT else MissWhy.TOO_LOUD, s.midi)
            }
        },
        foundLine = { _, _ -> prompt() },
        missLine = { a, _ -> if (a.why == MissWhy.TOO_LOUD) "Oops, you woke me! Softer, please." else "A little louder!" },
        onLetGo = { if (!game.done) { game.skip(); Log.d(TAG, "playground: soft-loud round let go, now ${game.round}") } },
    )
    val run = st.run
    val line = stageLine(st, remember(run.version, seq, st.demoKeys) { if (st.demoKeys.isNotEmpty()) "Listen!" else prompt() })

    val clock = rememberInfiniteTransition(label = "nap")
    val breathe by clock.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "breathe")
    val twinkle by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "twinkle")
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { night(if (still) 0.5f else twinkle) }
        val pupSize = (maxHeight * 0.42f).coerceIn(110.dp, 210.dp)
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                key(run.version, seq) { RoundIcons(game.rounds, game.round, Modifier.weight(1f, fill = false)) }
            }
            StageLine(line, Modifier.padding(top = 10.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                // The cushion, the pup on it, and his Zs while he sleeps.
                Canvas(Modifier.fillMaxWidth().height(pupSize * 0.32f)) {
                    drawRoundRect(Cushion, Offset(size.width / 2 - pupSize.toPx() * 0.6f, size.height * 0.3f), Size(pupSize.toPx() * 1.2f, size.height * 0.6f),
                        CornerRadius(size.height * 0.3f))
                }
                val lift = jump.value
                StagePup(when (nap) { Nap.ASLEEP, Nap.STIR -> Pose.SLEEPING; Nap.AWAKE -> Pose.SURPRISED; Nap.YAWN -> Pose.GOODNIGHT },
                    Modifier.offset { IntOffset(if (nap == Nap.STIR && !still) (sin(breathe * 6) * 4).roundToInt() else 0,
                        -(lift * pupSize.toPx() * 0.35f).roundToInt() - (pupSize.toPx() * 0.12f).roundToInt()) },
                    size = pupSize, lift = { lift }, fx = st.fx)   // asleep, the rig's SLEEP breathes for him (2.10)
                if (nap == Nap.ASLEEP || nap == Nap.STIR) Canvas(Modifier.fillMaxSize()) { zs(if (still) 0.5f else (breathe / (2 * Math.PI)).toFloat(), nap == Nap.STIR, measurer) }
                // A ring rises from the cushion for every sound heard.
                Ripples(st.fx, Modifier.fillMaxWidth().height(pupSize * 0.6f))
            }
            Meter(lastRise, game.anchorDb, offset, Modifier.fillMaxWidth().padding(top = 8.dp))
            if (!st.listening && !game.done && run.started) {
                // No microphone: two pads stand in for soft and loud.
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    val base = game.anchorDb ?: -32.0
                    Pad("Soft", LeftHandColor) { st.tap(60, riseDb = base - 8) }
                    Pad("Loud", Accent) { st.tap(60, riseDb = base + 8) }
                }
            }
        }
        if (game.done && game.matched > 0) Confetti(game.matched, Modifier.fillMaxSize())
    }
}

/** The stage's rings for every sound, rising from the cushion (a stage with no keyboard to show them on). */
@Composable
internal fun Ripples(fx: StageFx, modifier: Modifier) {
    var clock by remember { mutableLongStateOf(0L) }
    val newest = fx.ripples.lastOrNull()?.id
    LaunchedEffect(newest) {
        while (fx.ripples.any { System.nanoTime() - it.startNanos < StageFx.RIPPLE_MS * 1_000_000 }) withFrameNanos { clock = System.nanoTime() }
        clock = System.nanoTime()
    }
    Canvas(modifier) {
        val now = maxOf(clock, 1L)
        for (r in fx.ripples) {
            val p = ((now - r.startNanos) / 1e6f / StageFx.RIPPLE_MS).coerceIn(0f, 1f)
            if (p >= 1f) continue
            val c = Offset(size.width / 2, size.height * 0.75f)
            val radius = size.height * (0.15f + 0.6f * (1f - (1f - p) * (1f - p)))
            drawCircle(r.colour.copy(alpha = 0.18f * (1f - p)), radius, c)
            drawCircle(r.colour.copy(alpha = 0.85f * (1f - p)), radius, c, style = Stroke((4f * (1f - p) + 1f) * density))
        }
    }
}

/** A round stand-in pad for a phone without its microphone. */
@Composable
private fun Pad(label: String, color: Color, onTap: () -> Unit) {
    Box(Modifier.size(width = 120.dp, height = 56.dp).background(color.copy(alpha = 0.85f), RoundedCornerShape(28.dp))
        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onTap), contentAlignment = Alignment.Center) {
        Text(label, color = Color(0xFF1E1A17), fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
    }
}

/** One icon per round: a ring for the first note, then a moon (soft) or a burst (loud); done ones filled, the current one big. */
@Composable
internal fun RoundIcons(rounds: List<Loudness>, current: Int, modifier: Modifier = Modifier) {
    val all = listOf<Loudness?>(null) + rounds
    Canvas(modifier.size(width = (all.size * 26).dp, height = 26.dp).semantics {
        contentDescription = if (current < 0) "First note" else "Round ${current + 1} of ${rounds.size}: ${rounds.getOrNull(current)?.name?.lowercase() ?: "done"}"
    }) {
        val step = size.height
        all.forEachIndexed { i, kind ->
            val idx = i - 1
            val c = Offset(step / 2 + i * step, size.height / 2)
            val done = idx < current
            val now = idx == current
            val r = step * (if (now) 0.46f else 0.34f)
            val color = if (done || now) (if (kind == Loudness.LOUD) Accent else if (kind == Loudness.SOFT) LeftHandColor else PupGold) else InkDim.copy(alpha = 0.4f)
            when (kind) {
                null -> drawCircle(color, r * 0.8f, c, style = Stroke(2.dp.toPx()))
                Loudness.SOFT -> { drawCircle(color, r, c); drawCircle(if (done || now) NightTop else Color(0xFF1E1A17), r * 0.85f, c + Offset(r * 0.45f, -r * 0.3f)) }
                else -> { drawPath(star(c, r, inner = 0.5f), color) }
            }
        }
    }
}

/** The meter: where the last note sat between soft and loud, with the soft and loud zones around the player's own level. */
@Composable
internal fun Meter(rise: Double?, anchor: Double?, offset: Double, modifier: Modifier = Modifier) {
    val label = rise?.let { SoftLoud.grade(it, anchor, offset) }
    Column(modifier) {
        Text(if (label == null) "How loud I hear it" else "I hear: " + label.name.lowercase(), color = InkDim, fontSize = 13.sp)
        Canvas(Modifier.fillMaxWidth().height(18.dp).padding(top = 4.dp).semantics { contentDescription = "Loudness meter" }) {
            val center = anchor ?: (SoftLoud.SOFT_AT_MOST_DB + SoftLoud.LOUD_AT_LEAST_DB) / 2 - offset
            val span = 18.0
            fun x(db: Double) = (((db - (center - span)) / (2 * span)).coerceIn(0.0, 1.0) * size.width).toFloat()
            val softEdge = if (anchor != null) anchor - SoftLoud.MARGIN_DB else SoftLoud.SOFT_AT_MOST_DB - offset
            val loudEdge = if (anchor != null) anchor + SoftLoud.MARGIN_DB else SoftLoud.LOUD_AT_LEAST_DB - offset
            val h = size.height
            drawRoundRect(Ink.copy(alpha = 0.12f), Offset.Zero, Size(size.width, h), CornerRadius(h / 2))
            drawRoundRect(LeftHandColor.copy(alpha = 0.55f), Offset.Zero, Size(x(softEdge), h), CornerRadius(h / 2))
            drawRoundRect(Accent.copy(alpha = 0.55f), Offset(x(loudEdge), 0f), Size(size.width - x(loudEdge), h), CornerRadius(h / 2))
            rise?.let { r -> drawCircle(Ink, h * 0.62f, Offset(x(r), h / 2)); drawCircle(Color(0xFF1E1A17), h * 0.3f, Offset(x(r), h / 2)) }
        }
    }
}

/** The night: a deep sky, the moon, twinkling stars. */
internal fun DrawScope.night(twinkle: Float, moon: Boolean = true) {
    drawRect(Brush.verticalGradient(listOf(NightTop, NightBottom)))
    val m = Offset(size.width * 0.84f, size.height * 0.16f)
    val r = minOf(size.width, size.height) * 0.07f
    // A full moon with three soft craters (left out where a stage's own stars cross that corner).
    if (moon) {
        drawCircle(Brush.radialGradient(listOf(Moon.copy(alpha = 0.35f), Color.Transparent), m, r * 3), r * 3, m)
        drawCircle(Moon, r, m)
        for ((dx, dy, cr) in listOf(Triple(-0.3f, -0.25f, 0.22f), Triple(0.28f, 0.1f, 0.16f), Triple(-0.05f, 0.38f, 0.12f)))
            drawCircle(Color(0xFFE2D3A8), r * cr, m + Offset(r * dx, r * dy))
    }
    for (k in 0 until 14) {
        val sx = size.width * ((k * 0.137f + 0.05f) % 1f); val sy = size.height * ((k * 0.211f + 0.07f) % 0.55f)
        val a = 0.25f + 0.6f * (if (k % 2 == 0) twinkle else 1f - twinkle)
        drawPath(star(Offset(sx, sy), 3.dp.toPx() + (k % 3).dp.toPx(), inner = 0.4f), Color.White.copy(alpha = a))
    }
}

/** Zs drifting up and away from the sleeping pup; bigger when he stirs. */
internal fun DrawScope.zs(t: Float, stir: Boolean, measurer: androidx.compose.ui.text.TextMeasurer) {
    for (k in 0 until 3) {
        val p = (t + k / 3f) % 1f
        val x = size.width * (0.58f + 0.12f * p)
        val y = size.height * (0.55f - 0.45f * p)
        val s = (if (stir) 26f else 18f) + 10f * p
        val l = measurer.measure("z", TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = s.sp, color = Ink.copy(alpha = 1f - p)))
        drawText(l, topLeft = Offset(x, y))
    }
}
