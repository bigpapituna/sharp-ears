package com.bigpapituna.sharpears.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.Comeback
import com.bigpapituna.sharpears.engine.RetentionLines
import com.bigpapituna.sharpears.engine.world.GiftKind
import com.bigpapituna.sharpears.engine.world.GiftOpened
import com.bigpapituna.sharpears.engine.world.Surprise
import com.bigpapituna.sharpears.ui.family.drawPaw
import com.bigpapituna.sharpears.ui.profile.LocalProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.engine.pup.PupMood

/*
 * The 2.9 retention pieces on screen: Tomorrow's surprise (the wrapped gift on a kid's finish screen once the day's goal
 * is met, and the ready gift on the kid home the next day), the grown-ups' "Tomorrow:" line on their finish screens, the
 * comeback cards (the kid's over the map, the grown-up's on Today) and the rest-day paw. No countdown anywhere: a gift is
 * "Something for tomorrow" until the next calendar day, then it is ready, whatever the hour.
 */

/**
 * What a finish screen needs for the "tomorrow" slot, provided once by MainActivity for the open profile: [kid] (a gift)
 * or a grown-up (a line), whether today's goal is met, tomorrow's next thing, and the gift's two moves.
 */
class Retention(
    val kid: Boolean,
    /** Today's goal reached, read when asked (the progress store). */
    val goalMet: () -> Boolean,
    /** Grown-ups: "Tomorrow: the left hand of Twinkle" from tomorrow's plan, or null. Called off the main thread. */
    val tomorrow: () -> String?,
    /** Kids: wraps today's gift when the rules allow (engine/world Surprise); true when it was wrapped just now. */
    val wrap: () -> Boolean,
    /** Kids: a gift wrapped today is waiting (every finish screen of the day shows it). */
    val wrappedToday: () -> Boolean,
    /** The pup's voice. */
    val say: (String) -> Unit,
)

val LocalRetention = staticCompositionLocalOf<Retention?> { null }

/**
 * The "tomorrow" slot of a finish screen: once today's goal is met, a kid sees the wrapped gift ("Something for tomorrow")
 * and a grown-up the line naming tomorrow's next thing. Nothing before the goal; nothing without [LocalRetention]. It
 * checks again whenever the progress store changes (a lesson's last step writes its time a moment after the finish).
 */
@Composable
fun TomorrowSlot(modifier: Modifier = Modifier, onDark: Boolean = false) {
    val r = LocalRetention.current ?: return
    val progress = LocalProgress.current
    val v = progress?.version?.intValue ?: 0
    var gift by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<String?>(null) }
    val say by rememberUpdatedState(r.say)
    LaunchedEffect(v) {
        if (!r.goalMet()) return@LaunchedEffect
        if (r.kid) {
            val fresh = r.wrap()
            if (fresh || r.wrappedToday()) {
                if (!gift && fresh) { delay(2600); say(RetentionLines.WRAPPED) }
                gift = true
            }
        } else if (line == null) line = withContext(Dispatchers.IO) { runCatching { r.tomorrow() }.getOrNull() }
    }
    if (r.kid) { if (gift) WrappedGift(modifier) }
    else line?.let { l ->
        Text(l, style = MaterialTheme.typography.titleMedium, color = if (onDark) PupGold else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth().semantics { contentDescription = l })
    }
}

