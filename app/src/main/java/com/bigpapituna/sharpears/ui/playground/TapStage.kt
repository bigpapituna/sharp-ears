package com.bigpapituna.sharpears.ui.playground

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.HeardBy
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.StageGame
import com.bigpapituna.sharpears.engine.playground.TapChoice
import com.bigpapituna.sharpears.engine.playground.TapKind
import com.bigpapituna.sharpears.engine.playground.TapQuiz
import com.bigpapituna.sharpears.engine.playground.TapRound
import com.bigpapituna.sharpears.engine.playground.TreeSkyLines
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.world.WorldSounds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2.3 part B, the tap games ([TapQuiz]): the app plays, the child taps the answer. The pup shows one first (he plays
 * it and taps the answer himself: "Let me show you"); then each round plays, the buttons wake up ("Up or down?"), a
 * tap that is not it plays the round again ("Let's listen again"), three tries and the pup shows the answer and the
 * round goes by with a small star. Under the stars: up or down, same or different, which was louder (each sound lights
 * its own button), fast or slow (the rabbit and the turtle), long or short, happy or sad (the sun and the rain cloud).
 * In the treehouse, which finger: the painted hand presses a key with one finger (no number on it), the child taps the
 * number, then plays the key (the microphone checks the key, never the finger). Every tap and every sound is answered on
 * screen; the buttons carry pictures, with the word under them for readers.
 */
