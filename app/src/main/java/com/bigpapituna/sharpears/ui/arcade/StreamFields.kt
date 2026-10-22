package com.bigpapituna.sharpears.ui.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.arcade.Target
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.playground.balloon
import com.bigpapituna.sharpears.ui.playground.burst
import com.bigpapituna.sharpears.ui.playground.piano
import com.bigpapituna.sharpears.ui.playground.ripples
import com.bigpapituna.sharpears.ui.practice.PianoGeometry
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.skin.drawAnimal
import com.bigpapituna.sharpears.ui.skin.inkOn
import kotlin.math.PI
import kotlin.math.sin

/*
 * The three stream games' playfields: balloons rising, treats flying past the pup, moles peeking out of the keys. Each
 * reads the round's live targets and its clock inside its Canvas, so a frame redraws without recomposing.
 */

/** Where a stream target is, 0 (just came) to 1 (going), on the round's clock. */
private fun ArcadePlay.along(t: Target, at: Long = now): Float {
    val s = stream ?: return 0f
    return ((s.clock(at) - t.atMs).toFloat() / t.lifeMs).coerceIn(0f, 1f)
}

// ---- Balloon pop ------------------------------------------------------------------------------------------------------

/**
 * Balloon pop: each balloon rises from below the grass to above the sky in its key's colour, its animal or letter on it;
 * at the first levels it rises over its own key on the keys below. Two tied with a gold bow pop together. A balloon
 * played bursts into confetti; one that gets away just floats off.
 */
@Composable
internal fun BalloonField(play: ArcadePlay, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val night = isSystemInDarkTheme()
    val (low, high) = remember(play.round) { keyRange(play.round.keys) }
    Canvas(modifier.semantics { contentDescription = "Balloons rising: play each balloon's key to pop it." }) {
        val now = play.now
        if (night) sky(Color(0xFF263A6B), Color(0xFF7C6AA8)) else sky(Color(0xFF7EC8F6), Color(0xFFDDF2FF))
        for (i in 0 until 3) {
            val x = ((now / 45f + i * size.width / 2.7f) % (size.width + 240f)) - 120f
            cloud(Offset(x, size.height * (0.12f + 0.13f * i)), size.width * 0.12f, Color.White.copy(alpha = if (night) 0.25f else 0.8f))
        }
        grass(size.height * 0.92f, if (night) Color(0xFF3F6B45) else Color(0xFF8CCB6E), if (night) Color(0xFF2A4A30) else Color(0xFF5DA153))
        val g = PianoGeometry(low, high, size.width, size.height, 0f)
        val r = minOf(size.width / 10f, size.height / 9f)
        fun position(t: Target, at: Long, key: Int = t.key): Offset {
            val p = play.along(t, at)
            val x = t.lane?.let { it * size.width + (t.keys.indexOf(key) - (t.keys.size - 1) / 2f) * r * 2.3f } ?: g.rect(key).center.x
            val sway = sin(at / 520.0 + t.id).toFloat() * r * 0.18f
            return Offset(x + sway, size.height + r * 3f - (size.height + r * 6.5f) * p)
        }
        for (t in play.stream?.live.orEmpty()) {
            val half = play.last(FxKind.HALF, t.id)
            if (t.pair) {
                // The ribbon between the two strings and its bow.
                val a = position(t, now, t.keys[0]); val b = position(t, now, t.keys[1])
                val knot = Offset((a.x + b.x) / 2, maxOf(a.y, b.y) + r * 3.3f)
                drawLine(PupGold, a + Offset(0f, r * 2.9f), knot, 2.dp.toPx()); drawLine(PupGold, b + Offset(0f, r * 2.9f), knot, 2.dp.toPx())
                drawCircle(PupGold, r * 0.22f, knot)
            }
            for (k in t.keys) {
                val c = position(t, now, k)
                val glow = k in play.lit || (half != null && half.key % 12 == k % 12)
                balloon(c, r, keyColour(k), if (play.reader) NoteColours.letter(k) else null, measurer, glow)
                if (!play.reader) NoteColours.animal(k)?.let { drawAnimal(it, c, r * 1.05f, inkOn(k), keyColour(k)) }
            }
        }
        for (e in play.events) {
            if (e.kind != FxKind.HIT) continue
            val t = e.target ?: continue
            val p = play.age(e.at, 700) ?: continue
            for (k in t.keys) burst(position(t, e.at, k), r, keyColour(k), p)
        }
    }
}

