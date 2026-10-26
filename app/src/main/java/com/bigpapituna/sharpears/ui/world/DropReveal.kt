package com.bigpapituna.sharpears.ui.world

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.world.Drop
import com.bigpapituna.sharpears.engine.world.DropSource
import com.bigpapituna.sharpears.engine.world.LevelSticker
import com.bigpapituna.sharpears.engine.world.PupReaction
import com.bigpapituna.sharpears.engine.world.Rarity
import com.bigpapituna.sharpears.engine.world.Reward
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.ui.pup.ReplayPup
import com.bigpapituna.sharpears.ui.pup.rememberPupReplay
import com.bigpapituna.sharpears.ui.pup.PupController
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.pup.rememberPupController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A card's face is cream in both themes: it is a thing you hold, not a surface of the app. */
private val CardFace = Color(0xFFFFFBF2)

/** The headline for a drop: new and its rarity, or how many now for a double. */
fun dropTitle(d: Drop): String = when {
    d.source == DropSource.EGG -> "The egg hatched!"
    !d.isNew -> "Another one!"
    d.rarity == Rarity.SHINY -> "Shiny!"
    d.rarity == Rarity.RARE -> "A rare one!"
    else -> "New sticker!"
}

/**
 * One sticker card: its back (a paw pattern in the rarity's colour, a prism for shiny) until [flip] passes the half,
 * then the sticker on cream with a rarity edge. [flip] 0..1 is the turn over; [enter] 0..1 the card arriving
 * (growing and turning from edge-on). [burst] 0..1 throws sparkles for a shiny one.
 */
@Composable
fun StickerCard(drop: Drop, flip: Float, enter: Float, modifier: Modifier = Modifier, burst: Float = 0f, width: Dp = 150.dp) {
    val front = flip >= 0.5f
    val r = drop.rarity
    Box(modifier.width(width).aspectRatio(0.75f), contentAlignment = Alignment.Center) {
        if (r.special && front) Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.3f }) {
            drawCircle(Brush.radialGradient(listOf((if (r == Rarity.SHINY) ShinyPrism[1] else RareBlue).copy(alpha = 0.55f), Color.Transparent)), size.minDimension * 0.7f)
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            cameraDistance = 14f * density
            val e = enter
            scaleX = 0.4f + 0.6f * e; scaleY = 0.4f + 0.6f * e
            alpha = e.coerceIn(0f, 1f)
            // Arriving it turns from edge-on to its back; then it turns over to the front.
            rotationY = (1f - e) * 90f + flip * 180f
            transformOrigin = TransformOrigin.Center
        }) {
            if (!front) {
                val back = if (r == Rarity.SHINY) Brush.linearGradient(ShinyPrism) else Brush.verticalGradient(listOf(r.colour(), r.colour().copy(alpha = 0.75f)))
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(back).border(3.dp, WorldInk.copy(alpha = 0.6f), RoundedCornerShape(18.dp))) {
                    Canvas(Modifier.fillMaxSize()) {
                        // Paw prints all over the back.
                        for (row in 0 until 5) for (col in 0 until 3) {
                            val p = Offset(size.width * (0.2f + 0.3f * col + if (row % 2 == 1) 0.15f else 0f), size.height * (0.12f + 0.19f * row))
                            val u = size.width * 0.03f
                            drawOval(Color.White.copy(alpha = 0.35f), Offset(p.x - u * 1.4f, p.y - u * 0.6f), androidx.compose.ui.geometry.Size(u * 2.8f, u * 2.2f))
                            listOf(-1.6f to -1.3f, -0.6f to -2.1f, 0.6f to -2.1f, 1.6f to -1.3f).forEach { (dx, dy) -> drawCircle(Color.White.copy(alpha = 0.35f), u * 0.55f, Offset(p.x + dx * u, p.y + dy * u)) }
                        }
                    }
                }
            } else {
                // The front, drawn mirrored back by the extra half turn.
                val edge = if (r == Rarity.SHINY) Brush.linearGradient(ShinyPrism) else Brush.linearGradient(listOf(r.colour(), r.colour()))
                Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.clip(RoundedCornerShape(18.dp)).background(CardFace)
                    .border(if (r.special) 4.dp else 2.dp, edge, RoundedCornerShape(18.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                    StickerImage(drop.sticker, Modifier.fillMaxWidth().aspectRatio(1f), tilt = 0f)
                }
            }
        }
        if (burst > 0f && burst < 1f) Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = 1.8f; scaleY = 1.6f }) { sparkleBurst(burst) }
    }
}

