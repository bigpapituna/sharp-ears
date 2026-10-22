package com.bigpapituna.sharpears.ui.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.bigpapituna.sharpears.engine.arcade.ArcadeSongs
import com.bigpapituna.sharpears.engine.arcade.CallRound
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.playground.songPicture
import com.bigpapituna.sharpears.ui.playground.star
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The song games' playfields: fireworks over a sleeping town, and a picture painted a patch a note. Both show the line
 * the pup called as badges along the bottom; the whole song at the end goes without the pup's help (the badges stay, a
 * pre-reader's way to read a tune).
 */

/** Where a key launches its rocket across the field: low keys left, high keys right, over the round's keys. */
private fun x(key: Int, keys: List<Int>, w: Float): Float {
    val i = keys.indexOf(key).coerceAtLeast(0)
    return w * (0.12f + 0.76f * (i + 0.5f) / keys.size)
}

// ---- Fireworks --------------------------------------------------------------------------------------------------------

/**
 * Fireworks: every key played sends a rocket up from the town and bursts it in its colour (higher keys burst higher); a
 * line finished fills the sky with its colours. A key the pup let go sends up a little white one.
 */
@Composable
internal fun FireworksField(play: ArcadePlay, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val keys = remember(play.round) { play.round.keys.sorted() }
    Canvas(modifier.semantics { contentDescription = "Fireworks over a town: play the song and each note lights the sky." }) {
        val r = play.call ?: return@Canvas
        val now = play.now
        sky(Color(0xFF0C1230), Color(0xFF2A2350))
        for (i in 0 until 30) {
            val sx = (i * 97 % 100) / 100f * size.width; val sy = (i * 61 % 100) / 100f * size.height * 0.6f
            drawCircle(Color.White.copy(alpha = 0.3f + 0.3f * sin(now / 500.0 + i).toFloat()), 2f, Offset(sx, sy))
        }
        val townY = size.height * 0.8f
        for (e in play.events) {
            val t = e.target
            when (e.kind) {
                FxKind.HIT, FxKind.LET_GO -> if (t != null) play.age(e.at, 1_500)?.let { p ->
                    val bx = x(t.key, keys, size.width) + ((t.id * 31) % 7 - 3) * 8f
                    val by = size.height * (0.12f + 0.38f * (1f - keys.indexOf(t.key).coerceAtLeast(0) / keys.size.coerceAtLeast(1).toFloat()))
                    val colour = if (e.kind == FxKind.LET_GO) Color(0xFFE8E4F0) else keyColour(t.key)
                    rocket(Offset(bx, townY), Offset(bx, by), colour, p, if (e.kind == FxKind.LET_GO) 0.6f else 1f)
                }
                FxKind.LINE -> play.age(e.at, 2_000)?.let { p ->
                    val line = r.plan.lines.getOrNull(e.line) ?: return@let
                    line.keys.distinct().forEachIndexed { i, k ->
                        val c = Offset(size.width * (0.15f + 0.7f * ((i * 0.37f + e.line * 0.21f) % 1f)), size.height * (0.15f + 0.3f * ((i * 0.53f) % 1f)))
                        val q = ((p - i * 0.08f) / 0.8f).coerceIn(0f, 1f)
                        if (q > 0f) burstRing(c, size.minDimension * 0.28f, keyColour(k), q)
                    }
                }
                else -> {}
            }
        }
        town(townY, now)
        r.currentLine?.let { badgeRow(it.keys, doneIn(r), size.height * 0.91f, size.minDimension * 0.065f, play.reader, measurer, now, demoIndexOf(play), play.lit) }
    }
}

private fun doneIn(r: CallRound) = when (r.phase) { CallRound.Phase.LISTEN -> r.note; CallRound.Phase.GAP, CallRound.Phase.DONE -> r.currentLine?.targets?.size ?: 0; else -> 0 }
private fun demoIndexOf(play: ArcadePlay) = play.demoIndex?.takeIf { play.call?.phase == CallRound.Phase.DEMO }

/** A rocket from [from] up to [to] in the first third of [p], then its burst. */
private fun DrawScope.rocket(from: Offset, to: Offset, colour: Color, p: Float, scale: Float) {
    if (p < 0.3f) {
        val q = p / 0.3f
        val c = Offset(from.x, from.y + (to.y - from.y) * q)
        drawLine(colour.copy(alpha = 0.5f), c, c + Offset(0f, size.height * 0.08f), 4f, cap = StrokeCap.Round)
        drawCircle(Color.White, 5f, c)
    } else burstRing(to, size.minDimension * 0.18f * scale, colour, (p - 0.3f) / 0.7f)
}

