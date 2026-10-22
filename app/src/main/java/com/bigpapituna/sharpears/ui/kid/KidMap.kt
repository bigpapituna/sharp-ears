package com.bigpapituna.sharpears.ui.kid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.bigpapituna.sharpears.engine.kid.KidMapModel
import com.bigpapituna.sharpears.engine.kid.MapPage
import com.bigpapituna.sharpears.engine.kid.RoadUnit
import com.bigpapituna.sharpears.engine.kid.Stone
import com.bigpapituna.sharpears.engine.kid.StoneState
import com.bigpapituna.sharpears.engine.play.ConcertCardLayout
import com.bigpapituna.sharpears.engine.world.Decal
import com.bigpapituna.sharpears.engine.world.Decor
import com.bigpapituna.sharpears.engine.world.LevelSticker
import com.bigpapituna.sharpears.engine.world.LevelStickers
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.engine.world.SceneSlot
import com.bigpapituna.sharpears.ui.world.StickerSilhouette
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import com.bigpapituna.sharpears.engine.world.WorldState
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.world.PlaceScene
import com.bigpapituna.sharpears.ui.world.SCENE_RATIO
import com.bigpapituna.sharpears.ui.world.WorldAnchors
import com.bigpapituna.sharpears.ui.world.WorldInk
import com.bigpapituna.sharpears.ui.world.colour
import kotlin.math.roundToInt
import com.bigpapituna.sharpears.engine.play.Box as CardBox

/*
 * The kid's map (2.2): the Playground's pages one under the other, each its place's scene with a stone per lesson on
 * the winding trail, the gate to the big kids' road at the end of the last scene, and the road itself once it opens.
 * The pup stands on the next stone (ui/kid/KidHome.kt draws him with [PupSpot]); everything here is laid out in shares
 * of a scene (engine/world/Trail.kt, ui/world/WorldAnchors).
 */

/** How big things are on a scene [w] wide: the stones, the next stone, the pup, the gate. */
internal class MapSizes(private val w: Dp) {
    // The same sizes engine/world/Decor.kt keeps the stickers' spots clear of (DecorTest).
    val stone: Dp = Decor.stoneDp(w.value).dp
    val next: Dp = Decor.nextDp(w.value).dp
    val pup: Dp = Decor.pupDp(w.value).dp
    val gate: Dp = Decor.gateDp(w.value).dp
    val bubble: TextStyle @Composable get() = if (w >= 560.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
}

/** Places a child so its point ([ax], [ay]) as shares of its own size lands on ([x], [y]) of the box it is in. */
internal fun Modifier.pinAt(x: Dp, y: Dp, ax: Float = 0.5f, ay: Float = 0.5f): Modifier = this.layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(c.maxWidth, c.maxHeight) { p.place((x.roundToPx() - ax * p.width).roundToInt(), (y.roundToPx() - ay * p.height).roundToInt()) }
}

/**
 * One Playground page: its name on a little flag, then its scene with the stones on the trail. [overlay] draws over
 * the scene in its own box (the pup at the next stone, the gate on the last page); [onStone] takes a tap on a stone.
 */
@Composable
internal fun PageScene(page: MapPage, world: WorldState, modifier: Modifier = Modifier, onStone: (Stone) -> Unit,
                       onJar: (() -> Unit)?, onEgg: (() -> Unit)?, asleep: Boolean = false,
                       /** 2.5: the scene's sticker spots (decorate mode and its long press); null: stuck stickers only shown. */
                       spots: (@Composable BoxWithConstraintsScope.(SceneSlot, Decal?) -> Unit)? = null,
                       overlay: @Composable BoxWithConstraintsScope.(MapSizes) -> Unit) {
    Column(modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageFlag(page)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sizes = MapSizes(maxWidth)
            PlaceScene(page.place, world, Modifier.fillMaxWidth(), stops = page.stones.size, pupOnStage = false,
                onJar = onJar.takeIf { page.place == Place.GARDEN }, onEgg = onEgg.takeIf { page.place == Place.GARDEN },
                stop = { i -> page.stones.getOrNull(i)?.let { s -> StoneView(s, sizes, onClick = { onStone(s) }, asleep = asleep, stars = false, badge = badgeOf(s, world)) } },
                slots = spots,
                overlay = { overlay(sizes); StarsOver(page, sizes) })
        }
    }
}

