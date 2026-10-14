package com.bigpapituna.sharpears.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.RunWords
import com.bigpapituna.sharpears.engine.SongArt
import com.bigpapituna.sharpears.engine.TroubleSpot
import com.bigpapituna.sharpears.engine.TroubleSpots
import com.bigpapituna.sharpears.ui.motion.countUp
import com.bigpapituna.sharpears.ui.motion.easeOutCubic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** The anim values of a recap, all 0..1: the pup rising, the headline, the rest sliding in, numbers counting, the XP bar. */
private class RecapMotion(private val still: Boolean) {
    private fun a() = Animatable(if (still) 1f else 0f)
    val rise = a(); val pop = a(); val rest = a(); val count = a(); val xp = a()
}

/**
 * The end of a song run, built like a lesson's Celebration: the pup by result (a victory jump on a new best with
 * a slow sunburst and confetti, a thinking pose after a hard run), a headline and one coaching line in his voice,
 * the accuracy filling a ring as it counts up, stat chips (good, ok, missed, longest streak, time), +XP with the
 * level bar, then the hardest bars to loop and "Hear my run" as quieter actions under Play again. Wide windows
 * (the player is held sideways) put the pup on the left and the numbers on the right. Still with animations off.
 */
@Composable
fun RunRecap(e: RunEnd, speed: Double, sampler: Sampler?, onAgain: () -> Unit, onLoop: (TroubleSpot) -> Unit, onClose: () -> Unit, onReplay: () -> Unit) {
    val still = reducedMotion()
    val haptic = LocalHapticFeedback.current
    val m = remember(e) { RecapMotion(still) }
    LaunchedEffect(e) {
        if (e.newBest && sampler != null) launch { flourish(sampler) }
        if (still) return@LaunchedEffect
        launch { m.rise.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 200f)) }
        delay(260)
        if (e.newBest || (e.recap?.accuracy ?: 0) >= 85) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { m.pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
        delay(160)
        launch { m.rest.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        delay(160)
        m.count.animateTo(1f, tween(1000, easing = LinearEasing))
        if (e.xpGained != null) { m.xp.animateTo(1f, tween(1100, easing = LinearEasing)); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    val spin = if (e.newBest && !still) rememberInfiniteTransition(label = "spin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(45_000, easing = LinearEasing), RepeatMode.Restart), label = "spin") else null
    val r = e.recap
    val hardest = e.spots.firstOrNull()?.bar
    val pose = when {
        e.newBest -> Pose.LEVEL_UP
        r != null -> when { r.accuracy >= 85 -> Pose.GREAT_JOB; r.accuracy >= 50 -> Pose.PRACTICING; else -> Pose.MISTAKE }
        else -> when { e.wrongKeys == 0 -> Pose.GREAT_JOB; e.wrongKeys <= 5 -> Pose.PRACTICING; else -> Pose.MISTAKE }
    }
    val title = if (r != null) RunWords.title(r.accuracy, e.newBest) else RunWords.waitTitle(e.wrongKeys)
    val line = if (r != null) RunWords.line(r, hardest, speed) else RunWords.waitLine(e.wrongKeys, hardest)
    val stats = buildList {
        if (r != null) { add(Stat("${r.good}", "Good")); add(Stat("${r.ok}", "OK")); add(Stat("${r.miss}", "Missed")); add(Stat("${r.longestStreak}", "Streak")) }
        else add(Stat("${e.wrongKeys}", if (e.wrongKeys == 1) "Wrong note" else "Wrong notes"))
        if (e.timeMs > 0) add(Stat(SongArt.length(e.timeMs), "Time"))
        if (speed < 1.0) add(Stat("${(speed * 100).toInt()}%", "Speed"))
    }

    Box(Modifier.fillMaxSize().paper()) {
        if (e.newBest) Confetti(e, Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth > maxHeight * 1.2f
            val pupSize = if (wide) min(maxHeight * 0.46f, 190.dp) else min(maxWidth * 0.5f, 200.dp)
            val head: @Composable ColumnScope.() -> Unit = {
                Box(Modifier.size(pupSize * 1.25f).sunburst(e.newBest, spin, m.rise), contentAlignment = Alignment.Center) {
                    Pup(pose, size = pupSize, onTap = {}, modifier = Modifier.graphicsLayer {
                        translationY = (1 - m.rise.value) * size.height * 0.8f
                        val s = 0.5f + 0.5f * m.rise.value; scaleX = s; scaleY = s; alpha = m.rise.value.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    })
                }
                Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }.graphicsLayer {
                        val s = 0.6f + 0.4f * m.pop.value; scaleX = s; scaleY = s; alpha = m.pop.value.coerceIn(0f, 1f)
                    })
                Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp).graphicsLayer { alpha = m.pop.value.coerceIn(0f, 1f) })
            }
            val numbers: @Composable ColumnScope.() -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.slideIn(m.rest, 0)) {
                    if (r != null) AccuracyRing(r.accuracy, m.count, if (wide) 104.dp else 116.dp)
                    else Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
                    }
                    FlowRow(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.forEachIndexed { i, st -> StatChip(st, m.count, delay = i * 0.1f, Modifier.slideIn(m.rest, i + 1)) }
                    }
                }
                if (e.xpGained != null) XpLine(e.xpGained, e.xpBar, m.xp, Modifier.slideIn(m.rest, stats.size + 1))
                // The quieter way on: a hard bar on a loop.
                TroubleSpots.line(e.spots)?.let { loops ->
                    Column(Modifier.slideIn(m.rest, stats.size + 2), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(loops, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            e.spots.forEach { s -> SmallPill("Loop bar ${s.bar}", R.drawable.ic_repeat) { onLoop(s) } }
                        }
                    }
                }
            }
            // Play again, always in view; the run played back and Close beside it.
            val actions: @Composable () -> Unit = {
                FlowRow(Modifier.slideIn(m.rest, stats.size + 3), verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    ChunkyButton(onClick = onAgain, modifier = Modifier.widthIn(min = 180.dp)) {
                        Icon(painterResource(R.drawable.ic_repeat), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Play again")
                    }
                    if (e.canReplay) SmallPill("Hear my run", R.drawable.ic_play_arrow, onReplay)
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
            if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(0.42f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), content = head)
                Column(Modifier.weight(0.58f).fillMaxHeight().padding(vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp), content = numbers)
                    Spacer(Modifier.height(16.dp))
                    actions()
                }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                head()
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) { numbers(); actions() }
            }
        }
    }
}

