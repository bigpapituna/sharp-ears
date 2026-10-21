package com.bigpapituna.sharpears.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.world.Bounce
import com.bigpapituna.sharpears.engine.world.IdleAct
import com.bigpapituna.sharpears.engine.world.Outfit
import com.bigpapituna.sharpears.engine.world.PupBrain
import com.bigpapituna.sharpears.engine.world.PupLook
import com.bigpapituna.sharpears.engine.world.PupMood
import com.bigpapituna.sharpears.engine.world.PupReaction
import com.bigpapituna.sharpears.ui.motion.BounceSize
import com.bigpapituna.sharpears.ui.motion.Juice
import com.bigpapituna.sharpears.ui.motion.PupInput
import com.bigpapituna.sharpears.ui.world.LocalPupOutfit
import com.bigpapituna.sharpears.ui.world.Wear
import com.bigpapituna.sharpears.ui.world.drawOutfit
import com.bigpapituna.sharpears.ui.world.drawOutfitBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Golden Coach Pup's poses, each with its own small motion: the ten from the first sheet (art/mascot/sheet.jpg,
 * cut by scripts/cut_mascot.py) and the painted ones (art/mascot/pup-*.jpg, cut by scripts/cut_pup.py; what each
 * shows is in art/GEMINI-BRIEF.md). [WEAR_BASE] is the clean sitting pose the outfit is drawn over.
 */
enum class Pose(val res: Int) {
    WELCOME(R.drawable.pup_welcome), PRACTICING(R.drawable.pup_practicing), GREAT_JOB(R.drawable.pup_great_job),
    MISTAKE(R.drawable.pup_mistake), LEARNING(R.drawable.pup_learning), LEVEL_UP(R.drawable.pup_level_up),
    STREAK(R.drawable.pup_streak), LISTENING(R.drawable.pup_listening), GET_READY(R.drawable.pup_get_ready),
    RESTING(R.drawable.pup_resting),
    // Painted, one image each.
    BUTTERFLY(R.drawable.pup_butterfly), CLAPPING(R.drawable.pup_clapping), CROWN(R.drawable.pup_crown),
    DANCING(R.drawable.pup_dancing), DRUMMING(R.drawable.pup_drumming), FLYING(R.drawable.pup_flying),
    GOODNIGHT(R.drawable.pup_goodnight), HEAD_TILT(R.drawable.pup_head_tilt), PEEK(R.drawable.pup_peek),
    POINTING(R.drawable.pup_pointing), SCRATCH(R.drawable.pup_scratch), SHH(R.drawable.pup_shh),
    SLEEPING(R.drawable.pup_sleeping), SNEEZE(R.drawable.pup_sneeze), STANDING(R.drawable.pup_standing),
    STICKER(R.drawable.pup_sticker), SURPRISED(R.drawable.pup_surprised), TAIL_CHASE(R.drawable.pup_tail_chase),
    THINKING(R.drawable.pup_thinking), TREAT(R.drawable.pup_treat), WEAR_BASE(R.drawable.pup_wear_base),
}

/** The pose for a reaction. */
val PupReaction.pose: Pose get() = when (this) {
    PupReaction.SURPRISED -> Pose.SURPRISED
    PupReaction.THINKING -> Pose.THINKING
    PupReaction.SHH -> Pose.SHH
    PupReaction.PEEK -> Pose.PEEK
    PupReaction.HEAD_TILT -> Pose.HEAD_TILT
    PupReaction.TREAT -> Pose.TREAT
    PupReaction.CLAP -> Pose.CLAPPING
    PupReaction.DANCE -> Pose.DANCING
    PupReaction.FLY -> Pose.FLYING
    PupReaction.CROWN -> Pose.CROWN
    PupReaction.GOODNIGHT -> Pose.GOODNIGHT
}

/** The pose for a held mood (null: the screen's own pose). */
val PupMood.pose: Pose? get() = when (this) {
    PupMood.NONE -> null
    PupMood.THINKING -> Pose.THINKING
    PupMood.SHH -> Pose.SHH
    PupMood.LISTENING -> Pose.LISTENING
    PupMood.PEEK -> Pose.PEEK
    PupMood.SLEEPING -> Pose.SLEEPING
}

