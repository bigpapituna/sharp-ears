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
import com.bigpapituna.sharpears.engine.CoursePath
import com.bigpapituna.sharpears.engine.play.QuestEvent
import com.bigpapituna.sharpears.engine.world.Reward
import com.bigpapituna.sharpears.data.PlayStore
import com.bigpapituna.sharpears.data.WorldStore
import com.bigpapituna.sharpears.data.KidStore
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.data.TimedOnScreen
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.ToyKind
import com.bigpapituna.sharpears.engine.kid.Finale
import com.bigpapituna.sharpears.engine.kid.KidMap
import com.bigpapituna.sharpears.engine.kid.KidPages
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.ui.kid.StickerPage
import com.bigpapituna.sharpears.ui.world.LocalWorld
import com.bigpapituna.sharpears.ui.world.LocalPupOutfit
import com.bigpapituna.sharpears.ui.world.WorldTab
import com.bigpapituna.sharpears.ui.world.WorldSounds
import com.bigpapituna.sharpears.ui.motion.Juice
import com.bigpapituna.sharpears.ui.motion.wakesPup
import com.bigpapituna.sharpears.ui.play.Toy
import com.bigpapituna.sharpears.ui.play.frameTap
import com.bigpapituna.sharpears.ui.play.opens
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
import com.bigpapituna.sharpears.engine.toSong
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
import com.bigpapituna.sharpears.ui.GoalState
import com.bigpapituna.sharpears.engine.progress.DailyGoal
import com.bigpapituna.sharpears.ui.MicSetupScreen
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.SettingsScreen
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.paper
import androidx.compose.animation.AnimatedContent
import com.bigpapituna.sharpears.data.PracticeStore
import com.bigpapituna.sharpears.data.EarStore
import com.bigpapituna.sharpears.data.TheoryStore
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
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.ui.family.FamilySettings
import com.bigpapituna.sharpears.ui.family.FamilyWall
import com.bigpapituna.sharpears.ui.family.LocalParentGate
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.family.LocalProfiles
import com.bigpapituna.sharpears.ui.family.ParentGateController
import com.bigpapituna.sharpears.ui.family.ParentGateHost
import com.bigpapituna.sharpears.ui.family.ProfilePicker
import com.bigpapituna.sharpears.ui.family.familyWallEntries
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAG = "SharpEars"
/** The instance state's key for the open screen (see routeOf). */
private const val ROUTE = "screen"
/** The instance state's flag for "Who's playing today?" still waiting for an answer. */
private const val PICKER = "picker"

/**
 * Chord games, chord practice and the ear games listen for right notes only: the matcher skips naming wrong keys while
 * one is on screen (cheaper, and nothing there paints them). Not stored; the old setting comes back on leaving.
 */
@androidx.compose.runtime.Composable
private fun QuietWrongKeys(mic: MicInput) = androidx.compose.runtime.DisposableEffect(mic) {
    val before = mic.matcher.config.nameWrongKeys
    mic.matcher.config = mic.matcher.config.copy(nameWrongKeys = false)
    onDispose { mic.matcher.config = mic.matcher.config.copy(nameWrongKeys = before) }
}

private sealed interface Screen {
    /** The five tabs. [chordsTab], [inject] and [game] only matter for Chords, [practiceTab] for Practice. */
    data class Home(val tab: HomeTab = HomeTab.SONGS, val chordsTab: ChordsTab = ChordsTab.PLAY, val inject: String? = null,
                    val game: GameStart = GameStart(), val practiceTab: PracticeTab = PracticeTab.SCALES,
                    /** A kid's home (2.2) after a page's finale: the map opens at that page's end and rolls on to the next stone. */
                    val kidFrom: String? = null) : Screen
    /** [meta] is null for generated songs (mic check, progressions). [back] is where Back goes. */
    data class Player(val song: Song, val meta: SongMeta?, val start: PlayerStart, val startMs: Double, val back: Screen) : Screen
    /** A course lesson at step [step]. [inject] (debug) feeds the mic for that first step. */
    data class Lesson(val lesson: CourseLesson, val back: Screen, val step: Int = 0, val inject: String? = null, val midiScript: String? = null, val scene: Int = 0) : Screen
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
    data class Theory(val back: Screen) : Screen
    /** The pup's world (2.1): the places, the jar and egg, the album, dress-up and the records, from the You tab's card. */
    data class World(val back: Screen, val tab: WorldTab = WorldTab.WORLD) : Screen
    /** The play corner (2.1): the Jam, Pup says or Clap along. [hint] is the quest's line for the Jam ("find all the Cs"). */
    data class Play(val toy: Toy, val back: Screen, val hint: String? = null) : Screen
    /** A kid's songs (2.2, the kid home's Songs button): only the songs played by colour, big tiles. */
    data class KidSongs(val back: Screen) : Screen
    /** A kid's stickers (2.2, My stickers): the album, dress-up, records and paws as pages. */
    data class KidStickers(val back: Screen, val page: StickerPage = StickerPage.ALBUM) : Screen
    /** A Playground page's finale (2.2): fireworks, the album page, the certificate, then on along the path. */
    data class Finale(val pageId: String) : Screen
}

/** Practise together with nothing chosen (the parent brief, 2.4): the next song [whoId] picks opens as a duet with [partnerId]. */
private data class DuetNext(val whoId: String, val partnerId: String?)

/** The play corner's toy as the brief's log names it. */
private fun toyKind(t: Toy) = when (t) { Toy.JAM -> ToyKind.JAM; Toy.PUP_SAYS -> ToyKind.PUP_SAYS; Toy.CLAP -> ToyKind.CLAP_ALONG }

/** Order of screens for the transitions: deeper goes forward, shallower goes back, the player gets the curtain. */
private fun depthOf(s: Screen) = when (s) {
    is Screen.Home -> 0
    is Screen.Settings, is Screen.EarTraining, is Screen.Theory, Screen.Onboarding, is Screen.World, is Screen.KidSongs, is Screen.KidStickers -> 1
    is Screen.Player, is Screen.Lesson, is Screen.SightRead, is Screen.Daily, is Screen.Privacy, is Screen.Licences, is Screen.MicSetup, is Screen.Play,
    is Screen.Finale -> 2
}

/**
 * The screen as one line for the saved instance state, the screens it goes back to first ("home:LEARN/lesson:2:find-c"),
 * so a recreate (a process the system stopped in the background, Don't keep activities, a dark mode or font size change)
 * reopens it, a lesson at its step, instead of home. Practice today and generated songs (the mic check, progressions,
 * practice patterns) save as where they go back to. Read by [MainActivity.screenAt].
 */
private fun routeOf(s: Screen): String = when (s) {
    is Screen.Home -> "home:${s.tab.name}"
    is Screen.Player -> if (s.meta == null) routeOf(s.back) else "${routeOf(s.back)}/song:${s.meta.id}"
    is Screen.Lesson -> "${routeOf(s.back)}/lesson:${s.step}:${s.lesson.id}"
    is Screen.MicSetup -> "${routeOf(s.back)}/micsetup"
    is Screen.SightRead -> "${routeOf(s.back)}/sight:${s.level}"
    is Screen.Daily -> "home:SONGS"
    is Screen.Privacy -> "${routeOf(s.back)}/privacy"
    is Screen.Licences -> "${routeOf(s.back)}/licences"
    is Screen.Settings -> "${routeOf(s.back)}/settings"
    is Screen.EarTraining -> "${routeOf(s.back)}/ear"
    Screen.Onboarding -> "onboarding"
    is Screen.Theory -> "${routeOf(s.back)}/theory"
    is Screen.World -> "${routeOf(s.back)}/world:${s.tab.name}"
    is Screen.Play -> "${routeOf(s.back)}/play:${s.toy.name}"
    is Screen.KidSongs -> "${routeOf(s.back)}/kidsongs"
    is Screen.KidStickers -> "${routeOf(s.back)}/kidstickers:${s.page.name}"
    is Screen.Finale -> "finale:${s.pageId}"
}

/** The player is always dark, like a video; everything else follows the system. */
private fun playerShown(s: Screen) = s is Screen.Player ||
    (s is Screen.Daily && !s.between && s.plan.parts[s.index].let { it is SessionPart.Loop || it is SessionPart.Song })

/** The stage: the player, or a Practice today part that runs one. */
private fun isStage(s: Screen) = s is Screen.Player || (s is Screen.Daily && !s.between)

/** Screens played at the piano, hands off the phone for minutes: the screen stays on (see [KeepScreenOnWhile]). */
private fun keepsScreenOn(s: Screen) = s is Screen.Player || s is Screen.Lesson || s is Screen.Daily || s is Screen.SightRead ||
    s is Screen.Play || s is Screen.EarTraining || s is Screen.Theory || s is Screen.Finale

/**
 * The screen stays on while [stage] or while the mic listens (chord games, the tuner): a phone's 30 s timeout used to
 * switch the screen off under a child playing a lesson, which stopped the mic and the lesson with it.
 */
@androidx.compose.runtime.Composable
private fun KeepScreenOnWhile(mic: MicInput, stage: Boolean) {
    val on = stage || mic.source.value != null
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view, on) { view.keepScreenOn = on; onDispose { view.keepScreenOn = false } }
}

