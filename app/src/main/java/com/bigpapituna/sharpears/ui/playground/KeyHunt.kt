package com.bigpapituna.sharpears.ui.playground

import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.Heard
import com.bigpapituna.sharpears.engine.playground.Hunt
import com.bigpapituna.sharpears.engine.playground.PitchHeight
import com.bigpapituna.sharpears.engine.playground.huntPlaceOf
import com.bigpapituna.sharpears.engine.playground.huntPlaces
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.practice.PianoGeometry
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Cream = Color(0xFFF6EBDD)
private val HouseRoof = Color(0xFFC8693A)

/**
 * Lessons 2 and 3, the key hunts: a big keyboard (two octaves on a phone, three when there is room) where the pup points
 * at a dog house (two black keys), a fence (three) or a C, and each one found lights up and stays lit: a little house
 * over the dog house, a picket fence over the fence, a badge over the C. Any octave counts on the piano; the one found
 * on screen is the one played if it is shown, else the one pointed at. The pitch tracker names the key struck, so a
 * key's overtones never pass for another key; the keys can be tapped as well.
 */
@Composable
internal fun KeyHuntStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    var listening by remember { mutableStateOf(false) }
    val run = rememberPlaygroundRun(ex, "$lessonId-hunt", deps, sound = { deps.prefs.sound && (!listening || deps.mic.headphonesOn()) }, onEnded)
    val pitch = remember(run) { PitchHeight() }
    val heard = remember(run) { MutableSharedFlow<Heard>(extraBufferCapacity = 16) }
    listening = rememberListening(deps.mic, deps.voice, run.started && !run.ended, inject,
        raw = { s, n -> pitch.process(s, n).forEach { heard.tryEmit(it) } })
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val found = remember(run) { mutableStateListOf<List<Int>>() }
    var burst by remember { mutableStateOf(0) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        val low = 48
        val high = if (maxWidth >= 560.dp) 83 else 71
        val next = remember(run.version) { run.transport.nextGroup }
        val hunt = next?.let { g -> Hunt.of(g.notes.map { it.midi }) }
        val places = hunt?.let { huntPlaces(it, low, high) }.orEmpty()
        // The pup points at the one nearest the middle that is not found yet.
        val target = places.filter { it !in found }.minByOrNull { abs(it.first() - 61) } ?: places.minByOrNull { abs(it.first() - 61) }

        fun play(midi: Int) {
            val h = hunt; val t = target
            val r = run.key(midi)
            if (r == InputResult.GROUP_DONE && h != null) {
                val place = huntPlaceOf(h, midi)?.takeIf { it.all { k -> k in low..high } && it !in found } ?: t
                if (place != null && place !in found) found += place
                burst++
                Log.d(TAG, "playground: found ${h.name.lowercase()} with ${noteName(midi)}")
            }
        }
        // The collector outlives this composition: it must call the newest play, which knows the round now.
        val onHeard by rememberUpdatedState { h: Heard -> play(h.key) }
        LaunchedEffect(heard) { heard.collect { h -> onHeard(h) } }

        val last = run.last
        val line = rememberFleeting(last?.seq, when (last?.result) {
            InputResult.GROUP_DONE -> listOf("You found it!", "That's it!", "Yes!")[last.seq % 3]
            InputResult.WRONG -> when (hunt) {
                Hunt.DOG_HOUSE -> "Look for two black keys."
                Hunt.FENCE -> "Look for three black keys."
                Hunt.C -> "C is just left of the dog house."
                null -> null
            }
            else -> null
        }, ms = 2000) ?: when (hunt) {
            Hunt.DOG_HOUSE -> "Find a dog house!"
            Hunt.FENCE -> "Find a fence!"
            Hunt.C -> "Find a C!"
            null -> if (run.ended || run.done == run.rounds) "You found them all!" else null
        }
        val clock = rememberInfiniteTransition(label = "hunt")
        val pulse by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
        val p = if (still) 0.5f else pulse
        val measurer = rememberTextMeasurer()
        val finished = run.ended || (run.rounds > 0 && run.done == run.rounds)

        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.rounds, run.done)
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(if (finished) Painted.GREAT_JOB else if (last?.result == InputResult.WRONG) Painted.HEAD_TILT else Painted.POINTING,
                    size = (roomH * 0.3f).coerceIn(72.dp, 150.dp))
                Spacer(Modifier.width(8.dp))
                StageLine(line)
            }
            val allFound = { m: Int -> found.any { m in it } }
            BigKeys(low, high, "Piano keys from C3 to ${noteName(high)}. " + when (hunt) {
                    Hunt.DOG_HOUSE -> "Find a group of two black keys"; Hunt.FENCE -> "Find a group of three black keys"; Hunt.C -> "Find a C"; null -> "Every one is found"
                }, Modifier.fillMaxWidth().height((roomH * 0.52f).coerceIn(150.dp, 300.dp)),
                fill = { m -> when { allFound(m) -> Accent; target != null && m in target -> Color.White.copy(alpha = 0.12f + 0.3f * p); else -> null } },
                last = last, topRoom = 64.dp,
                onKey = { m ->
                    if (deps.prefs.sound && (!listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(700); deps.sampler.noteOff(m) } }
                    play(m)
                },
                overlay = { g ->
                    for (place in found) decoration(g, place, measurer)
                    if (target != null && !finished) pointer(g, target, p)
                })
        }
        if (burst > 0) Confetti(burst, Modifier.fillMaxSize())
    }
}

