package com.bigpapituna.sharpears.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.PracticeStore
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.practice.RoutineItem
import com.bigpapituna.sharpears.engine.practice.Warmup
import com.bigpapituna.sharpears.engine.practice.WarmupPreview
import com.bigpapituna.sharpears.engine.practice.buildRoutine
import com.bigpapituna.sharpears.engine.practice.routineIntro
import com.bigpapituna.sharpears.engine.practice.routineRoot
import com.bigpapituna.sharpears.engine.practice.routineSeconds
import com.bigpapituna.sharpears.engine.practice.scaleRootName
import com.bigpapituna.sharpears.engine.practice.spokenName
import com.bigpapituna.sharpears.engine.practice.warmupCatalog
import com.bigpapituna.sharpears.engine.practice.warmupPreview
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.reducedMotion

/*
 * Warm-ups: build a routine (5, 10 or 15 minutes, a level), see it as a short list of steps with a tiny keyboard
 * playing each pattern, and start it. Every warm-up is also listed on its own below, in any key.
 */

private fun minutesWords(seconds: Double): String {
    val m = Math.round(seconds / 60.0).toInt()
    return if (m < 1) "under a minute" else "about $m min"
}

private val LEVELS = listOf("Easy", "Medium", "Hard")

@Composable
internal fun WarmupsScreen(store: PracticeStore, onOpen: (Exercise, String) -> Unit, onEvent: (PracticeEvent) -> Unit,
                           /** Simple mode (2.7): one warm-up (engine/nav/Simple.kt), no routine to build. */
                           simple: Boolean = false, foot: @Composable () -> Unit = {}) {
    var minutes by remember { mutableIntStateOf(store.int("warmup.minutes", 10).let { if (it in listOf(5, 10, 15)) it else 10 }) }
    var level by remember { mutableIntStateOf(store.int("warmup.level", 1).coerceIn(1, 3)) }
    var seed by rememberSaveable { mutableIntStateOf(store.int("warmup.seed", 0)) }
    var browseRoot by remember { mutableIntStateOf(store.int("warmup.root", 0).coerceIn(0, 11)) }
    var opened by rememberSaveable { mutableStateOf(setOf<String>()) }
    @Suppress("UNUSED_VARIABLE") val unusedVersion = store.version.intValue
    val routine = remember(minutes, level, seed) { buildRoutine(minutes, level, seed) }
    val catalog = remember(browseRoot) { warmupCatalog(browseRoot) }
    val clock = rememberPreviewClock()
    val c = MaterialTheme.colorScheme

    fun open(w: Warmup, bpm: Int) {
        store.recordWarmup(w.id, bpm)
        opened = opened + w.id
        onEvent(PracticeEvent(PracticeKind.WARMUP, w.id, w.title, bpm))
        onOpen(w.at(bpm), w.title)
    }

    if (simple) {
        // One warm-up: the first of the catalogue, in C, at its usual tempo.
        val w = remember { com.bigpapituna.sharpears.engine.nav.Simple.warmups(warmupCatalog(0)).first() }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PupLine(Pose.GET_READY, "One warm-up to wake the fingers before you play.")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PatternPreview(remember(w.id) { warmupPreview(w) }, clock, Modifier.size(width = 84.dp, height = 44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text(w.description, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                }
                ChunkyButton(onClick = { open(w, w.defaultBpm) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
            }
            foot()
        }
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PupLine(Pose.GET_READY, routineIntro(minutes, routine))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (m in listOf(5, 10, 15)) {
                val on = m == minutes
                Column(Modifier.weight(1f).pressScale(0.95f).height(64.dp).clip(CircleShape)
                    .background(if (on) c.primary else c.surfaceContainerHigh)
                    .clickable(role = Role.Tab) { minutes = m; store.putInt("warmup.minutes", m); opened = emptySet() }
                    .semantics { selected = on; contentDescription = "$m minutes" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("$m", style = MaterialTheme.typography.headlineSmall.merge(Numeric).copy(fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
                        color = if (on) c.onPrimary else c.onSurface)
                    Text("min", style = MaterialTheme.typography.labelSmall, color = if (on) c.onPrimary.copy(alpha = 0.85f) else c.onSurfaceVariant)
                }
            }
        }
        SlidingSegments(LEVELS, level - 1, { level = it + 1; store.putInt("warmup.level", level); opened = emptySet() }, Modifier.fillMaxWidth(), height = 40.dp)

        // The routine: the one raised panel on this screen, its steps joined by a line.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("In ${spokenName(scaleRootName(routineRoot(seed), false))} major", style = MaterialTheme.typography.titleLarge)
                    Text("${routine.size} exercises, ${minutesWords(routineSeconds(routine))}", style = MaterialTheme.typography.labelMedium,
                        color = c.onSurfaceVariant)
                }
                TextButton(onClick = { seed++; store.putInt("warmup.seed", seed); opened = emptySet() }) { Text("Another key") }
            }
            Spacer(Modifier.height(6.dp))
            routine.forEachIndexed { i, item ->
                RoutineStep(i + 1, item, done = item.warmup.id in opened, last = i == routine.lastIndex, clock = clock) { open(item.warmup, item.bpm) }
            }
            Spacer(Modifier.height(10.dp))
            val next = routine.firstOrNull { it.warmup.id !in opened }
            ChunkyButton(onClick = { val n = next ?: routine.first(); open(n.warmup, n.bpm) }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(when { opened.isEmpty() -> "Start"; next == null -> "Start again"; else -> "Next: step ${routine.indexOf(next) + 1}" })
            }
        }

        Text("All warm-ups", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
    }
        // The key picker scrolls edge to edge, so the list sits outside the padding.
        WarmupCatalog(catalog, browseRoot, { browseRoot = it; store.putInt("warmup.root", it) }, store, clock, ::open)
        foot()
    }
}

