package com.bigpapituna.sharpears.ui.theory

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.theory.PostureCue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * The player at the bench and the foot on the pedal, in the "soft realistic" look chosen on the art-styles branch
 * (ui/theory/HandStyles.kt there) so they sit with the painted hand: no outlines, gentle gradients, a rim of light, faint
 * creases and soft blurred shadows. Side views on 2D skeletons; the posture scene drives the figure's pose and the
 * pedal scene the foot, both still pure functions of their inputs.
 */

/** A colour with its shade and its light, the three a painter needs. */
private class Tone(val base: Color, val shade: Color, val light: Color)

private val SkinT = Tone(Color(0xFFD9A07A), Color(0xFFBF865E), Color(0xFFEFC8A6))   // matched to the painted hand
private val Blush = Color(0xFFE48A76)
private val Sweater = Tone(Color(0xFF8C6450), Color(0xFF6C4A3A), Color(0xFFA87F69))
private val Trousers = Tone(Color(0xFF57514B), Color(0xFF413C37), Color(0xFF6F6861))
private val ShoeLeather = Tone(Color(0xFF74492F), Color(0xFF553320), Color(0xFF946449))
private val Sock = Tone(Color(0xFFE4D5BC), Color(0xFFC9B699), Color(0xFFF3E9D7))
private val Hair = Tone(Color(0xFF5C3B26), Color(0xFF442A19), Color(0xFF7E5639))
private val BenchWood = Tone(Color(0xFFBB895D), Color(0xFF946442), Color(0xFFD6A87D))
private val Case = Tone(Color(0xFF4B3C33), Color(0xFF33281F), Color(0xFF66544A))
private val Ivory = Tone(Color(0xFFF8F3EA), Color(0xFFE0D6C8), Color(0xFFFFFFFF))
private val Ebony = Tone(Color(0xFF2B2421), Color(0xFF181310), Color(0xFF4C423C))
private val Brass = Tone(Color(0xFFD3AA60), Color(0xFFA67E3B), Color(0xFFEFD192))
private val Paper = Tone(Color(0xFFFBF7EE), Color(0xFFE6DDCD), Color(0xFFFFFFFF))
private val SoleT = Tone(Color(0xFF3E2A1E), Color(0xFF2C1D14), Color(0xFF5A4030))
private val Line = Color(0xFF3A2416)

// ---- Geometry ----------------------------------------------------------------------------------------------------

private fun deg(d: Float) = (d * PI / 180.0).toFloat()
private fun lerpF(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun lerpO(a: Offset, b: Offset, f: Float) = Offset(lerpF(a.x, b.x, f), lerpF(a.y, b.y, f))
private fun Offset.unit(): Offset { val d = getDistance(); return if (d < 1e-4f) Offset(0f, -1f) else this / d }
private fun Offset.perp() = Offset(-y, x)

/** A tapered capsule: the hull of a circle of radius [ra] at [a] and one of [rb] at [b]. */
private fun capsule(a: Offset, ra: Float, b: Offset, rb: Float): Path {
    val d = b - a
    val len = d.getDistance()
    val p = Path()
    if (len < abs(ra - rb) + 0.01f) { if (ra >= rb) p.addOval(Rect(a, ra)) else p.addOval(Rect(b, rb)); return p }
    val phi = Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat()
    val th = Math.toDegrees(acos(((ra - rb) / len).coerceIn(-1f, 1f)).toDouble()).toFloat()
    p.arcTo(Rect(a, ra), phi + th, 360f - 2 * th, true)
    p.arcTo(Rect(b, rb), phi - th, 2 * th, false)
    p.close()
    return p
}

private fun circle(c: Offset, r: Float) = Path().apply { addOval(Rect(c, r)) }
private fun oval(c: Offset, rx: Float, ry: Float) = Path().apply { addOval(Rect(c.x - rx, c.y - ry, c.x + rx, c.y + ry)) }
private fun polygon(pts: List<Offset>) = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y); close() }
private fun union(paths: List<Path>): Path = paths.reduce { acc, p -> Path().apply { op(acc, p, PathOperation.Union) } }
private fun intersect(a: Path, b: Path) = Path().apply { op(a, b, PathOperation.Intersect) }
private fun curve(a: Offset, c: Offset, b: Offset) = Path().apply { moveTo(a.x, a.y); quadraticTo(c.x, c.y, b.x, b.y) }

