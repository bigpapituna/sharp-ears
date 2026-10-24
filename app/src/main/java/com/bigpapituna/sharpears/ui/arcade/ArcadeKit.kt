package com.bigpapituna.sharpears.ui.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.arcade.ArcadeRound
import com.bigpapituna.sharpears.engine.arcade.CallRound
import com.bigpapituna.sharpears.engine.arcade.RoundEnd
import com.bigpapituna.sharpears.engine.arcade.RoundResult
import com.bigpapituna.sharpears.engine.arcade.StreamRound
import com.bigpapituna.sharpears.engine.arcade.Target
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.playground.StageFx
import com.bigpapituna.sharpears.ui.playground.star
import com.bigpapituna.sharpears.ui.skin.drawAnimal
import com.bigpapituna.sharpears.ui.skin.inkOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import com.bigpapituna.sharpears.ui.skin.stripeColours
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * What every arcade game shares on screen: the round's live state for the playfields to draw ([ArcadePlay]), a key's
 * badge (its colour with its animal for a pre-reader, its letter for a reader: the arcade's one way of naming a key),
 * the round's clock as a sun crossing the sky (no numbers), and a few pieces of scenery.
 */

/** Something that just happened, kept a few seconds so a playfield can animate it. */
internal enum class FxKind { HIT, HALF, GONE, LET_GO, SLIP, LINE }

internal data class Fx(val kind: FxKind, val target: Target?, val key: Int, val at: Long, val line: Int = -1)

/**
 * A round on screen: the engine's round, the frame clock ([now], uptime ms; a playfield reads it inside its Canvas so
 * only drawing follows it), what just happened ([events]), what each target came to ([played]: true by the kid, false let
 * go or gone by), the keys lit for help ([lit]), the demo's keys and the one sounding, the pup's pose and line, and the
 * result once the round is over.
 */
@Stable
internal class ArcadePlay(val round: ArcadeRound, val reader: Boolean) {
    var now by mutableLongStateOf(0L)
    var version by mutableIntStateOf(0)
    val fx = StageFx()
    val events = mutableStateListOf<Fx>()
    val played = mutableStateMapOf<Int, Boolean>()
    var lit by mutableStateOf<Set<Int>>(emptySet())
    var demoKeys by mutableStateOf<List<Int>>(emptyList())
    var demoIndex by mutableStateOf<Int?>(null)
    var pose by mutableStateOf(Pose.LISTENING)
    var poseUntil = 0L
    var line by mutableStateOf<String?>(null)
    var lineSeq by mutableIntStateOf(0)
    var result by mutableStateOf<RoundResult?>(null)
    var end by mutableStateOf<RoundEnd?>(null)

    val stream: StreamRound? get() = round as? StreamRound
    val call: CallRound? get() = round as? CallRound

    fun add(e: Fx) {
        events += e
        val old = e.at - KEEP_MS
        events.removeAll { it.at < old }
        version++
    }

    fun show(text: String?) { line = text; lineSeq++ }

    fun react(p: Pose, ms: Long, at: Long) { pose = p; poseUntil = at + ms }

    /** The latest of [kind] for [targetId], or null. */
    fun last(kind: FxKind, targetId: Int): Fx? = events.lastOrNull { it.kind == kind && it.target?.id == targetId }

    /** 0..1 how far an effect that began at [at] and lasts [ms] has got, or null once it is over. */
    fun age(at: Long, ms: Long): Float? = ((now - at).toFloat() / ms).takeIf { it in 0f..1f }

    companion object { const val KEEP_MS = 4_000L }
}

/** A key's colour on screen (a black key: the blend of its two neighbours, used only where stripes cannot go). */
internal fun keyColour(midi: Int): Color = noteColour(midi)

/**
 * A key's badge at [c], [r] its radius: a disc in its colour (a black key in its neighbours' stripes) with a white rim, and
 * the key's animal on it for a pre-reader, its letter for a reader. [glow] when it is the one to play now.
 */
internal fun DrawScope.keyBadge(midi: Int, c: Offset, r: Float, reader: Boolean, measurer: TextMeasurer, glow: Boolean = false, alpha: Float = 1f) {
    val colour = keyColour(midi)
    if (glow) drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.6f * alpha), Color.Transparent), c, r * 2f), r * 2f, c)
    drawCircle(Color.Black.copy(alpha = 0.25f * alpha), r * 1.08f, c + Offset(0f, r * 0.08f))
    val stripes = stripeColours(midi)
    if (stripes != null) {
        drawCircle(stripes.first.copy(alpha = alpha), r, c)
        drawArc(stripes.second.copy(alpha = alpha), -45f, 180f, true, c - Offset(r, r), Size(r * 2, r * 2))
    } else drawCircle(colour.copy(alpha = alpha), r, c)
    drawCircle(Color.White.copy(alpha = 0.9f * alpha), r, c, style = Stroke(r * 0.12f))
    val ink = inkOn(midi).copy(alpha = alpha)
    val animal = NoteColours.animal(midi)
    if (!reader && animal != null) drawAnimal(animal, c, r * 1.25f, ink, colour.copy(alpha = alpha))
    else {
        val t = measurer.measure(NoteColours.letter(midi), TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
            fontSize = (r * 1.05f / density).sp, color = ink))
        drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
    }
}

