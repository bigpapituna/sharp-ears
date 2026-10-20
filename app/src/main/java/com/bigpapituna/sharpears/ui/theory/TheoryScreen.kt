package com.bigpapituna.sharpears.ui.theory

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
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
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Clicks
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.TheoryFinish
import com.bigpapituna.sharpears.data.TheoryStore
import com.bigpapituna.sharpears.data.rememberTheoryStore
import com.bigpapituna.sharpears.engine.theory.THEORY
import com.bigpapituna.sharpears.engine.theory.THEORY_LESSONS
import com.bigpapituna.sharpears.engine.theory.TheoryLesson
import com.bigpapituna.sharpears.engine.theory.TheoryProgress
import com.bigpapituna.sharpears.engine.theory.TheoryUnit
import com.bigpapituna.sharpears.engine.theory.lessonAfter
import com.bigpapituna.sharpears.engine.theory.theoryLesson
import com.bigpapituna.sharpears.ui.Celebration
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.Stat
import com.bigpapituna.sharpears.ui.reducedMotion

/** Where the Theory Academy is: the path of units, a lesson's explainer, its quiz, or the finish. */
private sealed interface Nav {
    data object Path : Nav
    data class Watch(val lessonId: String, val scene: Int, val openedAt: Long) : Nav
    data class Quiz(val lessonId: String, val openedAt: Long, val startAt: Int = 0) : Nav
    data class Done(val lessonId: String, val correct: Int, val total: Int, val seconds: Int, val finish: TheoryFinish) : Nav
}

/**
 * The Theory Academy: six units of short animated explainers, each followed by a quiz. Opens on the path (units
 * as banners, lessons as stops); a lesson plays its explainer, then its quiz, then a celebration (a bigger one
 * when it completes a unit). Progress is kept in [store] (its own preferences file).
 *
 * Events for the rest of the app: [onLessonDone] after every quiz (lesson id, first-try score in percent) and
 * [onUnitComplete] the first time a unit's last lesson is done (unit id, its average best score, seconds spent).
 * [soundOn] lets the celebration play its fanfare (the explainers always play their notes: they are the lesson).
 */
