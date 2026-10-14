package com.bigpapituna.sharpears.ui.theory

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.theory.ArticulationVisual
import com.bigpapituna.sharpears.engine.theory.Clef
import com.bigpapituna.sharpears.engine.theory.DynamicsVisual
import com.bigpapituna.sharpears.engine.theory.FingerNumbersVisual
import com.bigpapituna.sharpears.engine.theory.HandMove
import com.bigpapituna.sharpears.engine.theory.HandShapeVisual
import com.bigpapituna.sharpears.engine.theory.LaneVisual
import com.bigpapituna.sharpears.engine.theory.MidiVisual
import com.bigpapituna.sharpears.engine.theory.NoteValue
import com.bigpapituna.sharpears.engine.theory.PedalVisual
import com.bigpapituna.sharpears.engine.theory.PhraseHands
import com.bigpapituna.sharpears.engine.theory.PhraseNote
import com.bigpapituna.sharpears.engine.theory.PhraseVisual
import com.bigpapituna.sharpears.engine.theory.PostureCue
import com.bigpapituna.sharpears.engine.theory.PostureVisual
import com.bigpapituna.sharpears.engine.theory.SongMapVisual
import com.bigpapituna.sharpears.engine.theory.StaffNote
import com.bigpapituna.sharpears.engine.theory.Touch
import com.bigpapituna.sharpears.engine.theory.keySignature
import com.bigpapituna.sharpears.engine.theory.keySignaturePositions
import com.bigpapituna.sharpears.engine.theory.progress
import com.bigpapituna.sharpears.engine.theory.whiteAtOrAbove
import com.bigpapituna.sharpears.engine.theory.whiteAtOrBelow
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.RightHandColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Renderers for the Learn course's reading steps (2.0.1): the body at the piano (posture, the hand's shape, finger
 * numbers, the pedal foot), touch (loud and soft, smooth and short), and music on the page (a phrase on the staff,
 * falling notes, a song's section bar, a MIDI cable). Like Scenes.kt, each draws its visual at time t as a pure
 * function of the clock, so a still (animations off) is simply the end state.
 */

private val Skin = Color(0xFFD9A47C)          // the ghost hand's warm skin
private val SkinEdge = Color(0xFF8E5F3E)
private val SkinShade = Color(0xFFC48E66)
private val Nail = Color(0xFFF0D2BC)
private val Trousers = Color(0xFF5B6B7F)
private val Shoe = Color(0xFF3A302A)
private val Hair = Color(0xFF4A3426)
private val Wood = Color(0xFFA0704A)
private val WoodDark = Color(0xFF6E4A2F)
private val PianoCase = Color(0xFF2B2421)
private val PianoEdge = Color(0xFF5A4C42)
private val Brass = Color(0xFFB8955A)
private val Blur = Color(0xFFE5675F)
private val HandInk = Color(0xFF5A3A24)

private fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()

/** A short label in a soft pill, popping in as [p] goes 0 to 1; the newest one outlined in the accent. */
private fun DrawScope.pill(ink: StageInk, label: String, at: Offset, newest: Boolean, p: Float, size: TextUnit = 13.sp, alpha: Float = 1f, left: Boolean = false) {
    if (p <= 0f || alpha <= 0.01f) return
    val l = ink.layout(label, TextKind.LABEL, size)
    val sc = 0.6f + 0.4f * backOut(p)
    val w = (l.size.width + 18.dp.toPx()) * sc; val h = (l.size.height + 9.dp.toPx()) * sc
    val margin = 4.dp.toPx()
    val cx = if (left) at.x + w / 2 else at.x.coerceIn(w / 2 + margin, this.size.width - w / 2 - margin)
    val a = smooth(p * 2) * alpha
    val box = Offset(cx - w / 2, at.y - h / 2)
    drawRoundRect(if (newest) ink.accentSoft else ink.faint, box, Size(w, h), CornerRadius(h / 2), alpha = a)
    if (newest) drawRoundRect(ink.accent, box, Size(w, h), CornerRadius(h / 2), style = Stroke(1.5.dp.toPx()), alpha = a)
    text(ink, label, Offset(cx, at.y), TextKind.LABEL, size, if (newest) ink.ink else ink.dim, alpha = a, scale = sc)
}

/** A palm from [left] to [right] below [top], [h] tall, with the wrist and forearm running down out of the picture. */
private fun DrawScope.palm(left: Float, right: Float, top: Float, h: Float, r: Float) {
    val w = right - left
    val wristL = left + w * 0.2f; val wristW = w * 0.62f
    if (size.height > top + h * 0.6f) {
        drawRect(Skin, Offset(wristL, top + h * 0.6f), Size(wristW, size.height - top - h * 0.6f))
        drawLine(SkinEdge, Offset(wristL, top + h * 0.8f), Offset(wristL, size.height), 1.5.dp.toPx())
        drawLine(SkinEdge, Offset(wristL + wristW, top + h * 0.8f), Offset(wristL + wristW, size.height), 1.5.dp.toPx())
    }
    drawRoundRect(Skin, Offset(left, top), Size(w, h), CornerRadius(r))
    drawRoundRect(SkinEdge, Offset(left, top), Size(w, h), CornerRadius(r), style = Stroke(1.5.dp.toPx()))
    if (size.height > top + h * 0.6f) drawRect(Skin, Offset(wristL + 1.dp.toPx(), top + h * 0.7f), Size(wristW - 2.dp.toPx(), h * 0.32f))
}

/** A small tick drawn as a path (no glyphs as icons). */
private fun DrawScope.tick(c: Offset, r: Float, color: Color, alpha: Float) {
    val p = Path().apply { moveTo(c.x - r, c.y); lineTo(c.x - r * 0.3f, c.y + r * 0.7f); lineTo(c.x + r, c.y - r * 0.7f) }
    drawPath(p, color, style = Stroke(r * 0.45f, cap = StrokeCap.Round), alpha = alpha)
}

// ---- Posture -----------------------------------------------------------------------------------------------------

internal fun DrawScope.postureScene(v: PostureVisual, t: Int, ink: StageInk) {
    val pad = 12.dp.toPx()
    val u = minOf((size.width - 2 * pad) / 10.4f, (size.height - pad) / 8.6f)
    val floor = size.height - pad
    val x0 = (size.width - 10.4f * u) / 2
    fun e(c: PostureCue) = smooth(v.settled(c, t))
    val bench = e(PostureCue.BENCH); val edge = e(PostureCue.EDGE); val back = e(PostureCue.BACK)
    val elbows = e(PostureCue.ELBOWS); val level = e(PostureCue.LEVEL); val hands = e(PostureCue.HANDS)
    val seatY = floor - 2.15f * u
    val benchGood = x0 + 3.2f * u
    val torsoLen = 2.55f * u
    // The good pose places the piano: the hands rest on the keys at the height of level forearms.
    fun pose(bench: Float, edge: Float, back: Float, elbows: Float, level: Float, hands: Float, breathe: Float): List<Offset> {
        val benchX = benchGood + (1 - bench) * 1.25f * u
        val hip = Offset(benchX + mix(-0.75f, 0.35f, edge) * u, seatY - 0.3f * u)
        val lean = rad(mix(24f, 4f, back))
        val shoulder = hip + Offset(sin(lean) * torsoLen, -cos(lean) * torsoLen + breathe)
        val arm = rad(mix(-22f, 16f, elbows))
        val elbow = shoulder + Offset(sin(arm) * 1.2f * u, cos(arm) * 1.2f * u)
        val fore = rad(mix(-22f, 0f, level) + (1 - hands) * 58f)
        val wrist = elbow + Offset(cos(fore) * 1.35f * u, sin(fore) * 1.35f * u)
        return listOf(Offset(benchX, seatY), hip, shoulder, elbow, wrist)
    }
    val good = pose(1f, 1f, 1f, 1f, 1f, 1f, 0f)
    val keysX = good[4].x + 0.2f * u
    val keyY = good[4].y + 0.14f * u
    // The piano: case, keybed with the keys on top, a leg, and a sheet on the stand.
    drawLine(ink.faint, Offset(0f, floor), Offset(size.width, floor), 2.dp.toPx())
    drawRoundRect(PianoCase, Offset(keysX + 0.75f * u, floor - 5.6f * u), Size(2.4f * u, 5.6f * u), CornerRadius(0.18f * u))
    drawRoundRect(PianoEdge, Offset(keysX + 0.75f * u, floor - 5.6f * u), Size(2.4f * u, 5.6f * u), CornerRadius(0.18f * u), style = Stroke(1.5.dp.toPx()))
    drawRect(Color(0xFFF2EBDD), Offset(keysX + 0.45f * u, keyY - 1.55f * u), Size(0.32f * u, 1.1f * u))
    drawRect(PianoCase, Offset(keysX - 0.05f * u, keyY + 0.28f * u), Size(1.0f * u, 0.5f * u))
    drawRect(PianoCase, Offset(keysX + 0.1f * u, keyY + 0.7f * u), Size(0.22f * u, floor - keyY - 0.7f * u))
    drawRoundRect(Color(0xFFF5F1EA), Offset(keysX - 0.1f * u, keyY), Size(1.0f * u, 0.3f * u), CornerRadius(0.06f * u))
    for (k in 0..2) drawRect(Color(0xFF141110), Offset(keysX + (0.15f + k * 0.28f) * u, keyY - 0.1f * u), Size(0.16f * u, 0.12f * u))
    // The level line under the forearms.
    if (v.steps.any { it.cue == PostureCue.LEVEL }) {
        val y = good[3].y + 0.32f * u
        drawLine(ink.accent, Offset(good[3].x - 0.7f * u, y), Offset(keysX + 1.3f * u, y), 2.5.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx())), alpha = level)
        drawLine(ink.accent, Offset(keysX + 1.3f * u, y - 0.18f * u), Offset(keysX + 1.3f * u, y + 0.18f * u), 2.5.dp.toPx(), cap = StrokeCap.Round, alpha = level)
    }
    // The figure, in front of the piano.
    val now = pose(bench, edge, back, elbows, level, hands, sin(t / 650f) * 0.03f * u)
    val (benchAt, hip, shoulder, elbow, wrist) = now
    drawRoundRect(Wood, Offset(benchAt.x - 1.25f * u, seatY), Size(2.5f * u, 0.32f * u), CornerRadius(0.1f * u))
    for (sx in listOf(-1.05f, 0.9f)) drawRect(WoodDark, Offset(benchAt.x + sx * u, seatY + 0.32f * u), Size(0.17f * u, floor - seatY - 0.32f * u))
    val knee = hip + Offset(1.95f * u, 0.12f * u)
    val ankle = Offset(knee.x + 0.08f * u, floor - 0.2f * u)
    drawLine(Trousers, hip, knee, 0.52f * u, cap = StrokeCap.Round)
    drawLine(Trousers, knee, ankle, 0.42f * u, cap = StrokeCap.Round)
    drawLine(Shoe, ankle, ankle + Offset(0.55f * u, 0.06f * u), 0.3f * u, cap = StrokeCap.Round)
    // The back: a slouch bows it backward; upright it is straight.
    val mid = Offset((hip.x + shoulder.x) / 2, (hip.y + shoulder.y) / 2)
    val dir = Offset(shoulder.x - hip.x, shoulder.y - hip.y)
    val len = kotlin.math.hypot(dir.x, dir.y)
    val perp = Offset(-dir.y / len, dir.x / len)
    val bow = (1 - back) * 0.6f * u
    val torso = Path().apply { moveTo(hip.x, hip.y); quadraticTo(mid.x - perp.x * bow, mid.y - perp.y * bow, shoulder.x, shoulder.y) }
    drawPath(torso, ink.accent, style = Stroke(0.8f * u, cap = StrokeCap.Round))
    val head = shoulder + Offset(dir.x / len * 0.75f * u + 0.14f * u + (1 - back) * 0.3f * u, dir.y / len * 0.75f * u + (1 - back) * 0.2f * u)
    drawCircle(Skin, 0.48f * u, head)
    drawArc(Hair, 170f, 200f, true, head - Offset(0.5f * u, 0.52f * u), Size(1.0f * u, 0.9f * u))
    drawCircle(Shoe, 0.05f * u, head + Offset(0.26f * u, -0.04f * u))
    drawLine(ink.accent.darken(0.82f), shoulder, elbow, 0.36f * u, cap = StrokeCap.Round)
    drawLine(Skin, elbow, wrist, 0.28f * u, cap = StrokeCap.Round)
    val handAng = kotlin.math.atan2(wrist.y - elbow.y, wrist.x - elbow.x) + rad(14f)
    drawLine(Skin, wrist, wrist + Offset(cos(handAng) * 0.42f * u, sin(handAng) * 0.42f * u), 0.25f * u, cap = StrokeCap.Round)
    // The part being put right glows; the cues stack up as labels, top left.
    val anchors = mapOf(PostureCue.BENCH to Offset(benchAt.x, seatY + 0.16f * u), PostureCue.EDGE to hip, PostureCue.BACK to mid,
        PostureCue.ELBOWS to elbow, PostureCue.LEVEL to Offset((elbow.x + wrist.x) / 2, (elbow.y + wrist.y) / 2), PostureCue.HANDS to wrist)
    val newest = v.steps.indices.lastOrNull { t >= v.stepAt(it) } ?: -1
    v.steps.forEachIndexed { i, s ->
        val at = v.stepAt(i)
        if (t < at) return@forEachIndexed
        val glow = if (t < at + v.moveMs + 500) 1f - progress(t, at + v.moveMs, 500) else 0f
        if (glow > 0f) {
            val k = (t - at) % 700 / 700f
            drawCircle(ink.accent, (0.55f + 0.35f * k) * u, anchors.getValue(s.cue), style = Stroke(2.dp.toPx()), alpha = glow * (1 - k))
        }
        val p = progress(t, at + v.moveMs / 2, 380)
        val y = pad + 14.dp.toPx() + i * 30.dp.toPx()
        pill(ink, s.label, Offset(pad + 22.dp.toPx(), y), i == newest, p, left = true)
        if (p >= 1f || i < newest) tick(Offset(pad + 8.dp.toPx(), y), 5.dp.toPx(), ink.accent, smooth(p * 2))
    }
}