// ---- Feed the pup -----------------------------------------------------------------------------------------------------

/**
 * Feed the pup: bones with a key's badge fly in from the right in a gentle arc and on past the pup. A treat played zooms
 * into his mouth (he munches, and grows a little rounder through the round); one that gets away drifts up and off.
 */
@Composable
internal fun FeedField(play: ArcadePlay, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val night = isSystemInDarkTheme()
    val still = reducedMotion()
    BoxWithConstraints(modifier) {
        val pupSize = (minOf(maxWidth, maxHeight) * 0.42f)
        val total = remember(play.round) { play.stream?.plan?.targets?.size?.coerceAtLeast(1) ?: 1 }
        val eaten = play.played.count { it.value }
        val round = 1f + 0.16f * eaten / total
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Treats flying past the pup: play each treat's key to feed him." }) {
            val now = play.now
            if (night) sky(Color(0xFF2C3557), Color(0xFF5A4E7A)) else sky(Color(0xFFFFE3B8), Color(0xFFFFF6E6))
            drawCircle(if (night) Color(0xFFF4F0D8) else PupGold, size.minDimension * 0.07f, Offset(size.width * 0.85f, size.height * 0.1f))
            for (i in 0 until 2) {
                val x = ((now / 50f + i * size.width / 1.8f) % (size.width + 240f)) - 120f
                cloud(Offset(x, size.height * (0.2f + 0.14f * i)), size.width * 0.11f, Color.White.copy(alpha = if (night) 0.25f else 0.85f))
            }
            // A picnic blanket under the pup: red gingham.
            val blanketY = size.height * 0.84f
            grass(blanketY - size.height * 0.04f, if (night) Color(0xFF3F6B45) else Color(0xFF9BD17C), if (night) Color(0xFF2A4A30) else Color(0xFF6DB35D))
            val bw = size.width * 0.5f; val bh = size.height * 0.12f; val bx = size.width * 0.02f
            drawRoundRect(Color(0xFFFFF4EC), Offset(bx, blanketY), Size(bw, bh), CornerRadius(10f))
            val cell = bw / 8
            for (i in 0 until 8) drawRect(Color(0xFFE5484D).copy(alpha = 0.55f), Offset(bx + cell * i + cell * 0.25f, blanketY), Size(cell * 0.5f, bh))
            for (j in 0 until 3) drawRect(Color(0xFFE5484D).copy(alpha = 0.45f), Offset(bx, blanketY + bh * (j + 0.3f) / 3f), Size(bw, bh * 0.14f))
            val mouth = Offset(pupSize.toPx() * 0.62f, size.height - pupSize.toPx() * 0.62f)
            val r = minOf(size.width / 14f, size.height / 11f)
            fun position(t: Target, at: Long): Offset {
                val p = play.along(t, at)
                val y0 = size.height * 0.1f + (t.lane ?: 0.5f) * size.height * 0.42f
                return Offset(size.width + r * 2 - (size.width + r * 4) * p, y0 - sin(p * PI).toFloat() * size.height * 0.08f)
            }
            for (t in play.stream?.live.orEmpty()) bone(position(t, now), r, t.key, play, measurer)
            for (e in play.events) {
                val t = e.target ?: continue
                when (e.kind) {
                    FxKind.HIT -> {
                        play.age(e.at, 380)?.let { p ->
                            val from = position(t, e.at)
                            bone(Offset(from.x + (mouth.x - from.x) * p, from.y + (mouth.y - from.y) * p), r * (1f - 0.7f * p), t.key, play, measurer)
                        }
                        play.age(e.at + 380, 500)?.let { p -> sparkles(mouth, r, p) }
                    }
                    FxKind.GONE -> play.age(e.at, 900)?.let { p ->
                        val from = position(t, e.at)
                        bone(Offset(from.x - r * p, from.y - size.height * 0.2f * p), r * (1 - 0.4f * p), t.key, play, measurer, alpha = 1 - p)
                    }
                    else -> {}
                }
            }
        }
        val munching = play.events.any { it.kind == FxKind.HIT && play.now - it.at in 0..700 }
        val res = when {
            munching -> R.drawable.pup_treat
            play.pose == com.bigpapituna.sharpears.ui.playground.Painted.HEAD_TILT -> R.drawable.pup_head_tilt
            play.pose == com.bigpapituna.sharpears.ui.playground.Painted.SURPRISED -> R.drawable.pup_surprised
            play.pose == com.bigpapituna.sharpears.ui.playground.Painted.POINTING -> R.drawable.pup_pointing
            else -> R.drawable.pup_listening
        }
        Image(painterResource(res), contentDescription = null, modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 6.dp)
            .size(pupSize).graphicsLayer { transformOrigin = TransformOrigin(0.5f, 1f); scaleX = round * (if (munching && !still) 1.04f else 1f); scaleY = 1f + (round - 1f) * 0.4f })
    }
}

