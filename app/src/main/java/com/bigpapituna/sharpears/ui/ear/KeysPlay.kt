package com.bigpapituna.sharpears.ui.ear

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.GameLine
import com.bigpapituna.sharpears.engine.Lit
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.ear.Answering
import com.bigpapituna.sharpears.engine.ear.EarEvent
import com.bigpapituna.sharpears.engine.ear.EarKind
import com.bigpapituna.sharpears.engine.ear.EarTrainer
import com.bigpapituna.sharpears.engine.ear.ProgressionGame
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.ui.WrongGlow
import com.bigpapituna.sharpears.ui.Cheer
import com.bigpapituna.sharpears.ui.CheerBar
import com.bigpapituna.sharpears.ui.Good
import com.bigpapituna.sharpears.ui.Keyboard
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.cheerTitle
import com.bigpapituna.sharpears.ui.onScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOW = 48    // C3
private const val HIGH = 84   // C6

private enum class Phase { PROMPT, ANSWER, REVIEW }

/** What the cheer bar shows once a round is over. */
private class Review(val line: GameLine, val clean: Boolean, val picked: Int?)

/**
 * The generic screen for melody memory, perfect pitch, scale degrees and progressions. Each round: the voice says the
 * prompt (the first round, or when it changes), the sampler plays the key and the question with the mic off, then
 * the player answers with the keys (touch, MIDI, or the mic listening the way the chord Games do), the choice
 * buttons, or both. A cheer bar says what it was and holds the Continue button. [onFinished] is called once, after
 * the last round's Continue.
 */
