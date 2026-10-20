package com.bigpapituna.sharpears.ui.playground

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.Wav
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.Answer
import com.bigpapituna.sharpears.engine.playground.ClapSynth
import com.bigpapituna.sharpears.engine.playground.HeardBy
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_TRIES
import com.bigpapituna.sharpears.engine.playground.PitchHeight
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageClipWatch
import com.bigpapituna.sharpears.engine.playground.StageEar
import com.bigpapituna.sharpears.engine.playground.StageRounds
import com.bigpapituna.sharpears.engine.playground.StageSound
import com.bigpapituna.sharpears.engine.skin.WrongKey
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.onScreen
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.practice.PianoGeometry
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.skin.colour
import com.bigpapituna.sharpears.ui.world.WorldSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

/*
 * What the Playground's stages share: the stage's rounds (shown, heard, let go after three tries), the microphone as
 * the stages hear it (the note matcher with strict keys, the pitch tracker's guess, every sound answered), the
 * feedback for each sound (a ripple on the keys, the pup's ear, a key lit yellow or red), the call-outs said between
 * rounds, a big keyboard to draw and touch, the painted pup poses, and the little row of round stars.
 */

// ---- The painted pup ------------------------------------------------------------------------------------------------

/**
 * The painted poses the Playground draws (res/drawable-nodpi/pup_*.webp). ui/Pup.kt's Pose (the world team's) lists
 * ten of the twenty-two; these are drawn here directly until it has them all.
 */
enum class Painted(val res: Int) {
    FLYING(R.drawable.pup_flying), STANDING(R.drawable.pup_standing), SLEEPING(R.drawable.pup_sleeping),
    GOODNIGHT(R.drawable.pup_goodnight), SURPRISED(R.drawable.pup_surprised), DRUMMING(R.drawable.pup_drumming),
    CLAPPING(R.drawable.pup_clapping), POINTING(R.drawable.pup_pointing), HEAD_TILT(R.drawable.pup_head_tilt),
    DANCING(R.drawable.pup_dancing), CROWN(R.drawable.pup_crown), GREAT_JOB(R.drawable.pup_great_job),
    LISTENING(R.drawable.pup_listening),
    /** 2.2: curled up on his cushion (the pond's chord: he settles on it). */
    RESTING(R.drawable.pup_resting),
}

/**
 * A painted pose with the pup's manners (as ui/Pup.kt): standing in a soft gold glow over his shadow, a new pose
 * cross-fading in with a little hop. [lift] (0..1) raises him off his shadow, which shrinks; [squash] presses him down
 * (a landing, a drum beat). Each change of [poke] is "I heard you": a small hop and a flick of the ears. Decorative:
 * what he means is said beside him. Still with the phone's animations off.
 */
@Composable
fun PaintedPup(pose: Painted, modifier: Modifier = Modifier, size: Dp = 120.dp, lift: () -> Float = { 0f }, squash: () -> Float = { 0f },
               shadow: Boolean = true, poke: Int = 0) {
    val still = reducedMotion()
    val hop = remember { Animatable(1f) }
    val flick = remember { Animatable(0f) }
    LaunchedEffect(pose) { if (!still) { hop.snapTo(0.82f); hop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) } }
    LaunchedEffect(poke) {
        if (poke == 0 || still) return@LaunchedEffect
        launch { hop.snapTo(0.92f); hop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        flick.snapTo(1f); flick.animateTo(0f, tween(420))
    }
    Box(modifier.size(size).drawBehind {
        val l = lift().coerceIn(0f, 1f)
        val c = Offset(this.size.width / 2, this.size.height * 0.55f)
        val r = this.size.width * 0.55f
        drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = 0.28f + 0.25f * flick.value), Color.Transparent), c, r), r, c)
        if (shadow) {
            val w = this.size.width * 0.56f * (1f - 0.6f * l)
            drawOval(Color.Black.copy(alpha = 0.32f * (1f - 0.5f * l)), Offset((this.size.width - w) / 2, this.size.height * 0.93f),
                Size(w, this.size.height * 0.06f))
        }
    }) {
        Crossfade(pose, animationSpec = if (still) snap() else tween(220), label = "pose") { p ->
            Image(painterResource(p.res), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                val s = hop.value
                val q = squash().coerceIn(0f, 1f)
                scaleX = s * (1f + 0.06f * q); scaleY = s * (1f - 0.08f * q)
                // The ear flick: a quick wobble that rings out.
                rotationZ = 5f * flick.value * sin(flick.value * 18f)
            })
        }
    }
}

// ---- A stage's rounds -----------------------------------------------------------------------------------------------

/** One key the stage heard (the mic) or felt (a touch), what the round made of it, and a count to key effects on. */
data class Struck(val midi: Int, val result: InputResult, val seq: Int, val riseDb: Double? = null)