/** The pose for an idle act. */
val IdleAct.pose: Pose get() = when (this) {
    IdleAct.SNEEZE -> Pose.SNEEZE
    IdleAct.TAIL_CHASE -> Pose.TAIL_CHASE
    IdleAct.BUTTERFLY -> Pose.BUTTERFLY
    IdleAct.SCRATCH -> Pose.SCRATCH
}

/**
 * The living pup's brain for one Pup on screen (engine/world/PupBrain.kt): idle acts after ten seconds without
 * input (sneeze, tail chase, butterfly, scratch), asleep after a minute, awake on the next touch or note; [react] for
 * moments (a rare drop, a wrong key, the treat jar), [mood] for a state that holds (thinking in an ear game, shh in a
 * listening step). Make one with [rememberPupController] and pass it to [Pup] or [PupSays].
 */
@Stable
class PupController internal constructor(seed: Long, idle: Boolean, private val name: String) {
    internal val brain = PupBrain(seed, SystemClock.uptimeMillis(), idle)
    internal val changed = mutableIntStateOf(0)
    private val lookState = mutableStateOf<PupLook>(PupLook.Base)
    private val moodState = mutableStateOf(PupMood.NONE)

    /** What he shows now (the clock loop in [rememberPupController] keeps it current). */
    val look: PupLook get() = lookState.value

    /** A held state; [PupMood.NONE] gives the screen's pose back (with idle life). */
    var mood: PupMood
        get() = moodState.value
        set(v) { if (v != brain.mood) { brain.mood = v; moodState.value = v; refresh(); Log.d(TAG, "pup $name: mood $v") } }

    /** A moment: shows the reaction's pose for its time, then back. Wakes him if he slept. */
    fun react(r: PupReaction) {
        brain.react(r, SystemClock.uptimeMillis())
        refresh()
        Log.d(TAG, "pup $name: react $r")
    }

    /** Input happened (a touch, a note): wakes him and restarts the idle clock. */
    fun poke(at: Long = SystemClock.uptimeMillis()) {
        if (at <= brain.lastInput) return
        brain.input(at)
        refresh()
    }

    internal fun refresh() {
        val now = brain.look(SystemClock.uptimeMillis())
        if (now != lookState.value) {
            Log.d(TAG, "pup $name: " + when (now) {
                is PupLook.Idle -> "idle ${now.act.name.lowercase()}"
                PupLook.Asleep -> "asleep"
                PupLook.Base -> "awake"
                is PupLook.Reacting -> "reacting ${now.reaction.name.lowercase()}"
                is PupLook.Mood -> "mood ${now.mood.name.lowercase()}"
            })
            lookState.value = now
        }
        changed.intValue++
    }

    /** The pose to show on top of the screen's [base] pose. */
    fun poseFor(base: Pose): Pose = when (val l = look) {
        is PupLook.Reacting -> l.reaction.pose
        is PupLook.Mood -> l.mood.pose ?: base
        PupLook.Asleep -> Pose.SLEEPING
        is PupLook.Idle -> l.act.pose
        PupLook.Base -> base
    }
}

private var pupCount = 0

/**
 * A [PupController] for this screen. [idle] false: no idle acts and no sleep (the dress-up screen, where the base pose
 * must stay to show the outfit). Touches anywhere under [com.bigpapituna.sharpears.ui.motion.wakesPup] and every
 * [Juice.onStrike] count as input.
 */
@Composable
fun rememberPupController(idle: Boolean = true): PupController {
    val c = remember(idle) { PupController(java.util.Random().nextLong(), idle, "#${++pupCount}") }
    LaunchedEffect(c) { snapshotFlow { PupInput.last.longValue }.collect { c.poke(it) } }
    // Sleeps until the next moment the look can change by itself; any input or reaction restarts the wait.
    LaunchedEffect(c) {
        snapshotFlow { c.changed.intValue }.collectLatest {
            while (true) {
                val now = SystemClock.uptimeMillis()
                val next = c.brain.nextChange(now) ?: break
                delay((next - now).coerceAtLeast(1L))
                c.refresh()
            }
        }
    }
    return c
}

