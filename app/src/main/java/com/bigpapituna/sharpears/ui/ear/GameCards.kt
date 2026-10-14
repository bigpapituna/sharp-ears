package com.bigpapituna.sharpears.ui.ear

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.ear.EarKind
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.practice.PanelColor
import com.bigpapituna.sharpears.ui.practice.PianoGeometry
import com.bigpapituna.sharpears.ui.practice.PupLine
import com.bigpapituna.sharpears.ui.practice.SlidingSegments
import com.bigpapituna.sharpears.ui.reducedMotion

/*
 * The game hubs' parts, shared by Ear training and Chords > Play so both families look alike: a grid of game
 * cards (an illustration, the title, a star per level mastered, a best-score ring and a round Play button), the
 * sheet a card opens (the how-to in the pup's bubble, the level picker, Play), the ear games' small animated
 * pictures and a mini keyboard for the chord games.
 */

/** A best of this many out of ten fills a level's star (the workout's "mastered"). */
internal const val STAR_AT = 7

/** Two columns on a phone, three on a wide window. [item] gets its index and the modifier to put on its card. */
@Composable
internal fun CardGrid(count: Int, modifier: Modifier = Modifier, item: @Composable (Int, Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = if (maxWidth >= 600.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (0 until count).chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item(it, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One game on a hub: [art] on top, the title and the level it opens on, a star per level ([mastered]) and the
 * best of that level as a ring. Tapping the card opens its sheet ([onOpen]); the round button plays at once.
 */
@Composable
internal fun GameCard(
    title: String, level: Int, mastered: List<Boolean>, best: Int, onOpen: () -> Unit, onPlay: () -> Unit,
    modifier: Modifier = Modifier, artOnPanel: Boolean = false, art: @Composable BoxScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(modifier.pressScale().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)
        .clickable(role = Role.Button, onClickLabel = "How to play", onClick = onOpen).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp))
            .background(if (artOnPanel) PanelColor else c.surfaceContainerHighest).clearAndSetSemantics { }, contentAlignment = Alignment.Center, content = art)
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 22.sp), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Text("Level ${level + 1}", style = Numeric.merge(MaterialTheme.typography.labelMedium), color = c.onSurfaceVariant)
            }
            BestRing(best, Modifier.size(42.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Stars(mastered, Modifier.weight(1f))
            PlayDisc(onPlay, "Play $title, level ${level + 1}")
        }
    }
}

