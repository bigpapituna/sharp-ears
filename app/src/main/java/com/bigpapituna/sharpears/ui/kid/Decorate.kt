package com.bigpapituna.sharpears.ui.kid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.kid.KidLines
import com.bigpapituna.sharpears.engine.world.Decal
import com.bigpapituna.sharpears.engine.world.SceneSlot
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.world.StickerImage
import com.bigpapituna.sharpears.ui.world.centerAt
import kotlin.math.roundToInt

/*
 * Decorate mode (2.5, Roy: stickers "which they can place around the trail themselves as they learn, rather than being
 * put on randomly"). On the kid map a tap on a sticker in the tray (My stickers, or the tray here), or a long press on
 * an empty spot, starts it: the spots of every page the kid has reached show as dashed rings, the tray takes the
 * place of the three big buttons, and the kid taps a sticker then a spot (or a spot then a sticker), drags one there
 * (hold, then drag), taps a stuck sticker to pick it up and move it, or puts it back in the tray. The pup hops and says
 * "Ooh, there!". The rules are engine/world/Decor.kt; the placements are the profile's WorldStore.
 */

/** What decorate mode is doing now. Positions are in the root's coordinates. */
@Stable
class DecorateState {
    var active by mutableStateOf(false)
    /** A sticker picked up (from the tray or a spot), waiting for a spot. */
    var held by mutableStateOf<String?>(null)
    /** An empty spot chosen first (a long press on it), waiting for a sticker. */
    var target by mutableStateOf<String?>(null)
    /** Every spot on screen: id to its box. */
    val spots = mutableStateMapOf<String, Rect>()
    /** The tray's box: a sticker dropped on it goes back in. */
    var tray by mutableStateOf(Rect.Zero)
    /** A sticker being dragged, and where the finger is. */
    var dragging by mutableStateOf<String?>(null)
    var finger by mutableStateOf(Offset.Zero)
    /** Bumps when a sticker lands: the pup hops. */
    var landed by mutableIntStateOf(0)

    fun start(held: String? = null, target: String? = null) { active = true; this.held = held; this.target = target }
    fun stop() { active = false; held = null; target = null; dragging = null }

    /** The spot under [p], or null. */
    fun spotAt(p: Offset): String? = spots.entries.firstOrNull { it.value.inflate(10f).contains(p) }?.key
}

/** What decorate mode changes, and what the pup says while it runs. */
class DecorActions(
    val place: (key: String, slotId: String) -> Unit,
    val unplace: (key: String) -> Unit,
    val say: (String) -> Unit,
)

/**
 * One spot in a scene: in decorate mode a dashed ring (pulsing while a sticker waits for a spot) with what sits in it;
 * outside it, the sticker stuck there (a long press picks it up) or, empty, an invisible spot a long press opens.
 * [reachable]: a page the kid has reached; spots on pages ahead stay hidden and shut.
 */
