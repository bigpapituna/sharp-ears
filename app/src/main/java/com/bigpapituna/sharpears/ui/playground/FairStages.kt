package com.bigpapituna.sharpears.ui.playground

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.Answer
import com.bigpapituna.sharpears.engine.playground.FairLines
import com.bigpapituna.sharpears.engine.playground.NoteJudge
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.engine.playground.StrongmanJudge
import com.bigpapituna.sharpears.engine.playground.carouselSpeed
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.skin.noteColour
import com.bigpapituna.sharpears.ui.world.carouselHorse
import com.bigpapituna.sharpears.ui.world.WorldSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 2.3, the fair's games (part A): balloons to pop, the carousel to spin, the strongman's bell to ring. Each is a stage of
 * three rounds with a star a round (StageTop), heard like every Playground stage (rememberStage: a demo first, strict keys,
 * three tries and the round goes by, every sound answered). The photo booth is PhotoStage.kt.
 */

private const val NOTE_GAP_MS = 450L

/** A phrase game's coach: the demo long enough for the longest round of [notes] at [gapMs] a note, and a [call] said before it. */
internal fun phraseCoach(notes: Int, gapMs: Long = NOTE_GAP_MS, call: Boolean = false) = (if (call) 1_300L else 0L).let { c ->
    RoundCoach(firstDemoMs = 1_300 + c + notes * gapMs + 900, demoMs = c + notes * gapMs + 900, againDemoMs = 1_000 + notes * gapMs + 900)
}

// ---- Balloon pop ---------------------------------------------------------------------------------------------------------

/**
 * Balloon pop ([com.bigpapituna.sharpears.engine.playground.Game.BALLOONS]): each round a bunch of balloons floats up, one
 * per note, each the colour of its key (a reader sees the letter on it too). The pup calls the first by its colour (or
 * letter) and shows the bunch (each balloon bobs as its key rings gold); then each key heard pops its balloon with a burst
 * of confetti, and the pup calls the next one. The keys of the C position, any octave (NoteJudge).
 */
