package com.bigpapituna.sharpears.ui.playground

import android.os.SystemClock
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.Answer
import com.bigpapituna.sharpears.engine.playground.Hunt
import com.bigpapituna.sharpears.engine.playground.HuntJudge
import com.bigpapituna.sharpears.engine.playground.MissWhy
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.engine.playground.huntPlaceOf
import com.bigpapituna.sharpears.engine.playground.huntPlaces
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.practice.PianoGeometry
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Cream = Color(0xFFF6EBDD)
private val HouseRoof = Color(0xFFC8693A)

/**
 * Lessons 2 and 3, the key hunts: a big keyboard (the two octaves from middle C) where the pup points
 * at a dog house (two black keys), a fence (three) or a C, and each one found lights up and stays lit: a little house
 * over the dog house, a picket fence over the fence, a badge over the C. Each round is shown first: the app plays the
 * place the pup points at while it glows. The note matcher listens for that kind of place in the two octaves around
 * middle C (StageKeys); one in another octave is named by its wrong-key search and counts too, a white key makes the pup
 * say "That one's white" while the place glows hard (HuntJudge), and any other sound gets a ring on the keys and a
 * twitch of the pup's ears. Three tries and the round goes by with one star. The keys can be tapped as well.
 */
@Composable
internal fun KeyHuntStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val found = remember { mutableStateListOf<List<Int>>() }
    var burst by remember { mutableIntStateOf(0) }
    var tiltSeq by remember { mutableIntStateOf(0) }
    var nudge by remember { mutableLongStateOf(0L) }
    val runRef = remember { arrayOfNulls<StageRun>(1) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        // Two octaves from middle C: the places the note matcher listens for (StageKeys), big enough to find on any screen.
        val low = 60
        val high = 83
        // Read live: the stage's callbacks run between frames, on the round being played.
        fun huntOf(run: StageRun) = run.rounds.current?.let { g -> Hunt.of(g.notes.map { it.midi }) }
        fun targetOf(run: StageRun): List<Int>? {
            val places = huntOf(run)?.let { huntPlaces(it, low, high) }.orEmpty()
            // The pup points at the one nearest the middle that is not found yet.
            return places.filter { it !in found }.minByOrNull { abs(it.first() - 61) } ?: places.minByOrNull { abs(it.first() - 61) }
        }
        val st = rememberStage(ex, "$lessonId-hunt", lessonId, "key_hunt", deps, inject, onEnded,
            keys = { run -> huntOf(run)?.let(StageKeys::forHunt) ?: StageKeys.CS },
            demoKeys = { run -> targetOf(run).orEmpty() },
            judge = { s, run -> huntOf(run)?.let { HuntJudge.judge(s, it) } ?: Answer.Heard },
            foundLine = { run, _ -> if (run.finished) "You found them all!" else listOf("You found it!", "That's it!", "Yes!")[(run.done - 1).coerceAtLeast(0) % 3] },
            missLine = { a, run -> when (a.why) {
                MissWhy.WHITE_KEY -> PlaygroundLines.WHITE_KEY
                MissWhy.OTHER_GROUP -> if (huntOf(run) == Hunt.DOG_HOUSE) "Look for two black keys." else "Look for three black keys."
                MissWhy.NOT_C -> "C is just left of the dog house."
                else -> null
            } },
            onFound = { key ->
                val r = runRef[0]
                val h = r?.let(::huntOf)
                val t = r?.let(::targetOf)
                val place = h?.let { huntPlaceOf(it, key) }?.takeIf { it.all { k -> k in low..high } && it !in found } ?: t
                if (place != null && place !in found) found += place
                burst++
                Log.d(TAG, "playground: found ${h?.name?.lowercase()} with ${noteName(key)}")
            },
            onMiss = { a ->
                tiltSeq++
                if (a.why == MissWhy.WHITE_KEY || a.why == MissWhy.NOT_C || a.why == MissWhy.OTHER_GROUP) nudge = SystemClock.uptimeMillis() + 2600
            },
        )
        runRef[0] = st.run
        val run = st.run
        val hunt = remember(run.version) { huntOf(run) }
        val target = remember(run.version, found.size) { targetOf(run) }
        val now by produceState(SystemClock.uptimeMillis(), nudge) { while (SystemClock.uptimeMillis() < nudge) { value = SystemClock.uptimeMillis(); delay(100) }; value = SystemClock.uptimeMillis() }
        val nudging = now < nudge
        var tiltNow by remember { mutableStateOf(false) }
        LaunchedEffect(tiltSeq) { if (tiltSeq > 0) { tiltNow = true; delay(1300); tiltNow = false } }
        val finished = run.finished
        val line = stageLine(st, when {
            finished -> "You found them all!"
            st.demoKeys.isNotEmpty() -> "Listen!"
            else -> when (hunt) {
                Hunt.DOG_HOUSE -> "Find a dog house!"
                Hunt.FENCE -> "Find a fence!"
                Hunt.C -> "Find a C!"
                null -> null
            }
        })
        val clock = rememberInfiniteTransition(label = "hunt")
        val pulse by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
        val p = if (still) 0.5f else pulse
        val measurer = rememberTextMeasurer()

        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(if (finished) Painted.GREAT_JOB else if (tiltNow) Painted.HEAD_TILT else Painted.POINTING,
                    size = (roomH * 0.3f).coerceIn(72.dp, 150.dp), poke = st.fx.poke)
                Spacer(Modifier.width(8.dp))
                StageLine(line)
            }
            val allFound = { m: Int -> found.any { m in it } }
            val demo = st.demoKeys
            BigKeys(low, high, "Piano keys from ${noteName(low)} to ${noteName(high)}. " + when (hunt) {
                    Hunt.DOG_HOUSE -> "Find a group of two black keys"; Hunt.FENCE -> "Find a group of three black keys"; Hunt.C -> "Find a C"; null -> "Every one is found"
                }, Modifier.fillMaxWidth().height((roomH * 0.52f).coerceIn(150.dp, 300.dp)),
                fill = { m -> when {
                    m in demo -> PupGold
                    allFound(m) -> Accent
                    // The place the pup points at glows gold, hard while he nudges ("try the black ones here").
                    target != null && m in target -> PupGold.copy(alpha = if (nudging) 0.6f + 0.4f * p else 0.15f + 0.3f * p)
                    else -> null
                } },
                last = run.last, topRoom = 64.dp, fx = st.fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(700); deps.sampler.noteOff(m) } }
                    st.tap(m)
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