/**
 * The pup in [pose], standing in a soft gold glow with his shadow on the ground. He is alive on every screen:
 *
 * - A new pose cross-fades in with a springy hop (an instant swap with animations off). Each pose then keeps moving
 *   a little the way it suggests (a wave, a jump, a head tilt, slow breathing, a munch), his shadow shrinking as he
 *   leaves the ground. He blinks every few seconds, looks around every 6 to 10 s, wiggles when tapped ([onTap]),
 *   drifts drowsy after [sleepyAfterMs].
 * - His [controller] (one is made when none is passed) adds idle acts after ten seconds without input, sleep after a
 *   minute with little z's floating up, reactions and moods. [idle] false keeps the pose (no acts, no sleep).
 * - Every note played ([Juice.onStrike]) makes him hop to its pitch: [BounceSize.SMALL] on ordinary screens,
 *   [BounceSize.BIG] in the Jam, [BounceSize.NONE] to stay put.
 * - In [Pose.WEAR_BASE] he wears [outfit] (the open profile's, from [LocalPupOutfit]).
 *
 * Decorative: the text beside him says what he means, so screen readers skip him.
 */
@Composable
fun Pup(
    pose: Pose, modifier: Modifier = Modifier, size: Dp = 88.dp,
    /** Tapping him: a happy wiggle, then this. Without it he takes no touches (and stays out of the way). */
    onTap: (() -> Unit)? = null,
    /** After this long with no new pose and no tap he gets sleepy: slower sway, a slow nod. The home screen uses 30 s. */
    sleepyAfterMs: Long? = null,
    controller: PupController? = null,
    idle: Boolean = true,
    bounce: BounceSize = BounceSize.SMALL,
    outfit: Outfit = LocalPupOutfit.current,
) {
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    val ctl = controller ?: rememberPupController(idle)
    val shownPose = ctl.poseFor(pose)

    // The pose on screen and the one fading out under it.
    var current by remember { mutableStateOf(shownPose) }
    var previous by remember { mutableStateOf<Pose?>(null) }
    val fade = remember { Animatable(1f) }
    val hop = remember { Animatable(1f) }
    LaunchedEffect(shownPose) {
        if (shownPose == current) return@LaunchedEffect
        if (still) { current = shownPose; previous = null; return@LaunchedEffect }
        previous = current
        current = shownPose
        fade.snapTo(0f)
        // The screen's own pose changing hops in big; his own life (an act, a reaction, waking) more softly.
        hop.snapTo(if (shownPose == pose) 0.72f else 0.86f)
        launch { hop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        fade.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        previous = null
    }
    // A new outfit: a little hop, as if he shook it into place.
    var lastOutfit by remember { mutableStateOf(outfit) }
    LaunchedEffect(outfit) {
        if (outfit == lastOutfit) return@LaunchedEffect
        lastOutfit = outfit
        if (still || current != Pose.WEAR_BASE) return@LaunchedEffect
        hop.snapTo(0.88f)
        hop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        hop.snapTo(0.6f)
        hop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    // One 0..1 cycle, advanced by a frame clock so its speed can ease down when he is sleepy; each pose turns it
    // into its own motion below. Read only in the layer and draw blocks, so he never recomposes per frame.
    val period = periodOf(current)
    val cycle = remember { mutableFloatStateOf(0f) }
    val blink = remember { Animatable(0f) }       // 0 open .. 1 eyes shut (a quick squash of the whole pup)
    val look = remember { Animatable(0f) }        // -1 left .. 1 right
    val wiggle = remember { Animatable(1f) }      // 0..1 through a wiggle; 1 = none
    val sleepy = remember { Animatable(0f) }      // 0 awake .. 1 drowsy
    val jump = remember { Animatable(0f) }        // a note's hop, as a share of his size
    val squash = remember { Animatable(0f) }      // landing squash
    val flick = remember { Animatable(0f) }       // the ear flick: a small tilt, alternating sides
    var taps by remember { mutableIntStateOf(0) }
    val periodNow by rememberUpdatedState(period)
    val asleep = current == Pose.SLEEPING
    if (!still) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) withFrameNanos { now ->
                if (last != 0L) {
                    val speed = 1f - 0.6f * sleepy.value
                    cycle.floatValue = (cycle.floatValue + (now - last) / 1e6f / periodNow * speed) % 1f
                }
                last = now
            }
        }
        LaunchedEffect(asleep) {
            if (asleep) return@LaunchedEffect          // eyes already shut
            val r = java.util.Random()
            while (true) {
                delay(2600L + r.nextInt(3400) + (sleepy.value * 2500).toLong())
                val shut = 70 + (sleepy.value * 260).toInt()                    // drowsy blinks are slow
                blink.animateTo(1f, tween(shut)); blink.animateTo(0f, tween(110 + shut / 2))
                if (r.nextInt(5) == 0 && sleepy.value < 0.5f) { blink.animateTo(1f, tween(60)); blink.animateTo(0f, tween(100)) }
            }
        }
        LaunchedEffect(asleep) {
            if (asleep) { look.animateTo(0f); return@LaunchedEffect }
            val r = java.util.Random()
            while (true) {
                delay(6000L + r.nextInt(4000))
                if (sleepy.value > 0.5f) continue
                look.animateTo((if (r.nextBoolean()) 1f else -1f) * (0.6f + 0.4f * r.nextFloat()), spring(dampingRatio = 0.6f, stiffness = 120f))
                delay(700L + r.nextInt(900))
                look.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 90f))
            }
        }
        if (sleepyAfterMs != null) LaunchedEffect(pose, taps, sleepyAfterMs) {
            sleepy.animateTo(0f, tween(400))
            delay(sleepyAfterMs)
            sleepy.animateTo(1f, tween(2500))
        }
        if (bounce != BounceSize.NONE) LaunchedEffect(bounce) {
            var job: Job? = null
            var side = 1f
            Juice.bounces.collect { b ->
                val big = bounce == BounceSize.BIG
                val h = Bounce.height(b.midi, b.riseDb, big)
                val sq = Bounce.squash(b.midi, big)
                side = -side
                val s = side
                job?.cancel()
                job = launch {
                    launch { flick.snapTo(s); flick.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 380f)) }
                    jump.animateTo(h, tween(if (big) 120 else 90, easing = FastOutSlowInEasing))
                    jump.animateTo(0f, tween(if (big) 220 else 160, easing = FastOutLinearInEasing))
                    squash.snapTo(sq)
                    squash.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 700f))
                }
            }
        }
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val zColor = MaterialTheme.colorScheme.onBackground
    val m = if (still) 0f else 1f
    val liftOf = liftOf(current)
    fun hopNow() = abs(sin(cycle.floatValue * PI)).toFloat()   // 0..1..0, a hop per cycle
    val tap = if (onTap == null) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures {
            taps++
            ctl.poke()
            if (!still) scope.launch { wiggle.snapTo(0f); wiggle.animateTo(1f, tween(700, easing = LinearEasing)) }
            onTap()
        }
    }
    Box(modifier.size(size).then(tap).drawBehind {
        // The glow he stands in, and his shadow (smaller while he is in the air, longer while he lies asleep).
        val lift = liftOf * hopNow() * m + jump.value
        val glow = Offset(this.size.width / 2, this.size.height * 0.56f)
        val r = this.size.width * 0.56f
        drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = if (dark) 0.22f else 0.30f), Color.Transparent), glow, r), r, glow)
        val w = this.size.width * 0.58f * (1f - 2.2f * lift).coerceAtLeast(0.3f) * (1f + 0.03f * look.value * look.value) *
            (if (current == Pose.SLEEPING) 1.35f else 1f)
        drawOval(if (dark) Color.Black.copy(alpha = 0.32f) else Color(0xFF5A4030).copy(alpha = 0.16f),
            topLeft = Offset((this.size.width - w) / 2 + this.size.width * 0.03f * look.value, this.size.height * 0.93f), size = Size(w, this.size.height * 0.07f))
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)                 // moves from the feet
            val c = cycle.floatValue
            val wave = sin(c * 2 * PI).toFloat()                       // -1..1, smooth
            val hopped = hopNow()
            val lift = liftOf * hopped * m
            val s = hop.value
            scaleX = s; scaleY = s
            translationY = -size.toPx() * (lift + jump.value)
            poseMotion(current, c, wave, hopped, s, m, size.toPx())
            // Idle life on top of the pose: a blink (a quick squash reads as one from a distance), a look around,
            // a sleepy nod, a note's landing squash and ear flick, and a wiggle when tapped.
            val b = blink.value
            scaleY *= 1f - 0.06f * b
            scaleX *= 1f + 0.025f * b
            val lk = look.value
            rotationZ += 4f * lk
            translationX += size.toPx() * 0.03f * lk
            val z = sleepy.value
            rotationZ += z * 3f * sin(c * 2 * PI + 1.3).toFloat()
            translationY += size.toPx() * 0.015f * z
            val sq = squash.value
            scaleY *= 1f - sq
            scaleX *= 1f + sq * 0.6f
            rotationZ += 4f * flick.value
            val wg = wiggle.value
            if (wg < 1f) {
                val fadeOut = 1f - wg
                rotationZ += 9f * sin(wg * 6 * PI).toFloat() * fadeOut
                val pop = 1f + 0.08f * sin(wg * PI).toFloat()
                scaleX *= pop; scaleY *= pop
            }
        }) {
            previous?.let { p -> PoseImage(p, outfit, alpha = { 1f - fade.value }) }
            PoseImage(current, outfit, alpha = { if (previous == null) 1f else fade.value })
        }
        if (asleep && !still) SleepZs(cycle = { cycle.floatValue }, color = zColor, modifier = Modifier.fillMaxSize())
    }
}

