package com.bigpapituna.sharpears.ui.pup

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.analytics.Tester
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.lessons.ChimeMode
import com.bigpapituna.sharpears.engine.lessons.ChimeNote
import com.bigpapituna.sharpears.engine.lessons.Cheer
import com.bigpapituna.sharpears.engine.lessons.CheerCoach
import com.bigpapituna.sharpears.engine.lessons.CheerEntry
import com.bigpapituna.sharpears.engine.lessons.CheerMoment
import com.bigpapituna.sharpears.engine.lessons.CheerPlace
import com.bigpapituna.sharpears.engine.lessons.CheerRules
import com.bigpapituna.sharpears.engine.lessons.CheerSide
import com.bigpapituna.sharpears.engine.lessons.CheerTrigger
import com.bigpapituna.sharpears.engine.lessons.StepPopMotion
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.playground.FxSlow
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Cheer pops (2.13, Roy: "there are no real animations within the lesson itself that help keep the user engaged ... words
 * of affirmation ... similar to what Duolingo does"): now and then, while a step or a song plays, the pup slides in at a
 * corner with a line in a bubble and a small sparkle, stays about 1.6 s, and goes. engine/lessons/CheerRules.kt decides when
 * and what (CheerCoach), CheerLines holds the words; this file is the host that hears the learner ([CheerPops]) and the
 * composable that draws a pop ([CheerPop]).
 */

/**
 * The cheer pops of the screen being played (LessonScreen provides one per lesson, so its embedded player and its
 * Playground stages feed the same rules). The full song player makes its own when none is provided.
 */
val LocalCheers = staticCompositionLocalOf<CheerPops?> { null }

/**
 * The moment as the screen sees it: [busy] (the app plays a demo or a hint now), [tail] (the end of a song or step: let
 * them finish), [betweenNotes] (not the learner's turn: a stage between rounds, a rest of two seconds or more in the music;
 * the mic may be made deaf for a sound then), and [place]: which corner a pop takes (null: no room, the pop is skipped);
 * without one the place's single corner, the end.
 */
data class CheerSituation(val busy: Boolean = false, val tail: Boolean = false, val betweenNotes: Boolean = false,
                          val place: ((Cheer) -> CheerSide?)? = null)

/**
 * A pop on screen: [seq] counts up so two pops in a row both play; [side] the corner, [fromTop] (a one-corner place's
 * every other pop) slides down from the top edge instead of in from the side; [voice] what the voice did with the line.
 */
@Immutable
data class CheerCue(val seq: Int, val cheer: Cheer, val side: CheerSide, val fromTop: Boolean, val kid: Boolean, val voice: ChimeMode)

/**
 * The cheer pops for one screen: [coach] (the rules, one per lesson or song screen) hears every right and wrong note, the
 * progress and the time, and when it gives a [Cheer] this shows it ([cue], drawn by [CheerPop]), says it by the mic rules
 * (CheerRules.voiceMode: a kid's always, a grown-up's with "Say it aloud"; never over the learner's turn with the mic
 * listening through the speaker), plays the sparkle (CheerRules.soundMode), logs `cheer: <trigger> <audience> "<line>"
 * voice=<mode>` and sends `cheer_pop` (trigger and audience only). Never waits on anything and takes no touches.
 */
@Stable
class CheerPops internal constructor(
    val coach: CheerCoach, private val sampler: Sampler, private val mic: MicInput, private val voice: Voice, private val prefs: Prefs,
    private val scope: CoroutineScope, private val tonic: Int, private val telemetry: Boolean,
) {
    val kid get() = coach.kid
    var cue by mutableStateOf<CheerCue?>(null)
        private set
    /** The step pop's chime is ringing now (LessonScreen: StepPops.chiming): no sparkle on top of it. */
    var chiming: () -> Boolean = { false }
    private var sparkleJob: Job? = null
    private val audience get() = if (kid) "kid" else "grown-up"

    private fun now() = SystemClock.uptimeMillis()
    private fun moment(s: CheerSituation) = CheerMoment(now(), s.busy || voice.speaking.value, s.tail)
    private fun sync() { coach.frequency = prefs.cheerFrequency }

    /** A step opened, a try began, a song's run started fresh: [part] is the exercise or song (its bars are remembered). */
    fun stepBegan(part: String) { sync(); coach.stepBegan(part, now()) }

    /** A right note (a chord once): [bar] its bar (a stage: its round), [onTime] in the good window where timing is graded. */
    fun right(bar: Int?, onTime: Boolean?, s: CheerSituation) { sync(); show(coach.right(moment(s), bar, onTime), s) }

    /** A wrong key or a Perform note let pass. */
    fun wrong(bar: Int?) = coach.wrong(CheerMoment(now()), bar)

    /** [fraction] of a [long] step's or song's notes are behind. */
    fun progress(fraction: Double, long: Boolean, s: CheerSituation) { sync(); show(coach.progress(moment(s), fraction, long), s) }

    /** Time goes by ([running]: the learner's exercise or song is playing). */
    fun tick(running: Boolean, s: CheerSituation) { sync(); show(coach.tick(moment(s), running), s) }

    /**
     * A step passed with a pop (2.11): the line for the step pop's bubble (shown with its picture, never said: the step's
     * own line follows the chime), or null with Encouragement off.
     */
    fun stepPassed(): String? {
        sync()
        val c = coach.stepPassed(now()) ?: return null
        Log.d(TAG, "cheer: ${c.trigger.wire} $audience \"${c.line}\" voice=${ChimeMode.SKIP}")
        if (telemetry) Tester.cheerPop(c.trigger, kid)
        return c.line
    }

    /** The pop on screen goes (a step ended, the page moved on); its line, if said, finishes. */
    fun clear() { if (cue != null) cue = null }

    /** Debug (`--es screen cheerpop`): a pop of [trigger] now, whatever the waits, [entry] forced; no tester event. */
    fun debugFire(trigger: CheerTrigger, entry: CheerEntry?, s: CheerSituation = CheerSituation()) { sync(); show(coach.forced(trigger, now(), entry), s) }

    private fun show(c: Cheer?, s: CheerSituation) {
        if (c == null) return
        if (c.trigger == CheerTrigger.STEP_PASSED) return            // the step pop shows it (stepPassed)
        val side = if (s.place != null) s.place.invoke(c) else CheerSide.END
        if (side == null) { Log.d(TAG, "cheer: ${c.trigger.wire} $audience skipped, no room"); return }
        val listening = mic.listening
        val phones = listening && mic.headphonesOn()
        val said = CheerRules.voiceMode(prefs.voice && CheerRules.aloud(kid, prefs.cheerAloud), listening, phones, s.betweenNotes)
        val sound = CheerRules.soundMode(prefs.sound, chiming(), listening, phones, s.betweenNotes)
        Log.d(TAG, "cheer: ${c.trigger.wire} $audience \"${c.line}\" voice=$said")
        if (telemetry) Tester.cheerPop(c.trigger, kid)
        when (said) {
            ChimeMode.PLAY -> voice.speak(c.line)
            ChimeMode.PLAY_DEAF -> voice.speakOverMic(c.line)
            ChimeMode.SKIP -> {}
        }
        if (sound != ChimeMode.SKIP) {
            if (sound == ChimeMode.PLAY_DEAF) mic.deafFor(CheerRules.SPARKLE_DEAF_MS.toLong())
            val before = sparkleJob
            sparkleJob = scope.launch { before?.join(); playSparkle(sampler, CheerRules.sparkle(tonic)) }
        }
        // A place with one corner (s.place null) comes in from the top edge every other pop; one with two alternates corners.
        cue = CheerCue((cue?.seq ?: 0) + 1, c, side, fromTop = s.place == null && c.other, kid = kid, voice = said)
    }
}

/**
 * A [CheerPops] for this screen, new for each [key] (a lesson, a song). [kid]: whose words; [tonic]: the sparkle's key
 * (0, C, where the screen has none). [telemetry] false: the debug screen.
 */
@Composable
fun rememberCheerPops(sampler: Sampler, mic: MicInput, voice: Voice, prefs: Prefs, kid: Boolean, key: Any, tonic: Int = 0,
                      telemetry: Boolean = true): CheerPops {
    val scope = rememberCoroutineScope()
    return remember(key, kid) { CheerPops(CheerCoach(kid, prefs.cheerFrequency), sampler, mic, voice, prefs, scope, tonic, telemetry) }
}

/** The sparkle: two soft high notes, a short fade, let go (also when the screen goes). */
private suspend fun playSparkle(sampler: Sampler, notes: List<ChimeNote>) {
    var at = 0
    try {
        for (n in notes) { delay((n.atMs - at).toLong()); at = n.atMs; sampler.noteOn(n.midi, n.velocity) }
        delay((CheerRules.SPARKLE_MS - 60 - at).toLong().coerceAtLeast(0))
        for (k in 1..3) { notes.forEach { sampler.noteVolume(it.midi, 1f - k / 4f) }; delay(20) }
    } finally {
        notes.forEach { sampler.noteOff(it.midi) }
    }
}

/** Places its content in [rect] (the parent's coordinates, read at layout so a moving rect never recomposes); none: nothing. */
fun Modifier.inRect(rect: () -> Rect?): Modifier = layout { measurable, constraints ->
    val r = rect()?.takeIf { it.width >= 1f && it.height >= 1f }
    val p = measurable.measure(if (r == null) Constraints.fixed(0, 0) else Constraints.fixed(r.width.roundToInt(), r.height.roundToInt()))
    layout(constraints.maxWidth, constraints.maxHeight) { if (r != null) p.place(r.left.roundToInt(), r.top.roundToInt()) }
}

/**
 * The pose he wears for each family, all happy ones: he dances for a run, holds up the trophy for the hard part passed, claps
 * the beat, wears the crown for focus, and waves for the nudge. (Not the streak pose: its flame carries a 7.)
 */
private fun poseOf(t: CheerTrigger): Pose = when (t) {
    CheerTrigger.RUN -> Pose.DANCING
    CheerTrigger.RECOVERY, CheerTrigger.STEP_PASSED -> Pose.GREAT_JOB
    CheerTrigger.HALFWAY -> Pose.STANDING
    CheerTrigger.HARD_PASSED -> Pose.LEVEL_UP
    CheerTrigger.STEADY -> Pose.CLAPPING
    CheerTrigger.FOCUS -> Pose.CROWN
    CheerTrigger.NUDGE -> Pose.WELCOME
}

/** His beat as he lands: the little hop for the big moments, the cheer wag for the rest. */
private fun beatOf(t: CheerTrigger): PupMood = when (t) {
    CheerTrigger.RUN, CheerTrigger.RECOVERY, CheerTrigger.HARD_PASSED, CheerTrigger.FOCUS, CheerTrigger.STEP_PASSED -> PupMood.POP
    CheerTrigger.HALFWAY, CheerTrigger.STEADY, CheerTrigger.NUDGE -> PupMood.CHEER_WAG
}

/** The pop's timings (ms, stretched by `--ef fxSlow n` for a recording). */
private object PopTime {
    const val OUT_MS = 240
    const val FADE_MS = 200
    /** The pup lands this long after the pop starts: his beat and the sparkle go then. */
    const val LAND_MS = 260L
    const val SPARKLE_MS = 520f
}

/** Each entry's spring: the slide snappy with a little overshoot, the pop-up bouncier, the peek slower. */
private fun springOf(e: CheerEntry, slow: Float) = when (e) {
    CheerEntry.SLIDE -> spring<Float>(dampingRatio = 0.62f, stiffness = 380f / (slow * slow))
    CheerEntry.POP_UP -> spring(dampingRatio = 0.45f, stiffness = 420f / (slow * slow))
    CheerEntry.PEEK -> spring(dampingRatio = 0.7f, stiffness = 220f / (slow * slow))
}

/**
 * One cheer pop (2.13), drawn in the place it is given ([modifier] sizes it: a lesson's header, a lane's top band): the pup
 * in a happy pose at the [CheerCue.side] corner with the line in a bubble beside him, a small sparkle as he lands, about
 * 1.6 s (longer lines up to 2.4 s), then away the way he came. Three ways in ([CheerEntry]): sliding in from the edge on a
 * spring (or down from the top edge, [CheerCue.fromTop]), popping up from below, peeking round the edge tilted. With the
 * phone's animations off the pop fades in and out where it sits, no slide, no sparkle, no beat.
 *
 * It takes no touches (no pointer handling anywhere in it; the pup has no onTap) and moves only in graphics layers read from
 * remembered animations, so the screen under it never recomposes for it. The bubble is a polite live region, so TalkBack
 * reads the line. [maxPupDp]: the pup's largest size here.
 */
@Composable
fun CheerPop(cue: CheerCue?, modifier: Modifier = Modifier, maxPupDp: Float = 56f) {
    val still = reducedMotion()
    var shown by remember { mutableStateOf<CheerCue?>(null) }
    val enter = remember { Animatable(0f) }
    val spark = remember { mutableFloatStateOf(-1f) }
    val pup = rememberPupController(idle = false)
    LaunchedEffect(cue?.seq) {
        val slow = FxSlow.factor.coerceAtLeast(1f)
        // A pop on screen goes first (a new one, or the step ended).
        if (shown != null) { enter.animateTo(0f, tween((PopTime.OUT_MS * slow).toInt(), easing = FastOutLinearInEasing)); shown = null }
        val c = cue ?: return@LaunchedEffect
        shown = c
        spark.floatValue = -1f
        enter.snapTo(0f)
        coroutineScope {
            if (still) enter.animateTo(1f, tween((PopTime.FADE_MS * slow).toInt(), easing = LinearOutSlowInEasing))
            else {
                launch {
                    delay((PopTime.LAND_MS * slow).toLong())
                    pup.play(beatOf(c.cheer.trigger))
                    val start = withFrameNanos { it }
                    var t = 0f
                    while (t < 1f) withFrameNanos { n -> t = ((n - start) / 1_000_000f / slow / PopTime.SPARKLE_MS).coerceAtMost(1f); spark.floatValue = t }
                    spark.floatValue = -1f
                }
                enter.animateTo(1f, springOf(c.cheer.entry, slow))
            }
        }
        delay((c.cheer.holdMs * slow).toLong())
        enter.animateTo(0f, tween((PopTime.OUT_MS * slow).toInt(), easing = FastOutLinearInEasing))
        shown = null
    }
    val c = shown ?: return
    val star = remember { sparkStar() }
    BoxWithConstraints(modifier) {
        val pupDp = CheerPlace.pupDp(maxHeight.value, maxPupDp) ?: return@BoxWithConstraints
        val end = c.side == CheerSide.END
        val dir = if (end) 1f else -1f
        val density = LocalDensity.current
        val away = with(density) { 96.dp.toPx() }
        val entry = c.cheer.entry
        val bubbleMax = (maxWidth - pupDp.dp - 16.dp).coerceIn(80.dp, 260.dp)
        Row(Modifier.align(if (end) Alignment.TopEnd else Alignment.TopStart).padding(horizontal = 4.dp, vertical = 2.dp)
            .graphicsLayer {
                val p = enter.value
                if (still) { alpha = p.coerceIn(0f, 1f); return@graphicsLayer }
                val q = 1f - p
                when {
                    entry == CheerEntry.POP_UP -> {
                        transformOrigin = TransformOrigin(if (end) 0.85f else 0.15f, 1f)
                        val s = 0.3f + 0.7f * p
                        scaleX = s; scaleY = s
                        translationY = q * size.height * 0.7f
                        alpha = (p * 2.5f).coerceIn(0f, 1f)
                    }
                    entry == CheerEntry.PEEK && c.fromTop -> {
                        transformOrigin = TransformOrigin(if (end) 1f else 0f, 0f)
                        rotationZ = q * 24f * dir
                        translationY = -q * size.height * 0.9f
                    }
                    entry == CheerEntry.PEEK -> {
                        transformOrigin = TransformOrigin(if (end) 1f else 0f, 1f)
                        rotationZ = -q * 28f * dir
                        translationX = q * size.width * 0.6f * dir
                    }
                    c.fromTop -> translationY = -q * (size.height + away)
                    else -> translationX = q * (size.width + away) * dir
                }
            },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val bubble: @Composable () -> Unit = {
                CheerBubble(c.cheer.line, if (end) BubbleTail.END else BubbleTail.START,
                    Modifier.widthIn(max = bubbleMax).graphicsLayer {
                        // The bubble pops a beat after him (none with animations off: the whole pop fades).
                        if (!still) { val b = ((enter.value - 0.35f) / 0.65f).coerceIn(0f, 1.1f); scaleX = 0.7f + 0.3f * b; scaleY = scaleX
                            transformOrigin = TransformOrigin(if (end) 1f else 0f, 0.5f) }
                    },
                    if (c.kid) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
            }
            if (end) bubble()
            Box(Modifier.size(pupDp.dp)) {
                // The sparkle behind him: a few stars bursting out as he lands, gold and the accent.
                Canvas(Modifier.matchParentSize()) {
                    val t = spark.floatValue
                    if (t < 0f || still) return@Canvas
                    val r0 = size.minDimension * 0.35f
                    val reach = size.minDimension * 0.55f
                    val ease = 1f - (1f - t) * (1f - t)
                    for (i in 0 until SPARKS) {
                        val deg = i * 360f / SPARKS + c.seq * 23f
                        val rad = Math.toRadians(deg.toDouble())
                        val d = r0 + reach * ease
                        val at = Offset(center.x + d * sin(rad).toFloat(), center.y - d * cos(rad).toFloat())
                        val rs = size.minDimension * (if (i % 2 == 0) 0.09f else 0.065f) * (1f - 0.4f * t)
                        translate(at.x, at.y) { rotate(deg + 90f * t, Offset.Zero) { scale(rs, rs, Offset.Zero) {
                            drawPath(star, if (i % 2 == 0) PupGold else Accent, alpha = 1f - t)
                        } } }
                    }
                }
                Pup(poseOf(c.cheer.trigger), PupMood.IDLE, size = pupDp.dp, controller = pup, idle = false, shadow = false, zs = false)
            }
            if (!end) bubble()
        }
    }
}

private const val SPARKS = 7

/** Which side of the bubble its tail is on: toward the pup beside it, or down toward the step pop's check. */
internal enum class BubbleTail { START, END, BOTTOM }

/** The pup's gold bubble (SpeechBubble's colours) with its tail toward [tail]; two lines at most. */
@Composable
internal fun CheerBubble(line: String, tail: BubbleTail, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleSmall) {
    val density = LocalDensity.current
    val shape = remember(density, tail) {
        val tl = with(density) { 8.dp.toPx() }
        val r = with(density) { 16.dp.toPx() }
        GenericShape { s, _ ->
            when (tail) {
                BubbleTail.START -> {
                    addRoundRect(RoundRect(tl, 0f, s.width, s.height, CornerRadius(r)))
                    val y = s.height / 2
                    moveTo(tl + 1, y - tl); lineTo(0f, y + tl * 0.15f); lineTo(tl + 1, y + tl * 0.6f); close()
                }
                BubbleTail.END -> {
                    addRoundRect(RoundRect(0f, 0f, s.width - tl, s.height, CornerRadius(r)))
                    val y = s.height / 2
                    moveTo(s.width - tl - 1, y - tl); lineTo(s.width, y + tl * 0.15f); lineTo(s.width - tl - 1, y + tl * 0.6f); close()
                }
                BubbleTail.BOTTOM -> {
                    addRoundRect(RoundRect(0f, 0f, s.width, s.height - tl, CornerRadius(r)))
                    val x = s.width / 2
                    moveTo(x - tl, s.height - tl - 1); lineTo(x, s.height); lineTo(x + tl, s.height - tl - 1); close()
                }
            }
        }
    }
    val pad = when (tail) {
        BubbleTail.START -> Modifier.padding(start = 8.dp + 12.dp, end = 14.dp, top = 9.dp, bottom = 10.dp)
        BubbleTail.END -> Modifier.padding(start = 14.dp, end = 8.dp + 12.dp, top = 9.dp, bottom = 10.dp)
        BubbleTail.BOTTOM -> Modifier.padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 8.dp + 10.dp)
    }
    Text(line, style = style, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.background(MaterialTheme.colorScheme.primaryContainer, shape).then(pad))
}

/**
 * The step passed line (2.13) with the step pop (2.11): a bubble over the check, its tail pointing down at it, for the line's
 * hold (CheerCoach.holdMs), popping in with the picture and fading out; with animations off it fades in and out. [seq]: the
 * step pop's cue (a new one shows anew); [area]: the step's part in view, as StepPop's. Takes no touches.
 */
@Composable
fun StepPopLine(seq: Int?, line: String?, area: () -> Rect?, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val shownLine = remember { mutableStateOf<String?>(null) }
    val a = remember { Animatable(0f) }
    LaunchedEffect(seq) {
        val l = line
        if (seq == null || l == null) { shownLine.value = null; return@LaunchedEffect }
        val slow = FxSlow.factor.coerceAtLeast(1f)
        shownLine.value = l
        a.snapTo(0f)
        a.animateTo(1f, if (still) tween((PopTime.FADE_MS * slow).toInt()) else spring(dampingRatio = 0.5f, stiffness = 500f / (slow * slow)))
        delay((CheerCoach.holdMs(l) * slow).toLong())
        a.animateTo(0f, tween((PopTime.OUT_MS * slow).toInt(), easing = FastOutLinearInEasing))
        shownLine.value = null
    }
    val l = shownLine.value ?: return
    val density = LocalDensity.current
    Box(modifier) {
        CheerBubble(l, BubbleTail.BOTTOM, Modifier
            .layout { m, c ->
                val maxW = minOf(c.maxWidth, with(density) { 280.dp.roundToPx() })
                val p = m.measure(Constraints(0, maxW, 0, c.maxHeight))
                val box = area() ?: Rect(0f, 0f, c.maxWidth.toFloat(), c.maxHeight.toFloat())
                val above = with(density) { (StepPopMotion.DISC_DP * 1.3f).dp.toPx() + 8.dp.toPx() }
                val x = (box.center.x - p.width / 2f).coerceIn(0f, (c.maxWidth - p.width).toFloat().coerceAtLeast(0f))
                val y = (box.center.y - above - p.height).coerceAtLeast(box.top + with(density) { 8.dp.toPx() })
                layout(c.maxWidth, c.maxHeight) { p.place(x.roundToInt(), y.roundToInt()) }
            }
            .graphicsLayer {
                val v = a.value
                if (still) { alpha = v.coerceIn(0f, 1f); return@graphicsLayer }
                alpha = (v * 2f).coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * v; scaleY = scaleX
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
            if (l.length <= 18) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
    }
}

/** A four-pointed sparkle of radius 1 round the origin. */
private fun sparkStar(): Path = Path().apply {
    for (k in 0 until 8) {
        val r = if (k % 2 == 0) 1f else 0.32f
        val ang = Math.PI * k / 4
        val x = (r * sin(ang)).toFloat()
        val y = (-r * cos(ang)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
