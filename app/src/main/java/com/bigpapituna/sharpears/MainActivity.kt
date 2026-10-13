package com.bigpapituna.sharpears

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.ui.LearnScreen
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.LessonScreen
import com.bigpapituna.sharpears.ui.nextLesson
import com.bigpapituna.sharpears.ui.HomeShell
import com.bigpapituna.sharpears.ui.LicencesScreen
import com.bigpapituna.sharpears.ui.PrivacyScreen
import com.bigpapituna.sharpears.ui.HomeTab
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.ImportException
import com.bigpapituna.sharpears.data.LessonStore
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.MIC_CHECK_ID
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.PROGRESSIONS
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.micCheckExercise
import com.bigpapituna.sharpears.engine.progressionSong
import com.bigpapituna.sharpears.engine.songPack
import com.bigpapituna.sharpears.engine.ChordTrack
import com.bigpapituna.sharpears.engine.PracticeStats
import com.bigpapituna.sharpears.engine.LessonItem
import com.bigpapituna.sharpears.engine.SessionPart
import com.bigpapituna.sharpears.engine.SessionPlan
import com.bigpapituna.sharpears.engine.SessionSong
import com.bigpapituna.sharpears.engine.planSession
import com.bigpapituna.sharpears.engine.sessionSummary
import com.bigpapituna.sharpears.ui.DailyChords
import com.bigpapituna.sharpears.ui.Interstitial
import com.bigpapituna.sharpears.ui.TodayPanel
import com.bigpapituna.sharpears.engine.barMs
import com.bigpapituna.sharpears.engine.labelBarsAligned
import com.bigpapituna.sharpears.ui.ChordsScreen
import com.bigpapituna.sharpears.ui.ChordsTab
import com.bigpapituna.sharpears.ui.GameStart
import com.bigpapituna.sharpears.engine.GameKind
import com.bigpapituna.sharpears.ui.LibraryActions
import com.bigpapituna.sharpears.ui.LibraryEntry
import com.bigpapituna.sharpears.ui.LibraryScreen
import com.bigpapituna.sharpears.ui.MicSetupScreen
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.SettingsScreen
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.paper
import androidx.compose.animation.AnimatedContent
import com.bigpapituna.sharpears.data.PracticeStore
import com.bigpapituna.sharpears.data.ProgressStore
import com.bigpapituna.sharpears.engine.ear.EarKind
import com.bigpapituna.sharpears.ui.ear.EarStart
import com.bigpapituna.sharpears.ui.ear.EarTrainingScreen
import com.bigpapituna.sharpears.ui.motion.screenTransitions
import com.bigpapituna.sharpears.ui.onboarding.OnboardingScreen
import com.bigpapituna.sharpears.ui.practice.PracticeScreen
import com.bigpapituna.sharpears.ui.practice.PracticeTab
import com.bigpapituna.sharpears.ui.profile.ProfileScreen
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.engine.ear.EarEvent
import com.bigpapituna.sharpears.engine.progress.ProgressEvent
import com.bigpapituna.sharpears.ui.practice.PracticeKind
import com.bigpapituna.sharpears.ui.profile.LocalProgress
import com.bigpapituna.sharpears.ui.profile.ProgressOverlays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAG = "SharpEars"

private sealed interface Screen {
    /** The five tabs. [chordsTab], [inject] and [game] only matter for Chords, [practiceTab] for Practice. */
    data class Home(val tab: HomeTab = HomeTab.SONGS, val chordsTab: ChordsTab = ChordsTab.PLAY, val inject: String? = null,
                    val game: GameStart = GameStart(), val practiceTab: PracticeTab = PracticeTab.SCALES) : Screen
    /** [meta] is null for generated songs (mic check, progressions). [back] is where Back goes. */
    data class Player(val song: Song, val meta: SongMeta?, val start: PlayerStart, val startMs: Double, val back: Screen) : Screen
    /** A course lesson at step [step]. [inject] (debug) feeds the mic for that first step. */
    data class Lesson(val lesson: CourseLesson, val back: Screen, val step: Int = 0, val inject: String? = null, val midiScript: String? = null) : Screen
    data class MicSetup(val back: Screen) : Screen
    /** The sight-reading drill. [inject] (debug) feeds the mic. */
    data class SightRead(val back: Screen, val level: Int = 1, val inject: String? = null) : Screen
    /** "Practice today": part [index] of [plan]; [between] shows the one-line interstitial before it. */
    data class Daily(val plan: SessionPlan, val index: Int, val between: Boolean, val startedAt: Long, val done: List<SessionPart>) : Screen
    data class Privacy(val back: Screen) : Screen
    data class Licences(val back: Screen) : Screen
    /** Settings, pushed from the gear on You (it was a tab before 2.0). */
    data class Settings(val back: Screen) : Screen
    /** The ear training hub (Learn), or straight into a game or the workout with [start]. */
    data class EarTraining(val back: Screen, val start: EarStart? = null) : Screen
    /** First run: four questions, then the suggested lesson. */
    data object Onboarding : Screen
}