@Composable
internal fun BalloonStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val reader = LocalProfile.current.let { !it.isKid || it.reader }
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val bursts = remember { mutableStateListOf<Pair<Int, Long>>() }
    val longest = remember(ex) { longestPhrase(ex) }
    val st = rememberStage(ex, "$lessonId-balloons", lessonId, "balloons", deps, inject, onEnded,
        keys = { StageKeys.FIVE },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, run -> NoteJudge.judge(s, run.rounds.expectedKey()) },
        foundLine = { run, _ -> when {
            run.finished -> FairLines.ALL_POPPED
            run.rounds.doneInRound == 0 -> FairLines.POPPED[(run.done - 1).coerceAtLeast(0) % FairLines.POPPED.size]
            else -> run.rounds.expectedKey()?.let { FairLines.next(it, reader) }
        } },
        missLine = { _, _ -> FairLines.balloonMiss(reader) },
        prompt = { run -> run.rounds.expectedKey()?.let { FairLines.balloon(it, reader) } },
        onFound = { k -> bursts += k to System.nanoTime(); while (bursts.size > 6) bursts.removeAt(0) },
        coach = { phraseCoach(longest, call = true) }, phrases = true, demoGapMs = NOTE_GAP_MS,
    )
    val run = st.run
    val v = run.version
    val bunch = remember(v) { run.rounds.roundGroups.map { it.notes.first().midi } }
    val popped = remember(v) { run.rounds.doneInRound }
    val demo = st.demoKeys
    val line = stageLine(st, when {
        run.finished -> FairLines.ALL_POPPED
        demo.isNotEmpty() -> "Watch them float up!"
        run.phase == RoundCoach.Phase.LISTEN -> run.rounds.expectedKey()?.let { FairLines.balloon(it, reader) }
        else -> null
    })
    val clock = rememberInfiniteTransition(label = "balloons")
    val bob by clock.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "bob")
    val rise = remember { Animatable(0f) }
    LaunchedEffect(bunch) { if (still) rise.snapTo(1f) else { rise.snapTo(0f); rise.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) } }
    val measurer = rememberTextMeasurer()
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(bursts.size) { val until = System.nanoTime() + 700_000_000L; while (System.nanoTime() < until) withFrameNanos { frame++ } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        Canvas(Modifier.fillMaxSize()) { fairLights(if (still) 0.3f else bob / (2 * PI).toFloat()) }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(if (run.finished) Painted.DANCING else if (demo.isNotEmpty()) Painted.POINTING else Painted.LISTENING,
                    size = (roomH * 0.17f).coerceIn(56.dp, 100.dp), poke = st.fx.poke)
                Spacer(Modifier.width(8.dp))
                StageLine(line)
            }
            Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "${bunch.size - popped} balloons to pop" }) {
                if (frame < 0) return@Canvas      // read so the bursts redraw while they fly
                val n = bunch.size.coerceAtLeast(1)
                val slot = size.width / n
                val r = minOf(slot * 0.32f, size.height * 0.2f)
                bunch.forEachIndexed { i, k ->
                    val x = slot * (i + 0.5f)
                    val home = size.height * (0.42f + 0.08f * (i % 2))
                    val y = size.height + r * 2 + (home - size.height - r * 2) * rise.value + (if (still) 0f else sin(bob + i * 1.3f) * r * 0.12f)
                    if (i < popped) return@forEachIndexed
                    val next = i == popped && run.phase == RoundCoach.Phase.LISTEN
                    val sounding = demo.isNotEmpty() && st.demoIndex == i
                    balloon(Offset(x, y), r * (if (next || sounding) 1.15f else 1f), noteColour(k), if (reader) noteName(k).take(1) else null, measurer, glow = next || sounding)
                }
                val now = System.nanoTime()
                bursts.forEach { (k, at) ->
                    val p = ((now - at) / 7e8f).coerceIn(0f, 1f)
                    if (p >= 1f) return@forEach
                    val i = bunch.indexOf(k).takeIf { it >= 0 } ?: return@forEach
                    burst(Offset(slot * (i + 0.5f), size.height * (0.42f + 0.08f * (i % 2))), r, noteColour(k), p)
                }
            }
            BigKeys(57, 71, "Piano keys from A3 to B4: each balloon's key has its colour.",
                Modifier.fillMaxWidth().height((roomH * 0.24f).coerceIn(96.dp, 170.dp)),
                fill = { m -> if (m in StageKeys.FIVE) noteColour(m).copy(alpha = if (st.demoIndex?.let { demo.getOrNull(it) } == m) 1f else 0.45f) else null },
                last = run.last, fx = st.fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(600); deps.sampler.noteOff(m) } }
                    st.tap(m)
                })
        }
    }
}

/** The most notes in one round (a phrase) of [ex]'s right hand. */
internal fun longestPhrase(ex: com.bigpapituna.sharpears.engine.Exercise): Int =
    com.bigpapituna.sharpears.engine.parseTokens(ex.right).fold(listOf(0)) { acc, t -> if (t.rest) acc + 0 else acc.dropLast(1) + (acc.last() + 1) }.max()

/** A balloon at [c], [r] wide, in [colour], its [letter] on it for a reader, on a curly string; [glow] when it is next. */
internal fun DrawScope.balloon(c: Offset, r: Float, colour: Color, letter: String?, measurer: androidx.compose.ui.text.TextMeasurer, glow: Boolean) {
    val string = Path().apply { moveTo(c.x, c.y + r * 1.15f); quadraticTo(c.x - r * 0.3f, c.y + r * 1.6f, c.x, c.y + r * 2.0f); quadraticTo(c.x + r * 0.3f, c.y + r * 2.4f, c.x, c.y + r * 2.9f) }
    drawPath(string, Color.White.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    if (glow) drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.55f), Color.Transparent), c, r * 1.9f), r * 1.9f, c)
    drawOval(Color.Black.copy(alpha = 0.35f), Offset(c.x - r * 1.04f, c.y - r * 1.24f), Size(r * 2.08f, r * 2.48f))
    drawOval(colour, Offset(c.x - r, c.y - r * 1.2f), Size(r * 2f, r * 2.4f))
    drawPath(Path().apply { moveTo(c.x - r * 0.18f, c.y + r * 1.32f); lineTo(c.x + r * 0.18f, c.y + r * 1.32f); lineTo(c.x, c.y + r * 1.15f); close() }, colour)
    drawOval(Color.White.copy(alpha = 0.45f), Offset(c.x - r * 0.62f, c.y - r * 0.86f), Size(r * 0.42f, r * 0.62f))
    if (letter != null) {
        val t = measurer.measure(letter, TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = (r * 0.95f / density).sp, color = Color(0xFF2A221C)))
        drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
    }
}

