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
import androidx.compose.runtime.derivedStateOf
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
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.RunEvents
import com.bigpapituna.sharpears.engine.brief.RunTally
import com.bigpapituna.sharpears.engine.hints.HintPlan
import com.bigpapituna.sharpears.engine.hints.HintStep
import com.bigpapituna.sharpears.engine.hints.NoiseWatch
import com.bigpapituna.sharpears.engine.hints.OctaveHint
import com.bigpapituna.sharpears.engine.hints.WrongKey
import com.bigpapituna.sharpears.ui.hints.KeyGlow
import com.bigpapituna.sharpears.ui.hints.NoiseSheet
import com.bigpapituna.sharpears.ui.hints.OctaveArrow
import com.bigpapituna.sharpears.ui.hints.PointingHand
import com.bigpapituna.sharpears.engine.skin.KeyboardSize
import com.bigpapituna.sharpears.engine.play.Duet
import com.bigpapituna.sharpears.engine.play.QuestEvent
import com.bigpapituna.sharpears.engine.skin.Stars
import com.bigpapituna.sharpears.data.PlayStore
import com.bigpapituna.sharpears.data.ProgressStore
import com.bigpapituna.sharpears.ui.motion.Juice
import com.bigpapituna.sharpears.ui.skin.rememberWrongFlash
import com.bigpapituna.sharpears.ui.world.LocalWorld
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.ui.pup.rememberPupController

/** Before Play, the run's first notes sit this far (song ms) above the hit line: just under the top of the lane. */
private const val PREVIEW_MS = com.bigpapituna.sharpears.engine.LOOKAHEAD_MS * 0.75
/** Pup's Playground: a quiet stretch this long while wait mode holds counts as a try (three let the group go, about 27 s). */
private const val LET_GO_QUIET_MS = 9_000L

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
    /** Two people, one hand each (2.1, engine/play/Duet): both hands are played and both get the run. */
    val duet: Duet? = null,
    /** Open with the duet picker showing (2.4, the parent brief's "Practise together" from a grown-up's own week). */
    val pickDuet: Boolean = false,
    /** 2.9: open at this speed (the comeback's slower replay, engine/Comeback.GROWN_UP_SPEED); null: full speed. */
    val speed: Double? = null,
)

/**
 * The player inside a lesson step: no orientation lock, no back handling, and instead of the transport bar a
 * single row with play/pause, the step's fixed hand and mode ([state]) and the mic. [labels] false hides note
 * names and finger numbers (playing from memory). With [autoStart] the run starts once the voice has finished.
 */
data class Embedded(val state: String, val labels: Boolean = true, val autoStart: Boolean = true,
                    /** A ramp step: starts at 50%, plays again after each pass, ends only once the ramp reaches 100%. */
                    val ramp: Boolean = false,
                    /** Just before onFinished: the run's tally for the parent brief's step log (LessonScreen's StepRecorder). */
                    val onTally: ((RunTally) -> Unit)? = null,
                    /** A MIDI step: PEDAL (graded on the sustain pedal) or DYNAMICS (soft then loud). */
                    val special: com.bigpapituna.sharpears.engine.StepKind? = null)

/**
 * The full player's landscape lock (2.8.4, QA D1). On the Fold's inner screen Android 16 ignores orientation requests, and a
 * plain reset to UNSPECIFIED made there was dropped: the window manager compares it with the request as it overrides it
 * there (already UNSPECIFIED), so the stored SENSOR_LANDSCAPE outlived the player and the cover screen opened sideways
 * after the next fold. The release goes through USER first (a different value, so it is stored), then UNSPECIFIED.
 * MainActivity also releases it on every display change while no player holds it. Counted, so a player opening over
 * another (the transition keeps both a moment) keeps the lock.
 */
internal object LandscapeLock {
    private var holders = 0
    val held get() = holders > 0

    fun hold(activity: android.app.Activity) {
        holders++
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun release(activity: android.app.Activity) {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) reset(activity, "player closed")
    }

    /** Back to the phone's own orientation, in two steps so the inner screen's override cannot swallow it. */
    fun reset(activity: android.app.Activity, why: String) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        Log.d(com.bigpapituna.sharpears.TAG, "orientation: released ($why)")
    }
}

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
    /** Lesson ids whose exercises use any-of ranges ("any key above C4"): no octave hint there (the playground's list). */
    anyOfLessons: Set<String> = emptySet(),
    /** Every wrong key shown (mic, touch, MIDI), for the pup's head tilt and the skin's key flash (ui/hints/INTEGRATION.md). */
    onWrongKey: (WrongKey) -> Unit = {},
    /**
     * Pup's Playground (2.1.1, no dead ends): a group wait mode holds on goes by after three tries (wrong keys, or quiet
     * stretches with nothing played), and the pup says "Good try, let's keep going".
     */
    letGo: Boolean = false,
    /** The run's recap was closed (Practice today goes on to its next part then). */
    onRecapClosed: () -> Unit = {},
    /** A popular song's band, drums and bass (ui/chords/PopSongsScreen): plays while the Band toggle is on, in step with the transport. */
    band: com.bigpapituna.sharpears.engine.chords.BandBed? = null,
    /** 2.10: every right key (a note or a group done), for the lesson pup's wag. */
    onRightKey: (Int) -> Unit = {},
) {
    // 2.13, cheer pops: a lesson's own (LocalCheers, drawn over its header); a song played on its own gets one per song, drawn
    // in a top corner of its notes.
    val cheers = com.bigpapituna.sharpears.ui.pup.LocalCheers.current ?: if (embedded == null) com.bigpapituna.sharpears.ui.pup.rememberCheerPops(
        sampler, mic, voice, prefs, com.bigpapituna.sharpears.ui.family.LocalProfile.current.kidSkin, key = original.id,
        profileKid = com.bigpapituna.sharpears.ui.family.LocalProfile.current.isKid) else null
    PlayerStage(PlayerArgs(original, meta, sampler, midi, mic, voice, prefs, ready, startMs, debugDump, start, onBack, onFinished, embedded,
        modifier, chordTrack, onTimeUp, anyOfLessons, onWrongKey, letGo, onRecapClosed, band, onRightKey, cheers))
}

/**
 * [PlayerScreen]'s arguments as one object, made each composition, so the body's closures capture it rather than twenty
 * parameters one by one (2.4: see [PlayerVars] for why the body's register count matters).
 */
private class PlayerArgs(
    val original: Song, val meta: SongMeta?, val sampler: Sampler, val midi: MidiDevices, val mic: MicInput, val voice: Voice, val prefs: Prefs,
    val ready: Boolean, val startMs: Double, val debugDump: Boolean, val start: PlayerStart, val onBack: () -> Unit,
    val onFinished: (Report) -> Unit, val embedded: Embedded?, val modifier: Modifier, val chordTrack: com.bigpapituna.sharpears.engine.ChordTrack?,
    val onTimeUp: () -> Unit, val anyOfLessons: Set<String>, val onWrongKey: (WrongKey) -> Unit, val letGo: Boolean, val onRecapClosed: () -> Unit,
    val band: com.bigpapituna.sharpears.engine.chords.BandBed?, val onRightKey: (Int) -> Unit,
    val cheers: com.bigpapituna.sharpears.ui.pup.CheerPops?,
)