@Composable
internal fun TapStage(game: StageGame.Tap, step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit,
                      modifier: Modifier = Modifier) {
    val quiz = remember(game) { TapQuiz(game.example, game.rounds) }
    val kind = game.kind
    val finger = kind == TapKind.FINGER
    val profile = LocalProfile.current
    val words = !profile.isKid || profile.reader
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val ended by rememberUpdatedState(onEnded)
    val voice = deps.voice
    val mic = deps.mic
    val fx = remember { StageFx() }

    var version by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf<String?>(null) }
    var lineSeq by remember { mutableIntStateOf(0) }
    var playingPart by remember { mutableStateOf<Int?>(null) }
    var sounding by remember { mutableIntStateOf(0) }
    var hand by remember { mutableStateOf<HandShow?>(null) }
    var reveal by remember { mutableStateOf<TapChoice?>(null) }
    var revealNumber by remember { mutableStateOf(false) }
    var missed by remember { mutableStateOf<TapChoice?>(null) }
    var missSeq by remember { mutableIntStateOf(0) }
    var cheer by remember { mutableIntStateOf(0) }
    var playKey by remember { mutableStateOf<Int?>(null) }
    var playing by remember { mutableStateOf(false) }       // the which-finger key part has begun: the mic is on from then
    var exampleShown by remember { mutableStateOf(game.example == null) }
    var showJob by remember { mutableStateOf<Job?>(null) }

    fun now() = SystemClock.uptimeMillis()
    fun show(text: String) { line = text; lineSeq++ }
    fun say(text: String) { show(text); voice.sayOnStage(text, mic) }
    val keys = remember(game) { (game.rounds + listOfNotNull(game.example)).mapNotNull { it.play }.toSet() }
    val position = remember(game) { (game.rounds + listOfNotNull(game.example)).flatMap { r -> r.parts.flatten().flatMap { it.keys } }.toSet() }
    val right = remember(game) { game.rounds.firstOrNull()?.parts?.firstOrNull()?.firstOrNull()?.right ?: true }

    /** Plays [round]'s sounds after [leadMs], lighting each part (and the painted hand pressing, for a finger). */
    suspend fun playRound(round: TapRound, leadMs: Long) {
        delay(leadMs)
        var held: List<Int> = emptyList()
        try {
            for ((pi, part) in round.parts.withIndex()) {
                if (pi > 0) delay(TapRound.PART_GAP_MS)
                playingPart = pi
                for (s in part) {
                    if (playing) mic.deafFor(s.ms + 500L)
                    if (s.finger != null) hand = HandShow(s.right, s.finger, s.keys.first(), pressed = true, number = revealNumber)
                    held = s.keys
                    if (deps.prefs.sound) s.keys.forEach { deps.sampler.noteOn(it, s.velocity) }
                    sounding++
                    delay(s.ms.toLong())
                    s.keys.forEach { deps.sampler.noteOff(it) }
                    held = emptyList()
                    hand = hand?.copy(pressed = false)
                    delay(s.gapMs.toLong())
                }
            }
        } finally {
            // Cut short (the round shows again): nothing keeps sounding or lit.
            held.forEach { deps.sampler.noteOff(it) }
            playingPart = null
            hand = hand?.copy(pressed = false)
        }
    }

    fun handle(cues: List<TapQuiz.Cue>) {
        for (cue in cues) {
            Log.d(TAG, "playground: tap ${kind.name.lowercase()} $cue")
            version++
            when (cue) {
                is TapQuiz.Cue.Example -> {
                    say(PlaygroundLines.LET_ME_SHOW)
                    revealNumber = false
                    showJob = scope.launch { playRound(cue.round, quiz.leadMs) }
                    scope.launch {
                        delay(cue.answerAtMs)
                        reveal = cue.round.answer; revealNumber = true
                        hand = hand?.copy(number = true)
                        WorldSounds.stagePop()
                        say(TreeSkyLines.answer(cue.round.answer))
                        delay(cue.ms - cue.answerAtMs - 200)
                        reveal = null; exampleShown = true
                    }
                }
                is TapQuiz.Cue.Show -> {
                    reveal = null; missed = null; revealNumber = false; playKey = null
                    hand = hand?.copy(number = false)
                    when {
                        cue.first -> say(PlaygroundLines.YOUR_TURN)
                        cue.again -> say(if (finger) TreeSkyLines.WATCH_AGAIN else TreeSkyLines.LISTEN_AGAIN)
                    }
                    showJob?.cancel()
                    showJob = scope.launch { playRound(game.rounds[cue.index], cue.leadMs) }
                }
                is TapQuiz.Cue.Ask -> say(TreeSkyLines.ask(kind))
                is TapQuiz.Cue.Right -> {
                    reveal = cue.choice; revealNumber = true; hand = hand?.copy(number = true)
                    cheer++
                    WorldSounds.stagePop()
                    if (game.rounds[cue.index].play == null) say(TreeSkyLines.yes(cue.choice)) else show(TreeSkyLines.yes(cue.choice))
                }
                is TapQuiz.Cue.NotYet -> { missed = cue.choice; missSeq++ }
                is TapQuiz.Cue.LetGo -> {
                    reveal = cue.answer; revealNumber = true; hand = hand?.copy(number = true)
                    say(PlaygroundLines.LET_GO)
                    scope.launch { delay(1_400); show(TreeSkyLines.answer(cue.answer)) }
                }
                is TapQuiz.Cue.Play -> {
                    playKey = cue.key
                    playing = true
                    val f = game.rounds[cue.index].parts.single().single().finger ?: 1
                    if (!cue.again) say(TreeSkyLines.playWith(f))
                    else {
                        say(TreeSkyLines.LOOK_WHERE)
                        // The hand presses it again for them, the mic deaf meanwhile.
                        scope.launch { playRound(game.rounds[cue.index], 600) }
                    }
                }
                is TapQuiz.Cue.Played -> {
                    fx.found(cue.key); playKey = null; cheer++
                    mic.deafFor(250); WorldSounds.stagePop()
                    say(TreeSkyLines.PLAYED[cue.index % TreeSkyLines.PLAYED.size])
                }
                is TapQuiz.Cue.PlayMiss -> {
                    cue.key?.let { fx.wrong(it, playKey) }
                    missSeq++
                    if (cue.tries == 1 && cue.key != null) say(TreeSkyLines.LOOK_WHERE)
                }
                is TapQuiz.Cue.PlayLetGo -> {
                    say(PlaygroundLines.LET_GO)
                    playKey = null
                }
                TapQuiz.Cue.Done -> scope.launch {
                    mic.deafFor(800); WorldSounds.ding()
                    delay(1_600)
                    Log.d(TAG, "playground: tap game done, ${quiz.right} answered (${quiz.firstTry} first time), ${quiz.letGo.size} shown, ${quiz.played} keys")
                    ended(quiz.report())
                }
            }
        }
    }

    // Starts once the voice has read the instruction (and the sounds are loaded), then follows the clock.
    var started by remember { mutableStateOf(false) }
    val speaking = voice.speaking.value
    LaunchedEffect(speaking, deps.ready) {
        if (started || speaking || !deps.ready) return@LaunchedEffect
        delay(600)
        started = true
        handle(quiz.start(now()))
    }
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        while (!quiz.done) { withFrameNanos { }; handle(quiz.tick(now())) }
    }
    // Which finger: the key is heard through the note matcher, the position's keys only (strict).
    val listening = rememberStageEar(mic, voice, finger && playing && !quiz.done, inject, keys) { s ->
        fx.heard(s)
        if (!s.final) return@rememberStageEar
        val k = s.midi?.takeIf { s.by == HeardBy.MATCHER || s.by == HeardBy.NAMED }
        handle(quiz.key(k, now()))
    }

    val shownLine = rememberFleeting(lineSeq, line, ms = (2_600 * FxSlow.factor).toLong()) ?: when (quiz.phase) {
        TapQuiz.Phase.ASK -> TreeSkyLines.ask(kind)
        TapQuiz.Phase.SHOW, TapQuiz.Phase.EXAMPLE -> if (finger) "Watch my hand!" else "Listen!"
        else -> null
    }
    var tilt by remember { mutableStateOf(false) }
    LaunchedEffect(missSeq) { if (missSeq > 0) { tilt = true; delay(1_300); tilt = false } }
    val pulse by rememberInfiniteTransition(label = "tap").animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    val p = if (still) 0.5f else pulse
    val asking = quiz.phase == TapQuiz.Phase.ASK
    @Suppress("UNUSED_EXPRESSION") version

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        PlaceBackdrop(game.backdrop, Modifier.fillMaxSize(), floorFrom = 0.5f)
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(game.rounds.size, quiz.finished, letGo = quiz.letGo)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(when {
                    reveal != null -> Painted.GREAT_JOB
                    tilt -> Painted.HEAD_TILT
                    !exampleShown -> Painted.POINTING
                    playingPart != null -> Painted.LISTENING
                    else -> Painted.STANDING
                }, size = (roomH * 0.2f).coerceIn(64.dp, 120.dp), poke = sounding + fx.poke)
                Spacer(Modifier.width(8.dp))
                StageLine(shownLine)
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (finger) {
                    val (low, high) = stageRange(position)
                    Box(Modifier.fillMaxWidth().height((roomH * 0.42f).coerceIn(130.dp, 260.dp))) {
                        BigKeys(low, high, "Piano keys from ${noteName(low)} to ${noteName(high)}. Watch which finger presses a key.", Modifier.fillMaxSize(),
                            fill = { m -> when {
                                hand?.pressed == true && hand?.key == m -> PupGold
                                playKey == m -> PupGold.copy(alpha = 0.25f + 0.4f * p)
                                else -> null
                            } },
                            fx = fx,
                            onKey = { m ->
                                if (deps.prefs.sound && (!listening || mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(600); deps.sampler.noteOff(m) } }
                                if (quiz.phase == TapQuiz.Phase.PLAY) { fx.heard(com.bigpapituna.sharpears.engine.playground.StageSound(now(), HeardBy.MATCHER, m, final = true)); handle(quiz.key(m, now())) }
                            })
                        HandOverKeys(low, high, hand, Modifier.fillMaxSize())
                    }
                } else if (kind != TapKind.LOUDER) {
                    SoundBubbles(game.rounds.getOrNull(quiz.index)?.parts?.size ?: game.example?.parts?.size ?: 1, playingPart, sounding, still,
                        Modifier.fillMaxWidth().heightIn(max = (roomH * 0.26f).coerceAtLeast(64.dp)))
                }
            }
            // The answers: big pictures (the word under them for readers); five numbers for a finger.
            val enabled = asking
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (finger) 6.dp else 12.dp, Alignment.CenterHorizontally)) {
                for (c in kind.choices) {
                    val lit = reveal == c || (kind == TapKind.LOUDER && playingPart == kind.choices.indexOf(c))
                    ChoiceButton(c, kind, lit = lit, wobble = if (missed == c) missSeq else 0, enabled = enabled, words = words, glow = if (enabled) p else 0f,
                        right = right, modifier = if (finger) Modifier.weight(1f).widthIn(max = 72.dp) else Modifier.weight(1f).widthIn(max = 200.dp)) {
                        if (quiz.phase == TapQuiz.Phase.ASK) handle(quiz.tap(c, now()))
                    }
                }
            }
        }
        if (cheer > 0) Confetti(cheer, Modifier.fillMaxSize())
    }
}