/**
 * A stage's rounds while composed (engine [StageRounds]: the step's exercise in wait mode, each round shown first,
 * heard, and let go after three tries). [version] bumps on every change, so drawing can follow; [phase] is the coach's.
 */
@Stable
internal class StageRun(val rounds: StageRounds) {
    var version by mutableIntStateOf(0)
        internal set
    var started by mutableStateOf(false)
        internal set
    var ended by mutableStateOf(false)
        internal set
    /** The last key answer (found or not), for the key's flash and the pup's face. */
    var last by mutableStateOf<Struck?>(null)
        private set
    val count get() = rounds.rounds
    val done get() = rounds.done
    val phase get() = rounds.coach.phase
    val demoing get() = phase == RoundCoach.Phase.DEMO
    val letGo: List<Int> get() = rounds.letGo
    val finished get() = ended || (count > 0 && done >= count)

    fun found(key: Int, nowMs: Long): InputResult {
        val r = rounds.found(key, nowMs)
        last = Struck(key, r, (last?.seq ?: 0) + 1)
        version++
        Log.d(TAG, "playground: ${noteName(key)} -> $r (${rounds.done}/${rounds.rounds})")
        return r
    }

    fun miss(key: Int?, nowMs: Long): RoundCoach.Cue? {
        val cue = rounds.miss(nowMs)
        last = Struck(key ?: -1, InputResult.WRONG, (last?.seq ?: 0) + 1)
        version++
        Log.d(TAG, "playground: ${key?.let(::noteName) ?: "a sound"} is not it (try ${rounds.coach.tries} of ${rounds.coach.maxTries})")
        return cue
    }
}

/**
 * Runs [exercise] as a stage's rounds while composed: it starts once the voice has read the instruction (and the sounds
 * are loaded), ticks with the frames, sends any accompaniment to the sampler when [sound] allows, hands every coach cue
 * to [onCue], and gives [onEnded] the run's report when the last round is done and the song has run out.
 */
@Composable
internal fun rememberStageRun(exercise: Exercise, id: String, deps: LessonDeps, sound: () -> Boolean, coach: () -> RoundCoach = { RoundCoach() },
                              phrases: Boolean = false, onCue: (RoundCoach.Cue) -> Unit, onEnded: (Report) -> Unit, gapMs: Long = 1_400): StageRun {
    val run = remember(exercise, id) { StageRun(StageRounds(exercise.toSong(id, id), exercise.practice, coach(), gapMs = gapMs, phrases = phrases)) }
    val ended by rememberUpdatedState(onEnded)
    val cueTo by rememberUpdatedState(onCue)
    val soundNow by rememberUpdatedState(sound)
    fun apply(events: List<NoteEvent>) {
        val on = soundNow()
        for (e in events) when (e) { is NoteEvent.On -> if (on) deps.sampler.noteOn(e.midi, e.velocity); is NoteEvent.Off -> deps.sampler.noteOff(e.midi) }
    }
    val speaking = deps.voice.speaking.value
    LaunchedEffect(run, speaking, deps.ready) {
        if (run.started || speaking || !deps.ready) return@LaunchedEffect
        delay(600)
        run.rounds.start(SystemClock.uptimeMillis())
        run.started = true
        Log.d(TAG, "playground: run ${run.rounds.song.id} started, ${run.count} rounds")
    }
    LaunchedEffect(run) {
        snapshotFlow { run.started }.first { it }
        var last = withFrameNanos { it }
        var phase = run.phase
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e6).coerceIn(0.0, 100.0)
            last = now
            val (events, cue) = run.rounds.tick(dt, SystemClock.uptimeMillis())
            apply(events)
            if (cue != null) {
                Log.d(TAG, "playground: round ${cue.round + 1} ${cue::class.simpleName}")
                cueTo(cue)
            }
            if (events.isNotEmpty() || cue != null || run.phase != phase) { phase = run.phase; run.version++ }
            if (run.rounds.ended) {
                run.ended = true
                Log.d(TAG, "playground: run ${run.rounds.song.id} ended, ${run.done}/${run.count} rounds, ${run.letGo.size} let go, " +
                    "${run.rounds.transport.log.wrongKeys} slips")
                ended(run.rounds.report())
                break
            }
        }
    }
    DisposableEffect(run) { onDispose { run.rounds.transport.pause().forEach { if (it is NoteEvent.Off) deps.sampler.noteOff(it.midi) } } }
    return run
}

// ---- What a stage shows for each sound ------------------------------------------------------------------------------

/** A ring spreading from a key ([midi], or the middle of the keyboard when null), in [colour]; [fill] lights the key too. */
internal class Ripple(val id: Long, val midi: Int?, val colour: Color, val fill: Boolean, val startNanos: Long)