/** The player itself: [PlayerScreen]'s body, reading its arguments through `with(args)` exactly as it read the parameters. */
@Composable
private fun PlayerStage(args: PlayerArgs) {
    with(args) {
    val activity = LocalActivity.current ?: return
    // The screen's state lives in one holder, read through with(v) exactly as the locals were (2.4: a body holding sixty
    // locals made D8 allocate hundreds of registers to this method and misplace a null check; see PlayerVars).
    val v = androidx.compose.ui.platform.LocalContext.current.let { c -> remember { PlayerVars(start, savedAutoPercent(meta, prefs), c) } }
    with(v) {
    // Transpose (library songs keep it per song): every note moves, so the rest of the player sees one song.
    // `meta` is read through the small functions at the end of this file (see savedTranspose), never `meta?.` here.
    var transpose by remember(original.id) { mutableIntStateOf(savedTranspose(meta, prefs)) }
    val song = remember(original, transpose) { original.transposed(transpose) }
    /** Where a rebuilt transport (after a transpose) picks up. */
    var resumeAt by remember(original.id) { mutableStateOf<Double?>(null) }
    if (embedded == null) DisposableEffect(Unit) {
        LandscapeLock.hold(activity)
        onDispose { LandscapeLock.release(activity) }
    }
    // The stage is always dark: light status bar icons over it (with a light phone theme they were dark on black). Set
    // again after each configuration change, which puts the edge-to-edge style back (the turn to landscape is one).
    if (embedded == null) DisposableEffect(androidx.compose.ui.platform.LocalConfiguration.current) {
        val bars = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val lightBars = bars.isAppearanceLightStatusBars
        bars.isAppearanceLightStatusBars = false
        onDispose { bars.isAppearanceLightStatusBars = lightBars }
    }
    val labels = embedded?.labels ?: true
    // Who is playing: the mic offset and the report tag follow the profile; hint timings and the key size are theirs.
    val profile = com.bigpapituna.sharpears.ui.family.LocalProfile.current
    // 2.14: the kid skin (big keys, the pup in the bar, no numbers) is a little kid's; a big kid plays the grown-up player and
    // keeps a kid's quest (the quest is per kind, as Today and the map ask it).
    val kid = profile.kidSkin
    // 2.10: a kid's pup in the bar (the full player): he wags at every right key and tilts his head at a wrong one.
    val kidPup = rememberPupController(idle = false)
    val questStore = androidx.compose.ui.platform.LocalContext.current.let { c -> remember(profile.id) { if (profile.isKid) com.bigpapituna.sharpears.data.PlayStore(c.applicationContext, profile.id) else null } }
    // Kids get big keys: just the octave the notes need, about twice as wide (REGULAR is exactly fitRange).
    val keySize = KeyboardSize.forKid(kid)
    // A kid playing one hand (the concert's band plays the other) sees only that hand's keys; the rest play themselves.
    val range = remember(song, keySize) {
        val hand = when (start.practice) { Practice.RIGHT -> Hand.RIGHT; Practice.LEFT -> Hand.LEFT; Practice.BOTH -> null }
        val own = if (kid && hand != null && start.duet == null) song.notes.filter { it.hand == hand } else emptyList()
        keySize.rangeOf(own.ifEmpty { song.notes })
    }
    // Chord names follow a transpose: the bars are labelled again from the moved notes.
    val chords = remember(song, chordTrack) {
        if (chordTrack == null || transpose == 0) chordTrack
        else com.bigpapituna.sharpears.engine.ChordTrack(com.bigpapituna.sharpears.engine.labelBarsAligned(song), com.bigpapituna.sharpears.engine.barMs(song))
    }
    val transport = remember(song) { Transport(song, playable = range).apply { mode = start.mode; practice = start.practice } }
    lights.showCombo = !kid                                       // a kid's lane counts no streak
    // Wrong keys shown on the keyboard and the lane: yellow for a slip of up to two semitones, red further away.
    val wrongs = rememberWrongFlash(slow = start.lightSlow)
    LaunchedEffect(profile.id, profile.kind, profile.micOffsetDb) { mic.forProfile(profile.isKid, profile.micOffsetDb) }
    // Hint escalation while wait mode is stuck (glow, the hand points, the app plays it), and the octave hint.
    val hintPlan = remember(profile.hintSeconds) { HintPlan(profile.hintSeconds) }
    // Not where any key of a range will do ("any key above C4"): there the octave is the point.
    val octaveOn = remember(song, anyOfLessons) { OctaveHint.appliesTo(song.id, anyOfLessons) && song.groups.none { it.anyOf } }
    // Pup's Playground: a group held on goes by after three tries (RoundCoach without its demo: the hints show the key).
    val waitCoach = remember(letGo) { if (letGo) com.bigpapituna.sharpears.engine.playground.RoundCoach(firstDemoMs = 0, demoMs = 0, againDemoMs = 0, quietMs = LET_GO_QUIET_MS) else null }
    LaunchedEffect(highFive) { if (highFive > 0) { showHighFive = true; delay(2400); showHighFive = false } }
    val context = androidx.compose.ui.platform.LocalContext.current
    // MIDI steps: the sustain pedal against each chord, and soft-then-loud by velocity.
    val special = embedded?.special
    val pedalGrade = remember(song) { if (special == com.bigpapituna.sharpears.engine.StepKind.PEDAL) PedalGrader(PedalGrader.chordsOf(song)) else null }
    val loudness = remember(song) { if (special == com.bigpapituna.sharpears.engine.StepKind.DYNAMICS) DynamicsGrader(song.notes.count { it.hand == Hand.RIGHT }) else null }
    val scope = rememberCoroutineScope()
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    val world = LocalWorld.current
    val everyone = com.bigpapituna.sharpears.ui.family.LocalProfiles.current?.all.orEmpty()
    // Touch keys buzz (right: a tick, wrong: a double tap); the mic and MIDI never do, the phone may be on a stand.
    val haptics = com.bigpapituna.sharpears.ui.motion.rememberHaptics().also { it.enabled = prefs.haptics }

    val tutorial = remember(song) {
        tutorialOf(meta, song, prefs)
    }
    val windowSize = LocalWindowInfo.current.containerSize
    val windowHeightDp = with(LocalDensity.current) { windowSize.height.toDp() }
    val fingers = remember(song) { com.bigpapituna.sharpears.engine.Fingering.of(song) }

    // Metronome (every song) and backing (progressions: the app plays the left-hand root, the right hand is yours).
    DisposableEffect(Unit) { onDispose { if (clicks.isInitialized()) clicks.value.release(); rider?.release() } }
    val metro = remember(song) { com.bigpapituna.sharpears.engine.Metronome(song.bpm, song.timeSignature) }
    val isProgression = song.id.startsWith("prog-") || song.id.startsWith("pop-")

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
    fun flushPractice() {
        val secs = (practiceMs[0] / 1000).toInt()
        if (secs <= 0) return
        practiceMs[0] -= secs * 1000.0
        prefs.addPractice(com.bigpapituna.sharpears.data.AppClock.today(), secs)
        progress?.record(ProgressEvent.Practice(com.bigpapituna.sharpears.data.AppClock.now(), secs))      // after Prefs: the goal reads both
        Log.d(TAG, "practice +$secs s")
    }
    // 2.14: also asked by the session's checkpoint and end (data/Briefs.kt), so the brief's minutes and the goal's agree.
    DisposableEffect(Unit) { val f = ::flushPractice; com.bigpapituna.sharpears.data.Briefs.addFlusher(f); onDispose { com.bigpapituna.sharpears.data.Briefs.removeFlusher(f); flushPractice() } }

    fun seek(ms: Double) { apply(transport.seek(ms)); position = transport.positionMs; lights.resetCombo() }
    // The last minute or so of inputs and waits, for a "that wasn't right" report.
    fun tail(line: String) {
        tailLines.addLast(java.time.LocalTime.now().toString().take(12) + "  " + line)
        while (tailLines.size > 60) tailLines.removeFirst()
    }
    fun at(ms: Double) = "%d:%02d".format(java.util.Locale.ROOT, (ms / 60000).toInt(), ((ms / 1000) % 60).toInt())
    // Keyboard as answer key (MIDI on USB with the mic on: the mic plays, the keyboard checks it). Mismatches close
    // together go into one clip, saved once things have been quiet for 3 s.
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
            append("the last minute (newest last):\n"); tailLines.forEach { append("  ").append(it).append('\n') }
        }
        Log.d(TAG, "answer key: ${answerLines.size} mismatch(es), saving $name")
        mic.saveStall(answerState[0] + 2L * com.bigpapituna.sharpears.audio.MicInput.RATE, report, name)
        answerLines.clear()
    }
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
    fun stallBegin() {
        if (!micOn || transport.mode != Mode.WAIT) return
        stallWatch.profile = mic.profileKind
        stallMark[0] = mic.clipMark()
        stallWatch.start(System.currentTimeMillis(), transport.expected.map { noteName(it.midi) },
            "song=${song.id} title=${song.title} position_ms=${transport.positionMs.toLong()} speed=${transport.speed} hands=${transport.practice}\n" +
                "group=${transport.listenGroup.sorted().joinToString(",") { noteName(it) }} mic=${mic.source.value} " +
                "sensitivity_db=${mic.matcher.config.sensitivityDb} mic_offset_db=${mic.matcher.offsetDb} recorded=${java.time.LocalDateTime.now()}")
    }
    fun stallEnd(outcome: String) {
        val s = stallWatch.end(System.currentTimeMillis(), outcome) ?: return
        val name = "stall-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + song.id
        Log.d(TAG, "stuck moment: ${s.waitedMs} ms on ${transport.expected.map { noteName(it.midi) }} ($outcome), saving $name")
        val title = "Stuck: ${song.title} at ${at(transport.positionMs)} on ${transport.expected.joinToString(", ") { noteName(it.midi) }} (${s.waitedMs / 1000} s, $outcome)"
        mic.saveStall(stallMark[0], "title=$title\n" + s.report, name)
    }
    /** The pup says [line] over the lane (and aloud when the mic is not listening through the speaker). */
    // A hint is said while the mic listens (that is when hints come): over it, the mic deaf while the line plays, as the
    // Playground's call-outs are. speak(deferrable = false) dropped every one of them unless headphones were on.
    fun say(line: String) { hintLine = line; voice.speakOverMic(line) }
    /** A wait began: hint escalation (wait mode, labels shown) and the octave hint (mic) start over. */
    fun hintsBegin() {
        if (transport.mode == Mode.WAIT) waitCoach?.begin(0, android.os.SystemClock.uptimeMillis())
        hintStep = HintStep.NONE; octaveShown = null
        if (transport.mode == Mode.WAIT && labels) hintPlan.begin(android.os.SystemClock.elapsedRealtime())
        if (micOn && octaveOn && transport.mode == Mode.WAIT) mic.octaveReset(transport.listenFor)
    }
    fun hintsEnd() {
        transport.tally.waitEnded()
        waitCoach?.found()
        if (hintPlan.waiting || hintStep != HintStep.NONE || octaveShown != null) Log.d(TAG, "hints: wait over at $hintStep")
        hintPlan.end(); hintStep = HintStep.NONE; octaveShown = null; mic.octaveReset(emptySet())
    }
    /**
     * Hint step c: the app plays the waiting notes and the pup says "your turn". With the mic listening through the
     * speaker it is deaf meanwhile, so it does not take the phone's note for the player's.
     */
    fun playHint() {
        val notes = transport.hintKeys                        // an any-of group: only its demonstration key
        if (notes.isEmpty()) return
        if (prefs.sound) {
            if (micOn && !mic.headphonesOn()) mic.deafFor(1_700)
            notes.forEach { sampler.noteOn(it, 80) }
            scope.launch { delay(900); notes.forEach { sampler.noteOff(it) } }
        }
        Log.d(TAG, "hints: the app plays ${notes.map { noteName(it) }}")
        say(com.bigpapituna.sharpears.engine.hints.HintPlan.YOUR_TURN)
    }
    /**
     * Pup's Playground: the group waited on goes by ("Good try, let's keep going", said over the mic if it listens): its
     * notes go to the transport as if played, so the run moves on.
     */
    fun letGoGroup() {
        val keys = transport.expected.map { it.midi }
        if (keys.isEmpty()) return
        Log.d(TAG, "let go: ${keys.map { noteName(it) }} after ${waitCoach?.tries} tries")
        transport.tally.letGo()
        hintLine = com.bigpapituna.sharpears.engine.playground.PlaygroundLines.LET_GO
        voice.speakOverMic(com.bigpapituna.sharpears.engine.playground.PlaygroundLines.LET_GO)
        for (k in keys) if (transport.noteInput(k) == InputResult.GROUP_DONE) break
        version++
    }
    /** A try that was not it, while wait mode holds: the coach counts it (Pup's Playground). */
    fun waitMiss() {
        if (!transport.waiting) return
        if (waitCoach?.miss(android.os.SystemClock.uptimeMillis()) is com.bigpapituna.sharpears.engine.playground.RoundCoach.Cue.LetGo) letGoGroup()
    }
    /** A wrong key from any input: counted, logged, and handed to whoever draws it (the skin's flash, the pup). */
    fun showWrongKey(played: Int, nearest: Int?, distance: Int?) {
        val k = WrongKey(played, nearest, distance, (++wrongKeys).toLong())
        transport.tally.wrong(played, nearest)
        Log.d(TAG, "wrong key ${noteName(played)}" + (nearest?.let { " near ${noteName(it)}, $distance away, ${if (k.slip) "yellow" else "red"}" } ?: ""))
        distance?.let { wrongs.show(com.bigpapituna.sharpears.engine.skin.WrongKey(played, it)) }
        onWrongKey(k)
        cheer.wrong(cheers, transport, song, embedded != null)        // 2.13: the cheer pops' run starts again
    }
    /** Asks the room-noise watchdog; the pup offers once per session. */
    fun checkNoise(now: Long) {
        val s = mic.noiseWatch.check(now) ?: return
        Log.d(TAG, "noise watch: offer (${s.noise} noise, ${s.painted} painted in the last minute, pause=${s.pauseListening})")
        noiseOffer = s
        voice.speakOverMic(NoiseWatch.line(kid))
        Briefs.log { BriefEvent.NoisyRoom(it, "player") }
    }
    fun pause() { stallEnd("paused"); hintsEnd(); apply(transport.pause()); playing = false; rider?.stop(); savePosition(); flushPractice(); Log.d(TAG, "transport pause at ${transport.positionMs.toLong()} ms") }
    fun play() {
        recap = null
        // A fresh run starts early so its first notes are seen falling: Perform counts in two bars (clicks and
        // a beat counter), the backing one bar, Wait and Listen run in two beats.
        val leadIn = when {
            transport.mode == Mode.PERFORM -> 2 * metro.barMs
            backingOn -> metro.barMs
            else -> 2 * metro.beatMs
        }
        if (transport.fresh) { recorder.clear(); touchRun = false; midiRun = false; transport.tally.clear(); cheer.began(cheers, song) }
        transport.tally.open = true
        if (transport.fresh) Log.d(TAG, (if (transport.mode == Mode.PERFORM || backingOn) "count-in" else "lead-in") +
            " ${leadIn.toLong()} ms to the first group at ${transport.anchorMs.toLong()} ms (from ${transport.positionMs.toLong()} ms)")
        transport.play(leadInMs = leadIn); playing = true; tickNanos = 0L
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
        // The key itself glows yellow or red either way ([wrongs], from showWrongKey); no red border any more.
        if (prefs.feedbackLight) { lights.wrong(breaksCombo = transport.mode != Mode.PERFORM); Log.d(TAG, "light wrong") }
    }
    /** The run as played so far, kept in memory and in files/runs (library songs). */
    fun keepRun() {
        if (meta == null || recorder.size == 0) return
        val r = recorder.take(meta.id, transport.speed)
        lastRun = r
        runs.save(r)
    }
    fun keyReleased(midi: Int) { if (transport.playing) recorder.off(midi, transport.positionMs) }
    /** [midiOffsetMs] (2.14): the MIDI timing offset, given to Perform's scoring alone (never the recording or Wait mode). */
    fun keyStruck(midi: Int, latencyMs: Double = 0.0, velocity: Int = 80, riseDb: Double = Juice.DEFAULT_RISE, midiOffsetMs: Double = 0.0): InputResult {
        // Juice on every note, whatever it was: the pups hop to its pitch, a soft tick (the sampler's sound rule, so it
        // never feeds the mic), a crumb in the treat jar.
        Juice.onStrike(midi, riseDb, sound = soundAllowed())
        if (transport.playing && transport.mode != Mode.LISTEN) recorder.on(midi, transport.positionMs - latencyMs * transport.speed, velocity)
        // Perform: a note that raised the "good" count was on time (Perfect), any other right note was OK (Good).
        val good0 = transport.score?.goodCount ?: 0
        val expectedBefore = transport.expected.map { it.midi }
        // 2.14: in Perform a key is timed between frames too (the transport moves a frame at a time; up to 16 ms early before).
        val sinceTick = if (transport.mode == Mode.PERFORM && transport.playing && tickNanos != 0L) ((System.nanoTime() - tickNanos) / 1e6).coerceIn(0.0, 250.0) else 0.0
        val result = transport.noteInput(midi, latencyMs - sinceTick + if (transport.mode == Mode.PERFORM) midiOffsetMs else 0.0)
        val grade = if (transport.mode == Mode.PERFORM) (if ((transport.score?.goodCount ?: 0) > good0) HitLights.PERFECT else HitLights.GOOD) else null
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
            InputResult.WRONG -> {
                if (kid) kidPup.play(com.bigpapituna.sharpears.engine.pup.PupMood.TILT)
                wrongFeedback(); struggle.mistake(android.os.SystemClock.elapsedRealtime())
                WrongKey.of(midi, expectedBefore, 0).let { showWrongKey(midi, it.nearest, it.distance) }
                waitMiss()
            }
            InputResult.CORRECT, InputResult.GROUP_DONE -> {
                // A C played right counts toward a kid's quest, "Find all the Cs" (QA 2.8.3: Where C lives' every C did not).
                if (profile.isKid && midi in com.bigpapituna.sharpears.engine.play.QuestTask.ALL_CS) questStore?.recordQuest(com.bigpapituna.sharpears.engine.play.QuestEvent.NotePlayed(midi), kid = true)
                if (struggle.right()) { highFive++; Log.d(TAG, "pup: high five, through a hard passage") }
                if (kid) kidPup.play(com.bigpapituna.sharpears.engine.pup.PupMood.WAG)
                onRightKey(midi)
                // 2.14: in Perform the hit's early or late mark goes under the hit line (HitLights.tick).
                val off = if (grade != null) transport.score?.lastOffset else null
                if (prefs.feedbackLight) { val h = handOf(midi); lights.hit(midi, handColor(h), grade, tickMs = off); Log.d(TAG, "light hit ${noteName(midi)} $h" + (grade?.let { " $it" } ?: "") + (off?.let { " ${it.toInt()} ms" } ?: "")) }
                else scope.launch { flashes[midi] = Good; delay(200); flashes.remove(midi) }
            }
            // Listen mode, a count-in, a key between groups: no grade, a soft glow up its column.
            InputResult.IGNORED -> if (prefs.feedbackLight) lights.touch(midi, handColor(handOf(midi)))
        }
        // 2.13: the cheer pops hear it (a chord once, a Perform note with its timing).
        cheer.struck(cheers, transport, song, result, grade == HitLights.PERFECT, embedded != null, hintStep == HintStep.PLAY)
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
        transport.tally.open = false
        embedded?.onTally?.invoke(transport.tally.copy())
        onFinished(transport.report().copy(extraScore = extra, realMs = transport.runRealMs).also { Log.d(TAG, "run report ${song.id}: $it") })
        keepRun()
        if (meta == null) {                                // generated songs (lessons, progressions) keep no progress
            // A pop song (songs by chords, 2.6) keeps its runs under its own id, like a library song (see popRunEnd).
            val popRun = com.bigpapituna.sharpears.engine.chords.PopPlay.runIdOf(original.id)
            if (popRun != null && embedded == null) {
                recap = popRunEnd(popRun, transport, run, prefs, progress)?.timed(transport, speed, midiRun)?.also { if (touchRun) haptics.success() }
                return
            }
            run?.let { recap = RunEnd(it, false, emptyList(), timeMs = transport.runRealMs.toLong()).timed(transport, speed, midiRun) }
            Log.d(TAG, "finished ${song.id} mode=${transport.mode} recap=$run")
            return
        }
        // A run the player played: not Listen (the app plays it), and not one that reached nothing (Play pressed at the
        // end, after a seek or the seek bar dragged there, was a "Clean run!" with a sticker and 50 XP, and wiped the
        // song's hardest bars).
        val played = transport.mode != Mode.LISTEN && !transport.report().empty
        // The parent brief's log: the run, its wrong keys and octave hints (a duet partner gets the run too, below).
        val briefRun = if (!played) emptyList() else RunEvents.song(Briefs.now(), meta.id, transport.mode, transport.practice, transport.report(),
            transport.tally, transport.runRealMs.toLong(), fromTop = transport.scoreFromMs <= 0.0, duet = duet != null)
        Briefs.logAll(briefRun)
        // The hardest bars of a Wait or Perform run, remembered for the library row.
        val spots = if (!played) null else when (transport.mode) {
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
            val after = AutoSpeed.afterRun(before, effective, acc, com.bigpapituna.sharpears.data.AppClock.today())
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
        if (played) { prefs.addHandDone(meta.id, transport.practice); Log.d(TAG, "hands done ${song.id}: ${prefs.handsDone(meta.id)}") }
        val wasCompleted = prefs.progress(meta.id).completed
        // The recap shows the XP this run earned and the level bar, as lessons do.
        val xpBefore = progress?.xp
        fun xp(gained: Int?) = gained?.takeIf { it > 0 }
        fun bar() = progress?.let { p -> xpBefore?.let { com.bigpapituna.sharpears.ui.profile.xpBarFor(it, p.xp, p.level) } }
        val timeMs = transport.runRealMs.toLong()
        // A run the player played drops a sticker and counts for today's quest.
        val reward = if (played) world?.songComplete(meta.id, run?.accuracy) else null
        if (played) PlayStore(context, profile.id).recordQuest(QuestEvent.SongFinished(meta.id), profile.isKid)
            .forEach { Log.d(TAG, "quest: ${it.kind} done by ${song.id}") }
        // A duet: the partner gets the whole run too (their own best and first completion), and today's quest.
        val partner = duet?.partnerOf(profile.id)?.let { id -> everyone.firstOrNull { it.id == id } }
        fun creditPartner(accuracy: Int?) {
            val p = partner ?: return
            val partnerPrefs = Prefs(context, p.id)
            val before = partnerPrefs.progress(meta.id)
            if (accuracy != null && transport.scoreFromMs <= 0.0 && accuracy > 0) partnerPrefs.saveScore(meta.id, accuracy) else partnerPrefs.markCompleted(meta.id)
            ProgressStore(context, partnerPrefs).record(Duet.partnerRun(meta.id, accuracy, before.bestAccuracy.takeIf { it >= 0 }, before.completed))
            PlayStore(context, p.id).recordQuest(QuestEvent.SongFinished(meta.id), p.isKid)
            com.bigpapituna.sharpears.data.WorldStore(context, p.id).songComplete(meta.id, accuracy)
            briefRun.firstOrNull()?.let { Briefs.logFor(p.id, it) }
            Log.d(TAG, "duet: ${p.id} credited for ${song.id} (accuracy=$accuracy)")
        }
        val duetLine = partner?.let { p ->
            val stars = Stars.of(run, transport.report().wrongKeys)
            val left = if (duet?.leftId == profile.id) profile.name else p.name
            val right = if (duet?.leftId == profile.id) p.name else profile.name
            Duet.recapLine(left, right, stars)
        }
        if (run != null && played) {
            val r = run
            // Only a run from the top counts as a best score.
            val best = transport.scoreFromMs <= 0.0 && r.accuracy > 0 && prefs.saveScore(meta.id, r.accuracy)
            val earned = progress?.record(ProgressEvent.SongRun(meta.id, r.accuracy, newBest = best, firstCompletion = !wasCompleted))
            creditPartner(r.accuracy)
            recap = RunEnd(r, best, spots.orEmpty(), canReplay = lastRun != null, timeMs = timeMs, xpGained = xp(earned?.xpGained), xpBar = bar(),
                reward = reward, duetLine = duetLine).timed(transport, speed, midiRun)
            if (touchRun) haptics.success()
            Log.d(TAG, "perform recap ${song.id}: $r accuracy=${r.accuracy} from=${transport.scoreFromMs.toLong()} newBest=$best")
        } else if (!played) {
            // Listen (the app played it) or nothing reached: it opens at the top again, not marked played, no song XP.
            prefs.savePosition(meta.id, 0)
            Log.d(TAG, (if (transport.mode == Mode.LISTEN) "listened through " else "nothing played in the run of ") + song.id)
            if (transport.mode == Mode.LISTEN) com.bigpapituna.sharpears.analytics.Events.songListened(meta.id)
        } else {
            prefs.markCompleted(meta.id)
            val earned = progress?.record(ProgressEvent.SongRun(meta.id, null, newBest = false, firstCompletion = !wasCompleted))
            if (played) creditPartner(null)
            if (transport.mode == Mode.WAIT) {
                recap = RunEnd(null, false, spots.orEmpty(), wrongKeys = transport.report().wrongKeys, canReplay = lastRun != null,
                    timeMs = timeMs, xpGained = xp(earned?.xpGained), xpBar = bar(), reward = reward, duetLine = duetLine)
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
                    else {
                        Log.d(TAG, "NoteOn ${noteName(e.midi)} midi=${e.midi} vel=${e.velocity} src=midi")
                        if (transport.playing) midiRun = true
                        keyStruck(e.midi, velocity = e.velocity, riseDb = Juice.riseOf(e.velocity), midiOffsetMs = com.bigpapituna.sharpears.engine.MidiOffset.latencyMs(midi.offsetMs))
                    }
                }
                is NoteEvent.Off -> { held.remove(e.midi); if (!micOn) keyReleased(e.midi) }
            }
        }
    }
    // A keyboard unplugged with keys down sends no note-offs: they stayed lit until the player closed.
    LaunchedEffect(midi.connected.value) { if (midi.connected.value == null) held.clear() }

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
    // The full player listens between runs too, except while a run's recap is up (the pup talks then).
    val between = (embedded == null && recap == null) || playing
    DisposableEffect(micOn, mode, visible, voiceHolds, between, replaying) {
        if (voiceHolds && micOn) Log.d(TAG, "mic: waiting for the voice")
        // Inside a lesson the mic listens only while the step runs, so the voice can speak between runs.
        val listens = micOn && mode != Mode.LISTEN && visible && !voiceHolds && between && !replaying
        val truth = micOn
        if (listens) {
            if (start.inject != null) mic.startInjection(start.inject)
            else if (!mic.start()) { Log.d(TAG, "mic: could not start"); micOn = false }
        }
        if (micOn) midi.onTruth = mic::onMidiTruth
        // Only what this effect started: a leaving player's last dispose used to stop the next screen's mic.
        onDispose { stallEnd("left"); if (listens) mic.stop(); if (truth) midi.onTruth = null }
    }
    LaunchedEffect(version, micOn) {
        transport.chordSlack = if (micOn) 1 else 0
        mic.matcher.expected = if (micOn) transport.listenFor else emptySet()
        // A chord is one group; an any-of group's keys each their own (they never sound together).
        mic.matcher.groups = transport.listenGroups
    }
    LaunchedEffect(mic) {
        mic.events.collect { e ->
            if (!micOn) return@collect
            when (e) {
                is MicEvent.Strike -> {
                    Log.d(TAG, "NoteOn ${noteName(e.midi)} midi=${e.midi} src=mic rise=${"%.1f".format(e.riseDb)}dB delay=${e.delayMs?.let { "%.0fms".format(it) } ?: "?"}" +
                        (e.inferredFrom?.let { " (inferred from ${noteName(it)})" } ?: ""))
                    if (midi.connected.value != null) answerKey.mic(System.currentTimeMillis(), e.midi)
                    stallWatch.heard(System.currentTimeMillis(), "${noteName(e.midi)} heard, rise ${"%.1f".format(java.util.Locale.ROOT, e.riseDb)} dB" +
                        (e.inferredFrom?.let { " (credited from ${noteName(it)})" } ?: ""))
                    mic.noiseWatch.right(android.os.SystemClock.elapsedRealtime())
                    keyStruck(e.midi, latencyMs = e.delayMs ?: 0.0, riseDb = e.riseDb)
                }
                is MicEvent.Wrong -> if (transport.waiting) {
                    val what = "wrong key ${noteName(e.midi)}, ${e.distance} from ${noteName(e.nearest)}, rise ${"%.1f".format(java.util.Locale.ROOT, e.riseDb)} dB"
                    tail("mic: $what")
                    stallWatch.heard(System.currentTimeMillis(), what)
                    val paint = mic.paintsWrongKeys
                    if (paint) {
                        Juice.onStrike(e.midi, e.riseDb, sound = soundAllowed())
                        wrongFeedback()
                        showWrongKey(e.midi, e.nearest, e.distance)
                        val now = android.os.SystemClock.elapsedRealtime()
                        mic.noiseWatch.wrong(now); checkNoise(now)
                    }
                    Log.d(TAG, "input mic -> WRONG ${noteName(e.midi)} at ${transport.positionMs.toLong()} ms (painted=$paint)")
                    if (!paint) Juice.onHeard()
                    if (!paint && !mic.wrongPaintOff) transport.tally.wrong(e.midi, e.nearest)    // not shown, still a wrong key (not the room's)
                    waitMiss()
                }
            }
        }
    }
    // Loudness jumps with no piano note in them: the room-noise watchdog counts them while a run plays.
    LaunchedEffect(mic) {
        mic.noise.collect {
            if (!micOn || !transport.playing) return@collect
            val now = android.os.SystemClock.elapsedRealtime()
            mic.noiseWatch.noise(now)
            if (transport.waiting) stallWatch.heard(System.currentTimeMillis(), "noise (no piano note in it)")
            // "I heard you": every pup on screen hops a little, even for a sound with no key in it.
            Juice.onHeard()
            checkNoise(now)
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.d(TAG, "mic permission granted=$granted")
        // Refused (twice, and Android stops asking): the mic button did nothing at all; the pup says where to allow it.
        if (!granted) hintLine = MIC_REFUSED_LINE
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
    DisposableEffect(song) { onDispose {
        if (transport.tally.open) embedded?.onTally?.invoke(transport.tally.copy())     // a lesson's try left before its end
        savePosition(); apply(transport.pause()); if (lastRun == null) keepRun() } }
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
    // Practice today sums the seconds a part played when it is left: they are written before the caller moves on.
    if (embedded == null) BackHandler { flushPractice(); onBack() }
    // A lesson step starts by itself once the voice has read it out (and the sounds are loaded).
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
        // Debug (`--es midi`): the pretend keyboard starts with the transport's first frame, not at Play, so a slow first
        // frame (200 ms on a loaded emulator) does not put every scripted note early (2.14).
        if (start.midiScript != null && !scriptStarted) { scriptStarted = true; midi.debugPlay(start.midiScript, last / 1_000_000) }
        // Frames stop while the app is off screen (Home, the screen locked): the first frame back must not play the song
        // on by the time away (it jumped 40 s) nor count it as practice, so the step leaves out the time away.
        var awayLast = com.bigpapituna.sharpears.engine.ForegroundClock.app.awayMs()
        var wasWaiting = transport.waiting
        if (wasWaiting) { stallBegin(); hintsBegin() }   // resumed in the middle of a wait
        var listening = transport.listenFor
        var passes = transport.passes
        while (transport.playing) {
            withFrameNanos { now ->
                val before = transport.positionMs
                val away = com.bigpapituna.sharpears.engine.ForegroundClock.app.awayMs().let { a -> (a - awayLast).also { awayLast = a } }
                val step = ((now - last) / 1_000_000.0 - away).coerceAtLeast(0.0)
                practiceMs[0] += step
                if (practiceMs[0] >= 15_000) flushPractice()
                val events = transport.tick(step)
                if (events.isNotEmpty()) apply(events)
                // Clicks come from the phone's speaker, so they follow the same rule as the sampler.
                // The Perform count-in always clicks, metronome or not.
                val clickTo = when {
                    metronomeOn || backingOn -> transport.positionMs
                    mode == Mode.PERFORM -> minOf(transport.positionMs, transport.anchorMs)
                    else -> before
                }
                if (clickTo > before && soundAllowed()) metro.clicks(before, clickTo).forEach { clicks.value.play(it) }
                // A pop song's band (drums and bass) rides on the Backing toggle, in step with the transport.
                if (backingOn && band != null && transport.positionMs > before && soundAllowed())
                    (rider ?: com.bigpapituna.sharpears.ui.chords.BandRider(context, sampler, band).also { rider = it }).pass(before, transport.positionMs)
                if (transport.positionMs < before) Log.d(TAG, "loop wrap ${before.toLong()} -> ${transport.positionMs.toLong()} ms")
                if (transport.passes != passes) { passes = transport.passes; transport.lastPass?.let(::passEnded) }
                last = now
                tickNanos = now
                position = transport.positionMs
                if (transport.waiting != wasWaiting) {
                    wasWaiting = transport.waiting
                    version++
                    if (wasWaiting) Log.d(TAG, "wait at ${transport.positionMs.toLong()} ms expect=${transport.expected.map { noteName(it.midi) }}")
                    if (wasWaiting) stallBegin() else stallEnd("played")
                    if (wasWaiting) hintsBegin() else hintsEnd()
                    if (wasWaiting) tail("waiting at ${at(transport.positionMs)} for ${transport.expected.joinToString(", ") { noteName(it.midi) }}")
                }
                // Pup's Playground: three tries (or quiet stretches) and the group goes by.
                if (transport.waiting && waitCoach?.tick(android.os.SystemClock.uptimeMillis()) is com.bigpapituna.sharpears.engine.playground.RoundCoach.Cue.LetGo) letGoGroup()
                // Stuck: the key glows, then the hand points at it, then the app plays it.
                hintPlan.advance(android.os.SystemClock.elapsedRealtime())?.let { step ->
                    hintStep = step
                    transport.tally.hint(step.ordinal)
                    Log.d(TAG, "hints: $step on ${transport.expected.map { noteName(it.midi) }} after ${hintPlan.stepsMs[step.ordinal - 1] / 1000} s")
                    if (step == HintStep.PLAY) playHint()
                }
                // Perform mode: what the mic listens for moves with the song.
                if (mode == Mode.PERFORM && transport.listenFor != listening) { listening = transport.listenFor; version++ }
                // The lane's combo counter follows the Perform streak (only misses break it).
                if (mode == Mode.PERFORM) transport.score?.let { lights.streak(it.streakNow) }
                // 2.13: the cheer pops' time, the Perform notes let pass, the run's progress.
                cheer.frame(cheers, transport, song, now / 1_000_000, hintStep == HintStep.PLAY, embedded != null)
            }
        }
        playing = false
        rider?.stop()
        hintsEnd()
        flushPractice()
        Log.d(TAG, "transport stopped at ${transport.positionMs.toLong()} ms")
        if (transport.positionMs >= song.durationMs) finished()
    }

    val tabletop = rememberTabletop(force = start.tabletop)
    val waitingNow = remember(version) { transport.waiting }
    // The octave hint: while a wait-mode wait listens, ask the mic four times a second.
    LaunchedEffect(waitingNow, micOn, mode, playing) {
        if (!waitingNow || !micOn || !octaveOn || mode != Mode.WAIT || !playing) return@LaunchedEffect
        while (true) {
            delay(250)
            mic.octaveCheck()?.let { h ->
                Log.d(TAG, "octave hint: ${noteName(h.expected)} played ${if (h.playedOctaveOffset > 0) "an octave up" else "an octave down"}")
                octaveShown = h
                transport.tally.octave()
                say(h.line(kid))
            }
        }
    }
    LaunchedEffect(hintLine) { if (hintLine != null) { delay(4_500); hintLine = null } }
    val moving = playing && !waitingNow && transport.positionMs >= 0
    val showVideo = videoOn && tutorial != null

    // The bar needs the playhead to a tenth of a second (a slider pixel is a few hundred ms of a song): read per frame, the
    // whole bar recomposed 60 times a second while a song played.
    val barPosition by remember { derivedStateOf { kotlin.math.floor(position / 100.0) * 100.0 } }
    @Composable
    fun Bar() = OwnScope { TransportBar(
        BarState(playing, ready, maxOf(barPosition, transport.runFromMs, 0.0), song.durationMs, speed, mode, practice, loopStart, loopEnd, song.sections,
            midi.connected.value, micOn, { mic.level.floatValue }, title = song.title,
            // Under the title: the composer, and the part of the song the playhead is in (medleys, progressions).
            subtitle = listOfNotNull(
                if (transpose != 0) "${if (transpose > 0) "Up" else "Down"} ${kotlin.math.abs(transpose)} ${if (kotlin.math.abs(transpose) == 1) "semitone" else "semitones"}" else null,
                meta?.composer?.takeIf { it.isNotEmpty() },
                song.sections.lastOrNull { it.startMs <= barPosition }?.name).joinToString(", "),
            videoOn = if (tutorial != null) videoOn else null,
            metronome = metronomeOn,
            ramp = if (ramp != null) rampPercent else null,
            auto = if (autoOn) autoPercent else null,
            chords = if (chords != null) chordsOn else null,
            transpose = transpose,
            backing = if (isProgression) backingOn else null,
            backingLabel = if (band != null) "Band" else "Backing",
            status = when {
                mode != Mode.PERFORM -> ""
                barPosition < transport.anchorMs -> if (playing) "Get ready" else ""
                kid -> ""                                          // no numbers for kids
                else -> transport.score?.let { "Streak ${it.streakNow}" } ?: ""
            },
            kid = kid,
            layout = if (kid) com.bigpapituna.sharpears.engine.nav.Layout.FULL else LocalLayout.current.layout,     // the layout (2.8): the extras in one More sheet
            duet = duet?.let { d -> d.partnerOf(profile.id)?.let { id -> "Duet with " + (com.bigpapituna.sharpears.ui.family.LocalProfiles.current?.all?.firstOrNull { it.id == id }?.name ?: "") } }
                ?: if (meta != null && embedded == null && (com.bigpapituna.sharpears.ui.family.LocalProfiles.current?.all?.size ?: 0) >= 2) "" else null),
        BarActions(
            onBack = { flushPractice(); onBack() },
            onPlayPause = { if (playing) pause() else play() },
            onSpeed = { ramp = null; autoOn = false; setSpeed(it) },
            onRamp = { autoOn = false; ramp = com.bigpapituna.sharpears.engine.TempoRamp().also { setSpeed(it.speed); rampPercent = it.percent }; Log.d(TAG, "ramp on at 50%") },
            onAuto = { ramp = null; autoOn = true; setSpeed(autoPercent / 100.0); Log.d(TAG, "auto speed on at $autoPercent%") },
            onMode = ::setMode,
            onPractice = { if (duet == null) { practice = it; transport.practice = it; version++; Log.d(TAG, "hands=$it") } },
            onDuet = { if (duet != null) { duet = null; Log.d(TAG, "duet: off") } else { if (playing) pause(); pickingDuet = true } },
            onSeek = { seek(it) },
            onLoop = {
                when {
                    loopStart == null -> setLoop(transport.positionMs.coerceAtLeast(0.0), null)
                    // An end marked before the start (the seek bar went back) swapped nothing: the bar showed "0:20-0:10"
                    // and nothing looped. The two now swap; the same spot twice waits for an end somewhere else.
                    loopEnd == null -> { val a = loopStart!!; val b = transport.positionMs.coerceAtLeast(0.0); if (b != a) setLoop(minOf(a, b), maxOf(a, b)) }
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
        lead = if (kid && embedded == null) ({
            Pup(if (playing) Pose.LISTENING else Pose.GET_READY, size = 48.dp, controller = kidPup, idle = false,
                bounce = com.bigpapituna.sharpears.ui.motion.BounceSize.NONE)
        }) else null,
    ) }
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
            tailLines.forEach { append("  ").append(it).append('\n') }
        }
        Log.d(TAG, "flag: $what at ${at(flagAt)}, saving $name")
        if (mic.canClip) mic.saveStall(flagMark + 2L * com.bigpapituna.sharpears.audio.MicInput.RATE, report, name)
        else { mic.reporter.saveText(name, com.bigpapituna.sharpears.engine.StallWatch.tagProfile(report, mic.profileKind)); mic.reporter.flush() }
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
            wrong = wrongs,
        )
        // Perform count-in: the beats of the bar near the hit line, the current one bright.
        if (playing && mode == Mode.PERFORM && position < transport.anchorMs)
            BeatCounter(metro.beatsPerBar, metro.beatInBar(position), Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
        // The pup: ready during the count-in, a high-five after a hard passage comes good.
        if (showHighFive) PupSays(Pose.GREAT_JOB, com.bigpapituna.sharpears.engine.lessons.CheerLines.highFive(kid), Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
        else if (hintLine != null) PupSays(Pose.LEARNING, hintLine!!, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).widthIn(max = 420.dp), idle = false)
        else if (playing && mode == Mode.PERFORM && position < transport.anchorMs)
            Pup(Pose.GET_READY, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), size = 72.dp, idle = false)
        // Before the first Play: a quiet hint, nothing else.
        if (!playing && transport.fresh && position < transport.anchorMs && embedded?.autoStart != true)
            Text("Press play", style = MaterialTheme.typography.titleMedium, color = InkDim,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
        noiseOffer?.let { offer ->
            NoiseSheet(
                onKeep = { noiseOffer = null; Log.d(TAG, "noise watch: keep showing wrong keys") },
                onStop = { mic.wrongPaintOff = true; noiseOffer = null; Log.d(TAG, "noise watch: wrong keys off for this session") },
                // Session only: the mic goes off for this song; the "mic on" setting is not touched.
                onPause = if (offer.pauseListening && mode == Mode.WAIT) ({ noiseOffer = null; micOn = false; Log.d(TAG, "noise watch: listening paused") }) else null,
                modifier = Modifier.align(Alignment.Center).padding(16.dp), kid = kid,
            )
        }
        if (chordsOn && chords != null) ChordLabel(chords, position, song.preferFlats, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 10.dp))
        // 2.13: a song's cheer pop, in a top corner of the notes no deeper than CheerPlace.LANE_BAND of the lane (so it never
        // sits over the notes being read or the line), on the side with fewer notes coming through; the chord names keep the
        // left corner and the video the right. A lesson draws its pops over its header instead.
        if (embedded == null && cheers != null) {
            val laneDp = maxWidth.value
            val sides = buildSet { if (!(chordsOn && chords != null)) add(com.bigpapituna.sharpears.engine.lessons.CheerSide.START)
                if (!showVideo) add(com.bigpapituna.sharpears.engine.lessons.CheerSide.END) }
            androidx.compose.runtime.SideEffect {
                // The notes the learner plays (the other hand's, dimmed, are not read).
                cheer.place = { c -> com.bigpapituna.sharpears.engine.lessons.CheerPlace.laneSides(song.notes.filter { transport.required(it) }, range.first, range.last,
                    transport.positionMs, transport.speed, c.holdMs, CheerFeed.POP_WIDTH_DP / laneDp, sides, preferStart = c.other).firstOrNull() }
            }
            com.bigpapituna.sharpears.ui.pup.CheerPop(cheers.cue, Modifier.align(Alignment.TopStart).fillMaxWidth()
                .height(com.bigpapituna.sharpears.engine.lessons.CheerPlace.laneHeightDp(maxHeight.value).dp), maxPupDp = 48f)
        }
        if (showVideo) TutorialPip(tutorial!!, moving, speed, micOn && !mic.headphonesOn(), { transport.positionMs },
            onClose = { videoOn = false; Log.d(TAG, "video closed") }, maxHeight = maxHeight - 20.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        if (embedded == null) recap?.let { r ->
            // The end of a run is a moment of its own: the whole screen, the pup, the numbers counting up, the XP.
            Dialog(onDismissRequest = { recap = null; onRecapClosed() },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                Box(Modifier.fillMaxSize()) {
                    RunRecap(r, speed, sampler.takeIf { prefs.sound }, onAgain = { voice.stop(); recap = null; seek(0.0); play() }, onLoop = ::loopBar,
                        onClose = { recap = null; onRecapClosed() },
                        onReplay = { recap = null; replay = lastRun; Log.d(TAG, "hear my run: ${lastRun?.notes?.size} notes") },
                        songTitle = meta?.title ?: song.title, run = lastRun,
                        // "One more": the same song again, with a peek at what it will drop. Read after the reward above.
                        nextDrop = remember(r) { if (r.reward != null) world?.previewSong() else null },
                        say = { line -> voice.speak(line, interrupt = true) })
                    // A kid's "Send to someone" asks the parent gate: drawn in this window, over the recap (QA 2.8.3: the
                    // activity's own gate opened under the dialog and nothing seemed to happen). A gate still asked when the
                    // recap goes is left with it, so it never turns up on the player afterwards.
                    val gate = com.bigpapituna.sharpears.ui.family.LocalParentGate.current
                    com.bigpapituna.sharpears.ui.family.ParentGateHost(gate)
                    DisposableEffect(gate) { onDispose { if (gate.pending != null) gate.cancel() } }
                }
            }
        }
        if (pickingDuet) Dialog(onDismissRequest = { pickingDuet = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            SharpEarsTheme(dark = androidx.compose.foundation.isSystemInDarkTheme()) {
                DialogBarsFollowTheme()
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // One readable column, centred on a wide screen.
                    Box(contentAlignment = Alignment.TopCenter) {
                        com.bigpapituna.sharpears.ui.play.DuetPicker(onStart = { d ->
                            pickingDuet = false; duet = d
                            practice = Practice.BOTH; transport.practice = Practice.BOTH; version++
                            Log.d(TAG, "duet: ${d.leftId} left, ${d.rightId} right on ${song.id}")
                        }, onCancel = { pickingDuet = false }, modifier = Modifier.widthIn(max = 640.dp))
                    }
                }
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
                modifier = Modifier.fillMaxSize(),
                pulsing = remember(expected) { expected.associate { it.midi to handColor(it.hand) } },
                // The key the song waits for shows its suggested finger.
                labels = if (labels && prefs.fingerNumbers) keyLabels + expected.mapNotNull { n -> fingers[n]?.let { n.midi to "$it" } } else keyLabels,
                sheen = !playing,
                keySize = keySize,
                wrong = wrongs,
            )
            com.bigpapituna.sharpears.ui.motion.GhostHand(ghost, { position }, range.first, range.last, Modifier.matchParentSize(),
                visible = labels && prefs.ghostHand && prefs.version.intValue >= 0 && hintStep < HintStep.POINT)
            // Hints while stuck: the waiting keys glow, then the painted hand points at one (right hand first).
            if (hintStep >= HintStep.GLOW && expected.isNotEmpty())
                KeyGlow(remember(expected) { expected.associate { it.midi to handColor(it.hand) } }, range.first, range.last, Modifier.matchParentSize())
            if (hintStep >= HintStep.POINT) {
                // An any-of group points at its demonstration key, not the lowest of the range.
                val hint = remember(expected) { transport.hintKeys.toSet() }
                val pool = expected.filter { it.midi in hint }
                val n = pool.filter { it.hand == Hand.RIGHT }.minByOrNull { it.midi } ?: pool.minByOrNull { it.midi }
                // The hand presses with the note's own finger (the number on the falling note), not always the index.
                if (n != null) PointingHand(n.midi, n.hand == Hand.RIGHT, range.first, range.last, Modifier.matchParentSize(), finger = fingers[n] ?: 2)
            }
            octaveShown?.let { OctaveArrow(it.expected, it.playedOctaveOffset, range.first, range.last, Modifier.matchParentSize()) }
        }
    }

    // Debug: once the frozen screen settles, log each visible note's rectangle in window pixels for adb checks. Only with
    // --ez dump: keyed on the position, it made the whole player recompose (and relaunch this effect) on every frame.
    if (debugDump) LaunchedEffect(laneBox, position, playing) {
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
                    kid -> ""                                      // no numbers for kids
                    else -> transport.score?.let { "Streak ${it.streakNow}" } ?: ""
                })
            Lane(Modifier.fillMaxWidth().weight(1f))
            Keys(Modifier.fillMaxWidth().weight(keySize.heightShare))
            if (pedalGrade != null) PedalStrip(pedalGrade, position, song.durationMs, pedalDown, Modifier.fillMaxWidth().height(28.dp))
        }
        return
    }

    // A new two-hand song: suggest hands apart first, then together. Library songs only, never lessons or progressions.
    var handsTip by remember(song) { mutableStateOf(handsTipOf(meta, song, prefs, show = embedded == null && !kid)) }
    LaunchedEffect(handsTip) { handsTip?.let { Log.d(TAG, "hands tip ${song.id}: $it") } }
    fun pickHands(p: Practice?) {
        val stage = handsTip ?: return
        if (duet != null) return                       // a duet plays both hands, one person each: no hands advice
        if (p != null) { practice = p; transport.practice = p; version++ }
        meta?.let { prefs.dismissHandsTip(it.id, stage) }
        Log.d(TAG, "hands tip $stage -> ${p ?: "dismissed"}")
        handsTip = null
    }
    @Composable
    fun HandsTipRow() {
        val stage = handsTip ?: return
        if (duet != null) return                       // a duet plays both hands, one person each: no hands advice
        // Already playing the hand it suggests (picked on the bar): "Start with the right hand alone?" over R was noise.
        if (stage == "right" && practice == Practice.RIGHT || stage == "left" && practice == Practice.LEFT) return
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
            // Kids' big keys take more of the stage; grown-ups' as before (a smaller share on a tall screen).
            Keys(Modifier.fillMaxWidth().weight(if (kid) keySize.heightShare else if (windowHeightDp >= 500.dp) 0.3f else 0.45f))
        }
    }
    }
    }
}