/** The wrapped gift on a kid's finish screen: a present that sways a little, "Something for tomorrow". */
@Composable
fun WrappedGift(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)
        .border(2.dp, PupGold.copy(alpha = 0.7f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp)
        .semantics(mergeDescendants = true) { contentDescription = Surprise.WRAPPED_TITLE + ". " + Surprise.WRAPPED_LINE },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GiftBox(Modifier.size(64.dp), sway = true)
        Column(Modifier.weight(1f)) {
            Text(Surprise.WRAPPED_TITLE, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            Text(Surprise.WRAPPED_LINE, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}

/** The ready gift on the kid home (the next day or later): it bounces; a tap opens it. */
@Composable
fun ReadyGift(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surfaceContainerHigh)
        .border(2.dp, PupGold, RoundedCornerShape(22.dp))
        .clickable(role = Role.Button, onClickLabel = "Open the surprise", onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp)
        .semantics(mergeDescendants = true) { contentDescription = Surprise.READY_TITLE },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GiftBox(Modifier.size(72.dp), bounce = true)
        Column(Modifier.weight(1f)) {
            Text(Surprise.READY_TITLE, style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Text(Surprise.READY_LINE, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}

/**
 * The surprise opening (the kid home): the lid pops off with sparkles, then what was inside: a sticker card flipping in
 * (the drop reveal), something to wear (the pup puts it on and asks "Keep it on?"), or a pile of sparkles with how close
 * the next outfit is now. [onClose] after "Yay!".
 */
@Composable
fun GiftOpening(opened: GiftOpened, reader: Boolean, say: (String) -> Unit, onClose: () -> Unit) {
    val still = reducedMotion()
    val open = remember(opened) { Animatable(if (still) 1f else 0f) }
    var shown by remember(opened) { mutableStateOf(still) }
    val sayNow by rememberUpdatedState(say)
    LaunchedEffect(opened) {
        if (!still) { delay(350); open.animateTo(1f, tween(900)) }
        shown = true
        com.bigpapituna.sharpears.ui.world.WorldSounds.pop()
        // Something to wear says its own line as the pup puts it on ("Something new to wear! Keep it on?").
        if (opened.kind != GiftKind.OUTFIT) sayNow(Surprise.line(opened.kind))
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        // Edge to edge over the bars: their icons follow the page (dark icons on the light paper).
        val view = androidx.compose.ui.platform.LocalView.current
        val lightPage = MaterialTheme.colorScheme.background.luminance() > 0.5f
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                androidx.core.view.WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = lightPage; isAppearanceLightNavigationBars = lightPage }
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // What was inside scrolls in the space left; "Yay!" stays in reach at the bottom.
                Column(Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    Box(Modifier.size(width = 180.dp, height = if (shown) 170.dp else 240.dp), contentAlignment = Alignment.BottomCenter) {
                        GiftBox(Modifier.fillMaxSize(), open = open.value)
                        if (open.value in 0.2f..0.99f) Canvas(Modifier.fillMaxSize()) { sparkles(open.value) }
                    }
                    if (shown) {
                        Text(Surprise.line(opened.kind), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                        // 2.10: the pup jumps for joy (the jump clip where the app has it); an outfit has its own pup putting it on.
                        if (opened.kind != GiftKind.OUTFIT) Pup(Pose.GREAT_JOB, PupMood.HAPPY, size = 120.dp, idle = false, clips = true)
                        when (opened.kind) {
                            GiftKind.STICKER -> com.bigpapituna.sharpears.ui.world.DropReveal(opened.reward, cardWidth = 140.dp)
                            GiftKind.OUTFIT -> com.bigpapituna.sharpears.ui.world.RewardExtras(opened.reward, say = say)
                            GiftKind.SPARKLES -> {
                                Canvas(Modifier.size(96.dp)) { sparklePile() }
                                com.bigpapituna.sharpears.ui.world.NextOutfitMeter(opened.state.nextOutfit, reader)
                                if (opened.reward.unlocked.isNotEmpty()) com.bigpapituna.sharpears.ui.world.RewardExtras(opened.reward, say = say)
                            }
                        }
                    }
                }
                if (shown) ChunkyButton(onClick = onClose, modifier = Modifier.padding(top = 12.dp).widthIn(min = 220.dp)) { Text("Yay!") }
            }
        }
    }
}

/**
 * The kid's comeback card over the map (a return after three days or more): the pup, glad, "You're back!", and one stone
 * they know to play for stars ([stone], its title) or, with none, the next one. Shown once per return.
 */
@Composable
fun KidComebackCard(stone: String?, onPlay: () -> Unit, onLater: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onLater) {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainerHigh).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Pup(Pose.DANCING, PupMood.HAPPY, size = 150.dp, idle = true)
            Text(Comeback.KID_TITLE, style = MaterialTheme.typography.headlineMedium, color = c.primary, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
            Text(if (stone != null) Comeback.KID_LINE else Comeback.KID_LINE_NEXT, style = MaterialTheme.typography.titleMedium,
                color = c.onSurface, textAlign = TextAlign.Center)
            stone?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center) }
            ChunkyButton(onClick = onPlay, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(6.dp))
                Text("Play")
            }
            TextButton(onClick = onLater) { Text("Not now") }
        }
    }
}