/** A firework's burst at [c] out to [r], [p] 0..1: sparks out, falling a little and fading. */
private fun DrawScope.burstRing(c: Offset, r: Float, colour: Color, p: Float) {
    val n = 16
    for (k in 0 until n) {
        val a = 2 * PI * k / n
        val d = r * (1f - (1f - p) * (1f - p))
        val s = c + Offset((cos(a) * d).toFloat(), (sin(a) * d).toFloat() + p * p * r * 0.3f)
        drawLine(colour.copy(alpha = (1f - p) * 0.6f), c + Offset((cos(a) * d * 0.7f).toFloat(), (sin(a) * d * 0.7f).toFloat()), s, 3f, cap = StrokeCap.Round)
        drawCircle(colour.copy(alpha = 1f - p), 4f * (1f - p * 0.5f), s)
    }
    if (p < 0.2f) drawCircle(Color.White.copy(alpha = 1f - p / 0.2f), r * 0.2f, c)
}

/** The sleeping town along [y]: roofs, a few lit windows. */
private fun DrawScope.town(y: Float, now: Long) {
    val dark = Color(0xFF151530)
    var x = 0f
    var i = 0
    while (x < size.width) {
        val w = size.width * (0.09f + 0.04f * (i % 3)); val h = size.height * (0.07f + 0.035f * (i * 7 % 4))
        drawRect(dark, Offset(x, y - h), Size(w, size.height - y + h))
        drawPath(Path().apply { moveTo(x - 4f, y - h); lineTo(x + w / 2, y - h - w * 0.4f); lineTo(x + w + 4f, y - h); close() }, dark)
        if (i % 2 == 0) drawRect(PupGold.copy(alpha = 0.6f + 0.2f * sin(now / 900.0 + i).toFloat()), Offset(x + w * 0.35f, y - h * 0.6f), Size(w * 0.3f, h * 0.25f))
        x += w + 6f; i++
    }
    drawRect(dark, Offset(0f, y), Size(size.width, size.height - y))
}

// ---- Song painter -----------------------------------------------------------------------------------------------------

/**
 * Song painter: a picture of the song on an easel, hidden under paper, a patch for every note of the round; each note the
 * kid plays paints the next patch (the edges first, the middle last, so the picture shows itself at the end); a patch the
 * pup played is painted lighter; a slip leaves a smudge for a moment, wiped away.
 */
@Composable
internal fun PainterField(play: ArcadePlay, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val song = remember(play.round) { ArcadeSongs.of(play.round.level) }
    Canvas(modifier.semantics { contentDescription = "A picture on an easel: play the song and it paints itself, a patch a note." }) {
        val r = play.call ?: return@Canvas
        val now = play.now
        sky(Color(0xFF3A2C24), Color(0xFF5A4334))
        drawRect(Color(0xFF8A6243), Offset(0f, size.height * 0.78f), Size(size.width, size.height * 0.22f))
        // The easel and its canvas.
        val cw = minOf(size.width * 0.78f, size.height * 0.62f * 1.25f)
        val ch = cw / 1.25f
        val left = (size.width - cw) / 2; val top = size.height * 0.06f
        drawLine(Color(0xFF6B4A2E), Offset(size.width / 2, top), Offset(size.width / 2 - cw * 0.35f, size.height * 0.84f), 10f, cap = StrokeCap.Round)
        drawLine(Color(0xFF6B4A2E), Offset(size.width / 2, top), Offset(size.width / 2 + cw * 0.35f, size.height * 0.84f), 10f, cap = StrokeCap.Round)
        drawRoundRect(Color(0xFF5A3E28), Offset(left - 8f, top - 8f), Size(cw + 16f, ch + 16f), CornerRadius(8f))
        val targets = r.plan.targets
        val n = targets.size
        val cols = ceil(sqrt(n * 1.25)).toInt().coerceAtLeast(1)
        val rows = ceil(n / cols.toDouble()).toInt().coerceAtLeast(1)
        val tw = cw / cols; val th = ch / rows
        // The tiles, the edges first: tile k of the order is the k-th note's.
        val order = (0 until cols * rows).sortedByDescending { i ->
            val cx = (i % cols + 0.5f) / cols - 0.5f; val cy = (i / cols + 0.5f) / rows - 0.5f
            hypot(cx * 1.25f, cy)
        }
        clipRect(left, top, left + cw, top + ch) {
            drawRect(Color(0xFFFBF5EA), Offset(left, top), Size(cw, ch))
            // The picture, then paper over every tile not painted yet.
            inset(left, top, size.width - left - cw, size.height - top - ch) { painting(song.picture) }
            val newest = play.events.lastOrNull { it.kind == FxKind.HIT || it.kind == FxKind.LET_GO }
            val tiles = cols * rows
            for (k in 0 until tiles) {
                val tile = order[k]
                val o = Offset(left + (tile % cols) * tw, top + (tile / cols) * th)
                // The notes spread over every tile (a few notes paint two), so the middle shows only at the song's end.
                val t = targets.getOrNull(k * n / tiles)
                val state = t?.let { play.played[it.id] }
                val reveal = when {
                    r.over -> 1f
                    t == null -> 1f
                    state == null -> 0f
                    newest?.target?.id == t.id -> play.age(newest.at, 450) ?: 1f
                    else -> 1f
                }
                if (reveal < 1f) drawRect(Color(0xFFFBF5EA).copy(alpha = 1f - reveal), o, Size(tw + 0.5f, th + 0.5f))
                if (state == false) drawRect(Color.White.copy(alpha = 0.35f), o, Size(tw + 0.5f, th + 0.5f))
                if (state == null && t != null) drawRect(Color(0xFFD9CDBB), o, Size(tw, th), style = Stroke(1f))
            }
            // A smudge on the next patch after a slip, wiped away.
            play.events.lastOrNull { it.kind == FxKind.SLIP }?.let { e ->
                play.age(e.at, 1_200)?.let { p ->
                    val k = ((r.hits + r.helped) * tiles + n - 1) / n
                    if (k < tiles) {
                        val tile = order[k]
                        val c = Offset(left + (tile % cols + 0.5f) * tw, top + (tile / cols + 0.5f) * th)
                        drawCircle(Color(0xFF6E6A66).copy(alpha = 0.55f * (1f - p)), minOf(tw, th) * 0.6f, c)
                    }
                }
            }
        }
        if (r.over) sparkles(Offset(size.width / 2, top + ch / 2), cw * 0.3f, ((now / 1500f) % 1f))
        r.currentLine?.let { badgeRow(it.keys, doneIn(r), size.height * 0.91f, size.minDimension * 0.065f, play.reader, measurer, now, demoIndexOf(play), play.lit) }
    }
}