/**
 * The stage's answer to every sound (2.1.1, "I heard you"): [ripples] on the keyboard and a [poke] for the pup's ear,
 * within a frame of the microphone's report. Keys found ring gold; a wrong piano key lights yellow (a neighbour) or red
 * (further) as in the player; a sound with no key ripples pale where the pitch tracker guessed, or in the middle.
 */
@Stable
internal class StageFx {
    val ripples = mutableStateListOf<Ripple>()
    var poke by mutableIntStateOf(0)
        private set
    private var ids = 0L

    fun ripple(midi: Int?, colour: Color, fill: Boolean = false) {
        ripples += Ripple(ids++, midi, colour, fill, System.nanoTime())
        while (ripples.size > 8) ripples.removeAt(0)
    }

    /** Shown at once for every sound not yet decided: the pup's ear and a pale ring (a key's own ring follows if it is one). */
    fun heard(s: StageSound) {
        poke++
        if (s.final) return
        ripple(s.near, HeardBlue)
    }

    fun found(midi: Int) { poke++; ripple(midi, PupGold, fill = true) }

    /** A wrong key: yellow within two semitones of [nearest], red further (the player's colours). */
    fun wrong(midi: Int, nearest: Int?) {
        poke++
        val tone = nearest?.let { WrongKey(midi, abs(it - midi)).tone }
        ripple(midi, tone?.colour ?: Ink, fill = tone != null)
    }

    companion object {
        private const val BASE_RIPPLE_MS = 900L
        /** How long a ring lives (stretched by [FxSlow] in a debug screenshot run). */
        val RIPPLE_MS: Long get() = (BASE_RIPPLE_MS * FxSlow.factor).toLong()
    }
}

/**
 * Debug (--ef fxSlow n): stretches the stages' and the Jam's passing feedback (rings, call-outs, captions) n times, so
 * a screenshot can catch it, as --ef lightSlow does for the player's lights. Always 1 in a release build.
 */
object FxSlow { @Volatile var factor = 1f }

/** The ring for a sound with no key yet: a clear sky blue, seen on white keys and on black. */
internal val HeardBlue = Color(0xFF7DB8FF)

/**
 * Everything a stage does around its game (2.1.1), from [rememberStage]: the pup's [line] (said between rounds and over
 * a demo), the keys a demo is lighting ([demoKeys]), a nudge glow ([nudgeUntil]), and whether the mic [listening].
 */
@Stable
internal class StageState(val run: StageRun, val fx: StageFx) {
    var line by mutableStateOf<String?>(null)
        private set
    var lineSeq by mutableIntStateOf(0)
        private set
    /** How long [line] stays up. */
    var lineMs = 2200L
        private set
    var demoKeys by mutableStateOf<List<Int>>(emptyList())
        internal set
    /** Which of [demoKeys] the demo is playing now (a phrase's notes sound one after another), or null. */
    var demoIndex by mutableStateOf<Int?>(null)
        internal set
    var nudgeUntil by mutableLongStateOf(0L)
        internal set
    var listening by mutableStateOf(false)
        internal set

    internal fun say(text: String, ms: Long = 2200) { line = text; lineMs = ms; lineSeq++ }

    /** Shows [text] and says it over the mic (the mic deaf while it plays), as the call-outs between rounds are. */
    fun call(text: String) = onCall(text)
    internal var onCall: (String) -> Unit = { say(it) }

    /** A key touched on screen: certain, like a key the matcher confirmed. */
    fun tap(midi: Int, riseDb: Double? = null) = feed(StageSound(SystemClock.uptimeMillis(), HeardBy.MATCHER, midi, riseDb = riseDb, final = true))
    /** A sound from somewhere other than the mic (a key or pad touched), answered like one the mic heard. */
    fun feed(s: StageSound) = onFeed(s)
    internal var onFeed: (StageSound) -> Unit = {}
}

/**
 * A stage around its game (2.1.1): runs the rounds ([rememberStageRun]), listens ([rememberStageEar]) for [keys] of the
 * round, plays each round's demo ([demoKeys] at [velocity], the mic deaf meanwhile, "Let me show you" the first time
 * and "Your turn!" after it), says the call-outs between rounds over the mic, answers every sound ([StageFx]), plays the
 * pop for a key found and the ding at the end, lets a round go after three tries ("Good try, let's keep going"), and
 * keeps a stuck moment for tuning (sounds and no key for 8 s). The game is [judge] (what a decided sound means for the
 * round) and its lines, [foundLine] and [missLine] (a miss is said the first time in a round, shown after).
 *
 * 2.2: [prompt] is said at the start of a round's demo when it changes (the letter hunts name each letter); with
 * [phrases] a round is a phrase (StageRounds), its demo [demoGapMs] a note; [groups] are how the matcher groups the
 * keys (each its own by default; the chord as one) and [chords] lets every key of a chord through the ear.
 */
