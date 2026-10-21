package com.bigpapituna.sharpears.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.ui.motion.countUp
import com.bigpapituna.sharpears.ui.motion.easeOutCubic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One number on a celebration: "3:12" over "Time". The number counts up from zero as it slides in. */
data class Stat(val value: String, val label: String)

/**
 * Level progress for the XP bar: from [from] to [to] as fractions of a level (0..1). A [to] past 1 fills the
 * bar, wraps (a level up) and fills on to `to - 1`. [caption] sits beside the bar, e.g. "Level 4".
 */
data class XpBar(val from: Float, val to: Float, val caption: String? = null)

/**
 * The finish line, Duolingo style: the pup bounces up from below and lands in a victory jump with a puff of
 * dust, confetti bursts, a short piano flourish plays (with the app sound on) and the phone taps once; then the
 * headline pops, the stats slide in one by one with their numbers counting up, and Continue rises last.
 * [newBest]: a slow sunburst and a burst of stars behind the pup. [xpGained]: "+120 XP" rolls up under the stats
 * with a bar filling ([xpBar] for real level progress; without it the bar simply fills). The pup wiggles when
 * tapped. Covers the screen until [onPrimary] or [onSecondary]. With animations off everything is in place at once.
 *
 * World slots (ui/world/INTEGRATION.md): [reward] sits under the stats (the drop reveal), [oneMore] at the bottom (the
 * next lesson with a preview of its drop, and its own big button; [primary] then becomes a plain text button under
 * it). With either the pup is a little smaller and the middle scrolls above the buttons.
 */
/**
 * How many Celebrations are on screen now (integration): the level-up overlay waits while this is above zero, so a
 * finish reads in order, celebration first, then the level gained.
 */
val celebrationsShowing = androidx.compose.runtime.mutableIntStateOf(0)

