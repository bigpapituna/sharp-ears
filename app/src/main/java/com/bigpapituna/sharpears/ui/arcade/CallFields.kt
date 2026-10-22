package com.bigpapituna.sharpears.ui.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.arcade.ArcadeStars
import com.bigpapituna.sharpears.engine.arcade.CallRound
import com.bigpapituna.sharpears.engine.arcade.Line
import com.bigpapituna.sharpears.engine.arcade.Side
import com.bigpapituna.sharpears.engine.arcade.Target
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.RightHandColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/*
 * The call games' playfields: the garden's flowers, the tower, the frogs' pond and the rainbow bridge. Each shows the
 * line the pup called as a row of badges (or on its pads and planks), the note being waited for bouncing, and what the
 * kid has made so far. Read inside the Canvas: the round's line and note, what each target came to ([ArcadePlay.played]),
 * and the recent effects.
 */

/** The line shown now: the one being played, or after it ends the one just done. */
private val CallRound.shown: Line? get() = currentLine

/** How many of the shown line's notes are done (played or let go). */
private fun CallRound.doneInLine(): Int = when (phase) {
    CallRound.Phase.LISTEN -> note
    CallRound.Phase.GAP, CallRound.Phase.DONE -> currentLine?.targets?.size ?: 0
    else -> 0
}

/** The index in the demo's keys sounding now, when the demo is the shown line's. */
private fun ArcadePlay.soundingIndex(): Int? = demoIndex?.takeIf { call?.phase == CallRound.Phase.DEMO }

// ---- Grow the garden --------------------------------------------------------------------------------------------------

/**
 * Grow the garden: a flower bed with a flower for every walk. The walk's flower grows a step for each key played (a leaf
 * in that key's colour), and opens when the walk ends, its petals the walk's colours. A slip lands a ladybird on it, and
 * it flies off again. The walk is the row of badges under the bed.
 */
@Composable
internal fun GardenField(play: ArcadePlay, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val night = isSystemInDarkTheme()
    Canvas(modifier.semantics { contentDescription = "A flower bed: play the pup's walk and each flower grows." }) {
        val r = play.call ?: return@Canvas
        val now = play.now
        if (night) sky(Color(0xFF233457), Color(0xFF4F6A8A)) else sky(Color(0xFF9ED8F7), Color(0xFFE7F6FF))
        cloud(Offset(size.width * 0.2f, size.height * 0.12f), size.width * 0.12f, Color.White.copy(alpha = if (night) 0.2f else 0.8f))
        // The fence behind the bed.
        val fenceY = size.height * 0.36f
        for (i in 0..12) drawRoundRect(Color(0xFFF2E6D2).copy(alpha = if (night) 0.5f else 0.9f), Offset(size.width * i / 12f - 8f, fenceY), Size(16f, size.height * 0.2f), CornerRadius(6f))
        drawRect(Color(0xFFF2E6D2).copy(alpha = if (night) 0.5f else 0.9f), Offset(0f, fenceY + size.height * 0.06f), Size(size.width, 10f))
        val bedTop = size.height * 0.62f
        grass(bedTop - size.height * 0.08f, if (night) Color(0xFF3F6B45) else Color(0xFF8CCB6E), if (night) Color(0xFF2A4A30) else Color(0xFF5DA153))
        drawRoundRect(Color(0xFF7A5238), Offset(size.width * 0.03f, bedTop), Size(size.width * 0.94f, size.height * 0.14f), CornerRadius(18f))
        val slots = 6
        val doneLines = r.linesDone
        val current = if (r.phase == CallRound.Phase.GAP || r.phase == CallRound.Phase.DONE) -1 else r.line
        fun slot(k: Int): Pair<Offset, Float> {
            val row = (k / slots) % 2
            val i = k % slots
            val scale = if (row == 0) 1f else 0.72f
            val x = size.width * (0.1f + 0.8f * (i + 0.5f) / slots) + if (row == 1) size.width * 0.06f else 0f
            val y = if (row == 0) bedTop + size.height * 0.08f else bedTop - size.height * 0.02f
            return Offset(x, y) to scale
        }
        val stemMax = size.height * 0.42f
        // Back row first, then the front.
        val count = maxOf(doneLines, current + 1)
        val order = (0 until count).sortedBy { -((it / slots) % 2) }
        for (k in order) {
            val line = r.plan.lines.getOrNull(k) ?: continue
            val (base, scale) = slot(k)
            val done = k < doneLines || (k == r.line && (r.phase == CallRound.Phase.GAP || r.phase == CallRound.Phase.DONE))
            val grown = if (done) line.targets.size else if (k == r.line) r.doneInLine() else 0
            val bloomAt = play.events.lastOrNull { it.kind == FxKind.LINE && it.line == k }?.at
            val bloom = when { !done -> 0f; bloomAt == null -> 1f; else -> ((now - bloomAt) / 600f).coerceIn(0f, 1f) }
            flower(base, stemMax * scale, line, grown, bloom, play, now + k * 300L, scale)
        }
        // A ladybird on the walk's flower after a slip, flying off again.
        play.events.lastOrNull { it.kind == FxKind.SLIP }?.let { e ->
            play.age(e.at, 1_400)?.let { p ->
                val (base, scale) = slot(maxOf(r.line, 0))
                val sit = Offset(base.x + 14f * scale, base.y - stemMax * scale * 0.35f)
                val c = if (p < 0.7f) sit else sit + Offset((p - 0.7f) * size.width * 0.6f, -(p - 0.7f) * size.height * 0.8f)
                ladybird(c, size.minDimension * 0.035f, if (p < 0.7f) 0f else 1f)
            }
        }
        // The walk.
        r.shown?.let { badgeRow(it.keys, r.doneInLine(), size.height * 0.9f, size.minDimension * 0.07f, play.reader, measurer, now, play.soundingIndex(), play.lit) }
    }
}