@Composable
private fun WarmupCatalog(catalog: List<Warmup>, root: Int, onRoot: (Int) -> Unit, store: PracticeStore, clock: State<Long>, open: (Warmup, Int) -> Unit) {
    Column {
        KeyPicker((0..11).map { scaleRootName(it, false) }, root, List(12) { 0 }, onRoot)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            catalog.forEachIndexed { i, w ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val stat = store.stat("warmup:${w.id}")
                Row(Modifier.fillMaxWidth().pressScale(0.98f).clip(RoundedCornerShape(12.dp)).clickable { open(w, w.defaultBpm) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PatternPreview(remember(w.id) { warmupPreview(w) }, clock, Modifier.size(width = 76.dp, height = 40.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(w.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text("${LEVELS[w.level - 1]}, ${w.bpmMin} to ${w.bpmMax} bpm" + if (stat.times > 0) ", done ${stat.times}x" else "",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = "Practice ${w.title}", tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** One step of the routine: its number (a tick once opened) on a line joining the steps, the preview, what to play. */
@Composable
private fun RoutineStep(number: Int, item: RoutineItem, done: Boolean, last: Boolean, clock: State<Long>, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val w = item.warmup
    val line = c.outlineVariant
    Row(Modifier.fillMaxWidth().drawBehind {
            // The line joining the steps runs behind the number circles.
            val x = 14.dp.toPx()
            val mid = size.height / 2
            if (number > 1) drawLine(line, Offset(x, 0f), Offset(x, mid), 2.dp.toPx())
            if (!last) drawLine(line, Offset(x, mid), Offset(x, size.height), 2.dp.toPx())
        }.pressScale(0.98f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) c.primary else c.surfaceContainerHigh)
            .then(if (done) Modifier else Modifier.border(1.5.dp, c.outline, CircleShape)), contentAlignment = Alignment.Center) {
            if (done) Icon(painterResource(R.drawable.ic_check), contentDescription = "Done", tint = c.onPrimary, modifier = Modifier.size(16.dp))
            else Text("$number", style = MaterialTheme.typography.labelMedium.merge(Numeric), color = c.onSurfaceVariant)
        }
        PatternPreview(remember(w.id) { warmupPreview(w) }, clock, Modifier.size(width = 84.dp, height = 44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(w.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text("${item.reps}x at ${item.bpm} bpm, range ${w.bpmMin} to ${w.bpmMax}", style = MaterialTheme.typography.labelMedium,
                color = c.onSurfaceVariant)
        }
    }
}

/** A frame clock shared by every preview on the screen (read only while drawing, so nothing recomposes). */
@Composable
internal fun rememberPreviewClock(): State<Long> {
    val t = remember { mutableLongStateOf(0L) }
    val still = reducedMotion()
    if (!still) LaunchedEffect(Unit) { val start = withFrameMillis { it }; while (true) withFrameMillis { t.longValue = it - start } }
    return t
}

private val MiniWhite = Color(0xFFF6F1EA)
private val MiniBlack = Color(0xFF1A1613)

/**
 * A tiny keyboard with dots walking the warm-up's pattern: right-hand dots in the right colour, left in the left.
 * Each step glides from the last. With animations off it shows the first step and the whole pattern faintly.
 */
@Composable
internal fun PatternPreview(p: WarmupPreview, clock: State<Long>, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    // Each step lasts in proportion to its beats, within limits so sixteenths are not a blur.
    val durations = remember(p) { p.steps.map { (it.beats * 340).coerceIn(170.0, 700.0).toLong() } }
    val loop = remember(p) { durations.sum() + 500 }
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(PanelColor).clearAndSetSemantics { }) {
        val pad = 3.dp.toPx()
        val whites = (p.low..p.high).filter { !isBlack(it) }
        val ww = (size.width - pad * 2) / whites.size
        val h = size.height - pad * 2
        fun x(m: Int): Float {
            val i = whites.indexOf(if (isBlack(m)) m - 1 else m)
            return pad + (i + if (isBlack(m)) 1f else 0.5f) * ww
        }
        for ((i, _) in whites.withIndex()) {
            drawRoundRect(MiniWhite, Offset(pad + i * ww + 0.6f, pad), Size(ww - 1.2f, h), CornerRadius(1.5.dp.toPx()))
        }
        for (m in p.low..p.high) if (isBlack(m)) drawRoundRect(MiniBlack, Offset(x(m) - ww * 0.3f, pad), Size(ww * 0.6f, h * 0.6f), CornerRadius(1.dp.toPx()))
        // Where the playhead is in the loop.
        val t = if (still) 0L else clock.value % loop
        var acc = 0L; var step = p.steps.lastIndex; var into = 1f
        for ((i, d) in durations.withIndex()) { if (t < acc + d) { step = i; into = (t - acc).toFloat() / d; break }; acc += d }
        val resting = !still && t >= durations.sum()
        val r = (ww * 0.36f).coerceAtMost(4.5.dp.toPx())
        fun y(m: Int) = if (isBlack(m)) pad + h * 0.42f else pad + h * 0.8f
        if (still) for (s in p.steps) for (m in s.right + s.left) drawCircle((if (m in s.left) LeftHandColor else RightHandColor).copy(alpha = 0.35f), r * 0.7f, Offset(x(m), y(m)))
        if (resting) return@Canvas
        val cur = p.steps[step]
        val prev = p.steps.getOrNull(step - 1)
        val e = 1f - (1f - (into / 0.35f).coerceIn(0f, 1f)).let { it * it }
        fun dots(now: List<Int>, before: List<Int>?, color: Color) {
            for ((k, m) in now.withIndex()) {
                val from = before?.takeIf { it.size == now.size }?.get(k)
                val cx = if (from == null) x(m) else x(from) + (x(m) - x(from)) * e
                val cy = if (from == null) y(m) else y(from) + (y(m) - y(from)) * e
                drawCircle(color.copy(alpha = 0.3f), r * 1.7f, Offset(cx, cy))
                drawCircle(color, r, Offset(cx, cy))
            }
        }
        dots(cur.right, prev?.right, RightHandColor)
        dots(cur.left, prev?.left, LeftHandColor)
    }
}