/** A star per level, filled in the accent when that level is mastered. */
@Composable
internal fun Stars(mastered: List<Boolean>, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outline
    Row(modifier.semantics { contentDescription = "${mastered.count { it }} of ${mastered.size} levels mastered" },
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        mastered.forEach { m ->
            Canvas(Modifier.size(size)) {
                val p = starPath(this.size.width)
                if (m) drawPath(p, on) else drawPath(p, off, style = Stroke(1.4.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

private fun starPath(w: Float) = Path().apply {
    val cx = w / 2; val cy = w * 0.53f; val ro = w * 0.48f; val ri = ro * 0.45f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) ro else ri
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = cx + r * kotlin.math.cos(a).toFloat(); val y = cy + r * kotlin.math.sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The best score out of ten as a ring with the number inside; "New" before the first game. */
@Composable
internal fun BestRing(best: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(modifier.semantics { contentDescription = if (best < 0) "Not played yet" else "Best $best of 10" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = 4.dp.toPx()
            val box = Size(size.width - s, size.height - s)
            drawArc(c.outlineVariant, 0f, 360f, false, Offset(s / 2, s / 2), box, style = Stroke(s))
            if (best > 0) drawArc(if (best >= STAR_AT) c.tertiary else c.primary, -90f, 360f * best / 10f, false, Offset(s / 2, s / 2), box,
                style = Stroke(s, cap = StrokeCap.Round))
        }
        if (best < 0) Text("New", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        else Text("$best", style = Numeric.merge(MaterialTheme.typography.titleSmall).copy(fontWeight = FontWeight.SemiBold), color = c.onSurface)
    }
}

/** The round Play button on a card. */
@Composable
internal fun PlayDisc(onClick: () -> Unit, description: String) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.pressScale(0.9f).size(44.dp).clip(CircleShape).background(c.primary)
        .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(24.dp))
    }
}

/**
 * The sheet a card opens: the picture, the how-to in the pup's bubble, the level picker with that level's best,
 * and one big Play button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameSheet(
    title: String, howTo: (Int) -> String, levels: List<String>, bests: List<Int>, start: Int,
    onPlay: (Int) -> Unit, onDismiss: () -> Unit, artOnPanel: Boolean = false, art: @Composable BoxScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var level by remember { mutableIntStateOf(start) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (artOnPanel) PanelColor else c.surfaceContainerHighest).clearAndSetSemantics { },
                    contentAlignment = Alignment.Center, content = art)
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                PupLine(Pose.LEARNING, howTo(level))
                SlidingSegments(levels.indices.map { "Level ${it + 1}" }, level, { level = it }, Modifier.fillMaxWidth(), height = 40.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BestRing(bests[level], Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(levels[level], style = MaterialTheme.typography.titleMedium)
                        Text(when {
                            bests[level] < 0 -> "Not played yet"
                            bests[level] >= STAR_AT -> "Best ${bests[level]} of 10, mastered"
                            else -> "Best ${bests[level]} of 10. $STAR_AT earns the star."
                        }, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                }
                ChunkyButton(onClick = { onPlay(level) }, modifier = Modifier.fillMaxWidth()) { Text("Play level ${level + 1}") }
            }
        }
    }
}

// The pictures ----------------------------------------------------------------------------------------------

/** A 0..1 loop for a card's picture, [periodMs] long; a still frame at [rest] with animations off. */
@Composable
internal fun rememberLoop(periodMs: Int, rest: Float = 0.6f): State<Float> {
    if (reducedMotion()) return remember { mutableFloatStateOf(rest) }
    return rememberInfiniteTransition(label = "card art").animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "loop")
}

/** Each ear game's picture: rhythm blocks under a playhead, a melody line, the pup in his headphones, a ladder of degrees, numerals. */
@Composable
internal fun EarArt(kind: EarKind, modifier: Modifier = Modifier, big: Boolean = false) {
    when (kind) {
        EarKind.RHYTHM -> RhythmArt(modifier)
        EarKind.MELODY -> MelodyArt(modifier)
        EarKind.PITCH -> PitchArt(modifier, big)
        EarKind.DEGREE -> DegreeArt(modifier)
        EarKind.PROGRESSION -> NumeralArt(modifier)
    }
}

@Composable
private fun RhythmArt(modifier: Modifier) {
    val t by rememberLoop(2400)
    val c = MaterialTheme.colorScheme
    val accent = c.primary; val dim = c.outline
    Canvas(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        // Quarter, two eighths, quarter, rest, quarter: blocks on a line, lit as the playhead passes.
        val pattern = listOf(0f to 2f, 2f to 1f, 3f to 1f, 4f to 2f, 8f to 2f)
        val unit = size.width / 10f
        val y = size.height * 0.62f
        val h = size.height * 0.22f
        val head = t * 10f
        pattern.forEach { (at, len) ->
            val on = head >= at && head < at + len
            val passed = head >= at + len
            drawRoundRect(if (on) accent else if (passed) accent.copy(alpha = 0.35f) else dim.copy(alpha = 0.6f),
                Offset(at * unit + 2.dp.toPx(), y - h / 2), Size(len * unit - 4.dp.toPx(), h), CornerRadius(h / 2))
        }
        drawLine(accent.copy(alpha = 0.8f), Offset(head * unit, y - h * 1.6f), Offset(head * unit, y + h * 1.2f), 2.dp.toPx(), StrokeCap.Round)
        // The tap: a dot that bounces on each onset.
        val onset = pattern.filter { head >= it.first }.maxOfOrNull { it.first } ?: 0f
        val since = (head - onset).coerceIn(0f, 1f)
        val r = size.height * (0.1f + 0.06f * (1 - since))
        drawCircle(accent.copy(alpha = 0.25f * (1 - since)), r * 2f, Offset(onset * unit + unit / 2, size.height * 0.16f))
        drawCircle(accent, r * 0.7f, Offset(onset * unit + unit / 2, size.height * 0.16f))
    }
}

@Composable
private fun MelodyArt(modifier: Modifier) {
    val t by rememberLoop(2600)
    val c = MaterialTheme.colorScheme
    val accent = c.primary; val dim = c.outline
    Canvas(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        val heights = listOf(0.7f, 0.45f, 0.55f, 0.2f, 0.4f)
        val pts = heights.mapIndexed { i, h -> Offset(size.width * (i + 0.5f) / heights.size, size.height * h) }
        val lit = (t * (heights.size + 2)).toInt()
        for (i in 0 until pts.lastIndex) drawLine(if (i < lit - 1) accent.copy(alpha = 0.6f) else dim.copy(alpha = 0.5f), pts[i], pts[i + 1], 2.dp.toPx(), StrokeCap.Round)
        pts.forEachIndexed { i, p ->
            val r = size.height * 0.11f
            if (i < lit) { drawCircle(accent.copy(alpha = 0.22f), r * 1.7f, p); drawCircle(accent, r, p) }
            else drawCircle(dim, r, p, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun PitchArt(modifier: Modifier, big: Boolean) {
    val t by rememberLoop(1800)
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height * 0.55f)
            for (k in 0 until 3) {
                val p = (t + k / 3f) % 1f
                val r = size.height * (0.38f + 0.5f * p)
                for (side in listOf(-1f, 1f)) {
                    drawArc(accent.copy(alpha = 0.6f * (1 - p)), if (side < 0) 150f else -30f, 60f, false,
                        Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        Pup(Pose.LISTENING, size = if (big) 104.dp else 80.dp)
    }
}

@Composable
private fun DegreeArt(modifier: Modifier) {
    val t by rememberLoop(3500)
    val c = MaterialTheme.colorScheme
    val accent = c.primary; val dim = c.outline; val ink = c.onPrimary
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val names = listOf("do", "re", "mi", "fa", "sol", "la", "ti")
    Canvas(modifier.fillMaxSize().padding(vertical = 10.dp)) {
        // A ladder: seven rungs, do at the bottom; the lit rung climbs, then jumps about.
        val order = listOf(0, 1, 2, 3, 4, 2, 6, 5)
        val step = order[(t * order.size).toInt().coerceAtMost(order.lastIndex)]
        val w = (size.width * 0.3f).coerceAtMost(size.height * 0.9f)
        val x0 = size.width * 0.42f - w / 2
        val gap = size.height / 7f
        drawLine(dim, Offset(x0, 0f), Offset(x0, size.height), 2.dp.toPx())
        drawLine(dim, Offset(x0 + w, 0f), Offset(x0 + w, size.height), 2.dp.toPx())
        for (i in 0 until 7) {
            val y = size.height - gap * (i + 0.5f)
            if (i == step) {
                val h = (gap * 0.8f).coerceAtLeast(4.dp.toPx())
                drawRoundRect(accent, Offset(x0 - 4.dp.toPx(), y - h / 2), Size(w + 8.dp.toPx(), h), CornerRadius(h / 2))
                // The syllable in a pill beside the ladder, readable at any card size.
                val l = measurer.measure(names[i], TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp))
                val px = 7.dp.toPx(); val py = 2.dp.toPx()
                val bx = x0 + w + 12.dp.toPx()
                val by = (y - l.size.height / 2 - py).coerceIn(0f, size.height - l.size.height - py * 2)
                drawRoundRect(accent, Offset(bx, by), Size(l.size.width + px * 2, l.size.height + py * 2), CornerRadius(l.size.height.toFloat()))
                drawText(l, ink, Offset(bx + px, by + py))
            } else drawLine(dim, Offset(x0, y), Offset(x0 + w, y), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun NumeralArt(modifier: Modifier) {
    val t by rememberLoop(2800)
    val c = MaterialTheme.colorScheme
    val numerals = listOf("I", "V", "vi", "IV")
    val on = (t * 5).toInt()                               // four chords, then a beat of rest
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val tile = minOf(maxHeight * 0.56f, (maxWidth - 40.dp) / 4)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            numerals.forEachIndexed { i, n ->
                val lit = i == on
                Box(Modifier.size(tile).graphicsLayer { val s = if (lit) 1.08f else 1f; scaleX = s; scaleY = s }
                    .clip(RoundedCornerShape(10.dp)).background(if (lit) c.primary else if (i < on) c.primary.copy(alpha = 0.18f) else c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center) {
                    Text(n, style = (if (tile < 36.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge).copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
                        color = if (lit) c.onPrimary else c.onSurface, maxLines = 1)
                }
            }
        }
    }
}

/**
 * A small keyboard (C4 to E5) on the dark panel with [lit] keys in the accent: the chord games' pictures. [lit]
 * gets the loop's time so a picture can move (a chord building up, a third flipping between major and minor).
 */
@Composable
internal fun MiniKeys(modifier: Modifier = Modifier, periodMs: Int = 2400, arc: Pair<Int, Int>? = null, lit: (Float) -> Map<Int, Float>) {
    val t by rememberLoop(periodMs)
    Canvas(modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        val top = if (arc != null) size.height * 0.28f else 0f
        val g = PianoGeometry(60, 76, size.width, size.height - top, top)
        drawMiniKeys(g, lit(t))
        if (arc != null) {
            val a = g.rect(arc.first).center.x; val b = g.rect(arc.second).center.x
            val p = Path().apply { moveTo(a, top - 2.dp.toPx()); quadraticTo((a + b) / 2, -top * 0.4f, b, top - 2.dp.toPx()) }
            drawPath(p, RightHandColor.copy(alpha = 0.85f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

private val KeyWhite = Color(0xFFFFFCF7)
private val KeyWhiteLow = Color(0xFFE6DED3)
private val KeyBlack = Color(0xFF2A2420)

/** Flat little keys; a lit key is washed in the accent by its strength (0..1). */
internal fun DrawScope.drawMiniKeys(g: PianoGeometry, lit: Map<Int, Float>) {
    val r = CornerRadius(g.ww * 0.14f)
    for (m in g.whites) {
        val k = g.rect(m)
        val box = Rect(k.left + 1.dp.toPx(), k.top, k.right - 1.dp.toPx(), k.bottom)
        drawRoundRect(Brush.verticalGradient(listOf(KeyWhite, KeyWhiteLow), box.top, box.bottom), box.topLeft, box.size, r)
        lit[m]?.let { a -> if (a > 0f) drawRoundRect(RightHandColor.copy(alpha = 0.95f * a), box.topLeft, box.size, r) }
    }
    for (m in (g.low..g.high).filter(::isBlack)) {
        val k = g.rect(m)
        val a = lit[m] ?: 0f
        drawRoundRect(if (a > 0f) lerp(KeyBlack, RightHandColor, a) else KeyBlack, k.topLeft, k.size, CornerRadius(g.bw * 0.2f))
    }
}
