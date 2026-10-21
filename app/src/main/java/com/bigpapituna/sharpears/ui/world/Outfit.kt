package com.bigpapituna.sharpears.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bigpapituna.sharpears.engine.world.Outfit
import com.bigpapituna.sharpears.engine.world.WearItem

/**
 * What the pup wears, for every Pup in the composition (the integrator provides the open profile's outfit from
 * its WorldStore). Drawn only over the sitting base pose, [com.bigpapituna.sharpears.ui.Pose.WEAR_BASE].
 */
val LocalPupOutfit = compositionLocalOf { Outfit.NONE }

/** The pup's outline brown, shared with the stickers and the world scenes. */
val WorldInk = Color(0xFF54321E)

/**
 * The accessories, drawn in code in the pup's style (a thick brown outline, flat colour, a soft highlight) in the
 * pixel space of res/drawable-nodpi/pup_wear_base.webp ([BASE_W] x [BASE_H]), measured on that image: eyes at
 * (227, 220) and (377, 170), the sweatband rising 15 degrees to the right, the chin at (347, 363), the cord dipping
 * to the whistle at (350, 427). Painted pup-wear art (GEMINI-BRIEF B) can replace these later.
 */
object Wear {
    const val BASE_W = 574f
    const val BASE_H = 720f

    /** Where an item sits in base-image pixels: for its icon on its own. */
    fun box(item: WearItem): Rect = when (item) {
        WearItem.SUNGLASSES -> Rect(150f, 120f, 455f, 270f)
        WearItem.TOP_HAT -> Rect(150f, -150f, 430f, 110f)
        WearItem.PARTY_HAT -> Rect(200f, -170f, 390f, 90f)
        WearItem.HEADPHONES -> Rect(60f, -60f, 530f, 290f)
        WearItem.BOW_TIE -> Rect(250f, 340f, 440f, 450f)
        WearItem.SCARF -> Rect(170f, 300f, 470f, 600f)
        else -> Rect(0f, 0f, BASE_W, BASE_H)
    }
}

private val Black = Color(0xFF2E2A2B)
private val BlackHi = Color(0xFF55504F)
private val BowRed = Color(0xFFE0605A)
private val PartyPurple = Color(0xFFA98BE0)
private val Mint = Color(0xFF8FD6C2)
private val MintDeep = Color(0xFF5FB8A2)
private val CupPink = Color(0xFFF4A8B6)
private val Dot = Color(0xFFFCE38A)
private val Stripes = listOf(Color(0xFFE86054), Color(0xFFF6A654), Color(0xFFFCD860), Color(0xFF80C470), Color(0xFF74AEE6), Color(0xFFAA8CDC))

/** Everything in [outfit], neck first, then eyes, then head (a hat sits on top of the headphones' place). */
fun DrawScope.drawOutfit(outfit: Outfit, alpha: Float = 1f) {
    if (outfit.isEmpty || alpha <= 0f) return
    for (item in outfit.items.sortedBy { it.slot.ordinal * -1 }) drawWear(item, alpha)
}