@Composable
internal fun rememberStage(
    exercise: Exercise, id: String, lessonId: String, game: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit,
    keys: (StageRun) -> Set<Int>,
    demoKeys: (StageRun) -> List<Int>,
    judge: (StageSound, StageRun) -> Answer,
    foundLine: (StageRun, Int) -> String?,
    missLine: (Answer.Miss, StageRun) -> String?,
    velocity: (StageRun) -> Int = { 80 },
    onFound: (Int) -> Unit = {},
    onMiss: (Answer.Miss) -> Unit = {},
    onLetGo: () -> Unit = {},
    coach: () -> RoundCoach = { RoundCoach() },
    prompt: (StageRun) -> String? = { null },
    phrases: Boolean = false,
    demoGapMs: Long = 260,
    groups: ((Set<Int>) -> List<Set<Int>>)? = null,
    chords: Boolean = false,
    /** 2.3: the pause between one round's end and the next one's demo (the pedal stage plays the tune back in it). */
    gapMs: Long = 1_400,
): StageState {
    val scope = rememberCoroutineScope()
    val fx = remember { StageFx() }
    val mic = deps.mic
    val voice = deps.voice
    val clips = remember { StageClipWatch() }
    val clipMark = remember { longArrayOf(0) }
    val holder = remember { arrayOfNulls<StageState>(1) }
    fun now() = SystemClock.uptimeMillis()
    fun sayOver(text: String) { holder[0]?.say(text); voice.speakOverMic(text) }
    val keysNow by rememberUpdatedState(keys)
    val demoNow by rememberUpdatedState(demoKeys)
    val velocityNow by rememberUpdatedState(velocity)
    val letGoNow by rememberUpdatedState(onLetGo)
    val promptNow by rememberUpdatedState(prompt)
    val lastPrompt = remember { arrayOfNulls<String>(1) }
    fun handle(cue: RoundCoach.Cue) {
        val st = holder[0] ?: return
        when (cue) {
            is RoundCoach.Cue.Demo -> {
                val ks = demoNow(st.run)
                val vel = velocityNow(st.run)
                if (!cue.again) { clips.reset(now(), keysNow(st.run).map(::noteName)); clipMark[0] = mic.clipMark() }
                val said = if (cue.again) null else promptNow(st.run)?.takeIf { it != lastPrompt[0] }
                if (!cue.again) lastPrompt[0] = promptNow(st.run)
                scope.launch {
                    val lead = when {
                        cue.first -> { sayOver(PlaygroundLines.LET_ME_SHOW); 1300L }
                        cue.again -> { sayOver(PlaygroundLines.AGAIN); 1000L }
                        else -> 0L
                    }
                    delay(lead)
                    // A round that asks for something new says so first (2.2: "Find a G!").
                    if (said != null) { sayOver(said); delay(1300) }
                    st.demoKeys = ks
                    Log.d(TAG, "playground: demo ${ks.map(::noteName)} at velocity $vel")
                    // The phone plays the keys: the mic must not take them for the kid's.
                    mic.deafFor(ks.size * demoGapMs + 900)
                    // Each key the phone plays rings gold on the keys as it sounds (with the sound off, the rings alone show it).
                    // A phrase's notes each ring for their own beat; a chord's or a single key's ring together.
                    for ((i, k) in ks.withIndex()) {
                        if (phrases) ks.forEach { deps.sampler.noteOff(it) }
                        if (deps.prefs.sound) deps.sampler.noteOn(k, vel); fx.ripple(k, PupGold, fill = true)
                        st.demoIndex = i
                        delay(demoGapMs)
                    }
                    delay(650)
                    ks.forEach { deps.sampler.noteOff(it) }
                    st.demoKeys = emptyList(); st.demoIndex = null
                }
            }
            is RoundCoach.Cue.Listen -> if (cue.first) sayOver(PlaygroundLines.YOUR_TURN)
            is RoundCoach.Cue.LetGo -> {
                Log.d(TAG, "playground: round ${cue.round + 1} let go after $PLAYGROUND_TRIES tries")
                sayOver(PlaygroundLines.LET_GO)
                letGoNow()
                if (st.run.finished) end(mic)
            }
        }
    }
    val run = rememberStageRun(exercise, id, deps, sound = { false }, coach = coach, phrases = phrases, onEnded = onEnded, onCue = ::handle, gapMs = gapMs)
    val st = remember(run) { StageState(run, fx).also { holder[0] = it } }
    st.onCall = ::sayOver

    fun answer(s: StageSound) {
        if (!s.final || run.phase != RoundCoach.Phase.LISTEN) return
        when (val a = judge(s, run)) {
            is Answer.Found -> {
                fx.found(a.key)
                onFound(a.key)              // before the round moves on: the stage still knows which round it was
                run.found(a.key, now())
                clips.reset(now()); clipMark[0] = mic.clipMark()
                // The pop for a round found (a phrase's notes in between go by quietly: the next may come at once).
                if (!phrases || run.rounds.doneInRound == 0) { mic.deafFor(250); WorldSounds.stagePop() }
                foundLine(run, a.key)?.let { sayOver(it) }
                if (run.finished) end(mic)
            }
            is Answer.Miss -> {
                // Yellow or red against what the round wanted (its demo keys): a neighbour of them is a slip, the other side far.
                a.key?.let { k -> fx.wrong(k, demoNow(run).filter { it != k }.minByOrNull { abs(it - k) }) }
                clips.sound(now(), "${s.by.name.lowercase()} ${a.key?.let(::noteName) ?: "?"}: ${a.why.name.lowercase()}")
                onMiss(a)
                val first = run.rounds.coach.tries == 0
                val cue = run.miss(a.key, now())
                if (cue != null) handle(cue)
                else missLine(a, run)?.let { if (first) sayOver(it) else st.say(it) }
            }
            Answer.Heard -> clips.sound(now(), s.by.name.lowercase() + (s.guess?.let { " %.1f".format(it) } ?: ""))
        }
    }
    // A sound with no key yet: "I heard you!" for a moment (shown, not said: the mic is listening).
    fun heard(s: StageSound) {
        fx.heard(s)
        if (!s.final && (s.by == HeardBy.ONSET || s.by == HeardBy.PITCH)) st.say(PlaygroundLines.I_HEARD_YOU, ms = 1000)
    }
    st.onFeed = { s -> heard(s); answer(s) }
    val heardKeys = keys(run)
    st.listening = rememberStageEar(mic, voice, run.started && !run.ended, inject, heardKeys, groups?.invoke(heardKeys), chords) { s ->
        heard(s)
        answer(s)
    }
    // A stuck moment (sounds, no key) is kept for tuning, once per stretch.
    LaunchedEffect(run) {
        while (!run.ended) {
            delay(500)
            val clip = clips.check(now(), "lesson=$lessonId stage=$game round=${run.done + 1}/${run.count}", mic.profileKind) ?: continue
            val name = "stall-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-$lessonId-$game"
            Log.d(TAG, "playground: stuck ${clip.toMs - clip.fromMs} ms with ${clips.sounds} sounds, saving $name")
            mic.saveStall(clipMark[0], "title=Stage stuck: $lessonId $game round ${run.done + 1}\n" + clip.report, name)
        }
    }
    return st
}