/** One pose's image, feet on the ground (bottom-aligned), with the outfit over the base pose (and under it: a cape, wings). */
@Composable
private fun PoseImage(pose: Pose, outfit: Outfit, alpha: () -> Float) {
    // A hat or a halo reaches above the base image (Wear.box tops below 0). Where the box has no room over his head (a
    // tight tile, a 44 dp pup in a row), he and his outfit shrink together, feet down, so nothing pokes out of it.
    val dressed = pose == Pose.WEAR_BASE && !outfit.isEmpty
    val above = if (dressed) Wear.above(outfit) else 0f
    val fit = Modifier.fillMaxSize().graphicsLayer {
        this.alpha = alpha()
        if (above > 0f) {
            val k = minOf(size.width / Wear.BASE_W, size.height / Wear.BASE_H)
            val free = size.height - Wear.BASE_H * k
            val s = if (above * k <= free) 1f else size.height / (Wear.BASE_H * k + above * k)
            scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 1f)
        }
    }
    // The image's own pixels onto the box, the way ContentScale.Fit with BottomCenter places it.
    fun DrawScope.inBase(block: DrawScope.() -> Unit) {
        val k = minOf(size.width / Wear.BASE_W, size.height / Wear.BASE_H)
        translate((size.width - Wear.BASE_W * k) / 2f, size.height - Wear.BASE_H * k) { scale(k, Offset.Zero) { block() } }
    }
    if (dressed && Wear.behind(outfit).isNotEmpty()) Canvas(fit) { inBase { drawOutfitBehind(outfit) } }
    Image(painterResource(pose.res), contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter,
        modifier = fit)
    if (dressed) Canvas(fit) { inBase { drawOutfit(outfit) } }
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