// ---- Hand shape --------------------------------------------------------------------------------------------------

internal fun DrawScope.handShapeScene(v: HandShapeVisual, t: Int, ink: StageInk) {
    val pad = 14.dp.toPx()
    val low = whiteAtOrBelow(v.keys.min() - 3); val high = whiteAtOrAbove(v.keys.max() + 3)
    val l = pianoFor(low, high, size.width - 2 * pad, size.height * 0.46f)
    val keyH = l.height
    val top = maxOf(size.height * 0.2f, 44.dp.toPx())
    val o = Offset(pad, top)
    val hang = smooth(v.done(HandMove.HANG, t)); val ballIn = smooth(v.done(HandMove.BALL, t)); val curl = smooth(v.done(HandMove.CURL, t))
    val tips = smooth(v.done(HandMove.TIPS, t)); val thumb = smooth(v.done(HandMove.THUMB, t))
    val tipsAt = v.steps.indexOfFirst { it.move == HandMove.TIPS }.let { if (it < 0) -10_000 else v.stepAt(it) + v.moveMs - 150 }
    val thumbAt = v.steps.indexOfFirst { it.move == HandMove.THUMB }.let { if (it < 0) -10_000 else v.stepAt(it) + v.moveMs - 150 }
    val thumbDown = if (v.has(HandMove.THUMB)) thumb else tips
    // Keys under the fingertips: lit as the tips land, pressed for a moment, then resting.
    val lit = HashMap<Int, Color>(); val sinks = HashMap<Int, Float>()
    v.keys.forEachIndexed { k, m ->
        val down = if (k == 0) thumbDown else tips
        if (down > 0.05f) {
            lit[m] = ink.accent.copy(alpha = down)
            val at = if (k == 0 && v.has(HandMove.THUMB)) thumbAt else tipsAt + (k - (if (v.has(HandMove.THUMB)) 1 else 0)) * 80
            sinks[m] = maxOf(press(t - at), 0.35f * down)
        }
    }
    drawPiano(l, o, ink, lit = { lit[it] }, sink = { sinks[it] ?: 0f })
    val w = l.whiteWidth
    val fw = minOf(w * 0.64f, 30.dp.toPx())
    val lift = (1 - hang) * keyH * 0.55f
    val swing = (1 - hang) * 7f * sin(t / 260f)
    val palmTop = o.y + keyH * 0.88f + lift
    val xs = v.keys.map { o.x + centerX(l, it) }
    val cx = xs.average().toFloat()
    // The ball under the hand: in while the hand finds its shape, out once the fingers have curled round it.
    val ballA = 0.45f * (if (v.has(HandMove.BALL)) ballIn else 1f) * (if (v.has(HandMove.CURL)) 1 - curl else if (v.has(HandMove.BALL)) 1f else 0f)
    val ballC = Offset(cx, palmTop - keyH * 0.18f)
    rotate(swing, Offset(cx, palmTop + keyH * 0.3f)) {
        val pl = xs.first() - 0.7f * w; val pr = xs.last() + 0.6f * w
        palm(pl, pr, palmTop, keyH * 0.5f, w * 0.8f)
        val base = listOf(0.46f, 0.52f, 0.48f, 0.38f)
        for (f in 2..5) {
            val x = xs[f - 1]
            val black = isBlack(v.keys[f - 1])
            val len = keyH * base[f - 2] * mix(1.3f, 0.95f, curl) + (if (black) keyH * 0.12f * curl else 0f) + (1 - hang) * keyH * 0.08f
            val dip = tips * 0.6f + 0.4f * press(t - (tipsAt + (f - 2) * 80))
            val topY = palmTop + keyH * 0.04f - len + keyH * 0.05f * dip
            drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(x - fw / 2 + 3f, topY + 4f), Size(fw, palmTop + keyH * 0.05f - topY), CornerRadius(fw / 2))
            drawRoundRect(lerp(Skin, SkinShade, curl * 0.4f), Offset(x - fw / 2, topY), Size(fw, palmTop + keyH * 0.05f - topY), CornerRadius(fw / 2))
            drawRoundRect(SkinEdge, Offset(x - fw / 2, topY), Size(fw, palmTop + keyH * 0.05f - topY), CornerRadius(fw / 2), style = Stroke(1.5.dp.toPx()))
            // Curled: a knuckle crease and the nail at the tip, seen from above.
            if (curl > 0.05f) {
                val crease = topY + (palmTop - topY) * 0.45f
                drawLine(SkinEdge, Offset(x - fw * 0.3f, crease), Offset(x + fw * 0.3f, crease), 1.2.dp.toPx(), alpha = curl)
                drawOval(Nail, Offset(x - fw * 0.24f, topY + fw * 0.12f), Size(fw * 0.48f, fw * 0.36f), alpha = curl)
            }
            if (dip > 0.05f) drawCircle(ink.accent, fw * 0.55f, Offset(x, topY + fw * 0.45f), alpha = 0.45f * dip)
        }
        // The thumb: flat and splayed, then turned onto its side, playing on its corner.
        val tx = xs[0]
        val tlen = keyH * 0.32f + (1 - hang) * keyH * 0.05f
        val tw = fw * mix(1.2f, 0.82f, thumb)
        val ttop = palmTop + keyH * 0.1f - tlen + keyH * 0.05f * thumbDown
        rotate(mix(-38f, -14f, thumb), Offset(tx, palmTop + keyH * 0.14f)) {
            drawRoundRect(Skin, Offset(tx - tw / 2, ttop), Size(tw, palmTop + keyH * 0.16f - ttop), CornerRadius(tw / 2))
            drawRoundRect(SkinEdge, Offset(tx - tw / 2, ttop), Size(tw, palmTop + keyH * 0.16f - ttop), CornerRadius(tw / 2), style = Stroke(1.5.dp.toPx()))
            if (thumb > 0.05f) drawLine(SkinEdge, Offset(tx + tw * 0.28f, ttop + tw * 0.4f), Offset(tx + tw * 0.28f, palmTop), 1.2.dp.toPx(), alpha = thumb)
            if (thumbDown > 0.05f) drawCircle(ink.accent, tw * 0.5f, Offset(tx, ttop + tw * 0.4f), alpha = 0.45f * thumbDown)
        }
    }
    if (ballA > 0.01f) {
        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), ink.accent.copy(alpha = 0.35f)),
            center = ballC - Offset(w * 0.5f, w * 0.5f), radius = w * 2.2f), w * 1.7f, ballC, alpha = ballA * 1.4f)
        drawCircle(ink.accent, w * 1.7f, ballC, style = Stroke(2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
            alpha = (ballA * 2f).coerceAtMost(1f))
    }
    // The newest move's label over the keys; the one before fades out.
    val newest = v.steps.indices.lastOrNull { t >= v.stepAt(it) } ?: -1
    for (i in maxOf(0, newest - 1)..newest) {
        if (i < 0) continue
        val p = progress(t, v.stepAt(i) + (if (i > 0) 200 else 0), 380)
        val fade = if (i == newest) 1f else 1f - smooth(progress(t, v.stepAt(newest), 180))
        pill(ink, v.steps[i].label, Offset(size.width / 2, top / 2), i == newest, p, 15.sp, fade)
    }
}