/** Order of screens for the transitions: deeper goes forward, shallower goes back, the player gets the curtain. */
private fun depthOf(s: Screen) = when (s) {
    is Screen.Home -> 0
    is Screen.Settings, is Screen.EarTraining, Screen.Onboarding -> 1
    is Screen.Player, is Screen.Lesson, is Screen.SightRead, is Screen.Daily, is Screen.Privacy, is Screen.Licences, is Screen.MicSetup -> 2
}

/** The player is always dark, like a video; everything else follows the system. */
private fun playerShown(s: Screen) = s is Screen.Player ||
    (s is Screen.Daily && !s.between && s.plan.parts[s.index].let { it is SessionPart.Loop || it is SessionPart.Song })

/** The stage: the player, or a Practice today part that runs one. */
private fun isStage(s: Screen) = s is Screen.Player || (s is Screen.Daily && !s.between)

class MainActivity : ComponentActivity() {
    private lateinit var sampler: Sampler
    private lateinit var midi: MidiDevices
    private lateinit var mic: MicInput
    private lateinit var voice: Voice
    private val samplerReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sampler = Sampler(this) { samplerReady.value = true }
        midi = MidiDevices(this).also { it.start() }
        mic = MicInput(this)
        val repo = SongRepository(this)
        val prefs = Prefs(this).also { it.logRestored() }
        // Review pipeline (private build): crashes are kept, a weekly summary is written, and whatever waits goes out.
        if (mic.keepRecordings) {
            mic.reporter.catchCrashes(packageManager.getPackageInfo(packageName, 0).versionName ?: "?")
            mic.reporter.weeklySummary(prefs.practice())
            mic.reporter.flush()
        }
        com.bigpapituna.sharpears.reminder.Reminder.schedule(this, prefs)        // alarms do not survive an update or restart
        voice = Voice(this, prefs, mic)                // the speech engine itself starts on the first line
        val lessons = LessonStore(this)
        val progress = ProgressStore(this, prefs, songIds = { repo.all().map { it.id } })
        progress.catchUp()                             // once ever: XP for practice, lessons and songs from before 2.0, quietly
        val practiceStore = PracticeStore(this)
        val micCheck = micCheckExercise()

