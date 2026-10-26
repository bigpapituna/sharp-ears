package com.bigpapituna.sharpears.ui.pup

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.pup.PupFrame
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.engine.pup.PupMotion
import com.bigpapituna.sharpears.engine.world.Bounce
import com.bigpapituna.sharpears.engine.world.Outfit
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.motion.BounceSize
import com.bigpapituna.sharpears.ui.motion.Juice
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.world.LocalPupOutfit
import com.bigpapituna.sharpears.ui.world.Wear
import com.bigpapituna.sharpears.ui.world.drawOutfit
import com.bigpapituna.sharpears.ui.world.drawOutfitBehind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The pup (2.10, the living pup): the one composable every screen draws him with, on the forty stills, standing in a soft
 * gold glow over his shadow. Every number is in engine/pup/PupMotion.kt.
 *
 * - Idle: he breathes (a sine, 3.2 s), blinks every 3 to 6 s, and now and then flicks an ear or his tail. The nine poses
 *   cut into layers (PupLayers.kt) blink with their eyes, flick a real ear or the tail, wag the tail and tilt or lean the
 *   head; the other thirty squint the head's top and turn a soft-masked corner, and tilt and lean as a whole.
 * - [mood]: a held one (CHEER on the beat of [bpm], POINT, LISTEN, SLEEP, IDLE) or a beat played once when it changes to
 *   it (HAPPY, TILT, WAG). A [controller] plays beats on cue (`play`), holds a mood over this one, and runs his brain:
 *   idle acts after ten seconds without input, a nap after a minute, reactions (engine/world/PupBrain.kt).
 * - A new pose cross-fades in (150 ms) with a small pop, never a hard swap.
 * - Every note played ([Juice.onStrike]) makes him hop to its pitch ([bounce]); a tap ([onTap]) makes him wag.
 * - In [Pose.WEAR_BASE] he wears [outfit] (the open profile's, from [LocalPupOutfit]).
 * - [live] false, or smaller than PupMotion.MIN_LIVE_DP: a picture (no breathing, blinks, flicks or hops). [ground] false:
 *   no glow or shadow (a badge, a picture in a frame); [shadow] false keeps the glow. A stage that raises him itself tells
 *   his shadow how far ([lift], 0..1) and can press him down ([squash], 0..1); each change of [poke] is "I heard you": an
 *   ear flick and a little dip.
 * - With the phone's animations off he holds still: a pose change is the plain fade and a beat one fade of his glow.
 * - [clips]: his HAPPY plays the jump clip (assets/pup/jump.webp) where the app has it, and the controller's playClip
 *   plays any clip in his place, fading in over him and back (or, held, staying on its last frame); without the clip (or
 *   with animations off, or dressed up, which the clips are not) the rig plays the beat instead.
 *
 * Everything moving is read in graphicsLayer and draw blocks from remembered Animatables, so a pup never recomposes the
 * screen around him, and he never takes a touch unless [onTap] is set. Decorative: the words beside him say what he means.
 */
@Suppress("ModifierParameter")      // the brief's shape: Pup(pose, mood, modifier); mood is the call sites' second word
@Composable
fun Pup(
    pose: Pose,
    mood: PupMood = pose.mood,
    modifier: Modifier = Modifier,
    /** Dp.Unspecified: the [modifier] sizes him (a photo in a frame); he is a picture then. */
    size: Dp = 88.dp,
    controller: PupController? = null,
    /** Tapping him: a wag, then this. Without it he takes no touches. */
    onTap: (() -> Unit)? = null,
    /** After this long with no new pose and no tap he gets drowsy: slower breaths and blinks (Today uses 30 s). */
    sleepyAfterMs: Long? = null,
    /** False: no idle acts and no nap from his brain (the pose is the message). */
    idle: Boolean = true,
    bounce: BounceSize = BounceSize.SMALL,
    outfit: Outfit = LocalPupOutfit.current,
    live: Boolean = true,
    ground: Boolean = true,
    shadow: Boolean = true,
    /** False: no z's float up while he sleeps (a screen that draws its own). */
    zs: Boolean = true,
    /** CHEER's beat (null: the controller's, PupMotion.CHEER_BPM unless a screen set it) and when one fell (uptime ms). */
    bpm: Int? = null,
    beatAt: Long? = null,
    lift: (() -> Float)? = null,
    squash: (() -> Float)? = null,
    poke: Int = 0,
    colorFilter: ColorFilter? = null,
    clips: Boolean = false,
) {
    val still = reducedMotion()
    val alive = live && !still && size.isSpecified && size >= PupMotion.MIN_LIVE_DP.dp
    val ctl = controller ?: rememberPupController(idle && live)
    val shown = ctl.poseFor(pose)
    val held = ctl.mood?.takeIf { !it.beat } ?: mood.takeIf { !it.beat } ?: pose.mood
    LaunchedEffect(bpm) { if (bpm != null) ctl.bpm = bpm }
    LaunchedEffect(beatAt) { if (beatAt != null) ctl.beatAt = beatAt }
    LaunchedEffect(held) { ctl.holdFor(held) }

    // ---- The pictures: the one shown and the one fading out under it --------------------------------------------------
    val res = LocalResources.current
    var current by remember { mutableStateOf(shown) }
    var previous by remember { mutableStateOf<Pose?>(null) }
    val fade = remember { Animatable(1f) }
    val popT = remember { Animatable(PupMotion.SWAP_POP_MS.toFloat()) }
    LaunchedEffect(shown) {
        if (shown == current) return@LaunchedEffect
        previous = current
        current = shown
        fade.snapTo(0f)
        if (alive) launch { popT.snapTo(0f); popT.animateTo(PupMotion.SWAP_POP_MS.toFloat(), tween(slowed(PupMotion.SWAP_POP_MS), easing = LinearEasing)) }
        fade.animateTo(1f, tween(slowed(PupMotion.SWAP_MS), easing = LinearEasing))
        previous = null
    }
    val curArt = remember(current) { PupArt.bitmap(res, current.res) }
    val prevArt = remember(previous) { previous?.let { PupArt.bitmap(res, it.res) } }
    val twin = current.blinkTwin
    val twinArt = remember(twin) { twin?.let { PupArt.bitmap(res, it.res) } }
    // The pose's layers, when it has them, decoded off the main thread (he is drawn whole until they are in).
    val rigState = produceState<RigArt?>(null, current, alive) {
        value = if (alive && current in PUP_LAYERS) withContext(Dispatchers.Default) { loadRig(res, current) } else null
    }
    val dressed = current == Pose.WEAR_BASE && !outfit.isEmpty
    // His head turns on its own (a tilt, a listening lean) where the cut allows it and no hat sits on it.
    val headTurns = rigState.value.let { it != null && !dressed && it.rig.headMax >= PupMotion.TILT_DEG }
    val headLeans = rigState.value.let { it != null && !dressed && it.rig.headMax >= PupMotion.LISTEN_DEG }

    // ---- Motion state, read only while drawing --------------------------------------------------------------------------
    val enter = remember { Animatable(if (alive) PupMotion.ENTER_FROM else 1f) }
    val frameMs = remember { mutableLongStateOf(0L) }          // this frame's time (System.nanoTime ms, as nowMs())
    val breath = remember { mutableDoubleStateOf(0.0) }        // breaths so far
    val startMs = remember { System.nanoTime() / 1_000_000 }
    val beatT = remember { Animatable(0f) }                     // ms into the beat playing
    val beatMood = remember { mutableStateOf(PupMood.IDLE) }
    val tiltSide = remember { mutableFloatStateOf(1f) }
    val tiltHead = remember { mutableStateOf(false) }          // this tilt turns the head layer, not the whole pup
    val wagTail = remember { mutableStateOf(false) }           // this wag swings the tail layer
    val headTurnsNow by rememberUpdatedState(headTurns)
    val flickPart = remember { mutableIntStateOf(0) }          // 0 the left ear, 1 the right ear, 2 the tail (layered poses)
    val clipNow = remember { mutableStateOf<ShownClip?>(null) }
    val clipFade = remember { Animatable(0f) }
    val context = LocalContext.current
    val clipsOk by rememberUpdatedState(clips && alive && !dressed)
    // The jump (and any clip the screen will ask for, PupController.prepare) decoded ahead, so it starts at once (decoding
    // one on cue took half a second on the emulator). Each is decoded again after it played, for next time.
    val ready = remember { HashMap<String, android.graphics.drawable.AnimatedImageDrawable>() }
    suspend fun decodeAhead(name: String) { if (name !in ready) withContext(Dispatchers.IO) { PupClips.load(context, name) }?.let { ready[name] = it } }
    if (clips && alive) LaunchedEffect(ctl.ahead.size) { for (n in listOf(PupClips.JUMP) + ctl.ahead.toList()) decodeAhead(n) }
    val calm = remember { Animatable(0f) }                      // the glow's fade for a beat with animations off
    val blinkT = remember { Animatable(0f) }                    // ms into a blink
    val flickT = remember { Animatable(0f) }                    // ms into a flick
    val flickEar = remember { mutableStateOf(true) }            // the ear (top) or the tail (bottom)
    val jump = remember { Animatable(0f) }                      // a note's hop, a share of his size
    val land = remember { Animatable(0f) }                      // its landing squash
    val sleepy = remember { Animatable(0f) }
    var taps by remember { mutableIntStateOf(0) }
    val cheerAmt by animateFloatAsState(if (held == PupMood.CHEER && alive) 1f else 0f, tween(PupMotion.HELD_MS), label = "cheer")
    val sleepAmt by animateFloatAsState(if (held == PupMood.SLEEP) 1f else 0f, tween(PupMotion.HELD_MS), label = "sleep")
    val listen = if (alive && held == PupMood.LISTEN) ctl.lean.coerceIn(-1f, 1f) * PupMotion.LISTEN_DEG else 0f
    val point = if (alive && held == PupMood.POINT) ctl.pointAt.coerceIn(-1f, 1f) * PupMotion.POINT_LEAN_DEG else 0f
    val lean by animateFloatAsState((if (headLeans) 0f else listen) + point, tween(PupMotion.LISTEN_MS, easing = FastOutSlowInEasing), label = "lean")
    val headLean by animateFloatAsState(if (headLeans) listen else 0f, tween(PupMotion.LISTEN_MS, easing = FastOutSlowInEasing), label = "headLean")
    val slide by animateFloatAsState(if (held == PupMood.POINT && alive) ctl.pointAt.coerceIn(-1f, 1f) * PupMotion.POINT_MAX else 0f,
        tween(PupMotion.POINT_MS, easing = FastOutSlowInEasing), label = "slide")
    val bpmNow by rememberUpdatedState(PupMotion.cheerBpm(ctl.bpm))
    val anchor by rememberUpdatedState(ctl.beatAt.takeIf { it > 0L } ?: startMs)
    val stageLift by rememberUpdatedState(lift)
    val stageSquash by rememberUpdatedState(squash)

    LaunchedEffect(held) { Log.d(TAG, "pup: ${current.name.lowercase()} holds ${held.name.lowercase()}" + if (held == PupMood.CHEER) " at ${PupMotion.cheerBpm(ctl.bpm)} bpm" else "") }

    // The frame clock: breathing and the cheer follow it. Runs only while he is alive on screen.
    if (alive) LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { n ->
            val now = n / 1_000_000
            if (last != 0L) {
                val period = PupMotion.BREATH_MS + (PupMotion.SLEEP_BREATH_MS - PupMotion.BREATH_MS) * sleepAmt
                breath.doubleValue += (now - last) / (period * (1f + (PupMotion.SLEEPY_SLOW - 1f) * sleepy.value) * PupClock.slow)
            }
            last = now
            frameMs.longValue = now
        }
    }
    if (alive) LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = PupMotion.ENTER_DAMPING, stiffness = PupMotion.ENTER_STIFFNESS)) }

    // Beats on cue. A beat that comes while a bigger one plays is let go; otherwise it starts over from the new one.
    LaunchedEffect(ctl, still) {
        var job: Job? = null
        var playing = PupMood.IDLE
        snapshotFlow { ctl.cue }.collect { cue ->
            if (cue == null || cue.seq <= ctl.consumed) return@collect
            ctl.consumed = cue.seq
            if (System.nanoTime() / 1_000_000 - cue.atMs > STALE_MS) return@collect
            if (job?.isActive == true && PupMotion.priority(playing) > PupMotion.priority(cue.mood)) return@collect
            job?.cancel()
            playing = cue.mood
            job = launch {
                if (still || !live) { calm.snapTo(1f); calm.animateTo(0f, tween(PupMotion.CALM_MS)); return@launch }
                // His jump as the clip, where this pup plays clips and the app has it.
                if (cue.mood == PupMood.HAPPY && clipsOk) {
                    if (playClip(context, PupClips.JUMP, 1, clipNow, clipFade, ready.remove(PupClips.JUMP))) {
                        decodeAhead(PupClips.JUMP)
                        return@launch
                    }
                }
                if (cue.mood == PupMood.TILT) tiltSide.floatValue = -tiltSide.floatValue
                tiltHead.value = headTurnsNow
                wagTail.value = rigState.value != null
                beatMood.value = cue.mood
                val ms = PupMotion.beatMs(cue.mood)
                beatT.snapTo(0f)
                beatT.animateTo(ms.toFloat(), tween(slowed(ms), easing = LinearEasing))
                beatMood.value = PupMood.IDLE
            }
        }
    }
    LaunchedEffect(mood) { if (mood.beat) ctl.play(mood) }
    // A clip asked for by name: played in his place (a held one stays on its last frame), or its fallback beat where there
    // is none, then the mood it hands over to.
    LaunchedEffect(ctl, still) {
        var job: Job? = null
        snapshotFlow { ctl.clipCue }.collect { cue ->
            if (cue == null || cue.seq <= ctl.clipsTaken) return@collect
            ctl.clipsTaken = cue.seq
            // 2.14, tap the pup to play it again: a clip asked for while the same one plays, or holds its last frame, starts over
            // from its first frame (the drawable shown is rewound; no second decode). Another clip takes the place of the first.
            val shown = clipNow.value
            job?.cancelAndJoin()
            job = launch {
                val again = shown?.takeIf { it.name == cue.name }?.drawable
                if (!still && clipsOk && playClip(context, cue.name, cue.loops, clipNow, clipFade, ready.remove(cue.name) ?: again, cue.hold)) {
                    if (!cue.hold && cue.name in ctl.ahead) decodeAhead(cue.name)
                    return@launch
                }
                ctl.play(cue.fallback)
                cue.then?.let { m -> launch { delay(PupMotion.beatMs(cue.fallback).toLong()); ctl.mood = m } }
            }
        }
    }

    // Blinks: a squint (or the twin's fade) every 3 to 6 s while his eyes are open.
    if (alive && current.eyesOpen && held != PupMood.SLEEP) LaunchedEffect(current) {
        val r = java.util.Random()
        while (true) {
            delay(PupMotion.blinkGap(r.nextDouble(), sleepy.value > 0.5f))
            val ms = blinkMs(rigState.value, twin)
            blinkT.snapTo(0f)
            blinkT.animateTo(ms.toFloat(), tween(slowed(ms), easing = LinearEasing))
            blinkT.snapTo(0f)
        }
    }
    // An ear or tail flick every 8 to 15 s (not asleep), and an ear flick for every sound a stage heard.
    suspend fun flickNow(ear: Boolean) {
        flickEar.value = ear
        flickPart.intValue = if (!ear) 2 else if (flickPart.intValue == 0) 1 else 0
        flickT.snapTo(0f)
        flickT.animateTo(PupMotion.FLICK_MS.toFloat(), tween(slowed(PupMotion.FLICK_MS), easing = LinearEasing))
        flickT.snapTo(0f)
    }
    if (alive && held != PupMood.SLEEP) LaunchedEffect(Unit) {
        val r = java.util.Random()
        var ear = r.nextBoolean()
        while (true) { delay(PupMotion.flickGap(r.nextDouble())); flickNow(ear); ear = !ear }
    }
    if (alive) LaunchedEffect(ctl.blinks) {
        if (ctl.blinks == 0) return@LaunchedEffect
        val ms = blinkMs(rigState.value, twin)
        blinkT.snapTo(0f); blinkT.animateTo(ms.toFloat(), tween(slowed(ms), easing = LinearEasing)); blinkT.snapTo(0f)
    }
    if (alive) LaunchedEffect(ctl.flicks) { if (ctl.flicks != 0) flickNow(ctl.flicks % 2 == 1) }
    if (alive) LaunchedEffect(poke) {
        if (poke == 0) return@LaunchedEffect
        launch { land.snapTo(PupMotion.HEARD_DIP); land.animateTo(0f, spring(dampingRatio = PupMotion.HEARD_DAMPING, stiffness = PupMotion.HEARD_STIFFNESS)) }
        flickNow(true)
    }
    if (alive && sleepyAfterMs != null) LaunchedEffect(pose, taps, sleepyAfterMs) {
        sleepy.animateTo(0f, tween(PupMotion.WAKE_MS))
        delay(sleepyAfterMs)
        sleepy.animateTo(1f, tween(PupMotion.DROWSE_MS))
    }
    // Juice: every note played, a hop to its pitch and a landing squash.
    if (alive && bounce != BounceSize.NONE) LaunchedEffect(bounce) {
        var job: Job? = null
        Juice.bounces.collect { b ->
            val big = bounce == BounceSize.BIG
            val h = Bounce.height(b.midi, b.riseDb, big)
            val sq = Bounce.squash(b.midi, big)
            job?.cancel()
            job = launch {
                jump.animateTo(h, tween(if (big) PupMotion.JUICE_BIG_UP_MS else PupMotion.JUICE_UP_MS, easing = FastOutSlowInEasing))
                jump.animateTo(0f, tween(if (big) PupMotion.JUICE_BIG_DOWN_MS else PupMotion.JUICE_DOWN_MS, easing = FastOutLinearInEasing))
                land.snapTo(sq)
                land.animateTo(0f, spring(dampingRatio = PupMotion.JUICE_LAND_DAMPING, stiffness = PupMotion.JUICE_LAND_STIFFNESS))
            }
        }
    }

    // ---- Drawing ----------------------------------------------------------------------------------------------------------
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val zColor = MaterialTheme.colorScheme.onBackground
    val tapNow by rememberUpdatedState(onTap)
    val tap = if (onTap == null) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures {
            taps++
            ctl.poke()
            ctl.play(PupMood.WAG)
            tapNow?.invoke()
        }
    }
    fun beatFrame(): PupFrame = PupMotion.beat(beatMood.value, beatT.value, tiltSide.floatValue)
    fun cheerFrame(): PupFrame = if (cheerAmt <= 0f) PupFrame.REST else PupMotion.cheer((frameMs.longValue - anchor) / PupClock.slow * bpmNow / 60_000.0)
    fun liftNow(b: PupFrame, c: PupFrame) = b.lift + c.lift * cheerAmt + jump.value

    Box((if (size.isSpecified) modifier.size(size) else modifier).then(tap).drawBehind {
        if (!ground) return@drawBehind
        // The glow he stands in (brighter for a moment: a beat with animations off) and his shadow, smaller while he is up.
        pupGround(dark, lift = liftNow(beatFrame(), cheerFrame()) + (stageLift?.invoke()?.coerceIn(0f, 1f) ?: 0f), glow = calm.value,
            shift = slide, wide = current == Pose.SLEEPING, shadow = shadow)
    }) {
        // From the feet: the hop, the squash and stretch, the breath, the wag, the cheer's sway, the lean, the slide.
        Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)
            val px = this.size.height
            val b = beatFrame()
            val c = cheerFrame()
            val k = cheerAmt
            val br = PupMotion.breath(breath.doubleValue, PupMotion.BREATH_SCALE + (PupMotion.SLEEP_SCALE - PupMotion.BREATH_SCALE) * sleepAmt)
            val q = land.value
            val sq = stageSquash?.invoke()?.coerceIn(0f, 1f) ?: 0f
            val pop = PupMotion.swapPop(popT.value) * enter.value
            scaleX = PupMotion.clamp(b.scaleX * (1f + (c.scaleX - 1f) * k) * (1f + br * 0.5f) * (1f + q * 0.6f) * (1f + 0.06f * sq) * pop)
            scaleY = PupMotion.clamp(b.scaleY * (1f + (c.scaleY - 1f) * k) * (1f + br) * (1f - q) * (1f - 0.08f * sq) * pop)
            translationY = -px * liftNow(b, c)
            translationX = this.size.width * slide
            val beatTurn = when (beatMood.value) {
                PupMood.TILT -> 0f                                        // the inner layer (or the head) tilts
                PupMood.WAG -> if (wagTail.value) b.rotation * PupMotion.WAG_BODY_SHARE else b.rotation
                else -> b.rotation
            }
            rotationZ = beatTurn + c.rotation * k + lean
        }) {
            // The head tilt turns round a point low in the picture, so his head swings and his feet stay put.
            Box(Modifier.fillMaxSize().graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, PupMotion.TILT_PIVOT_Y)
                rotationZ = if (beatMood.value == PupMood.TILT && !tiltHead.value) beatFrame().rotation else 0f
            }) {
                Spacer(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - clipFade.value }.drawBehind {
                    val f = fade.value
                    val prev = previous
                    val pa = prevArt
                    // The layered pose, turned for this frame: the eyes' blink, an ear or the tail flicking, the tail's wag,
                    // the head's tilt and listening lean.
                    val ra = rigState.value?.takeIf { it.rig === PUP_LAYERS[current] }
                    val rp = ra?.let {
                        val fs = PupMotion.flickShare(flickT.value)
                        RigPose(
                            head = (if (beatMood.value == PupMood.TILT && tiltHead.value) beatFrame().rotation else 0f) + headLean,
                            leftEar = if (flickPart.intValue == 0) -PupMotion.EAR_FLICK_DEG * fs else 0f,
                            rightEar = if (flickPart.intValue == 1) PupMotion.EAR_FLICK_DEG * fs else 0f,
                            tail = (if (flickPart.intValue == 2) PupMotion.TAIL_FLICK_DEG * fs else 0f) +
                                (if ((beatMood.value == PupMood.WAG || beatMood.value == PupMood.CHEER_WAG) && wagTail.value) PupMotion.tailWag(beatT.value) else 0f),
                            eyes = if (it.rig.hasEyes) PupMotion.eyesBlink(blinkT.value) else 1f,
                        )
                    }
                    val drawCurrent: DrawScope.() -> Unit = {
                        if (ra != null && rp != null) drawPose(current, ra.rig.w, ra.rig.h, outfit) { r -> drawRig(ra, r, rp, colorFilter) }
                        else drawPose(current, curArt, outfit, colorFilter)
                    }
                    val art: DrawScope.() -> Unit = {
                        if (prev == null || pa == null || f >= 1f) drawCurrent()
                        else crossFade({ drawPose(prev, pa, outfit, colorFilter) }, drawCurrent, f)
                        if (ra == null && twinArt != null && twin != null) PupMotion.blinkTwin(blinkT.value).takeIf { it > 0f }?.let { a ->
                            layer(alpha = a) { drawPose(twin, twinArt, outfit, colorFilter) }
                        }
                    }
                    if (ra != null) { art(); return@drawBehind }       // the layers blink and flick themselves
                    val box = PupArt.fit(curArt.width, curArt.height, this.size)
                    val squint = if (twinArt == null) PupMotion.squint(blinkT.value) else 0f
                    val flick = PupMotion.flick(flickT.value)
                    when {
                        abs(flick) > 0.01f -> flicked(box, flickEar.value, flick) { if (squint > 0f) squinted(box, squint, art) else art() }
                        squint > 0f -> squinted(box, squint, art)
                        else -> art()
                    }
                })
            }
        }
        clipNow.value?.let { c -> if (size.isSpecified) ClipView(c.drawable, size, Modifier.graphicsLayer { alpha = clipFade.value }, c.scale) }
        if (current == Pose.SLEEPING && alive && zs) SleepZs(cycle = { (breath.doubleValue % 1.0).toFloat() }, color = zColor, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Debug only (PupDebug, `--ef slow n`): every beat, blink, flick, fade and breath runs n times slower, so a screen recording
 * on a slow emulator catches each frame of a 90 ms squint. Always 1 in a release build.
 */
internal object PupClock { @Volatile var slow = 1f }

private fun slowed(ms: Int) = (ms * PupClock.slow).toInt()

/**
 * The soft gold glow he stands in and his shadow on the ground: smaller while he is [lift] up (a share of his size), [glow]
 * brighter (0..1), moved [shift] of his size sideways, [wide] for a pup lying down. A clip (PupClip) stands on the same.
 */
internal fun DrawScope.pupGround(dark: Boolean, lift: Float = 0f, glow: Float = 0f, shift: Float = 0f, wide: Boolean = false, shadow: Boolean = true) {
    val c = Offset(size.width / 2, size.height * 0.56f)
    val r = size.width * 0.56f
    val a = (if (dark) 0.22f else 0.30f) + 0.35f * glow
    drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = a.coerceAtMost(1f)), Color.Transparent), c, r), r, c)
    if (!shadow) return
    val w = size.width * 0.58f * (1f - 2.2f * lift).coerceAtLeast(0.3f) * (if (wide) 1.35f else 1f)
    drawOval(if (dark) Color.Black.copy(alpha = 0.32f) else Color(0xFF5A4030).copy(alpha = 0.16f),
        topLeft = Offset((size.width - w) / 2 + size.width * shift, size.height * 0.93f), size = Size(w, size.height * 0.07f))
}