/**
 * Everything [PlayerScreen] remembers for as long as it is on screen (and not per song): the transport's view of the run,
 * the hints, the toggles, the recap, the flag and stuck-moment bookkeeping. One object made once, read and written inside
 * `with(v)` in the composable exactly as the locals it replaces were, so the screen behaves as before.
 *
 * Why (2.4): with sixty-odd remembered locals the composable's dex method held about 280 registers, and D8 (AGP 9.4.1)
 * once allocated a null check to the wrong one (engine/brief/INTEGRATION.md, "PlayerScreen and D8"). Its closures now
 * capture this holder instead of the locals one by one.
 */
private class PlayerVars(start: PlayerStart, autoPercent0: Int, context: android.content.Context) {
    var position by mutableDoubleStateOf(0.0)
    var playing by mutableStateOf(false)
    var speed by mutableDoubleStateOf(start.speed ?: 1.0)
    var mode by mutableStateOf(start.mode)
    // A duet plays both hands, one person each; the hand chooser is locked meanwhile.
    var duet by mutableStateOf(start.duet)
    var pickingDuet by mutableStateOf(start.pickDuet && start.duet == null)
    var practice by mutableStateOf(if (start.duet != null) Practice.BOTH else start.practice)
    var micOn by mutableStateOf(start.mic || start.inject != null)
    var loopStart by mutableStateOf<Double?>(null)
    var loopEnd by mutableStateOf<Double?>(null)
    var version by mutableIntStateOf(0)              // bumps when sounding/expected/waiting change
    val touched = mutableStateMapOf<Int, Color>()
    val held = mutableStateMapOf<Int, Color>()      // keys held on the MIDI keyboard
    val flashes = mutableStateMapOf<Int, Color>()    // short green flash on a correct key
    val lights = HitLights(slow = start.lightSlow)    // the hit-line light (on by default)
    val struggle = com.bigpapituna.sharpears.engine.Struggle()
    var hintStep by mutableStateOf(HintStep.NONE)
    var hintLine by mutableStateOf<String?>(null)     // the pup's hint line over the lane
    var octaveShown by mutableStateOf<OctaveHint.Hint?>(null)
    // Wrong keys shown so far (ids for onWrongKey); the room-noise watchdog's offer.
    var wrongKeys by mutableIntStateOf(0)
    var noiseOffer by mutableStateOf<NoiseWatch.Suggestion?>(null)
    var highFive by mutableStateOf(0)                 // bumps when the player pushes through a hard passage
    var showHighFive by mutableStateOf(false)
    // The notes the player plays in the current run (touch, MIDI or mic), kept for "Hear my run".
    val recorder = RunRecorder()
    val runs = RunStore(context)
    var lastRun by mutableStateOf<RunRecording?>(null)
    var replay by mutableStateOf<RunRecording?>(null)
    var pedalDown by mutableStateOf(false)
    var lastVelocity by mutableIntStateOf(-1)
    var scriptStarted by mutableStateOf(false)
    var recap by mutableStateOf<RunEnd?>(null)      // the end of a run: Perform recap and/or hardest bars
    var touchRun by mutableStateOf(false)             // this run was played on the touch keys
    var midiRun by mutableStateOf(false)              // 2.14: a MIDI keyboard played in this run (the timing line may name the offset)
    var tickNanos = 0L                                // 2.14: the frame time the transport was last moved to (a key between frames)
    // The tutorial video opens only when asked for (Video button), as a floating window over the notes.
    var videoOn by mutableStateOf(false)
    val clicks = lazy { com.bigpapituna.sharpears.audio.Clicks(context) }
    var metronomeOn by mutableStateOf(false)
    var chordsOn by mutableStateOf(false)
    var ramp by mutableStateOf<com.bigpapituna.sharpears.engine.TempoRamp?>(null)
    // Auto speed: the tempo managed last time (library songs), nudged up on good days.
    var autoOn by mutableStateOf(false)
    var autoPercent by mutableIntStateOf(autoPercent0)
    var rampPercent by mutableIntStateOf(0)
    var backingOn by mutableStateOf(false)
    /** The pop band's player, made at the first beat it plays (ui/chords/BandRider). */
    var rider: com.bigpapituna.sharpears.ui.chords.BandRider? = null
    val practiceMs = doubleArrayOf(0.0)
    // Stuck moments: wait mode on the same notes for 5 s or more with the mic on is kept (clip and report) for review.
    val stallWatch = com.bigpapituna.sharpears.engine.StallWatch()
    val tailLines = ArrayDeque<String>()
    val answerKey = com.bigpapituna.sharpears.engine.AnswerKey()
    val answerLines = mutableListOf<String>()
    val answerState = longArrayOf(0, 0, 0)        // clip mark, last mismatch time, song position then
    var flagging by mutableStateOf(false)
    var flagMark by mutableStateOf(0L)
    var flagAt by mutableStateOf(0.0)
    var flagContext by mutableStateOf("")      // what was going on when the flag was tapped
    val stallMark = longArrayOf(0)
    var autoStarted by mutableStateOf(false)
    var wasPlayingBeforeScrub by mutableStateOf(false)
    var laneBox by mutableStateOf<Rect?>(null)
    /** 2.13: what the player tells the cheer pops (ui/CheerFeed.kt). */
    val cheer = CheerFeed()
    var rootTopPx by mutableFloatStateOf(0f)
}