/** A page's name on a little pennant in its place's colour, with a gold star once every stone on it is walked. */
@Composable
private fun PageFlag(page: MapPage) {
    val tint = page.place.colour()
    Row(Modifier.height(40.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(22.dp, 30.dp)) {
            drawLine(WorldInk, Offset(size.width * 0.15f, 0f), Offset(size.width * 0.15f, size.height), size.width * 0.12f, StrokeCap.Round)
            val flag = Path().apply { moveTo(size.width * 0.2f, size.height * 0.05f); lineTo(size.width, size.height * 0.28f); lineTo(size.width * 0.2f, size.height * 0.5f); close() }
            drawPath(flag, tint)
            drawPath(flag, WorldInk, style = Stroke(size.width * 0.08f, join = StrokeJoin.Round))
        }
        Text(page.page.title, style = MaterialTheme.typography.headlineSmall)
        if (page.walked) Canvas(Modifier.size(26.dp).semantics { contentDescription = "every stone walked" }) { drawStar(PupGold, WorldInk, size.width * 0.06f) }
    }
}

/**
 * A stepping stone: a flat pebble with a soft shadow. Walked: warm sand with a paw print and its best stars under it.
 * Next: the accent, bigger, a play mark, a ring rippling out. Left behind (a big kid's skipped game, or a gap behind a
 * finale): plain stone with an empty paw, open to play. Ahead: pale and still, asleep until the pup gets there.
 */
/** A stone's own sticker while it is still to earn (2.5): its shape shows on the stone. */
internal fun badgeOf(stone: Stone, world: WorldState): LevelSticker? =
    LevelStickers.byLesson(stone.lessonId)?.takeIf { it.lessonId !in world.levels }

@Composable
internal fun StoneView(stone: Stone, sizes: MapSizes, onClick: () -> Unit, asleep: Boolean = false, stars: Boolean = true, badge: LevelSticker? = null) {
    val next = stone.state == StoneState.NEXT
    val d = if (next) sizes.next else sizes.stone
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val still = reducedMotion()
    // The next stone's ripple, read only where it is drawn: a frame of it repaints the pebble, never recomposes the stone.
    val ring = if (next && !still && !asleep) rememberInfiniteTransition(label = "stone")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart), label = "ring") else null
    val label = stone.title + (badge?.let { ", it gives the ${it.name.lowercase()} sticker" } ?: "") + ", " + when (stone.state) {
        StoneState.DONE -> "done, " + com.bigpapituna.sharpears.engine.skin.Stars.spoken(stone.stars).lowercase()
        StoneState.NEXT -> if (asleep) "next, the pup is asleep" else "next, tap to play"
        StoneState.OPEN -> if (stone.skipped) "passed by, open to play" else "open to play"
        StoneState.LATER -> "later"
    }
    Layout(content = {
        Box(Modifier.size(d * 1.1f, d).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }) {
            Canvas(Modifier.size(d * 1.1f, d)) { drawPebble(stone.state, night, ring?.value ?: 0f, asleep) }
            // The sticker this stone gives, as a shape on its shoulder until it is earned.
            if (badge != null) Box(Modifier.align(Alignment.TopEnd).offset(x = d * 0.22f, y = -d * 0.24f).size(d * 0.56f)
                .background(Color.White.copy(alpha = if (night) 0.75f else 0.88f), CircleShape)
                .border(1.5.dp, WorldInk.copy(alpha = 0.35f), CircleShape).padding(d * 0.07f).clearAndSetSemantics { }) {
                StickerSilhouette(badge, WorldInk.copy(alpha = 0.38f), Modifier.fillMaxSize())
            }
        }
        if (stars && stone.state == StoneState.DONE && stone.stars > 0) StarsUnder(stone.stars, sizes.stone * 0.36f)
    }) { ms, c ->
        // The stone is what is centred on the trail; its stars hang under it, outside its box.
        val pebble = ms[0].measure(c.copy(minWidth = 0, minHeight = 0))
        val stars = ms.getOrNull(1)?.measure(c.copy(minWidth = 0, minHeight = 0))
        layout(pebble.width, pebble.height) {
            pebble.place(0, 0)
            stars?.place((pebble.width - stars.width) / 2, (pebble.height * 0.86f).roundToInt())
        }
    }
}