/** How long one cycle of a pose's motion takes, ms. */
private fun periodOf(p: Pose) = when (p) {
    Pose.RESTING, Pose.SLEEPING -> 3200
    Pose.GREAT_JOB, Pose.LEVEL_UP -> 900
    Pose.STREAK -> 420
    Pose.PRACTICING -> 500
    Pose.CLAPPING -> 420
    Pose.DRUMMING -> 360
    Pose.DANCING -> 640
    Pose.SCRATCH -> 260
    Pose.TREAT -> 380
    Pose.TAIL_CHASE -> 700
    Pose.SNEEZE -> 1400
    Pose.FLYING -> 800
    Pose.SURPRISED -> 1100
    Pose.SHH, Pose.GOODNIGHT -> 2600
    else -> 1600
}

/** How far a pose leaves the ground at the top of its cycle, as a share of his size. */
private fun liftOf(p: Pose) = when (p) {
    Pose.GREAT_JOB, Pose.LEVEL_UP -> 0.12f
    Pose.FLYING -> 0.16f
    Pose.DANCING -> 0.05f
    Pose.STREAK, Pose.CLAPPING -> 0.04f
    Pose.PRACTICING, Pose.DRUMMING -> 0.02f
    else -> 0f
}

/** Each pose's own small motion, on top of the hop. [m] is 0 with animations off. */
private fun GraphicsLayerScope.poseMotion(pose: Pose, c: Float, wave: Float, hopped: Float, s: Float, m: Float, px: Float) {
    when (pose) {
        Pose.WELCOME, Pose.WEAR_BASE -> rotationZ = 4f * wave * m
        Pose.GREAT_JOB, Pose.LEVEL_UP -> scaleY = s * (1f - 0.04f * (1 - hopped) * m)   // squash on landing
        Pose.STREAK -> rotationZ = -3f * m
        Pose.MISTAKE -> rotationZ = (if (wave > 0) 5f * wave else 0f) * m              // a thoughtful tilt, then back
        Pose.LEARNING -> rotationZ = 1.5f * wave * m
        Pose.LISTENING -> rotationZ = 3f * wave * m                                     // swaying to the music
        Pose.GET_READY -> { val p = 1f + 0.05f * hopped * m; scaleX = s * p; scaleY = s * p }
        Pose.RESTING, Pose.SLEEPING -> scaleY = s * (1f + 0.025f * wave * m)           // slow breathing
        Pose.PRACTICING -> {}
        Pose.BUTTERFLY -> { rotationZ = 3f * wave * m; translationX = px * 0.01f * wave * m }   // following it with his eyes
        Pose.CLAPPING -> scaleX = s * (1f + 0.03f * hopped * m)
        Pose.CROWN -> { rotationZ = 2f * wave * m; val p = 1f + 0.02f * (wave + 1f) * m; scaleX = s * p; scaleY = s * p }   // proud
        Pose.DANCING -> { rotationZ = 7f * wave * m; translationX = px * 0.03f * wave * m }
        Pose.DRUMMING -> scaleY = s * (1f - 0.035f * hopped * m)
        Pose.FLYING -> rotationZ = -4f * wave * m
        Pose.GOODNIGHT -> { rotationZ = 2.5f * wave * m; scaleY = s * (1f - 0.02f * (wave + 1f) * m) }   // a slow yawn
        Pose.HEAD_TILT -> rotationZ = (3f + 2f * wave) * m
        Pose.PEEK -> translationX = -px * 0.05f * (1f - hopped) * m
        Pose.POINTING -> translationX = px * 0.025f * hopped * m                        // a little nudge toward it
        Pose.SCRATCH -> rotationZ = 1.6f * wave * m                                     // a quick jitter
        Pose.SHH -> scaleY = s * (1f + 0.015f * wave * m)
        Pose.SNEEZE -> {
            // Wind up, then the sneeze jolts him back.
            val k = if (c < 0.55f) c / 0.55f else 1f - (c - 0.55f) / 0.45f
            val jolt = if (c in 0.55f..0.65f) 1f else 0f
            rotationZ = (-4f * k + 6f * jolt) * m
            scaleY = s * (1f + 0.04f * k * m - 0.05f * jolt * m)
        }
        Pose.STANDING -> scaleY = s * (1f + 0.015f * wave * m)
        Pose.STICKER -> { val p = 1f + 0.03f * hopped * m; scaleX = s * p; scaleY = s * p }
        Pose.SURPRISED -> { rotationZ = 1.2f * sin(c * 12 * PI).toFloat() * m; translationY -= px * 0.03f * hopped * m }
        Pose.TAIL_CHASE -> { rotationZ = 9f * wave * m; translationX = px * 0.04f * cos(c * 2 * PI).toFloat() * m }
        Pose.THINKING -> rotationZ = (1.5f + 1.5f * wave) * m
        Pose.TREAT -> scaleY = s * (1f - 0.03f * hopped * m)                           // munching
    }
}