/** A flower at [base]: its stem grown [grown] of the walk's notes up to [height], a leaf a note, a bud, then [bloom] 0..1 open. */
private fun DrawScope.flower(base: Offset, height: Float, line: Line, grown: Int, bloom: Float, play: ArcadePlay, now: Long, scale: Float) {
    val n = line.targets.size.coerceAtLeast(1)
    val h = height * (0.15f + 0.85f * grown / n)
    val sway = sin(now / 700.0).toFloat() * 4f * scale * (if (bloom > 0f) 1f else 0.4f)
    val top = Offset(base.x + sway, base.y - h)
    drawLine(Color(0xFF4E9A4A), base, top, 6f * scale, cap = StrokeCap.Round)
    for (i in 0 until grown) {
        val t = line.targets[i]
        val y = base.y - h * (i + 1) / (n + 1)
        val side = if (i % 2 == 0) -1f else 1f
        val c = if (play.played[t.id] == false) Color(0xFF9BC59A) else keyColour(t.key)
        val leaf = Path().apply {
            moveTo(base.x + sway * (base.y - y) / h, y)
            quadraticTo(base.x + side * 26f * scale, y - 18f * scale, base.x + side * 34f * scale, y - 4f * scale)
            quadraticTo(base.x + side * 18f * scale, y + 8f * scale, base.x + sway * (base.y - y) / h, y)
        }
        drawPath(leaf, c)
    }
    if (bloom <= 0f) {
        drawCircle(Color(0xFF6DB35D), 9f * scale, top)
        return
    }
    val petals = maxOf(5, n)
    val pr = 22f * scale * (0.3f + 0.7f * bloom)
    for (i in 0 until petals) {
        val a = 2 * PI * i / petals + now / 4000.0
        val k = line.targets[i % n].key
        val c = top + Offset((cos(a) * pr).toFloat(), (sin(a) * pr).toFloat())
        drawCircle(keyColour(k), pr * 0.62f, c)
    }
    drawCircle(PupGold, pr * 0.55f, top)
    if (bloom < 1f) sparkles(top, pr * 1.6f, bloom)
}