/**
 * A page's walked stones' stars, where [StoneView] would hang them (just under each stone), but drawn over the pup, his
 * bubble and the signpost: the pup is taller than the gap between the trail's rows, and on rows two and three his head
 * hid the middle star of the walked stone above him.
 */
@Composable
private fun BoxWithConstraintsScope.StarsOver(page: MapPage, sizes: MapSizes) {
    val at = WorldAnchors.stops(page.place, page.stones.size)
    page.stones.forEachIndexed { i, s ->
        val a = at.getOrNull(i) ?: return@forEachIndexed
        if (s.state == StoneState.DONE && s.stars > 0)
            StarsUnder(s.stars, sizes.stone * 0.36f, Modifier.pinAt(maxWidth * a.x, maxHeight * a.y + sizes.stone * 0.36f, ay = 0f))
    }
}

/** Three little stars, [stars] of them gold. */
@Composable
private fun StarsUnder(stars: Int, size: Dp, modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(size * 0.08f)) {
        repeat(3) { i ->
            Canvas(Modifier.size(size).graphicsLayer { translationY = if (i == 1) -size.toPx() * 0.12f else 0f }) {
                if (i < stars) drawStar(PupGold, WorldInk, this.size.width * 0.08f)
                else drawStar(Color.White.copy(alpha = 0.55f), WorldInk.copy(alpha = 0.35f), this.size.width * 0.07f)
            }
        }
    }
}

/** A five-point star filling the canvas, [fill] with an [edge] outline [line] wide. */
internal fun DrawScope.drawStar(fill: Color, edge: Color, line: Float) {
    val inset = line
    val pts = ConcertCardLayout.starPoints(CardBox(inset, inset, size.width - inset, size.height - inset))
    val star = Path().apply { pts.forEachIndexed { k, (x, y) -> if (k == 0) moveTo(x, y) else lineTo(x, y) }; close() }
    drawPath(star, fill)
    drawPath(star, edge, style = Stroke(line, join = StrokeJoin.Round))
}

private fun DrawScope.drawPebble(state: StoneState, night: Boolean, ring: Float, asleep: Boolean) {
    val w = size.width; val h = size.height
    val line = w * 0.045f
    val face = when (state) {
        StoneState.DONE -> if (night) Color(0xFFD8B676) else Color(0xFFF6D894)
        StoneState.NEXT -> if (asleep) Color(0xFFB9A7D6) else Accent
        StoneState.OPEN -> if (night) Color(0xFFC9BEAE) else Color(0xFFEFE6D8)
        StoneState.LATER -> if (night) Color(0xFF8F877C) else Color(0xFFD9D1C5)
    }
    // Solid even when asleep: a see-through pebble showed the pond's planks and the hall's carpet through it. A sleeping
    // stone is told apart by its paler face, softer outline and lighter shadow, and by having no mark.
    val alpha = if (state == StoneState.LATER) 0.7f else 1f
    val body = Rect(w * 0.06f, h * 0.12f, w * 0.94f, h * 0.86f)
    // The ripple ring of the next stone.
    if (ring > 0f) {
        val k = 1f + 0.35f * ring
        val rw = body.width * k; val rh = body.height * k
        drawOval(Color.White, Offset(w / 2 - rw / 2, body.center.y - rh / 2), Size(rw, rh), alpha = (1f - ring) * 0.8f, style = Stroke(line * 1.2f))
    }
    // Shadow, then the pebble with its outline and a light catch on top.
    drawOval(Color.Black, Offset(body.left + w * 0.02f, body.top + h * 0.09f), body.size, alpha = 0.16f * alpha)
    drawOval(face, body.topLeft, body.size)
    drawOval(WorldInk, body.topLeft, body.size, alpha = 0.75f * alpha, style = Stroke(line))
    drawOval(Color.White, Offset(body.left + body.width * 0.2f, body.top + body.height * 0.12f), Size(body.width * 0.38f, body.height * 0.2f), alpha = 0.35f * alpha)
    val c = Offset(body.center.x, body.center.y + h * 0.02f)
    val mark = body.height * 0.5f
    when (state) {
        StoneState.DONE -> drawPawAt(c, mark, WorldInk.copy(alpha = 0.55f), filled = true)
        StoneState.OPEN -> drawPawAt(c, mark, WorldInk.copy(alpha = 0.4f), filled = false, line = line * 0.7f)
        StoneState.NEXT -> if (!asleep) {
            val t = Path().apply { moveTo(c.x - mark * 0.32f, c.y - mark * 0.42f); lineTo(c.x + mark * 0.46f, c.y); lineTo(c.x - mark * 0.32f, c.y + mark * 0.42f); close() }
            drawPath(t, Color.White)
            drawPath(t, Color.White, style = Stroke(line * 0.8f, join = StrokeJoin.Round))
        }
        StoneState.LATER -> {}
    }
}

