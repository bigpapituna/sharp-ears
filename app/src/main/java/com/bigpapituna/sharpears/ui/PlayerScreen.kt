package com.bigpapituna.sharpears.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.KeyLabels
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.LaneGeometry
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Recap
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.Transport
import com.bigpapituna.sharpears.engine.fitRange
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Perform mode starts this far (song ms) before the first note, so it can be played on time. */
private const val LEAD_IN_MS = 2000.0

/** How the player opens. [inject] (debug) replaces the microphone with a WAV file or "synth:..." notes. */
data class PlayerStart(
    val mode: Mode = Mode.LISTEN,
    val practice: Practice = Practice.BOTH,
    val mic: Boolean = false,
    val inject: String? = null,
    val tabletop: Boolean = false,
)

/**
 * The player inside a lesson step: no orientation lock, no back handling, and instead of the transport bar a
 * single row with play/pause, the step's fixed hand and mode ([state]) and the mic. [labels] false hides note
 * names and finger numbers (playing from memory). With [autoStart] the run starts once the voice has finished.
 */
data class Embedded(val state: String, val labels: Boolean = true, val autoStart: Boolean = true)

/**
 * Landscape player: transport bar, falling-notes lane, keyboard, and the song's tutorial video when it has one.
 * Listen plays the song; Wait holds at each note group until it is played; Perform never waits and scores.
 * In tabletop posture the lane (and video) sit above the hinge and the keyboard below it.
 * [meta] is null for generated songs (mic check, progressions), which keep no progress.
 * [startMs] and [debugDump] support adb checks: open frozen at a time and log every visible note's rectangle.
 */