/** A smooth closed curve through [pts] (Catmull-Rom as cubics). */
private fun smoothClosed(pts: List<Offset>, k: Float = 1f): Path {
    val n = pts.size
    val p = Path()
    p.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until n) {
        val p0 = pts[(i - 1 + n) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]; val p3 = pts[(i + 2) % n]
        val c1 = p1 + (p2 - p0) * (k / 6f); val c2 = p2 - (p3 - p1) * (k / 6f)
        p.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    p.close()
    return p
}

// ---- The soft painter --------------------------------------------------------------------------------------------

/** [path] in [color], blurred by [radius] px (a mask blur), moved by [at]; a stroke of [stroke] px when above 0. */
private fun DrawScope.blurred(path: Path, color: Color, radius: Float, at: Offset = Offset.Zero, stroke: Float = 0f) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (radius >= 0.5f) maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        if (stroke > 0f) { style = android.graphics.Paint.Style.STROKE; strokeWidth = stroke; strokeJoin = android.graphics.Paint.Join.ROUND }
    }
    drawIntoCanvas { c ->
        c.nativeCanvas.save(); c.nativeCanvas.translate(at.x, at.y)
        c.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        c.nativeCanvas.restore()
    }
}

/** Fills [path] with [tone]: light from the upper left, the shade gathering on the lower right ([shift] px wide). */
private fun DrawScope.paint(path: Path, tone: Tone, shift: Float) {
    val b = path.getBounds()
    drawPath(path, tone.shade)
    clipPath(path) {
        blurred(path, tone.base, shift * 0.9f, Offset(-shift, -shift * 0.6f))
        drawRect(Brush.verticalGradient(listOf(tone.light.copy(alpha = 0.32f), tone.light.copy(alpha = 0f)), b.top, b.top + b.height * 0.6f), b.topLeft, b.size)
        // The rim: a thin warm light along the shaded edge.
        blurred(path, tone.light.copy(alpha = 0.55f), 0.9.dp.toPx(), Offset(-1.2.dp.toPx(), -1.2.dp.toPx()), stroke = 2.6.dp.toPx())
    }
}

/** A shadow cast on a surface, blurred by [soft] px. */
private fun DrawScope.castShadow(path: Path, soft: Float, alpha: Float) = blurred(path, Color.Black.copy(alpha = (alpha * 1.25f).coerceAtMost(1f)), soft)

/** A thin detail line (crease, lace, seam). */
private fun DrawScope.detail(path: Path, shade: Color, width: Float = 1.5f, alpha: Float = 1f) =
    blurred(path, shade.copy(alpha = 0.55f * alpha), 0.6.dp.toPx(), stroke = (width * 0.8f).dp.toPx())

// ---- The player at the bench -------------------------------------------------------------------------------------

/** IK for a two-bone limb (y up): the elbow, bending down and back. */
private fun elbowOf(s: Offset, w: Offset, a: Float, b: Float): Offset {
    val d = w - s
    val c = d.getDistance().coerceIn(abs(a - b) + 0.01f, a + b - 0.01f)
    val base = atan2(d.y, d.x)
    val alpha = acos(((a * a + c * c - b * b) / (2 * a * c)).coerceIn(-1f, 1f))
    return s + Offset(cos(base - alpha) * a, sin(base - alpha) * a)
}