/** A balloon popping: bits flying out and fading, [p] 0..1. */
internal fun DrawScope.burst(c: Offset, r: Float, colour: Color, p: Float) {
    for (k in 0 until 12) {
        val a = 2 * PI * k / 12
        val d = r * (0.4f + 1.6f * p)
        drawCircle(if (k % 3 == 0) PupGold else colour, r * 0.12f * (1f - p * 0.6f), c + Offset((cos(a) * d).toFloat(), (sin(a) * d).toFloat()), alpha = 1f - p)
    }
    if (p < 0.25f) drawCircle(Color.White.copy(alpha = 1f - p / 0.25f), r * (0.6f + p * 2f), c, style = Stroke(3.dp.toPx()))
}

// ---- The carousel ----------------------------------------------------------------------------------------------------------

/**
 * The carousel ([com.bigpapituna.sharpears.engine.playground.Game.CAROUSEL]): a round is a run up to G and back. The
 * bulbs round the canopy light up one by one as its notes are heard; a run with no slip in it spins the carousel a step
 * faster (the horses go round and ride up and down); a run with a slip keeps the speed ("A smooth run spins it faster").
 */
@Composable
internal fun CarouselStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    var smooth by remember { mutableIntStateOf(0) }
    val slips = remember { intArrayOf(0) }
    val lastClean = remember { booleanArrayOf(false) }
    val runRef = remember { arrayOfNulls<StageRun>(1) }
    val longest = remember(ex) { longestPhrase(ex) }
    val colours = com.bigpapituna.sharpears.ui.skin.colourNotesOn()
    val st = rememberStage(ex, "$lessonId-carousel", lessonId, "carousel", deps, inject, onEnded,
        keys = { StageKeys.FIVE },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, run -> NoteJudge.judge(s, run.rounds.expectedKey()) },
        foundLine = { run, _ -> when {
            run.finished -> FairLines.CAROUSEL_DONE
            run.rounds.doneInRound == 0 -> if (lastClean[0]) FairLines.SPIN[(smooth - 1).coerceAtLeast(0) % FairLines.SPIN.size] else FairLines.SPIN_SAME
            else -> null
        } },
        missLine = { _, _ -> FairLines.CAROUSEL_MISS },
        onFound = { _ ->
            val r = runRef[0]
            // The run's last note: smooth when nothing slipped in it.
            if (r != null && r.rounds.doneInRound == r.rounds.roundGroups.size - 1) {
                lastClean[0] = slips[0] == 0
                if (lastClean[0]) smooth++
                Log.d(TAG, "playground: carousel run ${r.done + 1} ${if (lastClean[0]) "smooth" else "with ${slips[0]} slips"}, speed ${carouselSpeed(smooth)}")
                slips[0] = 0
            }
        },
        onMiss = { slips[0]++ },
        onLetGo = { slips[0] = 0 },
        coach = { phraseCoach(longest, 380) }, phrases = true, demoGapMs = 380,
    )
    runRef[0] = st.run
    val run = st.run
    val v = run.version
    val lit = remember(v) { if (run.finished) 9 else run.rounds.doneInRound }
    val total = remember(v) { run.rounds.roundGroups.size.coerceAtLeast(1) }
    val demo = st.demoKeys
    val line = stageLine(st, when {
        run.finished -> FairLines.CAROUSEL_DONE
        demo.isNotEmpty() -> "Listen, then play it with me!"
        run.phase == RoundCoach.Phase.LISTEN -> PlaygroundLines.YOUR_TURN
        else -> null
    })
    // The carousel's turn: it always drifts round a little, faster with every smooth run.
    var turn by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos { now -> turn += carouselSpeed(smooth) * ((now - last) / 1e9f); last = now }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        Canvas(Modifier.fillMaxSize()) { fairLights(0.3f) }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(if (run.finished) Painted.DANCING else if (demo.isNotEmpty()) Painted.POINTING else Painted.LISTENING,
                    size = (roomH * 0.16f).coerceIn(56.dp, 96.dp), poke = st.fx.poke)
                Spacer(Modifier.width(8.dp))
                StageLine(line)
            }
            Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "The carousel, turning" }) {
                carousel(turn, lit = (if (demo.isNotEmpty()) (st.demoIndex ?: -1) + 1 else lit), bulbs = total)
            }
            BigKeys(57, 71, "Piano keys from A3 to B4. Up to G and back.", Modifier.fillMaxWidth().height((roomH * 0.24f).coerceIn(96.dp, 170.dp)),
                fill = { m -> if (m == st.demoIndex?.let { demo.getOrNull(it) }) PupGold else if (colours && m in StageKeys.FIVE) noteColour(m).copy(alpha = 0.45f) else null },
                last = run.last, fx = st.fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(600); deps.sampler.noteOff(m) } }
                    st.tap(m)
                })
        }
    }
}