/** A blink's length: the eyes layer's, a closed-eyes twin's cross-fade, or the squint. */
private fun blinkMs(rig: RigArt?, twin: Pose?) = when {
    rig != null && rig.rig.hasEyes -> PupMotion.BLINK_EYES_MS
    twin != null -> PupMotion.BLINK_FADE_MS * 2 + PupMotion.BLINK_SHUT_MS
    else -> PupMotion.SQUINT_MS
}

/** A cue older than this when the pup first sees it (he was not on screen) is let go. */
private const val STALE_MS = 1_000L

/** One pose's picture, fitted feet down, with the outfit over the base pose (and under it: a cape, wings). */
private fun DrawScope.drawPose(pose: Pose, img: ImageBitmap, outfit: Outfit, colorFilter: ColorFilter?) =
    drawPose(pose, img.width, img.height, outfit) { r ->
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(r.left.toInt(), r.top.toInt()),
            IntSize(r.width.toInt(), r.height.toInt()), colorFilter = colorFilter)
    }

/** A [w] x [h] picture drawn by [picture] into its fitted box, feet down, with the outfit over the base pose. */
private fun DrawScope.drawPose(pose: Pose, w: Int, h: Int, outfit: Outfit, picture: DrawScope.(Rect) -> Unit) {
    val dressed = pose == Pose.WEAR_BASE && !outfit.isEmpty
    // A hat or a halo reaches above the base picture. Where the box has no room over his head (a tight tile, a 44 dp pup in
    // a row) he and his outfit shrink together, feet down, so nothing pokes out of it.
    val k = minOf(size.width / Wear.BASE_W, size.height / Wear.BASE_H)
    val above = if (dressed) Wear.above(outfit) else 0f
    val shrink = if (above > 0f && above * k > size.height - Wear.BASE_H * k) size.height / (Wear.BASE_H * k + above * k) else 1f
    scale(shrink, shrink, pivot = Offset(size.width / 2f, size.height)) {
        fun DrawScope.inBase(block: DrawScope.() -> Unit) =
            translate((size.width - Wear.BASE_W * k) / 2f, size.height - Wear.BASE_H * k) { scale(k, k, Offset.Zero) { block() } }
        if (dressed && Wear.behind(outfit).isNotEmpty()) inBase { drawOutfitBehind(outfit) }
        picture(PupArt.fit(w, h, size))
        if (dressed) inBase { drawOutfit(outfit) }
    }
}