private fun bezier(a: Offset, c: Offset, b: Offset, s: Float) = a * ((1 - s) * (1 - s)) + c * (2 * s * (1 - s)) + b * (s * s)
private fun bezierTan(a: Offset, c: Offset, b: Offset, s: Float) = ((c - a) * (2 * (1 - s)) + (b - c) * (2 * s)).unit()
private fun ramp(xs: FloatArray, ys: FloatArray, s: Float): Float {
    for (i in 1 until xs.size) if (s <= xs[i]) return lerpF(ys[i - 1], ys[i], (s - xs[i - 1]) / (xs[i] - xs[i - 1]))
    return ys.last()
}

/**
 * The player at the piano, side view, filling the canvas above [labelRoom]: each cue 0 (the poor habit) to 1 (right).
 * BENCH: the bench too far back. EDGE: sat deep on the bench. BACK: a slouch. ELBOWS: elbows pinned back. LEVEL:
 * wrists dropped below the keys. HANDS: hands in the lap. [levelLine] draws the dashed line under level forearms.
 * Returns where each cue lives on screen (for its glow), and the size of one unit.
 */
internal fun DrawScope.softFigure(
    bench: Float, edge: Float, back: Float, elbows: Float, level: Float, hands: Float, breathe: Float,
    levelLine: Boolean, ink: StageInk, labelRoom: Float,
): Pair<Map<PostureCue, Offset>, Float> {
    val pad = 10.dp.toPx()
    val u = minOf((size.width - 2 * pad) / 12.4f, (size.height - pad - labelRoom) / 13.6f)
    val floorY = size.height - pad
    val x0 = size.width - pad - 9.65f * u                      // the piano flush right, the bench toward the labels
    fun P(x: Float, y: Float) = Offset(x0 + x * u, floorY - y * u)
    fun Q(q: Offset) = P(q.x, q.y)
    fun cap(a: Offset, ra: Float, b: Offset, rb: Float) = capsule(Q(a), ra * u, Q(b), rb * u)
    fun box(x1: Float, y1: Float, x2: Float, y2: Float, r: Float) = Path().apply { addRoundRect(RoundRect(Rect(P(x1, y2), P(x2, y1)), CornerRadius(r * u))) }
    val sh = 0.14f * u
    // The poor habits move the bench back and the hip back on it.
    val benchX = -(1 - bench) * 1.25f
    val hip = Offset(0.25f + benchX - (1 - edge) * 0.85f, 5.55f)

    drawLine(ink.faint, Offset(0f, floorY), Offset(size.width, floorY), 2.dp.toPx())
    castShadow(oval(P(3.4f + benchX * 0.5f, 0f), 6.2f * u, 0.28f * u), 0.25f * u, 0.12f)

    // The piano: its tall case, the front leg and toe block, a sheet on the desk.
    paint(box(7.0f, 0.2f, 9.45f, 12.25f, 0.12f), Case, 0.5f * u)
    paint(box(6.8f, 12.15f, 9.6f, 12.5f, 0.1f), Case, sh)
    paint(box(6.2f, 0f, 7.1f, 0.3f, 0.08f), Case, sh)
    paint(box(6.4f, 0.25f, 6.75f, 6.6f, 0.08f), Case, sh)
    paint(box(6.55f, 7.85f, 7.05f, 8.05f, 0.05f), Case, sh)
    paint(polygon(listOf(P(6.62f, 8.0f), P(6.86f, 8.0f), P(7.22f, 10.2f), P(6.98f, 10.2f))), Paper, sh * 0.6f)
    for (k in 0..3) {
        val f = 0.25f + k * 0.17f
        drawLine(Line, lerpO(P(6.66f, 8.0f), P(7.02f, 10.2f), f), lerpO(P(6.82f, 8.0f), P(7.18f, 10.2f), f), 1.dp.toPx(), alpha = 0.3f)
    }

    // The bench.
    for (lx in listOf(-1.85f, 1.25f)) paint(box(lx + benchX, 0f, lx + benchX + 0.32f, 4.7f, 0.08f), BenchWood, sh)
    paint(box(-2.15f + benchX, 4.55f, 1.75f + benchX, 5.0f, 0.14f), BenchWood, 0.18f * u)

    // The spine leans and bows when slouched, upright when sitting tall.
    val lean = deg(lerpF(21f, 3f, back))
    val up = Offset(sin(lean), cos(lean))
    val nBack = Offset(-cos(lean), sin(lean))
    val top = hip + up * (4.55f + breathe)
    val ctrl = (hip + top) / 2f + nBack * ((1 - back) * 0.9f)
    fun spine(s: Float) = bezier(hip, ctrl, top, s)
    fun backN(s: Float) = bezierTan(hip, ctrl, top, s).let { Offset(-it.y, it.x) }
    val shoulder = spine(0.86f) - backN(0.86f) * 0.05f + Offset(0.3f, -0.15f) * (1 - back)
    val knee = Offset(hip.x + 3.85f, 5.25f)
    val ankle = Offset(knee.x + 0.25f, 0.95f)

    // Legs: the far one a step behind and darker, then the near one.
    val farT = Tone(Trousers.shade, Trousers.shade.darken(0.8f), Trousers.base)
    for ((tone, off) in listOf(farT to Offset(0.3f, 0.12f), Trousers to Offset.Zero)) {
        val leg = union(listOf(cap(hip + off + Offset(0.1f, -0.1f), 0.85f, knee + off, 0.62f), cap(knee + off, 0.56f, ankle + off + Offset(0f, 0.25f), 0.42f)))
        paint(leg, tone, 0.2f * u)
        drawShoe(P(ankle.x + off.x - 0.55f, off.y * 0.2f), 0.68f * u, 0f,
            if (off == Offset.Zero) ShoeLeather else Tone(ShoeLeather.shade, ShoeLeather.shade.darken(0.8f), ShoeLeather.base))
    }

    // The keys, at the height a level forearm meets.
    paint(box(5.35f, 6.55f, 7.05f, 7.15f, 0.06f), Case, sh)
    paint(box(5.45f, 7.1f, 7.05f, 7.42f, 0.05f), Ivory, sh * 0.6f)
    paint(box(6.3f, 7.38f, 7.05f, 7.64f, 0.05f), Ebony, sh * 0.6f)

    // Neck, torso, head.
    val headR = 0.95f; val neckLen = 0.85f
    val nod = deg(lerpF(32f, 8f, back))
    val neckDir = Offset(up.x * cos(nod) + up.y * sin(nod), -up.x * sin(nod) + up.y * cos(nod))
    val neckBase = spine(0.94f)
    val neckTop = neckBase + neckDir * neckLen
    paint(cap(neckBase, 0.34f, neckTop, 0.3f), SkinT, 0.12f * u)
    val tb = floatArrayOf(1.2f, 0.95f, 1.0f, 0.95f, 0.55f); val tf = floatArrayOf(0.95f, 1.0f + 0.15f * (1 - back), 1.08f, 0.9f, 0.45f)
    val ss = floatArrayOf(0f, 0.25f, 0.55f, 0.8f, 1f)
    val outline = ArrayList<Offset>()
    outline += hip + Offset(0.75f, -0.55f)
    for (k in 0..6) { val s = k / 6f; outline += spine(s) - backN(s) * ramp(ss, tf, s) }
    outline += top + bezierTan(hip, ctrl, top, 1f) * 0.3f
    for (k in 6 downTo 0) { val s = k / 6f; outline += spine(s) + backN(s) * ramp(ss, tb, s) }
    outline += hip + Offset(-0.55f, -0.5f)
    paint(smoothClosed(outline.map { Q(it) }), Sweater, 0.3f * u)
    val headC = neckTop + neckDir * (headR * 0.78f) + Offset(0.08f, 0f)
    drawHead(Q(headC), headR * u, deg(lerpF(16f, 0f, back)))

    // The arm: shoulder to elbow by IK to a wrist level with the keys (dropped when not level, in the lap with the
    // hands off the keys); pinned-back elbows pull the elbow behind the body.
    val keyTip = Offset(6.0f, 7.47f)
    val keyWrist = lerpO(Offset(4.45f, 7.0f), Offset(4.4f, 7.62f), level)
    val lapWrist = Offset(hip.x + 2.4f, 6.05f); val lapTip = Offset(hip.x + 3.55f, 5.95f)
    val wrist = lerpO(lapWrist, keyWrist, hands)
    val tipF = lerpO(lapTip, keyTip, hands)
    val el = elbowOf(shoulder, wrist, 2.85f, 2.5f) + Offset(-0.55f, 0.3f) * (1 - elbows)
    val kn = wrist + lerpO(Offset(0.85f, 0.42f), Offset(0.92f, 0.2f), level).let { lerpO(Offset(0.95f, -0.05f), it, hands) }
    val pip = lerpO(kn, tipF, 0.5f) + Offset(0f, lerpF(0.08f, 0.22f, level) * hands)
    paint(union(listOf(cap(wrist, 0.32f, kn, 0.3f), cap(kn, 0.24f, pip, 0.2f), cap(pip, 0.2f, tipF, 0.15f))), SkinT, 0.1f * u)
    val tip = Q(tipF); val dn = (Q(tipF) - Q(pip)).unit()
    drawOval(Color(0xFFFAE3D4), Offset(tip.x - 0.1f * u, tip.y - 0.12f * u) - dn * (0.02f * u), androidx.compose.ui.geometry.Size(0.18f * u, 0.12f * u), alpha = 0.9f)
    val fore = (wrist - el).unit()
    paint(union(listOf(cap(shoulder, 0.52f, el, 0.42f), cap(el, 0.42f, wrist - fore * 0.12f, 0.38f))), Sweater, 0.18f * u)
    detail(curve(Q(el + Offset(-0.1f, 0.3f)), Q(el + Offset(0.15f, 0.05f)), Q(el + Offset(0.05f, -0.3f))), Sweater.shade, 1.5f, 0.8f)

    // The level line under the forearms.
    if (levelLine && level > 0.02f) {
        val y = Q(el).y + 0.45f * u
        drawLine(ink.accent, Offset(Q(el).x - 0.6f * u, y), Offset(Q(keyWrist).x + 1.2f * u, y), 2.5.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx())), alpha = level)
    }
    val anchors = mapOf(
        PostureCue.BENCH to P(-0.2f + benchX, 4.78f), PostureCue.EDGE to Q(hip), PostureCue.BACK to Q(spine(0.5f)),
        PostureCue.ELBOWS to Q(el), PostureCue.LEVEL to Q((el + wrist) / 2f), PostureCue.HANDS to Q(lerpO(wrist, tipF, 0.5f)),
    )
    return anchors to u
}

