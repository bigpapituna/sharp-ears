package com.bigpapituna.sharpears.ui.skin

import com.bigpapituna.sharpears.ui.drawRestPaw

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.SessionWords
import com.bigpapituna.sharpears.engine.family.PawDay
import com.bigpapituna.sharpears.engine.family.PawWeek
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.SpeechBubble
import com.bigpapituna.sharpears.ui.family.drawPaw
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.profile.LocalProgress
import com.bigpapituna.sharpears.ui.reducedMotion
import java.time.LocalDate
import kotlin.math.sin
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.ui.pup.Pose
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.Crossfade
import com.bigpapituna.sharpears.ui.pup.PupClips
import com.bigpapituna.sharpears.ui.pup.PupClip

/**
 * The session goodbye for a kid (docs/KIDS-PLAN.md section 3, item 9; rules in engine/skin/Bedtime): the pup yawns
 * and settles to sleep under a few twinkling stars, says goodnight with the kid's [name] ([say] hands the same line to
 * the voice once, which can say names), today's paw lands on the week, and the way on is a song: songs and the Jam stay
 * open, lessons wait for tomorrow. [onOneMore] (behind the parent gate, wired by the caller) lets a grown-up allow one
 * more lesson. A lesson opened after the goodbye shows the same screen [asleep]. Still with the phone's animations off.
 */
