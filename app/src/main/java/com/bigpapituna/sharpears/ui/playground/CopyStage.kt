package com.bigpapituna.sharpears.ui.playground

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Fingering
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.Backdrop
import com.bigpapituna.sharpears.engine.playground.NoteJudge
import com.bigpapituna.sharpears.engine.playground.PageLines
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageGame
import com.bigpapituna.sharpears.engine.playground.TreeSkyLines
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.skin.colourNotesOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far apart the pup plays a phrase's notes when he shows it. */
private const val COPY_GAP_MS = 520L

/**
 * 2.3 part B, copy the pup. In the treehouse each round is a phrase the painted hand plays with its fingers (the finger
 * numbers on the hand and on the keys, the pup calling them out: "One, two, three!"), then you play it back; the hand
 * waits over the next key with its finger while you play. [StageGame.Copy.byEar]: after the first round the hand and
 * the numbers stay away and only the pup's call and the sound are left ("Now just listen. No peeking!"). Under the
 * stars the stars sing each note of the tune and light again as you play it back. Heard through the note matcher on
 * the exercise's keys (strict keys); a key that is not the next note is a try ("Which note comes next?"); three tries
 * and the rest of the phrase goes by with a small star. The app hears keys, not fingers: the pup asks for fingers and
 * shows his own, never says he saw yours.
 */
@Composable
internal fun CopyStage(game: StageGame.Copy, step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit,
                       modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val tree = game.backdrop == Backdrop.TREEHOUSE
    val song = remember(ex) { ex.toSong("$lessonId-copy", lessonId) }
    val fingering = remember(song) { Fingering.of(song) }
    val phrases = remember(song) { TreeSkyLines.phrases(song) }
    val calls = remember(ex) { TreeSkyLines.calls(ex) }
    val keys = remember(song) { song.notes.map { it.midi }.toSet() }
    val (low, high) = remember(keys) { stageRange(keys) }
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    var tiltSeq by remember { mutableIntStateOf(0) }
    var cheer by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf<Int?>(null) }
    val runRef = remember { arrayOfNulls<StageRun>(1) }
    val demoMs = phrases.maxOf { it.size } * COPY_GAP_MS + 900
    val st = rememberStage(ex, "$lessonId-copy", lessonId, "copy", deps, inject, onEnded,
        keys = { keys },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, run -> NoteJudge.judge(s, run.rounds.expectedKey()) },
        foundLine = { run, _ -> when {
            run.finished -> PageLines.ALL_COPIED
            run.rounds.doneInRound == 0 -> {
                val r = (run.done - 1).coerceAtLeast(0)
                if (phrases.getOrNull(r)?.any { isBlack(it.midi) } == true) TreeSkyLines.BLACK_KEY
                else (if (tree) TreeSkyLines.TREE_COPIED else TreeSkyLines.SKY_COPIED)[r % 3]
            }
            else -> null
        } },
        missLine = { _, _ -> PageLines.NEXT_NOTE },
        onFound = { _ ->
            val r = runRef[0]
            if (r != null && r.rounds.doneInRound == r.rounds.roundGroups.size - 1) {
                copied = r.done; cheer++
                Log.d(TAG, "playground: phrase ${r.done + 1} copied: ${phrases.getOrNull(r.done)?.map { noteName(it.midi) }}")
            }
        },
        onMiss = { tiltSeq++ },
        coach = { RoundCoach(firstDemoMs = 1_300 + demoMs, demoMs = demoMs, againDemoMs = 1_000 + demoMs) },
        phrases = true, demoGapMs = COPY_GAP_MS,
    )
    runRef[0] = st.run
    val run = st.run
    val demo = st.demoKeys
    val v = run.version
    val round = remember(v) { run.done }
    val inRound = remember(v) { run.rounds.doneInRound }
    // By ear: the first round shows the hand, the rest only the pup's words and the sound.
    val hidden = game.byEar && round > 0
    var saidNoPeeking by remember { mutableStateOf(false) }
    // Each time the pup shows a phrase he calls its fingers as his hand plays them.
    LaunchedEffect(demo) {
        if (demo.isEmpty()) {
            // A demo heard by ear leaves no rings behind on the keys.
            if (hidden) st.fx.ripples.clear()
            return@LaunchedEffect
        }
        copied = null
        if (hidden && !saidNoPeeking) { saidNoPeeking = true; st.call(TreeSkyLines.NO_PEEKING); delay(1_400) }
        calls.getOrNull(run.done)?.let { st.call(it) }
    }
    LaunchedEffect(cheer) { if (cheer > 0) { delay(2_000); copied = null } }
    var tiltNow by remember { mutableStateOf(false) }
    LaunchedEffect(tiltSeq) { if (tiltSeq > 0) { tiltNow = true; delay(1300); tiltNow = false } }
    val phrase = phrases.getOrNull(copied ?: round).orEmpty()
    val playing = st.demoIndex?.let { phrase.getOrNull(it) }
    val next = if (demo.isEmpty() && run.phase == RoundCoach.Phase.LISTEN && copied == null) phrase.getOrNull(inRound) else null
    val finished = run.finished
    val line = stageLine(st, when {
        finished -> PageLines.ALL_COPIED
        demo.isNotEmpty() -> if (hidden) "Listen!" else "Watch and listen!"
        run.phase == RoundCoach.Phase.LISTEN -> PlaygroundLines.YOUR_TURN
        else -> null
    })
    val pulse by rememberInfiniteTransition(label = "copy").animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    val p = if (still) 0.5f else pulse
    val measurer = rememberTextMeasurer()
    val hand = when {
        hidden || !tree -> null
        playing != null -> HandShow(playing.hand == Hand.RIGHT, fingering.getValue(playing), playing.midi, pressed = true)
        // Waiting over a key, the key's own badge carries the number (one badge, not two).
        next != null -> HandShow(next.hand == Hand.RIGHT, fingering.getValue(next), next.midi, pressed = false, number = false)
        else -> phrase.firstOrNull()?.let { HandShow(it.hand == Hand.RIGHT, fingering.getValue(it), it.midi, pressed = false, number = false) }
    }
    val fingerOf = remember(phrase) { phrase.associate { it.midi to fingering.getValue(it) } }
    val rightOf = remember(phrase) { phrase.associate { it.midi to (it.hand == Hand.RIGHT) } }
    val played = remember(v, copied, phrase) { (if (copied != null) phrase else phrase.take(inRound)).map { it.midi }.toSet() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        PlaceBackdrop(game.backdrop, Modifier.fillMaxSize(), floorFrom = 0.42f)
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(when {
                    finished || copied != null -> Painted.GREAT_JOB
                    tiltNow -> Painted.HEAD_TILT
                    demo.isNotEmpty() -> Painted.POINTING
                    else -> Painted.LISTENING
                }, size = (roomH * 0.2f).coerceIn(64.dp, 120.dp), poke = st.fx.poke)
                Spacer(Modifier.width(8.dp))
                StageLine(line)
            }
            if (!tree) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                key(round) {
                    StarSingers(phrase.map { it.midi }, playing = if (demo.isNotEmpty()) st.demoIndex else null, answered = if (copied != null) phrase.size else inRound, waiting = next != null, pulse = p,
                        modifier = Modifier.fillMaxWidth().heightIn(max = (roomH * 0.3f).coerceAtLeast(72.dp)))
                }
            } else Spacer(Modifier.weight(1f))
            val keysH = if (tree) (roomH * 0.5f).coerceIn(150.dp, 300.dp) else (roomH * 0.3f).coerceIn(110.dp, 190.dp)
            Box(Modifier.fillMaxWidth().height(keysH)) {
                BigKeys(low, high, "Piano keys from ${noteName(low)} to ${noteName(high)}. The tune uses ${keys.sorted().joinToString(", ") { noteName(it) }}.",
                    Modifier.fillMaxSize(),
                    fill = { m -> when {
                        hidden -> null
                        playing?.midi == m -> PupGold
                        m == next?.midi -> PupGold.copy(alpha = 0.18f + 0.3f * p)
                        else -> null
                    } },
                    last = run.last, fx = st.fx,
                    onKey = { m ->
                        if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(600); deps.sampler.noteOff(m) } }
                        st.tap(m)
                    })
                HandOverKeys(low, high, hand, Modifier.fillMaxSize())
                // The phrase's finger numbers on the fronts of its keys, over the hand: on the fingers that rest there.
                if (tree && !hidden) Canvas(Modifier.fillMaxSize()) {
                    keyFingers(com.bigpapituna.sharpears.ui.practice.PianoGeometry(low, high, size.width, size.height, 0f), fingerOf, { rightOf[it] ?: true }, played,
                        measurer, p, next?.midi)
                }
            }
        }
        if (cheer > 0) Confetti(cheer, Modifier.fillMaxSize())
    }
}

