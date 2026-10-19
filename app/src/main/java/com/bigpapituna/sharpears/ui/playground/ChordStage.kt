package com.bigpapituna.sharpears.ui.playground

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.playground.ChordJudge
import com.bigpapituna.sharpears.engine.playground.PageLines
import com.bigpapituna.sharpears.engine.playground.Place
import com.bigpapituna.sharpears.engine.playground.pageOf
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.skin.colourNotesOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The keys the chord stage shows: A3 to C5, white at both ends. */
private const val LOW = 57
private const val HIGH = 72

/**
 * 2.2, the pond's first chord (lesson 9): C, E and G glow on a big keyboard by the pond; the pup plays them together
 * first, then waits on his lily pad. The note matcher listens for the chord as one group (strict keys: a key outside it
 * never passes for one of its notes), every chord key comes through the ear as its own sound, and each one heard lights
 * in its colour. When all three ring together the pup jumps down and settles on them like a cushion. Two out of three,
 * left too long: "All three together!". Another key: the pup tilts his head, "C, E and G, all together." Three tries and
 * the round goes by with a small star. The keys can be tapped too (three taps make the chord).
 */
@Composable
internal fun ChordStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val chord = StageKeys.C_CHORD
    val judge = remember { ChordJudge(chord) }
    var lit by remember { mutableStateOf(emptySet<Int>()) }
    var sitSeq by remember { mutableIntStateOf(0) }
    var tiltSeq by remember { mutableIntStateOf(0) }
    val st = rememberStage(ex, "$lessonId-chord", lessonId, "chord", deps, inject, onEnded,
        keys = { chord },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, _ -> judge.judge(s).also { lit = judge.lit } },
        foundLine = { run, _ -> if (run.finished) PageLines.FIRST_CHORD else PageLines.CHORD_FOUND[(run.done - 1).coerceAtLeast(0) % 3] },
        missLine = { _, _ -> PageLines.CHORD_KEYS },
        velocity = { 84 },
        onFound = { sitSeq++; lit = emptySet(); Log.d(TAG, "playground: the chord rang together") },
        onMiss = { tiltSeq++ },
        onLetGo = { judge.reset(); lit = emptySet() },
        demoGapMs = 30,
        groups = { keys -> listOf(keys) },
        chords = true,
    )
    val run = st.run
    // Some of the chord, and the rest never came: say so once a round.
    LaunchedEffect(run) {
        var saidFor = -1
        while (!run.ended) {
            delay(200)
            val now = SystemClock.uptimeMillis()
            if (judge.partialSince(now) != null) {
                if (saidFor != run.done && run.phase == RoundCoach.Phase.LISTEN) { saidFor = run.done; st.call(PageLines.ALL_THREE) }
                judge.forget(now); lit = judge.lit
            }
        }
    }
    val sit = remember { Animatable(0f) }
    var sitting by remember { mutableStateOf(false) }
    LaunchedEffect(sitSeq) {
        if (sitSeq == 0) return@LaunchedEffect
        sitting = true
        if (still) sit.snapTo(1f) else { sit.snapTo(0f); sit.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 220f)) }
        delay(1_800)
        sitting = false
        sit.snapTo(0f)
    }
    var tiltNow by remember { mutableStateOf(false) }
    LaunchedEffect(tiltSeq) { if (tiltSeq > 0) { tiltNow = true; delay(1300); tiltNow = false } }
    val demo = st.demoKeys
    val finished = run.finished
    val line = stageLine(st, when {
        finished -> PageLines.FIRST_CHORD
        demo.isNotEmpty() -> "Listen!"
        run.phase == RoundCoach.Phase.LISTEN -> "C, E and G, all at once!"
        else -> null
    })
    val clock = rememberInfiniteTransition(label = "chord")
    val drift by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "drift")
    val pulse by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val p = if (still) 0.5f else pulse
    val colours = colourNotesOn()
    val reader = com.bigpapituna.sharpears.ui.family.LocalProfile.current.reader
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    // Where the chord sits on the keyboard, as a share of its width: the middle of C4 to G4's white keys.
    val whites = remember { (LOW..HIGH).filter { !isBlack(it) } }
    val chordAt = remember { (whites.indexOf(60) + whites.indexOf(67) + 1) / 2f / whites.size }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        val pupSize = (roomH * 0.3f).coerceIn(80.dp, 150.dp)
        val keysH = (roomH * 0.38f).coerceIn(120.dp, 220.dp)
        // The pond's water (2.3: on another page, its own backdrop, Scenery.kt).
        Canvas(Modifier.fillMaxSize()) { placeBackdrop(pageOf(lessonId)?.place?.takeIf { it != Place.GARDEN } ?: Place.POND, if (still) 0.3f else drift, 0.75f) }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(pupSize * 1.3f), contentAlignment = Alignment.BottomCenter) {
                    Canvas(Modifier.width(pupSize * 1.3f).height(pupSize * 0.5f).offset(y = pupSize * 0.12f)) {
                        lilyPad(Offset(size.width / 2, size.height / 2), size.width * 0.48f)
                    }
                    if (!sitting) PaintedPup(when { finished -> Painted.GREAT_JOB; tiltNow -> Painted.HEAD_TILT; demo.isNotEmpty() -> Painted.POINTING
                        else -> Painted.LISTENING }, size = pupSize, poke = st.fx.poke)
                }
                Spacer(Modifier.width(8.dp))
                StageLine(line)
            }
            Box(Modifier.fillMaxWidth().height(keysH + pupSize * 0.5f)) {
                BigKeys(LOW, HIGH, "Piano keys from A3 to C5. C, E and G glow: play them together.",
                    Modifier.fillMaxWidth().height(keysH + pupSize * 0.5f), topRoom = pupSize * 0.5f,
                    fill = { m -> when {
                        m in demo -> PupGold
                        m in lit -> if (colours) noteColour(m) else Accent
                        m in chord -> PupGold.copy(alpha = 0.18f + 0.3f * p)
                        else -> null
                    } },
                    last = run.last, fx = st.fx,
                    onKey = { m ->
                        if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 84); scope.launch { delay(900); deps.sampler.noteOff(m) } }
                        st.tap(m)
                    },
                    // C, E and G wear their badges at the front of the keys: the letter, or the animal before reading.
                    overlay = { g -> for (m in chord) g.rect(m).let { r -> keyBadge(m, Offset(r.center.x, r.bottom - r.width * 0.55f), r.width * 0.34f, reader, colours, measurer) } })
                // The pup drops onto the chord and settles there like on a cushion.
                if (sitting) BoxWithConstraints(Modifier.fillMaxSize()) {
                    val x = maxWidth * chordAt - pupSize * 0.55f
                    PaintedPup(Painted.RESTING, Modifier.offset { IntOffset(x.roundToPx(), ((1f - sit.value) * -pupSize.toPx() * 0.6f).roundToInt()) },
                        size = pupSize * 1.1f, squash = { (1f - sit.value).coerceIn(0f, 1f) }, shadow = false)
                }
            }
        }
        if (sitSeq > 0) Confetti(sitSeq, Modifier.fillMaxSize())
    }
}