/**
 * The grown-up's "Welcome back" on Today (a return after three days or more): three easy minutes with the last clean song
 * at a slower tempo, or the last clean lesson again; nothing new. [line] from engine/Comeback.grownUpLine.
 */
@Composable
fun WelcomeBackCard(line: String, onStart: () -> Unit, onLater: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).border(1.5.dp, c.primary.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The pup's greeting line just above already says hello: the card is the words and the way in.
        Text(Comeback.GROWN_UP_TITLE, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(line, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLater) { Text("Not now") }
            Spacer(Modifier.size(4.dp))
            ChunkyButton(onClick = onStart) {
                Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
                Text("Start")
            }
        }
    }
}

/**
 * A present: a coral box with a gold ribbon and bow. [open] 0..1 lifts the lid off and tips it away; [sway] rocks it a
 * little (wrapped, waiting); [bounce] hops it now and then (ready to open). Still with animations off.
 */
@Composable
fun GiftBox(modifier: Modifier = Modifier, open: Float = 0f, sway: Boolean = false, bounce: Boolean = false) {
    val still = reducedMotion()
    val t = if (still || (!sway && !bounce)) null else rememberInfiniteTransition(label = "gift")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(if (bounce) 1600 else 2400, easing = LinearEasing), RepeatMode.Restart), label = "gift")
    Canvas(modifier.graphicsLayer {
        val p = t?.value ?: 0f
        if (sway) rotationZ = 5f * sin(2 * PI * p).toFloat()
        if (bounce) { val hop = if (p < 0.25f) sin(PI * p / 0.25).toFloat() else 0f; translationY = -size.height * 0.12f * hop }
    }) { drawGift(open) }
}

private val GiftRed = Color(0xFFE2664F)
private val GiftDeep = Color(0xFFB8432F)

private fun DrawScope.drawGift(open: Float) {
    // Scaled by the narrower side and sat on the bottom, so a tall canvas leaves room above for the lid to fly off.
    val u = minOf(size.width, size.height)
    val cx = size.width / 2
    val bodyTop = size.height - u * 0.58f
    val line = u * 0.03f
    fun x(f: Float) = cx + u * (f - 0.5f)
    // Inside, once the lid lifts: a glow behind the box.
    if (open > 0f) drawCircle(PupGold.copy(alpha = 0.4f * open), radius = u * 0.36f * open, center = Offset(cx, bodyTop))
    // The box.
    drawRoundRect(GiftRed, Offset(x(0.14f), bodyTop), Size(u * 0.72f, u * 0.52f), CornerRadius(u * 0.05f))
    drawRoundRect(GiftDeep, Offset(x(0.14f), bodyTop), Size(u * 0.72f, u * 0.52f), CornerRadius(u * 0.05f), style = Stroke(line))
    drawRect(PupGold, Offset(x(0.45f), bodyTop), Size(u * 0.10f, u * 0.52f))
    // The lid and bow, lifted and tipped as it opens.
    val lift = u * 0.5f * open
    rotate(degrees = -24f * open, pivot = Offset(x(0.2f), bodyTop - lift)) {
        val lidTop = bodyTop - u * 0.12f - lift
        drawRoundRect(GiftRed, Offset(x(0.08f), lidTop), Size(u * 0.84f, u * 0.14f), CornerRadius(u * 0.04f))
        drawRoundRect(GiftDeep, Offset(x(0.08f), lidTop), Size(u * 0.84f, u * 0.14f), CornerRadius(u * 0.04f), style = Stroke(line))
        drawRect(PupGold, Offset(x(0.45f), lidTop), Size(u * 0.10f, u * 0.14f))
        // The bow: two loops and a knot.
        val knot = Offset(cx, lidTop)
        drawOval(PupGold, Offset(knot.x - u * 0.24f, knot.y - u * 0.15f), Size(u * 0.22f, u * 0.15f))
        drawOval(PupGold, Offset(knot.x + u * 0.02f, knot.y - u * 0.15f), Size(u * 0.22f, u * 0.15f))
        drawOval(GiftDeep.copy(alpha = 0.5f), Offset(knot.x - u * 0.18f, knot.y - u * 0.11f), Size(u * 0.11f, u * 0.07f), style = Stroke(line))
        drawOval(GiftDeep.copy(alpha = 0.5f), Offset(knot.x + u * 0.07f, knot.y - u * 0.11f), Size(u * 0.11f, u * 0.07f), style = Stroke(line))
        drawCircle(PupGold, radius = u * 0.06f, center = knot)
    }
}

