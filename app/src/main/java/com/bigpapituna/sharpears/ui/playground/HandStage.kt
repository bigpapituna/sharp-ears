package com.bigpapituna.sharpears.ui.playground

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.playground.HandLook
import com.bigpapituna.sharpears.engine.playground.NoteJudge
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.engine.playground.WorkshopLines
import com.bigpapituna.sharpears.engine.playground.bookFingers
import com.bigpapituna.sharpears.engine.playground.handHome
import com.bigpapituna.sharpears.engine.playground.handLook
import com.bigpapituna.sharpears.engine.theory.whiteAtOrAbove
import com.bigpapituna.sharpears.engine.theory.whiteAtOrBelow
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.motion.DarkStageHand
import com.bigpapituna.sharpears.ui.motion.HandPose
import com.bigpapituna.sharpears.ui.motion.drawPaintedHand
import com.bigpapituna.sharpears.ui.motion.handTip
import com.bigpapituna.sharpears.ui.motion.handWrist
import com.bigpapituna.sharpears.ui.motion.rememberHandFrames
import com.bigpapituna.sharpears.ui.practice.PianoGeometry
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * 2.3, the workshop's hand stage ([com.bigpapituna.sharpears.engine.playground.Game.HAND]): big keys with the painted
 * hand resting on its home (the right hand's thumb on middle C, or the left hand's little finger on the C below), each
 * finger's number on its fingertip. Each round the hand shows a finger pressing its key (the key rings gold) and the kid
 * plays that key; the hand presses the same finger as the key is heard. The app hears the key, never the finger: the
 * number tells the kid which finger to use. Its look follows the lesson ([handLook]): a bubble under the palm that never
 * pops while the fingers stay round, the finger numbers called out one by one, or the hand at home. Heard through the
 * note matcher with strict keys on the exercise's keys; a key in another octave counts (NoteJudge).
 */
@Composable
internal fun HandStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val look = handLook(lessonId)
    val right = ex.right.isNotBlank()
    val fingers = remember(ex) { bookFingers(ex) }
    val home = remember(ex) { handHome(ex, right) }
    var pressSeq by remember { mutableIntStateOf(0) }
    var pressFinger by remember { mutableIntStateOf(0) }
    var cheer by remember { mutableIntStateOf(0) }
    val runRef = remember { arrayOfNulls<StageRun>(1) }
    fun fingerOf(run: StageRun) = fingers.getOrNull(run.rounds.groupsDone)?.second ?: 0
    val found = when (look) { HandLook.BUBBLE -> WorkshopLines.BUBBLE_FOUND; HandLook.NUMBERS -> WorkshopLines.NUMBER_FOUND; HandLook.HOME -> WorkshopLines.NUMBER_FOUND }
    val st = rememberStage(ex, "$lessonId-hand", lessonId, "hand", deps, inject, onEnded,
        keys = { StageKeys.of(ex) },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, run -> NoteJudge.judge(s, run.rounds.expectedKey()) },
        foundLine = { run, _ -> if (run.finished) (if (look == HandLook.BUBBLE) WorkshopLines.BUBBLE_DONE else WorkshopLines.HOME_DONE)
            else found[(run.done - 1).coerceAtLeast(0) % found.size] },
        missLine = { _, _ -> when (look) { HandLook.BUBBLE -> WorkshopLines.BUBBLE_MISS; HandLook.NUMBERS -> WorkshopLines.NUMBER_MISS; HandLook.HOME -> WorkshopLines.HOME_MISS } },
        // The finger numbers lesson calls each round's finger before its demo.
        prompt = { run -> if (look == HandLook.NUMBERS) fingerOf(run).takeIf { it > 0 }?.let(WorkshopLines::fingerCall) else null },
        onFound = { _ -> runRef[0]?.let { pressFinger = fingerOf(it); pressSeq++; cheer++ } },
        // The finger called first takes a moment more, as the letter hunts' calls do.
        coach = { if (look == HandLook.NUMBERS) RoundCoach(firstDemoMs = 4_300, demoMs = 2_800) else RoundCoach() },
    )
    runRef[0] = st.run
    val run = st.run
    val v = run.version
    val roundFinger = remember(v) { fingerOf(run) }
    val roundKey = remember(v) { run.rounds.expectedKey() }
    val demo = st.demoKeys
    val line = stageLine(st, when {
        run.finished -> if (look == HandLook.BUBBLE) WorkshopLines.BUBBLE_DONE else WorkshopLines.HOME_DONE
        demo.isNotEmpty() -> "Watch my finger!"
        run.phase == RoundCoach.Phase.LISTEN -> PlaygroundLines.YOUR_TURN
        else -> null
    })
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        Canvas(Modifier.fillMaxSize()) { workshopRoom(0.9f) }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(if (run.finished) Painted.GREAT_JOB else if (demo.isNotEmpty()) Painted.POINTING else Painted.LISTENING,
                    size = (roomH * 0.18f).coerceIn(60.dp, 110.dp), poke = st.fx.poke)
                Spacer(Modifier.width(8.dp))
                StageLine(line)
            }
            HandKeys(home, right, look, Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                // During the demo the hand presses the round's finger; after a key found, the finger that plays it.
                press = if (demo.isNotEmpty()) roundFinger else pressFinger, pressSeq = if (demo.isNotEmpty()) -1 - run.done else pressSeq,
                waitingKey = if (run.phase == RoundCoach.Phase.LISTEN) roundKey else null, waitingFinger = if (run.phase == RoundCoach.Phase.LISTEN) roundFinger else 0,
                demoKey = demo.firstOrNull(), fx = st.fx, last = run.last,
                onKey = { m ->
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(600); deps.sampler.noteOff(m) } }
                    st.tap(m)
                })
        }
        if (cheer > 0 && run.finished) Confetti(cheer, Modifier.fillMaxSize())
    }
}