/** A bone treat at [c], [r] its badge's size, with [key]'s badge in the middle. */
private fun DrawScope.bone(c: Offset, r: Float, key: Int, play: ArcadePlay, measurer: androidx.compose.ui.text.TextMeasurer, alpha: Float = 1f) {
    if (r <= 1f) return
    val cream = Color(0xFFFFF4E0).copy(alpha = alpha)
    val edge = Color(0xFF8A5A3A).copy(alpha = 0.6f * alpha)
    for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) {
        drawCircle(edge, r * 0.52f, c + Offset(sx * r * 1.7f, sy * r * 0.42f))
        drawCircle(cream, r * 0.46f, c + Offset(sx * r * 1.7f, sy * r * 0.42f))
    }
    drawRoundRect(cream, Offset(c.x - r * 1.75f, c.y - r * 0.5f), Size(r * 3.5f, r), CornerRadius(r * 0.4f))
    keyBadge(key, c, r * 0.82f, play.reader, measurer, glow = key in play.lit, alpha = alpha)
}

// ---- Boop the moles ---------------------------------------------------------------------------------------------------

/**
 * Boop the moles: the field is a big keyboard under a lawn, a burrow at the top of every key. A mole peeks out of a key's
 * burrow and ducks back; played, it gets a boop (squashed, stars round its head) and pops down. Black keys' burrows sit
 * a little higher. The keys here are the ones to tap (there is no second keyboard below).
 */
