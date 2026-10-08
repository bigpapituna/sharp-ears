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
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.LearnScreen
import com.bigpapituna.sharpears.ui.LessonScreen
import com.bigpapituna.sharpears.ui.nextLesson
import com.bigpapituna.sharpears.ui.HomeShell
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
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
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
import com.bigpapituna.sharpears.ui.ChordsScreen
import com.bigpapituna.sharpears.ui.ChordsTab
import com.bigpapituna.sharpears.ui.LibraryActions
import com.bigpapituna.sharpears.ui.LibraryEntry
import com.bigpapituna.sharpears.ui.LibraryScreen
import com.bigpapituna.sharpears.ui.MicSetupScreen
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.SettingsScreen
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAG = "SharpEars"

private sealed interface Screen {
    /** The three tabs. [chordsTab] and [inject] only matter for the Chords tab. */
    data class Home(val tab: HomeTab = HomeTab.SONGS, val chordsTab: ChordsTab = ChordsTab.LESSONS, val inject: String? = null) : Screen
    /** [meta] is null for generated songs (mic check, progressions). [back] is where Back goes. */
    data class Player(val song: Song, val meta: SongMeta?, val start: PlayerStart, val startMs: Double, val back: Screen,
                      val lesson: CourseLesson? = null) : Screen
    data class Lesson(val lesson: CourseLesson, val back: Screen) : Screen
    data class MicSetup(val back: Screen) : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var sampler: Sampler
    private lateinit var midi: MidiDevices
    private lateinit var mic: MicInput
    private val samplerReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sampler = Sampler(this) { samplerReady.value = true }
        midi = MidiDevices(this).also { it.start() }
        mic = MicInput(this)
        val repo = SongRepository(this)
        val prefs = Prefs(this).also { it.logRestored() }
        val lessons = LessonStore(this)
        val micCheck = micCheckExercise()

        // Debug builds accept extras to open a screen directly:
        //   --es song <id>|mic-check [--el seek <ms>] [--ez dump true] [--ez tabletop true]
        //   [--es mode wait|perform] [--es hands left|right] [--ez mic true] [--es inject <wav path>|synth:76,75,60+64+67@600]
        //   --es song prog:<index>:<key 0-11>   (a chord progression, e.g. prog:0:7 = I-V-vi-IV in G)
        //   --es screen chords|settings|learn [--es tab daily|dictionary|practice|progressions] [--es inject ...]
        //   --es lesson <course lesson id>
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val openSong = intent.getStringExtra("song")?.takeIf { debuggable }
        val debugStart = PlayerStart(
            mode = when (intent.getStringExtra("mode")) { "wait" -> Mode.WAIT; "perform" -> Mode.PERFORM; else -> Mode.LISTEN },
            practice = when (intent.getStringExtra("hands")) { "left" -> Practice.LEFT; "right" -> Practice.RIGHT; else -> Practice.BOTH },
            mic = intent.getBooleanExtra("mic", false),
            inject = intent.getStringExtra("inject")?.takeIf { debuggable },
            tabletop = debuggable && intent.getBooleanExtra("tabletop", false),
        )
        val seekMs = intent.getLongExtra("seek", 0L).toDouble()
        val dump = debuggable && intent.getBooleanExtra("dump", false)
        val first: Screen = when {
            debuggable && intent.getStringExtra("screen") == "chords" -> Screen.Home(HomeTab.CHORDS,
                when (intent.getStringExtra("tab")) {
                    "dictionary" -> ChordsTab.DICTIONARY; "practice" -> ChordsTab.PRACTICE; "progressions" -> ChordsTab.PROGRESSIONS
                    else -> ChordsTab.LESSONS
                },
                debugStart.inject,
            )
            debuggable && intent.getStringExtra("screen") == "settings" -> Screen.Home(HomeTab.SETTINGS)
            debuggable && intent.getStringExtra("screen") == "learn" -> Screen.Home(HomeTab.LEARN)
            debuggable && intent.getStringExtra("lesson") != null ->
                COURSE.flatMap { it.lessons }.first { it.id == intent.getStringExtra("lesson") }.let { Screen.Lesson(it, Screen.Home(HomeTab.LEARN)) }
            openSong == null -> Screen.Home()
            openSong == MIC_CHECK_ID -> Screen.Player(micCheck, null, debugStart, seekMs, Screen.Home())
            openSong.startsWith("prog:") -> openSong.split(":").let { (_, i, k) ->
                Screen.Player(progressionSong(PROGRESSIONS[i.toInt()], k.toInt()), null, debugStart, seekMs, Screen.Home(HomeTab.CHORDS, ChordsTab.PROGRESSIONS))
            }
            else -> repo.all().first { it.id == openSong }.let { m -> Screen.Player(repo.load(m), m, debugStart, seekMs, Screen.Home()) }
        }

