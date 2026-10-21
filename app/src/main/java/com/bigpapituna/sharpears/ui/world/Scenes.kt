package com.bigpapituna.sharpears.ui.world

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.kid.KidPages
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage
import com.bigpapituna.sharpears.engine.world.Decal
import com.bigpapituna.sharpears.engine.world.Decor
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.engine.world.SceneSlot
import com.bigpapituna.sharpears.engine.world.Scenery
import com.bigpapituna.sharpears.engine.world.Sticker
import com.bigpapituna.sharpears.engine.world.Stickers
import com.bigpapituna.sharpears.engine.world.Trail
import com.bigpapituna.sharpears.engine.world.WorldState
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.motion.BounceSize
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** A scene is a little taller than wide; every anchor is a share of its width (x) and height (y). */
const val SCENE_RATIO = 1f / 0.95f

/** A named point in a place's scene, as shares of the scene's width and height. */
data class Anchor(val place: Place, val x: Float, val y: Float)

/**
 * The points the integrator lays things on: the Playground's lesson stops along each scene's trail (2.2: one scene per
 * Playground page, engine/playground/Pages.kt, twelve stops each in three winding rows, engine/world/Trail.kt), the
 * dog house, the treat jar and the egg's nest in the garden, the stage in the concert hall, the gate to the big kids'
 * road. Stickers sit where the kid puts them (2.5, engine/world/Decor.kt).
 */
object WorldAnchors {
    /** The scene a Playground page is drawn as. */
    fun scene(page: PlaygroundPage): Place = KidPages.worldPlace(page)

    /**
     * Playground lesson [n], 1-based along every page in order ([PLAYGROUND_PAGES]): its page's scene, its stop there
     * (the lesson's place on the page). Null past the last page's last lesson.
     */
    fun lesson(n: Int, pages: List<PlaygroundPage> = PLAYGROUND_PAGES): Anchor? =
        KidPages.locate(n, pages)?.let { (p, i) -> stop(scene(pages[p]), i, pages[p].lessonIds.size) }

    /** The Playground lesson [lessonId]'s stop, or null outside the Playground. */
    fun lesson(lessonId: String, pages: List<PlaygroundPage> = PLAYGROUND_PAGES): Anchor? =
        KidPages.locate(lessonId, pages)?.let { (p, i) -> stop(scene(pages[p]), i, pages[p].lessonIds.size) }

    /** The stops of [place], in order: [count] of them (a page's lessons; twelve by default). */
    fun stops(place: Place, count: Int = Trail.STOPS): List<Anchor> = Trail.stops(place, count).map { Anchor(place, it.x, it.y) }

    /** Stop [i] (0-based) of a trail of [count] stops through [place]. */
    fun stop(place: Place, i: Int, count: Int = Trail.STOPS): Anchor? =
        Trail.stops(place, maxOf(count, i + 1)).getOrNull(i)?.let { Anchor(place, it.x, it.y) }

    /** Where the gate to the big kids' road stands in [place]'s scene of [count] stops, when it is the last page's. */
    fun gate(place: Place, count: Int = Trail.STOPS): Anchor = Trail.gate(place, count).let { Anchor(place, it.x, it.y) }

    val HOUSE = Anchor(Place.GARDEN, 0.28f, 0.40f)
    val JAR = Anchor(Place.GARDEN, 0.555f, 0.47f)
    val EGG = Anchor(Place.GARDEN, 0.74f, 0.47f)
    val STAGE = Anchor(Place.CONCERT, 0.50f, 0.38f)

    /** The concert audience's seats, left to right. */
    fun seat(i: Int): Anchor = Anchor(Place.CONCERT, 0.07f + i * (0.86f / (Scenery.SEATS - 1)), 0.89f)
}

/** Places a child centred on an anchor of the scene it is in (the scene's Box gives the size). */
fun Modifier.centerAt(x: Float, y: Float): Modifier = this.layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(c.maxWidth, c.maxHeight) { p.place((x * c.maxWidth - p.width / 2f).roundToInt(), (y * c.maxHeight - p.height / 2f).roundToInt()) }
}

/** A scene's still parts, painted once into a bitmap per size (the holder is remembered per place, stage, theme and egg). */
private class SceneFront {
    private var image: ImageBitmap? = null
    private var size = Size.Zero

    fun image(scope: DrawScope, paint: DrawScope.() -> Unit): ImageBitmap {
        image?.let { if (size == scope.size) return it }
        val bitmap = ImageBitmap(scope.size.width.roundToInt().coerceAtLeast(1), scope.size.height.roundToInt().coerceAtLeast(1))
        CanvasDrawScope().draw(scope, scope.layoutDirection, Canvas(bitmap), scope.size) { paint() }
        image = bitmap; size = scope.size
        return bitmap
    }
}

