package com.bigpapituna.sharpears.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel

/** Lessons in course order, for "next lesson" and numbering. */
internal val ALL = COURSE.flatMap { lv -> lv.lessons.map { lv to it } }

/** One row of the path: a unit banner, a lesson stop, the card under a tapped stop, or a unit's trophy. */
private sealed interface PathRow {
    data class Banner(val level: CourseLevel) : PathRow
    data class Stop(val level: CourseLevel, val lesson: CourseLesson, val i: Int) : PathRow
    data class Card(val lesson: CourseLesson) : PathRow
    data class Trophy(val level: CourseLevel) : PathRow
}

/** Sideways place of the i-th stop in a unit, as a share of the swing: the trail winds left and right. */
private val WIND = listOf(0f, 0.55f, 0.9f, 0.55f, 0f, -0.55f, -0.9f, -0.55f)
private val SWING = 72.dp

/**
 * The Learn tab, Duolingo style: each level a unit with a banner, its lessons as round stops on a winding trail,
 * a trophy at the end. Done stops are ticked, the next one pulses under a bobbing "Start" with the pup pointing at
 * it, the rest wait in grey (still open to tap). Tapping a stop opens its card underneath. Opens scrolled to the
 * next lesson.
 */
@Composable
fun LearnScreen(done: (String) -> Boolean, onOpen: (CourseLesson) -> Unit, onSightRead: () -> Unit = {}, sightBest: String? = null) {
    val next = ALL.firstOrNull { !done(it.second.id) }?.second
    var open by remember { mutableStateOf<String?>(null) }
    val rows = remember(open, next) {
        COURSE.flatMap { level ->
            listOf(PathRow.Banner(level)) +
                level.lessons.flatMapIndexed { i, l -> listOfNotNull(PathRow.Stop(level, l, i), PathRow.Card(l).takeIf { open == l.id }) } +
                PathRow.Trophy(level)
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) {
        val at = rows.indexOfFirst { it is PathRow.Stop && it.lesson == next }
        if (at > 0) list.scrollToItem(at - 1)      // two rows above the next stop (the header is item 0), so it sits in view
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item(key = "head") {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(top = 20.dp, bottom = 12.dp)) {
                        Text("Learn", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Text("${ALL.count { done(it.second.id) }} of ${ALL.size} lessons", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Pup(Pose.LEARNING, size = 64.dp)
                }
                // Sight-reading sits beside the course: new music each time, so it is never "done".
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onSightRead).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Sight-read", style = MaterialTheme.typography.titleMedium)
                        Text(sightBest?.let { "Four new bars each time. Best $it." } ?: "Four new bars each time, names hidden.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }
        items(rows, key = { r -> when (r) { is PathRow.Banner -> "b-${r.level.id}"; is PathRow.Stop -> r.lesson.id; is PathRow.Card -> "c-${r.lesson.id}"; is PathRow.Trophy -> "t-${r.level.id}" } }) { r ->
            when (r) {
                is PathRow.Banner -> UnitBanner(r.level, r.level.lessons.count { done(it.id) }, current = r.level.lessons.any { it == next })
                is PathRow.Stop -> {
                    val state = when { done(r.lesson.id) -> StopState.DONE; r.lesson == next -> StopState.NEXT; else -> StopState.LATER }
                    StopRow(r.lesson, ALL.indexOfFirst { it.second == r.lesson } + 1, WIND[r.i % WIND.size], state) {
                        open = if (open == r.lesson.id) null else r.lesson.id
                    }
                }
                is PathRow.Card -> LessonCard(r.lesson, done(r.lesson.id), r.lesson == next) { open = null; onOpen(r.lesson) }
                is PathRow.Trophy -> UnitTrophy(r.level, r.level.lessons.all { done(it.id) })
            }
        }
        if (next == null) item(key = "end") {
            PupSays(Pose.LEVEL_UP, "Every lesson done. Keep going with songs and daily practice.", Modifier.widthIn(max = 560.dp).padding(20.dp), size = 96.dp)
        }
    }
}

private enum class StopState { DONE, NEXT, LATER }

/** A unit's banner: its name and what it covers, with a progress bar. The unit in progress is filled with the accent. */
@Composable
private fun UnitBanner(level: CourseLevel, done: Int, current: Boolean) {
    val c = MaterialTheme.colorScheme
    val bg = if (current) c.primary else c.surfaceContainerHigh
    val ink = if (current) c.onPrimary else c.onSurface
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
        .clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(level.title, style = MaterialTheme.typography.headlineSmall, color = ink, modifier = Modifier.weight(1f).semantics { heading() })
            Text("$done of ${level.lessons.size}", style = Numeric.merge(MaterialTheme.typography.labelLarge), color = ink.copy(alpha = 0.8f))
        }
        Text(level.about, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(ink.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(done / level.lessons.size.toFloat()).height(6.dp).clip(CircleShape).background(if (current) c.onPrimary else c.primary))
        }
    }
}

/**
 * One stop on the trail, shifted sideways by [wind]. The next stop is larger, pulses, has a bobbing "Start" above it
 * and the pup beside it pointing at it.
 */
@Composable
private fun StopRow(lesson: CourseLesson, number: Int, wind: Float, state: StopState, onClick: () -> Unit) {
    val still = reducedMotion()
    val x = SWING * wind
    Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(vertical = if (state == StopState.NEXT) 14.dp else 8.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.offset(x = x), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state == StopState.NEXT) {
                val t = rememberInfiniteTransition(label = "start")
                val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
                StartTag(Modifier.padding(bottom = 6.dp).graphicsLayer { translationY = if (still) 0f else -6.dp.toPx() * bob })
            }
            StopButton(state, number, lesson.title, onClick)
        }
        // The pup on the roomier side, pointing at the stop (his pose points right, so he is mirrored on the right).
        if (state == StopState.NEXT) {
            val right = wind < 0f
            Pup(Pose.GET_READY, size = 96.dp, modifier = Modifier.offset(x = x + if (right) 120.dp else (-120).dp, y = 18.dp)
                .graphicsLayer { if (right) scaleX = -1f })
        }
    }
}