/** The draw block's area, a little larger than the box so a turned corner is not cut off. */
private fun DrawScope.bounds() = Rect(-size.width * 0.25f, -size.height * 0.25f, size.width * 1.25f, size.height * 1.25f)

/** [block] drawn as one picture at [alpha], composited with [blend]. */
private fun DrawScope.layer(alpha: Float = 1f, blend: BlendMode = BlendMode.SrcOver, block: DrawScope.() -> Unit) {
    val c = drawContext.canvas
    c.saveLayer(bounds(), Paint().apply { this.alpha = alpha; blendMode = blend })
    block()
    c.restore()
}

/** Two pictures cross-faded so he never thins out in the middle: they add up ([BlendMode.Plus]) in a layer of their own. */
private fun DrawScope.crossFade(from: DrawScope.() -> Unit, to: DrawScope.() -> Unit, k: Float) = layer {
    layer(alpha = 1f - k) { from() }
    layer(alpha = k, blend = BlendMode.Plus) { to() }
}

/** The squint: the head's top share of the picture squashed toward the neck, over the body drawn as it is. */
private fun DrawScope.squinted(box: Rect, amount: Float, art: DrawScope.() -> Unit) {
    val split = box.top + box.height * PupMotion.HEAD_SHARE
    clipRect(top = split - 1f) { art() }
    clipRect(bottom = split) {
        scale(1f, 1f - (1f - PupMotion.SQUINT_Y) * amount, pivot = Offset(box.center.x, split)) { art() }
    }
}