/** Slides in as the [i]-th part of the rest (0..1 of [rest], each part a little after the one before). */
private fun Modifier.slideIn(rest: Animatable<Float, *>, i: Int) = graphicsLayer {
    val k = (rest.value * 1.6f - i * 0.12f).coerceIn(0f, 1f)
    alpha = k; translationY = (1 - k) * 36f
}

/** The accuracy filling a ring as its number counts up. */
@Composable
private fun AccuracyRing(accuracy: Int, count: Animatable<Float, *>, size: Dp) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(size).semantics(mergeDescendants = true) { contentDescription = "Accuracy $accuracy percent" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 9.dp.toPx()
            val s = Size(this.size.width - w, this.size.height - w)
            drawArc(c.surfaceContainerHighest, 0f, 360f, false, Offset(w / 2, w / 2), s, style = Stroke(w))
            val k = easeOutCubic(count.value)
            if (accuracy > 0) drawArc(c.primary, -90f, 360f * accuracy / 100f * k, false, Offset(w / 2, w / 2), s, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
            Text(countUp("$accuracy%", easeOutCubic(count.value)), style = Numeric.merge(MaterialTheme.typography.headlineMedium), color = c.onSurface)
            Text("accuracy", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
    }
}

/** One number in a soft chip, counting up as it lands. */
@Composable
private fun StatChip(st: Stat, count: Animatable<Float, *>, delay: Float, modifier: Modifier) {
    val k = ((count.value - delay) / (1f - delay)).coerceIn(0f, 1f)
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
        .semantics(mergeDescendants = true) { contentDescription = "${st.label} ${st.value}" },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(countUp(st.value, easeOutCubic(k)), style = Numeric.merge(MaterialTheme.typography.titleMedium), color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { })
        Text(st.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** "+15 XP" rolling up beside the level bar filling (and wrapping on a level up), as on a lesson's celebration. */
@Composable
private fun XpLine(gained: Int, bar: XpBar?, progress: Animatable<Float, *>, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.widthIn(max = 460.dp).fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Plus $gained XP" + (bar?.caption?.let { ", $it" } ?: "") },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(countUp("+$gained XP", easeOutCubic(progress.value)), style = Numeric.merge(MaterialTheme.typography.titleMedium), color = c.primary,
            modifier = Modifier.clearAndSetSemantics { })
        val track = c.surfaceContainerHighest
        Canvas(Modifier.weight(1f).height(12.dp)) {
            val ev = easeOutCubic(progress.value)
            val v = if (bar == null) ev else bar.from + (bar.to - bar.from) * ev
            val fill = if (v > 1f) v - floor(v) else v
            val rad = CornerRadius(size.height / 2f)
            drawRoundRect(track, cornerRadius = rad)
            if (fill > 0f) drawRoundRect(PupGold, size = Size(size.width * fill.coerceIn(0f, 1f), size.height), cornerRadius = rad)
            val sinceWrap = if (bar != null && v > 1f) (v - 1f) / maxOf(0.01f, bar.to - 1f) else 1f
            if (sinceWrap < 0.35f) drawRoundRect(PupGold, cornerRadius = rad, alpha = 0.6f * (1f - sinceWrap / 0.35f))
        }
        bar?.caption?.let { Text(it, style = Numeric.merge(MaterialTheme.typography.labelMedium), color = c.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { }) }
    }
}

/** A secondary action: a small outlined pill with an icon. */
@Composable
private fun SmallPill(label: String, icon: Int, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }

/** Behind the pup on a new best: a slow gold sunburst (still with animations off). Built once per size. */
private fun Modifier.sunburst(on: Boolean, spin: State<Float>?, rise: Animatable<Float, *>) = if (!on) this else drawWithCache {
    val c = Offset(size.width / 2f, size.height * 0.55f)
    val reach = size.width * 0.9f
    val rays = Path().apply {
        for (i in 0 until 12) {
            val a0 = (i * 2 * PI / 12).toFloat(); val a1 = a0 + 0.13f
            moveTo(c.x, c.y)
            lineTo(c.x + cos(a0) * reach, c.y + sin(a0) * reach)
            lineTo(c.x + cos(a1) * reach, c.y + sin(a1) * reach)
            close()
        }
    }
    val brush = Brush.radialGradient(listOf(PupGold.copy(alpha = 0.40f), PupGold.copy(alpha = 0.10f), Color.Transparent), c, reach)
    onDrawBehind { rotate(spin?.value ?: 0f, c) { drawPath(rays, brush, alpha = rise.value.coerceIn(0f, 1f)) } }
}

/** C, E, G and the C above, rolled: the same little "ta-da" a celebration plays, for a new best. */
private suspend fun flourish(sampler: Sampler) {
    listOf(72, 76, 79).forEach { sampler.noteOn(it, 70); delay(90) }
    sampler.noteOn(84, 88)
    delay(1400)
    listOf(72, 76, 79, 84).forEach(sampler::noteOff)
}