/** The carousel at [turn] (turns): striped canopy, [lit] of [bulbs] bulbs round its rim, six horses riding round and up and down. */
private fun DrawScope.carousel(turn: Float, lit: Int, bulbs: Int) {
    val w = size.width; val h = size.height
    val cx = w / 2; val rx = minOf(w * 0.38f, h * 0.55f); val rimY = h * 0.30f; val baseY = h * 0.86f
    val ink = Color(0xFF3A2416)
    // The base, then the horses behind the pole, the pole, the horses in front.
    drawOval(Color(0xFF4E3A68), Offset(cx - rx * 1.05f, baseY - h * 0.04f), Size(rx * 2.1f, h * 0.12f))
    drawOval(Color(0xFF7A5AA0), Offset(cx - rx, baseY - h * 0.07f), Size(rx * 2f, h * 0.1f))
    fun horse(k: Int, front: Boolean) {
        val a = (turn + k / 6f) * 2 * PI.toFloat()
        val depth = cos(a)
        if ((depth >= 0) != front) return
        val x = cx + rx * 0.8f * sin(a)
        val s = 0.72f + 0.28f * (depth + 1) / 2
        val y = baseY - h * 0.3f + sin(a * 2 + k) * h * 0.05f
        drawLine(Color(0xFFF2B84B), Offset(x, rimY + h * 0.02f), Offset(x, baseY - h * 0.05f), 3.dp.toPx() * s)
        val face = if (sin(a + PI.toFloat() / 2) >= 0) 1f else -1f
        carouselHorse(Offset(x, y), Size(w * 0.11f * s, h * 0.08f * s), face, listOf(Color(0xFFFFFCF4), Color(0xFFF6EBDD), Color(0xFFFFF4E0))[k % 3],
            listOf(Color(0xFFE0605A), Color(0xFF7DB8FF), Color(0xFF7CC173))[k % 3], 2.5.dp.toPx() * s)
    }
    for (k in 0 until 6) horse(k, front = false)
    drawRect(Color(0xFFE2B85A), Offset(cx - w * 0.015f, rimY), Size(w * 0.03f, baseY - rimY - h * 0.05f))
    for (k in 0 until 6) horse(k, front = true)
    // The canopy: a striped cone with scallops, a flag on top, the bulbs round the rim.
    val cone = Path().apply { moveTo(cx - rx * 1.1f, rimY); lineTo(cx, h * 0.05f); lineTo(cx + rx * 1.1f, rimY); close() }
    drawPath(cone, Color(0xFFFCD860))
    for (k in 0 until 6 step 2) {
        val a = cx - rx * 1.1f + rx * 2.2f * k / 6f; val b = cx - rx * 1.1f + rx * 2.2f * (k + 1) / 6f
        drawPath(Path().apply { moveTo(cx, h * 0.05f); lineTo(a, rimY); lineTo(b, rimY); close() }, Color(0xFFE0605A))
    }
    drawPath(cone, ink, style = Stroke(3.dp.toPx()))
    for (k in 0 until bulbs) {
        val x = cx - rx * 1.05f + rx * 2.1f * (k + 0.5f) / bulbs
        val on = k < lit
        if (on) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE3A0).copy(alpha = 0.7f), Color.Transparent), Offset(x, rimY), w * 0.04f), w * 0.04f, Offset(x, rimY))
        drawCircle(if (on) Color(0xFFFFF1C8) else Color(0xFF6B5A4A), w * 0.012f, Offset(x, rimY))
    }
}