/** "Start" in a little tag with a point toward the stop below it. */
@Composable
private fun StartTag(modifier: Modifier) {
    val density = LocalDensity.current
    val shape = remember(density) {
        val tail = with(density) { 7.dp.toPx() }; val r = with(density) { 12.dp.toPx() }
        GenericShape { s, _ ->
            addRoundRect(RoundRect(0f, 0f, s.width, s.height - tail, CornerRadius(r)))
            moveTo(s.width / 2 - tail, s.height - tail - 1); lineTo(s.width / 2, s.height); lineTo(s.width / 2 + tail, s.height - tail - 1); close()
        }
    }
    Text("START", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape).background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
            .padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 14.dp))
}

/** A round stop with a darker lip it sinks into when pressed, like the main buttons. */
@Composable
private fun StopButton(state: StopState, number: Int, title: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val size: Dp = if (state == StopState.NEXT) 80.dp else 68.dp
    val lip = 6.dp
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sink by animateDpAsState(if (pressed) lip else 0.dp, spring(dampingRatio = 0.45f, stiffness = 1100f), label = "sink")
    val face = when (state) { StopState.DONE, StopState.NEXT -> c.primary; StopState.LATER -> c.surfaceContainerHighest }
    val edge = when (state) { StopState.DONE, StopState.NEXT -> lerp(c.primary, Color.Black, 0.3f); StopState.LATER -> lerp(c.surfaceContainerHighest, Color.Black, 0.35f) }
    val still = reducedMotion()
    val ring = if (state == StopState.NEXT && !still) rememberInfiniteTransition(label = "ring")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulse").value else 0f
    Box(Modifier.size(size + 24.dp), contentAlignment = Alignment.Center) {
        // The next stop sends out a slow ring.
        if (state == StopState.NEXT) Box(Modifier.size(size + 4.dp).graphicsLayer {
            val k = 1f + 0.28f * ring; scaleX = k; scaleY = k; alpha = (1f - ring) * 0.5f
        }.border(3.dp, c.primary, CircleShape))
        Box(Modifier.size(size, size + lip).clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Lesson $number, $title, " + when (state) { StopState.DONE -> "done"; StopState.NEXT -> "next"; StopState.LATER -> "not started" } }) {
            Box(Modifier.size(size).offset(y = lip).background(edge, CircleShape))
            Box(Modifier.size(size).offset { IntOffset(0, sink.roundToPx()) }.background(face, CircleShape), contentAlignment = Alignment.Center) {
                when (state) {
                    StopState.DONE -> Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(32.dp))
                    StopState.NEXT -> Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(38.dp))
                    StopState.LATER -> Text("$number", style = Numeric.merge(MaterialTheme.typography.titleLarge), color = c.onSurfaceVariant)
                }
            }
        }
    }
}

/** The card a tapped stop opens: title, what it covers, how many steps, and the way in. */
@Composable
private fun LessonCard(lesson: CourseLesson, done: Boolean, next: Boolean, onStart: () -> Unit) {
    val density = LocalDensity.current
    val shape = remember(density) {
        val tail = with(density) { 10.dp.toPx() }; val r = with(density) { 20.dp.toPx() }
        GenericShape { s, _ ->
            addRoundRect(RoundRect(0f, tail, s.width, s.height, CornerRadius(r)))
            moveTo(s.width / 2 - tail, tail + 1); lineTo(s.width / 2, 0f); lineTo(s.width / 2 + tail, tail + 1); close()
        }
    }
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(lesson.title, style = MaterialTheme.typography.titleLarge)
        Text("${lesson.summary} ${lesson.steps.size} steps.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChunkyButton(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(when { done -> "Practise again"; next -> "Start"; else -> "Jump ahead" })
        }
    }
}

/** The end of a unit: the trophy pup, grey until every lesson in it is done. */
@Composable
private fun UnitTrophy(level: CourseLevel, complete: Boolean) {
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (complete) Pup(Pose.LEVEL_UP, size = 96.dp)
        else Image(painterResource(Pose.LEVEL_UP.res), contentDescription = null, colorFilter = grey,
            modifier = Modifier.size(80.dp).graphicsLayer { alpha = 0.4f })
        Text(if (complete) "${level.title} complete" else "Finish ${level.title} for the trophy", style = MaterialTheme.typography.labelLarge,
            color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** The lesson after [lesson] in course order, if any. */
fun nextLesson(lesson: CourseLesson): CourseLesson? = ALL.indexOfFirst { it.second == lesson }.let { ALL.getOrNull(it + 1)?.second }
