package com.bigpapituna.sharpears.ui.brief

import com.bigpapituna.sharpears.ui.drawRestPaw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.brief.BarRow
import com.bigpapituna.sharpears.engine.brief.BriefFormat
import com.bigpapituna.sharpears.engine.brief.DayStat
import com.bigpapituna.sharpears.engine.brief.LessonStat
import com.bigpapituna.sharpears.engine.brief.Spark
import com.bigpapituna.sharpears.engine.brief.TrendRow
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.family.drawPaw
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The full report's pieces (docs/PARENT-BRIEF-PLAN.md: the seven-day strip, lessons with stars and tries, skills over four
 * weeks as small trend lines, the toy mix, time of day). Charts stay to what the plan names: a strip, small lines and
 * plain bars. No pie charts, no red: a line that moves the wrong way is the app's warm accent, not an alarm.
 */

/** A small heading inside the full report. */
@Composable
fun ReportTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 22.dp, bottom = 8.dp).semantics { heading() })

/** One of the seven days: a paw (filled when there was practice), a gold ring on days the goal was reached, minutes, and the day. */
@Composable
fun WeekStrip(days: List<DayStat>, goalMinutes: Int?, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val fractions = Spark.dayFractions(days, goalMinutes)
    val description = days.joinToString(". ") { d ->
        BriefFormat.dayShort(d.date) + (if (d.practiceMinutes > 0) ", ${BriefFormat.minutes(d.practiceMinutes)}" else if (d.rest) ", a rest day" else ", no practice") + if (d.goal) ", the goal was reached" else ""
    }
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (d.goal) Canvas(Modifier.size(40.dp)) {
                            drawCircle(PupGold.copy(alpha = 0.28f))
                            drawCircle(PupGold, style = Stroke(1.5.dp.toPx()))
                        }
                        val practised = d.practiceMinutes > 0
                        Canvas(Modifier.size(26.dp)) { if (d.rest && !practised) drawRestPaw(c.primary) else drawPaw(if (practised) c.primary else c.outline, filled = practised) }
                    }
                    Text(if (d.practiceMinutes > 0) "${d.practiceMinutes}" else "0", style = Numeric.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                        color = if (d.practiceMinutes > 0) c.onSurface else c.onSurfaceVariant)
                    Box(Modifier.width(24.dp).height(4.dp).clip(CircleShape).background(c.outlineVariant)) {
                        Box(Modifier.fillMaxWidth(fractions[i]).height(4.dp).clip(CircleShape).background(c.primary))
                    }
                    Text(BriefFormat.dayShort(d.date), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Text(buildString {
            append("Minutes practised each day.")
            if (days.any { it.goal }) append(" A gold ring means the daily goal was reached.")
            if (days.any { it.rest }) append(" A paw with a moon is a rest day: one missed day the streak kept.")
        }, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Up to three small stars in a row: [filled] gold, the rest an outline. */
@Composable
fun MiniStars(filled: Int, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val c = MaterialTheme.colorScheme
    Canvas(modifier.size(width = size * 3 + 6.dp, height = size)) {
        val s = size.toPx()
        for (i in 0 until 3) {
            val cx = s / 2 + i * (s + 3.dp.toPx())
            val path = starPath(cx, s / 2, s / 2, s * 0.22f)
            if (i < filled) drawPath(path, PupGold, style = Fill)
            else drawPath(path, c.outline, style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path = Path().apply {
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) outer else inner
        val a = -PI / 2 + k * PI / 5
        val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A lesson played in the range: its title, its best stars, how many tries. */
@Composable
fun LessonRow(s: LessonStat, title: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {
        contentDescription = "$title, ${BriefFormat.stars(s.stars)}, ${BriefFormat.tries(s.tries)}"
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (s.letGo > 0) Text("The pup let ${s.letGo} step${if (s.letGo == 1) "" else "s"} go, on purpose, so it keeps moving.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        if (s.stars != null && s.stars > 0) MiniStars(s.stars)
        else Text("not yet", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        Text(BriefFormat.tries(s.tries), style = Numeric.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize), color = c.onSurfaceVariant,
            modifier = Modifier.width(64.dp), maxLines = 1)
    }
}

/** A song played in the range. */
@Composable
fun PlainRow(left: String, right: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(left, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(right, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
    }
}

/** One skill over four weeks: its name, a small line, and one plain sentence. */
@Composable
fun TrendLine(row: TrendRow, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val measured = row.points.filterNotNull()
    val rising = measured.size >= 2 && measured.last() > measured.first()
    val falling = measured.size >= 2 && measured.last() < measured.first()
    val moved = measured.size >= 2 && kotlin.math.abs(measured.last() - measured.first()) > 0.0
    val better = (rising && row.upIsGood) || (falling && !row.upIsGood)
    val colour = when { !moved -> c.onSurfaceVariant; better -> c.tertiary; else -> c.primary }
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = row.label + ". " + row.summary },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.bodyLarge)
            Text(row.summary, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        Canvas(Modifier.width(104.dp).height(36.dp)) {
            val pts = Spark.points(row.points, size.width, size.height, pad = 6.dp.toPx())
            drawLine(c.outlineVariant, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
            for (run in Spark.runs(pts)) {
                if (run.size >= 2) {
                    val path = Path().apply { run.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    drawPath(path, colour, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            val last = pts.lastOrNull { it != null }
            pts.forEach { p -> if (p != null) drawCircle(colour, if (p === last) 4.5.dp.toPx() else 2.5.dp.toPx(), Offset(p.x, p.y)) }
        }
    }
}

/** A list of plain horizontal bars: the toy mix, the time of day. The longest fills the track. */
@Composable
fun BarList(rows: List<BarRow>, subtitles: List<String?> = emptyList(), modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEachIndexed { i, r ->
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${r.label}, ${r.value}" }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        Text(r.label, style = MaterialTheme.typography.bodyLarge)
                        subtitles.getOrNull(i)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
                    }
                    Text(r.value, style = Numeric.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize), color = c.onSurfaceVariant)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(r.share.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.primary.copy(alpha = if (r.share > 0f) 1f else 0f)))
                }
            }
        }
    }
}

/** A small round dot for a list line (the design rules keep symbol glyphs out of copy). */
@Composable
fun Dot(modifier: Modifier = Modifier, colour: Color = MaterialTheme.colorScheme.primary) =
    Box(modifier.size(7.dp).clip(CircleShape).background(colour))