/** The stage's rounds are all done: the ding (the mic deaf for it). */
private fun end(mic: MicInput) {
    mic.deafFor(800)
    WorldSounds.ding()
}

// ---- The microphone ---------------------------------------------------------------------------------------------------

/**
 * The microphone as a stage hears (2.1.1): started while [active] once the voice has let go of it (or, in a debug
 * build, the [inject]ed audio instead, once), then kept on: lines said meanwhile go over it, deaf ([Voice.speakOverMic]).
 * The note matcher listens for [keys], each its own group (or as [groups] says: the pond's chord is one), with strict
 * keys; the pitch tracker reads the raw audio for a guess. Every report goes through a [StageEar] ([chords]: every key
 * of a chord is its own sound) and comes out in [onSound] on the main thread, one per moment (shown at once, then
 * decided). Returns whether it is listening.
 */
@Composable
internal fun rememberStageEar(mic: MicInput, voice: Voice, active: Boolean, inject: String?, keys: Set<Int>, groups: List<Set<Int>>? = null,
                              chords: Boolean = false, onSound: (StageSound) -> Unit): Boolean {
    val context = LocalContext.current
    val injected = remember { mutableStateOf(false) }
    // Waits for the voice once (the step's instruction), then stays on while the stage is active.
    var began by remember { mutableStateOf(false) }
    // Only with the app on screen: Home or the lock button mid-stage stops the mic, coming back starts it again.
    val can = active && onScreen() && (inject != null || mic.hasPermission())
    val holds = voice.holdsMic
    LaunchedEffect(can, holds) { if (can && !holds) began = true }
    val listen = can && began
    val listenNow by rememberUpdatedState(listen)
    val ear = remember(chords) { StageEar(chords = chords) }
    val grouping = groups ?: keys.map { setOf(it) }
    val tracker = remember { PitchHeight() }
    val pitches = remember { MutableSharedFlow<com.bigpapituna.sharpears.engine.playground.Heard>(extraBufferCapacity = 16) }
    val emit by rememberUpdatedState(onSound)
    fun out(list: List<StageSound>) = list.forEach { emit(it) }
    fun now() = SystemClock.uptimeMillis()
    DisposableEffect(listen) {
        if (!listen) return@DisposableEffect onDispose {}
        tracker.reset()
        // The pitch tracker decides about 120 ms after a note began: one that began while the phone played is dropped.
        val removeRaw = mic.rawFrames.add { chunk -> tracker.process(chunk, chunk.size).forEach { if (!mic.deafWithin(400)) pitches.tryEmit(it) } }
        mic.matcher.strictKeys = true
        mic.matcher.expected = keys; mic.matcher.groups = grouping
        val spec = inject?.takeIf { !injected.value }?.let { debugInjection(context, it) }
        when {
            spec != null -> { injected.value = true; mic.startInjection(spec); Log.d(TAG, "playground: mic injecting $spec") }
            inject != null -> Log.d(TAG, "playground: injection already played")
            mic.start() -> Log.d(TAG, "playground: mic listening for ${keys.map(::noteName)} (strict)")
            else -> Log.d(TAG, "playground: mic could not start")
        }
        onDispose {
            removeRaw(); mic.stop()
            mic.matcher.expected = emptySet(); mic.matcher.groups = emptyList(); mic.matcher.strictKeys = false
            ear.clear()
        }
    }
    LaunchedEffect(keys, grouping, listen) { if (listen) { mic.matcher.expected = keys; mic.matcher.groups = grouping } }
    LaunchedEffect(mic) {
        mic.events.collect { e ->
            if (!listenNow) return@collect
            when (e) {
                is MicEvent.Strike -> if (e.inferredFrom == null) {
                    Log.d(TAG, "playground: heard ${noteName(e.midi)} rise ${"%.1f".format(e.riseDb)} dB")
                    out(ear.strike(e.midi, e.riseDb, now()))
                }
                is MicEvent.Wrong -> {
                    Log.d(TAG, "playground: named ${noteName(e.midi)} (not listened for), rise ${"%.1f".format(e.riseDb)} dB")
                    out(ear.wrong(e.midi, e.riseDb, now()))
                }
            }
        }
    }
    LaunchedEffect(mic) { mic.noise.collect { if (listenNow) { Log.d(TAG, "playground: a sound with no key"); out(ear.noise(now())) } } }
    LaunchedEffect(pitches) { pitches.collect { h -> if (listenNow) out(ear.pitch(h.midi, h.clarity, now())) } }
    LaunchedEffect(listen) { while (listen) { delay(40); out(ear.poll(now())) } }
    return listen
}