/** A paw print [size] across centred on [c]: a pad and four toes. */
internal fun DrawScope.drawPawAt(c: Offset, size: Float, colour: Color, filled: Boolean, line: Float = size * 0.08f) {
    val style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(line)
    val l = c.x - size / 2; val t = c.y - size / 2
    drawOval(colour, Offset(l + size * 0.24f, t + size * 0.44f), Size(size * 0.52f, size * 0.44f), style = style)
    listOf(0.16f to 0.40f, 0.36f to 0.20f, 0.64f to 0.20f, 0.84f to 0.40f).forEach { (x, y) ->
        drawCircle(colour, size * 0.11f, Offset(l + size * x, t + size * y), style = style)
    }
}

/**
 * The gate to the big kids' road at the end of the last scene: two stone pillars, an arch with a star, and two
 * wooden doors, shut until the Playground is walked and swung wide after (a warm light on the road beyond).
 */
@Composable
internal fun Gate(open: Boolean, width: Dp, modifier: Modifier = Modifier) {
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Canvas(modifier.size(width, width * 1.15f).semantics { contentDescription = if (open) "The big kids' road, open" else "The big kids' road, shut for now" }) {
        val w = size.width; val h = size.height
        val line = w * 0.04f
        val pillar = w * 0.17f
        val stone = if (night) Color(0xFFB5ADA3) else Color(0xFFE6DED2)
        val wood = if (night) Color(0xFFA77A4B) else Color(0xFFD69A5C)
        val top = h * 0.30f
        if (open) drawRect(Brush.verticalGradient(listOf(PupGold.copy(alpha = 0.0f), PupGold.copy(alpha = 0.55f)), startY = top, endY = h),
            Offset(pillar, top), Size(w - 2 * pillar, h - top))
        // The doors: shut, two panels meeting in the middle; open, swung back against the pillars.
        val doorTop = top + h * 0.10f
        val doors = if (open) listOf(pillar to pillar + w * 0.08f, w - pillar - w * 0.08f to w - pillar)
            else listOf(pillar to w / 2, w / 2 to w - pillar)
        doors.forEach { (l, r) ->
            drawRect(wood, Offset(l, doorTop), Size(r - l, h * 0.95f - doorTop))
            drawRect(WorldInk, Offset(l, doorTop), Size(r - l, h * 0.95f - doorTop), style = Stroke(line * 0.8f))
            if (!open) {
                val slats = 3
                for (k in 1 until slats) { val x = l + (r - l) * k / slats; drawLine(WorldInk.copy(alpha = 0.5f), Offset(x, doorTop), Offset(x, h * 0.95f), line * 0.5f) }
                drawLine(WorldInk.copy(alpha = 0.6f), Offset(l, doorTop + (h * 0.95f - doorTop) * 0.35f), Offset(r, doorTop + (h * 0.95f - doorTop) * 0.35f), line * 0.6f)
            }
        }
        // Pillars.
        listOf(0f, w - pillar).forEach { x ->
            drawRoundRect(stone, Offset(x, top - h * 0.04f), Size(pillar, h - top + h * 0.04f), CornerRadius(line))
            drawRoundRect(WorldInk, Offset(x, top - h * 0.04f), Size(pillar, h - top + h * 0.04f), CornerRadius(line), style = Stroke(line))
            drawCircle(stone, pillar * 0.42f, Offset(x + pillar / 2, top - h * 0.06f))
            drawCircle(WorldInk, pillar * 0.42f, Offset(x + pillar / 2, top - h * 0.06f), style = Stroke(line * 0.8f))
        }
        // The arch with a star on its crown.
        val arch = Path().apply {
            moveTo(pillar * 0.5f, top); quadraticTo(w / 2, top - h * 0.36f, w - pillar * 0.5f, top)
        }
        drawPath(arch, WorldInk, style = Stroke(line * 3.2f, cap = StrokeCap.Round))
        drawPath(arch, if (open) PupGold else wood, style = Stroke(line * 2f, cap = StrokeCap.Round))
        val s = w * 0.22f
        val starBox = CardBox(w / 2 - s / 2, top - h * 0.30f - s / 2, w / 2 + s / 2, top - h * 0.30f + s / 2)
        val pts = ConcertCardLayout.starPoints(starBox)
        val star = Path().apply { pts.forEachIndexed { k, (x, y) -> if (k == 0) moveTo(x, y) else lineTo(x, y) }; close() }
        drawPath(star, if (open) PupGold else Color.White.copy(alpha = 0.8f))
        drawPath(star, WorldInk, style = Stroke(line * 0.7f, join = StrokeJoin.Round))
    }
}