class MainActivity : ComponentActivity() {
    private lateinit var sampler: Sampler
    private lateinit var midi: MidiDevices
    private lateinit var mic: MicInput
    private var openVoice: Voice? = null
    private val samplerReady = mutableStateOf(false)
    private lateinit var repo: SongRepository
    private lateinit var profiles: Profiles
    private lateinit var gate: ParentGateController
    /** The profile the app is open as (null while the picker shows). */
    private var openId: String? = null
    /** Kid or grown-up, as the app was opened (a switch of kind reopens it). */
    private var openKind: com.bigpapituna.sharpears.engine.family.ProfileKind? = null
    /** A kid reads or not, as the app was opened (a big kid's course path differs, so a change reopens it too). */
    private var openReader: Boolean? = null
    /** Launches of this activity's content ([openApp] with fromLaunch), so a new one forgets the last one's screen. */
    private var launches = 0
    /** The open screen as [routeOf] writes it, kept in the instance state; [restoreRoute] is the one a recreate reopens. */
    private var shownRoute: String? = null
    private var restoreRoute: String? = null
    /** The open profile's preferences and kid memory, so a parent brief action's change shows at once ([briefNav] changed). */
    private var openPrefs: Prefs? = null
    private var openKid: KidStore? = null
    /** A screen a parent brief action opens ([briefNav]), taken by the open composition. */
    private val navRequest = mutableStateOf<Screen?>(null)
    /** Counts those, so a lesson an action opens again starts fresh (its LessonScreen is keyed by it). */
    private val navCount = mutableIntStateOf(0)
    /** A screen an action asked for someone who is not open: the app opens as them on it ([openApp]). */
    private var pendingOpen: Pair<String, Screen>? = null
    /** Practise together with nothing chosen: the next song picked opens as a duet. */
    private val duetNext = mutableStateOf<DuetNext?>(null)
    /** The weekly brief's notification asked for this person's brief and period (all builds; closed by Back). */
    private val briefRequest = mutableStateOf<Pair<String, com.bigpapituna.sharpears.engine.brief.Period>?>(null)

    /**
     * The parent brief's doors into the app (ui/brief/AppEffects.kt, step 3 of the brief): Replay, Practise together and the
     * duet's song pick open screens for the child the brief is about, switching to them first when someone else is open,
     * and a change to someone's path (Set as next, Fresh start, Focus, Open the next page, Teacher mode) is read again by
     * the open map. Called from the action's worker thread: everything here runs on the main thread.
     */
    private val briefNav = object : com.bigpapituna.sharpears.ui.brief.BriefNav {
        override fun openLesson(who: Profile, lessonId: String) = runOnUiThread {
            val lesson = COURSE.flatMap { it.lessons }.firstOrNull { it.id == lessonId } ?: return@runOnUiThread
            Log.d(TAG, "brief: open lesson $lessonId for ${who.id}")
            go(who, Screen.Lesson(lesson, if (who.isKid) Screen.Home() else Screen.Home(HomeTab.LEARN)))
        }

        override fun openSong(who: Profile, songId: String, duet: Boolean) = runOnUiThread {
            val m = repo.all().firstOrNull { it.id == songId && repo.available(it) } ?: return@runOnUiThread
            val song = runCatching { repo.load(m) }.getOrNull() ?: return@runOnUiThread
            val partner = if (duet) com.bigpapituna.sharpears.engine.play.Duet.grownUpFor(who, openId, profiles.all) else null
            Log.d(TAG, "brief: open song $songId for ${who.id}" + if (duet) " as a duet with ${partner ?: "a partner they pick"}" else "")
            go(who, Screen.Player(song, m, briefStart(who, Prefs(this@MainActivity, who.id), duet, partner), 0.0,
                if (who.isKid) Screen.KidSongs(Screen.Home()) else Screen.Home(HomeTab.SONGS)))
        }

        override fun pickSong(who: Profile, duet: Boolean) = runOnUiThread {
            duetNext.value = if (duet) DuetNext(who.id, com.bigpapituna.sharpears.engine.play.Duet.grownUpFor(who, openId, profiles.all)) else null
            Log.d(TAG, "brief: ${who.id} picks a song" + if (duet) " for a duet" else "")
            go(who, if (who.isKid) Screen.KidSongs(Screen.Home()) else Screen.Home(HomeTab.SONGS))
        }

        override fun changed(who: Profile) = runOnUiThread {
            if (who.id == openId) { openPrefs?.let { it.version.intValue++ }; openKid?.let { it.version.intValue++ } }
        }
    }

    /** How a song a brief action opens starts: a kid's duet with the grown-up on the left hand, a grown-up's with the picker open. */
    private fun briefStart(who: Profile, prefs: Prefs, duet: Boolean, partnerId: String?): PlayerStart {
        val micOn = prefs.micDefault && mic.hasPermission()
        return when {
            !duet -> PlayerStart(Mode.WAIT, mic = micOn)
            who.isKid && partnerId != null -> PlayerStart(Mode.WAIT, Practice.BOTH, mic = micOn, duet = com.bigpapituna.sharpears.engine.play.Duet(partnerId, who.id))
            else -> PlayerStart(Mode.WAIT, Practice.BOTH, mic = micOn, pickDuet = true)
        }
    }

    /** Opens [to] as [who]: at once when they are open, else after the app opens as them (the brief's dialog closes either way). */
    private fun go(who: Profile, to: Screen) {
        briefRequest.value = null
        navCount.intValue++
        if (who.id == openId) {
            if (who.isKid) gate.close()          // the phone goes back to the child: a grown-up's open gate does not stay open for them
            navRequest.value = to
        } else {
            pendingOpen = who.id to to
            val wasOpen = openId != null
            profiles.switchTo(who.id)            // the profile flow reopens the app as them, on [to]
            if (!wasOpen) openApp(fromLaunch = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        restoreRoute = savedInstanceState?.getString(ROUTE)
        sampler = Sampler(this) { samplerReady.value = true }
        midi = MidiDevices(this).also { it.start() }
        mic = MicInput(this)
        repo = SongRepository(this)
        profiles = Profiles(this)
        gate = ParentGateController(profiles)
        Briefs.init(this)                                  // the parent brief's event logs, files/events/<profile>.log
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // --es profile <id> (debug): open as that profile, no picker.
        val debugProfile = intent.getStringExtra("profile")?.takeIf { debuggable }?.also(profiles::switchTo)
        // Review pipeline (private build): crashes are kept, a weekly summary is written, and whatever waits goes out.
        if (mic.keepRecordings) {
            val app = applicationContext      // read at crash time, never this activity (the handler outlives it)
            mic.reporter.catchCrashes(packageManager.getPackageInfo(packageName, 0).versionName ?: "?") { Profiles(app).all.map { it.name } }
            mic.reporter.weeklySummary(Prefs(this, Profile.OWNER_ID).practice())
            mic.reporter.flush()
        }
        com.bigpapituna.sharpears.reminder.Reminder.schedule(this)        // alarms do not survive an update or restart
        com.bigpapituna.sharpears.reminder.BriefReminder.scheduleAll(this) // the weekly brief's alarms, one per person, likewise
        // Someone else picked (the family wall, Settings > Family, the player deleted): the app opens again as them. So it
        // does when the player turns from kid to grown-up or back: the course path, the skin and the mic offset change.
        lifecycleScope.launch {
            profiles.currentFlow.map { Triple(it.id, it.kind, it.reader) }.distinctUntilChanged().collect { (id, kind, reader) ->
                if (openId != null && (openId != id || openKind != kind || openReader != reader)) openApp(fromLaunch = false)
            }
        }
        // Two or more people: "Who's playing today?" first, unless a debug route or a recreate (rotation, import) opened us;
        // a recreate while it still asked (the process stopped in the background) asks again rather than open as the last player.
        val debugRoute = debuggable && (listOf("screen", "song", "lesson").any { intent.getStringExtra(it) != null } || intent.getBooleanExtra("daily", false))
        // The world's jingles and the note tick (one SoundPool for the app; Settings > Sound switches them with the rest).
        WorldSounds.init(this)
        if (profiles.roster.needsPicker && (savedInstanceState == null || savedInstanceState.getBoolean(PICKER)) && !debugRoute && debugProfile == null) showPicker()
        else openApp(fromLaunch = true)
    }

    /** The launch picker; picking (or adding someone) opens the app as them. */
    private fun showPicker() {
        Briefs.sessionEnd(); Briefs.currentId = null        // no one is playing yet: nothing is logged
        setContent {
            SharpEarsTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val roster by profiles.rosterFlow.collectAsState()
                    ProfilePicker(roster,
                        onPick = { id -> profiles.switchTo(id); openApp(fromLaunch = true) },
                        onAdd = { p -> if (profiles.create(p) != null) openApp(fromLaunch = true) })
                }
            }
        }
    }