@Composable
fun Celebration(
    title: String, line: String, stats: List<Stat>, primary: String, onPrimary: () -> Unit,
    secondary: String? = null, onSecondary: (() -> Unit)? = null,
    pose: Pose = Pose.LEVEL_UP, sampler: Sampler? = null,
    newBest: Boolean = false, xpGained: Int? = null, xpBar: XpBar? = null,
    reward: (@Composable () -> Unit)? = null,
    oneMore: (@Composable () -> Unit)? = null,
) {
    BackHandler { (onSecondary ?: onPrimary)() }
    val compact = reward != null || oneMore != null
    val pupSize = if (compact) 120.dp else 200.dp
    androidx.compose.runtime.DisposableEffect(Unit) { celebrationsShowing.intValue++; onDispose { celebrationsShowing.intValue-- } }
    val still = reducedMotion()
    val haptic = LocalHapticFeedback.current
    fun anim() = Animatable(if (still) 1f else 0f)
    val rise = remember { anim() }      // the pup coming up
    val pop = remember { anim() }       // the headline
    val rest = remember { anim() }      // stats, then the buttons
    val count = remember { anim() }     // the stat numbers counting up
    val xp = remember { anim() }        // "+120 XP" and its bar
    val burst = remember { anim() }     // the new-best stars flying out
    val puff = remember { Animatable(0f) }   // dust where he lands (none when still)
    LaunchedEffect(Unit) {
        sampler?.let { launch { fanfare(it) } }
        if (still) return@LaunchedEffect
        launch { rise.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 180f)) }
        launch { snapshotFlow { rise.value }.first { it >= 0.97f }; puff.animateTo(1f, tween(750, easing = LinearEasing)) }
        if (newBest) launch { delay(260); burst.animateTo(1f, tween(950, easing = FastOutSlowInEasing)) }
        delay(320); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
        delay(180)
        launch { rest.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        delay(200)
        count.animateTo(1f, tween(900, easing = LinearEasing))
        if (xpGained != null) {
            xp.animateTo(1f, tween(1200, easing = LinearEasing))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    // The new-best sunburst turns slowly (one turn in 45 s), read only while drawing.
    val spin = if (newBest && !still) rememberInfiniteTransition(label = "spin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(45_000, easing = LinearEasing), RepeatMode.Restart), label = "spin") else null
    val px = with(LocalDensity.current) { 320.dp.toPx() }
    val head: @Composable ColumnScope.(pupSize: androidx.compose.ui.unit.Dp) -> Unit = { pupSize ->
        Box(Modifier.size(pupSize).landing(newBest, spin, rise, burst, puff), contentAlignment = Alignment.Center) {
            Pup(pose, size = pupSize, onTap = {}, idle = false, modifier = Modifier.graphicsLayer {
                translationY = (1 - rise.value) * px
                val s = 0.4f + 0.6f * rise.value
                scaleX = s; scaleY = s; alpha = rise.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            })
        }
        Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }.graphicsLayer {
                val s = 0.6f + 0.4f * pop.value; scaleX = s; scaleY = s; alpha = pop.value.coerceIn(0f, 1f)
            })
        Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) })
        if (stats.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.forEachIndexed { i, st ->
                // Each one in turn, sliding up, its number counting as it lands.
                Column(Modifier.graphicsLayer {
                    val k = (rest.value * (stats.size + 1) - i).coerceIn(0f, 1f)
                    alpha = k; translationY = (1 - k) * 40f
                }
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CountUpText(st.value, count, Numeric.merge(MaterialTheme.typography.titleLarge), MaterialTheme.colorScheme.onSurface,
                        delay = i * 0.12f)
                    Text(st.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (xpGained != null) XpRow(xpGained, xpBar, xp, Modifier.graphicsLayer {
            val k = ((rest.value - 0.4f) / 0.6f).coerceIn(0f, 1f); alpha = k; translationY = (1 - k) * 30f
        })
    }
    val drop: @Composable ColumnScope.() -> Unit = {
        if (reward != null && rest.value > 0.3f) Box(Modifier.padding(top = 4.dp)) { reward() }
    }
    val middle: @Composable ColumnScope.() -> Unit = { head(pupSize); drop() }
    val bottom: @Composable ColumnScope.() -> Unit = {
        // "One more" brings its own big button; then the way out is the quieter one.
        if (oneMore != null) {
            oneMore()
            TextButton(onClick = onPrimary, modifier = Modifier.padding(top = 6.dp)) { Text(primary) }
        } else ChunkyButton(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primary) }
        if (secondary != null && onSecondary != null) TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 6.dp)) { Text(secondary) }
    }
    val bottomMotion = Modifier.graphicsLayer { val k = ((rest.value - 0.5f) * 2).coerceIn(0f, 1f); alpha = k; translationY = (1 - k) * 60f }
    // A pane of its own: it takes the taps and, as a pane, hides the screen under it from TalkBack (its buttons were
    // still reachable and could be activated through the celebration).
    Box(Modifier.fillMaxSize().paper().pointerInput(Unit) { detectTapGestures { } }.semantics { paneTitle = title }
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Confetti(title, Modifier.fillMaxSize())
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // A landscape screen or a big square one (the Fold's inner screen): the pup and his words on the left, the drop
        // and the way on at the right, so nothing has to scroll out of sight.
        val wide = compact && (maxWidth > maxHeight * 1.2f || (maxWidth >= 720.dp && maxHeight < maxWidth * 1.3f))
        val headPup = minOf(maxHeight * 0.32f, 190.dp)
        if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(0.45f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                head(headPup)
            }
            Column(Modifier.weight(0.55f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                val scroll = rememberScrollState()
                Column(Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth().fadeBottom(scroll).verticalScroll(scroll).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), content = drop)
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 12.dp).then(bottomMotion),
                    horizontalAlignment = Alignment.CenterHorizontally, content = bottom)
            }
        } else if (compact) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // With a reward or "one more" there is more to show: the middle scrolls above the buttons, never under them.
            val scroll = rememberScrollState()
            Column(Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth().fadeBottom(scroll).verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), content = middle)
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp).then(bottomMotion),
                horizontalAlignment = Alignment.CenterHorizontally, content = bottom)
        } else {
            Column(Modifier.align(Alignment.Center).widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), content = middle)
            Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp).then(bottomMotion),
                horizontalAlignment = Alignment.CenterHorizontally, content = bottom)
        }
        }
    }
}

/** While more of a scrolling middle waits below, its last line fades out instead of being cut off flat. */
private fun Modifier.fadeBottom(scroll: androidx.compose.foundation.ScrollState) =
    this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        if (scroll.value < scroll.maxValue) {
            val h = 36.dp.toPx()
            drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - h, endY = size.height),
                topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        }
    }

