package com.bigpapituna.sharpears.ui.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.SpeechBubble
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.profile.rememberCountUp
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The Practice tab's small parts: pills, a sliding segmented control, the key picker, the draggable tempo, the
 * stat tiles with the ring of twelve keys, the pup's line and the nudge that slides up after a demo.
 * Stage variants (on the dark keyboard panel) use the fixed stage colours, like the player.
 */

/** The dark panel colours: the keyboard panel is always dark, like the player's stage. */
internal val PanelColor = Color(0xFF1E1A17)
internal val PanelWell = Color(0xFF2A2420)
internal val PanelThumb = Color(0xFF443A33)

@Composable
internal fun isDarkTheme() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * A pill choice: selected ones are solid (the accent when [accent], otherwise ink), the rest outlined. It
 * shrinks a little under the finger.
 */
@Composable
internal fun Pill(
    text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    accent: Boolean = false, height: Dp = 36.dp,
) {
    val c = MaterialTheme.colorScheme
    val bg = when { !selected -> Color.Transparent; accent -> c.primary; else -> c.onSurface }
    val fg = when { !selected -> c.onSurfaceVariant; accent -> c.onPrimary; else -> c.surface }
    Box(modifier.pressScale(0.94f).height(height).clip(CircleShape).background(bg)
        .then(if (selected) Modifier else Modifier.border(1.dp, c.outline, CircleShape))
        .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }
        .padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** One horizontal, scrollable row of [Pill]s (never a wrapping cloud), scrolled so the chosen one shows. */
@Composable
internal fun PillRow(labels: List<String>, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
    KeepInView(state, selected)
    LazyRow(modifier.fillMaxWidth(), state = state, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(labels) { i, label -> Pill(label, i == selected, { onPick(i) }) }
    }
}

/**
 * Equal segments in a pill with a thumb that slides to the chosen one. [stage]: the dark panel version.
 * [dots] puts a small coloured dot before a segment's label (the hand colours on the stage).
 */
@Composable
internal fun SlidingSegments(
    labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
    stage: Boolean = false, height: Dp = 36.dp, dots: List<List<Color>>? = null, descriptions: List<String>? = null,
) {
    val c = MaterialTheme.colorScheme
    val dark = isDarkTheme()
    val well = if (stage) PanelWell else c.surfaceContainerHigh
    val thumb = if (stage) PanelThumb else if (dark) c.surfaceContainerHighest else c.surfaceContainerLowest
    val on = if (stage) Ink else c.onSurface
    val off = if (stage) InkDim else c.onSurfaceVariant
    val still = reducedMotion()
    BoxWithConstraints(modifier.height(height).clip(CircleShape).background(well).padding(3.dp)) {
        val w = maxWidth / labels.size
        val x by animateDpAsState(w * selected, if (still) tween(0) else spring(dampingRatio = 0.75f, stiffness = 600f), label = "thumb")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(thumb))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Row(Modifier.width(w).fillMaxHeight().clip(CircleShape)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { this.selected = i == selected; descriptions?.getOrNull(i)?.let { contentDescription = it } },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    dots?.getOrNull(i)?.forEach { dot ->
                        Box(Modifier.padding(end = 3.dp).size(7.dp).background(dot, CircleShape))
                    }
                    if (dots?.getOrNull(i)?.isNotEmpty() == true) Spacer(Modifier.width(2.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (i == selected) on else off, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

/**
 * The twelve keys as round pills: the name, and under it a tiny count of times practised. The chosen key is
 * filled with the accent; keys practised before carry a faint accent ring.
 */
@Composable
internal fun KeyPicker(names: List<String>, selected: Int, counts: List<Int>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0))
    KeepInView(state, selected)
    LazyRow(modifier.fillMaxWidth(), state = state, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(names) { i, name ->
            val on = i == selected
            val n = counts.getOrElse(i) { 0 }
            Column(Modifier.pressScale(0.9f).size(50.dp).clip(CircleShape)
                .background(if (on) c.primary else c.surfaceContainerHigh)
                .then(if (!on && n > 0) Modifier.border(1.5.dp, c.primary.copy(alpha = 0.55f), CircleShape) else Modifier)
                .clickable(role = Role.Tab) { onPick(i) }
                .semantics { this.selected = on; contentDescription = spokenKey(name) + if (n > 0) ", practised $n ${if (n == 1) "time" else "times"}" else "" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 17.sp),
                    color = if (on) c.onPrimary else c.onSurface)
                if (n > 0) Text("$n", style = Numeric.copy(fontSize = 9.sp, lineHeight = 10.sp), color = if (on) c.onPrimary.copy(alpha = 0.8f) else c.onSurfaceVariant)
            }
        }
    }
}

private fun spokenKey(name: String) = com.bigpapituna.sharpears.engine.practice.spokenName(name)

/**
 * A number you drag sideways to change, like a knob: [value] with [unit] under it, faint arrows either side.
 * One step per 9 dp of drag. [stage]: drawn on the dark panel.
 */
@Composable
internal fun DragNumber(
    value: Int, onChange: (Int) -> Unit, range: IntRange, unit: String, modifier: Modifier = Modifier,
    stage: Boolean = false, big: Boolean = false, what: String = "Tempo",
) {
    val c = MaterialTheme.colorScheme
    val ink = if (stage) Ink else c.onSurface
    val dim = if (stage) InkDim else c.onSurfaceVariant
    val step = with(LocalDensity.current) { 9.dp.toPx() }
    val current by rememberUpdatedState(value)
    val nudge = remember { Animatable(0f) }
    val still = reducedMotion()
    Row(modifier.pointerInput(range) {
        var acc = 0f
        detectHorizontalDragGestures(onDragStart = { acc = 0f }) { change, dx ->
            change.consume()
            acc += dx
            val n = (acc / step).toInt()
            if (n != 0) { acc -= n * step; onChange((current + n).coerceIn(range)) }
        }
    }.semantics {
        contentDescription = "$what $value $unit. Drag sideways to change."
        stateDescription = "$value"
        customActions = listOf(
            CustomAccessibilityAction("Faster") { onChange((current + 1).coerceIn(range)); true },
            CustomAccessibilityAction("Slower") { onChange((current - 1).coerceIn(range)); true },
        )
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) 14.dp else 6.dp)) {
        Chevron(left = true, color = dim, size = if (big) 12.dp else 8.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { translationX = nudge.value }) {
            Text("$value", style = (if (big) MaterialTheme.typography.displayLarge else MaterialTheme.typography.titleLarge).merge(Numeric)
                .copy(fontWeight = FontWeight.SemiBold).let { if (big) it.copy(fontSize = 96.sp, lineHeight = 96.sp, platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)) else it.copy(lineHeight = 24.sp) },
                color = ink, textAlign = TextAlign.Center)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = dim)
        }
        Chevron(left = false, color = dim, size = if (big) 12.dp else 8.dp)
    }
    // A small sideways wiggle the first time, so the number reads as something to drag.
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay(700)
        for (x in listOf(5f, -4f, 2f, 0f)) nudge.animateTo(x, tween(110))
    }
}