@Composable
fun Goodbye(
    name: String?,
    week: List<PawDay>,
    onSongs: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onJam: (() -> Unit)? = null,
    onOneMore: (() -> Unit)? = null,
    say: (String) -> Unit = {},
    seed: Int = com.bigpapituna.sharpears.data.AppClock.today().dayOfYear,
    /** A lesson opened after today's goodbye: the pup is asleep already ("New lessons tomorrow!"). */
    asleep: Boolean = false,
    /**
     * 2.14 (Mila): for a child who cannot read yet, one picture and one spoken line: the sleepy pup, and one big button (play a
     * song, or the goodbye once songs are closed or the day's time is up). No bubble, no sentence, no paws, no Jam or Close
     * pills; Back closes it, TalkBack still hears the line.
     */
    picture: Boolean = false,
    /** 2.14: Songs and the Jam are open (wind down's "Close Songs and the Jam too" off): their buttons show. */
    songsOpen: Boolean = true,
    /** 2.14: today's time is up (engine/skin/TimeCap.kt): "That's all for today. See you tomorrow!" and only the goodbye. */
    capped: Boolean = false,
) {
    if (picture) androidx.activity.compose.BackHandler(onBack = onClose)
    // 2.15, the Plain look (a big kid's goodbye): no sleepy pup (the app's mark), the plain words, days played as dots.
    val plain = com.bigpapituna.sharpears.ui.LocalPlain.current.on
    val line = remember(name, seed, asleep, capped, plain) {
        when { plain -> com.bigpapituna.sharpears.engine.family.PlainLook.goodbye(name, asleep, capped)
            capped -> com.bigpapituna.sharpears.engine.skin.TimeCap.line(name); asleep -> SessionWords.lessonsAsleep(name); else -> SessionWords.goodnight(name, seed) }
    }
    val songs = songsOpen && !capped
    LaunchedEffect(line) { say(line) }
    val still = reducedMotion()
    val rise = remember { Animatable(if (still) 1f else 0f) }     // the pup coming up, then the words
    val yawn = remember { Animatable(if (still) 1f else 0f) }     // 0..1 through one big yawn
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        rise.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 140f))
        yawn.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
    }
    val breathe = if (still) null else rememberInfiniteTransition(label = "sleep")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "breathe")

    Box(modifier.fillMaxSize().paper()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth > maxHeight * 1.15f
            val pupSize = if (wide) min(maxHeight * 0.62f, 300.dp) else min(maxWidth * 0.66f, 280.dp)
            val pup: @Composable () -> Unit = {
                if (plain) com.bigpapituna.sharpears.ui.PlainMark(min(pupSize * 0.4f, 112.dp), Modifier.graphicsLayer { alpha = rise.value.coerceIn(0f, 1f) })
                else SleepyPup(pupSize, still, rise, yawn, breathe?.let { { it.value } })
            }
            val words: @Composable ColumnScope.() -> Unit = if (picture) { {
                Spacer(Modifier.size(1.dp).semantics { heading(); liveRegion = LiveRegionMode.Polite; contentDescription = line })
                // Songs closed (wind down) or the day's time up: the one button is the goodbye.
                ChunkyButton(onClick = if (songs) onSongs else onClose,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = if (songs) "Play a song" else "Goodbye" }) {
                    Icon(painterResource(if (songs) R.drawable.ic_play_arrow else R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(34.dp))
                }
                if (onOneMore != null && !capped) TextButton(onClick = onOneMore) {
                    Text("Grown-ups: one more lesson", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } } else { {
                if (plain) Text(line, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.widthIn(max = 460.dp)
                    .semantics { heading(); liveRegion = LiveRegionMode.Polite })
                else SpeechBubble(line, Modifier.widthIn(max = 460.dp).graphicsLayer { alpha = rise.value.coerceIn(0f, 1f) }
                    .semantics { heading(); liveRegion = LiveRegionMode.Polite }, MaterialTheme.typography.titleLarge)
                Text(if (capped) SessionWords.ALL_TOMORROW else if (songs) SessionWords.STILL_OPEN else SessionWords.SONGS_CLOSED,
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp))
                PawWeekRow(week, 34.dp, stamp = true, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                Spacer(Modifier.size(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    if (songs) {
                        ChunkyButton(onClick = onSongs, modifier = Modifier.widthIn(min = 180.dp)) {
                            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(6.dp)); Text("Play a song")
                        }
                        if (onJam != null) OutlinedButton(onClick = onJam) { Text(if (plain) com.bigpapituna.sharpears.engine.family.PlainLook.JAM else "Jam with the pup") }
                        TextButton(onClick = onClose) { Text("Close") }
                    } else ChunkyButton(onClick = onClose, modifier = Modifier.widthIn(min = 180.dp)) { Text("Goodbye") }    // only the goodbye
                }
                if (onOneMore != null && !capped) TextButton(onClick = onOneMore) {
                    Text("Grown-ups: one more lesson", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Box(Modifier.weight(0.45f), contentAlignment = Alignment.Center) { pup() }
                Column(Modifier.weight(0.55f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = words)
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                pup()
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = words)
            }
        }
    }
}

/**
 * The goodnight pup (pup_goodnight.webp) in a moonlit glow with stars twinkling around him: he rises in, stretches
 * through one big yawn, then (2.10) curls up asleep, the sleep clip breathing quietly (the rig's slow SLEEP without it),
 * while little z's float up. [breathe] is a 0..1 loop, null when still.
 */
@Composable
private fun SleepyPup(size: Dp, still: Boolean, rise: Animatable<Float, *>, yawn: Animatable<Float, *>, breathe: (() -> Float)?) {
    val zText = rememberTextMeasurer()
    val star = MaterialTheme.colorScheme.onSurfaceVariant
    val zInk = MaterialTheme.colorScheme.primary
    Box(Modifier.size(size * 1.25f).clearAndSetSemantics { contentDescription = "The pup yawning, ready for bed" }
        .drawBehind {
            val c = Offset(this.size.width / 2f, this.size.height * 0.55f)
            val r = this.size.width * 0.5f
            drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = 0.30f), PupGold.copy(alpha = 0.08f), Color.Transparent), c, r), r, c)
            // Five little stars around him, each twinkling on its own beat.
            val t = breathe?.invoke() ?: 0.25f
            listOf(0.12f to 0.16f, 0.86f to 0.12f, 0.93f to 0.46f, 0.06f to 0.52f, 0.72f to 0.04f).forEachIndexed { i, (x, y) ->
                val tw = if (still) 0.8f else (0.55f + 0.45f * sin((t + i * 0.21f) * 2 * Math.PI).toFloat())
                val p = Offset(this.size.width * x, this.size.height * y)
                val s = this.size.width * (0.012f + 0.006f * (i % 2)) * (0.7f + 0.5f * tw)
                drawCircle(star, s, p, alpha = 0.35f + 0.5f * tw)
                drawLine(star, p.copy(x = p.x - s * 2.4f), p.copy(x = p.x + s * 2.4f), strokeWidth = s * 0.45f, alpha = 0.3f * tw)
                drawLine(star, p.copy(y = p.y - s * 2.4f), p.copy(y = p.y + s * 2.4f), strokeWidth = s * 0.45f, alpha = 0.3f * tw)
            }
        }, contentAlignment = Alignment.Center) {
        // 2.10: after the yawn he curls up asleep: the sleep clip, faded in over the yawning pup.
        val dozing by remember { derivedStateOf { !still && yawn.value >= 1f } }
        Crossfade(dozing, animationSpec = tween(com.bigpapituna.sharpears.engine.pup.PupMotion.SWAP_MS), label = "dozing") { asleepNow ->
        if (asleepNow) PupClip(PupClips.SLEEP, size = size, fallback = Pose.GOODNIGHT, then = PupMood.SLEEP, jump = false, ground = false,
            scale = PupClips.SLEEP_SCALE)
        // The rise and the one big yawn are this screen's; the rig's SLEEP breathes for him meanwhile.
        else Pup(Pose.GOODNIGHT, PupMood.SLEEP, size = size, ground = false, idle = false, bounce = com.bigpapituna.sharpears.ui.motion.BounceSize.NONE,
            modifier = Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                val k = rise.value
                translationY = (1f - k) * size.toPx() * 0.4f
                alpha = k.coerceIn(0f, 1f)
                // The yawn: a stretch up and a little lean back.
                val stretch = sin(yawn.value * Math.PI).toFloat()
                scaleY = 1f + 0.07f * stretch
                scaleX = 1f - 0.03f * stretch
                rotationZ = -4f * stretch
            })
        }
        // Floating z's while he sleeps (a still "z z" with animations off). One "z" is measured once and scaled.
        val z = remember(zInk) { zText.measure("z", androidx.compose.ui.text.TextStyle(color = zInk, fontSize = 36.sp, fontFamily = Nunito)) }
        Canvas(Modifier.size(size * 1.25f)) {
            val origin = Offset(this.size.width * 0.66f, this.size.height * 0.34f)
            fun zAt(at: Offset, scale: Float, alpha: Float) = scale(scale, pivot = at) { drawText(z, topLeft = at, alpha = alpha) }
            if (breathe == null) {
                zAt(origin, 0.6f, 1f); zAt(Offset(origin.x + 30f, origin.y - 45f), 0.85f, 1f)
                return@Canvas
            }
            val t = breathe()
            for (i in 0 until 3) {
                val p = (t + i / 3f) % 1f
                val fade = (if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f) * yawn.value
                zAt(Offset(origin.x + this.size.width * 0.16f * p + 10f * sin(p * 6f), origin.y - this.size.height * 0.34f * p),
                    0.5f + 0.5f * p, fade.coerceIn(0f, 1f))
            }
        }
    }
}