/** A ladybird at [c], wings out when [flying] is 1. */
private fun DrawScope.ladybird(c: Offset, r: Float, flying: Float) {
    if (flying > 0f) for (s in listOf(-1f, 1f)) drawOval(Color.White.copy(alpha = 0.6f), Offset(c.x + s * r * 0.4f - r * 0.6f, c.y - r * 1.4f), Size(r * 1.2f, r * 1.0f))
    drawCircle(Color(0xFFE5484D), r, c)
    drawLine(Color(0xFF2A1A12), c - Offset(0f, r), c + Offset(0f, r), r * 0.12f)
    drawCircle(Color(0xFF2A1A12), r * 0.45f, c - Offset(0f, r * 0.9f))
    for ((dx, dy) in listOf(-0.45f to -0.2f, 0.45f to -0.2f, -0.4f to 0.4f, 0.4f to 0.4f)) drawCircle(Color(0xFF2A1A12), r * 0.18f, c + Offset(dx * r, dy * r))
}

// ---- Build the tower --------------------------------------------------------------------------------------------------

/**
 * Build the tower: a block for every key played, stacked on the grass and climbing past the clouds (the sky turns to
 * night and stars above them). The next block hovers over the top with a ring that empties over the beat (quicker as the
 * tower grows); played, it drops on; a slip makes the tower wobble and settle; a block let go puffs away.
 */
@Composable
internal fun TowerField(play: ArcadePlay, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val night = isSystemInDarkTheme()
    Canvas(modifier.semantics { contentDescription = "A tower of blocks: play each block's key to build it up to the clouds." }) {
        val r = play.call ?: return@Canvas
        val now = play.now
        val blocks = r.plan.lines.take(r.linesDone).map { it.targets.first() }.filter { play.played[it.id] == true }
        val bw = minOf(size.width * 0.3f, size.height * 0.22f)
        val bh = bw * 0.42f
        val ground = size.height * 0.92f
        val clouds = ArcadeStars.clouds(r.level)
        // The camera keeps the tower's top a little under the middle.
        val topWorld = blocks.size * bh
        val lift = maxOf(0f, topWorld - size.height * 0.5f)
        val space = (lift / (clouds * bh)).coerceIn(0f, 1f)
        fun lerp(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f)
        val skyTop = if (night) lerp(Color(0xFF1C2A4D), Color(0xFF0B0F24), space) else lerp(Color(0xFF6FC0F2), Color(0xFF1B2450), space)
        val skyBottom = if (night) lerp(Color(0xFF3B4F7A), Color(0xFF1C2A4D), space) else lerp(Color(0xFFD7F0FF), Color(0xFF5F7CC0), space)
        sky(skyTop, skyBottom)
        if (space > 0.3f) for (i in 0 until 24) {
            val x = (i * 97 % 100) / 100f * size.width; val y = (i * 53 % 100) / 100f * size.height * 0.7f
            drawCircle(Color.White.copy(alpha = (space - 0.3f) * (0.5f + 0.5f * sin(now / 400.0 + i).toFloat())), 2.5f, Offset(x, y))
        }
        val y0 = ground + lift
        // The clouds' band at the goal's height.
        val cloudY = y0 - clouds * bh
        for (i in 0 until 5) cloud(Offset(size.width * (0.1f + 0.2f * i), cloudY + (i % 2) * bh * 0.4f), bw * 0.7f, Color.White.copy(alpha = 0.9f))
        if (y0 < size.height + 40) grass(y0, if (night) Color(0xFF3F6B45) else Color(0xFF8CCB6E), if (night) Color(0xFF2A4A30) else Color(0xFF5DA153))
        // The wobble after a slip: a little swing that rings out.
        val wobble = play.events.lastOrNull { it.kind == FxKind.SLIP }?.let { e -> play.age(e.at, 1_000)?.let { p -> (sin(p * 22.0) * exp(-4.0 * p) * 5.0).toFloat() } } ?: 0f
        val cx = size.width / 2
        val lastHit = play.events.lastOrNull { it.kind == FxKind.HIT }
        rotate(wobble, Offset(cx, y0)) {
            blocks.forEachIndexed { i, t ->
                var y = y0 - (i + 1) * bh
                // The newest block drops on.
                if (i == blocks.lastIndex && lastHit?.target?.id == t.id) play.age(lastHit.at, 220)?.let { p -> y -= bh * 1.6f * (1 - p) * (1 - p) }
                val off = ((t.id * 37) % 9 - 4) * bw * 0.02f
                block(Offset(cx + off, y), bw, bh, t.key, play, measurer)
            }
        }
        // The next block, hovering with its beat ring.
        val next = r.current
        if (next != null && r.phase == CallRound.Phase.LISTEN) {
            val c = Offset(cx, y0 - (blocks.size + 1) * bh - bh * 1.4f + sin(now / 260.0).toFloat() * bh * 0.08f)
            if (next.key in play.lit) drawCircle(Brush.radialGradient(listOf(keyColour(next.key).copy(alpha = 0.6f), Color.Transparent), c, bw), bw, c)
            block(c - Offset(0f, bh / 2), bw, bh, next.key, play, measurer)
            val left = 1f - ((now - r.beatFrom).toFloat() / r.beatMs).coerceIn(0f, 1f)
            drawArc(PupGold, -90f, 360f * left, false, c - Offset(bw * 0.7f, bw * 0.7f), Size(bw * 1.4f, bw * 1.4f), style = Stroke(5f, cap = StrokeCap.Round))
        }
        play.events.lastOrNull { it.kind == FxKind.LET_GO }?.let { e ->
            play.age(e.at, 600)?.let { p ->
                val c = Offset(cx, y0 - (blocks.size + 1) * bh - bh * 1.4f)
                for (k in 0 until 6) drawCircle(Color.White.copy(alpha = 0.7f * (1 - p)), bw * 0.12f * (1 + p), c + Offset(cos(k.toFloat()) * bw * 0.5f * p, sin(k.toFloat()) * bh * p))
            }
        }
    }
}