@Composable
internal fun MoleField(play: ArcadePlay, modifier: Modifier, onKey: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val night = isSystemInDarkTheme()
    val still = reducedMotion()
    val (low, high) = remember(play.round) { keyRange(play.round.keys) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val geo = remember(area, low, high) { if (area.width == 0) null else PianoGeometry(low, high, area.width.toFloat(), area.height * 0.44f, area.height * 0.56f) }
    val classes = remember(play.round) { play.round.keys.map { it % 12 }.toSet() }
    Canvas(modifier.onSizeChanged { area = it }.semantics { contentDescription = "Moles peeking out of the keys: play the key a mole is on." }
        .pointerInput(geo) {
            val g = geo ?: return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                val key = g.keyAt(down.position.x, down.position.y.coerceAtLeast(g.top + 1f))
                if (key != null) { down.consume(); onKey(key) }
            }
        }) {
        val g = geo ?: return@Canvas
        val now = play.now
        if (night) sky(Color(0xFF243554), Color(0xFF3E5C4A)) else sky(Color(0xFFA8DCF7), Color(0xFFDFF3D9))
        drawCircle(if (night) Color(0xFFF4F0D8) else PupGold, size.minDimension * 0.06f, Offset(size.width * 0.86f, size.height * 0.09f))
        cloud(Offset(((now / 60f) % (size.width + 200f)) - 100f, size.height * 0.12f), size.width * 0.1f, Color.White.copy(alpha = if (night) 0.25f else 0.85f))
        grass(g.top - size.height * 0.3f, if (night) Color(0xFF3F6B45) else Color(0xFF9BD17C), if (night) Color(0xFF2A4A30) else Color(0xFF6DB35D))
        // A few flowers on the lawn.
        for (i in 0 until 7) {
            val fx = size.width * (0.07f + 0.14f * i); val fy = g.top - size.height * (0.2f + 0.04f * (i % 3))
            drawLine(Color(0xFF4E9A4A), Offset(fx, fy), Offset(fx, fy + 18f), 3f)
            drawCircle(keyColour(listOf(60, 64, 67, 69, 62, 71, 65)[i]), 8f, Offset(fx, fy))
        }
        val sounding = play.demoIndex?.let { play.demoKeys.getOrNull(it) }
        val level = play.round.level
        piano(g, fill = { m -> when {
            m == sounding || m in play.lit -> keyColour(m)
            level < 2 && m % 12 in classes && !blackKey(m) -> keyColour(m).copy(alpha = 0.35f)
            else -> null
        } })
        if (level < 2) for (m in g.whites) if (m % 12 in classes) {
            val rect = g.rect(m); val rr = minOf(g.ww * 0.28f, g.height * 0.12f)
            keyBadge(m, Offset(rect.center.x, rect.bottom - rr * 1.5f), rr, play.reader, measurer)
        }
        // Burrows along the keys' tops (black keys' a little higher), then the moles.
        fun hole(m: Int): Offset = g.rect(m).let { Offset(it.center.x, g.top - if (blackKey(m)) g.height * 0.17f else g.height * 0.04f) }
        val holeW = g.ww * 0.48f
        for (m in low..high) if (m in play.round.keys) {
            val h = hole(m)
            drawOval(Color(0xFF5A3A24), Offset(h.x - holeW, h.y - holeW * 0.28f), Size(holeW * 2, holeW * 0.56f))
            drawOval(Color(0xFF2E1D12), Offset(h.x - holeW * 0.8f, h.y - holeW * 0.2f), Size(holeW * 1.6f, holeW * 0.4f))
        }
        val maxRise = minOf(g.top * 0.62f, g.ww * 2f)
        fun mole(t: Target, k: Int, rise: Float, booped: Float?) {
            val h = hole(k)
            val w = holeW * 0.78f
            val top = h.y - maxRise * rise
            val squash = booped?.let { 1f - 0.35f * (1 - it) } ?: 1f
            val bodyH = (h.y - top) * squash
            if (bodyH <= 1f) return
            drawRoundRect(Color(0xFF7A5238), Offset(h.x - w, h.y - bodyH), Size(w * 2, bodyH + w * 0.2f), CornerRadius(w))
            drawOval(Color(0xFFE8C4A6), Offset(h.x - w * 0.55f, h.y - bodyH + w * 0.75f), Size(w * 1.1f, w * 0.9f))
            val eyeY = h.y - bodyH + w * 0.55f
            if (booped != null) {
                for (s in listOf(-1f, 1f)) drawLine(Color(0xFF2E1D12), Offset(h.x + s * w * 0.42f - w * 0.12f, eyeY - w * 0.1f), Offset(h.x + s * w * 0.42f + w * 0.12f, eyeY + w * 0.1f), w * 0.1f)
                if (!still) sparkles(Offset(h.x, h.y - bodyH - w * 0.2f), w, booped)
            } else for (s in listOf(-1f, 1f)) drawCircle(Color(0xFF2E1D12), w * 0.12f, Offset(h.x + s * w * 0.38f, eyeY))
            drawCircle(Color(0xFFE5484D), w * 0.16f, Offset(h.x, eyeY + w * 0.35f))
            // The key's badge on the mole's tummy: which key it is on, for a kid to match on the piano.
            keyBadge(k, Offset(h.x, h.y - bodyH * 0.35f), minOf(w * 0.5f, bodyH * 0.22f).coerceAtLeast(1f), play.reader, measurer, glow = k in play.lit)
            // The burrow's front lip over his tummy.
            drawOval(Color(0xFF5A3A24), Offset(h.x - holeW, h.y - holeW * 0.05f), Size(holeW * 2, holeW * 0.32f))
        }
        for (t in play.stream?.live.orEmpty()) {
            val p = play.along(t)
            val rise = when { p < 0.18f -> p / 0.18f; p > 0.85f -> (1f - p) / 0.15f; else -> 1f }
            for (k in t.keys) mole(t, k, rise, null)
        }
        for (e in play.events) {
            val t = e.target ?: continue
            if (e.kind == FxKind.HIT) play.age(e.at, 550)?.let { p -> for (k in t.keys) mole(t, k, 1f - p * p, p) }
        }
        ripples(g, play.fx.ripples, System.nanoTime(), still)
    }
}