@Composable
fun PlayerScreen(
    song: Song, meta: SongMeta?, sampler: Sampler, midi: MidiDevices, mic: MicInput, voice: Voice, prefs: Prefs, ready: Boolean,
    startMs: Double, debugDump: Boolean, start: PlayerStart = PlayerStart(), onBack: () -> Unit,
    /** Called when a run reaches the end with its mistakes (and the Perform recap); lesson steps use it. */
    onFinished: (Report) -> Unit = {},
    embedded: Embedded? = null,
    modifier: Modifier = Modifier,
    /** The song's chords by bar (library songs with a chord pack); a Chords pill shows them on the lane. */
    chordTrack: com.bigpapituna.sharpears.engine.ChordTrack? = null,
) {
    val activity = LocalActivity.current ?: return
    if (embedded == null) DisposableEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val labels = embedded?.labels ?: true

    val range = remember(song) { fitRange(song.notes) }
    val transport = remember(song) { Transport(song, playable = range).apply { mode = start.mode; practice = start.practice } }
    var position by remember { mutableDoubleStateOf(0.0) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableDoubleStateOf(1.0) }
    var mode by remember { mutableStateOf(start.mode) }
    var practice by remember { mutableStateOf(start.practice) }
    var micOn by remember { mutableStateOf(start.mic || start.inject != null) }
    var loopStart by remember { mutableStateOf<Double?>(null) }
    var loopEnd by remember { mutableStateOf<Double?>(null) }
    var version by remember { mutableIntStateOf(0) }              // bumps when sounding/expected/waiting change
    val touched = remember { mutableStateMapOf<Int, Color>() }
    val held = remember { mutableStateMapOf<Int, Color>() }      // keys held on the MIDI keyboard
    val flashes = remember { mutableStateMapOf<Int, Color>() }    // short green flash on a correct key
    var wrongFlash by remember { mutableIntStateOf(0) }           // bumps on each wrong note
    var recap by remember { mutableStateOf<Pair<Recap, Boolean>?>(null) }   // Perform result, new best
    val scope = rememberCoroutineScope()

    val tutorial = remember(song) {
        meta?.videos?.takeIf { it.isNotEmpty() }?.let { TutorialState(song.id, it, prefs, song.notes.first().startMs) }
    }
    val windowSize = LocalWindowInfo.current.containerSize
    val windowHeightDp = with(LocalDensity.current) { windowSize.height.toDp() }
    // On by default on the unfolded inner screen (both sides at least 500 dp); the cover screen is too short.
    // The tutorial opens only when asked for (Video button), as a floating window over the notes.
    var videoOn by remember { mutableStateOf(false) }
    val fingers = remember(song) { com.bigpapituna.sharpears.engine.Fingering.of(song) }

    // Metronome (every song) and backing (progressions: the app plays the left-hand root, the right hand is yours).
    val context = androidx.compose.ui.platform.LocalContext.current
    val clicks = remember { lazy { com.bigpapituna.sharpears.audio.Clicks(context) } }
    DisposableEffect(Unit) { onDispose { if (clicks.isInitialized()) clicks.value.release() } }
    val metro = remember(song) { com.bigpapituna.sharpears.engine.Metronome(song.bpm, song.timeSignature) }
    val isProgression = song.id.startsWith("prog-")
    var metronomeOn by remember { mutableStateOf(false) }
    var chordsOn by remember { mutableStateOf(false) }
    var ramp by remember { mutableStateOf<com.bigpapituna.sharpears.engine.TempoRamp?>(null) }
    var rampPercent by remember { mutableIntStateOf(0) }
    var backingOn by remember { mutableStateOf(false) }

    // While the mic listens, the app stays silent (it would hear itself), unless headphones are on.
    // A synced tutorial video carries the music itself.
    fun soundAllowed() = prefs.sound && (!(micOn && mode != Mode.LISTEN) || mic.headphonesOn()) && !(videoOn && tutorial?.anchor != null)
    fun apply(events: List<NoteEvent>) {
        val sound = soundAllowed()
        events.forEach { e -> when (e) { is NoteEvent.On -> if (sound) sampler.noteOn(e.midi, e.velocity); is NoteEvent.Off -> sampler.noteOff(e.midi) } }
        version++
    }
    fun savePosition() {
        if (meta != null && transport.positionMs > 0 && transport.positionMs < song.durationMs) prefs.savePosition(meta.id, transport.positionMs.toLong())
    }
    // Practice time: real time while the transport plays, written in whole seconds (every 15 s and when it stops).
    val practiceMs = remember { doubleArrayOf(0.0) }
    fun flushPractice() {
        val secs = (practiceMs[0] / 1000).toInt()
        if (secs <= 0) return
        practiceMs[0] -= secs * 1000.0
        prefs.addPractice(java.time.LocalDate.now(), secs)
        Log.d(TAG, "practice +$secs s")
    }
    DisposableEffect(Unit) { onDispose { flushPractice() } }

    fun seek(ms: Double) { apply(transport.seek(ms)); position = transport.positionMs }
    fun pause() { apply(transport.pause()); playing = false; savePosition(); flushPractice(); Log.d(TAG, "transport pause at ${transport.positionMs.toLong()} ms") }
    fun play() {
        recap = null
        // A count-in bar with the backing, or with the metronome in Perform mode; otherwise Perform's 2 s lead-in.
        val leadIn = when {
            backingOn || (metronomeOn && transport.mode == Mode.PERFORM) -> metro.barMs
            transport.mode == Mode.PERFORM -> LEAD_IN_MS
            else -> 0.0
        }
        if (leadIn > 0 && transport.positionMs <= 0.0) Log.d(TAG, if (leadIn == metro.barMs) "count-in ${leadIn.toLong()} ms (${metro.beatsPerBar} beats)" else "lead-in ${leadIn.toLong()} ms")
        transport.play(leadInMs = leadIn); playing = true
        Log.d(TAG, "transport play from ${transport.positionMs.toLong()} ms speed=${transport.speed} mode=${transport.mode} hands=${transport.practice}")
    }
    fun setLoop(a: Double?, b: Double?) {
        loopStart = a; loopEnd = b
        transport.loop = if (a != null && b != null && b > a) a..b else null
        Log.d(TAG, "loop ${transport.loop?.let { "${it.start.toLong()}..${it.endInclusive.toLong()} ms" } ?: "off (A=${a?.toLong()} B=${b?.toLong()})"}")
    }
    fun setMode(m: Mode) { apply(transport.pause()); playing = false; transport.mode = m; mode = m; recap = null; Log.d(TAG, "mode=$m") }
    fun keyStruck(midi: Int, latencyMs: Double = 0.0) {
        val result = transport.noteInput(midi, latencyMs)
        Log.d(TAG, "input ${noteName(midi)} -> $result at ${transport.positionMs.toLong()} ms" + (if (latencyMs > 0) " latency=${latencyMs.toInt()}ms" else ""))
        when (result) {
            InputResult.WRONG -> { wrongFlash++; Log.d(TAG, "redFlash") }
            InputResult.CORRECT, InputResult.GROUP_DONE -> scope.launch { flashes[midi] = Good; delay(200); flashes.remove(midi) }
            InputResult.IGNORED -> {}
        }
        version++
    }
    fun setSpeed(sp: Double) { speed = sp; transport.speed = sp; Log.d(TAG, "transport speed=$sp") }
    /** A loop pass (or the whole song) was played through: the ramp may speed up. */
    fun passEnded(r: Report) {
        val rr = ramp ?: return
        val up = rr.passEnded(transport.mode, r)
        Log.d(TAG, "ramp: pass ${if (rr.clean(transport.mode, r)) "clean" else "not clean"} (wrong=${r.wrongKeys} accuracy=${r.recap?.accuracy}) -> ${rr.percent}%")
        if (up != null) { setSpeed(up); rampPercent = rr.percent; voice.speak("Faster: ${rr.percent} percent.") }
    }
    fun finished() {
        if (transport.loop == null) passEnded(transport.report())
        val score = transport.score
        val run = if (transport.mode == Mode.PERFORM && score != null) score.recap() else null
        onFinished(transport.report().also { Log.d(TAG, "run report ${song.id}: $it") })
        if (meta == null) {                                // generated songs (lessons, progressions) keep no progress
            run?.let { recap = it to false }
            Log.d(TAG, "finished ${song.id} mode=${transport.mode} recap=$run")
            return
        }
        if (run != null) {
            val r = run
            // Only a run from the top counts as a best score.
            val best = transport.scoreFromMs <= 0.0 && r.accuracy > 0 && prefs.saveScore(meta.id, r.accuracy)
            recap = r to best
            Log.d(TAG, "perform recap ${song.id}: $r accuracy=${r.accuracy} from=${transport.scoreFromMs.toLong()} newBest=$best")
        } else {
            prefs.markCompleted(meta.id)
            Log.d(TAG, "completed ${song.id} mode=${transport.mode}")
        }
    }

    // MIDI keyboard: same path as touch, but no app sound, since the piano makes its own.
    // With the mic on, MIDI only judges: it is logged as truth and the mic alone drives the song.
    // ponytail: MIDI never plays through the sampler; add a setting if a silent controller keyboard shows up
    LaunchedEffect(midi) {
        midi.events.collect { e ->
            when (e) {
                is NoteEvent.On -> {
                    held[e.midi] = RightHandColor
                    if (micOn) Log.d(TAG, "truth NoteOn ${noteName(e.midi)} midi=${e.midi} vel=${e.velocity}")
                    else { Log.d(TAG, "NoteOn ${noteName(e.midi)} midi=${e.midi} vel=${e.velocity} src=midi"); keyStruck(e.midi) }
                }
                is NoteEvent.Off -> held.remove(e.midi)
            }
        }
    }

    // Microphone: runs while it is switched on, in wait or perform mode, with the app on screen.
    val visible = onScreen()
    LaunchedEffect(visible) { if (!visible) savePosition() }
    // The mic waits while the voice talks through the speaker (it would hear the phone).
    val voiceHolds = voice.holdsMic
    DisposableEffect(micOn, mode, visible, voiceHolds, embedded == null || playing) {
        if (voiceHolds && micOn) Log.d(TAG, "mic: waiting for the voice")
        // Inside a lesson the mic listens only while the step runs, so the voice can speak between runs.
        if (micOn && mode != Mode.LISTEN && visible && !voiceHolds && (embedded == null || playing)) {
            if (start.inject != null) mic.startInjection(start.inject)
            else if (!mic.start()) { Log.d(TAG, "mic: could not start"); micOn = false }
        }
        if (micOn) midi.onTruth = mic::onMidiTruth
        onDispose { mic.stop(); midi.onTruth = null }
    }
    LaunchedEffect(version, micOn) {
        transport.chordSlack = if (micOn) 1 else 0
        mic.matcher.expected = if (micOn) transport.listenFor else emptySet()
        mic.matcher.group = transport.listenGroup
    }
    LaunchedEffect(mic) {
        mic.events.collect { e ->
            if (!micOn) return@collect
            when (e) {
                is MicEvent.Strike -> {
                    Log.d(TAG, "NoteOn ${noteName(e.midi)} midi=${e.midi} src=mic rise=${"%.1f".format(e.riseDb)}dB delay=${e.delayMs?.let { "%.0fms".format(it) } ?: "?"}" +
                        (e.inferredFrom?.let { " (inferred from ${noteName(it)})" } ?: ""))
                    keyStruck(e.midi, latencyMs = e.delayMs ?: 0.0)
                }
                is MicEvent.Wrong -> if (transport.waiting) {
                    if (mic.wrongFlash) wrongFlash++
                    Log.d(TAG, "input mic -> WRONG at ${transport.positionMs.toLong()} ms (flash=${mic.wrongFlash})")
                }
            }
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.d(TAG, "mic permission granted=$granted")
        if (granted) { micOn = true; prefs.micDefault = true; if (mode == Mode.LISTEN && embedded == null) setMode(Mode.WAIT) }
    }
    fun toggleMic() {
        if (micOn) { micOn = false; prefs.micDefault = false; Log.d(TAG, "mic off"); return }
        if (!mic.hasPermission()) { askMic.launch(Manifest.permission.RECORD_AUDIO); return }
        micOn = true; prefs.micDefault = true
        // The mic listens in wait and perform mode.
        if (mode == Mode.LISTEN && embedded == null) setMode(Mode.WAIT)
        Log.d(TAG, "mic on")
    }
    LaunchedEffect(song) {
        if (startMs > 0) seek(startMs)
        Log.d(TAG, "player open ${song.id} range=${noteName(range.first)}..${noteName(range.last)} mode=$mode hands=$practice mic=$micOn at=${startMs.toLong()} video=${tutorial != null}")
        if (start.inject != null && embedded == null) {
            // Debug injection runs hands-free, starting with the injected audio (rendering it takes a moment).
            while (mic.matcher.samplesProcessed == 0L) delay(5)
            play()
        }
    }
    DisposableEffect(song) { onDispose { savePosition(); apply(transport.pause()) } }
    if (embedded == null) BackHandler(onBack = onBack)
    // A lesson step starts by itself once the voice has read it out (and the sounds are loaded).
    var autoStarted by remember { mutableStateOf(false) }
    val voiceSpeaking = voice.speaking.value
    if (embedded?.autoStart == true) LaunchedEffect(ready, voiceSpeaking) {
        if (autoStarted || !ready || voiceSpeaking) return@LaunchedEffect
        delay(700)
        autoStarted = true
        Log.d(TAG, "lesson step auto-start ${song.id} mode=$mode")
        play()
    }

    // Frame loop: tick the transport with the real frame delta while playing.
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        var wasWaiting = transport.waiting
        var listening = transport.listenFor
        var passes = transport.passes
        while (transport.playing) {
            withFrameNanos { now ->
                val before = transport.positionMs
                practiceMs[0] += (now - last) / 1_000_000.0
                if (practiceMs[0] >= 15_000) flushPractice()
                val events = transport.tick((now - last) / 1_000_000.0)
                if (events.isNotEmpty()) apply(events)
                // Clicks come from the phone's speaker, so they follow the same rule as the sampler.
                if ((metronomeOn || backingOn) && transport.positionMs > before && soundAllowed())
                    metro.clicks(before, transport.positionMs).forEach { clicks.value.play(it) }
                if (transport.positionMs < before) Log.d(TAG, "loop wrap ${before.toLong()} -> ${transport.positionMs.toLong()} ms")
                if (transport.passes != passes) { passes = transport.passes; transport.lastPass?.let(::passEnded) }
                last = now
                position = transport.positionMs
                if (transport.waiting != wasWaiting) {
                    wasWaiting = transport.waiting
                    version++
                    if (wasWaiting) Log.d(TAG, "wait at ${transport.positionMs.toLong()} ms expect=${transport.expected.map { noteName(it.midi) }}")
                }
                // Perform mode: what the mic listens for moves with the song.
                if (mode == Mode.PERFORM && transport.listenFor != listening) { listening = transport.listenFor; version++ }
            }
        }
        playing = false
        flushPractice()
        Log.d(TAG, "transport stopped at ${transport.positionMs.toLong()} ms")
        if (transport.positionMs >= song.durationMs) finished()
    }

    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var laneBox by remember { mutableStateOf<Rect?>(null) }
    val tabletop = rememberTabletop(force = start.tabletop)
    var rootTopPx by remember { mutableFloatStateOf(0f) }
    val waitingNow = remember(version) { transport.waiting }
    val moving = playing && !waitingNow && transport.positionMs >= 0
    val showVideo = videoOn && tutorial != null

    @Composable
    fun Bar() = TransportBar(
        BarState(playing, ready, position.coerceAtLeast(0.0), song.durationMs, speed, mode, practice, loopStart, loopEnd, song.sections,
            midi.connected.value, micOn, { mic.level.floatValue }, title = song.title,
            // Under the title: the composer, and the part of the song the playhead is in (medleys, progressions).
            subtitle = listOfNotNull(meta?.composer?.takeIf { it.isNotEmpty() },
                song.sections.lastOrNull { it.startMs <= position }?.name).joinToString(", "),
            videoOn = if (tutorial != null) videoOn else null,
            metronome = metronomeOn,
            ramp = if (ramp != null) rampPercent else null,
            chords = if (chordTrack != null) chordsOn else null,
            backing = if (isProgression) backingOn else null,
            status = when {
                mode != Mode.PERFORM -> ""
                position < 0 -> "Get ready…"
                else -> transport.score?.let { "Streak ${it.streakNow}" } ?: ""
            }),
        BarActions(
            onBack = onBack,
            onPlayPause = { if (playing) pause() else play() },
            onSpeed = { ramp = null; setSpeed(it) },
            onRamp = { ramp = com.bigpapituna.sharpears.engine.TempoRamp().also { setSpeed(it.speed); rampPercent = it.percent }; Log.d(TAG, "ramp on at 50%") },
            onMode = ::setMode,
            onPractice = { practice = it; transport.practice = it; version++; Log.d(TAG, "hands=$it") },
            onSeek = { seek(it) },
            onLoop = {
                when {
                    loopStart == null -> setLoop(transport.positionMs.coerceAtLeast(0.0), null)
                    loopEnd == null -> setLoop(loopStart, transport.positionMs)
                    else -> setLoop(null, null)
                }
            },
            onMic = ::toggleMic,
            onVideo = { videoOn = !videoOn; Log.d(TAG, "video panel=$videoOn") },
            onChords = { chordsOn = !chordsOn; Log.d(TAG, "chord names=$chordsOn") },
            onMetronome = { metronomeOn = !metronomeOn; Log.d(TAG, "metronome=$metronomeOn bpm=${song.bpm} beat=${metro.beatMs.toLong()} ms") },
            onBacking = {
                backingOn = !backingOn
                // The backing plays the left hand; the right hand is the player's.
                if (backingOn) { practice = Practice.RIGHT; transport.practice = Practice.RIGHT; version++ }
                Log.d(TAG, "backing=$backingOn hands=${transport.practice}")
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    @Composable
    fun Lane(modifier: Modifier) = androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        NoteLane(
            song = song, positionMs = position, lowMidi = range.first, highMidi = range.last,
            dimmed = { mode != Mode.LISTEN && practice != Practice.BOTH && !transport.required(it) },
            onScrubStart = { wasPlayingBeforeScrub = playing; if (playing) pause() },
            onScrub = { seek(transport.positionMs + it) },
            onScrubEnd = { Log.d(TAG, "scrub to ${transport.positionMs.toLong()} ms"); if (wasPlayingBeforeScrub) play() },
            modifier = Modifier.fillMaxSize().onGloballyPositioned { c ->
                if (debugDump) laneBox = Rect(c.positionInWindow(), c.size.toSize())
            },
            fingers = fingers, showNames = labels && prefs.noteNames && prefs.version.intValue >= 0, showFingers = labels && prefs.fingerNumbers,
        )
        if (chordsOn && chordTrack != null) ChordLabel(chordTrack, position, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 10.dp))
        if (showVideo) TutorialPip(tutorial!!, moving, speed, micOn && !mic.headphonesOn(), { transport.positionMs },
            onClose = { videoOn = false; Log.d(TAG, "video closed") }, maxHeight = maxHeight - 20.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        if (embedded == null) recap?.let { (r, best) ->
            Dialog(onDismissRequest = { recap = null }) {
                RecapCard(r, best, speed, onAgain = { recap = null; seek(0.0); play() }, onClose = { recap = null }, modifier = Modifier)
            }
        }
    }

    @Composable
    fun Keys(modifier: Modifier) {
        val expected = remember(version) { transport.expected }
        val sounding = remember(version) { transport.sounding.toList() }
        val lit: Map<Int, Color> = buildMap {
            sounding.forEach { put(it.midi, handColor(it.hand)) }
            putAll(touched)
            putAll(held)
            putAll(flashes)
        }
        var wrongVisible by remember { mutableStateOf(false) }
        LaunchedEffect(wrongFlash) { if (wrongFlash > 0) { wrongVisible = true; delay(150); wrongVisible = false } }
        val keyLabels = remember(prefs.version.intValue, range) { if (labels) keyLabels(prefs.keyLabels, range) else emptyMap() }
        Keyboard(
            lowMidi = range.first, highMidi = range.last, lit = lit,
            onNoteOn = { m, v ->
                Log.d(TAG, "NoteOn ${noteName(m)} midi=$m vel=$v src=touch")
                touched[m] = RightHandColor; if (prefs.sound && (!micOn || mic.headphonesOn())) sampler.noteOn(m, v); keyStruck(m)
            },
            onNoteOff = { m -> Log.d(TAG, "NoteOff ${noteName(m)} midi=$m src=touch"); touched.remove(m); sampler.noteOff(m) },
            modifier = modifier.then(if (wrongVisible) Modifier.border(4.dp, Bad) else Modifier),
            pulsing = remember(expected) { expected.associate { it.midi to handColor(it.hand) } },
            // The key the song waits for shows its suggested finger.
            labels = if (labels && prefs.fingerNumbers) keyLabels + expected.mapNotNull { n -> fingers[n]?.let { n.midi to "$it" } } else keyLabels,
        )
    }

    // Debug: once the frozen screen settles, log each visible note's rectangle in window pixels for adb checks.
    LaunchedEffect(laneBox, position, playing) {
        val box = laneBox ?: return@LaunchedEffect
        if (playing) return@LaunchedEffect
        delay(400)
        val lane = LaneGeometry(box.height)
        val keys = KeyboardLayout(range.first, range.last, box.width, box.height)
        Log.d(TAG, "laneDump pos=${position.toLong()} top=${box.top.toInt()} height=${box.height.toInt()} pxPerMs=${lane.pxPerMs}")
        lane.visible(song, position, song.notes.maxOf { it.durationMs }).filter { it.midi in range }.forEach { n ->
            val k = keys.rect(n.midi)
            Log.d(TAG, "laneNote ${noteName(n.midi)} hand=${n.hand} x=${(box.left + k.centerX).toInt()} " +
                "top=${(box.top + lane.topY(n, position)).toInt()} bottom=${(box.top + lane.bottomY(n, position)).toInt()}")
        }
    }

    if (embedded != null) {
        Column(modifier.background(LaneBackground)) {
            EmbeddedBar(embedded.state, playing, ready, micOn, onPlayPause = { if (playing) pause() else play() }, onMic = ::toggleMic,
                status = when {
                    mode != Mode.PERFORM || !playing -> ""
                    position < 0 -> "Get ready"
                    else -> transport.score?.let { "Streak ${it.streakNow}" } ?: ""
                })
            Lane(Modifier.fillMaxWidth().weight(1f))
            Keys(Modifier.fillMaxWidth().weight(0.45f))
        }
        return
    }

    val density = LocalDensity.current
    Column(Modifier.fillMaxSize().background(LaneBackground).safeDrawingPadding()
        .onGloballyPositioned { rootTopPx = it.positionInWindow().y }) {
        val muted = micOn && !mic.headphonesOn()
        if (tabletop != null) {
            // Above the hinge: video and lane. Below: controls and keyboard, the keyboard under the lane so columns line up.
            val topH = with(density) { (tabletop.hingeTopPx - rootTopPx).coerceAtLeast(120f).toDp() }
            val gap = with(density) { (tabletop.hingeBottomPx - tabletop.hingeTopPx).toDp() }
            Lane(Modifier.fillMaxWidth().height(topH))
            Spacer(Modifier.height(gap))
            Bar()
            Keys(Modifier.fillMaxWidth().weight(1f))
        } else {
            Bar()
            Lane(Modifier.fillMaxWidth().weight(1f))
            Keys(Modifier.fillMaxWidth().weight(if (windowHeightDp >= 500.dp) 0.3f else 0.45f))
        }
    }
}

@Composable
private fun RecapCard(r: Recap, newBest: Boolean, speed: Double, onAgain: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Card(modifier.widthIn(max = 420.dp), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (newBest) "New best" else "Run complete", style = MaterialTheme.typography.labelLarge,
                color = if (newBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${r.accuracy}%", style = Numeric.merge(MaterialTheme.typography.displayMedium))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) { RecapStat("Good", r.good); RecapStat("OK", r.ok); RecapStat("Missed", r.miss) }
            Text(
                when {
                    r.good + r.ok == 0 -> "No notes hit in time yet."
                    abs(r.biasMs) < 25 -> "Timing: right on the beat."
                    r.biasMs > 0 -> "Timing: ${r.biasMs} ms late on average."
                    else -> "Timing: ${-r.biasMs} ms early on average."
                } + "  Longest streak ${r.longestStreak}." + if (speed < 1.0) "  (at ${speed}x)" else "",
                style = MaterialTheme.typography.bodyMedium, color = InkDim, textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAgain) { Text("Play again") }
                OutlinedButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}

@Composable
private fun RecapStat(label: String, value: Int) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("$value", style = Numeric.merge(MaterialTheme.typography.titleLarge))
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Key labels from the setting: every C with its octave, or every white key's letter as well. */
private fun keyLabels(setting: KeyLabels, range: IntRange): Map<Int, String> = when (setting) {
    KeyLabels.OFF -> emptyMap()
    KeyLabels.C_ONLY -> range.filter { it % 12 == 0 }.associateWith { noteName(it) }
    KeyLabels.ALL -> range.filter { !isBlack(it) }.associateWith { if (it % 12 == 0) noteName(it) else noteName(it).filter(Char::isLetter) }
}

private fun handColor(h: Hand) = if (h == Hand.RIGHT) RightHandColor else LeftHandColor


/** The lesson player's one row: play/pause, what the step asks (fixed by the step), live status, and the mic. */
@Composable
private fun EmbeddedBar(state: String, playing: Boolean, ready: Boolean, micOn: Boolean, onPlayPause: () -> Unit, onMic: () -> Unit, status: String) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.material3.FilledIconButton(onClick = onPlayPause, enabled = ready, modifier = Modifier.size(44.dp)) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(if (playing) com.bigpapituna.sharpears.R.drawable.ic_pause else com.bigpapituna.sharpears.R.drawable.ic_play_arrow),
                contentDescription = if (!ready) "Loading sounds" else if (playing) "Pause" else "Play")
        }
        Text(state, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 1, modifier = Modifier.weight(1f))
        if (status.isNotEmpty()) Text(status, style = Numeric.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.primary, maxLines = 1)
        androidx.compose.material3.IconToggleButton(micOn, { onMic() }) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(if (micOn) com.bigpapituna.sharpears.R.drawable.ic_mic else com.bigpapituna.sharpears.R.drawable.ic_mic_off),
                contentDescription = "Microphone", tint = if (micOn) MaterialTheme.colorScheme.primary else InkDim)
        }
    }
}

/** The bar's chord in the lane's corner, quiet, with the next chord dimmed beside it. Lookup only, no relabeling. */
@Composable
private fun ChordLabel(track: com.bigpapituna.sharpears.engine.ChordTrack, positionMs: Double, modifier: Modifier) {
    val (now, next) = track.at(positionMs.coerceAtLeast(0.0))
    val name = now?.name
    LaunchedEffect(name) { if (name != null) Log.d(TAG, "chord label $name at ${positionMs.toLong()} ms") }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (name != null) Text(name, style = MaterialTheme.typography.titleLarge, color = Ink.copy(alpha = 0.78f))
        next?.let { Text(it.name, style = MaterialTheme.typography.titleSmall, color = InkDim.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 2.dp)) }
    }
}