/**
 * Behind and around the pup: for a new best a slow sunburst and a ring of stars that fly out, and in front of
 * his feet a puff of dust as he lands. Paths and brushes are built once per size.
 */
private fun Modifier.landing(newBest: Boolean, spin: State<Float>?, rise: Animatable<Float, *>, burst: Animatable<Float, *>, puff: Animatable<Float, *>) =
    this.then(Modifier.drawWithCache {
        val c = Offset(size.width / 2f, size.height * 0.55f)
        val reach = size.width * 0.95f
        val rays = Path().apply {
            for (i in 0 until 12) {
                val a0 = (i * 2 * PI / 12).toFloat(); val a1 = a0 + 0.13f
                moveTo(c.x, c.y)
                lineTo(c.x + cos(a0) * reach, c.y + sin(a0) * reach)
                lineTo(c.x + cos(a1) * reach, c.y + sin(a1) * reach)
                close()
            }
        }
        val rayBrush = Brush.radialGradient(listOf(PupGold.copy(alpha = 0.40f), PupGold.copy(alpha = 0.10f), Color.Transparent), c, reach)
        val star = starPath()
        val dust = Color(0xFFBDAE9C)
        onDrawWithContent {
            if (newBest) {
                val appear = rise.value.coerceIn(0f, 1f)
                val turn = spin?.value ?: 0f
                rotate(turn, c) { drawPath(rays, rayBrush, alpha = appear) }
                // Seven stars burst out to a ring, then hang there twinkling.
                val b = burst.value
                for (i in 0 until 7) {
                    val ang = (i * 2 * PI / 7 - PI / 2).toFloat()
                    val d = size.width * (0.52f + 0.08f * (i % 2)) * easeOutCubic(b)
                    val r = size.width * (0.035f + 0.015f * ((i * 3) % 4) / 3f) * (if (b < 0.3f) b / 0.3f else 1f)
                    val tw = if (spin == null) 1f else 0.75f + 0.25f * sin(turn * 0.5f + i)
                    translate(c.x + cos(ang) * d, c.y + sin(ang) * d) {
                        scale(r * tw, Offset.Zero) { drawPath(star, if (i % 3 == 0) Color.White else PupGold, alpha = b.coerceIn(0f, 1f)) }
                    }
                }
            }
            drawContent()
            // Dust: soft puffs rolling out both ways from his feet and fading.
            val p = puff.value
            if (p > 0f && p < 1f) {
                val feetY = size.height * 0.95f
                for (i in 0 until 6) {
                    val side = if (i % 2 == 0) -1f else 1f
                    val k = 0.55f + 0.25f * (i / 2)
                    val x = size.width / 2f + side * size.width * (0.12f + 0.30f * k * easeOutCubic(p))
                    val y = feetY - size.height * 0.07f * k * easeOutCubic(p)
                    val r = size.width * (0.035f + 0.05f * p) * (1.2f - 0.25f * (i / 2))
                    drawCircle(dust, r, Offset(x, y), alpha = 0.55f * (1f - p))
                }
            }
        }
    })

/** A five-point star of radius 1 around the origin. */
private fun starPath() = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) 1f else 0.45f
        val a = (-PI / 2 + i * PI / 5).toFloat()
        if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r)
    }
    close()
}

/** A stat's value counting up with [progress] (0..1, eased; [delay] of the run before it starts). Screen readers get the final value. */
@Composable
private fun CountUpText(value: String, progress: Animatable<Float, *>, style: TextStyle, color: Color, delay: Float = 0f) {
    val k = ((progress.value - delay) / (1f - delay)).coerceIn(0f, 1f)
    Text(countUp(value, easeOutCubic(k)), style = style, color = color,
        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(value) })
}