/** One bubble per sound a round compares, lit while its sound plays, pulsing with each note. */
@Composable
private fun SoundBubbles(parts: Int, playing: Int?, sounding: Int, still: Boolean, modifier: Modifier = Modifier) {
    val beat = remember { Animatable(0f) }
    LaunchedEffect(sounding) { if (sounding > 0 && !still) { beat.snapTo(1f); beat.animateTo(0f, tween(380)) } }
    Row(modifier.semantics { contentDescription = if (playing != null) "Listen" else "Waiting for your answer" },
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until parts) {
            val on = playing == i
            Canvas(Modifier.widthIn(max = 120.dp).weight(1f, fill = false).aspectRatio(1f).padding(4.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension * 0.32f * (1f + if (on) 0.12f * beat.value else 0f)
                if (on) drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = 0.55f), Color.Transparent), c, r * 1.9f), r * 1.9f, c)
                drawCircle(Color(0xFF241F1B).copy(alpha = 0.85f), r, c)
                drawCircle(if (on) PupGold else InkDim.copy(alpha = 0.5f), r, c, style = Stroke(2.5.dp.toPx()))
                musicNote(c, r * 0.9f, if (on) PupGold else Ink.copy(alpha = 0.6f))
                for (k in 1..2) if (on) drawCircle(PupGold.copy(alpha = 0.35f * (1f - beat.value) / k), r * (1.2f + 0.25f * k + 0.3f * (1 - beat.value)), c, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

/** A quaver: a filled head and its stem and flag. */
private fun DrawScope.musicNote(c: Offset, r: Float, colour: Color) {
    drawOval(colour, Offset(c.x - r * 0.42f, c.y + r * 0.15f), Size(r * 0.62f, r * 0.45f))
    drawLine(colour, Offset(c.x + r * 0.16f, c.y + r * 0.35f), Offset(c.x + r * 0.16f, c.y - r * 0.6f), r * 0.12f, cap = StrokeCap.Round)
    val flag = Path().apply { moveTo(c.x + r * 0.16f, c.y - r * 0.6f); quadraticTo(c.x + r * 0.55f, c.y - r * 0.35f, c.x + r * 0.42f, c.y - r * 0.05f) }
    drawPath(flag, colour, style = Stroke(r * 0.12f, cap = StrokeCap.Round))
}

/** A big answer: its picture in a card, the word under it for readers; lit when it is the answer shown. */
@Composable
private fun ChoiceButton(c: TapChoice, kind: TapKind, lit: Boolean, wobble: Int, enabled: Boolean, words: Boolean, glow: Float, right: Boolean,
                         modifier: Modifier = Modifier, onTap: () -> Unit) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(wobble) { if (wobble > 0) { shake.snapTo(1f); shake.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 400f)) } }
    val label = TreeSkyLines.label(c)
    val finger = c.finger
    val shape = if (finger != null) CircleShape else RoundedCornerShape(18.dp)
    Column(modifier.graphicsLayer { translationX = 10f * density * shake.value }
        .clip(shape).background(if (lit) Color(0xFF4A3A22) else Color(0xE6241F1B))
        .border(if (lit) 3.dp else 2.dp, if (lit) PupGold else if (enabled) Accent.copy(alpha = 0.35f + 0.5f * glow) else InkDim.copy(alpha = 0.3f), shape)
        .clickable(role = Role.Button, onClick = onTap).semantics { contentDescription = label }
        .padding(if (finger != null) 4.dp else 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (finger != null) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Text("$finger", color = if (lit) PupGold else Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
            }
            return@Column
        }
        Canvas(Modifier.fillMaxWidth(0.8f).aspectRatio(1.3f)) { choicePicture(c, lit, if (right) RightHandColor else LeftHandColor) }
        if (words) Text(label, color = if (lit) PupGold else Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
            modifier = Modifier.padding(top = 4.dp))
    }
}

