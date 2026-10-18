package com.bigpapituna.sharpears.ui.skin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.bigpapituna.sharpears.engine.skin.Animal

/**
 * The seven key animals as small flat faces drawn in code (C cat, D dog, E elephant, F frog, G goat, A ant, B bear),
 * for kids who cannot read yet. Decided 2026-10-18: pictures, not words. A pre-reader cannot read "cat", and a word
 * does not fit a key; a silhouette reads from 18 dp up, which is about the smallest a falling note gets on the Fold.
 *
 * Each face is one [ink] silhouette with its eyes and details cut out in [hole] (the colour underneath, so it reads as
 * a sticker), centred on [center] and [size] px across. Shapes sit in a -1..1 box scaled by size / 2; nothing is
 * allocated but the one reused [path].
 */
fun DrawScope.drawAnimal(animal: Animal, center: Offset, size: Float, ink: Color, hole: Color, path: Path = Path()) {
    val r = size / 2f
    fun p(x: Float, y: Float) = Offset(center.x + x * r, center.y + y * r)
    fun circle(c: Color, x: Float, y: Float, rad: Float) = drawCircle(c, rad * r, p(x, y))
    fun oval(c: Color, x: Float, y: Float, w: Float, h: Float) = drawOval(c, p(x - w / 2, y - h / 2), Size(w * r, h * r))
    fun eyes(y: Float, dx: Float, rad: Float) { circle(hole, -dx, y, rad); circle(hole, dx, y, rad) }
    fun tri(c: Color, a: Offset, b: Offset, d: Offset) {
        path.reset(); path.moveTo(a.x, a.y); path.lineTo(b.x, b.y); path.lineTo(d.x, d.y); path.close(); drawPath(path, c)
    }
    val line = Stroke(width = r * 0.11f, cap = StrokeCap.Round)
    when (animal) {
        Animal.CAT -> {
            tri(ink, p(-0.66f, -0.12f), p(-0.52f, -0.92f), p(-0.08f, -0.5f))
            tri(ink, p(0.66f, -0.12f), p(0.52f, -0.92f), p(0.08f, -0.5f))
            oval(ink, 0f, 0.14f, 1.42f, 1.24f)
            tri(hole, p(-0.5f, -0.26f), p(-0.46f, -0.66f), p(-0.24f, -0.44f))      // inner ears
            tri(hole, p(0.5f, -0.26f), p(0.46f, -0.66f), p(0.24f, -0.44f))
            oval(hole, -0.27f, 0.04f, 0.2f, 0.28f); oval(hole, 0.27f, 0.04f, 0.2f, 0.28f)
            tri(hole, p(-0.1f, 0.3f), p(0.1f, 0.3f), p(0f, 0.44f))                  // nose
            for (s in listOf(-1f, 1f)) for (dy in listOf(-0.06f, 0.1f)) {             // whiskers reach past the face
                drawLine(ink, p(0.5f * s, 0.36f + dy * 0.5f), p(0.98f * s, 0.3f + dy), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
                drawLine(hole, p(0.18f * s, 0.38f + dy * 0.4f), p(0.5f * s, 0.36f + dy * 0.5f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
            }
        }
        Animal.DOG -> {
            rotate(18f, p(-0.62f, 0.05f)) { oval(ink, -0.62f, 0.05f, 0.42f, 0.96f) }     // floppy ears
            rotate(-18f, p(0.62f, 0.05f)) { oval(ink, 0.62f, 0.05f, 0.42f, 0.96f) }
            circle(ink, 0f, 0.04f, 0.6f)
            eyes(-0.08f, 0.24f, 0.1f)
            oval(hole, 0f, 0.38f, 0.56f, 0.38f)                                          // muzzle
            oval(ink, 0f, 0.3f, 0.24f, 0.16f)                                            // nose
            drawLine(ink, p(0f, 0.36f), p(0f, 0.5f), strokeWidth = r * 0.06f, cap = StrokeCap.Round)
        }
        Animal.ELEPHANT -> {
            circle(ink, -0.6f, -0.1f, 0.42f); circle(ink, 0.6f, -0.1f, 0.42f)          // big ears
            circle(hole, -0.64f, -0.1f, 0.26f); circle(hole, 0.64f, -0.1f, 0.26f)
            circle(ink, 0f, -0.1f, 0.5f)
            eyes(-0.2f, 0.2f, 0.085f)
            // The trunk: a thick curl down and to the side.
            path.reset()
            path.moveTo(p(0f, 0.2f).x, p(0f, 0.2f).y)
            path.cubicTo(p(0.02f, 0.62f).x, p(0.02f, 0.62f).y, p(-0.06f, 0.86f).x, p(-0.06f, 0.86f).y, p(0.2f, 0.92f).x, p(0.2f, 0.92f).y)
            drawPath(path, ink, style = Stroke(width = r * 0.3f, cap = StrokeCap.Round))
        }
        Animal.FROG -> {
            circle(ink, -0.42f, -0.4f, 0.3f); circle(ink, 0.42f, -0.4f, 0.3f)          // eye bumps on top
            oval(ink, 0f, 0.18f, 1.7f, 1.06f)
            circle(hole, -0.42f, -0.42f, 0.18f); circle(hole, 0.42f, -0.42f, 0.18f)
            circle(ink, -0.42f, -0.4f, 0.08f); circle(ink, 0.42f, -0.4f, 0.08f)       // pupils
            drawArc(hole, 20f, 140f, false, p(-0.55f, -0.08f), Size(1.1f * r, 0.62f * r), style = Stroke(width = r * 0.1f, cap = StrokeCap.Round))
        }
        Animal.GOAT -> {
            // Horns sweeping back, then ears out to the side, a long face and a little beard.
            for (s in listOf(-1f, 1f)) {
                path.reset()
                path.moveTo(p(0.16f * s, -0.4f).x, p(0.16f * s, -0.4f).y)
                path.cubicTo(p(0.2f * s, -0.86f).x, p(0.2f * s, -0.86f).y, p(0.56f * s, -0.98f).x, p(0.56f * s, -0.98f).y, p(0.68f * s, -0.7f).x, p(0.68f * s, -0.7f).y)
                drawPath(path, ink, style = Stroke(width = r * 0.17f, cap = StrokeCap.Round))
                rotate(-22f * s, p(0.56f * s, -0.18f)) { oval(ink, 0.56f * s, -0.18f, 0.56f, 0.24f) }
            }
            oval(ink, 0f, 0.08f, 0.9f, 1.2f)
            eyes(-0.06f, 0.2f, 0.09f)
            oval(hole, -0.1f, 0.42f, 0.08f, 0.1f); oval(hole, 0.1f, 0.42f, 0.08f, 0.1f)  // nostrils
            tri(ink, p(-0.14f, 0.6f), p(0.14f, 0.6f), p(0f, 0.98f))                      // beard
        }
        Animal.ANT -> {
            // Seen from the side: head, middle, big back end, six legs and two feelers.
            for (dx in listOf(-0.2f, 0.05f, 0.3f)) {
                drawLine(ink, p(0.04f + dx * 0.3f, 0.06f), p(-0.1f + dx * 1.6f, 0.62f), strokeWidth = r * 0.08f, cap = StrokeCap.Round)
            }
            drawLine(ink, p(0.48f, -0.4f), p(0.34f, -0.86f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
            drawLine(ink, p(0.6f, -0.38f), p(0.84f, -0.78f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
            circle(ink, 0.56f, -0.2f, 0.27f)
            circle(ink, 0.06f, 0.06f, 0.2f)
            oval(ink, -0.5f, 0.3f, 0.82f, 0.62f)
            circle(hole, 0.64f, -0.26f, 0.07f)
        }
        Animal.BEAR -> {
            circle(ink, -0.48f, -0.46f, 0.26f); circle(ink, 0.48f, -0.46f, 0.26f)
            circle(hole, -0.48f, -0.46f, 0.12f); circle(hole, 0.48f, -0.46f, 0.12f)
            circle(ink, 0f, 0.06f, 0.64f)
            eyes(-0.08f, 0.24f, 0.09f)
            oval(hole, 0f, 0.34f, 0.52f, 0.38f)
            oval(ink, 0f, 0.26f, 0.22f, 0.15f)
        }
    }
}