    /**
     * Builds the current profile's stores and shows the app as them. [fromLaunch] honours the launch intent's debug
     * extras; a later switch opens at home. Everything per profile lives here, so a switch starts it fresh.
     */
    private fun openApp(fromLaunch: Boolean) {
        val profile = profiles.current
        val newPlayer = openId != profile.id
        // The phone handed to a kid: a gate a grown-up passed a minute ago does not stay open for them.
        if (profile.isKid && newPlayer) gate.close()
        openId = profile.id
        openKind = profile.kind
        openReader = profile.reader
        Log.d(TAG, "profiles: open as ${profile.id} (${profile.kind}, reader=${profile.reader})")
        val intent = if (fromLaunch) intent else android.content.Intent()      // a switch carries no debug extras
        val prefs = Prefs(this, profile.id).also { it.logRestored() }
        // The mic follows the player: a kid's offset and report tag for every mic screen (chords, ear games, tuner,
        // playground), and a new player starts with wrong keys painted again (the last one's room may have been noisier).
        mic.forProfile(profile.isKid, profile.micOffsetDb, newSession = newPlayer)
        openVoice?.release()
        val voice = Voice(this, prefs, mic).also { openVoice = it }   // the speech engine itself starts on the first line
        voice.speed = if (profile.isKid) 0.85f else 1f              // big and slow for kids (KIDS-PLAN 3.8)
        val lessons = LessonStore(this, profile.id)
        val progress = ProgressStore(this, prefs, songIds = { repo.all().map { it.id } })
        progress.catchUp()                             // once ever: XP for practice, lessons and songs from before 2.0, quietly
        // The parent brief's log follows the player: a switch ends the last one's session (with their practice) and, on
        // screen, starts this one's; events from here on go to this profile's log.
        if (Briefs.currentId != profile.id) Briefs.sessionEnd()
        Briefs.practiceSeconds = { progress.practiceSecondsTotal }
        Briefs.currentId = profile.id
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) Briefs.sessionStart(profile.id)
        // Someone who already played (XP from catchUp or since) skips the first-run questions.
        if (!progress.onboardingDone && progress.xp > 0) progress.onboardingDone = true
        // A kid goes straight in: no questions, the goal the profile was made with, and the first game opens at once.
        val newKid = profile.isKid && !progress.onboardingDone
        if (newKid) { progress.goalMinutes = profile.dailyGoalMin; progress.onboardingDone = true }
        // Kids read the keys by their pads: every white key labelled (a letter, or an animal before reading), until a grown-up picks.
        if (profile.isKid && !prefs.keyLabelsChosen) prefs.keyLabels = com.bigpapituna.sharpears.data.KeyLabels.ALL
        // Kids skip the onboarding that asks about the mic, so their lessons' player steps, concerts and songs opened
        // with it off and heard nothing a kid played (the stages listen anyway). On until someone switches it off.
        if (profile.isKid && !prefs.micDefaultChosen) prefs.micDefault = true
        // The pup's world for this profile: stickers, the jar every note feeds, the outfit; the shelf starts with old bests.
        val world = WorldStore(this, profile.id)
        world.mergeRecords(repo.all().associate { it.id to prefs.progress(it.id).bestAccuracy }.filterValues { it >= 0 })
        Juice.world = world
        WorldSounds.on = prefs.sound
        val playStore = PlayStore(this, profile.id)
        // The kid home's memory (2.2): each lesson's best stars, the finales played (kept for grown-ups too, never shown).
        val kidStore = KidStore(this, profile.id)
        openPrefs = prefs; openKid = kidStore
        fun quest(e: QuestEvent) = playStore.recordQuest(e, profile.isKid).forEach { Log.d(TAG, "quest: ${it.kind} done ($e)") }
        // The course in this person's order: a kid's Playground first, a grown-up's Basics (the Playground an extra).
        val path = CoursePath(profile.firstUnitId, bigKid = profile.isKid && profile.reader, startAt = { progress.experience?.startLessonId })
        val bedtime = com.bigpapituna.sharpears.ui.skin.BedtimeStore(this, profile.id)
        val practiceStore = PracticeStore(this, profile.id)
        val earStore = EarStore(this, profile.id)      // read for the Learn row and reset from Settings; the hub keeps its own
        val theory = TheoryStore(this, profile.id)     // one store for the Learn card and the academy, so the card updates
        val micCheck = micCheckExercise()
        if (fromLaunch && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) kidDebugSeed(intent, prefs, kidStore, progress, bedtime)

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
        //   --ef fxSlow <n>      (2.1.1: the same for the Playground stages' rings and call-outs and the Jam's captions)
        //   --es screen jam --es midi "3000:60/90,3010:64/90"   (2.1.1: a pretend MIDI keyboard into the Jam: ms, key/velocity)
        //   --ez daily true   (starts "Practice today" straight away)
        //   --es screen sight [--ei level 1|2] [--es inject ...]   (sight-reading; seeded when injecting)
        //   --es lesson <id> --es midi "800:p1 4790:p0 1000:60/40"   (a pretend MIDI keyboard: pedal and key/velocity at ms after Play)
        //   --es profile <id>   (open as that profile, skipping the picker; combine with any route above)
        //   --es screen world [--es tab world|album|dress|records]   (this profile's world)
        //   --es screen jam|pupsays|clap [--es hint "<line>"]   (the play corner)
        //   --es screen worlddemo --es part album|world|map|jar|egg|dress|reveal|shelf|idle|onemore ...   (a demo world, ui/world/WorldDemo.kt)
        //   --es screen skin --es view lane|wrong|recap|goodbye|asleep|paws|bite ...   (the kid skin's demo, ui/skin/SkinDemo.kt)
        //   --es screen motion [--es demo celebrate|cheer|pup|keys]   (the motion demo over home)
        //   --es lesson playground-<n> [--ei step n] [--es inject synth:...|claps:400,1150|dyn:60/-20,60/-8@1600~1500]
        //   2.2, the kid home (open as a kid with --es profile <id>):
        //   --es screen kidsongs | kidstickers [--es page album|dress|records|paws] | finale [--es page garden]
        //   --es kidseed <page>:<n>|road|reset   (marks the page's first n lessons done with stars; road: every page walked and
        //       its finale played; reset: the Playground's lessons, stars and finales forgotten) [--ez sleepy true] (today's goal
        //       reached and said goodnight to, so the map shows the pup asleep)
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
        com.bigpapituna.sharpears.ui.playground.FxSlow.factor = if (debuggable) intent.getFloatExtra("fxSlow", 1f).coerceIn(1f, 20f) else 1f
        val seekMs = intent.getLongExtra("seek", 0L).toDouble()
        val dump = debuggable && intent.getBooleanExtra("dump", false)
        val route = intent.getStringExtra("screen")?.takeIf { debuggable }
        // A debug route with a bad value (an unknown lesson or song, "prog:x") opens home instead of crashing the launch.
        val first: Screen = runCatching { when {
            route == "chords" -> Screen.Home(HomeTab.CHORDS,
                when (intent.getStringExtra("tab")) {
                    "dictionary" -> ChordsTab.DICTIONARY; "practice" -> ChordsTab.PRACTICE; "progressions" -> ChordsTab.PROGRESSIONS
                    "daily", "lessons" -> ChordsTab.LESSONS
                    else -> ChordsTab.PLAY
                },
                debugStart.inject,
                GameStart(GameKind.entries.firstOrNull { it.id == intent.getStringExtra("game") }, intent.getIntExtra("level", 0)),
            ).also {
                // --es pack <id>: start a Chords session on that pack, locked or not (to see its items).
                intent.getStringExtra("pack")?.let { id -> lessons.curriculum().packs.firstOrNull { p -> p.id == id }?.let { p -> lessons.startSession(p.items) } }
            }
            route == "settings" -> Screen.Settings(Screen.Home(HomeTab.YOU))
            route == "learn" -> Screen.Home(HomeTab.LEARN)
            route == "profile" || route == "you" -> Screen.Home(HomeTab.YOU)
            route == "onboarding" -> Screen.Onboarding
            route == "practice" -> Screen.Home(HomeTab.PRACTICE,
                practiceTab = PracticeTab.entries.firstOrNull { it.name.equals(intent.getStringExtra("tab"), ignoreCase = true) } ?: PracticeTab.SCALES)
            route == "ear" -> Screen.EarTraining(Screen.Home(HomeTab.LEARN), when {
                intent.getBooleanExtra("workout", false) -> EarStart(workout = true)
                else -> intent.getStringExtra("game")?.let(EarKind::byId)?.let { EarStart(it, intent.getIntExtra("level", 0)) }
            })
            route == "theory" -> Screen.Theory(Screen.Home(HomeTab.LEARN))
            route == "sight" -> Screen.SightRead(Screen.Home(HomeTab.LEARN), intent.getIntExtra("level", 1), debugStart.inject)
            route == "world" -> Screen.World(Screen.Home(HomeTab.YOU),
                WorldTab.entries.firstOrNull { it.name.equals(intent.getStringExtra("tab"), ignoreCase = true) } ?: WorldTab.WORLD)
            route == "kidsongs" -> Screen.KidSongs(Screen.Home())
            route == "kidstickers" -> Screen.KidStickers(Screen.Home(),
                StickerPage.entries.firstOrNull { it.name.equals(intent.getStringExtra("page"), ignoreCase = true) } ?: StickerPage.ALBUM)
            route == "finale" -> Screen.Finale(intent.getStringExtra("page") ?: PLAYGROUND_PAGES.first().id)
            route == "jam" || route == "pupsays" || route == "clap" ->
                Screen.Play(when (route) { "jam" -> Toy.JAM; "pupsays" -> Toy.PUP_SAYS; else -> Toy.CLAP }, Screen.Home(), intent.getStringExtra("hint"))
            // The teams' effect harnesses, each in its own demo state (never a real profile's world or progress).
            route == "worlddemo" -> return setContent { com.bigpapituna.sharpears.ui.world.WorldDemo(intent) { finish() } }
            route == "brief" -> return setContent { com.bigpapituna.sharpears.ui.brief.BriefDemo(intent) { finish() } }
            route == "skin" -> return setContent { com.bigpapituna.sharpears.ui.skin.SkinDemo(intent, sampler, midi, mic, voice, prefs) { samplerReady.value } }
            debuggable && intent.getStringExtra("lesson") != null ->
                COURSE.flatMap { it.lessons }.first { it.id == intent.getStringExtra("lesson") }.let {
                    Screen.Lesson(it, Screen.Home(HomeTab.LEARN), intent.getIntExtra("step", 0), debugStart.inject, intent.getStringExtra("midi"), intent.getIntExtra("scene", 0))
                }
            openSong == null -> Screen.Home()
            openSong == MIC_CHECK_ID -> Screen.Player(micCheck, null, debugStart, seekMs, Screen.Home())
            openSong.startsWith("prog:") -> openSong.split(":").let { (_, i, k) ->
                Screen.Player(progressionSong(PROGRESSIONS[i.toInt()], k.toInt()), null, debugStart, seekMs, Screen.Home(HomeTab.CHORDS, ChordsTab.PROGRESSIONS))
            }
            else -> repo.all().first { it.id == openSong }.let { m -> Screen.Player(repo.load(m), m, debugStart, seekMs, Screen.Home()) }
        } }.getOrElse { Log.d(TAG, "debug route failed, opening home: $it"); Screen.Home() }

