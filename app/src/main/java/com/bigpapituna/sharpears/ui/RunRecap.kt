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
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.skin.Stars
import com.bigpapituna.sharpears.ui.family.profile
import com.bigpapituna.sharpears.ui.skin.StarRow
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
 *
 * 2.1: every recap leads with one to three stars (engine/skin/Stars), landing one by one with a chime. Grown-ups keep
 * everything above under them. A kid sees no number at all (no ring, no chips, no XP, no "Loop bar 3"): the stars,
 * the pup, and the kid words from RunWords with their name; three stars are the party. Then the sticker the run
 * dropped ([RunEnd.reward], the world's reveal), "Play it again" with a peek at the next drop ([nextDrop]) and "Send to
 * someone" (the run replayed plus a card, ui/play/ShareConcert). [say] speaks the pup's words: a kid hears them with
 * their name on every recap, a grown-up on a new best only.
 */
@Composable
fun RunRecap(e: RunEnd, speed: Double, sampler: Sampler?, onAgain: () -> Unit, onLoop: (TroubleSpot) -> Unit, onClose: () -> Unit, onReplay: () -> Unit,
             songTitle: String? = null, run: com.bigpapituna.sharpears.engine.RunRecording? = null,
             nextDrop: com.bigpapituna.sharpears.engine.world.NextDrop? = null, say: ((String) -> Unit)? = null) {
    val still = reducedMotion()
    val haptic = LocalHapticFeedback.current
    val m = remember(e) { RecapMotion(still) }
    val who = profile()
    val kid = who.kind == ProfileKind.KID
    val stars = Stars.of(e.recap, e.wrongKeys)
    val seed = remember(e) { e.hashCode() }
    // The sunburst, confetti, trophy and flourish: grown-ups on a best worth it (RunWords.CELEBRATE_FROM), kids on three stars.
    val party = RunWords.party(who.kind, e.recap, e.wrongKeys, e.newBest)
    LaunchedEffect(e) {
        if (party && sampler != null) launch { flourish(sampler) }
        if (still) return@LaunchedEffect
        launch { m.rise.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 200f)) }
        delay(260)
        if (party || (e.recap?.accuracy ?: 0) >= 85) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { m.pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
        delay(160)
        launch { m.rest.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        delay(160)
        m.count.animateTo(1f, tween(1000, easing = LinearEasing))
        if (e.xpGained != null) { m.xp.animateTo(1f, tween(1100, easing = LinearEasing)); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    val spin = if (party && !still) rememberInfiniteTransition(label = "spin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(45_000, easing = LinearEasing), RepeatMode.Restart), label = "spin") else null
    val r = e.recap
    val hardest = e.spots.firstOrNull()?.bar
    val pose = when {
        party -> Pose.LEVEL_UP
        kid -> if (stars >= 2) Pose.GREAT_JOB else Pose.PRACTICING      // never a sad pup for a kid
        r != null -> when { r.accuracy >= 85 -> Pose.GREAT_JOB; r.accuracy >= 50 -> Pose.PRACTICING; else -> Pose.MISTAKE }
        else -> when { e.wrongKeys == 0 -> Pose.GREAT_JOB; e.wrongKeys <= 5 -> Pose.PRACTICING; else -> Pose.MISTAKE }
    }
    val title = RunWords.headline(who.kind, r, e.wrongKeys, e.newBest, seed)
    val line = RunWords.coaching(who.kind, r, e.wrongKeys, hardest, speed, name = who.name, seed = seed)
    // The pup says it, name and all (made on the phone, since names are never recorded): kids every time, grown-ups on a best.
    LaunchedEffect(e) {
        if (say == null) return@LaunchedEffect
        delay(if (still) 200 else 700)
        if (kid) say("$title $line") else if (party) say("${title.trimEnd('!', '.')}, ${who.name}!")
    }
    val stickerRes = e.reward?.drops?.firstOrNull()?.sticker?.let { com.bigpapituna.sharpears.data.WorldStore.stickerRes(it) }
    val stats = buildList {
        if (r != null) { add(Stat("${r.good}", "Good")); add(Stat("${r.ok}", "OK")); add(Stat("${r.miss}", "Missed")); add(Stat("${r.longestStreak}", "Streak")) }
        else add(Stat("${e.wrongKeys}", if (e.wrongKeys == 1) "Wrong note" else "Wrong notes"))
        if (e.timeMs > 0) add(Stat(SongArt.length(e.timeMs), "Time"))
        if (speed < 1.0) add(Stat("${(speed * 100).toInt()}%", "Speed"))
    }

    Box(Modifier.fillMaxSize().paper()) {
        if (party) Confetti(e, Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Side by side on a landscape screen and on a big square one (the Fold's inner screen), stacked on a phone.
            val wide = maxWidth > maxHeight * 1.2f || (maxWidth >= 720.dp && maxHeight < maxWidth * 1.3f)
            // A short landscape screen (the Fold's cover screen turned sideways): the stars move under the pup's words, so
            // the right-hand side has room for the sticker above the way on.
            val short = wide && maxHeight < 480.dp
            val pupSize = if (short) min(maxHeight * 0.34f, 150.dp) else if (wide) min(maxHeight * 0.46f, 190.dp) else min(maxWidth * 0.5f, 200.dp)
            val head: @Composable ColumnScope.() -> Unit = {
                Box(Modifier.size(pupSize * 1.25f).sunburst(party, spin, m.rise), contentAlignment = Alignment.Center) {
                    // Held in the run's pose (as on a lesson's celebration): left alone he dozed off on the recap.
                    Pup(pose, size = pupSize, onTap = {}, idle = false, modifier = Modifier.graphicsLayer {
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
                e.duetLine?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 420.dp).graphicsLayer { alpha = m.pop.value.coerceIn(0f, 1f) })
                }
            }
            // The sticker this run dropped, flipping in once the stars have landed (and the medal, when it went up). On a
            // phone held sideways it lands below the numbers' space: once they have counted, the column glides to it.
            val dropInView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
            if (short && e.reward != null) LaunchedEffect(e) { delay(if (still) 300L else 2900L); dropInView.bringIntoView() }
            val drop: @Composable ColumnScope.() -> Unit = {
                e.reward?.let { rw ->
                    Column(Modifier.fillMaxWidth().bringIntoViewRequester(dropInView).slideIn(m.rest, 1), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.bigpapituna.sharpears.ui.world.DropReveal(rw, cardWidth = if (short) 84.dp else if (kid) 132.dp else 116.dp)
                        com.bigpapituna.sharpears.ui.world.RewardExtras(rw, songTitle = { songTitle })
                    }
                }
            }
            // The stars lead; they start landing as the headline pops (kids get them bigger, and nothing else).
            val starRow: @Composable ColumnScope.() -> Unit = {
                StarRow(stars, Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).graphicsLayer { alpha = m.pop.value.coerceIn(0f, 1f) }, size = if (kid) (if (short) 56.dp else 72.dp) else 48.dp,
                    sampler = sampler, startDelayMs = if (still) 0L else 420L)
            }
            val numbers: @Composable ColumnScope.() -> Unit = if (kid) { { if (!short) starRow(); drop() } } else { {
                if (!short) starRow()
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
                drop()
                // The quieter way on: a hard bar on a loop.
                TroubleSpots.line(e.spots)?.let { loops ->
                    Column(Modifier.slideIn(m.rest, stats.size + 2), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(loops, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            e.spots.forEach { s -> SmallPill("Loop bar ${s.bar}", R.drawable.ic_repeat) { onLoop(s) } }
                        }
                    }
                }
            } }
            // Play again, always in view (with a peek at its drop: "one more"); the run played back, sent, and Close.
            val actions: @Composable () -> Unit = {
                Column(Modifier.slideIn(m.rest, stats.size + 3), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // No room on a short screen for the peek at the next drop: Play it again is a plain button there.
                    val peek = nextDrop.takeIf { !short }
                    if (peek != null) com.bigpapituna.sharpears.ui.world.OneMoreButton(peek, onClick = onAgain, title = songTitle, label = "Play it again",
                        delayMs = if (still) 0L else 1400L)
                    FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        if (peek == null) ChunkyButton(onClick = onAgain, modifier = Modifier.widthIn(min = 180.dp)) {
                            Icon(painterResource(R.drawable.ic_repeat), contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Play again")
                        }
                        // The replay is a grown-up's screen (its clock, "your notes are the thin bright ones"): not a kid's.
                        if (e.canReplay && !kid) SmallPill("Hear my run", R.drawable.ic_play_arrow, onReplay)
                        if (songTitle != null && (run != null || e.reward != null))
                            com.bigpapituna.sharpears.ui.play.ShareConcertButton(run, songTitle, stars, stickerRes = stickerRes)
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                }
            }
            if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(0.42f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    head()
                    if (short) starRow()
                }
                Column(Modifier.weight(0.58f).fillMaxHeight().padding(vertical = 8.dp)) {
                    // The numbers scroll inside the space left over, so Play again never leaves the screen.
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), content = numbers)
                    Spacer(Modifier.height(16.dp))
                    actions()
                }
            } else Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // The pup, the stars and the drop scroll in the space left over; the way on stays pinned under them.
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                    head()
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) { numbers() }
                }
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = 12.dp)) { actions() }
            }
        }
    }
}

/**
 * Slides in as the [i]-th part of the rest (0..1 of [rest], each part a little after the one before). Every part ends
 * fully in (the old 1.6 slope left the eighth part, Play again, at 64% when the stats ran long).
 */
private fun Modifier.slideIn(rest: Animatable<Float, *>, i: Int) = graphicsLayer {
    val k = (rest.value * (1f + i * 0.12f) - i * 0.12f).coerceIn(0f, 1f)
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

