package com.bigpapituna.sharpears.ui.world

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
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

/*
 * Things for his body, in base-image pixels (ui/world/Outfit.kt): the chest from the neck (the cord's dip, (182, 366)
 * to (456, 352)) down to the haunches, and sleeves over the front legs (left 198 to 312 at the shoulder, down to 242 to
 * 340 at the paw; right 356 to 462, down to 366 to 462). The front paws stay out under every costume.
 */

/** The chest and tummy, from the neckline down to a hem at [hem]. */
private fun torso(hem: Float = 560f) = path {
    moveTo(182f, 366f)
    quadraticTo(150f, 420f, 140f, 470f)
    quadraticTo(128f, 520f, 126f, hem)
    quadraticTo(300f, hem + 48f, 486f, hem - 4f)
    quadraticTo(476f, 450f, 456f, 352f)
    quadraticTo(352f, 444f, 182f, 366f)
    close()
}

/** The two sleeves over the front legs, ending in cuffs at [cuff]. */
private fun sleeves(cuff: Float = 636f) = path {
    moveTo(196f, 452f); lineTo(314f, 452f); lineTo(342f, cuff); quadraticTo(292f, cuff + 14f, 240f, cuff); close()
    moveTo(354f, 446f); lineTo(464f, 446f); lineTo(464f, cuff); quadraticTo(416f, cuff + 14f, 366f, cuff); close()
}

/** Cuffs: a band of [color] at the end of each sleeve. */
private fun DrawScope.cuffs(color: Color, a: Float, cuff: Float = 636f) {
    val l = path { moveTo(244f, cuff - 4f); quadraticTo(292f, cuff + 10f, 340f, cuff - 4f) }
    val r = path { moveTo(368f, cuff - 4f); quadraticTo(416f, cuff + 10f, 462f, cuff - 4f) }
    for (p in listOf(l, r)) { drawPath(p, WorldInk, alpha = a, style = Stroke(34f, cap = StrokeCap.Round)); drawPath(p, color, alpha = a, style = Stroke(22f, cap = StrokeCap.Round)) }
}

/** A suit: the sleeves, then the chest over them, both [color], with the seam between the legs. */
private fun DrawScope.suit(color: Color, a: Float, hem: Float = 560f, cuff: Float = 636f, shade: Color? = null) {
    wearShape(sleeves(cuff), color, a)
    wearShape(torso(hem), color, a)
    if (shade != null) {
        drawPath(torso(hem), Brush.horizontalGradient(listOf(Color.Transparent, shade.copy(alpha = 0.45f)), 330f, 490f), alpha = a)
        inkLine(path { moveTo(330f, 560f); quadraticTo(334f, 600f, 340f, cuff) }, 6f, a * 0.4f)
    }
}

/** A skirt from the waist at [waist] flaring to a scalloped hem at [hem]. */
private fun skirt(waist: Float = 500f, hem: Float = 652f, left: Float = 60f, right: Float = 552f, scallops: Int = 7) = path {
    moveTo(150f, waist - 6f)
    quadraticTo(310f, waist + 30f, 470f, waist - 10f)
    quadraticTo(520f, (waist + hem) / 2f, right, hem)
    val w = (right - left) / scallops
    for (i in 0 until scallops) {
        val x0 = right - i * w
        quadraticTo(x0 - w / 2f, hem + 26f, x0 - w, hem)
    }
    quadraticTo(90f, (waist + hem) / 2f, 150f, waist - 6f)
    close()
}

/** The bodice of a dress: the chest down to the waist, sleeveless. */
private fun bodice() = torso(hem = 512f)