/** A row of a line's badges centred in [width] at [y]: [done] of them played (small, dimmed), the next one bouncing. */
internal fun DrawScope.badgeRow(keys: List<Int>, done: Int, y: Float, r: Float, reader: Boolean, measurer: TextMeasurer, now: Long,
                                 sounding: Int? = null, lit: Set<Int> = emptySet()) {
    if (keys.isEmpty()) return
    val gap = minOf(r * 2.6f, size.width * 0.92f / keys.size)
    val rr = minOf(r, gap * 0.42f)
    val x0 = size.width / 2 - gap * (keys.size - 1) / 2
    keys.forEachIndexed { i, k ->
        val next = i == done
        val bounce = if (next) (sin(now / 160.0) * rr * 0.18f).toFloat() else 0f
        val c = Offset(x0 + gap * i, y - bounce)
        val alpha = if (i < done) 0.4f else 1f
        val scale = when { sounding == i -> 1.25f; next -> 1.12f; else -> 1f }
        keyBadge(k, c, rr * scale, reader, measurer, glow = sounding == i || (next && k in lit), alpha = alpha)
        if (i < done) drawPath(check(c + Offset(rr * 0.7f, -rr * 0.7f), rr * 0.45f), PupGold)
    }
}

/** A little tick mark at [c]. */
internal fun check(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x - r, c.y); lineTo(c.x - r * 0.3f, c.y + r * 0.7f); lineTo(c.x + r, c.y - r * 0.8f)
    lineTo(c.x + r * 0.8f, c.y - r); lineTo(c.x - r * 0.3f, c.y + r * 0.35f); lineTo(c.x - r * 0.8f, c.y - r * 0.2f); close()
}

/**
 * The round's clock: a sun crossing a thin arc from one side to the other as the round goes ([progress] 0..1); no
 * numbers. It sets (and the sky by it turns gold) near the end.
 */
@Composable
internal fun RoundClock(progress: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "How much of the game is left" }) {
        val p = progress().coerceIn(0f, 1f)
        val h = size.height; val w = size.width
        val track = Path().apply { moveTo(h / 2, h * 0.75f); quadraticTo(w / 2, -h * 0.15f, w - h / 2, h * 0.75f) }
        drawPath(track, Color.White.copy(alpha = 0.22f), style = Stroke(h * 0.12f))
        // Where the sun is on the arc (a quadratic: B(t) = (1-t)^2 P0 + 2(1-t)t P1 + t^2 P2).
        val t = p; val u = 1 - t
        val x = u * u * (h / 2) + 2 * u * t * (w / 2) + t * t * (w - h / 2)
        val y = u * u * (h * 0.75f) + 2 * u * t * (-h * 0.15f) + t * t * (h * 0.75f)
        val sun = if (p > 0.85f) Color(0xFFFF9E57) else PupGold
        for (k in 0 until 8) {
            val a = 2 * PI * k / 8 + p * 4
            drawLine(sun.copy(alpha = 0.8f), Offset(x + (cos(a) * h * 0.32f).toFloat(), y + (sin(a) * h * 0.32f).toFloat()),
                Offset(x + (cos(a) * h * 0.46f).toFloat(), y + (sin(a) * h * 0.46f).toFloat()), h * 0.07f)
        }
        drawCircle(sun, h * 0.24f, Offset(x, y))
    }
}

/** A sky from [top] to [bottom] colours over the whole field. */
internal fun DrawScope.sky(top: Color, bottom: Color) = drawRect(Brush.verticalGradient(listOf(top, bottom)))

/** A soft cloud at [c], [r] wide. */
internal fun DrawScope.cloud(c: Offset, r: Float, colour: Color = Color.White.copy(alpha = 0.85f)) {
    drawCircle(colour, r * 0.5f, c)
    drawCircle(colour, r * 0.38f, c + Offset(-r * 0.5f, r * 0.12f))
    drawCircle(colour, r * 0.42f, c + Offset(r * 0.48f, r * 0.08f))
    drawRoundRect(colour, Offset(c.x - r * 0.85f, c.y), Size(r * 1.7f, r * 0.42f), CornerRadius(r * 0.21f))
}

/** Grass from [y] down, with a lighter top edge. */
internal fun DrawScope.grass(y: Float, light: Color = Color(0xFF8CCB6E), dark: Color = Color(0xFF5DA153)) {
    drawRect(Brush.verticalGradient(listOf(light, dark), y, size.height), Offset(0f, y), Size(size.width, size.height - y))
    var x = 0f
    val blade = size.width / 40
    while (x < size.width) {
        drawLine(light, Offset(x, y + 1), Offset(x + blade * 0.3f, y - blade * 0.7f), blade * 0.25f)
        x += blade
    }
}

/** Little sparkles round [c] for a moment ([p] 0..1): a hit's flourish. */
internal fun DrawScope.sparkles(c: Offset, r: Float, p: Float, colour: Color = PupGold) {
    for (k in 0 until 6) {
        val a = 2 * PI * k / 6 + p
        val d = r * (0.6f + 1.2f * p)
        drawPath(star(c + Offset((cos(a) * d).toFloat(), (sin(a) * d).toFloat()), r * 0.22f * (1 - p * 0.5f)), colour.copy(alpha = 1f - p))
    }
}

/** Whether a key is drawn as a black key (moles only: the arcade's other games use white keys). */
internal fun blackKey(midi: Int) = isBlack(midi)