/**
 * The drop reveal: each sticker of [reward] in turn, the lesson's first. A card flips in and turns over to its
 * sticker with the rarity's jingle; a shiny one bursts into sparkles; a rare or shiny makes [pup] surprised. An egg's
 * sticker comes out of the egg: it wobbles, cracks (light through the crack) and opens, then its card flips. Taps
 * skip ahead. [onDone] after the last card has turned. Everything in place at once with animations off.
 */
@Composable
fun DropReveal(reward: Reward, modifier: Modifier = Modifier, pup: PupController? = null, cardWidth: Dp = 150.dp,
               /** 2.14: false for a child who cannot read yet: the card alone, its words only for TalkBack. */ captions: Boolean = true, onDone: () -> Unit = {}) {
    val still = reducedMotion()
    val sounds = rememberWorldSounds()
    val haptic = LocalHapticFeedback.current
    val drops = reward.drops
    val n = drops.size
    val enter = remember(reward) { List(n) { Animatable(if (still) 1f else 0f) } }
    val flip = remember(reward) { List(n) { Animatable(if (still) 1f else 0f) } }
    val burst = remember(reward) { List(n) { Animatable(0f) } }
    val egg = remember(reward) { Animatable(if (still) 3f else 0f) }     // 0 closed, 0..1 wobble, 1..2 crack, 2..3 open
    var shown by remember(reward) { mutableIntStateOf(if (still) n else 0) }
    var skip by remember(reward) { mutableStateOf(false) }
    LaunchedEffect(reward) {
        if (still) {
            drops.maxByOrNull { it.rarity.ordinal }?.let { sounds.jingle(it.rarity); if (it.rarity.special) pup?.react(PupReaction.SURPRISED) else pup?.react(PupReaction.CLAP) }
            onDone(); return@LaunchedEffect
        }
        for ((i, d) in drops.withIndex()) {
            if (d.source == DropSource.EGG) {
                if (!skip) egg.animateTo(1f, tween(700, easing = LinearEasing))
                sounds.crack()
                if (!skip) egg.animateTo(2f, tween(650, easing = FastOutSlowInEasing)) else egg.snapTo(2f)
                if (!skip) egg.animateTo(3f, tween(420, easing = FastOutSlowInEasing)) else egg.snapTo(3f)
            }
            shown = i + 1
            if (!skip) enter[i].animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) else enter[i].snapTo(1f)
            if (!skip) delay(260)
            launch { flip[i].animateTo(1f, if (skip) tween(160) else tween(520, easing = FastOutSlowInEasing)) }
            delay(if (skip) 80 else 260)
            sounds.jingle(d.rarity)
            haptic.performHapticFeedback(if (d.rarity.special) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            if (d.rarity.special) pup?.react(PupReaction.SURPRISED) else pup?.react(PupReaction.CLAP)
            if (d.rarity == Rarity.SHINY) launch { burst[i].snapTo(0.01f); burst[i].animateTo(1f, tween(1100, easing = LinearEasing)) }
            if (!skip) delay(if (i < n - 1) 1100 else 500)
        }
        onDone()
    }
    Row(modifier.pointerInput(reward) { detectTapGestures { skip = true } }.semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
        contentDescription = drops.take(shown).joinToString(". ") { "${dropTitle(it)} ${it.sticker.name}, ${it.rarity.label.lowercase()}" }
    }, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
        drops.forEachIndexed { i, d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(min = cardWidth).clearAndSetSemantics { }) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    if (d.source == DropSource.EGG && flip[i].value < 0.05f) {
                        val e = egg.value
                        EggView(
                            look = when { e < 1f -> EggLook.CLOSED; e < 2f -> EggLook.CRACKING; else -> EggLook.HATCHED },
                            modifier = Modifier.size(cardWidth * 0.75f, cardWidth),
                            crack = (e - 1f).coerceIn(0f, 1f), open = (e - 2f).coerceIn(0f, 1f),
                            wobble = if (e < 1f) kotlin.math.sin(e * 6f * Math.PI.toFloat()) * e else 0f,
                        )
                    }
                    if (i < shown) StickerCard(d, flip[i].value, enter[i].value, burst = burst[i].value, width = cardWidth)
                }
                val done = flip[i].value >= 0.5f
                if (captions) {
                    Text(if (done) dropTitle(d) else " ", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    Text(if (done) d.sticker.name + if (d.rarity.special) ", ${d.rarity.label.lowercase()}" else "" else " ",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * The whole reward moment over the screen, for after a lesson or a song: the pup, the drop reveal, then anything else
 * the reward brought (the egg laid, the dog house grown, something new to wear, a record), and "Into the album".
 * Confetti for a shiny. Back or the button closes it.
 */
@Composable
fun DropRevealOverlay(reward: Reward, onDone: () -> Unit, primary: String = "Into the album", songTitle: (String) -> String? = { null }) {
    BackHandler(onBack = onDone)
    val pup = rememberPupController()
    var revealed by remember(reward) { mutableStateOf(reward.drops.isEmpty()) }
    Box(Modifier.fillMaxSize().paper().pointerInput(Unit) { detectTapGestures { } }.windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (revealed && reward.best?.rarity == Rarity.SHINY) Confetti(reward, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.Center).widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 2.14: a tap on him plays his jump again, with a tiny sparkle.
            val replay = rememberPupReplay(pup, voice = com.bigpapituna.sharpears.engine.pup.PupTap.line(com.bigpapituna.sharpears.ui.family.profile().isKid))
            ReplayPup(Pose.STICKER, replay, pup, 128.dp, clips = true, idle = true)
            if (reward.drops.isNotEmpty()) DropReveal(reward, pup = pup, cardWidth = if (reward.drops.size > 1) 132.dp else 168.dp) { revealed = true }
            if (revealed) RewardExtras(reward, songTitle = songTitle)
        }
        if (revealed) ChunkyButton(onClick = onDone, modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)) { Text(primary) }
    }
}

/**
 * The rest of a reward, one line each with its picture: the stone's own sticker for the trail (2.5), an egg laid, the
 * dog house grown, a record, and something new to wear (2.5: the pup puts it on and asks "Keep it on?").
 */
@Composable
fun RewardExtras(reward: Reward, modifier: Modifier = Modifier, songTitle: (String) -> String? = { null },
                 /** The voice, for the pup's lines (a new item, a stone's sticker); null: shown only. */
                 say: ((String) -> Unit)? = null,
                 /** False when the caller shows the stone's sticker itself, first (a lesson's finish, 2.5.0). */
                 level: Boolean = true) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (level) reward.level?.let { l -> LevelStickerEarned(l, say = say) }
        if (reward.eggLaid) ExtraRow("A mystery egg appeared. It hatches when you finish your next lesson.") {
            EggView(EggLook.CLOSED, Modifier.size(44.dp, 56.dp))
        }
        if (reward.houseUp) ExtraRow(if (reward.houseAfter >= 3) "The dog house got a chimney, a flag and a porch light." else "The dog house got a red roof and a flower box.") {
            DogHouse(reward.houseAfter, Modifier.size(64.dp))
        }
        reward.record?.let { r ->
            ExtraRow("${r.after.label} record for ${songTitle(r.songId) ?: "this song"}.") { MedalDisc(r.after, Modifier.size(52.dp)) }
        }
        if (reward.unlocked.isNotEmpty()) WardrobeReveal(reward.unlocked, say = say)
    }
}

