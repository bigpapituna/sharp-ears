package com.bigpapituna.sharpears.ui.world

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.bigpapituna.sharpears.engine.world.WearItem
import kotlin.math.cos
import kotlin.math.sin

/*
 * Things for his head, over the sweatband, in base-image pixels (ui/world/Outfit.kt). Most sit in the head's own frame:
 * the top of the sweatband at (290, 58), tilted 15 degrees up to the right with his head ([onHead]). In that frame the
 * band's top edge runs through (-161, 29), (-25, -3) and (109, 19), its bottom edge through (-171, 96), (-56, 54) and
 * (115, 71), and the top of his head is about 40 above the band.
 */

internal val Black = Color(0xFF2E2A2B)
internal val BlackHi = Color(0xFF55504F)
internal val BowRed = Color(0xFFE0605A)
internal val PartyPurple = Color(0xFFA98BE0)
internal val Mint = Color(0xFF8FD6C2)
internal val MintDeep = Color(0xFF5FB8A2)
internal val CupPink = Color(0xFFF4A8B6)
internal val Dot = Color(0xFFFCE38A)
internal val Gold = Color(0xFFF4C150)
internal val GoldDeep = Color(0xFFD69A2E)
internal val Silver = Color(0xFFD5DBE4)
internal val SilverDeep = Color(0xFFA2ACBC)
internal val White = Color(0xFFFFFDF8)
internal val Stripes = listOf(Color(0xFFE86054), Color(0xFFF6A654), Color(0xFFFCD860), Color(0xFF80C470), Color(0xFF74AEE6), Color(0xFFAA8CDC))

/** The headband colours, each with its deeper shade. */
internal fun bandColour(item: WearItem): Pair<Color, Color> = when (item) {
    WearItem.BAND_PINK -> Color(0xFFF59BC0) to Color(0xFFD9709C)
    WearItem.BAND_BLUE -> Color(0xFF7FB8EE) to Color(0xFF4F8CCB)
    WearItem.BAND_GREEN -> Color(0xFF8CD47E) to Color(0xFF5FAE58)
    WearItem.BAND_YELLOW -> Color(0xFFFCE070) to Color(0xFFE5B83E)
    else -> Color(0xFFB79BEA) to Color(0xFF8D6FCB)
}

/** Draws [block] in the head's frame. */
private inline fun DrawScope.onHead(block: DrawScope.() -> Unit) = rotate(-15f, Offset(290f, 58f)) { translate(290f, 58f) { block() } }

/** The dome over the top of his head down to the band's bottom edge (a beanie, a helmet, a hood), in the head frame. */
private fun dome(top: Float = -128f) = path {
    moveTo(-180f, 102f)
    cubicTo(-205f, -30f, -70f, top, 20f, top)
    cubicTo(110f, top, 160f, -40f, 140f, 80f)
    quadraticTo(-40f, 18f, -180f, 102f)
    close()
}

/** A point along the band's top edge at [t] 0..1 (left to right), in the head frame. */
private fun bandTop(t: Float): Offset {
    val a = Offset(-165f, 34f); val c = Offset(-28f, -36f); val b = Offset(118f, 20f)
    val u = 1 - t
    return Offset(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y)
}