/** Sideways place of the road's i-th stone, as a share of its swing: the road winds left and right. */
private val WIND = floatArrayOf(0f, 0.6f, 0.95f, 0.6f, 0f, -0.6f, -0.95f, -0.6f)

/** How far down the road the [i]th stone sits, and how tall each step of the road is. */
internal val ROAD_STEP = 96.dp
internal val ROAD_TOP = 84.dp

/** Where the road's [i]th stone sits in the road's box [width] wide: (x, y) of its centre. */
internal fun roadSpot(i: Int, width: Dp): Pair<Dp, Dp> {
    val swing = min(width * 0.3f, 150.dp)
    return (width / 2 + swing * WIND[i % WIND.size]) to (ROAD_TOP + ROAD_STEP * i + ROAD_STEP / 2)
}

/** True when the road runs on to the right from its [i]th stone (toward the next one). */
internal fun roadForward(i: Int, width: Dp): Boolean = roadSpot(i + 1, width).first >= roadSpot(i, width).first

/** The road's height for [stones] stones. */
internal fun roadHeight(stones: Int): Dp = ROAD_TOP + ROAD_STEP * stones + 24.dp

/**
 * The big kids' road (2.2): out of the gate, a grey road with a dashed middle line winding down the page, a stone
 * per lesson of the course units the kid has reached, each unit's name on a small sign where it starts. [overlay]
 * draws over it (the pup at the next stone) in the road's own box.
 */
@Composable
internal fun BigKidsRoad(units: List<RoadUnit>, onStone: (Stone) -> Unit, modifier: Modifier = Modifier, asleep: Boolean = false,
                         world: WorldState? = null,
                         overlay: @Composable BoxWithConstraintsScope.(MapSizes) -> Unit) {
    val stones = units.flatMap { it.stones }
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)
        .height(roadHeight(stones.size)).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
        .background(if (night) Color(0xFF3D5A3F) else Color(0xFFB9DDA4))) {
        val w = maxWidth
        val sizes = MapSizes(w)
        val density = LocalDensity.current
        Canvas(Modifier.matchParentSize()) {
            val cw = size.width; val ch = size.height
            val pts = buildList {
                add(Offset(cw / 2, 0f))
                stones.indices.forEach { i -> val (x, y) = roadSpot(i, w); add(Offset(x.toPx(), y.toPx())) }
                add(Offset(cw / 2, ch + 20f))
            }
            val road = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 0 until pts.size - 1) {
                    val p0 = pts[maxOf(0, i - 1)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(pts.size - 1, i + 2)]
                    cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
                }
            }
            val rw = with(density) { (sizes.stone * 1.25f).toPx() }
            drawPath(road, WorldInk.copy(alpha = 0.6f), style = Stroke(rw + 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(road, if (night) Color(0xFF55585F) else Color(0xFF8A8D94), style = Stroke(rw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(road, Color(0xFFF5E6A8), style = Stroke(rw * 0.08f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(rw * 0.35f, rw * 0.3f))))
        }
        Text("The big kids' road", style = MaterialTheme.typography.headlineSmall, color = if (night) Color(0xFFF3EEE9) else WorldInk,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp).semantics { heading() })
        var at = 0
        units.forEach { u ->
            val first = at
            u.stones.forEach { s ->
                val (x, y) = roadSpot(at, w)
                Box(Modifier.pinAt(x, y)) { StoneView(s, sizes, onClick = { onStone(s) }, asleep = asleep, badge = world?.let { badgeOf(s, it) }) }
                at++
            }
            // The unit's sign beside its first stone, on the side the road does not go on to (the pup's bubble takes the
            // other), past where the pup's signpost stands.
            val (x, y) = roadSpot(first, w)
            val right = !roadForward(first, w)
            val off = sizes.next * 0.5f + sizes.pup * 0.68f + sizes.pup * 0.3f + 8.dp
            UnitSign(u.title, Modifier.pinAt(if (right) x + off else x - off, y, ax = if (right) 0f else 1f, ay = 0.5f))
        }
        overlay(sizes)
    }
}