/** Where the decorations sit: in the room above the keys, centred over [keys]. */
private fun span(g: PianoGeometry, keys: List<Int>) = keys.map { g.rect(it) }.let { rs -> rs.minOf { it.left } to rs.maxOf { it.right } }

/** What a found place wears: a house over a dog house, a fence over a fence, a badge over a C. */
private fun DrawScope.decoration(g: PianoGeometry, keys: List<Int>, measurer: androidx.compose.ui.text.TextMeasurer) {
    val (l, r) = span(g, keys)
    val base = g.top - 6.dp.toPx()
    val cx = (l + r) / 2
    when (keys.size) {
        2 -> {                                                         // the dog house
            val w = (r - l) + g.ww * 0.5f
            val h = 22.dp.toPx()
            drawRoundRect(Cream, Offset(cx - w / 2, base - h), Size(w, h), CornerRadius(3.dp.toPx()))
            val roof = Path().apply { moveTo(cx - w / 2 - 6.dp.toPx(), base - h); lineTo(cx, base - h - 20.dp.toPx()); lineTo(cx + w / 2 + 6.dp.toPx(), base - h); close() }
            drawPath(roof, HouseRoof)
            drawRoundRect(Color(0xFF3A2A20), Offset(cx - 6.dp.toPx(), base - 15.dp.toPx()), Size(12.dp.toPx(), 15.dp.toPx()), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
        }
        3 -> {                                                         // the fence
            val pw = g.bw * 0.7f
            for (k in keys) {
                val x = g.rect(k).center.x
                val top = base - 30.dp.toPx()
                val picket = Path().apply {
                    moveTo(x - pw / 2, base); lineTo(x - pw / 2, top + pw * 0.6f); lineTo(x, top); lineTo(x + pw / 2, top + pw * 0.6f); lineTo(x + pw / 2, base); close()
                }
                drawPath(picket, Cream)
            }
            for (y in listOf(base - 8.dp.toPx(), base - 19.dp.toPx())) drawRoundRect(Color(0xFFD9C7B0), Offset(l - 4.dp.toPx(), y - 2.dp.toPx()),
                Size(r - l + 8.dp.toPx(), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
        else -> {                                                      // a C: a badge with its letter
            val c = Offset(cx, base - 16.dp.toPx())
            drawCircle(Cream, 14.dp.toPx(), c)
            drawCircle(Accent, 14.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
            val t = measurer.measure("C", TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF3A2A20)))
            drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
        }
    }
}

/** A bouncing arrow over the place the pup points at. */
private fun DrawScope.pointer(g: PianoGeometry, keys: List<Int>, p: Float) {
    val (l, r) = span(g, keys)
    val cx = (l + r) / 2
    val tip = g.top - 8.dp.toPx() - 10.dp.toPx() * p
    val s = 12.dp.toPx()
    val arrow = Path().apply { moveTo(cx, tip); lineTo(cx - s, tip - s); lineTo(cx - s * 0.4f, tip - s); lineTo(cx - s * 0.4f, tip - s * 2.2f)
        lineTo(cx + s * 0.4f, tip - s * 2.2f); lineTo(cx + s * 0.4f, tip - s); lineTo(cx + s, tip - s); close() }
    drawCircle(Brush.radialGradient(listOf(Accent.copy(alpha = 0.4f), Color.Transparent), Offset(cx, tip - s), s * 2.4f), s * 2.4f, Offset(cx, tip - s))
    drawPath(arrow, Accent)
    drawPath(arrow, Ink.copy(alpha = 0.9f), style = Stroke(1.5.dp.toPx()))
}
