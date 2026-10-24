package com.bigpapituna.sharpears.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.world.Outfit
import com.bigpapituna.sharpears.engine.world.WearItem
import com.bigpapituna.sharpears.engine.world.WearSlot
import com.bigpapituna.sharpears.ui.pup.rememberPupBitmap
import com.bigpapituna.sharpears.ui.pup.Pose

/**
 * What the pup wears, for every Pup in the composition (the integrator provides the open profile's outfit from
 * its WorldStore). Drawn only over the sitting base pose, [com.bigpapituna.sharpears.ui.pup.Pose.WEAR_BASE].
 */
val LocalPupOutfit = compositionLocalOf { Outfit.NONE }

/** The pup's outline brown, shared with the stickers and the world scenes. */
val WorldInk = Color(0xFF54321E)

/**
 * The wardrobe (2.5: the six of 2.1 and fifty more), drawn in code in the pup's style (a thick brown outline, flat
 * colour, a soft highlight) in the pixel space of res/drawable-nodpi/pup_wear_base.webp ([BASE_W] x [BASE_H]), measured
 * on that image: eyes at (227, 212) and (377, 168), the sweatband from (142, 130) rising about 18 degrees to (420, 50),
 * the nose at (320, 235), the chin at (347, 363), the cord dipping to the whistle at (353, 427), the chest from (180, 370)
 * to (455, 350) down to the haunches at y 560, the front legs at x 197 to 313 and 360 to 460, the front paws at y 640 to
 * 707, the back paws at (87 to 180, 590 to 700) and (487 to 560, 567 to 673).
 *
 * Every item has a front layer (over the pup) and some a back layer (under him: capes, wings, the lion's mane, a
 * backpack), so things can reach behind him. Painted versions (art/GEMINI-BRIEF.md B and B2) can replace these later.
 */
object Wear {
    const val BASE_W = 574f
    const val BASE_H = 720f

    /** Where an item sits in base-image pixels: what its icon shows, and how far a hat reaches over his head (top < 0). */
    fun box(item: WearItem): Rect = when (item) {
        WearItem.SUNGLASSES, WearItem.STAR_GLASSES, WearItem.HEART_GLASSES, WearItem.HERO_MASK -> Rect(140f, 100f, 465f, 290f)
        WearItem.MONOCLE -> Rect(290f, 90f, 470f, 330f)
        WearItem.EYE_PATCH -> Rect(130f, 90f, 450f, 300f)
        WearItem.FRECKLES, WearItem.WHISKERS -> Rect(120f, 160f, 500f, 360f)
        WearItem.CARROT_NOSE -> Rect(220f, 160f, 470f, 330f)
        WearItem.TOP_HAT -> Rect(150f, -150f, 430f, 110f)
        WearItem.PARTY_HAT -> Rect(200f, -170f, 390f, 90f)
        WearItem.HEADPHONES -> Rect(60f, -60f, 530f, 290f)
        WearItem.WIZARD_HAT -> Rect(110f, -230f, 470f, 120f)
        WearItem.CHEF_HAT -> Rect(130f, -170f, 450f, 130f)
        WearItem.COWBOY_HAT, WearItem.PIRATE_HAT -> Rect(80f, -110f, 510f, 160f)
        WearItem.CROWN, WearItem.FLOWER_CROWN -> Rect(120f, -90f, 450f, 200f)
        WearItem.HALO -> Rect(150f, -110f, 450f, 60f)
        WearItem.ANTENNAE -> Rect(150f, -140f, 480f, 140f)
        WearItem.KNIGHT_HELMET, WearItem.FIRE_HELMET -> Rect(100f, -120f, 500f, 230f)
        WearItem.SPACE_HELMET -> Rect(10f, -80f, 590f, 470f)
        WearItem.DINO_CAP, WearItem.BEANIE -> Rect(100f, -110f, 500f, 230f)
        WearItem.HEAD_MIRROR -> Rect(110f, 20f, 440f, 250f)
        WearItem.BAND_PINK, WearItem.BAND_BLUE, WearItem.BAND_GREEN, WearItem.BAND_YELLOW, WearItem.BAND_PURPLE -> Rect(120f, 20f, 450f, 220f)
        WearItem.BOW_TIE -> Rect(250f, 340f, 440f, 450f)
        WearItem.SCARF -> Rect(170f, 300f, 470f, 600f)
        WearItem.BANDANA -> Rect(170f, 320f, 470f, 520f)
        WearItem.LION -> Rect(-20f, -90f, 600f, 470f)
        WearItem.CAPE_RED, WearItem.CAPE_BLUE -> Rect(20f, 300f, 560f, 720f)
        WearItem.TUTU, WearItem.BALLERINA, WearItem.PRINCESS, WearItem.RAINBOW_DRESS -> Rect(50f, 330f, 560f, 720f)
        WearItem.MERMAID -> Rect(60f, 330f, 600f, 720f)
        WearItem.BEE, WearItem.DINO -> Rect(20f, 300f, 570f, 720f)
        WearItem.BOOTS, WearItem.SKATES, WearItem.SLIPPERS -> Rect(180f, 540f, 480f, 730f)
        WearItem.WINGS -> Rect(-60f, 230f, 640f, 640f)
        WearItem.GUITAR -> Rect(170f, 260f, 620f, 660f)
        WearItem.BACKPACK, WearItem.ROCKET_PACK -> Rect(50f, 290f, 530f, 660f)
        else -> Rect(90f, 320f, 500f, 720f)        // the suits and coats
    }

