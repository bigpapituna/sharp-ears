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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.CoursePath
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_ID
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.ui.world.PlaceScene
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.LessonKind
import com.bigpapituna.sharpears.engine.kind
import com.bigpapituna.sharpears.engine.lessonKind
import com.bigpapituna.sharpears.ui.motion.pressScale

/** Lessons in course order (every unit, the Playground first), for finding a lesson's unit. */
internal val ALL = COURSE.flatMap { lv -> lv.lessons.map { lv to it } }

/**
 * One row of the path: a unit banner, a lesson stop, the card under a tapped stop, or a unit's trophy. A kid's
 * Playground is not a trail but the pup's world: each place a [Scene] with its four lessons as stops on it.
 */
private sealed interface PathRow {
    data class Banner(val level: CourseLevel) : PathRow
    data class Stop(val level: CourseLevel, val lesson: CourseLesson, val i: Int) : PathRow
    data class Card(val lesson: CourseLesson) : PathRow
    data class Trophy(val level: CourseLevel) : PathRow
    data class Scene(val level: CourseLevel, val place: Place, val lessons: List<CourseLesson>) : PathRow
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
fun LearnScreen(done: (String) -> Boolean, onOpen: (CourseLesson) -> Unit, onSightRead: () -> Unit = {}, sightBest: String? = null,
                onEar: () -> Unit = {}, earStreak: Int = 0,
                /** The Theory Academy's row (TheoryCard), drawn first beside the course. */
                theory: (@Composable (Modifier) -> Unit)? = null,
                /** The course in this person's order (2.1): a kid's starts in the Playground, a grown-up's at Basics. */
                path: CoursePath = CoursePath("basics")) {
    val next = path.next(done)
    val kid = com.bigpapituna.sharpears.ui.family.isKid()
    val reader = com.bigpapituna.sharpears.ui.family.reader()
    var open by remember { mutableStateOf<String?>(null) }
    // A kid walks the Playground through the pup's world (the places drawn from their own stickers).
    val world = com.bigpapituna.sharpears.ui.world.LocalWorld.current
    val mapped = world != null && path.levels.first().id == PLAYGROUND_ID && !path.isExtra(path.levels.first())
    val rows = remember(open, next, mapped) {
        path.levels.flatMap { level ->
            val body = if (mapped && level.id == PLAYGROUND_ID) Place.entries.flatMapIndexed { p, place ->
                val four = level.lessons.drop(p * 4).take(4)
                listOf(PathRow.Scene(level, place, four)) + four.filter { it.id == open }.map { PathRow.Card(it) }
            } else level.lessons.flatMapIndexed { i, l -> listOfNotNull(PathRow.Stop(level, l, i), PathRow.Card(l).takeIf { open == l.id }) }
            listOf(PathRow.Banner(level)) + body + PathRow.Trophy(level)
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) {
        val at = rows.indexOfFirst { (it is PathRow.Stop && it.lesson == next) || (it is PathRow.Scene && next in it.lessons) }
        if (at > 0) list.scrollToItem(at - 1)      // two rows above the next stop (the header is item 0), so it sits in view
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item(key = "head") {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(top = 20.dp, bottom = 12.dp)) {
                        Text("Learn", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        // A kid's path has no count to keep up with (the map and its ticks say it).
                        if (!kid) Text("${path.counted.count { done(it.id) }} of ${path.counted.size} lessons", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Pup(Pose.LEARNING, size = 64.dp)
                }
                // Beside the course: practice that is never "done" (new music, new rounds each time). Not before a kid
                // can read: the cards are words, and the pup's world below is the whole path for them.
                if (!kid || reader) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    theory?.invoke(Modifier.pressScale())
                    SideRow("Ear training", "Rhythm, melody, pitch, scale degrees, progressions. Daily workout inside.",
                        earStreak.takeIf { it > 0 }?.let { "Workout: $it ${if (it == 1) "day" else "days"} in a row" }, R.drawable.ic_ear, onEar)
                    SideRow("Sight-read", sightBest?.let { "Four new bars each time. Best $it." } ?: "Four new bars each time, names hidden.",
                        null, R.drawable.ic_play_arrow, onSightRead)
                }
            }
        }
        items(rows, key = { r -> when (r) { is PathRow.Banner -> "b-${r.level.id}"; is PathRow.Stop -> r.lesson.id; is PathRow.Card -> "c-${r.lesson.id}"
            is PathRow.Trophy -> "t-${r.level.id}"; is PathRow.Scene -> "s-${r.place.name}" } }) { r ->
            when (r) {
                is PathRow.Banner -> UnitBanner(r.level, r.level.lessons.count { done(it.id) }, current = r.level.lessons.any { it == next },
                    number = path.levels.indexOf(r.level) + 1, extra = path.isExtra(r.level))
                is PathRow.Stop -> {
                    val state = when { done(r.lesson.id) -> StopState.DONE; r.lesson == next -> StopState.NEXT; else -> StopState.LATER }
                    StopRow(r.lesson, path.numberOf(r.lesson), WIND[r.i % WIND.size], state) {
                        open = if (open == r.lesson.id) null else r.lesson.id
                    }
                }
                is PathRow.Scene -> PlaceScene(r.place, world!!.state, Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    stop = { n ->
                        r.lessons.getOrNull((n - 1) % 4)?.let { l ->
                            val state = when { done(l.id) -> StopState.DONE; l == next -> StopState.NEXT; else -> StopState.LATER }
                            MapStop(l, n, state) { open = if (open == l.id) null else l.id }
                        }
                    })
                is PathRow.Card -> LessonCard(r.lesson, done(r.lesson.id), r.lesson == next) { open = null; onOpen(r.lesson) }
                is PathRow.Trophy -> UnitTrophy(r.level, r.level.lessons.all { done(it.id) })
            }
        }
        if (next == null) item(key = "end") {
            PupSays(Pose.LEVEL_UP, "Every lesson done. Keep going with songs and daily practice.", Modifier.widthIn(max = 560.dp).padding(20.dp), size = 96.dp)
        }
    }
}

/** A row beside the course (Ear training, Sight-read): title, what it is, an optional status in the accent. */
@Composable
private fun SideRow(title: String, detail: String, status: String?, icon: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status?.let { Text(it, style = Numeric.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp)) }
        }
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    }
}