/** The song's picture filling the scope: its background, then its subject. */
private fun DrawScope.painting(picture: String) {
    when (picture) {
        "star" -> drawRect(Brush.verticalGradient(listOf(Color(0xFF1B2450), Color(0xFF3B3F86))))
        "lamb" -> { drawRect(Color(0xFFBFE6FA)); drawRect(Color(0xFF8CCB6E), Offset(0f, size.height * 0.62f), Size(size.width, size.height * 0.38f)) }
        "buns" -> { drawRect(Color(0xFFFFE9C9)); drawOval(Color.White, Offset(size.width * 0.12f, size.height * 0.62f), Size(size.width * 0.76f, size.height * 0.3f)) }
        else -> drawRect(Color(0xFF9ED8F7))
    }
    if (picture == "sun") sunPicture() else songPicture(picture)
    if (picture == "star") for (i in 0 until 12) drawPath(star(Offset((i * 83 % 100) / 100f * size.width, (i * 47 % 100) / 100f * size.height * 0.4f), 5f), Color.White.copy(alpha = 0.8f))
}

/** Ode to Joy's picture: a smiling sun over green hills with a rainbow. */
private fun DrawScope.sunPicture() {
    val w = size.width; val h = size.height
    listOf(60, 62, 64, 65, 67, 69, 71).forEachIndexed { i, k ->
        val rr = w * 0.62f - i * w * 0.035f
        drawArc(keyColour(k), 180f, 180f, false, Offset(w / 2 - rr, h * 0.9f - rr * 0.8f), Size(rr * 2, rr * 1.6f), style = Stroke(w * 0.03f))
    }
    drawOval(Color(0xFF6DB35D), Offset(-w * 0.2f, h * 0.7f), Size(w * 0.8f, h * 0.6f))
    drawOval(Color(0xFF8CCB6E), Offset(w * 0.35f, h * 0.74f), Size(w * 0.9f, h * 0.6f))
    val c = Offset(w * 0.5f, h * 0.36f); val r = h * 0.17f
    for (k in 0 until 12) {
        val a = 2 * PI * k / 12
        drawLine(PupGold, c + Offset((cos(a) * r * 1.25f).toFloat(), (sin(a) * r * 1.25f).toFloat()), c + Offset((cos(a) * r * 1.6f).toFloat(), (sin(a) * r * 1.6f).toFloat()), r * 0.16f, cap = StrokeCap.Round)
    }
    drawCircle(PupGold, r, c)
    drawCircle(Color(0xFF3A2A20), r * 0.1f, c + Offset(-r * 0.35f, -r * 0.15f)); drawCircle(Color(0xFF3A2A20), r * 0.1f, c + Offset(r * 0.35f, -r * 0.15f))
    drawArc(Color(0xFF3A2A20), 20f, 140f, false, c + Offset(-r * 0.4f, -r * 0.05f), Size(r * 0.8f, r * 0.55f), style = Stroke(r * 0.1f, cap = StrokeCap.Round))
}
