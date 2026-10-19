package com.bigpapituna.sharpears.ui.playground

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.playground.ChordJudge
import com.bigpapituna.sharpears.engine.playground.PageLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.engine.playground.TreeSkyLines
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.skin.colourNotesOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 2.3 part B, the happy chord (the night sky's "Happy or sad"): a rain cloud hangs over the pup; C, E and G glow on the
 * keys with their badges, the pup plays them together first, and when all three ring together ([ChordJudge], the chord
 * as one matcher group, every key its own sound) the cloud rolls away and the sun comes out. Two of them, left too
 * long: "All three together!". Another key: "C, E and G, all together." Three tries and it goes by with a small star.
 */
@Composable
internal fun SkyChordStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val chord = StageKeys.C_CHORD
    val judge = remember { ChordJudge(chord) }
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    var lit by remember { mutableStateOf(emptySet<Int>()) }
    var sunSeq by remember { mutableIntStateOf(0) }
    var tiltSeq by remember { mutableIntStateOf(0) }
    val sun = remember { Animatable(0f) }
    val st = rememberStage(ex, "$lessonId-sun", lessonId, "sky_chord", deps, inject, onEnded,
        keys = { chord },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, _ -> judge.judge(s).also { lit = judge.lit } },
        foundLine = { run, _ -> if (run.finished) TreeSkyLines.SUN_ALL else TreeSkyLines.SUN_OUT[(run.done - 1).coerceAtLeast(0) % 3] },
        missLine = { _, _ -> PageLines.CHORD_KEYS },
        velocity = { 84 },
        onFound = { sunSeq++; lit = emptySet(); Log.d(TAG, "playground: the happy chord rang, the sun comes out") },
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
    LaunchedEffect(sunSeq) {
        if (sunSeq == 0) return@LaunchedEffect
        if (still) sun.snapTo(1f) else sun.animateTo(1f, tween(900))
        delay(1_800)
        if (!run.finished) { if (still) sun.snapTo(0f) else sun.animateTo(0f, tween(900)) }
    }
    var tiltNow by remember { mutableStateOf(false) }
    LaunchedEffect(tiltSeq) { if (tiltSeq > 0) { tiltNow = true; delay(1300); tiltNow = false } }
    val demo = st.demoKeys
    // The round's phase is not snapshot state: its version is, so the line follows the round.
    val finished = remember(run.version) { run.finished }
    val line = stageLine(st, when {
        finished -> TreeSkyLines.SUN_ALL
        demo.isNotEmpty() -> "Listen!"
        run.phase == RoundCoach.Phase.LISTEN -> "C, E and G, all at once!"
        else -> null
    })
    val colours = colourNotesOn()
    val reader = LocalProfile.current.reader
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        SkyBackdrop(Modifier.fillMaxSize())
        // Day breaks over the night as the sun comes out.
        Canvas(Modifier.fillMaxSize()) { drawRect(Brush.verticalGradient(listOf(Color(0xFF7EC3F0).copy(alpha = 0.75f * sun.value), Color(0xFFFFE6A8).copy(alpha = 0.5f * sun.value)))) }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(when { finished -> Painted.DANCING; sun.value > 0.5f -> Painted.GREAT_JOB; tiltNow -> Painted.HEAD_TILT; demo.isNotEmpty() -> Painted.POINTING
                    else -> Painted.LISTENING }, size = (roomH * 0.28f).coerceIn(80.dp, 150.dp), poke = st.fx.poke)
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.width(150.dp).height(110.dp).semantics { contentDescription = if (sun.value > 0.5f) "The sun is out" else "A rain cloud" }) {
                        val c = Offset(size.width / 2, size.height * 0.48f)
                        val r = size.height * 0.28f
                        if (sun.value > 0.02f) sun(c, r * (0.6f + 0.4f * sun.value))
                        if (sun.value < 0.98f) {
                            val off = Offset(size.width * 0.9f * sun.value, -size.height * 0.2f * sun.value)
                            rainCloud(c + off, r)
                        }
                    }
                    StageLine(line)
                }
            }
            BigKeys(57, 72, "Piano keys from A3 to C5. C, E and G glow: play them together.", Modifier.fillMaxWidth().height((roomH * 0.34f).coerceIn(120.dp, 210.dp)),
                fill = { m -> when {
                    m in demo -> PupGold
                    m in lit -> if (colours) noteColour(m) else Accent
                    m in chord -> PupGold.copy(alpha = 0.28f)
                    else -> null
                } },
                last = run.last, fx = st.fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 84); scope.launch { delay(900); deps.sampler.noteOff(m) } }
                    st.tap(m)
                },
                overlay = { g -> for (m in chord) g.rect(m).let { r -> keyBadge(m, Offset(r.center.x, r.bottom - r.width * 0.55f), r.width * 0.34f, reader, colours, measurer) } })
        }
        if (sunSeq > 0) Confetti(sunSeq, Modifier.fillMaxSize())
    }
}