/** The pictures on the answer buttons. */
private fun DrawScope.choicePicture(c: TapChoice, lit: Boolean, hand: Color) {
    val w = size.width; val h = size.height
    val ink = if (lit) PupGold else Ink
    when (c) {
        TapChoice.UP, TapChoice.DOWN -> {
            val up = c == TapChoice.UP
            val tip = Offset(w / 2, if (up) h * 0.08f else h * 0.92f)
            val tail = Offset(w / 2, if (up) h * 0.85f else h * 0.15f)
            drawLine(ink, tail, tip, w * 0.07f, cap = StrokeCap.Round)
            val s = w * 0.2f * (if (up) 1f else -1f)
            drawLine(ink, tip, tip + Offset(-w * 0.18f, s), w * 0.07f, cap = StrokeCap.Round)
            drawLine(ink, tip, tip + Offset(w * 0.18f, s), w * 0.07f, cap = StrokeCap.Round)
            drawPath(star(Offset(w * 0.22f, if (up) h * 0.25f else h * 0.75f), w * 0.09f), PupGold.copy(alpha = 0.8f))
        }
        TapChoice.SAME, TapChoice.DIFFERENT -> {
            drawPath(star(Offset(w * 0.3f, h * 0.5f), h * 0.3f), PupGold)
            if (c == TapChoice.SAME) drawPath(star(Offset(w * 0.7f, h * 0.5f), h * 0.3f), PupGold)
            else moon(Offset(w * 0.7f, h * 0.5f), h * 0.28f)
        }
        TapChoice.FIRST, TapChoice.SECOND -> {
            val cx = Offset(w / 2, h / 2)
            drawPath(star(cx, h * 0.24f), if (lit) PupGold else Ink.copy(alpha = 0.8f))
            for (k in 1..3) drawArc(ink.copy(alpha = 0.7f / k), -40f, 80f, false, Offset(cx.x - h * 0.25f * k - h * 0.1f, cx.y - h * 0.25f * k - h * 0.1f),
                Size((h * 0.25f * k + h * 0.1f) * 2, (h * 0.25f * k + h * 0.1f) * 2), style = Stroke(w * 0.035f, cap = StrokeCap.Round))
            val dots = if (c == TapChoice.FIRST) 1 else 2
            for (d in 0 until dots) drawCircle(ink, w * 0.04f, Offset(w * 0.12f + d * w * 0.1f, h * 0.12f))
        }
        TapChoice.FAST -> rabbit(ink)
        TapChoice.SLOW -> turtle(ink)
        TapChoice.LONG -> {
            drawRoundRect(hand.copy(alpha = 0.9f), Offset(w * 0.08f, h * 0.38f), Size(w * 0.84f, h * 0.24f), CornerRadius(h * 0.12f))
            drawRoundRect(ink, Offset(w * 0.08f, h * 0.38f), Size(w * 0.84f, h * 0.24f), CornerRadius(h * 0.12f), style = Stroke(2.dp.toPx()))
        }
        TapChoice.SHORT -> {
            drawRoundRect(hand.copy(alpha = 0.9f), Offset(w * 0.38f, h * 0.38f), Size(w * 0.24f, h * 0.24f), CornerRadius(h * 0.12f))
            drawRoundRect(ink, Offset(w * 0.38f, h * 0.38f), Size(w * 0.24f, h * 0.24f), CornerRadius(h * 0.12f), style = Stroke(2.dp.toPx()))
        }
        TapChoice.HAPPY -> sun(Offset(w / 2, h / 2), h * 0.3f, ink)
        TapChoice.SAD -> rainCloud(Offset(w / 2, h * 0.42f), h * 0.3f, ink)
        else -> {}
    }
}