/**
 * One place of the pup's world as a flat scene drawn in code in the pup's style (thin brown outlines, pastel colour,
 * day in light theme and dusk in dark): the garden with the dog house at [WorldState.house]'s stage, the pond, the
 * concert hall with its audience. Every sticker collected in the place sits at its spot; the hall seats every animal
 * collected anywhere. The trail winds through twelve stops in three rows ([WorldAnchors.stops], 2.2: one stop per
 * lesson of the Playground page drawn here); [stop] draws what goes on stop i (0-based, the kid map's stones), centred
 * on it, for the first [stops] of them; without it the trail is bare. [overlay] is drawn last, over everything, in the
 * scene's own box (the kid map's pup, signpost and gate, placed with [centerAt]). [onSticker] makes the stuck stickers
 * tappable.
 */
@Composable
fun PlaceScene(place: Place, state: WorldState, modifier: Modifier = Modifier,
               /** Stones on the trail: a page's lessons (twelve fill it; a page still being written has fewer). */
               stops: Int = Trail.STOPS,
               stop: (@Composable (i: Int) -> Unit)? = null,
               onSticker: ((Sticker) -> Unit)? = null, pupOnStage: Boolean = true,
               /** The garden's treat jar and egg: tapping them (null: they are only shown). */
               onJar: (() -> Unit)? = null, onEgg: (() -> Unit)? = null,
               /**
                * 2.5, decorate mode: draws every slot of the scene ([Decor.of]) with what sits in it (null: empty), in the
                * scene's box (place it with [centerAt]). Null: the stickers stuck in the slots are just shown.
                */
               slots: (@Composable BoxWithConstraintsScope.(SceneSlot, Decal?) -> Unit)? = null,
               overlay: (@Composable BoxWithConstraintsScope.() -> Unit)? = null) {
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // The concert hall and the workshop have nothing that drifts: no clock, so the scene is painted once and never again
    // until it changes.
    val t = rememberSceneClock(running = place.moves)
    val owned = state.history.owned
    // 2.5: the kid puts the stickers here (decorate mode); nothing is placed by the app any more.
    val placed = state.placements.on(place)
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(SCENE_RATIO).clip(RoundedCornerShape(20.dp))
        .semantics { contentDescription = place.title + (if (placed.isEmpty()) "" else ", ${placed.size} ${if (placed.size == 1) "sticker" else "stickers"} stuck here") + if (place == Place.GARDEN) ", dog house stage ${state.house}" else "" }) {
        val w = maxWidth
        val stage = state.house
        val egg = state.egg
        // Everything that stands still is drawn once into a bitmap (per size, theme, stage and egg); each tick of the
        // scene clock only paints the sky, that bitmap, and the few things that move. Its own layer keeps the
        // stickers' and the pup's animation from redrawing it.
        // The bitmap lives in a remembered holder, not in drawWithCache: a recomposition of the screen around the scene
        // (the pup next to it hops, the jar fills) hands drawWithCache a new lambda, which threw the cache away and
        // painted the whole garden again into a fresh bitmap on every frame.
        val front = remember(place, stage, night, egg, stops) { SceneFront() }
        val p = remember(night) { Palette(night) }
        Spacer(Modifier.fillMaxSize().graphicsLayer().drawBehind {
            val still = front.image(this) {
                when (place) {
                    Place.GARDEN -> drawGarden(stage, night, egg)
                    Place.POND -> drawPond(night)
                    Place.CONCERT -> drawConcert(night)
                    Place.WORKSHOP -> drawWorkshop(night)
                    Place.TREEHOUSE -> drawTreehouse(night)
                    Place.NIGHT_SKY -> drawNightSky(night)
                    Place.FAIR -> drawFair(night)
                }
                drawTrail(trailPath(place, size, stops.coerceAtLeast(1)), place, night)
            }
            run {
                val tick = if (!place.moves) 0f else t.value      // read only where something moves
                when (place) {
                    Place.GARDEN -> { skyBase(p, night, 0.45f); skyLife(p, night, tick, 0.45f) }
                    Place.POND -> { skyBase(p, night, 0.38f); skyLife(p, night, tick, 0.38f) }
                    Place.TREEHOUSE -> { skyBase(p, night, 0.62f); skyLife(p, night, tick, 0.5f) }
                    Place.NIGHT_SKY -> nightSkyBase(night)
                    Place.FAIR -> { skyBase(p, night, 0.62f); skyLife(p, night, tick, 0.5f) }
                    Place.CONCERT, Place.WORKSHOP -> {}
                }
                drawImage(still)
                when (place) {
                    Place.GARDEN -> drawHouseLife(stage, HOUSE_BASE.toOffset(size), size.width * HOUSE_W, tick)
                    Place.POND -> ripples(tick)
                    Place.TREEHOUSE -> treehouseLife(tick)
                    Place.NIGHT_SKY -> nightSkyLife(tick)
                    Place.FAIR -> fairLife(night, tick)
                    Place.CONCERT, Place.WORKSHOP -> {}
                }
            }
        })
        if (place == Place.GARDEN) {
            val j = WorldAnchors.JAR
            val jarTap = if (onJar == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = "Open the treat jar") { onJar() }
            TreatJarView(state.jar, Modifier.centerAt(j.x, j.y).size(w * 0.11f, w * 0.14f).then(jarTap))
            if (state.egg) {
                val e = WorldAnchors.EGG
                val eggTap = if (onEgg == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = "Look at the egg") { onEgg() }
                EggView(EggLook.CLOSED, Modifier.centerAt(e.x, e.y - 0.035f).size(w * 0.09f, w * 0.115f).then(eggTap))
            }
        }
        if (place == Place.CONCERT && pupOnStage) {
            val a = WorldAnchors.STAGE
            Pup(Pose.PRACTICING, size = w * 0.26f, bounce = BounceSize.SMALL, modifier = Modifier.centerAt(a.x, a.y))
        }
        if (slots != null) {
            val at = placed.associate { it.first.id to it.second }
            Decor.of(place).forEach { sl -> slots(sl, at[sl.id]) }
        } else placed.forEach { (sl, d) ->
            val tap = if (onSticker == null || d !is Sticker) Modifier else Modifier.clickable(role = Role.Button) { onSticker(d) }
            StickerImage(d, Modifier.centerAt(sl.x, sl.y).size(w * sl.size).then(tap).semantics { contentDescription = "${d.name}, ${sl.name}" })
        }
        if (place == Place.CONCERT) Scenery.audience(owned).forEachIndexed { i, s ->
            val a = WorldAnchors.seat(i)
            // Sunk into the seat a little, clear of the stars under the hall's bottom row of stones.
            StickerImage(s, Modifier.centerAt(a.x, a.y - 0.02f).size(w * 0.105f), tilt = 0f, shine = false)
        }
        if (stop != null) WorldAnchors.stops(place, stops).forEachIndexed { i, a ->
            Box(Modifier.centerAt(a.x, a.y)) { stop(i) }
        }
        overlay?.invoke(this)
    }
}