// ---- Finger numbers ----------------------------------------------------------------------------------------------

internal fun DrawScope.fingerNumbersScene(v: FingerNumbersVisual, t: Int, ink: StageInk) {
    val pad = 12.dp.toPx()
    val labelRoom = 44.dp.toPx()
    val l = pianoFor(v.low, v.high, size.width - 2 * pad, size.height * 0.5f)
    val keyH = l.height
    val top = ((size.height - labelRoom - keyH * 1.55f) / 2).coerceAtLeast(0f) + labelRoom
    val o = Offset(pad, top)
    val w = l.whiteWidth
    fun kx(m: Int) = o.x + centerX(l, m)
    // Each hand's pose: the x of fingers 1 to 5. At rest from its keys; each step moves the hand so the pressing
    // finger is over its key, the others a [spread] of white keys apart (the left hand mirrored).
    fun poses(right: Boolean, keys: List<Int>): List<FloatArray>? {
        if (keys.size < 5) return null
        val dir = if (right) 1f else -1f
        var cur = FloatArray(5) { kx(keys[it]) }
        val out = mutableListOf(cur)
        for (s in v.steps) {
            val mine = s.presses.filter { it.right == right }
            if (mine.isNotEmpty()) {
                val p0 = mine.first()
                val anchor = kx(p0.key) - dir * (p0.finger - 1) * s.spread * w
                cur = FloatArray(5) { anchor + dir * it * s.spread * w }
                mine.forEach { cur[it.finger - 1] = kx(it.key) }
            }
            out += cur
        }
        return out
    }
    val glide = minOf(240, v.stepMs / 2)
    fun poseAt(ps: List<FloatArray>): FloatArray {
        var i = v.steps.indices.lastOrNull { t >= v.stepAt(it) - glide } ?: -1
        if (i < 0) return ps[0]
        val e = smooth(progress(t, v.stepAt(i) - glide, glide - 30))
        val a = ps[i]; val b = ps[i + 1]
        return FloatArray(5) { mix(a[it], b[it], e) }
    }
    // Which fingers are down at t (and how far), and the keys they light.
    val lit = HashMap<Int, Color>(); val sinks = HashMap<Int, Float>()
    val dips = arrayOf(FloatArray(6), FloatArray(6))      // [right, left][finger]
    val blackTip = arrayOf(BooleanArray(6), BooleanArray(6))
    fun down(right: Boolean, finger: Int, key: Int, at: Int, hold: Int) {
        val d = when { t < at - 80 || t > at + hold + 120 -> 0f; t < at -> (t - at + 80) / 80f; t <= at + hold -> 1f; else -> 1f - (t - at - hold) / 120f }
        if (d <= 0f) return
        val h = if (right) 0 else 1
        dips[h][finger] = maxOf(dips[h][finger], d)
        blackTip[h][finger] = isBlack(key)
        lit[key] = (if (right) RightHandColor else LeftHandColor).copy(alpha = d)
        sinks[key] = maxOf(sinks[key] ?: 0f, if (t >= at) press(t - at).coerceAtLeast(0.5f * d) else 0f)
    }
    if (v.intro) for (f in 1..5) {
        v.rightKeys.getOrNull(f - 1)?.let { down(true, f, it, v.popAt(f), (v.popMs * 0.7f).toInt()) }
        v.leftKeys.getOrNull(f - 1)?.let { down(false, f, it, v.popAt(f), (v.popMs * 0.7f).toInt()) }
    }
    v.steps.forEachIndexed { i, s -> s.presses.forEach { down(it.right, it.finger, it.key, v.stepAt(i), (v.stepMs * 0.7f).toInt()) } }
    drawPiano(l, o, ink, lit = { lit[it] }, sink = { sinks[it] ?: 0f })
    val fw = minOf(w * 0.62f, 28.dp.toPx())
    val palmTop = o.y + keyH * 0.9f
    for ((h, right) in listOf(0 to true, 1 to false)) {
        val ps = poses(right, if (right) v.rightKeys else v.leftKeys) ?: continue
        val xs = poseAt(ps)
        val dir = if (right) 1f else -1f
        val color = if (right) RightHandColor else LeftHandColor
        val pl = xs.min() - 0.62f * w; val pr = xs.max() + 0.62f * w
        palm(pl, pr, palmTop, keyH * 0.5f, w * 0.8f)
        val room = size.height - palmTop
        if (room > keyH * 0.3f) text(ink, if (right) "Right hand" else "Left hand", Offset((pl + pr) / 2, palmTop + minOf(keyH * 0.3f, room * 0.55f)),
            TextKind.LABEL, 11.sp, HandInk)
        val base = listOf(0.30f, 0.46f, 0.52f, 0.48f, 0.38f)
        for (f in 1..5) {
            val x = xs[f - 1]
            val d = dips[h][f]
            val len = keyH * base[f - 1] + (if (blackTip[h][f] && d > 0f) keyH * 0.14f else 0f)
            val baseY = palmTop + keyH * (if (f == 1) 0.16f else 0.05f)
            val topY = baseY - len + keyH * 0.05f * d
            val ww = fw * (if (f == 1) 1.08f else 1f) * (1f + 0.1f * d)
            val pivot = Offset(x, baseY)
            rotate(if (f == 1) -16f * dir * (1f - 0.5f * d) else 0f, pivot) {
                drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(x - ww / 2 + 3f, topY + 4f), Size(ww, baseY - topY), CornerRadius(ww / 2))
                drawRoundRect(lerp(Skin, color, 0.35f * d), Offset(x - ww / 2, topY), Size(ww, baseY - topY), CornerRadius(ww / 2))
                drawRoundRect(if (d > 0.3f) color else SkinEdge, Offset(x - ww / 2, topY), Size(ww, baseY - topY), CornerRadius(ww / 2),
                    style = Stroke((if (d > 0.3f) 2.5f else 1.5f).dp.toPx()))
                // The finger's number: popping on in the intro, lit while the finger plays.
                val shown = if (v.intro) progress(t, v.popAt(f) - 60, 320) else 1f
                if (shown > 0f) {
                    val r = minOf(ww * 0.42f, 11.dp.toPx()) * (0.6f + 0.4f * backOut(shown)) * (1f + 0.25f * d)
                    val c = Offset(x, topY + ww * 0.55f)
                    drawCircle(if (d > 0.3f) color else Color.White, r, c, alpha = smooth(shown * 2))
                    text(ink, "$f", c, TextKind.NUMBER, pxSp(r * 1.25f), if (d > 0.3f) Color.White else HandInk, alpha = smooth(shown * 2))
                }
            }
        }
    }
    // Step labels over the keys: the newest pops, the one before fades.
    val labelled = v.steps.indices.filter { v.steps[it].label != null && t >= v.stepAt(it) }
    labelled.takeLast(2).forEach { i ->
        val s = v.steps[i]
        val newest = i == labelled.last()
        val p = progress(t, v.stepAt(i), 360)
        val fade = if (newest) 1f else 1f - smooth(progress(t, v.stepAt(labelled.last()) - 60, 140))
        val x = s.presses.map { kx(it.key) }.average().toFloat()
        pill(ink, s.label!!, Offset(x, top - 24.dp.toPx()), newest, p, 14.sp, fade)
    }
}