internal fun DrawScope.bodyBehind(item: WearItem, a: Float) {
    when (item) {
        WearItem.CAPE_RED, WearItem.CAPE_BLUE -> {
            val (c, deep) = if (item == WearItem.CAPE_RED) Color(0xFFE5484D) to Color(0xFFB8353B) else Color(0xFF4F7FD8) to Color(0xFF345CA8)
            val cape = path { moveTo(160f, 330f); quadraticTo(310f, 300f, 470f, 316f); lineTo(566f, 690f); quadraticTo(300f, 740f, 16f, 694f); close() }
            wearShape(cape, c, a)
            drawPath(cape, Brush.horizontalGradient(listOf(deep.copy(alpha = 0.5f), Color.Transparent, deep.copy(alpha = 0.5f)), 16f, 566f), alpha = a)
            for (x in listOf(120f, 480f)) inkLine(path { moveTo(x + (310f - x) * 0.4f, 360f); lineTo(x, 690f) }, 6f, a * 0.35f, deep)
        }
        WearItem.HERO_SUIT -> {
            val cape = path { moveTo(170f, 340f); quadraticTo(310f, 310f, 460f, 324f); lineTo(520f, 620f); quadraticTo(300f, 660f, 70f, 620f); close() }
            wearShape(cape, Color(0xFFE5484D), a)
        }
        WearItem.LION -> {
            // The mane, round his head: tufts in two golds, peeking out all round him.
            val c = Offset(288f, 196f)
            val outer = Color(0xFFC9772F); val inner = Color(0xFFE59A45)
            // Fluffy: a ring of round tufts, every outline first and every fill over them, so only the outer edge shows.
            for ((r, col) in listOf(250f to outer, 214f to inner)) {
                val n = 18
                val tufts = (0 until n).map { i ->
                    val ang = i * 2 * Math.PI / n
                    Offset(c.x + r * kotlin.math.cos(ang).toFloat(), c.y + r * 0.92f * kotlin.math.sin(ang).toFloat())
                }
                val rad = if (col == outer) 62f else 54f
                tufts.forEach { drawCircle(WorldInk, rad + 8f, it, alpha = a) }
                drawCircle(WorldInk, r + 8f, c, alpha = a)
                drawCircle(col, r, c, alpha = a)
                tufts.forEach { drawCircle(col, rad, it, alpha = a) }
                tufts.forEachIndexed { i, t -> if (i % 2 == 0) inkLine(path { moveTo(t.x, t.y); lineTo(t.x + (t.x - c.x) * 0.15f, t.y + (t.y - c.y) * 0.15f) }, 5f, a * 0.35f) }
            }
        }
        WearItem.BEE -> {
            for ((c, ang) in listOf(Offset(150f, 360f) to -30f, Offset(462f, 344f) to 30f)) rotate(ang, c) {
                val wing = path { addOval(Rect(c.x - 46f, c.y - 76f, c.x + 46f, c.y + 20f)) }
                wearShape(wing, Color(0xFFE8F6FF).copy(alpha = 0.85f), a, 6f)
                inkLine(path { moveTo(c.x, c.y + 10f); lineTo(c.x, c.y - 60f) }, 4f, a * 0.4f)
            }
        }
        WearItem.DINO -> {
            val green = Color(0xFF7DC46E)
            val tail = path { moveTo(440f, 520f); cubicTo(540f, 520f, 600f, 560f, 620f, 660f); quadraticTo(560f, 640f, 470f, 640f); close() }
            for (i in 0..3) {
                val p = Offset(500f + i * 32f, 528f + i * 30f)
                val spike = path { moveTo(p.x - 18f, p.y + 6f); lineTo(p.x + 16f, p.y - 30f); lineTo(p.x + 22f, p.y + 18f); close() }
                wearShape(spike, Color(0xFFF6A654), a, 6f)
            }
            wearShape(tail, green, a)
            for (side in listOf(-1f, 1f)) {
                val x = if (side < 0) 120f else 470f
                for (k in 0..2) {
                    val p = Offset(x + side * 10f, 330f + k * 50f)
                    val spike = path { moveTo(p.x, p.y - 24f); lineTo(p.x + side * 36f, p.y); lineTo(p.x, p.y + 24f); close() }
                    wearShape(spike, Color(0xFFF6A654), a, 6f)
                }
            }
        }
        WearItem.MERMAID -> {
            val fin = path { moveTo(470f, 640f); cubicTo(520f, 600f, 580f, 560f, 610f, 590f); quadraticTo(580f, 640f, 600f, 700f); cubicTo(560f, 700f, 520f, 690f, 470f, 680f); close() }
            wearShape(fin, Color(0xFF58C2B0), a)
            for (k in 0..2) inkLine(path { moveTo(490f, 660f); lineTo(590f, 590f + k * 40f) }, 5f, a * 0.4f, Color(0xFF2E8E80))
        }
        WearItem.PRINCESS -> {
            val back = skirt(waist = 470f, hem = 700f, left = 20f, right = 590f, scallops = 9)
            wearShape(back, Color(0xFFD6B4F2), a)
        }
        else -> {}
    }
}