        val startDaily = debuggable && intent.getBooleanExtra("daily", false)
        // First run: the onboarding questions, unless a debug route asked for a screen. A new kid skips them and lands in
        // the first Playground game (Back from it goes to the Learn path).
        val opening = when {
            newKid && first == Screen.Home() && !startDaily ->
                KidMap.next(path, prefs::lessonDone, kidStore.finales(), pace = kidStore.pace(), stars = kidStore::stars)?.let { Screen.Lesson(it, Screen.Home(HomeTab.LEARN)) } ?: Screen.Home()
            !progress.onboardingDone && first == Screen.Home() && !startDaily -> Screen.Onboarding
            else -> first
        }
        if (newKid) Log.d(TAG, "profiles: new kid ${profile.id} opens on ${(opening as? Screen.Lesson)?.lesson?.id}")
        // A recreate reopens the screen that was open (see routeOf); a route that no longer resolves opens as a launch would.
        val reopened = restoreRoute?.takeIf { fromLaunch }?.let { r ->
            runCatching { screenAt(r, prefs, profile.isKid) }.onSuccess { Log.d(TAG, "screen restored: $r") }
                .onFailure { Log.d(TAG, "screen not restored ($r): $it") }.getOrNull()
        }
        restoreRoute = null
        // A parent brief action for this person, asked while someone else was open ([briefNav]): the app opens on its screen.
        val requested = pendingOpen?.takeIf { it.first == profile.id }?.second
        pendingOpen = null
        // --es midi "<script>": a pretend keyboard plays into the first lesson step (see MidiDevices.debugPlay).
        if (debuggable && intent.getStringExtra("midi") != null) midi.debugConnect()
        intent.getStringExtra("say")?.takeIf { debuggable }?.let { line ->
            android.os.Handler(mainLooper).postDelayed({ voice.speak(line) }, 1500)
        }