private val TAIL = 9.dp

/** What the pup says: a gold speech bubble with its tail toward him (on the left). Pops in when the line changes. */
@Composable
fun SpeechBubble(line: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium) {
    val density = LocalDensity.current
    val shape = remember(density) {
        val tail = with(density) { TAIL.toPx() }
        val r = with(density) { 18.dp.toPx() }
        val at = with(density) { 26.dp.toPx() }
        GenericShape { s, _ ->
            addRoundRect(RoundRect(tail, 0f, s.width, s.height, CornerRadius(r)))
            val y = minOf(at, s.height / 2)
            moveTo(tail + 1, y - tail); lineTo(0f, y + tail * 0.2f); lineTo(tail + 1, y + tail * 0.6f); close()
        }
    }
    val still = reducedMotion()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(line) { if (!still) { pop.snapTo(0.9f); pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f)) } }
    Text(line, style = style, color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = TransformOrigin(0f, 0.3f) }
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .padding(start = TAIL + 14.dp, end = 16.dp, top = 12.dp, bottom = 13.dp))
}

/** The pup with what he says beside him, for the moments he speaks up (greetings, feedback, results). */
@Composable
fun PupSays(pose: Pose, line: String, modifier: Modifier = Modifier, size: Dp = 72.dp,
            onTap: (() -> Unit)? = null, sleepyAfterMs: Long? = null, style: TextStyle? = null,
            controller: PupController? = null, bounce: BounceSize = BounceSize.SMALL,
            /** False where the pose is the message (a "not quite", a pointing pup): no idle acts, no sleep. */
            idle: Boolean = true) =
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pup(pose, size = size, onTap = onTap, sleepyAfterMs = sleepyAfterMs, controller = controller, bounce = bounce, idle = idle)
        SpeechBubble(line, Modifier.weight(1f, fill = false), style ?: MaterialTheme.typography.titleMedium)
    }