internal fun DrawScope.headFront(item: WearItem, a: Float) {
    when (item) {
        WearItem.TOP_HAT -> rotate(-15f, Offset(290f, 58f)) {
            translate(290f, 58f) {
                val crown = path { moveTo(-66f, -8f); lineTo(-72f, -150f); quadraticTo(0f, -164f, 72f, -150f); lineTo(66f, -8f); close() }
                val brim = path { addOval(Rect(-118f, -22f, 118f, 22f)) }
                wearShape(brim, Black, a); wearShape(crown, Black, a)
                drawPath(brim, Black, alpha = a)
                drawRect(BowRed, Offset(-70f, -52f), Size(140f, 30f), alpha = a)
                drawLine(WorldInk, Offset(-70f, -52f), Offset(70f, -52f), 5f, alpha = a)
                drawLine(WorldInk, Offset(-68f, -22f), Offset(68f, -22f), 5f, alpha = a)
                drawRoundRect(BlackHi, Offset(-52f, -140f), Size(18f, 76f), CornerRadius(9f), alpha = a)
                drawOval(BlackHi, Offset(-92f, -14f), Size(60f, 12f), alpha = a)
            }
        }
        WearItem.PARTY_HAT -> rotate(-22f, Offset(298f, 52f)) {
            translate(298f, 52f) {
                val cone = path { moveTo(-74f, 0f); lineTo(6f, -196f); lineTo(74f, 0f); quadraticTo(0f, 22f, -74f, 0f); close() }
                wearShape(cone, PartyPurple, a)
                for ((x, y, r) in listOf(Triple(-24f, -30f, 12f), Triple(28f, -48f, 11f), Triple(-6f, -92f, 10f), Triple(14f, -140f, 8f), Triple(-44f, -6f, 8f), Triple(48f, -10f, 9f))) {
                    drawCircle(Dot, r, Offset(x, y), alpha = a)
                }
                val trim = path { moveTo(-78f, 0f); quadraticTo(0f, 24f, 78f, 0f) }
                drawPath(trim, WorldInk, alpha = a, style = Stroke(26f, cap = StrokeCap.Round))
                drawPath(trim, Color.White, alpha = a, style = Stroke(12f, cap = StrokeCap.Round))
                drawCircle(WorldInk, 26f, Offset(6f, -200f), alpha = a)
                drawCircle(Color.White, 18f, Offset(6f, -200f), alpha = a)
            }
        }
        WearItem.HEADPHONES -> {
            val band = path { moveTo(122f, 180f); cubicTo(104f, -20f, 430f, -84f, 474f, 112f) }
            drawPath(band, WorldInk, alpha = a, style = Stroke(40f, cap = StrokeCap.Round))
            drawPath(band, Mint, alpha = a, style = Stroke(24f, cap = StrokeCap.Round))
            for ((c, ang) in listOf(Offset(128f, 214f) to -12f, Offset(476f, 150f) to -20f)) {
                rotate(ang, c) {
                    drawRoundRect(WorldInk, Offset(c.x - 46f, c.y - 62f), Size(92f, 124f), CornerRadius(40f), alpha = a)
                    drawRoundRect(CupPink, Offset(c.x - 38f, c.y - 54f), Size(76f, 108f), CornerRadius(34f), alpha = a)
                    drawRoundRect(MintDeep, Offset(c.x - 18f, c.y - 34f), Size(36f, 68f), CornerRadius(18f), alpha = a)
                    drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(c.x - 30f, c.y - 44f), Size(10f, 36f), CornerRadius(5f), alpha = a)
                }
            }
        }
        WearItem.BAND_PINK, WearItem.BAND_BLUE, WearItem.BAND_GREEN, WearItem.BAND_YELLOW, WearItem.BAND_PURPLE -> {
            // Over the red sweatband, in its shape: a coloured band with a white stripe and a darker lower edge.
            val (c, deep) = bandColour(item)
            val band = path {
                moveTo(136f, 140f); quadraticTo(228f, 44f, 420f, 42f); lineTo(426f, 98f)
                quadraticTo(244f, 106f, 152f, 202f); quadraticTo(134f, 176f, 136f, 140f); close()
            }
            wearShape(band, c, a, 7f)
            val low = path { moveTo(150f, 192f); quadraticTo(244f, 98f, 424f, 90f) }
            inkLine(low, 14f, a, deep)
            val stripe = path { moveTo(146f, 160f); quadraticTo(236f, 74f, 421f, 68f) }
            inkLine(stripe, 17f, a, White)
            shine(300f, 52f, 70f, 10f, a)
        }
        WearItem.BEANIE -> onHead {
            val knit = Color(0xFF6FB7E8); val deep = Color(0xFF4A8FC4)
            wearShape(dome(), knit, a)
            // Knit ribs over the dome, then the folded cuff along the bottom, then the bobble.
            clipPath(dome()) { for (k in -3..3) inkLine(path { moveTo(-20f + k * 40f, -118f); quadraticTo(-30f + k * 52f, -30f, -36f + k * 58f, 60f) }, 5f, a * 0.5f, deep) }
            val cuff = path { moveTo(-176f, 78f); quadraticTo(-40f, -2f, 140f, 54f) }
            drawPath(cuff, WorldInk, alpha = a, style = Stroke(64f, cap = StrokeCap.Round))
            drawPath(cuff, deep, alpha = a, style = Stroke(48f, cap = StrokeCap.Round))
            for (i in 0..12) {
                val t = i / 12f
                val p = Offset((1 - t) * (1 - t) * -176f + 2 * (1 - t) * t * -40f + t * t * 140f, (1 - t) * (1 - t) * 78f + 2 * (1 - t) * t * -2f + t * t * 54f)
                drawLine(knit, p + Offset(0f, -18f), p + Offset(-2f, 18f), 6f, StrokeCap.Round, alpha = a)
            }
            drawCircle(WorldInk, 42f, Offset(-14f, -138f), alpha = a)
            drawCircle(Color(0xFFFCE38A), 34f, Offset(-14f, -138f), alpha = a)
            shine(-36f, -160f, 22f, 12f, a)
        }
        WearItem.COWBOY_HAT -> onHead {
            val tan = Color(0xFFD9A066); val deep = Color(0xFFB07A44)
            val crown = path { moveTo(-88f, -6f); cubicTo(-100f, -90f, -70f, -128f, -30f, -112f); quadraticTo(0f, -96f, 30f, -112f); cubicTo(70f, -128f, 100f, -90f, 88f, -6f); close() }
            val brim = path { moveTo(-200f, -36f); quadraticTo(-180f, 30f, 0f, 26f); quadraticTo(180f, 30f, 200f, -36f); quadraticTo(150f, 0f, 0f, -6f); quadraticTo(-150f, 0f, -200f, -36f); close() }
            wearShape(crown, tan, a)
            wearShape(brim, tan, a)
            drawRect(deep, Offset(-88f, -36f), Size(176f, 26f), alpha = a)
            drawLine(WorldInk, Offset(-88f, -36f), Offset(88f, -36f), 5f, alpha = a)
            drawCircle(Gold, 10f, Offset(50f, -23f), alpha = a)
            inkLine(path { moveTo(0f, -100f); lineTo(0f, -60f) }, 6f, a * 0.5f, deep)
            shine(-70f, -90f, 18f, 40f, a)
        }
        WearItem.CHEF_HAT -> onHead {
            val puff = path {
                addOval(Rect(Offset(-70f, -104f), 64f)); addOval(Rect(Offset(-4f, -132f), 72f)); addOval(Rect(Offset(62f, -100f), 60f))
            }
            wearShape(puff, White, a)
            val band = path { addRoundRect(androidx.compose.ui.geometry.RoundRect(-112f, -64f, 110f, 22f, CornerRadius(16f))) }
            wearShape(band, White, a)
            for (x in listOf(-70f, -24f, 22f, 68f)) inkLine(path { moveTo(x, -56f); lineTo(x + 2f, 14f) }, 5f, a * 0.35f)
            drawOval(Color(0xFFE9E3DA), Offset(-80f, -150f), Size(150f, 20f), alpha = a * 0.6f)
            shine(-40f, -180f, 40f, 16f, a)
        }
        WearItem.PIRATE_HAT -> onHead {
            val hat = path {
                moveTo(-182f, 10f); quadraticTo(-150f, -40f, -96f, -118f); quadraticTo(-10f, -64f, 82f, -118f)
                quadraticTo(140f, -40f, 168f, 4f); quadraticTo(0f, 44f, -182f, 10f); close()
            }
            wearShape(hat, Black, a)
            val trim = path { moveTo(-176f, 8f); quadraticTo(0f, 40f, 162f, 4f) }
            inkLine(trim, 14f, a, Gold)
            // A friendly skull and bones.
            drawCircle(White, 26f, Offset(-8f, -46f), alpha = a)
            drawRoundRect(White, Offset(-24f, -34f), Size(32f, 22f), CornerRadius(6f), alpha = a)
            drawCircle(Black, 7f, Offset(-18f, -48f), alpha = a); drawCircle(Black, 7f, Offset(2f, -48f), alpha = a)
            inkLine(path { moveTo(-50f, -20f); lineTo(34f, 6f) }, 9f, a, White)
            inkLine(path { moveTo(-50f, 6f); lineTo(34f, -20f) }, 9f, a, White)
            shine(-120f, -60f, 30f, 10f, a)
        }
        WearItem.CROWN -> onHead { crown(a) }
        WearItem.FLOWER_CROWN -> onHead {
            val petals = listOf(Color(0xFFF7A8C4), Color(0xFFFCE070), Color(0xFF9CC9F5), Color(0xFFC6A8EE), Color(0xFFF7A8C4), Color(0xFFFFFFFF), Color(0xFFFCE070))
            inkLine(path { val p0 = bandTop(0f); moveTo(p0.x, p0.y); for (i in 1..20) { val p = bandTop(i / 20f); lineTo(p.x, p.y) } }, 14f, a, Color(0xFF6FAE63))
            for (i in 0..8) {
                val p = bandTop(0.02f + i * 0.12f) + Offset(0f, -6f)
                val leaf = path { addOval(Rect(p.x - 24f, p.y - 10f, p.x + 8f, p.y + 8f)) }
                wearShape(leaf, Color(0xFF7CC173), a, 4f)
            }
            for (i in 0..6) {
                val p = bandTop(0.05f + i * 0.15f) + Offset(0f, -8f)
                val c = petals[i]
                val r = if (i % 2 == 0) 22f else 18f
                for (k in 0 until 5) {
                    val ang = k * 72f * (Math.PI.toFloat() / 180f)
                    val q = p + Offset(cos(ang) * r * 0.75f, sin(ang) * r * 0.75f)
                    drawCircle(WorldInk, r * 0.62f, q, alpha = a)
                }
                for (k in 0 until 5) {
                    val ang = k * 72f * (Math.PI.toFloat() / 180f)
                    drawCircle(c, r * 0.5f, p + Offset(cos(ang) * r * 0.75f, sin(ang) * r * 0.75f), alpha = a)
                }
                drawCircle(Color(0xFFF6A654), r * 0.36f, p, alpha = a)
            }
        }
        WearItem.HALO -> onHead {
            val c = Offset(-20f, -112f)
            drawOval(Brush.radialGradient(listOf(Color(0xFFFFF1B0).copy(alpha = 0.7f), Color.Transparent), c, 170f), Offset(c.x - 170f, c.y - 70f), Size(340f, 140f), alpha = a)
            drawOval(WorldInk, Offset(c.x - 128f, c.y - 36f), Size(256f, 72f), alpha = a, style = Stroke(30f))
            drawOval(Gold, Offset(c.x - 128f, c.y - 36f), Size(256f, 72f), alpha = a, style = Stroke(18f))
            drawOval(Color(0xFFFFF4C2), Offset(c.x - 110f, c.y - 32f), Size(110f, 20f), alpha = a * 0.8f, style = Stroke(5f))
            for ((x, y, r) in listOf(Triple(-170f, -150f, 16f), Triple(140f, -160f, 20f), Triple(-40f, -190f, 12f))) sparkle(Offset(x, y), r, Gold, a)
        }
        WearItem.WIZARD_HAT -> onHead {
            val purple = Color(0xFF6E5BC8); val deep = Color(0xFF4F3FA0)
            val brim = path { addOval(Rect(-176f, -34f, 168f, 36f)) }
            val cone = path { moveTo(-104f, -6f); quadraticTo(-60f, -160f, 10f, -250f); quadraticTo(60f, -270f, 96f, -226f); quadraticTo(40f, -230f, 34f, -196f); quadraticTo(70f, -100f, 96f, -6f); close() }
            wearShape(brim, purple, a); wearShape(cone, purple, a)
            drawPath(brim, deep, alpha = a * 0.35f)
            drawRect(Gold, Offset(-100f, -40f), Size(196f, 24f), alpha = a)
            inkLine(path { moveTo(-100f, -40f); lineTo(96f, -40f) }, 4f, a); inkLine(path { moveTo(-100f, -16f); lineTo(96f, -16f) }, 4f, a)
            for ((x, y, r) in listOf(Triple(-30f, -100f, 18f), Triple(30f, -150f, 14f), Triple(-6f, -190f, 11f), Triple(40f, -70f, 12f))) starShape(Offset(x, y), r, Dot, a)
            // A moon.
            val moon = path { addOval(Rect(Offset(-62f, -150f), 16f)) }
            drawPath(moon, Dot, alpha = a)
            drawCircle(purple, 13f, Offset(-55f, -155f), alpha = a)
            drawCircle(WorldInk, 14f, Offset(96f, -228f), alpha = a); drawCircle(Gold, 10f, Offset(96f, -228f), alpha = a)
        }
        WearItem.KNIGHT_HELMET -> onHead {
            val plume = path { moveTo(10f, -126f); cubicTo(-10f, -230f, 120f, -230f, 150f, -150f); cubicTo(110f, -180f, 60f, -170f, 34f, -122f); close() }
            wearShape(plume, Color(0xFFE5484D), a)
            inkLine(path { moveTo(30f, -150f); quadraticTo(80f, -190f, 128f, -160f) }, 5f, a * 0.5f, Color(0xFFB3363B))
            wearShape(dome(-132f), Silver, a)
            drawPath(dome(-132f), Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), -130f, 40f), alpha = a)
            // The ridge down the middle and the rim with rivets.
            inkLine(path { moveTo(14f, -128f); quadraticTo(-10f, -40f, -30f, 40f) }, 10f, a * 0.6f, SilverDeep)
            val rim = path { moveTo(-176f, 84f); quadraticTo(-40f, 4f, 140f, 60f) }
            drawPath(rim, WorldInk, alpha = a, style = Stroke(40f, cap = StrokeCap.Round))
            drawPath(rim, SilverDeep, alpha = a, style = Stroke(26f, cap = StrokeCap.Round))
            for (i in 0..8) { val t = i / 8f; val p = Offset((1 - t) * (1 - t) * -176f + 2 * (1 - t) * t * -40f + t * t * 140f, (1 - t) * (1 - t) * 84f + 2 * (1 - t) * t * 4f + t * t * 60f); drawCircle(Silver, 5f, p, alpha = a) }
            shine(-110f, -90f, 26f, 54f, a)
        }
        WearItem.FIRE_HELMET -> onHead {
            val red = Color(0xFFE5484D); val deep = Color(0xFFB8353B)
            val brim = path { moveTo(-190f, 104f); quadraticTo(-60f, 30f, 170f, 60f); quadraticTo(210f, 70f, 196f, 100f); quadraticTo(-40f, 70f, -190f, 104f); close() }
            wearShape(brim, red, a)
            wearShape(dome(-120f), red, a)
            for (k in -2..2) inkLine(path { moveTo(10f + k * 44f, -116f); quadraticTo(4f + k * 50f, -40f, -6f + k * 62f, 40f) }, 6f, a * 0.45f, deep)
            // The front shield with a gold badge.
            val shield = path { moveTo(-86f, -70f); lineTo(-8f, -70f); lineTo(-10f, 0f); quadraticTo(-48f, 30f, -84f, 0f); close() }
            wearShape(shield, Color(0xFFFFF4E6), a, 6f)
            drawCircle(Gold, 20f, Offset(-47f, -28f), alpha = a)
            starShape(Offset(-47f, -28f), 14f, GoldDeep, a)
            shine(-130f, -60f, 24f, 50f, a)
        }
        WearItem.ANTENNAE -> onHead {
            for ((from, ctrl, to) in listOf(Triple(Offset(-90f, 4f), Offset(-120f, -80f), Offset(-160f, -118f)), Triple(Offset(40f, -10f), Offset(60f, -90f), Offset(100f, -130f)))) {
                val stalk = path { moveTo(from.x, from.y); quadraticTo(ctrl.x, ctrl.y, to.x, to.y) }
                drawPath(stalk, WorldInk, alpha = a, style = Stroke(20f, cap = StrokeCap.Round))
                drawPath(stalk, Black, alpha = a, style = Stroke(10f, cap = StrokeCap.Round))
                drawCircle(WorldInk, 30f, to, alpha = a)
                drawCircle(Color(0xFFFCD860), 22f, to, alpha = a)
                drawCircle(Color.White.copy(alpha = 0.7f), 7f, to + Offset(-8f, -8f), alpha = a)
            }
            val clip = path { moveTo(-120f, 30f); quadraticTo(-28f, -10f, 80f, 12f) }
            drawPath(clip, WorldInk, alpha = a, style = Stroke(24f, cap = StrokeCap.Round))
            drawPath(clip, Black, alpha = a, style = Stroke(12f, cap = StrokeCap.Round))
        }
        WearItem.SPACE_HELMET -> {
            // A glass bubble round his whole head, on a silver collar.
            val c = Offset(285f, 190f); val r = 262f
            drawCircle(Color(0xFFBFE6FF).copy(alpha = 0.2f), r, c, alpha = a)
            drawCircle(WorldInk, r, c, alpha = a, style = Stroke(14f))
            drawCircle(Color(0xFFE6F6FF), r - 10f, c, alpha = a * 0.8f, style = Stroke(6f))
            drawArc(Color.White.copy(alpha = 0.75f), 200f, 50f, false, Offset(c.x - r + 40f, c.y - r + 40f), Size((r - 40f) * 2, (r - 40f) * 2), alpha = a, style = Stroke(18f, cap = StrokeCap.Round))
            drawArc(Color.White.copy(alpha = 0.6f), 258f, 10f, false, Offset(c.x - r + 40f, c.y - r + 40f), Size((r - 40f) * 2, (r - 40f) * 2), alpha = a, style = Stroke(18f, cap = StrokeCap.Round))
            // The silver collar the bubble sits on, round his neck over the cord.
            val collar = path { moveTo(150f, 400f); quadraticTo(310f, 480f, 474f, 380f); lineTo(484f, 420f); quadraticTo(310f, 530f, 140f, 440f); close() }
            wearShape(collar, Silver, a)
            drawPath(collar, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent), 390f, 470f), alpha = a)
            for (x in listOf(190f, 250f, 310f, 370f, 430f)) drawCircle(SilverDeep, 7f, Offset(x, 446f + 30f * (1f - kotlin.math.abs(x - 310f) / 160f) - 10f), alpha = a)
        }
        WearItem.DINO_CAP -> onHead {
            val green = Color(0xFF7DC46E); val spike = Color(0xFFF6A654)
            for (i in 0..5) {
                val t = 0.08f + i * 0.17f
                val p = domePoint(t)
                val tri = path { moveTo(p.x - 24f, p.y + 10f); lineTo(p.x + (p.x - 20f) * 0.12f, p.y - 46f); lineTo(p.x + 24f, p.y + 10f); close() }
                wearShape(tri, spike, a, 6f)
            }
            wearShape(dome(), green, a)
            val rim = path { moveTo(-176f, 84f); quadraticTo(-40f, 6f, 140f, 60f) }
            drawPath(rim, WorldInk, alpha = a, style = Stroke(30f, cap = StrokeCap.Round))
            drawPath(rim, Color(0xFF5FAE58), alpha = a, style = Stroke(18f, cap = StrokeCap.Round))
            for ((x, y) in listOf(-90f to -60f, -20f to -86f, 50f to -50f, -60f to 0f)) drawCircle(Color(0xFF5FAE58), 12f, Offset(x, y), alpha = a)
            shine(-120f, -70f, 22f, 44f, a)
        }
        WearItem.HEAD_MIRROR -> {
            // A doctor's band over the sweatband, and the round mirror on his forehead.
            val strap = path { moveTo(140f, 160f); quadraticTo(232f, 70f, 424f, 70f) }
            drawPath(strap, WorldInk, alpha = a, style = Stroke(30f, cap = StrokeCap.Round))
            drawPath(strap, Color(0xFF4A4D63), alpha = a, style = Stroke(18f, cap = StrokeCap.Round))
            val c = Offset(262f, 92f)
            drawCircle(WorldInk, 54f, c, alpha = a)
            drawCircle(Silver, 46f, c, alpha = a)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFC4CFDD)), c, 46f), 40f, c, alpha = a)
            drawCircle(Color(0xFF4A4D63), 9f, c, alpha = a)
            drawArc(Color.White, 200f, 70f, false, Offset(c.x - 30f, c.y - 30f), Size(60f, 60f), alpha = a, style = Stroke(6f, cap = StrokeCap.Round))
        }
        else -> {}
    }
}