/**
 * The microphone for a stage that reads the raw audio only (the drum's clap counter): started while [active] once the
 * voice has let go of it (or the [inject]ed audio instead, once). [raw] gets every chunk on the audio thread. Returns
 * whether it is listening, so the stage can keep the app's own sound out of it.
 */
@Composable
internal fun rememberListening(mic: MicInput, voice: Voice, active: Boolean, inject: String?, raw: ((FloatArray, Int) -> Unit)? = null): Boolean {
    val context = LocalContext.current
    val injected = remember { mutableStateOf(false) }
    val listen = active && onScreen() && !voice.holdsMic && (inject != null || mic.hasPermission())
    DisposableEffect(listen) {
        if (!listen) return@DisposableEffect onDispose {}
        val removeRaw = raw?.let { r -> mic.rawFrames.add { chunk -> r(chunk, chunk.size) } }
        val spec = inject?.takeIf { !injected.value }?.let { debugInjection(context, it) }
        when {
            spec != null -> { injected.value = true; mic.startInjection(spec); Log.d(TAG, "playground: mic injecting $spec") }
            inject != null -> Log.d(TAG, "playground: injection already played")
            mic.start() -> Log.d(TAG, "playground: mic listening (raw)")
            else -> Log.d(TAG, "playground: mic could not start")
        }
        onDispose { removeRaw?.invoke(); mic.stop() }
    }
    return listen
}

/**
 * Debug injections the Playground understands, on top of MicInput's own ("synth:..." and WAV paths, passed through):
 * "claps:400,1150,1900" (synthetic claps at those ms) and "dyn:60/-20,60/-30,60/-8@1600~1500" (notes at gains in dB,
 * [step] ms apart from [first]). Written to a WAV in the cache.
 */
internal fun debugInjection(context: Context, spec: String): String {
    val rate = 48_000
    val samples = when {
        spec.startsWith("claps:") -> spec.removePrefix("claps:").split(',').map { it.trim().toDouble() }.let { t -> ClapSynth.render(t, t.max() + 1500, rate) }
        spec.startsWith("dyn:") -> {
            val (list, timing) = spec.removePrefix("dyn:").split('@').let { it[0] to it.getOrElse(1) { "" } }
            val step = timing.substringBefore('~').toDoubleOrNull() ?: 1600.0
            val first = timing.substringAfter('~', "").toDoubleOrNull() ?: 1500.0
            val notes = list.split(',').mapIndexed { i, n -> n.split('/').let { (m, g) -> SynthNote(m.toInt(), first + i * step, 700.0, gainDb = g.toDouble()) } }
            PianoSynth.render(notes, first + notes.size * step + 1000, rate)
        }
        else -> return spec
    }
    val file = File(context.cacheDir, "playground-inject.wav")
    Wav.write(file, samples, rate)
    return file.path
}