private val SunYellow = Color(0xFFFFCF4A)
private val CloudGrey = Color(0xFF9AA6B8)
private val RainBlue = Color(0xFF7DB8FF)

/** A smiling sun with its rays. */
internal fun DrawScope.sun(c: Offset, r: Float, ink: Color = Ink) {
    for (k in 0 until 10) {
        val a = 2 * PI * k / 10
        drawLine(SunYellow, c + Offset((r * 1.25f * cos(a)).toFloat(), (r * 1.25f * sin(a)).toFloat()), c + Offset((r * 1.6f * cos(a)).toFloat(), (r * 1.6f * sin(a)).toFloat()),
            r * 0.14f, cap = StrokeCap.Round)
    }
    drawCircle(SunYellow, r, c)
    face(c, r, Color(0xFF6B3A1F), happy = true)
}

/** A grey cloud with a sad face and rain falling from it. */
internal fun DrawScope.rainCloud(c: Offset, r: Float, ink: Color = Ink) {
    for ((dx, dy, s) in listOf(Triple(-0.6f, 0.1f, 0.6f), Triple(0f, -0.2f, 0.8f), Triple(0.6f, 0.1f, 0.6f))) drawCircle(CloudGrey, r * s, c + Offset(r * dx, r * dy))
    drawRoundRect(CloudGrey, c + Offset(-r * 1.1f, r * 0.05f), Size(r * 2.2f, r * 0.6f), CornerRadius(r * 0.3f))
    face(c + Offset(0f, r * 0.1f), r * 0.75f, Color(0xFF3A4458), happy = false)
    for (k in 0 until 4) {
        val x = c.x - r * 0.75f + k * r * 0.5f
        drawLine(RainBlue, Offset(x, c.y + r * 0.85f), Offset(x - r * 0.12f, c.y + r * 1.25f), r * 0.1f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.face(c: Offset, r: Float, ink: Color, happy: Boolean) {
    drawCircle(ink, r * 0.1f, c + Offset(-r * 0.32f, -r * 0.15f))
    drawCircle(ink, r * 0.1f, c + Offset(r * 0.32f, -r * 0.15f))
    if (happy) drawArc(ink, 20f, 140f, false, c + Offset(-r * 0.4f, -r * 0.1f), Size(r * 0.8f, r * 0.6f), style = Stroke(r * 0.1f, cap = StrokeCap.Round))
    else drawArc(ink, 200f, 140f, false, c + Offset(-r * 0.35f, r * 0.25f), Size(r * 0.7f, r * 0.5f), style = Stroke(r * 0.1f, cap = StrokeCap.Round))
}

/** A crescent moon. */
private fun DrawScope.moon(c: Offset, r: Float) {
    val crescent = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r))
        val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r * 0.45f, c.y - r * 1.1f, c.x + r * 1.45f, c.y + r * 0.9f)) }
        op(this, cut, androidx.compose.ui.graphics.PathOperation.Difference)
    }
    drawPath(crescent, Color(0xFFF6E7B0))
}