// ---- Pedal -------------------------------------------------------------------------------------------------------

internal fun DrawScope.pedalScene(v: PedalVisual, t: Int, ink: StageInk) {
    val pad = 12.dp.toPx()
    val cur = v.current(t)
    val pedal = v.pedal(t)
    // Keys: the chord held while the fingers are down, then ringing softly on the pedal.
    val l = pianoFor(v.low, v.high, size.width - 2 * pad, size.height * (if (v.foot) 0.3f else 0.22f))
    val kTop = pad + 6.dp.toPx()
    val o = Offset(pad, kTop)
    val lit = HashMap<Int, Color>(); val sinks = HashMap<Int, Float>()
    var ringing = false
    v.chords.forEachIndexed { i, c ->
        if (t < v.chordAt(i)) return@forEachIndexed
        val end = if (i + 1 < v.chords.size) v.liftAt(i + 1) else v.durationMs - 300
        if (t > end) return@forEachIndexed
        val held = t < v.releaseAt(i)
        val col = if (i == cur) ink.accent else Blur
        c.forEach { m ->
            lit[m] = col.copy(alpha = if (held) 1f else 0.4f)
            sinks[m] = if (held) maxOf(press(t - v.chordAt(i)), 0.6f) else 0f
        }
        if (!held) ringing = true
    }
    drawPiano(l, o, ink, lit = { lit[it] }, sink = { sinks[it] ?: 0f })
    // Rings rising from the keys while the sound goes on with the fingers up.
    if (ringing && pedal > 0.3f) {
        val cx = size.width / 2
        for (k in 0..2) {
            val ph = ((t + k * 300) % 900) / 900f
            drawArc(ink.accent, 215f, 110f, false, Offset(cx - (30 + 70 * ph).dp.toPx(), kTop + l.height * 0.2f - (10 + 40 * ph).dp.toPx()),
                Size((60 + 140 * ph).dp.toPx(), (40 + 80 * ph).dp.toPx()), style = Stroke(2.dp.toPx()), alpha = 0.5f * (1 - ph))
        }
        if (v.foot) pill(ink, "Keys up, still ringing", Offset(size.width / 2, kTop + l.height + 18.dp.toPx()), true, progress(t, v.releaseAt(0), 380))
    }
    // The foot on the pedal, side view.
    val footTop = if (v.foot) kTop + l.height + 40.dp.toPx() else size.height * 0.66f
    drawFoot(ink, Offset(pad, footTop), Size(size.width - 2 * pad, size.height - pad - footTop), pedal, v.foot, t, v.leadMs)
    if (v.foot) return
    // The lane: one block per chord, the pedal line drawn as it happens, an arrow at each "down after the chord".
    val laneTop = kTop + l.height + 30.dp.toPx(); val laneBot = size.height * 0.62f
    val laneL = pad + 34.dp.toPx(); val laneR = size.width - pad
    val t0 = v.leadMs - 250; val t1 = v.durationMs
    fun x(time: Int) = laneL + (time - t0).toFloat() / (t1 - t0) * (laneR - laneL)
    val blockBot = laneTop + (laneBot - laneTop) * 0.42f
    val yUp = laneTop + (laneBot - laneTop) * 0.62f; val yDown = laneTop + (laneBot - laneTop) * 0.95f
    text(ink, "Pedal", Offset(pad, (yUp + yDown) / 2), TextKind.LABEL, 11.sp, ink.dim, centred = false)
    v.chords.forEachIndexed { i, c ->
        val a = x(v.chordAt(i)); val b = x(if (i + 1 < v.chords.size) v.chordAt(i + 1) else v.chordAt(i) + v.chordMs) - 3.dp.toPx()
        val reached = t >= v.chordAt(i)
        drawRoundRect(if (reached) ink.accentSoft else ink.faint, Offset(a, laneTop), Size(b - a, blockBot - laneTop), CornerRadius(8.dp.toPx()),
            alpha = if (reached) 1f else 0.5f)
        if (i == cur) drawRoundRect(ink.accent, Offset(a, laneTop), Size(b - a, blockBot - laneTop), CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
        val name = v.names.getOrNull(i) ?: c.joinToString(" ") { keyLabel(it) }
        text(ink, name, Offset((a + b) / 2, (laneTop + blockBot) / 2), TextKind.LABEL, 12.sp, if (reached) ink.ink else ink.dim)
        // Late: the stretch where both chords ring at once.
        if (v.late && i > 0 && t >= v.chordAt(i)) {
            val e = minOf(t, v.liftAt(i))
            drawRect(Blur, Offset(a, laneTop), Size(x(e) - a, blockBot - laneTop), alpha = 0.3f)
            if (i == 1) pill(ink, "Blur", Offset((a + x(v.liftAt(i))) / 2, laneTop - 12.dp.toPx()), false, progress(t, v.chordAt(i) + 200, 380))
        }
    }
    val path = Path()
    var tt = t0
    var first = true
    while (tt <= minOf(t, t1)) {
        val y = mix(yUp, yDown, v.pedal(tt))
        if (first) { path.moveTo(x(tt), y); first = false } else path.lineTo(x(tt), y)
        tt += 25
    }
    drawPath(path, ink.ink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    if (t in t0..t1) drawLine(ink.accent, Offset(x(t), laneTop - 4.dp.toPx()), Offset(x(t), yDown + 4.dp.toPx()), 2.dp.toPx())
    if (!v.late) v.chords.indices.forEach { i ->
        val at = v.downAt(i)
        val e = outCubic(progress(t, v.chordAt(i), at - v.chordAt(i)))
        if (e <= 0f) return@forEach
        val a = Offset(x(v.chordAt(i)), blockBot + 3.dp.toPx()); val b = Offset(x(at), yDown - 4.dp.toPx())
        val arc = Path().apply { moveTo(a.x, a.y); quadraticTo(a.x, b.y, b.x, b.y) }
        drawPath(trim(arc, e), ink.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        if (e >= 1f) {
            drawLine(ink.accent, b, b + Offset(-6.dp.toPx(), -5.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink.accent, b, b + Offset(-6.dp.toPx(), 5.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        }
        if (i == minOf(1, v.chords.lastIndex)) pill(ink, "Down after the chord", Offset(b.x, laneTop - 12.dp.toPx()), true, progress(t, at, 380))
    }
}

/** A foot on the sustain pedal, side view, in [box]: heel on the floor, the ball of the foot pressing [pedal] 0..1. */
private fun DrawScope.drawFoot(ink: StageInk, origin: Offset, box: Size, pedal: Float, labels: Boolean, t: Int, leadMs: Int) {
    val fu = minOf(box.width / 5.6f, box.height / 2.6f)
    val floor = origin.y + box.height - 4.dp.toPx()
    val cx = origin.x + box.width / 2
    drawLine(ink.faint, Offset(origin.x, floor), Offset(origin.x + box.width, floor), 2.dp.toPx())
    // The pedal box under the piano, and the brass pedal pivoting at its back.
    val pivot = Offset(cx + 1.6f * fu, floor - 0.42f * fu)
    drawRoundRect(PianoCase, Offset(pivot.x - 0.15f * fu, floor - 1.25f * fu), Size(box.width + origin.x - pivot.x, 1.25f * fu), CornerRadius(0.12f * fu))
    drawLine(PianoEdge, Offset(pivot.x - 0.15f * fu, floor - 1.25f * fu), Offset(origin.x + box.width, floor - 1.25f * fu), 2.dp.toPx())
    val ang = rad(mix(13f, 2f, pedal))
    val len = 2.1f * fu
    val tip = pivot + Offset(-cos(ang) * len, -sin(ang) * len)
    drawLine(Brass, pivot, tip, 0.24f * fu, cap = StrokeCap.Round)
    // The foot: heel fixed on the floor, the ball on the pedal near its tip, the shin rising out of the picture.
    val ball = pivot + Offset(-cos(ang) * len * 0.72f, -sin(ang) * len * 0.72f - 0.12f * fu)
    val heel = Offset(ball.x - 1.6f * fu, floor - 0.1f * fu)
    val along = Offset(ball.x - heel.x, ball.y - heel.y).let { val d = kotlin.math.hypot(it.x, it.y); Offset(it.x / d, it.y / d) }
    val up = Offset(along.y, -along.x)
    val toe = ball + along * (0.55f * fu)
    val ankle = heel + along * (0.35f * fu) + up * (0.75f * fu)
    val knee = Offset(ankle.x - 0.5f * fu, maxOf(origin.y, ankle.y - 1.6f * fu))
    drawLine(Trousers, ankle, knee, 0.55f * fu, cap = StrokeCap.Round)
    val shoe = Path().apply {
        moveTo(heel.x, heel.y); lineTo(toe.x, toe.y)
        val toeTop = toe + up * (0.32f * fu)
        quadraticTo(toe.x + along.x * 0.18f * fu, toe.y + along.y * 0.18f * fu, toeTop.x, toeTop.y)
        val instep = ball + up * (0.5f * fu) - along * (0.2f * fu)
        lineTo(instep.x, instep.y)
        val front = ankle + along * (0.25f * fu)
        lineTo(front.x, front.y)
        val back = heel + up * (0.62f * fu)
        lineTo(back.x, back.y)
        quadraticTo(heel.x - along.x * 0.2f * fu, heel.y - along.y * 0.2f * fu, heel.x, heel.y)
        close()
    }
    drawPath(shoe, Color(0xFF7A5C48))
    drawPath(shoe, ink.line, style = Stroke(1.5.dp.toPx()), alpha = 0.6f)
    if (pedal > 0.05f) drawCircle(ink.accent, 0.32f * fu, ball, alpha = 0.35f * pedal)
    if (labels) {
        pill(ink, "Heel on the floor", Offset(heel.x - 1.3f * fu, floor - 0.45f * fu), false, progress(t, leadMs + 300, 380))
        pill(ink, "Ball of the foot", Offset(ball.x + 0.4f * fu, ball.y - 1.7f * fu), true, progress(t, leadMs + 1000, 380))
        drawLine(ink.faint, Offset(ball.x + 0.3f * fu, ball.y - 1.45f * fu), Offset(ball.x + 0.05f * fu, ball.y - 0.6f * fu), 1.5.dp.toPx(), alpha = progress(t, leadMs + 1000, 380))
    }
}

// ---- Loud and soft -----------------------------------------------------------------------------------------------

internal fun DrawScope.dynamicsScene(v: DynamicsVisual, t: Int, ink: StageInk) {
    val pad = 18.dp.toPx()
    val n = v.notes.size
    val slot = (size.width - 2 * pad) / n
    fun xOf(i: Int) = pad + (i + 0.5f) * slot
    val all = v.notes.flatten()
    val lo = all.min(); val hi = all.max()
    val dotTop = size.height * 0.1f; val dotBot = size.height * 0.46f
    fun yOf(m: Int) = if (hi == lo) (dotTop + dotBot) / 2 else dotBot - (m - lo).toFloat() / (hi - lo) * (dotBot - dotTop)
    val curveTop = size.height * 0.55f; val curveBot = size.height * 0.8f
    fun levelY(level: Int) = curveBot - (level - 20).coerceAtLeast(0) / 107f * (curveBot - curveTop)
    drawLine(ink.faint, Offset(pad, curveBot), Offset(size.width - pad, curveBot), 1.5.dp.toPx())
    // The swell: a filled curve through each note's loudness, traced as the notes play.
    val reached = v.notes.indices.lastOrNull { t >= v.noteAt(it) } ?: -1
    if (reached >= 0) {
        val pts = (0..reached).map { Offset(xOf(it), levelY(v.levels[it])) }.toMutableList()
        if (reached < n - 1) {
            val e = progress(t, v.noteAt(reached), v.stepMs)
            pts += Offset(mix(xOf(reached), xOf(reached + 1), e), mix(levelY(v.levels[reached]), levelY(v.levels[reached + 1]), e))
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (k in 1 until pts.size) {
                val a = pts[k - 1]; val b = pts[k]
                cubicTo((a.x + b.x) / 2, a.y, (a.x + b.x) / 2, b.y, b.x, b.y)
            }
        }
        val fill = Path().apply { addPath(line); lineTo(pts.last().x, curveBot); lineTo(pts[0].x, curveBot); close() }
        drawPath(fill, ink.accent, alpha = 0.18f)
        drawPath(line, ink.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
    v.notes.forEachIndexed { i, chord ->
        val at = v.noteAt(i)
        val x = xOf(i)
        val r = mix(5f, 17f, ((v.levels[i] - 20).coerceAtLeast(0) / 107f)).dp.toPx() * minOf(1f, slot / 40.dp.toPx() + 0.35f)
        if (t < at) {
            chord.forEach { drawCircle(ink.faint, 4.dp.toPx(), Offset(x, yOf(it))) }
            return@forEachIndexed
        }
        val p = progress(t, at, 300)
        val f = flash(t, at, 500)
        chord.forEach { m ->
            val c = Offset(x, yOf(m))
            if (f > 0f) drawCircle(ink.accent, r * (1f + 1.6f * (1 - f)), c, style = Stroke(2.dp.toPx()), alpha = 0.5f * f * (v.levels[i] / 127f))
            drawCircle(lerp(ink.accent, ink.ink, progress(t, at + 300, 900) * 0.4f), r * backOut(p), c)
        }
        drawCircle(ink.accent, 3.5.dp.toPx(), Offset(x, levelY(v.levels[i])), alpha = smooth(p * 2))
        v.marks[i]?.let { mk ->
            val dyn = mk.length <= 2 && mk.all { it in "pmf" }
            text(ink, mk, Offset(x, size.height * 0.9f), if (dyn) TextKind.TITLE else TextKind.LABEL, if (dyn) 26.sp else 13.sp,
                if (i == v.marks.keys.filter { t >= v.noteAt(it) }.maxOrNull()) ink.accent else ink.dim, alpha = smooth(p * 2), scale = 0.6f + 0.4f * backOut(p))
        }
    }
}

// ---- Smooth and short --------------------------------------------------------------------------------------------

internal fun DrawScope.articulationScene(v: ArticulationVisual, t: Int, ink: StageInk) {
    val pad = 16.dp.toPx()
    val l = pianoFor(v.low, v.high, size.width - 2 * pad, size.height * 0.2f)
    val kTop = size.height - pad - l.height
    val rowsTop = pad; val rowsBot = kTop - 12.dp.toPx()
    val rh = (rowsBot - rowsTop) / v.touches.size
    val n = v.notes.size
    val slot = (size.width - 2 * pad) / n
    val lo = v.notes.min(); val hi = v.notes.max()
    val lit = HashMap<Int, Color>(); val sinks = HashMap<Int, Float>()
    val playing = v.touches.indices.lastOrNull { t >= v.passAt(it) } ?: -1
    v.touches.forEachIndexed { k, touch ->
        val top = rowsTop + k * rh
        val nTop = top + 50.dp.toPx(); val nBot = top + rh - 10.dp.toPx()
        fun y(m: Int) = if (hi == lo) (nTop + nBot) / 2 else nBot - (m - lo).toFloat() / (hi - lo) * (nBot - nTop)
        text(ink, v.label(k), Offset(pad, top + 4.dp.toPx()), TextKind.LABEL, 13.sp, if (k == playing) ink.accent else ink.dim, centred = false)
        val bh = 10.dp.toPx()
        v.notes.forEachIndexed { i, m ->
            val cx = pad + (i + 0.5f) * slot
            val (a, b) = if (touch == Touch.LEGATO) (cx - slot * 0.46f) to (cx + slot * 0.54f) else (cx - slot * 0.16f) to (cx + slot * 0.16f)
            val yy = y(m)
            drawRoundRect(ink.dim, Offset(a, yy - bh / 2), Size(b - a, bh), CornerRadius(bh / 2), alpha = 0.25f)
            val at = v.noteAt(k, i)
            if (t < at) return@forEachIndexed
            val e = progress(t, at, v.hold(touch))
            val sounding = t < at + v.hold(touch)
            drawRoundRect(if (sounding) ink.accent else ink.ink.copy(alpha = 0.55f), Offset(a, yy - bh / 2), Size((b - a) * e, bh), CornerRadius(bh / 2))
            if (touch == Touch.STACCATO) drawCircle(ink.accent, 3.5.dp.toPx() * backOut(progress(t, at, 250)), Offset(cx, yy - bh - 6.dp.toPx()))
            if (sounding) { lit[m] = ink.accent; sinks[m] = if (touch == Touch.LEGATO) 1f else press(t - at) }
        }
        // The slur over the smooth row, drawn as it plays.
        if (touch == Touch.LEGATO && t >= v.passAt(k)) {
            val e = progress(t, v.passAt(k), n * v.stepMs)
            val x0 = pad + 0.2f * slot; val x1 = pad + (n - 0.2f) * slot
            val ys = nTop - 10.dp.toPx()
            val slur = Path().apply { moveTo(x0, ys + 12.dp.toPx()); quadraticTo((x0 + x1) / 2, ys - 22.dp.toPx(), x1, ys + 12.dp.toPx()) }
            drawPath(trim(slur, e), ink.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
    drawPiano(l, Offset(pad, kTop), ink, lit = { lit[it] }, sink = { sinks[it] ?: 0f }, name = { if (it in lit) keyLabel(it) else null })
}

// ---- A phrase on the staff ---------------------------------------------------------------------------------------

/** The glyph for a note [beats] long: the longest plain or dotted value that fits. */
private fun glyph(beats: Float): Pair<NoteValue, Boolean> {
    val options = listOf(NoteValue.WHOLE to false, NoteValue.HALF to true, NoteValue.HALF to false, NoteValue.QUARTER to true, NoteValue.QUARTER to false,
        NoteValue.EIGHTH to true, NoteValue.EIGHTH to false, NoteValue.SIXTEENTH to false)
    return options.firstOrNull { (v, d) -> v.beats * (if (d) 1.5f else 1f) <= beats + 0.001f } ?: (NoteValue.SIXTEENTH to false)
}

internal fun DrawScope.phraseScene(v: PhraseVisual, t: Int, ink: StageInk) {
    val pad = 12.dp.toPx()
    val grand = v.hasRight && v.hasLeft
    val l = pianoFor(v.low, v.high, size.width - 2 * pad, size.height * 0.2f)
    val kTop = size.height - pad - l.height
    val staffBot = kTop - 10.dp.toPx()
    val s = if (grand) minOf(staffBot / 16.5f, 15.dp.toPx(), (size.width - 2 * pad) / 22f) else minOf(staffBot / 11f, 18.dp.toPx(), (size.width - 2 * pad) / 20f)
    val staves = if (grand) {
        val gap = 3.8f * s
        val trebleBottom = staffBot / 2 - gap / 2 + 1.5f * s
        listOf(Stave(Clef.TREBLE, trebleBottom, s), Stave(Clef.BASS, trebleBottom + gap + 4 * s, s))
    } else listOf(Stave(if (v.hasRight) Clef.TREBLE else Clef.BASS, staffBot / 2 + 2.5f * s, s))
    fun staveOf(n: PhraseNote) = if (!grand) staves[0] else if (n.left) staves[1] else staves[0]
    val left = pad; val right = size.width - pad
    staves.forEach { st ->
        for (i in 0..4) {
            val e = outCubic(progress(t, i * 60, 420))
            if (e > 0f) drawLine(ink.line, Offset(left, st.y(i * 2)), Offset(left + (right - left) * e, st.y(i * 2)), 0.09f * s)
        }
        drawClef(st.clef == Clef.TREBLE, left + 0.35f * s, st.top, s, ink.ink, progress(t, 300, 700))
    }
    if (grand) drawLine(ink.line, Offset(left, staves[0].top), Offset(left, staves[0].top + (staves[1].bottom - staves[0].top) * smooth(progress(t, 200, 400))), 0.14f * s)
    var x = left + 3.4f * s
    val sig = abs(v.keyFifths)
    if (sig > 0) staves.forEach { st ->
        keySignaturePositions(v.keyFifths, st.clef).forEachIndexed { i, pos ->
            val p = progress(t, 900 + i * 150, 280)
            if (p > 0f) { val c = Offset(x + i * 1.15f * s + 0.5f * s, st.y(pos)); if (v.keyFifths > 0) drawSharp(c, s, ink.ink, backOut(p)) else drawFlat(c, s, ink.ink, backOut(p)) }
        }
    }
    x += sig * 1.15f * s + if (sig > 0) 0.8f * s else 0f
    val tp = progress(t, 1000, 350)
    staves.forEach { st ->
        text(ink, "${v.beats}", Offset(x + 0.9f * s, st.y(6)), TextKind.TITLE, pxSp(2.3f * s), ink.ink, alpha = smooth(tp * 2), scale = backOut(tp))
        text(ink, "4", Offset(x + 0.9f * s, st.y(2)), TextKind.TITLE, pxSp(2.3f * s), ink.ink, alpha = smooth(tp * 2), scale = backOut(tp))
    }
    x += 2.6f * s
    // Where each beat sits: onsets spread by a soft power of their gaps, so quick notes get room and long ones do not hog it.
    val onsets = (v.notes.map { it.start } + v.span).distinct().sorted()
    val weights = onsets.zipWithNext { a, b -> Math.pow((b - a).toDouble(), 0.55).toFloat() }
    val total = weights.sum().coerceAtLeast(0.001f)
    val x0 = x + 0.9f * s; val xEnd = right - 0.6f * s
    val xs = FloatArray(onsets.size)
    var acc = 0f
    onsets.indices.forEach { k -> xs[k] = x0 + acc / total * (xEnd - x0); if (k < weights.size) acc += weights[k] }
    fun xAt(beat: Float): Float {
        val k = onsets.indexOfLast { it <= beat }.coerceAtLeast(0)
        if (k >= onsets.lastIndex) return xs.last()
        return mix(xs[k], xs[k + 1], (beat - onsets[k]) / (onsets[k + 1] - onsets[k]))
    }
    val sigAcc = keySignature(v.keyFifths).associate { it.letter to it.accidental }
    // Barlines between bars.
    var bar = v.beats.toFloat()
    while (bar < v.span - 0.01f) {
        val k = onsets.indexOfFirst { it >= bar - 0.001f }
        if (k > 0) {
            val bx = (xs[k - 1] + xs[k]) / 2 + (xs[k] - xs[k - 1]) * 0.2f
            drawLine(ink.line, Offset(bx, staves.first().top), Offset(bx, staves.last().bottom), 0.12f * s, alpha = smooth(progress(t, v.at(0, bar) - 300, 300)))
        }
        bar += v.beats
    }
    val cursor = v.cursor(t)
    val focus = cursor?.let { v.passes[it.first] } ?: v.passes.last()
    // The eyes' band, one beat ahead of the playing.
    if (v.lookAhead && cursor != null) {
        val a = xAt(cursor.second + 0.2f); val b = xAt(minOf(v.span, cursor.second + 1.2f))
        if (b > a) {
            drawRoundRect(ink.accentSoft, Offset(a, staves.first().top - 1.5f * s), Size(b - a, staves.last().bottom - staves.first().top + 3f * s), CornerRadius(8.dp.toPx()), alpha = 0.7f)
            text(ink, "Eyes here", Offset((a + b) / 2, staves.first().top - 2.4f * s), TextKind.LABEL, 12.sp, ink.accent)
        }
    }
    val lit = HashMap<Int, Color>(); val sinks = HashMap<Int, Float>()
    v.notes.forEach { n ->
        val k = onsets.indexOf(n.start)
        val st = staveOf(n)
        val arrive = v.at(0, n.start)
        val slide = progress(t, arrive - 400, 400)
        if (slide <= 0f) return@forEach
        val nx = right + 2f * s + (xs[k] - right - 2f * s) * outCubic(slide)
        val front = focus == PhraseHands.BOTH || (focus == PhraseHands.LEFT) == n.left
        val sounding = cursor != null && cursor.second >= n.start && cursor.second < n.start + n.length && !n.rest
        val handCol = if (n.left) LeftHandColor else ink.accent
        val col = (if (sounding) handCol else if (t < arrive) ink.accent else ink.ink).copy(alpha = smooth(slide * 2.5f) * (if (front) 1f else 0.35f))
        val (value, dotted) = glyph(n.length)
        if (n.rest) { drawRest(value, nx, st.mid, s, col); return@forEach }
        drawChordNote(StaffNote(n.pitches, value, dotted), nx, st, s, col, if (sounding) 1.12f else 1f, sigAcc, ink)
        if (sounding) n.pitches.forEach { p ->
            lit[p.midi] = handCol.copy(alpha = if (front) 1f else 0.4f)
            sinks[p.midi] = press(((cursor!!.second - n.start) * v.beatMs).toInt())
        }
    }
    if (cursor != null) {
        val cx = xAt(cursor.second)
        drawLine(ink.accent, Offset(cx, staves.first().top - s), Offset(cx, staves.last().bottom + s), 2.dp.toPx(), alpha = 0.5f)
    }
    // Which hand this pass is about.
    if (v.passes.size > 1 || v.passes[0] != PhraseHands.BOTH) {
        val k = cursor?.first ?: v.passes.indices.lastOrNull { t >= v.passAt(it) } ?: 0
        pill(ink, v.passes[k].label, Offset(right - 50.dp.toPx(), 16.dp.toPx()), true, progress(t, v.passAt(k) - 200, 380))
    }
    drawPiano(l, Offset(pad, kTop), ink, lit = { lit[it] }, sink = { sinks[it] ?: 0f }, name = { if (it in lit) keyLabel(it, v.keyFifths < 0) else null })
}

// ---- Falling notes -----------------------------------------------------------------------------------------------

internal fun DrawScope.laneScene(v: LaneVisual, time: Int, ink: StageInk) {
    // A still (animations off) shows the notes about to land rather than an empty lane.
    val t = if (time >= v.durationMs) v.hitAt(0f) - 1 else time
    val pad = 12.dp.toPx()
    val l = pianoFor(v.low, v.high, size.width - 2 * pad, size.height * 0.24f)
    val kTop = size.height - pad - l.height
    val laneTop = size.height * 0.2f
    val o = Offset(pad, kTop)
    val now = v.beatAt(t)
    val perBeat = (kTop - laneTop) / v.fallBeats
    fun y(beat: Float) = kTop - (beat - now) * perBeat
    // Beat lines, stronger at the start of a bar.
    val firstBeat = kotlin.math.floor(now).toInt()
    for (b in firstBeat..(firstBeat + v.fallBeats.toInt() + 1)) {
        val yy = y(b.toFloat())
        if (yy < laneTop || yy > kTop) continue
        val strong = Math.floorMod(b, v.beats) == 0
        drawLine(ink.faint, Offset(pad, yy), Offset(size.width - pad, yy), (if (strong) 2f else 1f).dp.toPx())
    }
    val lit = HashMap<Int, Color>(); val sinks = HashMap<Int, Float>()
    v.notes.forEach { n ->
        val a = y(n.start + n.length); val b = y(n.start)
        if (b < laneTop || a > kTop + 2f) {
            if (now >= n.start && now < n.start + n.length) n.pitches.forEach { lit[it.midi] = if (n.left) LeftHandColor else ink.accent }
            return@forEach
        }
        val col = if (n.left) LeftHandColor else ink.accent
        val sounding = now >= n.start && now < n.start + n.length
        n.pitches.forEach { p ->
            val r = l.rect(p.midi)
            val w = (r.right - r.left) * (if (isBlack(p.midi)) 0.95f else 0.78f)
            val cx = o.x + r.centerX
            val top = maxOf(a, laneTop); val bot = minOf(b, kTop)
            if (bot > top) {
                drawRoundRect(col.copy(alpha = if (sounding) 1f else 0.85f), Offset(cx - w / 2, top), Size(w, bot - top), CornerRadius(minOf(w / 2, 8.dp.toPx())))
                if (b - a > 26.dp.toPx() && b <= kTop) text(ink, p.letterName, Offset(cx, bot - 10.dp.toPx()), TextKind.LABEL, 11.sp, Color.White)
            }
            if (sounding) {
                lit[p.midi] = col
                sinks[p.midi] = maxOf(press(((now - n.start) * v.beatMs).toInt()), 0.5f)
                drawCircle(col, w * 0.7f, Offset(cx, kTop), alpha = 0.35f)
            }
        }
    }
    // The hit line, then the keys.
    drawLine(ink.accent, Offset(pad, kTop - 1.dp.toPx()), Offset(size.width - pad, kTop - 1.dp.toPx()), 2.dp.toPx(), alpha = 0.7f)
    drawPiano(l, o, ink, lit = { lit[it] }, sink = { sinks[it] ?: 0f })
    // The count across the top: the beat being counted lit and larger; a count-in says so.
    val counting = t >= v.startAt - (v.countIn * v.beats * v.beatMs).toInt() && now < v.total
    val cw = (size.width - 2 * pad) / v.beats
    for (b in 1..v.beats) {
        val on = counting && Math.floorMod(kotlin.math.floor(now).toInt(), v.beats) == b - 1
        val beatStart = kotlin.math.floor(now)
        val pulse = if (on) 1f - ((now - beatStart) * 2f).coerceIn(0f, 1f) else 0f
        text(ink, "$b", Offset(pad + (b - 0.5f) * cw, laneTop * 0.5f), TextKind.TITLE, if (on) 30.sp else 22.sp, if (on) ink.accent else ink.faint, scale = 1f + 0.2f * pulse)
    }
    if (v.countIn > 0 && now < 0f && counting) pill(ink, "Count-in", Offset(size.width / 2, laneTop + 20.dp.toPx()), true, progress(t, v.startAt - (v.countIn * v.beats * v.beatMs).toInt(), 380))
}

// ---- The song's section bar --------------------------------------------------------------------------------------

internal fun DrawScope.songMapScene(v: SongMapVisual, t: Int, ink: StageInk) {
    val pad = 16.dp.toPx()
    val n = v.sections.size
    val barTop = size.height * 0.42f; val barH = minOf(size.height * 0.2f, 56.dp.toPx())
    val left = pad; val right = size.width - pad
    val secW = (right - left) / n
    val head = v.playhead(t)
    val k = head?.first ?: v.passes.indices.lastOrNull { t >= v.passAt(it) } ?: -1
    val pass = if (k >= 0) v.passes[k] else null
    val appear = smooth(progress(t, 0, 400))
    // Sections: plain until played; the played part fills.
    for (s in 0 until n) {
        val a = left + s * secW + 2.dp.toPx(); val w = secW - 4.dp.toPx()
        drawRoundRect(ink.faint, Offset(a, barTop), Size(w, barH), CornerRadius(12.dp.toPx()), alpha = appear)
        val played = when {
            head == null -> if (k >= 0 && s in v.passes.take(k + 1).flatMap { it.from..it.to }) 1f else 0f
            head.second >= s + 1 -> if (s >= (pass?.from ?: 0)) 1f else (if (k > 0 || s < (pass?.from ?: 0)) 0.5f else 0f)
            head.second >= s -> head.second - s
            else -> 0f
        }
        if (played > 0f) drawRoundRect(ink.accentSoft, Offset(a, barTop), Size(w * played.coerceIn(0f, 1f), barH), CornerRadius(12.dp.toPx()))
        val inLoop = pass != null && pass.to - pass.from + 1 < n && s in pass.from..pass.to
        text(ink, v.sections[s], Offset(a + w / 2, barTop + barH / 2), TextKind.TITLE, 20.sp, if (inLoop) ink.accent else ink.ink, alpha = appear)
    }
    // The loop bracket over a pass that repeats part of the song.
    if (pass != null && pass.to - pass.from + 1 < n) {
        val p = progress(t, v.passAt(k) - 400, 380)
        val a = left + pass.from * secW; val b = left + (pass.to + 1) * secW
        val y = barTop - 14.dp.toPx()
        val e = outCubic(p)
        val br = Path().apply { moveTo(a + 4.dp.toPx(), barTop - 2.dp.toPx()); lineTo(a + 4.dp.toPx(), y); lineTo(b - 4.dp.toPx(), y); lineTo(b - 4.dp.toPx(), barTop - 2.dp.toPx()) }
        drawPath(trim(br, e), ink.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        pill(ink, "Loop", Offset((a + b) / 2, y - 22.dp.toPx()), true, p)
    }
    // Speed.
    if (pass != null && (pass.speed < 100 || v.passes.any { it.speed < 100 })) {
        pill(ink, "Speed ${pass.speed}%", Offset(right - 54.dp.toPx(), size.height * 0.84f), pass.speed < 100, progress(t, v.passAt(k), 380), 14.sp)
    }
    // The playhead, and the tune's notes floating up from it.
    if (head != null) {
        val x = left + head.second * secW
        drawLine(ink.accent, Offset(x, barTop - 8.dp.toPx()), Offset(x, barTop + barH + 8.dp.toPx()), 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(ink.accent, 7.dp.toPx(), Offset(x, barTop + barH + 10.dp.toPx()))
    }
    v.cues.forEach { c ->
        val age = t - c.atMs
        if (age < 0 || age > 900) return@forEach
        val pk = v.passes.indices.last { c.atMs >= v.passAt(it) }
        val p = v.passes[pk]
        val pos = p.from + (c.atMs - v.passAt(pk)).toFloat() / (v.sectionMs * 100 / p.speed)
        val x = left + pos * secW
        val f = age / 900f
        val y = barTop + barH + 70.dp.toPx() - f * 36.dp.toPx()
        drawLoneNote(NoteValue.EIGHTH, Offset(x, y), 5.dp.toPx(), ink.accent.copy(alpha = 1 - f))
    }
}

// ---- A MIDI keyboard plugged in ----------------------------------------------------------------------------------

internal fun DrawScope.midiScene(v: MidiVisual, t: Int, ink: StageInk) {
    val pad = 14.dp.toPx()
    // The phone on the right, the piano on the left, the cable between them.
    val phoneW = minOf(size.width * 0.22f, 84.dp.toPx()); val phoneH = minOf(phoneW * 1.9f, size.height * 0.62f)
    val phone = Offset(size.width - pad - phoneW, size.height * 0.1f)
    val pianoW = size.width * 0.6f
    val l = pianoFor(53, 72, pianoW, size.height * 0.26f)
    val pianoTop = size.height * 0.4f
    val po = Offset(pad, pianoTop)
    // The digital piano's body around its keys, on two legs.
    drawRoundRect(PianoCase, po - Offset(8.dp.toPx(), 22.dp.toPx()), Size(l.width + 16.dp.toPx(), l.height + 30.dp.toPx()), CornerRadius(10.dp.toPx()))
    drawRect(PianoCase, Offset(po.x + 6.dp.toPx(), pianoTop + l.height + 6.dp.toPx()), Size(8.dp.toPx(), size.height - pianoTop - l.height - pad))
    drawRect(PianoCase, Offset(po.x + l.width - 14.dp.toPx(), pianoTop + l.height + 6.dp.toPx()), Size(8.dp.toPx(), size.height - pianoTop - l.height - pad))
    drawLine(ink.faint, Offset(0f, size.height - pad), Offset(size.width, size.height - pad), 2.dp.toPx())
    val cur = v.signals.indices.lastOrNull { t >= v.signalAt(it) } ?: -1
    val lit = HashMap<Int, Color>(); val sinks = HashMap<Int, Float>()
    if (cur >= 0) v.signals[cur].note?.let { m ->
        val u = t - v.signalAt(cur)
        if (u < 700) { lit[m] = ink.accent.copy(alpha = 1f - u / 700f); sinks[m] = press(u) }
    }
    drawPiano(l, po, ink, lit = { lit[it] }, sink = { sinks[it] ?: 0f })
    // A pedal under the piano, pressed by pedal signals.
    val pedalAt = Offset(po.x + l.width / 2, size.height - pad - 6.dp.toPx())
    val pedalDown = if (cur >= 0 && v.signals[cur].note == null) 1f - progress(t, v.signalAt(cur) + 700, 200) else 0f
    drawLine(Brass, pedalAt - Offset(16.dp.toPx(), 4.dp.toPx() * (1 - pedalDown)), pedalAt + Offset(10.dp.toPx(), 0f), 5.dp.toPx(), cap = StrokeCap.Round)
    // The cable: from the back of the piano, up and over, into the bottom of the phone.
    val from = Offset(po.x + l.width + 6.dp.toPx(), pianoTop + l.height * 0.4f)
    val port = Offset(phone.x + phoneW / 2, phone.y + phoneH + 4.dp.toPx())
    val cable = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(from.x + 50.dp.toPx(), from.y + 30.dp.toPx(), port.x, port.y + 90.dp.toPx(), port.x, port.y + 16.dp.toPx())
    }
    val drawn = outCubic(progress(t, v.leadMs, v.plugMs - 300))
    drawPath(trim(cable, drawn), ink.dim, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
    // The plug slides home at the end of the cable.
    val plugged = smooth(progress(t, v.leadMs + v.plugMs - 300, 300))
    if (drawn >= 1f) drawRoundRect(ink.ink, Offset(port.x - 6.dp.toPx(), port.y + (16 - 12 * plugged).dp.toPx() - 14.dp.toPx()), Size(12.dp.toPx(), 16.dp.toPx()), CornerRadius(3.dp.toPx()))
    // The phone, its screen lighting up once connected.
    drawRoundRect(ink.ink, phone, Size(phoneW, phoneH), CornerRadius(12.dp.toPx()))
    drawRoundRect(lerp(Color(0xFF2A2420), Color(0xFF3A2A16), plugged), phone + Offset(4.dp.toPx(), 4.dp.toPx()), Size(phoneW - 8.dp.toPx(), phoneH - 8.dp.toPx()), CornerRadius(9.dp.toPx()))
    if (plugged > 0f) {
        val c = Offset(phone.x + phoneW / 2, phone.y + phoneH * 0.32f)
        drawCircle(ink.accent, 13.dp.toPx() * backOut(plugged), c)
        tick(c, 6.dp.toPx(), Color.White, plugged)
        text(ink, "Connected", Offset(c.x, phone.y + phoneH * 0.55f), TextKind.LABEL, 10.sp, Color(0xFFF8DFB4), alpha = plugged)
    }
    // Each signal runs along the cable as a pulse, and its label pops by the phone.
    v.signals.forEachIndexed { i, s ->
        val at = v.signalAt(i)
        val e = progress(t, at, v.travelMs)
        if (e > 0f && e < 1f) {
            val pm = PathMeasure(); pm.setPath(cable, false)
            drawCircle(ink.accent, 7.dp.toPx(), pm.getPosition(pm.length * e))
        }
        val p = progress(t, at + v.travelMs, 320)
        val fade = if (i == cur) 1f else 1f - smooth(progress(t, v.signalAt(cur), 250))
        if (i >= cur - 1) pill(ink, s.label, Offset(phone.x + phoneW / 2, phone.y - 18.dp.toPx()), i == cur, p, 14.sp, fade)
    }
}

/** A key's usual name without octave, for the pictures. */
private fun keyLabel(midi: Int, flats: Boolean = false) = com.bigpapituna.sharpears.engine.theory.keyLabel(midi, flats)