/**
 * The big kids' road before it opens: the start of the grey road out of the gate, fading out, with a striped barrier
 * across it. It says what is ahead without a word a pre-reader needs.
 */
@Composable
internal fun RoadAhead(modifier: Modifier = Modifier) {
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp).height(150.dp)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(if (night) Color(0xFF3D5A3F) else Color(0xFFB9DDA4), Color.Transparent)))
        .semantics(mergeDescendants = true) { contentDescription = "The big kids' road, after the playground" }) {
        Canvas(Modifier.matchParentSize()) {
            val rw = size.width * 0.1f
            val x = size.width / 2
            drawLine(Color(0xFF8A8D94).copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height * 0.9f), rw, StrokeCap.Round)
            drawLine(Color(0xFFF5E6A8).copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height * 0.85f), rw * 0.08f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(rw * 0.35f, rw * 0.3f)))
            // The barrier: a striped bar on two posts.
            val top = size.height * 0.42f; val bar = size.height * 0.12f
            val left = x - rw * 1.3f; val right = x + rw * 1.3f
            listOf(left + rw * 0.2f, right - rw * 0.2f).forEach { px -> drawLine(WorldInk, Offset(px, top), Offset(px, top + bar * 2.6f), rw * 0.12f, StrokeCap.Round) }
            drawRoundRect(Color.White, Offset(left, top), Size(right - left, bar), CornerRadius(bar * 0.3f))
            val stripe = (right - left) / 6f
            for (k in 0 until 6 step 2) drawRect(Color(0xFFE5484D), Offset(left + k * stripe, top), Size(stripe, bar))
            drawRoundRect(WorldInk, Offset(left, top), Size(right - left, bar), CornerRadius(bar * 0.3f), style = Stroke(rw * 0.06f))
        }
        Text("The big kids' road", style = MaterialTheme.typography.titleLarge, color = if (night) Color(0xFFF3EEE9) else WorldInk,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp))
    }
}

/** A unit's name on a little green road sign. */
@Composable
private fun UnitSign(title: String, modifier: Modifier) {
    val shape = remember { GenericShape { s, _ -> addRoundRect(RoundRect(0f, 0f, s.width, s.height, CornerRadius(s.height * 0.3f))) } }
    Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White,
        modifier = modifier.background(Color(0xFF2F7D4C), shape).padding(horizontal = 12.dp, vertical = 6.dp))
}

/** The scene's height for a scene [w] wide. */
internal fun sceneHeight(w: Dp): Dp = w / SCENE_RATIO

/** The centre of [lessonId]'s stone in [page]'s scene [w] wide, or null when it is not on that page. */
internal fun nextSpot(page: MapPage, lessonId: String?, w: Dp): Pair<Dp, Dp>? {
    val i = page.stones.indexOfFirst { it.lessonId == lessonId }.takeIf { it >= 0 } ?: return null
    val a = WorldAnchors.stop(page.place, i, page.stones.size) ?: return null
    return w * a.x to sceneHeight(w) * a.y
}