        // A launch (onCreate, the picker, a debug route through onNewIntent) starts a fresh composition; a profile's kind
        // changing keeps the screen it is on.
        val opened = if (fromLaunch) ++launches else launches
        setContent { androidx.compose.runtime.key(profile.id, opened) {
            var screen by remember { mutableStateOf(requested ?: reopened ?: opening) }
            androidx.compose.runtime.SideEffect { shownRoute = routeOf(screen) }
            // A parent brief action's screen for the person open now ([briefNav]: Replay, Practise together).
            val asked = navRequest.value
            LaunchedEffect(asked) { if (asked != null) { voice.stop(); screen = asked; navRequest.value = null } }
            // Practise together with nothing chosen waits on the songs; leaving them without picking one drops it.
            LaunchedEffect(screen) {
                val sc = screen
                val onSongs = sc is Screen.KidSongs || sc is Screen.Player || (sc is Screen.Home && sc.tab == HomeTab.SONGS && !profile.isKid)
                if (!onSongs && duetNext.value != null) { duetNext.value = null; Log.d(TAG, "brief: the duet's song pick was left") }
            }
            KeepScreenOnWhile(mic, stage = keepsScreenOn(screen))
            val current by profiles.currentFlow.collectAsState()
            val roster by profiles.rosterFlow.collectAsState()
            // Every screen can report XP events through LocalProgress, and read who is playing through LocalProfile; the
            // pup's world (and the outfit every base-pose pup wears) through LocalWorld.
            androidx.compose.runtime.CompositionLocalProvider(LocalProgress provides progress, LocalProfile provides current,
                LocalProfiles provides profiles, LocalParentGate provides gate, LocalWorld provides world, LocalPupOutfit provides world.state.outfit,
                com.bigpapituna.sharpears.ui.brief.LocalBriefNav provides briefNav) {
            SharpEarsTheme(dark = playerShown(screen) || isSystemInDarkTheme()) {
                // Every touch anywhere wakes the pups (their idle clock restarts); it takes nothing from the screens.
                Surface(Modifier.fillMaxSize().paper().wakesPup(), color = androidx.compose.ui.graphics.Color.Transparent,
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
                        // Practise together (the parent brief) with nothing chosen: the song picked now is the duet.
                        duetNext.value?.takeIf { it.whoId == profile.id }?.let { d ->
                            duetNext.value = null
                            screen = Screen.Player(song, m, briefStart(profile, prefs, duet = true, partnerId = d.partnerId), 0.0, back)
                            return
                        }
                        // A kid starts a two-hand song on the tune (the right hand), as the grown-ups' hands tip advises.
                        val hands = if (profile.isKid && song.notes.any { it.hand == Hand.RIGHT }) Practice.RIGHT else Practice.BOTH
                        screen = Screen.Player(song, m, PlayerStart(Mode.WAIT, hands, mic = prefs.micDefault && mic.hasPermission()),
                            if (p.completed) 0.0 else p.lastPositionMs.toDouble(), back)
                    }
                    // "Practice today": due chords, the next lesson, then the Continue song's worst bar (or the song).
                    fun continueMeta() = songs.firstOrNull { it.id == prefs.lastSongId && repo.available(it) }
                        ?: songs.firstOrNull { it.difficulty == "easy" && repo.available(it) }
                    // A kid who reached today's goal hears goodnight once a run or a part is over (never mid-run; lessons
                    // say it themselves): the pup yawns, today's paw lands, songs and the Jam stay open.
                    var goodbye by remember { mutableStateOf(false) }
                    // A kid who finished Beginner is offered grown-up mode once, on their Learn path.
                    var grownUpOffered by remember { mutableStateOf(bedtime.grownUpOffered) }
                    fun maybeGoodnight(after: String): Boolean {
                        val today = java.time.LocalDate.now()
                        val sec = progress.secondsToday(today)
                        // The goal itself only earns a cheer; bedtime is a parental control (off by default).
                        if (!bedtime.bedtime && com.bigpapituna.sharpears.engine.skin.Bedtime.sayWellDone(profile.isKid, sec, progress.goalMinutes, bedtime.wellDoneOn, today)) {
                            bedtime.wellDoneOn = today
                            voice.speak(com.bigpapituna.sharpears.engine.SessionWords.wellDone(profile.name))
                            Log.d(TAG, "bedtime: goal cheered after $after")
                        }
                        if (!com.bigpapituna.sharpears.engine.skin.Bedtime.sayGoodnight(profile.isKid, sec, progress.goalMinutes, bedtime.goodnightOn, today, bedtime.bedtime)) return false
                        bedtime.goodnightOn = today
                        goodbye = true
                        Log.d(TAG, "bedtime: goodnight after $after")
                        return true
                    }
                    // A kid finished a Playground page's last lesson (2.2): its finale plays when they leave the lesson.
                    var pendingFinale by remember { mutableStateOf<String?>(null) }
                    // A course lesson finished: progress, today's quest, its stars on the stone, and the pup's world (a sticker from
                    // the lesson's page; the egg laid or hatched).
                    fun lessonDone(l: CourseLesson, stars: Int): Reward {
                        prefs.markLessonDone(l.id); progress.record(ProgressEvent.LessonComplete(l.id))
                        kidStore.lessonPlayed(l.id)          // a lesson pinned as the next stone (the parent brief) has been played
                        quest(QuestEvent.LessonDone(l.id))
                        kidStore.recordStars(l.id, stars)
                        if (profile.isKid) Finale.trigger(l.id, kidStore.finales())?.let { page ->
                            pendingFinale = page.id
                            Log.d(TAG, "kid: ${l.id} ends ${page.id}, its finale is next")
                        }
                        return world.lessonComplete(l.id, KidPages.dropPlace(l.id))
                    }
                    // The next lesson to suggest: a kid's next stone on the map, a grown-up's Learn path; a lesson a grown-up pinned
                    // from the parent brief comes first while its day lasts (engine/kid/Pace.kt).
                    fun nextLesson(): CourseLesson? = if (profile.isKid) KidMap.next(path, prefs::lessonDone, kidStore.finales(), pace = kidStore.pace(), stars = kidStore::stars)
                        else kidStore.pace().pin?.let(path::lesson) ?: path.next { prefs.lessonDone(it) }
                    // Where Songs is: the kid's own list for a kid, the Songs tab for a grown-up.
                    fun songsScreen(back: Screen = Screen.Home()): Screen = if (profile.isKid) Screen.KidSongs(back) else Screen.Home(HomeTab.SONGS)
                    fun todayPlan(): SessionPlan {
                        val queue = lessons.curriculum().queue(lessons.mastery(), System.currentTimeMillis())
                        // After a kid's goodnight the lessons sleep until tomorrow: no lesson part (songs stay open).
                        val asleep = profile.isKid && bedtime.goodnightOn == java.time.LocalDate.now()
                        val next = if (asleep) null else nextLesson()
                        // A kid's session is short (KIDS-PLAN 3.9): the next game or lesson and a whole song, no chord
                        // review and no bar on a loop.
                        val song = continueMeta()?.let { m ->
                            val minutes = runCatching { repo.load(m).durationMs / 60_000.0 }.getOrDefault(3.0)
                            SessionSong(m.id, m.title, minutes, if (profile.isKid) null else prefs.troubleBars(m.id).firstOrNull())
                        }
                        val chords = if (profile.isKid) emptyList() else queue.filterIsInstance<LessonItem.ChordItem>().map { it.id }
                        return planSession(chords, next?.let { it.id to it.title }, song)
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
                        voice.speak(if (profile.isKid) com.bigpapituna.sharpears.engine.SessionWords.kidSessionDone(profile.name)
                            else "Done for today: " + summary.removePrefix("Done today: ").replace(" min.", " minutes."))
                        dailyDone = Triple(done.size, maxOf(1L, (secs + 30) / 60), progress.streak().streak)
                        screen = Screen.Home()
                    }
                    fun advanceDaily(s: Screen.Daily, completed: Boolean, as_: SessionPart? = null) {
                        val part = as_ ?: s.plan.parts[s.index]
                        val done = if (completed) s.done + part else s.done
                        Log.d(TAG, "daily: part ${s.index + 1} ${if (completed) "done" else "left"}: ${part.done}")
                        // A kid's goal reached with this part: the session ends on goodnight (a lesson says it itself).
                        if (completed && part !is SessionPart.Lesson && maybeGoodnight("a Practice today part")) finishDaily(s, done)
                        else if (s.index + 1 >= s.plan.parts.size) finishDaily(s, done)
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
                            is EarEvent.Round -> {
                                progress.record(ProgressEvent.EarRound(e.clean))
                                Briefs.log { BriefEvent.EarRound(it, e.kind.id, e.clean, e.level) }
                                if (e.kind == EarKind.MELODY && e.clean) quest(QuestEvent.PupCopied(0))     // copying the pup, by ear
                            }
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
                    // A screen on its way out is off screen for onScreen(): its mic stops before the next screen's starts.
                    androidx.compose.runtime.CompositionLocalProvider(com.bigpapituna.sharpears.ui.LocalLeaving provides (s::class != screen::class)) {
                    SharpEarsTheme(dark = playerShown(s) || systemDark) {
                    when (s) {
                        is Screen.Player -> {
                            // A run that reached its end (a song, a progression) may be where a kid's goal was reached.
                            var ranToEnd by remember { mutableStateOf(false) }
                            PlayerScreen(s.song, s.meta, sampler, midi, mic, voice, prefs, samplerReady.value,
                                s.startMs, dump, s.start, onBack = { screen = s.back; if (ranToEnd) maybeGoodnight("a song") },
                                onFinished = { ranToEnd = true },
                                chordTrack = s.meta?.let { lessons.songChords.value[it.id] })
                        }
                        is Screen.Lesson -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            val l = s.lesson
                            // A kid (2.2): "one more" is the next stone on the map; a page's last lesson, finished the first time,
                            // leads to the page's finale instead (and today's goodbye waits until after it).
                            val finaleAhead = profile.isKid && remember(l.id) { Finale.trigger(l.id, kidStore.finales()) != null }
                            val next = if (finaleAhead) null else if (profile.isKid) KidMap.next(path, { it == l.id || prefs.lessonDone(it) }, kidStore.finales(),
                                pace = kidStore.pace().let { if (it.pin == l.id) it.copy(pin = null) else it }, stars = kidStore::stars)
                                else path.after(l) { prefs.lessonDone(it) }
                            val leave = {
                                val f = pendingFinale
                                if (f != null) { pendingFinale = null; voice.stop(); screen = Screen.Finale(f) } else screen = s.back
                            }
                            BackHandler { voice.stop(); leave() }
                            androidx.compose.runtime.key(l.id, navCount.intValue) { LessonScreen(l, s.step, LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), s.inject,
                                midiScript = s.midiScript, startScene = s.scene,
                                onStep = { if (it != s.step) screen = s.copy(step = it, inject = null, midiScript = null, scene = 0) },
                                onSong = l.songId?.let { id -> { songs.firstOrNull { it.id == id && repo.available(it) }?.let { openSong(it, back = s) } } },
                                onDone = { stars -> lessonDone(l, stars) },
                                onNext = if (finaleAhead) leave else next?.let { n -> { screen = Screen.Lesson(n, s.back) } },
                                onBack = leave,
                                onSongs = { voice.stop(); screen = songsScreen() },
                                onJam = { voice.stop(); screen = Screen.Play(Toy.JAM, Screen.Home()) },
                                next = next,
                                finishLabel = if (finaleAhead) "Hooray!" else null,
                                holdGoodnight = finaleAhead) }
                        }
                        is Screen.Daily -> {
                            val part = s.plan.parts[s.index]
                            if (s.between) Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                BackHandler { finishDaily(s, s.done) }
                                Interstitial(part.next, voice, onGo = { screen = s.copy(between = false) },
                                    onSkip = { voice.stop(); advanceDaily(s, completed = false) },
                                    onEnd = { voice.stop(); finishDaily(s, s.done) },
                                    parts = s.plan.parts, index = s.index, done = s.done, kid = profile.isKid)
                            } else when (part) {
                                is SessionPart.Chords -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    // Back is "Skip the rest": the chords answered count, and the session ends here (left open,
                                    // it took over Chords > Daily afterwards).
                                    fun endChords(n: Int) {
                                        part.itemIds.take(n).forEach { quest(QuestEvent.ChordReviewed(it)) }
                                        advanceDaily(s, completed = n > 0, part.copy(itemIds = part.itemIds.take(n)))
                                    }
                                    BackHandler { val n = lessons.sessionIndex; lessons.endSession(); endChords(n) }
                                    // Chord practice does not run the player, so its time is counted here.
                                    androidx.compose.runtime.DisposableEffect(s.index) {
                                        val t0 = com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs()
                                        onDispose {
                                            val secs = ((com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs() - t0) / 1000).toInt()
                                            prefs.addPractice(java.time.LocalDate.now(), secs)
                                            if (secs > 0) progress.record(ProgressEvent.Practice(java.time.LocalDateTime.now(), secs))
                                        }
                                    }
                                    QuietWrongKeys(mic)
                                    androidx.compose.foundation.layout.Column {
                                        Text("Practice today: chords", style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(start = 20.dp, top = 20.dp))
                                        DailyChords(lessons, sampler, midi, mic, prefs.micDefault, part.itemIds, inject = debugStart.inject.takeIf { startDaily }, sound = { prefs.sound },
                                            onEnd = ::endChords)
                                    }
                                }
                                is SessionPart.Lesson -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    val l = COURSE.flatMap { it.lessons }.first { it.id == part.lessonId }
                                    BackHandler { voice.stop(); advanceDaily(s, prefs.lessonDone(l.id)) }
                                    androidx.compose.runtime.key(l.id) { LessonScreen(l, 0, LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), null,
                                        onStep = {}, onSong = null,
                                        onDone = { stars -> lessonDone(l, stars) },
                                        onNext = null,
                                        onBack = { voice.stop(); advanceDaily(s, prefs.lessonDone(l.id)) },
                                        onSongs = { voice.stop(); finishDaily(s, s.done + part); screen = songsScreen() },
                                        onJam = { voice.stop(); finishDaily(s, s.done + part); screen = Screen.Play(Toy.JAM, Screen.Home()) }) }
                                }
                                is SessionPart.Loop, is SessionPart.Song -> {
                                    val id = if (part is SessionPart.Loop) part.songId else (part as SessionPart.Song).songId
                                    val m = songs.first { it.id == id }
                                    val song = remember(id) { repo.load(m) }
                                    // Played here, it is the Continue song (Songs said "Start here" after a whole session of it).
                                    LaunchedEffect(id) { prefs.lastSongId = id }
                                    val partStart = remember(s.index) { System.currentTimeMillis() }
                                    // A song part played to its end shows its recap first (stars, XP, the sticker it dropped);
                                    // closing the recap, or Back, goes on. It went on at once before, so none of it was seen.
                                    var ranToEnd by remember(s.index) { mutableStateOf(false) }
                                    androidx.compose.runtime.key(s.index) {
                                        PlayerScreen(song, m, sampler, midi, mic, voice, prefs, samplerReady.value,
                                            if (part is SessionPart.Song) prefs.progress(id).let { if (it.completed) 0.0 else it.lastPositionMs.toDouble() } else 0.0,
                                            dump, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission(),
                                                loopBar = (part as? SessionPart.Loop)?.bar, timeLimitMs = if (part is SessionPart.Loop) 120_000L else null),
                                            // Leaving the song after a minute or more of it counts; leaving at once does not.
                                            onBack = { advanceDaily(s, completed = ranToEnd || System.currentTimeMillis() - partStart >= 60_000) },
                                            onFinished = { if (part is SessionPart.Song) ranToEnd = true },
                                            onRecapClosed = { if (ranToEnd) advanceDaily(s, completed = true) },
                                            chordTrack = lessons.songChords.value[id],
                                            onTimeUp = {
                                                if (part is SessionPart.Loop) quest(QuestEvent.LoopDone(part.songId, part.bar))
                                                advanceDaily(s, completed = true)
                                            })
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
                            QuietWrongKeys(mic)
                            // The hub handles Back itself: a game goes back to the hub, the hub to Learn.
                            EarTrainingScreen(sampler, midi, mic, voice, prefs.micDefault, onBack = { screen = s.back },
                                onReport = ::earEvent, sound = { prefs.sound }, start = s.start)
                        }
                        Screen.Onboarding -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            OnboardingScreen(progress, prefs, onReminderChanged = { com.bigpapituna.sharpears.reminder.Reminder.schedule(this@MainActivity) },
                                askReminder = profile.isOwner) { lessonId ->
                                // The suggested first lesson opens straight away; Back from it lands on Learn.
                                screen = lessonId?.let { id -> COURSE.flatMap { it.lessons }.firstOrNull { it.id == id } }
                                    ?.let { Screen.Lesson(it, Screen.Home(HomeTab.LEARN)) } ?: Screen.Home()
                            }
                        }
                        is Screen.Settings -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            SettingsScreen(prefs, songs, packageManager.getPackageInfo(packageName, 0).versionName ?: "", voice = voice,
                                onReminder = { com.bigpapituna.sharpears.reminder.Reminder.schedule(this@MainActivity) },
                                onMicSetup = { gate.guardForKids { screen = Screen.MicSetup(s) } },
                                onPrivacy = { screen = Screen.Privacy(s) }, onLicences = { screen = Screen.Licences(s) },
                                onBack = { screen = s.back },
                                onResetProgress = { earStore.reset(); practiceStore.clear(); theory.reset(); progress.clearPracticeLog(); Log.d(TAG, "progress: reset (prefs, ear, practice, theory, practice log; XP kept)") },
                                family = { FamilySettings(profiles) }, showReminder = current.isOwner)
                        }
                        is Screen.Privacy -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            PrivacyScreen(mic.canRecord, onMicSetup = { gate.guardForKids { screen = Screen.MicSetup(s) } }, onBack = { screen = s.back })
                        }
                        is Screen.Theory -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            com.bigpapituna.sharpears.ui.theory.TheoryScreen(sampler, voice, onBack = { voice.stop(); screen = s.back },
                                store = theory, soundOn = prefs.sound,
                                // Time in a lesson is practice; recorded first, so the lesson's XP lands after the minutes.
                                onPractice = { secs ->
                                    prefs.addPractice(java.time.LocalDate.now(), secs)
                                    if (secs > 0) progress.record(ProgressEvent.Practice(java.time.LocalDateTime.now(), secs))
                                },
                                onLessonDone = { id, _ -> progress.record(ProgressEvent.TheoryLesson(id)) },
                                onUnitComplete = { unit, _, _ -> progress.record(ProgressEvent.TheoryUnit(unit)) })
                        }
                        is Screen.Licences -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            LicencesScreen(songs, onBack = { screen = s.back })
                        }
                        is Screen.World -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            com.bigpapituna.sharpears.ui.world.WorldScreen(world, songs, onBack = { screen = s.back }, start = s.tab,
                                onSong = { openSong(it, back = s) })
                        }
                        is Screen.Play -> Box(Modifier.fillMaxSize()) {
                            BackHandler { voice.stop(); screen = s.back }
                            TimedOnScreen(s.toy) { ms -> Briefs.log { BriefEvent.ToyOpened(it, toyKind(s.toy), ms) } }
                            val frames = remember { mic.frameTap() }
                            when (s.toy) {
                                Toy.JAM -> {
                                    com.bigpapituna.sharpears.ui.play.JamScreen(mic, midi, frames, onExit = { screen = s.back }, hint = s.hint, voice = voice)
                                    // Debug: --es screen jam --es midi "3000:60/90,3010:64/90,..." plays a pretend keyboard into the Jam.
                                    val script = remember { intent.getStringExtra("midi")?.takeIf { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 } }
                                    if (script != null) LaunchedEffect(script) { midi.debugPlay(script) }
                                }
                                Toy.PUP_SAYS -> com.bigpapituna.sharpears.ui.play.PupSaysScreen(mic, midi, sampler, voice, sound = { prefs.sound },
                                    onExit = { voice.stop(); screen = s.back },
                                    // Each player's best of the day goes into their own world (the crown of the day is read from them).
                                    onFinished = { results -> results.forEach { r ->
                                        val result = com.bigpapituna.sharpears.engine.world.PupSaysResult(r.profileId, r.best)
                                        if (r.profileId == world.profileId) world.pupSays(result) else WorldStore(this@MainActivity, r.profileId).pupSays(result)
                                    } })
                                Toy.CLAP -> {
                                    val clicks = remember { runCatching { com.bigpapituna.sharpears.audio.Clicks(this@MainActivity) }.getOrNull() }
                                    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { clicks?.release() } }
                                    com.bigpapituna.sharpears.ui.play.ClapAlongScreen(mic, frames, clicks, voice, sound = { prefs.sound }, onExit = { voice.stop(); screen = s.back })
                                }
                            }
                        }
                        is Screen.MicSetup -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            MicSetupScreen(mic, midi, debuggable,
                                onMicCheck = { screen = Screen.Player(micCheck, null, PlayerStart(Mode.WAIT, Practice.BOTH, mic = true), 0.0, s) },
                                onBack = { screen = s.back }, guardToken = gate::guard)
                        }
                        is Screen.Home -> if (current.isKid) {
                            // A kid's home (2.2) is the pup's world: one screen, no tabs (ui/kid/KidHome.kt).
                            val home = s.copy(kidFrom = null)
                            val pv = prefs.version.intValue
                            val kv = kidStore.version.intValue
                            val model = remember(pv, kv) { KidMap.build(path, prefs::lessonDone, kidStore::stars, kidStore.finales(), pace = kidStore.pace()) }
                            val today = remember { java.time.LocalDate.now() }
                            val asleep = !com.bigpapituna.sharpears.engine.skin.Bedtime.lessonOpen(true, progress.secondsToday(today), progress.goalMinutes,
                                bedtime.oneMoreOn, today, bedtime.bedtime)
                            val quest = remember(pv, kv) { playStore.quest(kid = true, plan = null) }
                            var corner by remember { mutableStateOf(false) }
                            // Back on the kid's map, a grown-up is done (Settings, a share, one more lesson): the gate shuts, so
                            // the whistle cannot hand the kid Settings or another player for the rest of its ten minutes.
                            LaunchedEffect(Unit) { if (gate.isOpen) { gate.close(); Log.d(TAG, "gate: closed, back on the kid's map") } }
                            // After the last page's finale the pup says it once: the big kids' road is open.
                            val roadLine = remember(kv) { model.roadOpen && !kidStore.roadSaid && kidStore.finales().containsAll(PLAYGROUND_PAGES.map { it.id }) }
                            // A finale left waiting (its lesson was left another way, or the app was closed before): it plays now.
                            LaunchedEffect(pendingFinale) {
                                (pendingFinale ?: Finale.owed(prefs::lessonDone, kidStore.finales())?.id)?.let { pendingFinale = null; screen = Screen.Finale(it) }
                            }
                            com.bigpapituna.sharpears.ui.kid.KidHome(model, world.state, current.name, current.avatar, asleep, quest,
                                onStone = { st -> path.lesson(st.lessonId)?.let { voice.stop(); screen = Screen.Lesson(it, home) } },
                                onToy = { t -> screen = Screen.Play(t, home) },
                                onSongs = { screen = Screen.KidSongs(home) },
                                onStickers = { screen = Screen.KidStickers(home) },
                                onQuestTask = { t ->
                                    Log.d(TAG, "quest: open ${t.opens} for ${t.kind} (kid home)")
                                    screen = when (t.opens) {
                                        "jam" -> Screen.Play(Toy.JAM, home, hint = "Can you find all the Cs?")
                                        "pupsays" -> Screen.Play(Toy.PUP_SAYS, home)
                                        "songs" -> Screen.KidSongs(home)
                                        else -> home
                                    }
                                },
                                onGrownUps = { gate.guard { corner = true } },
                                say = { voice.speak(it, interrupt = true) },
                                from = s.kidFrom, roadLine = roadLine, onRoadSaid = { kidStore.roadSaid = true },
                                // The garden's jar and egg, as on the world screen: a full jar is tipped for the pup; the pup says
                                // what the egg and an unfilled jar are about.
                                onJar = {
                                    if (world.tip()) { WorldSounds.munch(); voice.speak(com.bigpapituna.sharpears.engine.kid.KidLines.YUM, interrupt = true) }
                                    else voice.speak(com.bigpapituna.sharpears.ui.world.jarLine(world.state.jar, kid = true, world.state.treats), interrupt = true)
                                },
                                onEgg = { voice.speak(com.bigpapituna.sharpears.engine.kid.KidLines.EGG, interrupt = true) },
                                grownUps = if (!corner) null else com.bigpapituna.sharpears.ui.kid.GrownUpCorner(roster.profiles, current.id,
                                    onSettings = { corner = false; screen = Screen.Settings(home) },
                                    onMicSetup = { corner = false; screen = Screen.MicSetup(home) },
                                    onSwitch = { id -> corner = false; profiles.switchTo(id) }),
                                onGrownUpsClosed = { corner = false; gate.close(); Log.d(TAG, "gate: closed with the grown-ups' corner") },
                                // Grown-up mode, offered once after Beginner (it was on the kid's Learn path before 2.2).
                                grownUpOffer = if (!CoursePath.offerGrownUp(true, grownUpOffered, { pv >= 0 && prefs.lessonDone(it) })) null else { {
                                    fun answered() { bedtime.grownUpOffered = true; grownUpOffered = true }
                                    com.bigpapituna.sharpears.ui.family.GrownUpOffer(current.name,
                                        onSwitch = { gate.guard {
                                            answered()
                                            Log.d(TAG, "profiles: ${current.id} switched to grown-up mode after Beginner")
                                            profiles.setKind(current.id, com.bigpapituna.sharpears.engine.family.ProfileKind.ADULT)
                                        } },
                                        // A grown-up's answer too: a kid's tap on "Not yet" put the one-time offer away for good.
                                        onLater = { gate.guard { answered(); Log.d(TAG, "profiles: grown-up mode offered, not yet") } })
                                } })
                        } else {
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
                                                onMic = { gate.guardForKids { screen = Screen.MicSetup(s) } },
                                                onToday = ::startDaily,
                                                onRename = { m, title -> repo.rename(m, title); Log.d(TAG, "song ${m.id} renamed"); libraryVersion++ },
                                                onDelete = { m ->
                                                    repo.delete(m)
                                                    if (prefs.lastSongId == m.id) prefs.lastSongId = null
                                                    Log.d(TAG, "song ${m.id} deleted"); libraryVersion++
                                                },
                                            ),
                                            // The week in minutes, for grown-ups (a kid's home has no numbers).
                                            practice = remember(pv, progress.version.intValue, current.isKid) { if (current.isKid) null else PracticeStats.summary(prefs.practice(), java.time.LocalDate.now(), progress.streak().streak) },
                                            today = remember(pv, lv, lessons.songPacks.value) {
                                                val plan = todayPlan()
                                                // Debug --ez emptyToday true: the panel with nothing due (for screenshots).
                                                val kid = current.isKid
                                                TodayPanel(if (kid) com.bigpapituna.sharpears.engine.SessionWords.KID_TODAY else plan.aboutLabel,
                                                    if (debuggable && intent.getBooleanExtra("emptyToday", false)) emptyList()
                                                    else plan.parts.map { if (kid) com.bigpapituna.sharpears.engine.SessionWords.kidRow(it) else it.row },
                                                    prefs.dailySummary(java.time.LocalDate.now())?.let { if (kid) com.bigpapituna.sharpears.engine.SessionWords.KID_DONE_TODAY else it })
                                            },
                                            goal = remember(pv, progress.version.intValue, current.isKid) {
                                                val st = progress.streak()
                                                // A kid's goal line is how the pup feels, never minutes.
                                                GoalState(if (current.isKid) com.bigpapituna.sharpears.engine.SessionWords.kidGoal(progress.secondsToday(), progress.goalMinutes) else progress.goalLine(),
                                                    DailyGoal.met(progress.secondsToday(), progress.goalMinutes), st.streak, st.atRisk)
                                            },
                                            // Everyone's week, when there is more than one of us; tapping someone opens the app as them. Each
                                            // shows their latest sticker; whoever copied the pup furthest today wears the crown.
                                            family = if (!roster.needsPicker) null else {
                                                {
                                                    val wall = remember(roster, pv, progress.version.intValue) {
                                                        familyWallEntries(this@MainActivity, roster, latestSticker = { id -> WorldStore.latestSticker(this@MainActivity, id) })
                                                    }
                                                    val crowned = remember(roster, pv) { WorldStore.crownOfTheDay(this@MainActivity, roster.profiles.map { it.id }) }
                                                    FamilyWall(wall, onSwitch = profiles::switchTo, crowned = crowned)
                                                }
                                            },
                                            // Pup's quest under Practice today: a tap goes where the task is done.
                                            quest = {
                                                com.bigpapituna.sharpears.ui.play.QuestCard(remember(pv, lv) { todayPlan() }, onTask = { t ->
                                                    Log.d(TAG, "quest: open ${t.opens} for ${t.kind}")
                                                    val song = t.ref?.let { id -> songs.firstOrNull { it.id == id && repo.available(it) } }
                                                    if (t.opens == "songs" && song != null) openSong(song)     // "Play Minuet in G": straight in
                                                    else screen = when (t.opens) {
                                                        "jam" -> Screen.Play(Toy.JAM, s, hint = "Can you find all the Cs?")
                                                        "pupsays" -> Screen.Play(Toy.PUP_SAYS, s)
                                                        "chords" -> Screen.Home(HomeTab.CHORDS, ChordsTab.LESSONS)
                                                        "learn" -> Screen.Home(HomeTab.LEARN)
                                                        else -> s
                                                    }
                                                })
                                            },
                                            play = { com.bigpapituna.sharpears.ui.play.PlayRow(onToy = { t -> screen = Screen.Play(t, s) }) },
                                            kid = current.isKid)
                                    }
                                    HomeTab.LEARN -> {
                                        val pv = prefs.version.intValue
                                        val kv = kidStore.version.intValue
                                        LearnScreen(done = { pv >= 0 && prefs.lessonDone(it) }, onOpen = { screen = Screen.Lesson(it, s) },
                                            pinned = remember(pv, kv) { kidStore.pace().pin?.let(path::lesson) },
                                            onSightRead = { screen = Screen.SightRead(s) },
                                            sightBest = remember(pv) { (prefs.sightBest(1) ?: prefs.sightBest(2))?.label },
                                            onEar = { screen = Screen.EarTraining(s) },
                                            theory = { m -> com.bigpapituna.sharpears.ui.theory.TheoryCard(theory, onOpen = { screen = Screen.Theory(s) }, modifier = m) },
                                            earStreak = remember { earStore.workoutStreak() },
                                            path = path,
                                            grownUp = if (!CoursePath.offerGrownUp(current.isKid, grownUpOffered, { pv >= 0 && prefs.lessonDone(it) })) null else { {
                                                fun answered() { bedtime.grownUpOffered = true; grownUpOffered = true }
                                                com.bigpapituna.sharpears.ui.family.GrownUpOffer(current.name,
                                                    onSwitch = { gate.guard {
                                                        answered()
                                                        Log.d(TAG, "profiles: ${current.id} switched to grown-up mode after Beginner")
                                                        profiles.setKind(current.id, com.bigpapituna.sharpears.engine.family.ProfileKind.ADULT)
                                                    } },
                                                    onLater = { answered(); Log.d(TAG, "profiles: grown-up mode offered, not yet") })
                                            } })
                                    }
                                    HomeTab.PRACTICE -> PracticeScreen(sampler, mic, practiceStore, sound = { prefs.sound },
                                        // A scale or warm-up plays like a lesson step: the full player in wait mode at the exercise's
                                        // tempo; Back returns to this tab (the tab picked is kept in the Home screen).
                                        onOpenExercise = { ex, title ->
                                            screen = Screen.Player(ex.toSong("practice-" + title.hashCode(), title), null,
                                                PlayerStart(Mode.WAIT, ex.practice, mic = prefs.micDefault && mic.hasPermission(), beatLines = ex.beatLines), 0.0, s)
                                        },
                                        startTab = s.practiceTab,
                                        onEvent = { e -> progress.record(ProgressEvent.FeatureUsed(if (e.kind == PracticeKind.WARMUP) "warmups" else "scales")) },
                                        onTab = { t ->
                                            screen = s.copy(practiceTab = t)
                                            // The metronome and tuner have no "start" of their own: opening them is using them.
                                            when (t) { PracticeTab.METRONOME -> "metronome"; PracticeTab.TUNER -> "tuner"; else -> null }
                                                ?.let { progress.record(ProgressEvent.FeatureUsed(it)) }
                                        })
                                    HomeTab.CHORDS -> {
                                        QuietWrongKeys(mic)
                                        ChordsScreen(sampler, midi, mic, voice, prefs, lessons, prefs.micDefault, s.chordsTab, s.inject, s.game,
                                            onPlayProgression = { p, key ->
                                                screen = Screen.Player(progressionSong(p, key), null, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission()),
                                                    0.0, s.copy(chordsTab = if (lessons.session.isNotEmpty()) ChordsTab.LESSONS else ChordsTab.PROGRESSIONS, inject = null, game = GameStart()))
                                            })
                                    }
                                    HomeTab.YOU -> ProfileScreen(progress, prefs, songs, onSettings = { gate.guardForKids { screen = Screen.Settings(s) } },
                                        kid = current.isKid,
                                        // The pup's world in one card: the garden, and a way into the album, dress-up and the records.
                                        world = { com.bigpapituna.sharpears.ui.world.WorldCard(world, onOpen = { tab -> screen = Screen.World(s, tab) }) },
                                        paws = { com.bigpapituna.sharpears.ui.skin.PawCalendarCard(kid = current.isKid) })
                                }
                            }
                        }
                        is Screen.KidSongs -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            val kv = kidStore.version.intValue
                            com.bigpapituna.sharpears.ui.kid.KidSongsScreen(songs, repo, onSong = { openSong(it, back = s) }, onBack = { screen = s.back },
                                shelf = remember(kv) { kidStore.shelf },
                                duetWith = duetNext.value?.takeIf { it.whoId == profile.id }?.partnerId?.let { id -> roster.profiles.firstOrNull { it.id == id }?.name },
                                say = { voice.speak(it, interrupt = true) })
                        }
                        is Screen.KidStickers -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            com.bigpapituna.sharpears.ui.kid.KidStickersScreen(world, songs, onBack = { screen = s.back }, start = s.page,
                                onSong = { openSong(it, back = s) })
                        }
                        is Screen.Finale -> {
                            val page = PLAYGROUND_PAGES.firstOrNull { it.id == s.pageId }
                            if (page == null) LaunchedEffect(Unit) { screen = Screen.Home() }
                            else {
                                // Played once: the path walks on past the page from now on (engine/kid/KidMap.next).
                                LaunchedEffect(page.id) { kidStore.finalePlayed(page.id) }
                                com.bigpapituna.sharpears.ui.kid.FinaleScreen(page, current.name, world.state.album.page(KidPages.worldPlace(page)),
                                    com.bigpapituna.sharpears.engine.kid.LessonStars.ofPage(page.lessonIds, kidStore::stars), Finale.after(page),
                                    say = { voice.speak(it) }, sound = prefs.sound,
                                    onDone = { voice.stop(); screen = Screen.Home(kidFrom = page.id); maybeGoodnight("the ${page.id} finale") })
                            }
                        }
                    }
                    }
                    }
                    }
                    if (goodbye) SharpEarsTheme(dark = isSystemInDarkTheme()) {
                        com.bigpapituna.sharpears.ui.skin.Goodbye(current.name,
                            remember { com.bigpapituna.sharpears.engine.family.PawWeek.of(progress.practiceLog(), java.time.LocalDate.now()) },
                            onSongs = { voice.stop(); goodbye = false; dailyDone = null; screen = songsScreen() },
                            onClose = { voice.stop(); goodbye = false; dailyDone = null },
                            onJam = { voice.stop(); goodbye = false; dailyDone = null; screen = Screen.Play(Toy.JAM, Screen.Home()) },
                            say = { voice.speak(it, interrupt = true) })
                    }
                    if (!goodbye) dailyDone?.let { (parts, min, streak) ->
                        // A kid's session ends on the pup, not on numbers.
                        if (current.isKid) com.bigpapituna.sharpears.ui.Celebration("Done for today!",
                            com.bigpapituna.sharpears.engine.SessionWords.kidSessionDone(current.name), emptyList(),
                            primary = "Continue", onPrimary = { dailyDone = null },
                            pose = com.bigpapituna.sharpears.ui.Pose.LEVEL_UP, sampler = sampler.takeIf { prefs.sound })
                        else com.bigpapituna.sharpears.ui.Celebration("Done for today!",
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
                    // A Celebration on screen holds the level-up back until it closes: celebration first, then the level.
                    // A kid's session ends on the sticker, not on XP (KIDS-PLAN 3.4): XP still adds up underneath for grown-up
                    // mode later, but no level-up screen full of numbers.
                    if (current.isKid) LaunchedEffect(progress.levelUp.value) { if (progress.levelUp.value != null) progress.consumeLevelUp() }
                    // Nor achievement banners ("One week streak", "100 minutes"): they unlock underneath, unseen by a kid.
                    if (current.isKid) LaunchedEffect(progress.toasts.size) { progress.toasts.clear() }
                    if (!isStage(screen) && !current.isKid) SharpEarsTheme(dark = isSystemInDarkTheme()) {
                        ProgressOverlays(progress, holdLevelUp = current.isKid || com.bigpapituna.sharpears.ui.celebrationsShowing.intValue > 0)
                    }
                    // The parent gate, over everything while an action waits for a grown-up.
                    SharpEarsTheme(dark = isSystemInDarkTheme()) { ParentGateHost(gate) }
                }
            }
            }
        } }
    }

    /**
     * Debug builds (2.2): --es kidseed <page>:<n> marks a page's first n lessons done with stars (3, 2, 3, 1, ...) and
     * forgets its finale; road marks every page walked and its finale played; reset forgets the Playground's lessons,
     * stars and finales. --ez sleepy true reaches today's goal and says goodnight (the map shows the pup asleep);
     * --ez awake true lets a grown-up's one more lesson through today (the map awake whatever was played).
     */
    private fun kidDebugSeed(intent: android.content.Intent, prefs: Prefs, kid: KidStore, progress: ProgressStore,
                             bedtime: com.bigpapituna.sharpears.ui.skin.BedtimeStore) {
        val stars = intArrayOf(3, 2, 3, 1, 3, 2, 2, 3, 1, 3, 2, 3)
        intent.getStringExtra("kidseed")?.let { spec ->
            val all = PLAYGROUND_PAGES.flatMap { it.lessonIds }
            when (spec) {
                "reset" -> { all.forEach(prefs::forgetLessonDone); kid.debugForget() }
                "road" -> PLAYGROUND_PAGES.forEach { p ->
                    p.lessonIds.forEachIndexed { i, id -> prefs.markLessonDone(id); kid.recordStars(id, stars[i % stars.size]) }
                    kid.finalePlayed(p.id)
                }
                else -> {
                    val page = PLAYGROUND_PAGES.firstOrNull { it.id == spec.substringBefore(":") } ?: PLAYGROUND_PAGES.first()
                    val n = spec.substringAfter(":", "0").toIntOrNull() ?: 0
                    page.lessonIds.forEach(prefs::forgetLessonDone)
                    kid.debugForget(lessonIds = page.lessonIds, pageIds = listOf(page.id))
                    page.lessonIds.take(n).forEachIndexed { i, id -> prefs.markLessonDone(id); kid.recordStars(id, stars[i % stars.size]) }
                }
            }
            Log.d(TAG, "kid debug: seeded $spec")
        }
        val today = java.time.LocalDate.now()
        if (intent.getBooleanExtra("sleepy", false)) {
            val need = progress.goalMinutes * 60 - progress.secondsToday(today)
            if (need > 0) prefs.addPractice(today, need)
            bedtime.goodnightOn = today; bedtime.oneMoreOn = null
            Log.d(TAG, "kid debug: today's goal reached, goodnight said")
        }
        if (intent.getBooleanExtra("awake", false)) { bedtime.oneMoreOn = today; Log.d(TAG, "kid debug: lessons open today") }
    }

    /**
     * The activity is singleTop (manifest): the launcher icon, the reminder's notification and `adb shell am start` bring
     * this one back instead of stacking a second MainActivity on it (each with its own mic, voice, MIDI and sampler).
     * Debug builds: a route in the new intent (--es screen, song, lesson, profile, --ez daily) opens as a fresh launch would.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable || (listOf("screen", "song", "lesson", "profile").none { intent.getStringExtra(it) != null } && !intent.getBooleanExtra("daily", false))) return
        setIntent(intent)
        intent.getStringExtra("profile")?.let(profiles::switchTo)
        openApp(fromLaunch = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shownRoute?.let { outState.putString(ROUTE, it) }
        if (openId == null) outState.putBoolean(PICKER, true)
    }

    /**
     * The screen [route] names (see [routeOf]), each part going back to the one before it. A song reopens in wait mode at
     * its saved position, as from the library. A kid's Settings, Mic setup, Privacy and Licences sit behind the parent
     * gate, which a recreate closes: they reopen as the screen they go back to.
     */
    private fun screenAt(route: String, prefs: Prefs, kid: Boolean): Screen = route.split("/").fold<String, Screen>(Screen.Home()) { back, part ->
        val arg = part.substringAfter(":", "")
        when (part.substringBefore(":")) {
            "home" -> Screen.Home(HomeTab.valueOf(arg))
            "song" -> repo.all().first { it.id == arg }.let { m ->
                val p = prefs.progress(m.id)
                Screen.Player(repo.load(m), m, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission()),
                    if (p.completed) 0.0 else p.lastPositionMs.toDouble(), back)
            }
            "lesson" -> arg.substringAfter(":").let { id -> COURSE.flatMap { it.lessons }.first { it.id == id } }
                .let { Screen.Lesson(it, back, arg.substringBefore(":").toInt().coerceIn(0, it.steps.lastIndex)) }
            "micsetup" -> if (kid) back else Screen.MicSetup(back)
            "sight" -> Screen.SightRead(back, arg.toInt())
            "privacy" -> if (kid) back else Screen.Privacy(back)
            "licences" -> if (kid) back else Screen.Licences(back)
            "settings" -> if (kid) back else Screen.Settings(back)
            "ear" -> Screen.EarTraining(back)
            "onboarding" -> Screen.Onboarding
            "theory" -> Screen.Theory(back)
            "world" -> Screen.World(back, WorldTab.valueOf(arg))
            "play" -> Screen.Play(Toy.valueOf(arg), back)
            "kidsongs" -> Screen.KidSongs(back)
            "kidstickers" -> Screen.KidStickers(back, StickerPage.valueOf(arg))
            "finale" -> Screen.Finale(arg)
            else -> back
        }
    }

    // A session of the parent brief's log runs while the app is on screen as someone (the picker is no one).
    override fun onStart() { super.onStart(); com.bigpapituna.sharpears.engine.ForegroundClock.app.start(); openId?.let(Briefs::sessionStart) }
    override fun onStop() { Briefs.sessionEnd(); com.bigpapituna.sharpears.engine.ForegroundClock.app.stop(); super.onStop() }

    override fun onPause() {
        Juice.world?.flush()            // the treat jar saves every few notes; the rest now
        super.onPause()
    }

    override fun onDestroy() {
        openVoice?.release()
        mic.stop()
        midi.stop()
        sampler.release()
        super.onDestroy()
    }
}
