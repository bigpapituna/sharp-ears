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
import androidx.compose.ui.graphics.drawscope.rotate
import com.bigpapituna.sharpears.engine.world.WearItem

/*
 * Things for his feet and his back, in base-image pixels (ui/world/Outfit.kt): boots over the front paws (left 240 to
 * 346, right 368 to 463, from y 640 to 707), and what he carries on his back, which peeks out round him (drawn under
 * him) with its straps over his chest.
 */

/** A boot over each front paw, from [top] down, in [c]; [toe] is the sole's colour. */
private fun DrawScope.boots(c: Color, toe: Color, a: Float, top: Float = 590f) {
    for ((l, r) in listOf(236f to 350f, 362f to 468f)) {
        val boot = path { moveTo(l + 6f, top); lineTo(r - 4f, top); lineTo(r + 4f, 690f); quadraticTo(r + 4f, 712f, r - 20f, 712f); lineTo(l + 12f, 712f); quadraticTo(l - 10f, 712f, l - 6f, 690f); close() }
        wearShape(boot, c, a)
        drawRect(toe, Offset(l - 4f, 696f), Size(r - l + 8f, 14f), alpha = a)
        inkLine(path { moveTo(l - 4f, 696f); lineTo(r + 4f, 696f) }, 5f, a)
        shine(l + 10f, top + 12f, 14f, 40f, a)
    }
}

/** Two straps over his chest from the shoulders, in [c]. */
private fun DrawScope.straps(c: Color, a: Float) {
    for (s in listOf(path { moveTo(204f, 372f); quadraticTo(180f, 450f, 196f, 540f) }, path { moveTo(436f, 360f); quadraticTo(462f, 450f, 452f, 530f) })) {
        drawPath(s, WorldInk, alpha = a, style = Stroke(36f, cap = StrokeCap.Round))
        drawPath(s, c, alpha = a, style = Stroke(22f, cap = StrokeCap.Round))
    }
    for (b in listOf(Offset(188f, 470f), Offset(452f, 456f))) {
        drawRoundRect(WorldInk, Offset(b.x - 20f, b.y - 14f), Size(40f, 28f), CornerRadius(6f), alpha = a)
        drawRoundRect(Silver, Offset(b.x - 14f, b.y - 8f), Size(28f, 16f), CornerRadius(4f), alpha = a)
    }
}

internal fun DrawScope.backBehind(item: WearItem, a: Float) {
    when (item) {
        WearItem.WINGS -> {
            val tints = listOf(Color(0xFFF7C6E0), Color(0xFFC6E6F7))
            for (side in listOf(-1f, 1f)) {
                val cx = if (side < 0) 150f else 470f
                for ((k, c, sz) in listOf(Triple(-1, Offset(cx + side * 110f, 330f), Size(230f, 170f)), Triple(1, Offset(cx + side * 90f, 470f), Size(170f, 130f)))) {
                    rotate(side * (if (k < 0) -24f else 18f), c) {
                        val wing = path { addOval(Rect(c.x - sz.width / 2f, c.y - sz.height / 2f, c.x + sz.width / 2f, c.y + sz.height / 2f)) }
                        wearShape(wing, tints[if (k < 0) 0 else 1].copy(alpha = 0.92f), a, 7f)
                        drawPath(wing, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), c, sz.width / 2f), alpha = a)
                        inkLine(path { moveTo(c.x - side * sz.width * 0.42f, c.y); quadraticTo(c.x, c.y - sz.height * 0.2f, c.x + side * sz.width * 0.3f, c.y - sz.height * 0.2f) }, 4f, a * 0.4f)
                    }
                }
                sparkle(Offset(cx + side * 200f, 270f), 16f, Gold, a)
                sparkle(Offset(cx + side * 170f, 530f), 12f, Color.White, a)
            }
        }
        WearItem.BACKPACK -> {
            val c = Color(0xFFF6A654); val deep = Color(0xFFD98236)
            val pack = path { addRoundRect(RoundRect(92f, 300f, 524f, 600f, CornerRadius(70f))) }
            wearShape(pack, c, a)
            drawRoundRect(deep, Offset(92f, 300f), Size(60f, 300f), CornerRadius(30f), alpha = a * 0.5f)
            drawRoundRect(deep, Offset(464f, 300f), Size(60f, 300f), CornerRadius(30f), alpha = a * 0.5f)
            inkLine(path { moveTo(110f, 400f); lineTo(510f, 400f) }, 6f, a * 0.5f)
        }
        WearItem.ROCKET_PACK -> {
            for (x in listOf(92f, 528f)) {
                val tank = path { addRoundRect(RoundRect(x - 44f, 330f, x + 44f, 560f, CornerRadius(40f))) }
                wearShape(tank, Silver, a)
                drawPath(tank, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent, SilverDeep.copy(alpha = 0.6f)), x - 44f, x + 44f), alpha = a)
                val cap = path { moveTo(x - 44f, 360f); quadraticTo(x, 270f, x + 44f, 360f); close() }
                wearShape(cap, Color(0xFFE5484D), a)
                val flame = path { moveTo(x - 30f, 566f); quadraticTo(x - 20f, 640f, x, 676f); quadraticTo(x + 20f, 640f, x + 30f, 566f); close() }
                drawPath(flame, Color(0xFFF6A654), alpha = a)
                drawPath(path { moveTo(x - 16f, 566f); quadraticTo(x - 8f, 610f, x, 636f); quadraticTo(x + 8f, 610f, x + 16f, 566f); close() }, Color(0xFFFCE070), alpha = a)
                drawRoundRect(WorldInk, Offset(x - 32f, 552f), Size(64f, 20f), CornerRadius(6f), alpha = a)
            }
        }
        WearItem.GUITAR -> {
            // On his back, slung low on the left: the body peeks out by his tail, the neck up past his right shoulder.
            val wood = Color(0xFFE08A3C); val deep = Color(0xFFB8652A)
            val neck = path { moveTo(110f, 560f); lineTo(520f, 286f) }
            drawPath(neck, WorldInk, alpha = a, style = Stroke(46f, cap = StrokeCap.Round))
            drawPath(neck, Color(0xFF8A5A3A), alpha = a, style = Stroke(30f, cap = StrokeCap.Round))
            val head = path { addRoundRect(RoundRect(500f, 240f, 570f, 300f, CornerRadius(14f))) }
            rotate(-34f, Offset(530f, 276f)) { wearShape(head, Color(0xFF8A5A3A), a, 6f); for (k in 0..2) drawCircle(Silver, 7f, Offset(512f + k * 22f, 254f), alpha = a) }
            val body = path { addOval(Rect(14f, 520f, 150f, 660f)); addOval(Rect(36f, 456f, 136f, 556f)) }
            wearShape(body, wood, a)
            drawCircle(Color(0xFF4A2E1E), 26f, Offset(92f, 548f), alpha = a)
            drawRect(Color(0xFF6B4026), Offset(56f, 604f), Size(64f, 12f), alpha = a)
            drawPath(body, Brush.radialGradient(listOf(Color.Transparent, deep.copy(alpha = 0.6f)), Offset(80f, 580f), 90f), alpha = a)
        }
        else -> {}
    }
}