    /** Items drawn first in front: the body, then feet and back (straps over a coat), the face, the head. */
    private val FRONT = listOf(WearSlot.BODY, WearSlot.BACK, WearSlot.FACE, WearSlot.HEAD)
    /** Behind him: a cape or a mane, then wings or a pack on top of it. */
    private val BEHIND = listOf(WearSlot.BODY, WearSlot.BACK)

    fun front(outfit: Outfit): List<WearItem> = FRONT.mapNotNull(outfit::inSlot)
    fun behind(outfit: Outfit): List<WearItem> = BEHIND.mapNotNull(outfit::inSlot).filter { it in BACK_LAYER }

    /** Items with something under the pup. */
    val BACK_LAYER = setOf(WearItem.CAPE_RED, WearItem.CAPE_BLUE, WearItem.LION, WearItem.WINGS, WearItem.BACKPACK, WearItem.GUITAR,
        WearItem.ROCKET_PACK, WearItem.BEE, WearItem.DINO, WearItem.MERMAID, WearItem.HERO_SUIT, WearItem.PRINCESS)

    /** How far above his head the outfit reaches, in base pixels (0 when nothing does). */
    fun above(outfit: Outfit): Float = -minOf(0f, outfit.items.minOfOrNull { box(it).top } ?: 0f)
}

/** The parts of [outfit] that go under the pup (draw before his image). */
fun DrawScope.drawOutfitBehind(outfit: Outfit, alpha: Float = 1f) {
    if (outfit.isEmpty || alpha <= 0f) return
    for (item in Wear.behind(outfit)) wearBehind(item, alpha)
}

/** Everything in [outfit] that goes over the pup, body first, then feet and back, face, head. */
fun DrawScope.drawOutfit(outfit: Outfit, alpha: Float = 1f) {
    if (outfit.isEmpty || alpha <= 0f) return
    for (item in Wear.front(outfit)) drawWear(item, alpha)
}

/** One item's back layer, in base-image pixels. */
fun DrawScope.wearBehind(item: WearItem, alpha: Float = 1f) {
    if (item !in Wear.BACK_LAYER) return
    when (item.slot) {
        WearSlot.BODY -> bodyBehind(item, alpha)
        WearSlot.BACK -> backBehind(item, alpha)
        else -> {}
    }
}

/** One item's front layer, in base-image pixels (the caller scales the image's space onto the pup). */
fun DrawScope.drawWear(item: WearItem, alpha: Float = 1f) {
    when (item.slot) {
        WearSlot.HEAD -> headFront(item, alpha)
        WearSlot.FACE -> faceFront(item, alpha)
        WearSlot.BODY -> bodyFront(item, alpha)
        WearSlot.BACK -> backFront(item, alpha)
    }
}

// ---- shared drawing ---------------------------------------------------------------------------------------------

/** A shape in the pup's style: the brown outline ([line] each side), then the fill. */
internal fun DrawScope.wearShape(path: Path, fill: Color, alpha: Float, line: Float = 8f) {
    drawPath(path, WorldInk, alpha = alpha, style = Stroke(line * 2, join = StrokeJoin.Round, cap = StrokeCap.Round))
    drawPath(path, fill, alpha = alpha)
}

/** A brown line, round-ended. */
internal fun DrawScope.inkLine(path: Path, w: Float, alpha: Float, color: Color = WorldInk) =
    drawPath(path, color, alpha = alpha, style = Stroke(w, join = StrokeJoin.Round, cap = StrokeCap.Round))

/** A soft white highlight streak. */
internal fun DrawScope.shine(x: Float, y: Float, w: Float, h: Float, alpha: Float) =
    drawOval(Color.White.copy(alpha = 0.45f), Offset(x, y), Size(w, h), alpha = alpha)

internal fun path(block: Path.() -> Unit): Path = Path().apply(block)

/**
 * An item on the pup, for the dress-up tiles and the reveal: his base image cropped to the item's box (so a kid sees
 * where it goes), the item on him. Locked: the pup faded and the item a soft grey shape.
 */
@Composable
fun WearIcon(item: WearItem, modifier: Modifier = Modifier, locked: Boolean = false) {
    val base = rememberPupBitmap(Pose.WEAR_BASE)
    Canvas(modifier.clearAndSetSemantics { }) {
        val b = Wear.box(item).inflate(14f)
        val k = minOf(size.width / b.width, size.height / b.height)
        clipRect {
            translate(size.width / 2f - b.center.x * k, size.height / 2f - b.center.y * k) {
                scale(k, Offset.Zero) {
                    val a = if (locked) 0.3f else 1f
                    wearBehind(item, a)
                    drawImage(base, IntOffset.Zero, IntSize(base.width, base.height), dstSize = IntSize(Wear.BASE_W.toInt(), Wear.BASE_H.toInt()),
                        alpha = if (locked) 0.3f else 1f, colorFilter = if (locked) ColorFilter.tint(Color(0xFFB8B0A6), BlendMode.Modulate) else null)
                    drawWear(item, a)
                }
            }
        }
    }
}
