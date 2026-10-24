package com.bigpapituna.sharpears.ui.playground

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.playground.Pick
import com.bigpapituna.sharpears.engine.playground.PickRounds
import com.bigpapituna.sharpears.engine.playground.PickSound
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.pickRounds
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.theory.rememberStageInk
import com.bigpapituna.sharpears.ui.theory.softArm
import com.bigpapituna.sharpears.ui.theory.softFigure
import com.bigpapituna.sharpears.ui.theory.softFoot
import com.bigpapituna.sharpears.ui.world.WorldSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import com.bigpapituna.sharpears.ui.pup.Pose

/**
 * 2.3, the workshop's pick game ([com.bigpapituna.sharpears.engine.playground.Game.PICK]): the pup asks, two or three
 * pictures appear (the player at the bench, the arm at the key, a short or a long note, the foot by the pedal), and the
 * kid taps the right one. A listening round plays its sound first (middle C short or long; C, E and G with the pedal down
 * or without it). The right one glows gold with a star and a pop; another one wobbles and the pup says what it shows;
 * a quiet stretch asks again; three tries and the right one is shown, a small star. No microphone, so the pup's lines are
 * said aloud. Reports the rounds and the taps that were not it.
 */
@Composable
internal fun PickStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val game = remember(step) { PickRounds(pickRounds(step)) }
    val voice = deps.voice
    val scope = rememberCoroutineScope()
    val ended by rememberUpdatedState(onEnded)
    var started by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    // The picture shown as right (found, or shown after the let go) until the next round; the one that wobbles.
    var shown by remember { mutableStateOf<Pick?>(null) }
    var wobble by remember { mutableStateOf<Pick?>(null) }
    var wobbleSeq by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }     // a sound is playing
    var cheer by remember { mutableIntStateOf(0) }
    fun now() = SystemClock.uptimeMillis()
    fun say(text: String) { line = text; voice.speak(text, interrupt = true) }

    /** The round's sound: middle C short or long, or C E G with the pedal held or each note let go. */
    fun play(sound: PickSound) = scope.launch {
        listening = true
        val on = deps.prefs.sound
        when (sound) {
            PickSound.SHORT, PickSound.LONG -> {
                if (on) deps.sampler.noteOn(60, 84)
                delay(if (sound == PickSound.SHORT) 160 else 1_500)
                deps.sampler.noteOff(60)
            }
            PickSound.PEDAL, PickSound.DRY -> {
                for (k in listOf(60, 64, 67)) {
                    if (on) deps.sampler.noteOn(k, 80)
                    delay(380)
                    if (sound == PickSound.DRY) deps.sampler.noteOff(k)
                }
                delay(if (sound == PickSound.PEDAL) 1_600 else 300)
                listOf(60, 64, 67).forEach { deps.sampler.noteOff(it) }
            }
        }
        listening = false
    }

    fun handle(cue: RoundCoach.Cue?) {
        val r = game.current
        when (cue) {
            is RoundCoach.Cue.Demo -> {
                shown = null
                if (r == null) return
                scope.launch {
                    if (cue.again) { say(PlaygroundLines.AGAIN); delay(1_100) }
                    say(r.ask)
                    r.sound?.let { delay(1_300); play(it) }
                }
            }
            is RoundCoach.Cue.LetGo -> {
                // Three tries: the right one is shown, and the game goes on.
                shown = game.rounds.getOrNull(cue.round)?.answer
                say(PlaygroundLines.LET_GO)
            }
            else -> {}
        }
    }

    // It starts once the pup has read the instruction (and the sounds are loaded).
    val speaking = voice.speaking.value
    LaunchedEffect(speaking, deps.ready) {
        if (started || speaking || !deps.ready) return@LaunchedEffect
        delay(500)
        game.start(now()); started = true
        Log.d(TAG, "playground: pick game started, ${game.rounds.size} rounds")
    }
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        while (!game.ended) {
            handle(game.tick(now()))
            version++
            delay(50)
        }
        Log.d(TAG, "playground: pick game ended, ${game.letGo.size} let go, ${game.notIt} taps not it")
        WorldSounds.ding()
        delay(1_400)
        ended(game.report())
    }
    fun tap(p: Pick) {
        when (val t = game.tap(p, now())) {
            is PickRounds.Tap.Right -> {
                shown = p; cheer++
                WorldSounds.stagePop()
                say(t.line)
                Log.d(TAG, "playground: pick $p is right (round ${game.done})")
            }
            is PickRounds.Tap.NotIt -> {
                wobble = p; wobbleSeq++
                say(t.line)
                Log.d(TAG, "playground: pick $p is not it")
                if (t.cue is RoundCoach.Cue.LetGo) handle(t.cue)
            }
            PickRounds.Tap.Ignored -> {}
        }
        version++
    }

    val v = version
    val round = remember(v) { game.current ?: game.rounds.getOrNull(game.done - 1) }
    val asking = remember(v) { game.coach.phase == RoundCoach.Phase.DEMO }
    val still = reducedMotion()
    val pulse by rememberInfiniteTransition(label = "pick").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        Canvas(Modifier.fillMaxSize()) { workshopRoom(0.88f) }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(game.rounds.size, game.done, letGo = game.letGo)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                StagePup(when {
                    game.ended || shown != null && wobble == null -> Pose.GREAT_JOB
                    wobble != null -> Pose.HEAD_TILT
                    listening -> Pose.LISTENING
                    else -> Pose.POINTING
                }, size = (roomH * 0.2f).coerceIn(64.dp, 110.dp))
                Spacer(Modifier.width(8.dp))
                StageLine(line ?: round?.ask)
            }
            LaunchedEffect(wobbleSeq) { if (wobbleSeq > 0) { delay(700); wobble = null } }
            val options = round?.options.orEmpty()
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                options.forEach { p ->
                    key(round, p) {
                        PickCard(p, Modifier.weight(1f).fillMaxSize(), right = shown == p, wobble = wobble == p, wobbleSeq = wobbleSeq,
                            ready = !asking && !game.ended, pulse = if (still) 0.5f else pulse, sounding = listening && round?.sound != null) { tap(p) }
                    }
                }
            }
        }
        if (cheer > 0) Confetti(cheer, Modifier.fillMaxSize())
    }
}