// ---- The strongman ---------------------------------------------------------------------------------------------------------

/**
 * The strongman ([com.bigpapituna.sharpears.engine.playground.Game.STRONGMAN]): a round is three swings, any key near
 * middle C. Each sends the puck up the pole as high as the sound was big (a guess from the strike's rise, as soft and loud
 * is), and the biggest rings the bell at the top. Every height is cheered ("Up it goes!", "Strong!"); the round's best is
 * marked on the pole. The demo rings the bell.
 */
@Composable
internal fun StrongmanStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val offset = LocalProfile.current.micOffsetDb
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val puck = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    var best by remember { mutableFloatStateOf(0f) }
    var rang by remember { mutableIntStateOf(0) }
    val lastHeight = remember { floatArrayOf(0f) }
    val holder = remember { arrayOfNulls<StageState>(1) }
    fun fly(h: Float) = scope.launch {
        if (still) { puck.snapTo(h) } else { puck.snapTo(0f); puck.animateTo(h, tween(420, easing = FastOutSlowInEasing)) }
        if (h >= StrongmanJudge.BELL) {
            rang++
            launch { shake.snapTo(1f); shake.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 300f)) }
        }
        delay(500)
        if (!still) puck.animateTo(0f, tween(700))
    }
    val st = rememberStage(ex, "$lessonId-strongman", lessonId, "strongman", deps, inject, onEnded,
        keys = { StageKeys.SOFT_LOUD },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, run ->
            val a = StrongmanJudge.judge(s, run.rounds.expectedKey())
            if (a is Answer.Found) {
                val h = StrongmanJudge.height(s.riseDb ?: -60.0, offset)
                lastHeight[0] = h; best = maxOf(best, h)
                Log.d(TAG, "playground: strongman rise ${"%.1f".format(s.riseDb)} dB -> height ${"%.2f".format(h)}")
                fly(h)
                // Every height gets its cheer on screen; the round's best is said when the round ends.
                holder[0]?.say(FairLines.height(h), 1_400)
            }
            a
        },
        foundLine = { run, _ -> when {
            run.finished -> FairLines.STRONG_DONE
            run.rounds.doneInRound == 0 -> FairLines.height(best).also { best = 0f }
            else -> null
        } },
        missLine = { _, _ -> null },
        velocity = { 118 },
        coach = { phraseCoach(3, 700) }, phrases = true, demoGapMs = 700,
    )
    holder[0] = st
    val run = st.run
    val demo = st.demoKeys
    // The demo's swings ring the bell: this is what a heavy arm does.
    LaunchedEffect(st.demoIndex) { if (st.demoIndex != null) fly(if (st.demoIndex == 2) 1f else 0.55f + 0.15f * (st.demoIndex ?: 0)) }
    val line = stageLine(st, when {
        run.finished -> FairLines.STRONG_DONE
        demo.isNotEmpty() -> "Watch the puck fly!"
        run.phase == RoundCoach.Phase.LISTEN -> "Swing!"
        else -> null
    })
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        Canvas(Modifier.fillMaxSize()) { fairLights(0.3f) }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    StageLine(line)
                    Spacer(Modifier.height(10.dp))
                    PaintedPup(when {
                        run.finished -> Painted.DANCING
                        rang > 0 && puck.value > 0.9f -> Painted.CROWN
                        puck.value > 0.1f -> Painted.SURPRISED
                        else -> Painted.LISTENING
                    }, size = (roomH * 0.3f).coerceIn(80.dp, 170.dp), poke = st.fx.poke)
                }
                Canvas(Modifier.weight(1f).fillMaxSize().semantics { contentDescription = "The strongman's pole and bell" }) {
                    strongman(puck.value, best, shake.value)
                }
            }
            BigKeys(57, 71, "Piano keys from A3 to B4. Middle C is lit.", Modifier.fillMaxWidth().height((roomH * 0.2f).coerceIn(84.dp, 150.dp)),
                fill = { m -> if (m == 60) PupGold.copy(alpha = 0.6f) else null }, last = run.last, fx = st.fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 110); scope.launch { delay(600); deps.sampler.noteOff(m) } }
                    st.tap(m, riseDb = -30.0)
                })
        }
    }
}

