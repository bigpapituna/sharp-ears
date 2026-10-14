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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.bigpapituna.sharpears.engine.progress.ProgressEvent
import com.bigpapituna.sharpears.engine.Recap
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.Transport
import com.bigpapituna.sharpears.engine.transposed
import com.bigpapituna.sharpears.engine.AutoSpeed
import com.bigpapituna.sharpears.engine.DynamicsGrader
import com.bigpapituna.sharpears.engine.PedalGrader
import com.bigpapituna.sharpears.engine.RunRecorder
import com.bigpapituna.sharpears.engine.RunRecording
import com.bigpapituna.sharpears.data.RunStore
import com.bigpapituna.sharpears.engine.TroubleSpot
import com.bigpapituna.sharpears.engine.TroubleSpots
import com.bigpapituna.sharpears.engine.fitRange
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Before Play, the run's first notes sit this far (song ms) above the hit line: just under the top of the lane. */
private const val PREVIEW_MS = com.bigpapituna.sharpears.engine.LOOKAHEAD_MS * 0.75

/** How the player opens. [inject] (debug) replaces the microphone with a WAV file or "synth:..." notes. */
data class PlayerStart(
    val mode: Mode = Mode.LISTEN,
    val practice: Practice = Practice.BOTH,
    val mic: Boolean = false,
    val inject: String? = null,
    val tabletop: Boolean = false,
    /** Debug: stretches the hit-line lights so a screenshot can catch them. */
    val lightSlow: Float = 1f,
    /** Open straight into a wait-mode loop on this bar (1-based) at half speed, playing once the sounds are ready. */
    val loopBar: Int? = null,
    /** Stop after this much real time and call onTimeUp (the "Practice today" loop part). */
    val timeLimitMs: Long? = null,
    /** Beat lines on the lane whatever the setting (rhythm lessons). */
    val beatLines: Boolean = false,
    /** Debug: MIDI input played from the first Play (MidiDevices.debugPlay). */
    val midiScript: String? = null,
)

/**
 * The player inside a lesson step: no orientation lock, no back handling, and instead of the transport bar a
 * single row with play/pause, the step's fixed hand and mode ([state]) and the mic. [labels] false hides note
 * names and finger numbers (playing from memory). With [autoStart] the run starts once the voice has finished.
 */
data class Embedded(val state: String, val labels: Boolean = true, val autoStart: Boolean = true,
                    /** A ramp step: starts at 50%, plays again after each pass, ends only once the ramp reaches 100%. */
                    val ramp: Boolean = false,
                    /** A MIDI step: PEDAL (graded on the sustain pedal) or DYNAMICS (soft then loud). */
                    val special: com.bigpapituna.sharpears.engine.StepKind? = null)

/**
 * Landscape player: transport bar, falling-notes lane, keyboard, and the song's tutorial video when it has one.
 * Listen plays the song; Wait holds at each note group until it is played; Perform never waits and scores.
 * In tabletop posture the lane (and video) sit above the hinge and the keyboard below it.
 * [meta] is null for generated songs (mic check, progressions), which keep no progress.
 * [startMs] and [debugDump] support adb checks: open frozen at a time and log every visible note's rectangle.
 */