/** Every place one under the other, garden first: the Playground's whole map. */
@Composable
fun WorldMap(state: WorldState, modifier: Modifier = Modifier, stop: (@Composable (place: Place, i: Int) -> Unit)? = null,
             onSticker: ((Sticker) -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Place.entries.forEach { place ->
            PlaceScene(place, state, Modifier.fillMaxWidth(), stop = stop?.let { s -> { i -> s(place, i) } }, onSticker = onSticker)
        }
    }
}

// ---- drawing --------------------------------------------------------------------------------------------------

private val Ink = WorldInk

internal class Palette(night: Boolean) {
    val skyTop = if (night) Color(0xFF27304F) else Color(0xFFBFE3F7)
    val skyLow = if (night) Color(0xFF4B4466) else Color(0xFFEFF7EE)
    val hill = if (night) Color(0xFF3B5E45) else Color(0xFFB8DCA0)
    val grass = if (night) Color(0xFF4A7449) else Color(0xFF9FD38B)
    val grassDeep = if (night) Color(0xFF3D6340) else Color(0xFF86C376)
    val path = if (night) Color(0xFF8E7656) else Color(0xFFF2DCAE)
    val pathEdge = if (night) Color(0xFF6E5A42) else Color(0xFFDDBE86)
    val cloud = if (night) Color(0xFF5B5778) else Color.White
    val water = if (night) Color(0xFF3B6A8C) else Color(0xFF8ECBEA)
    val waterDeep = if (night) Color(0xFF2F5878) else Color(0xFF6FB6DF)
    val line = Ink.copy(alpha = if (night) 0.55f else 0.38f)
}

/**
 * The trail through a place's [count] stops ([Trail.path]): in on the left, the rows winding, out past the last row's end
 * (2.3: a short page, the fair's four, is one row across the scene).
 */
private fun trailPath(place: Place, size: Size, count: Int = Trail.STOPS): Path {
    val pts = Trail.path(place, count).map { Offset(it.x * size.width, it.y * size.height) }
    // Catmull-Rom through the points, as cubic Beziers.
    return Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 0 until pts.size - 1) {
            val p0 = pts[maxOf(0, i - 1)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(pts.size - 1, i + 2)]
            cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
        }
    }
}