        // Debug builds accept extras to open a screen directly:
        //   --es song <id>|mic-check [--el seek <ms>] [--ez dump true] [--ez tabletop true]
        //   [--es mode wait|perform] [--es hands left|right] [--ez mic true] [--es inject <wav path>|synth:76,75,60+64+67@600]
        //   --es song prog:<index>:<key 0-11>   (a chord progression, e.g. prog:0:7 = I-V-vi-IV in G)
        //   --es screen chords|settings|learn|profile [--es tab play|daily|dictionary|practice|progressions] [--es inject ...]
        //   --es screen practice [--es tab scales|arpeggios|warmups|metronome|tuner]
        //   --es screen ear [--es game rhythm|melody|pitch|degree|prog --ei level 0-2 | --ez workout true]
        //   --es screen onboarding   (the first-run flow, whatever was answered before)
        //   --es screen chords --es game build|hear|mm [--ei level 0-2] [--es inject ...]   (a chord game, seeded)
        //   --es lesson <course lesson id> [--ei step <0-based step>] [--es inject ...]
        //   --es say "<text>"   (speaks the line 1.5 s after launch, through the mic rule)
        //   --ef lightSlow <n>   (stretches the hit-line lights n times, to catch them in a screenshot)
        //   --ez daily true   (starts "Practice today" straight away)
        //   --es screen sight [--ei level 1|2] [--es inject ...]   (sight-reading; seeded when injecting)
        //   --es lesson <id> --es midi "800:p1 4790:p0 1000:60/40"   (a pretend MIDI keyboard: pedal and key/velocity at ms after Play)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val openSong = intent.getStringExtra("song")?.takeIf { debuggable }
        val debugStart = PlayerStart(
            mode = when (intent.getStringExtra("mode")) { "wait" -> Mode.WAIT; "perform" -> Mode.PERFORM; else -> Mode.LISTEN },
            practice = when (intent.getStringExtra("hands")) { "left" -> Practice.LEFT; "right" -> Practice.RIGHT; else -> Practice.BOTH },
            mic = intent.getBooleanExtra("mic", false),
            inject = intent.getStringExtra("inject")?.takeIf { debuggable },
            tabletop = debuggable && intent.getBooleanExtra("tabletop", false),
            lightSlow = if (debuggable) intent.getFloatExtra("lightSlow", 1f) else 1f,
        )
        val seekMs = intent.getLongExtra("seek", 0L).toDouble()
        val dump = debuggable && intent.getBooleanExtra("dump", false)
        val first: Screen = when {
            debuggable && intent.getStringExtra("screen") == "chords" -> Screen.Home(HomeTab.CHORDS,
                when (intent.getStringExtra("tab")) {
                    "dictionary" -> ChordsTab.DICTIONARY; "practice" -> ChordsTab.PRACTICE; "progressions" -> ChordsTab.PROGRESSIONS
                    "daily", "lessons" -> ChordsTab.LESSONS
                    else -> ChordsTab.PLAY
                },
                debugStart.inject,
                GameStart(GameKind.entries.firstOrNull { it.id == intent.getStringExtra("game") }, intent.getIntExtra("level", 0)),
            )
            debuggable && intent.getStringExtra("screen") == "settings" -> Screen.Settings(Screen.Home(HomeTab.YOU))
            debuggable && intent.getStringExtra("screen") == "learn" -> Screen.Home(HomeTab.LEARN)
            debuggable && intent.getStringExtra("screen") == "profile" -> Screen.Home(HomeTab.YOU)
            debuggable && intent.getStringExtra("screen") == "onboarding" -> Screen.Onboarding
            debuggable && intent.getStringExtra("screen") == "practice" -> Screen.Home(HomeTab.PRACTICE,
                practiceTab = PracticeTab.entries.firstOrNull { it.name.equals(intent.getStringExtra("tab"), ignoreCase = true) } ?: PracticeTab.SCALES)
            debuggable && intent.getStringExtra("screen") == "ear" -> Screen.EarTraining(Screen.Home(HomeTab.LEARN), when {
                intent.getBooleanExtra("workout", false) -> EarStart(workout = true)
                else -> intent.getStringExtra("game")?.let(EarKind::byId)?.let { EarStart(it, intent.getIntExtra("level", 0)) }
            })
            debuggable && intent.getStringExtra("screen") == "sight" -> Screen.SightRead(Screen.Home(HomeTab.LEARN), intent.getIntExtra("level", 1), debugStart.inject)
            debuggable && intent.getStringExtra("lesson") != null ->
                COURSE.flatMap { it.lessons }.first { it.id == intent.getStringExtra("lesson") }.let {
                    Screen.Lesson(it, Screen.Home(HomeTab.LEARN), intent.getIntExtra("step", 0), debugStart.inject, intent.getStringExtra("midi"))
                }
            openSong == null -> Screen.Home()
            openSong == MIC_CHECK_ID -> Screen.Player(micCheck, null, debugStart, seekMs, Screen.Home())
            openSong.startsWith("prog:") -> openSong.split(":").let { (_, i, k) ->
                Screen.Player(progressionSong(PROGRESSIONS[i.toInt()], k.toInt()), null, debugStart, seekMs, Screen.Home(HomeTab.CHORDS, ChordsTab.PROGRESSIONS))
            }
            else -> repo.all().first { it.id == openSong }.let { m -> Screen.Player(repo.load(m), m, debugStart, seekMs, Screen.Home()) }
        }