@Composable
fun TheoryScreen(
    sampler: Sampler, voice: Voice, onBack: () -> Unit,
    store: TheoryStore = rememberTheoryStore(),
    soundOn: Boolean = true,
    onLessonDone: (lessonId: String, percent: Int) -> Unit = { _, _ -> },
    onUnitComplete: (unitId: String, percent: Int, seconds: Int) -> Unit = { _, _, _ -> },
    /** Integration: the seconds a lesson took (explainer and quiz), reported when its quiz ends; they count as practice. */
    onPractice: (seconds: Int) -> Unit = {},
) {
    val context = LocalContext.current
    // Integration: XP when the lesson opened, so its celebration can show what the lesson earned.
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    var xpAtOpen by remember { mutableStateOf(progress?.xp ?: 0) }
    val clicks = remember { runCatching { Clicks(context) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { clicks?.release() } }
    var nav by remember { mutableStateOf(debugStart(context)) }
    val version = store.version.intValue

    fun open(lessonId: String) { xpAtOpen = progress?.xp ?: 0; nav = Nav.Watch(lessonId, 0, System.currentTimeMillis()) }
    fun toPath() { voice.stop(); nav = Nav.Path }

    when (val n = nav) {
        Nav.Path -> {
            BackHandler { onBack() }
            TheoryPath(remember(version) { store.progress() }, store, onOpen = ::open, onBack = onBack)
        }
        is Nav.Watch -> {
            BackHandler { toPath() }
            val (unit, lesson) = theoryLesson(n.lessonId)!!
            androidx.compose.runtime.key(n.lessonId) {
                Explainer(unit, lesson, sampler, clicks, voice, n.scene, onScene = {},
                    onQuiz = { nav = Nav.Quiz(n.lessonId, n.openedAt) }, onClose = ::toPath)
            }
        }
        is Nav.Quiz -> {
            BackHandler { toPath() }
            val (unit, lesson) = theoryLesson(n.lessonId)!!
            androidx.compose.runtime.key(n.lessonId) {
                Quiz(unit, lesson, sampler, voice, n.startAt, onDone = { correct, total ->
                    val secs = ((System.currentTimeMillis() - n.openedAt) / 1000).toInt()
                    val f = store.finish(n.lessonId, correct, total, secs)
                    onPractice(secs)
                    onLessonDone(n.lessonId, f.percent)
                    f.unitCompleted?.let { u ->
                        val p = store.progress()
                        onUnitComplete(u.id, p.unitScore(u) ?: f.percent, store.unitSeconds(u))
                    }
                    nav = Nav.Done(n.lessonId, correct, total, secs, f)
                }, onClose = ::toPath)
            }
        }
        is Nav.Done -> {
            val (unit, lesson) = theoryLesson(n.lessonId)!!
            val after = lessonAfter(n.lessonId)
            val unitDone = n.finish.unitCompleted != null
            val p = remember(version) { store.progress() }
            val xpGained = progress?.let { (it.xp - xpAtOpen).takeIf { g -> g > 0 } }
            val xpBar = progress?.takeIf { xpGained != null }?.let { com.bigpapituna.sharpears.ui.profile.xpBarFor(xpAtOpen, it.xp) }
            Box(Modifier.fillMaxSize()) {
                if (unitDone) Celebration(
                    "Unit ${unit.number} complete!", "You finished ${unit.title.replaceFirstChar { it.lowercase() }}." + (after?.let { " Next up: ${it.first.title}." } ?: " That was the last unit."),
                    listOf(Stat("${p.unitScore(unit) ?: n.finish.percent}%", "Quiz score"), Stat(clock(store.unitSeconds(unit).coerceAtLeast(n.seconds)), "Time"),
                        Stat("${unit.lessons.size}", "Lessons")),
                    primary = if (after != null) "Next unit" else "Done", onPrimary = { after?.let { open(it.second.id) } ?: toPath() },
                    secondary = "All units", onSecondary = ::toPath, pose = Pose.LEVEL_UP, sampler = sampler.takeIf { soundOn },
                    xpGained = xpGained, xpBar = xpBar,
                ) else Celebration(
                    "Lesson complete!", lesson.summary,
                    listOf(Stat("${n.correct}/${n.total}", "Score"), Stat(clock(n.seconds), "Time"),
                        Stat("${store.best(n.lessonId) ?: n.finish.percent}%", if (n.finish.newBest) "New best" else "Best")),
                    primary = if (after != null) "Next lesson" else "Done", onPrimary = { after?.let { open(it.second.id) } ?: toPath() },
                    secondary = "All lessons", onSecondary = ::toPath, pose = Pose.GREAT_JOB, sampler = sampler.takeIf { soundOn },
                    xpGained = xpGained, xpBar = xpBar,
                )
            }
        }
    }
}

private fun clock(secs: Int) = "%d:%02d".format(secs / 60, secs % 60)

/**
 * Debug builds only: `--es theory <lesson id>` opens a lesson ([--ei scene n]), `--es part quiz [--ei question n]` its
 * quiz, `--es part done` its lesson celebration and `--es part unit` its unit celebration (nothing is saved).
 */
private fun debugStart(context: android.content.Context): Nav {
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return Nav.Path
    val intent = (context as? Activity)?.intent ?: return Nav.Path
    val id = intent.getStringExtra("theory")?.takeIf { theoryLesson(it) != null } ?: return Nav.Path
    val now = System.currentTimeMillis()
    Log.d(TAG, "theory: debug start $id ${intent.getStringExtra("part") ?: "watch"}")
    return when (intent.getStringExtra("part")) {
        "quiz" -> Nav.Quiz(id, now, intent.getIntExtra("question", 0))
        "done" -> Nav.Done(id, 4, 5, 194, TheoryFinish(80, true, null))
        "unit" -> Nav.Done(id, 5, 5, 212, TheoryFinish(100, true, theoryLesson(id)!!.first))
        else -> Nav.Watch(id, intent.getIntExtra("scene", 0), now)
    }
}

// ---- The path ----------------------------------------------------------------------------------------------------

private sealed interface Row_ {
    data class Banner(val unit: TheoryUnit) : Row_
    data class Stop(val unit: TheoryUnit, val lesson: TheoryLesson, val i: Int) : Row_
    data class Card(val lesson: TheoryLesson) : Row_
    data class Trophy(val unit: TheoryUnit) : Row_
}

private val WIND = listOf(0f, 0.55f, 0.9f, 0.55f, 0f, -0.55f, -0.9f, -0.55f)
private val SWING = 72.dp

private enum class StopState { DONE, NEXT, LATER }

/** The academy's path, in the Learn tab's style: a banner per unit, its lessons as stops on a winding trail, a trophy. */
@Composable
private fun TheoryPath(p: TheoryProgress, store: TheoryStore, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val next = p.next?.second
    var open by remember { mutableStateOf<String?>(null) }
    val rows = remember(open, next, p) {
        THEORY.flatMap { u ->
            listOf(Row_.Banner(u)) + u.lessons.flatMapIndexed { i, l -> listOfNotNull(Row_.Stop(u, l, i), Row_.Card(l).takeIf { open == l.id }) } + Row_.Trophy(u)
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) {
        val at = rows.indexOfFirst { it is Row_.Stop && it.lesson == next }
        if (at > 1) list.scrollToItem(at - 1)
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item(key = "head") {
            Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                Column(Modifier.weight(1f).padding(start = 4.dp, top = 12.dp, bottom = 12.dp)) {
                    Text("Theory", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text("${p.lessonsDone} of ${THEORY_LESSONS.size} lessons, ${p.unitsDone} of ${THEORY.size} units", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pup(Pose.LEARNING, size = 64.dp)
            }
        }
        items(rows, key = { r -> when (r) { is Row_.Banner -> "b-${r.unit.id}"; is Row_.Stop -> r.lesson.id; is Row_.Card -> "c-${r.lesson.id}"; is Row_.Trophy -> "t-${r.unit.id}" } }) { r ->
            when (r) {
                is Row_.Banner -> UnitBanner(r.unit, p.lessonsDone(r.unit), current = r.unit.lessons.any { it == next })
                is Row_.Stop -> {
                    val state = when { p.done(r.lesson) -> StopState.DONE; r.lesson == next -> StopState.NEXT; else -> StopState.LATER }
                    StopRow(r.lesson, r.i + 1, WIND[r.i % WIND.size], state) { open = if (open == r.lesson.id) null else r.lesson.id }
                }
                is Row_.Card -> LessonCard(r.lesson, p.done(r.lesson), r.lesson == next, store.best(r.lesson.id)) { open = null; onOpen(r.lesson.id) }
                is Row_.Trophy -> UnitTrophy(r.unit, p.unitDone(r.unit))
            }
        }
        if (next == null) item(key = "end") {
            PupSays(Pose.LEVEL_UP, "Every theory lesson done. Come back any time to watch one again.", Modifier.widthIn(max = 560.dp).padding(20.dp), size = 96.dp)
        }
    }
}

/** A unit's banner: number, title, what it covers, a drawn emblem and a progress bar; the unit in progress in the accent. */
@Composable
private fun UnitBanner(unit: TheoryUnit, done: Int, current: Boolean) {
    val c = MaterialTheme.colorScheme
    val bg = if (current) c.primary else c.surfaceContainerHigh
    val ink = if (current) c.onPrimary else c.onSurface
    Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
        .clip(RoundedCornerShape(20.dp)).background(bg).padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Unit ${unit.number}", style = Numeric.merge(MaterialTheme.typography.labelMedium), color = ink.copy(alpha = 0.75f))
            Text(unit.title, style = MaterialTheme.typography.headlineSmall, color = ink, modifier = Modifier.semantics { heading() })
            Text(unit.about, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f))
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(ink.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(done / unit.lessons.size.toFloat()).height(6.dp).clip(CircleShape).background(if (current) c.onPrimary else c.primary))
                }
                Text("$done of ${unit.lessons.size}", style = Numeric.merge(MaterialTheme.typography.labelMedium), color = ink.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 10.dp))
            }
        }
        UnitEmblem(unit.number, ink, Modifier.padding(start = 12.dp).size(64.dp))
    }
}