/**
 * How a run ended: the Perform [recap] (null after a wait-mode run), a new best, the hardest bars, how long the run
 * took in real time ([timeMs]) and the XP it earned with the level bar (library songs only), shown by [RunRecap].
 */
data class RunEnd(val recap: Recap?, val newBest: Boolean, val spots: List<TroubleSpot>, val wrongKeys: Int = 0, val canReplay: Boolean = false,
                  val timeMs: Long = 0, val xpGained: Int? = null, val xpBar: XpBar? = null,
                  /** The sticker (and the medal) this run dropped in the pup's world; null outside library songs. */
                  val reward: com.bigpapituna.sharpears.engine.world.Reward? = null,
                  /** A duet's line naming both players ("Mia and Theo, two stars together"). */
                  val duetLine: String? = null,
                  /** 2.14: a Perform run's bars and notes against the beat, for the recap's timing panel (engine/PerformTiming.kt). */
                  val timing: com.bigpapituna.sharpears.engine.TimingReport? = null,
                  /** The song names black keys as flats (the timing panel's notes). */
                  val flats: Boolean = false)

/**
 * 2.14: [e] with its Perform run's timing (null and anything not Perform pass through): every graded note in its bar,
 * in real ms at the run's [speed]; [midi] when the run came from a MIDI keyboard (the line may point at the offset).
 */