/**
 * The night sky's singers: one star per note of the tune. The note [playing] in the demo sings (it bounces and glows
 * in its colour); the first [answered] notes are lit (played back); while [waiting], the next one breathes.
 */
@Composable
private fun StarSingers(tune: List<Int>, playing: Int?, answered: Int, waiting: Boolean, pulse: Float, modifier: Modifier = Modifier) {
    val colours = colourNotesOn()
    Row(modifier.semantics { contentDescription = "${tune.size} notes, $answered played back" },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        tune.forEachIndexed { i, midi ->
            val colour = if (colours) noteColour(midi) else PupGold
            val lit = i == playing || i < answered
            val next = waiting && i == answered
            val hop = remember { Animatable(0f) }
            LaunchedEffect(i == playing, i < answered) {
                if (lit) { hop.snapTo(1f); hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
            }
            Canvas(Modifier.weight(1f).widthIn(max = 96.dp).aspectRatio(1f).graphicsLayer { val s = 1f + 0.25f * hop.value; scaleX = s; scaleY = s }) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension * 0.36f
                if (lit) drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.7f), Color.Transparent), c, r * 2f), r * 2f, c)
                if (next) drawCircle(Color.White.copy(alpha = 0.25f + 0.35f * pulse), r * 1.35f, c, style = Stroke(2.dp.toPx()))
                drawPath(star(c, r, inner = 0.47f), if (lit) colour else Color.White.copy(alpha = 0.22f))
                drawPath(star(c, r, inner = 0.47f), Color.White.copy(alpha = if (lit) 0.8f else 0.35f), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}
