package com.bigpapituna.sharpears.ui.playground

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.playground.Answer
import com.bigpapituna.sharpears.engine.playground.HighLowJudge
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.engine.playground.flightHeight
import com.bigpapituna.sharpears.engine.playground.wantsHigh
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

private val SkyTop = Color(0xFF16233A)
private val SkyMid = Color(0xFF34506F)
private val Horizon = Color(0xFFE2A06A)
private val GrassTop = Color(0xFF4A7A3F)
private val GrassBottom = Color(0xFF2B4A26)

/**
 * Lesson 1, "High and low": the pup flies. Each round asks for one side, shown by a glowing star at the top or a
 * cushion on the grass and the matching half of the keyboard, and shown first: the app plays a key on that side
 * ("Let me show you"). The note matcher listens for six keys on each side (StageKeys.HIGH_LOW), so a key on either side
 * is heard as itself; a key it does not listen for is named by its wrong-key search, and the pitch tracker's guess
 * counts when it is clearly on one side (HighLowJudge). Every key heard sends the pup to its height; the other side
 * makes him tilt his head and say which way; a sound with no key makes his ears twitch and a ring spread on the keys.
 * Three tries and the round goes by with one star. The keys can be tapped as well.
 */
@Composable
internal fun HighLowStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    val height = remember { Animatable(0f) }
    var tiltSeq by remember { mutableIntStateOf(0) }
    fun fly(midi: Double) = scope.launch {
        val t = flightHeight(midi)
        if (still) height.snapTo(t) else height.animateTo(t, spring(dampingRatio = 0.55f, stiffness = 70f))
    }
    fun wantHigh(run: StageRun) = run.rounds.current?.let(::wantsHigh) ?: true
    val st = rememberStage(ex, "$lessonId-highlow", lessonId, "high_low", deps, inject, onEnded,
        keys = { StageKeys.HIGH_LOW },
        // The round's demonstration key, moved by octaves onto the keyboard on screen (C3 to C6), on the same side.
        demoKeys = { run -> run.rounds.demoKeys().map { k -> var m = k; while (m > 84) m -= 12; while (m < 48) m += 12; m } },
        judge = { s, run -> HighLowJudge.judge(s, wantHigh(run)).also { a -> if (a !is Answer.Heard) s.near?.let { fly(s.guess ?: it.toDouble()) } } },
        foundLine = { _, key -> if (key > 60) "Up I go!" else "Landed!" },
        missLine = { _, run -> if (wantHigh(run)) "Higher!" else "Lower!" },
        onMiss = { tiltSeq++ },
    )
    val run = st.run
    val next = remember(run.version) { run.rounds.current }
    val wantHigh = next?.let(::wantsHigh)
    var tiltNow by remember { mutableStateOf(false) }
    LaunchedEffect(tiltSeq) { if (tiltSeq > 0) { tiltNow = true; delay(1300); tiltNow = false } }
    // The demo's key glows; the pup lifts a little toward it while it plays.
    val demo = st.demoKeys
    LaunchedEffect(demo) { demo.firstOrNull()?.let { if (!still) height.animateTo(flightHeight(it.toDouble()) * 0.35f, tween(500)) } }
    val finished = run.finished
    val line = stageLine(st, when {
        finished -> null
        demo.isNotEmpty() -> "Listen!"
        else -> null
    })

    // Clouds drift and the target breathes; all still with animations off.
    val clock = rememberInfiniteTransition(label = "sky")
    val drift by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(46_000, easing = LinearEasing)), label = "drift")
    val pulse by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    val bob by clock.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "bob")

    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().semantics {
            contentDescription = when (wantHigh) { true -> "The pup waits to fly: play a high key"; false -> "The pup waits to land: play a low key"; null -> "The pup in the garden sky" }
        }) {
            val pupSize = (maxHeight * 0.34f).coerceIn(88.dp, 170.dp)
            val ground = maxHeight * 0.84f
            val travel = ground - pupSize - 30.dp
            Canvas(Modifier.fillMaxSize()) {
                sky(if (still) 0.3f else drift, if (still) 0.5f else pulse, wantHigh, ground.toPx(), height.value)
            }
            val y = ground - pupSize - travel * height.value
            val bobPx = if (still || height.value < 0.2f) 0f else sin(bob) * 6f
            PaintedPup(
                when { tiltNow -> Painted.HEAD_TILT; finished -> Painted.GREAT_JOB; height.value > 0.22f -> Painted.FLYING; else -> Painted.STANDING },
                Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (y.toPx() + bobPx).roundToInt()) }, size = pupSize,
                lift = { height.value }, shadow = false, poke = st.fx.poke)
            StageTop(run.count, run.done, Modifier.align(Alignment.TopStart).padding(12.dp), letGo = run.letGo)
            StageLine(line, Modifier.align(Alignment.TopCenter).offset { IntOffset((pupSize * 0.62f).roundToPx(), (y - 8.dp).toPx().roundToInt().coerceAtLeast(40)) })
            if (finished) Confetti(run.count, Modifier.fillMaxSize())
        }
        BigKeys(48, 84, "Piano keys from C3 to C6. The ${if (wantHigh == false) "low keys, left of middle C," else "high keys, right of middle C,"} glow",
            Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            fill = { m -> when {
                m in demo -> PupGold
                wantHigh == true && m > 60 -> Accent.copy(alpha = 0.55f + 0.25f * (if (still) 0.5f else pulse))
                wantHigh == false && m < 60 -> LeftHandColor.copy(alpha = 0.55f + 0.25f * (if (still) 0.5f else pulse))
                else -> null
            } },
            last = run.last, topRoom = 0.dp, fx = st.fx,
            onKey = { m ->
                if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(700); deps.sampler.noteOff(m) } }
                st.tap(m)
            },
            overlay = { g ->
                // Middle C, the pup's home key: a small gold dot at the front of the key.
                val r = g.rect(60)
                drawCircle(PupGold, r.width * 0.16f, Offset(r.center.x, r.bottom - r.width * 0.35f))
            })
    }
}