private fun RunEnd.timed(transport: Transport, speed: Double, midi: Boolean): RunEnd {
    val score = transport.score ?: return this
    if (recap == null || transport.mode != Mode.PERFORM) return this
    val song = transport.song
    val t = com.bigpapituna.sharpears.engine.PerformTiming.of(score.timings(), com.bigpapituna.sharpears.engine.barMs(song),
        song.timeSignature.first, speed, midi)
    Log.d(TAG, "timing ${song.id}: ${t.line} bias=${t.biasMs} bars=" + t.bars.joinToString(" ") { "${it.bar}:${it.verdict?.name?.first() ?: '-'}" })
    t.bars.forEach { b -> b.notes.forEach { n -> Log.d(TAG, "timing note bar ${n.bar} beat ${"%.2f".format(java.util.Locale.ROOT, n.beat + 1)} ${n.keys.joinToString("+") { noteName(it) }} ${com.bigpapituna.sharpears.engine.PerformTiming.offsetWords(n.offsetMs)}") } }
    return copy(timing = t, flats = song.preferFlats)
}

/**
 * A pop song's run to its end (songs by chords, 2.6; [runId] "pop-<id>", PopPlay.runIdOf). It has no library entry, so no
 * trouble bars, auto speed, sticker or quest, but a run the player played is kept as a library song's is: the parent
 * brief's SongRun (its stars from the Perform accuracy, or the wrong keys in Wait), a best score from the top, the
 * progress XP with its first completion, and the recap, Wait as well as Perform. Listen, or nothing played: no recap.
 */