        val startDaily = debuggable && intent.getBooleanExtra("daily", false)
        // --es midi "<script>": a pretend keyboard plays into the first lesson step (see MidiDevices.debugPlay).
        if (debuggable && intent.getStringExtra("midi") != null) midi.debugConnect()
        intent.getStringExtra("say")?.takeIf { debuggable }?.let { line ->
            android.os.Handler(mainLooper).postDelayed({ voice.speak(line) }, 1500)
        }

        setContent {
            var screen by remember { mutableStateOf(first) }
            // Every screen can report XP events through LocalProgress without a new parameter.
            androidx.compose.runtime.CompositionLocalProvider(LocalProgress provides progress) {
            SharpEarsTheme(dark = playerShown(screen) || isSystemInDarkTheme()) {
                Surface(Modifier.fillMaxSize().paper(), color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground) {   // textured page (ui/Backdrop.kt)
                    var libraryVersion by remember { mutableIntStateOf(0) }       // bumps after an import
                    var importError by remember { mutableStateOf<String?>(null) }
                    var importInto by remember { mutableStateOf<SongMeta?>(null) }
                    // "Practice today" just finished: parts done, minutes, days in a row, for the celebration.
                    var dailyDone by remember { mutableStateOf<Triple<Int, Long, Int>?>(null) }
                    val scope = rememberCoroutineScope()
                    val songs = remember(libraryVersion) { repo.all() }

                    // Each library song yields a "Chords of <song>" lesson pack (DESIGN 4.3), labeled off the main thread.
                    // The same bar labels drive the chord names on the falling notes (ChordTrack per song id).
                    LaunchedEffect(libraryVersion) {
                        val labeled = withContext(Dispatchers.Default) {
                            songs.filter(repo::available).mapNotNull { m ->
                                runCatching {
                                    val song = repo.load(m)
                                    val bars = labelBarsAligned(song)
                                    Triple(m.id, songPack(song, bars = bars), ChordTrack(bars, barMs(song)))
                                }.onFailure { Log.d(TAG, "song pack ${m.id}: $it") }.getOrNull()
                            }
                        }
                        lessons.songPacks.value = labeled.map { it.second }.filter { it.items.isNotEmpty() }
                        lessons.songChords.value = labeled.filter { !it.third.isEmpty }.associate { it.first to it.third }
                        Log.d(TAG, "lessons: ${lessons.songPacks.value.size} song packs " + lessons.songPacks.value.joinToString { "${it.id}=${it.items.size}" })
                    }

                    fun openSong(m: SongMeta, back: Screen = Screen.Home()) {
                        val song = try { repo.load(m) } catch (e: Exception) { importError = "Could not open ${m.title}: ${e.message}"; return }
                        val p = prefs.progress(m.id)
                        prefs.lastSongId = m.id
                        Log.d(TAG, "song ${song.id}: ${song.notes.size} notes, ${song.groups.size} groups, ${song.durationMs} ms, " +
                            "right=${song.notes.count { it.hand == Hand.RIGHT }} left=${song.notes.count { it.hand == Hand.LEFT }} resume=${p.lastPositionMs}")
                        screen = Screen.Player(song, m, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission()),
                            if (p.completed) 0.0 else p.lastPositionMs.toDouble(), back)
                    }
                    // "Practice today": due chords, the next lesson, then the Continue song's worst bar (or the song).
                    fun continueMeta() = songs.firstOrNull { it.id == prefs.lastSongId && repo.available(it) }
                        ?: songs.firstOrNull { it.difficulty == "easy" && repo.available(it) }
                    fun todayPlan(): SessionPlan {
                        val queue = lessons.curriculum().queue(lessons.mastery(), System.currentTimeMillis())
                        val next = com.bigpapituna.sharpears.ui.ALL.firstOrNull { !prefs.lessonDone(it.second.id) }?.second
                        val song = continueMeta()?.let { m ->
                            val minutes = runCatching { repo.load(m).durationMs / 60_000.0 }.getOrDefault(3.0)
                            SessionSong(m.id, m.title, minutes, prefs.troubleBars(m.id).firstOrNull())
                        }
                        return planSession(queue.filterIsInstance<LessonItem.ChordItem>().map { it.id }, next?.let { it.id to it.title }, song)
                    }
                    fun startDaily() {
                        val plan = todayPlan()
                        Log.d(TAG, "daily: plan ${plan.aboutLabel}: " + plan.parts.joinToString { it.done })
                        if (!plan.isEmpty) screen = Screen.Daily(plan, 0, between = true, startedAt = System.currentTimeMillis(), done = emptyList())
                    }
                    LaunchedEffect(Unit) { if (startDaily) startDaily() }
                    fun finishDaily(s: Screen.Daily, done: List<SessionPart>) {
                        val secs = (System.currentTimeMillis() - s.startedAt) / 1000
                        // Nothing done (every part skipped or left at once): no summary, the panel stays as it was.
                        if (done.isEmpty()) { Log.d(TAG, "daily: ended with nothing done"); screen = Screen.Home(); return }
                        val summary = sessionSummary(done, secs)
                        prefs.saveDailySummary(java.time.LocalDate.now(), summary)
                        progress.record(ProgressEvent.DailySession(done.size))
                        Log.d(TAG, "daily: $summary")
                        voice.speak("Done for today: " + summary.removePrefix("Done today: ").replace(" min.", " minutes."))
                        dailyDone = Triple(done.size, maxOf(1L, (secs + 30) / 60), PracticeStats.streak(prefs.practice(), java.time.LocalDate.now()))
                        screen = Screen.Home()
                    }
                    fun advanceDaily(s: Screen.Daily, completed: Boolean, as_: SessionPart? = null) {
                        val part = as_ ?: s.plan.parts[s.index]
                        val done = if (completed) s.done + part else s.done
                        Log.d(TAG, "daily: part ${s.index + 1} ${if (completed) "done" else "left"}: ${part.done}")
                        if (s.index + 1 >= s.plan.parts.size) finishDaily(s, done)
                        else screen = s.copy(index = s.index + 1, between = true, done = done)
                    }

                    // Ear training: a tick per round, a game's credit at its end, the workout like a game plus its practice time.
                    fun earEvent(e: EarEvent) {
                        fun practised(secs: Int) {
                            if (secs <= 0) return
                            prefs.addPractice(java.time.LocalDate.now(), secs)
                            progress.record(ProgressEvent.Practice(java.time.LocalDateTime.now(), secs))
                        }
                        when (e) {
                            is EarEvent.Round -> progress.record(ProgressEvent.EarRound(e.clean))
                            is EarEvent.GameComplete -> { practised(e.seconds); progress.record(ProgressEvent.GameComplete("ear:" + e.kind.id, e.level, e.score, e.rounds)) }
                            is EarEvent.Workout -> { practised(e.seconds); progress.record(ProgressEvent.GameComplete("ear:workout", 0, e.score, e.rounds)) }
                        }
                        Log.d(TAG, "ear: $e -> xp ${progress.xp}")
                    }

                    val pickMidi = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        val into = importInto
                        if (uri == null) return@rememberLauncherForActivityResult
                        scope.launch {
                            try {
                                val m = withContext(Dispatchers.IO) { repo.import(uri, into) }
                                Log.d(TAG, "import ok ${m.id} -> ${m.file}")
                                progress.record(ProgressEvent.FeatureUsed("import"))
                                libraryVersion++
                            } catch (e: ImportException) {
                                Log.d(TAG, "import failed: ${e.message}"); importError = e.message
                            } catch (e: Exception) {
                                Log.d(TAG, "import failed: $e"); importError = "Import failed: ${e.message}"
                            }
                        }
                    }

                    // One transition for the whole stack (ui/motion/Transitions.kt). The key is the screen's class only, so
                    // changes inside a screen (a tab, Daily's index, a lesson step) do not count as a new screen; HomeShell
                    // cross-fades its own tabs. Inside, always use `s`, never the outer `screen`.
                    val still = reducedMotion()
                    val systemDark = isSystemInDarkTheme()
                    AnimatedContent(screen, Modifier.fillMaxSize(), label = "screen", contentKey = { it::class },
                        transitionSpec = screenTransitions(still, isStage = ::isStage, depth = ::depthOf)) { s ->
                    // Each screen keeps its own theme while it animates out (the home does not turn dark under the curtain).
                    SharpEarsTheme(dark = playerShown(s) || systemDark) {
                    when (s) {
                        is Screen.Player -> PlayerScreen(s.song, s.meta, sampler, midi, mic, voice, prefs, samplerReady.value,
                            s.startMs, dump, s.start, onBack = { screen = s.back },
                            chordTrack = s.meta?.let { lessons.songChords.value[it.id] })
                        is Screen.Lesson -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { voice.stop(); screen = s.back }
                            val l = s.lesson
                            androidx.compose.runtime.key(l.id) { LessonScreen(l, s.step, LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), s.inject,
                                midiScript = s.midiScript,
                                onStep = { if (it != s.step) screen = s.copy(step = it, inject = null, midiScript = null) },
                                onSong = l.songId?.let { id -> { songs.firstOrNull { it.id == id && repo.available(it) }?.let { openSong(it, back = s) } } },
                                onDone = { prefs.markLessonDone(l.id); progress.record(ProgressEvent.LessonComplete(l.id)) },
                                onNext = nextLesson(l)?.let { n -> { screen = Screen.Lesson(n, s.back) } },
                                onBack = { screen = s.back }) }
                        }
                        is Screen.Daily -> {
                            val part = s.plan.parts[s.index]
                            if (s.between) Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                BackHandler { finishDaily(s, s.done) }
                                Interstitial(part.next, voice, onGo = { screen = s.copy(between = false) },
                                    onSkip = { voice.stop(); advanceDaily(s, completed = false) },
                                    onEnd = { voice.stop(); finishDaily(s, s.done) })
                            } else when (part) {
                                is SessionPart.Chords -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    BackHandler { advanceDaily(s, completed = false) }
                                    // Chord practice does not run the player, so its time is counted here.
                                    androidx.compose.runtime.DisposableEffect(s.index) {
                                        val t0 = System.currentTimeMillis()
                                        onDispose {
                                            val secs = ((System.currentTimeMillis() - t0) / 1000).toInt()
                                            prefs.addPractice(java.time.LocalDate.now(), secs)
                                            if (secs > 0) progress.record(ProgressEvent.Practice(java.time.LocalDateTime.now(), secs))
                                        }
                                    }
                                    androidx.compose.foundation.layout.Column {
                                        Text("Practice today: chords", style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(start = 20.dp, top = 20.dp))
                                        DailyChords(lessons, sampler, midi, mic, prefs.micDefault, part.itemIds, inject = debugStart.inject.takeIf { startDaily },
                                            onEnd = { n -> advanceDaily(s, completed = n > 0, part.copy(itemIds = part.itemIds.take(n))) })
                                    }
                                }
                                is SessionPart.Lesson -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    val l = COURSE.flatMap { it.lessons }.first { it.id == part.lessonId }
                                    BackHandler { voice.stop(); advanceDaily(s, prefs.lessonDone(l.id)) }
                                    androidx.compose.runtime.key(l.id) { LessonScreen(l, 0, LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), null,
                                        onStep = {}, onSong = null,
                                        onDone = { prefs.markLessonDone(l.id); progress.record(ProgressEvent.LessonComplete(l.id)) },
                                        onNext = null,
                                        onBack = { voice.stop(); advanceDaily(s, prefs.lessonDone(l.id)) }) }
                                }
                                is SessionPart.Loop, is SessionPart.Song -> {
                                    val id = if (part is SessionPart.Loop) part.songId else (part as SessionPart.Song).songId
                                    val m = songs.first { it.id == id }
                                    val song = remember(id) { repo.load(m) }
                                    val partStart = remember(s.index) { System.currentTimeMillis() }
                                    androidx.compose.runtime.key(s.index) {
                                        PlayerScreen(song, m, sampler, midi, mic, voice, prefs, samplerReady.value,
                                            if (part is SessionPart.Song) prefs.progress(id).let { if (it.completed) 0.0 else it.lastPositionMs.toDouble() } else 0.0,
                                            dump, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission(),
                                                loopBar = (part as? SessionPart.Loop)?.bar, timeLimitMs = if (part is SessionPart.Loop) 120_000L else null),
                                            // Leaving the song after a minute or more of it counts; leaving at once does not.
                                            onBack = { advanceDaily(s, completed = System.currentTimeMillis() - partStart >= 60_000) },
                                            onFinished = { if (part is SessionPart.Song) advanceDaily(s, completed = true) },
                                            chordTrack = lessons.songChords.value[id],
                                            onTimeUp = { advanceDaily(s, completed = true) })
                                    }
                                }
                            }
                        }
                        is Screen.SightRead -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { voice.stop(); screen = s.back }
                            com.bigpapituna.sharpears.ui.SightReadScreen(LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), s.level, s.inject,
                                onBack = { screen = s.back })
                        }
                        is Screen.EarTraining -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            // The hub handles Back itself: a game goes back to the hub, the hub to Learn.
                            EarTrainingScreen(sampler, midi, mic, voice, prefs.micDefault, onBack = { screen = s.back },
                                onReport = ::earEvent, sound = { prefs.sound }, start = s.start)
                        }
                        Screen.Onboarding -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            OnboardingScreen(progress, prefs, onReminderChanged = { com.bigpapituna.sharpears.reminder.Reminder.schedule(this@MainActivity, prefs) }) { lessonId ->
                                // The suggested first lesson opens straight away; Back from it lands on Learn.
                                screen = lessonId?.let { id -> COURSE.flatMap { it.lessons }.firstOrNull { it.id == id } }
                                    ?.let { Screen.Lesson(it, Screen.Home(HomeTab.LEARN)) } ?: Screen.Home()
                            }
                        }
                        is Screen.Settings -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            SettingsScreen(prefs, songs, packageManager.getPackageInfo(packageName, 0).versionName ?: "", voice = voice,
                                onReminder = { com.bigpapituna.sharpears.reminder.Reminder.schedule(this@MainActivity, prefs) },
                                onMicSetup = { screen = Screen.MicSetup(s) },
                                onPrivacy = { screen = Screen.Privacy(s) }, onLicences = { screen = Screen.Licences(s) },
                                onBack = { screen = s.back })
                        }
                        is Screen.Privacy -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            PrivacyScreen(mic.canRecord, onMicSetup = { screen = Screen.MicSetup(s) }, onBack = { screen = s.back })
                        }
                        is Screen.Licences -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            LicencesScreen(songs, onBack = { screen = s.back })
                        }
                        is Screen.MicSetup -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            MicSetupScreen(mic, midi, debuggable,
                                onMicCheck = { screen = Screen.Player(micCheck, null, PlayerStart(Mode.WAIT, Practice.BOTH, mic = true), 0.0, s) },
                                onBack = { screen = s.back })
                        }
                        is Screen.Home -> {
                            BackHandler(enabled = s.tab != HomeTab.SONGS) { screen = Screen.Home() }
                            val lv = lessons.version.intValue
                            val due = remember(lv, lessons.songPacks.value) {
                                lessons.curriculum().queue(lessons.mastery(), System.currentTimeMillis()).size
                            }
                            HomeShell(s.tab, { screen = s.copy(tab = it, inject = null, game = GameStart()) }, badge = mapOf(HomeTab.CHORDS to due)) { tab ->
                                when (tab) {
                                    HomeTab.SONGS -> {
                                        val pv = prefs.version.intValue
                                        val entries = remember(songs, pv) {
                                            songs.map { m -> LibraryEntry(m, repo.available(m), prefs.progress(m.id), m.pairWith?.let { p -> songs.firstOrNull { it.id == p } }) }
                                        }
                                        val last = remember(entries) { entries.firstOrNull { it.meta.id == prefs.lastSongId && it.available } }
                                        LibraryScreen(entries, last,
                                            micLabel = when { !mic.hasPermission() -> "Set up mic"; prefs.micDefault -> "Mic on"; else -> "Mic off" },
                                            LibraryActions(
                                                onOpen = ::openSong,
                                                onImport = { into -> importInto = into; pickMidi.launch(arrayOf("audio/midi", "audio/x-midi", "audio/mid", "application/x-midi", "application/octet-stream", "*/*")) },
                                                onMic = { screen = Screen.MicSetup(s) },
                                                onToday = ::startDaily,
                                            ),
                                            practice = remember(pv) { PracticeStats.summary(prefs.practice(), java.time.LocalDate.now()) },
                                            today = remember(pv, lv, lessons.songPacks.value) {
                                                val plan = todayPlan()
                                                TodayPanel(plan.aboutLabel, plan.parts.map { it.row },
                                                    prefs.dailySummary(java.time.LocalDate.now()))
                                            })
                                    }
                                    HomeTab.LEARN -> {
                                        val pv = prefs.version.intValue
                                        LearnScreen(done = { pv >= 0 && prefs.lessonDone(it) }, onOpen = { screen = Screen.Lesson(it, s) },
                                            onSightRead = { screen = Screen.SightRead(s) },
                                            sightBest = remember(pv) { (prefs.sightBest(1) ?: prefs.sightBest(2))?.label })
                                    }
                                    HomeTab.PRACTICE -> PracticeScreen(sampler, mic, practiceStore, onOpenExercise = { _, _ -> },
                                        startTab = s.practiceTab,
                                        onEvent = { e -> progress.record(ProgressEvent.FeatureUsed(if (e.kind == PracticeKind.WARMUP) "warmups" else "scales")) },
                                        onTab = { t ->
                                            screen = s.copy(practiceTab = t)
                                            // The metronome and tuner have no "start" of their own: opening them is using them.
                                            when (t) { PracticeTab.METRONOME -> "metronome"; PracticeTab.TUNER -> "tuner"; else -> null }
                                                ?.let { progress.record(ProgressEvent.FeatureUsed(it)) }
                                        })
                                    HomeTab.CHORDS -> ChordsScreen(sampler, midi, mic, voice, prefs, lessons, prefs.micDefault, s.chordsTab, s.inject, s.game,
                                        onPlayProgression = { p, key ->
                                            screen = Screen.Player(progressionSong(p, key), null, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission()),
                                                0.0, s.copy(chordsTab = if (lessons.session.isNotEmpty()) ChordsTab.LESSONS else ChordsTab.PROGRESSIONS, inject = null, game = GameStart()))
                                        })
                                    HomeTab.YOU -> ProfileScreen(progress, prefs, songs, onSettings = { screen = Screen.Settings(s) })
                                }
                            }
                        }
                    }
                    }
                    }
                    dailyDone?.let { (parts, min, streak) ->
                        com.bigpapituna.sharpears.ui.Celebration("Done for today!",
                            if (streak >= 2) "$streak days in a row. Keep it going." else "Same time tomorrow?",
                            listOf(com.bigpapituna.sharpears.ui.Stat("$parts", if (parts == 1) "Part" else "Parts"),
                                com.bigpapituna.sharpears.ui.Stat("$min min", "Practice"),
                                com.bigpapituna.sharpears.ui.Stat("$streak", if (streak == 1) "Day" else "Days in a row")),
                            primary = "Continue", onPrimary = { dailyDone = null },
                            pose = if (streak >= 2) com.bigpapituna.sharpears.ui.Pose.STREAK else com.bigpapituna.sharpears.ui.Pose.LEVEL_UP,
                            sampler = sampler.takeIf { prefs.sound })
                    }
                    // Debug: --es screen motion [--es demo celebrate|cheer|pup|keys] (Team E's effects, ui/motion/MotionDemo.kt).
                    var motionDemo by remember { mutableStateOf(debuggable && intent.getStringExtra("screen") == "motion") }
                    if (motionDemo) com.bigpapituna.sharpears.ui.motion.MotionDemo(intent.getStringExtra("demo"), sampler) { motionDemo = false }
                    importError?.let { msg ->
                        AlertDialog(onDismissRequest = { importError = null }, title = { Text("Could not import") }, text = { Text(msg) },
                            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } })
                    }
                    // Level-ups and achievement toasts, last so they sit on top. Not on the stage: a level-up waits until
                    // the song is over (the store keeps it pending), so it never interrupts playing.
                    if (!isStage(screen)) SharpEarsTheme(dark = isSystemInDarkTheme()) { ProgressOverlays(progress) }
                }
            }
            }
        }
    }

    override fun onDestroy() {
        voice.release()
        mic.stop()
        midi.stop()
        sampler.release()
        super.onDestroy()
    }
}