@Composable
fun PlayerScreen(
    original: Song, meta: SongMeta?, sampler: Sampler, midi: MidiDevices, mic: MicInput, voice: Voice, prefs: Prefs, ready: Boolean,
    startMs: Double, debugDump: Boolean, start: PlayerStart = PlayerStart(), onBack: () -> Unit,
    /** Called when a run reaches the end with its mistakes (and the Perform recap); lesson steps use it. */
    onFinished: (Report) -> Unit = {},
    embedded: Embedded? = null,
    modifier: Modifier = Modifier,
    /** The song's chords by bar (library songs with a chord pack); a Chords pill shows them on the lane. */
    chordTrack: com.bigpapituna.sharpears.engine.ChordTrack? = null,
    /** [PlayerStart.timeLimitMs] ran out. */
    onTimeUp: () -> Unit = {},
) {
    val activity = LocalActivity.current ?: return
    // Transpose (library songs keep it per song): every note moves, so the rest of the player sees one song.
    var transpose by remember(original.id) { mutableIntStateOf(meta?.let { prefs.transpose(it.id) } ?: 0) }
    val song = remember(original, transpose) { original.transposed(transpose) }
    /** Where a rebuilt transport (after a transpose) picks up. */
    var resumeAt by remember(original.id) { mutableStateOf<Double?>(null) }
    if (embedded == null) DisposableEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val labels = embedded?.labels ?: true

    val range = remember(song) { fitRange(song.notes) }
    // Chord names follow a transpose: the bars are labelled again from the moved notes.
    val chords = remember(song, chordTrack) {
        if (chordTrack == null || transpose == 0) chordTrack
        else com.bigpapituna.sharpears.engine.ChordTrack(com.bigpapituna.sharpears.engine.labelBarsAligned(song), com.bigpapituna.sharpears.engine.barMs(song))
    }
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
    var wrongFlash by remember { mutableIntStateOf(0) }           // bumps on each wrong note (light off)
    val lights = remember { HitLights(slow = start.lightSlow) }    // the hit-line light (on by default)
    val struggle = remember { com.bigpapituna.sharpears.engine.Struggle() }
    var highFive by remember { mutableStateOf(0) }                 // bumps when the player pushes through a hard passage
    var showHighFive by remember { mutableStateOf(false) }
    LaunchedEffect(highFive) { if (highFive > 0) { showHighFive = true; delay(2400); showHighFive = false } }
    // The notes the player plays in the current run (touch, MIDI or mic), kept for "Hear my run".
    val recorder = remember { RunRecorder() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val runs = remember { RunStore(context) }
    var lastRun by remember { mutableStateOf<RunRecording?>(null) }
    var replay by remember { mutableStateOf<RunRecording?>(null) }
    // MIDI steps: the sustain pedal against each chord, and soft-then-loud by velocity.
    val special = embedded?.special
    val pedalGrade = remember(song) { if (special == com.bigpapituna.sharpears.engine.StepKind.PEDAL) PedalGrader(PedalGrader.chordsOf(song)) else null }
    val loudness = remember(song) { if (special == com.bigpapituna.sharpears.engine.StepKind.DYNAMICS) DynamicsGrader(song.notes.count { it.hand == Hand.RIGHT }) else null }
    var pedalDown by remember { mutableStateOf(false) }
    var lastVelocity by remember { mutableIntStateOf(-1) }
    var scriptStarted by remember { mutableStateOf(false) }
    var recap by remember { mutableStateOf<RunEnd?>(null) }      // the end of a run: Perform recap and/or hardest bars
    val scope = rememberCoroutineScope()
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    // Touch keys buzz (right: a tick, wrong: a double tap); the mic and MIDI never do, the phone may be on a stand.
    val haptics = com.bigpapituna.sharpears.ui.motion.rememberHaptics().also { it.enabled = prefs.haptics }
    var touchRun by remember { mutableStateOf(false) }             // this run was played on the touch keys

    val tutorial = remember(song) {
        meta?.videos?.takeIf { it.isNotEmpty() }?.let { TutorialState(song.id, it, prefs, song.notes.first().startMs, song.groups.last().startMs) }
    }
    val windowSize = LocalWindowInfo.current.containerSize
    val windowHeightDp = with(LocalDensity.current) { windowSize.height.toDp() }
    // On by default on the unfolded inner screen (both sides at least 500 dp); the cover screen is too short.
    // The tutorial opens only when asked for (Video button), as a floating window over the notes.
    var videoOn by remember { mutableStateOf(false) }
    val fingers = remember(song) { com.bigpapituna.sharpears.engine.Fingering.of(song) }

    // Metronome (every song) and backing (progressions: the app plays the left-hand root, the right hand is yours).
    val clicks = remember { lazy { com.bigpapituna.sharpears.audio.Clicks(context) } }
    DisposableEffect(Unit) { onDispose { if (clicks.isInitialized()) clicks.value.release() } }
    val metro = remember(song) { com.bigpapituna.sharpears.engine.Metronome(song.bpm, song.timeSignature) }
    val isProgression = song.id.startsWith("prog-")
    var metronomeOn by remember { mutableStateOf(false) }
    var chordsOn by remember { mutableStateOf(false) }
    var ramp by remember { mutableStateOf<com.bigpapituna.sharpears.engine.TempoRamp?>(null) }
    // Auto speed: the tempo managed last time (library songs), nudged up on good days.
    var autoOn by remember { mutableStateOf(false) }
    var autoPercent by remember { mutableIntStateOf(meta?.let { prefs.autoSpeed(it.id)?.percent } ?: AutoSpeed.FIRST) }
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
        val at = transport.shownMs
        if (meta != null && at > 0 && at < song.durationMs) prefs.savePosition(meta.id, at.toLong())
    }
    // Practice time: real time while the transport plays, written in whole seconds (every 15 s and when it stops).
    val practiceMs = remember { doubleArrayOf(0.0) }
    fun flushPractice() {
        val secs = (practiceMs[0] / 1000).toInt()
        if (secs <= 0) return
        practiceMs[0] -= secs * 1000.0
        prefs.addPractice(java.time.LocalDate.now(), secs)
        progress?.record(ProgressEvent.Practice(java.time.LocalDateTime.now(), secs))      // after Prefs: the goal reads both
        Log.d(TAG, "practice +$secs s")
    }
    DisposableEffect(Unit) { onDispose { flushPractice() } }

    fun seek(ms: Double) { apply(transport.seek(ms)); position = transport.positionMs; lights.resetCombo() }
    // Stuck moments: wait mode on the same notes for 5 s or more with the mic on is kept (clip and report) for review.
    val stallWatch = remember { com.bigpapituna.sharpears.engine.StallWatch() }
    // The last minute or so of inputs and waits, for a "that wasn't right" report.
    val tail = remember { ArrayDeque<String>() }
    fun tail(line: String) {
        tail.addLast(java.time.LocalTime.now().toString().take(12) + "  " + line)
        while (tail.size > 60) tail.removeFirst()
    }
    fun at(ms: Double) = "%d:%02d".format((ms / 60000).toInt(), ((ms / 1000) % 60).toInt())
    // Keyboard as answer key (MIDI on USB with the mic on: the mic plays, the keyboard checks it). Mismatches close
    // together go into one clip, saved once things have been quiet for 3 s.
    val answerKey = remember { com.bigpapituna.sharpears.engine.AnswerKey() }
    val answerLines = remember { mutableListOf<String>() }
    val answerState = remember { longArrayOf(0, 0, 0) }        // clip mark, last mismatch time, song position then
    fun saveAnswers() {
        if (answerLines.isEmpty()) return
        val name = "answer-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + song.id
        val summary = answerLines.map { it.substringAfter("  ") }.groupingBy { it }.eachCount().entries.joinToString(", ") { (k, n) -> if (n > 1) "$k (x$n)" else k }
        val report = buildString {
            append("title=Mic vs keyboard: ${song.title} at ${at(answerState[2].toDouble())}: $summary\n")
            append("# sharp ears answer key: the keyboard (MIDI) says what was played, the mic disagreed\n")
            append("song=${song.id} title=${song.title} position_ms=${answerState[2]} speed=${transport.speed} hands=${transport.practice}\n")
            append("group=${transport.listenGroup.sorted().joinToString(",") { noteName(it) }} mic=${mic.source.value} sensitivity_db=${mic.matcher.config.sensitivityDb}\n")
            append("mismatches (clock time):\n"); answerLines.forEach { append("  ").append(it).append('\n') }
            append("the last minute (newest last):\n"); tail.forEach { append("  ").append(it).append('\n') }
        }
        Log.d(TAG, "answer key: ${answerLines.size} mismatch(es), saving $name")
        mic.saveStall(answerState[0] + 2L * com.bigpapituna.sharpears.audio.MicInput.RATE, report, name)
        answerLines.clear()
    }
    var flagging by remember { mutableStateOf(false) }
    var flagMark by remember { mutableStateOf(0L) }
    LaunchedEffect(micOn, midi.connected.value) {
        if (!micOn || midi.connected.value == null || !mic.keepRecordings) return@LaunchedEffect
        try {
            while (true) {
                delay(250)
                val now = System.currentTimeMillis()
                val found = answerKey.settle(now)
                if (found.isNotEmpty()) {
                    if (answerLines.isEmpty()) { answerState[0] = mic.clipMarkAgo(6); answerState[2] = transport.positionMs.toLong() }
                    found.forEach { m ->
                        answerLines += java.time.Instant.ofEpochMilli(m.atMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(12) + "  " +
                            (if (m.kind == com.bigpapituna.sharpears.engine.AnswerKey.Kind.MISSED) "missed " else "false ") + noteName(m.midi)
                    }
                    answerState[1] = now
                }
                if (answerLines.isNotEmpty() && (now - answerState[1] > 3_000 || answerLines.size >= 20)) saveAnswers()
            }
        } finally { saveAnswers() }
    }
    var flagAt by remember { mutableStateOf(0.0) }
    var flagContext by remember { mutableStateOf("") }      // what was going on when the flag was tapped
    val stallMark = remember { longArrayOf(0) }
    fun stallBegin() {
        if (!micOn || transport.mode != Mode.WAIT) return
        stallMark[0] = mic.clipMark()
        stallWatch.start(System.currentTimeMillis(), transport.expected.map { noteName(it.midi) },
            "song=${song.id} title=${song.title} position_ms=${transport.positionMs.toLong()} speed=${transport.speed} hands=${transport.practice}\n" +
                "group=${transport.listenGroup.sorted().joinToString(",") { noteName(it) }} mic=${mic.source.value} " +
                "sensitivity_db=${mic.matcher.config.sensitivityDb} recorded=${java.time.LocalDateTime.now()}")
    }
    fun stallEnd(outcome: String) {
        val s = stallWatch.end(System.currentTimeMillis(), outcome) ?: return
        val name = "stall-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + song.id
        Log.d(TAG, "stuck moment: ${s.waitedMs} ms on ${transport.expected.map { noteName(it.midi) }} ($outcome), saving $name")
        val title = "Stuck: ${song.title} at ${at(transport.positionMs)} on ${transport.expected.joinToString(", ") { noteName(it.midi) }} (${s.waitedMs / 1000} s, $outcome)"
        mic.saveStall(stallMark[0], "title=$title\n" + s.report, name)
    }
    fun pause() { stallEnd("paused"); apply(transport.pause()); playing = false; savePosition(); flushPractice(); Log.d(TAG, "transport pause at ${transport.positionMs.toLong()} ms") }
    fun play() {
        recap = null
        // A fresh run starts early so its first notes are seen falling: Perform counts in two bars (clicks and
        // a beat counter), the backing one bar, Wait and Listen run in two beats.
        val leadIn = when {
            transport.mode == Mode.PERFORM -> 2 * metro.barMs
            backingOn -> metro.barMs
            else -> 2 * metro.beatMs
        }
        if (transport.fresh) { recorder.clear(); touchRun = false }
        if (transport.fresh) Log.d(TAG, (if (transport.mode == Mode.PERFORM || backingOn) "count-in" else "lead-in") +
            " ${leadIn.toLong()} ms to the first group at ${transport.anchorMs.toLong()} ms (from ${transport.positionMs.toLong()} ms)")
        transport.play(leadInMs = leadIn); playing = true
        if (start.midiScript != null && !scriptStarted) { scriptStarted = true; midi.debugPlay(start.midiScript) }
        Log.d(TAG, "transport play from ${transport.positionMs.toLong()} ms speed=${transport.speed} mode=${transport.mode} hands=${transport.practice}")
    }
    fun setLoop(a: Double?, b: Double?) {
        loopStart = a; loopEnd = b
        transport.loop = if (a != null && b != null && b > a) a..b else null
        Log.d(TAG, "loop ${transport.loop?.let { "${it.start.toLong()}..${it.endInclusive.toLong()} ms" } ?: "off (A=${a?.toLong()} B=${b?.toLong()})"}")
    }
    fun setMode(m: Mode) { apply(transport.pause()); playing = false; transport.mode = m; mode = m; recap = null; Log.d(TAG, "mode=$m") }
    /** The hand of the song note on [midi] nearest the playhead (for the colour of its flash). */
    fun handOf(midi: Int): Hand {
        val pos = transport.positionMs
        var i = com.bigpapituna.sharpears.engine.firstStartAtOrAfter(song.notes, pos - 500)
        while (i < song.notes.size && song.notes[i].startMs <= pos + 500) { if (song.notes[i].midi == midi) return song.notes[i].hand; i++ }
        return Hand.RIGHT
    }
    fun wrongFeedback() {
        // In Perform only a miss breaks the streak (the bar's "Streak N"), so a wrong key leaves the combo standing.
        if (prefs.feedbackLight) { lights.wrong(breaksCombo = transport.mode != Mode.PERFORM); Log.d(TAG, "light wrong") } else { wrongFlash++; Log.d(TAG, "redFlash") }
    }
    /** The run as played so far, kept in memory and in files/runs (library songs). */
    fun keepRun() {
        if (meta == null || recorder.size == 0) return
        val r = recorder.take(meta.id, transport.speed)
        lastRun = r
        runs.save(r)
    }
    fun keyReleased(midi: Int) { if (transport.playing) recorder.off(midi, transport.positionMs) }
    fun keyStruck(midi: Int, latencyMs: Double = 0.0, velocity: Int = 80): InputResult {
        if (transport.playing && transport.mode != Mode.LISTEN) recorder.on(midi, transport.positionMs - latencyMs * transport.speed, velocity)
        // Perform: a note that raised the "good" count was on time (Perfect), any other right note was OK (Good).
        val good0 = transport.score?.recap()?.good ?: 0
        val result = transport.noteInput(midi, latencyMs)
        val grade = if (transport.mode == Mode.PERFORM) (if ((transport.score?.recap()?.good ?: 0) > good0) HitLights.PERFECT else HitLights.GOOD) else null
        Log.d(TAG, "input ${noteName(midi)} -> $result at ${transport.positionMs.toLong()} ms" + (if (latencyMs > 0) " latency=${latencyMs.toInt()}ms" else ""))
        tail("${noteName(midi)} -> $result at ${at(transport.positionMs)}" + (if (latencyMs > 0) " (mic, ${latencyMs.toInt()} ms late)" else ""))
        // Soft and loud: each right note is graded on its velocity, and the light says whether it was in range.
        if (loudness != null && (result == InputResult.CORRECT || result == InputResult.GROUP_DONE)) {
            val ok = loudness.note(velocity)
            lastVelocity = velocity
            Log.d(TAG, "loudness ${noteName(midi)} vel=$velocity ${if (ok) "in range" else "out of range"} (${loudness.inRange()}/${loudness.velocities.size})")
            if (ok) lights.hit(midi, handColor(Hand.RIGHT)) else lights.wrong()
            version++
            return result
        }
        when (result) {
            InputResult.WRONG -> { wrongFeedback(); struggle.mistake(android.os.SystemClock.elapsedRealtime()) }
            InputResult.CORRECT, InputResult.GROUP_DONE -> {
                if (struggle.right()) { highFive++; Log.d(TAG, "pup: high five, through a hard passage") }
                if (prefs.feedbackLight) { val h = handOf(midi); lights.hit(midi, handColor(h), grade); Log.d(TAG, "light hit ${noteName(midi)} $h" + (grade?.let { " $it" } ?: "")) }
                else scope.launch { flashes[midi] = Good; delay(200); flashes.remove(midi) }
            }
            InputResult.IGNORED -> {}
        }
        version++
        return result
    }
    fun setSpeed(sp: Double) { speed = sp; transport.speed = sp; Log.d(TAG, "transport speed=$sp") }
    fun setTranspose(n: Int) {
        val to = n.coerceIn(-6, 6)
        if (to == transpose) return
        pause()
        resumeAt = transport.shownMs
        meta?.let { prefs.saveTranspose(it.id, to) }
        Log.d(TAG, "transpose ${song.id}: $transpose -> $to semitones at ${resumeAt?.toLong()} ms")
        transpose = to
    }
    /** A loop pass (or the whole song) was played through: the ramp may speed up. */
    fun passEnded(r: Report) {
        val rr = ramp ?: return
        val up = rr.passEnded(transport.mode, r)
        Log.d(TAG, "ramp: pass ${if (rr.clean(transport.mode, r)) "clean" else "not clean"} (wrong=${r.wrongKeys} accuracy=${r.recap?.accuracy}) -> ${rr.percent}%")
        if (up != null) { setSpeed(up); rampPercent = rr.percent; voice.speak("Faster: ${rr.percent} percent.") }
    }
    fun finished() {
        if (transport.loop == null) passEnded(transport.report())
        // A ramp step goes round again until the ramp reaches 100%.
        val rr = ramp
        if (embedded?.ramp == true && rr != null && !rr.atTop) {
            Log.d(TAG, "ramp step: pass done, again at ${rr.percent}%")
            scope.launch { delay(1800); seek(0.0); play() }
            return
        }
        val score = transport.score
        val run = if (transport.mode == Mode.PERFORM && score != null) score.recap() else null
        val extra = pedalGrade?.score() ?: loudness?.score()
        onFinished(transport.report().copy(extraScore = extra, realMs = transport.runRealMs).also { Log.d(TAG, "run report ${song.id}: $it") })
        keepRun()
        if (meta == null) {                                // generated songs (lessons, progressions) keep no progress
            run?.let { recap = RunEnd(it, false, emptyList(), timeMs = transport.runRealMs.toLong()) }
            Log.d(TAG, "finished ${song.id} mode=${transport.mode} recap=$run")
            return
        }
        // The hardest bars of a Wait or Perform run, remembered for the library row.
        val spots = when (transport.mode) {
            Mode.WAIT -> TroubleSpots.forWait(song, transport.troubles)
            Mode.PERFORM -> TroubleSpots.forPerform(song, transport.troubles)
            Mode.LISTEN -> null
        }
        // Auto speed learns from every Wait and Perform run of a library song, Auto on or not.
        val effective = if (spots != null) AutoSpeed.effectivePercent(transport.runSongMs, transport.runRealMs) else null
        if (effective != null) {
            val rep = transport.report()
            val acc = rep.recap?.accuracy ?: AutoSpeed.waitAccuracy(rep.groups, rep.wrongKeys)
            val before = prefs.autoSpeed(meta.id)
            val after = AutoSpeed.afterRun(before, effective, acc, java.time.LocalDate.now())
            prefs.saveAutoSpeed(meta.id, after)
            Log.d(TAG, "auto speed ${song.id}: managed $effective% (song ${transport.runSongMs.toLong()} ms in ${transport.runRealMs.toLong()} ms), " +
                "accuracy $acc% -> ${after.percent}% (was ${before?.percent})")
            autoPercent = after.percent
            if (autoOn) setSpeed(after.speed)
        }
        if (spots != null) {
            prefs.saveTroubleBars(meta.id, spots)
            Log.d(TAG, "trouble spots ${song.id}: " + spots.joinToString { "bar ${it.bar} (" + "%.1f".format(it.score) + ")" })
        }
        if (transport.mode != Mode.LISTEN) { prefs.addHandDone(meta.id, transport.practice); Log.d(TAG, "hands done ${song.id}: ${prefs.handsDone(meta.id)}") }
        val wasCompleted = prefs.progress(meta.id).completed
        // The recap shows the XP this run earned and the level bar, as lessons do.
        val xpBefore = progress?.xp
        fun xp(gained: Int?) = gained?.takeIf { it > 0 }
        fun bar() = progress?.let { p -> xpBefore?.let { com.bigpapituna.sharpears.ui.profile.xpBarFor(it, p.xp) } }
        val timeMs = transport.runRealMs.toLong()
        if (run != null) {
            val r = run
            // Only a run from the top counts as a best score.
            val best = transport.scoreFromMs <= 0.0 && r.accuracy > 0 && prefs.saveScore(meta.id, r.accuracy)
            val earned = progress?.record(ProgressEvent.SongRun(meta.id, r.accuracy, newBest = best, firstCompletion = !wasCompleted))
            recap = RunEnd(r, best, spots.orEmpty(), canReplay = lastRun != null, timeMs = timeMs, xpGained = xp(earned?.xpGained), xpBar = bar())
            if (touchRun) haptics.success()
            Log.d(TAG, "perform recap ${song.id}: $r accuracy=${r.accuracy} from=${transport.scoreFromMs.toLong()} newBest=$best")
        } else {
            prefs.markCompleted(meta.id)
            val earned = progress?.record(ProgressEvent.SongRun(meta.id, null, newBest = false, firstCompletion = !wasCompleted))
            if (transport.mode == Mode.WAIT) {
                recap = RunEnd(null, false, spots.orEmpty(), wrongKeys = transport.report().wrongKeys, canReplay = lastRun != null,
                    timeMs = timeMs, xpGained = xp(earned?.xpGained), xpBar = bar())
                if (touchRun) haptics.success()
            }
            Log.d(TAG, "completed ${song.id} mode=${transport.mode}")
        }
    }
    /** "Loop bar N": the A-B loop on that bar, wait mode at half speed, from a lead-in before it. */
    fun loopBar(s: TroubleSpot) {
        recap = null
        autoOn = false
        if (transport.mode != Mode.WAIT) setMode(Mode.WAIT) else pause()
        ramp = null; setSpeed(0.5)
        setLoop(s.startMs, s.endMs)
        seek(s.startMs)
        Log.d(TAG, "loop bar ${s.bar}: ${s.startMs.toLong()}..${s.endMs.toLong()} ms at 50% in wait mode")
        play()
    }

    // MIDI keyboard: same path as touch, but no app sound, since the piano makes its own.
    // With the mic on, MIDI only judges: it is logged as truth and the mic alone drives the song.
    // ponytail: MIDI never plays through the sampler; add a setting if a silent controller keyboard shows up
    LaunchedEffect(midi) {
        midi.events.collect { e ->
            when (e) {
                is NoteEvent.On -> {
                    held[e.midi] = RightHandColor
                    if (micOn) {
                        Log.d(TAG, "truth NoteOn ${noteName(e.midi)} midi=${e.midi} vel=${e.velocity}")
                        answerKey.key(System.currentTimeMillis(), e.midi, e.midi in transport.listenFor)
                        tail("keyboard ${noteName(e.midi)}")
                    }
                    else { Log.d(TAG, "NoteOn ${noteName(e.midi)} midi=${e.midi} vel=${e.velocity} src=midi"); keyStruck(e.midi, velocity = e.velocity) }
                }
                is NoteEvent.Off -> { held.remove(e.midi); if (!micOn) keyReleased(e.midi) }
            }
        }
    }

    // The sustain pedal (MIDI CC 64): graded in pedal steps, shown on the strip under the keys.
    LaunchedEffect(midi) {
        midi.pedal.collect { down ->
            pedalDown = down
            if (transport.playing) pedalGrade?.pedal(down, transport.positionMs)
            Log.d(TAG, "pedal ${if (down) "down" else "up"} at ${transport.positionMs.toLong()} ms")
        }
    }

    // Microphone: runs while it is switched on, in wait or perform mode, with the app on screen.
    val visible = onScreen()
    LaunchedEffect(visible) { if (!visible) savePosition() }
    // The mic waits while the voice talks through the speaker (it would hear the phone).
    val voiceHolds = voice.holdsMic
    val replaying = replay != null
    DisposableEffect(micOn, mode, visible, voiceHolds, embedded == null || playing, replaying) {
        if (voiceHolds && micOn) Log.d(TAG, "mic: waiting for the voice")
        // Inside a lesson the mic listens only while the step runs, so the voice can speak between runs.
        if (micOn && mode != Mode.LISTEN && visible && !voiceHolds && (embedded == null || playing) && !replaying) {
            if (start.inject != null) mic.startInjection(start.inject)
            else if (!mic.start()) { Log.d(TAG, "mic: could not start"); micOn = false }
        }
        if (micOn) midi.onTruth = mic::onMidiTruth
        onDispose { stallEnd("left"); mic.stop(); midi.onTruth = null }
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
                    if (midi.connected.value != null) answerKey.mic(System.currentTimeMillis(), e.midi)
                    stallWatch.heard(System.currentTimeMillis(), "${noteName(e.midi)} heard, rise ${"%.1f".format(e.riseDb)} dB" +
                        (e.inferredFrom?.let { " (credited from ${noteName(it)})" } ?: ""))
                    keyStruck(e.midi, latencyMs = e.delayMs ?: 0.0)
                }
                is MicEvent.Wrong -> if (transport.waiting) {
                    tail("mic: a strike with none of the expected notes")
                    stallWatch.heard(System.currentTimeMillis(), "a strike with none of the expected notes in it")
                    if (mic.wrongFlash) wrongFeedback()
                    Log.d(TAG, "input mic -> WRONG at ${transport.positionMs.toLong()} ms (flash=${mic.wrongFlash})")
                }
            }
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.d(TAG, "mic permission granted=$granted")
        if (granted) { micOn = true; prefs.micDefault = true; progress?.record(ProgressEvent.FeatureUsed("mic")); if (mode == Mode.LISTEN && embedded == null) setMode(Mode.WAIT) }
    }
    fun toggleMic() {
        if (micOn) { micOn = false; prefs.micDefault = false; Log.d(TAG, "mic off"); return }
        if (!mic.hasPermission()) { askMic.launch(Manifest.permission.RECORD_AUDIO); return }
        micOn = true; prefs.micDefault = true
        progress?.record(ProgressEvent.FeatureUsed("mic"))           // counted once (features are a set)
        // The mic listens in wait and perform mode.
        if (mode == Mode.LISTEN && embedded == null) setMode(Mode.WAIT)
        Log.d(TAG, "mic on")
    }
    LaunchedEffect(song) {
        if (embedded?.ramp == true) { ramp = com.bigpapituna.sharpears.engine.TempoRamp().also { setSpeed(it.speed); rampPercent = it.percent } }
        // A transport rebuilt by a transpose keeps the speed, mode, hands and loop.
        transport.speed = speed
        if (transport.mode != mode) transport.mode = mode
        if (transport.practice != practice) transport.practice = practice
        transport.loop = loopStart?.let { a -> loopEnd?.let { b -> if (b > a) a..b else null } }
        val at = resumeAt ?: startMs
        if (at > 0) seek(at)
        // Before Play the first notes already hang just under the top of the lane.
        if (!debugDump) { transport.preview(PREVIEW_MS); position = transport.positionMs }
        Log.d(TAG, "player open ${song.id} range=${noteName(range.first)}..${noteName(range.last)} mode=$mode hands=$practice mic=$micOn at=${startMs.toLong()} video=${tutorial != null}")
        if (start.inject != null && embedded == null) {
            // Debug injection runs hands-free, starting with the injected audio (rendering it takes a moment).
            while (mic.matcher.samplesProcessed == 0L) delay(5)
            play()
        }
    }
    DisposableEffect(song) { onDispose { savePosition(); apply(transport.pause()); if (lastRun == null) keepRun() } }
    // "Practice today": straight into a loop on the worst bar, for a fixed time.
    start.loopBar?.let { bar ->
        LaunchedEffect(song, ready) {
            if (!ready) return@LaunchedEffect
            delay(600)
            loopBar(TroubleSpots.spot(song, bar))
        }
    }
    start.timeLimitMs?.let { limit ->
        LaunchedEffect(song) {
            delay(limit)
            Log.d(TAG, "time limit ${limit / 1000} s reached")
            pause()
            onTimeUp()
        }
    }
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
        if (wasWaiting) stallBegin()                     // resumed in the middle of a wait
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
                // The Perform count-in always clicks, metronome or not.
                val clickTo = when {
                    metronomeOn || backingOn -> transport.positionMs
                    mode == Mode.PERFORM -> minOf(transport.positionMs, transport.anchorMs)
                    else -> before
                }
                if (clickTo > before && soundAllowed()) metro.clicks(before, clickTo).forEach { clicks.value.play(it) }
                if (transport.positionMs < before) Log.d(TAG, "loop wrap ${before.toLong()} -> ${transport.positionMs.toLong()} ms")
                if (transport.passes != passes) { passes = transport.passes; transport.lastPass?.let(::passEnded) }
                last = now
                position = transport.positionMs
                if (transport.waiting != wasWaiting) {
                    wasWaiting = transport.waiting
                    version++
                    if (wasWaiting) Log.d(TAG, "wait at ${transport.positionMs.toLong()} ms expect=${transport.expected.map { noteName(it.midi) }}")
                    if (wasWaiting) stallBegin() else stallEnd("played")
                    if (wasWaiting) tail("waiting at ${at(transport.positionMs)} for ${transport.expected.joinToString(", ") { noteName(it.midi) }}")
                }
                // Perform mode: what the mic listens for moves with the song.
                if (mode == Mode.PERFORM && transport.listenFor != listening) { listening = transport.listenFor; version++ }
                // The lane's combo counter follows the Perform streak (only misses break it).
                if (mode == Mode.PERFORM) transport.score?.let { lights.streak(it.streakNow) }
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
        BarState(playing, ready, maxOf(position, transport.runFromMs, 0.0), song.durationMs, speed, mode, practice, loopStart, loopEnd, song.sections,
            midi.connected.value, micOn, { mic.level.floatValue }, title = song.title,
            // Under the title: the composer, and the part of the song the playhead is in (medleys, progressions).
            subtitle = listOfNotNull(
                if (transpose != 0) "${if (transpose > 0) "Up" else "Down"} ${kotlin.math.abs(transpose)} ${if (kotlin.math.abs(transpose) == 1) "semitone" else "semitones"}" else null,
                meta?.composer?.takeIf { it.isNotEmpty() },
                song.sections.lastOrNull { it.startMs <= position }?.name).joinToString(", "),
            videoOn = if (tutorial != null) videoOn else null,
            metronome = metronomeOn,
            ramp = if (ramp != null) rampPercent else null,
            auto = if (autoOn) autoPercent else null,
            chords = if (chords != null) chordsOn else null,
            transpose = transpose,
            backing = if (isProgression) backingOn else null,
            status = when {
                mode != Mode.PERFORM -> ""
                position < transport.anchorMs -> if (playing) "Get ready" else ""
                else -> transport.score?.let { "Streak ${it.streakNow}" } ?: ""
            }),
        BarActions(
            onBack = onBack,
            onPlayPause = { if (playing) pause() else play() },
            onSpeed = { ramp = null; autoOn = false; setSpeed(it) },
            onRamp = { autoOn = false; ramp = com.bigpapituna.sharpears.engine.TempoRamp().also { setSpeed(it.speed); rampPercent = it.percent }; Log.d(TAG, "ramp on at 50%") },
            onAuto = { ramp = null; autoOn = true; setSpeed(autoPercent / 100.0); Log.d(TAG, "auto speed on at $autoPercent%") },
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
            onTranspose = ::setTranspose,
            onMetronome = { metronomeOn = !metronomeOn; Log.d(TAG, "metronome=$metronomeOn bpm=${song.bpm} beat=${metro.beatMs.toLong()} ms") },
            onBacking = {
                backingOn = !backingOn
                // The backing plays the left hand; the right hand is the player's.
                if (backingOn) { practice = Practice.RIGHT; transport.practice = Practice.RIGHT; version++ }
                Log.d(TAG, "backing=$backingOn hands=${transport.practice}")
            },
            onFlag = if (mic.keepRecordings) ({
                flagMark = mic.clipMarkAgo(28); flagAt = transport.positionMs
                flagContext = "input=${if (micOn) "mic ${mic.source.value} sensitivity_db=${mic.matcher.config.sensitivityDb}" else "touch or MIDI"} midi=${midi.connected.value}\n" +
                    "expected=${transport.expected.joinToString(",") { noteName(it.midi) }}\n"
                if (playing) pause()                      // room to pick and type; the clip is already marked
                flagging = true
            }) else null,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    if (flagging) FlagDialog(onDismiss = { flagging = false }) { what, note ->
        flagging = false
        val name = "flag-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + song.id
        val report = buildString {
            append("title=Flag: ${song.title} at ${at(flagAt)}: $what\n")
            append("# sharp ears flag (\"that wasn't right\")\n")
            append("song=${song.id} title=${song.title} position_ms=${flagAt.toLong()} mode=${transport.mode} speed=${transport.speed} hands=${transport.practice}\n")
            append(flagContext)
            append("what=$what\n")
            if (note.isNotBlank()) append("note=${note.trim()}\n")
            append("the last minute (newest last):\n")
            tail.forEach { append("  ").append(it).append('\n') }
        }
        Log.d(TAG, "flag: $what at ${at(flagAt)}, saving $name")
        if (mic.canClip) mic.saveStall(flagMark + 2L * com.bigpapituna.sharpears.audio.MicInput.RATE, report, name)
        else { mic.reporter.saveText(name, report); mic.reporter.flush() }
    }

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
            // Until the run reaches its start, notes before a seek point stay hidden (they will not play).
            hideBefore = if (position < transport.runFromMs) transport.runFromMs else null,
            lights = lights,
            beatMs = if (start.beatLines || prefs.beatLines) metro.beatMs else null, beatsPerBar = metro.beatsPerBar,
        )
        // Perform count-in: the beats of the bar near the hit line, the current one bright.
        if (playing && mode == Mode.PERFORM && position < transport.anchorMs)
            BeatCounter(metro.beatsPerBar, metro.beatInBar(position), Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
        // The pup: ready during the count-in, a high-five after a hard passage comes good.
        if (showHighFive) PupSays(Pose.GREAT_JOB, "High five!", Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
        else if (playing && mode == Mode.PERFORM && position < transport.anchorMs)
            Pup(Pose.GET_READY, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), size = 72.dp)
        // Before the first Play: a quiet hint, nothing else.
        if (!playing && transport.fresh && position < transport.anchorMs && embedded?.autoStart != true)
            Text("Press play", style = MaterialTheme.typography.titleMedium, color = InkDim,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
        if (chordsOn && chords != null) ChordLabel(chords, position, song.preferFlats, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 10.dp))
        if (showVideo) TutorialPip(tutorial!!, moving, speed, micOn && !mic.headphonesOn(), { transport.positionMs },
            onClose = { videoOn = false; Log.d(TAG, "video closed") }, maxHeight = maxHeight - 20.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        if (embedded == null) recap?.let { r ->
            // The end of a run is a moment of its own: the whole screen, the pup, the numbers counting up, the XP.
            Dialog(onDismissRequest = { recap = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                RunRecap(r, speed, sampler.takeIf { prefs.sound }, onAgain = { recap = null; seek(0.0); play() }, onLoop = ::loopBar,
                    onClose = { recap = null },
                    onReplay = { recap = null; replay = lastRun; Log.d(TAG, "hear my run: ${lastRun?.notes?.size} notes") })
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
        // The ghost hand plays the part the player is asked for (everything in Listen). Hidden when labels are
        // (from memory, sight-reading): showing the next finger would give the notes away.
        val ghost = remember(song, fingers, practice, mode) {
            com.bigpapituna.sharpears.ui.motion.ghostNotes(song, fingers) { mode == Mode.LISTEN || transport.required(it) }
        }
        Box(modifier) {
            // No `flash` here: the player's feedback lives on the hit line, never on green or red keys.
            Keyboard(
                lowMidi = range.first, highMidi = range.last, lit = lit,
                onNoteOn = { m, v ->
                    Log.d(TAG, "NoteOn ${noteName(m)} midi=$m vel=$v src=touch")
                    touched[m] = RightHandColor; if (prefs.sound && (!micOn || mic.headphonesOn())) sampler.noteOn(m, v)
                    touchRun = true
                    when (keyStruck(m, velocity = v)) {
                        InputResult.CORRECT, InputResult.GROUP_DONE -> haptics.tick()
                        InputResult.WRONG -> haptics.miss()
                        InputResult.IGNORED -> {}
                    }
                },
                onNoteOff = { m -> Log.d(TAG, "NoteOff ${noteName(m)} midi=$m src=touch"); touched.remove(m); sampler.noteOff(m); keyReleased(m) },
                modifier = Modifier.fillMaxSize().then(if (wrongVisible) Modifier.border(4.dp, Bad) else Modifier),
                pulsing = remember(expected) { expected.associate { it.midi to handColor(it.hand) } },
                // The key the song waits for shows its suggested finger.
                labels = if (labels && prefs.fingerNumbers) keyLabels + expected.mapNotNull { n -> fingers[n]?.let { n.midi to "$it" } } else keyLabels,
                sheen = !playing,
            )
            com.bigpapituna.sharpears.ui.motion.GhostHand(ghost, { position }, range.first, range.last, Modifier.matchParentSize(),
                visible = labels && prefs.ghostHand && prefs.version.intValue >= 0)
        }
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
                    pedalGrade != null -> if (pedalDown) "Pedal down" else "Pedal up"
                    loudness != null -> remember(version) { loudness.nextIsSoft() }.let { soft ->
                        (when (soft) { true -> "Softly"; false -> "Loudly"; null -> "" }) + if (lastVelocity >= 0) ", last $lastVelocity" else "" }
                    embedded.ramp && !(playing && position >= transport.anchorMs) -> "Ramp $rampPercent%"
                    mode != Mode.PERFORM || !playing -> ""
                    position < transport.anchorMs -> "Get ready"
                    else -> transport.score?.let { "Streak ${it.streakNow}" } ?: ""
                })
            Lane(Modifier.fillMaxWidth().weight(1f))
            Keys(Modifier.fillMaxWidth().weight(0.45f))
            if (pedalGrade != null) PedalStrip(pedalGrade, position, song.durationMs, pedalDown, Modifier.fillMaxWidth().height(28.dp))
        }
        return
    }

    // A new two-hand song: suggest hands apart first, then together. Library songs only, never lessons or progressions.
    var handsTip by remember(song) {
        mutableStateOf(meta?.takeIf { embedded == null }?.let { m ->
            val twoHands = song.notes.any { it.hand == Hand.LEFT } && song.notes.any { it.hand == Hand.RIGHT }
            com.bigpapituna.sharpears.engine.handsTip(twoHands, prefs.handsDone(m.id), prefs.progress(m.id).completed)
                ?.takeIf { it != prefs.handsTipDismissed(m.id) }
        })
    }
    LaunchedEffect(handsTip) { handsTip?.let { Log.d(TAG, "hands tip ${song.id}: $it") } }
    fun pickHands(p: Practice?) {
        val stage = handsTip ?: return
        if (p != null) { practice = p; transport.practice = p; version++ }
        meta?.let { prefs.dismissHandsTip(it.id, stage) }
        Log.d(TAG, "hands tip $stage -> ${p ?: "dismissed"}")
        handsTip = null
    }
    @Composable
    fun HandsTipRow() {
        val stage = handsTip ?: return
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when (stage) {
                "right" -> "New song. Start with the right hand alone?"
                "left" -> "Right hand done. Now the left hand alone?"
                else -> "Both hands passed alone. Hands together?"
            }, style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            when (stage) {
                "right" -> { TipPill("Right hand") { pickHands(Practice.RIGHT) }; TipPill("Both") { pickHands(Practice.BOTH) } }
                "left" -> { TipPill("Left hand") { pickHands(Practice.LEFT) }; TipPill("Both") { pickHands(Practice.BOTH) } }
                else -> TipPill("Hands together") { pickHands(Practice.BOTH) }
            }
            androidx.compose.material3.IconButton(onClick = { pickHands(null) }) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.bigpapituna.sharpears.R.drawable.ic_close),
                    contentDescription = "Dismiss the suggestion", tint = InkDim)
            }
        }
    }

    replay?.let { r ->
        BackHandler { replay = null }
        RunReplay(song, r, range.first, range.last, sampler, prefs.sound, onClose = { replay = null })
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
            HandsTipRow()
            Keys(Modifier.fillMaxWidth().weight(1f))
        } else {
            Bar()
            HandsTipRow()
            Lane(Modifier.fillMaxWidth().weight(1f))
            Keys(Modifier.fillMaxWidth().weight(if (windowHeightDp >= 500.dp) 0.3f else 0.45f))
        }
    }
}