/**
 * The stone's own sticker, earned on its first finish (2.5): its shape (as it sat on the stone) turns into the sticker
 * with a pop and the pup says its line. With animations off it is the sticker at once.
 */
@Composable
fun LevelStickerEarned(sticker: LevelSticker, modifier: Modifier = Modifier, say: ((String) -> Unit)? = null) {
    val still = reducedMotion()
    val turn = remember(sticker.key) { Animatable(if (still) 1f else 0f) }
    val sounds = rememberWorldSounds()
    LaunchedEffect(sticker.key) {
        if (!still) {
            delay(500)
            turn.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        sounds.jingle(Rarity.COMMON)
        say?.invoke(sticker.line)
        android.util.Log.d(com.bigpapituna.sharpears.TAG, "world: stone sticker ${sticker.key} earned on the finish")
    }
    ExtraRow("${sticker.name}! A sticker for your trail. Stick it on the map.", modifier) {
        val t = turn.value
        val c = MaterialTheme.colorScheme
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            // The shape fades as the sticker grows in over it, with a little overshoot.
            if (t < 1f) StickerSilhouette(sticker, c.onSurface.copy(alpha = 0.38f), Modifier.fillMaxSize().graphicsLayer { alpha = 1f - t })
            if (t > 0f) StickerImage(sticker, Modifier.fillMaxSize().graphicsLayer {
                val k = if (t < 0.7f) 0.6f + 0.6f * (t / 0.7f) else 1.2f - 0.2f * ((t - 0.7f) / 0.3f)
                scaleX = k; scaleY = k; alpha = t.coerceIn(0f, 1f)
            })
        }
    }
}

@Composable
private fun ExtraRow(line: String, modifier: Modifier = Modifier, picture: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { picture() }
        Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