/** A toy block at [topCenter] (its top edge's middle), in [key]'s colour with its badge. */
private fun DrawScope.block(topCenter: Offset, w: Float, h: Float, key: Int, play: ArcadePlay, measurer: TextMeasurer) {
    val c = keyColour(key)
    drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(topCenter.x - w / 2 + 3f, topCenter.y + 4f), Size(w, h), CornerRadius(h * 0.2f))
    drawRoundRect(c, Offset(topCenter.x - w / 2, topCenter.y), Size(w, h), CornerRadius(h * 0.2f))
    drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(topCenter.x - w / 2 + 4f, topCenter.y + 4f), Size(w - 8f, h * 0.25f), CornerRadius(h * 0.12f))
    keyBadge(key, Offset(topCenter.x, topCenter.y + h / 2), h * 0.36f, play.reader, measurer)
}

// ---- Frog hop ---------------------------------------------------------------------------------------------------------

/**
 * Frog hop: lily pads across a pond, one for each key of the crossing, low to high from left to right, each with its
 * badge. The frog hops pad to pad as the keys come (up and across, then back); with two frogs the left hand's row of pads
 * is the lower one and its frog bluer. A slip splashes beside the frog; a key let go, he hops anyway.
 */
@Composable
internal fun FrogField(play: ArcadePlay, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val night = isSystemInDarkTheme()
    Canvas(modifier.semantics { contentDescription = "A pond with lily pads: play each pad's key and the frog hops on it." }) {
        val r = play.call ?: return@Canvas
        val now = play.now
        drawRect(Brush.verticalGradient(if (night) listOf(Color(0xFF1C3550), Color(0xFF0F2236)) else listOf(Color(0xFF7FC9E6), Color(0xFF3F93BD))))
        for (i in 0 until 7) {
            val y = size.height * (0.12f + 0.13f * i)
            val x = ((now / 30f + i * 140f) % (size.width + 100f)) - 50f
            drawLine(Color.White.copy(alpha = 0.25f), Offset(x, y), Offset(x + 40f, y), 3f, cap = StrokeCap.Round)
        }
        // The banks, left and right, with reeds.
        for (left in listOf(true, false)) {
            val x = if (left) 0f else size.width * 0.93f
            drawRect(if (night) Color(0xFF2A4A30) else Color(0xFF6DB35D), Offset(x, 0f), Size(size.width * 0.07f, size.height))
            for (k in 0 until 4) drawLine(Color(0xFF3E7A3A), Offset(x + size.width * 0.035f, size.height * (0.2f + 0.2f * k)), Offset(x + size.width * 0.02f, size.height * (0.12f + 0.2f * k)), 4f, cap = StrokeCap.Round)
        }
        val line = r.shown ?: return@Canvas
        val two = line.targets.any { it.side == Side.LOW } && line.targets.any { it.side == Side.HIGH }
        val rows = if (two) listOf(Side.HIGH, Side.LOW) else listOf(line.targets.first().side)
        val done = r.doneInLine()
        rows.forEachIndexed { ri, side ->
            val mine = line.targets.filter { it.side == side }
            val pads = mine.map { it.key }.distinct().sorted()
            val y = if (!two) size.height * 0.55f else if (ri == 0) size.height * 0.36f else size.height * 0.74f
            val padR = minOf(size.width * 0.86f / pads.size * 0.42f, size.height * (if (two) 0.13f else 0.18f))
            fun padX(k: Int) = size.width * 0.07f + size.width * 0.86f * (pads.indexOf(k) + 0.5f) / pads.size
            val next = line.targets.getOrNull(done)?.takeIf { it.side == side && r.phase == CallRound.Phase.LISTEN }
            for (k in pads) {
                val c = Offset(padX(k), y + sin(now / 900.0 + k).toFloat() * 3f)
                drawCircle(Color(0xFF3E8E3A), padR * 1.05f, c)
                drawCircle(Color(0xFF5DB357), padR, c)
                drawArc(Color(0xFF3E8E3A), 300f, 40f, true, c - Offset(padR, padR), Size(padR * 2, padR * 2))
                val sounding = play.soundingIndex()?.let { line.keys.getOrNull(it) } == k
                keyBadge(k, c, padR * 0.48f, play.reader, measurer, glow = sounding || (next?.key == k && k in play.lit), alpha = if (next?.key == k || sounding) 1f else 0.85f)
            }
            // Where this row's frog is: its last pad in the line, or the bank it starts from.
            val played = mine.filter { it.index < done }
            val ascending = mine.size < 2 || mine.first().key <= mine.last().key
            val bank = if (ascending) size.width * 0.035f else size.width * 0.965f
            fun at(t: Target?) = t?.let { Offset(padX(it.key), y) } ?: Offset(bank, y)
            var pos = at(played.lastOrNull())
            val hop = play.events.lastOrNull { (it.kind == FxKind.HIT || it.kind == FxKind.LET_GO) && it.target?.side == side && it.target.phrase == r.line }
            if (hop != null && played.isNotEmpty() && hop.target?.id == played.last().id) play.age(hop.at, 320)?.let { p ->
                val from = at(played.getOrNull(played.size - 2))
                pos = Offset(from.x + (pos.x - from.x) * p, from.y + (pos.y - from.y) * p - sin(p * PI).toFloat() * padR * 1.6f)
            }
            frog(pos - Offset(0f, padR * 0.35f), padR * 0.55f, if (side == Side.LOW) Color(0xFF4DB0A8) else Color(0xFF6CC04A), ascending xor (pos.x > size.width / 2 && played.size == mine.size))
            play.events.lastOrNull { it.kind == FxKind.SLIP }?.let { e ->
                play.age(e.at, 700)?.let { p -> for (k in 0 until 3) drawCircle(Color.White.copy(alpha = 0.7f * (1 - p)), padR * (0.3f + 0.6f * p + 0.25f * k), pos + Offset(padR * 0.9f, padR * 0.4f), style = Stroke(3f)) }
            }
        }
        if (two) {
            // Which hand: the hands' colours on the banks (blue left, orange right, as on the player).
            flag(Offset(size.width * 0.035f, size.height * 0.74f), LeftHandColor)
            flag(Offset(size.width * 0.965f, size.height * 0.36f), RightHandColor)
        }
    }
}