/**
 * The main action on a screen: a pill that sits on a darker lip and sinks into it when pressed, then springs back,
 * the same bounce the pup has. Secondary actions stay plain outlined or text buttons.
 */
@Composable
fun ChunkyButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lip = 4.dp
    val sink by animateDpAsState(if (pressed && enabled) lip else 0.dp, spring(dampingRatio = 0.45f, stiffness = 1100f), label = "sink")
    val c = MaterialTheme.colorScheme
    val face = if (enabled) c.primary else c.surfaceContainerHighest
    val edge = if (enabled) lerp(c.primary, Color.Black, 0.3f) else c.outlineVariant
    // Min constraints pass through, so a fillMaxWidth button fills with its face, not just its lip.
    Box(modifier.heightIn(min = 52.dp).clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        propagateMinConstraints = true) {
        Box(Modifier.matchParentSize().padding(top = lip).background(edge, CircleShape))
        CompositionLocalProvider(LocalContentColor provides if (enabled) c.onPrimary else c.onSurfaceVariant,
            LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)) {
            Row(Modifier.offset { IntOffset(0, sink.roundToPx()) }.padding(bottom = lip).background(face, CircleShape)
                .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
        }
    }
}

/** A burst of confetti in the accent, the pup's gold and the hand colours, once per [key]. Nothing with animations off. */
@Composable
fun Confetti(key: Any, modifier: Modifier = Modifier) {
    if (reducedMotion()) return
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(2400, easing = LinearEasing)) }
    // Each bit: sideways speed, upward speed, spin, size; colours in turn.
    val bits = remember(key) {
        val r = java.util.Random(key.hashCode().toLong())
        val colors = listOf(Accent, PupGold, Good, LeftHandColor)
        List(80) { floatArrayOf(r.nextFloat() * 1.3f - 0.65f, -(0.5f + r.nextFloat() * 0.9f), r.nextFloat() * 720f - 360f, 0.6f + r.nextFloat() * 0.8f) to colors[it % 4] }
    }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val sec = p * 2.4f
        val origin = Offset(size.width / 2, size.height * 0.3f)
        bits.forEach { (b, color) ->
            val x = origin.x + b[0] * size.width * 0.9f * sec
            val y = origin.y + (b[1] * sec + 0.75f * sec * sec) * size.height * 0.45f
            val w = 9.dp.toPx() * b[3]; val h = 5.dp.toPx() * b[3]
            rotate(b[2] * sec, Offset(x, y)) {
                drawRect(color.copy(alpha = if (p > 0.75f) (1 - p) * 4 else 1f), Offset(x - w / 2, y - h / 2), Size(w, h))
            }
        }
    }
}