private fun popRunEnd(runId: String, transport: Transport, run: Recap?, prefs: Prefs, progress: ProgressStore?): RunEnd? {
    val report = transport.report()
    if (transport.mode == Mode.LISTEN || report.empty) {
        Log.d(TAG, "pop: $runId ${if (transport.mode == Mode.LISTEN) "listened through" else "nothing played"}")
        return null
    }
    val fromTop = transport.scoreFromMs <= 0.0
    Briefs.logAll(RunEvents.song(Briefs.now(), runId, transport.mode, transport.practice, report, transport.tally, transport.runRealMs.toLong(),
        fromTop = fromTop, duet = false))
    val first = !prefs.progress(runId).completed
    val best = run != null && fromTop && run.accuracy > 0 && prefs.saveScore(runId, run.accuracy)
    prefs.markCompleted(runId)
    val xpBefore = progress?.xp
    val earned = progress?.record(ProgressEvent.SongRun(runId, run?.accuracy, newBest = best, firstCompletion = first))
    val bar = progress?.let { p -> xpBefore?.let { com.bigpapituna.sharpears.ui.profile.xpBarFor(it, p.xp, p.level) } }
    Log.d(TAG, "pop: run $runId mode=${transport.mode} accuracy=${run?.accuracy} wrong=${report.wrongKeys} stars=" +
        Stars.of(run, report.wrongKeys) + " newBest=$best first=$first xp=${earned?.xpGained}")
    return RunEnd(run, best, emptyList(), wrongKeys = report.wrongKeys, timeMs = transport.runRealMs.toLong(),
        xpGained = earned?.xpGained?.takeIf { it > 0 }, xpBar = bar)
}