/**
 * The flick: a corner of the picture (the ear at the top right, the tail at the bottom left) turned [deg] round a point
 * [PupMotion.FLICK_REACH] of his width in from it. The turned copy fades in toward the corner as the still one fades out,
 * adding up to one picture ([BlendMode.Plus]), so there is no seam and no double edge.
 */
private fun DrawScope.flicked(box: Rect, ear: Boolean, deg: Float, art: DrawScope.() -> Unit) {
    val corner = if (ear) Offset(box.right, box.top) else Offset(box.left, box.bottom)
    val reach = box.width * PupMotion.FLICK_REACH
    val toward = box.center - corner
    val pivot = corner + toward * (reach / hypot(toward.x, toward.y))
    val near = Brush.radialGradient(0f to Color.Black, 0.6f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent, center = corner, radius = reach)
    val far = Brush.radialGradient(0f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.65f), 1f to Color.Black, center = corner, radius = reach)
    val all = bounds()
    layer {
        layer { art(); drawRect(far, all.topLeft, all.size, blendMode = BlendMode.DstIn) }
        layer(blend = BlendMode.Plus) {
            rotate(if (ear) -deg else deg, pivot) { art() }
            drawRect(near, all.topLeft, all.size, blendMode = BlendMode.DstIn)
        }
    }
}

/** Little z's drifting up from a sleeping pup, one after another. */
@Composable
private fun SleepZs(cycle: () -> Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val c = cycle()
        for (i in 0 until 3) {
            val t = (c + i / 3f) % 1f
            val zs = size.width * (0.07f + 0.02f * i)
            val x = size.width * (0.62f + 0.12f * t) + zs * 0.4f * sin(t * 6f)
            val y = size.height * (0.42f - 0.32f * t)
            val a = (if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f) * 0.7f
            val z = Path().apply { moveTo(x, y); lineTo(x + zs, y); lineTo(x, y + zs); lineTo(x + zs, y + zs) }
            drawPath(z, color, alpha = a, style = Stroke(zs * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
