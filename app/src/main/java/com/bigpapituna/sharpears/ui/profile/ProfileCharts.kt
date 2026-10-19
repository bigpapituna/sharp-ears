package com.bigpapituna.sharpears.ui.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.progress.Heat
import com.bigpapituna.sharpears.engine.progress.WeeklyGoal
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.reducedMotion
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.Text
import kotlin.math.roundToInt

/** Numbers in Geist Mono: [Numeric] wins over the type scale's own family. */
fun TextStyle.mono(): TextStyle = merge(Numeric)

private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d")
internal fun shortDate(d: LocalDate): String = MONTH_DAY.format(d)

/** A number that counts up to [target] on first showing (and from the old value when it changes). Straight to it with animations off. */
@Composable
fun rememberCountUp(target: Int, durationMs: Int = 900, from: Int = 0): Int {
    val still = reducedMotion()
    val a = remember { Animatable(if (still) target.toFloat() else from.toFloat()) }
    LaunchedEffect(target, still) {
        if (still) a.snapTo(target.toFloat()) else a.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing))
    }
    return a.value.roundToInt()
}

/** 0..[fraction] filling in on first showing. */
@Composable
fun rememberFill(fraction: Float, durationMs: Int = 1000): Float {
    val still = reducedMotion()
    val a = remember { Animatable(if (still) fraction else 0f) }
    LaunchedEffect(fraction, still) {
        if (still) a.snapTo(fraction) else a.animateTo(fraction, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return a.value
}

/** A ring that fills clockwise from the top to [fraction]; [content] sits in the middle. */
@Composable
fun ProgressRing(
    fraction: Float, modifier: Modifier = Modifier, stroke: Dp = 10.dp,
    track: Color = MaterialTheme.colorScheme.outlineVariant, color: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val filled = rememberFill(fraction)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val topLeft = Offset(w / 2, w / 2)
            val arc = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
            if (filled > 0.002f) drawArc(color, -90f, 360f * filled, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** The streak's flame, filled in [color] with a lighter heart. */
@Composable
fun Flame(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, size: Dp = 32.dp) {
    val heart = MaterialTheme.colorScheme.background.copy(alpha = 0.35f)
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        fun path() = Path().apply {
            moveTo(0.5f * w, 0f)
            cubicTo(0.55f * w, 0.25f * h, 0.85f * w, 0.40f * h, 0.85f * w, 0.68f * h)
            cubicTo(0.85f * w, 0.88f * h, 0.70f * w, h, 0.5f * w, h)
            cubicTo(0.30f * w, h, 0.15f * w, 0.88f * h, 0.15f * w, 0.68f * h)
            cubicTo(0.15f * w, 0.52f * h, 0.25f * w, 0.42f * h, 0.35f * w, 0.34f * h)
            cubicTo(0.36f * w, 0.48f * h, 0.42f * w, 0.52f * h, 0.46f * w, 0.52f * h)
            cubicTo(0.40f * w, 0.30f * h, 0.45f * w, 0.12f * h, 0.5f * w, 0f)
            close()
        }
        drawPath(path(), color)
        scale(0.45f, 0.45f, pivot = Offset(0.5f * w, h * 0.98f)) { drawPath(path(), heart) }
    }
}

/** A six-armed snowflake, for streak freezes. */
@Composable
fun Snowflake(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.width / 2 - 1.dp.toPx()
        val sw = (this.size.width / 10f).coerceAtLeast(1.5f)
        for (i in 0 until 3) rotate(60f * i, c) {
            drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), sw, StrokeCap.Round)
            // little barbs at the tips
            for (s in listOf(-1f, 1f)) {
                drawLine(color, Offset(c.x, c.y + s * r), Offset(c.x - r * 0.28f, c.y + s * r * 0.72f), sw * 0.8f, StrokeCap.Round)
                drawLine(color, Offset(c.x, c.y + s * r), Offset(c.x + r * 0.28f, c.y + s * r * 0.72f), sw * 0.8f, StrokeCap.Round)
            }
        }
    }
}

/**
 * Minutes per day for the last 30 days as accent bars, oldest left, today right and brightest. A dashed line marks
 * the daily goal. [days] is 30 entries (day, seconds) oldest first.
 */