/**
 * How a run ended: the Perform [recap] (null after a wait-mode run), a new best, the hardest bars, how long the run
 * took in real time ([timeMs]) and the XP it earned with the level bar (library songs only), shown by [RunRecap].
 */
data class RunEnd(val recap: Recap?, val newBest: Boolean, val spots: List<TroubleSpot>, val wrongKeys: Int = 0, val canReplay: Boolean = false,
                  val timeMs: Long = 0, val xpGained: Int? = null, val xpBar: XpBar? = null)

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
private fun ChordLabel(track: com.bigpapituna.sharpears.engine.ChordTrack, positionMs: Double, flats: Boolean, modifier: Modifier) {
    val (now, next) = track.at(positionMs.coerceAtLeast(0.0))
    val name = now?.nameIn(flats)
    LaunchedEffect(name) { if (name != null) Log.d(TAG, "chord label $name at ${positionMs.toLong()} ms") }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (name != null) Text(name, style = MaterialTheme.typography.titleLarge, color = Ink.copy(alpha = 0.78f))
        next?.let { Text(it.nameIn(flats), style = MaterialTheme.typography.titleSmall, color = InkDim.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 2.dp)) }
    }
}

/**
 * The pedal strip under the keys: the chords left to right, each with the stretch where the pedal should be down
 * (outlined, a gap at each change), what the pedal really did (filled), and the playhead.
 */