/** A frog at [c], [r] its body, [right] facing right. */
private fun DrawScope.frog(c: Offset, r: Float, colour: Color, right: Boolean) {
    val f = if (right) 1f else -1f
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(c.x - r * 1.1f, c.y + r * 0.6f), Size(r * 2.2f, r * 0.6f))
    for (s in listOf(-1f, 1f)) drawOval(colour, Offset(c.x + s * r * 0.75f - r * 0.4f, c.y + r * 0.2f), Size(r * 0.8f, r * 0.55f))
    drawOval(colour, Offset(c.x - r, c.y - r * 0.6f), Size(r * 2f, r * 1.4f))
    drawOval(Color.White.copy(alpha = 0.35f), Offset(c.x - r * 0.5f, c.y - r * 0.1f), Size(r, r * 0.6f))
    for (s in listOf(-0.45f, 0.45f)) {
        val e = Offset(c.x + s * r + f * r * 0.15f, c.y - r * 0.65f)
        drawCircle(colour, r * 0.32f, e); drawCircle(Color.White, r * 0.22f, e); drawCircle(Color(0xFF1A1A1A), r * 0.11f, e + Offset(f * r * 0.05f, 0f))
    }
    drawArc(Color(0xFF2A4A1A), 20f, 140f, false, Offset(c.x - r * 0.45f + f * r * 0.1f, c.y - r * 0.35f), Size(r * 0.9f, r * 0.45f), style = Stroke(r * 0.08f, cap = StrokeCap.Round))
}