/** The garden at dusk: sky, drifting clouds, the grass, and the target for the round (a star up high, a cushion below). */
private fun DrawScope.sky(drift: Float, pulse: Float, wantHigh: Boolean?, groundY: Float, height: Float) {
    drawRect(Brush.verticalGradient(listOf(SkyTop, SkyMid, Horizon), 0f, groundY))
    // Clouds: soft rounded puffs drifting right and wrapping round.
    val w = size.width
    for ((i, c) in listOf(Triple(0.12f, 0.18f, 1.0f), Triple(0.55f, 0.32f, 0.8f), Triple(0.83f, 0.12f, 1.2f), Triple(0.35f, 0.5f, 0.7f)).withIndex()) {
        val x = ((c.first + drift * (0.6f + 0.2f * i)) % 1.2f - 0.1f) * w
        val y = c.second * groundY
        val s = c.third * w * 0.09f
        val a = 0.16f + 0.05f * i
        drawCircle(Color.White.copy(alpha = a), s, Offset(x, y))
        drawCircle(Color.White.copy(alpha = a), s * 0.8f, Offset(x + s * 0.9f, y + s * 0.15f))
        drawCircle(Color.White.copy(alpha = a), s * 0.7f, Offset(x - s * 0.85f, y + s * 0.2f))
    }
    // The grass, with a few flowers.
    drawRect(Brush.verticalGradient(listOf(GrassTop, GrassBottom), groundY, size.height), Offset(0f, groundY), Size(w, size.height - groundY))
    for (k in 0 until 9) {
        val fx = w * (0.06f + k * 0.11f); val fy = groundY + (size.height - groundY) * (0.35f + 0.3f * ((k * 37) % 5) / 5f)
        drawCircle(listOf(Accent, Color(0xFFF3EEE9), LeftHandColor)[k % 3].copy(alpha = 0.85f), 3.5.dp.toPx(), Offset(fx, fy))
    }
    // The pup's shadow on the grass, smaller the higher he flies.
    val sw = w * 0.16f * (1f - 0.65f * height)
    drawOval(Color.Black.copy(alpha = 0.3f * (1f - 0.5f * height)), Offset(w / 2 - sw / 2, groundY - 3.dp.toPx()), Size(sw, 8.dp.toPx()))
    when (wantHigh) {
        true -> {
            // A star high up, breathing, with chevrons pointing to it.
            val c = Offset(w * 0.5f, groundY * 0.1f + 22.dp.toPx())
            val r = 16.dp.toPx() * (1f + 0.12f * pulse)
            drawCircle(Brush.radialGradient(listOf(Accent.copy(alpha = 0.45f), Color.Transparent), c, r * 3), r * 3, c)
            drawPath(star(c, r), Accent)
            chevrons(Offset(w * 0.8f, groundY * 0.45f), up = true, pulse)
            chevrons(Offset(w * 0.2f, groundY * 0.45f), up = true, pulse)
        }
        false -> {
            // A cushion on the grass to land on.
            val cw = w * 0.3f; val ch = 18.dp.toPx()
            val top = groundY - ch * 0.6f
            drawRoundRect(Brush.radialGradient(listOf(LeftHandColor.copy(alpha = 0.5f), Color.Transparent), Offset(w / 2, top), cw), Offset(w / 2 - cw, top - ch * 2),
                Size(cw * 2, ch * 4), androidx.compose.ui.geometry.CornerRadius(ch * 2))
            drawRoundRect(LeftHandColor, Offset(w / 2 - cw / 2, top), Size(cw, ch), androidx.compose.ui.geometry.CornerRadius(ch / 2))
            chevrons(Offset(w * 0.8f, groundY * 0.45f), up = false, pulse)
            chevrons(Offset(w * 0.2f, groundY * 0.45f), up = false, pulse)
        }
        null -> {}
    }
}

/** Three stacked chevrons at [c], pointing up or down, the leading one brightest. */
private fun DrawScope.chevrons(c: Offset, up: Boolean, pulse: Float) {
    val s = 11.dp.toPx(); val gap = 12.dp.toPx()
    for (k in 0 until 3) {
        val dir = if (up) -1f else 1f
        val y = c.y + dir * (k - 1) * gap + dir * pulse * 4.dp.toPx()
        val p = Path().apply { moveTo(c.x - s, y - dir * s * 0.5f); lineTo(c.x, y + dir * s * 0.5f); lineTo(c.x + s, y - dir * s * 0.5f) }
        drawPath(p, Color.White.copy(alpha = 0.25f + 0.25f * k), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}