internal fun DrawScope.bodyFront(item: WearItem, a: Float) {
    when (item) {
        WearItem.BOW_TIE -> rotate(-12f, Offset(345f, 393f)) {
            translate(345f, 393f) {
                val wings = path {
                    moveTo(-14f, -10f); lineTo(-78f, -40f); quadraticTo(-92f, 0f, -78f, 40f); lineTo(-14f, 10f); close()
                    moveTo(14f, -10f); lineTo(78f, -40f); quadraticTo(92f, 0f, 78f, 40f); lineTo(14f, 10f); close()
                }
                wearShape(wings, BowRed, a, 7f)
                for ((x, y) in listOf(-56f to -14f, -46f to 16f, -72f to 6f, 56f to -14f, 46f to 16f, 72f to 6f)) drawCircle(Color.White, 6f, Offset(x, y), alpha = a)
                drawRoundRect(WorldInk, Offset(-24f, -24f), Size(48f, 48f), CornerRadius(14f), alpha = a)
                drawRoundRect(BowRed, Offset(-17f, -17f), Size(34f, 34f), CornerRadius(10f), alpha = a)
            }
        }
        WearItem.SCARF -> {
            fun band(from: Offset, ctrl: Offset, to: Offset, w: Float, n: Int) {
                val p = path { moveTo(from.x, from.y); quadraticTo(ctrl.x, ctrl.y, to.x, to.y) }
                drawPath(p, WorldInk, alpha = a, style = Stroke(w + 16f, cap = StrokeCap.Round))
                fun at(t: Float) = Offset((1 - t) * (1 - t) * from.x + 2 * (1 - t) * t * ctrl.x + t * t * to.x,
                    (1 - t) * (1 - t) * from.y + 2 * (1 - t) * t * ctrl.y + t * t * to.y)
                for (i in 0 until n) {
                    val s = at(i / n.toFloat()); val e = at((i + 1) / n.toFloat())
                    drawLine(Stripes[i % Stripes.size], s, e, w, cap = if (i == 0 || i == n - 1) StrokeCap.Round else StrokeCap.Butt, alpha = a)
                }
            }
            band(Offset(196f, 360f), Offset(326f, 452f), Offset(444f, 318f), 54f, 12)
            band(Offset(262f, 404f), Offset(230f, 480f), Offset(236f, 560f), 48f, 6)
            for (k in -2..2) drawLine(WorldInk, Offset(236f + k * 10f, 586f), Offset(236f + k * 12f, 608f), 6f, StrokeCap.Round, alpha = a)
        }
        WearItem.BANDANA -> {
            val red = Color(0xFFE5484D)
            val cloth = path { moveTo(186f, 362f); quadraticTo(320f, 436f, 454f, 346f); quadraticTo(420f, 430f, 330f, 516f); quadraticTo(240f, 440f, 186f, 362f); close() }
            wearShape(cloth, red, a)
            clipPath(cloth) { for ((x, y) in listOf(250f to 420f, 300f to 450f, 350f to 430f, 400f to 405f, 330f to 485f, 280f to 400f, 380f to 455f)) drawCircle(White, 9f, Offset(x, y), alpha = a) }
            val knot = path { addOval(Rect(Offset(196f, 372f), 22f)) }
            wearShape(knot, red, a, 6f)
            for (end in listOf(Offset(160f, 410f), Offset(176f, 420f))) inkLine(path { moveTo(196f, 380f); quadraticTo(176f, 392f, end.x, end.y) }, 18f, a, red)
        }
        WearItem.HOODIE -> {
            val c = Color(0xFF6FC2B4); val deep = Color(0xFF4A9C8F)
            suit(c, a, shade = deep)
            cuffs(deep, a)
            // The hood lies back round his neck; the strings and the pocket.
            val hood = path { moveTo(176f, 360f); quadraticTo(320f, 470f, 462f, 346f) }
            drawPath(hood, WorldInk, alpha = a, style = Stroke(58f, cap = StrokeCap.Round))
            drawPath(hood, deep, alpha = a, style = Stroke(42f, cap = StrokeCap.Round))
            for (x in listOf(300f, 360f)) { inkLine(path { moveTo(x, 430f); quadraticTo(x - 6f, 470f, x + 2f, 506f) }, 7f, a, White); drawCircle(White, 7f, Offset(x + 2f, 510f), alpha = a) }
            val pocket = path { moveTo(220f, 520f); lineTo(420f, 516f); lineTo(440f, 570f); quadraticTo(320f, 590f, 200f, 574f); close() }
            wearShape(pocket, c, a, 6f)
        }
        WearItem.RAINCOAT -> {
            val y = Color(0xFFFCD860); val deep = Color(0xFFE5B83E)
            suit(y, a, shade = deep)
            cuffs(deep, a)
            val hood = path { moveTo(176f, 360f); quadraticTo(320f, 470f, 462f, 346f) }
            drawPath(hood, WorldInk, alpha = a, style = Stroke(60f, cap = StrokeCap.Round))
            drawPath(hood, deep, alpha = a, style = Stroke(44f, cap = StrokeCap.Round))
            inkLine(path { moveTo(330f, 440f); lineTo(334f, 590f) }, 7f, a * 0.7f)
            for (yy in listOf(470f, 520f, 568f)) { drawRoundRect(WorldInk, Offset(312f, yy - 8f), Size(44f, 16f), CornerRadius(8f), alpha = a); drawRoundRect(Color(0xFF4A4D63), Offset(316f, yy - 5f), Size(36f, 10f), CornerRadius(5f), alpha = a) }
            shine(170f, 450f, 18f, 60f, a)
        }
        WearItem.BEE -> {
            val yellow = Color(0xFFFCD24A)
            wearShape(sleeves(), Black, a)
            wearShape(torso(570f), yellow, a)
            clipPath(torso(570f)) {
                for (yy in listOf(430f, 500f, 570f)) drawRect(Black, Offset(100f, yy - 18f), Size(420f, 34f), alpha = a)
            }
            drawPath(torso(570f), WorldInk, alpha = a, style = Stroke(10f))
            shine(170f, 430f, 22f, 50f, a)
        }
        WearItem.TUTU -> tutu(Color(0xFFF7A8C4), Color(0xFFE47EA3), a)
        WearItem.SNOWMAN -> {
            val snow = Color(0xFFF7FBFF)
            wearShape(sleeves(), snow, a)
            val belly = path { addOval(Rect(118f, 370f, 500f, 660f)) }
            wearShape(belly, snow, a)
            drawPath(belly, Brush.radialGradient(listOf(Color.Transparent, Color(0xFFCFE0F2)), Offset(280f, 470f), 230f), alpha = a)
            for (yy in listOf(450f, 520f, 590f)) { drawCircle(WorldInk, 16f, Offset(318f, yy), alpha = a); drawCircle(Black, 11f, Offset(318f, yy), alpha = a) }
            val scarf = path { moveTo(186f, 366f); quadraticTo(320f, 450f, 456f, 350f) }
            drawPath(scarf, WorldInk, alpha = a, style = Stroke(50f, cap = StrokeCap.Round))
            drawPath(scarf, Color(0xFFE5484D), alpha = a, style = Stroke(36f, cap = StrokeCap.Round))
            inkLine(path { moveTo(420f, 380f); quadraticTo(440f, 440f, 430f, 490f) }, 34f, a, Color(0xFFE5484D))
            inkLine(path { moveTo(420f, 380f); quadraticTo(440f, 440f, 430f, 490f) }, 4f, a * 0.4f)
        }
        WearItem.PUMPKIN -> {
            val orange = Color(0xFFF59A3A); val deep = Color(0xFFD9772A)
            val body = path { addOval(Rect(96f, 360f, 530f, 668f)) }
            wearShape(body, orange, a)
            clipPath(body) { for (x in listOf(190f, 260f, 330f, 400f, 470f)) inkLine(path { moveTo(x, 360f); quadraticTo(x + (x - 313f) * 0.35f, 514f, x, 668f) }, 10f, a, deep) }
            drawPath(body, WorldInk, alpha = a, style = Stroke(12f))
            shine(150f, 420f, 30f, 70f, a)
            // A leafy collar and a curly vine.
            for (k in 0..4) { val p = Offset(200f + k * 62f, 380f + kotlin.math.abs(k - 2) * -10f + 18f); wearShape(path { addOval(Rect(p.x - 34f, p.y - 16f, p.x + 34f, p.y + 16f)) }, Color(0xFF7CC173), a, 5f) }
            inkLine(path { moveTo(430f, 372f); cubicTo(470f, 340f, 500f, 380f, 470f, 400f) }, 7f, a, Color(0xFF5FAE58))
        }
        WearItem.DINO -> {
            val green = Color(0xFF7DC46E); val deep = Color(0xFF5FAE58)
            suit(green, a, shade = deep)
            cuffs(deep, a)
            val belly = path { addOval(Rect(230f, 410f, 420f, 610f)) }
            wearShape(belly, Color(0xFFF5E4B8), a, 6f)
            clipPath(belly) { for (yy in listOf(450f, 490f, 530f, 570f)) inkLine(path { moveTo(220f, yy); quadraticTo(325f, yy + 14f, 430f, yy) }, 5f, a * 0.4f) }
            for ((x, y) in listOf(170f to 470f, 450f to 450f, 190f to 530f, 455f to 520f)) drawCircle(deep, 13f, Offset(x, y), alpha = a)
        }
        WearItem.CAPE_RED, WearItem.CAPE_BLUE -> {
            // The cape's collar over his shoulders, fastened with two gold clasps.
            val c = if (item == WearItem.CAPE_RED) Color(0xFFE5484D) else Color(0xFF4F7FD8)
            for (side in listOf(Offset(182f, 372f), Offset(452f, 356f))) {
                val flap = path { addOval(Rect(side.x - 34f, side.y - 22f, side.x + 34f, side.y + 22f)) }
                wearShape(flap, c, a, 6f)
                drawCircle(WorldInk, 15f, side + Offset(0f, 8f), alpha = a); drawCircle(Gold, 10f, side + Offset(0f, 8f), alpha = a)
            }
        }
        WearItem.MERMAID -> {
            val teal = Color(0xFF58C2B0); val deep = Color(0xFF3FA393)
            val tail = path {
                moveTo(112f, 520f); quadraticTo(300f, 486f, 496f, 516f); quadraticTo(512f, 600f, 490f, 660f)
                quadraticTo(470f, 700f, 400f, 708f); lineTo(190f, 708f); quadraticTo(96f, 690f, 100f, 610f); close()
            }
            wearShape(tail, teal, a)
            clipPath(tail) {
                for (row in 0..6) for (col in 0..8) {
                    val x = 110f + col * 48f + (row % 2) * 24f; val y = 530f + row * 28f
                    drawArc(deep, 0f, 180f, false, Offset(x - 20f, y - 14f), Size(40f, 28f), alpha = a, style = Stroke(5f))
                }
            }
            drawPath(tail, WorldInk, alpha = a, style = Stroke(12f))
            val belt = path { moveTo(112f, 520f); quadraticTo(300f, 486f, 496f, 516f) }
            drawPath(belt, WorldInk, alpha = a, style = Stroke(30f, cap = StrokeCap.Round))
            drawPath(belt, Color(0xFFC6A8EE), alpha = a, style = Stroke(18f, cap = StrokeCap.Round))
            for (c in listOf(Offset(268f, 452f), Offset(392f, 440f))) shell(c, a)
        }
        WearItem.BALLERINA -> {
            val pink = Color(0xFFF7B6CF); val deep = Color(0xFFE47EA3)
            wearShape(bodice(), pink, a)
            clipPath(bodice()) { for (x in listOf(230f, 290f, 350f, 410f)) inkLine(path { moveTo(x, 380f); lineTo(x + 10f, 520f) }, 4f, a * 0.3f, deep) }
            tutu(Color(0xFFFBD3E2), deep, a, wide = true)
            bow(Offset(316f, 506f), deep, a)
        }
        WearItem.ASTRONAUT -> {
            val white = Color(0xFFF2F4F8); val grey = Color(0xFFB9C2D0)
            suit(white, a, shade = grey)
            cuffs(Color(0xFF8FA0B8), a)
            val belt = path { moveTo(132f, 540f); quadraticTo(300f, 574f, 484f, 536f) }
            drawPath(belt, WorldInk, alpha = a, style = Stroke(34f, cap = StrokeCap.Round))
            drawPath(belt, grey, alpha = a, style = Stroke(22f, cap = StrokeCap.Round))
            val panel = path { addRoundRect(RoundRect(250f, 440f, 380f, 520f, CornerRadius(14f))) }
            wearShape(panel, Color(0xFF4A4D63), a, 6f)
            for ((x, c) in listOf(276f to Color(0xFFE5484D), 314f to Color(0xFFFCD860), 352f to Color(0xFF6FC27A))) drawCircle(c, 10f, Offset(x, 466f), alpha = a)
            drawRoundRect(Color(0xFF7FB8EE), Offset(266f, 488f), Size(98f, 14f), CornerRadius(7f), alpha = a)
            // A star patch on the shoulder.
            drawCircle(WorldInk, 30f, Offset(190f, 420f), alpha = a); drawCircle(Color(0xFF4F7FD8), 24f, Offset(190f, 420f), alpha = a)
            starShape(Offset(190f, 420f), 16f, White, a)
        }
        WearItem.LION -> {}
        WearItem.HERO_SUIT -> {
            val blue = Color(0xFF4F7FD8); val deep = Color(0xFF345CA8)
            suit(blue, a, shade = deep)
            cuffs(Color(0xFFE5484D), a)
            val belt = path { moveTo(132f, 540f); quadraticTo(300f, 574f, 484f, 536f) }
            drawPath(belt, WorldInk, alpha = a, style = Stroke(34f, cap = StrokeCap.Round))
            drawPath(belt, Color(0xFFFCD860), alpha = a, style = Stroke(22f, cap = StrokeCap.Round))
            val badge = path { moveTo(250f, 430f); lineTo(390f, 420f); lineTo(400f, 470f); lineTo(326f, 530f); lineTo(244f, 478f); close() }
            wearShape(badge, Color(0xFFFCD860), a, 7f)
            starShape(Offset(322f, 466f), 30f, Color(0xFFE5484D), a)
        }
        WearItem.KNIGHT -> {
            val steel = Silver; val deep = SilverDeep
            wearShape(sleeves(), steel, a)
            for (cuff in listOf(500f, 560f, 610f)) {
                inkLine(path { moveTo(204f, cuff); lineTo(322f, cuff) }, 5f, a * 0.45f)
                inkLine(path { moveTo(358f, cuff); lineTo(464f, cuff) }, 5f, a * 0.45f)
            }
            wearShape(torso(), steel, a)
            drawPath(torso(), Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent, deep.copy(alpha = 0.5f)), 140f, 486f), alpha = a)
            // A red shield with a gold cross on the chest; round shoulder plates with rivets.
            val shield = path { moveTo(258f, 430f); lineTo(382f, 422f); lineTo(380f, 490f); quadraticTo(324f, 550f, 262f, 496f); close() }
            wearShape(shield, Color(0xFFE5484D), a, 7f)
            drawRect(Gold, Offset(312f, 432f), Size(18f, 92f), alpha = a)
            drawRect(Gold, Offset(272f, 462f), Size(100f, 18f), alpha = a)
            for (c in listOf(Offset(184f, 400f), Offset(458f, 386f))) {
                val plate = path { addOval(Rect(c.x - 52f, c.y - 36f, c.x + 52f, c.y + 36f)) }
                wearShape(plate, steel, a)
                for (k in -1..1) drawCircle(deep, 6f, c + Offset(k * 30f, 14f), alpha = a)
            }
            shine(160f, 470f, 18f, 50f, a)
        }
        WearItem.PRINCESS -> {
            val lilac = Color(0xFFD6B4F2); val deep = Color(0xFFAD86D8)
            wearShape(bodice(), lilac, a)
            val skirt = skirt(waist = 500f, hem = 660f, left = 54f, right = 560f, scallops = 8)
            wearShape(skirt, lilac, a)
            clipPath(skirt) { for (x in listOf(150f, 230f, 310f, 390f, 470f)) inkLine(path { moveTo(310f + (x - 310f) * 0.4f, 500f); lineTo(x, 670f) }, 6f, a * 0.4f, deep) }
            // The sash from the shoulder to the hip, with a rosette; puffed sleeves.
            val sash = path { moveTo(200f, 384f); quadraticTo(330f, 470f, 470f, 520f) }
            drawPath(sash, WorldInk, alpha = a, style = Stroke(44f, cap = StrokeCap.Round))
            drawPath(sash, Color(0xFFF59BC0), alpha = a, style = Stroke(30f, cap = StrokeCap.Round))
            drawCircle(WorldInk, 26f, Offset(452f, 512f), alpha = a); drawCircle(Gold, 19f, Offset(452f, 512f), alpha = a); drawCircle(White, 7f, Offset(452f, 512f), alpha = a)
            for (c in listOf(Offset(184f, 400f), Offset(456f, 386f))) wearShape(path { addOval(Rect(c.x - 44f, c.y - 32f, c.x + 44f, c.y + 32f)) }, lilac, a)
            for ((x, y) in listOf(130f to 620f, 250f to 600f, 380f to 610f, 500f to 630f, 200f to 560f, 440f to 570f)) sparkle(Offset(x, y), 10f, White, a)
        }
        WearItem.DOCTOR -> {
            val coat = Color(0xFFFAFBFC); val grey = Color(0xFFD7DCE3)
            suit(coat, a, hem = 590f, shade = grey)
            cuffs(grey, a)
            // Lapels in a V, a pocket with a pen, the stethoscope round his neck.
            inkLine(path { moveTo(200f, 380f); lineTo(300f, 500f); lineTo(330f, 600f) }, 7f, a * 0.8f)
            inkLine(path { moveTo(440f, 366f); lineTo(350f, 500f) }, 7f, a * 0.8f)
            val pocket = path { addRoundRect(RoundRect(372f, 480f, 444f, 540f, CornerRadius(8f))) }
            wearShape(pocket, coat, a, 5f)
            drawRoundRect(Color(0xFF4F7FD8), Offset(388f, 456f), Size(12f, 44f), CornerRadius(5f), alpha = a)
            val tube = path { moveTo(200f, 380f); quadraticTo(240f, 520f, 300f, 540f); moveTo(440f, 366f); quadraticTo(420f, 500f, 300f, 540f) }
            drawPath(tube, WorldInk, alpha = a, style = Stroke(18f, cap = StrokeCap.Round))
            drawPath(tube, Color(0xFF6B7180), alpha = a, style = Stroke(9f, cap = StrokeCap.Round))
            drawCircle(WorldInk, 24f, Offset(300f, 552f), alpha = a); drawCircle(Silver, 17f, Offset(300f, 552f), alpha = a)
        }
        WearItem.FIREFIGHTER -> {
            val tan = Color(0xFFE0B24A); val deep = Color(0xFFB98A2E)
            suit(tan, a, hem = 580f, shade = deep)
            cuffs(Color(0xFFDDE2E8), a)
            for (yy in listOf(510f, 556f)) clipPath(torso(580f)) {
                drawRect(Color(0xFFDDE2E8), Offset(100f, yy - 10f), Size(420f, 20f), alpha = a)
                inkLine(path { moveTo(100f, yy - 10f); lineTo(520f, yy - 10f) }, 3f, a * 0.4f)
            }
            for (sl in listOf(600f)) {
                drawRect(Color(0xFFDDE2E8), Offset(246f, sl - 8f), Size(94f, 16f), alpha = a)
                drawRect(Color(0xFFDDE2E8), Offset(366f, sl - 8f), Size(96f, 16f), alpha = a)
            }
            for (yy in listOf(440f, 480f)) { drawRoundRect(WorldInk, Offset(306f, yy - 9f), Size(50f, 18f), CornerRadius(4f), alpha = a); drawRoundRect(Color(0xFF8A8D94), Offset(310f, yy - 6f), Size(42f, 12f), CornerRadius(3f), alpha = a) }
            shine(170f, 440f, 18f, 50f, a)
        }
        WearItem.RAINBOW_DRESS -> {
            wearShape(bodice(), White, a)
            val skirt = skirt(waist = 500f, hem = 660f, left = 56f, right = 556f, scallops = 8)
            wearShape(skirt, Stripes[0], a)
            clipPath(skirt) { Stripes.forEachIndexed { i, c -> drawRect(c, Offset(40f, 500f + i * 28f), Size(540f, 30f), alpha = a) } }
            drawPath(skirt, WorldInk, alpha = a, style = Stroke(12f))
            for ((x, y) in listOf(250f to 430f, 380f to 420f, 320f to 470f)) sparkle(Offset(x, y), 18f, Gold, a)
            for ((x, y) in listOf(110f to 640f, 300f to 620f, 470f to 640f)) sparkle(Offset(x, y), 14f, White, a)
        }
        else -> {}
    }
}