/**
 * This week's paws, Monday to Sunday, with the day's initial under each. With [stamp], today's paw lands with a little
 * stamp (scale down from big, as if pressed on); days still to come are faint, a missed day a plain outline.
 */
@Composable
fun PawWeekRow(week: List<PawDay>, size: Dp, modifier: Modifier = Modifier, stamp: Boolean = false, today: LocalDate = com.bigpapituna.sharpears.data.AppClock.today()) {
    // 2.15, the Plain look: the days played as dots, no paws and no rest-day moon (rest days still count, silently).
    if (com.bigpapituna.sharpears.ui.LocalPlain.current.on) {
        com.bigpapituna.sharpears.ui.PlainDays(week.map { Triple(it.initial, it.practised, it.date == today) }, size * 0.5f, modifier)
        return
    }
    val c = MaterialTheme.colorScheme
    val still = reducedMotion()
    val press = remember { Animatable(if (still || !stamp) 1f else 0f) }
    LaunchedEffect(Unit) { if (!still && stamp) { kotlinx.coroutines.delay(900); press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) } }
    val days = week.count { it.practised }
    val rests = week.count { it.rest }
    Row(modifier.semantics(mergeDescendants = true) {
        contentDescription = "Paw prints this week: $days of 7 days" + if (rests > 0) ", and a rest day" else ""
    },
        horizontalArrangement = Arrangement.spacedBy(size * 0.22f)) {
        week.forEach { d ->
            val isToday = d.date == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(size).graphicsLayer {
                    if (isToday && d.practised) {
                        val k = press.value
                        val s = 1.7f - 0.7f * k; scaleX = s; scaleY = s; alpha = k.coerceIn(0f, 1f)
                    }
                }) {
                    val colour = when { d.practised -> c.primary; d.future -> c.outlineVariant; else -> c.outline }
                    // 2.9: a missed day the streak kept sleeps under a little moon (a rest day, never a gap).
                    if (d.rest) drawRestPaw(c.primary) else drawPaw(colour, filled = d.practised)
                }
                Spacer(Modifier.size(3.dp))
                Box(Modifier.size(size * 0.62f).clip(CircleShape).then(if (isToday) Modifier.border(1.5.dp, c.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center) {
                    Text(d.initial, style = MaterialTheme.typography.labelSmall, color = if (isToday) c.primary else c.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * The paw calendar for the You tab: seven paws for this week, filled on days played (any practice counts, as for the
 * streak); a missed day is a blank paw, never a lost streak. Kids get a line with no number in it. Reads the week from
 * [LocalProgress] when [week] is not given.
 */
@Composable
fun PawCalendarCard(kid: Boolean, modifier: Modifier = Modifier, week: List<PawDay>? = null, today: LocalDate = com.bigpapituna.sharpears.data.AppClock.today()) {
    val progress = LocalProgress.current
    val days = week ?: remember(progress, today) { PawWeek.of(progress?.practiceLog() ?: emptyMap(), today) }
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Paw prints", style = MaterialTheme.typography.titleMedium, color = c.onSurface, modifier = Modifier.semantics { heading() })
            Text(SessionWords.pawLine(days.count { it.practised }, kid), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        PawWeekRow(days, if (kid) 34.dp else 28.dp, today = today)
    }
}