/** Key labels from the setting: every C with its octave, or every white key's letter as well. */
private fun keyLabels(setting: KeyLabels, range: IntRange): Map<Int, String> = when (setting) {
    KeyLabels.OFF -> emptyMap()
    KeyLabels.C_ONLY -> range.filter { it % 12 == 0 }.associateWith { noteName(it) }
    KeyLabels.ALL -> range.filter { !isBlack(it) }.associateWith { if (it % 12 == 0) noteName(it) else noteName(it).filter(Char::isLetter) }
}

private fun handColor(h: Hand) = if (h == Hand.RIGHT) RightHandColor else LeftHandColor

/*
 * `meta` read outside the composable. PlayerScreen is one very large composable (about 280 registers once dexed), and
 * there D8 tested another register for `meta`'s null check (`meta?.let`, then `if (meta != null)`): every lesson's player
 * (meta null) crashed with a NullPointerException as it opened (found on the Fold emulator, 2026-10-21, after unrelated
 * edits moved the method's register allocation). In these small functions the null checks are plain.
 */
private fun savedTranspose(meta: SongMeta?, prefs: Prefs): Int = if (meta != null) prefs.transpose(meta.id) else 0

private fun savedAutoPercent(meta: SongMeta?, prefs: Prefs): Int = meta?.let { prefs.autoSpeed(it.id)?.percent } ?: AutoSpeed.FIRST

private fun tutorialOf(meta: SongMeta?, song: Song, prefs: Prefs): TutorialState? =
    meta?.videos?.takeIf { it.isNotEmpty() }?.let { TutorialState(song.id, it, prefs, song.notes.first().startMs, song.groups.last().startMs) }

/** The hands-apart tip for a two-hand library song, when [show] (the full player, a grown-up). */
private fun handsTipOf(meta: SongMeta?, song: Song, prefs: Prefs, show: Boolean): String? {
    if (meta == null || !show) return null
    val twoHands = song.notes.any { it.hand == Hand.LEFT } && song.notes.any { it.hand == Hand.RIGHT }
    return com.bigpapituna.sharpears.engine.handsTip(twoHands, prefs.handsDone(meta.id), prefs.progress(meta.id).completed)
        ?.takeIf { it != prefs.handsTipDismissed(meta.id) }
}


/** The lesson player's one row: play/pause, what the step asks (fixed by the step), live status, and the mic. */
/**
 * Runs [content] in a recompose scope of its own. A local composable function (PlayerScreen's Bar) has none, so its
 * state reads recomposed the whole player: the playhead, read by the bar, did that on every frame.
 */
@Composable
private fun OwnScope(content: @Composable () -> Unit) = content()

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