/** A tutu round his waist: three frilly layers of [c], [deep] lines; [wide] for the ballerina's bigger skirt. */
private fun DrawScope.tutu(c: Color, deep: Color, a: Float, wide: Boolean = false) {
    val w = if (wide) 1.18f else 1f
    for ((layer, y) in listOf(0 to 540f, 1 to 520f, 2 to 500f)) {
        val half = (230f - layer * 22f) * w
        val ruffle = path {
            val cx = 310f
            moveTo(cx - half, y)
            val n = 9
            for (i in 0 until n) {
                val x0 = cx - half + i * (2 * half / n)
                quadraticTo(x0 + half / n, y + 70f - layer * 6f, x0 + 2 * half / n, y)
            }
            quadraticTo(cx, y - 44f, cx - half, y)
            close()
        }
        wearShape(ruffle, if (layer == 1) c.copy(red = minOf(1f, c.red + 0.04f)) else c, a, 6f)
        clipPath(ruffle) { for (k in -4..4) inkLine(path { moveTo(310f + k * 40f * w, y - 20f); lineTo(310f + k * 54f * w, y + 60f) }, 3f, a * 0.35f, deep) }
    }
    val band = path { moveTo(160f, 492f); quadraticTo(310f, 530f, 466f, 488f) }
    drawPath(band, WorldInk, alpha = a, style = Stroke(26f, cap = StrokeCap.Round))
    drawPath(band, deep, alpha = a, style = Stroke(14f, cap = StrokeCap.Round))
}