// ---- The keyboard -------------------------------------------------------------------------------------------------------

private val WhiteTop = Color(0xFFFFFCF7)
private val WhiteBottom = Color(0xFFE9E1D6)
private val KeyGap = Color(0xFF15110F)
private val BlackTop = Color(0xFF3A322C)
private val BlackBottom = Color(0xFF110E0C)

/**
 * Draws a flat piano in [g]: white keys with a soft gradient, black keys polished, keys in [fill] washed in their colour
 * (a found group, the side to play), and [flash] (0..1) lighting one key bright for a moment.
 */
internal fun DrawScope.piano(g: PianoGeometry, fill: (Int) -> Color? = { null }, flash: Pair<Int, Float>? = null) {
    drawRect(KeyGap, Offset(0f, g.top), Size(g.width, g.height))
    val gap = 1.5f
    val wr = CornerRadius(g.ww * 0.14f)
    for (m in g.whites) {
        val r = g.rect(m)
        drawRoundRect(Brush.verticalGradient(listOf(WhiteTop, WhiteBottom), r.top, r.bottom), Offset(r.left + gap / 2, r.top), Size(r.width - gap, r.height), wr)
        fill(m)?.let { c -> drawRoundRect(Brush.verticalGradient(listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0.85f)), r.top, r.bottom),
            Offset(r.left + gap / 2, r.top), Size(r.width - gap, r.height), wr) }
        if (flash?.first == m && flash.second > 0f) drawRoundRect(Color.White.copy(alpha = 0.55f * flash.second), Offset(r.left + gap / 2, r.top), Size(r.width - gap, r.height), wr)
    }
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), g.top, g.top + g.height * 0.08f), Offset(0f, g.top), Size(g.width, g.height * 0.08f))
    for (m in (g.low..g.high).filter(::isBlack)) {
        val r = g.rect(m)
        val br = CornerRadius(g.bw * 0.18f)
        drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(r.left + g.bw * 0.12f, r.top), Size(r.width, r.height + 2f), br)
        // A lit black key stays a solid, darker shade of its colour (see-through, it would look white).
        val c = fill(m)?.let { lerp(BlackBottom, it.copy(alpha = 1f), 0.2f + 0.4f * it.alpha) }
        val brush = if (c == null) Brush.verticalGradient(listOf(BlackTop, BlackBottom), r.top, r.bottom)
            else Brush.verticalGradient(listOf(lerp(BlackBottom, c, 0.6f), c), r.top, r.bottom)
        drawRoundRect(brush, Offset(r.left, r.top), Size(r.width, r.height), br)
        drawRoundRect(Color.White.copy(alpha = if (c == null) 0.08f else 0.18f), Offset(r.left + r.width * 0.22f, r.top + r.height * 0.08f),
            Size(r.width * 0.56f, r.height * 0.78f), CornerRadius(r.width * 0.3f))
        if (flash?.first == m && flash.second > 0f) drawRoundRect(Color.White.copy(alpha = 0.5f * flash.second), Offset(r.left, r.top), Size(r.width, r.height), br)
    }
}

/** The stage's ripples over the keys in [g] at [nowNanos]: a key lit in its ripple's colour, a ring spreading from it. */
internal fun DrawScope.ripples(g: PianoGeometry, list: List<Ripple>, nowNanos: Long, still: Boolean) {
    for (r in list) {
        val p = ((nowNanos - r.startNanos) / 1e6f / StageFx.RIPPLE_MS).coerceIn(0f, 1f)
        if (p >= 1f) continue
        val m = r.midi?.coerceIn(g.low, g.high)
        val c = if (m == null) Offset(g.width / 2, g.top + g.height * 0.35f) else g.rect(m).let { Offset(it.center.x, it.top + it.height * 0.3f) }
        val fade = 1f - p
        if (r.fill && m != null) {
            // The key itself holds its colour, then lets go: the player's yellow or red, or gold for a key found.
            val k = g.rect(m)
            drawRoundRect(r.colour.copy(alpha = 0.9f * (1f - p * p)), Offset(k.left, k.top), Size(k.width, k.height), CornerRadius(g.ww * 0.14f))
        }
        val e = if (still) 0.5f else 1f - (1f - p) * (1f - p)
        val radius = g.ww * (0.6f + 2.8f * e)
        val w = (6f * fade + 2f) * density
        drawCircle(r.colour.copy(alpha = 0.25f * fade), radius, c)
        drawCircle(Color.Black.copy(alpha = 0.35f * fade), radius, c, style = Stroke(w + 2f * density))
        drawCircle(r.colour.copy(alpha = fade), radius, c, style = Stroke(w))
    }
}