/** A little pennant at [c] in [colour] (a hand's side). */
private fun DrawScope.flag(c: Offset, colour: Color) {
    drawLine(Color(0xFF6B4A2E), c, c - Offset(0f, 60f), 4f)
    drawPath(Path().apply { moveTo(c.x, c.y - 60f); lineTo(c.x + 34f, c.y - 50f); lineTo(c.x, c.y - 40f); close() }, colour)
}

// ---- Rainbow bridge ---------------------------------------------------------------------------------------------------

/**
 * Rainbow bridge: a river between two banks; the left hand lays planks out from the left bank (blue flag), the right hand
 * from the right (orange flag), taking turns, each plank its key's colour with its badge. The next plank hovers over its
 * spot. When the two halves meet a rainbow arcs over and the pup trots across; then a new river.
 */
@Composable
internal fun BridgeField(play: ArcadePlay, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val night = isSystemInDarkTheme()
    val pup = ImageBitmap.imageResource(R.drawable.pup_dancing)
    Canvas(modifier.semantics { contentDescription = "A river: play the planks' keys, left hand and right hand in turn, to build a bridge." }) {
        val r = play.call ?: return@Canvas
        val now = play.now
        if (night) sky(Color(0xFF1F2E52), Color(0xFF45608A)) else sky(Color(0xFF8ED0F5), Color(0xFFE2F4FF))
        val deckY = size.height * 0.5f
        val bankW = size.width * 0.16f
        drawRect(Brush.verticalGradient(if (night) listOf(Color(0xFF1E4A6A), Color(0xFF12304A)) else listOf(Color(0xFF5FB3DA), Color(0xFF2F7FAE)), deckY, size.height),
            Offset(0f, deckY + 10f), Size(size.width, size.height - deckY))
        for (i in 0 until 6) {
            val x = ((now / 25f + i * 170f) % (size.width + 100f)) - 50f
            drawLine(Color.White.copy(alpha = 0.3f), Offset(x, deckY + size.height * (0.12f + 0.06f * i)), Offset(x + 50f, deckY + size.height * (0.12f + 0.06f * i)), 3f, cap = StrokeCap.Round)
        }
        val bankColour = if (night) Color(0xFF2E5A36) else Color(0xFF6DB35D)
        drawRect(bankColour, Offset(0f, deckY - 6f), Size(bankW, size.height - deckY + 6f))
        drawRect(bankColour, Offset(size.width - bankW, deckY - 6f), Size(bankW, size.height - deckY + 6f))
        flag(Offset(bankW * 0.5f, deckY - 8f), LeftHandColor)
        flag(Offset(size.width - bankW * 0.5f, deckY - 8f), RightHandColor)
        val line = r.shown ?: return@Canvas
        val n = line.targets.size
        val half = (n + 1) / 2
        val gapW = size.width - 2 * bankW
        val pw = gapW / n
        val ph = minOf(pw * 0.55f, size.height * 0.09f)
        fun spot(t: Target): Offset {
            val i = t.index / 2
            val x = if (t.side == Side.LOW) bankW + pw * (i + 0.5f) else size.width - bankW - pw * (i + 0.5f)
            return Offset(x, deckY)
        }
        val done = r.doneInLine()
        for (t in line.targets.take(done)) {
            val c = spot(t)
            val c2 = if (play.played[t.id] == false) keyColour(t.key).copy(alpha = 0.55f) else keyColour(t.key)
            drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(c.x - pw / 2 + 2f, c.y + 4f), Size(pw - 2f, ph), CornerRadius(6f))
            drawRoundRect(c2, Offset(c.x - pw / 2 + 1f, c.y), Size(pw - 2f, ph), CornerRadius(6f))
            keyBadge(t.key, Offset(c.x, c.y + ph / 2), minOf(ph * 0.38f, pw * 0.3f), play.reader, measurer)
        }
        val next = line.targets.getOrNull(done)
        if (next != null && r.phase != CallRound.Phase.GAP) {
            val c = spot(next) - Offset(0f, ph * 2.2f + sin(now / 250.0).toFloat() * 4f)
            val sounding = play.soundingIndex()?.let { line.keys.getOrNull(it) } == next.key
            drawRoundRect(keyColour(next.key).copy(alpha = 0.35f), Offset(c.x - pw / 2 + 1f, c.y), Size(pw - 2f, ph), CornerRadius(6f), style = Stroke(3f))
            keyBadge(next.key, Offset(c.x, c.y + ph / 2), minOf(ph * 0.55f, pw * 0.36f), play.reader, measurer, glow = sounding || next.key in play.lit)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x, c.y + ph + 4f), Offset(c.x, deckY - 4f), 2f)
        }
        // A bridge done: the rainbow and the pup crossing.
        val doneAt = play.events.lastOrNull { it.kind == FxKind.LINE && it.line == r.line }?.at
        if (doneAt != null && (r.phase == CallRound.Phase.GAP || r.phase == CallRound.Phase.DONE)) {
            val p = ((now - doneAt) / 1_400f).coerceIn(0f, 1f)
            val colours = listOf(60, 62, 64, 65, 67, 69, 71).map(::keyColour)
            colours.forEachIndexed { i, c ->
                val rr = gapW * 0.5f - i * 9f
                drawArc(c.copy(alpha = 0.85f), 180f, 180f * p, false, Offset(size.width / 2 - rr, deckY - rr * 0.8f), Size(rr * 2, rr * 1.6f), style = Stroke(9f))
            }
            val s = (ph * 3f).toInt().coerceAtLeast(8)
            val x = bankW * 0.5f + (size.width - bankW) * p - s / 2f
            drawImage(pup, dstOffset = IntOffset(x.toInt(), (deckY - s).toInt()), dstSize = IntSize(s, s))
        }
        if (r.phase == CallRound.Phase.LISTEN && next != null) {
            // The hand to use now, as a glow round its bank's flag.
            val c = if (next.side == Side.LOW) Offset(bankW * 0.5f + 17f, deckY - 58f) else Offset(size.width - bankW * 0.5f + 17f, deckY - 58f)
            drawCircle(Brush.radialGradient(listOf((if (next.side == Side.LOW) LeftHandColor else RightHandColor).copy(alpha = 0.6f), Color.Transparent), c, 40f), 40f, c)
        }
    }
}