        setContent {
            var screen by remember { mutableStateOf(first) }
            // The player is always dark, like a video; everything else follows the system.
            SharpEarsTheme(dark = if (screen is Screen.Player) true else isSystemInDarkTheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var libraryVersion by remember { mutableIntStateOf(0) }       // bumps after an import
                    var importError by remember { mutableStateOf<String?>(null) }
                    var importInto by remember { mutableStateOf<SongMeta?>(null) }
                    val scope = rememberCoroutineScope()
                    val songs = remember(libraryVersion) { repo.all() }

                    // Each library song yields a "Chords of <song>" lesson pack (DESIGN 4.3), labeled off the main thread.
                    LaunchedEffect(libraryVersion) {
                        lessons.songPacks.value = withContext(Dispatchers.Default) {
                            songs.filter(repo::available).mapNotNull { m ->
                                runCatching { songPack(repo.load(m)) }.onFailure { Log.d(TAG, "song pack ${m.id}: $it") }.getOrNull()
                            }.filter { it.items.isNotEmpty() }
                        }
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
                    val pickMidi = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        val into = importInto
                        if (uri == null) return@rememberLauncherForActivityResult
                        scope.launch {
                            try {
                                val m = withContext(Dispatchers.IO) { repo.import(uri, into) }
                                Log.d(TAG, "import ok ${m.id} -> ${m.file}")
                                libraryVersion++
                            } catch (e: ImportException) {
                                Log.d(TAG, "import failed: ${e.message}"); importError = e.message
                            } catch (e: Exception) {
                                Log.d(TAG, "import failed: $e"); importError = "Import failed: ${e.message}"
                            }
                        }
                    }

                    when (val s = screen) {
                        is Screen.Player -> PlayerScreen(s.song, s.meta, sampler, midi, mic, prefs, samplerReady.value,
                            s.startMs, dump, s.start, onBack = { screen = s.back },
                            onFinished = { run ->
                                // A lesson exercise counts once played to the end; Perform ones need their pass mark.
                                val l = s.lesson ?: return@PlayerScreen
                                if (run == null || run.accuracy >= (l.exercise?.pass ?: 0)) { prefs.markLessonDone(l.id); Log.d(TAG, "lesson ${l.id} done") }
                                else Log.d(TAG, "lesson ${l.id}: ${run.accuracy}% < ${l.exercise?.pass}%")
                            })
                        is Screen.Lesson -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            val l = s.lesson
                            LessonScreen(l, done = prefs.lessonDone(l.id) && prefs.version.intValue >= 0,
                                onPractice = {
                                    val ex = l.exercise ?: return@LessonScreen
                                    screen = Screen.Player(ex.toSong(l.id, l.title), null,
                                        PlayerStart(ex.mode, ex.practice, mic = prefs.micDefault && mic.hasPermission()), 0.0, s, lesson = l)
                                },
                                onSong = { songs.firstOrNull { it.id == l.songId && repo.available(it) }?.let { openSong(it, back = s) } },
                                onMarkDone = { prefs.markLessonDone(l.id) },
                                onNext = nextLesson(l)?.let { n -> { screen = Screen.Lesson(n, s.back) } },
                                onBack = { screen = s.back })
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
                            HomeShell(s.tab, { screen = s.copy(tab = it, inject = null) }, badge = mapOf(HomeTab.CHORDS to due)) {
                                when (s.tab) {
                                    HomeTab.SONGS -> {
                                        val pv = prefs.version.intValue
                                        val entries = remember(songs, pv) { songs.map { LibraryEntry(it, repo.available(it), prefs.progress(it.id)) } }
                                        val last = remember(entries) { entries.firstOrNull { it.meta.id == prefs.lastSongId && it.available } }
                                        LibraryScreen(entries, last,
                                            micLabel = when { !mic.hasPermission() -> "Set up mic"; prefs.micDefault -> "Mic on"; else -> "Mic off" },
                                            LibraryActions(
                                                onOpen = ::openSong,
                                                onImport = { into -> importInto = into; pickMidi.launch(arrayOf("audio/midi", "audio/x-midi", "audio/mid", "application/x-midi", "application/octet-stream", "*/*")) },
                                                onMic = { screen = Screen.MicSetup(s) },
                                            ))
                                    }
                                    HomeTab.LEARN -> {
                                        val pv = prefs.version.intValue
                                        LearnScreen(done = { pv >= 0 && prefs.lessonDone(it) }, onOpen = { screen = Screen.Lesson(it, s) })
                                    }
                                    HomeTab.CHORDS -> ChordsScreen(sampler, midi, mic, lessons, prefs.micDefault, s.chordsTab, s.inject,
                                        onPlayProgression = { p, key ->
                                            screen = Screen.Player(progressionSong(p, key), null, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission()),
                                                0.0, s.copy(chordsTab = if (lessons.session.isNotEmpty()) ChordsTab.LESSONS else ChordsTab.PROGRESSIONS, inject = null))
                                        })
                                    HomeTab.SETTINGS -> SettingsScreen(prefs, songs, packageManager.getPackageInfo(packageName, 0).versionName ?: "",
                                        onMicSetup = { screen = Screen.MicSetup(s) })
                                }
                            }
                        }
                    }
                    importError?.let { msg ->
                        AlertDialog(onDismissRequest = { importError = null }, title = { Text("Could not import") }, text = { Text(msg) },
                            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        mic.stop()
        midi.stop()
        sampler.release()
        super.onDestroy()
    }
}