/** "+120 XP" rolling up, with the level bar filling (and wrapping on a level up). */
@Composable
private fun XpRow(gained: Int, bar: XpBar?, progress: Animatable<Float, *>, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.widthIn(max = 320.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CountUpText("+$gained XP", progress, Numeric.merge(MaterialTheme.typography.titleMedium), c.primary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val track = c.surfaceContainerHighest
            Canvas(Modifier.weight(1f).height(12.dp).clearAndSetSemantics { }) {
                val e = easeOutCubic(progress.value)
                val v = if (bar == null) e else bar.from + (bar.to - bar.from) * e
                val fill = if (v > 1f) v - kotlin.math.floor(v) else v
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(track, cornerRadius = r)
                if (fill > 0f) drawRoundRect(PupGold, size = Size(size.width * fill.coerceIn(0f, 1f), size.height), cornerRadius = r)
                // Right after a wrap the whole bar flashes once.
                val sinceWrap = if (bar != null && v > 1f) (v - 1f) / maxOf(0.01f, bar.to - 1f) else 1f
                if (sinceWrap < 0.35f) drawRoundRect(PupGold, cornerRadius = r, alpha = 0.6f * (1f - sinceWrap / 0.35f))
            }
            bar?.caption?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant) }
        }
    }
}

/** C, E, G, then the C above, rolled like a little "ta-da" on the piano. */
private suspend fun fanfare(sampler: Sampler) {
    listOf(72, 76, 79).forEach { sampler.noteOn(it, 70); delay(90) }
    sampler.noteOn(84, 88)
    delay(1400)
    listOf(72, 76, 79, 84).forEach(sampler::noteOff)
}

/** A cheer bar's look: a win (gold, high-five) or a miss (neutral, the pup thinking it over). */
enum class Cheer { GOOD, MISS }

/**
 * After one step, Duolingo style: a bar slides up from the bottom, the pup pops up over its edge (a small spring
 * overshoot) and the [title] types itself in, then the feedback [line] and one big button. Nothing moves on
 * until the button is pressed.
 */
@Composable
fun CheerBar(cheer: Cheer, title: String, line: String?, button: String, modifier: Modifier = Modifier, onButton: () -> Unit) {
    val still = reducedMotion()
    val haptic = LocalHapticFeedback.current
    val up = remember(title, line) { Animatable(if (still) 1f else 0f) }
    val peek = remember(title, line) { Animatable(if (still) 1f else 0f) }
    val typed = remember(title, line) { mutableIntStateOf(if (still) title.length else 0) }
    LaunchedEffect(title, line) {
        if (cheer == Cheer.GOOD) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        launch { up.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 420f)) }
        if (still) return@LaunchedEffect
        launch { delay(150); peek.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f)) }
        delay(120)
        for (i in 1..title.length) { typed.intValue = i; delay(18) }
    }
    val c = MaterialTheme.colorScheme
    val good = cheer == Cheer.GOOD
    Box(modifier.fillMaxWidth().graphicsLayer { translationY = (1 - up.value) * size.height * 1.2f }) {
        Column(Modifier.padding(top = 44.dp).fillMaxWidth()
            .background(if (good) c.primaryContainer else c.surfaceContainerHighest, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TypedText(title, typed, MaterialTheme.typography.headlineSmall, if (good) c.onPrimaryContainer else c.onSurface,
                Modifier.padding(start = 92.dp))
            line?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (good) c.onPrimaryContainer else c.onSurfaceVariant) }
            Spacer(Modifier.height(8.dp))
            ChunkyButton(onClick = onButton, modifier = Modifier.fillMaxWidth()) { Text(button) }
        }
        // The pup pops up over the top edge of the bar.
        Pup(if (good) Pose.GREAT_JOB else Pose.MISTAKE, size = 96.dp, onTap = {}, idle = false, modifier = Modifier.padding(start = 12.dp).graphicsLayer {
            val p = peek.value
            translationY = (1f - p) * size.height * 0.6f
            val s = 0.6f + 0.4f * p
            scaleX = s; scaleY = s; alpha = p.coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(0.5f, 1f)
        })
    }
}

/**
 * [text] typing itself in: the letters not typed yet are laid out but transparent, so nothing shifts as it
 * types, and screen readers get the whole line at once.
 */
@Composable
private fun TypedText(text: String, typed: State<Int>, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val n = typed.value.coerceIn(0, text.length)
    val shown = if (n >= text.length) AnnotatedString(text) else buildAnnotatedString {
        append(text.substring(0, n))
        withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring(n)) }
    }
    Text(shown, style = style, color = color, modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) })
}

/** Short, varied words for a passed step, so the bar does not say the same thing every time. */
fun cheerTitle(seed: Int) = com.bigpapituna.sharpears.engine.CHEER_TITLES.let { it[Math.floorMod(seed, it.size)] }