/**
 * Big keys with the painted hand on its [home]: the numbers on its fingertips, the [press]ed finger going down (a new
 * [pressSeq] presses it once; a negative one holds it, the demo), the key waited for breathing gold with its finger's
 * number lit, the stage's ripples for every sound, and a tap on a key is a key played. [look] adds the bubble under the
 * palm.
 */
@Composable
private fun HandKeys(home: IntArray, right: Boolean, look: HandLook, modifier: Modifier, press: Int, pressSeq: Int, waitingKey: Int?, waitingFinger: Int,
                     demoKey: Int?, fx: StageFx, last: Struck?, onKey: (Int) -> Unit) {
    val frames by rememberHandFrames()
    val measurer = rememberTextMeasurer()
    val still = reducedMotion()
    val low = whiteAtOrBelow(home.min() - 3); val high = whiteAtOrAbove(home.max() + 3)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val keyH = remember(size) { if (size.width == 0) 0f else minOf(size.height / 1.75f, size.width / (low..high).count { !isBlack(it) } * 4.6f) }
    val top = remember(size, keyH) { ((size.height - keyH * 1.62f) / 2f).coerceAtLeast(0f) }
    val geo = remember(low, high, size, keyH, top) { if (size.width == 0) null else PianoGeometry(low, high, size.width.toFloat(), keyH, top) }
    val down = remember { Animatable(0f) }
    LaunchedEffect(pressSeq) {
        if (pressSeq == 0) return@LaunchedEffect
        down.snapTo(0f); down.animateTo(1f, tween(if (still) 0 else 90))
        delay(if (pressSeq < 0) 900 else 420)
        down.animateTo(0f, tween(if (still) 0 else 260))
    }
    val clock = rememberInfiniteTransition(label = "hand")
    val pulse by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val wob by clock.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "wobble")
    // The ripples' clock, as BigKeys runs it: only while one lives.
    var rippleNow by remember { mutableLongStateOf(0L) }
    val newest = fx.ripples.lastOrNull()?.id
    LaunchedEffect(newest) {
        while (fx.ripples.any { System.nanoTime() - it.startNanos < StageFx.RIPPLE_MS * 1_000_000 }) withFrameNanos { rippleNow = System.nanoTime() }
        rippleNow = System.nanoTime()
    }
    val tap by rememberUpdatedState(onKey)
    val handColour = if (right) RightHandColor else LeftHandColor
    Canvas(modifier.onSizeChanged { size = it }
        .semantics { contentDescription = "Piano keys with the ${if (right) "right" else "left"} hand on them, a number on each finger" }
        .pointerInput(geo) {
            val g = geo ?: return@pointerInput
            awaitEachGesture { val d = awaitFirstDown(); g.keyAt(d.position.x, d.position.y)?.let { d.consume(); tap(it) } }
        }) {
        val g = geo ?: return@Canvas
        val p = if (still) 0.6f else pulse
        piano(g, fill = { m -> when (m) {
            demoKey -> PupGold
            waitingKey -> PupGold.copy(alpha = 0.35f + 0.45f * p)
            in home -> handColour.copy(alpha = 0.16f)
            else -> null
        } }, flash = last?.takeIf { it.result != com.bigpapituna.sharpears.engine.InputResult.WRONG }?.let { it.midi to 0f })
        ripples(g, fx.ripples, maxOf(rippleNow, 1L), still)
        val f = frames ?: return@Canvas
        val ww = g.ww
        fun kx(m: Int) = g.rect(m).center.x
        val ax0 = kx(home[2])
        val tipY = g.top + g.height * 0.94f - handTip(right, HandPose.REST, 1, 3, 0f, 0f, ww).y
        val finger = press.coerceIn(0, 5)
        val d = if (finger > 0) down.value else 0f
        val pose = if (finger > 0) HandPose.press(finger) else HandPose.REST
        var ax = ax0
        var ay = tipY
        if (finger > 0) {
            ax += (kx(home[finger - 1]) - handTip(right, pose, finger, 3, ax0, ay, ww).x) * d
            if (isBlack(home[finger - 1])) ay -= g.height * 0.3f * d
        }
        drawPaintedHand(f, right, HandPose.REST, pose, d, 3, ax, ay, ww, filter = DarkStageHand)
        // The bubble under the palm, seen through the hand (from above it would hide under it): it wobbles, never pops.
        if (look == HandLook.BUBBLE) {
            val tip3 = handTip(right, HandPose.REST, 3, 3, ax, ay, ww)
            val wrist = handWrist(right, 3, ax, ay, ww)
            bubble(lerp(tip3, wrist, 0.36f), ww * 1.45f * (1f + 0.04f * sin(wob) + 0.06f * d), still)
        }
        // The numbers on the fingertips: the one waited for (or pressing) lit in the hand's colour.
        for (n in 1..5) {
            val c = handTip(right, HandPose.REST, pose, d, n, 3, ax, ay, ww)
            val lit = n == waitingFinger || (n == finger && d > 0.3f)
            val r = minOf(ww * 0.3f, 13.dp.toPx()) * (if (lit) 1.2f + 0.1f * p else 1f)
            drawCircle(Color.Black, r * 1.08f, c + Offset(0f, 1.dp.toPx()), alpha = 0.2f)
            drawCircle(if (lit) handColour else Color.White, r, c)
            val t = measurer.measure("$n", TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = (r * 1.2f / density).sp,
                color = if (lit) Color.White else Color(0xFF5A3A24)))
            drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
        }
    }
}

/** The bubble under the palm: see-through, a soft rainbow sheen, a highlight, a dashed rim. */
private fun DrawScope.bubble(c: Offset, r: Float, still: Boolean) {
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.04f), Color(0xFF9AD0F5).copy(alpha = 0.22f), Color(0xFFC7B3F2).copy(alpha = 0.34f)), c, r), r, c)
    drawCircle(Color.White.copy(alpha = 0.7f), r, c, style = Stroke(2.dp.toPx(), pathEffect = if (still) null else PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))))
    drawCircle(Color.White.copy(alpha = 0.75f), r * 0.16f, c + Offset(-r * 0.42f, -r * 0.42f))
}