private fun DrawScope.drawTrail(trail: Path, place: Place, night: Boolean) {
    val p = Palette(night)
    val w = size.width * 0.062f
    // 2.3: a measuring tape across the workshop floor, log slices under the big tree, glowing stones up the night hill, a
    // sandy midway with its little lights at the fair (ui/world/MorePlaces.kt).
    when (place) {
        Place.WORKSHOP -> return drawTapeTrail(trail, w, night)
        Place.TREEHOUSE -> return drawLogTrail(trail, w, night)
        Place.NIGHT_SKY -> return drawGlowTrail(trail, w, night)
        Place.FAIR -> return drawMidwayTrail(trail, w, night)
        else -> {}
    }
    if (place == Place.POND) {
        // A boardwalk of planks across the water and the lawn: dark wood under, the planks over it with a seam between each.
        val under = if (night) Color(0xFF5E4630) else Color(0xFF9A6A44)
        val plank = if (night) Color(0xFF8E7052) else Color(0xFFD9B07A)
        drawPath(trail, under, style = Stroke(w + size.width * 0.014f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(trail, plank, style = Stroke(w, join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(size.width * 0.024f, size.width * 0.006f))))
        return
    }
    if (place == Place.CONCERT) {
        // A red carpet with gold edges.
        drawPath(trail, Color(0xFFE2B85A), style = Stroke(w + size.width * 0.018f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(trail, if (night) Color(0xFF8E3440) else Color(0xFFB8434F), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return
    }
    drawPath(trail, p.line, style = Stroke(w + size.width * 0.012f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(trail, p.path, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // Stepping stones down the middle.
    drawPath(trail, p.pathEdge, style = Stroke(size.width * 0.018f, cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(size.width * 0.02f, size.width * 0.05f))))
}

/** The sky behind a scene: its gradient, and at dusk the moon. */
internal fun DrawScope.skyBase(p: Palette, night: Boolean, horizon: Float) {
    drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyLow), endY = size.height * horizon), size = Size(size.width, size.height * horizon + 2f))
    if (night) {
        val moon = Offset(size.width * 0.34f, size.height * 0.11f)
        drawCircle(Color(0xFFFFF3C4).copy(alpha = 0.18f), size.width * 0.09f, moon)
        drawCircle(Color(0xFFFFF0B8), size.width * 0.045f, moon)
        drawCircle(p.skyTop, size.width * 0.04f, moon + Offset(size.width * 0.022f, -size.width * 0.012f))
    }
}

/** What moves in the sky: stars twinkling at dusk, two clouds drifting across. */
internal fun DrawScope.skyLife(p: Palette, night: Boolean, t: Float, horizon: Float) {
    if (night) {
        val r = java.util.Random(11)
        repeat(26) {
            val x = r.nextFloat() * size.width; val y = r.nextFloat() * size.height * horizon * 0.85f
            val tw = 0.5f + 0.5f * sin((t * 8f + it * 0.37f) * 2f * PI.toFloat())
            drawCircle(Color.White, size.width * (0.0035f + 0.003f * (it % 3)), Offset(x, y), alpha = 0.35f + 0.5f * tw)
        }
    }
    // Two clouds drifting slowly across.
    for ((i, at) in listOf(0.18f, 0.62f).withIndex()) {
        val x = ((at + t * (0.5f + 0.3f * i)) % 1.3f - 0.15f) * size.width
        val y = size.height * (0.07f + 0.1f * i)
        val s = size.width * (0.055f - 0.01f * i)
        val cloud = Path().apply {
            addOval(Rect(Offset(x, y), s)); addOval(Rect(Offset(x + s * 1.1f, y - s * 0.4f), s * 1.25f)); addOval(Rect(Offset(x + s * 2.2f, y), s * 0.95f))
            addRoundRect(androidx.compose.ui.geometry.RoundRect(x - s * 0.2f, y, x + s * 2.6f, y + s * 0.9f, CornerRadius(s * 0.45f)))
        }
        drawPath(cloud, p.cloud, alpha = if (night) 0.55f else 0.92f)
    }
}

internal fun DrawScope.ground(p: Palette, top: Float) {
    val h = size.height; val w = size.width
    // Far hills, then the lawn with a soft wavy edge.
    val hills = Path().apply {
        moveTo(0f, h * (top + 0.02f))
        quadraticTo(w * 0.18f, h * (top - 0.10f), w * 0.42f, h * (top - 0.01f))
        quadraticTo(w * 0.68f, h * (top - 0.12f), w, h * (top - 0.03f))
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(hills, p.hill)
    drawPath(hills, p.line, style = Stroke(w * 0.005f))
    val lawn = Path().apply {
        moveTo(0f, h * (top + 0.05f))
        quadraticTo(w * 0.3f, h * (top + 0.01f), w * 0.55f, h * (top + 0.04f))
        quadraticTo(w * 0.8f, h * (top + 0.07f), w, h * (top + 0.03f))
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(lawn, Brush.verticalGradient(listOf(p.grass, p.grassDeep), startY = h * top, endY = h))
    drawPath(lawn, p.line, style = Stroke(w * 0.005f))
    // A few tufts.
    val r = java.util.Random(5)
    repeat(14) {
        val x = r.nextFloat() * w; val y = h * (top + 0.08f + r.nextFloat() * (0.9f - top))
        val s = w * 0.012f
        val tuft = Path().apply { moveTo(x - s, y); lineTo(x - s * 0.4f, y - s * 1.6f); lineTo(x, y); lineTo(x + s * 0.5f, y - s * 2f); lineTo(x + s, y) }
        drawPath(tuft, p.line, style = Stroke(w * 0.004f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

internal fun DrawScope.outlined(path: Path, fill: Color, line: Float, ink: Color = Ink) {
    drawPath(path, fill)
    drawPath(path, ink, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Where the garden's dog house stands (the middle of its floor) and how wide it is, as shares of the scene. */
private val HOUSE_BASE = Offset(0.28f, 0.50f)
private const val HOUSE_W = 0.27f
private fun Offset.toOffset(size: Size) = Offset(x * size.width, y * size.height)

/** The garden's still parts: lawn, hills, the tree, the dog house in its stage, the nest when an egg waits. */
private fun DrawScope.drawGarden(stage: Int, night: Boolean, egg: Boolean) {
    val p = Palette(night)
    ground(p, top = 0.42f)
    val w = size.width; val h = size.height
    // A round tree on the right.
    val trunk = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.845f, h * 0.30f, w * 0.885f, h * 0.47f, CornerRadius(w * 0.01f))) }
    outlined(trunk, Color(0xFFB98454), w * 0.006f)
    val crown = Path().apply {
        addOval(Rect(Offset(w * 0.82f, h * 0.26f), w * 0.075f)); addOval(Rect(Offset(w * 0.90f, h * 0.25f), w * 0.07f))
        addOval(Rect(Offset(w * 0.865f, h * 0.19f), w * 0.08f))
    }
    drawPath(crown, Ink, style = Stroke(w * 0.012f))
    drawPath(crown, if (night) Color(0xFF3F7048) else Color(0xFF7CC173))
    drawCircle(Color.White.copy(alpha = 0.18f), w * 0.03f, Offset(w * 0.845f, h * 0.17f))
    drawDogHouse(stage, HOUSE_BASE.toOffset(size), w * HOUSE_W, night, life = false)
    if (egg) drawNest(Offset(WorldAnchors.EGG.x * w, WorldAnchors.EGG.y * h + w * 0.02f), w * 0.13f)
}

/**
 * The dog house, standing on [base] (centre of its floor), [width] wide. Stage 1 plain wood; 2 a red roof, a paw over
 * the door and a flower box; 3 also a chimney with smoke, a flag on top and a porch light (glowing at dusk).
 */
fun DrawScope.drawDogHouse(stage: Int, base: Offset, width: Float, night: Boolean, t: Float = 0f, life: Boolean = true) {
    val w = width; val line = w * 0.028f
    val left = base.x - w / 2f; val right = base.x + w / 2f
    val wallTop = base.y - w * 0.58f
    val peak = Offset(base.x, wallTop - w * 0.36f)
    if (stage >= 3) {
        // The chimney behind the roof (its smoke is drawHouseLife's).
        val ch = Path().apply { addRect(Rect(base.x + w * 0.16f, peak.y + w * 0.02f, base.x + w * 0.29f, wallTop - w * 0.02f)) }
        outlined(ch, Color(0xFFC9705F), line)
    }
    val walls = Path().apply { moveTo(left, base.y); lineTo(left, wallTop); lineTo(peak.x, peak.y + w * 0.06f); lineTo(right, wallTop); lineTo(right, base.y); close() }
    outlined(walls, Color(0xFFEBC890), line)
    // Planks.
    for (k in 1..3) {
        val y = wallTop + (base.y - wallTop) * k / 4f
        drawLine(Color(0xFFD2A86E), Offset(left + line, y), Offset(right - line, y), line * 0.6f)
    }
    // The roof: two slabs, red from stage 2 with a row of shingles.
    val roofColour = if (stage >= 2) Color(0xFFE0605A) else Color(0xFFB07A4A)
    val over = w * 0.1f
    val roof = Path().apply {
        moveTo(left - over, wallTop + w * 0.05f); lineTo(peak.x, peak.y - w * 0.02f); lineTo(right + over, wallTop + w * 0.05f)
        lineTo(right + over - w * 0.05f, wallTop + w * 0.12f); lineTo(peak.x, peak.y + w * 0.08f); lineTo(left - over + w * 0.05f, wallTop + w * 0.12f); close()
    }
    outlined(roof, roofColour, line)
    if (stage >= 2) drawPath(Path().apply {
        moveTo(left - over * 0.4f, wallTop + w * 0.05f); lineTo(peak.x, peak.y + w * 0.035f); lineTo(right + over * 0.4f, wallTop + w * 0.05f)
    }, Color(0xFFC24A46), style = Stroke(line * 0.8f, cap = StrokeCap.Round))
    // The door: a dark arch.
    val door = Path().apply {
        val dw = w * 0.36f; val dl = base.x - dw / 2f; val dt = base.y - w * 0.42f
        moveTo(dl, base.y); lineTo(dl, dt + dw / 2f); arcTo(Rect(dl, dt, dl + dw, dt + dw), 180f, 180f, false); lineTo(dl + dw, base.y); close()
    }
    outlined(door, Color(0xFF4A3A36), line)
    if (stage >= 2) {
        // A paw over the door.
        val c = Offset(base.x, wallTop + w * 0.03f)
        val pr = w * 0.035f
        drawOval(Color(0xFFC24A46), Offset(c.x - pr, c.y - pr * 0.4f), Size(pr * 2f, pr * 1.6f))
        listOf(-1.2f to -1.1f, -0.4f to -1.6f, 0.4f to -1.6f, 1.2f to -1.1f).forEach { (dx, dy) -> drawCircle(Color(0xFFC24A46), pr * 0.42f, Offset(c.x + dx * pr, c.y + dy * pr)) }
        // A flower box in front, on the right.
        val box = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(right - w * 0.05f, base.y - w * 0.09f, right + w * 0.3f, base.y + w * 0.02f, CornerRadius(w * 0.02f))) }
        listOf(Color(0xFFF4A8B6), Color(0xFFFCD860), Color(0xFFAA8CDC)).forEachIndexed { i, fc ->
            val fx = right + w * (0.01f + 0.11f * i); val fy = base.y - w * 0.15f
            drawLine(Color(0xFF6FAE63), Offset(fx, fy), Offset(fx, base.y - w * 0.08f), line * 0.8f)
            drawCircle(Ink, w * 0.05f, Offset(fx, fy)); drawCircle(fc, w * 0.038f, Offset(fx, fy)); drawCircle(Color(0xFFF6A654), w * 0.014f, Offset(fx, fy))
        }
        outlined(box, Color(0xFFB07A4A), line)
    }
    if (stage >= 3) {
        // The flag on the peak (waving: drawHouseLife) and a porch light beside the door.
        if (life) drawHouseLife(stage, base, width, t)
        val lamp = Offset(base.x - w * 0.3f, base.y - w * 0.36f)
        if (night) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A0).copy(alpha = 0.7f), Color.Transparent), lamp, w * 0.28f), w * 0.28f, lamp)
        drawLine(Ink, lamp + Offset(0f, -w * 0.06f), lamp + Offset(0f, -w * 0.1f), line * 0.7f)
        drawCircle(Ink, w * 0.05f, lamp)
        drawCircle(if (night) Color(0xFFFFF1B0) else Color(0xFFFCE38A), w * 0.036f, lamp)
    }
}

/** The moving part of a stage-3 dog house: smoke curling up from the chimney and the flag waving. */
fun DrawScope.drawHouseLife(stage: Int, base: Offset, width: Float, t: Float) {
    if (stage < 3) return
    val w = width; val line = w * 0.028f
    val wallTop = base.y - w * 0.58f
    val peak = Offset(base.x, wallTop - w * 0.36f)
    for (k in 0 until 3) {
        val f = (t * 6f + k / 3f) % 1f
        drawCircle(Color.White, w * (0.035f + 0.03f * f), Offset(base.x + w * (0.225f + 0.05f * sin(f * 6f)), peak.y - w * (0.02f + 0.3f * f)), alpha = 0.7f * (1f - f))
    }
    val pole = Offset(peak.x, peak.y - w * 0.02f)
    drawLine(Ink, pole, pole - Offset(0f, w * 0.3f), line)
    val wave = sin(t * 2f * PI.toFloat() * 10f) * w * 0.02f
    val flag = Path().apply { moveTo(pole.x, pole.y - w * 0.3f); lineTo(pole.x + w * 0.2f, pole.y - w * 0.25f + wave); lineTo(pole.x, pole.y - w * 0.19f); close() }
    outlined(flag, Color(0xFFE38E5A), line * 0.8f)
}

/** Ripples moving out across the pond. */
private fun DrawScope.ripples(t: Float) {
    val w = size.width; val h = size.height
    for (k in 0 until 3) {
        val f = (t * 4f + k / 3f) % 1f
        val c = Offset(w * (0.30f + 0.25f * k), h * (0.50f + 0.05f * (k % 2)))
        drawOval(Color.White, Offset(c.x - w * 0.08f * f, c.y - w * 0.02f * f), Size(w * 0.16f * f, w * 0.04f * f), alpha = 0.6f * (1f - f), style = Stroke(w * 0.004f))
    }
}

/** The pond's still parts: the far trees, the lawn, the water with its lily pads and reeds. */
private fun DrawScope.drawPond(night: Boolean) {
    val p = Palette(night)
    val w = size.width; val h = size.height
    // A line of round trees on the far side.
    val trees = Path().apply { for (k in 0 until 9) addOval(Rect(Offset(w * (0.03f + k * 0.12f), h * (0.33f - 0.02f * (k % 2))), w * 0.075f)) }
    // Outline first, wide, then the fill over it: only the outer edge of the row shows.
    drawPath(trees, p.line, style = Stroke(w * 0.01f))
    drawPath(trees, if (night) Color(0xFF345A40) else Color(0xFF8FCB84))
    ground(p, top = 0.36f)
    // The pond: a soft blob of water with ripples moving out and a few lily pads.
    val pond = Path().apply {
        moveTo(w * 0.08f, h * 0.55f)
        cubicTo(w * 0.06f, h * 0.40f, w * 0.40f, h * 0.38f, w * 0.55f, h * 0.41f)
        cubicTo(w * 0.78f, h * 0.38f, w * 0.97f, h * 0.44f, w * 0.94f, h * 0.57f)
        cubicTo(w * 0.92f, h * 0.71f, w * 0.62f, h * 0.73f, w * 0.48f, h * 0.71f)
        cubicTo(w * 0.24f, h * 0.73f, w * 0.10f, h * 0.67f, w * 0.08f, h * 0.55f)
        close()
    }
    drawPath(pond, Brush.verticalGradient(listOf(p.water, p.waterDeep), startY = h * 0.39f, endY = h * 0.72f))
    drawPath(pond, p.line, style = Stroke(w * 0.008f))
    for ((x, y, r) in listOf(Triple(0.18f, 0.62f, 0.05f), Triple(0.72f, 0.66f, 0.045f), Triple(0.86f, 0.50f, 0.04f))) {
        val c = Offset(w * x, h * y)
        val pad = Path().apply { addOval(Rect(c.x - w * r, c.y - w * r * 0.5f, c.x + w * r, c.y + w * r * 0.5f)) }
        outlined(pad, if (night) Color(0xFF4E8A55) else Color(0xFF79C26E), w * 0.004f)
        drawLine(p.water, c, c + Offset(w * r, -w * r * 0.15f), w * 0.008f)
    }
    // Reeds at both ends.
    for ((x, lean) in listOf(0.04f to -1f, 0.07f to 1f, 0.95f to 1f, 0.92f to -1f)) {
        val b = Offset(w * x, h * 0.60f)
        val top = b + Offset(lean * w * 0.02f, -h * 0.17f)
        drawLine(if (night) Color(0xFF4F7A48) else Color(0xFF6FAE63), b, top, w * 0.008f, StrokeCap.Round)
        drawRoundRect(Color(0xFF9A6A44), Offset(top.x - w * 0.009f, top.y), Size(w * 0.018f, h * 0.05f), CornerRadius(w * 0.009f))
    }
}

/** The concert hall, all still: the wall, the stage in its spotlight, the curtains, the seats. */
private fun DrawScope.drawConcert(night: Boolean) {
    val w = size.width; val h = size.height
    val dim = if (night) 0.8f else 1f
    fun c(argb: Long) = Color(argb).let { if (night) Color(it.red * dim, it.green * dim, it.blue * dim) else it }
    // The back wall, the stage floor, the hall floor.
    drawRect(Brush.verticalGradient(listOf(c(0xFF6E3B5A), c(0xFF4C2A44)), endY = h * 0.5f), size = Size(w, h * 0.5f))
    drawRect(c(0xFF3C2436), Offset(0f, h * 0.5f), Size(w, h * 0.5f))
    // Wall lamps.
    for (x in listOf(0.2f, 0.8f)) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE3A0).copy(alpha = 0.35f), Color.Transparent), Offset(w * x, h * 0.2f), w * 0.12f), w * 0.12f, Offset(w * x, h * 0.2f))
    val stage = Path().apply { moveTo(w * 0.04f, h * 0.47f); lineTo(w * 0.96f, h * 0.47f); lineTo(w, h * 0.54f); lineTo(0f, h * 0.54f); close() }
    outlined(stage, c(0xFFD39A62), w * 0.006f)
    for (k in 1..5) drawLine(c(0xFFB98454), Offset(w * k / 6f, h * 0.47f), Offset(w * (k / 6f - 0.02f + 0.04f * k / 6f), h * 0.54f), w * 0.004f)
    drawRect(c(0xFF8A5A3A), Offset(0f, h * 0.54f), Size(w, h * 0.02f))
    // The spotlight's cone on the middle of the stage.
    val glow = 0.24f
    val cone = Path().apply { moveTo(w * 0.47f, 0f); lineTo(w * 0.53f, 0f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.34f, h * 0.5f); close() }
    drawPath(cone, Brush.verticalGradient(listOf(Color(0xFFFFF0C0).copy(alpha = glow * 0.6f), Color(0xFFFFF0C0).copy(alpha = glow))))
    drawOval(Color(0xFFFFF0C0).copy(alpha = glow * 1.4f), Offset(w * 0.34f, h * 0.47f), Size(w * 0.32f, h * 0.05f))
    // The curtains, gathered at the sides, and the valance across the top.
    for (side in listOf(-1f, 1f)) {
        val x0 = if (side < 0) 0f else w
        val x1 = x0 - side * w * 0.17f
        val curtain = Path().apply {
            moveTo(x0, 0f); lineTo(x1, 0f); quadraticTo(x0 - side * w * 0.07f, h * 0.25f, x0 - side * w * 0.12f, h * 0.48f)
            lineTo(x0, h * 0.48f); close()
        }
        outlined(curtain, c(0xFFD2505E), w * 0.006f)
        for (k in 1..3) drawLine(c(0xFFAA3A4C), Offset(x0 - side * w * 0.04f * k, h * 0.02f), Offset(x0 - side * w * 0.035f * k, h * 0.46f), w * 0.006f)
        drawCircle(c(0xFFE2B85A), w * 0.016f, Offset(x0 - side * w * 0.085f, h * 0.30f))
    }
    val valance = Path().apply {
        moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.05f)
        for (k in 6 downTo 0) quadraticTo(w * (k + 0.5f) / 7f, h * 0.085f, w * k / 7f, h * 0.05f)
        close()
    }
    outlined(valance, c(0xFFB8434F), w * 0.006f)
    drawLine(c(0xFFE2B85A), Offset(0f, h * 0.012f), Offset(w, h * 0.012f), w * 0.01f)
    // The audience's seats, one per animal there could be.
    for (i in 0 until Scenery.SEATS) {
        val a = WorldAnchors.seat(i)
        val cx = a.x * w; val top = a.y * h - w * 0.01f
        val back = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(cx - w * 0.042f, top, cx + w * 0.042f, top + w * 0.075f, CornerRadius(w * 0.02f, w * 0.02f))) }
        outlined(back, c(0xFFC4505E), w * 0.005f)
        drawRect(c(0xFF9C3A4A), Offset(cx - w * 0.042f, top + w * 0.06f), Size(w * 0.084f, w * 0.018f))
    }
}