/** Sparkles flying out of the box as it opens ([p] 0..1). */
private fun DrawScope.sparkles(p: Float) {
    val c = Offset(size.width / 2, size.height - minOf(size.width, size.height) * 0.58f)
    for (i in 0 until 12) {
        val a = 2 * PI * i / 12 - PI / 2
        val r = size.width * (0.15f + 0.45f * p)
        val at = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat() * 0.8f)
        drawStar(at, size.width * 0.035f * (1f - p * 0.5f), if (i % 2 == 0) PupGold else GiftRed, alpha = 1f - p)
    }
}

/** A pile of sparkles: gold stars in a heap. */
private fun DrawScope.sparklePile() {
    val w = size.width
    val spots = listOf(0.5f to 0.25f, 0.32f to 0.45f, 0.68f to 0.45f, 0.2f to 0.7f, 0.5f to 0.62f, 0.8f to 0.7f, 0.36f to 0.85f, 0.64f to 0.85f)
    spots.forEachIndexed { i, (x, y) -> drawStar(Offset(w * x, w * y), w * (if (i == 0) 0.12f else 0.09f), PupGold) }
}

private fun DrawScope.drawStar(c: Offset, r: Float, colour: Color, alpha: Float = 1f) {
    val path = androidx.compose.ui.graphics.Path()
    for (k in 0 until 10) {
        val rr = if (k % 2 == 0) r else r * 0.42f
        val a = -PI / 2 + k * PI / 5
        val x = c.x + (rr * cos(a)).toFloat(); val y = c.y + (rr * sin(a)).toFloat()
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, colour.copy(alpha = alpha.coerceIn(0f, 1f)))
}

/**
 * The rest-day paw (2.9): a missed day the streak kept. The paw drawn soft (a light fill and an outline) with a small
 * crescent moon over it: the pup's day off, never a gap.
 */
fun DrawScope.drawRestPaw(colour: Color) {
    val w = size.width
    // The paw a little smaller, bottom left, so the moon has its own corner.
    scale(0.74f, pivot = Offset(0f, size.height)) {
        drawPaw(colour.copy(alpha = 0.28f), filled = true)
        drawPaw(colour, filled = false)
    }
    // The moon, top right: a crescent (a disc less an offset disc).
    val m = Offset(w * 0.78f, w * 0.22f)
    val disc = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(m, w * 0.2f)) }
    val bite = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(m.x + w * 0.1f, m.y - w * 0.07f), w * 0.17f)) }
    drawPath(androidx.compose.ui.graphics.Path().apply { op(disc, bite, androidx.compose.ui.graphics.PathOperation.Difference) }, colour)
}

/** The rest day's words, for the strips' TalkBack and legends. */
const val REST_DAY = "Rest day"