/**
 * A big keyboard from [low] to [high] (white keys at both ends) to look at and to tap: [fill] washes keys in a colour,
 * the [last] key found flashes, [fx]'s ripples answer every sound, and a tap calls [onKey]. [overlay] draws on top, in
 * the same geometry.
 */
@Composable
internal fun BigKeys(low: Int, high: Int, description: String, modifier: Modifier = Modifier, fill: (Int) -> Color? = { null }, last: Struck? = null,
                     onKey: ((Int) -> Unit)? = null, topRoom: Dp = 0.dp, fx: StageFx? = null, overlay: DrawScope.(PianoGeometry) -> Unit = {}) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val room = with(androidx.compose.ui.platform.LocalDensity.current) { topRoom.toPx() }
    val geo = remember(low, high, size, room) { if (size.width == 0) null else PianoGeometry(low, high, size.width.toFloat(), size.height - room, room) }
    val flash = remember { Animatable(0f) }
    val still = reducedMotion()
    LaunchedEffect(last?.seq) { if (last != null && last.result != InputResult.WRONG) { flash.snapTo(1f); flash.animateTo(0f, if (still) snap(400) else tween(600)) } }
    // The ripples' clock: runs only while one lives.
    var clock by remember { mutableLongStateOf(0L) }
    val newest = fx?.ripples?.lastOrNull()?.id
    LaunchedEffect(fx, newest) {
        val f = fx ?: return@LaunchedEffect
        while (f.ripples.any { System.nanoTime() - it.startNanos < StageFx.RIPPLE_MS * 1_000_000 }) withFrameNanos { clock = System.nanoTime() }
        clock = System.nanoTime()
    }
    val tap by rememberUpdatedState(onKey)
    Canvas(modifier.onSizeChanged { size = it }.semantics { contentDescription = description }.pointerInput(geo, onKey != null) {
        val g = geo ?: return@pointerInput
        if (onKey == null) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            g.keyAt(down.position.x, down.position.y)?.let { down.consume(); tap?.invoke(it) }
        }
    }) {
        val g = geo ?: return@Canvas
        piano(g, fill, last?.takeIf { it.result != InputResult.WRONG }?.let { it.midi to flash.value })
        overlay(g)
        if (fx != null) ripples(g, fx.ripples, maxOf(clock, 1L), still)
    }
}

// ---- Small pieces -----------------------------------------------------------------------------------------------------

/** One star per round, filled as rounds are done (a round let go after three tries gets a smaller one): no number. */
@Composable
internal fun RoundStars(rounds: Int, done: Int, modifier: Modifier = Modifier, letGo: List<Int> = emptyList()) {
    Canvas(modifier.size(width = (rounds * 22).dp, height = 22.dp).semantics { contentDescription = "$done of $rounds done" }) {
        val r = size.height * 0.42f
        for (i in 0 until rounds) {
            val c = Offset(size.height / 2 + i * size.height, size.height / 2)
            when {
                i in letGo -> { drawPath(star(c, r), Ink.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx())); drawPath(star(c, r * 0.6f), PupGold) }
                i < done -> drawPath(star(c, r), PupGold)
                else -> drawPath(star(c, r), Ink.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/** A five-pointed star around [c]. */
internal fun star(c: Offset, r: Float, inner: Float = 0.45f): Path = Path().apply {
    for (k in 0 until 10) {
        val a = Math.PI * (k / 5.0) - Math.PI / 2
        val rr = if (k % 2 == 0) r else r * inner
        val x = c.x + (rr * Math.cos(a)).toFloat(); val y = c.y + (rr * Math.sin(a)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A short line the pup calls out inside a stage, in a small gold-edged bubble (said aloud between rounds). */
@Composable
internal fun StageLine(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Box(modifier.background(Color(0xFF2A221C), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(text, color = Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

/** The stage's top row: the round stars on the left, anything else on the right. */
@Composable
internal fun StageTop(rounds: Int, done: Int, modifier: Modifier = Modifier, letGo: List<Int> = emptyList(), end: @Composable () -> Unit = {}) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        RoundStars(rounds, done, letGo = letGo)
        end()
    }
}

/** Keeps a short line on screen for [ms] after [key] changes. */
@Composable
internal fun rememberFleeting(key: Any?, text: String?, ms: Long = 1600): String? {
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(key) { shown = text; if (text != null) { delay(ms); shown = null } }
    return shown
}

/** The line a stage shows now: the pup's latest (a call-out, or "I heard you!" for a sound with no key) for a while, then [default]. */
@Composable
internal fun stageLine(st: StageState, default: String?): String? =
    rememberFleeting(st.lineSeq, st.line, ms = (st.lineMs * FxSlow.factor).toLong()) ?: default