/** A small drawing for each unit: keys, a staff, beats, an interval arc, the circle, a chord stack. Gently moving. */
@Composable
internal fun UnitEmblem(number: Int, ink: Color, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val t = if (still) 0f else rememberInfiniteTransition(label = "emblem").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "e").value
    Canvas(modifier) { emblem(number, ink, t) }
}

private fun DrawScope.emblem(n: Int, ink: Color, t: Float) {
    val w = size.width; val h = size.height
    val sw = 2.5.dp.toPx()
    val beat = (t * 4).toInt()
    when (n) {
        1 -> { // a few keys, one lit in turn
            val kw = w / 4
            for (i in 0 until 4) {
                drawRoundRect(if (i == beat) ink else ink.copy(alpha = 0.25f), Offset(i * kw + 2f, h * 0.15f), Size(kw - 4f, h * 0.7f), CornerRadius(4.dp.toPx()))
            }
            for (i in listOf(1, 2)) drawRoundRect(ink.copy(alpha = 0.9f), Offset(i * kw - kw * 0.28f, h * 0.15f), Size(kw * 0.56f, h * 0.4f), CornerRadius(3.dp.toPx()))
        }
        2 -> { // five lines and a note sliding up them
            for (i in 0..4) drawLine(ink.copy(alpha = 0.5f), Offset(0f, h * (0.2f + i * 0.15f)), Offset(w, h * (0.2f + i * 0.15f)), 1.5.dp.toPx())
            val y = h * (0.8f - 0.075f * (beat + 1))
            drawOval(ink, Offset(w * 0.45f, y - h * 0.065f), Size(w * 0.22f, h * 0.13f))
        }
        3 -> { // four beats, the current one big
            for (i in 0 until 4) drawCircle(ink.copy(alpha = if (i == beat) 1f else 0.3f), (if (i == beat) 7 else 5).dp.toPx(), Offset(w * (0.14f + i * 0.24f), h / 2))
        }
        4 -> { // an arc between two keys
            drawCircle(ink, 5.dp.toPx(), Offset(w * 0.15f, h * 0.78f)); drawCircle(ink, 5.dp.toPx(), Offset(w * 0.85f, h * 0.78f))
            drawArc(ink, 180f, 180f * minOf(1f, t * 1.6f), false, Offset(w * 0.15f, h * 0.2f), Size(w * 0.7f, h * 1.16f), style = Stroke(sw, cap = StrokeCap.Round))
        }
        5 -> { // the circle, one wedge lit, turning
            for (i in 0 until 12) {
                val on = i == (t * 12).toInt() % 12
                drawArc(ink.copy(alpha = if (on) 1f else 0.28f), i * 30f - 90f - 13f, 26f, false, Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.76f), style = Stroke(w * 0.14f))
            }
        }
        else -> { // a chord stacking up
            for (i in 0 until 3) if (beat > i) drawOval(ink, Offset(w * 0.34f, h * (0.66f - i * 0.2f)), Size(w * 0.32f, h * 0.17f))
            for (i in 0..4) drawLine(ink.copy(alpha = 0.35f), Offset(0f, h * (0.2f + i * 0.15f)), Offset(w, h * (0.2f + i * 0.15f)), 1.dp.toPx())
        }
    }
}