/** One item, in base-image pixels (the caller scales the image's space onto the pup). */
fun DrawScope.drawWear(item: WearItem, alpha: Float = 1f) {
    val line = 8f
    fun outline(path: Path, w: Float = line) = drawPath(path, WorldInk, alpha = alpha, style = Stroke(w * 2, join = StrokeJoin.Round, cap = StrokeCap.Round))
    when (item) {
        WearItem.SUNGLASSES -> rotate(-18.4f, Offset(302f, 195f)) {
            translate(302f, 195f) {
                // Arms toward the ears, the bridge, then two round dark lenses with a streak of light.
                val arms = Path().apply { moveTo(-125f, -8f); lineTo(-176f, -22f); moveTo(125f, -8f); lineTo(172f, -26f) }
                drawPath(arms, WorldInk, alpha = alpha, style = Stroke(14f, cap = StrokeCap.Round))
                val bridge = Path().apply { moveTo(-30f, -10f); quadraticTo(0f, -32f, 30f, -10f) }
                drawPath(bridge, WorldInk, alpha = alpha, style = Stroke(14f, cap = StrokeCap.Round))
                for (x in listOf(-79f, 79f)) {
                    drawOval(WorldInk, Offset(x - 56f, -50f), Size(112f, 100f), alpha = alpha)
                    drawOval(Brush.verticalGradient(listOf(Color(0xFF4A4D63), Color(0xFF1E1D26)), startY = -42f, endY = 42f),
                        Offset(x - 46f, -40f), Size(92f, 80f), alpha = alpha)
                    rotate(-35f, Offset(x - 12f, -14f)) {
                        drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(x - 30f, -22f), Size(36f, 12f), CornerRadius(6f), alpha = alpha)
                    }
                }
            }
        }
        WearItem.TOP_HAT -> rotate(-15f, Offset(290f, 58f)) {
            translate(290f, 58f) {
                val crown = Path().apply { moveTo(-66f, -8f); lineTo(-72f, -150f); quadraticTo(0f, -164f, 72f, -150f); lineTo(66f, -8f); close() }
                val brim = Path().apply { addOval(Rect(-118f, -22f, 118f, 22f)) }
                outline(brim); outline(crown)
                drawPath(brim, Black, alpha = alpha)
                drawPath(crown, Black, alpha = alpha)
                drawRect(BowRed, Offset(-70f, -52f), Size(140f, 30f), alpha = alpha)
                drawLine(WorldInk, Offset(-70f, -52f), Offset(70f, -52f), 5f, alpha = alpha)
                drawLine(WorldInk, Offset(-68f, -22f), Offset(68f, -22f), 5f, alpha = alpha)
                drawRoundRect(BlackHi, Offset(-52f, -140f), Size(18f, 76f), CornerRadius(9f), alpha = alpha)
                drawOval(BlackHi, Offset(-92f, -14f), Size(60f, 12f), alpha = alpha)
            }
        }
        WearItem.PARTY_HAT -> rotate(-22f, Offset(298f, 52f)) {
            translate(298f, 52f) {
                val cone = Path().apply { moveTo(-74f, 0f); lineTo(6f, -196f); lineTo(74f, 0f); quadraticTo(0f, 22f, -74f, 0f); close() }
                outline(cone)
                drawPath(cone, PartyPurple, alpha = alpha)
                // Polka dots, then a white trim along the bottom and a pompom on top.
                for ((x, y, r) in listOf(Triple(-24f, -30f, 12f), Triple(28f, -48f, 11f), Triple(-6f, -92f, 10f), Triple(14f, -140f, 8f), Triple(-44f, -6f, 8f), Triple(48f, -10f, 9f))) {
                    drawCircle(Dot, r, Offset(x, y), alpha = alpha)
                }
                val trim = Path().apply { moveTo(-78f, 0f); quadraticTo(0f, 24f, 78f, 0f) }
                drawPath(trim, WorldInk, alpha = alpha, style = Stroke(26f, cap = StrokeCap.Round))
                drawPath(trim, Color.White, alpha = alpha, style = Stroke(12f, cap = StrokeCap.Round))
                drawCircle(WorldInk, 26f, Offset(6f, -200f), alpha = alpha)
                drawCircle(Color.White, 18f, Offset(6f, -200f), alpha = alpha)
            }
        }
        WearItem.HEADPHONES -> {
            val band = Path().apply { moveTo(122f, 180f); cubicTo(104f, -20f, 430f, -84f, 474f, 112f) }
            drawPath(band, WorldInk, alpha = alpha, style = Stroke(40f, cap = StrokeCap.Round))
            drawPath(band, Mint, alpha = alpha, style = Stroke(24f, cap = StrokeCap.Round))
            for ((c, a) in listOf(Offset(128f, 214f) to -12f, Offset(476f, 150f) to -20f)) {
                rotate(a, c) {
                    drawRoundRect(WorldInk, Offset(c.x - 46f, c.y - 62f), Size(92f, 124f), CornerRadius(40f), alpha = alpha)
                    drawRoundRect(CupPink, Offset(c.x - 38f, c.y - 54f), Size(76f, 108f), CornerRadius(34f), alpha = alpha)
                    drawRoundRect(MintDeep, Offset(c.x - 18f, c.y - 34f), Size(36f, 68f), CornerRadius(18f), alpha = alpha)
                    drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(c.x - 30f, c.y - 44f), Size(10f, 36f), CornerRadius(5f), alpha = alpha)
                }
            }
        }
        WearItem.BOW_TIE -> rotate(-12f, Offset(345f, 393f)) {
            translate(345f, 393f) {
                val wings = Path().apply {
                    moveTo(-14f, -10f); lineTo(-78f, -40f); quadraticTo(-92f, 0f, -78f, 40f); lineTo(-14f, 10f); close()
                    moveTo(14f, -10f); lineTo(78f, -40f); quadraticTo(92f, 0f, 78f, 40f); lineTo(14f, 10f); close()
                }
                outline(wings, 7f)
                drawPath(wings, BowRed, alpha = alpha)
                for ((x, y) in listOf(-56f to -14f, -46f to 16f, -72f to 6f, 56f to -14f, 46f to 16f, 72f to 6f)) drawCircle(Color.White, 6f, Offset(x, y), alpha = alpha)
                drawRoundRect(WorldInk, Offset(-24f, -24f), Size(48f, 48f), CornerRadius(14f), alpha = alpha)
                drawRoundRect(BowRed, Offset(-17f, -17f), Size(34f, 34f), CornerRadius(10f), alpha = alpha)
            }
        }
        WearItem.SCARF -> {
            // Around the neck along the cord, striped, then the tail hanging over the chest on the left.
            fun band(from: Offset, ctrl: Offset, to: Offset, w: Float, n: Int) {
                val path = Path().apply { moveTo(from.x, from.y); quadraticTo(ctrl.x, ctrl.y, to.x, to.y) }
                drawPath(path, WorldInk, alpha = alpha, style = Stroke(w + 16f, cap = StrokeCap.Round))
                fun at(t: Float) = Offset((1 - t) * (1 - t) * from.x + 2 * (1 - t) * t * ctrl.x + t * t * to.x,
                    (1 - t) * (1 - t) * from.y + 2 * (1 - t) * t * ctrl.y + t * t * to.y)
                for (i in 0 until n) {
                    val a = at(i / n.toFloat()); val b = at((i + 1) / n.toFloat())
                    drawLine(Stripes[i % Stripes.size], a, b, w, cap = if (i == 0 || i == n - 1) StrokeCap.Round else StrokeCap.Butt, alpha = alpha)
                }
            }
            band(Offset(196f, 360f), Offset(326f, 452f), Offset(444f, 318f), 54f, 12)
            band(Offset(262f, 404f), Offset(230f, 480f), Offset(236f, 560f), 48f, 6)
            for (k in -2..2) drawLine(WorldInk, Offset(236f + k * 10f, 586f), Offset(236f + k * 12f, 608f), 6f, StrokeCap.Round, alpha = alpha)
        }
        else -> {}
    }
}

/** An item on its own, fitted into the icon (for the dress-up buttons). Greyed and faint when [locked]. */
@Composable
fun WearIcon(item: WearItem, modifier: Modifier = Modifier, locked: Boolean = false) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val b = Wear.box(item)
        val k = minOf(size.width / b.width, size.height / b.height) * 0.9f
        translate(size.width / 2f - b.center.x * k, size.height / 2f - b.center.y * k) {
            scale(k, Offset.Zero) { drawWear(item, if (locked) 0.28f else 1f) }
        }
    }
}