/** The strongman's pole with its coloured scale, the puck at [puck] (0..1), the round's [best] marked, the bell [shake]n. */
private fun DrawScope.strongman(puck: Float, best: Float, shake: Float) {
    val w = size.width; val h = size.height
    val cx = w * 0.5f
    val top = h * 0.16f; val bottom = h * 0.92f
    val pw = minOf(w * 0.16f, 34.dp.toPx())
    val ink = Color(0xFF3A2416)
    val bands = listOf(0xFF7CC173, 0xFFB5D86A, 0xFFFCD860, 0xFFF6A654, 0xFFE0605A)
    drawRoundRect(ink, Offset(cx - pw / 2 - 3f, top - 3f), Size(pw + 6f, bottom - top + 6f), CornerRadius(pw * 0.3f))
    bands.forEachIndexed { i, c ->
        val y1 = bottom - (bottom - top) * (i + 1) / bands.size; val y0 = bottom - (bottom - top) * i / bands.size
        drawRect(Color(c), Offset(cx - pw / 2, y1), Size(pw, y0 - y1))
    }
    // The base pad, and the bell on top (it swings when it rings).
    drawRoundRect(Color(0xFFE0605A), Offset(cx - pw * 1.6f, bottom), Size(pw * 3.2f, h * 0.05f), CornerRadius(8f))
    rotate(sin(shake * 14f) * 18f * shake, Offset(cx, top - h * 0.02f)) {
        val bell = Path().apply {
            moveTo(cx - pw * 1.3f, top); quadraticTo(cx - pw * 1.1f, top - h * 0.12f, cx, top - h * 0.13f)
            quadraticTo(cx + pw * 1.1f, top - h * 0.12f, cx + pw * 1.3f, top); close()
        }
        if (shake > 0.05f) drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = 0.7f * shake), Color.Transparent), Offset(cx, top - h * 0.06f), w * 0.4f), w * 0.4f,
            Offset(cx, top - h * 0.06f))
        drawPath(bell, Color(0xFFF2B84B)); drawPath(bell, ink, style = Stroke(3.dp.toPx()))
    }
    // The round's best so far: a little gold flag.
    if (best > 0.05f) {
        val y = bottom - (bottom - top) * best
        drawPath(Path().apply { moveTo(cx + pw / 2, y); lineTo(cx + pw * 1.6f, y - 8.dp.toPx()); lineTo(cx + pw / 2, y - 16.dp.toPx()); close() }, PupGold)
    }
    // The puck.
    val py = bottom - (bottom - top) * puck
    drawRoundRect(ink, Offset(cx - pw * 0.85f - 2f, py - h * 0.035f - 2f), Size(pw * 1.7f + 4f, h * 0.05f + 4f), CornerRadius(10f))
    drawRoundRect(Color(0xFF7DB8FF), Offset(cx - pw * 0.85f, py - h * 0.035f), Size(pw * 1.7f, h * 0.05f), CornerRadius(8f))
}