@Composable
private fun StopRow(lesson: TheoryLesson, number: Int, wind: Float, state: StopState, onClick: () -> Unit) {
    val still = reducedMotion()
    val x = SWING * wind
    Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(vertical = if (state == StopState.NEXT) 14.dp else 8.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.offset(x = x), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state == StopState.NEXT) {
                val bob by rememberInfiniteTransition(label = "start").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
                StartTag(Modifier.padding(bottom = 6.dp).graphicsLayer { translationY = if (still) 0f else -6.dp.toPx() * bob })
            }
            StopButton(state, number, lesson.title, onClick)
            Text(lesson.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
                color = if (state == StopState.LATER) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 150.dp).padding(top = 2.dp))
        }
        if (state == StopState.NEXT) {
            val right = wind < 0f
            Pup(Pose.GET_READY, size = 96.dp, idle = false, modifier = Modifier.offset(x = x + if (right) 130.dp else (-130).dp, y = 8.dp).graphicsLayer { if (right) scaleX = -1f })
        }
    }
}

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

/** Under a tapped stop: what the lesson covers, how long it is, the best score, and the way in. */
@Composable
private fun LessonCard(lesson: TheoryLesson, done: Boolean, next: Boolean, best: Int?, onStart: () -> Unit) {
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
        Text(lesson.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${lesson.scenes.size} scenes, ${lesson.quiz.size} questions" + (best?.let { ". Best $it%" } ?: ""),
            style = Numeric.merge(MaterialTheme.typography.labelMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChunkyButton(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(when { done -> "Watch again"; next -> "Start"; else -> "Jump ahead" })
        }
    }
}

@Composable
private fun UnitTrophy(unit: TheoryUnit, complete: Boolean) {
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (complete) Pup(Pose.LEVEL_UP, size = 96.dp)
        else Image(painterResource(Pose.LEVEL_UP.res), contentDescription = null, colorFilter = grey, modifier = Modifier.size(80.dp).graphicsLayer { alpha = 0.4f })
        Text(if (complete) "${unit.title} complete" else "Finish ${unit.title.lowercase()} for the trophy", style = MaterialTheme.typography.labelLarge,
            color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---- The Learn tab's summary -------------------------------------------------------------------------------------

/**
 * The academy in one row for the Learn tab: a ring of six segments (one per unit, filled when done), units done,
 * and the next lesson. Tapping it opens the academy.
 */
@Composable
fun TheoryCard(store: TheoryStore, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val v = store.version.intValue
    val p = remember(v) { store.progress() }
    val next = p.next
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHigh).clickable(onClick = onOpen)
        .padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val done = THEORY.map { p.unitDone(it) }
        val accent = c.primary; val faint = c.outlineVariant
        Canvas(Modifier.size(44.dp)) {
            for (i in 0 until 6) drawArc(if (done[i]) accent else faint, i * 60f - 90f + 4f, 52f, false, Offset(4.dp.toPx(), 4.dp.toPx()),
                Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(Modifier.weight(1f)) {
            Text("Theory", style = MaterialTheme.typography.titleMedium)
            Text(next?.let { "Next: ${it.second.title}" } ?: "Every lesson done. Watch any again.", style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant, maxLines = 1)
            Text("${p.unitsDone} of ${THEORY.size} units", style = Numeric.merge(MaterialTheme.typography.labelSmall), color = c.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = c.primary, modifier = Modifier.size(24.dp))
    }
}