@Composable
internal fun BoxWithConstraintsScope.SpotView(slot: SceneSlot, decal: Decal?, d: DecorateState, actions: DecorActions, reachable: Boolean) {
    val w = maxWidth
    val size = w * slot.size
    if (!reachable) {
        decal?.let { StickerImage(it, Modifier.centerAt(slot.x, slot.y).size(size)) }
        return
    }
    val held = d.held
    val still = reducedMotion()
    val pulse = if (d.active && (held != null || d.target == slot.id) && decal == null && !still)
        rememberInfiniteTransition(label = "spot").animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "p") else null
    val lifted = d.active && decal != null && held == decal.key
    fun tap() {
        when {
            !d.active -> return
            held != null && decal?.key != held -> { actions.place(held, slot.id); d.held = null; d.target = null; d.landed++; actions.say(KidLines.OOH) }
            held != null -> d.held = null                                  // the same sticker: put it down where it was
            decal != null -> { d.held = decal.key; d.target = null; actions.say(KidLines.PICK_SPOT) }
            else -> { d.target = slot.id; actions.say(KidLines.PICK_STICKER) }
        }
    }
    val label = slot.name.replaceFirstChar { it.uppercase() } + (decal?.let { ", ${it.name}" } ?: ", empty")
    Box(Modifier.centerAt(slot.x, slot.y).size(size)
        .onGloballyPositioned { d.spots[slot.id] = it.boundsInRoot() }
        .graphicsLayer { val k = if (lifted) 1.18f else 1f + 0.08f * (pulse?.value ?: 0f); scaleX = k; scaleY = k }
        .pointerInput(slot.id, decal?.key, d.active) {
            detectTapGestures(
                onTap = { tap() },
                onLongPress = { if (!d.active) { if (decal != null) d.start(held = decal.key) else d.start(target = slot.id); actions.say(if (decal != null) KidLines.PICK_SPOT else KidLines.PICK_STICKER) } },
            )
        }
        .then(if (decal != null && d.active) Modifier.dragOut(decal.key, d, actions) else Modifier)
        .semantics {
            contentDescription = label
            if (d.active) { role = Role.Button; onClick { tap(); true } }
        }, contentAlignment = Alignment.Center) {
        if (d.active) Canvas(Modifier.fillMaxSize()) {
            val chosen = d.target == slot.id
            drawOval(Color.White.copy(alpha = if (chosen) 0.55f else 0.3f))
            drawOval(if (chosen) PupGold else Color.White, style = Stroke(2.5.dp.toPx(),
                pathEffect = if (chosen) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
        }
        if (decal != null && d.dragging != decal.key) StickerImage(decal, Modifier.fillMaxSize())
    }
}

/** Hold, then drag a sticker to a spot (or back onto the tray). */
private fun Modifier.dragOut(key: String, d: DecorateState, actions: DecorActions): Modifier = this.then(Modifier.pointerInput(key) {
    detectDragGesturesAfterLongPress(
        onDragStart = { at -> d.finger = d.fingerFrom(key, at); d.dragging = key; d.held = null },
        onDrag = { change, drag -> change.consume(); d.finger += drag },
        onDragEnd = {
            val spot = d.spotAt(d.finger)
            when {
                spot != null -> { actions.place(key, spot); d.landed++; actions.say(KidLines.OOH) }
                d.tray.contains(d.finger) -> { actions.unplace(key); actions.say(KidLines.BACK_IN_TRAY) }
            }
            d.dragging = null
        },
        onDragCancel = { d.dragging = null },
    )
}).onGloballyPositioned { if (d.dragging != key) dragBase[key] = it.boundsInRoot().topLeft }

/** Where each draggable sticker sits in the root, so a drag starts under the finger. */
private val dragBase = HashMap<String, Offset>()

/** Places a drag's start under the finger: [detectDragGesturesAfterLongPress] reports it in the sticker's own space. */
internal fun DecorateState.fingerFrom(key: String, local: Offset): Offset = (dragBase[key] ?: Offset.Zero) + local

/**
 * The tray in decorate mode, where the three big buttons were: every sticker not stuck yet, newest first, each
 * wiggling a little ("Put me somewhere!"); tap one to pick it up, or hold and drag it to a spot. With a stuck sticker
 * picked up, "Back to the tray" puts it back. Done ends decorate mode.
 */
@Composable
internal fun DecorTray(tray: List<Decal>, d: DecorateState, actions: DecorActions, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val still = reducedMotion()
    val wiggle = if (still) null else rememberInfiniteTransition(label = "tray").animateFloat(-1f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "w")
    val placedHeld = d.held?.takeIf { k -> tray.none { it.key == k } }
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(0f to c.background.copy(alpha = 0f), 0.15f to c.background.copy(alpha = 0.96f), 1f to c.background))
        .navigationBarsPadding().onGloballyPositioned { d.tray = it.boundsInRoot() }.padding(top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(when {
                d.held != null -> KidLines.PICK_SPOT
                d.target != null -> KidLines.PICK_STICKER
                tray.isEmpty() -> KidLines.ALL_PLACED
                else -> KidLines.DECORATE
            }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (placedHeld != null) OutlinedButton(onClick = { actions.unplace(placedHeld); d.held = null; actions.say(KidLines.BACK_IN_TRAY) }) { Text("Back to the tray") }
            ChunkyButton(onClick = onDone) { Text("Done") }
        }
        LazyRow(Modifier.fillMaxWidth().height(84.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tray, key = { it.key }) { s ->
                val held = d.held == s.key
                fun pick() {
                    val t = d.target
                    if (t != null) { actions.place(s.key, t); d.target = null; d.landed++; actions.say(KidLines.OOH) }
                    else { d.held = if (held) null else s.key; if (!held) actions.say(KidLines.PICK_SPOT) }
                }
                Box(Modifier.size(78.dp).clip(RoundedCornerShape(16.dp)).background(if (held) c.primaryContainer else c.surfaceContainerHigh)
                    .pointerInput(s.key) { detectTapGestures { pick() } }
                    .dragOut(s.key, d, actions)
                    .semantics { contentDescription = s.name + if (held) ", picked up" else ", put me somewhere"; role = Role.Button; onClick { pick(); true } }
                    .padding(8.dp), contentAlignment = Alignment.Center) {
                    if (d.dragging != s.key) StickerImage(s, Modifier.fillMaxSize().graphicsLayer {
                        rotationZ = if (held) 0f else 5f * (wiggle?.value ?: 0f) * (if (s.key.hashCode() % 2 == 0) 1f else -1f)
                        val k = if (held) 1.12f else 1f; scaleX = k; scaleY = k
                    })
                }
            }
        }
    }
}

/** The sticker under the finger while it is dragged, drawn over the whole map. [origin]: the map's box in the root. */
@Composable
internal fun DragGhost(d: DecorateState, tray: List<Decal>, placed: (String) -> Decal?, origin: Offset) {
    val key = d.dragging ?: return
    val decal = tray.firstOrNull { it.key == key } ?: placed(key) ?: return
    val density = LocalDensity.current
    val half = with(density) { 40.dp.toPx() }
    val over = d.spotAt(d.finger) != null
    Box(Modifier.offset { IntOffset((d.finger.x - origin.x - half).roundToInt(), (d.finger.y - origin.y - half * 1.6f).roundToInt()) }
        .size(80.dp).graphicsLayer { val k = if (over) 1.15f else 1f; scaleX = k; scaleY = k; alpha = 0.92f }) {
        Canvas(Modifier.fillMaxSize()) { drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(6f, 10f), Size(size.width - 6f, size.height - 6f), CornerRadius(size.width / 2)) }
        StickerImage(decal, Modifier.fillMaxSize(), tilt = 0f)
    }
}