/** A little bow at [c]. */
private fun DrawScope.bow(c: Offset, color: Color, a: Float) {
    val loops = path {
        moveTo(c.x, c.y); quadraticTo(c.x - 44f, c.y - 36f, c.x - 46f, c.y); quadraticTo(c.x - 44f, c.y + 34f, c.x, c.y)
        moveTo(c.x, c.y); quadraticTo(c.x + 44f, c.y - 36f, c.x + 46f, c.y); quadraticTo(c.x + 44f, c.y + 34f, c.x, c.y)
    }
    wearShape(loops, color, a, 5f)
    drawCircle(WorldInk, 12f, c, alpha = a); drawCircle(color, 8f, c, alpha = a)
}

/** A seashell at [c] (the mermaid's top). */
private fun DrawScope.shell(c: Offset, a: Float) {
    val s = path { moveTo(c.x - 46f, c.y + 22f); quadraticTo(c.x - 50f, c.y - 44f, c.x, c.y - 46f); quadraticTo(c.x + 50f, c.y - 44f, c.x + 46f, c.y + 22f); quadraticTo(c.x, c.y + 36f, c.x - 46f, c.y + 22f); close() }
    wearShape(s, Color(0xFFC6A8EE), a, 6f)
    for (k in -2..2) inkLine(path { moveTo(c.x, c.y + 24f); lineTo(c.x + k * 18f, c.y - 36f) }, 4f, a * 0.5f, Color(0xFF8D6FCB))
}