@Composable
private fun Chevron(left: Boolean, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val p = Path().apply {
            if (left) { moveTo(this@Canvas.size.width * 0.75f, 0f); lineTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height / 2); lineTo(this@Canvas.size.width * 0.75f, this@Canvas.size.height) }
            else { moveTo(this@Canvas.size.width * 0.25f, 0f); lineTo(this@Canvas.size.width * 0.8f, this@Canvas.size.height / 2); lineTo(this@Canvas.size.width * 0.25f, this@Canvas.size.height) }
        }
        drawPath(p, color.copy(alpha = 0.7f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** The pup and his one line, in a bubble sized for a sentence or two. */
@Composable
internal fun PupLine(pose: Pose, line: String, modifier: Modifier = Modifier, size: Dp = 60.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Pup(pose, size = size, onTap = {})
        SpeechBubble(line, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}

/**
 * Three small tiles: times practised, best tempo, and the keys covered as a ring of twelve segments (the chosen
 * key's segment outlined). Numbers count up when they first show.
 */
@Composable
internal fun StatTiles(times: Int, best: Int, covered: Set<Int>, selected: Int, keyNames: List<String>, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val t = rememberCountUp(times, 800)
    val b = rememberCountUp(best, 900)
    val k = rememberCountUp(covered.size, 800)
    Row(modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        @Composable
        fun tile(m: Modifier, content: @Composable BoxScope.() -> Unit) =
            Box(m.fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh).padding(12.dp)) { content() }
        tile(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(if (times == 0) "0" else "$t", style = MaterialTheme.typography.headlineSmall.merge(Numeric), color = c.onSurface)
                Text(if (times == 1) "time practised" else "times practised", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
        }
        tile(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Column(Modifier.align(Alignment.BottomStart)) {
                if (best == 0) Text("Not yet", style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
                else Text("$b", style = MaterialTheme.typography.headlineSmall.merge(Numeric), color = c.onSurface)
                Text("best bpm", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
        }
        tile(Modifier.weight(1.25f).semantics(mergeDescendants = true) {
            contentDescription = "${covered.size} of 12 keys practised" + if (covered.isNotEmpty()) ": " + covered.sorted().joinToString(", ") { spokenKey(keyNames[it]) } else ""
        }) {
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeysRing(covered, selected, Modifier.size(64.dp)) {
                    Text("$k", style = MaterialTheme.typography.titleMedium.merge(Numeric).copy(fontWeight = FontWeight.SemiBold), color = c.onSurface)
                }
                Text("of 12 keys", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
        }
    }
}

/** Twelve arcs around a circle, C at the top, filled in the accent for each key practised. They fill in one by one. */
@Composable
internal fun KeysRing(covered: Set<Int>, selected: Int, modifier: Modifier = Modifier, center: @Composable () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    val still = reducedMotion()
    val fill = remember { Animatable(if (still) 12f else 0f) }
    LaunchedEffect(covered) { if (!still) { fill.snapTo(0f); fill.animateTo(12f, tween(900)) } }
    val track = c.outlineVariant
    val on = c.primary
    val ink = c.onSurface
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val box = Size(size.width - inset * 2, size.height - inset * 2)
            val gap = 7f
            for (i in 0 until 12) {
                val start = -90f + i * 30f + gap / 2
                val lit = i in covered && fill.value > i
                val color = when { lit -> on; i == selected -> ink.copy(alpha = 0.35f); else -> track }
                drawArc(color, start, 30f - gap, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        center()
    }
}

/**
 * After a demo: a bar slides up from the bottom with the pup popping over its edge, [title] and one big button.
 * [onDismiss] closes it ("Not now").
 */
@Composable
internal fun NudgeBar(title: String, line: String, button: String, onButton: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val up = remember { Animatable(if (still) 1f else 0f) }
    val peek = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        launch { up.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f)) }
        if (!still) { delay(160); peek.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f)) }
    }
    val c = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().graphicsLayer { translationY = (1 - up.value) * size.height * 1.2f }) {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(Modifier.padding(top = 40.dp).fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(c.primaryContainer, shape)
            .border(1.dp, c.primary.copy(alpha = 0.35f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer, modifier = Modifier.padding(start = 84.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer, modifier = Modifier.padding(start = 84.dp))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompositionLocalProvider(LocalContentColor provides c.onPrimaryContainer) {
                    TextButton(onClick = onDismiss) { Text("Not now", color = c.onPrimaryContainer) }
                }
                ChunkyButton(onClick = onButton, modifier = Modifier.weight(1f)) { Text(button) }
            }
        }
        Pup(Pose.GREAT_JOB, size = 88.dp, onTap = {}, modifier = Modifier.padding(start = 10.dp).graphicsLayer {
            val p = peek.value
            translationY = (1f - p) * size.height * 0.6f
            val s = 0.6f + 0.4f * p
            scaleX = s; scaleY = s; alpha = p.coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(0.5f, 1f)
        })
    }
}

/** A filled progress fraction that springs to its value; used by the warm-up routine. */
@Composable
internal fun rememberSpringFloat(target: Float): Float {
    val still = reducedMotion()
    val a = remember { Animatable(target) }
    LaunchedEffect(target) { if (still) a.snapTo(target) else a.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 300f)) }
    return a.value
}

/** Scrolls a row so the chosen item is fully on screen when it changes (a tap near the edge, a new key). */
@Composable
private fun KeepInView(state: LazyListState, selected: Int) {
    LaunchedEffect(selected) {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selected }
        val inside = item != null && item.offset >= 0 && item.offset + item.size <= info.viewportEndOffset - info.afterContentPadding
        if (!inside) state.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
}
