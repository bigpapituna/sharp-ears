package com.bigpapituna.sharpears.ui.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.arcade.ArcadeGame
import com.bigpapituna.sharpears.engine.arcade.ArcadeLines
import com.bigpapituna.sharpears.engine.arcade.ArcadeState
import com.bigpapituna.sharpears.engine.arcade.ArcadeUnlocks
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.play.ExitPill
import com.bigpapituna.sharpears.ui.playground.star
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The arcade's hub (the kid's Play sheet > Games): a big tile for every game, its picture, its name and its best stars
 * (three little stars, no numbers). Games open by practice, like the wardrobe ([ArcadeUnlocks]): the first three at once,
 * then one more every few sessions; a locked one is a wrapped present, the next one with a ring filling toward it. A tap
 * on a present has the pup say to keep playing. [newGame] glows the game that just opened.
 */
@Composable
fun ArcadeHub(state: ArcadeState, sparkles: Int, onGame: (ArcadeGame) -> Unit, onBack: () -> Unit, say: (String) -> Unit,
              modifier: Modifier = Modifier, newGame: ArcadeGame? = null) {
    val open = ArcadeUnlocks.open(state, sparkles)
    val toward = ArcadeUnlocks.towardNext(state, sparkles)
    val c = MaterialTheme.colorScheme
    LazyVerticalGrid(GridCells.Adaptive(150.dp), modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExitPill(onBack)
                    Spacer(Modifier.weight(1f))
                }
                Text("Games", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                PupSays(if (newGame != null) Pose.SURPRISED else Pose.DANCING, if (newGame != null) ArcadeLines.NEW_GAME else ArcadeLines.HUB, size = 72.dp,
                    onTap = { say(if (newGame != null) ArcadeLines.NEW_GAME else ArcadeLines.HUB) })
            }
        }
        items(ArcadeGame.entries.take(open), key = { it.id }) { g ->
            val best = state.of(g).best
            Column(Modifier.pressScale().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerHigh)
                .then(if (g == newGame) Modifier.border(3.dp, PupGold, RoundedCornerShape(24.dp)) else Modifier)
                .clickable(role = Role.Button, onClickLabel = "Play ${g.title}") { onGame(g) }
                .semantics(mergeDescendants = true) { contentDescription = "${g.title}, ${starWords(best)}" }
                .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(16.dp))) { tileArt(g) }
                Text(g.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, color = c.onSurface)
                Canvas(Modifier.size(width = 66.dp, height = 20.dp)) {
                    for (i in 0 until 3) {
                        val cc = Offset(size.height / 2 + i * size.width / 3f + 1f, size.height / 2)
                        if (i < best) drawPath(star(cc, size.height * 0.46f), PupGold)
                        else drawPath(star(cc, size.height * 0.46f), c.onSurfaceVariant.copy(alpha = 0.5f), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
        // 2.14 (Mila): the games not open yet are one wrapped present, not a wall of identical boxes a pre-reader taps for nothing.
        if (open < ArcadeGame.entries.size) item(key = "more-soon") {
            val next = ArcadeGame.entries[open]
            Column(Modifier.pressScale().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerHigh)
                .clickable(role = Role.Button, onClickLabel = ArcadeLines.MORE_SOON_LABEL) { say(ArcadeLines.LOCKED) }
                .semantics(mergeDescendants = true) { contentDescription = ArcadeLines.MORE_SOON_LABEL }
                .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(16.dp))) {
                    tileArt(next)
                    present(toward)
                }
                Text(ArcadeLines.MORE_SOON, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2,
                    color = c.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private fun starWords(n: Int) = when (n) { 0 -> "not played yet"; 3 -> "every star"; 2 -> "two stars"; else -> "one star" }

/** A wrapped present over a locked game's picture; [toward] fills a ring round it for the next one to open. */
private fun DrawScope.present(toward: Float?) {
    drawRect(Color(0xFF2A2236).copy(alpha = 0.55f))
    val s = size.minDimension * 0.5f
    val c = Offset(size.width / 2, size.height * 0.55f)
    drawRoundRect(Color(0xFFE5484D), Offset(c.x - s / 2, c.y - s * 0.35f), Size(s, s * 0.75f), CornerRadius(8f))
    drawRoundRect(Color(0xFFF26B70), Offset(c.x - s * 0.58f, c.y - s * 0.5f), Size(s * 1.16f, s * 0.22f), CornerRadius(6f))
    drawRect(PupGold, Offset(c.x - s * 0.08f, c.y - s * 0.5f), Size(s * 0.16f, s * 0.85f))
    for (sx in listOf(-1f, 1f)) drawOval(PupGold, Offset(c.x + sx * s * 0.2f - s * 0.18f, c.y - s * 0.72f), Size(s * 0.36f, s * 0.24f))
    if (toward != null) {
        val r = s * 0.9f
        drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, c - Offset(r, r * 1.05f), Size(r * 2, r * 2), style = Stroke(6f))
        drawArc(PupGold, -90f, 360f * toward.coerceIn(0.02f, 1f), false, c - Offset(r, r * 1.05f), Size(r * 2, r * 2), style = Stroke(6f, cap = StrokeCap.Round))
    }
}

/** Each game's tile picture. */
internal fun DrawScope.tileArt(g: ArcadeGame) {
    val w = size.width; val h = size.height
    when (g) {
        ArcadeGame.BALLOONS -> {
            sky(Color(0xFF7EC8F6), Color(0xFFDDF2FF))
            listOf(60, 64, 67).forEachIndexed { i, k -> balloonTile(Offset(w * (0.28f + 0.22f * i), h * (0.42f - 0.08f * (i % 2))), h * 0.17f, keyColour(k)) }
        }
        ArcadeGame.GARDEN -> {
            sky(Color(0xFF9ED8F7), Color(0xFFE7F6FF))
            grass(h * 0.72f)
            listOf(60, 64, 67).forEachIndexed { i, k ->
                val base = Offset(w * (0.25f + 0.25f * i), h * 0.8f); val top = base - Offset(0f, h * (0.38f + 0.06f * (i % 2)))
                drawLine(Color(0xFF4E9A4A), base, top, 5f)
                for (p in 0 until 5) { val a = 2 * PI * p / 5; drawCircle(keyColour(k), h * 0.06f, top + Offset((cos(a) * h * 0.08f).toFloat(), (sin(a) * h * 0.08f).toFloat())) }
                drawCircle(PupGold, h * 0.05f, top)
            }
        }
        ArcadeGame.FEED -> {
            sky(Color(0xFFFFE3B8), Color(0xFFFFF6E6))
            drawOval(Color(0xFFE5484D), Offset(w * 0.25f, h * 0.62f), Size(w * 0.5f, h * 0.22f))
            drawOval(Color(0xFFB8353A), Offset(w * 0.3f, h * 0.62f), Size(w * 0.4f, h * 0.12f))
            for ((i, k) in listOf(62, 65).withIndex()) {
                val c = Offset(w * (0.38f + 0.26f * i), h * (0.3f + 0.1f * i)); val r = h * 0.07f
                for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) drawCircle(Color(0xFFFFF4E0), r * 0.5f, c + Offset(sx * r * 1.7f, sy * r * 0.42f))
                drawRoundRect(Color(0xFFFFF4E0), Offset(c.x - r * 1.75f, c.y - r * 0.5f), Size(r * 3.5f, r), CornerRadius(r * 0.4f))
                drawCircle(keyColour(k), r * 0.6f, c)
            }
        }
        ArcadeGame.TOWER -> {
            sky(Color(0xFF6FC0F2), Color(0xFFD7F0FF))
            cloud(Offset(w * 0.25f, h * 0.22f), w * 0.18f)
            listOf(60, 62, 64, 65, 67).forEachIndexed { i, k ->
                val bw = w * 0.3f; val bh = h * 0.13f
                drawRoundRect(keyColour(k), Offset(w / 2 - bw / 2 + (i % 2) * 4f, h * 0.88f - (i + 1) * bh), Size(bw, bh - 2f), CornerRadius(6f))
            }
        }
        ArcadeGame.MOLES -> {
            sky(Color(0xFFA8DCF7), Color(0xFFDFF3D9)); grass(h * 0.55f)
            for (i in 0 until 7) drawRect(Color(0xFFFFFCF7), Offset(w * i / 7f + 1f, h * 0.7f), Size(w / 7f - 2f, h * 0.3f))
            val c = Offset(w * 0.5f, h * 0.62f)
            drawRoundRect(Color(0xFF7A5238), Offset(c.x - h * 0.1f, c.y - h * 0.25f), Size(h * 0.2f, h * 0.3f), CornerRadius(h * 0.1f))
            for (s in listOf(-1f, 1f)) drawCircle(Color(0xFF2E1D12), h * 0.018f, Offset(c.x + s * h * 0.045f, c.y - h * 0.17f))
            drawCircle(Color(0xFFE5484D), h * 0.025f, Offset(c.x, c.y - h * 0.12f))
            drawOval(Color(0xFF5A3A24), Offset(c.x - h * 0.15f, c.y - h * 0.02f), Size(h * 0.3f, h * 0.08f))
        }
        ArcadeGame.FROGS -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7FC9E6), Color(0xFF3F93BD))))
            for (i in 0 until 3) { val c = Offset(w * (0.22f + 0.28f * i), h * 0.62f); drawCircle(Color(0xFF5DB357), h * 0.13f, c); drawCircle(keyColour(listOf(60, 62, 64)[i]), h * 0.05f, c) }
            val f = Offset(w * 0.5f, h * 0.38f)
            drawOval(Color(0xFF6CC04A), Offset(f.x - h * 0.12f, f.y - h * 0.08f), Size(h * 0.24f, h * 0.17f))
            for (s in listOf(-1f, 1f)) { drawCircle(Color(0xFF6CC04A), h * 0.04f, f + Offset(s * h * 0.06f, -h * 0.08f)); drawCircle(Color.White, h * 0.025f, f + Offset(s * h * 0.06f, -h * 0.08f)) }
        }
        ArcadeGame.FIREWORKS -> {
            sky(Color(0xFF0C1230), Color(0xFF2A2350))
            listOf(60 to Offset(w * 0.3f, h * 0.35f), 67 to Offset(w * 0.68f, h * 0.3f), 64 to Offset(w * 0.5f, h * 0.55f)).forEach { (k, c) ->
                for (i in 0 until 12) { val a = 2 * PI * i / 12; drawLine(keyColour(k), c, c + Offset((cos(a) * h * 0.16f).toFloat(), (sin(a) * h * 0.16f).toFloat()), 3f, cap = StrokeCap.Round) }
            }
            drawRect(Color(0xFF151530), Offset(0f, h * 0.85f), Size(w, h * 0.15f))
        }
        ArcadeGame.PAINTER -> {
            sky(Color(0xFF3A2C24), Color(0xFF5A4334))
            drawRoundRect(Color(0xFFFBF5EA), Offset(w * 0.2f, h * 0.12f), Size(w * 0.6f, h * 0.55f), CornerRadius(6f))
            drawPath(star(Offset(w * 0.42f, h * 0.38f), h * 0.14f), PupGold)
            for ((i, k) in listOf(60, 62, 64, 65, 67, 69).withIndex()) drawCircle(keyColour(k), h * 0.035f, Offset(w * (0.3f + 0.08f * i), h * 0.82f))
            drawLine(Color(0xFF6B4A2E), Offset(w * 0.62f, h * 0.3f), Offset(w * 0.78f, h * 0.6f), 6f, cap = StrokeCap.Round)
        }
        ArcadeGame.BRIDGE -> {
            sky(Color(0xFF8ED0F5), Color(0xFFE2F4FF))
            drawRect(Color(0xFF2F7FAE), Offset(0f, h * 0.62f), Size(w, h * 0.38f))
            listOf(60, 62, 64, 65, 67, 69, 71).forEachIndexed { i, k ->
                val rr = w * 0.4f - i * w * 0.03f
                drawArc(keyColour(k), 180f, 180f, false, Offset(w / 2 - rr, h * 0.62f - rr * 0.7f), Size(rr * 2, rr * 1.4f), style = Stroke(w * 0.022f))
            }
            for (i in 0 until 6) drawRect(keyColour(listOf(48, 60, 50, 62, 52, 64)[i]), Offset(w * (0.12f + i * 0.127f), h * 0.6f), Size(w * 0.12f, h * 0.06f))
        }
    }
}

/** A plain balloon for a tile (no measurer: no letters). */
private fun DrawScope.balloonTile(c: Offset, r: Float, colour: Color) {
    drawLine(Color.White.copy(alpha = 0.7f), c + Offset(0f, r * 1.2f), c + Offset(0f, r * 2.6f), 2f)
    drawOval(colour, Offset(c.x - r, c.y - r * 1.2f), Size(r * 2f, r * 2.4f))
    drawOval(Color.White.copy(alpha = 0.45f), Offset(c.x - r * 0.62f, c.y - r * 0.86f), Size(r * 0.42f, r * 0.62f))
}

/** Three of the arcade's pictures side by side (balloons, the garden, the frogs): the Play sheet's Games tile. */
@Composable
fun GamesPicture(modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "Balloons, flowers and a frog" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (g in listOf(ArcadeGame.BALLOONS, ArcadeGame.GARDEN, ArcadeGame.FROGS))
            Canvas(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(16.dp))) { tileArt(g) }
    }
}
