package com.bigpapituna.sharpears.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** A tile of fine grain, made once: random specks of grey, repeated across the screen. */
private val Grain: ImageBitmap by lazy {
    val n = 160
    val r = java.util.Random(7)
    val px = IntArray(n * n) { val v = r.nextInt(256); (v shl 24) or 0xFFFFFF }   // white specks, random strength
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * The page the app sits on: the theme background with a fine paper grain, a soft gold light from the top and faint
 * paw prints scattered across it (the pup has been here). All of it quiet, so text and controls stay in front.
 * The note lane and keyboard keep their plain dark stage.
 */
@Composable
fun Modifier.paper(): Modifier {
    val bg = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    val dark = bg.luminance() < 0.5f
    val grain = remember { ShaderBrush(ImageShader(Grain, TileMode.Repeated, TileMode.Repeated)) }
    return drawWithCache {
        val glow = Brush.radialGradient(listOf(PupGold.copy(alpha = if (dark) 0.07f else 0.16f), Color.Transparent),
            center = Offset(size.width * 0.5f, -size.width * 0.15f), radius = size.width * 1.1f)
        val step = 150.dp.toPx()
        val paw = ink.copy(alpha = if (dark) 0.035f else 0.045f)
        onDrawBehind {
            drawRect(bg)
            drawRect(glow)
            // Grain: light specks on dark, darkening specks on light (the same tile, blended the other way).
            drawRect(grain, alpha = if (dark) 0.035f else 0.05f,
                blendMode = if (dark) androidx.compose.ui.graphics.BlendMode.Screen else androidx.compose.ui.graphics.BlendMode.Multiply)
            // Paw prints on a staggered grid, each turned a little, as if the pup wandered across the page.
            var row = 0
            var y = step * 0.4f
            while (y < size.height + step) {
                var x = if (row % 2 == 0) step * 0.3f else step * 0.85f
                while (x < size.width + step) {
                    val turn = if ((row + (x / step).toInt()) % 2 == 0) -22f else 18f
                    rotate(turn, Offset(x, y)) { drawPaw(Offset(x, y), 1.dp.toPx(), paw) }
                    x += step * 1.1f
                }
                y += step * 0.75f; row++
            }
        }
    }
}

/** One paw print centred on [c]: the pad and four toes. [u] is one dp in pixels. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPaw(c: Offset, u: Float, color: Color) {
    drawOval(color, Offset(c.x - 7f * u, c.y - 3f * u), Size(14f * u, 11f * u))
    listOf(-8.5f to -6f, -3.2f to -10.5f, 3.2f to -10.5f, 8.5f to -6f).forEach { (dx, dy) ->
        drawOval(color, Offset(c.x + dx * u - 2.6f * u, c.y + dy * u - 3.2f * u), Size(5.2f * u, 6.4f * u))
    }
}