/** The garden's dog house on its own (the reward screen when it grows), [width] wide, standing in a patch of lawn. */
@Composable
fun DogHouse(stage: Int, modifier: Modifier = Modifier) {
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val t = rememberSceneClock()
    Spacer(modifier.clearAndSetSemantics { contentDescription = "The dog house, stage $stage" }.drawWithCache {
        onDrawBehind {
            val p = Palette(night)
            drawOval(p.grass, Offset(size.width * 0.05f, size.height * 0.80f), Size(size.width * 0.9f, size.height * 0.16f))
            drawDogHouse(stage, Offset(size.width * 0.45f, size.height * 0.86f), size.width * 0.5f, night, t.value)
        }
    })
}

/** "7 of 10": the stickers of a place in its scene. */
fun Place.sceneLabel(state: WorldState): String = "${Scenery.items(this, state.history.owned).size} of ${stickers.size}"

/**
 * A slow clock for the scenes (clouds, smoke, ripples, the flag), 0..1 over [periodMs]. It ticks 20 times a second,
 * not every frame: the drift is slow, and a scene redrawn at 20 fps costs a third of one at 60. Still with animations off
 * or when not [running] (a scene with nothing that moves).
 */
@Composable
fun rememberSceneClock(periodMs: Long = 24_000L, running: Boolean = true): State<Float> {
    val still = reducedMotion()
    val s = remember { mutableFloatStateOf(0.3f) }
    if (!still && running) LaunchedEffect(periodMs) {
        val t0 = SystemClock.uptimeMillis() - (0.3f * periodMs).toLong()
        while (true) {
            s.floatValue = ((SystemClock.uptimeMillis() - t0) % periodMs) / periodMs.toFloat()
            delay(50)
        }
    }
    return s
}
