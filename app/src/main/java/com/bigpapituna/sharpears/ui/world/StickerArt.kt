package com.bigpapituna.sharpears.ui.world

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.WorldStore
import com.bigpapituna.sharpears.engine.world.Rarity
import com.bigpapituna.sharpears.engine.world.Sticker
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Rarity is data, like the hand colours: a cool blue for rare, a prism for shiny. Commons need no colour. */
val RareBlue = Color(0xFF7FB2E5)
val ShinyPrism = listOf(Color(0xFFFF9AA2), Color(0xFFFFD27F), Color(0xFFB5EAD7), Color(0xFF9AD0F5), Color(0xFFC7B3F2), Color(0xFFFF9AA2))

/** The colour a rarity shows in rings, card backs and labels. */
fun Rarity.colour(): Color = when (this) { Rarity.COMMON -> Color(0xFFD9C7AE); Rarity.RARE -> RareBlue; Rarity.SHINY -> Color(0xFFC7B3F2) }

/** The drawable of [sticker]. */
fun stickerRes(sticker: Sticker): Int = WorldStore.stickerRes(sticker)

/** A slight, stable tilt per sticker (degrees), as if stuck in by hand. */
fun Sticker.tilt(): Float = (((id.hashCode() % 9) + 9) % 9 - 4) * 1.6f

/**
 * A sticker as it sits in the album or the world: its picture with a crisp soft shadow, a little tilted. A rare one
 * has a cool glow behind it; a shiny one a prism sheen sweeping across it and twinkles around it ([shine]; still with
 * animations off, the sheen then rests across it).
 */
@Composable
fun StickerImage(sticker: Sticker, modifier: Modifier = Modifier, tilt: Float = sticker.tilt(), shine: Boolean = true, shadow: Boolean = true) {
    val still = reducedMotion()
    val shiny = shine && sticker.rarity == Rarity.SHINY
    val t: State<Float> = if (shiny && !still) rememberInfiniteTransition(label = "shine")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    else remember { mutableFloatStateOf(0.45f) }
    Box(modifier.graphicsLayer { rotationZ = tilt }) {
        if (shine && sticker.rarity == Rarity.RARE) Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(RareBlue.copy(alpha = 0.45f), Color.Transparent)), radius = size.minDimension * 0.62f)
        }
        val painter = painterResource(stickerRes(sticker))
        if (shadow) Image(painter, contentDescription = null, colorFilter = ColorFilter.tint(Color(0x2E3A2418), BlendMode.SrcIn),
            modifier = Modifier.fillMaxSize().offset(1.5.dp, 2.5.dp))
        Image(painter, contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (shiny) {
                    // A diagonal prism band sweeping across, only where the sticker is (SrcAtop).
                    val p = t.value * 2.2f - 0.6f
                    val w = size.width
                    drawRect(Brush.linearGradient(
                        0f to Color.Transparent,
                        (p - 0.18f).coerceIn(0f, 1f) to Color.Transparent,
                        (p - 0.09f).coerceIn(0f, 1f) to ShinyPrism[0].copy(alpha = 0.55f),
                        p.coerceIn(0f, 1f) to ShinyPrism[2].copy(alpha = 0.6f),
                        (p + 0.09f).coerceIn(0f, 1f) to ShinyPrism[4].copy(alpha = 0.55f),
                        (p + 0.18f).coerceIn(0f, 1f) to Color.Transparent,
                        1f to Color.Transparent,
                        start = Offset(0f, 0f), end = Offset(w, size.height)), blendMode = BlendMode.SrcAtop)
                }
            })
        if (shiny) Canvas(Modifier.fillMaxSize()) { twinkles(t.value, still) }
    }
}

/** Four twinkles around a shiny sticker, each pulsing in turn. */
private fun DrawScope.twinkles(t: Float, still: Boolean) {
    val spots = listOf(Offset(0.12f, 0.18f), Offset(0.88f, 0.12f), Offset(0.9f, 0.82f), Offset(0.16f, 0.86f))
    spots.forEachIndexed { i, o ->
        val k = if (still) 0.8f else (0.5f + 0.5f * sin((t + i * 0.25f) * 2f * PI.toFloat())).coerceIn(0f, 1f)
        sparkle(Offset(o.x * size.width, o.y * size.height), size.minDimension * (0.05f + 0.05f * k), Color.White, alpha = 0.35f + 0.65f * k)
    }
}

/** A four-point sparkle of radius [r] at [c]. */
fun DrawScope.sparkle(c: Offset, r: Float, color: Color, alpha: Float = 1f) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + r * 0.16f, c.y - r * 0.16f, c.x + r, c.y)
        quadraticTo(c.x + r * 0.16f, c.y + r * 0.16f, c.x, c.y + r)
        quadraticTo(c.x - r * 0.16f, c.y + r * 0.16f, c.x - r, c.y)
        quadraticTo(c.x - r * 0.16f, c.y - r * 0.16f, c.x, c.y - r)
        close()
    }
    drawPath(p, color, alpha = alpha.coerceIn(0f, 1f))
}

/** A sticker's shape only, in [color]: a gap in the album, the mystery in the "one more" preview. */
@Composable
fun StickerSilhouette(sticker: Sticker, color: Color, modifier: Modifier = Modifier, tilt: Float = 0f) {
    Image(painterResource(stickerRes(sticker)), contentDescription = null, colorFilter = ColorFilter.tint(color, BlendMode.SrcIn),
        modifier = modifier.graphicsLayer { rotationZ = tilt })
}

/** A burst of [n] sparkles flying out from the centre at progress [p] (0..1), for a shiny reveal. */
fun DrawScope.sparkleBurst(p: Float, n: Int = 14, colours: List<Color> = ShinyPrism) {
    if (p <= 0f || p >= 1f) return
    val c = Offset(size.width / 2f, size.height / 2f)
    for (i in 0 until n) {
        val a = i * 2f * PI.toFloat() / n + (i % 3) * 0.2f
        val d = size.minDimension * (0.35f + 0.35f * ((i * 7) % 5) / 4f) * (1f - (1f - p) * (1f - p))
        val r = size.minDimension * 0.04f * (1f - p * 0.6f)
        sparkle(Offset(c.x + cos(a) * d, c.y + sin(a) * d), r, colours[i % colours.size], alpha = 1f - p)
    }
}