@Composable
private fun PedalStrip(g: PedalGrader, positionMs: Double, durationMs: Long, down: Boolean, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier.background(LaneBackground).padding(horizontal = 12.dp, vertical = 8.dp)
        .semantics { contentDescription = if (down) "Pedal down" else "Pedal up" }) {
        val w = size.width; val h = size.height
        fun x(ms: Double) = (w * ms / durationMs.coerceAtLeast(1)).toFloat().coerceIn(0f, w)
        g.chordSpans().forEach { c ->
            drawRoundRect(InkDim.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(x(c.first + 150.0), 2f),
                androidx.compose.ui.geometry.Size(x(c.last.toDouble()) - x(c.first + 150.0) - 6f, h - 4f),
                androidx.compose.ui.geometry.CornerRadius(h / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        // What the pedal did, up to now.
        var from: Double? = null
        (g.events + (positionMs to false)).forEach { (t, d) ->
            if (d && from == null) from = t
            if (!d && from != null) {
                drawRoundRect(RightHandColor.copy(alpha = 0.85f), androidx.compose.ui.geometry.Offset(x(from!!), 5f),
                    androidx.compose.ui.geometry.Size((x(t) - x(from!!)).coerceAtLeast(2f), h - 10f), androidx.compose.ui.geometry.CornerRadius(h / 2))
                from = null
            }
        }
        drawLine(Ink, androidx.compose.ui.geometry.Offset(x(positionMs), 0f), androidx.compose.ui.geometry.Offset(x(positionMs), h), 3f)
    }
}

/** A small outlined pill for the hands tip. */
@Composable
private fun TipPill(label: String, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp)) { Text(label, maxLines = 1) }

/** The count-in: "1 2 3 4" with the current beat bright and larger. */
@Composable
private fun BeatCounter(beats: Int, current: Int, modifier: Modifier) {
    LaunchedEffect(current) { Log.d(TAG, "count-in beat ${current + 1} of $beats") }
    Row(modifier.semantics { contentDescription = "Count in, beat ${current + 1}" }, horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.Bottom) {
        (0 until beats).forEach { b ->
            Text("${b + 1}", style = Numeric.merge(if (b == current) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall),
                color = if (b == current) Ink else InkDim.copy(alpha = 0.55f))
        }
    }
}

/**
 * "That wasn't right": what went wrong, in one tap, and an optional note. The last 30 s of the mic (when kept)
 * and the last minute of inputs go to review with it.
 */
@Composable
private fun FlagDialog(onDismiss: () -> Unit, onSend: (what: String, note: String) -> Unit) {
    val choices = listOf("It accepted a wrong note", "It missed a note I played", "It moved on too early", "Something else")
    var what by remember { mutableStateOf(choices[0]) }
    var note by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("That wasn't right") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("The last 30 seconds go to review with what you pick.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                choices.forEach { c ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).radioRow(c == what) { what = c },
                        verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = c == what, onClick = null)
                        Text(c, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                androidx.compose.material3.OutlinedTextField(note, { note = it.take(300) }, label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. the C6 in bar 2") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSend(what, note) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Modifier.radioRow(selected: Boolean, onClick: () -> Unit) =
    this.then(Modifier.selectable(selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick))