private enum class StopState { DONE, NEXT, LATER }

/** The icon for a lesson (or step) kind: a book to read, an ear to listen, a hand to play, the metronome to perform, a note for a song. */
private fun LessonKind.icon() = when (this) {
    LessonKind.READ -> R.drawable.ic_book
    LessonKind.LISTEN -> R.drawable.ic_hearing
    LessonKind.PLAY -> R.drawable.ic_hand
    LessonKind.PERFORM -> R.drawable.ic_practice
    LessonKind.SONG -> R.drawable.ic_music_note
}

/**
 * A unit's banner: "Unit n", its name and what it covers, with a progress bar and the kinds of lesson inside as
 * small icons. The unit in progress is filled with the accent.
 */
@Composable
private fun UnitBanner(level: CourseLevel, done: Int, current: Boolean, number: Int, extra: Boolean) {
    val c = MaterialTheme.colorScheme
    val bg = if (current) c.primary else c.surfaceContainerHigh
    val ink = if (current) c.onPrimary else c.onSurface
    val kinds = remember(level) { level.lessons.map { it.kind }.distinct().sortedBy { it.ordinal } }
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
        .clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A grown-up's Playground is an extra, open to everyone: no unit number, no claim on the next lesson.
            Text(if (extra) "Extra: games for kids, open to all" else "Unit $number", style = Numeric.merge(MaterialTheme.typography.labelMedium),
                color = ink.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            // What is inside: one small icon per kind of lesson.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = kinds.joinToString(", ") { it.label }
            }) {
                kinds.forEach { k ->
                    Box(Modifier.size(26.dp).background(ink.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(k.icon()), contentDescription = null, tint = ink.copy(alpha = 0.9f), modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
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
 * One stop on the trail, shifted sideways by [wind], with its lesson's title under it (as on the Theory path). The
 * next stop is larger, pulses, has a bobbing "Start" above it and the pup beside it pointing at it.
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
            StopButton(state, number, lesson.title, lesson.kind, onClick)
            Text(lesson.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
                color = if (state == StopState.LATER) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 150.dp).padding(top = 2.dp).clearAndSetSemantics { })
        }
        // The pup on the roomier side, pointing at the stop (his pose points right, so he is mirrored on the right).
        if (state == StopState.NEXT) {
            val right = wind < 0f
            Pup(Pose.GET_READY, size = 96.dp, idle = false, modifier = Modifier.offset(x = x + if (right) 132.dp else (-132).dp, y = 8.dp)
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
    Text("Start", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape).background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
            .padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 14.dp))
}

/**
 * A round stop with a darker lip it sinks into when pressed, like the main buttons. Inside, the icon of the
 * lesson's [kind]; a done stop also wears a small tick.
 */
@Composable
private fun StopButton(state: StopState, number: Int, title: String, kind: LessonKind, onClick: () -> Unit) {
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
            .semantics { contentDescription = "Lesson $number, $title, ${kind.label.lowercase()}, " + when (state) { StopState.DONE -> "done"; StopState.NEXT -> "next"; StopState.LATER -> "not started" } }) {
            Box(Modifier.size(size).offset(y = lip).background(edge, CircleShape))
            Box(Modifier.size(size).offset { IntOffset(0, sink.roundToPx()) }.background(face, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(kind.icon()), contentDescription = null, tint = if (state == StopState.LATER) c.onSurfaceVariant else c.onPrimary,
                    modifier = Modifier.size(if (state == StopState.NEXT) 36.dp else 30.dp))
                if (state == StopState.DONE) Box(Modifier.align(Alignment.BottomEnd).size(24.dp).background(c.surface, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** The card a tapped stop opens: title, what it covers, its steps as a row of icons, and the way in. */
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
    val c = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp)
        .background(c.surfaceContainerHigh, shape).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(lesson.title, style = MaterialTheme.typography.titleLarge)
        Text(lesson.summary, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        // The steps in order, joined like a little trail.
        FlowRow(Modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) {
            contentDescription = "${lesson.steps.size} steps: " + lesson.steps.joinToString(", ") { it.lessonKind.label.lowercase() }
        }, verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            lesson.steps.forEachIndexed { i, step ->
                if (i > 0) Box(Modifier.size(width = 12.dp, height = 2.dp).background(c.outlineVariant))
                Box(Modifier.size(32.dp).background(c.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(step.lessonKind.icon()), contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }
            Text("${lesson.steps.size} steps", style = Numeric.merge(MaterialTheme.typography.labelMedium), color = c.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp))
        }
        ChunkyButton(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
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

/**
 * A Playground lesson on the pup's world map: a round stop like the trail's, smaller, numbered 1 to 12. The next one
 * is bigger and sends out a white ring; a done one wears a tick.
 */
@Composable
private fun MapStop(lesson: CourseLesson, number: Int, state: StopState, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val still = reducedMotion()
    val size: Dp = if (state == StopState.NEXT) 52.dp else 42.dp
    val face = when (state) { StopState.DONE, StopState.NEXT -> c.primary; StopState.LATER -> c.surfaceContainerHighest }
    val ring = if (state == StopState.NEXT && !still) rememberInfiniteTransition(label = "ring")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulse").value else 0f
    Box(Modifier.size(size + 20.dp), contentAlignment = Alignment.Center) {
        if (state == StopState.NEXT) Box(Modifier.size(size + 4.dp).graphicsLayer {
            val k = 1f + 0.3f * ring; scaleX = k; scaleY = k; alpha = (1f - ring) * 0.7f
        }.border(3.dp, Color.White, CircleShape))
        Box(Modifier.size(size).pressScale().clip(CircleShape).background(face).border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Lesson $number, ${lesson.title}, " + when (state) { StopState.DONE -> "done"; StopState.NEXT -> "next"; StopState.LATER -> "not started" } },
            contentAlignment = Alignment.Center) {
            if (state == StopState.DONE) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(22.dp))
            else Text("$number", style = Numeric.merge(MaterialTheme.typography.titleMedium),
                color = if (state == StopState.LATER) c.onSurfaceVariant else c.onPrimary)
        }
    }
}
