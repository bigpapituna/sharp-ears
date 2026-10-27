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
import com.bigpapituna.sharpears.engine.chords.PopFind
import com.bigpapituna.sharpears.engine.songPack
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.engine.ChordTrack
import com.bigpapituna.sharpears.engine.LessonItem
import com.bigpapituna.sharpears.engine.SessionPart
import com.bigpapituna.sharpears.engine.SessionPlan
import com.bigpapituna.sharpears.engine.SessionSong
import com.bigpapituna.sharpears.engine.planSession
import com.bigpapituna.sharpears.engine.sessionSummary
import com.bigpapituna.sharpears.engine.through
import com.bigpapituna.sharpears.ui.DailyChords
import com.bigpapituna.sharpears.ui.Interstitial
import com.bigpapituna.sharpears.engine.barMs
import com.bigpapituna.sharpears.engine.labelBarsAligned
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
import androidx.lifecycle.repeatOnLifecycle
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
/** The saved instance state's key for the weekly brief the notification opened, while it is still open. */
private const val BRIEF = "brief"
/** The launch extras every build reads: the weekly brief notification's (reminder/BriefReminder.kt). Every other extra is a debug route's. */
private val NOTIFICATION_EXTRAS = setOf(com.bigpapituna.sharpears.reminder.BriefReminder.EXTRA_PROFILE, com.bigpapituna.sharpears.reminder.BriefReminder.EXTRA_PERIOD)

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
    /** The tabs (a kid's map for a kid). [practiceTab] only matters for Practice: null opens its last tool used (2.7). */
    data class Home(val tab: HomeTab = HomeTab.TODAY, val practiceTab: PracticeTab? = null,
                    /** A kid's home (2.2) after a page's finale: the map opens at that page's end and rolls on to the next stone. */
                    val kidFrom: String? = null,
                    /**
                     * The Songs tab's section (2.7): the library, or songs by chords. 2.14: null is the person's own first section
                     * (songs by chords for a big kid, the library for a grown-up: engine/family/Ages.kt songsOpenOnChords).
                     */
                    val songs: com.bigpapituna.sharpears.ui.SongsSection? = null,
                    /** Songs by chords: the style chip and sort it opens on (popular-song chord mode). */
                    val pop: com.bigpapituna.sharpears.ui.chords.PopStart = com.bigpapituna.sharpears.ui.chords.PopStart()) : Screen
    /** [meta] is null for generated songs (mic check, progressions). [back] is where Back goes. [band] is a pop song's drums and bass. */
    data class Player(val song: Song, val meta: SongMeta?, val start: PlayerStart, val startMs: Double, val back: Screen,
                      val band: com.bigpapituna.sharpears.engine.chords.BandBed? = null,
                      /** A pop song's chord names for the lane's Chords pill (library songs get theirs from the lesson store). */
                      val chords: com.bigpapituna.sharpears.engine.ChordTrack? = null) : Screen
    /**
     * A pop song's page (popular-song chord mode, ui/chords): its chords by section, key and tempo steppers, the pattern and
     * the band. [shift] is semitones from the song's own key, [pattern] and [bpm] what the learner chose (null: the song's own);
     * they come back from the player. [hear] and [kid] are debug: start the band as it opens, show the kid page.
     */
    data class PopSong(val songId: String, val back: Screen, val shift: Int = 0, val pattern: com.bigpapituna.sharpears.engine.chords.PopSong.Pattern? = null,
                       val bpm: Int? = null, val hear: Boolean = false, val kid: Boolean? = null, val pre: Boolean = false) : Screen
    /**
     * Learn chords (2.7, the top of Learn): the daily chord review, the dictionary as a sheet, free chord practice and the
     * packs ([start] opens the sheet or the practice). [inject] (debug) feeds the mic.
     */
    data class LearnChords(val back: Screen, val start: com.bigpapituna.sharpears.ui.LearnChordsStart = com.bigpapituna.sharpears.ui.LearnChordsStart.REVIEW,
                           val inject: String? = null) : Screen
    /** Games (2.7, the top of Learn): the chord games. [start] (debug) opens a game at a level, with an injection. */
    data class ChordGames(val back: Screen, val start: GameStart = GameStart()) : Screen
    /** Progressions (2.7, the top of Learn): hear the classics, practise one in the player. */
    data class Progressions(val back: Screen) : Screen
    /** A course lesson at step [step]. [inject] (debug) feeds the mic for that first step. */
    data class Lesson(val lesson: CourseLesson, val back: Screen, val step: Int = 0, val inject: String? = null, val midiScript: String? = null, val scene: Int = 0) : Screen
    /** [inject] (debug, 2.15): the room check hears it instead of the microphone. */
    data class MicSetup(val back: Screen, val inject: String? = null) : Screen
    /** The sight-reading drill. [inject] (debug) feeds the mic; [midiScript] (debug) plays a pretend keyboard into a staff drill. */
    data class SightRead(val back: Screen, val level: Int = 1, val inject: String? = null, val midiScript: String? = null) : Screen
    /**
     * "Practice today": part [index] of [plan]; [between] shows the one-line interstitial before it ([held]: come back to it
     * from the part, so it waits for Go instead of counting down). [done] and [skipped] (passed over with Skip) decide whether
     * the plan is through at the end; [practiceAt] is today's practice seconds at Start, so the summary counts what it added.
     */
    data class Daily(val plan: SessionPlan, val index: Int, val between: Boolean, val startedAt: Long, val done: List<SessionPart>,
                     val skipped: List<SessionPart> = emptyList(), val practiceAt: Int = 0, val held: Boolean = false) : Screen
    data class Privacy(val back: Screen) : Screen
    data class Licences(val back: Screen) : Screen
    /** Settings, pushed from the gear on You (it was a tab before 2.0). */
    data class Settings(val back: Screen) : Screen
    /** The ear training hub (Learn), or straight into a game or the workout with [start]. */
    data class EarTraining(val back: Screen, val start: EarStart? = null) : Screen
    /** First run: four questions, then the suggested lesson. */
    data object Onboarding : Screen
    /** "Let's hear you play" (2.6): the short listen after the piano level was said ([said]); [startTask] and [inject] are debug. */
    data class Placement(val said: com.bigpapituna.sharpears.engine.family.Level, val startTask: Int = 0, val inject: String? = null) : Screen
    data class Theory(val back: Screen) : Screen
    /** The pup's world (2.1): the places, the jar and egg, the album, dress-up and the records, from the You tab's card. */
    data class World(val back: Screen, val tab: WorldTab = WorldTab.WORLD) : Screen
    /** The play corner (2.1): the Jam, Pup says or Clap along. [hint] is the quest's line for the Jam ("find all the Cs"). */
    data class Play(val toy: Toy, val back: Screen, val hint: String? = null) : Screen
    /** A kid's songs (2.2, the kid home's Songs button): only the songs played by colour, big tiles. */
    data class KidSongs(val back: Screen) : Screen
    /** A kid's songs by chords (2.6, the "Songs by chords" tile on the kid's songs): the Kids style's chord songs, big tiles. */
    data class KidChords(val back: Screen,
                         /** 2.14: one of a little reader's pop packs (Pop, Greatest hits, Rock); null: the kids' chord songs. */
                         val chip: com.bigpapituna.sharpears.engine.chords.PopFind.Chip? = null) : Screen
    /** A kid's stickers (2.2, My stickers): the album, dress-up, records and paws as pages. */
    data class KidStickers(val back: Screen, val page: StickerPage = StickerPage.ALBUM) : Screen
    /** A Playground page's finale (2.2): fireworks, the album page, the certificate, then on along the path. */
    data class Finale(val pageId: String) : Screen
    /** The kid arcade's hub (2.7, ui/arcade): a tile a game, from the kid's Play sheet. */
    data class Arcade(val back: Screen) : Screen
    /**
     * A round of an arcade game; [seed] draws it, [level] (debug) plays it at that level instead of the book's, [inject]
     * (debug) feeds the mic, [debugStars] (debug) skips to the finish with a round of that many stars.
     */
    data class ArcadeRound(val game: com.bigpapituna.sharpears.engine.arcade.ArcadeGame, val back: Screen, val seed: Long = System.nanoTime(),
                           val level: Int? = null, val inject: String? = null, val debugStars: Int? = null) : Screen
}

/** Practise together with nothing chosen (the parent brief, 2.4): the next song [whoId] picks opens as a duet with [partnerId]. */
private data class DuetNext(val whoId: String, val partnerId: String?)

/** The play corner's toy as the brief's log names it. */
private fun toyKind(t: Toy) = when (t) { Toy.JAM -> ToyKind.JAM; Toy.PUP_SAYS -> ToyKind.PUP_SAYS; Toy.CLAP -> ToyKind.CLAP_ALONG }

/** Order of screens for the transitions: deeper goes forward, shallower goes back, the player gets the curtain. */
private fun depthOf(s: Screen) = when (s) {
    is Screen.Home -> 0
    is Screen.Settings, is Screen.EarTraining, is Screen.Theory, Screen.Onboarding, is Screen.Placement, is Screen.World, is Screen.KidSongs, is Screen.KidChords, is Screen.KidStickers, is Screen.PopSong,
    is Screen.LearnChords, is Screen.ChordGames, is Screen.Progressions -> 1
    is Screen.Arcade -> 1
    is Screen.Player, is Screen.Lesson, is Screen.SightRead, is Screen.Daily, is Screen.Privacy, is Screen.Licences, is Screen.MicSetup, is Screen.Play,
    is Screen.Finale, is Screen.ArcadeRound -> 2
}

/**
 * The screen as one line for the saved instance state, the screens it goes back to first ("home:LEARN/lesson:2:find-c"),
 * so a recreate (a process the system stopped in the background, Don't keep activities, a dark mode or font size change)
 * reopens it, a lesson at its step, instead of home. Practice today and generated songs (the mic check, progressions,
 * practice patterns) save as where they go back to. Read by [MainActivity.screenAt].
 */
private fun routeOf(s: Screen): String = when (s) {
    is Screen.Home -> "home:${s.tab.name}" + when (s.songs) { com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS -> ":chords"; com.bigpapituna.sharpears.ui.SongsSection.LIBRARY -> ":library"; null -> "" }
    is Screen.Player -> if (s.meta == null) routeOf(s.back) else "${routeOf(s.back)}/song:${s.meta.id}"
    is Screen.Lesson -> "${routeOf(s.back)}/lesson:${s.step}:${s.lesson.id}"
    is Screen.MicSetup -> "${routeOf(s.back)}/micsetup"
    is Screen.SightRead -> "${routeOf(s.back)}/sight:${s.level}"
    is Screen.Daily -> "home:TODAY"
    is Screen.Privacy -> "${routeOf(s.back)}/privacy"
    is Screen.Licences -> "${routeOf(s.back)}/licences"
    is Screen.Settings -> "${routeOf(s.back)}/settings"
    is Screen.EarTraining -> "${routeOf(s.back)}/ear"
    Screen.Onboarding -> "onboarding"
    is Screen.Placement -> "home:TODAY"
    is Screen.Theory -> "${routeOf(s.back)}/theory"
    is Screen.World -> "${routeOf(s.back)}/world:${s.tab.name}"
    is Screen.Play -> "${routeOf(s.back)}/play:${s.toy.name}"
    is Screen.KidSongs -> "${routeOf(s.back)}/kidsongs"
    is Screen.KidChords -> "${routeOf(s.back)}/kidchords" + (s.chip?.let { ":${it.name}" } ?: "")
    is Screen.PopSong -> "${routeOf(s.back)}/popsong:${s.songId}:${s.shift}:${s.pattern?.name ?: "-"}:${s.bpm ?: 0}"
    is Screen.KidStickers -> "${routeOf(s.back)}/kidstickers:${s.page.name}"
    is Screen.Finale -> "finale:${s.pageId}"
    is Screen.LearnChords -> "${routeOf(s.back)}/learnchords"
    is Screen.ChordGames -> "${routeOf(s.back)}/chordgames"
    is Screen.Progressions -> "${routeOf(s.back)}/progressions"
    is Screen.Arcade -> "${routeOf(s.back)}/arcade"
    is Screen.ArcadeRound -> "${routeOf(s.back)}/arcadegame:${s.game.id}"
}

/** The player is always dark, like a video; everything else follows the system. */
private fun playerShown(s: Screen) = s is Screen.Player ||
    (s is Screen.Daily && !s.between && s.plan.parts[s.index].let { it is SessionPart.Loop || it is SessionPart.Song ||
        (it is SessionPart.Challenge && it.item !is com.bigpapituna.sharpears.engine.nav.Challenge.SightRead) })

/** The stage: the player, or a Practice today part that runs one. */
private fun isStage(s: Screen) = s is Screen.Player || (s is Screen.Daily && !s.between)