internal fun DrawScope.backFront(item: WearItem, a: Float) {
    when (item) {
        WearItem.BOOTS -> boots(Color(0xFFE5484D), Color(0xFF4A2E1E), a)
        WearItem.SKATES -> {
            boots(Color(0xFFFFFDF8), Color(0xFFF59BC0), a, top = 606f)
            for ((l, r) in listOf(236f to 350f, 362f to 468f)) {
                for (k in 0..2) inkLine(path { moveTo(l + 30f, 620f + k * 22f); lineTo(r - 30f, 630f + k * 22f) }, 5f, a, Color(0xFFF59BC0))
                for (x in listOf(l + 18f, l + (r - l) / 2f, r - 18f)) { drawCircle(WorldInk, 15f, Offset(x, 716f), alpha = a); drawCircle(Color(0xFF8FD6C2), 9f, Offset(x, 716f), alpha = a) }
            }
        }
        WearItem.SLIPPERS -> {
            val pink = Color(0xFFF7B6CF)
            for ((l, r) in listOf(238f to 348f, 364f to 466f)) {
                for (k in 0..1) inkLine(path { moveTo(l + 12f + k * 70f, 662f); lineTo(l + 78f - k * 70f, 590f) }, 8f, a, Color(0xFFE47EA3))
                val shoe = path { moveTo(l - 4f, 668f); quadraticTo((l + r) / 2f, 650f, r + 4f, 668f); lineTo(r + 4f, 694f); quadraticTo(r, 712f, r - 20f, 712f); lineTo(l + 14f, 712f); quadraticTo(l - 8f, 712f, l - 4f, 694f); close() }
                wearShape(shoe, pink, a, 7f)
                bowSmall(Offset((l + r) / 2f, 668f), a)
            }
        }
        WearItem.BACKPACK -> straps(Color(0xFFD98236), a)
        WearItem.ROCKET_PACK -> straps(Color(0xFF8A8D94), a)
        WearItem.GUITAR -> {
            val strap = path { moveTo(150f, 540f); quadraticTo(300f, 470f, 450f, 352f) }
            drawPath(strap, WorldInk, alpha = a, style = Stroke(30f, cap = StrokeCap.Round))
            drawPath(strap, Color(0xFFE5484D), alpha = a, style = Stroke(18f, cap = StrokeCap.Round))
            for (k in 0..4) drawCircle(Color(0xFFFCE070), 4f, Offset(190f + k * 56f, 520f - k * 36f - k * k * 2f), alpha = a)
        }
        else -> {}
    }
}

private fun DrawScope.bowSmall(c: Offset, a: Float) {
    val loops = path {
        moveTo(c.x, c.y); quadraticTo(c.x - 26f, c.y - 22f, c.x - 26f, c.y); quadraticTo(c.x - 26f, c.y + 20f, c.x, c.y)
        moveTo(c.x, c.y); quadraticTo(c.x + 26f, c.y - 22f, c.x + 26f, c.y); quadraticTo(c.x + 26f, c.y + 20f, c.x, c.y)
    }
    wearShape(loops, Color(0xFFE47EA3), a, 4f)
}