@Composable
internal fun KeysPlay(trainer: EarTrainer, env: EarEnv, label: String?, onExit: () -> Unit, onFinished: () -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.PROMPT) }
    var replay by remember { mutableIntStateOf(0) }
    var replayIntro by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf<Pair<String, Boolean>?>(null) }      // text, good
    var review by remember { mutableStateOf<Review?>(null) }
    var missedThisRound by remember { mutableStateOf(false) }
    var pings by remember { mutableIntStateOf(0) }                              // sounds played, for the pup's ripple
    var stepsPlayed by remember { mutableIntStateOf(0) }                        // steps of the question sounded so far
    var activeBeat by remember { mutableIntStateOf(-1) }                        // the progression slot sounding now
    var beatsHeard by remember { mutableIntStateOf(0) }
    val prog = trainer as? ProgressionGame
    var micOn by remember { mutableStateOf(env.micDefault && env.mic.hasPermission()) }
    val touched = remember { mutableStateMapOf<Int, Color>() }                  // fingers down
    val flash = remember { mutableStateMapOf<Int, Color>() }                    // judged keys, for a moment
    val glow = remember { mutableStateMapOf<Int, Color>() }                     // keys the app is playing
    val scope = rememberCoroutineScope()
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) micOn = true else hint = com.bigpapituna.sharpears.ui.MIC_REFUSED_LINE to false     // refused: where to allow it
    }
    val seen = version
    val round = trainer.round
    val finished = trainer.finished
    val usesKeys = trainer.answering == Answering.KEYS || trainer.answering == Answering.KEYS_AND_CHOICES
    val usesChoices = trainer.answering == Answering.CHOICES || trainer.answering == Answering.KEYS_AND_CHOICES

    fun touchSound() = env.sound() && (!(micOn && usesKeys && phase == Phase.ANSWER) || env.mic.headphonesOn())
    suspend fun voiceDone() { delay(250); while (env.voice.speaking.value) delay(50) }

    // Keys light only where seeing them is the point: melody memory's demonstration, and the key's chord and scale
    // before a scale degree. A note that is the answer (perfect pitch, the degree's note) never lights; the pup's
    // ripple pings instead, one ring per sound.
    // [beats]: the progression's beats per chord, so its slots light beat by beat (a held chord lights two).
    suspend fun playSteps(steps: List<com.bigpapituna.sharpears.engine.ear.Step>, light: Boolean, beats: List<Int>? = null) {
        var beat = 0
        for ((i, s) in steps.withIndex()) {
            if (env.sound()) s.notes.forEach { env.sampler.noteOn(it, s.velocity) }
            pings++
            stepsPlayed = i + 1
            if (light) s.notes.forEach { glow[it] = RightHandColor }
            try {
                val b = beats?.getOrNull(i)
                if (b == null) delay(s.holdMs.toLong())
                else for (k in 0 until b) {
                    activeBeat = beat + k; beatsHeard = beat + k + 1
                    delay(if (k < b - 1) PROG_BEAT_MS else s.holdMs - PROG_BEAT_MS * (b - 1))
                }
            } finally {
                s.notes.forEach { env.sampler.noteOff(it); glow.remove(it) }
            }
            beat += beats?.getOrNull(i) ?: 1
            if (s.gapMs > 0) delay(s.gapMs.toLong())
        }
        activeBeat = -1
    }

    fun flashKey(m: Int, color: Color) {
        flash[m] = color
        scope.launch { delay(450); if (flash[m] == color) flash.remove(m) }
    }

    // Each round (and each Hear again): the prompt and the sounds with the mic off, then listen.
    LaunchedEffect(round, replay, finished) {
        if (finished) return@LaunchedEffect
        phase = Phase.PROMPT
        glow.clear()
        if (replay == 0) {
            val p = trainer.prompt()
            Log.d(TAG, "ear: ${trainer.kind.id} level ${trainer.level} round ${round + 1}: ${p.text} (${trainer.describe()})")
            if (round == 0 || trainer.kind == EarKind.MELODY) { env.voice.speak(p.spoken); voiceDone() }
        }
        activeBeat = -1; beatsHeard = 0; stepsPlayed = 0
        delay(150)
        if (replay == 0 || replayIntro) playSteps(trainer.intro(), light = true)
        stepsPlayed = 0
        playSteps(trainer.toPlay(), light = trainer.kind == EarKind.MELODY, beats = prog?.target?.steps?.map { it.beats })
        delay(150)
        phase = Phase.ANSWER
    }

    fun handle(a: ChordGame.Answer, m: Int?, picked: Int?, src: String) {
        Log.d(TAG, "ear: ${trainer.kind.id} ${if (m != null) noteName(m) else "choice $picked"} src=$src -> ${a.outcome} clean=${a.clean} score=${trainer.score} streak=${trainer.streak}")
        when (a.outcome) {
            ChordGame.Outcome.WRONG -> {
                missedThisRound = true
                m?.let { flashKey(it, WrongGlow) }
                // A game that replays itself after a wrong note lets the replay be the answer; the others say what was wrong.
                a.line?.let { hint = it.text to false; if (!trainer.replayOnWrong) env.voice.speak(it.spoken, deferrable = false) }
                if (trainer.replayOnWrong) scope.launch { delay(700); replayIntro = false; replay++ }
            }
            ChordGame.Outcome.FOUND -> {
                m?.let { flashKey(it, Good) }
                a.line?.let { hint = it.text to true }
            }
            ChordGame.Outcome.DONE -> {
                m?.let { flashKey(it, if (a.clean) Good else WrongGlow) }
                hint = null
                review = Review(a.line!!, a.clean, picked)
                phase = Phase.REVIEW                                     // the mic stops, so the voice can speak
                env.voice.speak(a.line.spoken)
                env.report(EarEvent.Round(trainer.kind, trainer.level, a.clean))
            }
            ChordGame.Outcome.IGNORED -> {}
        }
        version++
    }

    fun strike(m: Int, src: String) { if (phase == Phase.ANSWER && usesKeys) handle(trainer.input(m), m, null, src) }
    fun pick(id: Int) { if (phase == Phase.ANSWER && usesChoices) handle(trainer.choose(id), null, id, "button") }

    fun goOn() {
        env.voice.stop()
        review = null; hint = null; missedThisRound = false
        replay = 0; replayIntro = true
        trainer.next()
        version++
        if (trainer.finished) onFinished()
    }

    LaunchedEffect(env.midi) {
        env.midi.events.collect { e -> if (e is NoteEvent.On) { touched[e.midi] = RightHandColor; strike(e.midi, "midi") } else touched.remove(e.midi) }
    }

    // The mic, the way the chord Games do it: expected notes and groups for this round; strikes from before listening started do not count.
    val visible = onScreen()
    val listen = usesKeys && micOn && visible && phase == Phase.ANSWER && !env.voice.holdsMic
    val listening by rememberUpdatedState(listen)
    var listenFrom by remember { mutableLongStateOf(0L) }
    DisposableEffect(listen) {
        if (listen && !env.mic.start()) micOn = false
        onDispose { if (listen) env.mic.stop() }  // only the mic this effect started: never the next screen's
    }
    LaunchedEffect(seen, listen) {
        env.mic.matcher.expected = if (listen) trainer.micNotes() else emptySet()
        env.mic.matcher.groups = trainer.micGroups()
    }
    LaunchedEffect(round, replay, listen) { listenFrom = env.mic.matcher.samplesProcessed }
    LaunchedEffect(env.mic) {
        env.mic.events.collect { e ->
            if (!listening || e !is MicEvent.Strike) return@collect
            val onset = e.sample - ((e.delayMs ?: 0.0) * MicInput.RATE / 1000).toLong()
            if (onset < listenFrom) { Log.d(TAG, "ear: ignored ${noteName(e.midi)} (struck before listening)"); return@collect }
            strike(e.midi, "mic")
        }
    }
    DisposableEffect(Unit) { onDispose { env.voice.stop() } }

    val pose = when {
        phase == Phase.PROMPT -> Pose.LISTENING
        review?.clean == true -> Pose.GREAT_JOB                              // he cheers with the bar
        missedThisRound || review != null -> Pose.MISTAKE
        trainer.streak >= 3 -> Pose.STREAK
        else -> Pose.GET_READY
    }
    val rev = review
    val lit: Map<Int, Color> = buildMap {
        trainer.lit().forEach { (n, l) -> put(n, when (l) { Lit.SHOWN -> RightHandColor.copy(alpha = 0.5f); Lit.GIVEN -> RightHandColor; Lit.FOUND -> Good }) }
        putAll(glow); putAll(flash); putAll(touched)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GameHeader(trainer, label, pose, if (finished) "" else trainer.prompt().text, onExit,
                listening = phase == Phase.PROMPT, pings = pings)
            val melody = trainer as? com.bigpapituna.sharpears.engine.ear.MelodyMemoryGame
            if (melody != null && !finished) MelodyDots(melody.melody.size, stepsPlayed, if (phase == Phase.REVIEW && rev?.clean == true) melody.melody.size else melody.progress,
                listening = phase == Phase.PROMPT, modifier = Modifier.padding(top = 4.dp))
            if (prog != null && !finished) ProgressionSlots(prog, activeBeat, beatsHeard, revealed = phase == Phase.REVIEW,
                clean = rev?.clean == true, modifier = Modifier.padding(top = 6.dp))
            // What was wrong reads in the neutral colour: a hint, not a scolding.
            Text(hint?.first ?: if (phase == Phase.PROMPT) "Listen" else "", color = when {
                hint == null -> MaterialTheme.colorScheme.primary
                hint?.second == false -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.tertiary
            }, style = MaterialTheme.typography.titleSmall, minLines = if (prog != null) 1 else 2, maxLines = 2)
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (usesKeys) FilterChip(micOn, {
                    if (micOn) micOn = false else if (env.mic.hasPermission()) micOn = true else askMic.launch(Manifest.permission.RECORD_AUDIO)
                }, label = { Text(if (micOn) "Listening" else "Use mic") },
                    leadingIcon = { Icon(painterResource(if (micOn) R.drawable.ic_mic else R.drawable.ic_mic_off), contentDescription = null, modifier = Modifier.size(18.dp)) })
                OutlinedButton(onClick = { replayIntro = false; replay++ }, enabled = phase == Phase.ANSWER) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp)); Text("Hear again")
                }
                if (trainer.intro().isNotEmpty()) OutlinedButton(onClick = { replayIntro = true; replay++ }, enabled = phase == Phase.ANSWER) { Text("Hear the key") }
                TextButton(onClick = {
                    env.voice.stop(); hint = null; review = null; missedThisRound = false; replay = 0; replayIntro = true
                    trainer.skip(); version++
                    if (trainer.finished) onFinished()
                }, enabled = phase != Phase.REVIEW) { Text("Skip") }
            }
            if (usesChoices) {
                val answer = if (phase == Phase.REVIEW) trainer.answerId() else null
                fun stateOf(c: com.bigpapituna.sharpears.engine.ear.Choice) = when {
                    answer == null -> ChoiceState.NEUTRAL
                    c.id == answer -> ChoiceState.RIGHT
                    c.id == rev?.picked -> ChoiceState.WRONG
                    else -> ChoiceState.DIMMED
                }
                // Choice-only games get big answer cards; the others keep the row of pills beside the keys.
                if (!usesKeys) AnswerCards(trainer.choices(), ::stateOf, enabled = phase == Phase.ANSWER) { pick(it) }
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    trainer.choices().forEach { c -> ChoiceButton(c, stateOf(c), enabled = phase == Phase.ANSWER) { pick(c.id) } }
                }
            }
            // The how-to shows in the first round only; the hub sheet has it too.
            if (round == 0) Text(trainer.kind.howToFor(trainer.level), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (usesKeys) Keyboard(
                LOW, HIGH, lit = lit,
                onNoteOn = { m, vel -> touched[m] = RightHandColor; if (touchSound()) env.sampler.noteOn(m, vel); strike(m, "touch") },
                onNoteOff = { m -> touched.remove(m); env.sampler.noteOff(m) },
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            Spacer(Modifier.height(if (rev != null) 240.dp else 12.dp))
        }
        if (rev != null) CheerBar(
            if (rev.clean) Cheer.GOOD else Cheer.MISS,
            if (rev.clean) cheerTitle(round + trainer.score) else missTitle(round + trainer.score),
            rev.line.text,
            if (round + 1 >= trainer.rounds) "See results" else "Continue",
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { goOn() }
    }
}