/** Screens played at the piano, hands off the phone for minutes: the screen stays on (see [KeepScreenOnWhile]). */
private fun keepsScreenOn(s: Screen) = s is Screen.Player || s is Screen.Placement || s is Screen.Lesson || s is Screen.Daily || s is Screen.SightRead ||
    s is Screen.Play || s is Screen.EarTraining || s is Screen.Theory || s is Screen.Finale || s is Screen.ArcadeRound

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
    /** 2.14: each library song's tempo and minutes, read once, for Today's next challenge (null: it does not load). */
    private val songTempos = HashMap<String, Pair<Double, Double>?>()
    private fun songTempo(m: SongMeta): Pair<Double, Double>? = songTempos.getOrPut(m.id) {
        runCatching { repo.load(m).let { it.bpm to it.durationMs / 60_000.0 } }.getOrNull()
    }
    private lateinit var profiles: Profiles
    private lateinit var gate: ParentGateController
    /** The profile the app is open as (null while the picker shows). */
    private var openId: String? = null
    /** Kid or grown-up, as the app was opened (a switch of kind reopens it). */
    private var openKind: com.bigpapituna.sharpears.engine.family.ProfileKind? = null
    /** A kid reads or not, as the app was opened (a big kid's course path differs, so a change reopens it too). */
    private var openReader: Boolean? = null
    /** The piano level the open profile was placed with: a change re-plans the marks in place, with no reopen (see the profile flow). */
    private var openLevel: com.bigpapituna.sharpears.engine.family.Level? = null
    /** 2.14: a kid's age band as the app was opened: the pup's world or the tabs, so a change reopens it. */
    private var openBand: com.bigpapituna.sharpears.engine.family.AgeBand? = null
    /** 2.14: the open profile's progress, for the level seeded from a new piano level. */
    private var openProgress: ProgressStore? = null
    /** Launches of this activity's content ([openApp] with fromLaunch), so a new one forgets the last one's screen. */
    private var launches = 0
    /** 2.14: this onCreate is a recreate (a fold, a rotation, dark mode): its first [openApp] is the same person, the gate stays. */
    private var recreated = false
    /** 2.14: the child whose brief Family today opened (one tap, on a grown-up's Today), or null. */
    private val familyBrief = mutableStateOf<String?>(null)
    /** The open screen as [routeOf] writes it, kept in the instance state; [restoreRoute] is the one a recreate reopens. */
    private var shownRoute: String? = null
    /** The arcade round (its seed) that has ended: a recreate on its finish reopens the games, not a new round (QA 2.8.3). */
    private var arcadeEnded: Long? = null
    private var restoreRoute: String? = null
    /** The open profile's preferences and kid memory, so a parent brief action's change shows at once ([briefNav] changed). */
    private var openPrefs: Prefs? = null
    private var openKid: KidStore? = null
    /** The open profile's world, so a session's end brings its practice ledger up to date ([Briefs.onSessionEnd]). */
    private var openWorld: WorldStore? = null
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
    /** Debug: that brief over sample findings offering every action (ui/brief/BriefDemo.kt, sampleActionsEnv). */
    private var briefSamples = false
    /**
     * The launch intent's debug extras may act (security review 2026-10-22, finding 1): set by [routesAllowed] in onCreate and
     * by an allowed onNewIntent. When false the activity's intent has been stripped of them ([withoutDebugExtras]), so no
     * screen or demo it opens (TheoryScreen, EmailDemo, BriefDemo, PupDebug, SkinDemo, WorldDemo) can read one.
     */
    var debugRoutes = false
        private set

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
            // A grown-up chose it behind the gate: after today's goodnight it is their "one more lesson", as on the asleep screen.
            if (who.isKid) com.bigpapituna.sharpears.ui.skin.BedtimeStore(this@MainActivity, who.id).oneMoreOn = com.bigpapituna.sharpears.data.AppClock.today()
            go(who, Screen.Lesson(lesson, if (who.mapHome) Screen.Home() else Screen.Home(HomeTab.LEARN)))
        }

        override fun openSong(who: Profile, songId: String, duet: Boolean) = runOnUiThread {
            // A pop song's run ("pop-<id>", songs by chords): its page, where it is played along (no duet there).
            com.bigpapituna.sharpears.engine.chords.PopPlay.ofRun(songId, com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs)?.let { pop ->
                Log.d(TAG, "brief: open pop song ${pop.id} for ${who.id}")
                val home = if (who.mapHome) Screen.KidSongs(Screen.Home()) else Screen.Home(HomeTab.SONGS, songs = com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS)
                go(who, Screen.PopSong(pop.id, if (who.mapHome) Screen.KidChords(home) else home))
                return@runOnUiThread
            }
            val m = repo.all().firstOrNull { it.id == songId && repo.available(it) } ?: return@runOnUiThread
            val song = runCatching { repo.load(m) }.getOrNull() ?: return@runOnUiThread
            val partner = if (duet) com.bigpapituna.sharpears.engine.play.Duet.grownUpFor(who, openId, profiles.all) else null
            Log.d(TAG, "brief: open song $songId for ${who.id}" + if (duet) " as a duet with ${partner ?: "a partner they pick"}" else "")
            go(who, Screen.Player(song, m, briefStart(who, Prefs(this@MainActivity, who.id), duet, partner), 0.0,
                if (who.mapHome) Screen.KidSongs(Screen.Home()) else Screen.Home(HomeTab.SONGS)))
        }

        override fun pickSong(who: Profile, duet: Boolean) = runOnUiThread {
            duetNext.value = if (duet) DuetNext(who.id, com.bigpapituna.sharpears.engine.play.Duet.grownUpFor(who, openId, profiles.all)) else null
            Log.d(TAG, "brief: ${who.id} picks a song" + if (duet) " for a duet" else "")
            go(who, if (who.mapHome) Screen.KidSongs(Screen.Home()) else Screen.Home(HomeTab.SONGS))
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

    /** The screen the activity is on, by its smallest width: a fold or an unfold changes it (QA D1). */
    private var smallestWidthDp = 0

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // A fold or unfold with no player open puts the phone's own orientation back (2.8.4, QA D1): a landscape lock left
        // on the activity by the other screen would otherwise turn this one sideways.
        val sw = newConfig.smallestScreenWidthDp
        if (sw != smallestWidthDp) {
            if (!com.bigpapituna.sharpears.ui.LandscapeLock.held) com.bigpapituna.sharpears.ui.LandscapeLock.reset(this, "display changed")
            smallestWidthDp = sw
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug routes (security review 2026-10-22, finding 1): this launch's extras act only when DebugRoutes allows them
        // (a keyed build wants `--es dk <key>`); any other launch loses them here, before anything reads them.
        // 2.14 (persona walk, Theo: "profiles: switched to owner" at "One more"): a launch's extras act once. The activity's intent
        // keeps them for as long as it lives, so a recreate (a theme or font change, the process coming back) read `--es profile`,
        // `--es today`, the seeds and the rest again; DebugRoutes.oneShot says they are used up, and they are dropped here.
        debugRoutes = routesAllowed(intent) && com.bigpapituna.sharpears.engine.DebugRoutes.oneShot(recreated = savedInstanceState != null)
        if (savedInstanceState != null && routesAllowed(intent)) Log.d(TAG, "debug routes: extras already used by this launch (recreate), not applied again")
        if (!debugRoutes) intent = withoutDebugExtras(intent)
        debugToday(intent, debugRoutes)
        smallestWidthDp = resources.configuration.smallestScreenWidthDp
        restoreRoute = savedInstanceState?.getString(ROUTE)
        recreated = savedInstanceState != null
        // The weekly brief's notification (all builds): that person's brief opens over whatever shows first. After a recreate,
        // only what was still open then (the launch intent keeps its extras, and the brief may have been closed since).
        briefRequest.value = if (savedInstanceState != null) savedInstanceState.getString(BRIEF)?.let(::briefOf) else briefFrom(intent)
        briefSamples = debugRoutes && intent.getBooleanExtra("brief_samples", false)
        sampler = Sampler(this) { samplerReady.value = true }
        midi = MidiDevices(this).also { it.start() }
        mic = MicInput(this)
        repo = SongRepository(this)
        profiles = Profiles(this)
        gate = ParentGateController(profiles)
        Briefs.init(this)                                  // the parent brief's event logs, files/events/<profile>.log
        // Tester analytics (analytics/Tester.kt): PostHog starts only on a build with a key and "Help Roy test" on.
        com.bigpapituna.sharpears.analytics.Tester.init(this)
        Briefs.tap = com.bigpapituna.sharpears.analytics.Tester::briefEvent
        // 2.15, debug: --es screen errortest (keyed builds through scripts/adb-route.ps1): a handled test exception, then an
        // uncaught one that closes the app (analytics/Errors.kt ErrorTest). Both carry is_error_test=true.
        if (debugRoutes && BuildConfig.DEBUG && intent.getStringExtra("screen") == "errortest") com.bigpapituna.sharpears.analytics.ErrorTest.run()
        // 2.14, debug walks: --es family "Mia:0,Theo:1" adds those kids (name, then 1 when they read) unless someone has the name.
        intent.getStringExtra("family")?.takeIf { debugRoutes }?.split(",")?.forEachIndexed { i, spec ->
            val name = spec.substringBefore(":").trim()
            if (name.isNotEmpty() && profiles.all.none { it.name == name }) {
                profiles.create(com.bigpapituna.sharpears.engine.family.Profile.kid(name, 1 + i, reader = spec.substringAfter(":", "0") == "1"), switch = false)
                    ?.let { Log.d(TAG, "profiles: debug family added ${it.id} ($name)") }
            }
        }
        // --es profile <id> (debug): open as that profile, no picker.
        val debugProfile = intent.getStringExtra("profile")?.takeIf { debugRoutes }?.also(profiles::switchTo)
        // Review pipeline (private build): crashes are kept, a weekly summary is written, and whatever waits goes out.
        if (mic.keepRecordings) {
            val app = applicationContext      // read at crash time, never this activity (the handler outlives it)
            mic.reporter.catchCrashes(packageManager.getPackageInfo(packageName, 0).versionName ?: "?") { Profiles(app).all.map { it.name } }
            mic.reporter.weeklySummary(Prefs(this, Profile.OWNER_ID).practice())
            mic.reporter.flush()
        }
        com.bigpapituna.sharpears.reminder.Reminder.schedule(this)        // alarms do not survive an update or restart
        com.bigpapituna.sharpears.reminder.BriefReminder.scheduleAll(this) // the weekly brief's alarms, one per person, likewise
        // A session's end (the app leaves the screen, or someone else takes over): the wardrobe's ledger takes in the minutes
        // and days just played (whatever was opened waits for the next finish screen), and the brief by email
        // (reminder/EmailJob.kt) plans its next look. The app's start sends what is due and sets the alarm.
        Briefs.onSessionEnd = { id ->
            openWorld?.takeIf { it.profileId == id }?.let { w ->
                runCatching { w.sync() }.onFailure { Log.d(TAG, "wardrobe: ledger sync at the session's end failed: ${it.javaClass.simpleName}") }
            }
            com.bigpapituna.sharpears.reminder.EmailJob.afterSession(applicationContext)
            // The tester diary keeps the session's refusal and error lines before logcat rolls them away.
            if (com.bigpapituna.sharpears.analytics.Tester.switchOn.value || com.bigpapituna.sharpears.analytics.Tester.diaryOn.value) {
                val app = applicationContext
                Thread { com.bigpapituna.sharpears.analytics.Tester.collectLog(app) }.start()
            }
        }
        com.bigpapituna.sharpears.reminder.EmailJob.runAsync(this, "app start")
        // Someone else picked (the family wall, Settings > Family, the player deleted): the app opens again as them. So it
        // does when the player turns from kid to grown-up or back: the course path, the skin and the mic offset change.
        lifecycleScope.launch {
            profiles.currentFlow.map { listOf(it.id, it.kind, it.reader, it.level, it.band) }.distinctUntilChanged().collect {
                val now = profiles.current
                if (openId != null && (openId != now.id || openKind != now.kind || openReader != now.reader || openBand != now.band)) openApp(fromLaunch = false)
                // A new piano level (the onboarding, Family > Piano level) re-plans the open profile's marks where it stands: the
                // path and the map read the prefs, so nothing reopens and no screen is lost.
                else if (openId != null && openLevel != now.level) {
                    openLevel = now.level
                    openPrefs?.let { p -> p.applyPlacement(com.bigpapituna.sharpears.engine.family.Placement.planOf(now)); p.version.intValue++ }
                    openKid?.let { it.version.intValue++ }
                    // 2.14: the level and XP the new answer starts at (a floor, never lowered; engine/progress/PlacementLevel.kt).
                    openProgress?.let { seedLevel(now, it) }
                }
            }
        }
        // 2.14, the one minutes ledger (data/Briefs.kt): the open session is checkpointed every 30 s while the app is on screen
        // (and in onPause), so a session killed mid-step keeps its minutes.
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) { kotlinx.coroutines.delay(Briefs.CHECKPOINT_MS); Briefs.checkpoint() }
            }
        }
        // Two or more people: "Who's playing today?" first, unless a debug route or a recreate (rotation, import) opened us;
        // a recreate while it still asked (the process stopped in the background) asks again rather than open as the last player.
        val debugRoute = debugRoutes && (listOf("screen", "song", "lesson").any { intent.getStringExtra(it) != null } || intent.getBooleanExtra("daily", false))
        // The world's jingles and the note tick (one SoundPool for the app; Settings > Sound switches them with the rest).
        WorldSounds.init(this)
        if (profiles.roster.needsPicker && (savedInstanceState == null || savedInstanceState.getBoolean(PICKER)) && !debugRoute && debugProfile == null) showPicker()
        else openApp(fromLaunch = true)
    }

    /**
     * 2.14: the level and XP [who]'s placement starts at (engine/progress/PlacementLevel.kt), raised quietly once the first-run
     * questions are done, for whoever sees levels (a grown-up, a big kid). A floor: XP already past it stays.
     */
    private fun seedLevel(who: Profile, progress: ProgressStore) {
        if (!progress.onboardingDone || !com.bigpapituna.sharpears.engine.family.Ages.showsLevels(who.audience)) return
        val gained = progress.seedFromPlacement(if (who.isKid) who.level.forKid else who.level)
        if (gained > 0) Log.d(TAG, "placement: ${who.id} starts at level ${progress.level} " +
            "(${com.bigpapituna.sharpears.engine.progress.Progression.title(progress.level)}), +$gained XP from ${who.level}")
    }

    /** The launch picker; picking (or adding someone) opens the app as them. */
    private fun showPicker() {
        Briefs.sessionEnd(); Briefs.currentId = null        // no one is playing yet: nothing is logged
        recreated = false                                   // a pick is a switch of player: the gate closes (ParentGateRules.closesOnOpen)
        setContent {
            SharpEarsTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val roster by profiles.rosterFlow.collectAsState()
                    var weekly by remember { mutableStateOf<com.bigpapituna.sharpears.engine.brief.WeeklySlot?>(null) }
                    var wantsCheck by remember { mutableStateOf(false) }
                    androidx.compose.runtime.CompositionLocalProvider(LocalProfiles provides profiles, LocalParentGate provides gate,
                        com.bigpapituna.sharpears.ui.brief.LocalBriefNav provides briefNav, com.bigpapituna.sharpears.ui.LocalMic provides mic) {
                        ProfilePicker(roster,
                            onPick = { id -> profiles.switchTo(id); openApp(fromLaunch = true) },
                            onAdd = { p ->
                                val made = profiles.create(p)
                                // A kid's weekly brief: the day and time chosen at the end of the flow (Sunday at 6 pm unless changed),
                                // as Settings > Family does.
                                if (made != null && made.isKid) {
                                    com.bigpapituna.sharpears.data.BriefStore(this@MainActivity, made.id).weekly = weekly ?: com.bigpapituna.sharpears.engine.brief.WeeklySlot.DEFAULT
                                    com.bigpapituna.sharpears.reminder.BriefReminder.schedule(this@MainActivity, made)
                                }
                                if (made != null && wantsCheck) Prefs(this@MainActivity, made.id).placementCheckPending = true
                                wantsCheck = false
                                if (made != null) openApp(fromLaunch = true)
                            },
                            onWeekly = { weekly = it }, onCheck = { wantsCheck = it })
                        BriefFromNotification(roster, openKid = false)
                        ParentGateHost(gate)
                    }
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
        // 2.14 (Priya's P4): every switch of player closes the gate, and so does a fresh start; a recreate keeps it.
        if (com.bigpapituna.sharpears.engine.family.ParentGateRules.closesOnOpen(openId, profile.id, recreated)) {
            gate.close(); Log.d(TAG, "gate: closed, ${profile.id} opens")
        }
        recreated = false
        if (newPlayer) familyBrief.value = null
        openId = profile.id
        openKind = profile.kind
        openReader = profile.reader
        openBand = profile.band
        Log.d(TAG, "profiles: open as ${profile.id} (${profile.kind}, reader=${profile.reader})")
        com.bigpapituna.sharpears.analytics.Tester.profile(profile.isKid, profile.id)     // "kid" or "adult" only, never the name
        val intent = if (fromLaunch) intent else android.content.Intent()      // a switch carries no debug extras
        val prefs = Prefs(this, profile.id).also { it.logRestored() }
        // Placement (2.6): the lessons this person's level skips are marked "done, skipped" (idempotent; a played lesson is never touched).
        prefs.applyPlacement(com.bigpapituna.sharpears.engine.family.Placement.planOf(profile))
        openLevel = profile.level
        // The mic follows the player: a kid's offset and report tag for every mic screen (chords, ear games, tuner,
        // playground), and a new player starts with wrong keys painted again (the last one's room may have been noisier).
        mic.forProfile(profile.isKid, profile.micOffsetDb, newSession = newPlayer)
        openVoice?.release()
        val voice = Voice(this, prefs, mic).also { openVoice = it }   // the speech engine itself starts on the first line
        voice.speed = com.bigpapituna.sharpears.engine.family.Ages.voiceSpeed(profile.audience)   // big and slow for little kids (KIDS-PLAN 3.8)
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
        // 2.7, What's new and the tour (engine/nav/Welcome.kt): a newcomer has nothing new to hear about (they get the tour after
        // the first-run questions), and a kid sees neither (the tour waits for grown-up mode).
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        if ((profile.isKid || !progress.onboardingDone && progress.xp == 0) && prefs.whatsNewSeen != appVersion) prefs.whatsNewSeen = appVersion
        // Debug --ez welcome true: What's new and the tour again for this profile.
        if (fromLaunch && debugRoutes && intent.getBooleanExtra("welcome", false)) {
            prefs.tourDone = false; prefs.whatsNewSeen = null; Log.d(TAG, "welcome: reset for ${profile.id} (debug)")
        }
        // Debug --es simple simple|advanced|full|unset (or --es layout ...; 2.7's on|off|default still read as simple|full|unset):
        // the profile's layout; unset forgets the choice, so the launch asks again.
        if (fromLaunch && debugRoutes) (intent.getStringExtra("layout") ?: intent.getStringExtra("simple"))?.let { v ->
            prefs.layout = when (v) { "on" -> com.bigpapituna.sharpears.engine.nav.Layout.SIMPLE; "off" -> com.bigpapituna.sharpears.engine.nav.Layout.FULL
                else -> com.bigpapituna.sharpears.engine.nav.Layout.parse(v) }
            Log.d(TAG, "layout: set to ${prefs.layout?.wire ?: "unset"} for ${profile.id} (debug)")
        }
        if (!progress.onboardingDone && progress.xp > 0) progress.onboardingDone = true
        // A kid goes straight in: no questions, the goal the profile was made with, and the first game opens at once.
        val newKid = profile.isKid && !progress.onboardingDone
        if (newKid) { progress.goalMinutes = profile.dailyGoalMin; progress.onboardingDone = true }
        openProgress = progress
        // 2.14: the level a placement starts at (a grown-up's or a big kid's; a little kid sees no levels), once the questions are done.
        seedLevel(profile, progress)
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
        // 2.7: the kid arcade's games (bests, levels, what is open), in the profile's "play" file.
        val arcadeStore = com.bigpapituna.sharpears.data.ArcadeStore(this, profile.id)
        // Pup's quest asks for one of the open games every other day (engine/arcade ArcadeQuest).
        fun questGame(): Pair<String, String>? = com.bigpapituna.sharpears.engine.arcade.ArcadeQuest.pick(com.bigpapituna.sharpears.data.AppClock.today(),
            arcadeStore.open(world.state.ledger.sparkles))?.let { it.id to com.bigpapituna.sharpears.engine.arcade.ArcadeQuest.title(it) }
        openPrefs = prefs; openKid = kidStore; openWorld = world
        // 2.5, the wardrobe's practice ledger: minutes and days from the practice log, three-star lessons and pages walked
        // from the kid store (a page whose last lesson is done counts at once: its rare item shows on that lesson's finish).
        world.ledgerSource = {
            com.bigpapituna.sharpears.engine.world.LedgerInput(progress.practiceLog(), kidStore.allStars().filterValues { it >= 3 }.keys,
                kidStore.finales() + PLAYGROUND_PAGES.filter { it.lessonIds.isNotEmpty() && prefs.lessonPlayed(it.lastLessonId) }.map { it.id })
        }
        fun quest(e: QuestEvent) = playStore.recordQuest(e, profile.isKid).forEach { Log.d(TAG, "quest: ${it.kind} done ($e)") }
        // The course in this person's order: a kid's Playground first, a grown-up's Basics (the Playground an extra).
        // 2.14: a big kid walks the main course (Profile.firstUnitId is Basics); the readers' short list is the little kids' map.
        val path = CoursePath(profile.firstUnitId, bigKid = profile.mapHome && profile.reader,
            startAt = { com.bigpapituna.sharpears.engine.family.Placement.startLessonId(profiles.current) ?: progress.experience?.startLessonId })
        val bedtime = com.bigpapituna.sharpears.ui.skin.BedtimeStore(this, profile.id)
        val practiceStore = PracticeStore(this, profile.id)
        val earStore = EarStore(this, profile.id)      // read for the Learn row and reset from Settings; the hub keeps its own
        val theory = TheoryStore(this, profile.id)     // one store for the Learn card and the academy, so the card updates
        val micCheck = micCheckExercise()
        if (fromLaunch && debugRoutes) kidDebugSeed(intent, prefs, kidStore, progress, bedtime)
        // Stones finished before 2.5 earn their own stickers into the tray; the ledger catches up with what was played.
        world.earnLevels(prefs::lessonPlayed)
        world.sync()
        if (fromLaunch && debugRoutes) worldDebugSeed(intent, world)

        // Debug builds accept extras to open a screen directly:
        //   --es song <id>|mic-check [--el seek <ms>] [--ez dump true] [--ez tabletop true]
        //   [--es mode wait|perform] [--es hands left|right] [--ez mic true] [--es inject <wav path>|synth:76,75,60+64+67@600]
        //   --es song prog:<index>:<key 0-11>   (a chord progression, e.g. prog:0:7 = I-V-vi-IV in G)
        //   2.7, the five tabs: --es screen today|songs [--es section chords]|learn|practice|you (profile)|settings
        //   --es screen learnchords [--es tab daily|dictionary|practice] [--es inject ...]   (Learn chords; --es pack <id> starts a session there)
        //   --es screen games [--es game build|hear|mm|int|back --ei level 0-2] [--es inject ...] | progressions
        //   --es screen chords [--es tab play|daily|dictionary|practice|progressions|songs]   (the old Chords tab's names: each opens its new place)
        //   --es screen practice [--es tab scales|arpeggios|warmups|metronome|tuner]   (no tab: the last tool used)
        //   --ez welcome true   (What's new and the tour again for the profile) --ez emptyToday true (Today with nothing due)
        //   --es screen ear [--es game rhythm|melody|pitch|degree|prog --ei level 0-2 | --ez workout true]
        //   --es screen onboarding   (the first-run flow, whatever was answered before)
        //   --es screen placement [--es said A_LITTLE|SOME|A_LOT] [--ei task n] [--es inject ...]   (2.6, "Let's hear you play"; the level is not changed unless it is played through)
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
        //   --es screen pup [--es pose <name>] [--es mood happy|cheer|tilt|wag|point|listen|sleep] [--ei every <ms>] [--ei size <dp>]
        //       [--ei bpm n] [--es clip jump|dance|wave|sleep|celebrate] [--es theme light|dark]   (2.10: the pup's rig, every mood on a button)
        //   --es lesson playground-<n> [--ei step n] [--es inject synth:...|claps:400,1150|dyn:60/-20,60/-8@1600~1500]
        //   2.2, the kid home (open as a kid with --es profile <id>):
        //   --es screen kidsongs | kidstickers [--es page album|dress|records|paws] | finale [--es page garden]
        //   --es kidseed <page>:<n>|road|reset   (marks the page's first n lessons done with stars; road: every page walked and
        //       its finale played; reset: the Playground's lessons, stars and finales forgotten) [--ez sleepy true] (today's goal
        //       reached and said goodnight to, so the map shows the pup asleep) [--ei practice <minutes>] (practice added today)
        // The launch's debug extras (finding 1): only when [routesAllowed] let this intent through (a switch's intent has none).
        val routes = fromLaunch && debugRoutes
        // Debug builds keep practice recordings and show the fixture recorder in Microphone setup (MicInput.canRecord).
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val openSong = intent.getStringExtra("song")?.takeIf { routes }
        val debugStart = PlayerStart(
            mode = when (intent.getStringExtra("mode")) { "wait" -> Mode.WAIT; "perform" -> Mode.PERFORM; else -> Mode.LISTEN },
            practice = when (intent.getStringExtra("hands")) { "left" -> Practice.LEFT; "right" -> Practice.RIGHT; else -> Practice.BOTH },
            mic = intent.getBooleanExtra("mic", false),
            inject = intent.getStringExtra("inject")?.takeIf { routes },
            tabletop = routes && intent.getBooleanExtra("tabletop", false),
            lightSlow = if (routes) intent.getFloatExtra("lightSlow", 1f) else 1f,
            // 2.13: a pretend MIDI keyboard into a song too (`--es song twinkle --es midi "3000:60/80,..."`, after Play).
            midiScript = intent.getStringExtra("midi")?.takeIf { routes },
        )
        com.bigpapituna.sharpears.ui.playground.FxSlow.factor = if (routes) intent.getFloatExtra("fxSlow", 1f).coerceIn(1f, 20f) else 1f
        val seekMs = intent.getLongExtra("seek", 0L).toDouble()
        val dump = routes && intent.getBooleanExtra("dump", false)
        val route = intent.getStringExtra("screen")?.takeIf { routes }
        // A debug route with a bad value (an unknown lesson or song, "prog:x") opens home instead of crashing the launch.
        val first: Screen = runCatching { when {
            // 2.7: the Chords tab's places now sit at the top of Learn; the old tab names still open them.
            route == "chords" || route == "learnchords" -> {
                // --es pack <id>: start a chord session on that pack, locked or not (to see its items), in Learn chords.
                val pack = intent.getStringExtra("pack")?.let { id -> lessons.curriculum().packs.firstOrNull { p -> p.id == id } }
                pack?.let { lessons.startSession(it.items) }
                val learn = Screen.Home(HomeTab.LEARN)
                val tab = intent.getStringExtra("tab") ?: if (route == "learnchords" || pack != null) "daily" else "play"
                when (tab) {
                    "dictionary" -> Screen.LearnChords(learn, com.bigpapituna.sharpears.ui.LearnChordsStart.DICTIONARY)
                    "practice" -> Screen.LearnChords(learn, com.bigpapituna.sharpears.ui.LearnChordsStart.PRACTICE, debugStart.inject)
                    "daily", "lessons" -> Screen.LearnChords(learn, inject = debugStart.inject)
                    "progressions" -> Screen.Progressions(learn)
                    "songs" -> Screen.Home(HomeTab.SONGS, songs = com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS)
                    else -> Screen.ChordGames(learn, GameStart(GameKind.entries.firstOrNull { it.id == intent.getStringExtra("game") }, intent.getIntExtra("level", 0), debugStart.inject))
                }
            }
            route == "games" -> Screen.ChordGames(Screen.Home(HomeTab.LEARN),
                GameStart(GameKind.entries.firstOrNull { it.id == intent.getStringExtra("game") }, intent.getIntExtra("level", 0), debugStart.inject))
            route == "progressions" -> Screen.Progressions(Screen.Home(HomeTab.LEARN))
            route == "popsongs" -> {
                // --es screen popsongs [--es chip all|hits|yacht|80s|pop|rock|country|soul|kids|holiday] [--es sort easiest|newest|can|one]
                //   [--es song <id> [--ei shift n] [--es pattern WHOLE|HALVES|...] [--ei bpm n] [--ez hear true] [--ez kid true]]
                //   [--ez kid true]   (no song: a kid's chord songs, from the kid's Songs)
                //   [--es view cards]   ("Songs that use these chords", over a plain page)
                //   [--es known C,F,G,Am]   (those chords as known, Leitner box 3, before it opens)
                intent.getStringExtra("known")?.let { k -> lessons.debugKnow(k.split(',').map { it.trim() }.filter { it.isNotEmpty() }, PopFind.KNOWN_BOX) }
                val chipArg = intent.getStringExtra("chip") ?: "all"
                val chip = PopFind.Chip.entries.firstOrNull { c -> c.name.startsWith(chipArg.uppercase()) || c.label.startsWith(chipArg, ignoreCase = true) } ?: PopFind.Chip.ALL
                val sort = when (intent.getStringExtra("sort")) { "newest" -> PopFind.Sort.NEWEST; "can" -> PopFind.Sort.CAN_PLAY; "one" -> PopFind.Sort.ONE_AWAY; else -> PopFind.Sort.EASIEST }
                val home = Screen.Home(HomeTab.SONGS, songs = com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS, pop = com.bigpapituna.sharpears.ui.chords.PopStart(chip, sort))
                val id = intent.getStringExtra("song")
                if (intent.getStringExtra("view") == "cards") return setContent {
                    SharpEarsTheme(dark = isSystemInDarkTheme()) { com.bigpapituna.sharpears.ui.chords.PopCardsDemo(lessons) } }
                if (id == null) { if (intent.getBooleanExtra("kid", false)) Screen.KidChords(Screen.KidSongs(Screen.Home())) else home }
                else Screen.PopSong(id, home, intent.getIntExtra("shift", 0),
                    com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.entries.firstOrNull { it.name.equals(intent.getStringExtra("pattern"), ignoreCase = true) },
                    intent.getIntExtra("bpm", 0).takeIf { it > 0 }, intent.getBooleanExtra("hear", false),
                    if (intent.hasExtra("kid")) intent.getBooleanExtra("kid", false) else null, intent.getBooleanExtra("pre", false))
            }
            route == "settings" -> Screen.Settings(Screen.Home(HomeTab.YOU))
            // 2.15: --es screen micsetup [--es inject <wav>|synth:...] (the room check hears the injection), --es screen privacy
            route == "micsetup" -> Screen.MicSetup(Screen.Settings(Screen.Home(HomeTab.YOU)), debugStart.inject)
            route == "privacy" -> Screen.Privacy(Screen.Settings(Screen.Home(HomeTab.YOU)))
            route == "today" -> Screen.Home(HomeTab.TODAY)
            route == "songs" -> Screen.Home(HomeTab.SONGS, songs = if (intent.getStringExtra("section") == "chords") com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS
                else com.bigpapituna.sharpears.ui.SongsSection.LIBRARY)
            route == "learn" -> Screen.Home(HomeTab.LEARN)
            route == "profile" || route == "you" -> Screen.Home(HomeTab.YOU)
            route == "onboarding" -> Screen.Onboarding
            // --es screen placement [--es said A_LITTLE|SOME|A_LOT] [--ei task n] [--es inject synth:...] (2.6): "Let's hear you play"
            route == "placement" -> Screen.Placement(com.bigpapituna.sharpears.engine.family.Level.parse(intent.getStringExtra("said") ?: "SOME"), intent.getIntExtra("task", 0), debugStart.inject)
            route == "practice" -> Screen.Home(HomeTab.PRACTICE,
                practiceTab = PracticeTab.entries.firstOrNull { it.name.equals(intent.getStringExtra("tab"), ignoreCase = true) })
            route == "ear" -> Screen.EarTraining(Screen.Home(HomeTab.LEARN), when {
                intent.getBooleanExtra("workout", false) -> EarStart(workout = true)
                else -> intent.getStringExtra("game")?.let(EarKind::byId)?.let { EarStart(it, intent.getIntExtra("level", 0)) }
            })
            route == "theory" -> Screen.Theory(Screen.Home(HomeTab.LEARN))
            route == "sight" -> Screen.SightRead(Screen.Home(HomeTab.LEARN), intent.getIntExtra("level", 1), debugStart.inject, debugStart.midiScript)
            route == "world" -> Screen.World(Screen.Home(HomeTab.YOU),
                WorldTab.entries.firstOrNull { it.name.equals(intent.getStringExtra("tab"), ignoreCase = true) } ?: WorldTab.WORLD)
            route == "kidsongs" -> Screen.KidSongs(Screen.Home())
            route == "kidstickers" -> Screen.KidStickers(Screen.Home(),
                StickerPage.entries.firstOrNull { it.name.equals(intent.getStringExtra("page"), ignoreCase = true) } ?: StickerPage.ALBUM)
            route == "finale" -> Screen.Finale(intent.getStringExtra("page") ?: PLAYGROUND_PAGES.first().id)
            // 2.7: --es screen arcade [--es game <id> [--ei level n] [--el seed n] [--es inject synth:...] [--ei stars 1|2|3]]
            route == "arcade" -> Screen.Arcade(Screen.Home()).let { hub ->
                com.bigpapituna.sharpears.engine.arcade.ArcadeGame.byId(intent.getStringExtra("game"))?.let { g ->
                    Screen.ArcadeRound(g, hub, intent.getLongExtra("seed", System.nanoTime()), intent.getIntExtra("level", -1).takeIf { it >= 0 },
                        debugStart.inject, intent.getIntExtra("stars", 0).takeIf { it in 1..3 })
                } ?: hub
            }
            route == "jam" || route == "pupsays" || route == "clap" ->
                Screen.Play(when (route) { "jam" -> Toy.JAM; "pupsays" -> Toy.PUP_SAYS; else -> Toy.CLAP }, Screen.Home(), intent.getStringExtra("hint"))
            // The teams' effect harnesses, each in its own demo state (never a real profile's world or progress).
            route == "worlddemo" -> return setContent { com.bigpapituna.sharpears.ui.world.WorldDemo(intent) { finish() } }
            // 2.10: the pup's rig on its own, every mood on a button (ui/pup/PupDebug.kt).
            route == "pup" -> return setContent { com.bigpapituna.sharpears.ui.pup.PupDebug(intent) { finish() } }
            // 2.11: the step pop on buttons, at a streak of 1, 3 and 6 (ui/StepPopDebug.kt).
            route == "steppop" -> return setContent { com.bigpapituna.sharpears.ui.StepPopDebug(sampler, mic, prefs) { finish() } }
            // 2.13: the cheer pops on buttons, every trigger, kid and grown-up, each way in (ui/pup/CheerPopDebug.kt).
            route == "cheerpop" -> return setContent { com.bigpapituna.sharpears.ui.pup.CheerPopDebug(sampler, mic, voice, prefs) { finish() } }
            route == "brief" -> return setContent { com.bigpapituna.sharpears.ui.brief.BriefDemo(intent) { finish() } }
            route == "email" -> return setContent { com.bigpapituna.sharpears.ui.brief.email.EmailDemo(intent, profile) { finish() } }
            route == "skin" -> return setContent { com.bigpapituna.sharpears.ui.skin.SkinDemo(intent, sampler, midi, mic, voice, prefs) { samplerReady.value } }
            routes && intent.getStringExtra("lesson") != null ->
                COURSE.flatMap { it.lessons }.first { it.id == intent.getStringExtra("lesson") }.let {
                    Screen.Lesson(it, Screen.Home(HomeTab.LEARN), intent.getIntExtra("step", 0), debugStart.inject, intent.getStringExtra("midi"), intent.getIntExtra("scene", 0))
                }
            openSong == null -> Screen.Home()
            openSong == MIC_CHECK_ID -> Screen.Player(micCheck, null, debugStart, seekMs, Screen.Home())
            openSong.startsWith("prog:") -> openSong.split(":").let { (_, i, k) ->
                Screen.Player(progressionSong(PROGRESSIONS[i.toInt()], k.toInt()), null, debugStart, seekMs, Screen.Progressions(Screen.Home(HomeTab.LEARN)))
            }
            else -> repo.all().first { it.id == openSong }.let { m -> Screen.Player(repo.load(m), m, debugStart, seekMs, Screen.Home()) }
        } }.getOrElse { Log.d(TAG, "debug route failed, opening home: $it"); Screen.Home() }

        val startDaily = routes && intent.getBooleanExtra("daily", false)
        // First run: the onboarding questions, unless a debug route asked for a screen. A new kid skips them and lands in
        // the first Playground game (Back from it goes to the Learn path).
        val opening = when {
            // 2.6: a parent asked for "Let's hear you play" when adding this kid: it comes before the first stone, once.
            newKid && first == Screen.Home() && !startDaily && prefs.placementCheckPending && com.bigpapituna.sharpears.engine.family.PlacementCheck.offered(profile.level.forKid) -> {
                prefs.placementCheckPending = false
                Screen.Placement(profile.level.forKid)
            }
            newKid && first == Screen.Home() && !startDaily ->
                KidMap.next(path, prefs::lessonDone, kidStore.finales(), pace = kidStore.pace(), stars = kidStore::stars)?.let { Screen.Lesson(it, Screen.Home(HomeTab.LEARN)) } ?: Screen.Home()
            !progress.onboardingDone && first == Screen.Home() && !startDaily -> Screen.Onboarding
            else -> first
        }
        if (newKid) Log.d(TAG, "profiles: new kid ${profile.id} opens on ${(opening as? Screen.Lesson)?.lesson?.id}")
        // A recreate reopens the screen that was open (see routeOf); a route that no longer resolves opens as a launch would.
        val reopened = restoreRoute?.takeIf { fromLaunch }?.let { r ->
            runCatching { screenAt(r, prefs, profile.isKid, profile.mapHome) }.onSuccess { Log.d(TAG, "screen restored: $r") }
                .onFailure { Log.d(TAG, "screen not restored ($r): $it") }.getOrNull()
        }
        restoreRoute = null
        // A parent brief action for this person, asked while someone else was open ([briefNav]): the app opens on its screen.
        val requested = pendingOpen?.takeIf { it.first == profile.id }?.second
        pendingOpen = null
        // --es midi "<script>": a pretend keyboard plays into the first lesson step (see MidiDevices.debugPlay).
        if (routes && intent.getStringExtra("midi") != null) midi.debugConnect()
        intent.getStringExtra("say")?.takeIf { routes }?.let { line ->
            android.os.Handler(mainLooper).postDelayed({ voice.speak(line) }, 1500)
        }

        // A launch (onCreate, the picker, a debug route through onNewIntent) starts a fresh composition; a profile's kind
        // changing keeps the screen it is on.
        val opened = if (fromLaunch) ++launches else launches
        setContent { androidx.compose.runtime.key(profile.id, opened) {
            var screen by remember { mutableStateOf(requested ?: reopened ?: opening) }
            androidx.compose.runtime.SideEffect {
                val shown = screen
                shownRoute = if (shown is Screen.ArcadeRound && shown.seed == arcadeEnded) routeOf(shown.back) else routeOf(shown)
                com.bigpapituna.sharpears.analytics.Tester.screen(shownRoute!!)
            }
            // A parent brief action's screen for the person open now ([briefNav]: Replay, Practise together).
            val asked = navRequest.value
            LaunchedEffect(asked) { if (asked != null) { voice.stop(); screen = asked; navRequest.value = null } }
            // Practise together with nothing chosen waits on the songs; leaving them without picking one drops it.
            LaunchedEffect(screen) {
                val sc = screen
                val onSongs = sc is Screen.KidSongs || sc is Screen.Player || (sc is Screen.Home && sc.tab == HomeTab.SONGS && !profile.mapHome)
                if (!onSongs && duetNext.value != null) { duetNext.value = null; Log.d(TAG, "brief: the duet's song pick was left") }
            }
            KeepScreenOnWhile(mic, stage = keepsScreenOn(screen))
            val current by profiles.currentFlow.collectAsState()
            val roster by profiles.rosterFlow.collectAsState()
            // The layouts (2.8, engine/nav/Layout.kt and Simple.kt): this person's choice (2.7's Simple switch carried over), or
            // the default from their piano level until they choose at launch; a kid's is Full (nothing cut, never asked).
            val chosenLayout = if (prefs.version.intValue >= 0) prefs.layout else null
            // 2.14: a big kid is in Simple (their own choice holds), a little kid in Full (engine/nav/Simple.kt by audience).
            val layout = com.bigpapituna.sharpears.engine.nav.Simple.layoutOf(current.audience, chosenLayout, current.level)
            val layoutMode = remember(layout) {
                com.bigpapituna.sharpears.ui.LayoutMode(layout) { l ->
                    prefs.layout = l
                    Briefs.setting("layout", l.wire)
                    Log.d(TAG, "layout: ${l.wire} for ${profile.id}")
                }
            }
            LaunchedEffect(layout) { com.bigpapituna.sharpears.analytics.Tester.look(layout.wire) }      // 2.15: `layout` with every tester event
            // Every screen can report XP events through LocalProgress, and read who is playing through LocalProfile; the
            // pup's world (and the outfit every base-pose pup wears) through LocalWorld.
            // 2.14: the pup's voice for his tap-to-replay line on a finish screen (a kid's; said once per screen).
            val pupVoice = remember(voice) { { line: String -> voice.speak(line, interrupt = true) } }
            androidx.compose.runtime.CompositionLocalProvider(LocalProgress provides progress, LocalProfile provides current,
                com.bigpapituna.sharpears.ui.pup.LocalPupVoice provides pupVoice,
                LocalProfiles provides profiles, LocalParentGate provides gate, LocalWorld provides world, LocalPupOutfit provides world.state.outfit,
                com.bigpapituna.sharpears.ui.brief.LocalBriefNav provides briefNav, com.bigpapituna.sharpears.ui.LocalLayout provides layoutMode,
                com.bigpapituna.sharpears.ui.LocalMic provides mic) {
            SharpEarsTheme(dark = playerShown(screen) || isSystemInDarkTheme()) {
                // Every touch anywhere wakes the pups (their idle clock restarts); it takes nothing from the screens.
                Surface(Modifier.fillMaxSize().paper().wakesPup(), color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground) {   // textured page (ui/Backdrop.kt)
                    var libraryVersion by remember { mutableIntStateOf(0) }       // bumps after an import
                    var importError by remember { mutableStateOf<String?>(null) }
                    var importInto by remember { mutableStateOf<SongMeta?>(null) }
                    // My songs (2.8.1): a picked file waiting in the title dialog, or the kind line it was refused with.
                    var mySongDraft by remember { mutableStateOf<com.bigpapituna.sharpears.ui.MySongDraft?>(null) }
                    var mySongRefused by remember { mutableStateOf<String?>(null) }
                    // "Practice today" just finished: parts done, minutes, days in a row, for the celebration.
                    var dailyDone by remember { mutableStateOf<Triple<Int, Long, Int>?>(null) }
                    // 2.14: "I know these chords" (ui/chords/KnownChordsSheet.kt), from Today's offer or an empty list in Songs.
                    var knownChordsOpen by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    // Settings keeps its place and "Show all settings" while one of its own pages is open (Privacy, Licences,
                    // Mic setup) and starts at the top when it is opened again (2.8.4, QA D14: the screen is disposed on the
                    // way to Privacy, and its scroll went with it).
                    val settingsScroll = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
                        androidx.compose.foundation.ScrollState(0)
                    }
                    val settingsShowAll = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                    fun openSettings(back: Screen) {
                        scope.launch { settingsScroll.scrollTo(0) }
                        settingsShowAll.value = false
                        screen = Screen.Settings(back)
                    }
                    val songs = remember(libraryVersion) { repo.all() }

                    // Each library song yields a "Chords of <song>" lesson pack (DESIGN 4.3), labeled off the main thread.
                    // The same bar labels drive the chord names on the falling notes (ChordTrack per song id).
                    // A song that does not load (a damaged file) is skipped and logged; the parser and the labeler are
                    // bounded (2.8.3), so no file can hold up the rest at launch.
                    LaunchedEffect(libraryVersion) {
                        val labeled = withContext(Dispatchers.Default) {
                            val t0 = System.currentTimeMillis()
                            songs.filter(repo::available).mapNotNull { m ->
                                runCatching {
                                    val song = repo.load(m)
                                    val bars = labelBarsAligned(song)
                                    Triple(m.id, songPack(song, bars = bars), ChordTrack(bars, barMs(song)))
                                }.onFailure { Log.d(TAG, "song pack ${m.id} skipped: $it") }.getOrNull()
                            }.also { Log.d(TAG, "song packs: ${it.size} songs read and labelled in ${System.currentTimeMillis() - t0} ms") }
                        }
                        lessons.songPacks.value = labeled.map { it.second }.filter { it.items.isNotEmpty() }
                        lessons.songChords.value = labeled.filter { !it.third.isEmpty }.associate { it.first to it.third }
                        Log.d(TAG, "lessons: ${lessons.songPacks.value.size} song packs " + lessons.songPacks.value.joinToString { "${it.id}=${it.items.size}" })
                    }

                    fun openSong(m: SongMeta, back: Screen = Screen.Home()) {
                        val song = try { repo.load(m) } catch (e: Exception) { importError = "Could not open ${m.title}: ${e.message}"; return }
                        val p = prefs.progress(m.id)
                        prefs.lastSongId = m.id; prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.Song(m.id))
                        Log.d(TAG, "song ${song.id}: ${song.notes.size} notes, ${song.groups.size} groups, ${song.durationMs} ms, " +
                            "right=${song.notes.count { it.hand == Hand.RIGHT }} left=${song.notes.count { it.hand == Hand.LEFT }} resume=${p.lastPositionMs}")
                        // Practise together (the parent brief) with nothing chosen: the song picked now is the duet.
                        duetNext.value?.takeIf { it.whoId == profile.id }?.let { d ->
                            duetNext.value = null
                            screen = Screen.Player(song, m, briefStart(profile, prefs, duet = true, partnerId = d.partnerId), 0.0, back)
                            return
                        }
                        // A kid starts a two-hand song on the tune (the right hand), as the grown-ups' hands tip advises.
                        val hands = if (profile.kidSkin && song.notes.any { it.hand == Hand.RIGHT }) Practice.RIGHT else Practice.BOTH
                        screen = Screen.Player(song, m, PlayerStart(Mode.WAIT, hands, mic = prefs.micDefault && mic.hasPermission()),
                            if (p.completed) 0.0 else p.lastPositionMs.toDouble(), back)
                    }
                    // "Practice today": due chords, the next lesson, then the Continue song's worst bar (or the song).
                    // 2.14 (engine/nav/DayOne.kt): the easy first song only when [easy]; someone who said some or a lot is not
                    // handed Twinkle before they play a song of their own.
                    fun continueMeta(easy: Boolean = true) = songs.firstOrNull { it.id == prefs.lastSongId && repo.available(it) }
                        ?: songs.firstOrNull { easy && it.difficulty == "easy" && repo.available(it) }
                    // The placement level the day-one rules read (a kid's answer is a kid's level).
                    fun placed() = profiles.current.let { if (it.isKid) it.level.forKid else it.level }
                    // The easy first song (Twinkle) as a default: a little kid's songs, a beginner grown-up; never some or a lot
                    // (engine/nav/DayOne.kt), nor a big kid, whose first songs are the songs by chords.
                    fun easyFirstSong() = profile.mapHome || (com.bigpapituna.sharpears.engine.nav.DayOne.easySongDefault(placed()) && !profile.bigKid)
                    // A kid who reached today's goal hears goodnight once a run or a part is over (never mid-run; lessons
                    // say it themselves): the pup yawns, today's paw lands, songs and the Jam stay open.
                    var goodbye by remember { mutableStateOf(false) }
                    // A kid who finished Beginner is offered grown-up mode once, on their Learn path.
                    var grownUpOffered by remember { mutableStateOf(bedtime.grownUpOffered) }
                    // 2.14, a kid's daily time (engine/skin/TimeCap.kt) and wind down's "Close Songs and the Jam too" (Priya's P6):
                    // once either closes the day, what was running ends kindly (a lesson at its next step, a song at its end, at
                    // the latest TimeCap.GRACE_MS on; a toy or a game at once), the pup says goodbye, and the doors say
                    // "Tomorrow" (engine/skin/KidDoors). The time is screen time today, from the one minutes ledger.
                    var timeVersion by remember { mutableIntStateOf(0) }
                    var capSince by remember { androidx.compose.runtime.mutableLongStateOf(-1L) }    // ForegroundClock ms the time ran out
                    var songsSince by remember { androidx.compose.runtime.mutableLongStateOf(-1L) }  // ... and the songs closed
                    var capGoodbye by remember { mutableStateOf(false) }
                    if (profile.isKid) LaunchedEffect(profile.id, timeVersion) {
                        while (true) {
                            val cap = bedtime.capMin
                            val ms = if (cap > 0) withContext(Dispatchers.IO) { runCatching { Briefs.screenMsToday(profile.id) }.getOrDefault(0L) } else 0L
                            val reached = com.bigpapituna.sharpears.engine.skin.TimeCap.reached(cap, ms)
                            if (reached && capSince < 0) { capSince = com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs(); Log.d(TAG, "time: ${profile.id}'s daily time is up (${ms / 1000} s on screen of $cap min)") }
                            else if (!reached && capSince >= 0) { capSince = -1L; Log.d(TAG, "time: ${profile.id}'s daily time is open again") }
                            kotlinx.coroutines.delay(if (cap > 0) 5_000L else 30_000L)
                        }
                    }
                    val songsAsleep = profile.isKid && timeVersion >= 0 && progress.version.intValue >= 0 && !com.bigpapituna.sharpears.engine.skin.Bedtime.songsOpen(true,
                        progress.secondsToday(com.bigpapituna.sharpears.data.AppClock.today()), progress.goalMinutes, bedtime.bedtime, bedtime.closeSongs)
                    LaunchedEffect(songsAsleep) { songsSince = if (songsAsleep) com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs() else -1L }
                    val kidDay = com.bigpapituna.sharpears.engine.skin.KidDay(capReached = capSince >= 0, songsAsleep = songsAsleep)
                    /** The day is closed for this kid: home, and the goodbye (today's time, or wind down's goodnight). */
                    fun closedForToday(why: String) {
                        voice.stop()
                        screen = Screen.Home()
                        if (capSince >= 0) { capGoodbye = true; bedtime.capShownOn = com.bigpapituna.sharpears.data.AppClock.today() } else goodbye = true
                        Log.d(TAG, "time: $why, ${profile.id}'s day ends on the goodbye")
                    }
                    /** What the pup says at a door that waits for tomorrow. */
                    fun sayTomorrow() = voice.speak(if (capSince >= 0) com.bigpapituna.sharpears.engine.skin.TimeCap.line(current.name)
                        else com.bigpapituna.sharpears.engine.SessionWords.SONGS_ASLEEP, interrupt = true)
                    fun maybeGoodnight(after: String): Boolean {
                        val today = com.bigpapituna.sharpears.data.AppClock.today()
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
                    // A sticker tapped in My stickers' tray (2.5): the map opens in decorate mode holding it.
                    var decorateWith by remember { mutableStateOf<String?>(null) }
                    // A course lesson finished: progress, today's quest, its stars on the stone, and the pup's world (a sticker from
                    // the lesson's page; the egg laid or hatched).
                    fun lessonDone(l: CourseLesson, stars: Int): Reward {
                        prefs.markLessonDone(l.id); progress.record(ProgressEvent.LessonComplete(l.id))
                        if (com.bigpapituna.sharpears.engine.Comeback.parseLeft(prefs.openStep)?.first == l.id) prefs.openStep = null      // 2.14: finished, not left
                        kidStore.lessonPlayed(l.id)          // a lesson pinned as the next stone (the parent brief) has been played
                        quest(QuestEvent.LessonDone(l.id))
                        kidStore.recordStars(l.id, stars)
                        if (profile.mapHome) Finale.trigger(l.id, kidStore.finales())?.let { page ->
                            pendingFinale = page.id
                            Log.d(TAG, "kid: ${l.id} ends ${page.id}, its finale is next")
                        }
                        return world.lessonComplete(l.id, KidPages.dropPlace(l.id), starter = profile.kidSkin)      // 2.14: a kid's first lesson gives the starter wearable and a placed sticker
                    }
                    // The next lesson to suggest: a kid's next stone on the map, a grown-up's Learn path; a lesson a grown-up pinned
                    // from the parent brief comes first while its day lasts (engine/kid/Pace.kt).
                    fun nextLesson(): CourseLesson? = if (profile.mapHome) KidMap.next(path, prefs::lessonDone, kidStore.finales(), pace = kidStore.pace(), stars = kidStore::stars)
                        else kidStore.pace().pin?.let(path::lesson) ?: path.next { prefs.lessonDone(it) }
                    // Where Songs is: the kid's own list for a kid, the Songs tab for a grown-up.
                    fun songsScreen(back: Screen = Screen.Home()): Screen = if (profile.mapHome) Screen.KidSongs(back) else Screen.Home(HomeTab.SONGS)
                    // The chord packs the daily review draws from: until Full only the six beginner chords (Advanced keeps the song
                    // packs whole; engine/nav/Simple.kt).
                    fun chordCurriculum() = lessons.curriculum().let { com.bigpapituna.sharpears.engine.Curriculum(com.bigpapituna.sharpears.engine.nav.Simple.packs(it.packs, layout)) }
                    /**
                     * Today's next challenge (2.14) for a grown-up placed at Some or A lot, or whose [next] lesson is past the first
                     * three units: a song at their level in Perform, a staff drill or chord changes, a kind a day in turn.
                     */
                    fun nextChallenge(next: CourseLesson?): com.bigpapituna.sharpears.engine.nav.Challenge? {
                        val nc = com.bigpapituna.sharpears.engine.nav.NextChallenge
                        val main = COURSE.filter { !com.bigpapituna.sharpears.engine.playground.isPlaygroundLevel(it.id) }
                        val unit = next?.let { l -> main.indexOfFirst { lv -> lv.lessons.any { it.id == l.id } } }?.takeIf { it >= 0 } ?: main.size
                        if (!nc.eligible(current.isKid, current.level, unit)) return null
                        val tier = nc.tier(current.level, unit)
                        val pool = songs.filter { it.difficulty == nc.difficulty(tier) && repo.available(it) }.mapNotNull { m ->
                            songTempo(m)?.let { (bpm, min) -> com.bigpapituna.sharpears.engine.nav.NextChallenge.SongChoice(m.id, m.title, m.difficulty, bpm, min, threeStars = prefs.progress(m.id).bestAccuracy >= 100) }
                        }
                        val staff = com.bigpapituna.sharpears.engine.SightLevels.staffOpen(false, current.reader, current.level, layout)
                        return nc.pick(tier, com.bigpapituna.sharpears.data.AppClock.today().toEpochDay(), current.id.hashCode().toLong(), pool, staff, prefs::sightDrillBest)
                            .also { Log.d(TAG, "today: challenge (tier $tier, unit $unit, ${current.level}) ${it?.title}, ${it?.target}") }
                    }
                    fun todayPlan(): SessionPlan {
                        val queue = chordCurriculum().queue(lessons.mastery(), System.currentTimeMillis())
                        // After a kid's goodnight the lessons sleep until tomorrow: no lesson part (songs stay open).
                        val asleep = profile.isKid && bedtime.goodnightOn == com.bigpapituna.sharpears.data.AppClock.today()
                        val next = if (asleep) null else nextLesson()
                        // A kid's session is short (KIDS-PLAN 3.9): the next game or lesson and a whole song, no chord
                        // review and no bar on a loop.
                        // A song that does not load (a damaged file) is left out, so the session never opens it.
                        // 2.14 (engine/nav/DayOne.kt): day one follows placement. A beginner's plan opens on the lesson and has no
                        // chord review until two lessons are played; some and a lot get no easy first song in it.
                        val level = placed()
                        val song = continueMeta(easy = easyFirstSong())?.let { m ->
                            val minutes = runCatching { repo.load(m).durationMs / 60_000.0 }
                                .onFailure { Log.d(TAG, "today: ${m.id} left out ($it)") }.getOrNull() ?: return@let null
                            SessionSong(m.id, m.title, minutes, if (profile.mapHome) null else prefs.troubleBars(m.id).firstOrNull())
                        }
                        val played = path.counted.count { prefs.lessonPlayed(it.id) }
                        val chords = if (profile.mapHome) emptyList() else com.bigpapituna.sharpears.engine.nav.DayOne.chordsFor(level, played,
                            queue.filterIsInstance<LessonItem.ChordItem>().map { it.id })
                        // 2.14 (depth): a grown-up past the first steps gets one thing at their level (engine/nav/NextChallenge.kt);
                        // no kid, a big kid included (NextChallenge.eligible asks isKid).
                        val challenge = if (profile.isKid) null else nextChallenge(next)
                        return planSession(chords, next?.let { it.id to it.title }, song,
                            lessonFirst = !profile.mapHome && com.bigpapituna.sharpears.engine.nav.DayOne.lessonFirst(level), challenge = challenge)
                    }
                    fun startDaily() {
                        val plan = todayPlan()
                        Log.d(TAG, "daily: plan ${plan.aboutLabel}: " + plan.parts.joinToString { it.done })
                        if (!plan.isEmpty) screen = Screen.Daily(plan, 0, between = true, startedAt = System.currentTimeMillis(), done = emptyList(),
                            practiceAt = progress.secondsToday())
                        if (!plan.isEmpty) com.bigpapituna.sharpears.analytics.Events.dailyPlanStarted(plan.parts.size)
                    }
                    LaunchedEffect(Unit) { if (startDaily) startDaily() }
                    fun finishDaily(s: Screen.Daily, done: List<SessionPart>, skipped: List<SessionPart> = s.skipped) {
                        // The practice the session added, as the goal line counts it (2.8.4, QA D5: the wall clock since Start,
                        // pauses and all, said "2 min" beside "0 of 10 min today"). Each part writes its seconds before it ends.
                        val secs = (progress.secondsToday() - s.practiceAt).coerceAtLeast(0).toLong()
                        com.bigpapituna.sharpears.analytics.Events.dailyPlanFinished(done.size, s.plan.parts.size, secs, complete = done.isNotEmpty() && (profile.mapHome || s.plan.through(done, skipped)))
                        // Nothing done (every part skipped or left at once): no summary, the panel stays as it was.
                        if (done.isEmpty()) { Log.d(TAG, "daily: ended with nothing done"); screen = Screen.Home(); return }
                        // A grown-up's plan is done for today only when every part was played or skipped (QA D5: a plan left
                        // after one part said "Done for today!"). Left part way, Today keeps the plan and Start. A kid's session
                        // ends on goodnight or a lesson's own way out by design, so anything a kid did is their day.
                        if (!profile.mapHome && !s.plan.through(done, skipped)) {
                            Log.d(TAG, "daily: ended part way, ${done.size} of ${s.plan.parts.size} parts done, ${skipped.size} skipped, $secs s; the plan stays open")
                            screen = Screen.Home(); return
                        }
                        val summary = sessionSummary(done, secs)
                        prefs.saveDailySummary(com.bigpapituna.sharpears.data.AppClock.today(), summary)
                        progress.record(ProgressEvent.DailySession(done.size))
                        Log.d(TAG, "daily: $summary")
                        voice.speak(if (profile.kidSkin) com.bigpapituna.sharpears.engine.SessionWords.kidSessionDone(profile.name)
                            else "Done for today: " + summary.removePrefix("Done today: ").replace(" min.", " minutes."))
                        dailyDone = Triple(done.size, secs / 60, progress.streak().streak)
                        screen = Screen.Home()
                    }
                    /** On to the next part: this one [completed], passed over with Skip ([skip]), or left part way (neither). */
                    fun advanceDaily(s: Screen.Daily, completed: Boolean, as_: SessionPart? = null, skip: Boolean = false) {
                        val part = as_ ?: s.plan.parts[s.index]
                        val done = if (completed) s.done + part else s.done
                        val skipped = if (skip && !completed) s.skipped + s.plan.parts[s.index] else s.skipped
                        Log.d(TAG, "daily: part ${s.index + 1} ${if (completed) "done" else if (skip) "skipped" else "left"}: ${part.done}")
                        val next = s.copy(done = done, skipped = skipped, held = false)
                        // A kid's goal reached with this part: the session ends on goodnight (a lesson says it itself).
                        if (completed && part !is SessionPart.Lesson && maybeGoodnight("a Practice today part")) finishDaily(next, done, skipped)
                        else if (s.index + 1 >= s.plan.parts.size) finishDaily(next, done, skipped)
                        else screen = next.copy(index = s.index + 1, between = true)
                    }

                    // Ear training: a tick per round, a game's credit at its end, the workout like a game plus its practice time.
                    fun earEvent(e: EarEvent) {
                        fun practised(secs: Int) {
                            if (secs <= 0) return
                            prefs.addPractice(com.bigpapituna.sharpears.data.AppClock.today(), secs)
                            progress.record(ProgressEvent.Practice(com.bigpapituna.sharpears.data.AppClock.now(), secs))
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
                                Log.d(TAG, "import failed: ${e.message}"); importError = e.message; com.bigpapituna.sharpears.analytics.Errors.report("song_import", e)
                            } catch (e: Exception) {
                                Log.d(TAG, "import failed: $e"); importError = "Import failed: ${e.message}"; com.bigpapituna.sharpears.analytics.Errors.report("song_import", e)
                            }
                        }
                    }

                    // My songs (2.8.1): the picked file's bytes are read once (the URI is never kept) and read for the title
                    // dialog; nothing is saved until Add (engine/MySongs.kt makes it easy, data/SongRepository.kt keeps it).
                    val pickMySong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        scope.launch {
                            try {
                                val draft = withContext(Dispatchers.IO) {
                                    val (bytes, name) = repo.readPicked(uri)
                                    com.bigpapituna.sharpears.ui.MySongDraft(bytes, com.bigpapituna.sharpears.engine.MySongs.scan(bytes, name))
                                }
                                Log.d(TAG, "my songs: picked ${draft.bytes.size} bytes, tune ${draft.scan.melodyNotes} notes, left hand ${draft.scan.leftNotes} notes")
                                mySongDraft = draft
                            } catch (e: com.bigpapituna.sharpears.engine.MySongs.Refused) {
                                Log.d(TAG, "my songs: refused, ${e.problem}"); mySongRefused = e.problem.line
                                com.bigpapituna.sharpears.analytics.Errors.report("midi_refused", e, mapOf("problem" to e.problem))
                            } catch (e: Exception) {
                                Log.d(TAG, "my songs: pick failed, $e"); mySongRefused = com.bigpapituna.sharpears.engine.MySongs.Problem.UNREADABLE.line
                                com.bigpapituna.sharpears.analytics.Errors.report("midi_pick", e)
                            }
                        }
                    }

                    // 2.9, retention (ui/Retention.kt): the tester event for each, kind only.
                    fun retentionEvent(name: String) = com.bigpapituna.sharpears.analytics.Tester.retention(name, profile.isKid)
                    // Grown-ups: the finish screens' "Tomorrow:" line, from tomorrow's plan (the Continue song's hands tip, the next
                    // lesson, or the song again). Called off the main thread.
                    fun tomorrowLine(): String? {
                        val m = continueMeta()
                        val song = m?.let { runCatching { repo.load(it) }.getOrNull() }
                        val tip = if (m != null && song != null) com.bigpapituna.sharpears.engine.handsTip(
                            song.notes.any { it.hand == Hand.LEFT } && song.notes.any { it.hand == Hand.RIGHT }, prefs.handsDone(m.id), prefs.progress(m.id).completed) else null
                        return com.bigpapituna.sharpears.engine.Tomorrow.line(m?.title, tip, nextLesson()?.title)
                    }
                    val retention = remember {
                        // 2.14: the wrapped gift is the map's (a little kid); the tabs get the "Tomorrow:" line (Ages.gift).
                        com.bigpapituna.sharpears.ui.Retention(com.bigpapituna.sharpears.engine.family.Ages.gift(profile.audience),
                            goalMet = { DailyGoal.met(progress.secondsToday(com.bigpapituna.sharpears.data.AppClock.today()), progress.goalMinutes) },
                            tomorrow = ::tomorrowLine,
                            // Kids only: the gift wrapped once the goal is met (one a day, never two waiting).
                            wrap = { profile.mapHome && world.wrapGift(goalMet = true).also { if (it) retentionEvent(com.bigpapituna.sharpears.engine.tester.Retention.SURPRISE_WRAPPED) } },
                            wrappedToday = { com.bigpapituna.sharpears.engine.world.Surprise.wrappedToday(world.state.gift, com.bigpapituna.sharpears.data.AppClock.today()) },
                            say = { voice.speak(it) })
                    }
                    // The comeback (engine/Comeback.kt): a return after three full days or more, greeted once per return.
                    val comeback = remember { com.bigpapituna.sharpears.engine.Comeback.due(progress.practiceLog().filterValues { it > 0 }.keys, com.bigpapituna.sharpears.data.AppClock.today(), playStore.comebackGreeted) }
                    var comebackOpen by remember { mutableStateOf(comeback != null) }
                    fun comebackGreeted() {
                        val r = comeback ?: return
                        if (playStore.comebackGreeted == r.lastPlayed) return
                        playStore.comebackGreeted = r.lastPlayed
                        Briefs.log { com.bigpapituna.sharpears.engine.brief.BriefEvent.Comeback(it, r.daysAway) }
                        retentionEvent(com.bigpapituna.sharpears.engine.tester.Retention.COMEBACK_SHOWN)
                        Log.d(TAG, "comeback: ${profile.id} back after ${r.daysAway} days away (last played ${r.lastPlayed})")
                    }
                    // A rest day that kept the streak (the day after it was played): counted once for the tester dashboard.
                    LaunchedEffect(progress.version.intValue) {
                        val days = progress.practiceLog().filterValues { it > 0 }.keys
                        val used = progress.streak().restDays.filter { it.plusDays(1) in days }.maxOrNull() ?: return@LaunchedEffect
                        if (playStore.restDayCounted?.let { !used.isAfter(it) } == true) return@LaunchedEffect
                        playStore.restDayCounted = used
                        retentionEvent(com.bigpapituna.sharpears.engine.tester.Retention.REST_DAY_USED)
                        Log.d(TAG, "rest day: $used kept ${profile.id}'s streak")
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
                    androidx.compose.runtime.CompositionLocalProvider(com.bigpapituna.sharpears.ui.LocalLeaving provides (s::class != screen::class),
                        com.bigpapituna.sharpears.ui.LocalRetention provides retention) {
                    SharpEarsTheme(dark = playerShown(s) || systemDark) {
                    when (s) {
                        is Screen.Player -> {
                            // A run that reached its end (a song, a progression) may be where a kid's goal was reached.
                            var ranToEnd by remember { mutableStateOf(false) }
                            // A kid leaving a library song (or a pop song, 2.6) played to its end: the pup's card first (the parent brief, 2.4).
                            val cardStep = com.bigpapituna.sharpears.ui.brief.rememberKidCardStep(current, ready = profile.isKid && ranToEnd && (s.meta != null || s.band != null))
                            Box(Modifier.fillMaxSize()) {
                                // 2.14: a kid's song that ends after today's time ran out: its recap a moment, then the goodbye.
                                if (profile.isKid) LaunchedEffect(ranToEnd, capSince) {
                                    if (ranToEnd && capSince >= 0) { kotlinx.coroutines.delay(4_000L); closedForToday("a song ended") }
                                }
                                PlayerScreen(s.song, s.meta, sampler, midi, mic, voice, prefs, samplerReady.value,
                                    s.startMs, dump, s.start, onBack = cardStep.then("All done!") { screen = s.back; if (ranToEnd) maybeGoodnight("a song") },
                                    onFinished = { ranToEnd = true },
                                    chordTrack = s.meta?.let { lessons.songChords.value[it.id] } ?: s.chords,
                                    band = s.band)
                                if (profile.isKid) SharpEarsTheme(dark = isSystemInDarkTheme()) {
                                    com.bigpapituna.sharpears.ui.brief.KidCardStepHost(cardStep, current, say = { voice.speak(it, interrupt = true) })
                                }
                            }
                        }
                        is Screen.Lesson -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            val l = s.lesson
                            // A grown-up's Today offers it again as Continue (2.7).
                            LaunchedEffect(l.id) { if (!profile.mapHome) prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson(l.id)) }
                            // A kid (2.2): "one more" is the next stone on the map; a page's last lesson, finished the first time,
                            // leads to the page's finale instead (and today's goodbye waits until after it).
                            val finaleAhead = profile.mapHome && remember(l.id) { Finale.trigger(l.id, kidStore.finales()) != null }
                            val next = if (finaleAhead) null else if (profile.mapHome) KidMap.next(path, { it == l.id || prefs.lessonDone(it) }, kidStore.finales(),
                                pace = kidStore.pace().let { if (it.pin == l.id) it.copy(pin = null) else it }, stars = kidStore::stars)
                                else path.after(l) { prefs.lessonDone(it) }
                            val leave = {
                                val f = pendingFinale
                                if (f != null) { pendingFinale = null; voice.stop(); screen = Screen.Finale(f) } else screen = s.back
                            }
                            BackHandler { voice.stop(); leave() }
                            androidx.compose.runtime.key(l.id, navCount.intValue) { LessonScreen(l, s.step, LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), s.inject,
                                midiScript = s.midiScript, startScene = s.scene,
                                onStep = {
                                    // 2.14: the comeback opens a lesson left part way at its step (engine/Comeback.kt resume).
                                    if (!prefs.lessonDone(l.id)) prefs.openStep = com.bigpapituna.sharpears.engine.Comeback.encodeLeft(l.id, it)
                                    // 2.14: today's time ran out: the lesson ends kindly at its next step (KidDoors, TimeCap).
                                    if (it != s.step) { if (profile.isKid && capSince >= 0) closedForToday("a lesson step ended") else screen = s.copy(step = it, inject = null, midiScript = null, scene = 0) }
                                },
                                onSong = l.songId?.let { id -> { songs.firstOrNull { it.id == id && repo.available(it) }?.let { openSong(it, back = s) } } },
                                onDone = { stars -> lessonDone(l, stars) },
                                // 2.14: "Try for 3 stars" opens this lesson again at its first graded step (a new key resets it);
                                // not once a kid's time is up (the same screen class, so the doors would not see a new lesson).
                                onRetry = { step -> if (profile.isKid && capSince >= 0) closedForToday("a lesson ended") else { navCount.intValue++; screen = Screen.Lesson(l, s.back, step) } },
                                onNext = if (finaleAhead) leave else next?.let { n -> { if (profile.isKid && capSince >= 0) closedForToday("a lesson ended") else screen = Screen.Lesson(n, s.back) } },
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
                                Interstitial(part.next, voice, onGo = { screen = s.copy(between = false, held = false) },
                                    onSkip = { voice.stop(); advanceDaily(s, completed = false, skip = true) },
                                    onEnd = { voice.stop(); finishDaily(s, s.done) },
                                    parts = s.plan.parts, index = s.index, done = s.done, kid = profile.kidSkin, countdown = !s.held)
                            } else when (part) {
                                is SessionPart.Chords -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    // Chord practice does not run the player, so its time is counted here: when the part ends
                                    // (before the session sums its minutes) and, for anything after that, when it closes.
                                    val chordClock = remember(s.index) { longArrayOf(com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs()) }
                                    fun creditChords() {
                                        val secs = ((com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs() - chordClock[0]) / 1000).toInt()
                                        if (secs <= 0) return
                                        chordClock[0] += secs * 1000L
                                        prefs.addPractice(com.bigpapituna.sharpears.data.AppClock.today(), secs)
                                        progress.record(ProgressEvent.Practice(com.bigpapituna.sharpears.data.AppClock.now(), secs))
                                    }
                                    // Back is "Skip the rest": the chords answered count, and the session goes on (left open, it took
                                    // over Learn chords afterwards). With none answered the part counts as skipped.
                                    fun endChords(n: Int) {
                                        creditChords()
                                        part.itemIds.take(n).forEach { quest(QuestEvent.ChordReviewed(it)) }
                                        advanceDaily(s, completed = n > 0, part.copy(itemIds = part.itemIds.take(n)), skip = n == 0)
                                    }
                                    BackHandler { val n = lessons.sessionIndex; lessons.endSession(); endChords(n) }
                                    androidx.compose.runtime.DisposableEffect(s.index) {
                                        val f = ::creditChords; Briefs.addFlusher(f)        // 2.14: the session's checkpoint and end ask too
                                        onDispose { Briefs.removeFlusher(f); creditChords() }
                                    }
                                    QuietWrongKeys(mic)
                                    androidx.compose.foundation.layout.Column {
                                        Text("Your plan: chords", style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(start = 20.dp, top = 20.dp))
                                        DailyChords(lessons, sampler, midi, mic, prefs.micDefault, part.itemIds, inject = debugStart.inject.takeIf { startDaily }, sound = { prefs.sound },
                                            plain = com.bigpapituna.sharpears.engine.nav.DayOne.plainChords(placed()),
                                            onEnd = ::endChords)
                                    }
                                }
                                is SessionPart.Lesson -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    val l = COURSE.flatMap { it.lessons }.first { it.id == part.lessonId }
                                    LaunchedEffect(l.id) { if (!profile.mapHome) prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson(l.id)) }
                                    // Back (and the arrow) goes back to this part's line, waiting for Go, Skip or End for today
                                    // (2.8.4, QA D9: the arrow said "Back to lessons" and moved on); a finished lesson goes on.
                                    fun backToPlan() { voice.stop(); if (prefs.lessonDone(l.id)) advanceDaily(s, true) else screen = s.copy(between = true, held = true) }
                                    BackHandler { backToPlan() }
                                    androidx.compose.runtime.key(l.id) { LessonScreen(l, 0, LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), null,
                                        onStep = {
                                            if (!prefs.lessonDone(l.id)) prefs.openStep = com.bigpapituna.sharpears.engine.Comeback.encodeLeft(l.id, it)
                                            // 2.14 merge: once a kid's time is up (a big kid has Practice today), the plan's lesson ends at
                                            // its next step, as a lesson opened from Learn does; TimeCap.GRACE_MS stays the latest.
                                            if (profile.isKid && capSince >= 0) closedForToday("a plan's lesson step ended")
                                        },
                                        onSong = null,
                                        onDone = { stars -> lessonDone(l, stars) },
                                        onNext = null,
                                        onBack = { voice.stop(); advanceDaily(s, prefs.lessonDone(l.id)) },
                                        onArrow = ::backToPlan, arrowLabel = "Back to your plan",
                                        onSongs = { voice.stop(); finishDaily(s, s.done + part); screen = songsScreen() },
                                        onJam = { voice.stop(); finishDaily(s, s.done + part); screen = Screen.Play(Toy.JAM, Screen.Home()) }) }
                                }
                                is SessionPart.Loop, is SessionPart.Song -> {
                                    val id = if (part is SessionPart.Loop) part.songId else (part as SessionPart.Song).songId
                                    val m = songs.first { it.id == id }
                                    val song = remember(id) { repo.load(m) }
                                    // Played here, it is the Continue song (Songs said "Start here" after a whole session of it).
                                    LaunchedEffect(id) { prefs.lastSongId = id; prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.Song(id)) }
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
                                // 2.14: today's next challenge. A song or chord changes in Perform at the target tempo, the recap
                                // (stars, the timing panel) closing goes on; a staff drill, its two minutes, then on.
                                is SessionPart.Challenge -> when (val c = part.item) {
                                    is com.bigpapituna.sharpears.engine.nav.Challenge.SightRead -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                        var drilled by remember(s.index) { mutableStateOf(false) }
                                        fun back() { voice.stop(); if (drilled) advanceDaily(s, true) else screen = s.copy(between = true, held = true) }
                                        BackHandler { back() }
                                        com.bigpapituna.sharpears.ui.SightReadScreen(LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), c.level,
                                            onBack = ::back, open = listOf(com.bigpapituna.sharpears.engine.SightLevel.of(c.level)), goal = c.goal,
                                            midiScript = debugStart.midiScript.takeIf { startDaily },
                                            onDrillDone = { sc -> drilled = true; Log.d(TAG, "daily: challenge ${c.title} ${sc.label} (goal ${c.goal})"); advanceDaily(s, true) })
                                    }
                                    else -> {
                                        val perform = c as? com.bigpapituna.sharpears.engine.nav.Challenge.Perform
                                        val chords = c as? com.bigpapituna.sharpears.engine.nav.Challenge.ChordChange
                                        val m = perform?.let { p -> songs.first { it.id == p.songId } }
                                        val song = remember(s.index) {
                                            if (m != null) repo.load(m)
                                            else progressionSong(PROGRESSIONS[chords!!.progression], chords.key, chords.bpm.toDouble())
                                        }
                                        if (m != null) LaunchedEffect(m.id) { prefs.lastSongId = m.id; prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.Song(m.id)) }
                                        val partStart = remember(s.index) { System.currentTimeMillis() }
                                        var ranToEnd by remember(s.index) { mutableStateOf(false) }
                                        androidx.compose.runtime.key(s.index) {
                                            PlayerScreen(song, m, sampler, midi, mic, voice, prefs, samplerReady.value, 0.0,
                                                dump, PlayerStart(Mode.PERFORM, mic = prefs.micDefault && mic.hasPermission(), speed = perform?.speed,
                                                    midiScript = debugStart.midiScript.takeIf { startDaily }),
                                                onBack = { advanceDaily(s, completed = ranToEnd || System.currentTimeMillis() - partStart >= 60_000) },
                                                onFinished = { r ->
                                                    ranToEnd = true
                                                    val stars = r.recap?.let { com.bigpapituna.sharpears.engine.songStars(it) }
                                                    Log.d(TAG, "daily: challenge ${c.title}, ${c.target}: ${stars ?: "no"} stars" + if (stars == 3) ", met" else "")
                                                },
                                                onRecapClosed = { if (ranToEnd) advanceDaily(s, completed = true) },
                                                chordTrack = m?.let { lessons.songChords.value[it.id] })
                                        }
                                    }
                                }
                            }
                        }
                        is Screen.SightRead -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { voice.stop(); screen = s.back }
                            com.bigpapituna.sharpears.ui.SightReadScreen(LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), s.level, s.inject,
                                onBack = { screen = s.back }, midiScript = s.midiScript,
                                // 2.14: the staff levels for Advanced and Full, Some and A lot, and kids who read and have had lessons.
                                open = com.bigpapituna.sharpears.engine.SightLevels.open(current.isKid, current.reader, current.level, layout, bigKid = current.bigKid))
                        }
                        is Screen.EarTraining -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            QuietWrongKeys(mic)
                            // The hub handles Back itself: a game goes back to the hub, the hub to Learn.
                            EarTrainingScreen(sampler, midi, mic, voice, prefs.micDefault, onBack = { screen = s.back },
                                onReport = ::earEvent, sound = { prefs.sound }, start = s.start)
                        }
                        Screen.Onboarding -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            OnboardingScreen(progress, prefs, onReminderChanged = { com.bigpapituna.sharpears.reminder.Reminder.schedule(this@MainActivity) },
                                askReminder = profile.isOwner,
                                // 2.14: the owner is asked their name on the first card (optional); anyone else was named when they were added.
                                askName = profile.isOwner, onName = { n -> profiles.nameOwner(n) },
                                // How much they have played (2.6): the profile keeps it and the flow below re-plans the marks in place.
                                onLevel = { l -> profiles.update(profile.id) { it.copy(level = l) } },
                                onCheck = { l -> screen = Screen.Placement(l) }) { lessonId ->
                                // The suggested first lesson opens straight away; Back from it lands on Learn. Someone here for
                                // songs ("a lot") lands on the library, the rest on the course.
                                val songsFirst = com.bigpapituna.sharpears.engine.family.Placement.planOf(profiles.current).homeFirst == com.bigpapituna.sharpears.engine.family.HomeFirst.SONGS
                                screen = lessonId?.let { id -> COURSE.flatMap { it.lessons }.firstOrNull { it.id == id } }
                                    ?.let { Screen.Lesson(it, Screen.Home(HomeTab.LEARN)) } ?: Screen.Home(if (songsFirst) HomeTab.SONGS else HomeTab.LEARN)
                            }
                        }
                        // "Let's hear you play" (2.6): two or three tiny things, then where they begin; it counts as a first lesson.
                        is Screen.Placement -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            val plan = com.bigpapituna.sharpears.engine.family.Placement.planFor(s.said, profile.kind, profile.reader, profile.band)
                            val songsFirst = plan.homeFirst == com.bigpapituna.sharpears.engine.family.HomeFirst.SONGS
                            com.bigpapituna.sharpears.ui.family.PlacementCheckScreen(sampler, midi, mic, voice, samplerReady.value, current, s.said,
                                award = {
                                    // The first lesson's rewards, once: the XP event, three stars on its stone, the world's sticker and sparkles.
                                    if (kidStore.stars(com.bigpapituna.sharpears.engine.family.Placement.CHECK_ID) > 0) null else {
                                        progress.record(ProgressEvent.LessonComplete(com.bigpapituna.sharpears.engine.family.Placement.CHECK_ID))
                                        kidStore.recordStars(com.bigpapituna.sharpears.engine.family.Placement.CHECK_ID, 3)
                                        world.lessonComplete(com.bigpapituna.sharpears.engine.family.Placement.CHECK_ID, null)
                                    }
                                },
                                finishLabel = if (profile.mapHome) "To the map" else if (songsFirst) "Show me the songs" else "Start learning",
                                inject = s.inject, startTask = s.startTask,
                                // 2.14: someone who chose the screen's keys or MIDI is not asked for the microphone.
                                askMic = com.bigpapituna.sharpears.engine.nav.DayOne.checkAsksMic(progress.useMic, profile.isKid),
                                onDone = { begin, played ->
                                    Log.d(TAG, "placement: ${profile.id} begins at $begin (check ${if (played) "played" else "skipped"})")
                                    // Only a check played through moves the level: skipped or left, the level said stays (it is already
                                    // stored by the onboarding or the new-kid flow; the debug route's said level is never stored).
                                    if (played && begin != profiles.current.level) profiles.update(profile.id) { it.copy(level = begin) }
                                    val after = com.bigpapituna.sharpears.engine.family.Placement.planFor(if (played) begin else profiles.current.level, profile.kind, profile.reader, profile.band)
                                    screen = if (profile.mapHome) Screen.Home()
                                    else if (after.homeFirst == com.bigpapituna.sharpears.engine.family.HomeFirst.SONGS) Screen.Home(HomeTab.SONGS)
                                    else COURSE.flatMap { it.lessons }.firstOrNull { it.id == after.startLessonId }?.let { Screen.Lesson(it, Screen.Home(HomeTab.LEARN)) } ?: Screen.Home(HomeTab.LEARN)
                                })
                        }
                        is Screen.Settings -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                            // 2.14 (Priya's P4): while the gate is open, Lock now shuts it; a kid's Settings closes with it.
                            com.bigpapituna.sharpears.ui.family.LockNowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), onLocked = { if (profile.isKid) screen = s.back })
                            // A kid's Settings closes when the gate does (away 30 s, a switch).
                            val closings0 = remember { gate.closings }
                            if (profile.isKid) LaunchedEffect(gate.closings) { if (gate.closings != closings0) screen = s.back }
                            Box(Modifier.weight(1f)) {
                            SettingsScreen(prefs, songs, packageManager.getPackageInfo(packageName, 0).versionName ?: "", voice = voice,
                                onReminder = { com.bigpapituna.sharpears.reminder.Reminder.schedule(this@MainActivity) },
                                onMicSetup = { gate.guardForKids { screen = Screen.MicSetup(s) } },
                                onPrivacy = { screen = Screen.Privacy(s) }, onLicences = { screen = Screen.Licences(s) },
                                onBack = { screen = s.back },
                                onResetProgress = { earStore.reset(); practiceStore.clear(); theory.reset(); progress.clearPracticeLog(); Log.d(TAG, "progress: reset (prefs, ear, practice, theory, practice log; XP kept)") },
                                family = { FamilySettings(profiles, extras = false) }, showReminder = current.isOwner, mic = mic,
                                midiTiming = { com.bigpapituna.sharpears.ui.MidiTimingSetting(midi, debugScript = debugStart.midiScript) },
                                scroll = settingsScroll, showAllState = settingsShowAll)
                            }
                            }
                        }
                        is Screen.Privacy -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            PrivacyScreen(com.bigpapituna.sharpears.ui.micFacts(mic), onMicSetup = { gate.guardForKids { screen = Screen.MicSetup(s) } }, onBack = { screen = s.back },
                                mic = mic)
                        }
                        is Screen.Theory -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            com.bigpapituna.sharpears.ui.theory.TheoryScreen(sampler, voice, onBack = { voice.stop(); screen = s.back },
                                store = theory, soundOn = prefs.sound,
                                // Time in a lesson is practice; recorded first, so the lesson's XP lands after the minutes.
                                onPractice = { secs ->
                                    prefs.addPractice(com.bigpapituna.sharpears.data.AppClock.today(), secs)
                                    if (secs > 0) progress.record(ProgressEvent.Practice(com.bigpapituna.sharpears.data.AppClock.now(), secs))
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
                                    val script = remember { intent.getStringExtra("midi")?.takeIf { routes } }
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
                        is Screen.Arcade -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { voice.stop(); screen = s.back }
                            TimedOnScreen(s) { ms -> Briefs.log { BriefEvent.ToyOpened(it, ToyKind.ARCADE, ms) } }
                            val av = arcadeStore.version.intValue
                            // The ledger catches up first, so practice since the last session's end counts toward the next game.
                            val sparkles = remember { world.sync(); world.state.ledger.sparkles }
                            val newGame = remember { arcadeStore.opened(sparkles) }
                            LaunchedEffect(Unit) {
                                voice.speak(if (newGame != null) com.bigpapituna.sharpears.engine.arcade.ArcadeLines.NEW_GAME else com.bigpapituna.sharpears.engine.arcade.ArcadeLines.HUB, interrupt = true)
                            }
                            androidx.compose.runtime.key(av) {
                                com.bigpapituna.sharpears.ui.arcade.ArcadeHub(arcadeStore.state, sparkles,
                                    onGame = { g -> voice.stop(); screen = Screen.ArcadeRound(g, s) },
                                    onBack = { voice.stop(); screen = s.back }, say = { voice.speak(it, interrupt = true) }, newGame = newGame)
                            }
                        }
                        is Screen.ArcadeRound -> Box(Modifier.fillMaxSize()) {
                            BackHandler { voice.stop(); screen = s.back }
                            TimedOnScreen(s) { ms -> Briefs.log { BriefEvent.ToyOpened(it, ToyKind.ARCADE, ms) } }
                            // 2.14: a reader's first Balloon pop starts at level 1 (five keys, letter names), not three animal keys.
                            val level = remember(s) { s.level ?: com.bigpapituna.sharpears.engine.arcade.ArcadeStart.level(s.game, arcadeStore.state.of(s.game), current.reader) }
                            // 2.8: a game's first round keeps C D E at level 0; later rounds roll their three keys.
                            val firstRound = remember(s) { arcadeStore.state.of(s.game).rounds == 0 }
                            val stickerId = com.bigpapituna.sharpears.engine.world.LevelStickers.arcade(s.game.id)
                            val debugResult = s.debugStars?.let { n ->
                                com.bigpapituna.sharpears.engine.arcade.RoundResult(s.game, level, offered = 20, hits = when (n) { 3 -> 20; 2 -> 12; else -> 4 },
                                    helped = when (n) { 3 -> 0; 2 -> 8; else -> 16 }, slips = 1,
                                    lines = if (s.game == com.bigpapituna.sharpears.engine.arcade.ArcadeGame.TOWER) listOf(2, 8, 30)[n - 1] else 20, clean = 10, keys = 5,
                                    durationMs = 70_000, needLines = if (n == 3) 0 else 99)
                            }
                            androidx.compose.runtime.key(s.seed) {
                                com.bigpapituna.sharpears.ui.arcade.ArcadeGameScreen(s.game, level, s.seed, LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), s.inject,
                                    sticker = com.bigpapituna.sharpears.engine.world.LevelStickers.byLesson(stickerId),
                                    onRoundEnd = { r ->
                                        // Its stars are kept now: a recreate on the finish screen goes back to the games.
                                        arcadeEnded = s.seed; shownRoute = routeOf(s.back)
                                        val end = arcadeStore.record(r)
                                        // A round left alone (no key played) ends kindly but is no round played: no sparkles,
                                        // quest star or brief line for it (QA 2.8.3).
                                        if (r.played) {
                                            world.gameRound(r.game.id, stickerId.takeIf { end.firstThree })
                                            Briefs.log { BriefEvent.GameRound(it, r.game.id, end.stars, r.keys, r.level, r.durationMs) }
                                            quest(QuestEvent.GameRound(r.game.id))
                                        } else Log.d(TAG, "arcade: ${r.game.id} round left alone, not counted")
                                        end
                                    },
                                    onAgain = { voice.stop(); screen = Screen.ArcadeRound(s.game, s.back) },
                                    onGames = { voice.stop(); screen = s.back }, onExit = { voice.stop(); screen = s.back },
                                    debugResult = debugResult,
                                    bot = routes && intent.getBooleanExtra("bot", false),
                                    first = firstRound)
                            }
                        }
                        is Screen.MicSetup -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                            // 2.14: Lock now, as on every grown-ups' screen; a kid's Microphone setup closes with it.
                            com.bigpapituna.sharpears.ui.family.LockNowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), onLocked = { if (profile.isKid) screen = s.back })
                            val closings0 = remember { gate.closings }
                            if (profile.isKid) LaunchedEffect(gate.closings) { if (gate.closings != closings0) screen = s.back }
                            Box(Modifier.weight(1f)) {
                            MicSetupScreen(mic, midi, debuggable,
                                onMicCheck = { screen = Screen.Player(micCheck, null, PlayerStart(Mode.WAIT, Practice.BOTH, mic = true), 0.0, s) },
                                onBack = { screen = s.back }, guardToken = { gate.guard(it) }, inject = s.inject)
                            }
                            }
                        }
                        // 2.14: the pup's world is a little kid's home; a big kid gets the tabs below (engine/family/Ages.kt).
                        is Screen.Home -> if (current.mapHome) {
                            // A kid's home (2.2) is the pup's world: one screen, no tabs (ui/kid/KidHome.kt).
                            val home = s.copy(kidFrom = null)
                            val pv = prefs.version.intValue
                            val kv = kidStore.version.intValue
                            val model = remember(pv, kv) { KidMap.build(path, prefs::lessonDone, kidStore::stars, kidStore.finales(), pace = kidStore.pace(), skipped = prefs::lessonSkipped) }
                            val today = remember { com.bigpapituna.sharpears.data.AppClock.today() }
                            // 2.14: today's time up, the pup sleeps on his stone too (and the goodbye shows once).
                            val asleep = capSince >= 0 || !com.bigpapituna.sharpears.engine.skin.Bedtime.lessonOpen(true, progress.secondsToday(today), progress.goalMinutes,
                                bedtime.oneMoreOn, today, bedtime.bedtime)
                            LaunchedEffect(capSince) {
                                if (capSince >= 0 && bedtime.capShownOn != com.bigpapituna.sharpears.data.AppClock.today()) {
                                    capGoodbye = true; bedtime.capShownOn = com.bigpapituna.sharpears.data.AppClock.today()
                                }
                            }
                            val quest = remember(pv, kv) { playStore.quest(kid = true, plan = null, game = questGame()) }
                            var corner by remember { mutableStateOf(false) }
                            // 2.9, Tomorrow's surprise: ready to open on the map, then the opening.
                            val giftReady = com.bigpapituna.sharpears.engine.world.Surprise.ready(world.state.gift, com.bigpapituna.sharpears.data.AppClock.today())
                            var giftOpened by remember { mutableStateOf<com.bigpapituna.sharpears.engine.world.GiftOpened?>(null) }
                            LaunchedEffect(giftReady) { if (giftReady && !comebackOpen) { kotlinx.coroutines.delay(2600); voice.speak(com.bigpapituna.sharpears.engine.RetentionLines.READY, interrupt = true) } }
                            // The parent brief from the corner (2.4): every kid on the phone, this one's tab first.
                            var briefOpen by remember { mutableStateOf(false) }
                            // Family (2.4.1): grown-ups corner > Family > Mia > Read the brief.
                            var familyOpen by remember { mutableStateOf(false) }
                            // 2.14: the gate closed (Lock now, away 30 s): the corner and what it opened close with it.
                            val closings0 = remember { gate.closings }
                            LaunchedEffect(gate.closings) { if (gate.closings != closings0) { corner = false; familyOpen = false; briefOpen = false } }
                            // Back on the kid's map, a grown-up is done (Settings, a share, one more lesson): the gate shuts, so
                            // the whistle cannot hand the kid Settings or another player for the rest of its ten minutes.
                            LaunchedEffect(Unit) { if (gate.isOpen) { gate.close(); Log.d(TAG, "gate: closed, back on the kid's map") } }
                            // After the last page's finale the pup says it once: the big kids' road is open.
                            val roadLine = remember(kv) { model.roadOpen && !kidStore.roadSaid && kidStore.finales().containsAll(PLAYGROUND_PAGES.map { it.id }) }
                            // A finale left waiting (its lesson was left another way, or the app was closed before): it plays now.
                            LaunchedEffect(pendingFinale) {
                                (pendingFinale ?: Finale.owed(prefs::lessonPlayed, kidStore.finales())?.id)?.let { pendingFinale = null; screen = Screen.Finale(it) }
                            }
                            com.bigpapituna.sharpears.ui.kid.KidHome(model, world.state, current.name, current.avatar, asleep, quest,
                                onStone = { st -> if (capSince >= 0) { capGoodbye = true; sayTomorrow() } else path.lesson(st.lessonId)?.let { voice.stop(); screen = Screen.Lesson(it, home) } },
                                playClosed = !com.bigpapituna.sharpears.engine.skin.KidDoors.open(com.bigpapituna.sharpears.engine.skin.KidDoor.TOYS, kidDay),
                                onClosed = ::sayTomorrow,
                                // 2.14: a stone under three stars, replayed from its first graded step (not its reading page); once
                                // today's time is up, the goodbye as for any stone.
                                onReplay = { st -> if (capSince >= 0) { capGoodbye = true; sayTomorrow() } else path.lesson(st.lessonId)?.let {
                                    voice.stop(); Log.d(TAG, "kid home: replay ${it.id} for stars")
                                    screen = Screen.Lesson(it, home, com.bigpapituna.sharpears.engine.kid.LessonStars.firstGraded(it.steps))
                                } },
                                // 2.14: a reader who breezes through the garden is offered the pond (the kid's own choice: nothing
                                // is lost, the garden stays open, so no gate).
                                skipGarden = if (!remember(pv, kv) { com.bigpapituna.sharpears.engine.kid.GardenSkip.offered(current.audience, current.reader, current.level,
                                        PLAYGROUND_PAGES.first().lessonIds, prefs::lessonPlayed, kidStore::stars, kidStore.gardenSkipAsked) }) null else { {
                                    com.bigpapituna.sharpears.ui.kid.SkipGardenCard(
                                        onSkip = {
                                            kidStore.gardenSkipAsked = true
                                            Log.d(TAG, "kid home: ${current.id} skips the garden, the pond next")
                                            profiles.update(current.id) { it.copy(level = com.bigpapituna.sharpears.engine.kid.GardenSkip.LEVEL_AFTER) }
                                        },
                                        onLater = { kidStore.gardenSkipAsked = true; Log.d(TAG, "kid home: skip the garden, not now") })
                                } },
                                onToy = { t -> screen = Screen.Play(t, home) },
                                onGames = { voice.stop(); screen = Screen.Arcade(home) },
                                // 2.9: Tomorrow's surprise, ready from the day after it was wrapped.
                                gift = if (!giftReady) null else { {
                                    com.bigpapituna.sharpears.ui.ReadyGift(onOpen = {
                                        voice.stop()
                                        giftOpened = world.openGift()?.also { retentionEvent(com.bigpapituna.sharpears.engine.tester.Retention.SURPRISE_OPENED) }
                                    })
                                } },
                                onSongs = { screen = Screen.KidSongs(home) },
                                onStickers = { screen = Screen.KidStickers(home) },
                                onQuestTask = { t ->
                                    Log.d(TAG, "quest: open ${t.opens} for ${t.kind} (kid home)")
                                    screen = when (t.opens) {
                                        "jam" -> Screen.Play(Toy.JAM, home, hint = "Can you find all the Cs?")
                                        "pupsays" -> Screen.Play(Toy.PUP_SAYS, home)
                                        "songs" -> Screen.KidSongs(home)
                                        "games" -> com.bigpapituna.sharpears.engine.arcade.ArcadeGame.byId(t.ref)?.let { Screen.ArcadeRound(it, Screen.Arcade(home)) } ?: Screen.Arcade(home)
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
                                    // 2.14: each asks the gate again (open, it runs at once and keeps it open two more minutes; run
                                    // out while the corner stayed open, it asks).
                                    onSettings = { gate.guard { corner = false; openSettings(home) } },
                                    onMicSetup = { gate.guard { corner = false; screen = Screen.MicSetup(home) } },
                                    onSwitch = { id -> gate.guard { corner = false; profiles.switchTo(id) } },
                                    onBrief = { corner = false; briefOpen = true; Log.d(TAG, "kid home: grown-ups opened the parent brief") },
                                    onFamily = { corner = false; familyOpen = true; Log.d(TAG, "kid home: grown-ups opened Family") },
                                    // 2.14: this kid's daily time and wind down, in the corner itself.
                                    kid = current, onTimeChanged = { timeVersion++ }),
                                onGrownUpsClosed = { corner = false; gate.close(); Log.d(TAG, "gate: closed with the grown-ups' corner") },
                                decor = com.bigpapituna.sharpears.ui.kid.DecorActions(world::place, world::unplace, say = {}),
                                decorateWith = decorateWith, onDecorateStarted = { decorateWith = null },
                                homesLine = world.state.homesLine, onHomesSaid = world::homesSaid,
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
                            giftOpened?.let { o ->
                                com.bigpapituna.sharpears.ui.GiftOpening(o, reader = current.reader, say = { voice.speak(it, interrupt = true) }, onClose = { voice.stop(); giftOpened = null })
                            }
                            // 2.9, the comeback: the pup is glad, and one stone the kid knows to play for stars (once per return).
                            // 2.14 merge: not once today's time is up (parents): its Play would only open the goodbye.
                            if (comebackOpen && comeback != null && capSince < 0) {
                                // 2.14: a concrete step: the lesson left part way at its step, else a stone they know from its first
                                // graded step (they know it; the reading page waits), else the next stone.
                                val left = remember { com.bigpapituna.sharpears.engine.Comeback.parseLeft(prefs.openStep)?.takeIf { (id, _) -> !prefs.lessonDone(id) && path.lesson(id) != null } }
                                val pick = remember { if (left != null) null else com.bigpapituna.sharpears.engine.Comeback.kidStone(
                                    model.stones.filter { it.state == com.bigpapituna.sharpears.engine.kid.StoneState.DONE && !it.skipped }.map { it.lessonId }) { id -> model.stone(id)?.stars } }
                                val lesson = remember { (left?.first ?: pick ?: model.next)?.let(path::lesson) }
                                val step = remember { when { left != null -> left.second; pick != null && lesson != null -> com.bigpapituna.sharpears.engine.kid.LessonStars.firstGraded(lesson.steps); else -> 0 } }
                                LaunchedEffect(Unit) {
                                    comebackGreeted()
                                    kotlinx.coroutines.delay(1400)
                                    voice.speak(if (pick != null) com.bigpapituna.sharpears.engine.RetentionLines.COMEBACK else com.bigpapituna.sharpears.engine.RetentionLines.COMEBACK_NEXT, interrupt = true)
                                }
                                com.bigpapituna.sharpears.ui.KidComebackCard(lesson?.title,
                                    onPlay = { comebackOpen = false; retentionEvent(com.bigpapituna.sharpears.engine.tester.Retention.COMEBACK_PLAYED); voice.stop()
                                        lesson?.let { Log.d(TAG, "comeback: ${it.id} at step ${step + 1}"); screen = Screen.Lesson(it, home, step) } },
                                    onLater = { comebackOpen = false; voice.stop() })
                            }
                            if (familyOpen) com.bigpapituna.sharpears.ui.family.FamilyDialog(profiles,
                                onClose = { familyOpen = false; gate.close(); Log.d(TAG, "gate: closed with Family") })
                            if (briefOpen) com.bigpapituna.sharpears.ui.brief.ParentBriefDialog(com.bigpapituna.sharpears.ui.brief.rememberBriefEnv(),
                                roster.profiles.filter { it.isKid }, initial = current.id,
                                // Closed, the phone is the kid's again: the gate shuts as it does with the corner.
                                onClose = { briefOpen = false; gate.close(); Log.d(TAG, "gate: closed with the parent brief") })
                        } else if (com.bigpapituna.sharpears.engine.nav.Simple.asks(current.isKid, chosenLayout)) {
                            // 2.8, the launch chooser: a grown-up who never chose a layout picks one before the tabs (Today); a
                            // tap saves it and the home opens under it. Kids never see it; 2.7's Simple switch counts as chosen.
                            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                com.bigpapituna.sharpears.ui.LayoutChooser(current.greetName, returning = progress.practiceLog().keys.any { it.isBefore(com.bigpapituna.sharpears.data.AppClock.today()) }) { l ->
                                    Log.d(TAG, "layout: chosen at launch")
                                    layoutMode.set(l)
                                }
                            }
                        } else {
                            BackHandler(enabled = s.tab != HomeTab.TODAY) { screen = Screen.Home() }
                            // 2.14: the level the answer starts at, once the first-run questions are done (the onboarding's answer
                            // arrives before its last step marks it done, so the tabs seed it too; a floor, so it runs once).
                            LaunchedEffect(current.level, current.band) { seedLevel(current, progress) }
                            val lv = lessons.version.intValue
                            val due = remember(lv, lessons.songPacks.value, layout) {
                                chordCurriculum().queue(lessons.mastery(), System.currentTimeMillis()).size
                            }
                            HomeShell(s.tab, { screen = s.copy(tab = it) }, badge = mapOf(HomeTab.LEARN to due)) { tab ->
                                when (tab) {
                                    // 2.7: Today, the grown-ups' home. One main action: the plan and its Start.
                                    HomeTab.TODAY -> {
                                        val pv = prefs.version.intValue
                                        val today = com.bigpapituna.sharpears.data.AppClock.today()
                                        val plan = remember(pv, lv, lessons.songPacks.value) { todayPlan() }
                                        val card = remember(plan, pv) { com.bigpapituna.sharpears.engine.nav.TodayCard.of(plan, prefs.dailySummary(today)) }
                                        val goal = remember(pv, progress.version.intValue) {
                                            val st = progress.streak()
                                            GoalState(progress.goalLine(), DailyGoal.met(progress.secondsToday(), progress.goalMinutes), st.streak, st.atRisk,
                                                rest = st.restInStreak, restYesterday = st.atRisk && com.bigpapituna.sharpears.data.AppClock.today().minusDays(1) in st.restDays)
                                        }
                                        // Continue: the song, song by chords or lesson played last; on a fresh install an easy first song.
                                        val cont = remember(songs, pv, plan) {
                                            val with = com.bigpapituna.sharpears.engine.nav.ContinueWith.of(prefs.lastPlayed, prefs.lastSongId) { c ->
                                                when (c) {
                                                    is com.bigpapituna.sharpears.engine.nav.ContinueWith.Song -> songs.any { it.id == c.id && repo.available(it) }
                                                    is com.bigpapituna.sharpears.engine.nav.ContinueWith.PopSong -> com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.any { it.id == c.id }
                                                    is com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson -> path.lesson(c.id) != null
                                                }
                                            }
                                            fun entry(m: SongMeta) = LibraryEntry(m, repo.available(m), prefs.progress(m.id), null)
                                            when (with) {
                                                is com.bigpapituna.sharpears.engine.nav.ContinueWith.Song -> songs.first { it.id == with.id }.let { com.bigpapituna.sharpears.ui.ContinueTile.Song(entry(it), fresh = false) }
                                                is com.bigpapituna.sharpears.engine.nav.ContinueWith.PopSong ->
                                                    com.bigpapituna.sharpears.ui.ContinueTile.PopSong(com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.first { it.id == with.id })
                                                is com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson -> path.lesson(with.id)!!.let { l ->
                                                    com.bigpapituna.sharpears.ui.ContinueTile.Lesson(l, path.levelOf(l)?.title, prefs.lessonPlayed(l.id))
                                                }
                                                // A fresh install: a challenge in today's plan wins (2.14 depth: no "Start here" beside it); else an
                                                // easy first song for a beginner; a song by chords for some, a lot and a big kid (2.14 levels-ages,
                                                // engine/nav/DayOne.kt), the easiest of them.
                                                null -> if (plan.parts.any { it is SessionPart.Challenge }) null
                                                    else if (easyFirstSong()) songs.firstOrNull { it.difficulty == "easy" && repo.available(it) }?.let { com.bigpapituna.sharpears.ui.ContinueTile.Song(entry(it), fresh = true) }
                                                    else com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.sortedWith(PopFind.easiest).firstOrNull()
                                                        ?.let { com.bigpapituna.sharpears.ui.ContinueTile.PopSong(it, fresh = true) }
                                            }
                                        }
                                        // The microphone banner, until the mic is allowed (then never again) or put away by hand.
                                        val micAllowed = mic.hasPermission()
                                        LaunchedEffect(micAllowed) { if (micAllowed && !prefs.micBannerClosed) prefs.micBannerClosed = true }
                                        // 2.14: the banner only for someone who plays into the microphone (MIDI and the screen's keys need none).
                                        com.bigpapituna.sharpears.ui.TodayScreen(card, goal, cont,
                                            micBanner = com.bigpapituna.sharpears.engine.nav.DayOne.micBanner(progress.useMic, micAllowed, prefs.micBannerClosed),
                                            com.bigpapituna.sharpears.ui.TodayActions(
                                                onStart = ::startDaily,
                                                onContinue = {
                                                    when (cont) {
                                                        is com.bigpapituna.sharpears.ui.ContinueTile.Song -> openSong(cont.entry.meta, back = s)
                                                        is com.bigpapituna.sharpears.ui.ContinueTile.PopSong -> screen = Screen.PopSong(cont.song.id, s)
                                                        is com.bigpapituna.sharpears.ui.ContinueTile.Lesson -> screen = Screen.Lesson(cont.lesson, s)
                                                        null -> {}
                                                    }
                                                },
                                                onMic = { screen = Screen.MicSetup(s) },
                                                onMicClose = { prefs.micBannerClosed = true; Log.d(TAG, "today: mic banner put away") },
                                                onSongs = { screen = Screen.Home(HomeTab.SONGS) },
                                                onChords = { screen = Screen.Home(HomeTab.LEARN) },
                                                onToy = { t -> screen = Screen.Play(t, s) },
                                            ),
                                            // Pup's quest as a row of stars in the card: a task's tap goes where it is done.
                                            quest = {
                                                com.bigpapituna.sharpears.ui.play.QuestStars(plan, onTask = { t ->
                                                    Log.d(TAG, "quest: open ${t.opens} for ${t.kind}")
                                                    val song = t.ref?.let { id -> songs.firstOrNull { it.id == id && repo.available(it) } }
                                                    if (t.opens == "songs" && song != null) openSong(song, back = s)     // "Play Minuet in G": straight in
                                                    else screen = when (t.opens) {
                                                        "jam" -> Screen.Play(Toy.JAM, s, hint = "Can you find all the Cs?")
                                                        "pupsays" -> Screen.Play(Toy.PUP_SAYS, s)
                                                        "chords" -> Screen.LearnChords(s)
                                                        "learn" -> Screen.Home(HomeTab.LEARN)
                                                        "songs" -> Screen.Home(HomeTab.SONGS)
                                                        else -> s
                                                    }
                                                })
                                            },
                                            emptyPlan = routes && intent.getBooleanExtra("emptyToday", false),
                                            name = current.greetName,
                                            // 2.9: back after three days or more: three easy minutes with something already known. 2.14: a
                                            // concrete step, never a list (engine/Comeback.kt resume: the lesson left part way at its step, the
                                            // last chart or song, else the next lesson); a big kid's words are plain; after a week away one line
                                            // says what became of the streak.
                                            welcomeBack = if (!comebackOpen || comeback == null || progress.secondsToday() > 0 || capSince >= 0) null else { {
                                                val r = remember {
                                                    com.bigpapituna.sharpears.engine.Comeback.resume(com.bigpapituna.sharpears.engine.Comeback.parseLeft(prefs.openStep),
                                                        com.bigpapituna.sharpears.engine.nav.ContinueWith.decode(prefs.lastPlayed), nextLesson()?.id, done = prefs::lessonDone) { c ->
                                                        when (c) {
                                                            is com.bigpapituna.sharpears.engine.nav.ContinueWith.Song -> songs.any { it.id == c.id && repo.available(it) }
                                                            is com.bigpapituna.sharpears.engine.nav.ContinueWith.PopSong -> com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.any { it.id == c.id }
                                                            is com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson -> path.lesson(c.id) != null
                                                        }
                                                    }
                                                }
                                                LaunchedEffect(Unit) { comebackGreeted(); Log.d(TAG, "comeback: opens $r") }
                                                val title = when (r) {
                                                    is com.bigpapituna.sharpears.engine.Comeback.Resume.LessonStep -> path.lesson(r.id)?.title
                                                    is com.bigpapituna.sharpears.engine.Comeback.Resume.NextLesson -> path.lesson(r.id)?.title
                                                    is com.bigpapituna.sharpears.engine.Comeback.Resume.PopChart -> com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.firstOrNull { it.id == r.id }?.title
                                                    is com.bigpapituna.sharpears.engine.Comeback.Resume.Song -> songs.firstOrNull { it.id == r.id }?.title
                                                    null -> null
                                                }
                                                val streak = remember {
                                                    if (!com.bigpapituna.sharpears.engine.family.Ages.streakLine(current.audience)) null
                                                    else com.bigpapituna.sharpears.engine.Comeback.streakLine(progress.practiceLog().filterValues { it > 0 }.keys, com.bigpapituna.sharpears.data.AppClock.today())
                                                }
                                                val big = current.bigKid
                                                com.bigpapituna.sharpears.ui.WelcomeBackCard(
                                                    if (big) com.bigpapituna.sharpears.engine.Comeback.BIG_KID_LINE.let { l -> title?.let { "${l.removeSuffix(".")}: $it." } ?: l }
                                                    else if (r == null) com.bigpapituna.sharpears.engine.Comeback.grownUpLine(null, false)
                                                    else com.bigpapituna.sharpears.engine.Comeback.grownUpLine(r, title),
                                                    title = if (big) com.bigpapituna.sharpears.engine.Comeback.BIG_KID_TITLE else com.bigpapituna.sharpears.engine.Comeback.GROWN_UP_TITLE,
                                                    streak = streak,
                                                    onStart = {
                                                        comebackOpen = false
                                                        retentionEvent(com.bigpapituna.sharpears.engine.tester.Retention.COMEBACK_PLAYED)
                                                        when (r) {
                                                            is com.bigpapituna.sharpears.engine.Comeback.Resume.LessonStep -> path.lesson(r.id)?.let { screen = Screen.Lesson(it, s, r.step) }
                                                            is com.bigpapituna.sharpears.engine.Comeback.Resume.NextLesson -> path.lesson(r.id)?.let { screen = Screen.Lesson(it, s) }
                                                            is com.bigpapituna.sharpears.engine.Comeback.Resume.PopChart -> screen = Screen.PopSong(r.id, s)
                                                            is com.bigpapituna.sharpears.engine.Comeback.Resume.Song -> songs.firstOrNull { it.id == r.id }?.let { m ->
                                                                runCatching { repo.load(m) }.getOrNull()?.let { song ->
                                                                    Log.d(TAG, "comeback: ${m.id} at ${(com.bigpapituna.sharpears.engine.Comeback.GROWN_UP_SPEED * 100).toInt()}% for three minutes")
                                                                    screen = Screen.Player(song, m, PlayerStart(Mode.WAIT, Practice.BOTH, mic = prefs.micDefault && mic.hasPermission(),
                                                                        timeLimitMs = com.bigpapituna.sharpears.engine.Comeback.GROWN_UP_MS, speed = com.bigpapituna.sharpears.engine.Comeback.GROWN_UP_SPEED), 0.0, s)
                                                                }
                                                            }
                                                            null -> screen = Screen.Home(HomeTab.LEARN)
                                                        }
                                                    },
                                                    onLater = { comebackOpen = false })
                                            } },
                                            // 2.14, Family today (Priya's P8): each child's minutes, lessons and stars; a tap opens their brief
                                            // without the gate, so a grown-up's Today only: a big kid sees Today too since 2.14 (levels-ages).
                                            family = if (current.isKid) null else roster.profiles.filter { it.isKid }.takeIf { it.isNotEmpty() }?.let { kids -> {
                                                com.bigpapituna.sharpears.ui.family.FamilyTodayCard(kids, onOpen = { k ->
                                                    Log.d(TAG, "today: family card opens ${k.id}'s brief"); familyBrief.value = k.id
                                                })
                                            } },
                                            // 2.14: "I know these chords" for someone who said some or a lot and knows none yet.
                                            knownChords = if (current.isKid || !remember(lv, pv) { com.bigpapituna.sharpears.engine.chords.KnownChords.offered(placed(),
                                                    PopFind.known(lessons.mastery()), prefs.knownChordsAsked) }) null else { {
                                                com.bigpapituna.sharpears.ui.KnownChordsRow(onOpen = { knownChordsOpen = true },
                                                    onClose = { prefs.knownChordsAsked = true; Log.d(TAG, "today: known chords put away") })
                                            } })
                                    }
                                    // 2.7: everything playable. The Play corner, then the library or songs by chords.
                                    HomeTab.SONGS -> {
                                        val pv = prefs.version.intValue
                                        val entries = remember(songs, pv) {
                                            songs.map { m -> LibraryEntry(m, repo.available(m), prefs.progress(m.id), m.pairWith?.let { p -> songs.firstOrNull { it.id == p } }) }
                                        }
                                        LibraryScreen(entries, played = prefs.lastSongId != null,
                                            LibraryActions(
                                                onOpen = { openSong(it, back = s) },
                                                onImport = { into -> importInto = into; pickMidi.launch(arrayOf("audio/midi", "audio/x-midi", "audio/mid", "application/x-midi", "application/octet-stream", "*/*")) },
                                                // My songs: MIDI first, any file as the fallback (some file apps call a .mid a plain file).
                                                onAdd = { pickMySong.launch(arrayOf("audio/midi", "audio/x-midi", "audio/mid", "application/x-midi", "*/*")) },
                                                onRename = { m, title -> repo.rename(m, title); Log.d(TAG, "song ${m.id} renamed"); libraryVersion++ },
                                                onDelete = { m ->
                                                    repo.delete(m)
                                                    if (prefs.lastSongId == m.id) prefs.lastSongId = null
                                                    Log.d(TAG, "song ${m.id} deleted"); libraryVersion++
                                                },
                                            ),
                                            // The Play corner at the top (it was on the home); 2.14: a big kid's has the arcade too.
                                            play = { com.bigpapituna.sharpears.ui.play.PlayRow(onToy = { t -> screen = Screen.Play(t, s) },
                                                onArcade = if (com.bigpapituna.sharpears.engine.family.Ages.arcadeInTabs(current.audience)) ({ voice.stop(); screen = Screen.Arcade(s) }) else null) },
                                            // Songs by chords, the tab's second section (2.7; the Chords tab's Songs before).
                                            pop = if (com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.isEmpty()) null else {
                                                val known = remember(lv) { PopFind.known(lessons.mastery()) }
                                                com.bigpapituna.sharpears.ui.PopSection(com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs, known, s.pop,
                                                    onOpen = { song, at -> screen = Screen.PopSong(song.id, s.copy(songs = com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS, pop = at)) },
                                                    // 2.14: the empty Songs I can play and One new chord away offer "I know these chords".
                                                    onKnownChords = { knownChordsOpen = true })
                                            },
                                            // 2.14: a big kid's Songs opens on the songs by chords, every one of them (engine/family/Ages.kt).
                                            section = s.songs ?: if (com.bigpapituna.sharpears.engine.family.Ages.songsOpenOnChords(current.audience)) com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS
                                                else com.bigpapituna.sharpears.ui.SongsSection.LIBRARY,
                                            onSection = { screen = s.copy(songs = it) }, audience = current.audience, startHere = easyFirstSong())
                                    }
                                    HomeTab.LEARN -> {
                                        val pv = prefs.version.intValue
                                        val kv = kidStore.version.intValue
                                        LearnScreen(done = { pv >= 0 && prefs.lessonDone(it) }, skipped = { pv >= 0 && prefs.lessonSkipped(it) },
                                            onOpen = { screen = Screen.Lesson(it, s) },
                                            pinned = remember(pv, kv) { kidStore.pace().pin?.let(path::lesson) },
                                            onSightRead = { screen = Screen.SightRead(s) },
                                            sightBest = remember(pv) { (prefs.sightBest(1) ?: prefs.sightBest(2))?.label },
                                            sightStaff = com.bigpapituna.sharpears.engine.SightLevels.staffOpen(current.isKid, current.reader, current.level, layout, bigKid = current.bigKid),
                                            onEar = { screen = Screen.EarTraining(s) },
                                            theory = { m -> com.bigpapituna.sharpears.ui.theory.TheoryCard(theory, onOpen = { screen = Screen.Theory(s) }, modifier = m) },
                                            earStreak = remember { earStore.workoutStreak() },
                                            path = path,
                                            // 2.7: the Chords tab's places, at the top of Learn (a kid's chords are on their own Songs).
                                            chords = if (current.mapHome) null else ({
                                                com.bigpapituna.sharpears.ui.ChordsSection(due, onLearnChords = { screen = Screen.LearnChords(s) },
                                                    onGames = { screen = Screen.ChordGames(s) }, onProgressions = { screen = Screen.Progressions(s) })
                                            }),
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
                                    HomeTab.YOU -> ProfileScreen(progress, prefs, songs, onSettings = { gate.guardForKids { openSettings(s) } },
                                        kid = current.kidSkin,
                                        // The pup's world in one card: the garden, and a way into the album, dress-up and the records.
                                        world = { com.bigpapituna.sharpears.ui.world.WorldCard(world, onOpen = { tab -> screen = Screen.World(s, tab) }) },
                                        paws = { com.bigpapituna.sharpears.ui.skin.PawCalendarCard(kid = current.kidSkin) },
                                        // The parent brief about themselves (2.4): a card that opens their week, no gate.
                                        brief = if (current.isKid) null else { { com.bigpapituna.sharpears.ui.brief.MyWeekCard(com.bigpapituna.sharpears.ui.brief.rememberBriefEnv(), current) } },
                                        // Everyone's week at the top (2.7; it was on the home), when there is more than one of us: tapping
                                        // someone opens the app as them. Each shows their latest sticker; whoever copied the pup furthest
                                        // today wears the crown.
                                        family = if (current.isKid || !roster.needsPicker) null else { {
                                            val pv = prefs.version.intValue
                                            val wall = remember(roster, pv, progress.version.intValue) {
                                                familyWallEntries(this@MainActivity, roster, latestSticker = { id -> WorldStore.latestSticker(this@MainActivity, id) })
                                            }
                                            val crowned = remember(roster, pv) { WorldStore.crownOfTheDay(this@MainActivity, roster.profiles.map { it.id }) }
                                            FamilyWall(wall, onSwitch = profiles::switchTo, crowned = crowned)
                                        } })
                                }
                            }
                            // 2.7 (engine/nav/Welcome.kt): What's new once per version, then the tour once per profile, over the tabs.
                            val wv = prefs.version.intValue
                            // 2.14: a phone from before 2.14 has an owner nobody named (the app called them "Roy"): asked once, before the sheets.
                            var nameAnswered by remember { mutableStateOf(false) }
                            val nameDue = !nameAnswered && current.isOwner && profiles.ownerNameDue(progress.onboardingDone)
                            if (nameDue) com.bigpapituna.sharpears.ui.onboarding.OwnerNameAsk { typed -> nameAnswered = true; profiles.nameOwner(typed); Log.d(TAG, "profiles: owner name asked, ${if (typed.isBlank()) "none given" else "given"}") }
                            else                             when (com.bigpapituna.sharpears.engine.nav.Welcome.next(current.isKid, wv >= 0 && prefs.tourDone, prefs.whatsNewSeen, appVersion)) {
                                com.bigpapituna.sharpears.engine.nav.Welcome.Show.WHATS_NEW -> com.bigpapituna.sharpears.ui.WhatsNewSheet(appVersion,
                                    com.bigpapituna.sharpears.engine.nav.Welcome.whatsNew(appVersion)) {
                                    prefs.whatsNewSeen = appVersion; Log.d(TAG, "welcome: what's new $appVersion seen")
                                }
                                com.bigpapituna.sharpears.engine.nav.Welcome.Show.TOUR -> {
                                    val from = remember { s.tab }
                                    com.bigpapituna.sharpears.ui.TourDialog(onCard = { t -> screen = s.copy(tab = HomeTab.entries.first { it.nav == t }) },
                                        name = current.greetName,
                                        onDone = { skipped ->
                                            prefs.tourDone = true; screen = s.copy(tab = from)
                                            Log.d(TAG, "welcome: tour ${if (skipped) "skipped" else "done"}")
                                        })
                                }
                                com.bigpapituna.sharpears.engine.nav.Welcome.Show.NOTHING -> {}
                            }
                        }
                        // 2.7, the top of Learn: Learn chords, Games and Progressions (the Chords tab's places before).
                        is Screen.LearnChords -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            QuietWrongKeys(mic)
                            val here = s.copy(start = com.bigpapituna.sharpears.ui.LearnChordsStart.REVIEW, inject = null)
                            com.bigpapituna.sharpears.ui.LearnChordsScreen(sampler, midi, mic, prefs, lessons, prefs.micDefault, s.start, s.inject,
                                // A session's progression opens in the player; Back comes here, to the session.
                                onPlayProgression = { p, key ->
                                    screen = Screen.Player(progressionSong(p, key), null, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission()), 0.0, here)
                                },
                                popSongs = com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs,
                                onOpenPopSong = { screen = Screen.PopSong(it.id, here) },
                                onShowPlayable = {
                                    screen = Screen.Home(HomeTab.SONGS, songs = com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS,
                                        pop = com.bigpapituna.sharpears.ui.chords.PopStart(sort = PopFind.Sort.CAN_PLAY))
                                },
                                onBack = { screen = s.back })
                        }
                        is Screen.ChordGames -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { voice.stop(); screen = s.back }
                            QuietWrongKeys(mic)
                            com.bigpapituna.sharpears.ui.ChordGamesPage(sampler, midi, mic, voice, prefs, prefs.micDefault, s.start, onBack = { voice.stop(); screen = s.back })
                        }
                        is Screen.Progressions -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            com.bigpapituna.sharpears.ui.ProgressionsPage(sampler, prefs, onPlay = { p, key ->
                                screen = Screen.Player(progressionSong(p, key), null, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission()), 0.0, s)
                            }, onBack = { screen = s.back })
                        }
                        is Screen.PopSong -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            val pop = com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.firstOrNull { it.id == s.songId }
                            if (pop == null) com.bigpapituna.sharpears.ui.chords.PopSongMissing { screen = s.back }
                            else {
                                LaunchedEffect(pop.id) {
                                    progress.record(ProgressEvent.FeatureUsed("popsongs"))
                                    if (!profile.mapHome) prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.PopSong(pop.id))      // Today's Continue (2.7)
                                }
                                val known = remember(lessons.version.intValue) { com.bigpapituna.sharpears.engine.chords.PopFind.known(lessons.mastery()) }
                                com.bigpapituna.sharpears.ui.chords.PopSongPage(pop, known, sampler, sound = { prefs.sound },
                                    // Simple keeps to Whole and Halves, Advanced to the plain four: a song that suggests another pattern opens on Halves.
                                    shift = s.shift, pattern = com.bigpapituna.sharpears.engine.nav.Simple.openingPattern(s.pattern ?: pop.pattern, layout), bpm = s.bpm ?: pop.bpm,
                                    onState = { sh, pa, bp -> screen = s.copy(shift = sh, pattern = pa, bpm = bp, hear = false) },
                                    onPlayAlong = { run ->
                                        Log.d(TAG, "pop: play along ${pop.id} ${run.song.id}")
                                        // The right hand plays the chords; the app plays the left hand (the band toggle adds drums and bass).
                                        screen = Screen.Player(run.song, null, PlayerStart(Mode.WAIT, Practice.RIGHT, mic = prefs.micDefault && mic.hasPermission()),
                                            0.0, s.copy(hear = false), run.band, run.chords)
                                    },
                                    onBack = { screen = s.back }, hearAtOpen = s.hear, kidOverride = s.kid, preReader = s.pre)
                            }
                        }
                        is Screen.KidSongs -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            val kv = kidStore.version.intValue
                            com.bigpapituna.sharpears.ui.kid.KidSongsScreen(songs, repo, onSong = { openSong(it, back = s) }, onBack = { screen = s.back },
                                shelf = remember(kv) { kidStore.shelf },
                                duetWith = duetNext.value?.takeIf { it.whoId == profile.id }?.partnerId?.let { id -> roster.profiles.firstOrNull { it.id == id }?.name },
                                say = { voice.speak(it, interrupt = true) },
                                onChordSongs = if (com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.none { com.bigpapituna.sharpears.engine.chords.PopSong.Style.KIDS in it.styles }) null
                                    else ({ screen = Screen.KidChords(s) }),
                                reader = current.reader,
                                // 2.14: today's time up, or wind down closed the songs: "Tomorrow" tiles.
                                closed = profile.isKid && !com.bigpapituna.sharpears.engine.skin.KidDoors.open(com.bigpapituna.sharpears.engine.skin.KidDoor.SONGS, kidDay),
                                onClosed = ::sayTomorrow,
                                // 2.14: a little reader's pop packs after the nursery songs (engine/kid/KidLevels.kt KidPop).
                                onPopPack = if (!com.bigpapituna.sharpears.engine.kid.KidPop.shows(current.audience, current.reader)) null
                                    else ({ chip -> screen = Screen.KidChords(s, chip) }))
                        }
                        // A kid's chord songs (2.6): the kid list; a song opens the kid page (colour blocks; animals for a pre-reader).
                        is Screen.KidChords -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            com.bigpapituna.sharpears.ui.chords.KidChordSongs(com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs,
                                onOpen = { screen = Screen.PopSong(it.id, s) }, onBack = { screen = s.back }, chip = s.chip)
                        }
                        is Screen.KidStickers -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            com.bigpapituna.sharpears.ui.kid.KidStickersScreen(world, songs, onBack = { screen = s.back }, start = s.page,
                                onSong = { openSong(it, back = s) }, reader = current.reader,
                                onDecorate = { key -> decorateWith = key; screen = if (s.back is Screen.Home) s.back else Screen.Home() })
                        }
                        is Screen.Finale -> {
                            val page = PLAYGROUND_PAGES.firstOrNull { it.id == s.pageId }
                            if (page == null) LaunchedEffect(Unit) { screen = Screen.Home() }
                            else {
                                // Played once: the path walks on past the page from now on (engine/kid/KidMap.next).
                                LaunchedEffect(page.id) { kidStore.finalePlayed(page.id) }
                                com.bigpapituna.sharpears.ui.kid.FinaleScreen(page, current.name, world.state.album.page(KidPages.worldPlace(page)), world.state,
                                    com.bigpapituna.sharpears.engine.kid.LessonStars.ofPage(page.lessonIds, kidStore::stars), Finale.after(page),
                                    say = { voice.speak(it) }, sound = prefs.sound,
                                    onDone = { voice.stop(); screen = Screen.Home(kidFrom = page.id); maybeGoodnight("the ${page.id} finale") })
                            }
                        }
                    }
                    }
                    }
                    }
                    if (goodbye && !capGoodbye) SharpEarsTheme(dark = isSystemInDarkTheme()) {
                        com.bigpapituna.sharpears.ui.skin.Goodbye(current.name,
                            remember { com.bigpapituna.sharpears.engine.family.PawWeek.of(progress.practiceLog(), com.bigpapituna.sharpears.data.AppClock.today()) },
                            onSongs = { voice.stop(); goodbye = false; dailyDone = null; screen = songsScreen() },
                            onClose = { voice.stop(); goodbye = false; dailyDone = null },
                            onJam = { voice.stop(); goodbye = false; dailyDone = null; screen = Screen.Play(Toy.JAM, Screen.Home()) },
                            say = { voice.speak(it, interrupt = true) }, songsOpen = !songsAsleep, picture = current.kidSkin && !current.reader)
                    }
                    // 2.14: today's time is up: "That's all for today. See you tomorrow!" and only the goodbye.
                    if (capGoodbye) SharpEarsTheme(dark = isSystemInDarkTheme()) {
                        com.bigpapituna.sharpears.ui.skin.Goodbye(current.name,
                            remember { com.bigpapituna.sharpears.engine.family.PawWeek.of(progress.practiceLog(), com.bigpapituna.sharpears.data.AppClock.today()) },
                            onSongs = {}, onClose = { voice.stop(); capGoodbye = false; goodbye = false; dailyDone = null; screen = Screen.Home() },
                            say = { voice.speak(it, interrupt = true) }, capped = true, picture = current.kidSkin && !current.reader)
                    }
                    // 2.14, the doors (engine/skin/KidDoors) for what is on screen once the day has closed: opened after it closed,
                    // it ends at once; running when it closed, at its boundary (a lesson's step, a song's end, below) or after
                    // TimeCap.GRACE_MS; a toy or a game at once. Wind down's songs end with their own goodnight.
                    // 2.14 merge: a big kid (levels-ages) has the tabs, so Practice today and Learn's tools are doors too:
                    // practising is a lesson's door (the cap closes it), the chord games are a game's.
                    val kidDoor = if (!profile.isKid) null else when (screen) {
                        is Screen.Lesson, is Screen.Daily, is Screen.LearnChords, is Screen.Progressions, is Screen.SightRead,
                        is Screen.EarTraining, is Screen.Theory -> com.bigpapituna.sharpears.engine.skin.KidDoor.LESSON
                        is Screen.Player, is Screen.PopSong, is Screen.KidChords -> com.bigpapituna.sharpears.engine.skin.KidDoor.SONGS
                        is Screen.Play -> com.bigpapituna.sharpears.engine.skin.KidDoor.TOYS
                        is Screen.Arcade, is Screen.ArcadeRound, is Screen.ChordGames -> com.bigpapituna.sharpears.engine.skin.KidDoor.GAMES
                        else -> null
                    }
                    val enteredAt = remember(screen::class) { com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs() }
                    val closedSince = when {
                        kidDoor == null -> -1L
                        kidDoor == com.bigpapituna.sharpears.engine.skin.KidDoor.LESSON -> capSince
                        capSince >= 0 && songsSince >= 0 -> minOf(capSince, songsSince)
                        capSince >= 0 -> capSince
                        else -> songsSince
                    }
                    if (kidDoor != null && closedSince >= 0 && !com.bigpapituna.sharpears.engine.skin.KidDoors.open(kidDoor, kidDay)) LaunchedEffect(kidDoor, closedSince) {
                        if (enteredAt < closedSince) {
                            if (capSince < 0) return@LaunchedEffect
                            if (kidDoor == com.bigpapituna.sharpears.engine.skin.KidDoor.LESSON || kidDoor == com.bigpapituna.sharpears.engine.skin.KidDoor.SONGS)
                                kotlinx.coroutines.delay((com.bigpapituna.sharpears.engine.skin.TimeCap.GRACE_MS - (com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs() - capSince)).coerceAtLeast(0L))
                        }
                        closedForToday("${kidDoor.name.lowercase()} closed for today")
                    }
                    // The parent brief from Family today (2.14, Priya's P8): one tap, on Today; every action in it still asks the gate.
                    familyBrief.value?.let { id ->
                        SharpEarsTheme(dark = isSystemInDarkTheme()) {
                            com.bigpapituna.sharpears.ui.brief.ParentBriefDialog(com.bigpapituna.sharpears.ui.brief.rememberBriefEnv(), roster.profiles.filter { it.isKid },
                                onClose = { familyBrief.value = null }, initial = id, period = com.bigpapituna.sharpears.engine.brief.Period.DAY, gated = false)
                        }
                    }
                    // 2.14: "I know these chords": the picks go to the mastery store at the known box; either answer puts Today's offer away.
                    if (knownChordsOpen) {
                        val known = remember(lessons.version.intValue) { PopFind.known(lessons.mastery()) }
                        com.bigpapituna.sharpears.ui.chords.KnownChordsSheet(com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs, known,
                            onSave = { picked -> lessons.know(picked); prefs.knownChordsAsked = true; knownChordsOpen = false },
                            onLater = { prefs.knownChordsAsked = true; knownChordsOpen = false; Log.d(TAG, "lessons: known chords, not now") })
                    }
                    if (!goodbye) dailyDone?.let { (parts, min, streak) -> androidx.compose.runtime.CompositionLocalProvider(com.bigpapituna.sharpears.ui.LocalRetention provides retention) {
                        // A kid's session ends on the pup, not on numbers.
                        if (current.kidSkin) com.bigpapituna.sharpears.ui.Celebration("Done for today!",
                            com.bigpapituna.sharpears.engine.SessionWords.kidSessionDone(current.name), emptyList(),
                            primary = "Continue", onPrimary = { dailyDone = null },
                            pose = com.bigpapituna.sharpears.ui.pup.Pose.LEVEL_UP, sampler = sampler.takeIf { prefs.sound },
                            reward = { com.bigpapituna.sharpears.ui.TomorrowSlot() }, clip = com.bigpapituna.sharpears.ui.pup.PupClips.CELEBRATE,
                            picture = !current.reader)           // 2.14: a child who cannot read yet sees the pup and one arrow
                        else com.bigpapituna.sharpears.ui.Celebration("Done for today!",
                            if (streak >= 2) "$streak days in a row. Keep it going." else "Same time tomorrow?",
                            // The minutes the session added, whole as the goal line counts them; under one, no chip (not "0 min").
                            listOfNotNull(com.bigpapituna.sharpears.ui.Stat("$parts", if (parts == 1) "Part" else "Parts"),
                                com.bigpapituna.sharpears.ui.Stat("$min min", "Practice").takeIf { min > 0 },
                                com.bigpapituna.sharpears.ui.Stat("$streak", if (streak == 1) "Day" else "Days in a row")),
                            primary = "Continue", onPrimary = { dailyDone = null },
                            pose = if (streak >= 2) com.bigpapituna.sharpears.ui.pup.Pose.STREAK else com.bigpapituna.sharpears.ui.pup.Pose.LEVEL_UP,
                            sampler = sampler.takeIf { prefs.sound },
                            reward = { com.bigpapituna.sharpears.ui.TomorrowSlot() }, clip = com.bigpapituna.sharpears.ui.pup.PupClips.CELEBRATE)
                    } }
                    // Debug: --es screen motion [--es demo celebrate|cheer|pup|keys] (Team E's effects, ui/motion/MotionDemo.kt).
                    var motionDemo by remember { mutableStateOf(routes && intent.getStringExtra("screen") == "motion") }
                    if (motionDemo) com.bigpapituna.sharpears.ui.motion.MotionDemo(intent.getStringExtra("demo"), sampler) { motionDemo = false }
                    importError?.let { msg ->
                        LaunchedEffect(msg) { com.bigpapituna.sharpears.analytics.Tester.error("song import") }
                        AlertDialog(onDismissRequest = { importError = null }, title = { Text("Could not import") }, text = { Text(msg) },
                            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } })
                    }
                    // My songs: the title dialog (title, Melody only or Both hands), then the song joins the list.
                    mySongDraft?.let { d ->
                        com.bigpapituna.sharpears.ui.MySongDialog(d, onAdd = { title, both ->
                            mySongDraft = null
                            scope.launch {
                                try {
                                    val m = withContext(Dispatchers.IO) { repo.addMine(d.bytes, d.scan.title, title, both) }
                                    Log.d(TAG, "my songs: added ${m.id} both=${m.bothHands}")
                                    progress.record(ProgressEvent.FeatureUsed("import"))
                                    libraryVersion++
                                } catch (e: Exception) {
                                    Log.d(TAG, "my songs: save failed, $e"); mySongRefused = com.bigpapituna.sharpears.engine.MySongs.Words.SAVE_FAILED
                                }
                            }
                        }, onCancel = { mySongDraft = null; Log.d(TAG, "my songs: add cancelled") })
                    }
                    mySongRefused?.let { line ->
                        AlertDialog(onDismissRequest = { mySongRefused = null }, title = { Text(com.bigpapituna.sharpears.engine.MySongs.Words.REFUSED_TITLE) },
                            text = { Text(line) }, confirmButton = { TextButton(onClick = { mySongRefused = null }) { Text("OK") } })
                    }
                    // Level-ups and achievement toasts, last so they sit on top. Not on the stage: a level-up waits until
                    // the song is over (the store keeps it pending), so it never interrupts playing.
                    // A Celebration on screen holds the level-up back until it closes: celebration first, then the level.
                    // A kid's session ends on the sticker, not on XP (KIDS-PLAN 3.4): XP still adds up underneath for grown-up
                    // mode later, but no level-up screen full of numbers.
                    if (current.kidSkin) LaunchedEffect(progress.levelUp.value) { if (progress.levelUp.value != null) progress.consumeLevelUp() }
                    // Nor achievement banners ("One week streak", "100 minutes"): they unlock underneath, unseen by a kid.
                    // 2.14: a big kid sees levels as a grown-up does (Ages.showsLevels); a little kid sees none.
                    if (current.kidSkin) LaunchedEffect(progress.toasts.size) { progress.toasts.clear() }
                    if (!isStage(screen) && !current.kidSkin) SharpEarsTheme(dark = isSystemInDarkTheme()) {
                        ProgressOverlays(progress, holdLevelUp = current.kidSkin || com.bigpapituna.sharpears.ui.celebrationsShowing.intValue > 0)
                    }
                    // The weekly brief's notification asked for someone's brief (2.4).
                    BriefFromNotification(roster, openKid = current.isKid)
                    // 2.13: Help Roy test came on by default in this test build: a one-time notice, to a grown-up only (a kid's
                    // profile leaves it for the next grown-up one), never over the stage. OK and it is gone for good.
                    if (com.bigpapituna.sharpears.engine.tester.TesterRules.showsNotice(com.bigpapituna.sharpears.analytics.Tester.noticePending.value, current.isKid) &&
                        !isStage(screen)) SharpEarsTheme(dark = isSystemInDarkTheme()) {
                        com.bigpapituna.sharpears.analytics.TesterDefaultNotice { com.bigpapituna.sharpears.analytics.Tester.noticeSeen(this@MainActivity) }
                    }
                    // The parent gate, over everything while an action waits for a grown-up.
                    SharpEarsTheme(dark = isSystemInDarkTheme()) { ParentGateHost(gate) }
                }
            }
            }
        } }
    }

    /**
     * The weekly brief's notification asked for someone's brief ([briefRequest]): a kid's opens the parent brief on their tab
     * and that period, behind the parent gate; a grown-up's opens their own week. Closing it on a kid's screen shuts the gate.
     */
    @androidx.compose.runtime.Composable
    private fun BriefFromNotification(roster: com.bigpapituna.sharpears.engine.family.Roster, openKid: Boolean) {
        val (id, period) = briefRequest.value ?: return
        val who = roster.profiles.firstOrNull { it.id == id }
        if (who == null) { LaunchedEffect(id) { briefRequest.value = null; Log.d(TAG, "brief: notification for $id, who is gone") }; return }
        SharpEarsTheme(dark = isSystemInDarkTheme()) {
            val env = com.bigpapituna.sharpears.ui.brief.rememberBriefEnv().let { live ->
                if (briefSamples) remember(live) { com.bigpapituna.sharpears.ui.brief.sampleActionsEnv(live) } else live
            }
            val close = { briefRequest.value = null; if (openKid) gate.close() }
            if (who.isKid) com.bigpapituna.sharpears.ui.brief.ParentBriefDialog(env, roster.profiles.filter { it.isKid }, onClose = close, initial = id, period = period)
            else com.bigpapituna.sharpears.ui.brief.MyWeekDialog(env, who, onClose = close, period = period)
        }
    }

    /** The weekly notification's extras ([BriefReminder.EXTRA_PROFILE], [BriefReminder.EXTRA_PERIOD]), or null. */
    private fun briefFrom(intent: android.content.Intent?): Pair<String, com.bigpapituna.sharpears.engine.brief.Period>? {
        // The email brief's buttons: sharpears://brief/<profile id>/<day or week> (any build; the brief asks the parent gate itself).
        if (intent?.data?.scheme == "sharpears") return com.bigpapituna.sharpears.engine.brief.email.EmailLinks.parse(intent.dataString)?.let { it.profileId to it.period }
        val id = intent?.getStringExtra(com.bigpapituna.sharpears.reminder.BriefReminder.EXTRA_PROFILE) ?: return null
        val period = intent.getStringExtra(com.bigpapituna.sharpears.reminder.BriefReminder.EXTRA_PERIOD)
        return briefOf("$id|${period ?: "WEEK"}")
    }

    /** "<profile id>|<PERIOD>" back to the request (a bad period is the week). */
    private fun briefOf(s: String): Pair<String, com.bigpapituna.sharpears.engine.brief.Period>? {
        val id = s.substringBefore('|').takeIf { it.isNotBlank() } ?: return null
        val period = com.bigpapituna.sharpears.engine.brief.Period.entries.firstOrNull { it.name == s.substringAfter('|', "") }
            ?: com.bigpapituna.sharpears.engine.brief.Period.WEEK
        return id to period
    }

    /**
     * Debug builds (2.2): --es kidseed <page>:<n> marks a page's first n lessons done with stars (3, 2, 3, 1, ...) and
     * forgets its finale; road marks every page walked and its finale played; reset forgets the Playground's lessons,
     * stars and finales. --ez sleepy true reaches today's goal and says goodnight (the map shows the pup asleep);
     * --ez awake true lets a grown-up's one more lesson through today (the map awake whatever was played).
     */
    /**
     * Debug builds (2.5, the wardrobe and decorate mode): `--es wardrobe all|<ids>` opens items, `--es wear <ids>` dresses
     * the pup, `--es fresh <ids>` queues items for the next finish screen's reveal, `--es decor <key>@<slot>+...` sticks
     * stickers (`--es decor clear` empties every spot), `--ez homes true` brings the "new homes" line back. Ids by commas.
     */
    private fun worldDebugSeed(intent: android.content.Intent, world: WorldStore) {
        fun items(extra: String) = intent.getStringExtra(extra)?.let { v ->
            if (v == "all") com.bigpapituna.sharpears.engine.world.WearItem.entries.toList()
            else v.split(",").mapNotNull { com.bigpapituna.sharpears.engine.world.WearItem.byId(it.trim()) }
        }
        var s = world.state
        items("wardrobe")?.let { s = s.copy(wardrobe = s.wardrobe + it) }
        items("wear")?.let { w -> s = s.copy(wardrobe = s.wardrobe + w, outfit = w.fold(com.bigpapituna.sharpears.engine.world.Outfit.NONE) { o, i -> o.with(i) }) }
        items("fresh")?.let { f -> s = s.copy(wardrobe = s.wardrobe + f, fresh = f) }
        intent.getStringExtra("decor")?.let { spec ->
            s = if (spec == "clear") s.copy(placements = com.bigpapituna.sharpears.engine.world.Placements())
            else spec.split("+").fold(s) { st, p -> com.bigpapituna.sharpears.engine.world.World.place(st, p.substringBefore("@"), p.substringAfter("@")) }
        }
        if (intent.getBooleanExtra("homes", false)) s = s.copy(homesLine = true)
        if (s != world.state) world.debugSet(s)
    }

    private fun kidDebugSeed(intent: android.content.Intent, prefs: Prefs, kid: KidStore, progress: ProgressStore,
                             bedtime: com.bigpapituna.sharpears.ui.skin.BedtimeStore) {
        // 2.14: --ei kidstars 1..3 seeds every stone at that many stars (Skip the garden wants three in a row).
        val stars = intent.getIntExtra("kidstars", 0).takeIf { it in 1..3 }?.let { s -> IntArray(12) { s } } ?: intArrayOf(3, 2, 3, 1, 3, 2, 2, 3, 1, 3, 2, 3)
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
        val today = com.bigpapituna.sharpears.data.AppClock.today()
        // 2.14: practice a seed adds goes to both stores and, as a session that ended just now, to the one minutes ledger, so the
        // brief, the family card and the daily time count it as the goal does (data/Briefs.debugSession).
        fun seedPractice(secs: Int) {
            if (secs <= 0) return
            prefs.addPractice(today, secs)
            progress.record(ProgressEvent.Practice(com.bigpapituna.sharpears.data.AppClock.now(), secs))
            Briefs.debugSession(prefs.profileId, secs * 1000L, secs)
        }
        if (intent.getBooleanExtra("sleepy", false)) {
            val need = progress.goalMinutes * 60 - progress.secondsToday(today)
            seedPractice(need)
            bedtime.goodnightOn = today; bedtime.oneMoreOn = null
            Log.d(TAG, "kid debug: today's goal reached, goodnight said")
        }
        if (intent.getBooleanExtra("awake", false)) { bedtime.oneMoreOn = today; Log.d(TAG, "kid debug: lessons open today") }
        // 2.5.0: --ei practice <minutes> adds practice to today (the wardrobe's ledger takes it in at the next sync).
        intent.getIntExtra("practice", 0).takeIf { it > 0 }?.let { m -> seedPractice(m * 60); Log.d(TAG, "kid debug: $m minutes of practice added today") }
        // 2.14: --ei onscreen <minutes> adds time on screen today with no practice (to walk the daily time with a short cap).
        intent.getIntExtra("onscreen", 0).takeIf { it > 0 }?.let { m ->
            Briefs.debugSession(prefs.profileId, m * 60_000L, 0); Log.d(TAG, "kid debug: $m minutes on screen added today")
        }
        // 2.14: --ei cap <minutes> sets the daily time (0 off), --ez closesongs true|false wind down's songs switch.
        if (intent.hasExtra("cap")) bedtime.capMin = intent.getIntExtra("cap", 0)
        if (intent.hasExtra("closesongs")) bedtime.closeSongs = intent.getBooleanExtra("closesongs", false)
        if (intent.hasExtra("winddown")) bedtime.bedtime = intent.getBooleanExtra("winddown", false)
    }

    /**
     * 2.9, debug builds: `--es today 2026-10-28` moves the app's today (data/AppClock.kt) for a walk on an emulator whose
     * clock cannot be set without root; the brief's events are stamped on the same moved clock. Lasts for the process.
     * [allowed]: [routesAllowed] let this intent's extras through.
     */
    private fun debugToday(intent: android.content.Intent?, allowed: Boolean) {
        if (!allowed) return
        val day = intent?.getStringExtra("today")?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return
        com.bigpapituna.sharpears.data.AppClock.pretend(day)
        Briefs.clock = com.bigpapituna.sharpears.data.AppClock::millis
        Log.d(TAG, "clock: today is $day (debug, moved ${com.bigpapituna.sharpears.data.AppClock.offsetDays} days)")
    }

    /**
     * Whether [intent]'s extras may act as debug routes (security review 2026-10-22, finding 1): a debug build, and
     * [DebugRoutes.allowed] with the build's key digest, the intent's `dk` extra, its action and whether the launcher (its icon
     * or a shortcut) or a browser sent it. Logs why a launch with extras was refused, never the key.
     */
    private fun routesAllowed(intent: android.content.Intent): Boolean {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val names = runCatching { intent.extras?.keySet()?.toSet() }.getOrNull().orEmpty() - NOTIFICATION_EXTRAS
        if (names.isEmpty()) return false                 // nothing to act on (the launcher, a notification, a link)
        val fromLauncher = intent.hasCategory(android.content.Intent.CATEGORY_LAUNCHER) || intent.hasCategory(android.content.Intent.CATEGORY_BROWSABLE) ||
            intent.sourceBounds != null || intent.hasExtra("android.intent.extra.shortcut.ID")
        val key = runCatching { intent.getStringExtra(com.bigpapituna.sharpears.engine.DebugRoutes.KEY_EXTRA) }.getOrNull()
        val verdict = com.bigpapituna.sharpears.engine.DebugRoutes.verdict(BuildConfig.DEBUG_ROUTE_KEY_SHA256, key, intent.action, fromLauncher)
        val ok = debuggable && verdict == com.bigpapituna.sharpears.engine.DebugRoutes.Verdict.ALLOWED
        if (!ok) Log.d(TAG, "debug routes: extras ignored (${if (debuggable) verdict.name.lowercase() else "not a debug build"}): " +
            (names - com.bigpapituna.sharpears.engine.DebugRoutes.KEY_EXTRA).sorted().joinToString())
        return ok
    }

    /** [intent] with every extra dropped but the weekly brief notification's two ([NOTIFICATION_EXTRAS], any build). */
    private fun withoutDebugExtras(intent: android.content.Intent): android.content.Intent {
        val kept = Bundle()
        for (k in NOTIFICATION_EXTRAS) runCatching { intent.getStringExtra(k) }.getOrNull()?.let { kept.putString(k, it) }
        return android.content.Intent(intent).apply { replaceExtras(kept) }
    }

    /**
     * The activity is singleTop (manifest): the launcher icon, the reminder's notification and `adb shell am start` bring
     * this one back instead of stacking a second MainActivity on it (each with its own mic, voice, MIDI and sampler).
     * Debug builds: a route in the new intent (--es screen, song, lesson, profile, --ez daily) opens as a fresh launch would,
     * when [routesAllowed] lets it through.
     */
    override fun onNewIntent(raw: android.content.Intent) {
        super.onNewIntent(raw)
        val allowed = routesAllowed(raw)
        val intent = if (allowed) raw else withoutDebugExtras(raw)
        debugToday(intent, allowed)
        // The weekly brief's notification on an app already running: its brief opens over the screen that is up.
        briefFrom(intent)?.let { req ->
            Log.d(TAG, "brief: notification opens ${req.first} ${req.second}")
            briefSamples = allowed && intent.getBooleanExtra("brief_samples", false)
            briefRequest.value = req
            return
        }
        if (!allowed || (listOf("screen", "song", "lesson", "profile", "today").none { intent.getStringExtra(it) != null } && !intent.getBooleanExtra("daily", false))) return
        debugRoutes = true
        setIntent(intent)
        if (BuildConfig.DEBUG && intent.getStringExtra("screen") == "errortest") com.bigpapituna.sharpears.analytics.ErrorTest.run()   // 2.15
        intent.getStringExtra("profile")?.let(profiles::switchTo)
        openApp(fromLaunch = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shownRoute?.let { outState.putString(ROUTE, it) }
        briefRequest.value?.let { (id, period) -> outState.putString(BRIEF, "$id|${period.name}") }
        if (openId == null) outState.putBoolean(PICKER, true)
    }

    /**
     * The screen [route] names (see [routeOf]), each part going back to the one before it. A song reopens in wait mode at
     * its saved position, as from the library. A kid's Settings, Mic setup, Privacy and Licences sit behind the parent
     * gate, which a recreate closes: they reopen as the screen they go back to.
     */
    private fun screenAt(route: String, prefs: Prefs, kid: Boolean, map: Boolean = kid): Screen = route.split("/").fold<String, Screen>(Screen.Home()) { back, part ->
        val arg = part.substringAfter(":", "")
        when (part.substringBefore(":")) {
            "home" -> Screen.Home(HomeTab.valueOf(arg.substringBefore(":")),
                songs = arg.substringAfter(":", "").let { a -> if (a == "chords") com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS
                    else if (a == "library") com.bigpapituna.sharpears.ui.SongsSection.LIBRARY else null })
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
            "kidchords" -> Screen.KidChords(back, com.bigpapituna.sharpears.engine.chords.PopFind.Chip.entries.firstOrNull { it.name == arg })
            "popsong" -> arg.split(":").let { a ->
                Screen.PopSong(a[0], back, a.getOrNull(1)?.toIntOrNull() ?: 0, com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.entries.firstOrNull { it.name == a.getOrNull(2) },
                    a.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 })
            }
            "kidstickers" -> Screen.KidStickers(back, StickerPage.valueOf(arg))
            "finale" -> Screen.Finale(arg)
            // A little kid's chords are on their own Songs; a big kid has Learn's (2.14).
            "learnchords" -> if (map) back else Screen.LearnChords(back)
            "chordgames" -> if (map) back else Screen.ChordGames(back)
            "progressions" -> if (map) back else Screen.Progressions(back)
            "arcade" -> Screen.Arcade(back)
            "arcadegame" -> com.bigpapituna.sharpears.engine.arcade.ArcadeGame.byId(arg)?.let { Screen.ArcadeRound(it, back) } ?: back
            else -> back
        }
    }

    /**
     * 2.15: every answer to the microphone question, from whichever screen asked (each has its own launcher; androidx.activity
     * hands every result through here to them), as `mic_permission`. An answer already given asks nothing and sends nothing.
     */
    @Deprecated("androidx.activity still calls it for every permission result; overridden only to look")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        @Suppress("DEPRECATION") super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val i = permissions.indexOf(android.Manifest.permission.RECORD_AUDIO)
        if (i in grantResults.indices) com.bigpapituna.sharpears.analytics.Events.micPermission(grantResults[i] == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    // A session of the parent brief's log runs while the app is on screen as someone (the picker is no one).
    override fun onStart() {
        super.onStart(); com.bigpapituna.sharpears.engine.ForegroundClock.app.start(); openId?.let(Briefs::sessionStart)
        com.bigpapituna.sharpears.analytics.Tester.back()
        gate.back()                     // 2.14: more than 30 s in the background closes the parent gate
    }
    override fun onStop() {
        Briefs.sessionEnd(); com.bigpapituna.sharpears.engine.ForegroundClock.app.stop(); com.bigpapituna.sharpears.analytics.Tester.away()
        gate.away()
        super.onStop()
    }

    override fun onPause() {
        Juice.world?.flush()            // the treat jar saves every few notes; the rest now
        Briefs.checkpoint()             // 2.14: a session killed from here on keeps its minutes
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