/** A point on the dome's top edge at [t] 0..1, in the head frame (for the dinosaur's spikes). */
private fun domePoint(t: Float): Offset {
    // The dome's two cubics as one curve, near enough for spikes along the top.
    val pts = listOf(Offset(-170f, 40f), Offset(-150f, -60f), Offset(-80f, -116f), Offset(20f, -128f), Offset(100f, -100f), Offset(140f, -20f), Offset(140f, 50f))
    val f = t * (pts.size - 1)
    val i = f.toInt().coerceIn(0, pts.size - 2)
    val u = f - i
    return pts[i] + (pts[i + 1] - pts[i]) * u
}

/** The gold crown, in the head frame: a band and five points with gems. */
private fun DrawScope.crown(a: Float) {
    val body = path {
        moveTo(-118f, 10f); lineTo(-128f, -96f); lineTo(-74f, -40f); lineTo(-30f, -122f); lineTo(14f, -40f); lineTo(62f, -122f)
        lineTo(96f, -40f); lineTo(140f, -96f); lineTo(118f, 10f); quadraticTo(0f, 26f, -118f, 10f); close()
    }
    wearShape(body, Gold, a)
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFFFFE9A6), Color.Transparent), -120f, -20f), alpha = a)
    val band = path { moveTo(-120f, -16f); quadraticTo(0f, 0f, 120f, -16f) }
    inkLine(band, 6f, a)
    for ((x, c) in listOf(-80f to Color(0xFFE5484D), -8f to Color(0xFF5FA8E8), 66f to Color(0xFF6FC27A))) {
        drawCircle(WorldInk, 15f, Offset(x, -2f), alpha = a); drawCircle(c, 10f, Offset(x, -2f), alpha = a)
    }
    for ((x, y) in listOf(-128f to -96f, -30f to -122f, 62f to -122f, 140f to -96f)) { drawCircle(WorldInk, 13f, Offset(x, y), alpha = a); drawCircle(White, 8f, Offset(x, y), alpha = a) }
    sparkle(Offset(-60f, -140f), 18f, Color.White, a)
    sparkle(Offset(110f, -150f), 14f, Color.White, a)
}

/** A five-point star of radius [r] at [c], outlined. */
internal fun DrawScope.starShape(c: Offset, r: Float, fill: Color, a: Float) {
    val p = path {
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.48f
            val ang = (-90f + i * 36f) * (Math.PI.toFloat() / 180f)
            val q = Offset(c.x + rr * cos(ang), c.y + rr * sin(ang))
            if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
        }
        close()
    }
    wearShape(p, fill, a, r * 0.16f)
}