/** A running rabbit: long ears, a round body, a puff of a tail, speed lines behind. */
internal fun DrawScope.rabbit(ink: Color) {
    val w = size.width; val h = size.height
    val body = Color(0xFFEDE3D6)
    drawOval(body, Offset(w * 0.28f, h * 0.45f), Size(w * 0.42f, h * 0.32f))
    drawCircle(body, h * 0.15f, Offset(w * 0.72f, h * 0.45f))
    for (dx in listOf(0.66f, 0.74f)) drawOval(body, Offset(w * dx, h * 0.05f), Size(w * 0.07f, h * 0.32f))
    drawCircle(Color.White, h * 0.07f, Offset(w * 0.28f, h * 0.55f))
    drawCircle(Color(0xFF3A2A20), h * 0.025f, Offset(w * 0.76f, h * 0.43f))
    for (k in 0 until 3) drawLine(ink.copy(alpha = 0.7f), Offset(w * 0.02f, h * (0.48f + 0.1f * k)), Offset(w * 0.2f, h * (0.48f + 0.1f * k)), h * 0.035f, cap = StrokeCap.Round)
}

/** A slow turtle: a domed shell with its pattern, head and feet. */
internal fun DrawScope.turtle(ink: Color) {
    val w = size.width; val h = size.height
    val skin = Color(0xFF8DBF6A)
    drawCircle(skin, h * 0.12f, Offset(w * 0.82f, h * 0.6f))
    for (x in listOf(0.3f, 0.65f)) drawOval(skin, Offset(w * x - w * 0.06f, h * 0.68f), Size(w * 0.12f, h * 0.14f))
    drawArc(Color(0xFF5E8A3C), 180f, 180f, true, Offset(w * 0.18f, h * 0.3f), Size(w * 0.6f, h * 0.6f))
    for (x in listOf(0.33f, 0.48f, 0.63f)) drawCircle(Color(0xFF3F6528), h * 0.06f, Offset(w * x, h * 0.5f))
    drawCircle(Color(0xFF3A2A20), h * 0.022f, Offset(w * 0.85f, h * 0.57f))
    drawLine(ink.copy(alpha = 0.4f), Offset(w * 0.1f, h * 0.84f), Offset(w * 0.9f, h * 0.84f), h * 0.02f)
}
