package com.bigpapituna.sharpears.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.BarTiming
import com.bigpapituna.sharpears.engine.BarVerdict
import com.bigpapituna.sharpears.engine.PerformTiming
import com.bigpapituna.sharpears.engine.Recap
import com.bigpapituna.sharpears.engine.SongStars
import com.bigpapituna.sharpears.engine.TimingReport
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.ui.Good
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.Stage
import com.bigpapituna.sharpears.ui.StageRaised
import com.bigpapituna.sharpears.ui.WrongGlow

/** A verdict's colour on the strip (a stage panel: the feedback colours live on the stage). */
internal fun verdictColour(v: BarVerdict?): Color = when (v) {
    BarVerdict.CLEAN -> Good
    BarVerdict.RUSHED -> PupGold
    BarVerdict.DRAGGED -> LeftHandColor
    BarVerdict.MESSY -> WrongGlow
    null -> StageRaised
}

/**
 * The timing panel of a Perform recap (2.14, ui/RunRecap.kt places it; the recap's layout is branch polish's). For a grown-up:
 * the song's star rule against this run ("3 stars: no misses, 9 of 10 notes in time."), a strip of the run's bars coloured by
 * verdict (clean, rushed, dragged, messy) with a key, the one plain line ("You rush the first beat of bars 3 and 7.") and,
 * once a bar is tapped, its notes early or late in milliseconds. A kid ([kid]) sees only the strip, a face in each bar and
 * never a sad one, no numbers and no words. Nothing at all without a Perform [recap].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimingPanel(timing: TimingReport?, recap: Recap?, kid: Boolean, modifier: Modifier = Modifier, flats: Boolean = false,
                /** 2.15: false while a tip says the same thing ("Tap a bar to see which notes were early or late."). */ hint: Boolean = true) {
    if (recap == null) return
    val bars = timing?.bars.orEmpty()
    var open by remember(timing) { mutableStateOf<Int?>(null) }
    val c = MaterialTheme.colorScheme
    Column(modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!kid) {
            Text("Timing", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, modifier = Modifier.semantics { heading() })
            SongStars.ruleLines(recap).forEachIndexed { i, line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, color = if (i == 0) c.onSurface else c.onSurfaceVariant)
            }
        }
        if (bars.any { it.verdict != null }) {
            // The strip on its own small stage: one cell a bar, in rows.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Stage).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    bars.forEach { b -> BarCell(b, kid, selected = open == b.bar) { if (!kid && b.verdict != null) open = if (open == b.bar) null else b.bar } }
                }
                if (!kid) Legend()
            }
            if (!kid) {
                Text(timing!!.line, style = MaterialTheme.typography.bodyLarge, color = c.primary)
                val shown = bars.firstOrNull { it.bar == open }
                if (shown != null) BarDetail(shown, flats) else if (hint) Text("Tap a bar to see its notes.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
    }
}

/** One bar: its verdict's colour (and its number), or a face for a kid. */
@Composable
private fun BarCell(b: BarTiming, kid: Boolean, selected: Boolean, onTap: () -> Unit) {
    val colour = verdictColour(b.verdict)
    val words = if (b.verdict == null) "Bar ${b.bar}, nothing to play" else "Bar ${b.bar}, ${b.verdict.word.lowercase()}"
    val size = if (kid) 34.dp else 28.dp
    Box(Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(if (b.verdict == null) StageRaised else colour.copy(alpha = if (kid) 0.9f else 0.85f))
        .then(if (selected) Modifier.border(2.dp, Ink, RoundedCornerShape(6.dp)) else Modifier)
        .then(if (!kid && b.verdict != null) Modifier.clickable(role = Role.Button, onClickLabel = "Show bar ${b.bar}", onClick = onTap) else Modifier)
        .semantics(mergeDescendants = true) { contentDescription = words; this.selected = selected },
        contentAlignment = Alignment.Center) {
        if (kid) { if (b.verdict != null) Canvas(Modifier.size(size * 0.8f)) { face(b.verdict) } }
        else Text("${b.bar}", style = Numeric.merge(MaterialTheme.typography.labelSmall).copy(fontSize = 10.sp),
            color = if (b.verdict == null) InkDim else Stage, modifier = Modifier.clearAndSetSemantics { })
    }
}

/**
 * A kid's face for a bar, never sad (as the recap's pup): clean grins, rushed and dragged an "oh" of surprise (eyes looking
 * ahead or back), messy a silly wavy grin with its ends up.
 */
private fun DrawScope.face(v: BarVerdict) {
    val w = size.width; val ink = Stage
    val eyeY = w * 0.38f; val r = w * 0.07f
    val look = when (v) { BarVerdict.RUSHED -> w * 0.05f; BarVerdict.DRAGGED -> -w * 0.05f; else -> 0f }
    drawCircle(ink, r, Offset(w * 0.33f + look, eyeY)); drawCircle(ink, r, Offset(w * 0.67f + look, eyeY))
    val stroke = Stroke(w * 0.08f, cap = StrokeCap.Round)
    when (v) {
        BarVerdict.CLEAN -> drawArc(ink, 20f, 140f, false, Offset(w * 0.25f, w * 0.35f), Size(w * 0.5f, w * 0.4f), style = stroke)
        BarVerdict.RUSHED, BarVerdict.DRAGGED -> drawCircle(ink, w * 0.1f, Offset(w * 0.5f, w * 0.68f), style = Stroke(w * 0.07f))
        BarVerdict.MESSY -> drawPath(Path().apply {
            moveTo(w * 0.26f, w * 0.6f); quadraticTo(w * 0.38f, w * 0.78f, w * 0.5f, w * 0.64f); quadraticTo(w * 0.62f, w * 0.78f, w * 0.74f, w * 0.6f)
        }, ink, style = stroke)
    }
}

/** What the colours mean, under the strip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() = FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    BarVerdict.entries.forEach { v ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(9.dp).background(verdictColour(v), CircleShape))
            Text(v.word, style = MaterialTheme.typography.labelSmall, color = InkDim)
        }
    }
}

/** A tapped bar's notes: where each fell in the bar, the keys, and how early or late. */
@Composable
private fun BarDetail(b: BarTiming, flats: Boolean) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Bar ${b.bar}, ${b.verdict?.word?.lowercase() ?: "nothing to play"}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        b.notes.forEach { n ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(PerformTiming.beatLabel(n.beat), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 76.dp))
                Text(n.keys.joinToString(" ") { noteName(it, flats) }, style = Numeric.merge(MaterialTheme.typography.bodyMedium),
                    color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(PerformTiming.offsetWords(n.offsetMs), style = Numeric.merge(MaterialTheme.typography.bodyMedium),
                    color = when { n.missed -> WrongGlow; n.early || n.late -> c.onSurface; else -> c.onSurfaceVariant })
            }
        }
    }
}