/** One picture to tap: its card, gold with a star when right, wobbling when it was not it, breathing while the pup waits. */
@Composable
private fun PickCard(pick: Pick, modifier: Modifier, right: Boolean, wobble: Boolean, wobbleSeq: Int, ready: Boolean, pulse: Float, sounding: Boolean, onTap: () -> Unit) {
    val ink = rememberStageInk()
    val shake = remember { Animatable(0f) }
    val still = reducedMotion()
    LaunchedEffect(wobbleSeq, wobble) {
        if (!wobble || still) return@LaunchedEffect
        shake.snapTo(0f); shake.animateTo(1f, tween(520, easing = LinearEasing)); shake.snapTo(0f)
    }
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.graphicsLayer { translationX = if (wobble) sin(shake.value * 6 * PI.toFloat()) * 10.dp.toPx() * (1 - shake.value) else 0f }
        .clip(shape).background(Color(0xFF2A221C))
        .border(if (right) 4.dp else 2.dp, if (right) PupGold else Color.White.copy(alpha = if (ready) 0.18f + 0.22f * pulse else 0.12f), shape)
        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onTap)
        .semantics { contentDescription = describe(pick) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            when (pick) {
                Pick.SIT_TALL -> softFigure(1f, 1f, 1f, 1f, 1f, 1f, 0f, false, ink, 0f)
                Pick.SIT_SLOUCH -> softFigure(1f, 0.3f, 0f, 1f, 1f, 1f, 0f, false, ink, 0f)
                Pick.SIT_CLOSE -> softFigure(1f, 1f, 1f, 1f, 1f, 1f, 0f, false, ink, 0f, close = 1f)
                Pick.SIT_FEET -> softFigure(1f, 1f, 1f, 1f, 1f, 1f, 0f, false, ink, 0f, tucked = 1f)
                Pick.WRIST_LEVEL -> softArm(ink, 0f, 0f, 0f, 0.4f, if (right) 1f else 0f)
                Pick.WRIST_LOW -> softArm(ink, 0f, -1f, 0f, 0.4f, 0f)
                Pick.WRIST_HIGH -> softArm(ink, 0f, 1f, 0f, 0.4f, 0f)
                Pick.SHORT -> bounce(sounding, pulse)
                Pick.LONG -> longNote(sounding, pulse)
                Pick.PEDAL_DOWN -> {
                    val (_, ball) = softFoot(ink, Offset.Zero, size.width, size.height, 1f)
                    // An arrow pressing down on the ball of the foot: this one is down.
                    val a = ball - Offset(0f, size.height * 0.34f); val b = ball - Offset(0f, size.height * 0.12f)
                    drawLine(PupGold, a, b, 5.dp.toPx(), StrokeCap.Round)
                    drawLine(PupGold, b, b + Offset(-9.dp.toPx(), -9.dp.toPx()), 5.dp.toPx(), StrokeCap.Round)
                    drawLine(PupGold, b, b + Offset(9.dp.toPx(), -9.dp.toPx()), 5.dp.toPx(), StrokeCap.Round)
                }
                Pick.PEDAL_UP -> softFoot(ink, Offset.Zero, size.width, size.height, 0f)
            }
            if (right) {
                val c = Offset(size.width - 22.dp.toPx(), 22.dp.toPx())
                drawCircle(Color(0xFF2A221C), 18.dp.toPx(), c)
                drawPath(star(c, 15.dp.toPx()), PupGold)
            }
        }
    }
}