/** A head in profile facing right, centred [c], [r] across, nodding [tilt] radians. */
private fun DrawScope.drawHead(c: Offset, r: Float, tilt: Float) {
    fun L(x: Float, y: Float): Offset {
        val px = x * r; val py = -y * r
        return Offset(c.x + px * cos(tilt) - py * sin(tilt), c.y + px * sin(tilt) + py * cos(tilt))
    }
    val face = smoothClosed(listOf(
        -0.62f to -0.75f, -0.92f to -0.2f, -0.9f to 0.4f, -0.55f to 0.85f, 0f to 1f, 0.5f to 0.86f, 0.76f to 0.52f, 0.84f to 0.24f,
        0.83f to 0.1f, 1.0f to -0.14f, 0.96f to -0.23f, 0.84f to -0.27f, 0.87f to -0.38f, 0.83f to -0.47f, 0.86f to -0.6f,
        0.72f to -0.74f, 0.36f to -0.8f, 0.02f to -0.68f, -0.26f to -0.74f,
    ).map { L(it.first, it.second) })
    paint(face, SkinT, 0.2f * r)
    // Hair: a cap over the crown and the back, with a little fringe.
    val hairPts = listOf(0.4f to 1.6f, 0.9f to 0.72f, 0.62f to 0.6f, 0.25f to 0.52f, 0.08f to 0.26f, -0.28f to 0.24f, -0.34f to -0.2f, -0.58f to -0.6f, -1.6f to -0.6f, -1.6f to 1.6f)
    paint(intersect(circle(L(-0.04f, 0.05f), 1.07f * r), polygon(hairPts.map { L(it.first, it.second) })), Hair, 0.22f * r)
    // The ear, then the face.
    paint(oval(L(-0.12f, -0.06f), 0.17f * r, 0.23f * r), SkinT, 0.08f * r)
    detail(curve(L(-0.06f, 0.08f), L(-0.2f, -0.02f), L(-0.08f, -0.16f)), SkinT.shade.darken(0.8f), 1.4f, 0.8f)
    val eye = L(0.56f, 0.12f)
    drawOval(Hair.shade, Offset(eye.x - 0.05f * r, eye.y - 0.035f * r), androidx.compose.ui.geometry.Size(0.1f * r, 0.07f * r))
    drawPath(curve(L(0.5f, 0.17f), L(0.58f, 0.2f), L(0.68f, 0.15f)), Hair.shade, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
    blurred(curve(L(0.48f, 0.3f), L(0.6f, 0.36f), L(0.74f, 0.3f)), Hair.base.copy(alpha = 0.9f), 0.5.dp.toPx(), stroke = 2.dp.toPx())
    blurred(oval(L(0.55f, -0.22f), 0.16f * r, 0.1f * r), Blush.copy(alpha = 0.3f), 0.08f * r)
    blurred(oval(L(0.86f, -0.4f), 0.06f * r, 0.035f * r), Blush.darken(0.85f).copy(alpha = 0.6f), 0.8.dp.toPx())
}

/** A shoe in side view: heel at [heel] on the floor, [s] px per unit (the shoe is about 2.8 long), tipped up [ang]. */
private fun DrawScope.drawShoe(heel: Offset, s: Float, ang: Float, leather: Tone) {
    fun L(x: Float, y: Float): Offset { val px = x * s; val py = -y * s; return Offset(heel.x + px * cos(-ang) - py * sin(-ang), heel.y + px * sin(-ang) + py * cos(-ang)) }
    val shape = smoothClosed(listOf(
        0f to 0.16f, 0.14f to 0f, 1.2f to 0f, 2.35f to 0f, 2.72f to 0.12f, 2.8f to 0.32f, 2.62f to 0.52f, 2.15f to 0.64f, 1.6f to 0.8f,
        1.22f to 1.0f, 0.92f to 1.1f, 0.3f to 1.12f, 0.02f to 0.82f, -0.06f to 0.42f,
    ).map { L(it.first, it.second) })
    paint(shape, leather, 0.16f * s)
    // The sole, and the laces over the instep.
    clipPath(shape) { drawPath(polygon(listOf(L(-0.3f, -0.3f), L(3.1f, -0.3f), L(3.1f, 0.2f), L(-0.3f, 0.2f))), SoleT.base) }
    for (k in 0..1) {
        val a = L(1.32f + k * 0.28f, 0.98f - k * 0.17f); val b = L(1.5f + k * 0.28f, 0.74f - k * 0.17f)
        detail(curve(a, lerpO(a, b, 0.5f) + Offset(0.04f * s, 0f), b), leather.shade.darken(0.7f), 1.6f, 0.9f)
    }
}

// ---- The foot on the pedal ---------------------------------------------------------------------------------------

/**
 * A foot on the sustain pedal, side view, in the box at [origin] of [w] x [h] px: heel on the floor, the ball of the
 * foot pressing [pedal] 0..1, the shin rising out of the box. Returns the heel and the ball of the foot on screen.
 */
internal fun DrawScope.softFoot(ink: StageInk, origin: Offset, w: Float, h: Float, pedal: Float): Pair<Offset, Offset> {
    val fu = minOf(w / 7.6f, h / 3.4f)
    val floorY = origin.y + h - 4.dp.toPx()
    val x0 = origin.x + (w - 7.6f * fu) / 2 + 0.45f * fu      // world x from -0.45; the piano runs to the box's right edge
    val right = (origin.x + w - x0) / fu
    fun P(x: Float, y: Float) = Offset(x0 + x * fu, floorY - y * fu)
    fun box(x1: Float, y1: Float, x2: Float, y2: Float, r: Float) = Path().apply { addRoundRect(RoundRect(Rect(P(x1, y2), P(x2, y1)), CornerRadius(r * fu))) }
    var heel = Offset.Zero; var ball = Offset.Zero
    clipRect(origin.x, origin.y, origin.x + w, origin.y + h) {
        drawLine(ink.faint, Offset(origin.x, floorY), Offset(origin.x + w, floorY), 2.dp.toPx())
        castShadow(oval(P(1.85f, 0f), 1.75f * fu, 0.14f * fu), 0.12f * fu, 0.16f)
        // The piano's bottom and the pedal box.
        paint(box(4.0f, 1.05f, right, 3.6f, 0.08f), Case, 0.3f * fu)
        paint(box(3.9f, 3.45f, right, 3.75f, 0.06f), Case, 0.1f * fu)
        // The lever: pivoting inside the box, its top running under the ball of the foot.
        val theta = deg(lerpF(16.5f, 8.5f, pedal))
        val heelW = Offset(0.55f, 0f)
        val ballW = heelW + Offset(cos(theta), sin(theta)) * 2.0f
        val pivot = Offset(4.65f, 0.36f)
        val along = (ballW - pivot).unit()
        val tipW = ballW + along * 0.5f
        val down = Offset(along.y, -along.x).let { if (it.y > 0) it * -1f else it }
        fun Pw(o: Offset) = P(o.x, o.y)
        val lever = polygon(listOf(Pw(pivot), Pw(tipW), Pw(tipW + down * 0.3f), Pw(pivot + down * 0.3f)))
        paint(union(listOf(lever, circle(Pw(tipW + down * 0.13f), 0.18f * fu))), Brass, 0.07f * fu)
        paint(box(4.25f, 0f, right, 1.05f, 0.08f), Case, 0.2f * fu)
        // The leg: trousers from above, a sock at the ankle, the shoe with its heel on the floor.
        val sp = P(heelW.x, heelW.y)
        fun local(x: Float, y: Float): Offset { val px = x * 0.72f * fu; val py = -y * 0.72f * fu; return Offset(sp.x + px * cos(-theta) - py * sin(-theta), sp.y + px * sin(-theta) + py * cos(-theta)) }
        val ankle = local(0.62f, 1.0f)
        val knee = P(1.25f, 9.5f)
        val shin = (knee - ankle).unit()
        paint(capsule(ankle, 0.34f * fu, ankle + shin * (1.0f * fu), 0.34f * fu), Sock, 0.1f * fu)
        paint(capsule(ankle + shin * (0.75f * fu), 0.48f * fu, knee, 0.56f * fu), Trousers, 0.18f * fu)
        detail(curve(ankle + shin * (0.95f * fu) + shin.perp() * (0.42f * fu), ankle + shin * (0.86f * fu), ankle + shin * (0.95f * fu) - shin.perp() * (0.42f * fu)), Trousers.shade, 1.5f, 0.6f)
        drawShoe(sp, 0.72f * fu, theta, ShoeLeather)
        heel = sp; ball = Pw(ballW)
        if (pedal > 0.05f) drawCircle(ink.accent, 0.3f * fu, ball, alpha = 0.3f * pedal)
    }
    return heel to ball
}