@Composable
fun PracticeBars(days: List<Pair<LocalDate, Int>>, goalMinutes: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val grow = rememberFill(1f, 900)
    val maxSec = maxOf(days.maxOfOrNull { it.second } ?: 0, goalMinutes * 60, 60)
    val totalMin = days.sumOf { it.second } / 60
    val best = (days.maxOfOrNull { it.second } ?: 0) / 60
    val summary = "Practice in the last 30 days: ${com.bigpapituna.sharpears.engine.plural(totalMin, "minute")} in total, best day ${com.bigpapituna.sharpears.engine.plural(best, "minute")}."
    Column(modifier.semantics { contentDescription = summary }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = days.size.coerceAtLeast(1)
            val gap = 3.dp.toPx()
            val barW = (size.width - gap * (n - 1)) / n
            val usable = size.height - 4.dp.toPx()
            val stub = 3.dp.toPx()
            days.forEachIndexed { i, (_, sec) ->
                val x = i * (barW + gap)
                val isToday = i == days.lastIndex
                if (sec <= 0) {
                    drawRoundRect(c.outlineVariant, Offset(x, size.height - stub), Size(barW, stub), CornerRadius(stub / 2))
                } else {
                    val bh = (sec.toFloat() / maxSec * usable * grow).coerceAtLeast(stub)
                    drawRoundRect(c.primary.copy(alpha = if (isToday) 1f else 0.62f), Offset(x, size.height - bh), Size(barW, bh), CornerRadius(barW / 2.4f))
                }
            }
            // The goal line.
            val gy = size.height - goalMinutes * 60f / maxSec * usable
            drawLine(c.onSurfaceVariant.copy(alpha = 0.55f), Offset(0f, gy), Offset(size.width, gy), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val dim = c.onSurfaceVariant
            Text(days.firstOrNull()?.first?.let(::shortDate).orEmpty(), style = MaterialTheme.typography.labelSmall.mono(), color = dim, modifier = Modifier.weight(1f))
            Text("Today", style = MaterialTheme.typography.labelSmall.mono(), color = dim)
        }
    }
}

/**
 * The last 12 weeks, one square a day (columns are weeks, Monday at the top). Tint follows minutes against the daily
 * goal; days a streak freeze covered are outlined; days to come are left blank. [week] 0 is the oldest column.
 */
@Composable
fun Heatmap(log: Map<LocalDate, Int>, today: LocalDate, goalMinutes: Int, frozen: Set<LocalDate>, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val grow = rememberFill(1f, 800)
    val weeks = 12
    val start = WeeklyGoal.weekStart(today).minusWeeks((weeks - 1).toLong())
    val active = log.count { (d, s) -> s > 0 && !d.isBefore(start) && !d.isAfter(today) }
    val summary = "Practice calendar for the last 12 weeks: ${com.bigpapituna.sharpears.engine.plural(active, "day")} with practice."
    // Squares with gaps a fifth of a square: width = 12.0 + 11 * 0.2 squares, height = 7 + 6 * 0.2.
    Canvas(modifier.fillMaxWidth().aspectRatio(14.2f / 8.2f).semantics { contentDescription = summary }) {
        val cell = size.width / 14.2f
        val gap = cell * 0.2f
        val corner = CornerRadius(cell * 0.28f)
        for (w in 0 until weeks) for (r in 0 until 7) {
            val day = start.plusDays((w * 7 + r).toLong())
            val x = w * (cell + gap); val y = r * (cell + gap)
            if (day.isAfter(today)) {
                drawRoundRect(c.outlineVariant.copy(alpha = 0.35f), Offset(x, y), Size(cell, cell), corner, style = Stroke(1.dp.toPx()))
                continue
            }
            val sec = log[day] ?: 0
            drawRoundRect(heatColor(sec, goalMinutes, c.surfaceContainerHighest, c.primary, grow), Offset(x, y), Size(cell, cell), corner)
            if (day in frozen && sec <= 0) drawRoundRect(c.primary.copy(alpha = 0.7f), Offset(x, y), Size(cell, cell), corner, style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** The tint of one day: none, then four steps up to twice the goal. */
internal fun heatColor(seconds: Int, goalMinutes: Int, empty: Color, accent: Color, grow: Float = 1f): Color {
    val level = Heat.level(seconds, goalMinutes)
    if (level == 0) return empty
    val alpha = listOf(0f, 0.30f, 0.55f, 0.82f, 1f)[level]
    return lerp(empty, accent, alpha * grow)
}

/** The five swatches under the heatmap: less to more. */
@Composable
fun HeatLegend(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        for (l in 0..4) Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            .background(heatColor(listOf(0, 60, 400, 600, 1200)[l], 10, c.surfaceContainerHighest, c.primary)))
        Text("More", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    }
}