/** What TalkBack says for a picture. */
private fun describe(p: Pick) = when (p) {
    Pick.SIT_TALL -> "Sitting tall, at the right distance, feet flat"
    Pick.SIT_SLOUCH -> "Slouching on the bench"
    Pick.SIT_CLOSE -> "Sitting squashed up close to the keys"
    Pick.SIT_FEET -> "Feet tucked under the bench"
    Pick.WRIST_LEVEL -> "A level wrist, like a bridge"
    Pick.WRIST_LOW -> "A wrist sagging down"
    Pick.WRIST_HIGH -> "A wrist poking up"
    Pick.SHORT -> "A short note, bouncing"
    Pick.LONG -> "A long note, smooth"
    Pick.PEDAL_DOWN -> "A foot pressing the pedal"
    Pick.PEDAL_UP -> "A foot resting, the pedal up"
}

/** A short note: a ball bouncing off a key, the hops drawn as arcs. */
private fun DrawScope.bounce(sounding: Boolean, pulse: Float) {
    val w = size.width; val h = size.height
    val floor = h * 0.78f
    drawRoundRect(Color(0xFFFFFCF7), Offset(w * 0.18f, floor), Size(w * 0.64f, h * 0.1f), CornerRadius(w * 0.03f))
    for (k in 0 until 3) {
        val x0 = w * (0.14f + 0.24f * k)
        drawArc(Color.White.copy(alpha = 0.35f), 180f, 180f, false, Offset(x0, floor - h * (0.42f - 0.1f * k)), Size(w * 0.24f, h * (0.84f - 0.2f * k)),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
    val hop = if (sounding) abs(sin(pulse * PI.toFloat())) else 0.4f
    val c = Offset(w * 0.5f, floor - h * 0.08f - hop * h * 0.3f)
    drawCircle(Color(0xFFE0605A), w * 0.1f, c)
    drawCircle(Color.White.copy(alpha = 0.45f), w * 0.03f, c + Offset(-w * 0.03f, -w * 0.03f))
}

/** A long note: one smooth ribbon held over the keys, glowing while it sounds. */
private fun DrawScope.longNote(sounding: Boolean, pulse: Float) {
    val w = size.width; val h = size.height
    val floor = h * 0.78f
    drawRoundRect(Color(0xFFFFFCF7), Offset(w * 0.18f, floor), Size(w * 0.64f, h * 0.1f), CornerRadius(w * 0.03f))
    val ribbon = Path().apply {
        moveTo(w * 0.12f, h * 0.5f)
        cubicTo(w * 0.35f, h * 0.30f, w * 0.6f, h * 0.65f, w * 0.88f, h * 0.45f)
    }
    if (sounding) drawPath(ribbon, PupGold.copy(alpha = 0.25f + 0.3f * pulse), style = Stroke(w * 0.12f, cap = StrokeCap.Round))
    drawPath(ribbon, Brush.horizontalGradient(listOf(Color(0xFFF2B84B), Color(0xFFE0905A))), style = Stroke(w * 0.06f, cap = StrokeCap.Round))
}
